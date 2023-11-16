/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.protocol;

import java.io.IOException;

import javax.media.MediaLocator;
import javax.media.Time;
import javax.media.protocol.CaptureDevice;
import javax.media.protocol.DataSource;
import javax.media.protocol.PushBufferDataSource;
import javax.media.protocol.PushBufferStream;

/**
 * Implements most of <code>PushBufferDataSource</code> for a particular <code>DataSource</code> and
 * requires extenders to only implement {@link PushBufferDataSource#getStreams()}. Intended to allow
 * easier overriding of the streams returned by a <code>DataSource</code>.
 *
 * @param <T> the very type of <code>DataSource</code> to be wrapped in a <code>PushBufferDataSourceDelegate</code>
 *
 * @author Lyubomir Marinov
 */
public abstract class PushBufferDataSourceDelegate<T extends DataSource> extends
        CaptureDeviceDelegatePushBufferDataSource
{
    /**
     * The wrapped <code>DataSource</code> this instance delegates to.
     */
    protected final T dataSource;

    /**
     * Initializes a new <code>PushBufferDataSourceDelegate</code> which is to delegate to a specific <code>DataSource</code>.
     *
     * @param dataSource the <code>DataSource</code> the new instance is to delegate to
     */
    public PushBufferDataSourceDelegate(T dataSource)
    {
        super((dataSource instanceof CaptureDevice) ? (CaptureDevice) dataSource : null);
        if (dataSource == null)
            throw new NullPointerException("dataSource");

        this.dataSource = dataSource;
    }

    /**
     * Implements {@link DataSource#connect()}. Delegates to the wrapped <code>DataSource</code>.
     * Overrides {@link CaptureDeviceDelegatePushBufferDataSource#connect()} because the wrapped
     * <code>DataSource</code> may not be a <code>CaptureDevice</code> yet it still needs to be connected.
     *
     * @throws IOException if the wrapped <code>DataSource</code> throws such an exception
     */
    @Override
    public void connect()
            throws IOException
    {
        dataSource.connect();
    }

    /**
     * Implements {@link DataSource#disconnect()}. Delegates to the wrapped <code>DataSource</code>.
     * Overrides {@link CaptureDeviceDelegatePushBufferDataSource#disconnect()} because the wrapped
     * <code>DataSource</code> may not be a <code>CaptureDevice</code> yet it still needs to be disconnected.
     */
    @Override
    public void disconnect()
    {
        dataSource.disconnect();
    }

    /**
     * Implements {@link DataSource#getContentType()}. Delegates to the wrapped <code>DataSource</code>.
     * Overrides {@link CaptureDeviceDelegatePushBufferDataSource#getContentType()} because the
     * wrapped <code>DataSource</code> may not be a <code>CaptureDevice</code> yet it still needs to report
     * the content type.
     *
     * @return a <code>String</code> value which describes the content type of the wrapped <code>DataSource</code>
     */
    @Override
    public String getContentType()
    {
        return dataSource.getContentType();
    }

    /**
     * Implements {@link DataSource#getLocator()}. Delegates to the wrapped <code>DataSource</code>.
     *
     * @return a <code>MediaLocator</code> value which describes the locator of the wrapped <code>DataSource</code>
     */
    @Override
    public MediaLocator getLocator()
    {
        return dataSource.getLocator();
    }

    /**
     * Implements {@link DataSource#getControl(String)}. Delegates to the wrapped <code>DataSource</code>. Overrides
     * {@link CaptureDeviceDelegatePushBufferDataSource#getControl(String)} because the wrapped
     * <code>DataSource</code> may not be a <code>CaptureDevice</code> yet it still needs to give access to the control.
     *
     * @param controlType a <code>String</code> value which names the type of the control to be retrieved
     * @return an <code>Object</code> which represents the control of the requested <code>controlType</code>
     * of the wrapped <code>DataSource</code>
     */
    @Override
    public Object getControl(String controlType)
    {
        return dataSource.getControl(controlType);
    }

    /**
     * Implements {@link DataSource#getControls()}. Delegates to the wrapped <code>PushBufferDataSource</code>. Overrides
     * {@link CaptureDeviceDelegatePushBufferDataSource#getControls()} because the wrapped
     * <code>DataSource</code> may not be a <code>CaptureDevice</code> yet it still needs to give access to the controls.
     *
     * @return an array of <code>Objects</code> which represent the controls of the wrapped <code>DataSource</code>
     */
    @Override
    public Object[] getControls()
    {
        return dataSource.getControls();
    }

    /**
     * Gets the <code>DataSource</code> wrapped by this instance.
     *
     * @return the <code>DataSource</code> wrapped by this instance
     */
    public T getDataSource()
    {
        return dataSource;
    }

    /**
     * Implements {@link DataSource#getDuration()}. Delegates to the wrapped <code>DataSource</code>.
     * Overrides {@link CaptureDeviceDelegatePushBufferDataSource#getDuration()} because the
     * wrapped <code>DataSource</code> may not be a <code>CaptureDevice</code> yet it still needs to
     * report the duration.
     *
     * @return the duration of the wrapped <code>DataSource</code>
     */
    @Override
    public Time getDuration()
    {
        return dataSource.getDuration();
    }

    /**
     * Gets the <code>PushBufferStream</code>s through which this <code>PushBufferDataSource</code> gives
     * access to its media data.
     *
     * @return an array of <code>PushBufferStream</code>s through which this
     * <code>PushBufferDataSource</code> gives access to its media data
     */
    @Override
    public abstract PushBufferStream[] getStreams();

    /**
     * Implements {@link DataSource#start()}. Delegates to the wrapped <code>DataSource</code>.
     * Overrides {@link CaptureDeviceDelegatePushBufferDataSource#start()} because the wrapped
     * <code>DataSource</code> may not be a <code>CaptureDevice</code> yet it still needs to be started.
     *
     * @throws IOException if the wrapped <code>DataSource</code> throws such an exception
     */
    @Override
    public void start()
            throws IOException
    {
        dataSource.start();
    }

    /**
     * Implements {@link DataSource#stop()}. Delegates to the wrapped <code>DataSource</code>.
     * Overrides {@link CaptureDeviceDelegatePushBufferDataSource#stop()} because the wrapped
     * <code>DataSource</code> may not be a <code>CaptureDevice</code> yet it still needs to be stopped.
     *
     * @throws IOException if the wrapped <code>DataSource</code> throws such an exception
     */
    @Override
    public void stop()
            throws IOException
    {
        dataSource.stop();
    }
}
