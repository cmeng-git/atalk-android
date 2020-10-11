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
import android.view.View;

import net.java.sip.communicator.impl.protocol.jabber.CallJabberImpl;
import net.java.sip.communicator.plugin.notificationwiring.NotificationManager;
import net.java.sip.communicator.plugin.notificationwiring.NotificationWiringActivator;
import net.java.sip.communicator.service.contactlist.MetaContact;
import net.java.sip.communicator.service.notification.NotificationData;
import net.java.sip.communicator.service.notification.NotificationService;
import net.java.sip.communicator.service.protocol.ProtocolProviderService;
import net.java.sip.communicator.service.systray.SystrayService;
import net.java.sip.communicator.util.GuiUtils;

import org.atalk.android.R;
import org.atalk.android.aTalkApp;
import org.atalk.impl.androidnotification.VibrateHandlerImpl;
import org.atalk.impl.androidtray.NotificationPopupHandler;
import org.atalk.util.MediaType;
import org.jivesoftware.smack.SmackException;
import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.packet.*;
import org.jivesoftware.smackx.avatar.AvatarManager;
import org.jivesoftware.smackx.jinglemessage.JingleMessageListener;
import org.jivesoftware.smackx.jinglemessage.JingleMessageManager;
import org.jivesoftware.smackx.jinglemessage.packet.JingleMessage;
import org.jxmpp.jid.Jid;
import org.jxmpp.util.XmppStringUtils;
import org.xmpp.extensions.jingle.RtpDescriptionExtension;
import org.xmpp.extensions.jingle.element.Jingle;

import java.util.*;
import java.util.concurrent.Callable;

import timber.log.Timber;

/**
 * Handler for the received Jingle Message Listener events.
 * XEP-0353: Jingle Message Initiation 0.3 (2017-09-11)
 *
 * @author Eng Chong Meng
 */
public final class JingleMessageHelper implements JingleMessageListener
{
    private static final Map<XMPPConnection, JingleMessageHelper> INSTANCES = new WeakHashMap<>();

    /**
     * A map of the JingleMessage (id == Jingle Sid) to FullJid/Jid: can be initiator or responder.
     * The entry must be removed after its final usage.
     */
    private static final Map<String, Jid> mJingleCalls = new HashMap<>();

    /**
     * A map of the JingleMessage id to XMPPConnection.The entry is removed after its final usage.
     */
    private static final Map<String, XMPPConnection> mConnections = new HashMap<>();

    private static final Map<XMPPConnection, ProtocolProviderService> mProviders = new HashMap<>();

    private static List<CallEndListener> callEndListeners = new ArrayList<>();

    private static boolean isVideoCall = false;

    public static synchronized JingleMessageHelper getInstanceFor(ProtocolProviderService pps)
    {
        XMPPConnection connection = pps.getConnection();

        JingleMessageHelper jingleMessageHelper = INSTANCES.get(connection);
        if (jingleMessageHelper == null) {
            jingleMessageHelper = new JingleMessageHelper(connection);
            INSTANCES.put(connection, jingleMessageHelper);
            mProviders.put(connection, pps);
        }
        return jingleMessageHelper;
    }

    /**
     * @param connection return an instance of the JingleMessagerHelper
     */
    private JingleMessageHelper(XMPPConnection connection)
    {
        JingleMessageManager jingleMessageManager = JingleMessageManager.getInstanceFor(connection);
        jingleMessageManager.addIncomingListener(this);
        callEndListeners.clear();
    }

    //==================== Outgoing Call processes flow ====================//

    /**
     * Prepare and send the Jingle Message <propose/> to callee
     *
     * @param pps ProtocolProviderService of the initiator
     * @param callee the targeted contact (BareJid) to send the call propose
     * @param videoCall video call if true, audio call otherwise
     * @see AndroidCallUtil#createCall(Context, MetaContact, boolean, View)
     */
    public static void createAndSendJingleMessagePropose(ProtocolProviderService pps, Jid callee, boolean videoCall)
    {
        XMPPConnection connection = pps.getConnection();
        mProviders.put(connection, pps);

        String id = Jingle.generateSid();
        String msgId = "jm-propose-" + id;
        mConnections.put(id, connection);
        mJingleCalls.put(id, callee);
        isVideoCall = videoCall;

        JingleMessage msgPropose = new JingleMessage(JingleMessage.ACTION_PROPOSE, id);

        RtpDescriptionExtension rtpDescriptionExtension = new RtpDescriptionExtension();
        rtpDescriptionExtension.setMedia("audio");
        msgPropose.addDescriptionExtension(rtpDescriptionExtension);

        if (videoCall) {
            rtpDescriptionExtension = new RtpDescriptionExtension();
            rtpDescriptionExtension.setMedia("video");
            msgPropose.addDescriptionExtension(rtpDescriptionExtension);
        }

        MessageBuilder msgBuilder = StanzaBuilder.buildMessage(msgId)
                .ofType(Message.Type.chat)
                .from(connection.getUser())
                .to(callee.asBareJid())
                .setLanguage("us")
                .addExtension(msgPropose);
        try {
            startJMActivity(id);
            connection.sendStanza(msgBuilder.build());
        } catch (SmackException.NotConnectedException | InterruptedException e) {
            Timber.e("Error in sending jingle message propose to: %s: %s", callee.toString(), e.getMessage());
        }
    }

    /**
     * Start JingleMessage Activity with UI allowing caller to retract call
     *
     * @param callee the callee Jid
     * @param id of Jingle Message
     */
    private static void startJMActivity(String id)
    {
        Context ctx = aTalkApp.getGlobalContext();
        Intent intent = new Intent(ctx, JingleMessageCallActivity.class);

        intent.putExtra(CallManager.CALL_SID, id);
        intent.putExtra(CallManager.CALL_EVENT, NotificationManager.OUTGOING_CALL);

        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        ctx.startActivity(intent);
    }

    /**
     * Call from JingleMessageManager with the received original message and Proceed JingleMessage;
     * The message is returned by the callee, proceed with the call session-initiate using the id as sid
     *
     * @param connection XMPPConnection
     * @param jingleMessage Proceed received
     * @param message the original received Jingle Message
     */
    @Override
    public void onJingleMessageProceed(XMPPConnection connection, JingleMessage jingleMessage, Message message)
    {
        String id = jingleMessage.getId();
        Jid callee = mJingleCalls.get(id);  // BareJid

        if (callee != null) {
            Jid callPeer = message.getFrom();
            if (callee.isParentOf(callPeer)) {
                Timber.d("Jingle Message proceed received");
                closeCallInitActivity();

                // pass the id to CallJabberImpl for session-initiate sid - must use the same
                CallJabberImpl.setJingleCallId(callPeer, id);

                ProtocolProviderService pps = mProviders.get(connection);
                AndroidCallUtil.createCall(aTalkApp.getGlobalContext(), pps, callPeer, isVideoCall);
            }
            else {
                Timber.w("Unknown callPeer '%s' accepting call meant for: %s", callPeer, callee);
            }
            cacheCleanUp(id);
        }
        else {
            Timber.w("JingleCalls contains no valid caller: %s (%s)", message.getFrom(), mJingleCalls.values());
        }
    }

    /**
     * Call from JingleMessageManager with the received original message and propose JingleMessage
     * The Callee has rejected the call. So end the call in progress
     *
     * @param connection XMPPConnection
     * @param jingleMessage Reject received
     * @param message the original received Jingle Message
     */
    @Override
    public void onJingleMessageReject(XMPPConnection connection, JingleMessage jingleMessage, Message message)
    {
        closeCallInitActivity();
        endJmCallProcess(jingleMessage.getId(), R.string.service_gui_CALL_REJECTED, message.getFrom());
    }

    /**
     * Prepare Jingle Message Retract and send it to the callee if call is retracted by caller
     *
     * @param id the intended Jingle Message call id
     */
    public static void sendJingleMessageRetract(String id)
    {
        XMPPConnection connection = mConnections.get(id);
        Jid callee = mJingleCalls.get(id);

        if ((connection != null) && (callee != null)) {
            JingleMessage msgRetract = new JingleMessage(JingleMessage.ACTION_RETRACT, id);
            MessageBuilder messageBuilder = StanzaBuilder.buildMessage()
                    .ofType(Message.Type.chat)
                    .from(callee.asBareJid())
                    .to(connection.getUser());

            sendJingleMessage(connection, msgRetract, messageBuilder.build());
            cacheCleanUp(id);
        }
    }

    //==================== Incoming Call processes flow ====================//

    /**
     * Call from JingleMessageManager with the received original message and Propose JingleMessage
     *
     * @param connection XMPPConnection
     * @param jingleMessage propse received
     * @param message the original received Jingle Message
     */
    @Override
    public void onJingleMessagePropose(XMPPConnection connection, JingleMessage jingleMessage, Message message)
    {
        List<String> media = jingleMessage.getMedia();
        isVideoCall = media.contains(MediaType.VIDEO.toString());

        String id = jingleMessage.getId();
        Jid caller = message.getFrom();
        mJingleCalls.put(id, caller);
        mConnections.put(id, connection);

        onCallProposed(caller, id);
    }

    /**
     * Set up the heads-up notification for user to accept or dismiss the call
     *
     * @param caller the caller who sends the Jingle Message Propose
     * @param id the Jingle Message call id
     */
    private void onCallProposed(Jid caller, final String id)
    {
        Map<String, Object> extras = new HashMap<>();
        extras.put(NotificationData.POPUP_MESSAGE_HANDLER_TAG_EXTRA, id);
        extras.put(NotificationData.SOUND_NOTIFICATION_HANDLER_LOOP_CONDITION_EXTRA,
                (Callable<Boolean>) () -> (NotificationPopupHandler.getCallNotificationId(id) != null));

        byte[] contactIcon = AvatarManager.getAvatarImageByJid(caller.asBareJid());
        String message = (isVideoCall ? "(vide): " : "(audio): ") + GuiUtils.formatDateTimeShort(new Date());

        NotificationService notificationService = NotificationWiringActivator.getNotificationService();
        notificationService.fireNotification(NotificationManager.INCOMING_CALL, SystrayService.JINGLE_MESSAGE_PROPOSE,
                aTalkApp.getResString(R.string.service_gui_CALL_INCOMING,
                        XmppStringUtils.parseLocalpart(caller.toString())), message, contactIcon, extras);
    }

    /**
     * On user accepted call, send an "accept: message to own bareJid; server will then forward to all our resources
     * Note: the attached message are with to/from reversed for sendJingleMessage requirements
     *
     * @param id the intended Jingle Message call id
     */
    public static void sendJingleAccept(String id)
    {
        XMPPConnection connection = mConnections.get(id);
        if (connection != null) {
            Jid callee = connection.getUser();

            if (callee != null) {
                JingleMessage msgAccept = new JingleMessage(JingleMessage.ACTION_ACCEPT, id);
                MessageBuilder messageBuilder = StanzaBuilder.buildMessage()
                        .ofType(Message.Type.chat)
                        .from(callee.asBareJid())   // actual message send to
                        .to(callee);                // actual message send from

                sendJingleMessage(connection, msgAccept, messageBuilder.build());
            }
        }
    }

    /**
     * Call from JingleMessageManager with the received original message and Accept JingleMessage
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
        String id = jingleMessage.getId();
        Jid caller = mJingleCalls.get(id);

        // Valid caller found, and we are the sender of the "accept" jingle message, then request sender to proceed
        if (caller != null) {
            if (connection.getUser().equals(message.getFrom())) {

                message.setFrom(caller);  // message actual send to
                JingleMessage msgProceed = new JingleMessage(JingleMessage.ACTION_PROCEED, id);
                sendJingleMessage(connection, msgProceed, message);
            }
            else {
                endJmCallProcess(jingleMessage.getId(), R.string.service_gui_CALL_ANSWER, message.getTo());
            }
        }
    }

    /**
     * Callee has rejected the call; prepare Jingle Message reject and send it to the caller
     *
     * @param id the intended Jingle Message call id
     */
    public static void sendJingleMessageReject(String id)
    {
        XMPPConnection connection = mConnections.get(id);
        Jid callee = mJingleCalls.get(id);

        if ((connection != null) && (callee != null)) {
            JingleMessage msgReject = new JingleMessage(JingleMessage.ACTION_REJECT, id);
            MessageBuilder messageBuilder = StanzaBuilder.buildMessage()
                    .ofType(Message.Type.chat)
                    .from(callee.asBareJid())
                    .to(connection.getUser());

            sendJingleMessage(connection, msgReject, messageBuilder.build());
            endJmCallProcess(id,null);
        }
    }

    /**
     * Call from JingleMessageManager with the received original message and Retract JingleMessage
     * i.e. when caller decides to abort the call. Send missed call notification.
     *
     * @param connection XMPPConnection
     * @param jingleMessage Retract received
     * @param message the original received Jingle Message
     */
    @Override
    public void onJingleMessageRetract(XMPPConnection connection, JingleMessage jingleMessage, Message message)
    {
        String id = jingleMessage.getId();
        NotificationPopupHandler.removeCallNotification(id);

        Jid caller = mJingleCalls.get(id);
        if (caller != null) {
            endJmCallProcess(jingleMessage.getId(), R.string.service_gui_CALL_END, message.getFrom());

            // fired a missed call notification
            Map<String, Object> extras = new HashMap<>();
            extras.put(NotificationData.POPUP_MESSAGE_HANDLER_TAG_EXTRA, caller);

            byte[] contactIcon = AvatarManager.getAvatarImageByJid(caller.asBareJid());
            String textMessage = caller.asBareJid() + " " + GuiUtils.formatDateTimeShort(new Date());

            NotificationService notificationService = NotificationWiringActivator.getNotificationService();
            notificationService.fireNotification(NotificationManager.MISSED_CALL, SystrayService.MISSED_CALL_MESSAGE_TYPE,
                    aTalkApp.getResString(R.string.service_gui_CALL_MISSED_CALL), textMessage, contactIcon, extras);
        }
    }

    //==================== Helper common utilities ====================//

    /**
     * Build the message from source and add Jingle Message attachment before sending
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
     * Check to see if the session-initiate is triggered via JingleMessage <accept/>
     *
     * @param jingleSI incoming Jingle session-initiate for verification
     * @return true if was call via JingleMessage
     */
    public static boolean isJingleMessageAccept(Jingle jingleSI)
    {
        String sid = jingleSI.getSid();

        Jid caller = mJingleCalls.get(sid);
        if (jingleSI.getInitiator().equals(caller)) {
            cacheCleanUp(sid);
            return true;
        }
        return false;
    }

    /**
     * Get the callee of a given sid
     *
     * @param sid incoming Jingle session-initiate id
     * @return true Jid of the callee
     */
    public static Jid getCallee(String sid)
    {
        return mJingleCalls.get(sid);
    }

    /**
     * This is called when the Jingle Message process cycle has ended i.e. accept, reject or retract.
     * Jingle message must stop both the RingTone looping and vibrator independent of Jingle call.
     *
     * @param sid The jingle Message id (== Jingle Sid)
     * @param id String id
     * @param arg arg for the string format
     */
    private static void endJmCallProcess(String sid, Integer id, Object... arg)
    {
        if (id != null)
            aTalkApp.showToastMessage(id, arg);

        new VibrateHandlerImpl().cancel();
        cacheCleanUp((sid));
    }

    /**
     * Cleanup the cache after last use
     *
     * @param id cache identification key
     */
    private static void cacheCleanUp(String id)
    {
        mJingleCalls.remove(id);
        mConnections.remove(id);
    }

    /**
     * Add FinishListener
     *
     * @param fl FinishListener to close activity
     */
    public static void addFinishListener(CallEndListener fl)
    {
        callEndListeners.add(fl);
    }

    public void closeCallInitActivity()
    {
        for (CallEndListener fl : callEndListeners) {
            fl.onRejectCallback();
        }
        callEndListeners.clear();
    }

    public interface CallEndListener
    {
        void onRejectCallback();
    }
}
