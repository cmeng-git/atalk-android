/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.conference;

import java.lang.ref.SoftReference;

import javax.media.Buffer;
import javax.media.protocol.SourceStream;

/**
 * Describes additional information about a specific input audio <code>SourceStream</code> of an
 * <code>AudioMixer</code> so that the <code>AudioMixer</code> can, for example, quickly discover the output
 * <code>AudioMixingPushBufferDataSource</code> in the mix of which the contribution of the
 * <code>SourceStream</code> is to not be included.
 * <p>
 * Private to <code>AudioMixer</code> and <code>AudioMixerPushBufferStream</code> but extracted into its own
 * file for the sake of clarity.
 * </p>
 *
 * @author Lyubomir Marinov
 */
class InStreamDesc
{
	/**
	 * The <code>Buffer</code> into which media data is to be read from {@link #inStream}.
	 */
	private SoftReference<Buffer> buffer;

	/**
	 * The <code>DataSource</code> which created the <code>SourceStream</code> described by this instance
	 * and additional information about it.
	 */
	public final InDataSourceDesc inDataSourceDesc;

	/**
	 * The <code>SourceStream</code> for which additional information is described by this instance.
	 */
	private SourceStream inStream;

	/**
	 * Initializes a new <code>InStreamDesc</code> instance which is to describe additional information
	 * about a specific input audio <code>SourceStream</code> of an <code>AudioMixer</code>. Associates the
	 * specified <code>SourceStream</code> with the <code>DataSource</code> which created it and additional
	 * information about it.
	 *
	 * @param inStream
	 * 		a <code>SourceStream</code> for which additional information is to be described by the new
	 * 		instance
	 * @param inDataSourceDesc
	 * 		the <code>DataSource</code> which created the <code>SourceStream</code> to be described by the
	 * 		new instance and additional information about it
	 */
	public InStreamDesc(SourceStream inStream, InDataSourceDesc inDataSourceDesc)
	{
		this.inStream = inStream;
		this.inDataSourceDesc = inDataSourceDesc;
	}

	/**
	 * Gets the <code>Buffer</code> into which media data is to be read from the <code>SourceStream</code>
	 * described by this instance.
	 *
	 * @param create
	 * 		the indicator which determines whether the <code>Buffer</code> is to be created in case it
	 * 		does not exist
	 * @return the <code>Buffer</code> into which media data is to be read from the
	 * <code>SourceStream</code> described by this instance
	 */
	public Buffer getBuffer(boolean create)
	{
		Buffer buffer = (this.buffer == null) ? null : this.buffer.get();

		if ((buffer == null) && create) {
			buffer = new Buffer();
			setBuffer(buffer);
		}
		return buffer;
	}

	/**
	 * Gets the <code>SourceStream</code> described by this instance.
	 *
	 * @return the <code>SourceStream</code> described by this instance
	 */
	public SourceStream getInStream()
	{
		return inStream;
	}

	/**
	 * Gets the <code>AudioMixingPushBufferDataSource</code> in which the mix contribution of the
	 * <code>SourceStream</code> described by this instance is to not be included.
	 *
	 * @return the <code>AudioMixingPushBufferDataSource</code> in which the mix contribution of the
	 * <code>SourceStream</code> described by this instance is to not be included
	 */
	public AudioMixingPushBufferDataSource getOutDataSource()
	{
		return inDataSourceDesc.outDataSource;
	}

	/**
	 * Sets the <code>Buffer</code> into which media data is to be read from the <code>SourceStream</code>
	 * described by this instance.
	 *
	 * @param buffer
	 * 		the <code>Buffer</code> into which media data is to be read from the <code>SourceStream</code>
	 * 		described by this instance
	 */
	public void setBuffer(Buffer buffer)
	{
		this.buffer = (buffer == null) ? null : new SoftReference<Buffer>(buffer);
	}

	/**
	 * Sets the <code>SourceStream</code> to be described by this instance.
	 *
	 * @param inStream
	 * 		the <code>SourceStream</code> to be described by this instance
	 */
	public void setInStream(SourceStream inStream)
	{
		if (this.inStream != inStream) {
			this.inStream = inStream;

			/*
			 * Since the inStream has changed, one may argue that the Buffer of the old value is
			  * not optimal for the new value.
			 */
			setBuffer(null);
		}
	}
}
