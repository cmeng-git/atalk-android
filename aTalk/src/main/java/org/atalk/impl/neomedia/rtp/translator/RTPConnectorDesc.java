/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.rtp.translator;

import javax.media.rtp.RTPConnector;

/**
 * Describes an <code>RTPConnector</code> associated with an endpoint from and to which an
 * <code>RTPTranslatorImpl</code> is translating.
 *
 * @author Lyubomir Marinov
 */
class RTPConnectorDesc
{
	/**
	 * The <code>RTPConnector</code> associated with an endpoint from and to which an
	 * <code>RTPTranslatorImpl</code> is translating.
	 */
	public final RTPConnector connector;

	public final StreamRTPManagerDesc streamRTPManagerDesc;

	public RTPConnectorDesc(StreamRTPManagerDesc streamRTPManagerDesc, RTPConnector connector)
	{
		this.streamRTPManagerDesc = streamRTPManagerDesc;
		this.connector = connector;
	}
}
