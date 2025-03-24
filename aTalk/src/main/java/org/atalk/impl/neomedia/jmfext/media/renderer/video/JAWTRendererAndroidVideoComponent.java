/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.jmfext.media.renderer.video;

import android.content.Context;
import android.opengl.GLSurfaceView;
import android.view.View;

import java.awt.Component;
import java.awt.Graphics;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

import org.atalk.impl.timberlog.TimberLog;
import org.atalk.service.neomedia.ViewAccessor;

/**
 * Implements <code>java.awt.Component</code> for <code>JAWTRenderer</code> on Android using a {@link GLSurfaceView}.
 *
 * @author Lyubomir Marinov
 * @author Eng Chong Meng
 */
public class JAWTRendererAndroidVideoComponent extends Component implements ViewAccessor {
    private static final long serialVersionUID = 1L;

    /**
     * The <code>JAWTRenderer</code> which is to use or is using this instance as its visual <code>Component</code>.
     */
    private final JAWTRenderer renderer;

    /**
     * The <code>GLSurfaceView</code> is the actual visual counterpart of this <code>java.awt.Component</code>.
     */
    private GLSurfaceView glSurfaceView;

    /**
     * Initializes a new <code>JAWTRendererAndroidVideoComponent</code> which is to be the visual
     * <code>Component</code> of a specific <code>JAWTRenderer</code>.
     *
     * @param renderer the <code>JAWTRenderer</code> which is to use the new instance as its visual <code>Component</code>
     */
    public JAWTRendererAndroidVideoComponent(JAWTRenderer renderer) {
        this.renderer = renderer;
    }

    /**
     * Implements {@link ViewAccessor#getView(Context)}. Gets the {@link View} provided by this
     * instance which is to be used in a specific {@link Context}.
     *
     * @param context the <code>Context</code> in which the provided <code>View</code> will be used
     *
     * @return the <code>View</code> provided by this instance which is to be used in a specific <code>Context</code>
     *
     * @see ViewAccessor#getView(Context)
     */
    public synchronized GLSurfaceView getView(Context context) {
        if ((glSurfaceView == null) && (context != null)) {
            glSurfaceView = new GLSurfaceView(context);
            if (TimberLog.isTraceEnable)
                glSurfaceView.setDebugFlags(GLSurfaceView.DEBUG_LOG_GL_CALLS);

            glSurfaceView.setRenderer(new GLSurfaceView.Renderer() {
                public void onSurfaceCreated(GL10 gl, EGLConfig config) {
                    // TODO Auto-generated method stub
                }

                /**
                 * Implements {@link GLSurfaceView.Renderer#onDrawFrame(GL10)}. Draws the current frame.
                 *
                 * @param gl the <code>GL10</code> interface with which the drawing is to be performed
                 */
                public void onDrawFrame(GL10 gl) {
                    JAWTRendererAndroidVideoComponent.this.onDrawFrame(gl);
                }

                public void onSurfaceChanged(GL10 gl, int width, int height) {
                    // TODO Auto-generated method stub
                }
            });
            glSurfaceView.setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);
        }
        return glSurfaceView;
    }

    /**
     * Called by the <code>GLSurfaceView</code> which is the actual visual counterpart of this
     * <code>java.awt.Component</code> to draw the current frame.
     *
     * @param gl the <code>GL10</code> interface with which the drawing is to be performed
     */
    protected void onDrawFrame(GL10 gl) {
        synchronized (renderer.getHandleLock()) {
            long handle = renderer.getHandle();

            if (handle != 0) {
                Graphics g = null;
                int zOrder = -1;
                JAWTRenderer.paint(handle, this, g, zOrder);
            }
        }
    }

    @Override
    public synchronized void repaint() {
        if (glSurfaceView != null)
            glSurfaceView.requestRender();
    }
}
