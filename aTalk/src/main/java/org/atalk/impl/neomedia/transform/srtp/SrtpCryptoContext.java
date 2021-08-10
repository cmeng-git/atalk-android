/*
 * Copyright @ 2015 - present 8x8, Inc
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
 *
 * Some of the code in this class is derived from ccRtp's SRTP implementation,
 * which has the following copyright notice:
 *
 * Copyright (C) 2004-2006 the Minisip Team
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307 USA
 */
package org.atalk.impl.neomedia.transform.srtp;

import org.atalk.impl.neomedia.transform.srtp.utils.SrtpPacketUtils;
import org.atalk.util.ByteArrayBuffer;
import org.bouncycastle.crypto.params.KeyParameter;

import java.util.Arrays;

import timber.log.Timber;

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
 */
public class SrtpCryptoContext extends BaseSrtpCryptoContext
{
    /**
     * For the receiver only, the rollover counter guessed from the sequence number of the received
     * packet that is currently being processed (i.e. the value is valid during the execution of
     * {@link #reverseTransformPacket(ByteArrayBuffer, boolean)} only.) RFC 3711 refers to it by the name <tt>v</tt>.
     */
    private int guessedROC;

    /**
     * RFC 3711: a 32-bit unsigned rollover counter (ROC), which records how many times the 16-bit RTP
     * sequence number has been reset to zero after passing through 65,535.  Unlike the sequence number (SEQ),
     * which SRTP extracts from the RTP packet header, the ROC is maintained by SRTP as described in Section 3.3.1.
     */
    private int roc;

    /**
     * RFC 3711: for the receiver only, a 16-bit sequence number <tt>s_l</tt>,
     * which can be thought of as the highest received RTP sequence number (see
     * Section 3.3.1 for its handling), which SHOULD be authenticated since
     * message authentication is RECOMMENDED.
     */
    private int s_l = 0;

    /**
     * The indicator which determines whether this instance is used by an SRTP
     * sender (<tt>true</tt>) or receiver (<tt>false</tt>).
     */
    private final boolean sender;

    /**
     * The indicator which determines whether {@link #s_l} has seen set i.e.
     * appropriately initialized.
     */
    private boolean seqNumSet = false;

    /**
     * Constructs an empty SrtpCryptoContext using ssrc. The other parameters are set to default null value.
     *
     * @param sender <tt>true</tt> if the new instance is to be used by an SRTP sender; <tt>false</tt> if
     * the new instance is to be used by an SRTP receiver
     * @param ssrc SSRC of this SrtpCryptoContext
     */
    public SrtpCryptoContext(boolean sender, int ssrc)
    {
        super(ssrc);
        this.sender = sender;
        roc = 0;
    }

    /**
     * Constructs a normal SrtpCryptoContext based on the given parameters.
     *
     * @param sender <tt>true</tt> if the new instance is to be used by an SRTP sender; <tt>false</tt> if
     * the new instance is to be used by an SRTP receiver
     * @param ssrc the RTP SSRC that this SRTP cryptographic context protects.
     * @param roc the initial Roll-Over-Counter according to RFC 3711. These are the upper 32-bits
     * of the overall 48-bits SRTP packet index. Refer to chapter 3.2.1 of the RFC.
     * @param masterK byte array holding the master key for this SRTP cryptographic context.
     * Refer to chapter 3.2.1 of the RFC about the role of the master key.
     * @param masterS byte array holding the master salt for this SRTP cryptographic context.
     * It is used to computer the initialization vector that in turn as input to compute
     * the session key, session authentication key, and the session salt.
     * @param policy SRTP policy for this SRTP cryptographic context, defined
     * the encryption algorithm, the authentication algorithm, etc
     */
    public SrtpCryptoContext(
            boolean sender, int ssrc, int roc,
            byte[] masterK, byte[] masterS, SrtpPolicy policy)
    {
        super(ssrc, masterK, masterS, policy);
        this.sender = sender;
        this.roc = roc;

        deriveSrtpKeys(masterK, masterS);
    }

    /**
     * Authenticates a specific <tt>RawPacket</tt> if the <tt>policy</tt> of this
     * <tt>SrtpCryptoContext</tt> specifies that authentication is to be performed.
     *
     * @param pkt the <tt>RawPacket</tt> to authenticate
     * @return <tt>true</tt> if the <tt>policy</tt> of this <tt>SrtpCryptoContext</tt> specifies that authentication
     * is to not be performed or <tt>pkt</tt> was successfully authenticated; otherwise, <tt>false</tt>
     */
    private SrtpErrorStatus authenticatePacket(ByteArrayBuffer pkt)
    {
        if (policy.getAuthType() != SrtpPolicy.NULL_AUTHENTICATION) {
            int tagLength = policy.getAuthTagLength();

            // get original authentication and store in tempStore
            pkt.readRegionToBuff((pkt.getLength() - tagLength), tagLength, tempStore);

            pkt.shrink(tagLength);
            // save computed authentication in tagStore
            authenticatePacketHmac(pkt, guessedROC);

            // compare authentication tags using constant time comparison
            int nonEqual = 0;
            for (int i = 0; i < tagLength; i++) {
                nonEqual |= (tempStore[i] ^ tagStore[i]);
            }
            if (nonEqual != 0)
                return SrtpErrorStatus.AUTH_FAIL;
        }
        return SrtpErrorStatus.OK;
    }

    /**
     * Checks if a packet is a replayed based on its sequence number. The method
     * supports a 64 packet history relative the the specified sequence number.
     * The sequence number is guaranteed to be real (i.e. not faked) through authentication.
     *
     * @param seqNo sequence number of the packet
     * @param guessedIndex guessed ROC
     * @return <tt>true</tt> if the specified sequence number indicates that the
     * packet is not a replayed one; <tt>false</tt>, otherwise.
     */
    SrtpErrorStatus checkReplay(int seqNo, long guessedIndex)
    {
        // Compute the index of the previously received packet and its delta to the newly received packet.
        long localIndex = (((long) roc) << 16) | s_l;
        long delta = guessedIndex - localIndex;

        if (delta > 0) {
            return SrtpErrorStatus.OK; // Packet not received yet.
        }
        else if (-delta >= REPLAY_WINDOW_SIZE) {
            if (sender) {
                Timber.e("Discarding RTP packet with sequence number %d, SSRC %d because it is outside the replay window! (roc %d, s_l %d), guessedROC %d",
                        seqNo, (0xFFFFFFFFL & ssrc), roc, s_l, guessedROC);
            }
            return SrtpErrorStatus.REPLAY_OLD; // Packet too old.
        }
        else if (((replayWindow >>> (-delta)) & 0x1) != 0) {
            if (sender) {
                Timber.e("Discarding RTP packet with sequence number %d, SSRC %d because it has been received already! (roc %d, s_l %d), guessedROC %d",
                        seqNo, (0xFFFFFFFFL & ssrc), roc, s_l, guessedROC);
            }
            return SrtpErrorStatus.REPLAY_FAIL; // Packet received already!
        }
        else {
            return SrtpErrorStatus.OK; // Packet not received yet.
        }

    }

    /**
     * Derives the srtp session keys from the master key
     */
    private void deriveSrtpKeys(byte[] masterKey, byte[] masterSalt)
    {
        SrtpKdf kdf = new SrtpKdf(masterKey, masterSalt, policy);

        // compute the session salt
        kdf.deriveSessionKey(saltKey, SrtpKdf.LABEL_RTP_SALT);

        // compute the session encryption key
        if (cipherCtr != null) {
            byte[] encKey = new byte[policy.getEncKeyLength()];
            kdf.deriveSessionKey(encKey, SrtpKdf.LABEL_RTP_ENCRYPTION);

            if (cipherF8 != null) {
                cipherF8.init(encKey, saltKey);
            }
            cipherCtr.init(encKey);
            Arrays.fill(encKey, (byte) 0);
        }

        // compute the session authentication key
        if (mac != null) {
            byte[] authKey = new byte[policy.getAuthKeyLength()];
            kdf.deriveSessionKey(authKey, SrtpKdf.LABEL_RTP_MSG_AUTH);
            mac.init(new KeyParameter(authKey));
            Arrays.fill(authKey, (byte) 0);
        }

        kdf.close();
    }

    /**
     * For the receiver only, determines/guesses the SRTP index of a received
     * SRTP packet with a specific sequence number.
     *
     * @param seqNo the sequence number of the received SRTP packet.
     * @return the SRTP index of the received SRTP packet with the specified <tt>seqNo</tt>
     */
    private long guessIndex(int seqNo)
    {
        if (s_l < 32768) {
            if (seqNo - s_l > 32768)
                guessedROC = roc - 1;
            else
                guessedROC = roc;
        }
        else {
            if (s_l - 32768 > seqNo)
                guessedROC = roc + 1;
            else
                guessedROC = roc;
        }
        return (((long) guessedROC) << 16) | seqNo;
    }

    /**
     * Performs Counter Mode AES encryption/decryption
     *
     * @param pkt the RTP packet to be encrypted/decrypted
     */
    private void processPacketAesCm(ByteArrayBuffer pkt)
    {
        int ssrc = SrtpPacketUtils.getSsrc(pkt);
        int seqNo = SrtpPacketUtils.getSequenceNumber(pkt);
        long index = (((long) guessedROC) << 16) | seqNo;

        // byte[] iv = new byte[16];
        ivStore[0] = saltKey[0];
        ivStore[1] = saltKey[1];
        ivStore[2] = saltKey[2];
        ivStore[3] = saltKey[3];

        int i;
        for (i = 4; i < 8; i++) {
            ivStore[i] = (byte) ((0xFF & (ssrc >> ((7 - i) * 8))) ^ saltKey[i]);
        }
        for (i = 8; i < 14; i++) {
            ivStore[i] = (byte) ((0xFF & (byte) (index >> ((13 - i) * 8))) ^ saltKey[i]);
        }
        ivStore[14] = ivStore[15] = 0;

        int rtpHeaderLength = SrtpPacketUtils.getTotalHeaderLength(pkt);

        cipherCtr.process(
                pkt.getBuffer(),
                pkt.getOffset() + rtpHeaderLength,
                pkt.getLength() - rtpHeaderLength,
                ivStore);
    }

    /**
     * Performs F8 Mode AES encryption/decryption
     *
     * @param pkt the RTP packet to be encrypted/decrypted
     */
    private void processPacketAesF8(ByteArrayBuffer pkt)
    {
        // 11 bytes of the RTP header are the 11 bytes of the iv
        // the first byte of the RTP header is not used.
        System.arraycopy(pkt.getBuffer(), pkt.getOffset(), ivStore, 0, 12);
        ivStore[0] = 0;

        // set the ROC in network order into IV
        int roc = guessedROC;

        ivStore[12] = (byte) (roc >> 24);
        ivStore[13] = (byte) (roc >> 16);
        ivStore[14] = (byte) (roc >> 8);
        ivStore[15] = (byte) roc;

        int rtpHeaderLength = SrtpPacketUtils.getTotalHeaderLength(pkt);

        cipherF8.process(
                pkt.getBuffer(),
                pkt.getOffset() + rtpHeaderLength,
                pkt.getLength() - rtpHeaderLength,
                ivStore);
    }

    /**
     * Transforms an SRTP packet into an RTP packet. The method is called when
     * an SRTP packet is received. Operations done by the this operation
     * include: authentication check, packet replay check and decryption. Both
     * encryption and authentication functionality can be turned off as long as
     * the SrtpPolicy used in this SrtpCryptoContext is requires no encryption
     * and no authentication. Then the packet will be sent out untouched.
     * However, this is not encouraged. If no SRTP feature is enabled, then we
     * shall not use SRTP TransformConnector. We should use the original method
     * (RTPManager managed transportation) instead.
     *
     * @param pkt the RTP packet that is just received
     * @param skipDecryption if {@code true}, the decryption of the packet will not be performed (so as not to waste
     * resources when it is not needed). The packet will still be authenticated and the ROC updated.
     * @return {@link SrtpErrorStatus#OK} if the packet can be accepted; an error status if
     * the packet failed authentication or failed replay check
     */
    synchronized public SrtpErrorStatus reverseTransformPacket(ByteArrayBuffer pkt, boolean skipDecryption)
    {
        if (!SrtpPacketUtils.validatePacketLength(pkt, policy.getAuthTagLength())) {
            /* Too short to be a valid SRTP packet */
            return SrtpErrorStatus.INVALID_PACKET;
        }

        int seqNo = SrtpPacketUtils.getSequenceNumber(pkt);
        if ((seqNo % 5000) == 0)
            Timber.d("Reverse transform for SSRC: %s; SeqNo: %s; s_l: %s; seqNumSet: %s; roc: %s; guessedROC: %s",
                    this.ssrc, seqNo, s_l, seqNumSet, guessedROC, roc);

        // Whether s_l was initialized while processing this packet.
        boolean seqNumWasJustSet = false;
        if (!seqNumSet) {
            seqNumSet = true;
            s_l = seqNo;
            seqNumWasJustSet = true;
        }

        // Guess the SRTP index (48 bit), see RFC 3711, 3.3.1
        // Stores the guessed rollover counter (ROC) in this.guessedROC.
        long guessedIndex = guessIndex(seqNo);
        SrtpErrorStatus ret, err;

        // Replay control
        if (policy.isReceiveReplayDisabled() || ((err = checkReplay(seqNo, guessedIndex)) == SrtpErrorStatus.OK)) {
            // Authenticate the packet.
            if ((err = authenticatePacket(pkt)) == SrtpErrorStatus.OK) {
                if (!skipDecryption) {
                    switch (policy.getEncType()) {
                        // Decrypt the packet using Counter Mode encryption.
                        case SrtpPolicy.AESCM_ENCRYPTION:
                        case SrtpPolicy.TWOFISH_ENCRYPTION:
                            processPacketAesCm(pkt);
                            break;

                        // Decrypt the packet using F8 Mode encryption.
                        case SrtpPolicy.AESF8_ENCRYPTION:
                        case SrtpPolicy.TWOFISHF8_ENCRYPTION:
                            processPacketAesF8(pkt);
                            break;
                    }
                }
                // Update the rollover counter and highest sequence number if necessary.
                update(seqNo, guessedIndex);

                ret = SrtpErrorStatus.OK;
            }
            else {
                Timber.w("SRTP auth failed for SSRC %s", ssrc);
                ret = err;
            }
        }
        else {
            ret = err;
        }

        if (ret != SrtpErrorStatus.OK && seqNumWasJustSet) {
            // We set the initial value of s_l as a result of processing this
            // packet, but the packet failed to authenticate. We shouldn't
            // update our state based on an untrusted packet, so we revert seqNumSet.
            seqNumSet = false;
            s_l = 0;
        }

        return ret;
    }

    /**
     * Transforms an RTP packet into an SRTP packet. The method is called when a normal RTP packet
     * ready to be sent. Operations done by the transformation may include: encryption, using either
     * Counter Mode encryption, or F8 Mode encryption, adding authentication tag, currently HMC SHA1
     * method. Both encryption and authentication functionality can be turned off as long as the
     * SRTPPolicy used in this SrtpCryptoContext is requires no encryption and no authentication.
     * Then the packet will be sent out untouched. However, this is not encouraged. If no SRTP
     * feature is enabled, then we shall not use SRTP TransformConnector. We should use the original
     * method (RTPManager managed transportation) instead.
     *
     * @param pkt the RTP packet that is going to be sent out
     */
    synchronized public SrtpErrorStatus transformPacket(ByteArrayBuffer pkt)
    {
        int seqNo = SrtpPacketUtils.getSequenceNumber(pkt);

        if (!seqNumSet) {
            seqNumSet = true;
            s_l = seqNo;
        }

        // Guess the SRTP index (48 bit), see RFC 3711, 3.3.1
        // Stores the guessed ROC in this.guessedROC
        long guessedIndex = guessIndex(seqNo);

        SrtpErrorStatus err;

        /*
         * XXX The invocation of the checkReplay method here is not meant as
         * replay protection but as a consistency check of our implementation.
         */
        if (policy.isSendReplayEnabled() && (err = checkReplay(seqNo, guessedIndex)) != SrtpErrorStatus.OK)
            return err;

        switch (policy.getEncType()) {
            // Encrypt the packet using Counter Mode encryption.
            case SrtpPolicy.AESCM_ENCRYPTION:
            case SrtpPolicy.TWOFISH_ENCRYPTION:
                processPacketAesCm(pkt);
                break;

            // Encrypt the packet using F8 Mode encryption.
            case SrtpPolicy.AESF8_ENCRYPTION:
            case SrtpPolicy.TWOFISHF8_ENCRYPTION:
                processPacketAesF8(pkt);
                break;
        }

        /* Authenticate the packet. */
        if (policy.getAuthType() != SrtpPolicy.NULL_AUTHENTICATION) {
            authenticatePacketHmac(pkt, guessedROC);
            pkt.append(tagStore, policy.getAuthTagLength());
        }

        // Update the ROC if necessary.
        update(seqNo, guessedIndex);

        return SrtpErrorStatus.OK;
    }

    /**
     * For the receiver only, updates the rollover counter (i.e. {@link #roc})
     * and highest sequence number (i.e. {@link #s_l}) in this cryptographic
     * context using the SRTP/packet index calculated by
     * {@link #guessIndex(int)} and updates the replay list (i.e.
     * {@link #replayWindow}). This method is called after all checks were successful.
     *
     * @param seqNo the sequence number of the accepted SRTP packet
     * @param guessedIndex the SRTP index of the accepted SRTP packet calculated by <tt>guessIndex(int)</tt>
     */
    private void update(int seqNo, long guessedIndex)
    {
        long delta = guessedIndex - ((((long) roc) << 16) | s_l);

        /* Update the replay bit mask. */
        if (delta >= REPLAY_WINDOW_SIZE) {
            replayWindow = 1;
        }
        else if (delta > 0) {
            replayWindow <<= delta;
            replayWindow |= 1;
        }
        else {
            replayWindow |= (1L << -delta);
        }

        if (guessedROC == roc) {
            if (seqNo > s_l)
                s_l = seqNo & 0xffff;
        }
        else if (guessedROC == (roc + 1)) {
            s_l = seqNo & 0xffff;
            roc = guessedROC;
        }

        // Limit the debug info to 1 per 1000
        if (seqNo % 5000 == 0)
            logReplayWindow(guessedIndex);
    }

    /**
     * Logs the current state of the replay window, for debugging purposes.
     */
    private void logReplayWindow(long newIdx)
    {
        Timber.d("Updated replay window with seqNo: %s. %s", newIdx,
                SrtpPacketUtils.formatReplayWindow((roc << 16 | s_l), replayWindow, REPLAY_WINDOW_SIZE));
    }
}
