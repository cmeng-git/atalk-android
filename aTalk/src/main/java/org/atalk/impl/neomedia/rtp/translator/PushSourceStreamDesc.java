/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.rtp.translator;

import org.atalk.impl.neomedia.jmfext.media.protocol.AbstractPushBufferStream;

import javax.media.protocol.PushBufferStream;
import javax.media.protocol.PushSourceStream;

/**
 * Describes a <code>PushSourceStream</code> associated with an endpoint from which an
 * <code>RTPTranslatorImpl</code> is translating.
 *
 * @author Lyubomir Marinov
 */
class PushSourceStreamDesc
{
	/**
	 * The endpoint <code>RTPConnector</code> which owns {@link #stream}.
	 */
	public final RTPConnectorDesc connectorDesc;

	/**
	 * <code>true</code> if this instance represents a data/RTP stream or <code>false</code> if this
	 * instance represents a control/RTCP stream
	 */
	public final boolean data;

	/**
	 * The <code>PushSourceStream</code> associated with an endpoint from which an
	 * <code>RTPTranslatorImpl</code> is translating.
	 */
	public final PushSourceStream stream;

	/**
	 * The <code>PushBufferStream</code> control over {@link #stream}, if available, which may provide
	 * Buffer properties other than <code>data</code>, <code>length</code> and <code>offset</code> such as
	 * <code>flags</code>.
	 */
	public final PushBufferStream streamAsPushBufferStream;

	/**
	 * Initializes a new <code>PushSourceStreamDesc</code> instance which is to describe a specific
	 * endpoint <code>PushSourceStream</code> for an <code>RTPTranslatorImpl</code>.
	 *
	 * @param connectorDesc
	 *        the endpoint <code>RTPConnector</code> which owns the specified <code>stream</code>
	 * @param stream
	 *        the endpoint <code>PushSourceStream</code> to be described by the new instance for an
	 *        <code>RTPTranslatorImpl</code>
	 * @param data
	 *        <code>true</code> if the specified <code>stream</code> is a data/RTP stream or <code>false</code>
	 *        if the specified <code>stream</code> is a control/RTCP stream
	 */
	public PushSourceStreamDesc(RTPConnectorDesc connectorDesc, PushSourceStream stream,
		boolean data)
	{
		this.connectorDesc = connectorDesc;
		this.stream = stream;
		this.data = data;

		streamAsPushBufferStream = (PushBufferStream)
				stream.getControl(AbstractPushBufferStream.PUSH_BUFFER_STREAM_CLASS_NAME);
	}
}
