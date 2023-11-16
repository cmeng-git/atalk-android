/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.conference;

import java.lang.ref.SoftReference;

import javax.media.Buffer;
import javax.media.format.AudioFormat;

/**
 * Describes a specific set of audio samples read from a specific set of input streams specified by
 * their <code>InStreamDesc</code>s.
 * <p>
 * Private to <code>AudioMixerPushBufferStream</code> but extracted into its own file for the sake of
 * clarity.
 * </p>
 *
 * @author Lyubomir Marinov
 */
class InSampleDesc
{
	/**
	 * The <code>Buffer</code> into which media data is to be read from {@link #inStreams}.
	 */
	private SoftReference<Buffer> buffer;

	/**
	 * The <code>AudioFormat</code> of {@link #inSamples}.
	 */
	public final AudioFormat format;

	/**
	 * The set of audio samples read from {@link #inStreams}.
	 */
	public final short[][] inSamples;

	/**
	 * The set of input streams from which {@link #inSamples} were read.
	 */
	public final InStreamDesc[] inStreams;

	/**
	 * The time stamp of <code>inSamples</code> to be reported in the <code>Buffer</code>s of the
	 * <code>AudioMixingPushBufferStream</code>s when mixes are read from them.
	 */
	private long timeStamp = Buffer.TIME_UNKNOWN;

	/**
	 * Initializes a new <code>InSampleDesc</code> instance which is to describe a specific set of audio
	 * samples read from a specific set of input streams specified by their <code>InStreamDesc</code>s.
	 *
	 * @param inSamples
	 * 		the set of audio samples read from <code>inStreams</code>
	 * @param inStreams
	 * 		the set of input streams from which <code>inSamples</code> were read
	 * @param format
	 * 		the <code>AudioFormat</code> of <code>inSamples</code>
	 */
	public InSampleDesc(short[][] inSamples, InStreamDesc[] inStreams, AudioFormat format)
	{
		this.inSamples = inSamples;
		this.inStreams = inStreams;
		this.format = format;
	}

	/**
	 * Gets the <code>Buffer</code> into which media data is to be read from the input streams
	 * associated with this instance.
	 *
	 * @return the <code>Buffer</code> into which media data is to be read from the input streams
	 * associated with this instance
	 */
	public Buffer getBuffer()
	{
		Buffer buffer = (this.buffer == null) ? null : this.buffer.get();

		if (buffer == null) {
			buffer = new Buffer();
			setBuffer(buffer);
		}
		return buffer;
	}

	/**
	 * Gets the time stamp of <code>inSamples</code> to be reported in the <code>Buffer</code>s of the
	 * <code>AudioMixingPushBufferStream</code>s when mixes are read from them.
	 *
	 * @return the time stamp of <code>inSamples</code> to be reported in the <code>Buffer</code>s of the
	 * <code>AudioMixingPushBufferStream</code>s when mixes are read from them
	 */
	public long getTimeStamp()
	{
		return timeStamp;
	}

	/**
	 * Sets the <code>Buffer</code> into which media data is to be read from the input streams
	 * associated with this instance.
	 *
	 * @param buffer
	 * 		the <code>Buffer</code> into which media data is to be read from the input streams
	 * 		associated with this instance
	 */
	private void setBuffer(Buffer buffer)
	{
		this.buffer = (buffer == null) ? null : new SoftReference<>(buffer);
	}

	/**
	 * Sets the time stamp of <code>inSamples</code> to be reported in the <code>Buffer</code>s of the
	 * <code>AudioMixingPushBufferStream</code>s when mixes are read from them.
	 *
	 * @param timeStamp
	 * 		the time stamp of <code>inSamples</code> to be reported in the <code>Buffer</code>s of the
	 * 		<code>AudioMixingPushBufferStream</code>s when mixes are read from them
	 */
	public void setTimeStamp(long timeStamp)
	{
		if (this.timeStamp == Buffer.TIME_UNKNOWN) {
			this.timeStamp = timeStamp;
		}
		else {
			/*
			 * Setting the timeStamp more than once does not make sense because the inStreams will
			 * report different timeStamps so only one should be picked up where the very reading
			 * from inStreams takes place.
			 */
			throw new IllegalStateException("timeStamp");
		}
	}
}
