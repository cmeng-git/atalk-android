/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.control;

import javax.media.Control;

/**
 * Defines the interface for controlling <code>CaptureDevice</code>s/ <code>DataSource</code>s associated
 * with the <code>imgstreaming</code> FMJ/JMF protocol.
 *
 * @author Lyubomir Marinov
 */
public interface ImgStreamingControl extends Control {
    /**
     * Set the display index and the origin of the stream associated with a specific index in the
     * <code>DataSource</code> of this <code>Control</code>.
     *
     * @param streamIndex the index in the associated <code>DataSource</code> of the stream to set the display index
     * and the origin of
     * @param displayIndex the display index to set on the specified stream
     * @param x the x coordinate of the origin to set on the specified stream
     * @param y the y coordinate of the origin to set on the specified stream
     */
    public void setOrigin(int streamIndex, int displayIndex, int x, int y);
}
