/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.android.gui.call;

import android.Manifest;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;

import net.java.sip.communicator.service.protocol.Call;
import net.java.sip.communicator.service.protocol.CallState;
import net.java.sip.communicator.service.protocol.event.CallChangeEvent;
import net.java.sip.communicator.service.protocol.event.CallChangeListener;
import net.java.sip.communicator.service.protocol.event.CallPeerEvent;

import org.atalk.android.BaseActivity;
import org.atalk.android.R;
import org.atalk.android.gui.aTalk;
import org.atalk.impl.appstray.NotificationPopupHandler;

import timber.log.Timber;

/**
 * The <code>ReceivedCallActivity</code> is the activity that corresponds to the screen shown on incoming call.
 *
 * @author Yana Stamcheva
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
public class ReceivedCallActivity extends BaseActivity implements CallChangeListener {
    /**
     * The identifier of the call.
     */
    private String mSid;

    // Jingle Message incoming call parameters
    private boolean mAutoAccept;

    /**
     * The corresponding call.
     */
    private Call call;

    /**
     * Called when the activity is starting. Initializes the call identifier.
     *
     * @param savedInstanceState If the activity is being re-initialized after previously being shut down then this Bundle contains the
     * data it most recently supplied in onSaveInstanceState(Bundle). Note: Otherwise it is null.
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.call_received);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                | WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
                | WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
        );

        ImageView hangupView = findViewById(R.id.hangupButton);
        hangupView.setOnClickListener(v -> hangupCall());

        ImageView mCallButton = findViewById(R.id.callButton);
        mCallButton.setOnClickListener(v -> answerCall(call, false));

        // Proceed with video call only if camera permission is granted.
        ImageView mVideoCallButton = findViewById(R.id.videoCallButton);
        mVideoCallButton.setOnClickListener(v -> answerCall(call,
                aTalk.hasPermission(this, false, aTalk.PRC_CAMERA, Manifest.permission.CAMERA)));

        Bundle extras = getIntent().getExtras();
        Timber.d("ReceivedCall onCreate!!!");
        if (extras != null) {
            mSid = extras.getString(CallManager.CALL_SID);

            // Handling the incoming JingleCall
            call = CallManager.getActiveCall(mSid);
            if (call != null) {
                // call.setAutoAnswer(mAutoAccept);

                String Callee = CallUIUtils.getCalleeAddress(call);
                TextView addressView = findViewById(R.id.calleeAddress);
                addressView.setText(Callee);

                byte[] avatar = CallUIUtils.getCalleeAvatar(call);
                if (avatar != null) {
                    Bitmap bitmap = BitmapFactory.decodeByteArray(avatar, 0, avatar.length);
                    ImageView avatarView = findViewById(R.id.calleeAvatar);
                    avatarView.setImageBitmap(bitmap);
                }
            }
            else {
                Timber.e("There is no call with ID: %s", mSid);
                finish();
                return;
            }

            if (extras.getBoolean(CallManager.AUTO_ACCEPT, false))
                mVideoCallButton.performClick();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void onResume() {
        super.onResume();
        // Call is null for call via JingleMessage <propose/>
        if (call != null) {
            if (call.getCallState().equals(CallState.CALL_ENDED)) {
                finish();
            }
            else {
                call.addCallChangeListener(this);
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void onPause() {
        if (call != null) {
            call.removeCallChangeListener(this);
        }
        NotificationPopupHandler.removeCallNotification(mSid);
        super.onPause();
    }

    /**
     * Answers the given call and launches the call user interface.
     *
     * @param call the call to answer
     * @param isVideoCall indicates if video shall be usede
     */
    private void answerCall(final Call call, boolean isVideoCall) {
        CallManager.answerCall(call, isVideoCall);
        runOnUiThread(() -> {
            Intent videoCall = VideoCallActivity.createVideoCallIntent(ReceivedCallActivity.this, mSid);
            startActivity(videoCall);
            finish();
        });
    }

    /**
     * Hangs up the call and finishes this <code>Activity</code>.
     */
    private void hangupCall() {
        CallManager.hangupCall(call);
        finish();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        // Block the back key action to end this activity.
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            // hangupCall();
            return true;
        }
        return super.onKeyUp(keyCode, event);
    }

    /**
     * Indicates that a new call peer has joined the source call.
     *
     * @param evt the <code>CallPeerEvent</code> containing the source call and call peer.
     */
    public void callPeerAdded(CallPeerEvent evt) {

    }

    /**
     * Indicates that a call peer has left the source call.
     *
     * @param evt the <code>CallPeerEvent</code> containing the source call and call peer.
     */
    public void callPeerRemoved(CallPeerEvent evt) {

    }

    /**
     * Indicates that a change has occurred in the state of the source call.
     *
     * @param evt the <code>CallChangeEvent</code> instance containing the source calls and its old and new state.
     */
    public void callStateChanged(CallChangeEvent evt) {
        Object callState = evt.getNewValue();
        if (CallState.CALL_ENDED.equals(callState)) {
            finish();
        }
    }
}
