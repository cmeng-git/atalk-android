/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.device;

import org.atalk.service.neomedia.RTPExtension;
import org.atalk.service.neomedia.device.MediaDevice;
import org.atalk.service.neomedia.format.MediaFormat;

import java.io.IOException;
import java.util.List;

import javax.media.*;
import javax.media.protocol.DataSource;

/**
 * Defines the interface for <tt>MediaDevice</tt> required by the <tt>org.atalk.impl.neomedia</tt>
 * implementation of <tt>org.atalk.service.neomedia</tt>.
 *
 * @author Lyubomir Marinov
 * @author Eng Chong Meng
 */
public abstract class AbstractMediaDevice implements MediaDevice
{
    /**
     * Connects to a specific <tt>CaptureDevice</tt> given in the form of a <tt>DataSource</tt>.
     * Explicitly defined in order to allow extenders to customize the connect procedure.
     *
     * @param captureDevice the <tt>CaptureDevice</tt> to be connected to
     * @throws IOException if anything wrong happens while connecting to the specified <tt>captureDevice</tt>
     */
    public void connect(DataSource captureDevice)
            throws IOException
    {
        if (captureDevice == null)
            throw new NullPointerException("captureDevice");
        try {
            captureDevice.connect();
        } catch (NullPointerException npe) {
            /*
             * The old media says it happens when the operating system does not support the operation.
             */
            throw new IOException(npe);
        }
    }

    /**
     * Creates a <tt>DataSource</tt> instance for this <tt>MediaDevice</tt> which gives access to the captured media.
     *
     * @return a <tt>DataSource</tt> instance which gives access to the media captured by this <tt>MediaDevice</tt>
     */
    protected abstract DataSource createOutputDataSource();

    /**
     * Initializes a new <tt>Processor</tt> instance which is to be used to play back media on this
     * <tt>MediaDevice</tt> . Allows extenders to, for example, disable the playback on this
     * <tt>MediaDevice</tt> by completely overriding and returning <tt>null</tt>.
     *
     * @param dataSource the <tt>DataSource</tt> which is to be played back by the new <tt>Processor</tt> instance
     * @return a new <tt>Processor</tt> instance which is to be used to play back the media provided by the specified
     * <tt>dataSource</tt> or <tt>null</tt> if the specified <tt>dataSource</tt> is to not be played back
     * @throws Exception if an exception is thrown by {@link DataSource#connect()},
     * {@link Manager#createProcessor(DataSource)}, or {@link DataSource#disconnect()}
     */
    protected Processor createPlayer(DataSource dataSource)
            throws Exception
    {
        Processor player = null;

        // A Player is documented to be created on a connected DataSource.
        dataSource.connect();
        try {
            player = Manager.createProcessor(dataSource);
        } finally {
            if (player == null)
                dataSource.disconnect();
        }
        return player;
    }

    /**
     * Initializes a new <tt>Renderer</tt> instance which is to play back media on this
     * <tt>MediaDevice</tt>. Allows extenders to initialize a specific <tt>Renderer</tt> instance.
     * The implementation of <tt>AbstractMediaDevice</tt> returns <tt>null</tt> which means that it
     * is left to FMJ to choose a suitable <tt>Renderer</tt> irrespective of this
     * <tt>MediaDevice</tt>.
     *
     * @return a new <tt>Renderer</tt> instance which is to play back media on this
     * <tt>MediaDevice</tt> or <tt>null</tt> if a suitable <tt>Renderer</tt> is to be chosen
     * irrespective of this <tt>MediaDevice</tt>
     */
    protected Renderer createRenderer()
    {
        return null;
    }

    /**
     * Creates a new <tt>MediaDeviceSession</tt> instance which is to represent the use of this
     * <tt>MediaDevice</tt> by a <tt>MediaStream</tt>.
     *
     * @return a new <tt>MediaDeviceSession</tt> instance which is to represent the use of this
     * <tt>MediaDevice</tt> by a <tt>MediaStream</tt>
     */
    public MediaDeviceSession createSession()
    {
        switch (getMediaType()) {
            case VIDEO:
                return new VideoMediaDeviceSession(this);
            default:
                return new AudioMediaDeviceSession(this);
        }
    }

    /**
     * Returns a <tt>List</tt> containing (at the time of writing) a single extension descriptor
     * indicating <tt>RECVONLY</tt> support for mixer-to-client audio levels.
     *
     * @return a <tt>List</tt> containing the <tt>CSRC_AUDIO_LEVEL_URN</tt> extension descriptor.
     */
    public List<RTPExtension> getSupportedExtensions()
    {
        return null;
    }

    /**
     * Gets a list of <tt>MediaFormat</tt>s supported by this <tt>MediaDevice</tt>.
     *
     * @return the list of <tt>MediaFormat</tt>s supported by this device
     * @see MediaDevice#getSupportedFormats()
     */
    public List<MediaFormat> getSupportedFormats()
    {
        return getSupportedFormats(null, null);
    }
}
