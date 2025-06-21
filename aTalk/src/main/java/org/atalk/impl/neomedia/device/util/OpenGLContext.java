/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.device.util;

import android.opengl.EGL14;
import android.opengl.EGLConfig;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.EGLExt;
import android.opengl.EGLSurface;
import android.opengl.GLUtils;

import timber.log.Timber;

/**
 * Code for EGL context handling
 */
public class OpenGLContext {
    private static final int EGL_RECORDABLE_ANDROID = 0x3142;

    private EGLDisplay mEGLDisplay = EGL14.EGL_NO_DISPLAY;
    private EGLContext mEGLContext = EGL14.EGL_NO_CONTEXT;
    private EGLSurface mEGLSurface = EGL14.EGL_NO_SURFACE;

    /**
     * Prepares EGL. We want a GLES 2.0 context and a surface that supports recording.
     */
    public OpenGLContext(boolean recorder, Object objSurface, EGLContext sharedContext) {
        mEGLDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);
        if (mEGLDisplay == EGL14.EGL_NO_DISPLAY) {
            throw new RuntimeException("unable to get EGL14 display");
        }
        int[] majorVersion = new int[1];
        int[] minorVersion = new int[1];
        if (!EGL14.eglInitialize(mEGLDisplay, majorVersion, 0, minorVersion, 0)) {
            mEGLDisplay = null;
            throw new RuntimeException("unable to initialize EGL14");
        }
        // Timber.i("EGL version: %s.%s", majorVersion[0], minorVersion[0]);

        // Configure context for OpenGL ES 2.0.
        EGLConfig eglConfig = chooseEglConfig(mEGLDisplay, recorder);
        int[] attrib_list = {EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE};
        mEGLContext = EGL14.eglCreateContext(mEGLDisplay, eglConfig, sharedContext, attrib_list, 0);
        checkEglError("eglCreateContext");

        // Create a window surface with default values, and attach it to the Surface we received.
        int[] surfaceAttribs = {EGL14.EGL_NONE};
        mEGLSurface = EGL14.eglCreateWindowSurface(mEGLDisplay, eglConfig, objSurface, surfaceAttribs, 0);
        checkEglError("eglCreateWindowSurface");
    }

    private EGLConfig chooseEglConfig(EGLDisplay eglDisplay, boolean recorder) {
        EGLConfig[] configs = new EGLConfig[1];
        int[] attribList;
        if (recorder) {
            // Configure EGL for recording and OpenGL ES 2.0.
            attribList = new int[]{EGL14.EGL_RED_SIZE, 8, EGL14.EGL_GREEN_SIZE, 8,
                    EGL14.EGL_BLUE_SIZE, 8, EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                    EGL_RECORDABLE_ANDROID, 1, EGL14.EGL_NONE};
        }
        else {
            // Configure EGL for OpenGL ES 2.0 only.
            attribList = new int[]{EGL14.EGL_RED_SIZE, 8, EGL14.EGL_GREEN_SIZE, 8,
                    EGL14.EGL_BLUE_SIZE, 8, EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                    EGL14.EGL_NONE};
        }

        int[] numconfigs = new int[1];
        if (!EGL14.eglChooseConfig(eglDisplay, attribList, 0, configs, 0, configs.length,
                numconfigs, 0)) {
            throw new IllegalArgumentException("eglChooseConfig failed " + GLUtils.getEGLErrorString(EGL14.eglGetError()));
        }
        else if (numconfigs[0] <= 0) {
            throw new IllegalArgumentException("eglChooseConfig failed " + GLUtils.getEGLErrorString(EGL14.eglGetError()));
        }
        return configs[0];
    }

    /**
     * Discards all resources held by this class, notably the EGL context. Also releases the Surface
     * that was passed to our constructor.
     */
    public void release() {
        if (mEGLDisplay != EGL14.EGL_NO_DISPLAY) {
            // Android is unusual in that it uses a reference-counted EGLDisplay.
            // So for every eglInitialize() we need an eglTerminate().
            EGL14.eglMakeCurrent(mEGLDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT);
            EGL14.eglDestroySurface(mEGLDisplay, mEGLSurface);
            EGL14.eglDestroyContext(mEGLDisplay, mEGLContext);
            EGL14.eglTerminate(mEGLDisplay);
            EGL14.eglReleaseThread();
        }
        mEGLDisplay = EGL14.EGL_NO_DISPLAY;
        mEGLContext = EGL14.EGL_NO_CONTEXT;
        mEGLSurface = EGL14.EGL_NO_SURFACE;
    }

    public void makeCurrent() {
        EGLContext ctx = EGL14.eglGetCurrentContext();
        EGLSurface surface = EGL14.eglGetCurrentSurface(EGL14.EGL_DRAW);
        if (!mEGLContext.equals(ctx) || !mEGLSurface.equals(surface)) {
            if (!EGL14.eglMakeCurrent(mEGLDisplay, mEGLSurface, mEGLSurface, mEGLContext)) {
                throw new RuntimeException("eglMakeCurrent failed "
                        + GLUtils.getEGLErrorString(EGL14.eglGetError()));
            }
        }
    }

    /**
     * Sets "no surface" and "no context" on the current display.
     */
    public void releaseEGLSurfaceContext() {
        if (mEGLDisplay != EGL14.EGL_NO_DISPLAY) {
            EGL14.eglMakeCurrent(mEGLDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT);
        }
    }

    /**
     * Calls eglSwapBuffers. Use this to "publish" the current frame.
     */
    public void swapBuffers() {
        if (!EGL14.eglSwapBuffers(mEGLDisplay, mEGLSurface)) {
            throw new RuntimeException("Cannot swap buffers");
        }
        checkEglError("opSwapBuffers");
    }

    /**
     * Sends the presentation time stamp to EGL. Time is expressed in nanoseconds.
     */
    public void setPresentationTime(long nsecs) {
        EGLExt.eglPresentationTimeANDROID(mEGLDisplay, mEGLSurface, nsecs);
        checkEglError("eglPresentationTimeANDROID");
    }

    /**
     * Checks for EGL errors. Throws an exception if one is found.
     */
    private void checkEglError(String msg) {
        int error;
        if ((error = EGL14.eglGetError()) != EGL14.EGL_SUCCESS) {
            // aTalkApp.showToastMessage(msg + ": EGL error: 0x" + Integer.toHexString(error));
            throw new RuntimeException(msg + ": EGL error: 0x" + Integer.toHexString(error));
        }
    }

    public EGLContext getContext() {
        return mEGLContext;
    }
}
