/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.android.gui.call.notification;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;
import android.content.pm.PackageManager;

import androidx.core.content.ContextCompat;

import java.util.Iterator;

import net.java.sip.communicator.service.protocol.Call;
import net.java.sip.communicator.service.protocol.CallPeer;
import net.java.sip.communicator.util.GuiUtils;

import org.atalk.android.R;
import org.atalk.android.aTalkApp;
import org.atalk.android.gui.call.CallManager;

/**
 * Class runs the thread that updates call control notification.
 *
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
class CtrlNotificationThread {
    /**
     * Notification update interval.
     */
    private static final long UPDATE_INTERVAL = 1000;
    /**
     * The thread that does the updates.
     */
    private Thread thread;
    /**
     * Flag used to stop the thread.
     */
    private boolean run = true;
    /**
     * The call control notification that is being updated by this thread.
     */
    private final Notification mNotification;
    /**
     * The Android context.
     */
    private final Context mContext;
    /**
     * The call that is controlled by notification.
     */
    private final Call mCall;
    /**
     * The notification ID.
     */
    private final int mId;

    /**
     * Creates new instance of {@link CtrlNotificationThread}.
     *
     * @param ctx the Android context.
     * @param call the call that is controlled by current notification.
     * @param id the notification ID.
     * @param notification call control notification that will be updated by this thread.
     */
    public CtrlNotificationThread(Context ctx, Call call, int id, Notification notification) {
        mContext = ctx;
        mCall = call;
        mId = id;
        mNotification = notification;
    }

    /**
     * Starts notification update thread.
     */
    public void start() {
        thread = new Thread(this::notificationLoop);
        thread.start();
    }

    private void notificationLoop() {
        NotificationManager mNotificationManager
                = (NotificationManager) mContext.getSystemService(Context.NOTIFICATION_SERVICE);
        boolean micEnabled = ContextCompat.checkSelfPermission(mContext, Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED;

        while (run) {
            // Timber.log(TimberLog.FINER, "Running control notification thread " + hashCode());

            // Update call duration timer on call notification
            long callStartDate = CallPeer.CALL_DURATION_START_TIME_UNKNOWN;
            Iterator<? extends CallPeer> peers = mCall.getCallPeers();
            if (peers.hasNext()) {
                callStartDate = peers.next().getCallDurationStartTime();
            }
            if (callStartDate != CallPeer.CALL_DURATION_START_TIME_UNKNOWN) {
                mNotification.contentView.setTextViewText(R.id.call_duration,
                        GuiUtils.formatTime(callStartDate, System.currentTimeMillis()));
            }

            boolean isSpeakerphoneOn = aTalkApp.getAudioManager().isSpeakerphoneOn();
            mNotification.contentView.setImageViewResource(R.id.button_speakerphone, isSpeakerphoneOn
                    ? R.drawable.call_speakerphone_on_dark
                    : R.drawable.call_receiver_on_dark);

            // Update notification call mute status
            boolean isMute = (!micEnabled || CallManager.isMute(mCall));

            mNotification.contentView.setImageViewResource(R.id.button_mute,
                    isMute ? R.drawable.call_microphone_mute_dark : R.drawable.call_microphone_dark);

            // Update notification call hold status
            boolean isOnHold = CallManager.isLocallyOnHold(mCall);
            mNotification.contentView.setImageViewResource(R.id.button_hold,
                    isOnHold ? R.drawable.call_hold_on_dark : R.drawable.call_hold_off_dark);

            if (run && (mNotificationManager != null)) {
                mNotificationManager.notify(mId, mNotification);
            }

            synchronized (this) {
                try {
                    this.wait(UPDATE_INTERVAL);
                } catch (InterruptedException e) {
                    break;
                }
            }
        }
    }

    public int getCtrlId() {
        return mId;
    }

    /**
     * Stops notification thread.
     */
    public void stop() {
        run = false;
        synchronized (this) {
            this.notifyAll();
        }

        try {
            thread.join();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
}
