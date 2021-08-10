/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.imgstreaming;

import org.atalk.android.util.java.awt.*;
import org.atalk.android.util.java.awt.image.BufferedImage;
import org.atalk.util.OSUtils;

import timber.log.Timber;

/**
 * Capture desktop screen either via native code (JNI) if available or by using
 * <tt>java.awt.Robot</tt>.
 *
 * @author Sebastien Vincent
 * @author Eng Chong Meng
 * @see java.awt.Robot
 */
public class DesktopInteractImpl implements DesktopInteract
{
    /**
     * Screen capture robot.
     */
    private Robot robot = null;

    /**
     * Constructor.
     *
     * @throws AWTException if platform configuration does not allow low-level input control
     * @throws SecurityException if Robot creation is not permitted
     */
    public DesktopInteractImpl()
    {
        // throws AWTException, SecurityException {
        robot = new Robot();
    }

    /**
     * Capture the full desktop screen using native grabber.
     * <p>
     * Contrary to other captureScreen method, it only returns raw bytes and not
     * <tt>BufferedImage</tt>. It is done in order to limit slow operation such as converting ARGB
     * images (uint32_t) to bytes especially for big big screen. For example a 1920x1200 desktop
     * consumes 9 MB of memory for grabbing and another 9 MB array for conversion operation.
     *
     * @param display index of display
     * @param output output buffer to store bytes in. Be sure that output length is sufficient
     * @return true if success, false if JNI error or output length too short
     */
    public boolean captureScreen(int display, byte output[])
    {
        Dimension dim = Toolkit.getDefaultToolkit().getScreenSize();

        return captureScreen(display, 0, 0, dim.width, dim.height, output);
    }

    /**
     * Capture the full desktop screen using native grabber.
     * <p>
     * Contrary to other captureScreen method, it only returns raw bytes and not
     * <tt>BufferedImage</tt>. It is done in order to limit slow operation such as converting ARGB
     * images (uint32_t) to bytes especially for big big screen. For example a 1920x1200 desktop
     * consumes 9 MB of memory for grabbing and another 9 MB array for conversion operation.
     *
     * @param display index of display
     * @param buffer native output buffer to store bytes in. Be sure that output length is sufficient
     * @param bufferLength length of native buffer
     * @return true if success, false if JNI error or output length too short
     */
    public boolean captureScreen(int display, long buffer, int bufferLength)
    {
        Dimension dim = Toolkit.getDefaultToolkit().getScreenSize();
        return captureScreen(display, 0, 0, dim.width, dim.height, buffer, bufferLength);
    }

    /**
     * Capture a part of the desktop screen using native grabber.
     * <p>
     * Contrary to other captureScreen method, it only returns raw bytes and not
     * <tt>BufferedImage</tt>. It is done in order to limit slow operation such as converting ARGB
     * images (uint32_t) to bytes especially for big big screen. For example a 1920x1200 desktop
     * consumes 9 MB of memory for grabbing and another 9 MB array for conversion operation.
     *
     * @param display index of display
     * @param x x position to start capture
     * @param y y position to start capture
     * @param width capture width
     * @param height capture height
     * @param output output buffer to store bytes in. Be sure that output length is sufficient
     * @return true if success, false if JNI error or output length too short
     */
    public boolean captureScreen(int display, int x, int y, int width, int height, byte[] output)
    {
        return (OSUtils.IS_LINUX || OSUtils.IS_MAC || OSUtils.IS_WINDOWS)
                && ScreenCapture.grabScreen(display, x, y, width, height, output);
    }

    /**
     * Capture a part of the desktop screen using native grabber.
     * <p>
     * Contrary to other captureScreen method, it only returns raw bytes and not
     * <tt>BufferedImage</tt>. It is done in order to limit slow operation such as converting ARGB
     * images (uint32_t) to bytes especially for big big screen. For example a 1920x1200 desktop
     * consumes 9 MB of memory for grabbing and another 9 MB array for conversion operation.
     *
     * @param display index of display
     * @param x x position to start capture
     * @param y y position to start capture
     * @param width capture width
     * @param height capture height
     * @param buffer native output buffer to store bytes in. Be sure that output length is sufficient
     * @param bufferLength length of native buffer
     * @return true if success, false if JNI error or output length too short
     */
    public boolean captureScreen(int display, int x, int y, int width, int height, long buffer,
            int bufferLength)
    {
        return (OSUtils.IS_LINUX || OSUtils.IS_MAC || OSUtils.IS_WINDOWS)
                && ScreenCapture.grabScreen(display, x, y, width, height, buffer, bufferLength);
    }

    /**
     * Capture the full desktop screen using <tt>java.awt.Robot</tt>.
     *
     * @return <tt>BufferedImage</tt> of the desktop screen
     */
    public BufferedImage captureScreen()
    {
        Dimension dim = Toolkit.getDefaultToolkit().getScreenSize();

        return captureScreen(0, 0, dim.width, dim.height);
    }

    /**
     * Capture a part of the desktop screen using <tt>java.awt.Robot</tt>.
     *
     * @param x x position to start capture
     * @param y y position to start capture
     * @param width capture width
     * @param height capture height
     * @return <tt>BufferedImage</tt> of a part of the desktop screen or null if Robot problem
     */
    public BufferedImage captureScreen(int x, int y, int width, int height)
    {
        BufferedImage img = null;
        Rectangle rect = null;

        if (robot == null) {
            /* Robot has not been created so abort */
            return null;
        }

        Timber.i("Begin capture: %d", System.nanoTime());
        rect = new Rectangle(x, y, width, height);
        img = robot.createScreenCapture(rect);
        Timber.i("End capture: %s", System.nanoTime());

        return img;
    }
}
