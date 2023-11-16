/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.protocol;

import org.atalk.impl.neomedia.control.AbstractControls;
import org.atalk.service.neomedia.DTMFInbandTone;

import java.io.IOException;
import java.util.LinkedList;

import javax.media.Buffer;
import javax.media.Format;
import javax.media.protocol.PullBufferDataSource;
import javax.media.protocol.PullBufferStream;

/**
 * Implements a <code>PullBufferDataSource</code> wrapper which provides mute support for the wrapped
 * instance.
 * <p>
 * Because the class wouldn't work for our use case without it, <code>CaptureDevice</code> is
 * implemented and is being delegated to the wrapped <code>DataSource</code> (if it supports the
 * interface in question).
 * </p>
 *
 * @author Damian Minkov
 * @author Lyubomir Marinov
 */
public class RewritablePullBufferDataSource
		extends PullBufferDataSourceDelegate<PullBufferDataSource>
		implements MuteDataSource, InbandDTMFDataSource
{
	/**
	 * The indicator which determines whether this <code>DataSource</code> is mute.
	 */
	private boolean mute;

	/**
	 * The tones to send via inband DTMF, if not empty.
	 */
	private final LinkedList<DTMFInbandTone> tones = new LinkedList<>();

	/**
	 * Initializes a new <code>RewritablePullBufferDataSource</code> instance which is to provide mute
	 * support for a specific <code>PullBufferDataSource</code>.
	 *
	 * @param dataSource
	 * 		the <code>PullBufferDataSource</code> the new instance is to provide mute support for
	 */
	public RewritablePullBufferDataSource(PullBufferDataSource dataSource)
	{
		super(dataSource);
	}

	/**
	 * Sets the mute state of this <code>DataSource</code>.
	 *
	 * @param mute
	 * 		<code>true</code> to mute this <code>DataSource</code>; otherwise, <code>false</code>
	 */
	public void setMute(boolean mute)
	{
		this.mute = mute;
	}

	/**
	 * Determines whether this <code>DataSource</code> is mute.
	 *
	 * @return <code>true</code> if this <code>DataSource</code> is mute; otherwise, <code>false</code>
	 */
	public boolean isMute()
	{
		return mute;
	}

	/**
	 * Adds a new inband DTMF tone to send.
	 *
	 * @param tone
	 * 		the DTMF tone to send.
	 */
	public void addDTMF(DTMFInbandTone tone)
	{
		this.tones.add(tone);
	}

	/**
	 * Determines whether this <code>DataSource</code> sends a DTMF tone.
	 *
	 * @return <code>true</code> if this <code>DataSource</code> is sending a DTMF tone; otherwise,
	 * <code>false</code>.
	 */
	public boolean isSendingDTMF()
	{
		return !this.tones.isEmpty();
	}

	/**
	 * Get wrapped DataSource.
	 *
	 * @return wrapped DataSource
	 */
	public PullBufferDataSource getWrappedDataSource()
	{
		return dataSource;
	}

	/**
	 * {@inheritDoc}
	 * <p>
	 * Overrides the super implementation to include the type hierarchy of the very wrapped
	 * <code>dataSource</code> instance into the search for the specified <code>controlType</code>.
	 */
	@Override
	public Object getControl(String controlType)
	{
		if (InbandDTMFDataSource.class.getName().equals(controlType)
				|| MuteDataSource.class.getName().equals(controlType)) {
			return this;
		}
		else {
			/*
			 * The super implements a delegate so we can be sure that it delegates the
			 * invocation of Controls#getControl(String) to the wrapped dataSource.
			 */
			return AbstractControls.queryInterface(dataSource, controlType);
		}
	}

	/**
	 * Implements {@link PullBufferDataSource#getStreams()}. Wraps the streams of the wrapped
	 * <code>PullBufferDataSource</code> into <code>MutePullBufferStream</code> instances in order to
	 * provide mute support to them.
	 *
	 * @return an array of <code>PullBufferStream</code> instances with enabled mute support
	 */
	@Override
	public PullBufferStream[] getStreams()
	{
		PullBufferStream[] streams = dataSource.getStreams();

		if (streams != null)
			for (int streamIndex = 0; streamIndex < streams.length; streamIndex++)
				streams[streamIndex] = new MutePullBufferStream(streams[streamIndex]);
		return streams;
	}

	/**
	 * Implements a <code>PullBufferStream</code> wrapper which provides mute support for the wrapped
	 * instance.
	 */
	private class MutePullBufferStream extends SourceStreamDelegate<PullBufferStream>
			implements PullBufferStream
	{

		/**
		 * Initializes a new <code>MutePullBufferStream</code> instance which is to provide mute
		 * support for a specific <code>PullBufferStream</code>.
		 *
		 * @param stream
		 * 		the <code>PullBufferStream</code> the new instance is to provide mute support for
		 */
		private MutePullBufferStream(PullBufferStream stream)
		{
			super(stream);
		}

		/**
		 * Implements {@link PullBufferStream#getFormat()}. Delegates to the wrapped
		 * <code>PullBufferStream</code>.
		 *
		 * @return the <code>Format</code> of the wrapped <code>PullBufferStream</code>
		 */
		public Format getFormat()
		{
			return stream.getFormat();
		}

		/**
		 * Implements PullBufferStream#read(Buffer). If this instance is muted (through its owning
		 * RewritablePullBufferDataSource), overwrites the data read from the wrapped
		 * PullBufferStream with silence data.
		 *
		 * @param buffer
		 * 		which data will be filled. @throws IOException Thrown if an error occurs while
		 * 		reading.
		 */
		public void read(Buffer buffer)
				throws IOException
		{
			stream.read(buffer);

			if (isSendingDTMF())
				RewritablePushBufferDataSource.sendDTMF(buffer, tones.poll());
			else if (isMute())
				RewritablePushBufferDataSource.mute(buffer);
		}

		/**
		 * Implements PullBufferStream#willReadBlock(). Delegates to the wrapped PullSourceStream.
		 *
		 * @return <code>true</code> if read would block; otherwise returns <code>false</code>.
		 */
		public boolean willReadBlock()
		{
			return stream.willReadBlock();
		}
	}
}
