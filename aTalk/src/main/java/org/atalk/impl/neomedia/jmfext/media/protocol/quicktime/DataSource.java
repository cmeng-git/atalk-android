/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.jmfext.media.protocol.quicktime;

import org.atalk.impl.neomedia.control.FrameRateControlAdapter;
import org.atalk.impl.neomedia.device.DeviceSystem;
import org.atalk.impl.neomedia.jmfext.media.protocol.AbstractPushBufferCaptureDevice;
import org.atalk.impl.neomedia.jmfext.media.protocol.AbstractVideoPushBufferCaptureDevice;
import org.atalk.impl.neomedia.quicktime.NSErrorException;
import org.atalk.impl.neomedia.quicktime.QTCaptureDevice;
import org.atalk.impl.neomedia.quicktime.QTCaptureDeviceInput;
import org.atalk.impl.neomedia.quicktime.QTCaptureSession;

import java.awt.Dimension;
import java.io.IOException;
import java.lang.reflect.UndeclaredThrowableException;
import java.util.LinkedList;
import java.util.List;
import java.util.Vector;

import javax.media.Format;
import javax.media.MediaLocator;
import javax.media.PlugInManager;
import javax.media.control.FormatControl;
import javax.media.control.FrameRateControl;
import javax.media.format.VideoFormat;

import timber.log.Timber;

/**
 * Implements a <code>PushBufferDataSource</code> and <code>CaptureDevice</code> using QuickTime/QTKit.
 *
 * @author Lyubomir Marinov
 * @author Eng Chong Meng
 */
public class DataSource extends AbstractVideoPushBufferCaptureDevice
{
	/**
	 * The <code>QTCaptureSession</code> which captures from {@link #device} and pushes media data to
	 * the <code>PushBufferStream</code>s of this <code>PushBufferDataSource</code>.
	 */
	private QTCaptureSession captureSession;

	/**
	 * The <code>QTCaptureDevice</code> which represents the media source of this <code>DataSource</code>.
	 */
	private QTCaptureDevice device;

	/**
	 * The list of <code>Format</code>s to be reported by <code>DataSource</code> instances as supported
	 * formats.
	 */
	private static Format[] supportedFormats;

	/**
	 * Initializes a new <code>DataSource</code> instance.
	 */
	public DataSource()
	{
		this(null);
	}

	/**
	 * Initializes a new <code>DataSource</code> instance from a specific <code>MediaLocator</code>.
	 *
	 * @param locator
	 *        the <code>MediaLocator</code> to create the new instance from
	 */
	public DataSource(MediaLocator locator)
	{
		super(locator);
	}

	/**
	 * Overrides {@link AbstractVideoPushBufferCaptureDevice#createFrameRateControl()} to provide a
	 * <code>FrameRateControl</code> which gets and sets the frame rate of the
	 * <code>QTCaptureDecompressedVideoOutput</code> represented by the <code>QuickTimeStream</code> made
	 * available by this <code>DataSource</code>.
	 *
	 * {@inheritDoc}
	 * 
	 * @see AbstractVideoPushBufferCaptureDevice#createFrameRateControl()
	 */
	@Override
	protected FrameRateControl createFrameRateControl()
	{
		return new FrameRateControlAdapter()
		{
			/**
			 * The output frame rate to be managed by this <code>FrameRateControl</code> when there is
			 * no <code>QuickTimeStream</code> to delegate to.
			 */
			private float frameRate = -1;

			@Override
			public float getFrameRate()
			{
				float frameRate = -1;
				boolean frameRateFromQuickTimeStream = false;

				synchronized (getStreamSyncRoot()) {
					Object[] streams = streams();

					if ((streams != null) && (streams.length != 0)) {
						for (Object stream : streams) {
							QuickTimeStream quickTimeStream = (QuickTimeStream) stream;

							if (quickTimeStream != null) {
								frameRate = quickTimeStream.getFrameRate();
								frameRateFromQuickTimeStream = true;
								if (frameRate != -1)
									break;
							}
						}
					}
				}
				return frameRateFromQuickTimeStream ? frameRate : this.frameRate;
			}

			@Override
			public float setFrameRate(float frameRate)
			{
				float setFrameRate = -1;
				boolean frameRateFromQuickTimeStream = false;

				synchronized (getStreamSyncRoot()) {
					Object[] streams = streams();

					if ((streams != null) && (streams.length != 0)) {
						for (Object stream : streams) {
							QuickTimeStream quickTimeStream = (QuickTimeStream) stream;

							if (quickTimeStream != null) {
								float quickTimeStreamFrameRate = quickTimeStream
									.setFrameRate(frameRate);

								if (quickTimeStreamFrameRate != -1) {
									setFrameRate = quickTimeStreamFrameRate;
								}
								frameRateFromQuickTimeStream = true;
							}
						}
					}
				}
				if (frameRateFromQuickTimeStream)
					return setFrameRate;
				else {
					this.frameRate = frameRate;
					return this.frameRate;
				}
			}
		};
	}

	/**
	 * Creates a new <code>PushBufferStream</code> which is to be at a specific zero-based index in the
	 * list of streams of this <code>PushBufferDataSource</code>. The <code>Format</code>-related
	 * information of the new instance is to be abstracted by a specific <code>FormatControl</code>.
	 *
	 * @param streamIndex
	 *        the zero-based index of the <code>PushBufferStream</code> in the list of streams of this
	 *        <code>PushBufferDataSource</code>
	 * @param formatControl
	 *        the <code>FormatControl</code> which is to abstract the <code>Format</code>-related
	 *        information of the new instance
	 * @return a new <code>PushBufferStream</code> which is to be at the specified <code>streamIndex</code>
	 *         in the list of streams of this <code>PushBufferDataSource</code> and which has its
	 *         <code>Format</code>-related information abstracted by the specified
	 *         <code>formatControl</code>
	 * @see AbstractPushBufferCaptureDevice#createStream(int, FormatControl)
	 */
	@Override
	protected QuickTimeStream createStream(int streamIndex, FormatControl formatControl)
	{
		QuickTimeStream stream = new QuickTimeStream(this, formatControl);

		if (captureSession != null)
			try {
				captureSession.addOutput(stream.captureOutput);
			}
			catch (NSErrorException nseex) {
				Timber.e(nseex, "Failed to addOutput to QTCaptureSession");
				throw new UndeclaredThrowableException(nseex);
			}
		return stream;
	}

	/**
	 * Opens a connection to the media source specified by the <code>MediaLocator</code> of this
	 * <code>DataSource</code>.
	 *
	 * @throws IOException
	 *         if anything goes wrong while opening the connection to the media source specified by
	 *         the <code>MediaLocator</code> of this <code>DataSource</code>
	 * @see AbstractPushBufferCaptureDevice#doConnect()
	 */
	@Override
	protected void doConnect()
		throws IOException
	{
		super.doConnect();

		boolean deviceIsOpened;

		try {
			deviceIsOpened = device.open();
		}
		catch (NSErrorException nseex) {
			IOException ioex = new IOException();

			ioex.initCause(nseex);
			throw ioex;
		}
		if (!deviceIsOpened)
			throw new IOException("Failed to open QTCaptureDevice");

		QTCaptureDeviceInput deviceInput = QTCaptureDeviceInput.deviceInputWithDevice(device);

		captureSession = new QTCaptureSession();
		try {
			captureSession.addInput(deviceInput);
		}
		catch (NSErrorException nseex) {
			IOException ioex = new IOException();

			ioex.initCause(nseex);
			throw ioex;
		}

		/*
		 * Add the QTCaptureOutputs represented by the QuickTimeStreams (if any) to the
		 * QTCaptureSession.
		 */
		synchronized (getStreamSyncRoot()) {
			Object[] streams = streams();

			if (streams != null)
				for (Object stream : streams)
					if (stream != null)
						try {
							captureSession.addOutput(((QuickTimeStream) stream).captureOutput);
						}
						catch (NSErrorException nseex) {
							Timber.e(nseex, "Failed to addOutput to QTCaptureSession");

							IOException ioex = new IOException();

							ioex.initCause(nseex);
							throw ioex;
						}
		}
	}

	/**
	 * Closes the connection to the media source specified by the <code>MediaLocator</code> of this
	 * <code>DataSource</code>.
	 *
	 * @see AbstractPushBufferCaptureDevice#doDisconnect()
	 */
	@Override
	protected void doDisconnect()
	{
		super.doDisconnect();

		if (captureSession != null) {
			captureSession.close();
			captureSession = null;
		}
		device.close();
	}

	/**
	 * Starts the transfer of media data from this <code>DataSource</code>.
	 *
	 * @throws IOException
	 *         if anything goes wrong while starting the transfer of media data from this
	 *         <code>DataSource</code>
	 * @see AbstractPushBufferCaptureDevice#doStart()
	 */
	@Override
	protected void doStart()
		throws IOException
	{
		captureSession.startRunning();

		super.doStart();
	}

	/**
	 * Stops the transfer of media data from this <code>DataSource</code>.
	 *
	 * @throws IOException
	 *         if anything goes wrong while stopping the transfer of media data from this
	 *         <code>DataSource</code>
	 * @see AbstractPushBufferCaptureDevice#doStop()
	 */
	@Override
	protected void doStop()
		throws IOException
	{
		super.doStop();

		captureSession.stopRunning();
	}

	/**
	 * Gets the <code>Format</code>s which are to be reported by a <code>FormatControl</code> as supported
	 * formats for a <code>PushBufferStream</code> at a specific zero-based index in the list of streams
	 * of this <code>PushBufferDataSource</code>.
	 *
	 * @param streamIndex
	 *        the zero-based index of the <code>PushBufferStream</code> for which the specified
	 *        <code>FormatControl</code> is to report the list of supported <code>Format</code>s
	 * @return an array of <code>Format</code>s to be reported by a <code>FormatControl</code> as the
	 *         supported formats for the <code>PushBufferStream</code> at the specified
	 *         <code>streamIndex</code> in the list of streams of this <code>PushBufferDataSource</code>
	 * @see AbstractPushBufferCaptureDevice#getSupportedFormats(int)
	 */
	@Override
	protected Format[] getSupportedFormats(int streamIndex)
	{
		return getSupportedFormats(super.getSupportedFormats(streamIndex));
	}

	/**
	 * Gets a list of <code>Format</code>s which are more specific than given <code>Format</code>s with
	 * respect to video size. The implementation tries to come up with sane video sizes (for
	 * example, by looking for codecs which accept the encodings of the specified generic
	 * <code>Format</code>s and using their sizes if any).
	 *
	 * @param genericFormats
	 *        the <code>Format</code>s from which more specific are to be derived
	 * @return a list of <code>Format</code>s which are more specific than the given <code>Format</code>s
	 *         with respect to video size
	 */
	private static synchronized Format[] getSupportedFormats(Format[] genericFormats)
	{
		if ((supportedFormats != null) && (supportedFormats.length > 0))
			return supportedFormats.clone();

		List<Format> specificFormats = new LinkedList<Format>();

		for (Format genericFormat : genericFormats) {
			VideoFormat genericVideoFormat = (VideoFormat) genericFormat;

			if (genericVideoFormat.getSize() == null) {
				@SuppressWarnings("unchecked")
				Vector<String> codecs = PlugInManager.getPlugInList(new VideoFormat(
					genericVideoFormat.getEncoding()), null, PlugInManager.CODEC);

				for (String codec : codecs) {
					Format[] supportedInputFormats = PlugInManager.getSupportedInputFormats(codec,
						PlugInManager.CODEC);

					for (Format supportedInputFormat : supportedInputFormats)
						if (supportedInputFormat instanceof VideoFormat) {
							Dimension size = ((VideoFormat) supportedInputFormat).getSize();

							if (size != null)
								specificFormats.add(genericFormat.intersects(new VideoFormat(null,
									size, Format.NOT_SPECIFIED, null, Format.NOT_SPECIFIED)));
						}
				}
			}

			specificFormats.add(genericFormat);
		}
		supportedFormats = specificFormats.toArray(new Format[specificFormats.size()]);
		return supportedFormats.clone();
	}

	/**
	 * Sets the <code>QTCaptureDevice</code> which represents the media source of this
	 * <code>DataSource</code>.
	 *
	 * @param device
	 *        the <code>QTCaptureDevice</code> which represents the media source of this
	 *        <code>DataSource</code>
	 */
	private void setDevice(QTCaptureDevice device)
	{
		if (this.device != device)
			this.device = device;
	}

	/**
	 * Attempts to set the <code>Format</code> to be reported by the <code>FormatControl</code> of a
	 * <code>PushBufferStream</code> at a specific zero-based index in the list of streams of this
	 * <code>PushBufferDataSource</code>. The <code>PushBufferStream</code> does not exist at the time of
	 * the attempt to set its <code>Format</code>.
	 *
	 * @param streamIndex
	 *        the zero-based index of the <code>PushBufferStream</code> the <code>Format</code> of which is
	 *        to be set
	 * @param oldValue
	 *        the last-known <code>Format</code> for the <code>PushBufferStream</code> at the specified
	 *        <code>streamIndex</code>
	 * @param newValue
	 *        the <code>Format</code> which is to be set
	 * @return the <code>Format</code> to be reported by the <code>FormatControl</code> of the
	 *         <code>PushBufferStream</code> at the specified <code>streamIndex</code> in the list of
	 *         streams of this <code>PushBufferStream</code> or <code>null</code> if the attempt to set the
	 *         <code>Format</code> did not success and any last-known <code>Format</code> is to be left in
	 *         effect
	 * @see AbstractPushBufferCaptureDevice#setFormat(int, Format, Format)
	 */
	@Override
	protected Format setFormat(int streamIndex, Format oldValue, Format newValue)
	{
		if (newValue instanceof VideoFormat) {
			// This DataSource supports setFormat.
			return newValue;
		}
		else
			return super.setFormat(streamIndex, oldValue, newValue);
	}

	/**
	 * Sets the <code>MediaLocator</code> which specifies the media source of this <code>DataSource</code>.
	 *
	 * @param locator
	 *        the <code>MediaLocator</code> which specifies the media source of this <code>DataSource</code>
	 * @see javax.media.protocol.DataSource#setLocator(MediaLocator)
	 */
	@Override
	public void setLocator(MediaLocator locator)
	{
		super.setLocator(locator);

		locator = getLocator();

		QTCaptureDevice device;

		if ((locator != null)
			&& DeviceSystem.LOCATOR_PROTOCOL_QUICKTIME.equalsIgnoreCase(locator.getProtocol())) {
			String deviceUID = locator.getRemainder();

			device = QTCaptureDevice.deviceWithUniqueID(deviceUID);
		}
		else
			device = null;
		setDevice(device);
	}
}
