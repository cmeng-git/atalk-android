/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.android.gui.call.notification;

import static org.atalk.impl.androidtray.NotificationPopupHandler.getPendingIntentFlag;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;

import androidx.core.app.NotificationCompat;

import net.java.sip.communicator.service.protocol.Call;
import net.java.sip.communicator.service.protocol.CallPeer;
import net.java.sip.communicator.service.protocol.event.CallChangeEvent;
import net.java.sip.communicator.service.protocol.event.CallChangeListener;
import net.java.sip.communicator.service.protocol.event.CallPeerEvent;

import org.atalk.android.R;
import org.atalk.android.gui.call.CallManager;
import org.atalk.android.gui.call.CallUIUtils;
import org.atalk.android.gui.call.VideoCallActivity;
import org.atalk.android.gui.util.AndroidImageUtil;
import org.atalk.impl.androidnotification.AndroidNotifications;

import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

import timber.log.Timber;

/**
 * Class manages currently running call control notifications. Those are displayed when {@link VideoCallActivity} is
 * minimized or closed and the call is still active. They allow user to do basic call operations like mute, put on hold
 * and hang up directly from the status bar.
 *
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
public class CallNotificationManager
{
    /**
     * Map content contains callId to CallNotificationManager instance.
     */
    private static final Map<String, CallNotificationManager> INSTANCES = new WeakHashMap<>();

    /**
     * Active running notificationHandler if not null.
     */
    private CtrlNotificationThread mNotificationHandler = null;

    /**
     * Android system NOTIFICATION_SERVICE manager
     */
    private NotificationManager mNotificationManager = null;

    /**
     * Back to call pending intent, to allow trigger from message chat send button
     */
    private PendingIntent pVideo = null;

    /**
     * The call ID that will be used in this <code>Instance</code>, and the <code>Intents</code> binding.
     * The ID is managed by {@link CallManager}.
     */
    private final String mCallId;

    /**
     * Map to facilitate the toggle of requestCodeBase between 0 and 10 to avoid existing PendingIntents get cancel:
     * FLAG_CANCEL_CURRENT <a href="https://developer.android.com/reference/android/app/PendingIntent">PendingIntent</a>.
     */
    private static final Map<String, Integer> requestCodes = new HashMap<>();

    /**
     * Returns call control notifications manager for the given callId.
     *
     * @return the <code>CallNotificationManager</code>.
     */
    public static synchronized CallNotificationManager getInstanceFor(String callId)
    {
        CallNotificationManager callNotificationManager = INSTANCES.get(callId);
        if (callNotificationManager == null) {
            callNotificationManager = new CallNotificationManager(callId);
            INSTANCES.put(callId, callNotificationManager);
        }
        return callNotificationManager;
    }

    /**
     * Private constructor
     */
    private CallNotificationManager(String callId)
    {
        mCallId = callId;
    }

    /**
     * Displays notification allowing user to control the call state directly from the status bar.
     *
     * @param context the Android context.
     */
    public synchronized void showCallNotification(Context context)
    {
        final Call call = CallManager.getActiveCall(mCallId);
        if (call == null) {
            throw new IllegalArgumentException("There's no call with id: " + mCallId);
        }

        // Sets call peer display name and avatar in content view
        RemoteViews contentView = new RemoteViews(context.getPackageName(), R.layout.call_status_bar_notification);
        CallPeer callPeer = call.getCallPeers().next();
        byte[] avatar = CallUIUtils.getCalleeAvatar(call);
        if (avatar != null) {
            contentView.setImageViewBitmap(R.id.avatarView, AndroidImageUtil.bitmapFromBytes(avatar));
        }
        contentView.setTextViewText(R.id.calleeDisplayName, callPeer.getDisplayName());

        // Binds pending intents using the requestCodeBase to avoid being cancel; aTalk can have 2 callNotifications.
        int requestCodeBase = requestCodes.containsValue(10) ? 0 : 10;
        requestCodes.put(mCallId, requestCodeBase);
        setIntents(context, contentView, requestCodeBase);

        Notification notification = new NotificationCompat.Builder(context, AndroidNotifications.CALL_GROUP)
                .setWhen(System.currentTimeMillis())
                .setSmallIcon(R.drawable.missed_call)
                .setContent(contentView) // Sets the content view
                .build();

        // Must use random Id, else notification cancel() may not work properly
        int id = (int) (System.currentTimeMillis() % 10000);
        mNotificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        mNotificationManager.notify(id, notification);
        mNotificationHandler = new CtrlNotificationThread(context, call, id, notification);

        call.addCallChangeListener(new CallChangeListener()
        {
            public void callPeerAdded(CallPeerEvent evt)
            {
            }

            public void callPeerRemoved(CallPeerEvent evt)
            {
                stopNotification();
                call.removeCallChangeListener(this);
            }

            public void callStateChanged(CallChangeEvent evt)
            {
            }
        });

        // Starts notification update thread
        mNotificationHandler.start();
    }

    /**
     * Binds pending intents to all control <code>Views</code>.
     *
     * @param ctx Android context.
     * @param contentView notification root <code>View</code>.
     * @param requestCodeBase the starting Request Code ID that will be used in the <code>Intents</code>
     */
    private void setIntents(Context ctx, RemoteViews contentView, int requestCodeBase)
    {
        // Speakerphone button
        PendingIntent pSpeaker = PendingIntent.getBroadcast(ctx, requestCodeBase++, CallControl.getToggleSpeakerIntent(mCallId),
                getPendingIntentFlag(false, false));
        contentView.setOnClickPendingIntent(R.id.button_speakerphone, pSpeaker);

        // Mute button
        PendingIntent pMute = PendingIntent.getBroadcast(ctx, requestCodeBase++, CallControl.getToggleMuteIntent(mCallId),
                getPendingIntentFlag(false, false));
        contentView.setOnClickPendingIntent(R.id.button_mute, pMute);

        // Hold button
        PendingIntent pHold = PendingIntent.getBroadcast(ctx, requestCodeBase++, CallControl.getToggleOnHoldIntent(mCallId),
                getPendingIntentFlag(false, false));
        contentView.setOnClickPendingIntent(R.id.button_hold, pHold);

        // Hangup button
        PendingIntent pHangup = PendingIntent.getBroadcast(ctx, requestCodeBase++, CallControl.getHangupIntent(mCallId),
                getPendingIntentFlag(false, false));
        contentView.setOnClickPendingIntent(R.id.button_hangup, pHangup);

        // Transfer call via VideoCallActivity, and execute in place to show VideoCallActivity (note-10)
        // Call via broadcast receiver has problem of CallTransferDialog keeps popping up
        Intent pTransfer = new Intent(ctx, VideoCallActivity.class);
        pTransfer.putExtra(CallManager.CALL_SID, mCallId);
        pTransfer.putExtra(CallManager.CALL_TRANSFER, true);
        pVideo = PendingIntent.getActivity(ctx, requestCodeBase++, pTransfer, getPendingIntentFlag(false, false));
        contentView.setOnClickPendingIntent(R.id.button_transfer, pVideo);

        // Show video call Activity on click; pendingIntent executed in place i.e. no via Broadcast receiver
        Intent videoCall = new Intent(ctx, VideoCallActivity.class);
        videoCall.putExtra(CallManager.CALL_SID, mCallId);
        videoCall.putExtra(CallManager.CALL_TRANSFER, false);
        pVideo = PendingIntent.getActivity(ctx, requestCodeBase, videoCall, getPendingIntentFlag(false, false));
        contentView.setOnClickPendingIntent(R.id.button_back_to_call, pVideo);

        // Binds launch VideoCallActivity to the whole area
        contentView.setOnClickPendingIntent(R.id.notificationContent, pVideo);
    }

    /**
     * Stops the notification running for the call with Id stored in mNotificationHandler.
     */
    public synchronized void stopNotification()
    {
        if (mNotificationHandler != null) {
            Timber.d("Call Notification Panel removed: %s; id: %s", mCallId, mNotificationHandler.getCtrlId());
            // Stop NotificationHandler and remove the notification from system notification bar
            mNotificationHandler.stop();
            mNotificationManager.cancel(mNotificationHandler.getCtrlId());

            mNotificationHandler = null;
            INSTANCES.remove(mCallId);
            requestCodes.remove(mCallId);
        }
    }

    /**
     * Checks if there is notification running for a call.
     *
     * @return <code>true</code> if there is notification running in this instance.
     */
    public synchronized boolean isNotificationRunning()
    {
        return mNotificationHandler != null;
    }

    public void backToCall()
    {
        if (pVideo != null) {
            try {
                pVideo.send();
            } catch (PendingIntent.CanceledException e) {
                e.printStackTrace();
            }
        }
    }
}
