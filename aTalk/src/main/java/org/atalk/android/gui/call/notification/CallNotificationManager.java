/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.android.gui.call.notification;

import android.app.*;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;

import net.java.sip.communicator.service.protocol.Call;
import net.java.sip.communicator.service.protocol.CallPeer;
import net.java.sip.communicator.service.protocol.event.*;

import org.atalk.android.R;
import org.atalk.android.gui.call.*;
import org.atalk.android.gui.util.AndroidImageUtil;
import org.atalk.impl.androidnotification.AndroidNotifications;

import java.util.HashMap;
import java.util.Map;

import androidx.core.app.NotificationCompat;

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
     * Back to call pending intent, to allow trigger from message chat send button
     */
    private PendingIntent pVideo = null;

    /**
     * Map of currently running notifications - likely to contain only single item in android.
     */
    private Map<String, CtrlNotificationThread> handlersMap = new HashMap<>();

    /**
     * Singleton instance
     */
    private static CallNotificationManager instance = new CallNotificationManager();

    private NotificationManager mNotificationManager = null;

    /**
     * Private constructor
     */
    private CallNotificationManager()
    {
    }

    /**
     * Returns call control notifications manager.
     *
     * @return the <tt>CallNotificationManager</tt>.
     */
    public static CallNotificationManager get()
    {
        return instance;
    }

    /**
     * Displays notification allowing user to control the call state directly from the status bar.
     *
     * @param context the Android context.
     * @param callID the ID of call that will be used. The ID is managed by {@link CallManager}.
     */
    public synchronized void showCallNotification(Context context, final String callID)
    {
        final Call call = CallManager.getActiveCall(callID);
        if (call == null) {
            throw new IllegalArgumentException("There's no call with id: " + callID);
        }

        NotificationCompat.Builder nBuilder;
        nBuilder = new NotificationCompat.Builder(context, AndroidNotifications.CALL_GROUP);

        nBuilder.setWhen(System.currentTimeMillis())
                .setSmallIcon(R.drawable.missed_call);
        RemoteViews contentView = new RemoteViews(context.getPackageName(), R.layout.call_status_bar_notification);

        // Sets call peer display name and avatar
        CallPeer callPeer = call.getCallPeers().next();
        byte[] avatar = CallUIUtils.getCalleeAvatar(call);
        if (avatar != null) {
            contentView.setImageViewBitmap(R.id.avatarView, AndroidImageUtil.bitmapFromBytes(avatar));
        }
        contentView.setTextViewText(R.id.calleeDisplayName, callPeer.getDisplayName());

        // Binds pending intents
        setIntents(context, contentView, callID);

        // Sets the content view
        nBuilder.setContent(contentView);
        Notification notification = nBuilder.build();

        // Must use random Id, else notification cancel() may not work properly
        int id = (int) (System.currentTimeMillis() % 10000);
        mNotificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        mNotificationManager.notify(id, notification);

        CtrlNotificationThread notificationHandler = new CtrlNotificationThread(context, call, id, notification);
        handlersMap.put(callID, notificationHandler);

        call.addCallChangeListener(new CallChangeListener()
        {
            public void callPeerAdded(CallPeerEvent evt)
            {
            }

            public void callPeerRemoved(CallPeerEvent evt)
            {
                stopNotification(callID);
                call.removeCallChangeListener(this);
            }

            public void callStateChanged(CallChangeEvent evt)
            {
            }
        });

        // Starts notification update thread
        notificationHandler.start();
    }

    /**
     * Binds pending intents to all control <tt>Views</tt>.
     *
     * @param ctx Android context.
     * @param contentView notification root <tt>View</tt>.
     * @param callID the call ID that will be used in the <tt>Intents</tt>
     */
    private void setIntents(Context ctx, RemoteViews contentView, String callID)
    {
        // Speakerphone button
        PendingIntent pSpeaker = PendingIntent.getBroadcast(ctx, 1, CallControl.getToggleSpeakerIntent(callID),
                PendingIntent.FLAG_CANCEL_CURRENT);
        contentView.setOnClickPendingIntent(R.id.button_speakerphone, pSpeaker);

        // Mute button
        PendingIntent pMute = PendingIntent.getBroadcast(ctx, 2, CallControl.getToggleMuteIntent(callID),
                PendingIntent.FLAG_CANCEL_CURRENT);
        contentView.setOnClickPendingIntent(R.id.button_mute, pMute);

        // Hold button
        PendingIntent pHold = PendingIntent.getBroadcast(ctx, 3, CallControl.getToggleOnHoldIntent(callID),
                PendingIntent.FLAG_CANCEL_CURRENT);
        contentView.setOnClickPendingIntent(R.id.button_hold, pHold);

        // Hangup button
        PendingIntent pHangup = PendingIntent.getBroadcast(ctx, 0, CallControl.getHangupIntent(callID),
                PendingIntent.FLAG_CANCEL_CURRENT);
        contentView.setOnClickPendingIntent(R.id.button_hangup, pHangup);

        // Show video call Activity
        Intent videoCall = new Intent(ctx, VideoCallActivity.class);
        videoCall.putExtra(CallManager.CALL_IDENTIFIER, callID);
        pVideo = PendingIntent.getActivity(ctx, 4, videoCall, PendingIntent.FLAG_CANCEL_CURRENT);
        contentView.setOnClickPendingIntent(R.id.button_back_to_call, pVideo);

        // Binds show video call intent to the whole area
        contentView.setOnClickPendingIntent(R.id.notificationContent, pVideo);
    }

    /**
     * Stops the notification running for the call identified by given <tt>callId</tt>.
     *
     * @param callId the ID of the call managed by {@link CallManager}.
     */
    public synchronized void stopNotification(String callId)
    {
        CtrlNotificationThread notificationHandler = handlersMap.get(callId);
        if (notificationHandler != null) {
            notificationHandler.stop();
            handlersMap.remove(callId);
            // Remove the notification
            mNotificationManager.cancel(notificationHandler.id);
        }
    }

    /**
     * Checks if there is notification running for a call with given <tt>callID</tt>.
     *
     * @param callID the ID of a call managed by {@link CallManager}.
     * @return <tt>true</tt> if there is notification running for a call identified by <tt>callID</tt>.
     */
    public synchronized boolean isNotificationRunning(String callID)
    {
        return handlersMap.containsKey(callID);
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
