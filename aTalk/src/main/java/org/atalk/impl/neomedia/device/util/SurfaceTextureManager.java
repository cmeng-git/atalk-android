/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.device.util;

import android.graphics.SurfaceTexture;

import org.atalk.impl.neomedia.jmfext.media.protocol.androidcamera.SurfaceStream;

import timber.log.Timber;

/**
 * Manages a SurfaceTexture for local view. Creates SurfaceTexture and CameraSurfaceRenderer objects,
 * and provides functions that wait for frames and render them to the current EGL surface.
 *
 * The SurfaceTexture can be passed to Camera.setPreviewTexture() to receive camera output.
 */
public class SurfaceTextureManager implements SurfaceTexture.OnFrameAvailableListener
{
    private SurfaceTexture mSurfaceTexture;

    private CameraSurfaceRenderer mSurfaceRender;

    protected SurfaceStream mSurfaceStream;

    /**
     * guards frameAvailable
     */
    private final Object frameSyncObject = new Object();

    private boolean frameAvailable;

    public boolean hasProcess;

    /**
     * Create instances of CameraSurfaceRenderer and SurfaceTexture.
     */
    public SurfaceTextureManager(SurfaceStream surfaceStream)
    {
        mSurfaceStream = surfaceStream;
        mSurfaceRender = new CameraSurfaceRenderer();
        mSurfaceRender.surfaceCreated();

        int texName = mSurfaceRender.getTextureId();
        mSurfaceTexture = new SurfaceTexture(texName);
        mSurfaceTexture.setOnFrameAvailableListener(this);
    }

    public void release()
    {
        if (mSurfaceRender != null) {
            mSurfaceRender.release();
            mSurfaceRender = null;
        }
        if (mSurfaceTexture != null) {
            mSurfaceTexture.release();
            mSurfaceTexture = null;
        }
    }

    /**
     * Returns the SurfaceTexture.
     */
    public SurfaceTexture getSurfaceTexture()
    {
        return mSurfaceTexture;
    }

    /**
     * Draws the data from SurfaceTexture onto the current EGL surface.
     */
    public void drawImage()
    {
        mSurfaceRender.drawFrame(mSurfaceTexture);
    }

    /**
     * Latches the next buffer into the texture. Must be called from the thread that created the OutputSurface object.
     */
    public void awaitNewImage()
    {
        final int TIMEOUT_MS = 2500;
        hasProcess = true;

        Timber.w("Waiting for onFrameAvailable!");
        synchronized (frameSyncObject) {
            while (!frameAvailable) {
                try {
                    // Wait for onFrameAvailable() to signal us. Use a timeout to avoid stalling the test if it doesn't arrive.
                    frameSyncObject.wait(TIMEOUT_MS);
                    if (!frameAvailable) {
                        throw new RuntimeException("Camera frame wait timed out");
                    }
                } catch (InterruptedException ie) {
                    throw new RuntimeException(ie);
                }
            }
            frameAvailable = false;
        }
    }

    /**
     * Callback interface for being notified that a new stream frame is available.
     * Must handle both updateTexImage() and swapBuffers in this callback thread
     *
     * @param st SurfaceTexture that gets filled with the stream image.
     */
    @Override
    public void onFrameAvailable(SurfaceTexture st)
    {
        synchronized (frameSyncObject) {
            while (!hasProcess) {
                // waiting on different thread is OK; else locked the whole process
            };
            // Timber.w("Waiting for process completed!");
            mSurfaceTexture = st;
            st.updateTexImage();

            frameAvailable = true;
            hasProcess = false;
            mSurfaceStream.pushEncoderData(st);
            frameSyncObject.notifyAll();
        }
    }
}
