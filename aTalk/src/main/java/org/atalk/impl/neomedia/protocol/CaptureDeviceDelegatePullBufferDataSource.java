/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.protocol;

import org.atalk.impl.neomedia.control.ControlsAdapter;

import java.io.IOException;

import javax.media.CaptureDeviceInfo;
import javax.media.Time;
import javax.media.control.FormatControl;
import javax.media.protocol.CaptureDevice;
import javax.media.protocol.ContentDescriptor;
import javax.media.protocol.DataSource;
import javax.media.protocol.PullBufferDataSource;
import javax.media.protocol.PullBufferStream;

/**
 * Represents a <code>PullBufferDataSource</code> which is also a <code>CaptureDevice</code> through
 * delegation to a specific <code>CaptureDevice</code> .
 *
 * @author Damian Minkov
 * @author Lubomir Marinov
 */
public class CaptureDeviceDelegatePullBufferDataSource extends PullBufferDataSource
		implements CaptureDevice
{
	/**
	 * The <code>CaptureDevice</code> this instance delegates to in order to implement its
	 * <code>CaptureDevice</code> functionality.
	 */
	protected final CaptureDevice captureDevice;

	/**
	 * The constant which represents an empty array with <code>PullBufferStream</code> element type.
	 * Explicitly defined in order to reduce unnecessary allocations.
	 */
	protected static final PullBufferStream[] EMPTY_STREAMS = new PullBufferStream[0];

	/**
	 * Initializes a new <code>CaptureDeviceDelegatePullBufferDataSource</code> instance which
	 * delegates to a specific <code>CaptureDevice</code> in order to implement its
	 * <code>CaptureDevice</code> functionality.
	 *
	 * @param captureDevice
	 * 		the <code>CaptureDevice</code> the new instance is to delegate to in order to provide its
	 * 		<code>CaptureDevice</code> functionality
	 */
	public CaptureDeviceDelegatePullBufferDataSource(CaptureDevice captureDevice)
	{
		this.captureDevice = captureDevice;
	}

	/**
	 * Implements {@link PullBufferDataSource#getStreams()}. Delegates to the wrapped
	 * <code>CaptureDevice</code> if it implements <code>PullBufferDataSource</code>; otherwise, returns an
	 * empty array with <code>PullBufferStream</code> element type.
	 *
	 * @return an array of <code>PullBufferStream</code>s as returned by the wrapped
	 * <code>CaptureDevice</code> if it implements <code>PullBufferDataSource</code>; otherwise, an
	 * empty array with <code>PullBufferStream</code> element type
	 */
	@Override
	public PullBufferStream[] getStreams()
	{
		if (captureDevice instanceof PullBufferDataSource)
			return ((PullBufferDataSource) captureDevice).getStreams();
		return EMPTY_STREAMS;
	}

	/**
	 * Implements {@link DataSource#getContentType()}. Delegates to the wrapped
	 * <code>CaptureDevice</code> if it implements <code>DataSource</code>; otherwise, returns
	 * {@link ContentDescriptor#CONTENT_UNKNOWN}.
	 *
	 * @return a <code>String</code> value which describes the content type of the wrapped
	 * <code>CaptureDevice</code> if it implements <code>DataSource</code>; otherwise,
	 * <code>ContentDescriptor#CONTENT_UNKNOWN</code>
	 */
	@Override
	public String getContentType()
	{
		if (captureDevice instanceof DataSource)
			return ((DataSource) captureDevice).getContentType();
		return ContentDescriptor.CONTENT_UNKNOWN;
	}

	/**
	 * Implements {@link CaptureDevice#connect()}. Delegates to the wrapped <code>CaptureDevice</code>
	 * if available; otherwise, does nothing.
	 *
	 * @throws IOException
	 * 		if the wrapped <code>CaptureDevice</code> throws such an exception
	 */
	@Override
	public void connect()
			throws IOException
	{
		if (captureDevice != null)
			captureDevice.connect();
	}

	/**
	 * Implements {@link CaptureDevice#disconnect()}. Delegates to the wrapped
	 * <code>CaptureDevice</code> if available; otherwise, does nothing.
	 */
	@Override
	public void disconnect()
	{
		if (captureDevice != null)
			captureDevice.disconnect();
	}

	/**
	 * Implements {@link CaptureDevice#start()}. Delegates to the wrapped <code>CaptureDevice</code> if
	 * available; otherwise, does nothing.
	 *
	 * @throws IOException
	 * 		if the wrapped <code>CaptureDevice</code> throws such an exception
	 */
	@Override
	public void start()
			throws IOException
	{
		if (captureDevice != null)
			captureDevice.start();
	}

	/**
	 * Implements {@link CaptureDevice#start()}. Delegates to the wrapped <code>CaptureDevice</code> if
	 * available; otherwise, does nothing.
	 *
	 * @throws IOException
	 * 		if the wrapped <code>CaptureDevice</code> throws such an exception
	 */
	@Override
	public void stop()
			throws IOException
	{
		if (captureDevice != null)
			captureDevice.stop();
	}

	/**
	 * Implements {@link DataSource#getControl(String)}. Delegates to the wrapped
	 * <code>CaptureDevice</code> if it implements <code>DataSource</code>; otherwise, returns
	 * <code>null</code>
	 * .
	 *
	 * @param controlType
	 * 		a <code>String</code> value which names the type of the control to be retrieved
	 * @return an <code>Object</code> which represents the control of the requested
	 * <code>controlType</code> of the wrapped <code>CaptureDevice</code> if it implements
	 * <code>DataSource</code>; otherwise, <code>null</code>
	 */
	@Override
	public Object getControl(String controlType)
	{
		if (captureDevice instanceof DataSource)
			return ((DataSource) captureDevice).getControl(controlType);
		return null;
	}

	/**
	 * Implements {@link DataSource#getControls()}. Delegates to the wrapped <code>CaptureDevice</code>
	 * if it implements <code>DataSource</code>; otherwise, returns an empty array with <code>Object</code>
	 * element type.
	 *
	 * @return the array of controls for the wrapped <code>CaptureDevice</code> if it implements
	 * <code>DataSource</code>; otherwise, an empty array with <code>Object</code> element type
	 */
	@Override
	public Object[] getControls()
	{
		if (captureDevice instanceof DataSource)
			return ((DataSource) captureDevice).getControls();
		return ControlsAdapter.EMPTY_CONTROLS;
	}

	/**
	 * Implements {@link DataSource#getDuration()}. Delegates to the wrapped <code>CaptureDevice</code>
	 * if it implements <code>DataSource</code>; otherwise, returns
	 * {@link DataSource#DURATION_UNKNOWN}.
	 *
	 * @return the duration of the wrapped <code>CaptureDevice</code> as returned by its implementation
	 * of <code>DataSource</code> if any; otherwise, returns
	 * <code>DataSource#DURATION_UNKNOWN</code>
	 */
	@Override
	public Time getDuration()
	{
		if (captureDevice instanceof DataSource)
			return ((DataSource) captureDevice).getDuration();
		return DataSource.DURATION_UNKNOWN;
	}

	/**
	 * Implements {@link CaptureDevice#getFormatControls()}. Delegates to the wrapped
	 * <code>CaptureDevice</code> if available; otherwise, returns an empty array with
	 * <code>FormatControl</code> element type.
	 *
	 * @return the array of <code>FormatControl</code>s of the wrapped <code>CaptureDevice</code> if
	 * available; otherwise, an empty array with <code>FormatControl</code> element type
	 */
	public FormatControl[] getFormatControls()
	{
		return (captureDevice != null)
				? captureDevice.getFormatControls() : new FormatControl[0];
	}

	/**
	 * Implements {@link CaptureDevice#getCaptureDeviceInfo()}. Delegates to the wrapped
	 * <code>CaptureDevice</code> if available; otherwise, returns <code>null</code>.
	 *
	 * @return the <code>CaptureDeviceInfo</code> of the wrapped <code>CaptureDevice</code> if available;
	 * otherwise, <code>null</code>
	 */
	public CaptureDeviceInfo getCaptureDeviceInfo()
	{
		return (captureDevice != null)
				? captureDevice.getCaptureDeviceInfo() : null;
	}
}
