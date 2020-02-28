/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.service.neomedia;

/**
 * ZRTP based SRTP MediaStream encryption control.
 *
 * @author Damian Minkov
 * @author Eng Chong Meng
 * @author MilanKral
 */
public interface ZrtpControl extends SrtpControl
{
    /**
     * Gets the cipher information for the current media stream.
     *
     * @return the cipher information string.
     */
    String getCipherString();

    /**
     * Gets the negotiated ZRTP protocol version.
     *
     * @return the <tt>int</tt> representation of the negotiated ZRTP protocol version.
     */
    int getCurrentProtocolVersion();

    /**
     * Returns the zrtp hello hash String.
     *
     * @param index Hello hash of the Hello packet identified by index. Must be
     * <code>0 &lt;= index &lt; SUPPORTED_ZRTP_VERSIONS</code>.
     * @return String the zrtp hello hash.
     */
    String getHelloHash(int index);

    /**
     * Gets the ZRTP Hello Hash data - separate strings.
     *
     * @param index Hello hash of the Hello packet identified by index. Must be
     * <code>0 &lt;= index &lt; SUPPORTED_ZRTP_VERSIONS</code>.
     * @return String array containing the version string at offset 0, the Hello hash value as
     * hex-digits at offset 1. Hello hash is available immediately after class
     * instantiation. Returns <tt>null</tt> if ZRTP is not available.
     */
    String[] getHelloHashSep(int index);

    /**
     * Gets the number of supported ZRTP protocol versions.
     *
     * @return the number of supported ZRTP protocol versions.
     */
    int getNumberSupportedVersions();

    /**
     * Gets the peer's Hello Hash data as a <tt>String</tt>.
     *
     * @return a String containing the Hello hash value as hex-digits. Peer Hello hash is available
     * after we received a Hello packet from our peer. If peer's hello hash is not
     * available, returns <tt>null</tt>.
     */
    String getPeerHelloHash();

    /**
     * Gets other party's ZID (ZRTP Identifier) data that was received during ZRTP processing. The
     * ZID data can be retrieved after ZRTP receives the first Hello packet from the other party.
     *
     * @return the ZID data as a <tt>byte</tt> array.
     */
    byte[] getPeerZid();

    /**
     * Gets other party's ZID (ZRTP Identifier) data that was received during ZRTP processing as a
     * <tt>String</tt>. The ZID data can be retrieved after ZRTP receives the first Hello packet
     * from the other party.
     *
     * @return the ZID data as a <tt>String</tt>.
     */
    String getPeerZidString();

    /**
     * Gets the SAS for the current media stream.
     *
     * @return the four character ZRTP SAS.
     */
    String getSecurityString();

    /**
     * Returns the timeout value in milliseconds that we will wait and fire timeout secure event if
     * call is not secured.
     *
     * @return the timeout value in milliseconds that we will wait and fire timeout secure event if
     * call is not secured.
     */
    long getTimeoutValue();

    /**
     * Gets the status of the SAS verification.
     *
     * @return <tt>true</tt> when the SAS has been verified.
     */
    boolean isSecurityVerified();

    /**
     * Sets the SAS verification
     *
     * @param verified the new SAS verification status
     */
    void setSASVerification(boolean verified);
    /**
     * Set ZRTP version received from the signaling layer.
     * @param version received version
     */
    void setReceivedSignaledZRTPVersion(String version);

    /**
     * Set ZRTP hash value received from the signaling layer.
     * @param value hash value
     */
    void setReceivedSignaledZRTPHashValue(String value);
}
