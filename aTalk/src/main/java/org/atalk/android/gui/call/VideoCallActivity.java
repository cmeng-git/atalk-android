/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.android.gui.call;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.media.AudioManager;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.FragmentManager;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.SubMenu;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import net.java.sip.communicator.service.gui.call.CallPeerRenderer;
import net.java.sip.communicator.service.gui.call.CallRenderer;
import net.java.sip.communicator.service.protocol.Call;
import net.java.sip.communicator.service.protocol.CallConference;
import net.java.sip.communicator.service.protocol.CallPeer;
import net.java.sip.communicator.service.protocol.CallPeerState;
import net.java.sip.communicator.service.protocol.CallState;
import net.java.sip.communicator.service.protocol.event.CallChangeEvent;
import net.java.sip.communicator.service.protocol.event.CallChangeListener;
import net.java.sip.communicator.service.protocol.event.CallPeerEvent;
import net.java.sip.communicator.service.protocol.event.CallPeerSecurityNegotiationStartedEvent;
import net.java.sip.communicator.service.protocol.event.CallPeerSecurityOffEvent;
import net.java.sip.communicator.service.protocol.event.CallPeerSecurityOnEvent;
import net.java.sip.communicator.service.protocol.event.CallPeerSecurityTimeoutEvent;
import net.java.sip.communicator.service.protocol.media.MediaAwareCallPeer;
import net.java.sip.communicator.util.Logger;
import net.java.sip.communicator.util.call.CallPeerAdapter;

import org.atalk.android.R;
import org.atalk.android.aTalkApp;
import org.atalk.android.gui.call.notification.CallNotificationManager;
import org.atalk.android.gui.controller.AutoHideController;
import org.atalk.android.gui.util.ActionBarUtil;
import org.atalk.android.gui.util.AndroidImageUtil;
import org.atalk.android.gui.util.AndroidUtils;
import org.atalk.android.gui.util.ViewUtil;
import org.atalk.android.gui.widgets.ClickableToastController;
import org.atalk.android.gui.widgets.LegacyClickableToastCtrl;
import org.atalk.android.util.java.awt.Dimension;
import org.atalk.impl.neomedia.device.util.CameraUtils;
import org.atalk.service.neomedia.MediaType;
import org.atalk.service.neomedia.SrtpControl;
import org.atalk.service.neomedia.ZrtpControl;
import org.atalk.service.osgi.OSGiActivity;
import org.jivesoftware.smackx.avatar.AvatarManager;
import org.jxmpp.jid.Jid;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.EventObject;
import java.util.Iterator;

/**
 * The <tt>VideoCallActivity</tt> corresponds the call screen.
 *
 * @author Yana Stamcheva
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
public class VideoCallActivity extends OSGiActivity implements CallPeerRenderer, CallRenderer,
        CallChangeListener, PropertyChangeListener, ZrtpInfoDialog.SasVerificationListener,
        AutoHideController.AutoHideListener
{
    /**
     * The logger
     */
    private static final Logger logger = Logger.getLogger(VideoCallActivity.class);

    /**
     * Tag name for fragment that handles proximity sensor in order to turn the screen on and off.
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
     * The delay for hiding the call control buttons, after the call has started
     */
    private static final long AUTO_HIDE_DELAY = 5000;

    /**
     * Tag for call volume control fragment.
     */
    private static final String VOLUME_CTRL_TAG = "call_volume_ctrl";

    /**
     * The call peer adapter that gives us access to all call peer events.
     */
    private CallPeerAdapter callPeerAdapter;

    /**
     * The corresponding call.
     */
    private Call call;

    /**
     * The {@link CallConference} instance depicted by this <tt>CallPanel</tt>.
     */
    private CallConference callConference;

    /**
     * Flag indicates if the shutdown Thread has been started
     */
    private volatile boolean finishing = false;

    /**
     * The call identifier managed by {@link CallManager}
     */
    private String callIdentifier;

    /**
     * The zrtp SAS verification toast controller.
     */
    private LegacyClickableToastCtrl sasToastController;

    /**
     * Auto-hide controller fragment for call control buttons. It is attached when remote video
     * covers most part of the screen.
     */
    private AutoHideController autoHide;

    /**
     * Call volume control fragment instance.
     */
    private CallVolumeCtrlFragment volControl;

    /**
     * Instance holds call state to be displayed in <tt>CallEnded</tt> fragment. Call objects will
     * be no longer available after the call has ended.
     */
    static CallStateHolder callState = new CallStateHolder();

    private VideoHandlerFragment videoFragment;

    private SubMenu mSubMenuRes;

    private ImageView peerAvatar;

    /**
     * Called when the activity is starting. Initializes the corresponding call interface.
     *
     * @param savedInstanceState
     *         If the activity is being re-initialized after previously being shut down then this
     *         Bundle contains the data it most recently supplied in onSaveInstanceState(Bundle).
     *         Note: Otherwise it is null.
     */
    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.video_call);
        this.callIdentifier = getIntent().getExtras().getString(CallManager.CALL_IDENTIFIER);
        call = CallManager.getActiveCall(callIdentifier);

        if (call == null) {
            logger.error("There's no call with id: " + callIdentifier);
            return;
        }

        callConference = call.getConference();
        initSpeakerphoneButton();
        initMicrophoneView();
        initHangupView();

        // Registers as the call state listener
        call.addCallChangeListener(this);
        View toastView = findViewById(R.id.clickable_toast);
        View.OnClickListener toastclickHandler = new View.OnClickListener()
        {
            public void onClick(View v)
            {
                showZrtpInfoDialog();
                sasToastController.hideToast(true);
            }
        };
        peerAvatar = findViewById(R.id.calleeAvatar);

        sasToastController = new ClickableToastController(toastView, toastclickHandler);
        if (savedInstanceState == null) {
            // VideoHandlerFragment videoFragment;
            if (AndroidUtils.hasAPI(18)) {
                videoFragment = new VideoHandlerFragmentAPI18();
            }
            else {
                videoFragment = new VideoHandlerFragment();
            }
            volControl = new CallVolumeCtrlFragment();

            /**
             * Adds fragment that turns on and off the screen when proximity sensor detects FAR/NEAR distance.
             */
            getSupportFragmentManager().beginTransaction().add(volControl,
                    VOLUME_CTRL_TAG).add(new ProximitySensorFragment(), PROXIMITY_FRAGMENT_TAG)
                    /* Adds the fragment that handles video display logic */
                    .add(videoFragment, VIDEO_FRAGMENT_TAG)
                    /* Adds the fragment that handles call duration logic */
                    .add(new CallTimerFragment(), TIMER_FRAGMENT_TAG).commit();
        }
        else {
            FragmentManager fragmentManager = getSupportFragmentManager();
            // Retrieve restored auto hide fragment
            autoHide = (AutoHideController) fragmentManager.findFragmentByTag(AUTO_HIDE_TAG);
            volControl = (CallVolumeCtrlFragment) fragmentManager.findFragmentByTag(VOLUME_CTRL_TAG);
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState)
    {
        if (sasToastController != null)
            sasToastController.onSaveInstanceState(outState);
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState)
    {
        super.onRestoreInstanceState(savedInstanceState);
        if (sasToastController != null)
            sasToastController.onRestoreInstanceState(savedInstanceState);
    }

    /**
     * Initializes the hangup button view.
     */
    private void initHangupView()
    {
        ImageView hangupView = findViewById(R.id.callHangupButton);
        hangupView.setOnClickListener(new View.OnClickListener()
        {
            public void onClick(View v)
            {
                // Start the hang up Thread, Activity will be closed later on call ended event
                if (call != null)
                    CallManager.hangupCall(call);
                // if call thread is null, then just exit the activity
                else
                    finish();
            }
        });
    }

    /**
     * Called on call ended event. Runs on separate thread to release the EDT Thread and preview
     * surface can be hidden effectively.
     */
    private void doFinishActivity()
    {
        if (finishing)
            return;

        finishing = true;
        new Thread(new Runnable()
        {
            public void run()
            {
                // Waits for camera to be stopped
                videoFragment.ensureCameraClosed();

                runOnUiThread(new Runnable()
                {
                    @Override
                    public void run()
                    {
                        callState.callDuration
                                = ViewUtil.getTextViewValue(findViewById(android.R.id.content), R.id.callTime);
                        callState.callEnded = true;

                        // Remove video fragment
                        if (videoFragment != null) {
                            getSupportFragmentManager().beginTransaction().remove(videoFragment).commit();
                        }
                        // Remove auto hide fragment
                        ensureAutoHideFragmentDetached();
                        getSupportFragmentManager().beginTransaction().replace(android.R.id.content,
                                new CallEnded()).commit();
                    }
                });
            }
        }).start();
    }

    /**
     * Initializes the microphone button view.
     */
    private void initMicrophoneView()
    {
        final ImageView microphoneButton = findViewById(R.id.callMicrophoneButton);
        microphoneButton.setOnClickListener(new View.OnClickListener()
        {
            public void onClick(View v)
            {
                CallManager.setMute(call, !isMuted());
            }
        });
        microphoneButton.setOnLongClickListener(new View.OnLongClickListener()
        {
            public boolean onLongClick(View view)
            {
                DialogFragment newFragment = VolumeControlDialog.createInputVolCtrlDialog();
                newFragment.show(getSupportFragmentManager(), "vol_ctrl_dialog");
                return true;
            }
        });
    }

    /**
     * Returns <tt>true</tt> if call is currently muted.
     *
     * @return <tt>true</tt> if call is currently muted.
     */
    private boolean isMuted()
    {
        return CallManager.isMute(call);
    }

    private void updateMuteStatus()
    {
        runOnUiThread(new Runnable()
        {
            public void run()
            {
                doUpdateMuteStatus();
            }
        });
    }

    private void doUpdateMuteStatus()
    {
        final ImageView microphoneButton = findViewById(R.id.callMicrophoneButton);

        if (isMuted()) {
            microphoneButton.setBackgroundColor(0x50000000);
            microphoneButton.setImageResource(R.drawable.callmicrophonemute_dark);
        }
        else {
            microphoneButton.setBackgroundColor(Color.TRANSPARENT);
            microphoneButton.setImageResource(R.drawable.callmicrophone_dark);
        }
    }

    /**
     * Initializes speakerphone button.
     */
    private void initSpeakerphoneButton()
    {
        View speakerphoneButton = findViewById(R.id.speakerphoneButton);
        speakerphoneButton.setOnLongClickListener(new View.OnLongClickListener()
        {
            @Override
            public boolean onLongClick(View v)
            {
                onCallVolumeClicked(v);
                return true;
            }
        });
    }

    /**
     * Fired when call volume control button is clicked.
     *
     * @param v
     *         the call volume control <tt>View</tt>.
     */
    public void onCallVolumeClicked(View v)
    {
        // Create and show the dialog.
        DialogFragment newFragment = VolumeControlDialog.createOutputVolCtrlDialog();
        newFragment.show(getSupportFragmentManager(), "vol_ctrl_dialog");
    }

    /**
     * Fired when speakerphone button is clicked.
     *
     * @param v
     *         the speakerphone button <tt>View</tt>.
     */
    public void onSpeakerphoneClicked(View v)
    {
        AudioManager audioManager = aTalkApp.getAudioManager();
        audioManager.setSpeakerphoneOn(!audioManager.isSpeakerphoneOn());
        updateSpeakerphoneStatus();
    }

    /**
     * Updates speakerphone button status.
     */
    private void updateSpeakerphoneStatus()
    {
        final ImageView speakerPhoneButton = findViewById(R.id.speakerphoneButton);

        if (aTalkApp.getAudioManager().isSpeakerphoneOn()) {
            speakerPhoneButton.setBackgroundColor(0x50000000);
        }
        else {
            speakerPhoneButton.setBackgroundColor(Color.TRANSPARENT);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean dispatchKeyEvent(KeyEvent event)
    {
        /**
         * The call to: setVolumeControlStream(AudioManager.STREAM_VOICE_CALL) doesn't work when
         * notification was being played during this Activity creation, so the buttons must be
         * captured and the voice call level will be manipulated programmatically.
         */
        int action = event.getAction();
        int keyCode = event.getKeyCode();
        switch (keyCode) {
            case KeyEvent.KEYCODE_VOLUME_UP:
                if (action == KeyEvent.ACTION_UP) {
                    volControl.onKeyVolUp();
                }
                return true;
            case KeyEvent.KEYCODE_VOLUME_DOWN:
                if (action == KeyEvent.ACTION_DOWN) {
                    volControl.onKeyVolDown();
                }
                return true;
            default:
                return super.dispatchKeyEvent(event);
        }
    }

    /**
     * Reinitialize the <tt>Activity</tt> to reflect current call status.
     */
    @Override
    protected void onResume()
    {
        super.onResume();

        // Clears the in call notification
        if (CallNotificationManager.get().isNotificationRunning(callIdentifier)) {
            CallNotificationManager.get().stopNotification(callIdentifier);

        }
        // Call already ended or not found
        if (call == null)
            return;

        // To take care when the phone orientation is changed while call is in progress
        if (videoFragment == null)
            videoFragment = (VideoHandlerFragment) getSupportFragmentManager().findFragmentByTag("video");

        // Registers as the call state listener
        call.addCallChangeListener(this);

        // Checks if call peer has video component
        Iterator<? extends CallPeer> peers = call.getCallPeers();
        if (peers.hasNext()) {
            CallPeer callPeer = peers.next();
            addCallPeerUI(callPeer);
        }
        else {
            if (!callState.callEnded) {
                logger.error("There aren't any peers in the call");
                finish();
            }
            return;
        }
        doUpdateHoldStatus();
        doUpdateMuteStatus();
        updateSpeakerphoneStatus();
        initSecurityStatus();
    }

    /**
     * Called when this <tt>Activity</tt> is paused(hidden). Releases all listeners and leaves the
     * in call notification if the call is in progress.
     */
    @Override
    protected void onPause()
    {
        super.onPause();

        if (call == null)
            return;

        call.removeCallChangeListener(this);

        if (callPeerAdapter != null) {
            Iterator<? extends CallPeer> callPeerIter = call.getCallPeers();
            if (callPeerIter.hasNext()) {
                removeCallPeerUI(callPeerIter.next());
            }
            callPeerAdapter.dispose();
            callPeerAdapter = null;
        }
        if (call.getCallState() != CallState.CALL_ENDED) {
            leaveNotification();
        }
    }

    /**
     * Leaves the in call notification.
     */
    private void leaveNotification()
    {
        CallNotificationManager.get().showCallNotification(this, callIdentifier);
    }

    /**
     * Sets the peer name.
     *
     * @param name
     *         the name of the call peer
     */
    public void setPeerName(final String name)
    {
        callState.callPeerName = name;
        runOnUiThread(new Runnable()
        {
            public void run()
            {
                ActionBarUtil.setTitle(VideoCallActivity.this,
                        getResources().getString(R.string.service_gui_CALL_WITH) + ": ");
                ActionBarUtil.setSubtitle(VideoCallActivity.this, name);
            }
        });
    }

    /**
     * Sets the peer image.
     *
     * @param peer
     *         the Jid of the call peer
     */
    public void setPeerImage(Jid peer)
    {
        setPeerImage(AvatarManager.getAvatarImageByJid(peer.asBareJid()));
    }

    /**
     * Sets the peer image.
     *
     * @param image
     *         the avatar of the call peer
     */
    public void setPeerImage(byte[] image)
    {
        if ((image != null) && (image.length != 0)) {
            peerAvatar.setImageBitmap(AndroidImageUtil.bitmapFromBytes(image));
        }
    }

    /**
     * Sets the peer state.
     *
     * @param oldState
     *         the old peer state
     * @param newState
     *         the new peer state
     * @param stateString
     *         the state of the call peer
     */
    public void setPeerState(CallPeerState oldState, CallPeerState newState, final String stateString)
    {
        runOnUiThread(new Runnable()
        {
            public void run()
            {
                TextView statusName = findViewById(R.id.callStatus);
                statusName.setText(stateString);
            }
        });
    }

    /**
     * Ensures that auto hide fragment is added and started.
     */
    void ensureAutoHideFragmentAttached()
    {
        if (autoHide != null)
            return;

        this.autoHide = AutoHideController.getInstance(R.id.button_Container, AUTO_HIDE_DELAY);
        getSupportFragmentManager().beginTransaction().add(autoHide, AUTO_HIDE_TAG).commit();
    }

    /**
     * Removes the auto hide fragment, so that call control buttons will be always visible from
     * now on.
     */
    public void ensureAutoHideFragmentDetached()
    {
        if (autoHide != null) {
            autoHide.show();

            getSupportFragmentManager().beginTransaction().remove(autoHide).commit();
            autoHide = null;
        }
    }

    /**
     * Shows (or cancels) the auto hide fragment.
     */
    @Override
    public void onUserInteraction()
    {
        super.onUserInteraction();

        if (autoHide != null)
            autoHide.show();
    }

    /**
     * Returns <tt>CallVolumeCtrlFragment</tt> if it exists or <tt>null</tt> otherwise.
     *
     * @return <tt>CallVolumeCtrlFragment</tt> if it exists or <tt>null</tt> otherwise.
     */
    public CallVolumeCtrlFragment getVolCtrlFragment()
    {
        return volControl;
    }

    public void setErrorReason(final String reason)
    {
        logger.info("Error reason: " + reason);

        runOnUiThread(new Runnable()
        {
            @Override
            public void run()
            {
                callState.errorReason = reason;

                TextView errorReason = findViewById(R.id.callErrorReason);
                if (errorReason != null) {
                    errorReason.setText(reason);
                    errorReason.setVisibility(View.VISIBLE);
                }
            }
        });
    }

    public void setMute(boolean isMute)
    {
        // Just invoke mute UI refresh
        updateMuteStatus();
    }

    /**
     * Method mapped to hold button view on click event
     *
     * @param holdButtonView
     *         the button view that has been clicked
     */
    public void onHoldButtonClicked(View holdButtonView)
    {
        // call == null if call setup failed
        if (call != null)
            CallManager.putOnHold(call, !isOnHold());
    }

    private boolean isOnHold()
    {
        boolean onHold = false;
        Iterator<? extends CallPeer> peers = call.getCallPeers();
        if (peers.hasNext()) {
            CallPeerState peerState = call.getCallPeers().next().getState();
            onHold = CallPeerState.ON_HOLD_LOCALLY.equals(
                    peerState) || CallPeerState.ON_HOLD_MUTUALLY.equals(peerState);
        }
        else {
            logger.warn("No peer belongs to call: " + call.toString());
        }
        return onHold;
    }

    public void setOnHold(boolean isOnHold)
    {
    }

    /**
     * Updates on hold button to represent it's actual state
     */
    private void updateHoldStatus()
    {
        runOnUiThread(new Runnable()
        {
            public void run()
            {
                doUpdateHoldStatus();
            }
        });
    }

    /**
     * Updates on hold button to represent it's actual state. Called from
     * {@link #updateHoldStatus()}.
     */
    private void doUpdateHoldStatus()
    {
        final ImageView holdButton = findViewById(R.id.callHoldButton);

        if (isOnHold()) {
            holdButton.setBackgroundColor(0x50000000);
        }
        else {
            holdButton.setBackgroundColor(Color.TRANSPARENT);
        }
    }

    public void printDTMFTone(char dtmfChar)
    {
    }

    public CallRenderer getCallRenderer()
    {
        return this;
    }

    public void setLocalVideoVisible(final boolean isVisible)
    {
        // It cannot be hidden here, because the preview surface will be destroyed and camera
        // recording system will crash
    }

// 	Dynamic get videoFragment can sometimes return null;
//	private VideoHandlerFragment getVideoFragment() {
//		return videoFragment;
//		// return (VideoHandlerFragment) getSupportFragmentManager().findFragmentByTag("video");
//	}

    public boolean isLocalVideoVisible()
    {
        return videoFragment.isLocalVideoVisible();
    }

    public Call getCall()
    {
        return call;
    }

    public CallPeerRenderer getCallPeerRenderer(CallPeer callPeer)
    {
        return this;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu)
    {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.video_call_menu, menu);

        // Add subMenu items for all supported resolutions
        mSubMenuRes = menu.findItem(R.id.video_resolution).getSubMenu();
        for (Dimension res : CameraUtils.PREFERRED_SIZES) {
            String sResolution = ((int) res.getWidth()) + "x" + ((int) res.getHeight());
            mSubMenuRes.addSubMenu(0, R.id.video_dimension, Menu.NONE, sResolution);
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item)
    {
        switch (item.getItemId()) {
            case R.id.video_dimension:
                // #TODO for actual action
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
    private void showCallInfoDialog()
    {
        CallInfoDialogFragment callInfo = CallInfoDialogFragment.newInstance(
                getIntent().getStringExtra(CallManager.CALL_IDENTIFIER));
        callInfo.show(getSupportFragmentManager(), "callinfo");
    }

    /**
     * Displays ZRTP call information dialog.
     */
    private void showZrtpInfoDialog()
    {
        ZrtpInfoDialog zrtpInfo = ZrtpInfoDialog.newInstance(
                getIntent().getStringExtra(CallManager.CALL_IDENTIFIER));
        zrtpInfo.show(getSupportFragmentManager(), "zrtpinfo");
    }

    @Override
    public void propertyChange(PropertyChangeEvent evt)
    {
        /**
         * If a Call is added to or removed from the CallConference depicted by this CallPanel, an
         * update of the view from its model will most likely be required.
         */
        if (CallConference.CALLS.equals(evt.getPropertyName()))
            onCallConferenceEventObject(evt);
    }

    @Override
    public void callPeerAdded(CallPeerEvent evt)
    {
        CallPeer callPeer = evt.getSourceCallPeer();
        addCallPeerUI(callPeer);
        onCallConferenceEventObject(evt);
    }

    @Override
    public void callPeerRemoved(CallPeerEvent evt)
    {
        CallPeer callPeer = evt.getSourceCallPeer();

        if (callPeerAdapter != null) {
            callPeer.addCallPeerListener(callPeerAdapter);
            callPeer.addCallPeerSecurityListener(callPeerAdapter);
            callPeer.addPropertyChangeListener(callPeerAdapter);
        }

        setPeerState(callPeer.getState(), callPeer.getState(),
                callPeer.getState().getLocalizedStateString());
        onCallConferenceEventObject(evt);
    }

    @Override
    public void callStateChanged(CallChangeEvent evt)
    {
        onCallConferenceEventObject(evt);
    }

    /**
     * Invoked by {@link CallChangeListener} to notify this instance about an <tt>EventObject</tt>
     * related to the <tt>CallConference</tt> depicted by this <tt>CallPanel</tt>, the
     * <tt>Call</tt>s participating in it, the <tt>CallPeer</tt>s associated with them, the
     * <tt>ConferenceMember</tt>s participating in any telephony conferences organized by them,
     * etc. In other words, notifies this instance about any change which may cause an update to
     * be required so that this view i.e. <tt>CallPanel</tt> depicts the current state of its
     * model i.e. {@link #callConference}.
     *
     * @param ev
     *         the <tt>EventObject</tt> this instance is being notified about.
     */
    private void onCallConferenceEventObject(EventObject ev)
    {
        /*
         * The main task is to invoke updateViewFromModel() in order to make sure that this view
		 * depicts the current state of its model.
		 */

        try {
            /**
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
     * Gets the <tt>CallTimerFragment</tt>.
     *
     * @return the <tt>CallTimerFragment</tt>.
     */
    private CallTimerFragment getCallTimerFragment()
    {
        return (CallTimerFragment) getSupportFragmentManager().findFragmentByTag(TIMER_FRAGMENT_TAG);
    }

    /**
     * Starts the timer that counts call duration.
     */
    public void startCallTimer()
    {
        if (getCallTimerFragment() != null)
            getCallTimerFragment().startCallTimer();
    }

    /**
     * Stops the timer that counts call duration.
     */
    public void stopCallTimer()
    {
        if (getCallTimerFragment() != null)
            getCallTimerFragment().stopCallTimer();
    }

    /**
     * Returns <code>true</code> if the call timer has been started, otherwise returns
     * <code>false</code>.
     *
     * @return <code>true</code> if the call timer has been started, otherwise returns
     * <code>false</code>
     */
    public boolean isCallTimerStarted()
    {
        return getCallTimerFragment() != null && getCallTimerFragment().isCallTimerStarted();
    }

    /**
     * {@inheritDoc}
     */
    public void onSasVerificationChanged(boolean isVerified)
    {
        doUpdatePadlockStatus(true, isVerified);
    }

    private void addCallPeerUI(CallPeer callPeer)
    {
        callPeerAdapter = new CallPeerAdapter(callPeer, this);

        callPeer.addCallPeerListener(callPeerAdapter);
        callPeer.addCallPeerSecurityListener(callPeerAdapter);
        callPeer.addPropertyChangeListener(callPeerAdapter);

        setPeerState(null, callPeer.getState(), callPeer.getState().getLocalizedStateString());
        setPeerName(callPeer.getDisplayName());
        setPeerImage(callPeer.getPeerJid());
        setPeerImage(callPeer.getPeerJid());
        getCallTimerFragment().callPeerAdded(callPeer);
    }

    /**
     * Removes given <tt>callPeer</tt> from UI.
     *
     * @param callPeer
     *         the {@link CallPeer} to be removed from UI.
     */
    private void removeCallPeerUI(CallPeer callPeer)
    {
        callPeer.removeCallPeerListener(callPeerAdapter);
        callPeer.removeCallPeerSecurityListener(callPeerAdapter);
        callPeer.removePropertyChangeListener(callPeerAdapter);
    }

    private void updateViewFromModel(EventObject ev)
    {
    }

    public void updateHoldButtonState()
    {
        updateHoldStatus();
    }

    public void dispose()
    {
    }

    public void securityNegotiationStarted(
            CallPeerSecurityNegotiationStartedEvent securityStartedEvent)
    {
    }

    /**
     * Initializes current security status displays.
     */
    private void initSecurityStatus()
    {
        boolean isSecure = false;
        boolean isVerified = false;
        ZrtpControl zrtpCtrl = null;

        Iterator<? extends CallPeer> callPeers = call.getCallPeers();
        if (callPeers.hasNext()) {
            CallPeer cpCandidate = callPeers.next();
            if (cpCandidate instanceof MediaAwareCallPeer<?, ?, ?>) {
                MediaAwareCallPeer<?, ?, ?> mediaAwarePeer
                        = (MediaAwareCallPeer<?, ?, ?>) cpCandidate;
                SrtpControl srtpCtrl
                        = mediaAwarePeer.getMediaHandler().getEncryptionMethod(MediaType.AUDIO);
                isSecure = srtpCtrl != null && srtpCtrl.getSecureCommunicationStatus();

                if (srtpCtrl instanceof ZrtpControl) {
                    zrtpCtrl = (ZrtpControl) srtpCtrl;
                    isVerified = zrtpCtrl.isSecurityVerified();
                }
                else {
                    isVerified = true;
                }
            }
        }
        // Protocol name label
        ViewUtil.setTextViewValue(findViewById(android.R.id.content), R.id.security_protocol,
                zrtpCtrl != null ? "zrtp" : "");
        doUpdatePadlockStatus(isSecure, isVerified);
    }

    /**
     * Updates padlock status text, icon and it's background color.
     *
     * @param isSecure
     *         <tt>true</tt> if the call is secured.
     * @param isVerified
     *         <tt>true</tt> if zrtp SAS string is verified.
     */
    @SuppressLint("ResourceAsColor")
    private void doUpdatePadlockStatus(boolean isSecure, boolean isVerified)
    {
        if (isSecure) {
            if (isVerified) {
                // Security on
                setPadlockColor(R.color.padlock_green);
                setPadlockSecure(true);
            }
            else {
                // Security pending
                setPadlockColor(R.color.padlock_orange);
                setPadlockSecure(true);
            }
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
     * @param colorId
     *         the color resource id that will be used.
     */
    private void setPadlockColor(int colorId)
    {
        View padlockGroup = findViewById(R.id.security_group);
        int color = getResources().getColor(colorId);
        padlockGroup.setBackgroundColor(color);
    }

    /**
     * Updates padlock icon based on security status.
     *
     * @param isSecure
     *         <tt>true</tt> if the call is secure.
     */
    private void setPadlockSecure(boolean isSecure)
    {
        ViewUtil.setImageViewIcon(findViewById(android.R.id.content), R.id.security_padlock,
                isSecure ? R.drawable.secure_on_dark : R.drawable.secure_off_dark);
    }

    /**
     * {@inheritDoc}
     */
    public void securityPending()
    {
        runOnUiThread(new Runnable()
        {
            public void run()
            {
                doUpdatePadlockStatus(false, false);
            }
        });
    }

    /**
     * {@inheritDoc}
     */
    public void securityTimeout(CallPeerSecurityTimeoutEvent evt)
    {
    }

    /**
     * {@inheritDoc}
     */
    public void setSecurityPanelVisible(boolean visible)
    {
    }

    /**
     * {@inheritDoc}
     */
    public void securityOff(CallPeerSecurityOffEvent evt)
    {
        runOnUiThread(new Runnable()
        {
            public void run()
            {
                doUpdatePadlockStatus(false, false);
            }
        });
    }

    /**
     * {@inheritDoc}
     */
    public void securityOn(final CallPeerSecurityOnEvent evt)
    {
        runOnUiThread(new Runnable()
        {
            public void run()
            {
                SrtpControl srtpCtrl = evt.getSecurityController();
                ZrtpControl zrtpControl = null;
                if (srtpCtrl instanceof ZrtpControl) {
                    zrtpControl = (ZrtpControl) srtpCtrl;
                }

                boolean isVerified = zrtpControl != null && zrtpControl.isSecurityVerified();
                doUpdatePadlockStatus(zrtpControl != null, isVerified);

                // Protocol name label
                ViewUtil.setTextViewValue(findViewById(android.R.id.content),
                        R.id.security_protocol, zrtpControl != null ? "zrtp" : "sdes");

                if (!isVerified && zrtpControl != null) {
                    String toastMsg = getString(R.string.service_gui_security_VERIFY_TOAST);
                    sasToastController.showToast(false, toastMsg);
                }
            }
        });
    }

    /**
     * Creates new video call intent for given <tt>callIdentifier</tt>.
     *
     * @param parent
     *         the parent <tt>Context</tt> that will be used to start new <tt>Activity</tt>.
     * @param callIdentifier
     *         the call ID managed by {@link CallManager}.
     * @return new video call <tt>Intent</tt> parametrized with given <tt>callIdentifier</tt>.
     */
    static public Intent createVideoCallIntent(Context parent, String callIdentifier)
    {
        Intent videoCallIntent = new Intent(parent, VideoCallActivity.class);
        videoCallIntent.putExtra(CallManager.CALL_IDENTIFIER, callIdentifier);
        VideoHandlerFragment.wasVideoEnabled = false;
        return videoCallIntent;
    }

    /**
     * Updates views alignment which depend on call control buttons group visibility state.
     * <p>
     * {@inheritDoc}
     */
    @Override
    public void onAutoHideStateChanged(AutoHideController source, int visibility)
    {
        videoFragment.updateCallInfoMargin();
    }

    static class CallStateHolder
    {
        // Call call;
        // CallPeer callPeer;
        String callPeerName = "";
        String callDuration = "";
        String errorReason = "";
        boolean callEnded = false;
    }
}