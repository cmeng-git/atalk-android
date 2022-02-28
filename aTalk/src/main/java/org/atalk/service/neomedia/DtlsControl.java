/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.service.neomedia;

import java.util.Map;

/**
 * Implements {@link SrtpControl} for DTSL-SRTP.
 *
 * @author Lyubomir Marinov
 */
public interface DtlsControl extends SrtpControl
{
    /**
     * The transport protocol (i.e. <code>&lt;proto&gt;</code>) to be specified in a SDP media description
     * (i.e. <code>m=</code> line) in order to denote a RTP/SAVP stream transported over DTLS with UDP.
     */
    public static final String UDP_TLS_RTP_SAVP = "UDP/TLS/RTP/SAVP";

    /**
     * The transport protocol (i.e. <code>&lt;proto&gt;</code>) to be specified in a SDP media description
     * (i.e. <code>m=</code> line) in order to denote a RTP/SAVPF stream transported over DTLS with UDP.
     */
    public static final String UDP_TLS_RTP_SAVPF = "UDP/TLS/RTP/SAVPF";

    /**
     * Gets the fingerprint of the local certificate that this instance uses to authenticate its
     * ends of DTLS sessions.
     *
     * @return the fingerprint of the local certificate that this instance uses to authenticate its
     * ends of DTLS sessions
     */
    String getLocalFingerprint();

    /**
     * Gets the hash function with which the fingerprint of the local certificate is computed i.e.
     * the digest algorithm of the signature algorithm of the local certificate.
     *
     * @return the hash function with which the fingerprint of the local certificate is computed
     */
    String getLocalFingerprintHashFunction();

    /**
     * Sets the certificate fingerprints presented by the remote endpoint via the signaling path.
     *
     * @param remoteFingerprints a <code>Map</code> of hash functions to certificate fingerprints
     * that have been presented by the remote endpoint via the signaling path
     */
    void setRemoteFingerprints(Map<String, String> remoteFingerprints);

    /**
     * Sets the value of the <code>setup</code> SDP attribute defined by RFC 4145 &quot;TCP-Based Media
     * Transport in the Session Description Protocol (SDP)&quot; which determines whether this
     * instance is to act as a DTLS client or a DTLS server.
     *
     * @param setup the value of the <code>setup</code> SDP attribute to set on this instance in order to
     * determine whether this instance is to act as a DTLS client or a DTLS server
     */
    void setSetup(Setup setup);

    /**
     * Enables/disables rtcp-mux.
     *
     * @param rtcpmux whether to enable or disable.
     */
    void setRtcpmux(boolean rtcpmux);

    /**
     * Enumerates the possible values of the <code>setup</code> SDP attribute defined by RFC 4145
     * &quot;TCP-Based Media Transport in the Session Description Protocol (SDP)&quot;.
     *
     * @author Lyubomir Marinov
     */
    enum Setup
    {
        ACTIVE,
        ACTPASS,
        HOLDCONN,
        PASSIVE;

        /**
         * Parses a <code>String</code> into a <code>Setup</code> enum value. The specified <code>String</code>
         * to parse must be in a format as produced by {@link #toString()}; otherwise, the method
         * will throw an exception.
         *
         * @param s the <code>String</code> to parse into a <code>Setup</code> enum value
         * @return a <code>Setup</code> enum value on which <code>toString()</code> produces the specified <code>s</code>
         * @throws IllegalArgumentException if none of the <code>Setup</code> enum values produce
         * the specified <code>s</code> when <code>toString()</code> is invoked on them
         * @throws NullPointerException if <code>s</code> is <code>null</code>
         */
        static Setup parseSetup(String s)
        {
            if (s == null)
                throw new NullPointerException("s");
            for (Setup v : values()) {
                if (v.toString().equalsIgnoreCase(s))
                    return v;
            }
            throw new IllegalArgumentException(s);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public String toString()
        {
            return name().toLowerCase();
        }
    }
}
