/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.android.gui.call;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.graphics.Color;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.SubMenu;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentOnAttachListener;

import java.awt.Dimension;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.Collection;
import java.util.EventObject;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.NoSuchElementException;

import net.java.sip.communicator.service.gui.call.CallPeerRenderer;
import net.java.sip.communicator.service.gui.call.CallRenderer;
import net.java.sip.communicator.service.protocol.Call;
import net.java.sip.communicator.service.protocol.CallConference;
import net.java.sip.communicator.service.protocol.CallPeer;
import net.java.sip.communicator.service.protocol.CallPeerState;
import net.java.sip.communicator.service.protocol.CallState;
import net.java.sip.communicator.service.protocol.OperationSetAdvancedTelephony;
import net.java.sip.communicator.service.protocol.event.CallChangeEvent;
import net.java.sip.communicator.service.protocol.event.CallChangeListener;
import net.java.sip.communicator.service.protocol.event.CallPeerEvent;
import net.java.sip.communicator.service.protocol.event.CallPeerSecurityNegotiationStartedEvent;
import net.java.sip.communicator.service.protocol.event.CallPeerSecurityOffEvent;
import net.java.sip.communicator.service.protocol.event.CallPeerSecurityOnEvent;
import net.java.sip.communicator.service.protocol.event.CallPeerSecurityTimeoutEvent;
import net.java.sip.communicator.service.protocol.media.MediaAwareCallPeer;
import net.java.sip.communicator.util.call.CallPeerAdapter;

import org.atalk.android.BaseActivity;
import org.atalk.android.R;
import org.atalk.android.aTalkApp;
import org.atalk.android.gui.aTalk;
import org.atalk.android.gui.actionbar.ActionBarUtil;
import org.atalk.android.gui.call.notification.CallControl;
import org.atalk.android.gui.call.notification.CallNotificationManager;
import org.atalk.android.gui.controller.AutoHideController;
import org.atalk.android.gui.util.ViewUtil;
import org.atalk.android.gui.widgets.ClickableToastController;
import org.atalk.android.gui.widgets.LegacyClickableToastCtrl;
import org.atalk.android.util.AppImageUtil;
import org.atalk.impl.appstray.NotificationPopupHandler;
import org.atalk.impl.neomedia.device.util.CameraUtils;
import org.atalk.impl.neomedia.jmfext.media.protocol.androidcamera.CameraStreamBase;
import org.atalk.impl.neomedia.transform.sdes.SDesControlImpl;
import org.atalk.service.neomedia.DtlsControl;
import org.atalk.service.neomedia.SDesControl;
import org.atalk.service.neomedia.SrtpControl;
import org.atalk.service.neomedia.SrtpControlType;
import org.atalk.service.neomedia.ZrtpControl;
import org.atalk.util.FullScreenHelper;
import org.atalk.util.MediaType;
import org.jetbrains.annotations.NotNull;
import org.jxmpp.jid.Jid;

import timber.log.Timber;

/**
 * The <code>VideoCallActivity</code> corresponds the call screen.
 *
 * @author Yana Stamcheva
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
public class VideoCallActivity extends BaseActivity implements CallPeerRenderer, CallRenderer,
        CallChangeListener, PropertyChangeListener, ZrtpInfoDialog.SasVerificationListener,
        AutoHideController.AutoHideListener, View.OnClickListener, View.OnLongClickListener,
        VideoHandlerFragment.OnRemoteVideoChangeListener, FragmentOnAttachListener {
    /**
     * Tag name for the fragment that handles proximity sensor in order to turn the screen on and off.
     */
    private static final String PROXIMITY_FRAGMENT_TAG = "proximity";

    /**
     * Tag name that identifies video handler fragment.
     */
    private static final String VIDEO_FRAGMENT_TAG = "video";

    /**
     * Tag name that identifies call timer fragment.
     */
    private static final String TIMER_FRAGMENT_TAG = "call_timer";

    /**
     * Tag name that identifies call control buttons auto hide controller fragment.
     */
    private static final String AUTO_HIDE_TAG = "auto_hide";

    /**
     * Tag for call volume control fragment.
     */
    private static final String VOLUME_CTRL_TAG = "call_volume_ctrl";

    /**
     * The delay for hiding the call control buttons, after the call has started
     */
    private static final long AUTO_HIDE_DELAY = 5000;

    /**
     * Call notification broadcast receiver for android-O
     */
    private BroadcastReceiver callNotificationControl = null;

    /**
     * The ZRTP SAS verification toast control panel.
     */
    private LegacyClickableToastCtrl sasToastControl;

    /**
     * Call volume control fragment instance.
     */
    private CallVolumeCtrlFragment callVolumeControl;

    private CallTimerFragment callTimer;

    /**
     * Auto-hide controller fragment for call control buttons. It is attached when remote video
     * covers most part of the screen.
     */
    private AutoHideController autoHideControl;

    /**
     * The call peer adapter that gives us access to all call peer events.
     */
    private CallPeerAdapter callPeerAdapter;

    /**
     * The corresponding call.
     */
    private Call mCall;

    /**
     * The call identifier managed by {@link CallManager}
     */
    private String mCallIdentifier;

    /**
     * Instance holds call state to be displayed in <code>VideoCallActivity</code> fragment.
     * Call objects will be no longer available after the call has ended.
     */
    static CallStateHolder callState = new CallStateHolder();

    /**
     * The {@link CallConference} instance depicted by this <code>CallPanel</code>.
     */
    private CallConference callConference;

    /**
     * Dialog displaying list of contacts for user selects to transfer the call to.
     */
    private CallTransferDialog mTransferDialog;

    /**
     * Flag to auto launch callTransfer dialog on resume if true
     */
    private Boolean callTransfer = false;

    private static VideoHandlerFragment videoFragment;

    /**
     * Indicates that the user has temporary back to chat window to send chat messages
     */
    private static boolean mBackToChat = false;

    /**
     * Flag for enable/disable DTMF handling
     */
    private boolean dtmfEnabled = true;

    private boolean micEnabled;

    /**
     * Flag indicates if the shutdown Thread has been started
     */
    private volatile boolean finishing = false;

    private FullScreenHelper fullScreenHelper;
    private ImageView peerAvatar;
    private ImageView microphoneButton;
    private ImageView speakerphoneButton;
    private View padlockGroupView;
    private TextView callEndReason;

    /**
     * Called when the activity is starting. Initializes the corresponding call interface.
     *
     * @param savedInstanceState If the activity is being re-initialized after previously being shut down then this
     * Bundle contains the data it most recently supplied in onSaveInstanceState(Bundle).
     * Note: Otherwise it is null.
     */
    @SuppressLint("SourceLockedOrientationActivity")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.call_video_audio);

        setScreenOn();
        fullScreenHelper = new FullScreenHelper(this);

        Bundle extras = getIntent().getExtras();
        if (extras != null) {
            mCallIdentifier = extras.getString(CallManager.CALL_SID);
            // End all call notifications in case any, once the call has started.
            NotificationPopupHandler.removeCallNotification(mCallIdentifier);

            mCall = CallManager.getActiveCall(mCallIdentifier);
            if (mCall == null) {
                Timber.e("There's no call with id: %s", mCallIdentifier);
                return;
            }
            // Check to see if launching call transfer dialog on resume has been requested
            callTransfer = extras.containsKey(CallManager.CALL_TRANSFER) && extras.getBoolean(CallManager.CALL_TRANSFER);

        }
        if (mCall == null)
            return;

        // Registers as the call state listener
        mCall.addCallChangeListener(this);
        callConference = mCall.getConference();

        // Initialize callChat button action
        findViewById(R.id.button_call_back_to_chat).setOnClickListener(this);

        // Initialize speakerphone button action
        speakerphoneButton = findViewById(R.id.button_speakerphone);
        speakerphoneButton.setOnClickListener(this);
        speakerphoneButton.setOnLongClickListener(this);

        // Initialize the microphone button view.
        microphoneButton = findViewById(R.id.button_call_microphone);
        microphoneButton.setOnClickListener(this);
        microphoneButton.setOnLongClickListener(this);
        micEnabled = aTalk.hasPermission(aTalk.getInstance(), false,
                aTalk.PRC_RECORD_AUDIO, Manifest.permission.RECORD_AUDIO);

        findViewById(R.id.button_call_hold).setOnClickListener(this);
        findViewById(R.id.button_call_hangup).setOnClickListener(this);
        findViewById(R.id.button_call_transfer).setOnClickListener(this);

        // set up clickable toastView for onSaveInstanceState in case phone rotate
        View toastView = findViewById(R.id.clickable_toast);
        sasToastControl = new ClickableToastController(toastView, this, R.id.clickable_toast);
        toastView.setOnClickListener(this);

        callEndReason = findViewById(R.id.callEndReason);
        callEndReason.setVisibility(View.GONE);

        peerAvatar = findViewById(R.id.calleeAvatar);
        mBackToChat = false;

        padlockGroupView = findViewById(R.id.security_group);
        padlockGroupView.setOnClickListener(this);

        FragmentManager fragmentManager = getSupportFragmentManager();
        fragmentManager.addFragmentOnAttachListener(this);

        if (savedInstanceState == null) {
            videoFragment = new VideoHandlerFragment();
            callVolumeControl = new CallVolumeCtrlFragment();
            callTimer = new CallTimerFragment();

            /*
             * Adds a fragment that turns on and off the screen when proximity sensor detects FAR/NEAR distance.
             */
            fragmentManager.beginTransaction()
                    .add(callVolumeControl, VOLUME_CTRL_TAG)
                    .add(new ProximitySensorFragment(), PROXIMITY_FRAGMENT_TAG)
                    /* Fragment that handles video display logic */
                    .add(videoFragment, VIDEO_FRAGMENT_TAG)
                    /* Fragment that handles call duration logic */
                    .add(callTimer, TIMER_FRAGMENT_TAG)
                    .commit();
        }
        else {
            // Retrieve restored auto hide fragment
            autoHideControl = (AutoHideController) fragmentManager.findFragmentByTag(AUTO_HIDE_TAG);
            callVolumeControl = (CallVolumeCtrlFragment) fragmentManager.findFragmentByTag(VOLUME_CTRL_TAG);
            callTimer = (CallTimerFragment) fragmentManager.findFragmentByTag(TIMER_FRAGMENT_TAG);
        }
        getOnBackPressedDispatcher().addCallback(backPressedCallback);
    }

    /**
     * Creates new video call intent for given <code>callIdentifier</code>.
     *
     * @param parent the parent <code>Context</code> that will be used to start new <code>Activity</code>.
     * @param callIdentifier the call ID managed by {@link CallManager}.
     *
     * @return new video call <code>Intent</code> parametrized with given <code>callIdentifier</code>.
     */
    public static Intent createVideoCallIntent(Context parent, String callIdentifier) {
        // Timber.d(new Exception("createVideoCallIntent: " + parent.getPackageName()));
        Intent videoCallIntent = new Intent(parent, VideoCallActivity.class);
        videoCallIntent.putExtra(CallManager.CALL_SID, callIdentifier);
        return videoCallIntent;
    }

    @Override
    public void onAttachFragment(@NonNull @NotNull FragmentManager fragmentManager, @NonNull @NotNull Fragment fragment) {
        // Timber.w("onAttachFragment Tag: %s", fragment);
        if (fragment instanceof VideoHandlerFragment) {
            ((VideoHandlerFragment) fragment).setRemoteVideoChangeListener(this);
        }
    }

    @Override
    protected void onSaveInstanceState(@NotNull Bundle outState) {
        if (sasToastControl != null)
            sasToastControl.onSaveInstanceState(outState);
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onRestoreInstanceState(@NonNull Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        if (sasToastControl != null)
            sasToastControl.onRestoreInstanceState(savedInstanceState);
    }

    /**
     * Reinitialize the <code>Activity</code> to reflect current call status.
     */
    @Override
    protected void onResume() {
        super.onResume();

        // Stop call broadcast receiver
        if (callNotificationControl != null) {
            aTalkApp.getInstance().unregisterReceiver(callNotificationControl);
            Timber.d("callNotificationControl unregistered: %s; %s", mCallIdentifier, callNotificationControl);
            callNotificationControl = null;
        }

        // Clears the in call notification
        if (CallNotificationManager.getInstanceFor(mCallIdentifier).isNotificationRunning()) {
            Timber.d("callNotificationControl hide notification panel: %s", mCallIdentifier);
            CallNotificationManager.getInstanceFor(mCallIdentifier).stopNotification();
        }

        // Call already ended or not found
        if (mCall == null)
            return;

        // To take care when the phone orientation is changed while call is in progress
        if (videoFragment == null)
            videoFragment = (VideoHandlerFragment) getSupportFragmentManager().findFragmentByTag("video");

        // Registers as the call state listener
        mCall.addCallChangeListener(this);

        // Checks if call peer has video component
        Iterator<? extends CallPeer> peers = mCall.getCallPeers();
        if (peers.hasNext()) {
            CallPeer callPeer = peers.next();
            addCallPeerUI(callPeer);
        }
        else {
            if (!callState.callEnded) {
                Timber.e("There aren't any peers in the call");
                finish();
            }
            return;
        }
        doUpdateHoldStatus();
        doUpdateMuteStatus();
        updateSpeakerphoneStatus();
        initSecurityStatus();

        if (callTransfer) {
            callTransfer = false;
            transferCall();
        }
    }

    /**
     * Called when this <code>Activity</code> is paused(hidden). Releases all listeners and leaves the
     * in call notification if the call is in progress.
     */
    @Override
    protected void onPause() {
        super.onPause();
        if (mCall == null)
            return;

        mCall.removeCallChangeListener(this);
        if (callPeerAdapter != null) {
            Iterator<? extends CallPeer> callPeerIter = mCall.getCallPeers();
            if (callPeerIter.hasNext()) {
                removeCallPeerUI(callPeerIter.next());
            }
            callPeerAdapter.dispose();
            callPeerAdapter = null;
        }
        if (mCall.getCallState() != CallState.CALL_ENDED) {
            mBackToChat = true;
            callNotificationControl = new CallControl();
            ContextCompat.registerReceiver(aTalkApp.getInstance(), callNotificationControl,
                    new IntentFilter(CallControl.CALL_CTRL_ACTION), ContextCompat.RECEIVER_NOT_EXPORTED);
            leaveNotification();
            Timber.d("callNotificationControl registered: %s: %s", mCallIdentifier, callNotificationControl);
        }
        else {
            mBackToChat = false;
        }
    }

    /*
     * Close the Call Transfer Dialog is shown; else close call UI
     */
    OnBackPressedCallback backPressedCallback = new OnBackPressedCallback(true) {
        @Override
        public void handleOnBackPressed() {
            if (mTransferDialog != null) {
                mTransferDialog.closeDialog();
                mTransferDialog = null;
            }
        }
    };

    /**
     * Called on call ended event. Runs on separate thread to release the EDT Thread and preview
     * surface can be hidden effectively.
     */
    private void doFinishActivity() {
        if (finishing)
            return;

        finishing = true;
        callNotificationControl = null;

        new Thread(() -> {
            // Waits for the camera to be stopped
            videoFragment.ensureCameraClosed();

            runOnUiThread(() -> {
                callState.callDuration = ViewUtil.getTextViewValue(findViewById(android.R.id.content), R.id.callTime);
                callState.callEnded = true;

                // Remove video fragment
                if (videoFragment != null) {
                    getSupportFragmentManager().beginTransaction().remove(videoFragment).commit();
                }
                // Remove auto hide fragment
                ensureAutoHideFragmentDetached();
                // !!! below is not working in kotlin code; merged with this activity
                // getSupportFragmentManager().beginTransaction().replace(android.R.id.content, new CallEnded()).commit();

                // auto exit 3 seconds after call ended successfully
                new Handler(Looper.getMainLooper()).postDelayed(this::finish, 5000);
            });
        }).start();
    }

    public static VideoHandlerFragment getVideoFragment() {
        return videoFragment;
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            onRemoteVideoChange((videoFragment != null) && videoFragment.isRemoteVideoVisible());
        }
    }

    @Override
    public void onRemoteVideoChange(boolean isRemoteVideoVisible) {
        if (isRemoteVideoVisible) {
            fullScreenHelper.enterFullScreen();
        }
        else {
            fullScreenHelper.exitFullScreen();
        }
    }

    /**
     * Handle buttons action events- the <code>ActionEvent</code> that notified us
     */
    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.button_call_back_to_chat:
                finish();
                break;

            case R.id.button_speakerphone:
                AudioManager audioManager = aTalkApp.getAudioManager();
                audioManager.setSpeakerphoneOn(!audioManager.isSpeakerphoneOn());
                updateSpeakerphoneStatus();
                break;

            case R.id.button_call_microphone:
                if (micEnabled)
                    CallManager.setMute(mCall, !isMuted());
                break;

            case R.id.button_call_hold:
                // call == null if call setup failed
                if (mCall != null)
                    CallManager.putOnHold(mCall, !isOnHold());
                break;

            case R.id.button_call_transfer:
                // call == null if call setup failed
                if (mCall != null)
                    transferCall();
                break;

            case R.id.button_call_hangup:
                // Start the hang up Thread, Activity will be closed later on call ended event
                if (mCall == null || CallState.CALL_ENDED == mCall.getCallState()) {
                    finish();
                }
                else {
                    CallManager.hangupCall(mCall);
                    setErrorReason(callState.errorReason);
                }
                break;

            case R.id.security_group:
                showZrtpInfoDialog();
                break;

            case R.id.clickable_toast:
                showZrtpInfoDialog();
                sasToastControl.hideToast(true);
                break;
        }
    }

    /**
     * Handle buttons longPress action events - the <code>ActionEvent</code> that notified us
     */
    @Override
    public boolean onLongClick(View v) {
        DialogFragment newFragment;
        switch (v.getId()) {
            // Create and show the volume control dialog.
            case R.id.button_speakerphone:
                // Create and show the dialog.
                newFragment = VolumeControlDialog.createOutputVolCtrlDialog();
                newFragment.show(getSupportFragmentManager(), "vol_ctrl_dialog");
                return true;

            // Create and show the mic gain control dialog.
            case R.id.button_call_microphone:
                if (micEnabled) {
                    newFragment = VolumeControlDialog.createInputVolCtrlDialog();
                    newFragment.show(getSupportFragmentManager(), "vol_ctrl_dialog");
                }
                return true;
        }
        return false;
    }

    /**
     * Transfers the given <tt>callPeer</tt>.
     */
    private void transferCall() {
        // If the telephony operation set is null we have nothing more to do here.
        OperationSetAdvancedTelephony<?> telephony
                = mCall.getProtocolProvider().getOperationSet(OperationSetAdvancedTelephony.class);
        if (telephony == null)
            return;

        // We support transfer for one-to-one calls only. next() => NoSuchElementException
        try {
            CallPeer initialPeer = mCall.getCallPeers().next();
            Collection<CallPeer> transferCalls = getTransferCallPeers();

            mTransferDialog = new CallTransferDialog(this, initialPeer, transferCalls);
            mTransferDialog.show();
        } catch (NoSuchElementException e) {
            Timber.w("Transferring call: %s", e.getMessage());
        }
    }

    /**
     * Returns the list of transfer call peers.
     *
     * @return the list of transfer call peers
     */
    private Collection<CallPeer> getTransferCallPeers() {
        Collection<CallPeer> transferCalls = new LinkedList<>();

        for (Call activeCall : CallManager.getInProgressCalls()) {
            // We're only interested in one to one calls
            if (!activeCall.equals(mCall) && (activeCall.getCallPeerCount() == 1)) {
                transferCalls.add(activeCall.getCallPeers().next());
            }
        }
        return transferCalls;
    }

    /**
     * Updates speakerphone button status.
     */
    private void updateSpeakerphoneStatus() {
        if (aTalkApp.getAudioManager().isSpeakerphoneOn()) {
            speakerphoneButton.setImageResource(R.drawable.call_speakerphone_on_dark);
            speakerphoneButton.setBackgroundColor(0x50000000);
        }
        else {
            speakerphoneButton.setImageResource(R.drawable.call_receiver_on_dark);
            speakerphoneButton.setBackgroundColor(Color.TRANSPARENT);
        }
    }

    /**
     * Returns <code>true</code> if call is currently muted.
     *
     * @return <code>true</code> if call is currently muted.
     */
    private boolean isMuted() {
        return CallManager.isMute(mCall);
    }

    private void updateMuteStatus() {
        runOnUiThread(this::doUpdateMuteStatus);
    }

    private void doUpdateMuteStatus() {
        if (!micEnabled || isMuted()) {
            microphoneButton.setImageResource(R.drawable.call_microphone_mute_dark);
            microphoneButton.setBackgroundColor(0x50000000);
        }
        else {
            microphoneButton.setImageResource(R.drawable.call_microphone_dark);
            microphoneButton.setBackgroundColor(Color.TRANSPARENT);
        }
    }

    /**
     * {@inheritDoc}
     */
    @SuppressLint("RestrictedApi")
    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        /*
         * The call to: setVolumeControlStream(AudioManager.STREAM_VOICE_CALL) doesn't work when
         * notification was being played during this Activity creation, so the buttons must be
         * captured, and the voice call level will be manipulated programmatically.
         */
        int action = event.getAction();
        int keyCode = event.getKeyCode();
        switch (keyCode) {
            case KeyEvent.KEYCODE_VOLUME_UP:
                if (action == KeyEvent.ACTION_UP) {
                    callVolumeControl.onKeyVolUp();
                }
                return true;
            case KeyEvent.KEYCODE_VOLUME_DOWN:
                if (action == KeyEvent.ACTION_DOWN) {
                    callVolumeControl.onKeyVolDown();
                }
                return true;
            default:
                return super.dispatchKeyEvent(event);
        }
    }

    /**
     * Leaves the in call notification.
     */
    private void leaveNotification() {
        CallNotificationManager.getInstanceFor(mCallIdentifier).showCallNotification(this);
    }

    /**
     * Sets the peer name.
     *
     * @param name the name of the call peer
     */
    public void setPeerName(final String name) {
        runOnUiThread(() -> {
            ActionBarUtil.setTitle(VideoCallActivity.this, getString(R.string.call_with));
            ActionBarUtil.setSubtitle(VideoCallActivity.this, name);
        });
    }

    /**
     * Sets the peer image.
     *
     * @param image the avatar of the call peer
     */
    public void setPeerImage(byte[] image) {
        if ((image != null) && (image.length != 0)) {
            peerAvatar.setImageBitmap(AppImageUtil.bitmapFromBytes(image));
        }
    }

    /**
     * Sets the peer state.
     *
     * @param oldState the old peer state
     * @param newState the new peer state
     * @param stateString the state of the call peer
     */
    public void setPeerState(CallPeerState oldState, CallPeerState newState, final String stateString) {
        runOnUiThread(() -> {
            TextView statusName = findViewById(R.id.callStatus);
            statusName.setText(stateString);
        });
    }

    /**
     * Ensures that auto hide fragment is added and started.
     */
    void ensureAutoHideFragmentAttached() {
        if (autoHideControl != null)
            return;

        this.autoHideControl = AutoHideController.getInstance(R.id.button_Container, AUTO_HIDE_DELAY);
        getSupportFragmentManager().beginTransaction().add(autoHideControl, AUTO_HIDE_TAG).commit();
    }

    /**
     * Removes the auto hide fragment, so that call control buttons will be always visible from now on.
     */
    public void ensureAutoHideFragmentDetached() {
        if (autoHideControl != null) {
            autoHideControl.show();

            getSupportFragmentManager().beginTransaction().remove(autoHideControl).commit();
            autoHideControl = null;
        }
    }

    /**
     * Shows (or cancels) the auto hide fragment.
     */
    @Override
    public void onUserInteraction() {
        super.onUserInteraction();

        if (autoHideControl != null)
            autoHideControl.show();
    }

    /**
     * Returns <code>CallVolumeCtrlFragment</code> if it exists or <code>null</code> otherwise.
     *
     * @return <code>CallVolumeCtrlFragment</code> if it exists or <code>null</code> otherwise.
     */
    public CallVolumeCtrlFragment getVolCtrlFragment() {
        return callVolumeControl;
    }

    public void setErrorReason(final String reason) {
        Timber.i("End call reason: %s", reason);
        runOnUiThread(() -> {
            callState.errorReason = reason;

            callEndReason.setText(reason);
            callEndReason.setVisibility(View.VISIBLE);
        });
    }

    public void setMute(boolean isMute) {
        // Just invoke mute UI refresh
        updateMuteStatus();
    }

    private boolean isOnHold() {
        boolean onHold = false;
        Iterator<? extends CallPeer> peers = mCall.getCallPeers();
        if (peers.hasNext()) {
            CallPeerState peerState = mCall.getCallPeers().next().getState();
            onHold = CallPeerState.ON_HOLD_LOCALLY.equals(
                    peerState) || CallPeerState.ON_HOLD_MUTUALLY.equals(peerState);
        }
        else {
            Timber.w("No peer belongs to call: %s", mCall.toString());
        }
        return onHold;
    }

    public void setOnHold(boolean isOnHold) {
    }

    /**
     * Updates on hold button to represent it's actual state
     */
    private void updateHoldStatus() {
        runOnUiThread(this::doUpdateHoldStatus);
    }

    /**
     * Updates on hold button to represent it's actual state. Called from
     * {@link #updateHoldStatus()}.
     */
    private void doUpdateHoldStatus() {
        final ImageView holdButton = findViewById(R.id.button_call_hold);
        if (isOnHold()) {
            holdButton.setImageResource(R.drawable.call_hold_on_dark);
            holdButton.setBackgroundColor(0x50000000);
        }
        else {
            holdButton.setImageResource(R.drawable.call_hold_off_dark);
            holdButton.setBackgroundColor(Color.TRANSPARENT);
        }
    }

    public void printDTMFTone(char dtmfChar) {
    }

    public CallRenderer getCallRenderer() {
        return this;
    }

    public void setLocalVideoVisible(final boolean isVisible) {
        // It cannot be hidden here, because the preview surface will be destroyed and camera
        // recording system will crash
    }

    public boolean isLocalVideoVisible() {
        return videoFragment.isLocalVideoVisible();
    }

    public Call getCall() {
        return mCall;
    }

    public CallPeerRenderer getCallPeerRenderer(CallPeer callPeer) {
        return this;
    }

    @Override
    public boolean onCreateOptionsMenu(@NonNull Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.video_call_menu, menu);

        // Add subMenu items for all supported resolutions
        SubMenu mSubMenuRes = menu.findItem(R.id.video_resolution).getSubMenu();
        for (Dimension res : CameraUtils.PREFERRED_SIZES) {
            String sResolution = ((int) res.getWidth()) + "x" + ((int) res.getHeight());
            mSubMenuRes.addSubMenu(0, R.id.video_dimension, Menu.NONE, sResolution);
        }

        // cmeng - hide menu item - not implemented
        MenuItem mMenuRes = menu.findItem(R.id.video_resolution);
        mMenuRes.setVisible(false);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.video_dimension:
                aTalkApp.showToastMessage("Not implemented!");
                return true;
            case R.id.call_info_item:
                showCallInfoDialog();
                return true;
            case R.id.call_zrtp_info_item:
                showZrtpInfoDialog();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    /**
     * Displays technical call information dialog.
     */
    private void showCallInfoDialog() {
        CallInfoDialogFragment callInfo = CallInfoDialogFragment.newInstance(
                getIntent().getStringExtra(CallManager.CALL_SID));
        callInfo.show(getSupportFragmentManager(), "callinfo");
    }

    /**
     * Displays ZRTP call information dialog.
     */
    private void showZrtpInfoDialog() {
        ZrtpInfoDialog zrtpInfo = ZrtpInfoDialog.newInstance(getIntent().getStringExtra(CallManager.CALL_SID));
        zrtpInfo.show(getSupportFragmentManager(), "zrtpinfo");
    }

    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        /*
         * If a Call is added to or removed from the CallConference depicted by this CallPanel, an
         * update of the view from its model will most likely be required.
         */
        if (CallConference.CALLS.equals(evt.getPropertyName()))
            onCallConferenceEventObject(evt);
    }

    @Override
    public void callPeerAdded(CallPeerEvent evt) {
        CallPeer callPeer = evt.getSourceCallPeer();
        addCallPeerUI(callPeer);
        onCallConferenceEventObject(evt);
    }

    @Override
    public void callPeerRemoved(CallPeerEvent evt) {
        CallPeer callPeer = evt.getSourceCallPeer();
        if (callPeerAdapter != null) {
            callPeer.addCallPeerListener(callPeerAdapter);
            callPeer.addCallPeerSecurityListener(callPeerAdapter);
            callPeer.addPropertyChangeListener(callPeerAdapter);
        }

        setPeerState(callPeer.getState(), callPeer.getState(), callPeer.getState().getLocalizedStateString());
        onCallConferenceEventObject(evt);
    }

    @Override
    public void callStateChanged(CallChangeEvent evt) {
        onCallConferenceEventObject(evt);
    }

    /**
     * Invoked by {@link CallChangeListener} to notify this instance about an <code>EventObject</code>
     * related to the <code>CallConference</code> depicted by this <code>CallPanel</code>, the
     * <code>Call</code>s participating in it, the <code>CallPeer</code>s associated with them, the
     * <code>ConferenceMember</code>s participating in any telephony conferences organized by them,
     * etc. In other words, notifies this instance about any change which may cause an update to
     * be required so that this view i.e. <code>CallPanel</code> depicts the current state of its
     * model i.e. {@link #callConference}.
     *
     * @param ev the <code>EventObject</code> this instance is being notified about.
     */
    private void onCallConferenceEventObject(EventObject ev) {
        /*
         * The main task is to invoke updateViewFromModel() in order to make sure that this view
         * depicts the current state of its model.
         */

        try {
            /*
             * However, we seem to be keeping track of the duration of the call (i.e. the
             * telephony conference) in the user interface. Stop the Timer which ticks the
             * duration of the call as soon as the telephony conference depicted by this instance
             * appears to have ended. The situation will very likely occur when a Call is
             * removed from the telephony conference or a CallPeer is removed from a Call.
             */
            boolean tryStopCallTimer = false;

            if (ev instanceof CallPeerEvent) {
                tryStopCallTimer = (CallPeerEvent.CALL_PEER_REMOVED == ((CallPeerEvent) ev).getEventID());
            }
            else if (ev instanceof PropertyChangeEvent) {
                PropertyChangeEvent pcev = (PropertyChangeEvent) ev;

                tryStopCallTimer = (CallConference.CALLS.equals(pcev.getPropertyName())
                        && (pcev.getOldValue() instanceof Call) && (pcev.getNewValue() == null));
            }

            if (tryStopCallTimer && (callConference.isEnded()
                    || callConference.getCallPeerCount() == 0)) {
                stopCallTimer();
                doFinishActivity();
            }
        } finally {
            updateViewFromModel(ev);
        }
    }

    /**
     * Starts the timer that counts call duration.
     */
    public void startCallTimer() {
        callTimer.startCallTimer();
    }

    /**
     * Stops the timer that counts call duration.
     */
    public void stopCallTimer() {
        callTimer.stopCallTimer();
    }

    /**
     * Returns {@code true} if the call timer has been started, otherwise returns {@code false}.
     *
     * @return {@code true} if the call timer has been started, otherwise returns {@code false}
     */
    public boolean isCallTimerStarted() {
        return callTimer.isCallTimerStarted();
    }

    private void addCallPeerUI(CallPeer callPeer) {
        callPeerAdapter = new CallPeerAdapter(callPeer, this);

        callPeer.addCallPeerListener(callPeerAdapter);
        callPeer.addCallPeerSecurityListener(callPeerAdapter);
        callPeer.addPropertyChangeListener(callPeerAdapter);

        setPeerState(null, callPeer.getState(), callPeer.getState().getLocalizedStateString());
        setPeerName(callPeer.getDisplayName());
        setPeerImage(CallUIUtils.getCalleeAvatar(mCall));
        callTimer.callPeerAdded(callPeer);

        // set for use by CallEnded
        callState.callPeer = callPeer.getPeerJid();
    }

    /**
     * Removes given <code>callPeer</code> from UI.
     *
     * @param callPeer the {@link CallPeer} to be removed from UI.
     */
    private void removeCallPeerUI(CallPeer callPeer) {
        callPeer.removeCallPeerListener(callPeerAdapter);
        callPeer.removeCallPeerSecurityListener(callPeerAdapter);
        callPeer.removePropertyChangeListener(callPeerAdapter);
    }

    private void updateViewFromModel(EventObject ev) {
    }

    public void updateHoldButtonState() {
        updateHoldStatus();
    }

    public void dispose() {
    }

    public void securityNegotiationStarted(CallPeerSecurityNegotiationStartedEvent securityStartedEvent) {
    }

    /**
     * Initializes current security status displays.
     */
    private void initSecurityStatus() {
        boolean isSecure = false;
        boolean isVerified = false;
        ZrtpControl zrtpCtrl;
        SrtpControlType srtpControlType = SrtpControlType.NULL;

        Iterator<? extends CallPeer> callPeers = mCall.getCallPeers();
        if (callPeers.hasNext()) {
            CallPeer cpCandidate = callPeers.next();
            if (cpCandidate instanceof MediaAwareCallPeer<?, ?, ?>) {
                MediaAwareCallPeer<?, ?, ?> mediaAwarePeer = (MediaAwareCallPeer<?, ?, ?>) cpCandidate;
                SrtpControl srtpCtrl = mediaAwarePeer.getMediaHandler().getEncryptionMethod(MediaType.AUDIO);
                isSecure = (srtpCtrl != null) && srtpCtrl.getSecureCommunicationStatus();

                if (srtpCtrl instanceof ZrtpControl) {
                    srtpControlType = SrtpControlType.ZRTP;
                    zrtpCtrl = (ZrtpControl) srtpCtrl;
                    isVerified = zrtpCtrl.isSecurityVerified();
                }
                else if (srtpCtrl instanceof SDesControl) {
                    srtpControlType = SrtpControlType.SDES;
                    isVerified = true;
                }
                else if (srtpCtrl instanceof DtlsControl) {
                    srtpControlType = SrtpControlType.DTLS_SRTP;
                    isVerified = true;
                }
            }
        }

        // Update padLock status and protocol name label (only if in secure mode) 
        doUpdatePadlockStatus(isSecure, isVerified);
        if (isSecure) {
            ViewUtil.setTextViewValue(findViewById(android.R.id.content), R.id.security_protocol,
                    srtpControlType.toString());
        }
    }

    /**
     * Updates padlock status text, icon and it's background color.
     *
     * @param isSecure <code>true</code> if the call is secured.
     * @param isVerified <code>true</code> if zrtp SAS string is verified.
     */
    @SuppressLint("ResourceAsColor")
    private void doUpdatePadlockStatus(boolean isSecure, boolean isVerified) {
        if (isSecure) {
            if (isVerified) {
                // Security on
                setPadlockColor(R.color.padlock_green);
            }
            else {
                // Security pending
                setPadlockColor(R.color.padlock_orange);
            }
            setPadlockSecure(true);
        }
        else {
            // Security off
            setPadlockColor(R.color.padlock_red);
            setPadlockSecure(false);
        }
    }

    /**
     * Sets the security padlock background color.
     *
     * @param colorId the color resource id that will be used.
     */
    private void setPadlockColor(int colorId) {
        padlockGroupView.setOnClickListener(this);

        int color = getResources().getColor(colorId, null);
        padlockGroupView.setBackgroundColor(color);
    }

    /**
     * Updates padlock icon based on security status.
     *
     * @param isSecure <code>true</code> if the call is secure.
     */
    private void setPadlockSecure(boolean isSecure) {
        ViewUtil.setImageViewIcon(findViewById(android.R.id.content), R.id.security_padlock,
                isSecure ? R.drawable.secure_on_dark : R.drawable.secure_off_dark);
    }

    /**
     * For ZRTP security
     * {@inheritDoc}
     */
    public void onSasVerificationChanged(boolean isVerified) {
        doUpdatePadlockStatus(true, isVerified);
    }

    /**
     * {@inheritDoc}
     */
    public void securityPending() {
        runOnUiThread(() -> doUpdatePadlockStatus(false, false));
    }

    /**
     * {@inheritDoc}
     */
    public void securityTimeout(CallPeerSecurityTimeoutEvent evt) {
        Timber.e("Security timeout: %s", evt.getSessionType());
    }

    /**
     * {@inheritDoc}
     */
    public void setSecurityPanelVisible(boolean visible) {
    }

    @Override
    public void setDtmfToneEnabled(boolean enabled) {
        dtmfEnabled = enabled;
    }

    @Override
    public boolean isDtmfToneEnabled() {
        return dtmfEnabled;
    }

    /**
     * {@inheritDoc}
     */
    public void securityOff(CallPeerSecurityOffEvent evt) {
        runOnUiThread(() -> doUpdatePadlockStatus(false, false));
    }

    /**
     * {@inheritDoc}
     */
    public void securityOn(final CallPeerSecurityOnEvent evt) {
        final SrtpControlType srtpControlType;
        final boolean isVerified;

        SrtpControl srtpCtrl = evt.getSecurityController();
        if (srtpCtrl instanceof ZrtpControl) {
            srtpControlType = SrtpControlType.ZRTP;
            isVerified = ((ZrtpControl) srtpCtrl).isSecurityVerified();
            if (!isVerified) {
                String toastMsg = getString(R.string.security_verify_toast);
                sasToastControl.showToast(false, toastMsg);
            }
        }
        else if (srtpCtrl instanceof SDesControlImpl) {
            srtpControlType = SrtpControlType.SDES;
            isVerified = true;
        }
        else if (srtpCtrl instanceof DtlsControl) {
            srtpControlType = SrtpControlType.DTLS_SRTP;
            isVerified = true;
        }
        else {
            isVerified = false;
            srtpControlType = SrtpControlType.NULL;
        }

        // Timber.d("SRTP Secure: %s = %s", isVerified, srtpControlType.toString());
        runOnUiThread(() -> {
            // Update both secure padLock status and protocol name
            doUpdatePadlockStatus(true, isVerified);
            ViewUtil.setTextViewValue(findViewById(android.R.id.content), R.id.security_protocol,
                    srtpControlType.toString());

        });
    }

    /**
     * Updates view alignment which depend on call control buttons group visibility state.
     * {@inheritDoc}
     */
    @Override
    public void onAutoHideStateChanged(AutoHideController source, int visibility) {
        // NPE from field report
        if (videoFragment != null)
            videoFragment.updateCallInfoMargin();
    }

    public static void setBackToChat(boolean state) {
        mBackToChat = state;
    }

    public static class CallStateHolder {
        Jid callPeer = null;
        String callDuration = "";
        String errorReason = "";
        boolean callEnded = false;
    }

    /*
     * This method requires the encoder to support auto-detect remote video size change.
     * App handling of device rotation during video call to:
     * a. Perform camera rotation for swap & flip, for properly video data transformation before sending
     * b. Update camera setDisplayOrientation(rotation)
     *
     * Note: If setRequestedOrientation() in the onCreate() cycle; this method will never get call even
     * it is defined in manifest android:configChanges="orientation|screenSize|screenLayout"
     */
    @Override
    public void onConfigurationChanged(@NotNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (mCall.getCallState() != CallState.CALL_ENDED) {
            // Must update aTalkApp isPortrait before calling; found to have race condition
            aTalkApp.isPortrait = (newConfig.orientation == Configuration.ORIENTATION_PORTRAIT);

            videoFragment.initVideoViewOnRotation();
            CameraStreamBase instance = CameraStreamBase.getInstance();
            if (instance != null)
                instance.initPreviewOnRotation(true);
        }
    }
}