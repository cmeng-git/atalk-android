/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.android.gui.call.notification;

import android.app.*;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.support.v4.app.NotificationCompat;
import android.widget.RemoteViews;

import net.java.sip.communicator.service.protocol.Call;
import net.java.sip.communicator.service.protocol.CallPeer;
import net.java.sip.communicator.service.protocol.event.*;

import org.atalk.android.R;
import org.atalk.android.aTalkApp;
import org.atalk.android.gui.call.CallManager;
import org.atalk.android.gui.call.VideoCallActivity;
import org.atalk.android.plugin.notificationwiring.AndroidNotifications;
import org.atalk.impl.androidtray.SystrayServiceImpl;

import java.util.HashMap;
import java.util.Map;

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
    PendingIntent pVideo = null;

    /**
     * Private constructor
     */
    private CallNotificationManager()
    {
    }

    /**
     * Singleton instance
     */
    private static CallNotificationManager instance = new CallNotificationManager();

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
     * Map of currently running notifications - likely to contain only single item in android.
     */
    private Map<String, CtrlNotificationThread> handlersMap = new HashMap<>();

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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            nBuilder = new NotificationCompat.Builder(context, AndroidNotifications.DEFAULT_GROUP);
        else
            nBuilder = new NotificationCompat.Builder(context, null);

        nBuilder.setWhen(System.currentTimeMillis()).setSmallIcon(R.drawable.ic_notification);

        // Sets call peer display name
        RemoteViews contentView = new RemoteViews(context.getPackageName(), R.layout.status_bar_call);

        CallPeer callPeer = call.getCallPeers().next();
        contentView.setTextViewText(R.id.calleeDisplayName, callPeer.getDisplayName());

        // Binds pending intents
        setIntents(context, contentView, callID);

        // Sets the content view
        nBuilder.setContent(contentView);
        Notification notification = nBuilder.build();
        NotificationManager mNotificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

        int id = SystrayServiceImpl.getGeneralNotificationId();
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
        // Hangup button
        PendingIntent pHangup = PendingIntent.getBroadcast(ctx, 0, CallControl.getHangupIntent(callID),
                PendingIntent.FLAG_CANCEL_CURRENT);
        contentView.setOnClickPendingIntent(R.id.hangup_button, pHangup);

        // Speakerphone button
        PendingIntent pSpeaker = PendingIntent.getBroadcast(ctx, 1, CallControl.getToggleSpeakerIntent(callID),
                PendingIntent.FLAG_CANCEL_CURRENT);
        contentView.setOnClickPendingIntent(R.id.speakerphone, pSpeaker);

        // Mute button
        PendingIntent pMute = PendingIntent.getBroadcast(ctx, 2, CallControl.getToggleMuteIntent(callID),
                PendingIntent.FLAG_CANCEL_CURRENT);
        contentView.setOnClickPendingIntent(R.id.mute_button, pMute);

        // Hold button
        PendingIntent pHold = PendingIntent.getBroadcast(ctx, 3, CallControl.getToggleOnHoldIntent(callID),
                PendingIntent.FLAG_CANCEL_CURRENT);
        contentView.setOnClickPendingIntent(R.id.hold_button, pHold);

        // Show video call Activity
        Intent videoCall = new Intent(ctx, VideoCallActivity.class);
        videoCall.putExtra(CallManager.CALL_IDENTIFIER, callID);
        pVideo = PendingIntent.getActivity(ctx, 4, videoCall, PendingIntent.FLAG_CANCEL_CURRENT);
        contentView.setOnClickPendingIntent(R.id.back_to_call, pVideo);

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
            aTalkApp.getNotificationManager().cancel(notificationHandler.id);
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
