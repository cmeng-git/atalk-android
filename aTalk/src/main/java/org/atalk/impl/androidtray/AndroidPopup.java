/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.androidtray;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.os.Build;

import net.java.sip.communicator.impl.muc.MUCActivator;
import net.java.sip.communicator.impl.protocol.jabber.ChatRoomJabberImpl;
import net.java.sip.communicator.service.contactlist.MetaContact;
import net.java.sip.communicator.service.muc.ChatRoomWrapper;
import net.java.sip.communicator.service.protocol.*;
import net.java.sip.communicator.service.systray.PopupMessage;
import net.java.sip.communicator.service.systray.SystrayService;
import net.java.sip.communicator.util.ConfigurationUtils;

import org.atalk.android.R;
import org.atalk.android.aTalkApp;
import org.atalk.android.gui.AndroidGUIActivator;
import org.atalk.android.gui.chat.ChatPanel;
import org.atalk.android.gui.chat.ChatSessionManager;
import org.atalk.android.gui.dialogs.DialogActivity;
import org.atalk.android.gui.util.AndroidImageUtil;
import org.atalk.impl.androidnotification.AndroidNotifications;

import java.util.*;

import androidx.core.app.NotificationCompat;
import timber.log.Timber;

/**
 * Class manages displayed notification for given <code>PopupMessage</code>.
 *
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
public class AndroidPopup
{
    /**
     * Parent notifications handler
     */
    protected final NotificationPopupHandler handler;

    /**
     * Displayed <code>PopupMessage</code>.
     */
    protected PopupMessage popupMessage;

    /**
     * Timeout handler.
     */
    private Timer timeoutHandler;

    /**
     * Notification id.
     */
    protected int id;

    /**
     * Optional chatTransport descriptor if supplied by <code>PopupMessage</code>.
     */
    private Object mDescriptor;

    /*
     * Notification channel group
     */
    private String group;

    /**
     * Small icon used for this notification.
     */
    private int mSmallIcon;

    private Context mContext;

    /**
     * Stores all the endMuteTime for each notification Id.
     */
    private final static Hashtable<Integer, Long> snoozeEndTimes = new Hashtable<>();
    private Long muteEndTime;

    /**
     * Creates new instance of <code>AndroidPopup</code>.
     *
     * @param handler parent notifications handler that manages displayed notifications.
     * @param popupMessage the popup message that will be displayed by this instance.
     */
    protected AndroidPopup(NotificationPopupHandler handler, PopupMessage popupMessage)
    {
        this.handler = handler;
        this.popupMessage = popupMessage;
        mContext = aTalkApp.getGlobalContext();

        group = popupMessage.getGroup();
        id = (int) (System.currentTimeMillis() % Integer.MAX_VALUE);

        // set separate notification icon for each group of notification
        switch (group) {
            case AndroidNotifications.MESSAGE_GROUP:
                mSmallIcon = R.drawable.incoming_message;
                break;

            case AndroidNotifications.SILENT_GROUP:
                mSmallIcon = R.drawable.incoming_message;
                break;

            case AndroidNotifications.FILE_GROUP:
                mSmallIcon = R.drawable.ic_attach_dark;
                break;

            case AndroidNotifications.CALL_GROUP:
                switch (popupMessage.getMessageType()) {
                    case SystrayService.WARNING_MESSAGE_TYPE:
                        mSmallIcon = R.drawable.ic_alert_dark;
                        break;

                    case SystrayService.JINGLE_INCOMING_CALL:
                    case SystrayService.JINGLE_MESSAGE_PROPOSE:
                        mSmallIcon = R.drawable.call_incoming;
                        break;

                    case SystrayService.MISSED_CALL_MESSAGE_TYPE:
                        mSmallIcon = R.drawable.call_incoming_missed;
                        break;

                    default:
                        mSmallIcon = R.drawable.ic_info_dark;
                        break;
                }
                break;

            // default group is sharing general notification icon
            // By default all notifications share aTalk icon
            case AndroidNotifications.DEFAULT_GROUP:
            default:
                id = SystrayServiceImpl.getGeneralNotificationId();
                mSmallIcon = R.drawable.ic_notification;
                break;
        }
        // Extract contained chat descriptor if any
        mDescriptor = popupMessage.getTag();
    }

    /**
     * Returns displayed <code>PopupMessage</code>.
     *
     * @return displayed <code>PopupMessage</code>.
     */
    public PopupMessage getPopupMessage()
    {
        return popupMessage;
    }

    public int getPopupIcon()
    {
        return mSmallIcon;
    }

    /**
     * Removes this notification.
     */
    public void removeNotification(int nId)
    {
        cancelTimeout();
        snoozeEndTimes.remove(nId);
        NotificationManager notifyManager = aTalkApp.getNotificationManager();
        notifyManager.cancel(id);
    }

    /**
     * Returns <code>true</code> if this popup is related to given <code>ChatPanel</code>.
     *
     * @param chatPanel the <code>ChatPanel</code> to check.
     * @return <code>true</code> if this popup is related to given <code>ChatPanel</code>.
     */
    public boolean isChatRelated(ChatPanel chatPanel)
    {
        if (chatPanel != null) {
            Object descriptor = chatPanel.getChatSession().getCurrentChatTransport().getDescriptor();
            return (descriptor != null) && descriptor.equals(mDescriptor)
                    && (AndroidNotifications.MESSAGE_GROUP.equals(group)
                    || AndroidNotifications.FILE_GROUP.equals(group));
        }
        else {
            return false;
        }
    }

    /**
     * Returns notification id.
     *
     * @return notification id.
     */
    public int getId()
    {
        return id;
    }

    /**
     * Creates new <code>AndroidPopup</code> for given parameters.
     *
     * @param handler notifications manager.
     * @param popupMessage the popup message that will be displayed by returned <code>AndroidPopup</code>
     * @return new <code>AndroidPopup</code> for given parameters.
     */
    static public AndroidPopup createNew(NotificationPopupHandler handler, PopupMessage popupMessage)
    {
        return new AndroidPopup(handler, popupMessage);
    }

    /**
     * Tries to merge given <code>PopupMessage</code> with this instance. Will return merged
     * <code>AndroidPopup</code> or <code>null</code> otherwise.
     *
     * @param popupMessage the <code>PopupMessage</code> to merge.
     * @return merged <code>AndroidPopup</code> with given <code>PopupMessage</code> or <code>null</code> otherwise.
     */
    public AndroidPopup tryMerge(PopupMessage popupMessage)
    {
        if (this.isGroupTheSame(popupMessage) && isSenderTheSame(popupMessage)) {
            return mergePopup(popupMessage);
        }
        else {
            return null;
        }
    }

    /**
     * Merges this instance with given <code>PopupMessage</code>.
     *
     * @param popupMessage the <code>PopupMessage</code> to merge.
     * @return merge result for this <code>AndroidPopup</code> and given <code>PopupMessage</code>.
     */
    protected AndroidPopup mergePopup(PopupMessage popupMessage)
    {
        // Timeout notifications are replaced
        /*
         * if(this.timeoutHandler != null) { cancelTimeout(); this.popupMessage = popupMessage;
         * return this; } else {
         */
        AndroidMergedPopup merge = new AndroidMergedPopup(this);
        merge.mergePopup(popupMessage);
        return merge;
        // }
    }

    /**
     * Checks whether <code>Contact</code> of this instance matches with given <code>PopupMessage</code>.
     *
     * @param popupMessage the <code>PopupMessage</code> to check.
     * @return <code>true</code> if <code>Contact</code>s for this instance and given <code>PopupMessage</code> are the same.
     */
    private boolean isSenderTheSame(PopupMessage popupMessage)
    {
        return (mDescriptor != null) && mDescriptor.equals(popupMessage.getTag());
    }

    /**
     * Checks whether group of this instance matches with given <code>PopupMessage</code>.
     *
     * @param popupMessage the <code>PopupMessage</code> to check.
     * @return <code>true</code> if group of this instance and given <code>PopupMessage</code> are the same.
     */
    private boolean isGroupTheSame(PopupMessage popupMessage)
    {
        if (this.popupMessage.getGroup() == null) {
            return popupMessage.getGroup() == null;
        }
        else {
            return this.popupMessage.getGroup().equals(popupMessage.getGroup());
        }
    }

    /**
     * Returns message string that will displayed in single line notification.
     *
     * @return message string that will displayed in single line notification.
     */
    protected String getMessage()
    {
        return popupMessage.getMessage();
    }

    /**
     * Builds notification and returns the builder object which can be used to extend the notification.
     *
     * @return builder object describing current notification.
     */
    NotificationCompat.Builder buildNotification(int nId)
    {
        NotificationCompat.Builder builder;
        // Do not show head-up notification when user has put the id notification in snooze
        if (isSnooze(nId) || !ConfigurationUtils.isHeadsUpEnable()) {
            builder = new NotificationCompat.Builder(mContext, AndroidNotifications.SILENT_GROUP);
        }
        else {
            builder = new NotificationCompat.Builder(mContext, group);
        }

        builder.setSmallIcon(mSmallIcon)
                .setContentTitle(popupMessage.getMessageTitle())
                .setContentText(getMessage())
                .setAutoCancel(true)        // will be cancelled once clicked
                .setVibrate(new long[]{})   // no vibration
                .setSound(null);            // no sound

        Resources res = aTalkApp.getAppResources();
        // Preferred size
        int prefWidth = (int) res.getDimension(android.R.dimen.notification_large_icon_width);
        int prefHeight = (int) res.getDimension(android.R.dimen.notification_large_icon_height);

        // Use popup icon if provided
        Bitmap iconBmp = null;
        byte[] icon = popupMessage.getIcon();
        if (icon != null) {
            iconBmp = AndroidImageUtil.scaledBitmapFromBytes(icon, prefWidth, prefHeight);
        }

        // Set default avatar if none provided
        if (iconBmp == null && mDescriptor != null) {
            if (mDescriptor instanceof ChatRoom)
                iconBmp = AndroidImageUtil.scaledBitmapFromResource(res, R.drawable.ic_chatroom, prefWidth, prefHeight);
            else
                iconBmp = AndroidImageUtil.scaledBitmapFromResource(res, R.drawable.contact_avatar, prefWidth, prefHeight);
        }

        if (iconBmp != null) {
            if (iconBmp.getWidth() > prefWidth || iconBmp.getHeight() > prefHeight) {
                iconBmp = Bitmap.createScaledBitmap(iconBmp, prefWidth, prefHeight, true);
            }
            builder.setLargeIcon(iconBmp);
        }

        // Build inbox style
        NotificationCompat.InboxStyle inboxStyle = new NotificationCompat.InboxStyle();
        onBuildInboxStyle(inboxStyle);
        builder.setStyle(inboxStyle);
        return builder;
    }

    /**
     * Returns the <code>PendingIntent</code> that should be trigger when user clicks the notification.
     *
     * @return the <code>PendingIntent</code> that should be trigger by notification
     */
    public PendingIntent createContentIntent()
    {
        Intent targetIntent = null;
        PopupMessage message = getPopupMessage();

        String group = (message != null) ? message.getGroup() : null;
        if (AndroidNotifications.MESSAGE_GROUP.equals(group) || AndroidNotifications.FILE_GROUP.equals(group)) {
            Object tag = message.getTag();
            if (tag instanceof Contact) {
                Contact contact = (Contact) tag;
                MetaContact metaContact = AndroidGUIActivator.getContactListService().findMetaContactByContact(contact);
                if (metaContact == null) {
                    Timber.e("Meta contact not found for %s", contact);
                }
                else {
                    targetIntent = ChatSessionManager.getChatIntent(metaContact);
                }
            }
            else if (tag instanceof ChatRoomJabberImpl) {
                ChatRoomJabberImpl chatRoom = (ChatRoomJabberImpl) tag;
                ChatRoomWrapper chatRoomWrapper
                        = MUCActivator.getMUCService().getChatRoomWrapperByChatRoom(chatRoom, true);
                if (chatRoomWrapper == null) {
                    Timber.e("ChatRoomWrapper not found for %s", chatRoom.getIdentifier());
                }
                else {
                    targetIntent = ChatSessionManager.getChatIntent(chatRoomWrapper);
                }
            }
        }
        // Displays popup message details when the notification is clicked when targetIntent is null
        if ((message != null) && (targetIntent == null)) {
            targetIntent = DialogActivity.getDialogIntent(aTalkApp.getGlobalContext(),
                    message.getMessageTitle(), message.getMessage());
        }

        // Must be unique for each, so use the notification id as the request code
        return (targetIntent == null)
                ? null : PendingIntent.getActivity(aTalkApp.getGlobalContext(), getId(), targetIntent,
                Build.VERSION.SDK_INT < Build.VERSION_CODES.M ? PendingIntent.FLAG_UPDATE_CURRENT
                        : PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    /**
     * Method fired when large notification view using <code>InboxStyle</code> is being built.
     *
     * @param inboxStyle the inbox style instance used for building large notification view.
     */
    protected void onBuildInboxStyle(NotificationCompat.InboxStyle inboxStyle)
    {
        inboxStyle.addLine(getMessage());
        // Summary
        if (mDescriptor instanceof Contact) {
            ProtocolProviderService pps = ((Contact) mDescriptor).getProtocolProvider();
            if (pps != null) {
                inboxStyle.setSummaryText(pps.getAccountID().getDisplayName());
            }
        }
    }

    /**
     * Cancels the timeout if it exists.
     */
    protected void cancelTimeout()
    {
        // Remove timeout handler
        if (timeoutHandler != null) {
            Timber.d("Removing timeout from notification: %s", id);
            // FFR: NPE: 2.1.5 AndroidPopup.cancelTimeout (AndroidPopup.java:379) ?
            timeoutHandler.cancel();
            timeoutHandler = null;
        }
    }

    /**
     * Enable snooze for the next 30 minutes
     */
    protected void setSnooze(int nId)
    {
        muteEndTime = (System.currentTimeMillis() + 30 * 60 * 1000);  // 30 minutes
        snoozeEndTimes.put(nId, muteEndTime);
    }

    /**
     * Check if the given notification ID is still in snooze period
     *
     * @param nId Notification id
     * @return true if it is still in snooze
     */
    protected boolean isSnooze(int nId)
    {
        muteEndTime = snoozeEndTimes.get(nId);
        return (muteEndTime != null) && (System.currentTimeMillis() < muteEndTime);

    }

    /**
     * Check if the android head-up notification allowed
     *
     * @return true if the group is MESSAGE_GROUP
     */
    public boolean isHeadUpNotificationAllow()
    {
        return ConfigurationUtils.isHeadsUpEnable() &&
                (AndroidNotifications.MESSAGE_GROUP.equals(group) || AndroidNotifications.CALL_GROUP.equals(group));
    }

    /**
     * Method called by notification manger when the notification is posted to the tray.
     */
    public void onPost()
    {
        cancelTimeout();
        long timeout = popupMessage.getTimeout();
        if (timeout > 0) {
            Timber.d("Setting timeout %d; on notification: %d", timeout, id);

            timeoutHandler = new Timer();
            timeoutHandler.schedule(new TimerTask()
            {
                @Override
                public void run()
                {
                    handler.onTimeout(AndroidPopup.this);
                }
            }, timeout);
        }
    }
}
