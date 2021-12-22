/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.device.util;

import android.opengl.EGLContext;
import android.view.Surface;

/**
 * Holds state associated with a Surface used for MediaCodec encoder input.
 *
 * The constructor takes a Surface obtained from MediaCodec.createInputSurface() and uses that to create
 * an EGL window surface. Calls to eglSwapBuffers() cause a frame of data to be sent to the video encoder.
 *
 * This object owns the Surface -- releasing this will release the Surface too.
 */
public class CodecInputSurface extends OpenGLContext
{
    private Surface mSurface;

    /**
     * Creates a CodecInputSurface from a Surface.
     *
     * @param surface the input surface.
     * @param sharedContext shared context if any.
     */
    public CodecInputSurface(Surface surface, EGLContext sharedContext)
    {
        super(true, surface, sharedContext);
        mSurface = surface;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void release()
    {
        super.release();
        mSurface.release();
    }
}
