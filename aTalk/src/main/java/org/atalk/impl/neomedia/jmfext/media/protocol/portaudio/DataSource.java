/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.jmfext.media.protocol.portaudio;

import org.atalk.impl.neomedia.device.AudioSystem;
import org.atalk.impl.neomedia.jmfext.media.protocol.AbstractPullBufferCaptureDevice;

import java.io.IOException;

import javax.media.Format;
import javax.media.MediaLocator;
import javax.media.control.FormatControl;

import timber.log.Timber;

/**
 * Implements <code>DataSource</code> and <code>CaptureDevice</code> for PortAudio.
 *
 * @author Damian Minkov
 * @author Lyubomir Marinov
 * @author Eng Chong Meng
 */
public class DataSource extends AbstractPullBufferCaptureDevice
{
    /**
     * The indicator which determines whether this <code>DataSource</code> will use audio quality
     * improvement in accord with the preferences of the user.
     */
    private final boolean audioQualityImprovement;

    /**
     * The list of <code>Format</code>s in which this <code>DataSource</code> is capable of capturing audio
     * data.
     */
    private final Format[] supportedFormats;

    /**
     * Initializes a new <code>DataSource</code> instance.
     */
    public DataSource()
    {
        this.supportedFormats = null;
        this.audioQualityImprovement = true;
    }

    /**
     * Initializes a new <code>DataSource</code> instance from a specific <code>MediaLocator</code>.
     *
     * @param locator the <code>MediaLocator</code> to create the new instance from
     */
    public DataSource(MediaLocator locator)
    {
        this(locator, null, true);
    }

    /**
     * Initializes a new <code>DataSource</code> instance from a specific <code>MediaLocator</code> and
     * which has a specific list of <code>Format</code> in which it is capable of capturing audio data
     * overriding its registration with JMF and optionally uses audio quality improvement in accord
     * with the preferences of the user.
     *
     * @param locator the <code>MediaLocator</code> to create the new instance from
     * @param supportedFormats the list of <code>Format</code>s in which the new instance is to be capable of capturing
     * audio data
     * @param audioQualityImprovement <code>true</code> if audio quality improvement is to be enabled in accord with the
     * preferences of the user or <code>false</code> to completely disable audio quality
     * improvement
     */
    public DataSource(MediaLocator locator, Format[] supportedFormats,
            boolean audioQualityImprovement)
    {
        super(locator);

        this.supportedFormats = (supportedFormats == null) ? null : supportedFormats.clone();
        this.audioQualityImprovement = audioQualityImprovement;
    }

    /**
     * Creates a new <code>PullBufferStream</code> which is to be at a specific zero-based index in the
     * list of streams of this <code>PullBufferDataSource</code>. The <code>Format</code>-related
     * information of the new instance is to be abstracted by a specific <code>FormatControl</code>.
     *
     * @param streamIndex the zero-based index of the <code>PullBufferStream</code> in the list of streams of this
     * <code>PullBufferDataSource</code>
     * @param formatControl the <code>FormatControl</code> which is to abstract the <code>Format</code>-related
     * information of the new instance
     * @return a new <code>PullBufferStream</code> which is to be at the specified <code>streamIndex</code>
     * in the list of streams of this <code>PullBufferDataSource</code> and which has its
     * <code>Format</code>-related information abstracted by the specified
     * <code>formatControl</code>
     * @see AbstractPullBufferCaptureDevice#createStream(int, FormatControl)
     */
    @Override
    protected PortAudioStream createStream(int streamIndex, FormatControl formatControl)
    {
        return new PortAudioStream(this, formatControl, audioQualityImprovement);
    }

    /**
     * Opens a connection to the media source specified by the <code>MediaLocator</code> of this
     * <code>DataSource</code>.
     *
     * @throws IOException if anything goes wrong while opening the connection to the media source specified by
     * the <code>MediaLocator</code> of this <code>DataSource</code>
     * @see AbstractPullBufferCaptureDevice#doConnect()
     */
    @Override
    protected void doConnect()
            throws IOException
    {
        super.doConnect();

        String deviceID = getDeviceID();

        synchronized (getStreamSyncRoot()) {
            for (Object stream : getStreams())
                ((PortAudioStream) stream).setDeviceID(deviceID);
        }
    }

    /**
     * Closes the connection to the media source specified by the <code>MediaLocator</code> of this
     * <code>DataSource</code>. Allows extenders to override and be sure that there will be no request
     * to close a connection if the connection has not been opened yet.
     */
    @Override
    protected void doDisconnect()
    {
        try {
            synchronized (getStreamSyncRoot()) {
                Object[] streams = streams();

                if (streams != null) {
                    for (Object stream : streams) {
                        try {
                            ((PortAudioStream) stream).setDeviceID(null);
                        } catch (IOException ioex) {
                            Timber.e(ioex, "Failed to close %s", stream.getClass().getSimpleName());
                        }
                    }
                }
            }
        } finally {
            super.doDisconnect();
        }
    }

    /**
     * Gets the device index of the PortAudio device identified by the <code>MediaLocator</code> of this
     * <code>DataSource</code>.
     *
     * @return the device index of a PortAudio device identified by the <code>MediaLocator</code> of
     * this <code>DataSource</code>
     * @throws IllegalStateException if there is no <code>MediaLocator</code> associated with this <code>DataSource</code>
     */
    private String getDeviceID()
    {
        MediaLocator locator = getLocator();

        if (locator == null)
            throw new IllegalStateException("locator");
        else
            return getDeviceID(locator);
    }

    /**
     * Gets the device index of a PortAudio device from a specific <code>MediaLocator</code> identifying
     * it.
     *
     * @param locator the <code>MediaLocator</code> identifying the device index of a PortAudio device to get
     * @return the device index of a PortAudio device identified by <code>locator</code>
     */
    public static String getDeviceID(MediaLocator locator)
    {
        if (locator == null) {
            /*
             * Explicitly throw a NullPointerException because the implicit one does not have a
             * message and is thus a bit more difficult to debug.
             */
            throw new NullPointerException("locator");
        }
        else if (AudioSystem.LOCATOR_PROTOCOL_PORTAUDIO.equalsIgnoreCase(locator.getProtocol())) {
            String remainder = locator.getRemainder();

            if ((remainder != null) && (remainder.charAt(0) == '#'))
                remainder = remainder.substring(1);
            return remainder;
        }
        else {
            throw new IllegalArgumentException("locator.protocol");
        }
    }

    /**
     * Gets the <code>Format</code>s which are to be reported by a <code>FormatControl</code> as supported
     * formats for a <code>PullBufferStream</code> at a specific zero-based index in the list of streams
     * of this <code>PullBufferDataSource</code>.
     *
     * @param streamIndex the zero-based index of the <code>PullBufferStream</code> for which the specified
     * <code>FormatControl</code> is to report the list of supported <code>Format</code>s
     * @return an array of <code>Format</code>s to be reported by a <code>FormatControl</code> as the
     * supported formats for the <code>PullBufferStream</code> at the specified
     * <code>streamIndex</code> in the list of streams of this <code>PullBufferDataSource</code>
     * @see AbstractPullBufferCaptureDevice#getSupportedFormats(int)
     */
    @Override
    protected Format[] getSupportedFormats(int streamIndex)
    {
        return (supportedFormats == null) ? super.getSupportedFormats(streamIndex)
                : supportedFormats;
    }
}
