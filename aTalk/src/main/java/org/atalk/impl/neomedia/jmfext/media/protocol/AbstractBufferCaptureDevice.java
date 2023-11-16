/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.jmfext.media.protocol;

import net.sf.fmj.media.util.RTPInfo;

import org.atalk.impl.neomedia.control.AbstractControls;
import org.atalk.impl.neomedia.control.AbstractFormatControl;
import org.atalk.impl.neomedia.control.ControlsAdapter;

import java.io.IOException;
import java.lang.reflect.Array;
import java.lang.reflect.UndeclaredThrowableException;
import java.util.Vector;
import java.util.concurrent.locks.ReentrantLock;

import javax.media.CaptureDeviceInfo;
import javax.media.CaptureDeviceManager;
import javax.media.Controls;
import javax.media.Format;
import javax.media.MediaLocator;
import javax.media.control.FormatControl;
import javax.media.control.FrameRateControl;
import javax.media.protocol.CaptureDevice;
import javax.media.protocol.DataSource;
import javax.media.protocol.SourceStream;

import timber.log.Timber;

/**
 * Facilitates the implementations of the <code>CaptureDevice</code> and <code>DataSource</code> interfaces
 * provided by <code>AbstractPullBufferCaptureDevice</code> and <code>AbstractPushBufferCaptureDevice</code>
 *
 * @param <AbstractBufferStreamT> the type of <code>AbstractBufferStream</code> through which this
 * <code>AbstractBufferCaptureDevice</code> is to give access to its media data
 *
 * @author Lyubomir Marinov
 * @author Eng Chong Meng
 */
public abstract class AbstractBufferCaptureDevice<AbstractBufferStreamT extends AbstractBufferStream<?>>
        implements CaptureDevice, Controls
{
    /**
     * The value of the <code>formatControls</code> property of <code>AbstractBufferCaptureDevice</code>
     * which represents an empty array of <code>FormatControl</code>s. Explicitly defined in order to
     * reduce unnecessary allocations.
     */
    private static final FormatControl[] EMPTY_FORMAT_CONTROLS = new FormatControl[0];

    /**
     * The indicator which determines whether a connection to the media source specified by the
     * <code>MediaLocator</code> of this <code>DataSource</code> has been opened.
     */
    private boolean connected = false;

    /**
     * The <code>Object</code> to synchronize the access to the state related to the <code>Controls</code>
     * interface implementation in order to avoid locking <code>this</code> if not necessary.
     */
    private final Object controlsSyncRoot = new Object();

    /**
     * The array of <code>FormatControl</code> instances each one of which can be used before
     * {@link #connect()} to get and set the capture <code>Format</code> of each one of the capture streams.
     */
    private FormatControl[] formatControls;

    /**
     * The <code>FrameRateControl</code>s of this <code>AbstractBufferCaptureDevice</code>.
     */
    private FrameRateControl[] frameRateControls;

    private final ReentrantLock lock = new ReentrantLock();

    /**
     * The <code>RTPInfo</code>s of this <code>AbstractBufferCaptureDevice</code>.
     */
    private RTPInfo[] rtpInfos;

    /**
     * The indicator which determines whether the transfer of media data from this <code>DataSource</code> has been started.
     */
    private boolean started = false;

    /**
     * The <code>PushBufferStream</code>s through which this <code>PushBufferDataSource</code> gives access to its media data.
     * <p>
     * Warning: Caution is advised when directly using the field and access to it is to be
     * synchronized with synchronization root <code>this</code>.
     * </p>
     */
    private AbstractBufferStream<?>[] streams;

    private final Object streamSyncRoot = new Object();

    /**
     * Opens a connection to the media source of this <code>AbstractBufferCaptureDevice</code>.
     *
     * @throws IOException if anything goes wrong while opening the connection to the media source of this
     * <code>AbstractBufferCaptureDevice</code>
     */
    public void connect()
            throws IOException
    {
        lock();
        try {
            if (!connected) {
                doConnect();
                connected = true;
            }
        } finally {
            unlock();
        }
    }

    /**
     * Creates a new <code>FormatControl</code> instance which is to be associated with a <code>PushBufferStream</code>
     * at a specific zero-based index in the list of streams of this <code>PushBufferDataSource</code>.
     * As the <code>FormatControl</code>s of a <code>PushBufferDataSource</code> can be requested before {@link #connect()},
     * its <code>PushBufferStream</code>s may not exist at the time of the request for the creation of the
     * <code>FormatControl</code>.
     *
     * @param streamIndex the zero-based index of the <code>PushBufferStream</code> in the list of streams of this
     * <code>PushBufferDataSource</code> which is to be associated with the new <code>FormatControl</code> instance
     * @return a new <code>FormatControl</code> instance which is to be associated with a
     * <code>PushBufferStream</code> at the specified <code>streamIndex</code> in the list of
     * streams of this <code>PushBufferDataSource</code>
     */
    protected FormatControl createFormatControl(final int streamIndex)
    {
        return new AbstractFormatControl()
        {
            /**
             * Gets the <code>Format</code> of the media data of the owner of this <code>FormatControl</code>.
             *
             * @return the <code>Format</code> of the media data of the owner of this <code>FormatControl</code>
             */
            public Format getFormat()
            {
                mFormat = AbstractBufferCaptureDevice.this.internalGetFormat(streamIndex, mFormat);
                return mFormat;
            }

            /**
             * Gets the <code>Format</code>s in which the owner of this <code>FormatControl</code> is
             * capable of providing media data.
             *
             * @return an array of <code>Format</code>s in which the owner of this <code>FormatControl</code>
             * is capable of providing media data
             */
            public Format[] getSupportedFormats()
            {
                // Timber.d("FormatControl getSupportedFormats for streamIndex: %s; size = %s", streamIndex,
                //        AbstractBufferCaptureDevice.this.getSupportedFormats(streamIndex).length);
                return AbstractBufferCaptureDevice.this.getSupportedFormats(streamIndex);
            }

            /**
             * Implements {@link FormatControl#setFormat(Format)}. Attempts to set the
             * <code>Format</code> in which the owner of this <code>FormatControl</code> is to provide media data.
             *
             * @param format the <code>Format</code> to be set on this instance
             * @return the currently set <code>Format</code> after the attempt to set it on this
             *         instance if <code>format</code> is supported by this instance and regardless of
             *         whether it was actually set; <code>null</code> if <code>format</code> is not supported by this instance
             */
            @Override
            public Format setFormat(Format format)
            {
                Format oldFormat = super.getFormat();
                Format setFormat = super.setFormat(format);
                if (setFormat != null) {
                    setFormat = AbstractBufferCaptureDevice.this.internalSetFormat(streamIndex, oldFormat, format);
                }
                return setFormat;
            }
        };
    }

    /**
     * Creates the <code>FormatControl</code>s of this <code>CaptureDevice</code>.
     *
     * @return an array of the <code>FormatControl</code>s of this <code>CaptureDevice</code>
     */
    protected FormatControl[] createFormatControls()
    {
        FormatControl formatControl = createFormatControl(0);
        return (formatControl == null) ? EMPTY_FORMAT_CONTROLS : new FormatControl[]{formatControl};
    }

    /**
     * Creates a new <code>FrameRateControl</code> instance which is to allow the getting and
     * setting of the frame rate of this <code>AbstractBufferCaptureDevice</code>.
     *
     * @return a new <code>FrameRateControl</code> instance which is to allow the getting and
     * setting of the frame rate of this <code>AbstractBufferCaptureDevice</code>
     */
    protected FrameRateControl createFrameRateControl()
    {
        return null;
    }

    /**
     * Creates a new <code>RTPInfo</code> instance of this <code>AbstractBufferCaptureDevice</code>.
     *
     * @return a new <code>RTPInfo</code> instance of this <code>AbstractBufferCaptureDevice</code>
     */
    protected RTPInfo createRTPInfo()
    {
        return new RTPInfo()
        {
            public String getCNAME()
            {
                // TODO Auto-generated method stub
                return null;
            }
        };
    }

    /**
     * Create a new <code>AbstractBufferStream</code> which is to be at a specific zero-based index in
     * the list of streams of this <code>AbstractBufferCaptureDevice</code>. The <code>Format</code>-related
     * information of the new instance is to be abstracted by a specific <code>FormatControl</code>.
     *
     * @param streamIndex the zero-based index of the <code>AbstractBufferStream</code> in the list of streams of
     * this <code>AbstractBufferCaptureDevice</code>
     * @param formatControl the <code>FormatControl</code> which is to abstract the <code>Format</code>-related
     * information of the new instance
     * @return a new <code>AbstractBufferStream</code> which is to be at the specified
     * <code>streamIndex</code> in the list of streams of this
     * <code>AbstractBufferCaptureDevice</code> and which has its <code>Format</code>-related
     * information abstracted by the specified <code>formatControl</code>
     */
    protected abstract AbstractBufferStreamT createStream(int streamIndex, FormatControl formatControl);

    /**
     * Provides the default implementation of <code>AbstractBufferCaptureDevice</code> for {@link #doStart()}.
     *
     * @throws IOException if anything goes wrong while starting the transfer of media data from this
     * <code>AbstractBufferCaptureDevice</code>
     * @see #doStart()
     */
    final void defaultDoStart()
            throws IOException
    {
        synchronized (getStreamSyncRoot()) {
            if (streams != null) {
                for (AbstractBufferStream<?> stream : streams)
                    stream.start();
            }
        }
    }

    /**
     * Provides the default implementation of <code>AbstractBufferCaptureDevice</code> for {@link #doStop()}.
     *
     * @throws IOException if anything goes wrong while stopping the transfer of media data from this
     * <code>AbstractBufferCaptureDevice</code>
     * @see #doStop()
     */
    final void defaultDoStop()
            throws IOException
    {
        synchronized (getStreamSyncRoot()) {
            if (streams != null) {
                for (AbstractBufferStream<?> stream : streams)
                    stream.stop();
            }
        }
    }

    /**
     * Provides the default implementation of <code>AbstractBufferCaptureDevice</code> for {@link #getControls()}.
     *
     * @return an array of <code>Object</code>s which represent the controls available for this instance
     */
    final Object[] defaultGetControls()
    {
        FormatControl[] formatControls = internalGetFormatControls();
        int formatControlCount = (formatControls == null) ? 0 : formatControls.length;
        FrameRateControl[] frameRateControls = internalGetFrameRateControls();
        int frameRateControlCount = (frameRateControls == null) ? 0 : frameRateControls.length;
        RTPInfo[] rtpInfos = internalGetRTPInfos();
        int rtpInfoCount = (rtpInfos == null) ? 0 : rtpInfos.length;

        if ((formatControlCount == 0) && (frameRateControlCount == 0) && (rtpInfoCount == 0))
            return ControlsAdapter.EMPTY_CONTROLS;
        else {
            Object[] controls = new Object[formatControlCount + frameRateControlCount
                    + rtpInfoCount];
            int offset = 0;

            if (formatControlCount != 0) {
                System.arraycopy(formatControls, 0, controls, offset, formatControlCount);
                offset += formatControlCount;
            }
            if (frameRateControlCount != 0) {
                System.arraycopy(frameRateControls, 0, controls, offset, frameRateControlCount);
                offset += frameRateControlCount;
            }
            if (rtpInfoCount != 0) {
                System.arraycopy(rtpInfos, 0, controls, offset, rtpInfoCount);
                offset += rtpInfoCount;
            }
            return controls;
        }
    }

    /**
     * Provides the default implementation of <code>AbstractBufferCaptureDevice</code> for {@link #getFormat(int, Format)}.
     *
     * @param streamIndex the zero-based index of the <code>AbstractBufferStream</code> the <code>Format</code> of which
     * is to be retrieved
     * @param oldValue the last-known <code>Format</code> for the <code>AbstractBufferStream</code> at the specified
     * <code>streamIndex</code>
     * @return the <code>Format</code> to be reported by the <code>FormatControl</code> of the
     * <code>AbstractBufferStream</code> at the specified <code>streamIndex</code> in the list of
     * streams of this <code>AbstractBufferCaptureDevice</code>
     * @see #getFormat(int, Format)
     */
    final Format defaultGetFormat(int streamIndex, Format oldValue)
    {
        if (oldValue != null)
            return oldValue;

        Format[] supportedFormats = getSupportedFormats(streamIndex);

        return ((supportedFormats == null) || (supportedFormats.length < 1))
                ? null : supportedFormats[0];
    }

    /**
     * Provides the default implementation of <code>AbstractBufferCaptureDevice</code> for
     * {@link #getSupportedFormats(int)}.
     *
     * @param streamIndex the zero-based index of the <code>AbstractBufferStream</code> for which the specified
     * <code>FormatControl</code> is to report the list of supported <code>Format</code>s
     * @return an array of <code>Format</code>s to be reported by a <code>FormatControl</code> as the
     * supported formats for the <code>AbstractBufferStream</code> at the specified
     * <code>streamIndex</code> in the list of streams of this <code>AbstractBufferCaptureDevice</code>
     */
    final Format[] defaultGetSupportedFormats(int streamIndex)
    {
        CaptureDeviceInfo captureDeviceInfo = getCaptureDeviceInfo();
        return (captureDeviceInfo == null) ? new Format[0] : captureDeviceInfo.getFormats();
    }

    /**
     * Closes the connection to the media source specified of this
     * <code>AbstractBufferCaptureDevice</code>. If such a connection has not been opened, the call is ignored.
     */
    public void disconnect()
    {
        lock();
        try {
            try {
                stop();
            } catch (IOException ioex) {
                Timber.e(ioex, "Failed to stop %s", getClass().getSimpleName());
            }

            if (connected) {
                doDisconnect();
                connected = false;
            }
        } finally {
            unlock();
        }
    }

    /**
     * Opens a connection to the media source of this <code>AbstractBufferCaptureDevice</code>. Allows
     * extenders to override and be sure that there will be no request to open a connection if the
     * connection has already been opened.
     *
     * @throws IOException if anything goes wrong while opening the connection to the media source of this
     * <code>AbstractBufferCaptureDevice</code>
     */
    protected abstract void doConnect()
            throws IOException;

    /**
     * Closes the connection to the media source of this <code>AbstractBufferCaptureDevice</code>.
     * Allows extenders to override and be sure that there will be no request to close a connection
     * if the connection has not been opened yet.
     */
    protected abstract void doDisconnect();

    /**
     * Starts the transfer of media data from this <code>AbstractBufferCaptureDevice</code>. Allows
     * extenders to override and be sure that there will be no request to start the transfer of
     * media data if it has already been started.
     *
     * @throws IOException if anything goes wrong while starting the transfer of media data from this
     * <code>AbstractBufferCaptureDevice</code>
     */
    protected abstract void doStart()
            throws IOException;

    /**
     * Stops the transfer of media data from this <code>AbstractBufferCaptureDevice</code>. Allows
     * extenders to override and be sure that there will be no request to stop the transfer of
     * media data if it has not been started yet.
     *
     * @throws IOException if anything goes wrong while stopping the transfer of media data from this
     * <code>AbstractBufferCaptureDevice</code>
     */
    protected abstract void doStop()
            throws IOException;

    /**
     * Gets the <code>CaptureDeviceInfo</code> of this <code>CaptureDevice</code> which describes it.
     *
     * @return the <code>CaptureDeviceInfo</code> of this <code>CaptureDevice</code> which describes it
     */
    public abstract CaptureDeviceInfo getCaptureDeviceInfo();

    /**
     * Gets the <code>CaptureDeviceInfo</code> of a specific <code>CaptureDevice</code> by locating its
     * registration in JMF using its <code>MediaLocator</code>.
     *
     * @param captureDevice the <code>CaptureDevice</code> to gets the <code>CaptureDeviceInfo</code> of
     * @return the <code>CaptureDeviceInfo</code> of the specified <code>CaptureDevice</code> as registered in JMF
     */
    public static CaptureDeviceInfo getCaptureDeviceInfo(DataSource captureDevice)
    {
        /*
         * TODO The implemented search for the CaptureDeviceInfo of this CaptureDevice by looking
         * for its MediaLocator is inefficient.
         */
        @SuppressWarnings("unchecked")
        Vector<CaptureDeviceInfo> captureDeviceInfos = CaptureDeviceManager.getDeviceList(null);
        MediaLocator locator = captureDevice.getLocator();

        for (CaptureDeviceInfo captureDeviceInfo : captureDeviceInfos)
            if (captureDeviceInfo.getLocator().toString().equals(locator.toString()))
                return captureDeviceInfo;
        return null;
    }

    /**
     * Gets the control of the specified type available for this instance.
     *
     * @param controlType the type of the control available for this instance to be retrieved
     * @return an <code>Object</code> which represents the control of the specified type available for
     * this instance if such a control is indeed available; otherwise, <code>null</code>
     */
    public Object getControl(String controlType)
    {
        return AbstractControls.getControl(this, controlType);
    }

    /**
     * Implements {@link javax.media.Controls#getControls()}. Gets the controls available for this instance.
     *
     * @return an array of <code>Object</code>s which represent the controls available for this instance
     */
    public Object[] getControls()
    {
        return defaultGetControls();
    }

    /**
     * Gets the <code>Format</code> to be reported by the <code>FormatControl</code> of an
     * <code>AbstractBufferStream</code> at a specific zero-based index in the list of streams of this
     * <code>AbstractBufferCaptureDevice</code>. The <code>AbstractBufferStream</code> may not exist at the
     * time of requesting its <code>Format</code>. Allows extenders to override the default behavior
     * which is to report any last-known format or the first <code>Format</code> from the list of
     * supported formats as defined in the JMF registration of this <code>CaptureDevice</code>.
     *
     * @param streamIndex the zero-based index of the <code>AbstractBufferStream</code> the <code>Format</code> of which
     * is to be retrieved
     * @param oldValue the last-known <code>Format</code> for the <code>PushBufferStream</code> at the specified
     * <code>streamIndex</code>
     * @return the <code>Format</code> to be reported by the <code>FormatControl</code> of the
     * <code>PushBufferStream</code> at the specified <code>streamIndex</code> in the list of
     * streams of this <code>PushBufferDataSource</code>.
     */
    protected abstract Format getFormat(int streamIndex, Format oldValue);

    /**
     * Gets an array of <code>FormatControl</code> instances each one of which can be used before
     * {@link #connect()} to get and set the capture <code>Format</code> of each one of the capture streams.
     *
     * @return an array of <code>FormatControl</code> instances each one of which can be used before
     * {@link #connect()} to get and set the capture <code>Format</code> of each one of the capture streams
     */
    public FormatControl[] getFormatControls()
    {
        return AbstractFormatControl.getFormatControls(this);
    }

    /**
     * Gets the <code>Object</code> which is to synchronize the access to {@link #streams()} and its return value.
     *
     * @return the <code>Object</code> which is to synchronize the access to {@link #streams()} and its return value
     */
    Object getStreamSyncRoot()
    {
        return streamSyncRoot;
    }

    /**
     * Gets the <code>AbstractBufferStream</code>s through which this
     * <code>AbstractBufferCaptureDevice</code> gives access to its media data.
     *
     * @param <SourceStreamT> the type of <code>SourceStream</code> which is to be the element type of the returned array
     * @param clz the <code>Class</code> of <code>SourceStream</code> which is to be the element type of the returned array
     * @return an array of the <code>SourceStream</code>s through which this
     * <code>AbstractBufferCaptureDevice</code> gives access to its media data
     */
    public <SourceStreamT extends SourceStream>
    SourceStreamT[] getStreams(Class<SourceStreamT> clz)
    {
        synchronized (getStreamSyncRoot()) {
            return internalGetStreams(clz);
        }
    }

    /**
     * Gets the <code>Format</code>s which are to be reported by a <code>FormatControl</code> as supported
     * formats for a <code>AbstractBufferStream</code> at a specific zero-based index in the list of
     * streams of this <code>AbstractBufferCaptureDevice</code>.
     *
     * @param streamIndex the zero-based index of the <code>AbstractBufferStream</code> for which the specified
     * <code>FormatControl</code> is to report the list of supported <code>Format</code>s
     * @return an array of <code>Format</code>s to be reported by a <code>FormatControl</code> as the
     * supported formats for the <code>AbstractBufferStream</code> at the specified
     * <code>streamIndex</code> in the list of streams of this <code>AbstractBufferCaptureDevice</code>
     */
    protected abstract Format[] getSupportedFormats(int streamIndex);

    /**
     * Gets the <code>Format</code> to be reported by the <code>FormatControl</code> of a
     * <code>PushBufferStream</code> at a specific zero-based index in the list of streams of this
     * <code>PushBufferDataSource</code>. The <code>PushBufferStream</code> may not exist at the time of
     * requesting its <code>Format</code>.
     *
     * @param streamIndex the zero-based index of the <code>PushBufferStream</code> the <code>Format</code> of which is
     * to be retrieved
     * @param oldValue the last-known <code>Format</code> for the <code>PushBufferStream</code> at the specified
     * <code>streamIndex</code>
     * @return the <code>Format</code> to be reported by the <code>FormatControl</code> of the
     * <code>PushBufferStream</code> at the specified <code>streamIndex</code> in the list of
     * streams of this <code>PushBufferDataSource</code>.
     */
    private Format internalGetFormat(int streamIndex, Format oldValue)
    {
        if (lock.tryLock()) {
            try {
                synchronized (getStreamSyncRoot()) {
                    if (streams != null) {
                        AbstractBufferStream<?> stream = streams[streamIndex];
                        if (stream != null) {
                            Format streamFormat = stream.internalGetFormat();
                            if (streamFormat != null)
                                return streamFormat;
                        }
                    }
                }
            } finally {
                lock.unlock();
            }
        }
        else {
            /*
             * XXX In order to prevent a deadlock, do not ask the streams about the format.
             */
        }
        return getFormat(streamIndex, oldValue);
    }

    /**
     * Gets an array of <code>FormatControl</code> instances each one of which can be used before
     * {@link #connect()} to get and set the capture <code>Format</code> of each one of the capture streams.
     *
     * @return an array of <code>FormatControl</code> instances each one of which can be used before
     * {@link #connect()} to get and set the capture <code>Format</code> of each one of the capture streams
     */
    private FormatControl[] internalGetFormatControls()
    {
        synchronized (controlsSyncRoot) {
            if (formatControls == null) {
                formatControls = createFormatControls();
            }
            return formatControls;
        }
    }

    /**
     * Gets an array of <code>FrameRateControl</code> instances which can be used to get and/or set the
     * output frame rate of this <code>AbstractBufferCaptureDevice</code>.
     *
     * @return an array of <code>FrameRateControl</code> instances which can be used to get and/or set
     * the output frame rate of this <code>AbstractBufferCaptureDevice</code>.
     */
    private FrameRateControl[] internalGetFrameRateControls()
    {
        synchronized (controlsSyncRoot) {
            if (frameRateControls == null) {
                FrameRateControl frameRateControl = createFrameRateControl();

                // Don't try to create the FrameRateControl more than once.
                frameRateControls = (frameRateControl == null)
                        ? new FrameRateControl[0]
                        : new FrameRateControl[]{frameRateControl};
            }
            return frameRateControls;
        }
    }

    /**
     * Gets an array of <code>RTPInfo</code> instances of this <code>AbstractBufferCaptureDevice</code>.
     *
     * @return an array of <code>RTPInfo</code> instances of this <code>AbstractBufferCaptureDevice</code>.
     */
    private RTPInfo[] internalGetRTPInfos()
    {
        synchronized (controlsSyncRoot) {
            if (rtpInfos == null) {
                RTPInfo rtpInfo = createRTPInfo();

                // Don't try to create the RTPInfo more than once.
                rtpInfos = (rtpInfo == null) ? new RTPInfo[0] : new RTPInfo[]{rtpInfo};
            }
            return rtpInfos;
        }
    }

    /**
     * Gets the <code>AbstractBufferStream</code>s through which this
     * <code>AbstractBufferCaptureDevice</code> gives access to its media data.
     *
     * @param <SourceStreamT> the type of <code>SourceStream</code> which is to be the element type of the returned array
     * @param clz the <code>Class</code> of <code>SourceStream</code> which is to be the element type of the returned array
     * @return an array of the <code>SourceStream</code>s through which this
     * <code>AbstractBufferCaptureDevice</code> gives access to its media data
     */
    private <SourceStreamT extends SourceStream>
    SourceStreamT[] internalGetStreams(Class<SourceStreamT> clz)
    {
        if (streams == null) {
            FormatControl[] formatControls = internalGetFormatControls();

            if (formatControls != null) {
                int formatControlCount = formatControls.length;
                streams = new AbstractBufferStream[formatControlCount];
                for (int i = 0; i < formatControlCount; i++) {
                    streams[i] = createStream(i, formatControls[i]);
                    // Timber.d("Index: %s; Stream: %s; Control: %s", i, streams[i], formatControls[i]);
                }

                /*
                 * Start the streams if this DataSource has already been started.
                 */
                if (started) {
                    for (AbstractBufferStream<?> stream : streams) {
                        try {
                            stream.start();
                        } catch (IOException ioex) {
                            throw new UndeclaredThrowableException(ioex);
                        }
                    }
                }
            }
        }

        int streamCount = (streams == null) ? 0 : streams.length;
        @SuppressWarnings("unchecked")
        SourceStreamT[] clone = (SourceStreamT[]) Array.newInstance(clz, streamCount);

        if (streamCount != 0)
            System.arraycopy(streams, 0, clone, 0, streamCount);
        return clone;
    }

    /**
     * Attempts to set the <code>Format</code> to be reported by the <code>FormatControl</code> of a <code>PushBufferStream</code>
     * at a specific zero-based index in the list of streams of this <code>PushBufferDataSource</code>.
     *
     * @param streamIndex the zero-based index of the <code>PushBufferStream</code> the <code>Format</code> of which is to be set
     * @param oldValue the last-known <code>Format</code> for the <code>PushBufferStream</code> at the specified <code>streamIndex</code>
     * @param newValue the <code>Format</code> which is to be set
     * @return the <code>Format</code> to be reported by the <code>FormatControl</code> of the
     * <code>PushBufferStream</code> at the specified <code>streamIndex</code> in the list of
     * streams of this <code>PushBufferStream</code> or <code>null</code> if the attempt to set the
     * <code>Format</code> did not success and any last-known <code>Format</code> is to be left in effect
     */
    private Format internalSetFormat(int streamIndex, Format oldValue, Format newValue)
    {
        lock();
        try {
            synchronized (getStreamSyncRoot()) {
                if (streams != null) {
                    AbstractBufferStream<?> stream = streams[streamIndex];
                    if (stream != null)
                        return stream.internalSetFormat(newValue);
                }
            }
        } finally {
            unlock();
        }
        return setFormat(streamIndex, oldValue, newValue);
    }

    private void lock()
    {
        lock.lock();
    }

    /**
     * Attempts to set the <code>Format</code> to be reported by the <code>FormatControl</code> of a
     * <code>AbstractBufferStream</code> at a specific zero-based index in the list of streams of this
     * <code>AbstractBufferCaptureDevice</code>. The <code>AbstractBufferStream</code> does not exist at
     * the time of the attempt to set its <code>Format</code>. Allows extenders to override the default
     * behavior which is to not attempt to set the specified <code>Format</code> so that they can
     * enable setting the <code>Format</code> prior to creating the <code>AbstractBufferStream</code>. If
     * setting the <code>Format</code> of an existing <code>AbstractBufferStream</code> is desired,
     * <code>AbstractBufferStream#doSetFormat(Format)</code> should be overridden instead.
     *
     * @param streamIndex the zero-based index of the <code>AbstractBufferStream</code> the <code>Format</code> of which
     * is to be set
     * @param oldValue the last-known <code>Format</code> for the <code>AbstractBufferStream</code> at the specified
     * <code>streamIndex</code>
     * @param newValue the <code>Format</code> which is to be set
     * @return the <code>Format</code> to be reported by the <code>FormatControl</code> of the
     * <code>AbstractBufferStream</code> at the specified <code>streamIndex</code> in the list of
     * streams of this <code>AbstractBufferStream</code> or <code>null</code> if the attempt to set
     * the <code>Format</code> did not success and any last-known <code>Format</code> is to be left in effect
     */
    protected abstract Format setFormat(int streamIndex, Format oldValue, Format newValue);

    /**
     * Starts the transfer of media data from this <code>AbstractBufferCaptureDevice</code>.
     *
     * @throws IOException if anything goes wrong while starting the transfer of media data from this
     * <code>AbstractBufferCaptureDevice</code>
     */
    public void start()
            throws IOException
    {
        lock();
        try {
            if (!started) {
                if (!connected) {
                    throw new IOException(getClass().getName() + " not connected");
                }

                doStart();
                started = true;
            }
        } finally {
            unlock();
        }
    }

    /**
     * Stops the transfer of media data from this <code>AbstractBufferCaptureDevice</code>.
     *
     * @throws IOException if anything goes wrong while stopping the transfer of media data from this
     * <code>AbstractBufferCaptureDevice</code>
     */
    public void stop()
            throws IOException
    {
        lock();
        try {
            if (started) {
                doStop();
                started = false;
            }
        } finally {
            unlock();
        }
    }

    /**
     * Gets the internal array of <code>AbstractBufferStream</code>s through which this
     * <code>AbstractBufferCaptureDevice</code> gives access to its media data.
     *
     * @return the internal array of <code>AbstractBufferStream</code>s through which this
     * <code>AbstractBufferCaptureDevice</code> gives access to its media data
     */
    AbstractBufferStream<?>[] streams()
    {
        return streams;
    }

    private void unlock()
    {
        lock.unlock();
    }
}
