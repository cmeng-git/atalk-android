/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.jmfext.media.protocol;

import java.io.IOException;

import javax.media.CaptureDeviceInfo;
import javax.media.Format;
import javax.media.MediaLocator;
import javax.media.Time;
import javax.media.control.FormatControl;
import javax.media.control.FrameRateControl;
import javax.media.protocol.CaptureDevice;
import javax.media.protocol.ContentDescriptor;
import javax.media.protocol.PullBufferDataSource;
import javax.media.protocol.PullBufferStream;

/**
 * Provides a base implementation of <code>PullBufferDataSource</code> and <code>CaptureDevice</code> in
 * order to facilitate implementers by taking care of boilerplate in the most common cases.
 *
 * @author Lyubomir Marinov
 */
public abstract class AbstractPullBufferCaptureDevice extends PullBufferDataSource
        implements CaptureDevice
{

    /**
     * The <code>CaptureDeviceInfo</code>.
     */
    private CaptureDeviceInfo deviceInfo;

    /**
     * The <code>AbstractBufferCaptureDevice</code> which provides the implementation of this
     * <code>AbstractPullBufferCaptureDevice</code>.
     */
    private final AbstractBufferCaptureDevice<AbstractPullBufferStream<?>> impl
            = new AbstractBufferCaptureDevice<AbstractPullBufferStream<?>>()
    {
        @Override
        protected FrameRateControl createFrameRateControl()
        {
            return AbstractPullBufferCaptureDevice.this.createFrameRateControl();
        }

        @Override
        protected AbstractPullBufferStream<?> createStream(int streamIndex, FormatControl formatControl)
        {
            return AbstractPullBufferCaptureDevice.this.createStream(streamIndex, formatControl);
        }

        @Override
        protected void doConnect()
                throws IOException
        {
            AbstractPullBufferCaptureDevice.this.doConnect();
        }

        @Override
        protected void doDisconnect()
        {
            AbstractPullBufferCaptureDevice.this.doDisconnect();
        }

        @Override
        protected void doStart()
                throws IOException
        {
            AbstractPullBufferCaptureDevice.this.doStart();
        }

        @Override
        protected void doStop()
                throws IOException
        {
            AbstractPullBufferCaptureDevice.this.doStop();
        }

        @Override
        public CaptureDeviceInfo getCaptureDeviceInfo()
        {
            return AbstractPullBufferCaptureDevice.this.getCaptureDeviceInfo();
        }

        @Override
        protected Format getFormat(int streamIndex, Format oldValue)
        {
            return AbstractPullBufferCaptureDevice.this.getFormat(streamIndex, oldValue);
        }

        @Override
        protected Format[] getSupportedFormats(int streamIndex)
        {
            return AbstractPullBufferCaptureDevice.this.getSupportedFormats(streamIndex);
        }

        @Override
        protected Format setFormat(int streamIndex, Format oldValue, Format newValue)
        {
            return AbstractPullBufferCaptureDevice.this.setFormat(streamIndex, oldValue, newValue);
        }
    };

    /**
     * Initializes a new <code>AbstractPullBufferCaptureDevice</code> instance.
     */
    protected AbstractPullBufferCaptureDevice()
    {
    }

    /**
     * Initializes a new <code>AbstractPullBufferCaptureDevice</code> instance from a specific <code>MediaLocator</code>.
     *
     * @param locator the <code>MediaLocator</code> to create the new instance from
     */
    protected AbstractPullBufferCaptureDevice(MediaLocator locator)
    {
        setLocator(locator);
    }

    /**
     * Opens a connection to the media source specified by the <code>MediaLocator</code> of this <code>DataSource</code>.
     *
     * @throws IOException if anything goes wrong while opening the connection to the media source specified by
     * the <code>MediaLocator</code> of this <code>DataSource</code>
     */
    @Override
    public void connect()
            throws IOException
    {
        impl.connect();
    }

    /**
     * Creates a new <code>FrameRateControl</code> instance which is to allow the getting and
     * setting of the frame rate of this <code>AbstractPullBufferCaptureDevice</code>.
     *
     * @return a new <code>FrameRateControl</code> instance which is to allow the getting and
     * setting of the frame rate of this <code>AbstractPullBufferCaptureDevice</code>
     */
    protected FrameRateControl createFrameRateControl()
    {
        return null;
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
     * <code>Format</code>-related information abstracted by the specified <code>formatControl</code>
     */
    protected abstract AbstractPullBufferStream<?> createStream(int streamIndex, FormatControl formatControl);

    /**
     * Closes the connection to the media source specified by the <code>MediaLocator</code> of this
     * <code>DataSource</code>. If such a connection has not been opened, the call is ignored.
     */
    @Override
    public void disconnect()
    {
        impl.disconnect();
    }

    /**
     * Opens a connection to the media source specified by the <code>MediaLocator</code> of this
     * <code>DataSource</code>. Allows extenders to override and be sure that there will be no request
     * to open a connection if the connection has already been opened.
     *
     * @throws IOException if anything goes wrong while opening the connection to the media source specified by
     * the <code>MediaLocator</code> of this <code>DataSource</code>
     */
    protected void doConnect()
            throws IOException
    {
    }

    /**
     * Closes the connection to the media source specified by the <code>MediaLocator</code> of this
     * <code>DataSource</code>. Allows extenders to override and be sure that there will be no request
     * to close a connection if the connection has not been opened yet.
     */
    protected void doDisconnect()
    {
        /*
         * While it is not clear whether the streams can be released upon disconnect,
         * com.imb.media.protocol.SuperCloneableDataSource gets the streams of the DataSource it
         * adapts (i.e. this DataSource when SourceCloneable support is to be created for it)
         * before #connect(). Unfortunately, it means that it isn't clear when the streams are
         * to be disposed.
         */
    }

    /**
     * Starts the transfer of media data from this <code>DataSource</code>. Allows extenders to
     * override and be sure that there will be no request to start the transfer of media data if
     * it has already been started.
     *
     * @throws IOException if anything goes wrong while starting the transfer of media data from this
     * <code>DataSource</code>
     */
    protected void doStart()
            throws IOException
    {
        impl.defaultDoStart();
    }

    /**
     * Stops the transfer of media data from this <code>DataSource</code>. Allows extenders to override and be
     * sure that there will be no request to stop the transfer of media data if it has not been started yet.
     *
     * @throws IOException if anything goes wrong while stopping the transfer of media data from this <code>DataSource</code>
     */
    protected void doStop()
            throws IOException
    {
        impl.defaultDoStop();
    }

    /**
     * Gets the <code>CaptureDeviceInfo</code> of this <code>CaptureDevice</code> which describes it.
     *
     * @return the <code>CaptureDeviceInfo</code> of this <code>CaptureDevice</code> which describes it
     */
    public CaptureDeviceInfo getCaptureDeviceInfo()
    {
        return (deviceInfo == null)
                ? AbstractBufferCaptureDevice.getCaptureDeviceInfo(this) : deviceInfo;
    }

    /**
     * Gets the content type of the media represented by this instance. The
     * <code>AbstractPullBufferCaptureDevice</code> implementation always returns
     * {@link ContentDescriptor#RAW}.
     *
     * @return the content type of the media represented by this instance
     */
    @Override
    public String getContentType()
    {
        return ContentDescriptor.RAW;
    }

    /**
     * Gets the control of the specified type available for this instance.
     *
     * @param controlType the type of the control available for this instance to be retrieved
     * @return an <code>Object</code> which represents the control of the specified type available for
     * this instance if such a control is indeed available; otherwise, <code>null</code>
     */
    @Override
    public Object getControl(String controlType)
    {
        return impl.getControl(controlType);
    }

    /**
     * Implements {@link javax.media.Controls#getControls()}. Gets the controls available for this instance.
     *
     * @return an array of <code>Object</code>s which represent the controls available for this instance
     */
    @Override
    public Object[] getControls()
    {
        return impl.getControls();
    }

    /**
     * Gets the duration of the media represented by this instance. The
     * <code>AbstractPullBufferCaptureDevice</code> always returns {@link #DURATION_UNBOUNDED}.
     *
     * @return the duration of the media represented by this instance
     */
    @Override
    public Time getDuration()
    {
        return DURATION_UNBOUNDED;
    }

    /**
     * Gets the <code>Format</code> to be reported by the <code>FormatControl</code> of a
     * <code>PullBufferStream</code> at a specific zero-based index in the list of streams of this
     * <code>PullBufferDataSource</code>. The <code>PullBufferStream</code> may not exist at the time of
     * requesting its <code>Format</code>. Allows extenders to override the default behavior which
     * is to report any last-known format or the first <code>Format</code> from the list of supported
     * formats as defined in the JMF registration of this <code>CaptureDevice</code>.
     *
     * @param streamIndex the zero-based index of the <code>PullBufferStream</code> the <code>Format</code> of which is
     * to be retrieved
     * @param oldValue the last-known <code>Format</code> for the <code>PullBufferStream</code> at the specified
     * <code>streamIndex</code>
     * @return the <code>Format</code> to be reported by the <code>FormatControl</code> of the
     * <code>PullBufferStream</code> at the specified <code>streamIndex</code> in the list of
     * streams of this <code>PullBufferDataSource</code>.
     */
    protected Format getFormat(int streamIndex, Format oldValue)
    {
        return impl.defaultGetFormat(streamIndex, oldValue);
    }

    /**
     * Gets an array of <code>FormatControl</code> instances each one of which can be used before
     * {@link #connect()} to get and set the capture <code>Format</code> of each one of the capture streams.
     *
     * @return an array of <code>FormatControl</code> instances each one of which can be used before
     * {@link #connect()} to get and set the capture <code>Format</code> of each one of the capture streams
     */
    public FormatControl[] getFormatControls()
    {
        return impl.getFormatControls();
    }

    /**
     * Gets the <code>Object</code> which is to synchronize the access to {@link #streams()} and its return value.
     *
     * @return the <code>Object</code> which is to synchronize the access to {@link #streams()} and its return value
     */
    protected Object getStreamSyncRoot()
    {
        return impl.getStreamSyncRoot();
    }

    /**
     * Gets the <code>PullBufferStream</code>s through which this <code>PullBufferDataSource</code> gives
     * access to its media data.
     *
     * @return an array of the <code>PullBufferStream</code>s through which this
     * <code>PullBufferDataSource</code> gives access to its media data
     */
    @Override
    public PullBufferStream[] getStreams()
    {
        return impl.getStreams(PullBufferStream.class);
    }

    /**
     * Gets the <code>Format</code>s which are to be reported by a <code>FormatControl</code> as supported
     * formats for a <code>PullBufferStream</code> at a specific zero-based index in the list of
     * streams of this <code>PullBufferDataSource</code>.
     *
     * @param streamIndex the zero-based index of the <code>PullBufferStream</code> for which the specified
     * <code>FormatControl</code> is to report the list of supported <code>Format</code>s
     * @return an array of <code>Format</code>s to be reported by a <code>FormatControl</code> as the
     * supported formats for the <code>PullBufferStream</code> at the specified
     * <code>streamIndex</code> in the list of streams of this <code>PullBufferDataSource</code>
     */
    protected Format[] getSupportedFormats(int streamIndex)
    {
        return impl.defaultGetSupportedFormats(streamIndex);
    }

    /**
     * Sets a specific <code>CaptureDeviceInfo</code> on this <code>CaptureDevice</code>.
     *
     * @param deviceInfo the <code>CaptureDeviceInfo</code> on this <code>CaptureDevice</code>
     */
    public void setCaptureDeviceInfo(CaptureDeviceInfo deviceInfo)
    {
        this.deviceInfo = deviceInfo;
    }

    /**
     * Attempts to set the <code>Format</code> to be reported by the <code>FormatControl</code> of a
     * <code>PullBufferStream</code> at a specific zero-based index in the list of streams of this
     * <code>PullBufferDataSource</code>. The <code>PullBufferStream</code> does not exist at the time of
     * the attempt to set its <code>Format</code>. Allows extenders to override the default behavior
     * which is to not attempt to set the specified <code>Format</code> so that they can enable setting
     * the <code>Format</code> prior to creating the <code>PullBufferStream</code>. If setting the
     * <code>Format</code> of an existing <code>PullBufferStream</code> is desired,
     * <code>AbstractPullBufferStream#doSetFormat(Format)</code> should be overridden instead.
     *
     * @param streamIndex the zero-based index of the <code>PullBufferStream</code> the <code>Format</code> of which is
     * to be set
     * @param oldValue the last-known <code>Format</code> for the <code>PullBufferStream</code> at the specified
     * <code>streamIndex</code>
     * @param newValue the <code>Format</code> which is to be set
     * @return the <code>Format</code> to be reported by the <code>FormatControl</code> of the
     * <code>PullBufferStream</code> at the specified <code>streamIndex</code> in the list of
     * streams of this <code>PullBufferStream</code> or <code>null</code> if the attempt to set the
     * <code>Format</code> did not success and any last-known <code>Format</code> is to be left in effect
     */
    protected Format setFormat(int streamIndex, Format oldValue, Format newValue)
    {
        return oldValue;
    }

    /**
     * Starts the transfer of media data from this <code>DataSource</code>
     *
     * @throws IOException if anything goes wrong while starting the transfer of media data from this
     * <code>DataSource</code>
     */
    @Override
    public void start()
            throws IOException
    {
        impl.start();
    }

    /**
     * Stops the transfer of media data from this <code>DataSource</code>.
     *
     * @throws IOException if anything goes wrong while stopping the transfer of media data from this <code>DataSource</code>
     */
    @Override
    public void stop()
            throws IOException
    {
        impl.stop();
    }

    /**
     * Gets the internal array of <code>AbstractPushBufferStream</code>s through which this
     * <code>AbstractPushBufferCaptureDevice</code> gives access to its media data.
     *
     * @return the internal array of <code>AbstractPushBufferStream</code>s through which this
     * <code>AbstractPushBufferCaptureDevice</code> gives access to its media data
     */
    protected AbstractBufferStream<?>[] streams()
    {
        return impl.streams();
    }
}
