/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.androidtray;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;

import net.java.sip.communicator.impl.muc.MUCActivator;
import net.java.sip.communicator.impl.protocol.jabber.ChatRoomJabberImpl;
import net.java.sip.communicator.plugin.notificationwiring.NotificationManager;
import net.java.sip.communicator.service.contactlist.MetaContact;
import net.java.sip.communicator.service.muc.ChatRoomWrapper;
import net.java.sip.communicator.service.protocol.*;
import net.java.sip.communicator.service.systray.*;
import net.java.sip.communicator.service.systray.event.SystrayPopupMessageEvent;

import org.atalk.android.R;
import org.atalk.android.aTalkApp;
import org.atalk.android.gui.AndroidGUIActivator;
import org.atalk.android.gui.aTalk;
import org.atalk.android.gui.call.*;
import org.atalk.android.gui.chat.ChatPanel;
import org.atalk.android.gui.chat.ChatSessionManager;
import org.atalk.android.gui.chatroomslist.ChatRoomListFragment;
import org.atalk.android.gui.contactlist.ContactListFragment;
import org.atalk.android.gui.util.AndroidUtils;
import org.atalk.impl.androidnotification.AndroidNotifications;
import org.atalk.service.osgi.OSGiService;
import org.jxmpp.jid.Jid;

import java.util.*;

import androidx.core.app.NotificationCompat;
import androidx.core.app.RemoteInput;
import androidx.fragment.app.Fragment;
import timber.log.Timber;

/**
 * Displays popup messages as Android status bar notifications.
 *
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
public class NotificationPopupHandler extends AbstractPopupMessageHandler
        implements ChatSessionManager.CurrentChatListener
{
    private static final String KEY_TEXT_REPLY = "key_text_reply";

    private static Context mContext = aTalkApp.getGlobalContext();

    /**
     * Map of currently displayed <tt>AndroidPopup</tt>s. Value is removed when
     * corresponding notification is clicked or discarded.
     */
    private static Map<Integer, AndroidPopup> notificationMap = new HashMap<>();

    /**
     * Map of call sid to notificationId, for remote removing of heads-up notification
     */
    private static Map<String, Integer> callNotificationMap = new HashMap<>();


    /**
     * Creates new instance of <tt>NotificationPopupHandler</tt>. Registers as active chat listener.
     */
    public NotificationPopupHandler()
    {
        ChatSessionManager.addCurrentChatListener(this);
    }

    /**
     * {@inheritDoc}
     */
    public void showPopupMessage(PopupMessage popupMessage)
    {
        AndroidPopup newPopup = null;
        // Check for existing notifications and create mergePopUp else create new
        for (AndroidPopup popup : notificationMap.values()) {
            AndroidPopup merge = popup.tryMerge(popupMessage);
            if (merge != null) {
                newPopup = merge;
                break;
            }
        }
        if (newPopup == null) {
            newPopup = AndroidPopup.createNew(this, popupMessage);
        }

        // Create the notification base view
        int nId = newPopup.getId();
        NotificationCompat.Builder mBuilder = newPopup.buildNotification(nId);

        // Create and register the content intent for click action
        mBuilder.setContentIntent(newPopup.createContentIntent());

        // Register delete intent
        mBuilder.setDeleteIntent(createDeleteIntent(nId));
        mBuilder.setWhen(0);

        // Must setFullScreenIntent to wake android from sleep and for heads-up to stay on
        // heads-up notification is for both the Jingle Message propose and Jingle call
        String notificationGroup = popupMessage.getGroup();
        switch (notificationGroup) {
            case AndroidNotifications.CALL_GROUP:
                if (NotificationManager.INCOMING_CALL.equals(popupMessage.getEventType())) {
                    Object tag = popupMessage.getTag();
                    // Timber.d("Popup Message Tag: %s", tag);
                    if (tag == null)
                        return;
                    callNotificationMap.put((String) tag, nId);

                    Intent fullScreenIntent;
                    if (SystrayService.JINGLE_MESSAGE_PROPOSE == popupMessage.getMessageType()) {
                        fullScreenIntent = new Intent(mContext, JingleMessageCallActivity.class)
                                .putExtra(CallManager.CALL_SID, (String) tag)
                                .putExtra(CallManager.CALL_EVENT, popupMessage.getEventType());
                    }
                    else {
                        fullScreenIntent = new Intent(mContext, ReceivedCallActivity.class)
                                .putExtra(CallManager.CALL_IDENTIFIER, (String) tag);
                    }

                    PendingIntent fullScreenPendingIntent = PendingIntent.getActivity(aTalkApp.getGlobalContext(),
                            0, fullScreenIntent, PendingIntent.FLAG_UPDATE_CURRENT);

                    mBuilder.setPriority(NotificationCompat.PRIORITY_HIGH)
                            .setCategory(Notification.CATEGORY_CALL)
                            .setFullScreenIntent(fullScreenPendingIntent, true)
                            .setOngoing(true)
                            .setAutoCancel(false);  // must not allow user to cancel, else no UI to take call

                    // Build end call action
                    NotificationCompat.Action dimissAction = new NotificationCompat.Action.Builder(
                            R.drawable.ic_call_end_light,
                            aTalkApp.getResString(R.string.service_gui_DISMISS),
                            createDismissIntent(nId)).build();
                    mBuilder.addAction(dimissAction);

                    // Build answer call action
                    NotificationCompat.Action answerAction = new NotificationCompat.Action.Builder(
                            R.drawable.ic_call_light,
                            aTalkApp.getResString(R.string.service_gui_ANSWER),
                            createAnswerIntent(nId)).build();
                    mBuilder.addAction(answerAction);
                }
                break;

            // Create android Heads-up / Action Notification for incoming message
            case AndroidNotifications.MESSAGE_GROUP:
                if (!aTalkApp.isForeground && !newPopup.isSnooze(nId) && newPopup.isHeadUpNotificationAllow()) {
                    mBuilder.setPriority(NotificationCompat.PRIORITY_HIGH);

                    // Build Reply action for OS >= android-N
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        RemoteInput remoteInput = new RemoteInput.Builder(KEY_TEXT_REPLY)
                                .setLabel("")
                                .build();

                        NotificationCompat.Action replyAction = new NotificationCompat.Action.Builder(
                                R.drawable.ic_send_text_dark,
                                aTalkApp.getResString(R.string.service_gui_REPLY),
                                createReplyIntent(nId))
                                .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_REPLY)
                                .setShowsUserInterface(false)
                                .addRemoteInput(remoteInput)
                                .build();
                        mBuilder.addAction(replyAction);
                    }

                    // Build Mark as read action
                    NotificationCompat.Action markReadAction = new NotificationCompat.Action.Builder(
                            R.drawable.ic_read_dark,
                            aTalkApp.getResString(R.string.service_gui_MAS),
                            createMarkAsReadIntent(nId))
                            .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_MARK_AS_READ)
                            .setShowsUserInterface(false)
                            .build();
                    mBuilder.addAction(markReadAction);

                    // Build Snooze action if more than the specific limit has been reached
                    if (newPopup instanceof AndroidMergedPopup) {
                        if (((AndroidMergedPopup) newPopup).displaySnoozeAction()) {
                            NotificationCompat.Action snoozeAction = new NotificationCompat.Action.Builder(
                                    R.drawable.ic_notifications_paused_dark,
                                    aTalkApp.getResString(R.string.service_gui_SNOOZE),
                                    createSnoozeIntent(nId)).build();
                            mBuilder.addAction(snoozeAction);
                        }
                    }
                }
                break;
        }

        // post the notification
        aTalkApp.getNotificationManager().notify(nId, mBuilder.build());
        newPopup.onPost();

        // caches the notification until clicked or cleared
        notificationMap.put(nId, newPopup);
    }

    /**
     * Create a pending intent onDelete
     *
     * @param id Must be unique for each, so use notification id as request code
     * @return Delete PendingIntent
     */
    private PendingIntent createDeleteIntent(int id)
    {
        final Intent intent = PopupClickReceiver.createDeleteIntent(id);
        return PendingIntent.getBroadcast(mContext, id, intent, PendingIntent.FLAG_UPDATE_CURRENT);
    }

    /**
     * Create a pending intent onDelete
     *
     * @param id Must be unique for each, so use notification id as request code
     * @return Delete PendingIntent
     */
    private PendingIntent createReplyIntent(int id)
    {
        final Intent intent = PopupClickReceiver.createReplyIntent(id);
        return PendingIntent.getBroadcast(mContext, id, intent, PendingIntent.FLAG_UPDATE_CURRENT);
    }

    /**
     * Create a pending intent onDelete
     *
     * @param id Must be unique for each, so use notification id as request code
     * @return Delete PendingIntent
     */
    private PendingIntent createMarkAsReadIntent(int id)
    {
        final Intent intent = PopupClickReceiver.createMarkAsReadIntent(id);
        return PendingIntent.getBroadcast(mContext, id, intent, PendingIntent.FLAG_UPDATE_CURRENT);
    }

    /**
     * Create a pending intent onDelete
     *
     * @param id Must be unique for each, so use notification id as request code
     * @return Delete PendingIntent
     */
    private PendingIntent createSnoozeIntent(int id)
    {
        final Intent intent = PopupClickReceiver.createSnoozeIntent(id);
        return PendingIntent.getBroadcast(mContext, id, intent, PendingIntent.FLAG_UPDATE_CURRENT);
    }

    /**
     * Create a pending intent onDismiss call
     *
     * @param id Must be unique for each, so use notification id as request code
     * @return Delete PendingIntent
     */
    private PendingIntent createDismissIntent(int id)
    {
        final Intent intent = PopupClickReceiver.createCallDismiss(id);
        return PendingIntent.getBroadcast(mContext, id, intent, PendingIntent.FLAG_UPDATE_CURRENT);
    }

    /**
     * Create a pending intent onAnswer call
     *
     * @param id Must be unique for each, so use notification id as request code
     * @return Delete PendingIntent
     */
    private PendingIntent createAnswerIntent(int id)
    {
        final Intent intent = PopupClickReceiver.createCallAnswer(id);
        return PendingIntent.getBroadcast(mContext, id, intent, PendingIntent.FLAG_UPDATE_CURRENT);
    }

    /**
     * Fires <tt>SystrayPopupMessageEvent</tt> for clicked notification.
     *
     * @param notificationId the id of clicked notification.
     */
    void fireNotificationClicked(int notificationId)
    {
        AndroidPopup popup = notificationMap.get(notificationId);
        if (popup == null) {
            Timber.e("No valid notification exists for %s", notificationId);
            return;
        }

        PopupMessage msg = popup.getPopupMessage();
        if (msg == null) {
            Timber.e("No popup message found for %s", notificationId);
            return;
        }
        firePopupMessageClicked(new SystrayPopupMessageEvent(msg, msg.getTag()));
        removeNotification(notificationId);
    }

    /**
     * Fires <tt>SystrayPopupMessageEvent</tt> for clicked notification.
     *
     * @param notificationId the id of clicked notification.
     */
    void fireNotificationClicked(int notificationId, Intent intent)
    {
        AndroidPopup popup = notificationMap.get(notificationId);
        if (popup == null) {
            Timber.e("No valid notification exists for %s", notificationId);
            return;
        }

        PopupMessage message = popup.getPopupMessage();
        String group = (message != null) ? message.getGroup() : null;

        Bundle remoteInput = RemoteInput.getResultsFromIntent(intent);
        CharSequence replyText = null;
        if (remoteInput != null) {
            replyText = remoteInput.getCharSequence(KEY_TEXT_REPLY);
        }

        Notification repliedNotification;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            repliedNotification = new Notification.Builder(mContext, group)
                    .setSmallIcon(popup.getPopupIcon())
                    .setContentText(replyText)
                    .build();

            // Issue the new notification to acknowledge
            aTalkApp.getNotificationManager().notify(notificationId, repliedNotification);
        }

        if (!TextUtils.isEmpty(replyText) && AndroidNotifications.MESSAGE_GROUP.equals(group)) {
            ChatPanel chatPanel = null;
            Object tag = message.getTag();
            if (tag instanceof Contact) {
                Contact contact = (Contact) tag;
                MetaContact metaContact = AndroidGUIActivator.getContactListService().findMetaContactByContact(contact);
                if (metaContact != null) {
                    chatPanel = ChatSessionManager.getActiveChat(metaContact.getMetaUID());
                }
            }
            else if (tag instanceof ChatRoomJabberImpl) {
                ChatRoomJabberImpl chatRoom = (ChatRoomJabberImpl) tag;
                ChatRoomWrapper chatRoomWrapper
                        = MUCActivator.getMUCService().getChatRoomWrapperByChatRoom(chatRoom, false);
                if (chatRoomWrapper != null) {
                    chatPanel = ChatSessionManager.getActiveChat(chatRoomWrapper.getChatRoomID());
                }
            }
            if ((chatPanel != null) && (replyText != null)) {
                Timber.d("Popup action reply message to: %s %s", tag, replyText);
                chatPanel.sendMessage(replyText.toString(), IMessage.ENCODE_PLAIN);
            }
        }

        // Clear systray notification and reset unread message counter;
        fireNotificationClicked(notificationId, PopupClickReceiver.ACTION_MARK_AS_READ);
    }

    /**
     * Fires <tt>SystrayPopupMessageEvent</tt> for clicked notification with the specified action.
     *
     * @param notificationId the id of clicked notification.
     * @param action the action to be perform of clicked notification.
     */
    void fireNotificationClicked(int notificationId, String action)
    {
        AndroidPopup popup = notificationMap.get(notificationId);
        if (popup == null) {
            Timber.e("No valid notification exists for %s", notificationId);
            return;
        }

        // Remove the notification for all actions except ACTION_SNOOZE.
        if (!PopupClickReceiver.ACTION_SNOOZE.equals(action))
            removeNotification(notificationId);

        // Retrieve the popup tag to process
        PopupMessage message = popup.getPopupMessage();
        Object tag = message.getTag();
        boolean jinglePropose = SystrayService.JINGLE_MESSAGE_PROPOSE == message.getMessageType();

        switch (action) {
            case PopupClickReceiver.ACTION_POPUP_CLEAR:
                break;

            case PopupClickReceiver.ACTION_MARK_AS_READ:
                if (tag instanceof Contact) {
                    Contact contact = (Contact) tag;
                    MetaContact metaContact = AndroidGUIActivator.getContactListService().findMetaContactByContact(contact);
                    if (metaContact != null) {
                        metaContact.setUnreadCount(0);
                    }
                    Fragment clf = aTalk.getFragment(aTalk.CL_FRAGMENT);
                    if (clf instanceof ContactListFragment) {
                        ((ContactListFragment) clf).updateUnreadCount(metaContact);
                    }
                }
                else if (tag instanceof ChatRoomJabberImpl) {
                    ChatRoomJabberImpl chatRoom = (ChatRoomJabberImpl) tag;
                    ChatRoomWrapper chatRoomWrapper
                            = MUCActivator.getMUCService().getChatRoomWrapperByChatRoom(chatRoom, false);
                    chatRoomWrapper.setUnreadCount(0);
                    Fragment crlf = aTalk.getFragment(aTalk.CRL_FRAGMENT);
                    if (crlf instanceof ChatRoomListFragment) {
                        ((ChatRoomListFragment) crlf).updateUnreadCount(chatRoomWrapper);
                    }
                }
                break;

            case PopupClickReceiver.ACTION_SNOOZE:
                popup.setSnooze(notificationId);
                break;

            case PopupClickReceiver.ACTION_CALL_DISMISS:
                String sid = (String) tag;
                callNotificationMap.remove(sid);

                if (jinglePropose) {
                    JingleMessageHelper.sendJingleMessageReject(sid);
                }
                else {
                    Call call = CallManager.getActiveCall(sid);
                    if (call != null) {
                        CallManager.hangupCall(call);
                    }
                }
                break;

            case PopupClickReceiver.ACTION_CALL_ANSWER:
                sid = (String) tag;
                callNotificationMap.remove(sid);

                if (jinglePropose) {
                    JingleMessageHelper.sendJingleAccept(sid);
                }
                else {
                    Call call = CallManager.getActiveCall(sid);
                    if (call != null) {
                        AndroidCallListener.answerCall(call, true);
                    }
                }
                break;

            default:
                Timber.w("Unsupported action: %s", action);
        }

        PopupMessage msg = popup.getPopupMessage();
        if (msg == null) {
            Timber.e("No popup message found for %s", notificationId);
            return;
        }
        firePopupMessageClicked(new SystrayPopupMessageEvent(msg, msg.getTag()));
    }

    /**
     * Removes notification for given <tt>notificationId</tt> and performs necessary cleanup.
     *
     * @param notificationId the id of notification to remove.
     */
    private static void removeNotification(int notificationId)
    {
        if (notificationId == OSGiService.getGeneralNotificationId()) {
            AndroidUtils.clearGeneralNotification(mContext);
        }
        AndroidPopup popup = notificationMap.get(notificationId);
        if (popup == null) {
            Timber.w("Notification for id: %s already removed", notificationId);
            return;
        }

        Timber.d("Removing notification: %s", notificationId);
        popup.removeNotification(notificationId);
        notificationMap.remove(notificationId);
    }

    /**
     * Clear the entry in the callNotificationMap for the specified call Id.
     * The callNotificationMap entry for the callId must be cleared, so the Ring tone will stop
     *
     * @param callId call Id / Jingle Sid
     * @see JingleMessageHelper#onCallProposed(Jid, String)
     * @see getCallNotificationId
     */
    public static void removeCallNotification(String callId)
    {
        Integer notificationId = callNotificationMap.get(callId);
        if (notificationId != null) {
            removeNotification(notificationId);
            callNotificationMap.remove(callId);
        }
    }

    /**
     * Use by phone ring Tone to check if the call notification has been dismissed, hence to stop the ring tone
     *
     * @param callId call Id / Jingle Sid
     * @return the notificationId for the specified callId
     */
    public static Integer getCallNotificationId(String callId)
    {
        return callNotificationMap.get(callId);
    }

    /**
     * Removes all currently registered notifications from the status bar.
     */
    void dispose()
    {
        // Removes active chat listener
        ChatSessionManager.removeCurrentChatListener(this);

        for (Map.Entry<Integer, AndroidPopup> entry : notificationMap.entrySet()) {
            entry.getValue().removeNotification(entry.getKey());
        }
        notificationMap.clear();
    }

    /**
     * {@inheritDoc} <br/>
     * This implementations scores 3: <br/>
     * +1 detecting clicks <br/>
     * +1 being able to match a click to a message <br/>
     * +1 using a native popup mechanism <br/>
     */
    public int getPreferenceIndex()
    {
        return 3;
    }

    @Override
    public String toString()
    {
        // return aTalkApp.getResString(R.string.impl_popup_status_bar);
        return getClass().getName();
    }

    /**
     * Method called by <tt>AndroidPopup</tt> to signal the timeout.
     *
     * @param popup <tt>AndroidPopup</tt> on which timeout event has occurred.
     */
    public void onTimeout(AndroidPopup popup)
    {
        removeNotification(popup.getId());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onCurrentChatChanged(String chatId)
    {
        // Clears chat notification related to currently opened chat for incomingMessage & incomingFile
        ChatPanel openChat = ChatSessionManager.getActiveChat(chatId);

        if (openChat == null)
            return;

        List<AndroidPopup> chatPopups = new ArrayList<>();
        for (AndroidPopup popup : notificationMap.values()) {
            if (popup.isChatRelated(openChat)) {
                chatPopups.add(popup);
                break;
            }
        }
        for (AndroidPopup chatPopup : chatPopups) {
            removeNotification(chatPopup.getId());
        }
    }
}
