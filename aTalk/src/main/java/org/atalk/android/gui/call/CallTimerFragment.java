/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.android.gui.call;

import android.widget.TextView;

import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;

import net.java.sip.communicator.service.protocol.CallPeer;
import net.java.sip.communicator.service.protocol.CallPeerState;
import net.java.sip.communicator.util.GuiUtils;

import org.atalk.android.BaseFragment;
import org.atalk.android.R;

import timber.log.Timber;

/**
 * Fragment implements the logic responsible for updating call duration timer. It is expected that parent
 * <code>Activity</code> contains <code>TextView</code> with <code>R.id.callTime</code> ID.
 *
 * @author Pawel Domas
 */
public class CallTimerFragment extends BaseFragment {
    /**
     * Indicates if the call timer has been started.
     */
    private boolean isCallTimerStarted = false;

    /**
     * The start date time of the call.
     */
    private Date callStartDate;

    /**
     * A timer to count call duration.
     */
    private final Timer callDurationTimer = new Timer();

    /**
     * Must be called in order to initialize and start the timer.
     *
     * @param callPeer the <code>CallPeer</code> for which we're tracking the call duration.
     */
    public void callPeerAdded(CallPeer callPeer) {
        CallPeerState currentState = callPeer.getState();
        if ((currentState == CallPeerState.CONNECTED || CallPeerState.isOnHold(currentState)) && !isCallTimerStarted()) {
            callStartDate = new Date(callPeer.getCallDurationStartTime());
            startCallTimer();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onResume() {
        super.onResume();
        doUpdateCallDuration();
    }

    /**
     * Called when an activity is destroyed.
     */
    @Override
    public void onDestroy() {
        if (isCallTimerStarted()) {
            stopCallTimer();
        }
        super.onDestroy();
    }

    /**
     * Updates the call duration string. Invoked on UI thread.
     */
    public void updateCallDuration() {
        runOnUiThread(this::doUpdateCallDuration);
    }

    /**
     * Updates the call duration string.
     */
    private void doUpdateCallDuration() {
        if (callStartDate == null || getActivity() == null)
            return;

        String timeStr = GuiUtils.formatTime(callStartDate.getTime(), System.currentTimeMillis());
        TextView callTime = getActivity().findViewById(R.id.callTime);
        callTime.setText(timeStr);
        VideoCallActivity.callState.callDuration = timeStr;
    }

    /**
     * Starts the timer that counts call duration.
     */
    public void startCallTimer() {
        if (callStartDate == null) {
            this.callStartDate = new Date();
        }

        // Do not schedule if it is already started (pidgin sends 4 session-accept's on user accept incoming call)
        if (!isCallTimerStarted) {
            try {
                this.callDurationTimer.schedule(new CallTimerTask(), new Date(System.currentTimeMillis()), 1000);
                this.isCallTimerStarted = true;
            } catch (IllegalStateException e) {  // Timer already canceled.
                Timber.w("Start call timber error: %s", e.getMessage());
            }
        }
    }

    /**
     * Stops the timer that counts call duration.
     */
    public void stopCallTimer() {
        this.callDurationTimer.cancel();
    }

    /**
     * Returns {@code true</code> if the call timer has been started, otherwise returns <code>false}.
     *
     * @return {@code true</code> if the call timer has been started, otherwise returns <code>false}
     */
    public boolean isCallTimerStarted() {
        return isCallTimerStarted;
    }

    /**
     * Each second refreshes the time label to show to the user the exact duration of the call.
     */
    private class CallTimerTask extends TimerTask {
        @Override
        public void run() {
            updateCallDuration();
        }
    }
}
