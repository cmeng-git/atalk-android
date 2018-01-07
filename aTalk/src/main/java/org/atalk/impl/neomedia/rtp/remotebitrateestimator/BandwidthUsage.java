/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.rtp.remotebitrateestimator;

/**
 * webrtc/modules/remote_bitrate_estimator/include/bwe_defines.h
 *
 * @author Lyubomir Marinov
 */
enum BandwidthUsage
{
	kBwNormal(0), kBwUnderusing(-1), kBwOverusing(1);

	private int value;

	BandwidthUsage(int value)
	{
		this.value = value;
	}

	public int getValue()
	{
		return value;
	}
}
