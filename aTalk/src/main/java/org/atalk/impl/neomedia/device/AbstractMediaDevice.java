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

import javax.media.Manager;
import javax.media.Processor;
import javax.media.Renderer;
import javax.media.protocol.DataSource;

/**
 * Defines the interface for <code>MediaDevice</code> required by the <code>org.atalk.impl.neomedia</code>
 * implementation of <code>org.atalk.service.neomedia</code>.
 *
 * @author Lyubomir Marinov
 * @author Eng Chong Meng
 */
public abstract class AbstractMediaDevice implements MediaDevice
{
    /**
     * Connects to a specific <code>CaptureDevice</code> given in the form of a <code>DataSource</code>.
     * Explicitly defined in order to allow extenders to customize the connect procedure.
     *
     * @param captureDevice the <code>CaptureDevice</code> to be connected to
     * @throws IOException if anything wrong happens while connecting to the specified <code>captureDevice</code>
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
     * Creates a <code>DataSource</code> instance for this <code>MediaDevice</code> which gives access to the captured media.
     *
     * @return a <code>DataSource</code> instance which gives access to the media captured by this <code>MediaDevice</code>
     */
    protected abstract DataSource createOutputDataSource();

    /**
     * Initializes a new <code>Processor</code> instance which is to be used to play back media on this
     * <code>MediaDevice</code> . Allows extenders to, for example, disable the playback on this
     * <code>MediaDevice</code> by completely overriding and returning <code>null</code>.
     *
     * @param dataSource the <code>DataSource</code> which is to be played back by the new <code>Processor</code> instance
     * @return a new <code>Processor</code> instance which is to be used to play back the media provided by the specified
     * <code>dataSource</code> or <code>null</code> if the specified <code>dataSource</code> is to not be played back
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
     * Initializes a new <code>Renderer</code> instance which is to play back media on this
     * <code>MediaDevice</code>. Allows extenders to initialize a specific <code>Renderer</code> instance.
     * The implementation of <code>AbstractMediaDevice</code> returns <code>null</code> which means that it
     * is left to FMJ to choose a suitable <code>Renderer</code> irrespective of this
     * <code>MediaDevice</code>.
     *
     * @return a new <code>Renderer</code> instance which is to play back media on this
     * <code>MediaDevice</code> or <code>null</code> if a suitable <code>Renderer</code> is to be chosen
     * irrespective of this <code>MediaDevice</code>
     */
    protected Renderer createRenderer()
    {
        return null;
    }

    /**
     * Creates a new <code>MediaDeviceSession</code> instance which is to represent the use of this
     * <code>MediaDevice</code> by a <code>MediaStream</code>.
     *
     * @return a new <code>MediaDeviceSession</code> instance which is to represent the use of this
     * <code>MediaDevice</code> by a <code>MediaStream</code>
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
     * Returns a <code>List</code> containing (at the time of writing) a single extension descriptor
     * indicating <code>RECVONLY</code> support for mixer-to-client audio levels.
     *
     * @return a <code>List</code> containing the <code>CSRC_AUDIO_LEVEL_URN</code> extension descriptor.
     */
    public List<RTPExtension> getSupportedExtensions()
    {
        return null;
    }

    /**
     * Gets a list of <code>MediaFormat</code>s supported by this <code>MediaDevice</code>.
     *
     * @return the list of <code>MediaFormat</code>s supported by this device
     * @see MediaDevice#getSupportedFormats()
     */
    public List<MediaFormat> getSupportedFormats()
    {
        return getSupportedFormats(null, null);
    }
}
