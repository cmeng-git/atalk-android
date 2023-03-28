/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.android.gui.call;

import static android.hardware.camera2.CameraMetadata.LENS_FACING_BACK;
import static android.hardware.camera2.CameraMetadata.LENS_FACING_FRONT;

import android.Manifest;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.ImageView;
import android.widget.RelativeLayout;

import androidx.annotation.Nullable;

import net.java.sip.communicator.service.protocol.Call;
import net.java.sip.communicator.service.protocol.CallPeer;
import net.java.sip.communicator.service.protocol.CallState;
import net.java.sip.communicator.service.protocol.OperationSetVideoTelephony;
import net.java.sip.communicator.service.protocol.ProtocolProviderService;

import org.atalk.android.R;
import org.atalk.android.aTalkApp;
import org.atalk.android.gui.aTalk;
import org.atalk.android.gui.controller.SimpleDragController;
import org.atalk.android.gui.util.AndroidUtils;
import org.atalk.impl.neomedia.codec.video.AndroidDecoder;
import org.atalk.impl.neomedia.device.DeviceConfiguration;
import org.atalk.impl.neomedia.device.util.AndroidCamera;
import org.atalk.impl.neomedia.device.util.CameraUtils;
import org.atalk.impl.neomedia.device.util.OpenGlCtxProvider;
import org.atalk.impl.neomedia.device.util.PreviewSurfaceProvider;
import org.atalk.impl.neomedia.device.util.ViewDependentProvider;
import org.atalk.impl.neomedia.jmfext.media.protocol.androidcamera.CameraStreamBase;
import org.atalk.service.neomedia.ViewAccessor;
import org.atalk.service.osgi.OSGiFragment;
import org.atalk.util.event.SizeChangeVideoEvent;
import org.atalk.util.event.VideoEvent;
import org.atalk.util.event.VideoListener;
import org.jetbrains.annotations.NotNull;

import java.awt.Component;
import java.awt.Dimension;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import timber.log.Timber;

/**
 * Fragment takes care of handling call UI parts related to the video - both local and remote.
 *
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
public class VideoHandlerFragment extends OSGiFragment implements View.OnLongClickListener
{
    /**
     * Default remote video view dimension (aTalk default) - must also be valid for OpenGL else crash
     * Note: Other dimension ratio e.g. (1x1) will cause Invalid Operation in OpenGL
     * Static variable must only be init in constructor for android Fragment
     *
     * Assuming the received video is in portrait and using aTalk default
     */
    @SuppressWarnings("SuspiciousNameCombination")
    protected static int DEFAULT_WIDTH = DeviceConfiguration.DEFAULT_VIDEO_HEIGHT;
    @SuppressWarnings("SuspiciousNameCombination")
    protected static int DEFAULT_HEIGHT = DeviceConfiguration.DEFAULT_VIDEO_WIDTH;

    // Default local preview width
    private static final int DEFAULT_PREVIEW_WIDTH = 160;

    /**
     * The callee avatar.
     */
    private ImageView calleeAvatar;

    /**
     * The remote video container.
     */
    private RemoteVideoLayout remoteVideoContainer;

    /**
     * The remote video view
     */
    private ViewAccessor remoteVideoAccessor;

    /**
     * Container used for local preview
     */
    protected ViewGroup localPreviewContainer;

    /**
     * The preview surface state handler
     */
    public PreviewSurfaceProvider localPreviewSurface;

    /**
     * <code>OpenGlCtxProvider</code> that provides Open GL context for local preview rendering. It is
     * used in direct surface encoding mode.
     */
    public OpenGlCtxProvider mLocalPreviewGlCtxProvider;

    private ViewDependentProvider<?> currentPreviewProvider;

    /**
     * Instance of video listener that should be unregistered once this Activity is destroyed
     */
    private VideoListener callPeerVideoListener;

    /**
     * Stores local video state when <code>CallActivity</code> is paused i.e. back-to-chat, and is used to start content-modify
     * for new local video streaming when the call is resumed. Screen rotation uses isLocalVideoEnabled() to re-init local video
     */
    private static final Map<String, Boolean> mVideoLocalLastState = new HashMap<>();

    private static boolean isCameraEnable = false;

    /**
     * Indicate phone orientation change and need to init RemoteVideoContainer
     */
    private boolean initOnPhoneOrientationChange = false;

    /**
     * The call for which this fragment is handling video events.
     */
    private Call mCall;

    /**
     * The thread that switches the camera.
     */
    private Thread cameraSwitchThread;

    /**
     * Call info group
     */
    private ViewGroup callInfoGroup;

    /**
     * Call control buttons group.
     */
    private View ctrlButtonsGroup;

    /**
     * Local video call button.
     */
    private ImageView mCallVideoButton;

    /**
     * For long press to toggle between front and back (full screen display - not shown option in Android 8.0)
     */
    private MenuItem mCameraToggle;

    /**
     * VideoHandlerFragment parent activity for the callback i.e. VideoCallActivity
     */
    private VideoCallActivity mCallActivity;

    /**
     * Create a new instance of <code>VideoHandlerFragment</code>.
     */
    public VideoHandlerFragment()
    {
        setHasOptionsMenu(true);
    }

    /**
     * Must be called by parent activity on fragment attached
     *
     * @param activity VideoCall Activity
     */
    public void setRemoteVideoChangeListener(VideoCallActivity activity)
    {
        mCallActivity = activity;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        remoteVideoContainer = mCallActivity.findViewById(R.id.remoteVideoContainer);
        localPreviewContainer = mCallActivity.findViewById(R.id.localPreviewContainer);
        callInfoGroup = mCallActivity.findViewById(R.id.callInfoGroup);
        ctrlButtonsGroup = mCallActivity.findViewById(R.id.button_Container);

        // (must be done after layout or 0 sizes will be returned)
        ctrlButtonsGroup.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener()
        {
            @Override
            public void onGlobalLayout()
            {
                // We know the size of all components at this point, so we can init layout
                // dependent stuff. Initial call info margin adjustment
                updateCallInfoMargin();

                // Remove the listener, as it has to be called only once
                ctrlButtonsGroup.getViewTreeObserver().removeOnGlobalLayoutListener(this);
            }
        });

        isCameraEnable = aTalk.hasPermission(aTalk.getInstance(), false,
                aTalk.PRC_CAMERA, Manifest.permission.CAMERA);
        calleeAvatar = mCallActivity.findViewById(R.id.calleeAvatar);
        mCallVideoButton = mCallActivity.findViewById(R.id.button_call_video);

        mCallVideoButton.setOnClickListener(this::onLocalVideoButtonClicked);
        if (isCameraEnable) {
            mCallVideoButton.setOnLongClickListener(this);
        }
        mCall = mCallActivity.getCall();

        // Creates and registers surface handler for events
        localPreviewSurface = new PreviewSurfaceProvider(mCallActivity, localPreviewContainer, true);
        CameraUtils.setPreviewSurfaceProvider(localPreviewSurface);
        // Makes the local preview window draggable on the screen
        localPreviewContainer.setOnTouchListener(new SimpleDragController());

        /*
         * Initialize android hardware encoder and decoder Preview and surfaceProvider;
         * use only if hardware encoder/decoder are enabled.
         *
         * See Constants#ANDROID_SURFACE, DataSource#AbstractPushBufferStream and AndroidEncoder#isDirectSurfaceEnabled
         * on conditions for the selection and use of this stream in SurfaceStream setup
         * i.e. AndroidEncoder#DIRECT_SURFACE_ENCODE_PROPERTY must be true
         */
        mLocalPreviewGlCtxProvider = new OpenGlCtxProvider(mCallActivity, localPreviewContainer);
        AndroidDecoder.renderSurfaceProvider = new PreviewSurfaceProvider(mCallActivity, remoteVideoContainer, false);

        // Make the remote preview display draggable on the screen - not applicable in aTalk default full screen mode
        // remoteVideoContainer.setOnTouchListener(new SimpleDragController());
    }

    @Override
    public void onResume()
    {
        super.onResume();
        if (mCall == null) {
            Timber.e("Call is null");
            return;
        }

        if (Boolean.TRUE.equals(mVideoLocalLastState.get(mCall.getCallId())))
            setLocalVideoEnabled(true);

        // Checks if call peer has video component
        Iterator<? extends CallPeer> peers = mCall.getCallPeers();
        if (peers.hasNext()) {
            CallPeer callPeer = peers.next();
            addVideoListener(callPeer);
            initRemoteVideo(callPeer);
        }
        else {
            Timber.e("There aren't any peers in the call");
        }
    }

    /**
     * Restores local video state if it was enabled or on first video call entry; The local preview size is
     * configured to be proportional to the actually camera captured video dimension with the default width.
     * The localPreview can either be localPreviewSurface or mLocalPreviewGlCtxProvider
     *
     * Note: runOnUiThread(0 for view as this may be call from non-main thread
     */
    public void initLocalPreviewContainer(ViewDependentProvider<?> provider)
    {
        Timber.d("init Local Preview Container %s (%s)", mVideoLocalLastState.get(mCall.getCallId()), provider);
        if (provider != null) {
            currentPreviewProvider = provider;
            RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams) localPreviewContainer.getLayoutParams();
            Dimension videoSize = provider.getVideoSize();
            // Local preview size has default fixed width of 160 (landscape mode)
            float scale = getResources().getDisplayMetrics().density * DEFAULT_PREVIEW_WIDTH / videoSize.width;
            Dimension previewSize;
            if (aTalkApp.isPortrait) {
                previewSize = new Dimension(videoSize.height, videoSize.width);
            }
            else {
                previewSize = videoSize;
            }
            params.width = (int) (previewSize.width * scale + 0.5);
            params.height = (int) (previewSize.height * scale + 0.5);

            runOnUiThread(() -> {
                localPreviewContainer.setLayoutParams(params);
                provider.setAspectRatio(params.width, params.height);
            });
            Timber.d("SurfaceView instance Size: [%s x %s]; %s", params.width, params.height, provider.getView());
        }
        // Set proper videoCallButtonState and restore local video
        initLocalVideoState(true);
    }

    @Override
    public void onPause()
    {
        super.onPause();

        // Make sure to join the switch camera thread
        if (cameraSwitchThread != null) {
            try {
                cameraSwitchThread.join();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
        if (mCall == null) {
            Timber.e("Call is null");
            return;
        }

        removeVideoListener();
        if (mCall.getCallState() != CallState.CALL_ENDED) {
            // save a copy of the local video state for use when the call is resumed
            mVideoLocalLastState.put(mCall.getCallId(), isLocalVideoEnabled());

            /*
             * Disables local video to stop the camera and release the surface.
             * Otherwise, media recorder will crash on invalid preview surface.
             * 20210306 - must setLocalVideoEnabled(false) to restart camera after screen orientation changed
             */
            setLocalVideoEnabled(false);

            localPreviewSurface.waitForObjectRelease();
            // TODO: release object on rotation, but the data source have to be paused
            // remoteSurfaceHandler.waitForObjectRelease();
            //}
        }
        else {
            mVideoLocalLastState.remove(mCall.getCallId());
        }
    }

    @Override
    public void onDestroy()
    {
        super.onDestroy();
        // Release shared video component
        remoteVideoContainer.removeAllViews();
        mLocalPreviewGlCtxProvider = null;
    }

    @Override
    public void onCreateOptionsMenu(@NotNull Menu menu, @NotNull MenuInflater inflater)
    {
        super.onCreateOptionsMenu(menu, inflater);

        AndroidCamera selectedCamera = AndroidCamera.getSelectedCameraDevInfo();
        if (!isCameraEnable || selectedCamera == null) {
            return;
        }

        // Check and set camera option with other facing from current system if available
        boolean isFrontCamera = (selectedCamera.getCameraFacing() == AndroidCamera.FACING_FRONT);
        int otherFacing = isFrontCamera ? AndroidCamera.FACING_BACK : AndroidCamera.FACING_FRONT;

        if (AndroidCamera.getCameraFromCurrentDeviceSystem(otherFacing) != null) {
            inflater.inflate(R.menu.camera_menu, menu);
            String displayName = isFrontCamera
                    ? getString(R.string.service_gui_settings_USE_BACK_CAMERA)
                    : getString(R.string.service_gui_settings_USE_FRONT_CAMERA);
            mCameraToggle = menu.findItem(R.id.switch_camera).setTitle(displayName);
        }
    }

    /**
     * Switch to alternate camera on the device when user toggles the camera
     *
     * @param item the user clicked menu item
     * @return return true is activation is from menu item R.id.switch_camera
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item)
    {
        if (item.getItemId() == R.id.switch_camera) {
            startCameraSwitchThread(item);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    /**
     * Long press camera icon changes to alternate camera available on the device.
     *
     * @param v the clicked view
     * @return return true is activation is from R.id.button_call_video
     */
    @Override
    public boolean onLongClick(View v)
    {
        if (v.getId() == R.id.button_call_video) {
            // Do not proceed if no alternate camera (i.e. mCameraToggle == null) is available on the device
            if (mCameraToggle != null) {
                aTalkApp.showToastMessage(mCameraToggle.getTitle().toString());
                startCameraSwitchThread(mCameraToggle);
            }
            return true;
        }
        return false;
    }

    /**
     * Toggle the camera device in separate thread and update the menu title text
     *
     * @param item Menu Item
     */
    private void startCameraSwitchThread(MenuItem item)
    {
        // Ignore action if camera switching is in progress
        if (cameraSwitchThread != null)
            return;

        final AndroidCamera newDevice;

        String back = getString(R.string.service_gui_settings_USE_BACK_CAMERA);
        if (item.getTitle().equals(back)) {
            // Switch to back camera and toggle item name
            newDevice = AndroidCamera.getCameraFromCurrentDeviceSystem(LENS_FACING_BACK);
            item.setTitle(R.string.service_gui_settings_USE_FRONT_CAMERA);
        }
        else {
            // Switch to front camera and toggle item name
            newDevice = AndroidCamera.getCameraFromCurrentDeviceSystem(LENS_FACING_FRONT);
            item.setTitle(back);
        }

        // Timber.w("New Camera selected: %s", newDevice.getName());
        // Switch the camera in separate thread
        cameraSwitchThread = new Thread()
        {
            @Override
            public void run()
            {
                if (newDevice != null) {
                    CameraStreamBase instance = CameraStreamBase.getInstance();
                    instance.switchCamera(newDevice.getLocator(), isLocalVideoEnabled());

                    // Keep track of created threads
                    cameraSwitchThread = null;
                }
            }
        };
        cameraSwitchThread.start();
    }

    /**
     * Called when local video button is pressed. Give user feedback if camera not enabled
     *
     * @param callVideoButton local video button <code>View</code>.
     */
    private void onLocalVideoButtonClicked(View callVideoButton)
    {
        if (aTalk.isMediaCallAllowed(true)) {
            initLocalVideoState(!isLocalVideoEnabled());
        }
    }

    /**
     * Initialize the Call Video Button to its proper state
     */
    private void initLocalVideoState(boolean isVideoEnable)
    {
        setLocalVideoEnabled(isVideoEnable);
        if (!isCameraEnable) {
            mCallVideoButton.setImageResource(R.drawable.call_video_no_dark);
            mCallVideoButton.setBackgroundColor(android.graphics.Color.TRANSPARENT);
        }
        else if (isVideoEnable) {
            mCallVideoButton.setImageResource(R.drawable.call_video_record_dark);
            mCallVideoButton.setBackgroundColor(0x50000000);
        }
        else {
            mCallVideoButton.setImageResource(R.drawable.call_video_dark);
            mCallVideoButton.setBackgroundColor(android.graphics.Color.TRANSPARENT);
        }
    }

    /**
     * Checks local video status.
     *
     * @return <code>true</code> if local video is enabled.
     */
    public boolean isLocalVideoEnabled()
    {
        return CallManager.isLocalVideoEnabled(mCall);
    }

    /**
     * Sets local video status.
     *
     * @param enable flag indicating local video status to be set.
     */
    private void setLocalVideoEnabled(boolean enable)
    {
        if (mCall == null) {
            Timber.e("Call instance is null (the call has ended already?)");
            return;
        }
        CallManager.enableLocalVideo(mCall, enable);
    }

    // ============ Remote video view handler ============

    /**
     * Adds a video listener for the given call peer.
     *
     * @param callPeer the <code>CallPeer</code> to which we add a video listener
     */
    private void addVideoListener(final CallPeer callPeer)
    {
        ProtocolProviderService pps = callPeer.getProtocolProvider();
        if (pps == null)
            return;

        OperationSetVideoTelephony osvt = pps.getOperationSet(OperationSetVideoTelephony.class);
        if (osvt == null)
            return;

        if (callPeerVideoListener == null) {
            callPeerVideoListener = new VideoListener()
            {
                public void videoAdded(VideoEvent event)
                {
                    handleVideoEvent(callPeer, event);
                }

                public void videoRemoved(VideoEvent event)
                {
                    // Timber.w(new Exception(), "Call Peer: %s; event: %s", callPeer, event);
                    handleVideoEvent(callPeer, event);
                }

                public void videoUpdate(VideoEvent event)
                {
                    handleVideoEvent(callPeer, event);
                }
            };
        }
        osvt.addVideoListener(callPeer, callPeerVideoListener);
    }

    /**
     * Handles a video event.
     *
     * @param callPeer the corresponding call peer
     * @param event the <code>VideoEvent</code> that notified us
     */
    public void handleVideoEvent(CallPeer callPeer, final VideoEvent event)
    {
        if (event.isConsumed())
            return;
        event.consume();

        /*
         * VideoEvent.LOCAL: local video events are not handled here because the preview is required for the
         * camera to start, and it must not be removed until is stopped, so it's handled by directly
         */
        if (event.getOrigin() == VideoEvent.REMOTE) {
            int eventType = event.getType();
            Component visualComponent = ((eventType == VideoEvent.VIDEO_ADDED)
                    || (eventType == VideoEvent.VIDEO_SIZE_CHANGE))
                    ? event.getVisualComponent() : null;

            SizeChangeVideoEvent scve = (eventType == VideoEvent.VIDEO_SIZE_CHANGE)
                    ? (SizeChangeVideoEvent) event : null;

            Timber.d("handleVideoEvent %s; %s", eventType, visualComponent);
            handleRemoteVideoEvent(visualComponent, scve);
        }
    }

    /**
     * Removes remote video listener.
     */
    private void removeVideoListener()
    {
        Iterator<? extends CallPeer> calPeers = mCall.getCallPeers();
        if (calPeers.hasNext()) {
            CallPeer callPeer = calPeers.next();

            ProtocolProviderService pps = mCall.getProtocolProvider();
            if (pps == null)
                return;

            OperationSetVideoTelephony osvt = pps.getOperationSet(OperationSetVideoTelephony.class);
            if (osvt == null)
                return;

            if (callPeerVideoListener != null) {
                osvt.removeVideoListener(callPeer, callPeerVideoListener);
            }
        }
    }

    /**
     * Initializes remote video for the call. Visual component is always null on initial setup;
     * but non-null on phone rotate: Need to re-init remote video on screen rotation. However device
     * rotation is currently handled by onConfigurationChanged, so handleRemoteVideoEvent will not be called
     *
     * Let remote handleVideoEvent triggers the initial setup.
     * Multiple quick access to GLSurfaceView can cause problem.
     *
     * @param callPeer owner of video object.
     */
    private void initRemoteVideo(CallPeer callPeer)
    {
        ProtocolProviderService pps = callPeer.getProtocolProvider();
        Component visualComponent = null;

        if (pps != null) {
            OperationSetVideoTelephony osvt = pps.getOperationSet(OperationSetVideoTelephony.class);
            if (osvt != null)
                visualComponent = osvt.getVisualComponent(callPeer);
        }

        if (visualComponent != null) {
            initOnPhoneOrientationChange = true;
            handleRemoteVideoEvent(visualComponent, null);
        }
    }

    /**
     * Handles the remote video event.
     *
     * @param visualComponent the remote video <code>Component</code> if available or <code>null</code> otherwise.
     * visualComponent is null on video call initial setup and on remote video removed.
     * No null on the remote device rotated; need to re-init remote video on screen rotation
     * @param scvEvent the <code>SizeChangeVideoEvent</code> event if was supplied.
     */
    private void handleRemoteVideoEvent(final Component visualComponent, final SizeChangeVideoEvent scvEvent)
    {
        if (visualComponent instanceof ViewAccessor) {
            remoteVideoAccessor = (ViewAccessor) visualComponent;
        }
        else {
            remoteVideoAccessor = null;
            // null visualComponent evaluates to false, so need to check here before warn
            // Report component is not compatible
            if (visualComponent != null) {
                Timber.e("Remote video component is not Android compatible.");
            }
        }

        // Update window full screen visibility only in UI
        runOnUiThread(() -> {
            mCallActivity.onRemoteVideoChange(remoteVideoAccessor != null);

            if (remoteVideoAccessor != null) {
                View view = remoteVideoAccessor.getView(mCallActivity);
                Dimension preferredSize = selectRemotePreferredSize(visualComponent, view, scvEvent);
                doAlignRemoteVideo(view, preferredSize);
            }

            /*
             * (20181228) cmeng: New implementation will not trigger content remove and content add when toggle local camera
             * Remove remote view container and realign display will happen when:
             * a. The remote camera is toggled off/on
             * b. The remote camera (phone) rotation changed (20210308 - S
             * as the action causes stream Timeout event, then followed by receipt of new stream data
             *
             * May be want to investigate extend stream timeout period to avoid remote video playback disruption
             * i.e. playback view is being toggled off and on for ~2S
             *
             * Note: change in remote camera dimension due to rotation will cause the media decoder to trigger a
             * remote video change event.
             */
            else {
                remoteVideoContainer.preferredSize = null;
                doAlignRemoteVideo(null, null);
            }
        });
    }

    /**
     * Selected remote video preferred size based on current visual components and event status.
     * In android: the remote video view container size is fixed by aTalk to full screen; and user is
     * not allow to change. Hence remoteVideoView has higher priority over visualComponent
     *
     * @param visualComponent remote video <code>Component</code>, <code>null</code> if not available
     * @param remoteVideoView the remote video <code>View</code> if already created, or <code>null</code> otherwise
     * @param scvEvent the <code>SizeChangeVideoEvent</code> if was supplied during event handling or <code>null</code> otherwise.
     * @return selected preferred remote video size.
     */
    private Dimension selectRemotePreferredSize(Component visualComponent, View remoteVideoView,
            SizeChangeVideoEvent scvEvent)
    {
        // There is no remote video View, so returns the default video dimension.
        if ((remoteVideoView == null) || (visualComponent == null)) {
            return new Dimension(DEFAULT_WIDTH, DEFAULT_HEIGHT);
        }

        int width = DEFAULT_WIDTH;
        int height = DEFAULT_HEIGHT;
        /*
         * The SizeChangeVideoEvent may have been delivered with a delay and thus may not
         * represent the up-to-date size of the remote video. The visualComponent is taken
         * as fallback in case SizeChangeVideoEvent is null
         */
        if ((scvEvent != null)
                && (scvEvent.getHeight() > 0) && (scvEvent.getWidth() > 0)) {
            width = scvEvent.getWidth();
            height = scvEvent.getHeight();
        }
        /*
         * If the visualComponent displaying the video of the remote callPeer has a
         * preferredSize, then use as fallback in case scvEvent video size is invalid.
         */
        else {
            Dimension preferredSize = visualComponent.getPreferredSize();
            if ((preferredSize != null) && (preferredSize.width > 0) && (preferredSize.height > 0)) {
                width = preferredSize.width;
                height = preferredSize.height;
            }
        }
        return new Dimension(width, height);
    }

    /**
     * Align remote <code>Video</code> component if available.
     *
     * @param remoteVideoView the remote video <code>View</code> if available or <code>null</code> otherwise.
     * @param preferredSize preferred size of remote video <code>View</code>.
     */
    private void doAlignRemoteVideo(View remoteVideoView, Dimension preferredSize)
    {
        if (remoteVideoView != null) {
            // GLSurfaceView frequent changes can cause error, so change only if necessary
            boolean sizeChange = remoteVideoContainer.setVideoPreferredSize(preferredSize, initOnPhoneOrientationChange);
            Timber.w("Remote video view alignment @ size: %s; sizeChange: %s; initOnPhoneOrientationChange: %s",
                    preferredSize, sizeChange, initOnPhoneOrientationChange);
            if (!sizeChange && !initOnPhoneOrientationChange) {
                return;
            }

            // reset the flag after use
            initOnPhoneOrientationChange = false;

            // Hack only for GLSurfaceView. Remote video view will match parents width and height,
            // but renderer object is properly updated only when removed and added back again.
            if (remoteVideoView instanceof GLSurfaceView) {
                remoteVideoContainer.removeAllViews();

                // remoteVideoView must be an orphan before assigned to another ViewGroup parent
                ViewGroup viewGroup = (ViewGroup) remoteVideoView.getParent();
                if (viewGroup != null) {
                    viewGroup.removeView(remoteVideoView);
                    Timber.d("Make remoteVideo view '%s' as orphan", remoteVideoView);
                }
                remoteVideoContainer.addView(remoteVideoView);
            }

            // When remote video is visible then the call info is positioned in the bottom part of the screen
            RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams) callInfoGroup.getLayoutParams();
            params.addRule(RelativeLayout.CENTER_VERTICAL, 0);
            params.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);

            // Realign call group info start from left if system is in landscape mode
            // int rotation = mCallActivity.getWindowManager().getDefaultDisplay().getRotation();
            // if ((rotation == Surface.ROTATION_90) || (rotation == Surface.ROTATION_270))
            //     params.addRule(RelativeLayout.ALIGN_PARENT_LEFT);

            callInfoGroup.setLayoutParams(params);
            calleeAvatar.setVisibility(View.GONE);
            remoteVideoContainer.setVisibility(View.VISIBLE);
        }
        else { // if (!initOnPhoneOrientationChange) {
            Timber.d("Remote video view removed: %s", preferredSize);

            remoteVideoContainer.removeAllViews();

            // When remote video is hidden then the call info is centered below the avatar
            RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams) callInfoGroup.getLayoutParams();
            params.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM, 0);
            params.addRule(RelativeLayout.CENTER_VERTICAL);
            callInfoGroup.setLayoutParams(params);
            calleeAvatar.setVisibility(View.VISIBLE);
            remoteVideoContainer.setVisibility(View.GONE);
        }

        // Update call info group margin based on control buttons group visibility state
        updateCallInfoMargin();
    }

    /**
     * Returns <code>true</code> if local video is currently visible.
     *
     * @return <code>true</code> if local video is currently visible.
     */
    public boolean isLocalVideoVisible()
    {
        return localPreviewContainer.getChildCount() > 0;
    }

    public boolean isRemoteVideoVisible()
    {
        return remoteVideoContainer.getChildCount() > 0;
    }

    /**
     * Block the program until camera is stopped to prevent from crashing on not existing preview surface.
     */
    void ensureCameraClosed()
    {
        localPreviewSurface.waitForObjectRelease();
        // TODO: remote display must be released too (but the DataSource must be paused)
        // remoteVideoSurfaceHandler.waitForObjectRelease();
    }

    /**
     * Positions call info group buttons.
     */
    void updateCallInfoMargin()
    {
        RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams) callInfoGroup.getLayoutParams();

        int marginBottom = 0;
        // If we have remote video
        if (remoteVideoContainer.getChildCount() > 0) {
            DisplayMetrics displaymetrics = new DisplayMetrics();
            mCallActivity.getWindowManager().getDefaultDisplay().getMetrics(displaymetrics);

            int ctrlButtonsHeight = ctrlButtonsGroup.getHeight();
            marginBottom = (int) (0.10 * displaymetrics.heightPixels);

            if (marginBottom < ctrlButtonsHeight
                    && ctrlButtonsGroup.getVisibility() == View.VISIBLE) {
                marginBottom = ctrlButtonsHeight + AndroidUtils.pxToDp(10);
            }

            // This can be used if we want to keep it on the same height
            if (ctrlButtonsGroup.getVisibility() == View.VISIBLE) {
                marginBottom -= ctrlButtonsHeight;
            }
        }
        params.setMargins(0, 0, 0, marginBottom);
        callInfoGroup.setLayoutParams(params);
    }

    // Parent container activity must implement this interface for callback from this fragment
    public interface OnRemoteVideoChangeListener
    {
        void onRemoteVideoChange(boolean isRemoteVideoVisible);
    }

    /**
     * Init both the local and remote video container on device rotation.
     */
    public void initVideoViewOnRotation()
    {
        if (isLocalVideoEnabled()) {
            initLocalPreviewContainer(currentPreviewProvider);
        }

        if (remoteVideoAccessor != null) {
            initOnPhoneOrientationChange = true;
            handleRemoteVideoEvent((Component) remoteVideoAccessor, null);
        }
    }

}
