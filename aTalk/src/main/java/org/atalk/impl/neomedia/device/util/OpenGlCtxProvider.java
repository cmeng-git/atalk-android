/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.device.util;

import android.app.Activity;
import android.graphics.SurfaceTexture;
import android.opengl.EGL14;
import android.view.*;

import org.atalk.android.plugin.timberlog.TimberLog;

import timber.log.Timber;

/**
 * Provider of Open GL context. Currently used to provide shared context for recorded video that
 * will be used to draw local preview.
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
    protected OpenGLContext context;

    protected TextureView mTextureView;

    /**
     * Flag used to inform the <tt>SurfaceStream</tt> that the <tt>onSurfaceTextureUpdated</tt>
     * event has occurred.
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
        mTextureView = new TextureView(mActivity);
        mTextureView.setSurfaceTextureListener(this);
        Timber.d("TextView created: %s", mTextureView);
        return mTextureView;
    }

    public TextureView getTextureView() {
        return mTextureView;
    }

    // ========= SurfaceTextureListener implementation ========= //
    @Override
    synchronized public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height)
    {
        context = new OpenGLContext(false, surface, EGL14.EGL_NO_CONTEXT);
        onObjectCreated(context);
        // Timber.d("onSurface Texture Available");
    }

    @Override
    synchronized public boolean onSurfaceTextureDestroyed(SurfaceTexture surface)
    {
        onObjectDestroyed();
        if (context != null) {
            // Release context only when the View is destroyed
            context.release();
            context = null;
        }
        return false;
    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height)
    {
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture surface)
    {
        Timber.log(TimberLog.FINER, "onSurfaceTextureUpdated");
        this.textureUpdated = true;
    }
}
