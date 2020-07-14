/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 *
 *
 * Some of the code in this class is derived from ccRtp's SRTP implementation, which has the
 * following copyright notice:
 *
 * Copyright (C) 2004-2006 the Minisip Team
 *
 * This library is free software; you can redistribute it and/or modify it under the terms of the
 * GNU Lesser General Public License as published by the Free Software Foundation; either version
 * 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without
 * even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License along with this library;
 * if not, write to the Free Software Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA
 * 02111-1307 USA
 */
package org.atalk.impl.neomedia.transform.srtp;

import org.atalk.impl.neomedia.transform.srtp.crypto.*;
import org.atalk.util.ByteArrayBuffer;
import org.bouncycastle.crypto.Mac;
import org.bouncycastle.crypto.engines.TwofishEngine;
import org.bouncycastle.crypto.macs.SkeinMac;

/**
 * SrtpCryptoContext class is the core class of SRTP implementation. There can be multiple SRTP
 * sources in one SRTP session. And each SRTP stream has a corresponding SrtpCryptoContext object,
 * identified by SSRC. In this way, different sources can be protected independently.
 *
 * SrtpCryptoContext class acts as a manager class and maintains all the information used in SRTP
 * transformation. It is responsible for deriving encryption/salting/authentication keys from master
 * keys. And it will invoke certain class to encrypt/decrypt (transform/reverse transform) RTP
 * packets. It will hold a replay check db and do replay check against incoming packets.
 *
 * Refer to section 3.2 in RFC3711 for detailed description of cryptographic context.
 *
 * Cryptographic related parameters, i.e. encryption mode / authentication mode, master encryption
 * key and master salt key are determined outside the scope of SRTP implementation. They can be
 * assigned manually, or can be assigned automatically using some key management protocol, such as
 * MIKEY (RFC3830), SDES (RFC4568) or Phil Zimmermann's ZRTP protocol (RFC6189).
 *
 * @author Bing SU (nova.su@gmail.com)
 * @author Lyubomir Marinov
 * @author Eng Chong Meng
 * @author MilanKral
 */
public class BaseSrtpCryptoContext
{
    /**
     * The replay check windows size.
     */
    protected static final long REPLAY_WINDOW_SIZE = 64;

    /**
     * implements the counter cipher mode for RTP according to RFC 3711
     */
    protected final SrtpCipherCtr cipherCtr;

    /**
     * F8 mode cipher
     */
    protected final SrtpCipherF8 cipherF8;

    /**
     * Temp store.
     */
    protected final byte[] ivStore = new byte[16];

    /**
     * The HMAC object we used to do packet authentication
     */
    protected final Mac mac; // used for various HMAC computations

    /**
     * Encryption / Authentication policy for this session
     */
    protected final SrtpPolicy policy;

    /**
     * Temp store.
     */
    protected final byte[] rbStore = new byte[4];

    /**
     * Bit mask for replay check
     */
    protected long replayWindow;

    /**
     * Derived session salting key
     */
    protected final byte[] saltKey;

    /**
     * RTP/RTCP SSRC of this cryptographic context
     */
    protected final int ssrc;

    /**
     * Temp store.
     */
    protected final byte[] tagStore;

    /**
     * this is a working store, used by some methods to avoid new operations the methods must use
     * this only to store results for immediate processing
     */
    protected final byte[] tempStore = new byte[100];

    protected BaseSrtpCryptoContext(int ssrc)
    {
        this.ssrc = ssrc;

        cipherCtr = null;
        cipherF8 = null;
        mac = null;
        policy = null;
        saltKey = null;
        tagStore = null;
    }

    @SuppressWarnings("fallthrough")
    protected BaseSrtpCryptoContext(int ssrc, byte[] masterK, byte[] masterS, SrtpPolicy policy)
    {
        this.ssrc = ssrc;
        this.policy = policy;

        int encKeyLength = policy.getEncKeyLength();

        if (masterK != null) {
            if (masterK.length != encKeyLength) {
                throw new IllegalArgumentException("masterK.length != encKeyLength");
            }
        }
        else {
            if (encKeyLength != 0) {
                throw new IllegalArgumentException("null masterK but encKeyLength != 0");
            }
        }
        int saltKeyLength = policy.getSaltKeyLength();

        if (masterS != null) {
            if (masterS.length != saltKeyLength) {
                throw new IllegalArgumentException("masterS.length != saltKeyLength");
            }
        }
        else {
            if (saltKeyLength != 0) {
                throw new IllegalArgumentException("null masterS but saltKeyLength != 0");
            }
        }

        SrtpCipherCtr cipherCtr = null;
        SrtpCipherF8 cipherF8 = null;
        byte[] saltKey = null;

        switch (policy.getEncType()) {
            case SrtpPolicy.NULL_ENCRYPTION:
                break;

            case SrtpPolicy.AESF8_ENCRYPTION:
                cipherF8 = new SrtpCipherF8(Aes.createBlockCipher(encKeyLength));
                //$FALL-THROUGH$

            case SrtpPolicy.AESCM_ENCRYPTION:
                // use OpenSSL if available and AES128 is in use
                if (OpenSslWrapperLoader.isLoaded()
                        && (encKeyLength == 16 || encKeyLength == 24 || encKeyLength == 32)) {
                    cipherCtr = new SrtpCipherCtrOpenSsl();
                }
                else {
                    cipherCtr = new SrtpCipherCtrJava(Aes.createBlockCipher(encKeyLength));
                }
                saltKey = new byte[saltKeyLength];
                break;

            case SrtpPolicy.TWOFISHF8_ENCRYPTION:
                cipherF8 = new SrtpCipherF8(new TwofishEngine());
                //$FALL-THROUGH$

            case SrtpPolicy.TWOFISH_ENCRYPTION:
                cipherCtr = new SrtpCipherCtrJava(new TwofishEngine());
                saltKey = new byte[saltKeyLength];
                break;
        }
        this.cipherCtr = cipherCtr;
        this.cipherF8 = cipherF8;
        this.saltKey = saltKey;

        Mac mac;
        byte[] tagStore;

        switch (policy.getAuthType()) {
            case SrtpPolicy.HMACSHA1_AUTHENTICATION:
                mac = HmacSha1.createMac();
                tagStore = new byte[mac.getMacSize()];
                break;

            case SrtpPolicy.SKEIN_AUTHENTICATION:
                tagStore = new byte[policy.getAuthTagLength()];
                mac = new SkeinMac(SkeinMac.SKEIN_512, tagStore.length * 8);
                break;

            case SrtpPolicy.NULL_AUTHENTICATION:
            default:
                mac = null;
                tagStore = null;
                break;
        }
        this.mac = mac;
        this.tagStore = tagStore;
    }

    /**
     * Authenticates a packet. Calculated authentication tag is returned/stored in {@link #tagStore}
     * .
     *
     * @param pkt the RTP packet to be authenticated
     * @param rocIn Roll-Over-Counter
     */
    synchronized protected void authenticatePacketHmac(ByteArrayBuffer pkt, int rocIn)
    {
        mac.update(pkt.getBuffer(), pkt.getOffset(), pkt.getLength());
        rbStore[0] = (byte) (rocIn >> 24);
        rbStore[1] = (byte) (rocIn >> 16);
        rbStore[2] = (byte) (rocIn >> 8);
        rbStore[3] = (byte) rocIn;
        mac.update(rbStore, 0, rbStore.length);
        mac.doFinal(tagStore, 0);
    }

    /**
     * Closes this crypto context. The close functions deletes key data and performs a cleanup of
     * this crypto context. Clean up key data, maybe this is the second time. However, sometimes we
     * cannot know if the CryptoContext was used and the application called deriveSrtpKeys(...).
     */
    synchronized public void close()
    {
        /* TODO, clean up ciphers and mac. */
    }

    /**
     * Gets the authentication tag length of this SRTP cryptographic context
     *
     * @return the authentication tag length of this SRTP cryptographic context
     */
    public int getAuthTagLength()
    {
        return policy.getAuthTagLength();
    }

    /**
     * Gets the SSRC of this SRTP cryptographic context
     *
     * @return the SSRC of this SRTP cryptographic context
     */
    public int getSsrc()
    {
        return ssrc;
    }
}
