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

import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;

import net.java.sip.communicator.plugin.notificationwiring.NotificationManager;

import org.atalk.android.BaseActivity;
import org.atalk.android.R;
import org.atalk.android.gui.aTalk;
import org.atalk.android.util.AppImageUtil;
import org.atalk.impl.appstray.NotificationPopupHandler;
import org.jivesoftware.smackx.avatar.AvatarManager;
import org.jxmpp.jid.Jid;

/**
 * The process to handle the incoming and outgoing call for <code>Jingle Message</code> states changes.
 * Note: incoming call is via ReceivedCallActivity instead due to android-12 constraint.
 * <p>
 * Implementation for aTalk v3.0.5:
 * Starting with Android 12 notifications will not work if they do not start activities directly
 * NotificationService: Indirect notification activity start (trampoline) from org.atalk.android blocked
 * <a href="https://proandroiddev.com/notification-trampoline-restrictions-android12-7d2a8b15bbe2">Notification trampoline restrictions-Android12</a>
 * Heads-up notification launches ReceivedCallActivity directly; failed if launches JingleMessageCallActivity => ReceivedCallActivity;
 * ActivityTaskManager: Background activity start will failed for android-12 and above.
 *
 * @author Eng Chong Meng
 */
public class JingleMessageCallActivity extends BaseActivity implements JingleMessageSessionImpl.JmEndListener {
    private ImageView peerAvatar;
    private String mSid;

    /**
     * Create the UI with call hang up button to retract call for outgoing call.
     * Incoming JingleMessage <propose/> will only sendJingleAccept(mSid), automatically only
     * if aTalk is not in locked screen; else show UI for user choice to accept or reject call.
     * Note: heads-up notification is not shown when device is in locked screen.
     *
     * @param savedInstanceState If the activity is being re-initialized after previously being shut down then this
     * Bundle contains the data it most recently supplied in onSaveInstanceState(Bundle).
     * Note: Otherwise it is null.
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.call_received);
        setScreenOn();

        // Implementation not supported currently
        findViewById(R.id.videoCallButton).setVisibility(View.GONE);
        ImageButton callButton = findViewById(R.id.callButton);
        ImageButton hangUpButton = findViewById(R.id.hangupButton);
        peerAvatar = findViewById(R.id.calleeAvatar);

        JingleMessageSessionImpl.setJmEndListener(this);
        Bundle extras = getIntent().getExtras();
        if (extras != null) {
            // Jingle Message / Jingle Session sid
            mSid = extras.getString(CallManager.CALL_SID);

            String eventType = extras.getString(CallManager.CALL_EVENT);
            boolean isIncomingCall = NotificationManager.INCOMING_CALL.equals(eventType);
            boolean autoAccept = extras.getBoolean(CallManager.AUTO_ACCEPT, false);
            if (isIncomingCall && autoAccept) {
                JingleMessageSessionImpl.sendJingleAccept(mSid);
                return;
            }

            Jid remote = JingleMessageSessionImpl.getRemote();
            ((TextView) findViewById(R.id.calleeAddress)).setText(remote);
            setPeerImage(remote);

            if (isIncomingCall) {
                // Call accepted, send Jingle Message <accept/> to inform caller.
                callButton.setOnClickListener(v -> {
                            JingleMessageSessionImpl.sendJingleAccept(mSid);
                        }
                );

                // Call rejected, send Jingle Message <reject/> to inform caller.
                hangUpButton.setOnClickListener(v -> {
                            JingleMessageSessionImpl.sendJingleMessageReject(mSid);
                        }
                );
            }
            else { // NotificationManager.OUTGOING_CALL
                // Call retract, send Jingle Message <retract/> to inform caller.
                hangUpButton.setOnClickListener(v -> {
                            // NPE: Get triggered with remote == null at time???
                            if (remote != null) {
                                JingleMessageSessionImpl.sendJingleMessageRetract(remote, mSid);
                            }
                        }
                );
                callButton.setVisibility(View.GONE);
            }
        }
        getOnBackPressedDispatcher().addCallback(backPressedCallback);
    }

    /**
     * Hangs up the call when back is pressed as this Activity will not be displayed again.
     */
    OnBackPressedCallback backPressedCallback = new OnBackPressedCallback(true) {
        @Override
        public void handleOnBackPressed() {
        }
    };

    /**
     * Bring aTalk to foreground, and end JingleMessageCallActivity UI; else user is prompted with
     * both heads-up notification and ReceivedCallActivity UI to take action, this confuses user;
     * Also to avoid failure arises on launching ...CallActivity from background;
     * <p>
     * Note: Due to android design constraints i.e. only activity launch is allowed when android is in locked screen.
     * Hence two UI are still being shown on call received i.e. JingleMessageCallActivity and VideoCallActivity
     */
    @Override
    public void onJmEndCallback() {
        NotificationPopupHandler.removeCallNotification(mSid);
        startActivity(aTalk.class);

        // Must destroy JingleMessageCallActivity UI, else remain visible after end call.
        finish();
    }

    /**
     * Sets the peer avatar.
     *
     * @param callee the avatar of the callee
     */
    public void setPeerImage(Jid callee) {
        if (callee == null)
            return;

        byte[] avatar = AvatarManager.getAvatarImageByJid(callee.asBareJid());
        if ((avatar != null) && (avatar.length != 0)) {
            peerAvatar.setImageBitmap(AppImageUtil.bitmapFromBytes(avatar));
        }
    }
}