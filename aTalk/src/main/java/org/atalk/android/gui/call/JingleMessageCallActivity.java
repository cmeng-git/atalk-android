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
import android.view.*;
import android.widget.*;

import net.java.sip.communicator.plugin.notificationwiring.NotificationManager;

import org.atalk.android.R;
import org.atalk.android.gui.util.AndroidImageUtil;
import org.atalk.impl.androidtray.NotificationPopupHandler;
import org.atalk.service.osgi.OSGiActivity;
import org.jetbrains.annotations.NotNull;
import org.jivesoftware.smackx.avatar.AvatarManager;
import org.jxmpp.jid.Jid;

/**
 * The <tt>Jingle Message</tt> UI allowing caller to retract the call
 *
 * @author Eng Chong Meng
 */
public class JingleMessageCallActivity extends OSGiActivity implements JingleMessageHelper.CallEndListener
{
    private ImageView peerAvatar;

    /**
     * Jingle Message id
     */
    private String id;

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

        ImageButton callButton = findViewById(R.id.callButton);
        ImageButton hangUpButton = findViewById(R.id.hangupButton);

        peerAvatar = findViewById(R.id.calleeAvatar);

        Bundle extras = getIntent().getExtras();
        if (extras != null) {
            id = extras.getString(CallManager.CALL_SID);
            Jid callee = JingleMessageHelper.getCallee(id);

            ((TextView) findViewById(R.id.calleeAddress)).setText(callee);
            setPeerImage(callee);

            String eventType = extras.getString(CallManager.CALL_EVENT);
            if (NotificationManager.INCOMING_CALL.equals(eventType)) {
                // Call accepted, send Jingle Message <accept/> to inform caller.
                callButton.setOnClickListener(v -> {
                            NotificationPopupHandler.removeCallNotification(id);
                            JingleMessageHelper.sendJingleAccept(id);
                            finish();
                        }
                );

                // Call rejected, send Jingle Message <reject/> to inform caller.
                hangUpButton.setOnClickListener(v -> {
                            NotificationPopupHandler.removeCallNotification(id);
                            JingleMessageHelper.sendJingleMessageReject(id);
                            finish();
                        }
                );
            }
            else {
                // Call retract, send Jingle Message <retract/> to inform caller.
                hangUpButton.setOnClickListener(v -> {
                            JingleMessageHelper.sendJingleMessageRetract(id);
                            finish();
                        }
                );
                callButton.setVisibility(View.GONE);
            }
        }
        JingleMessageHelper.addFinishListener(this);
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

    public void onRejectCallback()
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