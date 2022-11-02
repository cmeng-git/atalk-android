/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.jmfext.media.renderer;

import org.atalk.impl.neomedia.control.ControlsAdapter;

import javax.media.Format;
import javax.media.Renderer;

import timber.log.Timber;

/**
 * Provides an abstract base implementation of <code>Renderer</code> in order to facilitate extenders.
 *
 * @param <T> the type of <code>Format</code> of the media data processed as input by <code>AbstractRenderer</code>
 * @author Lyubomir Marinov
 * @author Eng Chong Meng
 */
public abstract class AbstractRenderer<T extends Format> extends ControlsAdapter
        implements Renderer
{
    /**
     * The <code>Format</code> of the media data processed as input by this <code>Renderer</code>.
     */
    protected T inputFormat;

    /**
     * Resets the state of this <code>PlugIn</code>.
     */
    public void reset()
    {
        // TODO Auto-generated method stub
    }

    /**
     * Sets the <code>Format</code> of the media data to be rendered by this <code>Renderer</code>.
     *
     * @param format the <code>Format</code> of the media data to be rendered by this <code>Renderer</code>
     * @return <code>null</code> if the specified <code>format</code> is not compatible with this
     * <code>Renderer</code>; otherwise, the <code>Format</code> which has been successfully set
     */
    public Format setInputFormat(Format format)
    {
        Format matchingFormat = null;
        for (Format supportedInputFormat : getSupportedInputFormats()) {
            if (supportedInputFormat.matches(format)) {
                matchingFormat = supportedInputFormat.intersects(format);
                break;
            }
        }
        if (matchingFormat == null)
            return null;

        @SuppressWarnings("unchecked")
        T t = (T) matchingFormat;

        inputFormat = t;
        return inputFormat;
    }

    /**
     * Changes the priority of the current thread to a specific value.
     *
     * @param threadPriority the priority to set the current thread to
     */
    public static void useThreadPriority(int threadPriority)
    {
        Throwable throwable = null;
        try {
            Thread.currentThread().setPriority(threadPriority);
        } catch (IllegalArgumentException | SecurityException iae) {
            throwable = iae;
        }
        if (throwable != null) {
            Timber.w(throwable, "Failed to use thread priority: %s", threadPriority);
        }
    }
}
