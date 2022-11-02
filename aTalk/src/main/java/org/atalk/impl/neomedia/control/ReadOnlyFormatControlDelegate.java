/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.control;

import java.awt.Component;

import javax.media.Control;
import javax.media.Format;
import javax.media.control.FormatControl;

/**
 * Represents a wrapper of a specific <code>FormatControl</code> instance which does not allow setting
 * its format using {@link FormatControl#setFormat(Format)}.
 *
 * @author Lubomir Marinov
 * @author Eng Chong Meng
 */
public class ReadOnlyFormatControlDelegate extends AbstractFormatControl
{
    /**
     * The <code>FormatControl</code> wrapped by this instance.
     */
    private final FormatControl formatControl;

    /**
     * Initializes a new <code>ReadOnlyFormatControlDelegate</code> instance which is to wrap a
     * specific <code>FormatControl</code> in order to prevent calls to its
     * {@link FormatControl#setFormat(Format)}.
     *
     * @param formatControl the <code>FormatControl</code> which is to have calls to its
     * <code>FormatControl#setFormat(Format)</code> prevented
     */
    public ReadOnlyFormatControlDelegate(FormatControl formatControl)
    {
        this.formatControl = formatControl;
    }

    /**
     * Implements {@link Control#getControlComponent()}.
     *
     * @return a <code>Component</code> which represents UI associated with this instance if any;
     * otherwise, <code>null</code>
     */
    public Component getControlComponent()
    {
        return formatControl.getControlComponent();
    }

    /**
     * Gets the <code>Format</code> of the owner of this <code>FormatControl</code>. Delegates to the
     * wrapped <code>FormatControl</code>.
     *
     * @return the <code>Format</code> of the owner of this <code>FormatControl</code>
     */
    public Format getFormat()
    {
        return formatControl.getFormat();
    }

    /**
     * Gets the <code>Format</code>s supported by the owner of this <code>FormatControl</code>.
     * Delegates to the wrapped <code>FormatControl</code>.
     *
     * @return an array of <code>Format</code>s supported by the owner of this <code>FormatControl</code>
     */
    public Format[] getSupportedFormats()
    {
        return formatControl.getSupportedFormats();
    }

    /**
     * Implements {@link FormatControl#isEnabled()}.
     *
     * @return <code>true</code> if this track is enabled; otherwise, <code>false</code>
     */
    public boolean isEnabled()
    {
        return formatControl.isEnabled();
    }

    /**
     * Implements {@link FormatControl#setEnabled(boolean)}.
     *
     * @param enabled <code>true</code> if this track is to be enabled; otherwise, <code>false</code>
     */
    public void setEnabled(boolean enabled)
    {
        // Ignore the request because this instance is read-only.
    }

    /**
     * Implements {@link FormatControl#setFormat(Format)}. Not supported and just returns the
     * currently set format if the specified <code>Format</code> is supported and <code>null</code> if
     * it is not supported.
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
}
