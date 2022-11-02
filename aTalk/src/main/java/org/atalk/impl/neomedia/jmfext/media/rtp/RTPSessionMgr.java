/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.jmfext.media.rtp;

import net.sf.fmj.media.rtp.GenerateSSRCCause;

import org.atalk.service.neomedia.SSRCFactory;

/**
 * Implements {@link javax.media.rtp.RTPManager} for the purposes of the libjitsi library in general
 * and the neomedia package in particular.
 * <p>
 * Allows <code>MediaStream</code> to optionally utilize {@link SSRCFactory}.
 * </p>
 *
 * @author Lyubomir Marinov
 */
public class RTPSessionMgr extends net.sf.fmj.media.rtp.RTPSessionMgr
{
	/**
	 * The <code>SSRCFactory</code> to be utilized by this instance to generate new synchronization
	 * source (SSRC) identifiers. If <code>null</code>, this instance will employ internal logic to
	 * generate new synchronization source (SSRC) identifiers.
	 */
	private SSRCFactory ssrcFactory;

	/**
	 * Initializes a new <code>RTPSessionMgr</code> instance.
	 */
	public RTPSessionMgr()
	{
	}

	/**
	 * Gets the <code>SSRCFactory</code> utilized by this instance to generate new synchronization
	 * source (SSRC) identifiers.
	 *
	 * @return the <code>SSRCFactory</code> utilized by this instance or <code>null</code> if this instance
	 * employs internal logic to generate new synchronization source (SSRC) identifiers
	 */
	public SSRCFactory getSSRCFactory()
	{
		return ssrcFactory;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected long generateSSRC(GenerateSSRCCause cause)
	{
		SSRCFactory ssrcFactory = getSSRCFactory();

		return (ssrcFactory == null)
				? super.generateSSRC(cause)
				: ssrcFactory.generateSSRC((cause == null) ? null : cause.name());
	}

	/**
	 * Sets the <code>SSRCFactory</code> to be utilized by this instance to generate new
	 * synchronization source (SSRC) identifiers.
	 *
	 * @param ssrcFactory
	 * 		the <code>SSRCFactory</code> to be utilized by this instance to generate new
	 * 		synchronization source (SSRC) identifiers or <code>null</code> if this instance is to
	 * 		employ internal logic to generate new synchronization source (SSRC) identifiers
	 */
	public void setSSRCFactory(SSRCFactory ssrcFactory)
	{
		this.ssrcFactory = ssrcFactory;
	}
}
