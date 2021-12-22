/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.device.util;

import android.app.Activity;
import android.graphics.*;
import android.opengl.EGL14;
import android.view.*;

import org.atalk.android.aTalkApp;
import org.atalk.android.plugin.timberlog.TimberLog;

import timber.log.Timber;

/**
 * Provider of Open GL context. Currently use to provide 'shared context' for recording/streaming video; and it
 * is used for rendering the local video preview.
 *
 * Note: A TextureView object wraps a SurfaceTexture, responding to callbacks and acquiring new buffers.
 * link: https://source.android.com/devices/graphics/arch-tv
 *
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
public class OpenGlCtxProvider extends ViewDependentProvider<OpenGLContext>
        implements TextureView.SurfaceTextureListener
{
    /**
     * The <tt>OpenGLContext</tt>.
     */
    protected OpenGLContext mGLContext;

    protected AutoFitTextureView mTextureView;

    /**
     * Flag used to inform the <tt>SurfaceStream</tt> that the <tt>onSurfaceTextureUpdated</tt> event has occurred.
     */
    public boolean textureUpdated = true;

    /**
     * Creates new instance of <tt>OpenGlCtxProvider</tt>.
     *
     * @param activity parent <tt>Activity</tt>.
     * @param container the container that will hold maintained <tt>View</tt>.
     */
    public OpenGlCtxProvider(Activity activity, ViewGroup container)
    {
        super(activity, container);
    }

    @Override
    protected View createViewInstance()
    {
        mTextureView = new AutoFitTextureView(mActivity);
        mTextureView.setSurfaceTextureListener(this);
        Timber.d("TextView created: %s", mTextureView);
        return mTextureView;
    }

    /**
     * setup the TextureView with the given size to take care 4x3 and 16x9 aspect ration video
     *
     * @param width The width of `mTextureView`
     * @param height The height of `mTextureView`
     */
    public void setAspectRatio(int width, int height)
    {
        if (mTextureView != null) {
            mTextureView.setAspectRatio(width, height);
        }
        else {
            Timber.w("onSurfaceTexture configure transform mTextureView is null");
        }
    }

    /**
     * ConfigureTransform with the previously setup mTextureView size
     */
    public void setTransformMatrix()
    {
        configureTransform(mTextureView.mRatioWidth, mTextureView.mRatioHeight);
    }

    /**
     * Configures the necessary {@link android.graphics.Matrix} transformation to `mTextureView`.
     * This method should be called after the camera preview size is determined
     * and also the size of `mTextureView` is fixed.
     *
     * Note: The transform is not working when the local preview container is very first setup;
     * Subsequence device rotation work but it also affects change the stream video; so far unable to solve
     * this problem.
     *
     * @param viewWidth The width of `mTextureView`
     * @param viewHeight The height of `mTextureView`
     */
    public void configureTransform(int viewWidth, int viewHeight)
    {
        RectF viewRect = new RectF(0, 0, viewWidth, viewHeight);
        float centerX = viewRect.centerX();
        float centerY = viewRect.centerY();
        float scale = Math.max((float) viewWidth / mVideoSize.height, (float) viewHeight / mVideoSize.width);

        // Create an identity matrix
        Matrix matrix = new Matrix();
        int rotation = mActivity.getWindowManager().getDefaultDisplay().getRotation();
        if (Surface.ROTATION_90 == rotation || Surface.ROTATION_270 == rotation) {
            int degree = 90 * (rotation - 2);

            RectF bufferRect = new RectF(0, 0, mVideoSize.height, mVideoSize.width);
            bufferRect.offset(centerX - bufferRect.centerX(), centerY - bufferRect.centerY());

            matrix.setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL);
            matrix.postScale(scale, scale, centerX, centerY);
            matrix.postRotate(degree, centerX, centerY);
        }
        Timber.d("onSurfaceTexture configure transform: %s => [%sx%s]; scale: %s; rotation: %s",
                mVideoSize, viewWidth, viewHeight, scale, rotation);

        // Not properly rotate when in landscape if proceed
//        else { // else if (Surface.ROTATION_0 == rotation || Surface.ROTATION_180 == rotation) {
//            int degree = (rotation == 0) ? 0 : 180;
//            matrix.postRotate(degree, centerX, centerY);
//        }
        mTextureView.setTransform(matrix);
    }

    /**
     * The method has problem to get the surface image to fill the local container size.
     */
    private void configureTransform2(int viewWidth, int viewHeight)
    {
        RectF viewRect = new RectF(0, 0, viewWidth, viewHeight);
        float centerX = viewRect.centerX();
        float centerY = viewRect.centerY();

        int bufferWidth;
        int bufferHeight;
        int degree;

        if (aTalkApp.isPortrait) {
            bufferWidth = mVideoSize.height;
            bufferHeight = mVideoSize.width;
        }
        else {
            bufferWidth = mVideoSize.width;
            bufferHeight = mVideoSize.height;
        }

        int rotation = mActivity.getWindowManager().getDefaultDisplay().getRotation();
        if (Surface.ROTATION_90 == rotation || Surface.ROTATION_270 == rotation) {
            degree = 90 * (rotation - 2);
        }
        else { // else if (Surface.ROTATION_0 == rotation || Surface.ROTATION_180 == rotation) {
            degree = (rotation == 0) ? 0 : 180;
        }
        RectF bufferRect = new RectF(0, 0, bufferWidth, bufferHeight);
        bufferRect.offset(centerX - bufferRect.centerX(), centerY - bufferRect.centerY());

        Matrix matrix = new Matrix();
        float scale = Math.max((float) viewWidth / mVideoSize.width, (float) viewHeight / mVideoSize.height);

        matrix.setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL);
        matrix.postScale(scale, scale, centerX, centerY);
        matrix.postRotate(degree, centerX, centerY);

        Timber.d("onSurfaceTexture configure transform: [%sx%s] => [%sx%s]; scale=%s (%s/%s); rotation: %s (%s)",
                bufferWidth, bufferHeight, viewWidth, viewHeight, scale, scale * bufferWidth, scale * bufferHeight, rotation, degree);
        mTextureView.setTransform(matrix);
    }

    // ========= SurfaceTextureListener implementation ========= //
    @Override
    synchronized public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height)
    {
        mGLContext = new OpenGLContext(false, surface, EGL14.EGL_NO_CONTEXT);
        onObjectCreated(mGLContext);
        Timber.d("onSurfaceTexture Available with dimension: [%s x %s] (%s)", width, height, mVideoSize);
    }

    @Override
    synchronized public boolean onSurfaceTextureDestroyed(SurfaceTexture surface)
    {
        onObjectDestroyed();
        // Release context only when the View is destroyed
        if (mGLContext != null) {
            mGLContext.release();
            mGLContext = null;
        }
        return false;
    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height)
    {
        Timber.d("onSurfaceTexture SizeChanged: [%s x %s]", width, height);
        configureTransform(width, height);
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture surface)
    {
        Timber.log(TimberLog.FINER, "onSurfaceTextureUpdated");
        textureUpdated = true;
    }
}
