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

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.java.sip.communicator.impl.protocol.jabber.CallPeerJabberImpl;
import net.java.sip.communicator.impl.protocol.jabber.ProtocolProviderServiceJabberImpl;
import net.java.sip.communicator.plugin.notificationwiring.NotificationManager;
import net.java.sip.communicator.plugin.notificationwiring.NotificationWiringActivator;
import net.java.sip.communicator.service.notification.NotificationData;
import net.java.sip.communicator.service.notification.NotificationService;
import net.java.sip.communicator.service.protocol.Call;
import net.java.sip.communicator.service.protocol.CallPeer;
import net.java.sip.communicator.service.systray.SystrayService;
import net.java.sip.communicator.util.GuiUtils;

import org.atalk.android.R;
import org.atalk.android.aTalkApp;
import org.atalk.android.gui.aTalk;
import org.atalk.impl.appnotification.VibrateHandlerImpl;
import org.atalk.impl.appstray.NotificationPopupHandler;

import org.jivesoftware.smack.packet.Message;
import org.jivesoftware.smack.packet.NamedElement;
import org.jivesoftware.smack.packet.XmlElement;

import org.jivesoftware.smackx.avatar.AvatarManager;
import org.jivesoftware.smackx.jingle.element.JingleReason;
import org.jivesoftware.smackx.jingle_rtp.element.RtpDescription;
import org.jivesoftware.smackx.jinglemessage.JingleMessageListener;
import org.jivesoftware.smackx.jinglemessage.JingleMessageManager;
import org.jivesoftware.smackx.jinglemessage.JingleMessageState;
import org.jivesoftware.smackx.jinglemessage.element.JingleMessage;
import org.jivesoftware.smackx.jinglemessage.element.MigratedElement;
import org.jivesoftware.smackx.jinglemessage.element.TieBreakElement;

import org.jxmpp.jid.BareJid;
import org.jxmpp.jid.EntityFullJid;
import org.jxmpp.jid.FullJid;
import org.jxmpp.jid.Jid;

import timber.log.Timber;

/**
 * Handler for both Jingle Message incoming and outgoing events.
 * Secondary incoming call from new contact is separately handled till proceed, reject or retract.
 * <p>
 * XEP-0353: Jingle Message Initiation 0.8.0 (2026-02-19)
 *
 * @author Eng Chong Meng
 */
public final class JingleMessageSessionImpl implements JingleMessageListener {
    private static JingleMessageManager mJnManager;
    private static JmEndListener jmEndListener;

    private final ProtocolProviderServiceJabberImpl mPPS;
    private static EntityFullJid mUser;

    // Call Initiator = BareJid; Responder = FullJid
    private static Jid mRemote;

    // Secondary incoming call Jidfrom.
    private static Jid mRemote2;
    // The Jingle Message Session UUID.
    private static String mSid = null;
    // Secondary incoming call sid, mainly to used to check if call is JM call.
    private static String mSid2 = null;

    private final List<String> media = new ArrayList<>();

    private boolean isVideoCall = false;
    private static boolean inProgress = false;

    private static JingleMessageState mState = JingleMessageState.initial;

    public JingleMessageSessionImpl(ProtocolProviderServiceJabberImpl pps, JingleMessageManager jnManager) {
        mPPS = pps;
        mUser = pps.getOurJid();
        mJnManager = jnManager;
        mJnManager.addIncomingListener(this);
    }

    /**
     * Handle the JingleMessageSession request.
     *
     * @param jingleMessage Jingle Message request.
     * @param message the original received chat Message.
     */
    @Override
    public void handleJmSession(JingleMessage jingleMessage, Message message) {
        Jid from = message.getFrom();
        String sid = jingleMessage.getId();

        switch (jingleMessage.getAction()) {
        case JingleMessage.ACTION_PROPOSE:
            onJingleMessagePropose(jingleMessage, message);
            break;

        case JingleMessage.ACTION_RETRACT:
            onJingleMessageRetract(jingleMessage, message);
            break;

        case JingleMessage.ACTION_RINGING:
            onJingleMessageRinging(jingleMessage, message);
            break;

        case JingleMessage.ACTION_PROCEED:
            if (mRemote.isParentOf(from)) {
                onJingleMessageProceed(jingleMessage, message);
            }
            else {
                onCallProceed(jingleMessage, message);
            }
            break;

        case JingleMessage.ACTION_REJECT:
            if (mRemote.isParentOf(from)) {
                onJingleMessageReject(jingleMessage, message);
            }
            else {
                onCallRejected(from, sid);
            }
            break;

        case JingleMessage.ACTION_FINISH:
            onJingleMessageFinish(jingleMessage, message);
            break;
        }
    }

    //==================== Outgoing Call processes flow ====================//

    /**
     * Prepare and send the Jingle Message <propose/> to remote BareJid.
     *
     * @param recipient the targeted callee (BareJid) to send the call propose, so all instances get informed.
     * @param videoCall video call if true, audio call otherwise
     */
    public static void sendJingleMessagePropose(BareJid recipient, String sid, boolean videoCall) {
        // Do not send propose if the incoming process has already been processed.
        if (mSid != null && mState.equals(JingleMessageState.propose))
            return;

        // Save a copy of the remote and id for outgoing call.
        mRemote = recipient;
        mSid = sid;

        List<XmlElement> xmlElements = new ArrayList<>();
        RtpDescription.Builder rtpBuilder = RtpDescription.getBuilder();
        rtpBuilder.setMedia("audio");
        xmlElements.add(rtpBuilder.build());

        // Add video RtpDescription if true
        if (videoCall) {
            rtpBuilder = RtpDescription.getBuilder();
            rtpBuilder.setMedia("video");
            xmlElements.add(rtpBuilder.build());
        }

        startJMCallActivity(sid);
        mJnManager.sendJingleMessagePropose(recipient, sid, xmlElements);
        mState = JingleMessageState.propose;
    }

    /**
     * Request from JingleMessageCallActivity or CallManager if Jingle RTP has yet to start.
     * Prepare Jingle Message Retract and send it to the remote callee if call is retracted by caller.
     *
     * @param recipient the remote callee
     * @param sid the intended Jingle Message call id
     */
    public static void sendJingleMessageRetract(BareJid recipient, String sid) {
        JingleReason reason = new JingleReason(JingleReason.Reason.cancel, "Retracted", null);
        mJnManager.sendJingleMessageRetract(recipient, sid, reason, null);
        endJmCallProcess(sid, true, null);
    }

    /**
     * The Callee device has started ringing.
     *
     * @param jingleMessage Ringing received
     * @param message the original received Jingle Message
     */
    public void onJingleMessageRinging(JingleMessage jingleMessage, Message message) {
        mState = JingleMessageState.ringing;
    }

    /**
     * The message is received from the callee, to proceed with the call session-initiate using the id as sid.
     * Update mRemote to FullJid whom has accepted the call.
     *
     * @param jingleMessage Proceed received
     * @param message the original received Jingle Message
     */
    public void onJingleMessageProceed(JingleMessage jingleMessage, Message message) {
        mRemote = message.getFrom();
        String sid = jingleMessage.getId();

        Timber.d("Jingle Message proceed received");
        endJmCallProcess(sid, false, R.string.call_answered, mRemote);
        mState = JingleMessageState.proceed;
        inProgress = true;

        AppCallUtil.createCall(aTalkApp.getInstance(), mPPS, mRemote, isVideoCall);
    }

    /**
     * The Callee has rejected the call. So end the JM call in progress
     *
     * @param jingleMessage Reject received
     * @param message the original received Jingle Message
     */
    public void onJingleMessageReject(JingleMessage jingleMessage, Message message) {
        String sid = jingleMessage.getId();

        if (sid.equals(mSid)) {
            Jid jidFrom = message.getFrom();
            endJmCallProcess(sid, true, R.string.call_rejected, jidFrom);
        }
        else {
            // clear jm call UI a second time, in case it was not cleared during proposal retract tie-break due to race condition..
            endJmCallProcess(sid, false, null);
        }
    }

    //==================== Incoming Call processes flow ====================//

    /**
     * Call propose received; check for tie-break or call migration and take appropriate action.
     * If call is from another contact, proceed to take call via aTalk call waiting.
     *
     * @param jingleMessage propose received
     * @param message the original received Jingle Message
     */
    public void onJingleMessagePropose(JingleMessage jingleMessage, Message message) {
        String sid = jingleMessage.getId();
        Jid fromJid = message.getFrom();

        // Take action on new incoming call while another call is in progress e.g tie-break.
        if (mSid != null) {
            // Use this check to handle call migration handling.
            if (fromJid.asBareJid().isParentOf(mRemote)) {

                // Use this check to skip call migration handling i.e. handle via aTalk call hold.
                // if (mRemote.isParentOf(fromJid)) {
                if (!inProgress) {
                    JingleReason reason = new JingleReason(JingleReason.Reason.expired, "Tie-Break", null);

                    // Check sid preceding order.
                    // Note: Sending of retract and reject jingle message to remote contact cannot follow as specified in
                    // https://xmpp.org/extensions/xep-0353.html; else have problems if followed.
                    int result = sid.compareTo(mSid);
                    //  New sid preceding mSid: retract current call and proceed with the new incoming call notification.
                    if (result < 0) {
                        Timber.w("New call takes priority.");
                        mJnManager.sendJingleMessageRetract(fromJid.asBareJid(), mSid, reason, new TieBreakElement());
                        endJmCallProcess(mSid, true, R.string.call_retracted, mUser);
                    }
                    // Else just reject the new incoming call; skip showing incoming call notification.
                    else if (result > 0) {
                        mJnManager.sendJingleMessageReject(fromJid, sid, reason, new TieBreakElement());
                        return;
                    }
                    else {
                        // Both sid are equal.
                    }
                }
                // End current in progress call and proceed to migrating to new incoming call.
                else {
                    JingleReason reason = new JingleReason(JingleReason.Reason.expired, "Session migrated", null);
                    mJnManager.sendJingleMessageFinish(mRemote, mSid, reason, new MigratedElement(sid));
                    // Hangup the call in progress
                    onCallMigrated(mSid);

                    // End current call.
                    endJmCallProcess(mSid, true, null);

                    // Request remote to proceed with the call, without any UI display for user selection.
                    mJnManager.sendJingleMessageProceed(fromJid, sid);
                    mState = JingleMessageState.proceed;
                    mRemote = fromJid;  // update to new recipient.
                    mSid = sid;
                    inProgress = true;
                    return;
                }
            }
            // new call Propose from another contact, call waiting.
            else {
                // Propose from different remote and the call is in progress, then proceed to allow Jingle Call process to take appropriate actions.
                if (inProgress) {
                    mRemote2 = fromJid;
                    mSid2 = sid;
                    // Secondary call will force to audio call only.
                    AppCallListener.startIncomingCallNotification(fromJid, sid, SystrayService.JINGLE_MESSAGE_PROPOSE, false);
                    sendJingleMessageRinging(fromJid.asFullJidIfPossible(), sid);

                    return;
                }
            }
        }

        mRemote = fromJid;
        mSid = sid;

        media.clear();
        List<NamedElement> elements = jingleMessage.getElements(org.jivesoftware.smackx.jingle_rtp.element.RtpDescription.ELEMENT);
        if (elements != null) {
            for (NamedElement element : elements) {
                media.add(((RtpDescription) element).getMedia());
            }
        }
        isVideoCall = media.contains("video");

        // Check for resource permission before proceed, if mic permission is granted at a minimum
        Timber.d("Starting incoming call notification: %s", sid);
        if (aTalk.isMediaCallAllowed(false)) {
            // v3.0.5: always starts with heads-up notification for JingleMessage call propose
            AppCallListener.startIncomingCallNotification(mRemote, sid, SystrayService.JINGLE_MESSAGE_PROPOSE, isVideoCall);
            sendJingleMessageRinging(mRemote.asFullJidIfPossible(), sid);
            mState = JingleMessageState.propose;
        }
        else {
            JingleReason reason = new JingleReason(JingleReason.Reason.unsupported_applications, "Unsupported", null);
            mJnManager.sendJingleMessageReject(mRemote.asFullJidIfPossible(), sid, reason, null);
            endJmCallProcess(sid, true, R.string.call_no_active_device);
        }
    }

    /**
     * Terminate the current call in progress, on receive new call to migrate.
     *
     * @param sid The call Id.
     */
    public synchronized void onCallMigrated(String sid) {
        Call call = CallManager.getActiveCall(sid);
        if (call == null)
            return;

        CallPeer peer = call.getCallPeers().next();

        // if we are failing a peer and have a reason, add the reason packet extension
        String reasonText = aTalkApp.getResString(R.string.call_migrated);
        JingleReason jingleReason = new JingleReason(JingleReason.Reason.success, reasonText, null);

        // XXX maybe add answer/hangup abstract method to MediaAwareCallPeer
        if (peer instanceof CallPeerJabberImpl) {
            ((CallPeerJabberImpl) peer).hangup(false, reasonText, jingleReason);
        }
    }

    public void sendJingleMessageRinging(FullJid remote, String sid) {
        mJnManager.sendJingleMessageRinging(remote, sid);
        mState = JingleMessageState.ringing;

    }

    /**
     * When user accepted the call on this device, send an "proceed: message to own bareJid;
     * server will then forward to all our resources for action.
     * Other resources not answer the call should end the notifications.
     * Use mSid dominated during normal and tie-break.
     */
    public static void sendJingleMessageProceed(String sid) {
        mJnManager.sendJingleMessageProceed(mUser.asBareJid(), sid);
    }

    /**
     * The received "proceed" message is forwarded by the server.
     * Check to see if we are the original sender; Request caller to proceed with the call if so.
     * endJmCallProcess for either case.
     *
     * @param jingleMessage Accept received from the server
     * @param message the original received Jingle Message
     */
    public void onCallProceed(JingleMessage jingleMessage, Message message) {
        // Valid caller found, and we are the sender of the "proceed" jingle message, then request sender to proceed
        Jid from = message.getFrom();
        String sid = jingleMessage.getId();

        // Received self broadcast, so proceed with the call.
        if (mUser.equals(from)) {
            Jid remote = sid.equals(mSid) ? mRemote : mRemote2;
            mJnManager.sendJingleMessageProceed(remote.asFullJidIfPossible(), sid);

            endJmCallProcess(sid, false, R.string.connecting_, remote);
            mState = JingleMessageState.proceed;
            inProgress = true;
        }
        else {
            // Display to user who has accepted the call
            endJmCallProcess(sid, true, R.string.call_answered, from);
        }
    }

    /**
     * Local user has rejected the call; prepare Jingle Message reject and send
     * it to the remote (Primary or secondary incoming call).
     *
     * @param sid the intended Jingle Message call id
     */
    public static void sendJingleMessageReject(String sid, JingleReason reason) {
        Jid remote = sid.equals(mSid) ? mRemote : mRemote2;
        mJnManager.sendJingleMessageReject(remote.asFullJidIfPossible(), sid, reason, null);
        endJmCallProcess(sid, true, null);
    }

    /**
     * Dismiss notification if another resource instance has rejected the call propose during normal or tie-break;
     *
     * @param from another instance of the user whom has rejected the call.
     * @param sid the intended Jingle Message call id
     */
    public static void onCallRejected(Jid from, String sid) {
        endJmCallProcess(sid, true, R.string.call_rejected, from);
    }

    /**
     * Call retracted by caller. Proceed only if retracted sid is the same as the current mSid.
     * i.e. when caller decides to abort the call. Send call retracted notification.
     * Otherwise it is retracted in secondary call, so just clear the notification.
     *
     * @param jingleMessage Retract received
     * @param message the original received Jingle Message
     */
    public void onJingleMessageRetract(JingleMessage jingleMessage, Message message) {
        String sid = jingleMessage.getId();
        Jid fromJid = message.getFrom();

        if (sid.equals(mSid)) {
            BareJid jidFrom = fromJid.asBareJid();
            endJmCallProcess(sid, true, R.string.call_retracted, jidFrom);
            onCallRetracted(jidFrom);
        }
        else {
            // Stop all call alerts and clear heads-up notification that are in progress for secondary call.
            new VibrateHandlerImpl().cancel();
            NotificationPopupHandler.removeCallNotification(sid);
        }
    }

    /**
     * fired a call retracted notification on call retracted by caller.
     *
     * @param caller BareJid of caller.
     */
    private void onCallRetracted(BareJid caller) {
        Map<String, Object> extras = new HashMap<>();
        extras.put(NotificationData.POPUP_MESSAGE_HANDLER_TAG_EXTRA, caller);

        byte[] contactIcon = AvatarManager.getAvatarImageByJid(caller);
        String textMessage = caller + " " + GuiUtils.formatDateTimeShort(new Date());

        NotificationService notificationService = NotificationWiringActivator.getNotificationService();
        notificationService.fireNotification(NotificationManager.MISSED_CALL, SystrayService.MISSED_CALL_MESSAGE_TYPE,
                aTalkApp.getResString(R.string.call_retracted, caller.getLocalpartOrNull()), textMessage, contactIcon, extras);
    }

    //==================== Common utilities ====================//

    /**
     * Send Jingle Message finish on end call successful.
     * Send of session-terminate on hang up call will trigger this function.
     *
     * @param remote the remote callee FullJid
     * @param sid the intended Jingle Message call id
     * @param reason the JingleReason xmlElement to be included
     */
    public static void sendJingleMessageFinish(FullJid remote, String sid, JingleReason reason) {
        mJnManager.sendJingleMessageFinish(remote, sid, reason, null);
        endJmCallProcess(sid, true, null);
        mState = JingleMessageState.finish;
    }

    /**
     * Routine to take care of non-compliant client which does not send Finish JM or when call exception occurred.
     *
     * @param sid Call Sid
     */
    public static void setJingleMessageFinish(String sid) {
        // Only proceed if it was not init yet.
        if (mState != JingleMessageState.initial || mSid != null) {
            Timber.d("set JingleMessageFinish!");
            endJmCallProcess(sid, true, null);
            inProgress = false;
        }
    }

    // Send finish in response to remote init to end the call. Block cycling sending.
    private void onJingleMessageFinish(JingleMessage jingleMessage, Message message) {
        // Do not send finish if it was already sent.
        if (mState == JingleMessageState.finish)
            return;

        String sid = jingleMessage.getId();
        Jid from = message.getFrom();
        Timber.w("Call ended by remote: (%s) %s", sid, from);

        // Do not response with finish if it is a call migration.
        if (jingleMessage.getElements(MigratedElement.ELEMENT).isEmpty()) {
            JingleReason reason = new JingleReason(JingleReason.Reason.success, "Call ended", null);
            mJnManager.sendJingleMessageFinish(from.asFullJidIfPossible(), sid, reason, null);
            mState = JingleMessageState.finish;
        }
        endJmCallProcess(mSid, true, null);
    }

    /**
     * Check to see if the session-initiate is triggered via JingleMessage session by comparing to the JM mSid's.
     *
     * @param sid incoming Jingle session-initiate sid for verification
     *
     * @return true if the call was via JingleMessage
     */
    public static boolean isJingleMessageSession(String sid) {
        return sid.equals(mSid) || sid.equals(mSid2);
    }

    /**
     * Get the current call recipient. Note the mRemote for
     * a. Initiator is BareJid until onJingleMessageProceed()
     * b. Responder is FullJid
     *
     * @return Jid of the call remote
     */
    public static Jid getRecipient() {
        return mRemote;
    }

    /**
     * Start JingleMessageCallActivity with UI allowing caller to take call.
     *
     * @param sid the unique for Jingle Message / Jingle Session sid
     */
    private static void startJMCallActivity(String sid) {
        Context context = aTalkApp.getInstance();
        Intent intent = new Intent(context, JingleMessageCallActivity.class);

        intent.putExtra(CallManager.CALL_SID, sid);
        intent.putExtra(CallManager.CALL_EVENT, NotificationManager.OUTGOING_CALL);

        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
    }

    /**
     * This is called when the Jingle Message process cycle has ended i.e. accept, reject or retract etc.
     * Jingle message must stop both the RingTone looping and vibrator independent of Jingle call.
     *
     * @param sid The JM sid for notification dismiss
     * @param init Flag to indicate to clear jm state flags
     * @param id String id
     * @param arg arg for the string format
     */
    private static void endJmCallProcess(String sid, boolean init, Integer id, Object... arg) {
        if (id != null) {
            aTalkApp.showToastMessage(id, arg);
        }
        Timber.d("endJmCallProcess: %s: %s \n%s \n%s", init, jmEndListener != null, sid,
                NotificationPopupHandler.getCallNotificationId(sid));

        // Due to race condition, JmCallActivity is not launched until <proceed/> is sent.
        // Never clear the jmEngListener until the JmCallActivity UI has been disposed of.
        if (jmEndListener != null) {
            jmEndListener.endJmCallActivity();
            jmEndListener = null;
        }

        // Stop all call alerts and clear heads-up notification that are in progress.
        new VibrateHandlerImpl().cancel();
        NotificationPopupHandler.removeCallNotification(sid);

        // Proceed to init call parameters only if sid matches mSid.
        if (init) {
            inProgress = false;
            mSid = null;
            mSid2 = null;
            mState = JingleMessageState.initial;
        }
    }

    /**
     * Add JMEndListener to end the JM call UI.
     *
     * @param fl JmEndListener to finish the activity
     */
    public static void setJmEndListener(JmEndListener fl) {
        jmEndListener = fl;
    }

    public interface JmEndListener {
        void endJmCallActivity();
    }
}
