/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.device.util;

import android.hardware.camera2.CameraDevice;
import android.view.Display;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import timber.log.Timber;

/**
 * The class exposes methods for managing preview surfaceView state which must be synchronized with
 * currently used {@link CameraDevice} state.
 * The surface must be present before the camera is started and for this purpose
 * {@link #obtainObject()} method shall be used.
 * <p>
 * When the call is ended, before the <code>Activity</code> is finished we should ensure that the camera
 * has been stopped (which is done by video telephony internals), so we should wait for it to be
 * disposed by invoking method {@link #waitForObjectRelease()}. It will block current
 * <code>Thread</code> until it happens or an <code>Exception</code> will be thrown if timeout occurs.
 */
public class PreviewSurfaceProvider extends ViewDependentProvider<SurfaceHolder> implements SurfaceHolder.Callback {
    private AutoFitSurfaceView mSurfaceView;

    /**
     * Flag indicates whether {@link SurfaceView#setZOrderMediaOverlay(boolean)} should be called on
     * created <code>SurfaceView</code>.
     */
    private final boolean setZMediaOverlay;

    /**
     * Create a new instance of <code>PreviewSurfaceProvider</code>.
     *
     * @param parent parent <code>OSGiActivity</code> instance.
     * @param container the <code>ViewGroup</code> that will hold maintained <code>SurfaceView</code>.
     * @param setZMediaOverlay if set to <code>true</code> then the <code>SurfaceView</code> will be
     * displayed on the top of other surfaces e.g. local camera surface preview
     */
    public PreviewSurfaceProvider(AppCompatActivity parent, ViewGroup container, boolean setZMediaOverlay) {
        super(parent, container);
        this.setZMediaOverlay = setZMediaOverlay;
    }

    @Override
    protected View createViewInstance() {
        mSurfaceView = new AutoFitSurfaceView(mActivity);
        mSurfaceView.getHolder().addCallback(this);
        if (setZMediaOverlay)
            mSurfaceView.setZOrderMediaOverlay(true);
        return mSurfaceView;
    }

    public void setAspectRatio(int width, int height) {
        if (mSurfaceView != null) {
            mSurfaceView.setAspectRatio(width, height);
        }
        else {
            Timber.w(" setAspectRatio for mSurfaceView is null");
        }
    }

    /**
     * Method is called before <code>Camera</code> is started and shall return non <code>null</code> {@link SurfaceHolder} instance.
     * The is also used by android decoder.
     *
     * @return {@link SurfaceHolder} instance that will be used for local video preview
     */
    @Override
    public SurfaceHolder obtainObject() {
        // Timber.e(new Exception("Obtain Object for testing only"));
        return super.obtainObject();
    }

    /**
     * Method is called when <code>Camera</code> is stopped and it's safe to release the {@link Surface} object.
     */
    @Override
    public void onObjectReleased() {
        super.onObjectReleased();
    }

    /**
     * Should return current {@link Display} rotation as defined in {@link android.view.Display#getRotation()}.
     *
     * @return current {@link Display} rotation as one of values:
     * {@link Surface#ROTATION_0}, {@link Surface#ROTATION_90}, {@link Surface#ROTATION_180}, {@link Surface#ROTATION_270}.
     */
    public int getDisplayRotation() {
        return mActivity.getWindowManager().getDefaultDisplay().getRotation();
    }

    // ============== SurfaceHolder.Callback ================== //

    /**
     * This is called immediately after the surface is first created. Implementations of this should
     * start up whatever rendering code they desire. Note that only one thread can ever draw into a
     * {@link Surface}, so you should not draw into the Surface here if your normal rendering will
     * be in another thread.
     * <p>
     * Must setFixedSize() to the user selected video size, to ensure local preview is in correct aspect ratio
     * <a href="https://developer.android.com/reference/android/hardware/camera2/CameraDevice.html#createCaptureSession(android.hardware.camera2.params.SessionConfigurat">...</a>ion)
     *
     * @param holder The SurfaceHolder whose surface is being created.
     */
    @Override
    public void surfaceCreated(@NonNull SurfaceHolder holder) {
        // Timber.d("SurfaceHolder created setFixedSize: %s", mVideoSize);
        if (mVideoSize != null) {
            holder.setFixedSize(mVideoSize.width, mVideoSize.height);
        }
        onObjectCreated(holder);
    }

    /**
     * This is called immediately after any structural changes (format or size) have been made to
     * the surface. You should at this point update the imagery in the surface. This method is
     * always called at least once, after {@link #surfaceCreated}.
     *
     * @param holder The SurfaceHolder whose surface has changed.
     * @param format The new PixelFormat of the surface.
     * @param width The new width of the surface.
     * @param height The new height of the surface.
     */
    @Override
    public void surfaceChanged(@NonNull SurfaceHolder holder, int format, int width, int height) {
        /*
         * surfaceChange event is mainly triggered by local video preview change by user;
         * currently not implemented in android aTalk. Hence no action is required.
         * Note: the event get trigger whenever there is an init of the local video preview e.g. init or toggle camera
         * Timber.w("Preview surface change size: %s x %s", width, height);
         */
        // Timber.d("SurfaceHolder size changed: [%s x %s]; %s", width, height, holder);
        // preview surface does not exist
        // if (mHolder.getSurface() == null){
        //     return;
        // }
        //
        // // stop preview before making changes
        // try {
        //     mCamera.stopPreview();
        // } catch (Exception e){
        // ignore: tried to stop a non-existent preview
        // }

        // set preview size and make any resize, rotate or reformatting changes here
        // start preview with new settings
        // try {
        //     mCamera.setPreviewDisplay(mHolder);
        //     mCamera.startPreview();
        // } catch (Exception e){
        //     Timber.e("Error starting camera preview: %s", e.getMessage());
        // }
    }

    /**
     * This is called immediately before a surface is being destroyed. After returning from this
     * call, you should no longer try to access this surface. If you have a rendering thread that
     * directly accesses the surface, you must ensure that thread is no longer touching the Surface
     * before returning from this function.
     *
     * @param holder The SurfaceHolder whose surface is being destroyed.
     */
    @Override
    public void surfaceDestroyed(@NonNull SurfaceHolder holder) {
        onObjectDestroyed();
    }
}
