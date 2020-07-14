/*
 * Copyright @ 2015 Atlassian Pty Ltd
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.atalk.impl.neomedia.transform.srtp;

import java.util.Arrays;

/**
 * The <tt>SrtpContextFactory</tt> creates the initial crypto contexts for RTP
 * and RTCP encryption using the supplied key material.
 *
 * @author Bing SU (nova.su@gmail.com)
 */
public class SrtpContextFactory
{
    /**
     * Master encryption key
     */
    private final byte[] masterKey;

    /**
     * Master salting key
     */
    private final byte[] masterSalt;

    /**
     * The indicator which determines whether this instance is used by SRTP
     * senders (<tt>true</tt>) or receiver (<tt>false</tt>).
     */
    private final boolean sender;

    /**
     * Encryption / Authentication policy for SRTP.
     */
    private final SrtpPolicy srtpPolicy;

    /**
     * Encryption / Authentication policy for SRTCP.
     */
    private final SrtpPolicy srtcpPolicy;

    /**
     * Construct a SrtpTransformEngine based on given master encryption key, master salt key and Srtp/Srtcp policy.
     *
     * @param sender <tt>true</tt> if the new instance is to be used by an SRTP sender;
     * <tt>false</tt> if the new instance is to be used by an SRTP receiver
     * @param masterKey the master encryption key
     * @param masterSalt the master salt key
     * @param srtpPolicy SRTP policy
     * @param srtcpPolicy SRTCP policy
     */
    public SrtpContextFactory(boolean sender, byte[] masterKey, byte[] masterSalt, SrtpPolicy srtpPolicy,
            SrtpPolicy srtcpPolicy)
    {
        int encKeyLength = srtpPolicy.getEncKeyLength();
        if (encKeyLength != srtcpPolicy.getEncKeyLength()) {
            throw new IllegalArgumentException("srtpPolicy.getEncKeyLength() != srtcpPolicy.getEncKeyLength()");
        }

        if (masterKey != null) {
            if (masterKey.length != encKeyLength) {
                throw new IllegalArgumentException("masterK.length != encKeyLength (" + masterKey.length + " : " + encKeyLength +")");
            }

            this.masterKey = new byte[encKeyLength];
            System.arraycopy(masterKey, 0, this.masterKey, 0, encKeyLength);
        }
        else {
            if (encKeyLength != 0) {
                throw new IllegalArgumentException("null masterK but encKeyLength != 0");
            }
            this.masterKey = new byte[0];
        }

        int saltKeyLength = srtpPolicy.getSaltKeyLength();
        if (saltKeyLength != srtcpPolicy.getSaltKeyLength()) {
            throw new IllegalArgumentException("srtpPolicy.getSaltKeyLength() != srtcpPolicy.getSaltKeyLength()");
        }

        if (masterSalt != null) {
            if (masterSalt.length != saltKeyLength) {
                throw new IllegalArgumentException("masterS.length != saltKeyLength (" + masterSalt.length + " : " + saltKeyLength +")");
            }

            this.masterSalt = new byte[saltKeyLength];
            System.arraycopy(masterSalt, 0, this.masterSalt, 0, saltKeyLength);
        }
        else {
            if (saltKeyLength != 0) {
                throw new IllegalArgumentException("null masterS but saltKeyLength != 0");
            }
            this.masterSalt = new byte[0];
        }

        this.sender = sender;
        this.srtpPolicy = srtpPolicy;
        this.srtcpPolicy = srtcpPolicy;
    }

    /**
     * Close the transformer engine.
     *
     * The close functions closes all stored default crypto state.
     */
    public void close()
    {
        Arrays.fill(masterKey, (byte) 0);
        Arrays.fill(masterSalt, (byte) 0);
    }

    /**
     * Derives a new SrtpCryptoContext for use with a new SSRC. The method returns a new SrtpCryptoContext
     * initialized with the master key, master salt, and sender state of this factory.
     * Before the application can use this SrtpCryptoContext it must call the deriveSrtpKeys method.
     *
     * @param ssrc The SSRC for this context
     * @param roc The Roll-Over-Counter for this context
     * @return a new SrtpCryptoContext with all relevant data set.
     */
    public SrtpCryptoContext deriveContext(int ssrc, int roc)
    {
        return new SrtpCryptoContext(sender, ssrc, roc, masterKey, masterSalt, srtpPolicy);
    }

    /**
     * Derives a new SrtcpCryptoContext for use with a new SSRC. The method returns a new SrtcpCryptoContext
     * initialized with the master key and master salt of this factory.
     * Before the application can use this SrtpCryptoContext it must call the deriveSrtcpKeys method.
     *
     * @param ssrc The sender SSRC for this context
     * @return a new SrtcpCryptoContext with all relevant data set.
     */
    public SrtcpCryptoContext deriveControlContext(int ssrc)
    {
        return new SrtcpCryptoContext(ssrc, masterKey, masterSalt, srtcpPolicy);
    }
}
