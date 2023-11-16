/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.protocol;

import java.awt.Component;
import java.io.IOException;
import java.util.LinkedList;
import java.util.List;

import javax.media.Buffer;
import javax.media.Control;
import javax.media.Format;
import javax.media.control.BufferControl;
import javax.media.format.AudioFormat;
import javax.media.protocol.BufferTransferHandler;
import javax.media.protocol.ContentDescriptor;
import javax.media.protocol.PushBufferStream;
import javax.media.protocol.SourceStream;

/**
 * Enables reading from a <code>PushBufferStream</code> a certain maximum number of data units (e.g.
 * bytes, shorts, ints) even if the <code>PushBufferStream</code> itself pushes a larger number of data
 * units.
 * <p>
 * An example use of this functionality is pacing a <code>PushBufferStream</code> which pushes more data
 * units in a single step than a <code>CaptureDevice</code>. When these two undergo audio mixing, the
 * different numbers of per-push data units will cause the <code>PushBufferStream</code> "play" itself
 * faster than the <code>CaptureDevice</code>.
 * </p>
 *
 * @author Lyubomir Marinov
 */
public class CachingPushBufferStream implements PushBufferStream
{

	/**
	 * The default length in milliseconds of the buffering to be performed by
	 * <code>CachePushBufferStream</code>s.
	 */
	public static final long DEFAULT_BUFFER_LENGTH = 20;

	/**
	 * The maximum number of <code>Buffer</code>s to be cached in a <code>CachingPushBufferStream</code>.
	 * Generally, defined to a relatively large value which allows large buffering and yet tries to
	 * prevent <code>OutOfMemoryError</code>.
	 */
	private static final int MAX_CACHE_SIZE = 1024;

	/**
	 * The <code>BufferControl</code> of this <code>PushBufferStream</code> which allows the adjustment of
	 * the size of the buffering it performs.
	 */
	private BufferControl bufferControl;

	/**
	 * The <code>Object</code> which synchronizes the access to {@link #bufferControl}.
	 */
	private final Object bufferControlSyncRoot = new Object();

	/**
	 * The list of <code>Buffer</code>s in which this instance stores the data it reads from the
	 * wrapped <code>PushBufferStream</code> and from which it reads in chunks later on when its
	 * {@link #read(Buffer)} method is called.
	 */
	private final List<Buffer> cache = new LinkedList<Buffer>();

	/**
	 * The length of the media in milliseconds currently available in {@link #cache}.
	 */
	private long cacheLengthInMillis = 0;

	/**
	 * The last <code>IOException</code> this stream has received from the <code>#read(Buffer)</code>
	 * method of the wrapped stream and to be thrown by this stream on the earliest call of its
	 * <code>#read(Buffer)</code> method.
	 */
	private IOException readException;

	/**
	 * The <code>PushBufferStream</code> being paced by this instance with respect to the maximum
	 * number of data units it provides in a single push.
	 */
	private final PushBufferStream stream;

	/**
	 * The <code>BufferTransferHandler</code> set on {@link #stream}.
	 */
	private BufferTransferHandler transferHandler;

	/**
	 * Initializes a new <code>CachingPushBufferStream</code> instance which is to pace the number of
	 * per-push data units a specific <code>PushBufferStream</code> provides.
	 *
	 * @param stream
	 * 		the <code>PushBufferStream</code> to be paced with respect to the number of per-push data
	 * 		units it provides
	 */
	public CachingPushBufferStream(PushBufferStream stream)
	{
		this.stream = stream;
	}

	/**
	 * Determines whether adding a new <code>Buffer</code> to {@link #cache} is acceptable given the
	 * maximum size of the <code>cache</code> and the length of the media currently available in it.
	 *
	 * @return <code>true</code> if adding a new <code>Buffer</code> to <code>cache</code> is acceptable;
	 * otherwise, <code>false</code> which means that the reading from the wrapped
	 * <code>PushBufferStream</code> should be blocked until <code>true</code> is returned
	 */
	private boolean canWriteInCache()
	{
		synchronized (cache) {
			int cacheSize = cache.size();

			/*
			 * Obviously, if there's nothing in the cache, we desperately want something to be
			 * written into it.
			 */
			if (cacheSize < 1)
				return true;
			/*
			 * For the sake of not running out of memory, don't let the sky be the limit.
			 */
			if (cacheSize >= MAX_CACHE_SIZE)
				return false;

			long bufferLength = getBufferLength();

			/*
			 * There is no bufferLength specified by a BufferControl so don't buffer anything.
			 */
			if (bufferLength < 1)
				return false;
			/*
			 * Having Buffers in the cache and yet not having their length in milliseconds is weird
			 * so don't buffer anything.
			 */
			if (cacheLengthInMillis < 1)
				return false;
			/*
			 * Of course, if the media in the cache hasn't reached the specified buffer length,
			 * write more to the cache.
			 */
			return (cacheLengthInMillis < bufferLength);
		}
	}

	/**
	 * Implements {@link SourceStream#endOfStream()}. Delegates to the wrapped
	 * <code>PushBufferStream</code> when the cache of this instance is fully read; otherwise, returns
	 * <code>false</code>.
	 *
	 * @return <code>true</code> if this <code>PushBufferStream</code> has reached the end of the
	 * content it makes available; otherwise, <code>false</code>
	 */
	public boolean endOfStream()
	{
		/*
		 * TODO If the cache is still not exhausted, don't report the end of this stream even if
		  * the
		 * wrapped stream has reached its end.
		 */
		return stream.endOfStream();
	}

	/**
	 * Gets the <code>BufferControl</code> of this <code>PushBufferStream</code> which allows the
	 * adjustment of the size of the buffering it performs. If it does not exist yet, it is
	 * created.
	 *
	 * @return the <code>BufferControl</code> of this <code>PushBufferStream</code> which allows the
	 * adjustment of the size of the buffering it performs
	 */
	private BufferControl getBufferControl()
	{
		synchronized (bufferControlSyncRoot) {
			if (bufferControl == null)
				bufferControl = new BufferControlImpl();
			return bufferControl;
		}
	}

	/**
	 * Gets the length in milliseconds of the buffering performed by this
	 * <code>PushBufferStream</code>.
	 *
	 * @return the length in milliseconds of the buffering performed by this
	 * <code>PushBufferStream</code> if such a value has been set; otherwise,
	 * {@link BufferControl#DEFAULT_VALUE}
	 */
	private long getBufferLength()
	{
		synchronized (bufferControlSyncRoot) {
			return (bufferControl == null)
					? BufferControl.DEFAULT_VALUE : bufferControl.getBufferLength();
		}
	}

	/**
	 * Implements {@link SourceStream#getContentDescriptor()}. Delegates to the wrapped
	 * <code>PushBufferStream</code>.
	 *
	 * @return a <code>ContentDescriptor</code> which describes the type of the content made available
	 * by the wrapped <code>PushBufferStream</code>
	 */
	public ContentDescriptor getContentDescriptor()
	{
		return stream.getContentDescriptor();
	}

	/**
	 * Implements {@link SourceStream#getContentLength()}. Delegates to the wrapped
	 * <code>PushBufferStream</code>.
	 *
	 * @return the length of the content made available by the wrapped <code>PushBufferStream</code>
	 */
	public long getContentLength()
	{
		return stream.getContentLength();
	}

	/**
	 * Implements {@link javax.media.Controls#getControl(String)}. Delegates to the wrapped
	 * <code>PushBufferStream</code> and gives access to the <code>BufferControl</code> of this instance if
	 * such a <code>controlType</code> is specified and the wrapped <code>PushBufferStream</code> does not
	 * have such a control available.
	 *
	 * @param controlType
	 * 		a <code>String</code> value which names the type of the control of the wrapped
	 * 		<code>PushBufferStream</code> to be retrieved
	 * @return an <code>Object</code> which represents the control of the wrapped
	 * <code>PushBufferStream</code> with the specified type if such a control is available;
	 * otherwise, <code>null</code>
	 */
	public Object getControl(String controlType)
	{
		Object control = stream.getControl(controlType);

		if ((control == null) && BufferControl.class.getName().equals(controlType)) {
			control = getBufferControl();
		}
		return control;
	}

	/**
	 * Implements {@link javax.media.Controls#getControls()}. Delegates to the wrapped
	 * <code>PushBufferStream</code> and adds the <code>BufferControl</code> of this instance if the
	 * wrapped <code>PushBufferStream</code> does not have a control of such type available.
	 *
	 * @return an array of <code>Object</code>s which represent the control available for the wrapped
	 * <code>PushBufferStream</code>
	 */
	public Object[] getControls()
	{
		Object[] controls = stream.getControls();

		if (controls == null) {
			BufferControl bufferControl = getBufferControl();
			if (bufferControl != null)
				controls = new Object[]{bufferControl};
		}
		else {
			boolean bufferControlExists = false;

			for (Object control : controls) {
				if (control instanceof BufferControl) {
					bufferControlExists = true;
					break;
				}
			}
			if (!bufferControlExists) {
				BufferControl bufferControl = getBufferControl();

				if (bufferControl != null) {
					Object[] newControls = new Object[controls.length + 1];

					newControls[0] = bufferControl;
					System.arraycopy(controls, 0, newControls, 1, controls.length);
				}
			}
		}
		return controls;
	}

	/**
	 * Implements {@link PushBufferStream#getFormat()}. Delegates to the wrapped
	 * <code>PushBufferStream</code>.
	 *
	 * @return the <code>Format</code> of the media data available for reading in this
	 * <code>PushBufferStream</code>
	 */
	public Format getFormat()
	{
		return stream.getFormat();
	}

	/**
	 * Gets the length in milliseconds of the media in a specific <code>Buffer</code> (often
	 * referred to as duration).
	 *
	 * @param buffer
	 * 		the <code>Buffer</code> which contains media the length in milliseconds of which is to be
	 * 		calculated
	 * @return the length in milliseconds of the media in <code>buffer</code> if there actually is
	 * media in <code>buffer</code> and its length in milliseconds can be calculated; otherwise,
	 * <code>0</code>
	 */
	private long getLengthInMillis(Buffer buffer)
	{
		int length = buffer.getLength();

		if (length < 1)
			return 0;

		Format format = buffer.getFormat();

		if (format == null) {
			format = getFormat();
			if (format == null)
				return 0;
		}
		if (!(format instanceof AudioFormat))
			return 0;

		AudioFormat audioFormat = (AudioFormat) format;
		long duration = audioFormat.computeDuration(length);

		return (duration < 1) ? 0 : (duration / 1000000);
	}

	/**
	 * Gets the <code>PushBufferStream</code> wrapped by this instance.
	 *
	 * @return the <code>PushBufferStream</code> wrapped by this instance
	 */
	public PushBufferStream getStream()
	{
		return stream;
	}

	/**
	 * Implements {@link PushBufferStream#read(Buffer)}. If an <code>IOException</code> has been thrown
	 * by the wrapped stream when data was last read from it, re-throws it. If there has been no
	 * such exception, reads from the cache of this instance.
	 *
	 * @param buffer
	 * 		the <code>Buffer</code> to receive the read media data
	 * @throws IOException
	 * 		if the wrapped stream has thrown such an exception when data was last read from it
	 */
	public void read(Buffer buffer)
			throws IOException
	{
		synchronized (cache) {
			if (readException != null) {
				IOException ioe = new IOException();

				ioe.initCause(readException);
				readException = null;
				throw ioe;
			}

			buffer.setLength(0);
			if (!cache.isEmpty()) {
				int bufferOffset = buffer.getOffset();

				while (!cache.isEmpty()) {
					Buffer cacheBuffer = cache.get(0);
					int nextBufferOffset = read(cacheBuffer, buffer, bufferOffset);

					if ((cacheBuffer.getLength() <= 0) || (cacheBuffer.getData() == null))
						cache.remove(0);
					if (nextBufferOffset < 0)
						break;
					else
						bufferOffset = nextBufferOffset;
				}

				cacheLengthInMillis -= getLengthInMillis(buffer);
				if (cacheLengthInMillis < 0)
					cacheLengthInMillis = 0;

				if (canWriteInCache())
					cache.notify();
			}
		}
	}

	/**
	 * Reads data from a specific input <code>Buffer</code> (if such data is available) and writes the
	 * read data into a specific output <code>Buffer</code>. The input <code>Buffer</code> will be modified
	 * to reflect the number of read data units. If the output <code>Buffer</code> has allocated an
	 * array for storing the read data and the type of this array matches that of the input
	 * <code>Buffer</code>, it will be used and thus the output <code>Buffer</code> may control the maximum
	 * number of data units to be read into it.
	 *
	 * @param in
	 * 		the <code>Buffer</code> to read data from
	 * @param out
	 * 		the <code>Buffer</code> into which to write the data read from the specified <code>in</code>
	 * @param outOffset
	 * 		the offset in <code>out</code> at which the data read from <code>in</code> is to be written
	 * @return the offset in <code>out</code> at which a next round of writing is to continue;
	 * <code>-1</code> if no more writing in <code>out</code> is to be performed and <code>out</code> is
	 * to be returned to the caller
	 * @throws IOException
	 * 		if reading from <code>in</code> into <code>out</code> fails including if either of the
	 * 		formats of <code>in</code> and <code>out</code> are not supported
	 */
	private int read(Buffer in, Buffer out, int outOffset)
			throws IOException
	{
		Object outData = out.getData();

		if (outData != null) {
			Object inData = in.getData();

			if (inData == null) {
				out.setFormat(in.getFormat());
				// There was nothing to read so continue reading and concatenating.
				return outOffset;
			}

			Class<?> dataType = outData.getClass();

			if (inData.getClass().equals(dataType) && dataType.equals(byte[].class)) {
				int inOffset = in.getOffset();
				int inLength = in.getLength();
				byte[] outBytes = (byte[]) outData;
				int outLength = outBytes.length - outOffset;

				// Where is it supposed to be written?
				if (outLength < 1)
					return -1;

				if (inLength < outLength)
					outLength = inLength;
				System.arraycopy(inData, inOffset, outBytes, outOffset, outLength);

				out.setData(outBytes);
				out.setLength(out.getLength() + outLength);

				/*
				 * If we're currently continuing a concatenation, the parameters of the first read
				 * from input are left as the parameters of output. Mostly done at least for
				 * timeStamp.
				 */
				if (out.getOffset() == outOffset) {
					out.setFormat(in.getFormat());

					out.setDiscard(in.isDiscard());
					out.setEOM(in.isEOM());
					out.setFlags(in.getFlags());
					out.setHeader(in.getHeader());
					out.setSequenceNumber(in.getSequenceNumber());
					out.setTimeStamp(in.getTimeStamp());
					out.setRtpTimeStamp(in.getRtpTimeStamp());
					out.setHeaderExtension(in.getHeaderExtension());

					/*
					 * It's possible that we've split the input into multiple outputs so the output
					 * duration may be different than the input duration. An alternative to
					 * Buffer.TIME_UNKNOWN is possibly the calculation of the output duration as
					  * the input duration multiplied by the ratio between the current output
					  * length and the initial input length.
					 */
					out.setDuration(Buffer.TIME_UNKNOWN);
				}

				in.setLength(inLength - outLength);
				in.setOffset(inOffset + outLength);
				// Continue reading and concatenating.
				return (outOffset + outLength);
			}
		}

		/*
		 * If we were supposed to continue a concatenation and we discovered that it could not be
		 * continued, flush whatever has already been written to the caller.
		 */
		if (out.getOffset() == outOffset) {
			out.copy(in);

			int outputLength = out.getLength();

			in.setLength(in.getLength() - outputLength);
			in.setOffset(in.getOffset() + outputLength);
		}
		/*
		 * We didn't know how to concatenate the media so return it to the caller.
		 */
		return -1;
	}

	/**
	 * Implements {@link PushBufferStream#setTransferHandler(BufferTransferHandler)}. Delegates to
	 * the wrapped <code>PushBufferStream<code> but wraps the specified
	 * BufferTransferHandler in order to intercept the calls to
	 * {@link BufferTransferHandler#transferData(PushBufferStream)} and read
	 * data from the wrapped <code>PushBufferStream</code> into the cache during the calls in question.
	 *
	 * @param transferHandler
	 * 		the <code>BufferTransferHandler</code> to be notified by this <code>PushBufferStream</code>
	 * 		when media data is available for reading
	 */
	public void setTransferHandler(BufferTransferHandler transferHandler)
	{
		BufferTransferHandler substituteTransferHandler = (transferHandler == null) ? null
				: new StreamSubstituteBufferTransferHandler(transferHandler, stream, this)
		{
			@Override
			public void transferData(PushBufferStream stream)
			{
				if (CachingPushBufferStream.this.stream == stream)
					CachingPushBufferStream.this.transferData(this);

				super.transferData(stream);
			}
		};

		synchronized (cache) {
			stream.setTransferHandler(substituteTransferHandler);
			this.transferHandler = substituteTransferHandler;
			cache.notifyAll();
		}
	}

	/**
	 * Reads data from the wrapped/input <code>PushBufferStream</code> into the cache of this stream if
	 * the cache accepts it. If the cache does not accept a new read, blocks the calling thread
	 * until the cache accepts a new read and data is read from the wrapped
	 * <code>PushBufferStream</code> into the cache.
	 *
	 * @param transferHandler
	 * 		the <code>BufferTransferHandler</code> which has been notified
	 */
	protected void transferData(BufferTransferHandler transferHandler)
	{
		/*
		 * Obviously, we cannot cache every Buffer because we will run out of memory. So wait for
		 * room to appear within cache (or for this instance to be stopped, of course).
		 */
		boolean interrupted = false;
		boolean canWriteInCache = false;

		synchronized (cache) {
			while (true) {
				if (this.transferHandler != transferHandler) {
					/*
					 * The specified transferHandler has already been obsoleted/replaced so it does
					 * not have the right to cause a read or a write.
					 */
					canWriteInCache = false;
					break;
				}
				else if (canWriteInCache()) {
					canWriteInCache = true;
					break;
				}
				else {
					try {
						cache.wait(DEFAULT_BUFFER_LENGTH / 2);
					}
					catch (InterruptedException iex) {
						interrupted = true;
					}
				}
			}
		}
		if (interrupted) {
			Thread.currentThread().interrupt();
		}
		else if (canWriteInCache) {
			/*
			 * The protocol of PushBufferStream's #read(Buffer) method is that it does not block.
			 * The underlying implementation may be flawed though so we would better not take any
			 * chances. Besides, we have a report at the time of this writing which suggests
			 * that we may really be hitting a rogue implementation in a real-world scenario.
			 */
			Buffer buffer = new Buffer();
			IOException readException;

			try {
				stream.read(buffer);
				readException = null;
			}
			catch (IOException ioe) {
				readException = ioe;
			}
			if (readException == null) {
				if (!buffer.isDiscard() && (buffer.getLength() != 0)
						&& (buffer.getData() != null)) {
					/*
					 * Well, we risk disagreeing with #canWriteInCache() because we have
					 * temporarily released the cache but we have read a Buffer from the stream
					 * so it is probably better to not throw it away.
					 */
					synchronized (cache) {
						cache.add(buffer);
						cacheLengthInMillis += getLengthInMillis(buffer);
					}
				}
			}
			else {
				synchronized (cache) {
					this.readException = readException;
				}
			}
		}
	}

	/**
	 * Implements a <code>BufferControl</code> which enables the adjustment of the length of the
	 * buffering performed by a <code>CachingPushBufferStream</code>.
	 */
	private static class BufferControlImpl implements BufferControl
	{

		/**
		 * The length of the buffering to be performed by the owner of this instance.
		 */
		private long bufferLength = DEFAULT_VALUE;

		/**
		 * The indicator which determines whether threshold calculations are enabled.
		 *
		 * @see BufferControl#setEnabledThreshold(boolean)
		 */
		private boolean enabledThreshold;

		/**
		 * The minimum threshold in milliseconds for the buffering performed by the owner of this
		 * instance.
		 *
		 * @see BufferControl#getMinimumThreshold()
		 */
		private long minimumThreshold = DEFAULT_VALUE;

		/**
		 * Implements {@link BufferControl#getBufferLength()}. Gets the length in milliseconds of
		 * the buffering performed by the owner of this instance.
		 *
		 * @return the length in milliseconds of the buffering performed by the owner of this
		 * instance; {@link BufferControl#DEFAULT_VALUE} if it is up to the owner of this
		 * instance to decide the length in milliseconds of the buffering to perform if any
		 */
		public long getBufferLength()
		{
			return bufferLength;
		}

		/**
		 * Implements {@link Control#getControlComponent()}. Gets the UI <code>Component</code>
		 * representing this instance and exported by the owner of this instance. Returns
		 * <code>null</code>.
		 *
		 * @return the UI <code>Component</code> representing this instance and exported by the
		 * owner of this instance if such a <code>Component</code> is available; otherwise,
		 * <code>null</code>
		 */
		public Component getControlComponent()
		{
			return null;
		}

		/**
		 * Implements {@link BufferControl#getEnabledThreshold()}. Gets the indicator which
		 * determines whether threshold calculations are enabled.
		 *
		 * @return <code>true</code> if threshold calculations are enabled; otherwise, <code>false</code>
		 */
		public boolean getEnabledThreshold()
		{
			return enabledThreshold;
		}

		/**
		 * Implements {@link BufferControl#getMinimumThreshold()}. Gets the minimum threshold in
		 * milliseconds for the buffering performed by the owner of this instance.
		 *
		 * @return the minimum threshold in milliseconds for the buffering performed by the
		 * owner of this instance
		 */
		public long getMinimumThreshold()
		{
			return minimumThreshold;
		}

		/**
		 * Implements {@link BufferControl#setBufferLength(long)}. Sets the length in milliseconds
		 * of the buffering to be performed by the owner of this instance and returns the value
		 * actually in effect after attempting to set it to the specified value.
		 *
		 * @param bufferLength
		 * 		the length in milliseconds of the buffering to be performed by the owner of this
		 * 		instance
		 * @return the length in milliseconds of the buffering performed by the owner of this
		 * instance that is actually in effect after the attempt to set it to the specified
		 * <code>bufferLength</code>
		 */
		public long setBufferLength(long bufferLength)
		{
			if ((bufferLength == DEFAULT_VALUE) || (bufferLength > 0))
				this.bufferLength = bufferLength;
			// Returns the current value as specified by the javadoc.
			return getBufferLength();
		}

		/**
		 * Implements {@link BufferControl#setEnabledThreshold(boolean)}. Sets the indicator which
		 * determines whether threshold calculations are enabled.
		 *
		 * @param enabledThreshold
		 * 		<code>true</code> if threshold calculations are to be enabled; otherwise,
		 * 		<code>false</code>
		 */
		public void setEnabledThreshold(boolean enabledThreshold)
		{
			this.enabledThreshold = enabledThreshold;
		}

		/**
		 * Implements {@link BufferControl#setMinimumThreshold(long)}. Sets the minimum
		 * threshold in milliseconds for the buffering to be performed by the owner of this
		 * instance and returns the value actually in effect after attempting to set it to the
		 * specified value.
		 *
		 * @param minimumThreshold
		 * 		the minimum threshold in milliseconds for the buffering to be performed by the
		 * 		owner of this instance
		 * @return the minimum threshold in milliseconds for the buffering performed by the
		 * owner of this instance that is actually in effect after the attempt to set it to the
		 * specified <code>minimumThreshold</code>
		 */
		public long setMinimumThreshold(long minimumThreshold)
		{
			/*
			 * The minimumThreshold property is not supported in any way at the time of this
			 * writing so returns the current value as specified by the javadoc.
			 */
			return getMinimumThreshold();
		}
	}
}
