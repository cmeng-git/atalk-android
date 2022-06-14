/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.android.gui.call;

import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;

import net.java.sip.communicator.plugin.notificationwiring.NotificationManager;
import net.java.sip.communicator.plugin.notificationwiring.NotificationWiringActivator;
import net.java.sip.communicator.service.notification.NotificationData;
import net.java.sip.communicator.service.notification.NotificationService;
import net.java.sip.communicator.service.protocol.Call;
import net.java.sip.communicator.service.protocol.CallState;
import net.java.sip.communicator.service.protocol.Contact;
import net.java.sip.communicator.service.protocol.event.CallChangeEvent;
import net.java.sip.communicator.service.protocol.event.CallChangeListener;
import net.java.sip.communicator.service.protocol.event.CallEvent;
import net.java.sip.communicator.service.protocol.event.CallListener;
import net.java.sip.communicator.service.protocol.event.CallPeerChangeEvent;
import net.java.sip.communicator.service.protocol.event.CallPeerEvent;
import net.java.sip.communicator.service.systray.SystrayService;
import net.java.sip.communicator.util.GuiUtils;

import org.atalk.android.R;
import org.atalk.android.aTalkApp;
import org.atalk.android.gui.util.AndroidUtils;
import org.atalk.impl.androidtray.NotificationPopupHandler;
import org.jivesoftware.smackx.avatar.AvatarManager;
import org.jxmpp.jid.Jid;
import org.jxmpp.util.XmppStringUtils;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import timber.log.Timber;

/**
 * A utility implementation of the {@link CallListener} interface which delivers
 * the <code>CallEvent</code>s to the AWT event dispatching thread.
 *
 * @author Yana Stamcheva
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
public class AndroidCallListener implements CallListener, CallChangeListener
{
    /**
     * The application context.
     */
    private static final Context appContext = aTalkApp.getGlobalContext();

    /*
     * Flag stores speakerphone status to be restored to initial value once the call has ended.
     */
    private static Boolean speakerPhoneBeforeCall;

    /**
     * {@inheritDoc}
     *
     * Delivers the <code>CallEvent</code> to the AWT event dispatching thread.
     */
    public void incomingCallReceived(CallEvent ev)
    {
        onCallEvent(ev);
        ev.getSourceCall().addCallChangeListener(this);
    }

    /**
     * {@inheritDoc}
     *
     * Delivers the <code>CallEvent</code> to the AWT event dispatching thread.
     */
    public void outgoingCallCreated(CallEvent ev)
    {
        onCallEvent(ev);
    }

    /**
     * {@inheritDoc}
     *
     * Delivers the <code>CallEvent</code> to the AWT event dispatching thread.
     */
    public void callEnded(CallEvent ev)
    {
        onCallEvent(ev);
        ev.getSourceCall().removeCallChangeListener(this);
    }

    /**
     * Notifies this <code>CallListener</code> about a specific <code>CallEvent</code>. Executes in whichever
     * thread brought the event to this listener. Delivers the event to the AWT event dispatching thread.
     *
     * @param evt the <code>CallEvent</code> this <code>CallListener</code> is being notified about
     */
    protected void onCallEvent(final CallEvent evt)
    {
        Call call = evt.getSourceCall();
        Timber.d("Received CallEvent: %s: %s", evt, call.getCallId());
        switch (evt.getEventID()) {
            // Triggered by outgoing call after session-initiate
            case CallEvent.CALL_INITIATED:
                storeSpeakerPhoneStatus();
                clearVideoCallState();
                startVideoCallActivity(call);
                break;

            case CallEvent.CALL_RECEIVED:
                // cmeng 20220529: Allow two active calls to support call in waiting
                if (CallManager.getActiveCallsCount() > 1) {
                    CallManager.hangupCall(call);
                }
                else {
                    if (CallManager.getActiveCallsCount() == 0) {
                        storeSpeakerPhoneStatus();
                        clearVideoCallState();
                    }

                    // cmeng - answer call and on hold current - mic not working
                    // If incoming call accepted via Jingle Message session, then start the VideoCallActivity UI;
                    if (call.getCallId().equals(JingleMessageSessionImpl.getSid())) {
                        startVideoCallActivity(call);
                    }
                    // Launch UI for user selection of audio or video call
                    else if (aTalkApp.isForeground) {
                        startCallActivity(call);
                    }
                    // else launch a heads-up UI for user to accept the call
                    else {
                        startReceivedCallActivity(evt);
                    }

                    // merge call - exception; It will end up with a conference call.
                    // CallManager.answerCallInFirstExistingCall(evt.getSourceCall());
                }
                break;

            // Call Activity must close itself
            case CallEvent.CALL_ENDED:
                endCall(call);
                break;
        }
    }

    /**
     * Clears call state stored in previous calls.
     */
    private void clearVideoCallState()
    {
        VideoCallActivity.callState = new VideoCallActivity.CallStateHolder();
    }

    /**
     * Stores speakerphone status for the call duration; to be restored after the call has ended.
     */
    private void storeSpeakerPhoneStatus()
    {
        AudioManager audioManager = aTalkApp.getAudioManager();
        speakerPhoneBeforeCall = audioManager.isSpeakerphoneOn();
        // Timber.d("Storing speakerphone status: %s", speakerPhoneBeforeCall);
    }

    /**
     * Restores speakerphone status.
     */
    private static void restoreSpeakerPhoneStatus()
    {
        if (speakerPhoneBeforeCall != null) {
            AudioManager audioManager = aTalkApp.getAudioManager();
            audioManager.setSpeakerphoneOn(speakerPhoneBeforeCall);
            // Timber.d("Restoring speakerphone to: %s", speakerPhoneBeforeCall);
            speakerPhoneBeforeCall = null;
        }
    }

    @Override
    public void callPeerAdded(CallPeerEvent callPeerEvent)
    {
    }

    @Override
    public void callPeerRemoved(CallPeerEvent callPeerEvent)
    {
    }

    @Override
    public void callStateChanged(CallChangeEvent evt)
    {
        Object callState = evt.getNewValue();
        Call call = evt.getSourceCall();

        if (CallState.CALL_ENDED.equals(callState)) {
            // remove heads-up notification in case the call end is by remote retract.
            NotificationPopupHandler.removeCallNotification(call.getCallId());
            if (CallState.CALL_INITIALIZATION.equals(evt.getOldValue())) {
                if ((evt.getCause() != null)
                        && (evt.getCause().getReasonCode() != CallPeerChangeEvent.NORMAL_CALL_CLEARING)) {
                    // Missed call
                    fireMissedCallNotification(evt);
                }
            }
        }
    }

    /**
     * Starts the video call UI when:
     * a. an incoming call is accepted via Jingle Message Initiation or
     * b. an outgoing call has been initiated.
     *
     * @param call the <code>Call</code> to be handled
     */
    private void startVideoCallActivity(Call call)
    {
        String callIdentifier = CallManager.addActiveCall(call);
        Intent videoCall = VideoCallActivity.createVideoCallIntent(appContext, callIdentifier);
        videoCall.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        appContext.startActivity(videoCall);
    }

    /**
     * Starts the receive call UI when in legacy jingle call
     *
     * @param call the <code>Call</code> to be handled
     */
    private void startCallActivity(Call call)
    {
        String callIdentifier = CallManager.addActiveCall(call);
        Intent intent = new Intent(appContext, ReceivedCallActivity.class)
                .putExtra(CallManager.CALL_IDENTIFIER, callIdentifier)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        appContext.startActivity(intent);
    }

    /**
     * Start the heads-up notifications for incoming (received) call when aTalk is not in focus
     *
     * @param evt the <code>CallEvent</code>
     */
    private void startReceivedCallActivity(CallEvent evt)
    {
        Call call = evt.getSourceCall();

        String identifier = CallManager.addActiveCall(call);
        Map<String, Object> extras = new HashMap<>();
        extras.put(NotificationData.POPUP_MESSAGE_HANDLER_TAG_EXTRA, identifier);

        Jid peerJid = call.getCallPeers().next().getContact().getJid();
        byte[] contactIcon = AvatarManager.getAvatarImageByJid(peerJid.asBareJid());
        String message = (evt.isVideoCall() ? "(vide): " : "(audio): ") + GuiUtils.formatDateTimeShort(new Date());

        NotificationService notificationService = NotificationWiringActivator.getNotificationService();
        notificationService.fireNotification(NotificationManager.INCOMING_CALL, SystrayService.JINGLE_INCOMING_CALL,
                aTalkApp.getResString(R.string.service_gui_CALL_INCOMING, XmppStringUtils.parseLocalpart(peerJid.toString())),
                message, contactIcon, extras);
    }

    /**
     * Answers the given call and launches the call user interface.
     *
     * @param call the call to answer
     * @param isVideoCall indicates if video shall be used.
     */
    public static void answerCall(final Call call, boolean isVideoCall)
    {
        new Thread()
        {
            public void run()
            {
                CallManager.answerCall(call, isVideoCall);
                String callIdentifier = CallManager.addActiveCall(call);
                Intent videoCall = VideoCallActivity.createVideoCallIntent(appContext, callIdentifier);
                videoCall.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                appContext.startActivity(videoCall);
            }
        }.start();
    }

    /**
     * End the specific call.
     *
     * @param call the call to end
     */
    private void endCall(Call call)
    {
        // Clears all inCall notification
        AndroidUtils.clearGeneralNotification(appContext);
        // NotificationPopupHandler.removeCallNotification(call.getCallId()); // Called by Jingle call only

        // Removes the call from active calls list and restores speakerphone status
        CallManager.removeActiveCall(call);
        restoreSpeakerPhoneStatus();
    }

    /**
     * Fires missed call notification for given <code>CallChangeEvent</code>.
     *
     * @param evt the <code>CallChangeEvent</code> that describes missed call.
     */
    private void fireMissedCallNotification(CallChangeEvent evt)
    {
        NotificationService notificationService = NotificationWiringActivator.getNotificationService();

        Contact contact = evt.getCause().getSourceCallPeer().getContact();
        if ((contact == null) || (notificationService == null)) {
            Timber.w("No contact found - missed call notification skipped");
            return;
        }

        Map<String, Object> extras = new HashMap<>();
        extras.put(NotificationData.POPUP_MESSAGE_HANDLER_TAG_EXTRA, contact);

        byte[] contactIcon = contact.getImage();
        String message = contact.getDisplayName() + " " + GuiUtils.formatDateTime(new Date());

        notificationService.fireNotification(NotificationManager.MISSED_CALL, SystrayService.MISSED_CALL_MESSAGE_TYPE,
                aTalkApp.getResString(R.string.service_gui_CALL_MISSED_CALL), message, contactIcon, extras);
    }
}