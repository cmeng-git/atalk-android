/*
 * aTalk, android VoIP and Instant Messaging client
 * Copyright 2014 Eng Chong Meng
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.atalk.android.gui.call;

import android.content.Context;
import android.content.Intent;

import net.java.sip.communicator.impl.protocol.jabber.OperationSetBasicTelephonyJabberImpl;
import net.java.sip.communicator.impl.protocol.jabber.ProtocolProviderServiceJabberImpl;
import net.java.sip.communicator.plugin.notificationwiring.NotificationManager;
import net.java.sip.communicator.plugin.notificationwiring.NotificationWiringActivator;
import net.java.sip.communicator.service.notification.NotificationData;
import net.java.sip.communicator.service.notification.NotificationService;
import net.java.sip.communicator.service.protocol.CallPeer;
import net.java.sip.communicator.service.systray.SystrayService;
import net.java.sip.communicator.util.GuiUtils;

import org.atalk.android.R;
import org.atalk.android.aTalkApp;
import org.atalk.impl.androidnotification.VibrateHandlerImpl;
import org.atalk.impl.androidtray.NotificationPopupHandler;
import org.atalk.util.MediaType;
import org.jivesoftware.smack.SmackException;
import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.packet.Message;
import org.jivesoftware.smack.packet.MessageBuilder;
import org.jivesoftware.smack.packet.StanzaBuilder;
import org.jivesoftware.smackx.avatar.AvatarManager;
import org.jivesoftware.smackx.jingle.JingleManager;
import org.jivesoftware.smackx.jingle.element.Jingle;
import org.jivesoftware.smackx.jingle_rtp.element.RtpDescription;
import org.jivesoftware.smackx.jinglemessage.JingleMessageListener;
import org.jivesoftware.smackx.jinglemessage.JingleMessageManager;
import org.jivesoftware.smackx.jinglemessage.JingleMessageType;
import org.jivesoftware.smackx.jinglemessage.element.JingleMessage;
import org.jxmpp.jid.FullJid;
import org.jxmpp.jid.Jid;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import timber.log.Timber;

/**
 * Handler for the received Jingle Message Listener events.
 * XEP-0353: Jingle Message Initiation 0.3 (2017-09-11)
 *
 * @author Eng Chong Meng
 */
public final class JingleMessageSessionImpl implements JingleMessageListener
{
    private static final Map<XMPPConnection, JingleMessageSessionImpl> INSTANCES = new WeakHashMap<>();

    private static final Map<XMPPConnection, OperationSetBasicTelephonyJabberImpl> jmStateListeners = new HashMap<>();
    private static JmEndListener jmEndListener;

    // Both the mConnection and mRemote references will get initialized to the current outgoing / incoming call.
    private static XMPPConnection mConnection;
    private static Jid mRemote;  // BareJid or FullJid pending on state change update

    // JingleMessageSession call sid.
    private static String mSid = null;

    private static boolean isVideoCall = false;

    // Should send retract to close the loop, when user ends the call and jingle session-initiate has not started
    private static boolean allowSendRetract = false;

    public static synchronized JingleMessageSessionImpl getInstanceFor(XMPPConnection connection)
    {
        JingleMessageSessionImpl jingleMessageSessionImpl = INSTANCES.get(connection);
        if (jingleMessageSessionImpl == null) {
            jingleMessageSessionImpl = new JingleMessageSessionImpl(connection);
            INSTANCES.put(connection, jingleMessageSessionImpl);
        }
        return jingleMessageSessionImpl;
    }

    private JingleMessageSessionImpl(XMPPConnection connection)
    {
        JingleMessageManager jingleMessageManager = JingleMessageManager.getInstanceFor(connection);
        jingleMessageManager.addIncomingListener(this);
    }

    //==================== Outgoing Call processes flow ====================//

    /**
     * Prepare and send the Jingle Message <propose/> to callee.
     *
     * @param connection XMPPConnection
     * @param remote the targeted contact (BareJid) to send the call propose
     * @param videoCall video call if true, audio call otherwise
     */
    public static void sendJingleMessagePropose(XMPPConnection connection, Jid remote, boolean videoCall)
    {
        mConnection = connection;
        mRemote = remote;
        isVideoCall = videoCall;
        allowSendRetract = true;

        String sid = JingleManager.randomId();
        String msgId = "jm-propose-" + sid;

        JingleMessage msgPropose = new JingleMessage(JingleMessage.ACTION_PROPOSE, sid);
        RtpDescription.Builder rtpBuilder = RtpDescription.getBuilder();
        rtpBuilder.setMedia("audio");
        msgPropose.addDescriptionExtension(rtpBuilder.build());

        // Add video description if true
        if (videoCall) {
            rtpBuilder = RtpDescription.getBuilder();
            rtpBuilder.setMedia("video");
            msgPropose.addDescriptionExtension(rtpBuilder.build());
        }

        MessageBuilder msgBuilder = StanzaBuilder.buildMessage(msgId)
                .ofType(Message.Type.chat)
                .from(connection.getUser())
                .to(mRemote.asBareJid())
                .setLanguage("us")
                .addExtension(msgPropose);

        try {
            startJMCallActivity(sid);
            connection.sendStanza(msgBuilder.build());
        } catch (SmackException.NotConnectedException | InterruptedException e) {
            Timber.e("Error in sending jingle message propose to: %s: %s", mRemote, e.getMessage());
        }
    }

    /**
     * Start JingleMessage Activity with UI allowing caller to retract call.
     *
     * @param sid the unique for Jingle Message / Jingle Session sid
     */
    private static void startJMCallActivity(String sid)
    {
        Context context = aTalkApp.getGlobalContext();
        Intent intent = new Intent(context, JingleMessageCallActivity.class);

        intent.putExtra(CallManager.CALL_SID, sid);
        intent.putExtra(CallManager.CALL_EVENT, NotificationManager.OUTGOING_CALL);

        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
    }

    /**
     * Call from JingleMessageManager with the received original message and Proceed JingleMessage.
     * The message is returned by the callee, proceed with the call session-initiate using the id as sid
     *
     * @param connection XMPPConnection
     * @param jingleMessage Proceed received
     * @param message the original received Jingle Message
     */
    @Override
    public void onJingleMessageProceed(XMPPConnection connection, JingleMessage jingleMessage, Message message)
    {
        String sid = jingleMessage.getId();
        mRemote = message.getFrom();

        Timber.d("Jingle Message proceed received");
        // notify all listeners in preparation for Jingle RTP session-accept; sid - must use the same;
        // and to make earlier registerJingleSessionHandler() with JingleManager
        notifyOnStateChange(connection, JingleMessageType.proceed, mRemote, sid);
        endJmCallProcess(R.string.service_gui_CALL_ANSWER, mRemote);

        OperationSetBasicTelephonyJabberImpl telephonyJabber = jmStateListeners.get(connection);
        if (telephonyJabber != null)
            AndroidCallUtil.createCall(aTalkApp.getGlobalContext(), telephonyJabber.getProtocolProvider(), mRemote, isVideoCall);
    }

    /**
     * Call from JingleMessageManager with the received original message and propose JingleMessage.
     * The Callee has rejected the call. So end the call in progress
     *
     * @param connection XMPPConnection
     * @param jingleMessage Reject received
     * @param message the original received Jingle Message
     */
    @Override
    public void onJingleMessageReject(XMPPConnection connection, JingleMessage jingleMessage, Message message)
    {
        String sid = jingleMessage.getId();
        mRemote = message.getFrom();
        allowSendRetract = false;

        notifyOnStateChange(connection, JingleMessageType.reject, mRemote, sid);
        endJmCallProcess(R.string.service_gui_CALL_REJECTED, mRemote);
    }

    /**
     * Prepare Jingle Message Retract and send it to the remote callee if call is retracted by caller.
     *
     * @param remote the remote callee
     * @param sid the intended Jingle Message call id
     */
    public static void sendJingleMessageRetract(Jid remote, String sid)
    {
        allowSendRetract = false;
        JingleMessage msgRetract = new JingleMessage(JingleMessage.ACTION_RETRACT, sid);
        MessageBuilder messageBuilder = StanzaBuilder.buildMessage()
                .ofType(Message.Type.chat)
                .from(remote.asBareJid())
                .to(mConnection.getUser());

        sendJingleMessage(mConnection, msgRetract, messageBuilder.build());
        endJmCallProcess(R.string.service_gui_CALL_RETRACTED, mConnection.getUser());
    }

    /**
     * Send Jingle Message Retract if the peer was the targeted remote;
     * Request from CallManager if Jingle RTP has yet to start.
     *
     * @param peer the remote call peer
     */
    public static void sendJingleMessageRetract(CallPeer peer)
    {
        Jid jid = peer.getPeerJid();
        if ((mRemote != null) && mRemote.isParentOf(jid) && allowSendRetract) {
            sendJingleMessageRetract(mRemote, peer.getCall().getCallId());
        }
    }

    //==================== Incoming Call processes flow ====================//

    /**
     * Call from JingleMessageManager with the received original message and Propose JingleMessage.
     *
     * @param connection XMPPConnection
     * @param jingleMessage propose received
     * @param message the original received Jingle Message
     */
    @Override
    public void onJingleMessagePropose(XMPPConnection connection, JingleMessage jingleMessage, Message message)
    {
        List<String> media = jingleMessage.getMedia();
        boolean isVideoCall = media.contains(MediaType.VIDEO.toString());
        String sid = jingleMessage.getId();

        mConnection = connection;
        mRemote = message.getFrom();

        notifyOnStateChange(connection, JingleMessageType.propose, mRemote, sid);
        // v3.0.5: always starts with heads-up notification for JingleMessage call propose
        AndroidCallListener.startIncomingCallNotification(mRemote, sid, SystrayService.JINGLE_MESSAGE_PROPOSE, isVideoCall);
    }

    /**
     * Starts the receive call UI when in legacy jingle call (not use)
     * v3.0.5: always starts with heads-up notification for JingleMessage call propose
     *
     * @param call the <code>Call</code> to be handled
     */
    private void startJingleMessageCallActivity(String sid)
    {
        Context context = aTalkApp.getGlobalContext();
        Intent intent = new Intent(context, JingleMessageCallActivity.class)
                .putExtra(CallManager.CALL_SID, sid)
                .putExtra(CallManager.CALL_EVENT, NotificationManager.INCOMING_CALL)
                // .putExtra(CallManager.CALL_AUTO_ANSWER, true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
    }

    /**
     * On user accepted call, send an "accept: message to own bareJid; server will then forward to all our resources.
     * Cancel vibrate in progress; The ending of ring tone is handled by the caller
     * i.e. NotificationPopupHandler.removeCallNotification(id);
     *
     * Note: the attached message is with to/from reversed for sendJingleMessage requirements
     *
     * @param sid the intended Jingle Message call id
     */
    public static void sendJingleAccept(String sid)
    {
        Jid local = mConnection.getUser();
        mSid = sid;

        JingleMessage msgAccept = new JingleMessage(JingleMessage.ACTION_ACCEPT, sid);
        MessageBuilder messageBuilder = StanzaBuilder.buildMessage()
                .ofType(Message.Type.chat)
                .from(local.asBareJid())   // the actual message send to
                .to(local);                // the actual message send from

        sendJingleMessage(mConnection, msgAccept, messageBuilder.build());
    }

    /**
     * Call from JingleMessageManager with the received original message and Accept JingleMessage.
     * The received "accept" message is forward by the server. Check to see if we are the original sender;
     * Request caller to proceed with the call if so
     *
     * @param connection XMPPConnection
     * @param jingleMessage Accept received from the server
     * @param message the original received Jingle Message
     */
    @Override
    public void onJingleMessageAccept(XMPPConnection connection, JingleMessage jingleMessage, Message message)
    {
        // Valid caller found, and we are the sender of the "accept" jingle message, then request sender to proceed
        Jid callee = message.getFrom();
        if (mRemote != null) {
            String sid = jingleMessage.getId();

            if (connection.getUser().equals(callee)) {
                // notify all listeners for session-accept; sid - must use the same;
                // and to make earlier registerJingleSessionHandler() with JingleManager
                notifyOnStateChange(connection, JingleMessageType.accept, mRemote, sid);
                message.setFrom(mRemote);  // message actual send to
                JingleMessage msgProceed = new JingleMessage(JingleMessage.ACTION_PROCEED, sid);
                sendJingleMessage(connection, msgProceed, message);
                aTalkApp.showToastMessage(R.string.service_gui_CONNECTING_ACCOUNT, mRemote);
            }
            else {
                // Dismiss notification if another user instance has accepted the call propose.
                NotificationPopupHandler.removeCallNotification(sid);
            }
            // Display to user who has accepted the call
            endJmCallProcess(R.string.service_gui_CALL_ANSWER, callee);
        }
    }

    /**
     * Local user has rejected the call; prepare Jingle Message reject and send it to the remote.
     *
     * @param sid the intended Jingle Message call id
     */
    public static void sendJingleMessageReject(String sid)
    {
        if (mRemote != null) {
            JingleMessage msgReject = new JingleMessage(JingleMessage.ACTION_REJECT, sid);
            MessageBuilder messageBuilder = StanzaBuilder.buildMessage()
                    .ofType(Message.Type.chat)
                    .from(mRemote.asBareJid())
                    .to(mConnection.getUser());

            sendJingleMessage(mConnection, msgReject, messageBuilder.build());
            endJmCallProcess(R.string.service_gui_CALL_REJECTED, mConnection.getUser());
        }
    }

    /**
     * Call from JingleMessageManager with the received original message and Retract JingleMessage.
     * i.e. when caller decides to abort the call. Send missed call notification.
     *
     * @param connection XMPPConnection
     * @param jingleMessage Retract received
     * @param message the original received Jingle Message
     */
    @Override
    public void onJingleMessageRetract(XMPPConnection connection, JingleMessage jingleMessage, Message message)
    {
        String sid = jingleMessage.getId();
        NotificationPopupHandler.removeCallNotification(sid);

        if (mRemote != null) {
            notifyOnStateChange(connection, JingleMessageType.retract, mRemote, sid);
            endJmCallProcess(R.string.service_gui_CALL_END, message.getFrom());
            onCallRetract(mRemote.asEntityFullJidIfPossible());
        }
    }

    private void onCallRetract(FullJid caller)
    {
        // fired a missed call notification
        Map<String, Object> extras = new HashMap<>();
        extras.put(NotificationData.POPUP_MESSAGE_HANDLER_TAG_EXTRA, caller);

        byte[] contactIcon = AvatarManager.getAvatarImageByJid(caller.asBareJid());
        String textMessage = caller.asBareJid() + " " + GuiUtils.formatDateTimeShort(new Date());

        NotificationService notificationService = NotificationWiringActivator.getNotificationService();
        notificationService.fireNotification(NotificationManager.MISSED_CALL, SystrayService.MISSED_CALL_MESSAGE_TYPE,
                aTalkApp.getResString(R.string.service_gui_CALL_MISSED_CALL), textMessage, contactIcon, extras);
    }

    //==================== Helper common utilities ====================//

    /**
     * Build the message from source and add Jingle Message attachment before sending.
     *
     * @param connection XMPPConnection
     * @param jingleMessage the extension element to be sent
     * @param message the source message for parameters extraction; to and from may have been modified by caller
     */
    private static void sendJingleMessage(XMPPConnection connection, JingleMessage jingleMessage, Message message)
    {
        MessageBuilder msgBuilder = StanzaBuilder.buildMessage(message.getStanzaId())
                .ofType(Message.Type.chat)
                .from(connection.getUser())
                .to(message.getFrom())
                .setLanguage(message.getLanguage())
                .addExtension(jingleMessage);

        try {
            connection.sendStanza(msgBuilder.build());
        } catch (SmackException.NotConnectedException | InterruptedException e) {
            Timber.e("Error in sending jingle message: %s: %s : %s", jingleMessage.getAction(),
                    jingleMessage.getId(), e.getMessage());
        }
    }

    /**
     * Check to see if the session-initiate is triggered via JingleMessage <code>accept</code>.
     *
     * @param jingleSI incoming Jingle session-initiate for verification
     * @return true if the call was via JingleMessage
     */
    public static boolean isJingleMessageAccept(Jingle jingleSI)
    {
        // see <a href="https://xmpp.org/extensions/xep-0166.html#def">XEP-0166 Jingle#7. Formal Definition</a>
        FullJid initiator = jingleSI.getInitiator();
        if (initiator == null) {
            // conversations excludes initiator attribute in session-initiate
            initiator = jingleSI.getFrom().asEntityFullJidIfPossible();
        }
        return initiator.equals(mRemote);
    }

    /**
     * Disable retract sending once Jingle session-initiate has started
     *
     * @param allow false to disable sending retract when user ends the call
     * {@link CallPeerJabberImpl#initiateSession(Iterable, String)}
     */
    public static void setAllowSendRetract(boolean allow)
    {
        allowSendRetract = allow;
    }

    /**
     * Get the current active remote in communication.
     *
     * @return Jid of the callee
     */
    public static Jid getRemote()
    {
        return mRemote;
    }

    /**
     * legacy call must check for correct sid before assumes JingleMessage session call
     */
    public static String getSid()
    {
        return mSid;
    }

    /**
     * This is called when the Jingle Message process cycle has ended i.e. accept, reject or retract etc.
     * Jingle message must stop both the RingTone looping and vibrator independent of Jingle call.
     *
     * @param id String id
     * @param arg arg for the string format
     */
    private static void endJmCallProcess(Integer id, Object... arg)
    {
        if (id != null) {
            aTalkApp.showToastMessage(id, arg);
        }

        new VibrateHandlerImpl().cancel();
        if (jmEndListener != null) {
            jmEndListener.onJmEndCallback();
            jmEndListener = null;
        }
    }

    /**
     * Add JmStateListener.
     *
     * @param basicTelephony OperationSetBasicTelephonyJabberImpl instance
     */
    public static void addJmStateListener(OperationSetBasicTelephonyJabberImpl basicTelephony)
    {
        ProtocolProviderServiceJabberImpl pps = basicTelephony.getProtocolProvider();
        jmStateListeners.put(pps.getConnection(), basicTelephony);
    }

    /**
     * Remove JmStateListener.
     *
     * @param basicTelephony OperationSetBasicTelephonyJabberImpl instance
     */
    public static void removeJmStateListener(OperationSetBasicTelephonyJabberImpl basicTelephony)
    {
        ProtocolProviderServiceJabberImpl pps = basicTelephony.getProtocolProvider();
        jmStateListeners.remove(pps.getConnection());
    }

    /**
     * Notify all the registered StateListeners on JingleMessage state change for taking action.
     *
     * @param connection XMPPConnection
     * @param type JingleMessageType enum
     * @param remote The remote caller/callee, should be a FullJid
     * @param sid The Jingle sessionId for session-initiate; must be from negotiated JingleMessage
     */
    public void notifyOnStateChange(XMPPConnection connection, JingleMessageType type, Jid remote, String sid)
    {
        OperationSetBasicTelephonyJabberImpl basicTelephone = jmStateListeners.get(connection);
        if (basicTelephone != null)
            basicTelephone.onJmStateChange(type, remote.asEntityFullJidIfPossible(), sid);
    }

    public interface JmStateListener
    {
        void onJmStateChange(JingleMessageType type, FullJid remote, String sid);
    }

    /**
     * Add FinishListener
     *
     * @param fl JmEndListener to finish the activity
     */
    public static void setJmEndListener(JmEndListener fl)
    {
        jmEndListener = fl;
    }

    public interface JmEndListener
    {
        void onJmEndCallback();
    }
}
