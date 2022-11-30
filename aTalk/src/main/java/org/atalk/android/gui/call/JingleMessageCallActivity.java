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
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import net.java.sip.communicator.plugin.notificationwiring.NotificationManager;

import org.atalk.android.R;
import org.atalk.android.gui.aTalk;
import org.atalk.android.gui.util.AndroidImageUtil;
import org.atalk.impl.androidtray.NotificationPopupHandler;
import org.atalk.service.osgi.OSGiActivity;
import org.jetbrains.annotations.NotNull;
import org.jivesoftware.smackx.avatar.AvatarManager;
import org.jxmpp.jid.Jid;

/**
 * The process to handle the incoming and outgoing call for <code>Jingle Message</code> states changes.
 * Note: incoming call is via ReceivedCallActivity instead due to android-12 constraint.
 *
 * Implementation for aTalk v3.0.5:
 * Starting with Android 12 notifications will not work if they do not start activities directly
 * NotificationService: Indirect notification activity start (trampoline) from org.atalk.android blocked
 * https://proandroiddev.com/notification-trampoline-restrictions-android12-7d2a8b15bbe2
 * Heads-up notification launches ReceivedCallActivity directly; failed if launches JingleMessageCallActivity => ReceivedCallActivity;
 * ActivityTaskManager: Background activity start will failed for android-12 and above.
 *
 * @author Eng Chong Meng
 */
public class JingleMessageCallActivity extends OSGiActivity implements JingleMessageSessionImpl.JmEndListener
{
    private ImageView peerAvatar;
    private ImageButton mCallButton;
    private String mSid;

    private boolean isIncomingCall = false;
    private boolean mAutoAccept = false;

    /**
     * Create the UI with call hang up button to retract call.
     *
     * @param savedInstanceState If the activity is being re-initialized after previously being shut down then this
     * Bundle contains the data it most recently supplied in onSaveInstanceState(Bundle).
     * Note: Otherwise it is null.
     */
    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.call_received);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                | WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
                | WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
        );

        // Implementation not supported currently
        findViewById(R.id.videoCallButton).setVisibility(View.GONE);
        mCallButton = findViewById(R.id.callButton);
        ImageButton hangUpButton = findViewById(R.id.hangupButton);

        peerAvatar = findViewById(R.id.calleeAvatar);

        Bundle extras = getIntent().getExtras();
        if (extras != null) {
            // Jingle Message / Session sid
            mSid = extras.getString(CallManager.CALL_SID);
            NotificationPopupHandler.removeCallNotification(mSid);

            String eventType = extras.getString(CallManager.CALL_EVENT);
            isIncomingCall = NotificationManager.INCOMING_CALL.equals(eventType);
            mAutoAccept = extras.getBoolean(CallManager.CALL_AUTO_ACCEPT, false);
            if (mAutoAccept)
                return;

            Jid remote = JingleMessageSessionImpl.getRemote();
            ((TextView) findViewById(R.id.calleeAddress)).setText(remote);
            setPeerImage(remote);

            if (isIncomingCall) {
                // Call accepted, send Jingle Message <accept/> to inform caller.
                mCallButton.setOnClickListener(v -> {
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
                            finish();
                        }
                );
                mCallButton.setVisibility(View.GONE);
            }
        }
        JingleMessageSessionImpl.setJmEndListener(this);
    }

    @Override
    protected void onSaveInstanceState(@NotNull Bundle outState)
    {
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState)
    {
        super.onRestoreInstanceState(savedInstanceState);
    }

    @Override
    protected void onResume()
    {
        super.onResume();

        /*
         * JingleMessage propose will only sendJingleAccept(mSid); Then bring aTalk to foreground,
         * to avoid failure arises on launching ReceivedCallActivity from background
         */
        if (isIncomingCall && mAutoAccept) {
            JingleMessageSessionImpl.sendJingleAccept(mSid);
            finish();
            startActivity(aTalk.class);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event)
    {
        // Hangs up the call when back is pressed as this Activity will not be displayed again.
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            return true;
        }
        return super.onKeyUp(keyCode, event);
    }

    @Override
    public void onJmEndCallback()
    {
        finish();
    }

    /**
     * Sets the peer avatar.
     *
     * @param avatar the avatar of the callee
     */
    public void setPeerImage(Jid callee)
    {
        if (callee == null)
            return;

        byte[] avatar = AvatarManager.getAvatarImageByJid(callee.asBareJid());
        if ((avatar != null) && (avatar.length != 0)) {
            peerAvatar.setImageBitmap(AndroidImageUtil.bitmapFromBytes(avatar));
        }
    }
}