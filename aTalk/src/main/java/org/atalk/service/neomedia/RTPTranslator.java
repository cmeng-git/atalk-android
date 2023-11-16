/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.service.neomedia;

import net.sf.fmj.media.rtp.SSRCCache;

import org.atalk.impl.neomedia.rtp.StreamRTPManager;

import java.util.List;

/**
 * Represents an RTP translator which forwards RTP and RTCP traffic between multiple
 * <code>MediaStream</code>s.
 *
 * @author Lyubomir Marinov
 */
public interface RTPTranslator
{
	/**
	 * Finds the {@code StreamRTPManager} which receives a specific SSRC.
	 *
	 * @param receiveSSRC
	 *        the SSRC of the RTP stream received by the {@code StreamRTPManager} to be returned
	 * @return the {@code StreamRTPManager} which receives {@code receiveSSRC} of {@code null}
	 */
	StreamRTPManager findStreamRTPManagerByReceiveSSRC(int receiveSSRC);

	/**
	 * Returns a list of <code>StreamRTPManager</code>s currently attached to this
	 * <code>RTPTranslator</code>. This is admittedly wrong, to expose the bare <code>SSRCCache</code> to
	 * the use of of the <code>StreamRTPManager</code>. We should find a better way of exposing this
	 * information. Currently it is necessary for RTCP termination.
	 *
	 * @return a list of <code>StreamRTPManager</code>s currently attached to this
	 *         <code>RTPTranslator</code>.
	 */
	List<StreamRTPManager> getStreamRTPManagers();

	/**
	 * Provides access to the underlying <code>SSRCCache</code> that holds statistics information about
	 * each SSRC that we receive.
	 *
	 * @return the underlying <code>SSRCCache</code> that holds statistics information about each SSRC
	 *         that we receive.
	 */
	SSRCCache getSSRCCache();

	/**
	 * Defines a packet filter which allows an observer of an <code>RTPTranslator</code> to disallow the
	 * writing of specific packets into a specific destination identified by a <code>MediaStream</code>.
	 */
	interface WriteFilter
	{
		boolean accept(MediaStream source, RawPacket pkt,
			MediaStream destination, boolean data);
	}

	/**
	 * Adds a <code>WriteFilter</code> to this <code>RTPTranslator</code>.
	 *
	 * @param writeFilter
	 *        the <code>WriteFilter</code> to add to this <code>RTPTranslator</code>
	 */
	void addWriteFilter(WriteFilter writeFilter);

	/**
	 * Releases the resources allocated by this instance in the course of its execution and prepares
	 * it to be garbage collected.
	 */
	void dispose();

	/**
	 * Removes a <code>WriteFilter</code> from this <code>RTPTranslator</code>.
	 *
	 * @param writeFilter
	 *        the <code>WriteFilter</code> to remove from this <code>RTPTranslator</code>
	 */
	void removeWriteFilter(WriteFilter writeFilter);
}
