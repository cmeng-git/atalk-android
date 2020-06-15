/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.device.util;

import android.view.*;

import org.atalk.service.osgi.OSGiActivity;

/**
 * The class exposes methods for managing preview surface state which must be synchronized with
 * currently used {@link android.hardware.Camera} state.<br/>
 * The surface must be present before the camera is started and for this purpose
 * {@link #obtainObject()} method shall be used. <br/>
 * When the call is ended, before the <tt>Activity</tt> is finished we should ensure that the camera
 * has been stopped(which is done by video telephony internals), so we should wait for it to be
 * disposed by invoking method {@link #waitForObjectRelease()}. It will block current
 * <tt>Thread</tt> until it happens or an <tt>Exception</tt> will be thrown if timeout occurs.
 */
public class PreviewSurfaceProvider extends ViewDependentProvider<SurfaceHolder>
        implements SurfaceHolder.Callback
{
    /**
     * Flag indicates whether {@link SurfaceView#setZOrderMediaOverlay(boolean)} should be called on
     * created <tt>SurfaceView</tt>.
     */
    private final boolean setZMediaOverlay;

    /**
     * Creates new instance of <tt>PreviewSurfaceProvider</tt>.
     *
     * @param parent parent <tt>OSGiActivity</tt> instance.
     * @param container the <tt>ViewGroup</tt> that will hold maintained <tt>SurfaceView</tt>.
     * @param setZMediaOverlay if set to <tt>true</tt> then the <tt>SurfaceView</tt> will be displayed on the top of
     * other surfaces.
     */
    public PreviewSurfaceProvider(OSGiActivity parent, ViewGroup container, boolean setZMediaOverlay)
    {
        super(parent, container);
        this.setZMediaOverlay = setZMediaOverlay;
    }

    @Override
    protected View createViewInstance()
    {
        SurfaceView view = new SurfaceView(activity);
        view.getHolder().addCallback(PreviewSurfaceProvider.this);

        if (setZMediaOverlay)
            view.setZOrderMediaOverlay(true);
        return view;
    }

    /**
     * Method is called before <tt>Camera</tt> is started and shall return non <tt>null</tt>
     * {@link SurfaceHolder} instance.
     *
     * @return {@link SurfaceHolder} instance that will be used for local video preview
     */
    @Override
    public SurfaceHolder obtainObject()
    {
        return super.obtainObject();
    }

    /**
     * Returns maintained <tt>View</tt> object.
     *
     * @return maintained <tt>View</tt> object.
     */
    public View getView()
    {
        if (obtainObject() != null) {
            return view;
        }
        throw new RuntimeException("Failed to obtain view");
    }

    /**
     * Method is called when <tt>Camera</tt> is stopped and it's safe to release the {@link Surface}
     * object.
     */
    @Override
    public void onObjectReleased()
    {
        super.onObjectReleased();
    }

    /**
     * Should return current {@link Display} rotation as defined in
     * {@link android.view.Display#getRotation()}.
     *
     * @return current {@link Display} rotation as one of values:
     * {@link Surface#ROTATION_0}, {@link Surface#ROTATION_90}, {@link Surface#ROTATION_180}, {@link Surface#ROTATION_270}.
     */
    public int getDisplayRotation()
    {
        return activity.getWindowManager().getDefaultDisplay().getRotation();
    }

    /**
     * This is called immediately after the surface is first created. Implementations of this should
     * start up whatever rendering code they desire. Note that only one thread can ever draw into a
     * {@link Surface}, so you should not draw into the Surface here if your normal rendering will
     * be in another thread.
     *
     * @param holder The SurfaceHolder whose surface is being created.
     */
    @Override
    public void surfaceCreated(SurfaceHolder holder)
    {
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
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height)
    {
        /*
         * surfaceChange event is mainly triggered by local video preview change by user; currently
         * not implemented in android aTalk. Hence no action is required.
         * Note: the event get trigger whenever there is an init of the local video preview
         * e.g. init or toggle camera
         * Timber.w("Preview surface change size: %s x %s", width, height);
         */
        // if (mHolder.getSurface() == null){
        // // preview surface does not exist
        // return;
        // }
        //
        // // stop preview before making changes
        // try {
        // mCamera.stopPreview();
        // } catch (Exception e){
        // // ignore: tried to stop a non-existent preview
        // }
        //
        // // set preview size and make any resize, rotate or reformatting changes here
        //
        // // start preview with new settings
        // try {
        // mCamera.setPreviewDisplay(mHolder);
        // mCamera.startPreview();
        //
        // } catch (Exception e){
        // Timber.e("Error starting camera preview: %s", e.getMessage());
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
    public void surfaceDestroyed(SurfaceHolder holder)
    {
        onObjectDestroyed();
    }
}
