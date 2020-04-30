/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.android.gui.call;

import android.Manifest;
import android.content.pm.PackageManager;
import android.hardware.Camera;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.view.*;
import android.widget.ImageView;
import android.widget.RelativeLayout;

import net.java.sip.communicator.service.protocol.*;

import org.atalk.android.R;
import org.atalk.android.aTalkApp;
import org.atalk.android.gui.controller.SimpleDragController;
import org.atalk.android.gui.util.AndroidUtils;
import org.atalk.android.util.java.awt.Component;
import org.atalk.android.util.java.awt.Dimension;
import org.atalk.impl.neomedia.NeomediaServiceUtils;
import org.atalk.impl.neomedia.codec.video.AndroidDecoder;
import org.atalk.impl.neomedia.device.DeviceConfiguration;
import org.atalk.impl.neomedia.device.util.*;
import org.atalk.impl.neomedia.jmfext.media.protocol.androidcamera.PreviewStream;
import org.atalk.service.neomedia.ViewAccessor;
import org.atalk.service.osgi.OSGiFragment;
import org.atalk.util.event.*;
import org.jetbrains.annotations.NotNull;

import java.util.Iterator;
import java.util.List;

import androidx.core.content.ContextCompat;
import timber.log.Timber;

/**
 * Fragment takes care of handling call UI parts related to the video - both local and remote.
 *
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
@SuppressWarnings("deprecation")
public class VideoHandlerFragment extends OSGiFragment implements View.OnLongClickListener
{
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
     * Instance of video listener that should be unregistered once this Activity is destroyed
     */
    private VideoListener callPeerVideoListener;

    /**
     * The preview surface state handler
     */
    private PreviewSurfaceProvider previewSurfaceHandler;

    /**
     * Stores the current local video state in case this <tt>Activity</tt> is hidden during call.
     * Also use during screen rotation to re-init local video
     */
    public static boolean wasVideoEnabled = false;

    private static boolean isCameraEnable = false;

    /**
     * Indicate phone orientation change and need to init RemoteVideoContainer
     */
    private boolean initOnPhoneOrientationChange = false;

    /**
     * The call for which this fragment is handling video events.
     */
    private Call call;

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
     * VideoHandlerFragment parent activity for callback i.e. VideoCallActivity
     */
    private VideoCallActivity mCallback;

    /**
     * Creates new instance of <tt>VideoHandlerFragment</tt>.
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
        mCallback = activity;
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState)
    {
        super.onActivityCreated(savedInstanceState);

        remoteVideoContainer = mCallback.findViewById(R.id.remoteVideoContainer);
        localPreviewContainer = mCallback.findViewById(R.id.localPreviewContainer);
        callInfoGroup = mCallback.findViewById(R.id.callInfoGroup);
        ctrlButtonsGroup = mCallback.findViewById(R.id.button_Container);

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

        isCameraEnable = ContextCompat.checkSelfPermission(mCallback, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED;

        calleeAvatar = mCallback.findViewById(R.id.calleeAvatar);
        mCallVideoButton = mCallback.findViewById(R.id.button_call_video);

        if (isCameraEnable) {
            mCallVideoButton.setOnClickListener(this::onLocalVideoButtonClicked);
            mCallVideoButton.setOnLongClickListener(this);
        }

        // Creates and registers surface handler for events
        this.previewSurfaceHandler = new PreviewSurfaceProvider(mCallback, localPreviewContainer, true);
        CameraUtils.setPreviewSurfaceProvider(previewSurfaceHandler);

        // Makes the local preview window draggable on the screen
        localPreviewContainer.setOnTouchListener(new SimpleDragController());

        this.call = mCallback.getCall();
        AndroidDecoder.renderSurfaceProvider = new PreviewSurfaceProvider(mCallback, remoteVideoContainer, false);

        // Makes the preview display draggable on the screen - not applicable in full screen mode
        // remoteVideoContainer.setOnTouchListener(new SimpleDragController());

        if (AndroidUtils.hasAPI(18))
            CameraUtils.localPreviewCtxProvider = new OpenGlCtxProvider(mCallback, localPreviewContainer);
    }

    @Override
    public void onResume()
    {
        super.onResume();
        if (call == null) {
            Timber.e("Call is null");
            return;
        }

        // Default local preview width
        int DEFAULT_PREVIEW_WIDTH = 160;

        /*
         * Restores local video state if it was enabled or on first video call entry
         * The local preview size is configure to be proportional to the actually camera capture
         * video dimension with preset default width
         */
        if (wasVideoEnabled || isLocalVideoEnabled()) {

            DeviceConfiguration deviceConfig = NeomediaServiceUtils.getMediaServiceImpl().getDeviceConfiguration();
            Dimension videoSize = deviceConfig.getVideoSize();

            // Remote and Local video preview dimension follows phone orientation i.e. portrait or landscape
            int cameraId = PreviewStream.getCameraId();
            int mRotation = CameraUtils.getCameraDisplayRotation(cameraId);
            boolean swap = (mRotation == 90) || (mRotation == 270);
            RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams) localPreviewContainer.getLayoutParams();

            // Get actual preview size for phone in its current orientation
            List<Camera.Size> supportSizes = CameraUtils.getSupportSizeForCameraId(cameraId);
            Dimension optimizedSize = CameraUtils.getOptimalPreviewSize(videoSize, supportSizes);

            // Local preview size has default fixed width of 160 (landscape mode)
            float scale = getResources().getDisplayMetrics().density * DEFAULT_PREVIEW_WIDTH / optimizedSize.width;
            Dimension previewSize;
            if (swap) {
                previewSize = new Dimension(optimizedSize.height, optimizedSize.width);
            }
            else {
                previewSize = optimizedSize;
            }
            params.width = (int) (previewSize.width * scale + 0.5f);
            params.height = (int) (previewSize.height * scale + 0.5f);
            localPreviewContainer.setLayoutParams(params);

            // Set proper videoCallButtonState and restore local video
            initLocalVideoState(true);
        }

        // Checks if call peer has video component
        Iterator<? extends CallPeer> peers = call.getCallPeers();
        if (peers.hasNext()) {
            CallPeer callPeer = peers.next();
            addVideoListener(callPeer);
            initOnPhoneOrientationChange = true;
            initRemoteVideo(callPeer);
        }
        else {
            Timber.e("There aren't any peers in the call");
        }
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
        if (call == null) {
            Timber.e("Call is null");
            return;
        }

        removeVideoListener();
        if (call.getCallState() != CallState.CALL_ENDED) {
            wasVideoEnabled = isLocalVideoEnabled();
            // Timber.e("Was local enabled ? %s", wasVideoEnabled);

            /*
             * Disables local video to stop the camera and release the surface.
             * Otherwise media recorder will crash on invalid preview surface.
             * 20180921 - crash does not happen anymore when remains as true- fixed?
             */
            //  if (!mCallback.isBackToChat()) {
            //                previewSurfaceHandler.setPreviewSurface(PreviewStream.getCamera());
            //  }
            //            else {
            setLocalVideoEnabled(false);

            previewSurfaceHandler.waitForObjectRelease();
            // TODO: release object on rotation, but the data source have to be paused
            // remoteSurfaceHandler.waitForObjectRelease();
            //}
        }
    }

    @Override
    public void onDestroy()
    {
        super.onDestroy();
        // Release shared video component
        remoteVideoContainer.removeAllViews();
        CameraUtils.localPreviewCtxProvider = null;
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
     * Switch to alternate camera on device when user toggle the camera
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
     * Long press camera icon change to alternate camera availab on the device.
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

        String back = getString(R.string.service_gui_settings_USE_BACK_CAMERA);
        String front = getString(R.string.service_gui_settings_USE_FRONT_CAMERA);
        String newTitle;

        final AndroidCamera newDevice;
        if (item.getTitle().equals(back)) {
            // Switch to back camera and toggle item name
            newDevice = AndroidCamera.getCameraFromCurrentDeviceSystem(Camera.CameraInfo.CAMERA_FACING_BACK);
            newTitle = front;
        }
        else {
            // Switch to front camera and toggle item name
            newDevice = AndroidCamera.getCameraFromCurrentDeviceSystem(Camera.CameraInfo.CAMERA_FACING_FRONT);
            newTitle = back;
        }
        item.setTitle(newTitle);

        // Switch the camera in separate thread
        cameraSwitchThread = new Thread()
        {
            @Override
            public void run()
            {
                if (newDevice != null) {
                    AndroidCamera.setSelectedCamera(newDevice.getLocator());
                    // Keep track of created threads
                    cameraSwitchThread = null;
                }
            }
        };
        cameraSwitchThread.start();
    }

    /**
     * Called when local video button is pressed.
     *
     * @param callVideoButton local video button <tt>View</tt>.
     */
    private void onLocalVideoButtonClicked(View callVideoButton)
    {
        initLocalVideoState(!isLocalVideoEnabled());
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
     * @return <tt>true</tt> if local video is enabled.
     */
    private boolean isLocalVideoEnabled()
    {
        return CallManager.isLocalVideoEnabled(call);
    }

    /**
     * Sets local video status.
     *
     * @param enable flag indicating local video status to be set.
     */
    private void setLocalVideoEnabled(boolean enable)
    {
        if (call == null) {
            Timber.e("Call instance is null(the call has ended already ?)");
            return;
        }
        // Timber.w("Set local Video enable state: %s", enable);
        CallManager.enableLocalVideo(call, enable);
    }

    /**
     * Adds a video listener for the given call peer.
     *
     * @param callPeer the <tt>CallPeer</tt> to which we add a video listener
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
     * Removes remote video listener.
     */
    private void removeVideoListener()
    {
        Iterator<? extends CallPeer> calPeers = call.getCallPeers();
        if (calPeers.hasNext()) {
            CallPeer callPeer = calPeers.next();

            ProtocolProviderService pps = call.getProtocolProvider();
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
     * Initializes remote video for the call. Visual component is null on initial setup.
     * but no null on phone rotate: Need to re-init remote video on screen rotation
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
        handleRemoteVideoEvent(visualComponent, null);
    }

    /**
     * Handles a video event.
     *
     * @param callPeer the corresponding call peer
     * @param event the <tt>VideoEvent</tt> that notified us
     */
    public void handleVideoEvent(CallPeer callPeer, final VideoEvent event)
    {
        if (event.isConsumed())
            return;
        event.consume();

        /*
         * if (event.getOrigin() == VideoEvent.LOCAL) {
         * 	local video events are not used because the preview is required for camera to start
         *  and it must not be removed until is stopped, so it's handled by direct cooperation with
         *  .jmfext.media.protocol.mediarecorder.DataSource
         * }
         */

        if (event.getOrigin() == VideoEvent.REMOTE) {
            int eventType = event.getType();
            Component visualComponent = ((eventType == VideoEvent.VIDEO_ADDED)
                    || (eventType == VideoEvent.VIDEO_SIZE_CHANGE))
                    ? event.getVisualComponent() : null;

            SizeChangeVideoEvent scve = (eventType == VideoEvent.VIDEO_SIZE_CHANGE)
                    ? (SizeChangeVideoEvent) event : null;

            handleRemoteVideoEvent(visualComponent, scve);
        }
    }

    /**
     * Handles remote video event arguments.
     *
     * @param visualComponent the remote video <tt>Component</tt> if available or <tt>null</tt> otherwise.
     * visualComponent is null on video call initial setup and on remote video removed.
     * No null on phone rotate and need to re-init remote video on screen rotation
     * @param scvEvent the <tt>SizeChangeVideoEvent</tt> event if was supplied.
     */
    private void handleRemoteVideoEvent(final Component visualComponent, final SizeChangeVideoEvent scvEvent)
    {
        if (visualComponent instanceof ViewAccessor) {
            this.remoteVideoAccessor = (ViewAccessor) visualComponent;
        }
        else {
            this.remoteVideoAccessor = null;
            // null evaluates to false, so need to check here before warn
            // Report component is not compatible
            if (visualComponent != null) {
                Timber.e("Remote video component is not Android compatible.");
            }
        }

        runOnUiThread(() -> {
            // Update window full screen visibility
            mCallback.onRemoteVideoChange(remoteVideoAccessor != null);

            if (remoteVideoAccessor != null) {
                View view = remoteVideoAccessor.getView(mCallback);
                Dimension preferredSize = selectRemotePreferredSize(visualComponent, view, scvEvent);
                Timber.w("Remote video content changed @ size: %s", preferredSize.toString());
                doAlignRemoteVideo(view, preferredSize);
            }
            /*
             * (20181228) cmeng: New implementation will not trigger content remove and content add when toggle local camera
             * Remove remote view container and realign display will happen when:
             * a. Remote camera is toggled
             * b. Remote camera (phone) rotation changed
             * as the action causes stream Timeout event, then followed by receipt of new stream data
             *
             * May want to investigate extend stream timeout period to avoid remote video playback disruption
             * i.e. playback view is being toggled off and on for ~2S
             * Note change in remote camera dimension due to rotation will cause media decoder to trigger a
             * remote video chane event.
             */
            else {
                Timber.w("Remote video removed.");
                doAlignRemoteVideo(null, null);
            }
        });
    }

    /**
     * Selected remote video preferred size based on current visual components and event status.
     * In android: the remote video view container size is fixed by aTalk to full screen; and user is
     * not allow to change. Hence remoteVideoView has higher priority over visualComponent
     *
     * @param visualComponent remote video <tt>Component</tt>, <tt>null</tt> if not available
     * @param remoteVideoView the remote video <tt>View</tt> if already created, or <tt>null</tt> otherwise
     * @param scvEvent the <tt>SizeChangeVideoEvent</tt> if was supplied during event handling or <tt>null</tt> otherwise.
     * @return selected preferred remote video size.
     */
    private Dimension selectRemotePreferredSize(Component visualComponent, View remoteVideoView,
            SizeChangeVideoEvent scvEvent)
    {
        // Default view dimension - must be valid for OpenGL else crash
        int width = 720;
        int height = 1280;

        // There is no remote video View, so returns default dimension (720x1280).
        // Note: Other dimension ratio e.g. (-1x-1) will cause Invalid Operation in OpenGL
        if ((remoteVideoView == null) || (visualComponent == null)) {
            return new Dimension(width, height);
        }

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
     * Aligns remote <tt>Video</tt> component if available.
     *
     * @param remoteVideoView the remote video <tt>View</tt> if available or <tt>null</tt> otherwise.
     * @param preferredSize preferred size of remote video <tt>View</tt>.
     */
    private void doAlignRemoteVideo(View remoteVideoView, Dimension preferredSize)
    {
        if (remoteVideoView != null) {
            // GLSurfaceView frequent changes can cause error, so change only if absolutely necessary
            boolean sizeChange = remoteVideoContainer.setVideoPreferredSize(remoteVideoView, preferredSize);
            if (!sizeChange && !initOnPhoneOrientationChange) {
                Timber.d("No change in remote video viewContainer dimension!");
                return;
            }
            // reset the flag after use
            initOnPhoneOrientationChange = false;

            // Hack only for GLSurfaceView. Remote video view will match parents width and height,
            // but renderer object is properly updated only when removed and added back again.
            if (remoteVideoView instanceof GLSurfaceView) {
                remoteVideoContainer.removeAllViews();
                remoteVideoContainer.addView(remoteVideoView);
            }
            // When remote video is visible then the call info is positioned in the bottom part of the screen
            RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams) callInfoGroup.getLayoutParams();
            params.addRule(RelativeLayout.CENTER_VERTICAL, 0);
            params.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);

            // Realign call group info start from left if system is in landscape mode
            // int rotation = mCallback.getWindowManager().getDefaultDisplay().getRotation();
            // if ((rotation == Surface.ROTATION_90) || (rotation == Surface.ROTATION_270))
            //     params.addRule(RelativeLayout.ALIGN_PARENT_LEFT);

            callInfoGroup.setLayoutParams(params);
            calleeAvatar.setVisibility(View.GONE);
        }
        else {
            remoteVideoContainer.removeAllViews();

            // When remote video is hidden then the call info is centered below the avatar
            RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams) callInfoGroup.getLayoutParams();
            params.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM, 0);
            params.addRule(RelativeLayout.CENTER_VERTICAL);
            callInfoGroup.setLayoutParams(params);
            calleeAvatar.setVisibility(View.VISIBLE);
        }

        // Update call info group margin based on control buttons group visibility state
        updateCallInfoMargin();
    }

    /**
     * Returns <tt>true</tt> if local video is currently visible.
     *
     * @return <tt>true</tt> if local video is currently visible.
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
        previewSurfaceHandler.waitForObjectRelease();
        // TODO: remote display must be released too (but the DataSource must be paused)
        // remoteVideoSurfaceHandler.waitForObjectRelease();
    }

    /**
     * Positions call info group buttons.
     */
    void updateCallInfoMargin()
    {
        RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams) callInfoGroup.getLayoutParams();

        // If we have remote video
        if (remoteVideoContainer.getChildCount() > 0) {
            DisplayMetrics displaymetrics = new DisplayMetrics();
            mCallback.getWindowManager().getDefaultDisplay().getMetrics(displaymetrics);

            int ctrlButtonsHeight = ctrlButtonsGroup.getHeight();
            int marginBottom = (int) (0.10 * displaymetrics.heightPixels);

            if (marginBottom < ctrlButtonsHeight
                    && ctrlButtonsGroup.getVisibility() == View.VISIBLE) {
                marginBottom = ctrlButtonsHeight + AndroidUtils.pxToDp(10);
            }

            // This can be used if we want to keep it on the same height
            if (ctrlButtonsGroup.getVisibility() == View.VISIBLE) {
                marginBottom -= ctrlButtonsHeight;
            }

            params.setMargins(0, 0, 0, marginBottom);
            callInfoGroup.setLayoutParams(params);
        }
        else {
            params.setMargins(0, 0, 0, 0);
            callInfoGroup.setLayoutParams(params);
        }
    }

    // Parent container activity must implement this interface for callback from this fragment
    public interface OnRemoteVideoChangeListener
    {
        void onRemoteVideoChange(boolean isRemoteVideoVisible);
    }
}
