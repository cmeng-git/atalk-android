/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.control;

import java.awt.Component;
import java.util.ArrayList;
import java.util.List;

import javax.media.Control;
import javax.media.Controls;
import javax.media.Format;
import javax.media.control.FormatControl;

/**
 * Provides an abstract implementation of <code>FormatControl</code> which facilitates implementers by requiring
 * them to implement just {@link FormatControl#getSupportedFormats()} and {@link FormatControl#getFormat()}.
 * https://docs.oracle.com/cd/E17802_01/j2se/javase/technologies/desktop/media/jmf/2.1.1/apidocs/javax/media/control/FormatControl.html
 *
 * @author Lyubomir Marinov
 * @author Eng Chong Meng
 */
public abstract class AbstractFormatControl implements FormatControl
{
    /**
     * The <code>Format</code> of this <code>FormatControl</code> and, respectively, of the media data of its owner.
     */
    protected Format mFormat = null;

    /**
     * The indicator which determines whether this track is enabled.
     */
    private boolean enabled;

    /**
     * Implements {@link Control#getControlComponent()}. Returns <code>null</code>.
     *
     * @return a <code>Component</code> which represents UI associated with this instance if any; otherwise, <code>null</code>
     */
    public Component getControlComponent()
    {
        // No Component is exported by this instance.
        return null;
    }

    /**
     * Gets an array of <code>FormatControl</code> instances from the list of controls available for a
     * specific <code>Controls</code> implementation.
     *
     * @param controlsImpl the <code>Controls</code> implementation from which the <code>FormatControl</code>
     * instances are to be retrieved
     * @return an array of <code>FormatControl</code> instances from the list of controls available for
     * the specified <code>Controls</code> implementation
     */
    public static FormatControl[] getFormatControls(Controls controlsImpl)
    {
        List<FormatControl> formatControls = new ArrayList<>();

        for (Object control : controlsImpl.getControls()) {
            if (control instanceof FormatControl)
                formatControls.add((FormatControl) control);
        }
        return formatControls.toArray(new FormatControl[0]);
    }

    /**
     * Implements {@link FormatControl#isEnabled()}.
     *
     * @return <code>true</code> if this track is enabled; otherwise, <code>false</code>
     */
    public boolean isEnabled()
    {
        return enabled;
    }

    /**
     * Implements {@link FormatControl#setEnabled(boolean)}.
     *
     * @param enabled <code>true</code> if this track is to be enabled; otherwise, <code>false</code>
     */
    public void setEnabled(boolean enabled)
    {
        this.enabled = enabled;
    }

    /**
     * Obtain the format that this object is set to.
     *
     * @return the current format.
     */
    public Format getFormat()
    {
        return mFormat;
    }

    /**
     * Implements {@link FormatControl#setFormat(Format)}. Not supported and just returns the currently
     * set format if the specified <code>Format</code> is supported and <code>null</code> if it is not supported.
     *
     * @param format the <code>Format</code> to be set on this instance
     * @return the currently set <code>Format</code> after the attempt to set it on this instance if
     * <code>format</code> is supported by this instance and regardless of whether it was
     * actually set; <code>null</code> if <code>format</code> is not supported by this instance
     */
    public Format setFormat(Format format)
    {
        return setFormat(this, format);
    }

    /**
     * Implements setting the <code>Format</code> of a specific <code>FormatControl</code> as documented for
     * {@link FormatControl#setFormat(Format) for JMF} in the case of not supporting <code>Format</code> setting.
     *
     * https://docs.oracle.com/cd/E17802_01/j2se/javase/technologies/desktop/media/jmf/2.1.1/apidocs/javax/media/control/FormatControl.html
     * Sets the data format. The method returns null if the format is not supported. Otherwise, it returns the format that's actually set.
     *
     * @param formatControl the <code>FormatControl</code> for which the functionality is implemented
     * @param format the <code>Format</code> specified to be set to <code>formatControl</code> and which will be
     * ignored in accord with the documentation of <code>FormatControl#setFormat(Format)</code>
     * @return null if the format is not supported; otherwise return the format that's actually set
     */
    public Format setFormat(FormatControl formatControl, Format format)
    {
        mFormat = null;
        if (format != null) {
            // Determine whether the specified format is supported by this instance
            for (Format sFormat : formatControl.getSupportedFormats())
                if (sFormat.matches(format)) {
                    mFormat = sFormat;
                    break;
                }
        }
        return mFormat;
    }
}
