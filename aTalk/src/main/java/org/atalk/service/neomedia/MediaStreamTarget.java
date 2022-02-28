/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.service.neomedia;

import java.net.InetAddress;
import java.net.InetSocketAddress;

/**
 * The <code>MediaStreamTarget</code> contains a pair of host:port couples indicating data (RTP) and control (RTCP) locations.
 *
 * @author Emil Ivov
 * @author Lyubomir Marinov
 */
public class MediaStreamTarget
{
    /**
     * The data (RTP) address of the target.
     */
    private final InetSocketAddress rtpTarget;

    /**
     * The control (RTCP) address of the target.
     */
    private final InetSocketAddress rtcpTarget;

    /**
     * Initializes a new <code>MediaStreamTarget</code> instance with specific RTP and RTCP
     * <code>InetSocketAddress<code>es.
     *
     * @param rtpTarget the <code>InetSocketAddress</code> that the new instance is to indicate as a data/RTP address.
     * @param rtcpTarget the <code>InetSocketAddress</code> that the new instance is to indicate as a control/RTCP address.
     */
    public MediaStreamTarget(InetSocketAddress rtpTarget, InetSocketAddress rtcpTarget)
    {
        this.rtpTarget = rtpTarget;
        this.rtcpTarget = rtcpTarget;
    }

    /**
     * Initializes a new <code>MediaStreamTarget</code> instance with specific RTP and RTCP
     * <code>InetAddress</code>es and ports.
     *
     * @param rtpAddr the <code>InetAddress</code> that the new instance is to indicate as the IP address of a
     * data/RTP address
     * @param rtpPort the port that the new instance is to indicate as the port of a data/RTP address
     * @param rtcpAddr the <code>InetAddress</code> that the new instance is to indicate as the IP address of a
     * control/RTCP address
     * @param rtcpPort the port that the new instance is to indicate as the port of a control/RTCP address
     */
    public MediaStreamTarget(InetAddress rtpAddr, int rtpPort, InetAddress rtcpAddr, int rtcpPort)
    {
        this(new InetSocketAddress(rtpAddr, rtpPort), new InetSocketAddress(rtcpAddr, rtcpPort));
    }

    /**
     * Determines whether two specific <code>InetSocketAddress</code> instances are equal.
     *
     * @param addr1 one of the <code>InetSocketAddress</code> instances to be compared
     * @param addr2 the other <code>InetSocketAddress</code> instance to be compared
     * @return <code>true</code> if <code>addr1</code> is equal to <code>addr2</code>; otherwise, <code>false</code>
     */
    public static boolean addressesAreEqual(InetSocketAddress addr1, InetSocketAddress addr2)
    {
        return (addr1 == null) ? (addr2 == null) : addr1.equals(addr2);
    }

    /**
     * Determines whether this <code>MediaStreamTarget</code> is equal to a specific <code>Object</code>.
     *
     * @param obj the <code>Object</code> to be compared to this <code>MediaStreamTarget</code>
     * @return <code>true</code> if this <code>MediaStreamTarget</code> is equal to the specified
     * <code>obj</code>; otherwise, <code>false</code>
     */
    @Override
    public boolean equals(Object obj)
    {
        if (this == obj)
            return true;

        if (!getClass().isInstance(obj))
            return false;

        MediaStreamTarget mediaStreamTarget = (MediaStreamTarget) obj;

        return addressesAreEqual(getControlAddress(), mediaStreamTarget.getControlAddress())
                && addressesAreEqual(getDataAddress(), mediaStreamTarget.getDataAddress());
    }

    /**
     * Returns the <code>InetSocketAddress</code> that this <code>MediaTarget</code> is pointing to for all
     * media (RTP) traffic.
     *
     * @return the <code>InetSocketAddress</code> that this <code>MediaTarget</code> is pointing to for all
     * media (RTP) traffic.
     */
    public InetSocketAddress getDataAddress()
    {
        return rtpTarget;
    }

    /**
     * Returns the <code>InetSocketAddress</code> that this <code>MediaTarget</code> is pointing to for all
     * media (RTP) traffic.
     *
     * @return the <code>InetSocketAddress</code> that this <code>MediaTarget</code> is pointing to for all
     * media (RTP) traffic.
     */
    public InetSocketAddress getControlAddress()
    {
        return rtcpTarget;
    }

    /**
     * Returns a hash code for this <code>MediaStreamTarget</code> instance which is suitable for use in hash tables.
     *
     * @return a hash code for this <code>MediaStreamTarget</code> instance which is suitable for use in hash tables
     */
    @Override
    public int hashCode()
    {
        int hashCode = 0;
        InetSocketAddress controlAddress = getControlAddress();

        if (controlAddress != null)
            hashCode |= controlAddress.hashCode();

        InetSocketAddress dataAddress = getDataAddress();

        if (dataAddress != null)
            hashCode |= dataAddress.hashCode();

        return hashCode;
    }

    /**
     * Returns a human-readable representation of this <code>MediaStreamTarget</code> instance in the
     * form of a <code>String</code> value.
     *
     * @return a <code>String</code> value which gives a human-readable representation of this
     * <code>MediaStreamTarget</code> instance
     */
    @Override
    public String toString()
    {
        return getClass().getSimpleName() + " with dataAddress " + getDataAddress()
                + " and controlAddress " + getControlAddress();
    }
}
