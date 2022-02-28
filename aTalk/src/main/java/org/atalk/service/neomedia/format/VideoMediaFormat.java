/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.service.neomedia.format;

import java.awt.Dimension;

/**
 * The interface represents a video format. Video formats characterize video streams and the
 * <code>VideoMediaFormat</code> interface gives access to some of their properties such as encoding and clock rate.
 *
 * @author Emil Ivov
 */
public interface VideoMediaFormat extends MediaFormat
{
    /**
     * Returns the size of the image that this <code>VideoMediaFormat</code> describes.
     *
     * @return a <code>java.awt.Dimension</code> instance indicating the image size (in pixels) of this <code>VideoMediaFormat</code>.
     */
    public Dimension getSize();

    /**
     * Returns the frame rate associated with this <code>MediaFormat</code>.
     *
     * @return The frame rate associated with this format.
     */
    public float getFrameRate();
}
