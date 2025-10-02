/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.plugin.notificationwiring;

import android.text.Html;

import androidx.fragment.app.Fragment;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.Callable;

import net.java.sip.communicator.impl.muc.MUCActivator;
import net.java.sip.communicator.impl.muc.MUCServiceImpl;
import net.java.sip.communicator.impl.protocol.jabber.ChatRoomJabberImpl;
import net.java.sip.communicator.service.contactlist.MetaContact;
import net.java.sip.communicator.service.gui.Chat;
import net.java.sip.communicator.service.gui.UIService;
import net.java.sip.communicator.service.muc.ChatRoomWrapper;
import net.java.sip.communicator.service.notification.CommandNotificationAction;
import net.java.sip.communicator.service.notification.CommandNotificationHandler;
import net.java.sip.communicator.service.notification.NotificationAction;
import net.java.sip.communicator.service.notification.NotificationData;
import net.java.sip.communicator.service.notification.NotificationService;
import net.java.sip.communicator.service.notification.SoundNotificationAction;
import net.java.sip.communicator.service.protocol.AdHocChatRoom;
import net.java.sip.communicator.service.protocol.Call;
import net.java.sip.communicator.service.protocol.CallConference;
import net.java.sip.communicator.service.protocol.CallPeer;
import net.java.sip.communicator.service.protocol.CallPeerState;
import net.java.sip.communicator.service.protocol.ChatRoom;
import net.java.sip.communicator.service.protocol.ChatRoomMember;
import net.java.sip.communicator.service.protocol.Contact;
import net.java.sip.communicator.service.protocol.IMessage;
import net.java.sip.communicator.service.protocol.IncomingFileTransferRequest;
import net.java.sip.communicator.service.protocol.OperationSet;
import net.java.sip.communicator.service.protocol.OperationSetAdHocMultiUserChat;
import net.java.sip.communicator.service.protocol.OperationSetBasicInstantMessaging;
import net.java.sip.communicator.service.protocol.OperationSetBasicTelephony;
import net.java.sip.communicator.service.protocol.OperationSetChatStateNotifications;
import net.java.sip.communicator.service.protocol.OperationSetFileTransfer;
import net.java.sip.communicator.service.protocol.OperationSetMultiUserChat;
import net.java.sip.communicator.service.protocol.OperationSetSmsMessaging;
import net.java.sip.communicator.service.protocol.ProtocolProviderFactory;
import net.java.sip.communicator.service.protocol.ProtocolProviderService;
import net.java.sip.communicator.service.protocol.event.AdHocChatRoomMessageDeliveredEvent;
import net.java.sip.communicator.service.protocol.event.AdHocChatRoomMessageDeliveryFailedEvent;
import net.java.sip.communicator.service.protocol.event.AdHocChatRoomMessageListener;
import net.java.sip.communicator.service.protocol.event.AdHocChatRoomMessageReceivedEvent;
import net.java.sip.communicator.service.protocol.event.CallChangeEvent;
import net.java.sip.communicator.service.protocol.event.CallChangeListener;
import net.java.sip.communicator.service.protocol.event.CallEvent;
import net.java.sip.communicator.service.protocol.event.CallListener;
import net.java.sip.communicator.service.protocol.event.CallPeerChangeEvent;
import net.java.sip.communicator.service.protocol.event.CallPeerConferenceEvent;
import net.java.sip.communicator.service.protocol.event.CallPeerConferenceListener;
import net.java.sip.communicator.service.protocol.event.CallPeerEvent;
import net.java.sip.communicator.service.protocol.event.CallPeerListener;
import net.java.sip.communicator.service.protocol.event.CallPeerSecurityListener;
import net.java.sip.communicator.service.protocol.event.CallPeerSecurityMessageEvent;
import net.java.sip.communicator.service.protocol.event.CallPeerSecurityNegotiationStartedEvent;
import net.java.sip.communicator.service.protocol.event.CallPeerSecurityOffEvent;
import net.java.sip.communicator.service.protocol.event.CallPeerSecurityOnEvent;
import net.java.sip.communicator.service.protocol.event.CallPeerSecurityStatusEvent;
import net.java.sip.communicator.service.protocol.event.CallPeerSecurityTimeoutEvent;
import net.java.sip.communicator.service.protocol.event.ChatRoomMessageDeliveredEvent;
import net.java.sip.communicator.service.protocol.event.ChatRoomMessageDeliveryFailedEvent;
import net.java.sip.communicator.service.protocol.event.ChatRoomMessageListener;
import net.java.sip.communicator.service.protocol.event.ChatRoomMessageReceivedEvent;
import net.java.sip.communicator.service.protocol.event.ChatStateNotificationEvent;
import net.java.sip.communicator.service.protocol.event.ChatStateNotificationsListener;
import net.java.sip.communicator.service.protocol.event.FileTransferCreatedEvent;
import net.java.sip.communicator.service.protocol.event.FileTransferRequestEvent;
import net.java.sip.communicator.service.protocol.event.LocalUserAdHocChatRoomPresenceChangeEvent;
import net.java.sip.communicator.service.protocol.event.LocalUserAdHocChatRoomPresenceListener;
import net.java.sip.communicator.service.protocol.event.LocalUserChatRoomPresenceChangeEvent;
import net.java.sip.communicator.service.protocol.event.LocalUserChatRoomPresenceListener;
import net.java.sip.communicator.service.protocol.event.MessageDeliveredEvent;
import net.java.sip.communicator.service.protocol.event.MessageDeliveryFailedEvent;
import net.java.sip.communicator.service.protocol.event.MessageListener;
import net.java.sip.communicator.service.protocol.event.MessageReceivedEvent;
import net.java.sip.communicator.service.protocol.event.ScFileTransferListener;
import net.java.sip.communicator.service.systray.SystrayService;

import org.apache.commons.text.StringEscapeUtils;
import org.atalk.android.R;
import org.atalk.android.aTalkApp;
import org.atalk.android.gui.AppGUIActivator;
import org.atalk.android.gui.AppUIServiceImpl;
import org.atalk.android.gui.aTalk;
import org.atalk.android.gui.chat.ChatMessage;
import org.atalk.android.gui.chat.ChatPanel;
import org.atalk.android.gui.chat.ChatTransport;
import org.atalk.android.gui.chat.conference.ConferenceChatManager;
import org.atalk.android.gui.chatroomslist.ChatRoomListFragment;
import org.atalk.android.gui.contactlist.ContactListFragment;
import org.atalk.android.util.AppImageUtil;
import org.atalk.service.neomedia.MediaService;
import org.atalk.service.neomedia.SrtpControl;
import org.atalk.service.neomedia.event.SrtpListener;
import org.atalk.service.neomedia.recording.Recorder;
import org.jivesoftware.smackx.chatstates.ChatState;
import org.osgi.framework.Bundle;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceEvent;
import org.osgi.framework.ServiceListener;
import org.osgi.framework.ServiceReference;

import timber.log.Timber;

/**
 * Listens to various events which are related to the display and/or playback of notifications
 * and shows/starts or hides/stops the notifications in question.
 *
 * @author Damian Minkov
 * @author Lyubomir Marinov
 * @author Eng Chong Meng
 * @see #registerDefaultNotifications
 * This is where all the events actions e.g. notification popup, vibrate and alert are defined for each notification
 */
public class NotificationManager implements CallChangeListener, CallListener, CallPeerConferenceListener,
        CallPeerListener, CallPeerSecurityListener, ChatRoomMessageListener, LocalUserChatRoomPresenceListener,
        AdHocChatRoomMessageListener, LocalUserAdHocChatRoomPresenceListener,
        ScFileTransferListener, MessageListener, Recorder.Listener, ServiceListener, ChatStateNotificationsListener {
    /**
     * Default event type for a busy call.
     */
    public static final String BUSY_CALL = "BusyCall";

    /**
     * Default event type for call been saved using a recorder.
     */
    public static final String CALL_SAVED = "CallSaved";

    /**
     * Default event type for security error on a call.
     */
    public static final String CALL_SECURITY_ERROR = "CallSecurityError";

    /**
     * Default event type for activated security on a call.
     */
    public static final String CALL_SECURITY_ON = "CallSecurityOn";

    /**
     * Default event type for dialing.
     */
    public static final String DIALING = "Dialing";

    /**
     * Default event type for hanging up calls.
     */
    public static final String HANG_UP = "HangUp";

    /**
     * Default event type for receiving calls (incoming calls).
     */
    public static final String INCOMING_CALL = "IncomingCall";

    /**
     * Default event type for incoming file transfers.
     */
    public static final String INCOMING_FILE = "IncomingFile";

    /**
     * Default event type for incoming invitation received.
     */
    public static final String INCOMING_INVITATION = "IncomingInvitation";

    /**
     * Default event type for receiving messages.
     */
    public static final String INCOMING_MESSAGE = "IncomingMessage";

    /**
     * Default event type for missed call.
     */
    public static final String MISSED_CALL = "MissedCall";

    /**
     * Default event type for outgoing calls.
     */
    public static final String OUTGOING_CALL = "OutgoingCall";

    /**
     * Default event type for proactive notifications (chat state notifications when chatting).
     */
    public static final String PROACTIVE_NOTIFICATION = "ProactiveNotification";
    /**
     * Default event type when a secure message received.
     */
    public static final String SECURITY_MESSAGE = "SecurityMessage";

    /**
     * Stores notification references to stop them if a notification has expired (e.g. to stop the dialing sound).
     */
    private final Map<Call, NotificationData> callNotifications = new WeakHashMap<>();
    /**
     * The pseudo timer which is used to delay multiple composing notifications before receiving the message.
     */
    private final Map<Object, Long> proactiveTimer = new HashMap<>();

    /**
     * Fires a chat message notification for the given event type through the <code>NotificationService</code>.
     *
     * @param chatDescriptor the chat contact to which the chat message corresponds; the chat contact could be a
     * Contact or a ChatRoom.
     * @param eventType the event type for which we fire a notification
     * @param messageTitle the title of the message
     * @param message the content of the message
     * @param messageUid the message UID
     */
    public static void fireChatNotification(Object chatDescriptor, String eventType, String messageTitle,
            String message, String messageUid) {
        NotificationService notificationService = NotificationWiringActivator.getNotificationService();
        if (notificationService == null)
            return;

        NotificationAction popupActionHandler = null;
        UIService uiService = NotificationWiringActivator.getUIService();

        Chat chatPanel = null;
        byte[] contactIcon = null;
        if (chatDescriptor instanceof Contact) {
            Contact contact = (Contact) chatDescriptor;

            if (uiService != null)
                chatPanel = uiService.getChat(contact);

            contactIcon = contact.getImage(false);
            if (contactIcon == null) {
                contactIcon = AppImageUtil.getImageBytes(aTalkApp.getInstance(), R.drawable.person_photo);
            }
        }
        else if (chatDescriptor instanceof ChatRoom) {
            ChatRoomJabberImpl chatRoom = (ChatRoomJabberImpl) chatDescriptor;

            // For system rooms we don't want to send notification events.
            if (chatRoom.isSystem())
                return;

            chatDescriptor = ((ChatRoom) chatDescriptor).getIdentifier();
            if (uiService != null) {
                chatPanel = uiService.getChat(chatRoom);
            }
        }

        // Do not popup notification if the chatPanel is focused for INCOMING_MESSAGE or INCOMING_FILE
        if ((chatPanel != null) && chatPanel.isChatFocused()
                && (eventType.equals(INCOMING_MESSAGE) || eventType.equals(INCOMING_FILE))) {
            popupActionHandler
                    = notificationService.getEventNotificationAction(eventType, NotificationAction.ACTION_POPUP_MESSAGE);
            popupActionHandler.setEnabled(false);
        }

        Map<String, Object> extras = new HashMap<>();
        extras.put(NotificationData.POPUP_MESSAGE_HANDLER_TAG_EXTRA, chatDescriptor);
        notificationService.fireNotification(eventType, SystrayService.INFORMATION_MESSAGE_TYPE,
                messageTitle, message, contactIcon, extras);

        // Reset the popupActionHandler to enable for ACTION_POPUP_MESSAGE for incomingMessage if it was disabled
        if (popupActionHandler != null)
            popupActionHandler.setEnabled(true);
    }

    /**
     * Fires a notification for the given event type through the <code>NotificationService</code>. The
     * event type is one of the static constants defined in the <code>NotificationManager</code> class.
     * <p>
     * <b>Note</b>: The uses of the method at the time of this writing do not take measures to stop looping sounds
     * if the respective notifications use them i.e. there is implicit agreement that the notifications fired through
     * the method do not loop sounds. Consequently, the method passes arguments to <code>NotificationService</code>
     * so that sounds are played once only.
     *
     * @param eventType the event type for which we want to fire a notification
     */
    public static void fireNotification(String eventType) {
        NotificationService notificationService = NotificationWiringActivator.getNotificationService();
        if (notificationService != null)
            notificationService.fireNotification(eventType);
    }

    /**
     * Fires a notification for the given event type through the <code>NotificationService</code>. The
     * event type is one of the static constants defined in the <code>NotificationManager</code> class.
     *
     * @param eventType the event type for which we want to fire a notification
     * @param loopCondition the method which will determine whether any sounds played as part of the specified
     * notification will continue looping
     *
     * @return a reference to the fired notification to stop it.
     */
    private static NotificationData fireNotification(String eventType, Callable<Boolean> loopCondition) {
        return fireNotification(eventType, null, null, null, loopCondition);
    }

    /**
     * Fires a notification through the <code>NotificationService</code> with a specific event type, a
     * specific message title and a specific message.
     * <p>
     * <b>Note</b>: The uses of the method at the time of this writing do not take measures to
     * stop looping sounds if the respective notifications use them i.e. there is implicit
     * agreement that the notifications fired through the method do not loop sounds. Consequently,
     * the method passes arguments to <code>NotificationService</code> so that sounds are played once only.
     *
     * @param eventType the event type of the notification to be fired
     * @param msgType the notification sub-category message type
     * @param messageTitle the title of the message to be displayed by the notification to be fired if such a
     * display is supported
     * @param message the message to be displayed by the notification to be fired if such a display is supported
     */
    private static void fireNotification(String eventType, int msgType, String messageTitle, String message) {
        NotificationService notificationService = NotificationWiringActivator.getNotificationService();
        if (notificationService != null) {
            notificationService.fireNotification(eventType, msgType, messageTitle, message, null);
        }
    }

    /**
     * Fires a message notification for the given event type through the <code>NotificationService</code>.
     *
     * @param eventType the event type for which we fire a notification
     * @param messageTitle the title of the message
     * @param message the content of the message
     * @param cmdargs the value to be provided to
     * {@link CommandNotificationHandler#execute(CommandNotificationAction, Map)} as the <code>cmdargs</code> argument
     * @param loopCondition the method which will determine whether any sounds played as part of the specified
     * notification will continue looping
     *
     * @return a reference to the fired notification to stop it.
     */
    private static NotificationData fireNotification(String eventType, String messageTitle,
            String message, Map<String, String> cmdargs, Callable<Boolean> loopCondition) {
        NotificationService notificationService = NotificationWiringActivator.getNotificationService();
        if (notificationService == null)
            return null;
        else {
            Map<String, Object> extras = new HashMap<>();
            if (cmdargs != null) {
                extras.put(NotificationData.COMMAND_NOTIFICATION_HANDLER_CMDARGS_EXTRA, cmdargs);
            }
            if (loopCondition != null) {
                extras.put(NotificationData.SOUND_NOTIFICATION_HANDLER_LOOP_CONDITION_EXTRA, loopCondition);
            }
            return notificationService.fireNotification(eventType,
                    SystrayService.INFORMATION_MESSAGE_TYPE, messageTitle, message, null, extras);
        }
    }

    /**
     * Returns all <code>ProtocolProviderFactory</code>s obtained from the bundle context.
     *
     * @return all <code>ProtocolProviderFactory</code>s obtained from the bundle context
     */
    public static Map<Object, ProtocolProviderFactory> getProtocolProviderFactories() {
        ServiceReference<?>[] serRefs = null;
        try {
            // get all registered provider factories
            serRefs = NotificationWiringActivator.bundleContext
                    .getServiceReferences(ProtocolProviderFactory.class.getName(), null);
        } catch (InvalidSyntaxException e) {
            Timber.e("NotificationManager : %s", e.getMessage());
        }

        Map<Object, ProtocolProviderFactory> providerFactoriesMap = new Hashtable<>();
        if (serRefs != null) {
            for (ServiceReference<?> serRef : serRefs) {
                ProtocolProviderFactory providerFactory
                        = (ProtocolProviderFactory) NotificationWiringActivator.bundleContext.getService(serRef);
                providerFactoriesMap.put(serRef.getProperty(ProtocolProviderFactory.PROTOCOL), providerFactory);
            }
        }
        return providerFactoriesMap;
    }

    /**
     * Returns all protocol providers currently registered.
     *
     * @return all protocol providers currently registered.
     */
    public static List<ProtocolProviderService> getProtocolProviders() {
        ServiceReference<?>[] serRefs = null;
        try {
            // get all registered provider factories
            serRefs = NotificationWiringActivator.bundleContext
                    .getServiceReferences(ProtocolProviderService.class.getName(), null);
        } catch (InvalidSyntaxException e) {
            Timber.e("NotificationManager : %s", e.getMessage());
        }

        List<ProtocolProviderService> providersList = new ArrayList<>();
        if (serRefs != null) {
            for (ServiceReference<?> serRef : serRefs) {
                ProtocolProviderService pp
                        = (ProtocolProviderService) NotificationWiringActivator.bundleContext.getService(serRef);
                providersList.add(pp);
            }
        }
        return providersList;
    }

    /**
     * Determines whether a specific {@code ChatRoom} is private i.e. represents a one-to-one
     * conversation which is not a channel. Since the interface {@link ChatRoom} does not expose
     * the private property, an heuristic is used as a workaround: (1) a system
     * {@code ChatRoom} is obviously not private and (2) a {@code ChatRoom} is private
     * if it has only one {@code ChatRoomMember} who is not the local user.
     *
     * @param chatRoom the {@code ChatRoom} to be determined as private or not
     *
     * @return <code>true</code> if the specified {@code ChatRoom} is private; otherwise, <code>false</code>
     */
    private static boolean isPrivate(ChatRoom chatRoom) {
        if (!chatRoom.isSystem() && chatRoom.isJoined() && (chatRoom.getMembersCount() == 1)) {
            String nickname = chatRoom.getUserNickname().toString();

            for (ChatRoomMember member : chatRoom.getMembers()) {
                if (nickname.equals(member.getNickName()))
                    return false;
            }
            return true;
        }
        return false;
    }

    /**
     * Determines whether the <code>DIALING</code> sound notification should be played for a specific <code>CallPeer</code>.
     *
     * @param weakPeer the <code>CallPeer</code> for which it is to be determined whether the <code>DIALING</code>
     * sound notification is to be played
     *
     * @return <code>true</code> if the <code>DIALING</code> sound notification should be played for the
     * specified <code>callPeer</code>; otherwise, code>false</code>
     */
    private static boolean shouldPlayDialingSound(WeakReference<CallPeer> weakPeer) {
        CallPeer peer = weakPeer.get();
        if (peer == null)
            return false;

        Call call = peer.getCall();
        if (call == null)
            return false;

        CallConference conference = call.getConference();
        if (conference == null)
            return false;

        boolean play = false;

        for (Call aCall : conference.getCalls()) {
            Iterator<? extends CallPeer> peerIter = aCall.getCallPeers();

            while (peerIter.hasNext()) {
                CallPeer aPeer = peerIter.next();

                // The peer is still in a call/telephony conference so the DIALING sound may need to be played.
                if (peer == aPeer)
                    play = true;

                CallPeerState state = peer.getState();
                if (CallPeerState.INITIATING_CALL.equals(state) || CallPeerState.CONNECTING.equals(state)) {
                    // The DIALING sound should be played for the first CallPeer only.
                    if (peer != aPeer)
                        return false;
                }
                else {
                    /*
                     * The DIALING sound should not be played if there is a CallPeer which does
                     * not require the DIALING sound to be played.
                     */
                    return false;
                }
            }
        }
        return play;
    }

    /**
     * Implements CallListener.callEnded. Stops sounds that are playing at the moment if there're any.
     *
     * @param evt the <code>CallEvent</code>
     */
    public void callEnded(CallEvent evt) {
        try {
            // Stop all telephony related sounds.
            // stopAllTelephonySounds();
            NotificationData notification = callNotifications.get(evt.getSourceCall());
            if (notification != null)
                stopSound(notification);

            // Play the hangup sound - Let peerStateChanged() fire HANG_UP; else double firing
            // fireNotification(HANG_UP);
        } catch (Throwable t) {
            if (t instanceof ThreadDeath)
                throw (ThreadDeath) t;
            else {
                Timber.e(t, "An error occurred while trying to notify about the end of a call.");
            }
        }
    }

    /**
     * Implements the <code>CallChangeListener.callPeerAdded</code> method.
     *
     * @param evt the <code>CallPeerEvent</code> that notifies us for the change
     */
    public void callPeerAdded(CallPeerEvent evt) {
        CallPeer peer = evt.getSourceCallPeer();
        if (peer == null)
            return;

        peer.addCallPeerListener(this);
        peer.addCallPeerSecurityListener(this);
        peer.addCallPeerConferenceListener(this);
    }

    /**
     * Implements the <code>CallChangeListener.callPeerRemoved</code> method.
     *
     * @param evt the <code>CallPeerEvent</code> that has been triggered
     */
    public void callPeerRemoved(CallPeerEvent evt) {
        CallPeer peer = evt.getSourceCallPeer();
        if (peer == null)
            return;

        peer.removeCallPeerListener(this);
        peer.removeCallPeerSecurityListener(this);
        peer.addCallPeerConferenceListener(this);
    }

    /**
     * {@inheritDoc}
     *
     * Not used.
     */
    public void callStateChanged(CallChangeEvent evt) {
    }

    /**
     * {@inheritDoc}
     *
     * Not used.
     */
    public void conferenceFocusChanged(CallPeerConferenceEvent evt) {
    }

    /**
     * Indicates that the given conference member has been added to the given peer.
     *
     * @param conferenceEvent the event
     */
    public void conferenceMemberAdded(CallPeerConferenceEvent conferenceEvent) {
        try {
            CallPeer peer = conferenceEvent.getConferenceMember().getConferenceFocusCallPeer();
            if (peer.getConferenceMemberCount() > 0) {
                CallPeerSecurityStatusEvent securityEvent = peer.getCurrentSecuritySettings();
                if (securityEvent instanceof CallPeerSecurityOnEvent)
                    fireNotification(CALL_SECURITY_ON);
            }
        } catch (Throwable t) {
            if (t instanceof ThreadDeath)
                throw (ThreadDeath) t;
            else
                Timber.e(t, "Error notifying for secured call member");
        }
    }

    /**
     * {@inheritDoc}
     *
     * Not used.
     */
    public void conferenceMemberErrorReceived(CallPeerConferenceEvent evt) {
    }

    /**
     * {@inheritDoc}
     *
     * Not used.
     */
    public void conferenceMemberRemoved(CallPeerConferenceEvent evt) {
    }

    /**
     * {@inheritDoc}
     *
     * Not used.
     */
    public void fileTransferCreated(FileTransferCreatedEvent evt) {
    }

    /**
     * {@inheritDoc}
     *
     * Not used.
     */
    public void fileTransferRequestCanceled(FileTransferRequestEvent evt) {
    }

    /**
     * When a request has been received we show a notification.
     *
     * @param event <code>FileTransferRequestEvent</code>
     *
     * @see ScFileTransferListener#fileTransferRequestReceived(FileTransferRequestEvent)
     */
    public void fileTransferRequestReceived(FileTransferRequestEvent event) {
        try {
            IncomingFileTransferRequest request = event.getRequest();
            String message = request.getFileName() + "  (size: " + request.getFileSize() + ")";
            Contact sourceContact = request.getSender();

            // Fire notification
            String title = aTalkApp.getResString(R.string.file_receive_from,
                    sourceContact.getDisplayName());
            fireChatNotification(sourceContact, INCOMING_FILE, title, message, request.getId());
        } catch (Throwable t) {
            Timber.e(t, "Error notifying for file transfer request received");
        }
    }

    /**
     * {@inheritDoc}
     *
     * Not used.
     */
    public void fileTransferRequestRejected(FileTransferRequestEvent evt) {
    }

    /**
     * Adds all listeners related to the given protocol provider.
     *
     * @param protocolProvider the <code>ProtocolProviderService</code>
     */
    private void handleProviderAdded(ProtocolProviderService protocolProvider) {
        if (!protocolProvider.getAccountID().isEnabled())
            return;

        Map<String, OperationSet> supportedOperationSets = protocolProvider.getSupportedOperationSets();

        // Obtain the basic instant messaging operation set.
        String imOpSetClassName = OperationSetBasicInstantMessaging.class.getName();
        if (supportedOperationSets.containsKey(imOpSetClassName)) {
            // Add to all instant messaging operation sets the Message listener which handles all received messages.
            OperationSetBasicInstantMessaging im
                    = (OperationSetBasicInstantMessaging) supportedOperationSets.get(imOpSetClassName);
            if (im != null)
                im.addMessageListener(this);
        }

        // Obtain the sms messaging operation set.
        String smsOpSetClassName = OperationSetSmsMessaging.class.getName();
        if (supportedOperationSets.containsKey(smsOpSetClassName)) {
            OperationSetSmsMessaging sms = (OperationSetSmsMessaging) supportedOperationSets.get(smsOpSetClassName);
            if (sms != null)
                sms.addMessageListener(this);
        }

        // Obtain the chat state notifications operation set.
        String tnOpSetClassName = OperationSetChatStateNotifications.class.getName();
        if (supportedOperationSets.containsKey(tnOpSetClassName)) {
            OperationSetChatStateNotifications tn
                    = (OperationSetChatStateNotifications) supportedOperationSets.get(tnOpSetClassName);

            // Add to all chat state notification operation sets the Message listener implemented in
            // the ContactListPanel, which handles all received messages.
            if (tn != null)
                tn.addChatStateNotificationsListener(this);
        }

        // Obtain file transfer operation set.
        OperationSetFileTransfer fileTransferOpSet = protocolProvider.getOperationSet(OperationSetFileTransfer.class);
        if (fileTransferOpSet != null) {
            fileTransferOpSet.addFileTransferListener(this);
        }

        // Obtain the multi user chat operation set & Manager.
        AppUIServiceImpl uiService = AppGUIActivator.getUIService();
        if (uiService != null) {
            ConferenceChatManager conferenceChatManager = uiService.getConferenceChatManager();

            OperationSetMultiUserChat multiChatOpSet = protocolProvider.getOperationSet(OperationSetMultiUserChat.class);
            if (multiChatOpSet != null) {
                multiChatOpSet.addPresenceListener(this);
                multiChatOpSet.addInvitationListener(conferenceChatManager);
                multiChatOpSet.addPresenceListener(conferenceChatManager);
            }

            // Obtain the ad-hoc multi user chat operation set.
            OperationSetAdHocMultiUserChat adHocMultiChatOpSet
                    = protocolProvider.getOperationSet(OperationSetAdHocMultiUserChat.class);
            if (adHocMultiChatOpSet != null) {
                adHocMultiChatOpSet.addPresenceListener(this);
                adHocMultiChatOpSet.addInvitationListener(conferenceChatManager);
                adHocMultiChatOpSet.addPresenceListener(conferenceChatManager);
            }
        }

        // Obtain the basic telephony operation set.
        OperationSetBasicTelephony<?> basicTelephonyOpSet = protocolProvider.getOperationSet(OperationSetBasicTelephony.class);
        if (basicTelephonyOpSet != null) {
            basicTelephonyOpSet.addCallListener(this);
        }
    }

    /**
     * Removes all listeners related to the given protocol provider.
     *
     * @param protocolProvider the <code>ProtocolProviderService</code>
     */
    private void handleProviderRemoved(ProtocolProviderService protocolProvider) {
        Map<String, OperationSet> supportedOperationSets = protocolProvider.getSupportedOperationSets();

        // Obtain the basic instant messaging operation set.
        String imOpSetClassName = OperationSetBasicInstantMessaging.class.getName();
        if (supportedOperationSets.containsKey(imOpSetClassName)) {
            OperationSetBasicInstantMessaging im
                    = (OperationSetBasicInstantMessaging) supportedOperationSets.get(imOpSetClassName);

            // Add to all instant messaging operation sets the Message listener which handles all received messages.
            if (im != null)
                im.removeMessageListener(this);
        }

        // Obtain the chat state notifications operation set.
        String tnOpSetClassName = OperationSetChatStateNotifications.class.getName();
        if (supportedOperationSets.containsKey(tnOpSetClassName)) {
            OperationSetChatStateNotifications tn
                    = (OperationSetChatStateNotifications) supportedOperationSets.get(tnOpSetClassName);

            // Add to all chat state notification operation sets the Message listener implemented in
            // the ContactListPanel, which handles all received messages.
            if (tn != null)
                tn.removeChatStateNotificationsListener(this);
        }

        // Obtain file transfer operation set.
        OperationSetFileTransfer fileTransferOpSet = protocolProvider.getOperationSet(OperationSetFileTransfer.class);
        if (fileTransferOpSet != null) {
            fileTransferOpSet.removeFileTransferListener(this);
        }

        // Obtain the multi user chat operation set & Manager.
        AppUIServiceImpl uiService = AppGUIActivator.getUIService();
        ConferenceChatManager conferenceChatManager = uiService.getConferenceChatManager();

        OperationSetMultiUserChat multiChatOpSet = protocolProvider.getOperationSet(OperationSetMultiUserChat.class);
        if (multiChatOpSet != null) {
            multiChatOpSet.removePresenceListener(this);
            multiChatOpSet.removeInvitationListener(conferenceChatManager);
            multiChatOpSet.removePresenceListener(conferenceChatManager);
        }

        OperationSetAdHocMultiUserChat multiAdHocChatOpSet
                = protocolProvider.getOperationSet(OperationSetAdHocMultiUserChat.class);
        if (multiAdHocChatOpSet != null) {
            multiAdHocChatOpSet.removePresenceListener(this);
            multiAdHocChatOpSet.removeInvitationListener(conferenceChatManager);
            multiAdHocChatOpSet.removePresenceListener(conferenceChatManager);
        }

        // Obtain the basic telephony operation set.
        OperationSetBasicTelephony<?> basicTelephonyOpSet
                = protocolProvider.getOperationSet(OperationSetBasicTelephony.class);
        if (basicTelephonyOpSet != null) {
            basicTelephonyOpSet.removeCallListener(this);
        }

    }

    /**
     * Implements CallListener.incomingCallReceived. Upon received a call, plays the phone ring tone to the user
     * and gathers caller information that may be used by a user-specified command (incomingCall event trigger).
     *
     * @param evt the <code>CallEvent</code>
     */
    public void incomingCallReceived(CallEvent evt) {
        try {
            Map<String, String> peerInfo = new HashMap<>();

            Call call = evt.getSourceCall();
            CallPeer peer = call.getCallPeers().next();
            String peerName = peer.getDisplayName();

            peerInfo.put("caller.uri", peer.getURI());
            peerInfo.put("caller.address", peer.getAddress());
            peerInfo.put("caller.name", peerName);
            peerInfo.put("caller.id", peer.getPeerID());

            /*
             * The loopCondition will stay with the notification sound until the latter is stopped.
             * If by any chance the sound fails to stop by the time the call is no longer referenced, do try
             * to stop it then. That's why the loopCondition will weakly reference the call.
             */
            final WeakReference<Call> weakCall = new WeakReference<>(call);

            NotificationData notification = fireNotification(INCOMING_CALL, "",
                    aTalkApp.getResString(R.string.call_incoming, peerName), peerInfo, () -> {
                        Call call1 = weakCall.get();
                        if (call1 == null)
                            return false;

                        /*
                         * INCOMING_CALL should be played for a Call only while there is a
                         * CallPeer in the INCOMING_CALL state.
                         */
                        Iterator<? extends CallPeer> peerIter = call1.getCallPeers();
                        boolean loop = false;
                        while (peerIter.hasNext()) {
                            CallPeer peer1 = peerIter.next();
                            if (CallPeerState.INCOMING_CALL.equals(peer1.getState())) {
                                loop = true;
                                break;
                            }
                        }
                        return loop;
                    });

            if (notification != null)
                callNotifications.put(call, notification);

            call.addCallChangeListener(this);
            peer.addCallPeerListener(this);
            peer.addCallPeerSecurityListener(this);
            peer.addCallPeerConferenceListener(this);
        } catch (Throwable t) {
            if (t instanceof ThreadDeath)
                throw (ThreadDeath) t;
            else {
                Timber.e(t, "An error occurred while trying to notify about an incoming call");
            }
        }
    }

    /**
     * Initialize, register default notifications and start listening for new protocols or removed
     * one and find any that are already registered.
     */
    void init() {
        registerDefaultNotifications();
        // listens for new protocols
        NotificationWiringActivator.bundleContext.addServiceListener(this);

        // enumerate currently registered protocols
        for (ProtocolProviderService pp : getProtocolProviders()) {
            handleProviderAdded(pp);
        }

        MediaService mediaServiceImpl = NotificationWiringActivator.getMediaService();
        if (mediaServiceImpl == null) {
            Timber.w("Media Service record listener init failed - jnlibffmpeg failed to load?");
        }
        else
            mediaServiceImpl.addRecorderListener(this);
    }

    /**
     * Checks if the contained call is a conference call.
     *
     * @param call the call to check
     *
     * @return {@code true} if the contained <code>Call</code> is a conference call, otherwise returns {@code false}.
     */
    public boolean isConference(Call call) {
        // If we're the focus of the conference.
        if (call.isConferenceFocus())
            return true;

        // If one of our peers is a conference focus, we're in a conference call.
        Iterator<? extends CallPeer> callPeers = call.getCallPeers();

        while (callPeers.hasNext()) {
            CallPeer callPeer = callPeers.next();
            if (callPeer.isConferenceFocus())
                return true;
        }

        // the call can have two peers at the same time and there is no one is conference focus.
        // This is situation when some one has made an attended transfer and has transferred us. We
        // have one call with two peers the one we are talking to and the one we have been
        // transferred to. And the first one is been hangup and so the call passes through
        // conference call for a moment and than go again to one to one call.
        return call.getCallPeerCount() > 1;
    }

    /**
     * Implements the <code>LocalUserAdHocChatRoomPresenceListener.localUserPresenceChanged</code> method
     *
     * @param evt the <code>LocalUserAdHocChatRoomPresenceChangeEvent</code> that notified us of a presence change
     */
    public void localUserAdHocPresenceChanged(LocalUserAdHocChatRoomPresenceChangeEvent evt) {
        String eventType = evt.getEventType();
        if (LocalUserAdHocChatRoomPresenceChangeEvent.LOCAL_USER_JOINED.equals(eventType)) {
            evt.getAdHocChatRoom().addMessageListener(this);
        }
        else if (LocalUserAdHocChatRoomPresenceChangeEvent.LOCAL_USER_LEFT.equals(eventType)
                || LocalUserAdHocChatRoomPresenceChangeEvent.LOCAL_USER_DROPPED.equals(eventType)) {
            evt.getAdHocChatRoom().removeMessageListener(this);
        }
    }

    /**
     * cmeng: should remove this from here
     * Implements the <code>LocalUserChatRoomPresenceListener.localUserPresenceChanged</code> method.
     *
     * @param evt the <code>LocalUserChatRoomPresenceChangeEvent</code> that notified us
     */
    public void localUserPresenceChanged(LocalUserChatRoomPresenceChangeEvent evt) {
        ChatRoom sourceChatRoom = evt.getChatRoom();
        String eventType = evt.getEventType();

        if (LocalUserChatRoomPresenceChangeEvent.LOCAL_USER_JOINED.equals(eventType)) {
            sourceChatRoom.addMessageListener(this);
        }
        else if (LocalUserChatRoomPresenceChangeEvent.LOCAL_USER_LEFT.equals(eventType)
                || LocalUserChatRoomPresenceChangeEvent.LOCAL_USER_KICKED.equals(eventType)
                || LocalUserChatRoomPresenceChangeEvent.LOCAL_USER_DROPPED.equals(eventType)) {
            sourceChatRoom.removeMessageListener(this);
        }
    }

    /**
     * {@inheritDoc}
     *
     * Not used.
     */
    public void messageDelivered(AdHocChatRoomMessageDeliveredEvent evt) {
    }

    /**
     * {@inheritDoc}
     *
     * Not used.
     */
    public void messageDelivered(ChatRoomMessageDeliveredEvent evt) {
    }

    /**
     * {@inheritDoc}
     *
     * Not used
     */
    public void messageDelivered(MessageDeliveredEvent evt) {
    }

    /**
     * {@inheritDoc}
     *
     * Not used.
     */
    public void messageDeliveryFailed(AdHocChatRoomMessageDeliveryFailedEvent evt) {
    }

    /**
     * {@inheritDoc}
     *
     * Not used.
     */
    public void messageDeliveryFailed(ChatRoomMessageDeliveryFailedEvent evt) {
    }

    /**
     * {@inheritDoc}
     *
     * Not used.
     */
    public void messageDeliveryFailed(MessageDeliveryFailedEvent evt) {
    }

    /**
     * Implements the <code>AdHocChatRoomMessageListener.messageReceived</code> method. <br>
     *
     * @param evt the <code>AdHocChatRoomMessageReceivedEvent</code> that notified us
     */
    public void messageReceived(AdHocChatRoomMessageReceivedEvent evt) {
        // Fire notification as INCOMING_FILE is found
        AdHocChatRoom chatRoom = evt.getSourceChatRoom();
        String sourceParticipant = evt.getSourceChatRoomParticipant().getDisplayName();
        final IMessage message = evt.getMessage();
        String msgBody = message.getContent();
        String msgUid = message.getMessageUID();

        if (ChatMessage.MESSAGE_HTTP_FILE_DOWNLOAD == evt.getEventType()) {
            String filePath = msgBody.split("#")[0];
            String fileName = filePath.substring(filePath.lastIndexOf('/') + 1);

            String title = aTalkApp.getResString(R.string.file_receive_from, sourceParticipant);
            fireChatNotification(chatRoom, INCOMING_FILE, title, fileName, msgUid);
        }
        else {
            boolean fireChatNotification;
            String nickname = chatRoom.getName();

            fireChatNotification = (nickname == null) || msgBody.toLowerCase().contains(nickname.toLowerCase());
            if (fireChatNotification) {
                String title = aTalkApp.getResString(R.string.message_received, sourceParticipant);
                if (!(IMessage.ENCODE_HTML == evt.getMessage().getMimeType())) {
                    msgBody = StringEscapeUtils.escapeHtml4(msgBody);
                }
                fireChatNotification(chatRoom, INCOMING_MESSAGE, title, msgBody, evt.getMessage().getMessageUID());
            }
        }
    }

    /**
     * Implements the <code>ChatRoomMessageListener.messageReceived</code> method. <br>
     * Obtains the corresponding <code>ChatPanel</code> and process the message there.
     *
     * @param evt the <code>ChatRoomMessageReceivedEvent</code> that notified us that a message has been received
     */
    public void messageReceived(ChatRoomMessageReceivedEvent evt) {
        // Fire notification as INCOMING_FILE is found
        ChatRoom chatRoom = evt.getSourceChatRoom();
        String nickName = evt.getSourceChatRoomMember().getNickName();  // sender
        final IMessage message = evt.getMessage();
        String msgBody = message.getContent();
        String msgUid = message.getMessageUID();

        if (ChatMessage.MESSAGE_HTTP_FILE_DOWNLOAD == evt.getEventType()) {
            String filePath = msgBody.split("#")[0];
            String fileName = filePath.substring(filePath.lastIndexOf('/') + 1);

            String title = aTalkApp.getResString(R.string.file_receive_from, nickName);
            fireChatNotification(chatRoom, INCOMING_FILE, title, fileName, msgUid);
        }
        else {
            boolean fireChatNotification;

            /*
             * It is uncommon for IRC clients to display popup notifications for messages which
             * are sent to public channels and which do not mention the nickname of the local user.
             */
            if (chatRoom.isSystem() || isPrivate(chatRoom) || (msgBody == null))
                fireChatNotification = true;
            else {
                fireChatNotification = (chatRoom.getUserNickname() != null);  // recipient
            }

            if (fireChatNotification) {
                // Block notification event if isHistoryMessage() and from autoJoined chatRoom
                if (!(evt.isHistoryMessage() && evt.isAutoJoin())) {
                    String title = aTalkApp.getResString(R.string.message_received, nickName);
                    // cmeng - extract only the msg body for notification display
                    if (!(IMessage.ENCODE_HTML == message.getMimeType())) {
                        msgBody = StringEscapeUtils.escapeHtml4(msgBody);
                    }
                    fireChatNotification(chatRoom, INCOMING_MESSAGE, title, msgBody, msgUid);
                }

                // update unread count for fired notification
                // Must pre-stored in ChatRoomWrapper attribute as crlf is null when aTalk is closed
                MUCServiceImpl mucService = MUCActivator.getMUCService();
                ChatRoomWrapper crWrapper = mucService.getChatRoomWrapperByChatRoom(chatRoom, false);
                if (crWrapper != null) {
                    int unreadCount = crWrapper.getUnreadCount() + 1;
                    crWrapper.setUnreadCount(unreadCount);

                    Fragment crlf = aTalk.getFragment(aTalk.CRL_FRAGMENT);
                    if (crlf instanceof ChatRoomListFragment) {
                        ((ChatRoomListFragment) crlf).updateUnreadCount(crWrapper);
                    }
                }
            }
        }
    }

    /**
     * Fired on new messages.
     *
     * @param evt the <code>MessageReceivedEvent</code> containing details on the received message
     */
    public void messageReceived(MessageReceivedEvent evt) {
        // Fire notification as INCOMING_FILE is found
        Contact contact = evt.getSourceContact();
        final IMessage message = evt.getSourceMessage();
        String msgBody = message.getContent();
        String msgUid = message.getMessageUID();

        if (ChatMessage.MESSAGE_HTTP_FILE_DOWNLOAD == evt.getEventType()) {
            String filePath = msgBody.split("#")[0];
            String fileName = filePath.substring(filePath.lastIndexOf('/') + 1);

            String title = aTalkApp.getResString(R.string.file_receive_from, contact.getAddress());
            fireChatNotification(contact, INCOMING_FILE, title, fileName, msgUid);
        }
        else {
            // Fire as message notification
            String title = aTalkApp.getResString(R.string.message_received, contact.getAddress());

            // cmeng - extract only the msg body for notification display
            if (!(IMessage.ENCODE_HTML == message.getMimeType())) {
                msgBody = StringEscapeUtils.escapeHtml4(message.getContent());
            }
            fireChatNotification(contact, INCOMING_MESSAGE, title, msgBody, msgUid);
        }

        // update unread count for fired notification.
        updateUnreadCount(contact);
    }

    /**
     * Update message unread count for the actual recipient (contact). The value must only
     * pre-store in metaContact unreadCount attribute, as clf is null when aTalk is closed
     * Note: Carbon copy message does not trigger messageReceived().
     *
     * @param contact the message recipient to which the unread count is to be updated
     */
    public static void updateUnreadCount(Contact contact) {
        MetaContact metaContact = AppGUIActivator.getContactListService().findMetaContactByContact(contact);
        if (metaContact != null) {
            int unreadCount = metaContact.getUnreadCount() + 1;
            metaContact.setUnreadCount(unreadCount);

            Fragment clf = aTalk.getFragment(aTalk.CL_FRAGMENT);
            if (clf instanceof ContactListFragment) {
                ((ContactListFragment) clf).updateUnreadCount(metaContact);
            }
        }
    }

    /**
     * Do nothing. Implements CallListener.outGoingCallCreated.
     *
     * @param event the <code>CallEvent</code>
     */
    public void outgoingCallCreated(CallEvent event) {
        Call call = event.getSourceCall();
        call.addCallChangeListener(this);

        if (call.getCallPeers().hasNext()) {
            CallPeer peer = call.getCallPeers().next();
            peer.addCallPeerListener(this);
            peer.addCallPeerSecurityListener(this);
            peer.addCallPeerConferenceListener(this);
        }
    }

    /**
     * {@inheritDoc}
     *
     * Not used.
     */
    public void peerAddressChanged(CallPeerChangeEvent evt) {
    }

    /**
     * {@inheritDoc}
     *
     * Not used.
     */
    public void peerDisplayNameChanged(CallPeerChangeEvent evt) {
    }

    /**
     * {@inheritDoc}
     *
     * Not used.
     */
    public void peerImageChanged(CallPeerChangeEvent evt) {
    }

    /**
     * Fired when peer's state has changed
     *
     * @param evt fired CallPeerEvent
     */
    public void peerStateChanged(CallPeerChangeEvent evt) {
        try {
            CallPeer peer = evt.getSourceCallPeer();
            Call call = peer.getCall();
            CallPeerState newState = (CallPeerState) evt.getNewValue();
            CallPeerState oldState = (CallPeerState) evt.getOldValue();
            Timber.d("Peer State Changed to %s", newState);

            // Play the dialing audio when in connecting and initiating call state.
            // Stop the dialing audio when we enter any other state.
            if ((newState == CallPeerState.INITIATING_CALL)
                    || (newState == CallPeerState.CONNECTING)) {
                /*
                 * The loopCondition will stay with the notification sound until the latter is being stopped.
                 * If by any chance the sound fails to stop by the time the peer is no longer referenced,
                 * do try to stop it then. That's why the loopCondition will weakly reference the peer.
                 */
                final WeakReference<CallPeer> weakPeer = new WeakReference<>(peer);

                /* We want to play the dialing once for multiple CallPeers. */
                if (shouldPlayDialingSound(weakPeer)) {
                    NotificationData notification = fireNotification(DIALING,
                            () -> shouldPlayDialingSound(weakPeer));

                    if (notification != null)
                        callNotifications.put(call, notification);
                }
            }
            else {
                NotificationData notification = callNotifications.get(call);
                if (notification != null)
                    stopSound(notification);
            }

            // If we were already in state of CONNECTING_WITH_EARLY_MEDIA, then the server has already
            // taking care of playing the notification, so we don't need to fire a notification here.
            if (newState == CallPeerState.ALERTING_REMOTE_SIDE
                    && oldState != CallPeerState.CONNECTING_WITH_EARLY_MEDIA) {
                final WeakReference<CallPeer> weakPeer = new WeakReference<>(peer);
                NotificationData notification = fireNotification(OUTGOING_CALL, () -> {
                    CallPeer peer1 = weakPeer.get();
                    return (peer1 != null) && CallPeerState.ALERTING_REMOTE_SIDE.equals(peer1.getState());
                });

                if (notification != null)
                    callNotifications.put(call, notification);
            }
            else if (newState == CallPeerState.BUSY) {
                // We start the busy sound only if we're in a simple call.
                if (!isConference(call)) {
                    final WeakReference<CallPeer> weakPeer = new WeakReference<>(peer);
                    NotificationData notification = fireNotification(BUSY_CALL, () -> {
                        CallPeer peer12 = weakPeer.get();
                        return (peer12 != null) && CallPeerState.BUSY.equals(peer12.getState());
                    });
                    if (notification != null)
                        callNotifications.put(call, notification);
                }
            }
            else if ((newState == CallPeerState.DISCONNECTED) || (newState == CallPeerState.FAILED)) {
                fireNotification(HANG_UP);
            }
        } catch (Throwable t) {
            if (t instanceof ThreadDeath)
                throw (ThreadDeath) t;
            else {
                Timber.e(t, "An error occurred while trying to notify about a change in the state of a call peer.");
            }
        }
    }

    /**
     * {@inheritDoc}
     *
     * Not used.
     */
    public void peerTransportAddressChanged(CallPeerChangeEvent evt) {
    }

    /**
     * Notifies that a specific <code>Recorder</code> has stopped recording the media associated with
     * it.
     *
     * @param recorder the <code>Recorder</code> which has stopped recording its associated media
     */
    public void recorderStopped(Recorder recorder) {
        try {
            fireNotification(CALL_SAVED, SystrayService.NONE_MESSAGE_TYPE,
                    aTalkApp.getResString(R.string.callrecordingconfig_call_saved),
                    aTalkApp.getResString(R.string.callrecordingconfig_call_saved_to, recorder.getFilename()));
        } catch (Throwable t) {
            if (t instanceof ThreadDeath)
                throw (ThreadDeath) t;
            else {
                Timber.e(t, "An error occurred while trying to notify that the recording of a call has stopped.");
            }
        }
    }

    /**
     * Register all default notifications.
     */
    private void registerDefaultNotifications() {
        NotificationService notificationService = NotificationWiringActivator.getNotificationService();

        if (notificationService == null)
            return;

        // Register incoming message notifications.
        notificationService.registerDefaultNotificationForEvent(INCOMING_MESSAGE,
                NotificationAction.ACTION_POPUP_MESSAGE, null, null);
        notificationService.registerDefaultNotificationForEvent(INCOMING_MESSAGE,
                new SoundNotificationAction(SoundProperties.INCOMING_MESSAGE, -1,
                        true, false, false));

        // Register sound notification for incoming files.
        notificationService.registerDefaultNotificationForEvent(INCOMING_FILE,
                NotificationAction.ACTION_POPUP_MESSAGE, null, null);
        notificationService.registerDefaultNotificationForEvent(INCOMING_FILE,
                new SoundNotificationAction(SoundProperties.INCOMING_FILE, -1,
                        true, false, false));

        // Register incoming call notifications.
        notificationService.registerDefaultNotificationForEvent(INCOMING_CALL,
                NotificationAction.ACTION_POPUP_MESSAGE, null, null);
        SoundNotificationAction inCallSoundHandler = new SoundNotificationAction(
                SoundProperties.INCOMING_CALL, 2000, true, true, true);
        notificationService.registerDefaultNotificationForEvent(INCOMING_CALL, inCallSoundHandler);

        // Register outgoing call notifications.
        notificationService.registerDefaultNotificationForEvent(OUTGOING_CALL,
                new SoundNotificationAction(SoundProperties.OUTGOING_CALL, 3000, false, true, false));

        // Register busy call notifications.
        notificationService.registerDefaultNotificationForEvent(BUSY_CALL,
                new SoundNotificationAction(SoundProperties.BUSY, 1, false, true, false));

        // Register dial notifications.
        notificationService.registerDefaultNotificationForEvent(DIALING,
                new SoundNotificationAction(SoundProperties.DIALING, -1, false, true, false));

        // Register the hangup sound notification.
        notificationService.registerDefaultNotificationForEvent(HANG_UP,
                new SoundNotificationAction(SoundProperties.HANG_UP, -1, false, true, false));

        // Register proactive notifications.
        notificationService.registerDefaultNotificationForEvent(PROACTIVE_NOTIFICATION,
                NotificationAction.ACTION_POPUP_MESSAGE, null, null);

        // Register warning message notifications.
        notificationService.registerDefaultNotificationForEvent(SECURITY_MESSAGE,
                NotificationAction.ACTION_POPUP_MESSAGE, null, null);

        // Register sound notification for security state off during a call.
        notificationService.registerDefaultNotificationForEvent(CALL_SECURITY_ERROR,
                NotificationAction.ACTION_POPUP_MESSAGE, null, null);
        notificationService.registerDefaultNotificationForEvent(CALL_SECURITY_ERROR,
                new SoundNotificationAction(SoundProperties.CALL_SECURITY_ERROR, -1,
                        false, true, false));

        // Register sound notification for security state on during a call.
        notificationService.registerDefaultNotificationForEvent(CALL_SECURITY_ON,
                new SoundNotificationAction(SoundProperties.CALL_SECURITY_ON, -1,
                        false, true, false));

        // Register notification for saved calls.
        notificationService.registerDefaultNotificationForEvent(CALL_SAVED,
                NotificationAction.ACTION_POPUP_MESSAGE, null, null);
    }

    /**
     * Processes the received security message and security errors.
     *
     * @param evt the event we received
     */
    public void securityMessageReceived(CallPeerSecurityMessageEvent evt) {
        try {
            int messageTitleKey;
            // Android notification cannot support html tags
            String message = Html.fromHtml(evt.getI18nMessage(), Html.FROM_HTML_MODE_LEGACY).toString();

            switch (evt.getEventSeverity()) {
                // Don't play alert sound for Info or warning.
                case SrtpListener.INFORMATION:
                    messageTitleKey = R.string.security_info;
                    aTalkApp.showToastMessage(message);
                    return;

                case SrtpListener.WARNING:
                    messageTitleKey = R.string.security_warning;
                    break;

                // Security cannot be established! Play an alert sound and popup message
                case SrtpListener.SEVERE:
                case SrtpListener.ERROR:
                    messageTitleKey = R.string.security_error;
                    fireNotification(CALL_SECURITY_ERROR, SystrayService.WARNING_MESSAGE_TYPE,
                            aTalkApp.getResString(messageTitleKey), message);
                    return;

                default:
                    // Whatever other severity there is or will be, we do not know how to react to it yet.
                    messageTitleKey = -1;
            }

            if (messageTitleKey != -1) {
                fireNotification(SECURITY_MESSAGE, SystrayService.INFORMATION_MESSAGE_TYPE,
                        aTalkApp.getResString(messageTitleKey), message);
            }
        } catch (Throwable t) {
            if (t instanceof ThreadDeath)
                throw (ThreadDeath) t;
            else {
                Timber.e(t, "An error occurred while trying to notify about a security message");
            }
        }
    }

    /**
     * {@inheritDoc}
     *
     * Not used.
     */
    public void securityNegotiationStarted(CallPeerSecurityNegotiationStartedEvent evt) {
    }

    /**
     * {@inheritDoc}
     *
     * Not used.
     */
    public void securityOff(CallPeerSecurityOffEvent evt) {
    }

    /**
     * When a <code>securityOnEvent</code> is received.
     *
     * @param evt the event we received
     */
    public void securityOn(CallPeerSecurityOnEvent evt) {
        try {
            SrtpControl securityController = evt.getSecurityController();
            CallPeer peer = (CallPeer) evt.getSource();

            if (!securityController.requiresSecureSignalingTransport()
                    || peer.getProtocolProvider().isSignalingTransportSecure()) {
                fireNotification(CALL_SECURITY_ON);
            }
        } catch (Throwable t) {
            if (t instanceof ThreadDeath)
                throw (ThreadDeath) t;
            else {
                Timber.e(t, "An error occurred while trying to notify about a security-related event");
            }
        }
    }

    /**
     * {@inheritDoc}
     *
     * Not used.
     */
    public void securityTimeout(CallPeerSecurityTimeoutEvent evt) {
        Timber.w("Notification security timeout: %s", evt.getSessionType());
    }

    /**
     * Implements the <code>ServiceListener</code> method. Verifies whether the passed event concerns
     * a <code>ProtocolProviderService</code> and adds the corresponding listeners.
     *
     * @param event The <code>ServiceEvent</code> object.
     */
    public void serviceChanged(ServiceEvent event) {
        ServiceReference<?> serviceRef = event.getServiceReference();

        // if the event is caused by a bundle being stopped, we don't want to know
        if (serviceRef.getBundle().getState() == Bundle.STOPPING)
            return;

        Object service = NotificationWiringActivator.bundleContext.getService(serviceRef);

        // we don't care if the source service is not a protocol provider
        if (service instanceof ProtocolProviderService) {
            switch (event.getType()) {
                case ServiceEvent.REGISTERED:
                    handleProviderAdded((ProtocolProviderService) service);
                    break;
                case ServiceEvent.UNREGISTERING:
                    handleProviderRemoved((ProtocolProviderService) service);
                    break;
            }
        }
    }

    /**
     * Stops all sounds for the given event type.
     *
     * @param data the event type for which we should stop sounds. One of the static event types defined in this class.
     */
    private void stopSound(NotificationData data) {
        if (data == null)
            return;

        try {
            NotificationService notificationService = NotificationWiringActivator.getNotificationService();
            if (notificationService != null)
                notificationService.stopNotification(data);
        } finally {
            /*
             * The field callNotifications associates a Call with a NotificationData for the
             * purposes of the stopSound method so the stopSound method should dissociate them
             * upon stopping a specific NotificationData.
             */
            callNotifications.entrySet().removeIf(e -> data.equals(e.getValue()));
        }
    }

    /**
     * {@inheritDoc}
     *
     * Not used.
     */
    public void chatStateNotificationDeliveryFailed(ChatStateNotificationEvent evt) {
    }

    /**
     * Informs the user what is the chat state of his chat contacts.
     *
     * @param evt the event containing details on the chat state notification
     */
    public void chatStateNotificationReceived(ChatStateNotificationEvent evt) {
        try {
            // we don't care for proactive notifications, different than chat state sometimes after
            // closing chat we can see someone is composing, usually it's just server sanding that the
            // chat is inactive (ChatState.inactive)
            if (evt.getChatState() != ChatState.composing) {
                return;
            }

            // check whether the current chat window is showing the chat we received
            // a chat state info for; in such case don't show notifications
            Object chatDescriptor = evt.getChatDescriptor();
            UIService uiService = NotificationWiringActivator.getUIService();
            if (uiService != null) {
                Chat chat = uiService.getCurrentChat();
                if ((chat != null) && chat.isChatFocused()) {
                    ChatTransport chatTransport = ((ChatPanel) chat).getChatSession().getCurrentChatTransport();
                    Object descriptor = chatTransport.getDescriptor();
                    if (!chatDescriptor.equals(descriptor))
                        return;
                }
            }

            long currentTime = System.currentTimeMillis();
            String fromJid = evt.getMessage().getFrom().toString();
            if (!proactiveTimer.isEmpty()) {
                // first remove chatDescriptors that have been here longer than the timeout to avoid memory leaks
                Iterator<Map.Entry<Object, Long>> entries = proactiveTimer.entrySet().iterator();
                while (entries.hasNext()) {
                    Map.Entry<Object, Long> entry = entries.next();
                    Long lastNotificationDate = entry.getValue();

                    // The entry is outdated
                    if (lastNotificationDate + 30000 < currentTime) {
                        entries.remove();
                    }
                }

                // Now, check if the chatDescriptor is still in the map; We already notified the others about this
                if (proactiveTimer.containsKey(chatDescriptor)) {
                    return;
                }
            }

            proactiveTimer.put(chatDescriptor, currentTime);
            String chatState = aTalkApp.getResString(R.string.proactive_notification, evt.getChatState());
            fireChatNotification(chatDescriptor, PROACTIVE_NOTIFICATION, fromJid, chatState, null);
        } catch (Throwable t) {
            if (t instanceof ThreadDeath)
                throw (ThreadDeath) t;
            else {
                Timber.e(t, "An error occurred while handling a chat state notification.");
            }
        }
    }
}
