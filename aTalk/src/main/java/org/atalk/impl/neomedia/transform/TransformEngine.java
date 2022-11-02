/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.transform;

/**
 * Defines how to get <code>PacketTransformer</code>s for RTP and RTCP packets. A single
 * <code>PacketTransformer</code> can be used for both RTP and RTCP packets or there can be two separate
 * <code>PacketTransformer</code>s.
 *
 * @author Bing SU (nova.su@gmail.com)
 */
public interface TransformEngine
{
	/**
	 * Gets the <code>PacketTransformer</code> for RTP packets.
	 *
	 * @return the <code>PacketTransformer</code> for RTP packets
	 */
	PacketTransformer getRTPTransformer();

	/**
	 * Gets the <code>PacketTransformer</code> for RTCP packets.
	 *
	 * @return the <code>PacketTransformer</code> for RTCP packets
	 */
	PacketTransformer getRTCPTransformer();
}
