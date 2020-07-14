/*
 * Copyright (C) 2006-2008 Werner Dittmann
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 * Authors: Werner Dittmann <Werner.Dittmann@t-online.de>
 */

package gnu.java.zrtp;

import gnu.java.zrtp.ZrtpConstants.SupportedSASTypes;
import gnu.java.zrtp.packets.*;
import gnu.java.zrtp.utils.Base32;
import gnu.java.zrtp.utils.EmojiBase32;
import gnu.java.zrtp.utils.ZrtpSecureRandom;
import gnu.java.zrtp.utils.ZrtpUtils;
import gnu.java.zrtp.zidfile.ZidFile;
import gnu.java.zrtp.zidfile.ZidRecord;
import org.bouncycastle.crypto.Digest;
import org.bouncycastle.crypto.digests.SHA256Digest;
import org.bouncycastle.crypto.digests.SHA384Digest;
import org.bouncycastle.crypto.macs.HMac;
import org.bouncycastle.crypto.params.*;
import org.bouncycastle.crypto.AsymmetricCipherKeyPair;
import org.bouncycastle.math.ec.ECPoint;

import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.EnumSet;

/**
 * The main ZRTP class.
 * 
 * This is the main class of the RTP/SRTP independent part of the GNU ZRTP. It
 * handles the ZRTP HMAC, DH, and other data management. The user of this class
 * needs to know only a few methods and needs to provide only a few external
 * functions to connect to a Timer mechanism and to send data via RTP and SRTP.
 * Refer to the ZrtpCallback class to get detailed information regarding the
 * callback methods required by GNU RTP.
 * 
 * The class ZRTPTransformEngine is the Java JMF specific implementation that extends
 * standard Java JMF and the related RTP transport. Refer to the documentation of
 * ZRTPTransformEngine to get more information about the usage of ZRtp and associated
 * classes.
 * 
 * The main entry into the ZRTP class is the processExtensionHeader() method.
 * 
 * This class does not directly handle the protocol states, timers, and packet
 * resend. The protocol state engine is responsible for these actions.
 * 
 * Example how to use ZRtp:
 * 
 * <pre>
 *   transConnector = (ZrtpTransformConnector)TransformManager.createZRTPConnector(sa);
 *   zrtpEngine = transConnector.getEngine();
 *   zrtpEngine.setUserCallback(new MyCallback());
 *   if (!zrtpEngine.initialize(&quot;test_t.zid&quot;))
 *       System.out.println(&quot;initialize failed&quot;);
 * 
 *    zrtpEngine.startZrtpEngine();
 * </pre>
 * 
 * @see ZrtpCallback
 * 
 * @author Werner Dittmann &lt;Werner.Dittmann@t-online.de&gt;
 * 
 */

public class ZRtp {

    // max. number of parallel supported ZRTP protocol versions.
    static final int MAX_ZRTP_VERSIONS = 2;

    // max. number of parallel supported ZRTP protocol versions.
    static final int SUPPORTED_ZRTP_VERSIONS = 1;

    /**
     * Faster access to Hello packets with different versions.
     */
    static class HelloPacketVersion {
        int version;
        ZrtpPacketHello packet;
        byte[] helloHash;
    }


    /**
     * The state engine takes care of protocol processing.
     */
    private final ZrtpStateClass stateEngine;

    /**
     * ZRTP cache entry that holds RS data for a specific ZID.
     */
    ZidRecord zidRec;
    /**
     * This is my ZID that I send to the peer.
     */
    private final byte[] zid = new byte[ZidRecord.IDENTIFIER_LENGTH];

    /**
     * The peer's ZID
     */
    private byte[] peerZid;

    /**
     * The call back class provides me with the interface to send data and to
     * deal with timer management of the hosting system.
     */
    private ZrtpCallback callback = null;

    private AsymmetricCipherKeyPair dhKeyPair = null;

    private AsymmetricCipherKeyPair ecKeyPair = null;

    private SecureRandom secRand;

    /**
     * The computed DH shared secret
     */
    private byte[] DHss = null;

    /**
     * My computed public key
     */
    private byte[] pubKeyBytes = null;

    /**
     * My Role in the game
     */
    private ZrtpCallback.Role myRole;

    /**
     * The human readable SAS value
     */
    private String SAS;

    /**
     * The SAS hash for signaling and alike. Refer to chapters 5.5, 6.13, 9.4.
     * sasValue and the SAS string are derived from sasHash.
     */
    private byte[] sasHash = null;

    /**
     * The variables for the retained shared secrets
     */
    private byte[] rs1IDr = null;

    private byte[] rs2IDr = null;

    private byte[] auxSecretIDr = null;

    private byte[] pbxSecretIDr = null;

    private byte[] rs1IDi = null;

    private byte[] rs2IDi = null;

    private byte[] auxSecretIDi = null;

    private byte[] pbxSecretIDi = null;

    /**
     * Remember is valid rs1 or rs2 records were available
     */
    private boolean rs1Valid = false;

    private boolean rs2Valid = false;

    /**
     * My hvi
     */
    private byte[] hvi = new byte[ZrtpConstants.MAX_DIGEST_LENGTH];

    /**
     * The peer's hvi
     */
    private byte[] peerHvi = null;

    /**
     * Context to compute the hash and HMAC of selected messages. This is the
     * negotiated hash algorithm
     */
    private int hashLength;

    private Digest hashFunction;

    private Digest hashCtxFunction;

    private HMac hmacFunction;

    // These are implicit hash and HMAC settings.
    private int hashLengthImpl = ZrtpConstants.SHA256_DIGEST_LENGTH;

    private Digest hashFunctionImpl = new SHA256Digest();

    private HMac hmacFunctionImpl = new HMac(new SHA256Digest());

    /**
     * Committed Hash, Cipher, and public key algorithms
     */
    private ZrtpConstants.SupportedHashes hash;

    private ZrtpConstants.SupportedSymCiphers cipher;

    private ZrtpConstants.SupportedPubKeys pubKey;

    /**
     * The selected SAS type.
     */
    private ZrtpConstants.SupportedSASTypes sasType;

    /**
     * The selected authenitaction length.
     */
    private ZrtpConstants.SupportedAuthLengths authLength;

    /**
     * The Hash images as defined in chapter 5.1.1 (H0 is a random value, not
     * stored here). Need full max hash lenght to store hash value but only the
     * leftmost 128 bits are used in computations and comparisons.
     */
    private byte[] H0 = new byte[ZrtpConstants.MAX_DIGEST_LENGTH];

    private byte[] H1 = new byte[ZrtpConstants.MAX_DIGEST_LENGTH];

    private byte[] H2 = new byte[ZrtpConstants.MAX_DIGEST_LENGTH];

    private byte[] H3 = new byte[ZrtpConstants.MAX_DIGEST_LENGTH];

    private byte[] peerHelloHash = new byte[ZrtpConstants.MAX_DIGEST_LENGTH];
    private byte[] peerHelloVersion = null;

    // need 128 bits only to store peer's values
    private byte[] peerH2 = new byte[ZrtpConstants.MAX_DIGEST_LENGTH];

    private byte[] peerH3 = new byte[ZrtpConstants.MAX_DIGEST_LENGTH];

    /**
     * The hash over selected messages
     */
    private byte[] messageHash = new byte[ZrtpConstants.MAX_DIGEST_LENGTH];

    /**
     * The s0
     */
    private byte[] s0 = new byte[ZrtpConstants.MAX_DIGEST_LENGTH];

    /**
     * The new Retained Secret
     */
    private byte[] newRs1 = null;

    /**
     * The GoClear HMAC keys and confirm HMAC key
     */
    private byte[] hmacKeyI = null;

    private byte[] hmacKeyR = null;

    /**
     * The Initiator's srtp key and salt
     */
    private byte[] srtpKeyI = null;

    private byte[] srtpSaltI = null;

    /**
     * The Responder's srtp key and salt
     */
    private byte[] srtpKeyR = null;

    private byte[] srtpSaltR = null;

    /**
     * The keys used to encrypt/decrypt the confirm message
     */
    private byte[] zrtpKeyI = null;

    private byte[] zrtpKeyR = null;

    /**
     * The ZRTP Session Key Refer to chapter 5.4.1.4
     */
    private byte[] zrtpSession = null;

    /**
     * True if this ZRTP instance uses multi-stream mode.
     */
    private boolean multiStream = false;

    /**
     * True if the other ZRTP client supports multi-stream mode.
     */
    private boolean multiStreamAvailable = false;

    /**
     * Enable MitM (PBX) enrollment
     * 
     * If set to true then ZRTP honors the PBX enrollment flag in
     * Commit packets and calls the appropriate user callback
     * methods. If the parameter is set to false ZRTP ignores the PBX
     * enrollment flags.
     */
    private boolean enableMitmEnrollment = false;
    
    /**
     * True if a valid trusted MitM key of the other peer is available, i.e. enrolled.
     */
    private boolean peerIsEnrolled;
    /**
     * Set to true if the Hello packet contained the M-flag (MitM flag).
     * We use this later to check some stuff for SAS Relay processing
     */
    private boolean mitmSeen = false;
    
    /**
     * Set to true if the Hello packet contained the S-flag (sign SAS flag).
     */
    private boolean signSasSeen = false;

    /**
     * Temporarily store computed pbxSecret, if user accepts enrollment then
     * it will copied to our ZID record of the PBX (MitM)  
     */
    private byte[] pbxSecretTmp = null;

    /**
     * If true then we will set the enrollment flag (E) in the confirm
     * packets. Set to true if the PBX enrollment service started this ZRTP 
     * session. Can be set to true only if mitmMode is also true. 
     */
    private boolean enrollmentMode = false;
    
    /**
     * Pre-initialized packets.
     */
    private ZrtpPacketHello zrtpHello_11 = new ZrtpPacketHello();
    private ZrtpPacketHello zrtpHello_12 = new ZrtpPacketHello();

    private ZrtpPacketHelloAck zrtpHelloAck = new ZrtpPacketHelloAck();

    private ZrtpPacketConf2Ack zrtpConf2Ack = new ZrtpPacketConf2Ack();

    // ZrtpPacketClearAck zrtpClearAck;
    // ZrtpPacketGoClear zrtpGoClear;
    private ZrtpPacketError zrtpError = new ZrtpPacketError();

    private ZrtpPacketErrorAck zrtpErrorAck = new ZrtpPacketErrorAck();

    private ZrtpPacketDHPart zrtpDH1 = new ZrtpPacketDHPart();

    private ZrtpPacketDHPart zrtpDH2 = new ZrtpPacketDHPart();

    private ZrtpPacketCommit zrtpCommit = new ZrtpPacketCommit();

    private ZrtpPacketConfirm zrtpConfirm1 = new ZrtpPacketConfirm();

    private ZrtpPacketConfirm zrtpConfirm2 = new ZrtpPacketConfirm();

    private ZrtpPacketPingAck zrtpPingAck = new ZrtpPacketPingAck();

    private ZrtpPacketSASRelay zrtpSasRelay = new ZrtpPacketSASRelay();

    private ZrtpPacketRelayAck zrtpRelayAck = new ZrtpPacketRelayAck();

    HelloPacketVersion helloPackets[] = new HelloPacketVersion[MAX_ZRTP_VERSIONS];
    int highestZrtpVersion;

    // Pointer to Hello packet sent to partner, initialized in ZRtp, modified by ZrtpStateClass
    ZrtpPacketHello currentHelloPacket;

    /**
     * Random IV data to encrypt the confirm data, 128 bit for AES
     */
    private byte[] randomIV = new byte[16];

    private byte[] tempMsgBuffer = new byte[1024];

    private int lengthOfMsgData;

    /**
     * Variables to store signature data. Includes the signature type block
     */
    private byte[] signatureData = null; // will be allocated when needed

    private int signatureLength = 0; // overall length in bytes

    private int peerSSRC = 0; // the partner's ssrc

    /**
     * Enable or disable paranoid mode.
     * 
     * The Paranoid mode controls the behaviour and handling of the SAS verify flag. If
     * Panaoid mode is set to flase then ZRtp applies the normal handling. If Paranoid
     * mode is set to true then the handling is:
     * 
     * <ul>
     * <li> Force the SAS verify flag to be false at srtpSecretsOn() callback. This gives
     *      the user interface (UI) the indication to handle the SAS as <b>not verified</b>. 
     *      See implementation note below.</li>
     * <li> Don't set the SAS verify flag in the <code>Confirm</code> packets, thus the other
     *      also must report the SAS as <b>not verified</b>.</li>
     * <li> ignore the <code>SASVerified()</code> function, thus do not set the SAS to verified
     *      in the ZRTP cache. </li>
     * <li> Disable the <b>Trusted PBX MitM</b> feature. Just send the <code>SASRelay</code> packet
     *      but do not process the relayed data. This protects the user from a malicious 
     *      "trusted PBX".</li>
     * </ul>
     * ZRtp performs alls other steps during the ZRTP negotiations as usual, in particular it 
     * computes, compares, uses, and stores the retained secrets. This avoids unnecessary warning
     * messages. The user may enable or disable the Paranoid mode on a call-by-call basis without
     * breaking the key continuity data.
     * 
     * <b>Implementation note:</b></br>
     * An application shall always display the SAS code if the SAS verify flag is <code>false</code>.
     * The application shall also use mechanisms to remind the user to compare the SAS code, for
     * example useing larger fonts, different colours and other display features.
     */
    private boolean paranoidMode = false;
    
    private ZrtpConfigure configureAlgos;

    public ZRtp(byte[] myZid, ZrtpCallback cb, String id, ZrtpConfigure config) {
        this(myZid, cb, id, config, false, false);
    }

    public ZRtp(byte[] myZid, ZrtpCallback cb, String id, ZrtpConfigure config, boolean mitmMode) {
        this(myZid, cb, id, config, mitmMode, false);
    }
    /**
     * Constructor initializes all relevant data but does not start the engine.
     */
    public ZRtp(byte[] myZid, ZrtpCallback cb, String id, ZrtpConfigure config, boolean mitmMode, boolean sasSignSupport) {

        secRand = ZrtpSecureRandom.getInstance();

        configureAlgos = config;
        enableMitmEnrollment = config.isTrustedMitM();
        paranoidMode = config.isParanoidMode();

        System.arraycopy(myZid, 0, zid, 0, ZidRecord.IDENTIFIER_LENGTH);
        callback = cb;
        secRand.nextBytes(randomIV); // IV used in ZRTP packet encryption

        /*
         * Generate H0 as a random number (256 bits, 32 bytes) and then the hash chain, refer to chapter 9
         */
        secRand.nextBytes(H0);
        // hash H0 and generate H1
        hashFunctionImpl.update(H0, 0, ZrtpPacketBase.HASH_IMAGE_SIZE);
        hashFunctionImpl.doFinal(H1, 0);

        hashFunctionImpl.update(H1, 0, ZrtpPacketBase.HASH_IMAGE_SIZE); // H2
        hashFunctionImpl.doFinal(H2, 0);

        hashFunctionImpl.update(H2, 0, ZrtpPacketBase.HASH_IMAGE_SIZE); // H3
        hashFunctionImpl.doFinal(H3, 0);

        zrtpHello_11.configureHello(config);
        zrtpHello_11.setH3(H3); // set H3 in Hello, included in helloHash
        zrtpHello_11.setZid(zid);
        zrtpHello_11.setVersion(ZrtpConstants.zrtpVersion_11);

        zrtpHello_12.configureHello(config);
        zrtpHello_12.setH3(H3); // set H3 in Hello, included in helloHash
        zrtpHello_12.setZid(zid);
        zrtpHello_12.setVersion(ZrtpConstants.zrtpVersion_12);

        if (mitmMode) {                 // this session acts for a trusted MitM (PBX)
            zrtpHello_11.setMitmMode();
            zrtpHello_12.setMitmMode();
        }
        if (sasSignSupport) {
            zrtpHello_11.setSasSign();
            zrtpHello_12.setSasSign();
        }

        // Keep array in ascending order (greater index -> greater version)
        helloPackets[0] = new HelloPacketVersion();
        helloPackets[0].helloHash = new byte[ZrtpConstants.MAX_DIGEST_LENGTH];
        helloPackets[0].packet = zrtpHello_11;
        helloPackets[0].version = zrtpHello_11.getVersionInt();
        setClientId(id, helloPackets[0]);      // set id, compute HMAC and final helloHash

        helloPackets[1] = new HelloPacketVersion();
        helloPackets[1].helloHash = new byte[ZrtpConstants.MAX_DIGEST_LENGTH];
        helloPackets[1].packet = zrtpHello_12;
        helloPackets[1].version = zrtpHello_12.getVersionInt();
        setClientId(id, helloPackets[1]);      // set id, compute HMAC and final helloHash
     
        currentHelloPacket = helloPackets[SUPPORTED_ZRTP_VERSIONS-1].packet;  // start with supported available version

        stateEngine = new ZrtpStateClass(this);
    }

    /*
     * First the public methods.
     */
    /**
     * Kick off the ZRTP protocol engine.
     * 
     * This method calls the ZrtpStateClass#evInitial() state of the state
     * engine. After this call we are able to process ZRTP packets from our peer
     * and to process them.
     */
    public void startZrtpEngine() {
        if (stateEngine != null && stateEngine.isInState(ZrtpStateClass.ZrtpStates.Initial)) {
            ZrtpStateClass.Event ev = stateEngine.new Event(ZrtpStateClass.EventDataType.ZrtpInitial, null);

            stateEngine.processEvent(ev);
        }
    }

    /**
     * Stop ZRTP security.
     * 
     */
    public void stopZrtp() {
        if (stateEngine != null) {
            ZrtpStateClass.Event ev = stateEngine.new Event(ZrtpStateClass.EventDataType.ZrtpClose, null);

            stateEngine.processEvent(ev);
        }
    }

    /**
     * Process RTP extension header.
     * 
     * This method expects to get a pointer to the extension header of a RTP
     * packet. The method checks if this is really a ZRTP packet. If this check
     * fails the method returns 0 (false) in case this is not a ZRTP packet. We
     * return a 1 if we processed the ZRTP extension header and the caller may
     * process RTP data after the extension header as usual. The method return
     * -1 the call shall dismiss the packet and shall not forward it to further
     * RTP processing.
     * 
     * @param extHeader
     *            A pointer to the first byte of the extension header. Refer to
     *            RFC3550.
     */
    public void processZrtpMessage(byte[] extHeader, int ssrc) {
        peerSSRC = ssrc;

        if (stateEngine != null) {
            ZrtpStateClass.Event ev = stateEngine.new Event(ZrtpStateClass.EventDataType.ZrtpPacket, extHeader);
            stateEngine.processEvent(ev);
        }
    }

    /**
     * Process a timeout event.
     * 
     * We got a timeout from the timeout provider. Forward it to the protocol
     * state engine.
     * 
     */
    public void processTimeout() {
        if (stateEngine != null) {
            ZrtpStateClass.Event ev = stateEngine.new Event(ZrtpStateClass.EventDataType.Timer, null);

            stateEngine.processEvent(ev);
        }
    }

    /**
     * Check for and handle GoClear ZRTP packet header.
     * 
     * This method checks if this is a GoClear packet. If not, just return
     * false. Otherwise handle it according to the specification.
     * 
     * @param extHeader
     *            A pointer to the first byte of the extension header. Refer to
     *            RFC3550.
     * @return False if not a GoClear, true otherwise.
     * 
     *         // bool handleGoClear(uint *extHeader);
     */

    /**
     * Set the auxiliary secret.
     * 
     * Use this method to set the auxiliary secret data. Refer to ZRTP
     * specification, chapter 4.3 ff
     * 
     * @param data
     *            Points to the secret data.
     */
    public void setAuxSecret(byte[] data) {
    }
    
    /**
     * Check the state of the enrollment mode.
     * 
     * If true then we will set the enrollment flag (E) in the confirm
     * packets and performs the enrollment actions. A MitM (PBX) enrollment service
     * started this ZRTP session. Can be set to true only if mitmMode is also true.
     * 
     * @return status of the enrollmentMode flag.
     */
    public boolean isEnrollmentMode() {
        return enrollmentMode;
    }

    /**
     * Check the state of the enrollment mode.
     * 
     * If true then we will set the enrollment flag (E) in the confirm
     * packets and perform the enrollment actions. A MitM (PBX) enrollment 
     * service must sets this mode to true. 
     * 
     * Can be set to true only if mitmMode is also true. 
     * 
     * @param enrollmentMode defines the new state of the enrollmentMode flag
     */
    public void setEnrollmentMode(boolean enrollmentMode) {
        this.enrollmentMode = enrollmentMode;
    }

    /**
     * Check if a peer's cache entry has a vaild MitM key.
     *
     * If true then the other peer ha a valid MtiM key, i.e. the peer has performed
     * the enrollment procedure. A PBX ZRTP Back-2-Back application can use this function
     * to check which of the peers is enrolled.
     *
     * @return True if the other peer has a valid Mitm key (is enrolled).
     */
    public boolean isPeerEnrolled() {
        return peerIsEnrolled;
    }

    /**
     * Send the SAS relay packet.
     * 
     * The method creates and sends a SAS relay packet according to the ZRTP
     * specifications. Usually only a MitM capable user agent (PBX) uses this
     * function.
     * 
     * @param sh the full SAS hash value
     * @param render the SAS rendering algorithm
     */
    public boolean sendSASRelayPacket(byte[] sh, ZrtpConstants.SupportedSASTypes render) {
        byte[] hkey, ekey;
        // If we are responder then the PBX used it's Initiator keys
        if (myRole == ZrtpCallback.Role.Responder) {
            hkey = hmacKeyR;
            ekey = zrtpKeyR;
        }
        else {
            hkey = hmacKeyI;
            ekey = zrtpKeyI;
        }
        secRand.nextBytes(randomIV);
        zrtpSasRelay.setIv(randomIV);
        zrtpSasRelay.setTrustedSas(sh);
        zrtpSasRelay.setSasType(render.name);

        // Encrypt and HMAC with selectedkey
        byte[] dataToSecure = zrtpSasRelay.getDataToSecure();
        try {
            cipher.cipher.init(true, new ParametersWithIV(new KeyParameter(ekey, 0, cipher.keyLength), randomIV));
            int done = cipher.cipher.processBytes(dataToSecure, 0, dataToSecure.length, dataToSecure, 0);
            cipher.cipher.doFinal(dataToSecure, done);
        } catch (Exception e) {
            sendInfo(ZrtpCodes.MessageSeverity.Severe, EnumSet.of(ZrtpCodes.SevereCodes.SevereSecurityException));
            return false;
        }
        byte[] confMac = computeHmac(hkey, hashLength, dataToSecure, dataToSecure.length);
        zrtpSasRelay.setDataToSecure(dataToSecure);
        zrtpSasRelay.setHmac(confMac);
        stateEngine.sendSASRelay(zrtpSasRelay);
        return true;
    }

    /**
     * Get the commited SAS rendering algorithm for this ZRTP session.
     * 
     * @return the commited SAS rendering algorithm
     */
    public ZrtpConstants.SupportedSASTypes getSasType() {
        return sasType;
    }

    /**
     * Get the computed SAS hash for this ZRTP session.
     * 
     * @return a refernce to the byte array that contains the full 
     *         SAS hash.
     */
    public byte[] getSasHash() {
        return sasHash;
    }


    /**
     * Check current state of the ZRTP state engine
     * 
     * @param state
     *            The state to check.
     * @return Returns true id ZRTP engine is in the given state, false
     *         otherwise.
     */
    public boolean inState(ZrtpStateClass.ZrtpStates state) {
        return stateEngine != null && stateEngine.isInState(state);
    }

    /**
     * Set SAS as verified.
     * 
     * Call this method if the user confirmed (verfied) the SAS. ZRTP remembers
     * this together with the retained secrets data.
     */
    public void SASVerified() {
        if (paranoidMode)
            return;

        zidRec.setSasVerified();
        ZidFile.getInstance().saveRecord(zidRec);
    }

    /**
     * Reset the SAS verfied flag for the current active user's retained secrets.
     */
    public void resetSASVerified() {
        zidRec.resetSasVerified();
        ZidFile.getInstance().saveRecord(zidRec);
    }


    public void setRs2Valid() {
        if (zidRec != null) {
            zidRec.setRs2Valid();
            ZidFile.getInstance().saveRecord(zidRec);
        }
    }
    /**
     * Get the ZRTP Hello Hash data.
     * 
     * Use this method to get the ZRTP Hello Hash data. The method returns the
     * data as a string.
     * 
     * @param  index 
     *         Hello hash of the Hello packet identified by index. Index must be 0 &lt;= index &lt; SUPPORTED_ZRTP_VERSIONS.
     *
     * @return a string containing the Hello hash value as hex-digits. The
     *         hello hash is available immediately after class instantiation.
     */
    public String getHelloHash(int index) {
        String pv = new String(helloPackets[index].packet.getVersion());
        String hs = new String(ZrtpUtils.bytesToHexString(helloPackets[index].helloHash, hashLengthImpl));
        return pv + " " + hs;
    }

    /**
     * Get the ZRTP Hello Hash data - separate strings.
     * 
     * Use this method to get the ZRTP Hello Hash data. The method returns the
     * data as separate strings.
     * 
     * @param  index 
     *         Hello hash of the Hello packet identified by index. Index must be 0 &lt;= index &lt; SUPPORTED_ZRTP_VERSIONS.
     *
     * @return String array containing the version string at offset 0, the Hello
     *         hash value as hex-digits at offset 1. Hello hash is available
     *         immediately after class instantiation.
     */
    public String[] getHelloHashSep(int index) {
        String ret[] = new String[2];
        ret[0] = new String(helloPackets[index].packet.getVersion());
        ret[1] = new String(ZrtpUtils.bytesToHexString(helloPackets[index].helloHash, hashLengthImpl));
        return ret;
    }

    /**
     * Get the peer's Hello Hash data.
     * 
     * Use this method to get the peer's Hello Hash data. The method returns the
     * data as a string.
     * 
     * @return a String containing the Hello hash value as hex-digits. 
     *         Peer Hello hash is available after we received a Hello packet 
     *         from our peer. If peer's hello hash is not available return null.
     */
    public String getPeerHelloHash() {
        if (peerHelloVersion == null)
            return null;

        String pv = new String(peerHelloVersion);
        String hs = new String(ZrtpUtils.bytesToHexString(peerHelloHash, hashLengthImpl));
        return pv + " " + hs;
    }

    /**
     * Get the peer's Hello Hash data - separate strings.
     * 
     * Use this method to get the peer's Hello Hash data. The method returns the
     * data as separate strings.
     * 
     * @return String array containing the version string at offset 0, the Hello
     *         hash value as hex-digits at offset 1. Peer Hello hash is available
     *         after we received a Hello packet from our peer. If peer's hello
     *         hash is not available return null.
     */
    public String[] getPeerHelloHashSep() {
        String ret[] = new String[2];

        if (peerHelloVersion == null)
            return null;

        ret[0] = new String(peerHelloVersion);
        ret[1] = new String(ZrtpUtils.bytesToHexString(peerHelloHash, hashLengthImpl));
        return ret;
    }

    /**
     * Get Multi-stream parameters.
     * 
     * Use this method to get the Multi-stream that were computed during the
     * ZRTP handshake. An application may use these parameters to enable
     * multi-stream processing for an associated SRTP session.
     * 
     * Refer to chapter 5.4.2 in the ZRTP specification for further details and
     * restriction how and when to use multi-stream mode.
     * 
     * @return a string that contains the multi-stream parameters. The
     *         application must not modify the contents of this string, it is
     *         opaque data. The application may hand over this string to a new
     *         ZrtpQueue instance to enable multi-stream processing for this
     *         ZrtpQueue. If ZRTP was not started or ZRTP is not yet in secure
     *         state the method returns an empty string.
     */
    public byte[] getMultiStrParams() {
        byte[] tmp = null;

        if (inState(ZrtpStateClass.ZrtpStates.SecureState) && !multiStream) {
            // digest length + cipher + authLength + hash
            tmp = new byte[hashLength + 1 + 1 + 1];
            // construct array that holds zrtpSession, cipher type, auth-length,
            // and hash
            tmp[0] = (byte) hash.ordinal();
            tmp[1] = (byte) authLength.ordinal();
            tmp[2] = (byte) cipher.ordinal();
            System.arraycopy(zrtpSession, 0, tmp, 3, hashLength);
        }
        return tmp;
    }

    /**
     * Set Multi-stream parameters.
     * 
     * Use this method to set the parameters required to enable Multi-stream
     * processing of ZRTP. The multi-stream parameters must be set before the
     * application starts the ZRTP protocol engine.
     * 
     * Refer to chapter 5.4.2 in the ZRTP specification for further details of
     * multi-stream mode.
     * 
     * @param parameters
     *            A byte array that contains the multi-stream parameters. See
     *            also {@code getMultiStrParams()}
     */
    public void setMultiStrParams(byte[] parameters) {

        for (ZrtpConstants.SupportedHashes a : ZrtpConstants.SupportedHashes.values()) {
            if (a.ordinal() == (parameters[0] & 0xff)) {
                hash = a;
                break;
            }
        }
        setNegotiatedHash(hash);
        zrtpSession = new byte[hashLength];
        for (ZrtpConstants.SupportedAuthLengths a : ZrtpConstants.SupportedAuthLengths.values()) {
            if (a.ordinal() == (parameters[1] & 0xff)) {
                authLength = a;
                break;
            }
        }
        for (ZrtpConstants.SupportedSymCiphers c : ZrtpConstants.SupportedSymCiphers.values()) {
            if (c.ordinal() == (parameters[2] & 0xff)) {
                cipher = c;
                break;
            }
        }
        System.arraycopy(parameters, 3, zrtpSession, 0, hashLength);
        multiStream = true;
        stateEngine.setMultiStream(true);
    }

    /**
     * Check if this ZRTP use Multi-stream.
     * 
     * Use this method to check if this ZRTP instance uses multi-stream. Even if
     * the application provided multi-stram parameters it may happen that full
     * DH mode was used. Refer to chapters 5.2 and 5.4.2 in the ZRTP # when this
     * may happen.
     * 
     * @return True if multi-stream is used, false otherwise.
     */
    public boolean isMultiStream() {
        return multiStream;
    }

    /**
     * Check if the other ZRTP client supports Multi-stream.
     * 
     * Use this method to check if the other ZRTP client supports Multi-stream
     * mode.
     * 
     * @return True if multi-stream is available, false otherwise.
     */
    public boolean isMultiStreamAvailable() {
        return multiStreamAvailable;
    }

    /**
     * Accept a PBX enrollment request.
     * 
     * If a PBX service asks to enroll the MiTM key and the user accepts this
     * request, for example by pressing an OK button, the client application
     * shall call this method and set the parameter <code>accepted</code> to
     * true. If the user does not accept the request set the parameter to false.
     * 
     * @param accepted
     *            True if the enrollment request is accepted, false otherwise.
     */
    public void acceptEnrollment(boolean accepted) {

        if (!accepted) {
            callback.zrtpInformEnrollment(ZrtpCodes.InfoEnrollment.EnrollmentCanceled);
            return;
        }
        if (pbxSecretTmp != null) {
            zidRec.setMiTMData(pbxSecretTmp);
            callback.zrtpInformEnrollment(ZrtpCodes.InfoEnrollment.EnrollmentOk);
        }
        else {
            callback.zrtpInformEnrollment(ZrtpCodes.InfoEnrollment.EnrollmentFailed);
            return;
        }
        ZidFile.getInstance().saveRecord(zidRec);
    }

    /**
     * Set signature data
     * 
     * This functions stores signature data and transmitts it during ZRTP
     * processing to the other party as part of the Confirm packets. Refer to
     * chapters 7.2ff.
     * 
     * @param data
     *            The signature data including the signature type block. The
     *            method copies this data into the Confirm packet at signature
     *            type block. The length of the signature data must be multiple
     *            of 4 bytes.
     * @return True if the method stored the data, false otherwise.
     */
    public boolean setSignatureData(byte[] data) {
        if ((data.length % 4) != 0)
            return false;
        ZrtpPacketConfirm conf = (myRole == ZrtpCallback.Role.Responder) ? zrtpConfirm1 : zrtpConfirm2;

        conf.setSignatureLength(data.length / 4);
        return conf.setSignatureData(data);
    }

    /**
     * Get signature data
     * 
     * This functions returns signature data that was receivied during ZRTP
     * processing. Refer to chapters 7.2ff.
     * 
     * @return Signature data in a byte array.
     */
    public byte[] getSignatureData() {
        return signatureData;
    }

    /**
     * Get length of signature data
     * 
     * This functions returns the length of signature data that was receivied
     * during ZRTP processing. Refer to chapters 6.7 and 8.2.
     * 
     * @return Length in bytes of the received signature data. The method
     *         returns zero if no signature data avilable.
     */
    public int getSignatureLength() {
        return signatureLength * 4;
    }

    /**
     * Emulate a Conf2Ack packet.
     * 
     * This method emulates a Conf2Ack packet. According to ZRTP specification
     * the first valid SRTP packet that the Initiator receives must switch on
     * secure mode. Refer to chapter 5.6 in the specificaton
     * 
     */
    public void conf2AckSecure() {
        if (stateEngine != null) {
            ZrtpStateClass.Event ev = stateEngine.new Event(ZrtpStateClass.EventDataType.ZrtpPacket,
                            zrtpConf2Ack.getHeaderBase());
            stateEngine.processEvent(ev);
        }
    }

    /**
     * Get other party's ZID (ZRTP Identifier) data
     * 
     * This functions returns the other party's ZID that was receivied during
     * ZRTP processing.
     * 
     * The ZID data can be retrieved after ZRTP receive the first Hello packet
     * from the other party. The application may call this method for example
     * during SAS processing in showSAS(...) user callback method.
     * 
     * @return the ZID data as byte array.
     */

    public byte[] getPeerZid() {
        byte[] ret = new byte[ZidRecord.IDENTIFIER_LENGTH];
        System.arraycopy(peerZid, 0, ret, 0, ZidRecord.IDENTIFIER_LENGTH);
        return ret;
    }

    /**
     * Get remaining time before a "ZRTP not supported by other party" is reported.
     * 
     * This function calls the protocol state engine to determine how many time is left
     * in ZRTP's discovery phase (Hello phase).
     * 
     * @return Time left in milliseconds.
     */
    public long getTimeoutValue() {
        if(stateEngine != null) {
            return stateEngine.getTimeoutValue();
        }

        return -1;
    }

    /**
     * Get number of supported ZRTP protocol versions.
     *
     * @return the number of supported ZRTP protocol versions.
     */
    public int getNumberSupportedVersions() {
        return SUPPORTED_ZRTP_VERSIONS;
    }

    /**
     * Get negotiated ZRTP protocol version.
     *
     * @return the integer representation of the negotiated ZRTP protocol version.
     */
    public int getCurrentProtocolVersion() {
        return currentHelloPacket.getVersionInt();
    }

    /*
     * The following methods are helper functions for ZrtpStateClass.
     * ZrtpStateClass calls them to prepare packets, send data, report problems,
     * etc.
     */
    /**
     * Send a ZRTP packet.
     * 
     * The state engines calls this method to send a packet via the RTP stack.
     * 
     * @param packet
     *            Points to the ZRTP packet.
     * @return false if sending failed, true if packet was send
     */
    protected boolean sendPacketZRTP(ZrtpPacketBase packet) {
        // the packetBuffer reflects the real size of the data including the CRC
        // field.
        return (packet != null && callback.sendDataZRTP(packet.getHeaderBase()));
    }

    /**
     * Activate a Timer using the host callback.
     * 
     * @param tm
     *            The time in milliseconds.
     * @return zero if activation failed, one if timer was activated
     */
    protected int activateTimer(int tm) {
        return callback.activateTimer(tm);
    }

    /**
     * Cancel the active Timer using the host callback.
     * 
     * @return zero if activation failed, one if timer was activated
     */
    protected int cancelTimer() {
        return callback.cancelTimer();
    }

    /**
     * Prepare a Hello packet.
     * 
     * Just take the preinitialized Hello packet and return it. No further
     * processing required.
     * 
     * @return A pointer to the initialized Hello packet.
     */
    protected ZrtpPacketHello prepareHello() {
        return currentHelloPacket;
    }

    /**
     * Prepare a HelloAck packet.
     * 
     * Just take the preinitialized HelloAck packet and return it. No further
     * processing required.
     * 
     * @return A pointer to the initialized HelloAck packet.
     */
    protected ZrtpPacketHelloAck prepareHelloAck() {
        return zrtpHelloAck;
    }

    /**
     * Prepare a Commit packet.
     * 
     * We have received a Hello packet from our peer. Check the offers it makes
     * to us and select the most appropriate. Using the selected values prepare
     * a Commit packet and return it to protocol state engine.
     * 
     * @param hello
     *            Points to the received Hello packet
     * @return A pointer to the prepared Commit packet
     */
    protected ZrtpPacketCommit prepareCommit(ZrtpPacketHello hello, ZrtpCodes.ZrtpErrorCodes[] errMsg) {

        if (!hello.isLengthOk()) {
            errMsg[0] = ZrtpCodes.ZrtpErrorCodes.CriticalSWError;
            return null;
        }
        // Save our peer's ZRTP id
        peerZid = hello.getZid();
        // peers have the same ZID?
        if (ZrtpUtils.byteArrayCompare(peerZid, zid, ZidRecord.IDENTIFIER_LENGTH) == 0) {
            errMsg[0] = ZrtpCodes.ZrtpErrorCodes.EqualZIDHello;
            return null;
        }
        System.arraycopy(hello.getH3(), 0, peerH3, 0, ZrtpPacketBase.HASH_IMAGE_SIZE);

        // calculate hash over the received Hello packet, it's the peer's hello hash.
        //
        // getHeaderBase() returns the full packetBuffer array. The length of
        // this array includes the CRC which is not part of the helloHash.
        // Thus compute digest only for the real message length.
        // Use implicit hash algo
        int helloLen = hello.getLength() * ZrtpPacketBase.ZRTP_WORD_SIZE;
        hashFunctionImpl.update(hello.getHeaderBase(), 0, helloLen);
        hashFunctionImpl.doFinal(peerHelloHash, 0);
        peerHelloVersion = hello.getVersion();

        sendInfo(ZrtpCodes.MessageSeverity.Info, EnumSet.of(ZrtpCodes.InfoCodes.InfoHelloReceived));

        /*
         * The Following section extracts the algorithm from the Hello packet. Always the best possible (offered)
         * algorithms are used. If the received Hello does not contain algo specifiers or offers only unsupported
         * (optional) algos then replace these with mandatory algos and put them into the Commit packet. Refer to the
         * findBest*() functions.
         */
        sasType = hello.findBestSASType(configureAlgos);

        if (!multiStream) {
            pubKey = hello.findBestPubkey(configureAlgos);
            hash = hello.getSelectedHash();
            if (hash == null) {
                errMsg[0] = ZrtpCodes.ZrtpErrorCodes.UnsuppHashType;
                return null;               
            }
            cipher = hello.getSelectedCipher();
            if (cipher == null)
                cipher = hello.findBestCipher(configureAlgos, pubKey);
            authLength = hello.findBestAuthLen(configureAlgos);
            multiStreamAvailable = hello.checkMultiStream();
        }
        else {
            if (hello.checkMultiStream()) {
                return prepareCommitMultiStream(hello);
            }
            else {
                // we are in multi-stream but peer does not offer multi-stream
                // return error message Unspported PK, we require Mult in the
                // Hello
                errMsg[0] = ZrtpCodes.ZrtpErrorCodes.UnsuppPKExchange;
                return null;
            }
        }
        setNegotiatedHash(hash);

        if (!fillPubKey()) {
            errMsg[0] = ZrtpCodes.ZrtpErrorCodes.CriticalSWError;
            return null;
        }
        sendInfo(ZrtpCodes.MessageSeverity.Info, EnumSet.of(ZrtpCodes.InfoCodes.InfoCommitDHGenerated));

        /*
         * Prepare our DHPart2 packet here. Required to compute HVI. If we stay in Initiator role then we reuse this
         * packet later in prepareDHPart2(). To create this DH packet we have to compute the retained secret ids first.
         * Thus get our peer's retained secret data first.
         */
        zidRec = ZidFile.getInstance().getRecord(peerZid);

        // Compute the Initator's and Responder's retained secret ids.
        computeSharedSecretSet();

        // Check if a PBX application set the MitM flag.
        if (hello.isMitmMode()) {
            mitmSeen = true;
        }
        // Flag to record that fact that we have a MitM key of the other peer.
        peerIsEnrolled = zidRec.isMITMKeyAvailable();

        // Check for sign SAS flag and remember it.
        signSasSeen = hello.isSasSign();
        // Construct a DHPart2 message (Initiator's DH message). This packet
        // is required to compute the HVI (Hash Value Initiator), refer to
        // chapter 5.4.1.1.

        // Fill the values in the DHPart2 packet
        zrtpDH2.setPubKeyType(pubKey);
        zrtpDH2.setMessageType(ZrtpConstants.DHPart2Msg);
        zrtpDH2.setRs1Id(rs1IDi);
        zrtpDH2.setRs2Id(rs2IDi);
        zrtpDH2.setAuxSecretId(auxSecretIDi);
        zrtpDH2.setPbxSecretId(pbxSecretIDi);
        zrtpDH2.setPv(pubKeyBytes);
        zrtpDH2.setH1(H1);

        // Compute HMAC over Hello, excluding the HMAC field (2*ZTP_WORD_SIZE)
        // and store in DH2
        byte[] hmac = computeMsgHmac(H0, zrtpDH2);
        zrtpDH2.setHMAC(hmac);

        // Compute the HVI, refer to chapter 5.4.1.1 of the specification
        computeHvi(zrtpDH2, hello);

        zrtpCommit.setZid(zid);
        zrtpCommit.setHashType(hash.name);
        zrtpCommit.setCipherType(cipher.name);
        zrtpCommit.setAuthLen(authLength.name);
        zrtpCommit.setPubKeyType(pubKey.name);
        zrtpCommit.setSasType(sasType.name);
        zrtpCommit.setHvi(hvi);
        zrtpCommit.setH2(H2);

        // Compute HMAC over Commit, excluding the HMAC field (2*ZTP_WORD_SIZE)
        // and store in Hello
        hmac = computeMsgHmac(H1, zrtpCommit);
        zrtpCommit.setHMAC(hmac);

        // hash first messages to produce overall message hash
        // First the Responder's Hello message, second the Commit
        // (always Initator's)
        // Use negotiated hash algo.
        hashCtxFunction.update(hello.getHeaderBase(), 0, hello.getLength() * ZrtpPacketBase.ZRTP_WORD_SIZE);
        final int len = zrtpCommit.getLength() * ZrtpPacketBase.ZRTP_WORD_SIZE;
        hashCtxFunction.update(zrtpCommit.getHeaderBase(), 0, len);

        // store Hello data temporarily until we can check HMAC after receiving
        // Commit as
        // Responder or DHPart1 as Initiator
        storeMsgTemp(hello);

        return zrtpCommit;
    }

    private boolean fillPubKey() {
        // Generate the standard DH data and keys according to the selected
        // DH algorithm

        if (pubKey == ZrtpConstants.SupportedPubKeys.DH2K || pubKey == ZrtpConstants.SupportedPubKeys.DH3K) {

            dhKeyPair = pubKey.keyPairGen.generateKeyPair();
            pubKeyBytes = ((DHPublicKeyParameters) dhKeyPair.getPublic()).getY().toByteArray();

            if (pubKeyBytes.length != pubKey.pubKeySize) {
                if ((pubKeyBytes = adjustBigBytes(pubKeyBytes, pubKey.pubKeySize)) == null)
                    return false;
            }
        }
        // Here produce the ECDH stuff
        else if (pubKey == ZrtpConstants.SupportedPubKeys.EC25
                || pubKey == ZrtpConstants.SupportedPubKeys.EC38
                || pubKey == ZrtpConstants.SupportedPubKeys.E255) {

            ecKeyPair = pubKey.keyPairGen.generateKeyPair();
            byte[] encoded = ((ECPublicKeyParameters) ecKeyPair.getPublic()).getQ()
                    .getEncoded(pubKey == ZrtpConstants.SupportedPubKeys.E255);
            pubKeyBytes = new byte[pubKey.pubKeySize];
            System.arraycopy(encoded, 1, pubKeyBytes, 0, pubKey.pubKeySize);
        }
        else {
            return false;
        }
        return true;
    }

    protected ZrtpPacketCommit prepareCommitMultiStream(ZrtpPacketHello hello) {
        // This is the Multi-Stream NONCE size
        hvi = new byte[ZrtpPacketBase.ZRTP_WORD_SIZE * 4];
        secRand.nextBytes(hvi);

        zrtpCommit.setZid(zid);
        zrtpCommit.setHashType(hash.name);
        zrtpCommit.setCipherType(cipher.name);
        zrtpCommit.setAuthLen(authLength.name);
        zrtpCommit.setPubKeyType(ZrtpConstants.SupportedPubKeys.MULT.name);
        zrtpCommit.setSasType(sasType.name);
        zrtpCommit.setNonce(hvi);
        zrtpCommit.setH2(H2);

        int len = zrtpCommit.getLength() * ZrtpPacketBase.ZRTP_WORD_SIZE;

        // Compute HMAC over Commit, excluding the HMAC field (2*ZTP_WORD_SIZE)
        // and store in Hello
        byte[] hmac = computeMsgHmac(H1, zrtpCommit);
        zrtpCommit.setHMACMulti(hmac);

        // hash first messages to produce overall message hash
        // First the Responder's Hello message, second the Commit
        // (always Initator's). Use negotiated Hash algo.
        hashCtxFunction.update(hello.getHeaderBase(), 0, hello.getLength() * ZrtpPacketBase.ZRTP_WORD_SIZE);
        hashCtxFunction.update(zrtpCommit.getHeaderBase(), 0, len);

        // store Hello data temporarily until we can check HMAC after receiving
        // Commit as Responder or DHPart1 as Initiator
        storeMsgTemp(hello);

        // calculate hash over the received Hello packet - is peer's hello hash.
        //
        // getHeaderBase() returns the full packetBuffer array. The length of
        // this array includes the CRC which is not part of the helloHash.
        // Thus compute digest only for the real message length.
        // Use implicit hash algo
        int helloLen = hello.getLength() * ZrtpPacketBase.ZRTP_WORD_SIZE;
        hashFunctionImpl.update(hello.getHeaderBase(), 0, helloLen);
        hashFunctionImpl.doFinal(peerHelloHash, 0);
        peerHelloVersion = hello.getVersion();

        return zrtpCommit;
    }

    /**
     * Prepare the DHPart1 packet.
     * 
     * This method prepares a DHPart1 packet. The input to the method is always
     * a Commit packet received from the peer. Also we are in the role of the
     * Responder.
     * 
     * When we receive a Commit packet we get the selected ciphers, hashes, etc
     * and cross-check if this is ok. Then we need to initialize a set of DH
     * keys according to the selected cipher. Using this data we prepare our
     * DHPart1 packet.
     */
    protected ZrtpPacketDHPart prepareDHPart1(ZrtpPacketCommit commit, ZrtpCodes.ZrtpErrorCodes[] errMsg) {
        sendInfo(ZrtpCodes.MessageSeverity.Info, EnumSet.of(ZrtpCodes.InfoCodes.InfoRespCommitReceived));

        if (!commit.isLengthOk()) {
            errMsg[0] = ZrtpCodes.ZrtpErrorCodes.CriticalSWError;
            return null;
        }

        // The following code check the hash chain according chapter 10 to
        // detect false ZRTP packets.
        // Use implicit hash algo.
        System.arraycopy(commit.getH2(), 0, peerH2, 0, ZrtpPacketBase.HASH_IMAGE_SIZE);

        byte[] tmpH3 = new byte[ZrtpConstants.MAX_DIGEST_LENGTH];
        hashFunctionImpl.update(peerH2, 0, ZrtpPacketBase.HASH_IMAGE_SIZE);
        hashFunctionImpl.doFinal(tmpH3, 0);

        if (ZrtpUtils.byteArrayCompare(tmpH3, peerH3, ZrtpPacketBase.HASH_IMAGE_SIZE) != 0) {
            errMsg[0] = ZrtpCodes.ZrtpErrorCodes.IgnorePacket;
            return null;
        }

        // Check HMAC of previous Hello packet stored in temporary buffer. The
        // HMAC key of peer's Hello packet is peer's H2 that is contained in the
        // Commit packet. Refer to chapter 9.1.
        if (!checkMsgHmac(peerH2)) {
            sendInfo(ZrtpCodes.MessageSeverity.Severe, EnumSet.of(ZrtpCodes.SevereCodes.SevereHelloHMACFailed));
            errMsg[0] = ZrtpCodes.ZrtpErrorCodes.CriticalSWError;
            return null;
        }

        // check if we support the commited Cipher type
        cipher = commit.getCipher();
        if (cipher == null) {
            errMsg[0] = ZrtpCodes.ZrtpErrorCodes.UnsuppCiphertype;
            return null;
        }

        // check if we support the commited Authentication length
        authLength = commit.getAuthlen();
        if (authLength == null) {
            errMsg[0] = ZrtpCodes.ZrtpErrorCodes.UnsuppSRTPAuthTag;
            return null;
        }

        ZrtpConstants.SupportedHashes newHash = commit.getHash();
        if (newHash == null) {
            errMsg[0] = ZrtpCodes.ZrtpErrorCodes.UnsuppHashType;
            return null;
        }
        // check if the peer's commited hash is the same that we used when
        // preparing our commit packet. If not do the necessary resets and
        // recompute some data.
        if (newHash != hash) {
            hash = newHash;
            setNegotiatedHash(hash);
            computeSharedSecretSet(); // Re-compute the Initator's and Responder's RS.
        }

        // check if we support the commited pub key type (check here for
        // different pubkey - maybe we need to create a new own public
        // key here if the peer's commit differs with respect to our
        // preparation done in prepareCommit(...)
        ZrtpConstants.SupportedPubKeys commitPubKey = commit.getPubKey();
        if (commitPubKey == null) {
            errMsg[0] = ZrtpCodes.ZrtpErrorCodes.UnsuppPKExchange;
            return null;
        }
        /*
         * Saftey check if we can resuse the DH key pair. According to the public key algo check this is usually the
         * case. If we cannot reuse it refill the pubkey.
         */
        if (commitPubKey != pubKey) {
            pubKey = commitPubKey;
            if (!fillPubKey()) {
                errMsg[0] = ZrtpCodes.ZrtpErrorCodes.CriticalSWError;
                return null;
            }
        }
        // if ECDH 384 is in use then hash must be SHA 384 or better
        if (pubKey == ZrtpConstants.SupportedPubKeys.EC38 && hash != ZrtpConstants.SupportedHashes.S384) {
            errMsg[0] = ZrtpCodes.ZrtpErrorCodes.UnsuppHashType;
            return null;
        }
        // check if we support the commited SAS type
        sasType = commit.getSas();
        if (sasType == null) {
            errMsg[0] = ZrtpCodes.ZrtpErrorCodes.UnsuppSASScheme;
            return null;
        }
        sendInfo(ZrtpCodes.MessageSeverity.Info, EnumSet.of(ZrtpCodes.InfoCodes.InfoDH1DHGenerated));

        // Setup a DHPart1 packet.
        zrtpDH1.setPubKeyType(pubKey);
        zrtpDH1.setMessageType(ZrtpConstants.DHPart1Msg);
        zrtpDH1.setRs1Id(rs1IDr);
        zrtpDH1.setRs2Id(rs2IDr);
        zrtpDH1.setAuxSecretId(auxSecretIDr);
        zrtpDH1.setPbxSecretId(pbxSecretIDr);
        zrtpDH1.setPv(pubKeyBytes);
        zrtpDH1.setH1(H1);

        // Compute HMAC over DHPart1, excluding the HMAC field (2*ZTP_WORD_SIZE)
        // and store in DHPart1
        byte[] hmac = computeMsgHmac(H0, zrtpDH1);
        zrtpDH1.setHMAC(hmac);

        // We are definitly responder. Save the peer's hvi for later compare.
        myRole = ZrtpCallback.Role.Responder;
        peerHvi = commit.getHvi();

        // We are responder. Reset the message SHA context. Use negotiated
        // hash algo.
        hashCtxFunction.reset();

        // Hash messages to produce overall message hash:
        // First the Responder's (my) Hello message, second the Commit
        // (always Initator's), then the DH1 message (which is always a
        // Responder's message)
        hashCtxFunction.update(currentHelloPacket.getHeaderBase(), 0, currentHelloPacket.getLength() * ZrtpPacketBase.ZRTP_WORD_SIZE);
        hashCtxFunction.update(commit.getHeaderBase(), 0, commit.getLength() * ZrtpPacketBase.ZRTP_WORD_SIZE);
        hashCtxFunction.update(zrtpDH1.getHeaderBase(), 0, zrtpDH1.getLength() * ZrtpPacketBase.ZRTP_WORD_SIZE);

        // store Commit data temporarily until we can check HMAC after receiving
        // DHPart2
        storeMsgTemp(commit);

        return zrtpDH1;
    }

    /**
     * Prepare the DHPart2 packet.
     * 
     * This method returns the prepared DHPart2 packet. The input to the method
     * is always a DHPart1 packet received from the peer. Our peer sends the DH1Part as
     * response to our Commit packet. Thus we are in the role of the Initiator.
     * The method uses the DHPart1 data to create the Initiator's secrets.
     * 
     */
    protected ZrtpPacketDHPart prepareDHPart2(ZrtpPacketDHPart dhPart1, ZrtpCodes.ZrtpErrorCodes[] errMsg) {
        sendInfo(ZrtpCodes.MessageSeverity.Info, EnumSet.of(ZrtpCodes.InfoCodes.InfoInitDH1Received));

        if (!dhPart1.isLengthOk()) {
            errMsg[0] = ZrtpCodes.ZrtpErrorCodes.CriticalSWError;
            return null;
        }
        // Because we are initiator the protocol engine didn't receive Commit
        // thus could not store a peer's H2. A two step hash is required to
        // re-compute H3. Then compare with peer's H3 from peer's Hello packet.
        // Use implicit hash algo
        hashFunctionImpl.update(dhPart1.getH1(), 0,
                ZrtpPacketBase.HASH_IMAGE_SIZE); // Compute peer's H2
        hashFunctionImpl.doFinal(peerH2, 0);

        byte[] tmpHash = new byte[ZrtpConstants.MAX_DIGEST_LENGTH];
        // Compute peer's H3 (tmpHash)
        hashFunctionImpl.update(peerH2, 0, ZrtpPacketBase.HASH_IMAGE_SIZE);
        hashFunctionImpl.doFinal(tmpHash, 0);

        if (ZrtpUtils.byteArrayCompare(tmpHash, peerH3,
                ZrtpPacketBase.HASH_IMAGE_SIZE) != 0) {
            errMsg[0] = ZrtpCodes.ZrtpErrorCodes.IgnorePacket;
            return null;
        }

        // Check HMAC of previous Hello packet stored in temporary buffer. The
        // HMAC key of the Hello packet is peer's H2 that was computed above.
        // Refer to chapter 9.1 and chapter 10.
        if (!checkMsgHmac(peerH2)) {
            sendInfo(ZrtpCodes.MessageSeverity.Severe, EnumSet.of(ZrtpCodes.SevereCodes.SevereHelloHMACFailed));
            errMsg[0] = ZrtpCodes.ZrtpErrorCodes.CriticalSWError;
            return null;
        }
        // get and check Responder's public value, see chap. 5.4.3 in the spec
        byte[] pvrBytes = dhPart1.getPv();
        int dhSize;

        if (pubKey == ZrtpConstants.SupportedPubKeys.DH2K || pubKey == ZrtpConstants.SupportedPubKeys.DH3K) {

            // generate the resonpder's public key from the pvr data and the key
            // specs, then compute the shared secret.
            BigInteger pvrBigInt = new BigInteger(1, pvrBytes);
            if (!checkPubKey(pvrBigInt, pubKey)) {
                errMsg[0] = ZrtpCodes.ZrtpErrorCodes.DHErrorWrongPV;
                return null;
            }
            pubKey.dhContext.init(dhKeyPair.getPrivate());
            DHPublicKeyParameters pvr = new DHPublicKeyParameters(pvrBigInt, pubKey.specDh);
            dhSize = pubKey.pubKeySize;
            BigInteger bi = pubKey.dhContext.calculateAgreement(pvr);
            DHss = bi.toByteArray();
        }
        // Here produce the ECDH stuff
        else if (pubKey == ZrtpConstants.SupportedPubKeys.EC25
                || pubKey == ZrtpConstants.SupportedPubKeys.EC38
                || pubKey == ZrtpConstants.SupportedPubKeys.E255) {

            byte[] encoded = new byte[pvrBytes.length + 1];
            encoded[0] = (byte)(pubKey == ZrtpConstants.SupportedPubKeys.E255
                    ? 0x02   // compressed, i.e. X only
                    : 0x04); // uncompressed
            System.arraycopy(pvrBytes, 0, encoded, 1, pvrBytes.length);
            ECPoint point = pubKey.curve.decodePoint(encoded);
            dhSize = pubKey == (ZrtpConstants.SupportedPubKeys.E255) ? pubKey.pubKeySize: pubKey.pubKeySize / 2;
            pubKey.dhContext.init(ecKeyPair.getPrivate());

            ECDomainParameters ecdParam = ((ECKeyParameters) ecKeyPair.getPublic()).getParameters();
            BigInteger bi = pubKey.dhContext.calculateAgreement(new ECPublicKeyParameters(point, ecdParam));
            DHss = bi.toByteArray();
        }
        else {
            errMsg[0] = ZrtpCodes.ZrtpErrorCodes.CriticalSWError;
            return null;
        }
        if (DHss.length != dhSize) {
            if ((DHss = adjustBigBytes(DHss, dhSize)) == null) {
                errMsg[0] = ZrtpCodes.ZrtpErrorCodes.CriticalSWError;
                return null;
            }
        }
        myRole = ZrtpCallback.Role.Initiator;

        // We are Inititaor: the Responder's Hello and the Initiator's (our)
        // Commit are already hashed in the context. Now hash the
        // Responder's DH1 and then the Initiator's (our) DH2 in that order.
        // Use negotiated hash algo.
        hashCtxFunction.update(dhPart1.getHeaderBase(), 0, dhPart1.getLength() * ZrtpPacketBase.ZRTP_WORD_SIZE);
        hashCtxFunction.update(zrtpDH2.getHeaderBase(), 0, zrtpDH2.getLength() * ZrtpPacketBase.ZRTP_WORD_SIZE);

        // Compute the message Hash
        hashCtxFunction.doFinal(messageHash, 0);
        hashCtxFunction = null;

        // Now compute the S0, all dependend keys and the new RS1. The functions 
        // also performs sign SAS callback if it's active.
        generateKeysInitiator(dhPart1);

        // store DHPart1 data temporarily until we can check HMAC after
        // receiving Confirm1
        storeMsgTemp(dhPart1);
        return zrtpDH2;
    }

    /**
     * Prepare the Confirm1 packet.
     * 
     * This method prepare the Confirm1 packet. The input to this method is the
     * DHPart2 packect received from our peer. The peer sends the DHPart2 packet
     * as response of our DHPart1. Here we are in the role of the Responder.
     * 
     * The method uses the data of the DHPart2 packet to create the responder's
     * secrets. 
     * 
     */
    protected ZrtpPacketConfirm prepareConfirm1(ZrtpPacketDHPart dhPart2, ZrtpCodes.ZrtpErrorCodes[] errMsg) {

        sendInfo(ZrtpCodes.MessageSeverity.Info, EnumSet.of(ZrtpCodes.InfoCodes.InfoRespDH2Received));

        if (!dhPart2.isLengthOk()) {
            errMsg[0] = ZrtpCodes.ZrtpErrorCodes.CriticalSWError;
            return null;
        }
        // Because we are responder we received a Commit and stored its H2.
        // Now re-compute H2 from received H1 and compare with stored peer's H2.
        byte[] tmpHash = new byte[ZrtpConstants.MAX_DIGEST_LENGTH];
        hashFunctionImpl.update(dhPart2.getH1(), 0, ZrtpPacketBase.HASH_IMAGE_SIZE);
        hashFunctionImpl.doFinal(tmpHash, 0);

        if (ZrtpUtils.byteArrayCompare(tmpHash, peerH2, ZrtpPacketBase.HASH_IMAGE_SIZE) != 0) {
            errMsg[0] = ZrtpCodes.ZrtpErrorCodes.IgnorePacket;
            return null;
        }
        // Because we are responder re-compute my
        // hvi using my Hello packet and the Initiator's DHPart2 and compare
        // with hvi sent in commit packet. If it doesn't macht then a MitM
        // attack may have occured.
        computeHvi(dhPart2, currentHelloPacket);
        if (ZrtpUtils.byteArrayCompare(hvi, peerHvi, ZrtpPacketBase.HVI_SIZE) != 0) {
            errMsg[0] = ZrtpCodes.ZrtpErrorCodes.DHErrorWrongHVI;
            return null;
        }
        // Check HMAC of Commit packet stored in temporary buffer. The
        // HMAC key of the Commit packet is peer's H1 that is contained in.
        // DHPart2. Refer to chapter 9.1 and chapter 10.
        if (!checkMsgHmac(dhPart2.getH1())) {
            sendInfo(ZrtpCodes.MessageSeverity.Severe, EnumSet.of(ZrtpCodes.SevereCodes.SevereCommitHMACFailed));
            errMsg[0] = ZrtpCodes.ZrtpErrorCodes.CriticalSWError;
            return null;
        }
        // Get and check the Initiator's public value, see chap. 5.4.2 of the
        // spec
        byte[] pviBytes = dhPart2.getPv();
        int dhSize;

        if (pubKey == ZrtpConstants.SupportedPubKeys.DH2K || pubKey == ZrtpConstants.SupportedPubKeys.DH3K) {

            // generate the resonpder's public key from the pvi data and the key
            // specs, then compute the shared secret.
            BigInteger pviBigInt = new BigInteger(1, pviBytes);
            if (!checkPubKey(pviBigInt, pubKey)) {
                errMsg[0] = ZrtpCodes.ZrtpErrorCodes.DHErrorWrongPV;
                return null;
            }
            pubKey.dhContext.init(dhKeyPair.getPrivate());
            DHPublicKeyParameters pvi = new DHPublicKeyParameters(pviBigInt, pubKey.specDh);
            dhSize = pubKey.pubKeySize;
            BigInteger bi = pubKey.dhContext.calculateAgreement(pvi);
            DHss = bi.toByteArray();
        }
        // Here produce the ECDH stuff
        else if (pubKey == ZrtpConstants.SupportedPubKeys.EC25
                || pubKey == ZrtpConstants.SupportedPubKeys.EC38
                || pubKey == ZrtpConstants.SupportedPubKeys.E255) {

            byte[] encoded = new byte[pviBytes.length + 1];
            encoded[0] = (byte)(pubKey == ZrtpConstants.SupportedPubKeys.E255
                    ? 0x02   // compressed, i.e. X only
                    : 0x04); // uncompressed
            System.arraycopy(pviBytes, 0, encoded, 1, pviBytes.length);
            ECPoint pubPoint = pubKey.curve.decodePoint(encoded);
            dhSize = pubKey == (ZrtpConstants.SupportedPubKeys.E255) ? pubKey.pubKeySize: pubKey.pubKeySize / 2;
            pubKey.dhContext.init(ecKeyPair.getPrivate());

            ECDomainParameters ecdParam = ((ECKeyParameters) ecKeyPair.getPublic()).getParameters();
            BigInteger bi = pubKey.dhContext.calculateAgreement(new ECPublicKeyParameters(pubPoint, ecdParam));
            DHss = bi.toByteArray();
        }
        else {
            errMsg[0] = ZrtpCodes.ZrtpErrorCodes.CriticalSWError;
            return null;
        }
        if (DHss.length != dhSize) {
            if ((DHss = adjustBigBytes(DHss, dhSize)) == null) {
                errMsg[0] = ZrtpCodes.ZrtpErrorCodes.CriticalSWError;
                return null;
            }
        }
        // Hash the Initiator's DH2 into the message Hash (other messages
        // already prepared, see method prepareDHPart1().
        hashCtxFunction.update(dhPart2.getHeaderBase(), 0, dhPart2.getLength() * ZrtpPacketBase.ZRTP_WORD_SIZE);
        hashCtxFunction.doFinal(messageHash, 0);
        hashCtxFunction = null;

        /*
         * The expected shared secret Ids were already computed when we built the DHPart1 packet. Generate s0, all
         * depended keys, and the new RS1 value for the ZID record. The functions also performs sign SAS callback if
         * it's active.
         */
        generateKeysResponder(dhPart2);

        // Fill in Confirm1 packet.
        zrtpConfirm1.setMessageType(ZrtpConstants.Confirm1Msg);

        // Check if user verfied the SAS in a previous call and thus verfied
        // the retained secret. Forward this information to our peer. Don't set
        // the verified flag if paranoidMode is true.
        if (zidRec.isSasVerified() && !paranoidMode) {
            zrtpConfirm1.setSASFlag();
        }
        zrtpConfirm1.setExpTime(0xFFFFFFFF);
        zrtpConfirm1.setIv(randomIV);
        zrtpConfirm1.setHashH0(H0);

        // if this run at PBX user agent enrollment service then set flag in confirm
        // packet and store the MitM key
        if (enrollmentMode) {
            computePBXSecret();
            zrtpConfirm1.setPBXEnrollment();
            zidRec.setMiTMData(pbxSecretTmp);
        }

        // Encrypt and HMAC with Responder's key - we are Respondere here
        // see ZRTP specification chapter
        byte[] dataToSecure = zrtpConfirm1.getDataToSecure();
        try {
            cipher.cipher.init(true, new ParametersWithIV(new KeyParameter(zrtpKeyR, 0, cipher.keyLength), randomIV));
            int done = cipher.cipher.processBytes(dataToSecure, 0, dataToSecure.length, dataToSecure, 0);
            cipher.cipher.doFinal(dataToSecure, done);
        } catch (Exception e) {
            sendInfo(ZrtpCodes.MessageSeverity.Severe, EnumSet.of(ZrtpCodes.SevereCodes.SevereSecurityException));
            errMsg[0] = ZrtpCodes.ZrtpErrorCodes.CriticalSWError;
            return null;
        }
        byte[] confMac = computeHmac(hmacKeyR, hashLength, dataToSecure, dataToSecure.length);
        zrtpConfirm1.setDataToSecure(dataToSecure);
        zrtpConfirm1.setHmac(confMac);

        // store DHPart2 data temporarily until we can check HMAC after
        // receiving Confirm2
        storeMsgTemp(dhPart2);
        return zrtpConfirm1;
    }

    private byte[] adjustBigBytes(byte[] in, int size) {
        // adjust byte arry if we have a leading zero
        byte[] tmp;
        if (in.length > size && in[0] == 0) {
            tmp = new byte[in.length - 1];
            System.arraycopy(in, 1, tmp, 0, tmp.length);
            return tmp;
        }
        // Fill with zeros if too short.
        if (in.length < size) {
            int fill = size - in.length;
            tmp = new byte[size];
            System.arraycopy(in, 0, tmp, fill, size - fill);
            return tmp;
        }
        return null;
    }
    /*
     * At this point we are Responder.
     */
    protected ZrtpPacketConfirm prepareConfirm1MultiStream(ZrtpPacketCommit commit, ZrtpCodes.ZrtpErrorCodes[] errMsg) {

        sendInfo(ZrtpCodes.MessageSeverity.Info, EnumSet.of(ZrtpCodes.InfoCodes.InfoRespCommitReceived));

        if (!commit.isLengthOk()) {
            errMsg[0] = ZrtpCodes.ZrtpErrorCodes.CriticalSWError;
            return null;
        }
        // In multi stream mode we don't get a DH packt. Thus we need to
        // recompute the hash chain starting with Commit's H2.
        // Use the implicit hash algo
        System.arraycopy(commit.getH2(), 0, peerH2, 0, ZrtpPacketBase.HASH_IMAGE_SIZE);
        byte[] tmpH3 = new byte[ZrtpConstants.MAX_DIGEST_LENGTH];
        hashFunctionImpl.update(peerH2, 0, ZrtpPacketBase.HASH_IMAGE_SIZE);
        hashFunctionImpl.doFinal(tmpH3, 0);

        if (ZrtpUtils.byteArrayCompare(tmpH3, peerH3, ZrtpPacketBase.HASH_IMAGE_SIZE) != 0) {
            errMsg[0] = ZrtpCodes.ZrtpErrorCodes.IgnorePacket;
            return null;
        }
        // Check HMAC of previous Hello packet stored in temporary buffer. The
        // HMAC key of peer's Hello packet is peer's H2 that is contained in the
        // Commit packet. Refer to chapter 9.1.
        if (!checkMsgHmac(peerH2)) {
            sendInfo(ZrtpCodes.MessageSeverity.Severe, EnumSet.of(ZrtpCodes.SevereCodes.SevereCommitHMACFailed));
            errMsg[0] = ZrtpCodes.ZrtpErrorCodes.CriticalSWError;
            return null;
        }
        // check if we support the commited pub key type
        if (commit.getPubKey() != ZrtpConstants.SupportedPubKeys.MULT) {
            errMsg[0] = ZrtpCodes.ZrtpErrorCodes.UnsuppPKExchange;
            return null;
        }
        cipher = commit.getCipher();
        if (cipher == null) {
            errMsg[0] = ZrtpCodes.ZrtpErrorCodes.UnsuppCiphertype;
            return null;
        }
        // check if we support the commited Authentication length
        authLength = commit.getAuthlen();
        if (authLength == null) {
            errMsg[0] = ZrtpCodes.ZrtpErrorCodes.UnsuppSRTPAuthTag;
            return null;
        }
        ZrtpConstants.SupportedHashes newHash = commit.getHash();
        if (newHash == null) {
            errMsg[0] = ZrtpCodes.ZrtpErrorCodes.UnsuppHashType;
            return null;
        }
        if (newHash != hash) {
            hash = newHash;
            setNegotiatedHash(hash);
        }
        myRole = ZrtpCallback.Role.Responder;
        // We are responder. Reset message SHA context
        hashCtxFunction.reset();
        // Hash messages to produce overall message hash:
        // First the Responder's (my) Hello message, second the Commit
        // (always Initator's)
        hashCtxFunction.update(currentHelloPacket.getHeaderBase(), 0, currentHelloPacket.getLength() * ZrtpPacketBase.ZRTP_WORD_SIZE);
        hashCtxFunction.update(commit.getHeaderBase(), 0, commit.getLength() * ZrtpPacketBase.ZRTP_WORD_SIZE);
        hashCtxFunction.doFinal(messageHash, 0);
        hashCtxFunction = null;

        generateKeysMultiStream();

        // Fill in Confirm1 packet.
        zrtpConfirm1.setMessageType(ZrtpConstants.Confirm1Msg);
        zrtpConfirm1.setExpTime(0xFFFFFFFF);
        zrtpConfirm1.setIv(randomIV);
        zrtpConfirm1.setHashH0(H0);

        // Encrypt and HMAC with Responder's key - we are Respondere here
        // see ZRTP specification chapter xYxY
        byte[] dataToSecure = zrtpConfirm1.getDataToSecure();
        try {
            cipher.cipher.init(true, new ParametersWithIV(new KeyParameter(zrtpKeyR, 0, cipher.keyLength), randomIV));
            int done = cipher.cipher.processBytes(dataToSecure, 0, dataToSecure.length, dataToSecure, 0);
            cipher.cipher.doFinal(dataToSecure, done);
        } catch (Exception e) {
            sendInfo(ZrtpCodes.MessageSeverity.Severe, EnumSet.of(ZrtpCodes.SevereCodes.SevereSecurityException));
            errMsg[0] = ZrtpCodes.ZrtpErrorCodes.CriticalSWError;
            return null;
        }
        byte[] confMac = computeHmac(hmacKeyR, hashLength, dataToSecure, dataToSecure.length);
        zrtpConfirm1.setDataToSecure(dataToSecure);
        zrtpConfirm1.setHmac(confMac);

        // Store Commit data temporarily until we can check HMAC after receiving
        // Confirm2
        storeMsgTemp(commit);
        return zrtpConfirm1;
    }

    /**
     * Prepare the Confirm2 packet.
     * 
     * This method prepare the Confirm2 packet. The input to this method is the
     * Confirm1 packet received from our peer. The peer sends the Confirm1
     * packet as response of our DHPart2. Here we are in the role of the
     * Initiator
     */
    protected ZrtpPacketConfirm prepareConfirm2(ZrtpPacketConfirm confirm1, ZrtpCodes.ZrtpErrorCodes[] errMsg) {
        sendInfo(ZrtpCodes.MessageSeverity.Info, EnumSet.of(ZrtpCodes.InfoCodes.InfoInitConf1Received));

        if (!confirm1.isLengthOk()) {
            errMsg[0] = ZrtpCodes.ZrtpErrorCodes.CriticalSWError;
            return null;
        }
        // Use the Responder's keys here to decrypt because we are
        // Initiator and receive packets from Responder
        byte[] dataToSecure = confirm1.getDataToSecure();
        byte[] confMac = computeHmac(hmacKeyR, hashLength, dataToSecure, dataToSecure.length);

        if (ZrtpUtils.byteArrayCompare(confMac, confirm1.getHmac(), 2 * ZrtpPacketBase.ZRTP_WORD_SIZE) != 0) {
            errMsg[0] = ZrtpCodes.ZrtpErrorCodes.ConfirmHMACWrong;
            return null;
        }
        try {
            // Decrypting here
            cipher.cipher.init(false,
                            new ParametersWithIV(new KeyParameter(zrtpKeyR, 0, cipher.keyLength), confirm1.getIv()));
            int done = cipher.cipher.processBytes(dataToSecure, 0, dataToSecure.length, dataToSecure, 0);
            cipher.cipher.doFinal(dataToSecure, done);
        } catch (Exception e) {
            sendInfo(ZrtpCodes.MessageSeverity.Severe, EnumSet.of(ZrtpCodes.SevereCodes.SevereSecurityException));
            errMsg[0] = ZrtpCodes.ZrtpErrorCodes.CriticalSWError;
            return null;
        }
        confirm1.setDataToSecure(dataToSecure);

        // Check HMAC of DHPart1 packet stored in temporary buffer. The
        // HMAC key of the DHPart1 packet is peer's H0 that is contained in
        // Confirm1. Refer to chapter 9
        if (!checkMsgHmac(confirm1.getHashH0())) {
            sendInfo(ZrtpCodes.MessageSeverity.Severe, EnumSet.of(ZrtpCodes.SevereCodes.SevereDH1HMACFailed));
            errMsg[0] = ZrtpCodes.ZrtpErrorCodes.CriticalSWError;
            return null;
        }
        signatureLength = confirm1.getSignatureLength();
        if (signSasSeen && signatureLength > 0) {
            signatureData = confirm1.getSignatureData();
            callback.checkSASSignature(sasHash);
        }
        /*
         * The Confirm1 is ok, handle the Retained secret stuff and inform GUI about state.
         */
        // Did our peer verified the SAS during last session? Get its SAS verified flag.
        boolean sasFlag = confirm1.isSASFlag();

        // Our peer did not confirm the SAS in last session, thus reset our stored SAS 
        // flag too. Reset the flag also if paranoidMode is true.
        if (!sasFlag || paranoidMode) {
            zidRec.resetSasVerified();
        }

        // Now get the resulting SAS verified flag from current RS1 before setting a new RS1.
        // It's a combination of our SAS verfied flag and peer's verified flag. Only if both
        // were set (true) then sasFlag becomes true.
        sasFlag = zidRec.isSasVerified();

        // now we are ready to save the new RS1 which inherits the verified
        // flag from old RS1
        zidRec.setNewRs1(newRs1, -1);

        // now generate my Confirm2 message
        zrtpConfirm2.setMessageType(ZrtpConstants.Confirm2Msg);
        zrtpConfirm2.setHashH0(H0);

        if (sasFlag) {
            zrtpConfirm2.setSASFlag();
        }
        zrtpConfirm2.setExpTime(0xFFFFFFFF);
        zrtpConfirm2.setIv(randomIV);

        // Compute PBX secret if we are in enrollemnt mode (PBX user agent)
        // or enrollment was enabled at normal user agent and flag in confirm packet
        if (enrollmentMode || (enableMitmEnrollment && confirm1.isPBXEnrollment())) {
            computePBXSecret();

            // if this runs at PBX user agent enrollment service then set flag in confirm
            // packet and store the MitM key. The PBX user agent service always stores
            // its MitM key.
            if (enrollmentMode) {
                zrtpConfirm2.setPBXEnrollment();
                zidRec.setMiTMData(pbxSecretTmp);
            }
        }
        ZidFile.getInstance().saveRecord(zidRec);

        // Encrypt and HMAC with Initiator's key - we are Initiator here
        dataToSecure = zrtpConfirm2.getDataToSecure();

        try {
            cipher.cipher.init(true, new ParametersWithIV(new KeyParameter(zrtpKeyI, 0, cipher.keyLength), randomIV));
            int done = cipher.cipher.processBytes(dataToSecure, 0, dataToSecure.length, dataToSecure, 0);
            cipher.cipher.doFinal(dataToSecure, done);
        } catch (Exception e) {
            sendInfo(ZrtpCodes.MessageSeverity.Severe, EnumSet.of(ZrtpCodes.SevereCodes.SevereSecurityException));
            errMsg[0] = ZrtpCodes.ZrtpErrorCodes.CriticalSWError;
            return null;
        }
        confMac = computeHmac(hmacKeyI, hashLength, dataToSecure, dataToSecure.length);

        zrtpConfirm2.setDataToSecure(dataToSecure);
        zrtpConfirm2.setHmac(confMac);

        callback.srtpSecretsOn(cipher.readable + "/" + pubKey, SAS, sasFlag);

        // Ask for enrollment only if enabled via configuration and the
        // confirm packet contains the enrollment flag. The enrolling user
        // agent stores the MitM key only if the user accepts the enrollment
        // request.
        if (enableMitmEnrollment && confirm1.isPBXEnrollment()) {
            callback.zrtpAskEnrollment(ZrtpCodes.InfoEnrollment.EnrollmentRequest);
        }
        return zrtpConfirm2;
    }

    /*
     * At this point we are Initiator.
     */
    /**
     * @param confirm1 The ZRTP confirm1 packet
     * @param errMsg Arry to return an error code
     * @return a ZRTP confirm packet, here a confirm2
     */
    protected ZrtpPacketConfirm prepareConfirm2MultiStream(ZrtpPacketConfirm confirm1, ZrtpCodes.ZrtpErrorCodes[] errMsg) {

        // check Confirm1 packet using the keys
        // prepare Confirm2 packet
        // don't update SAS, RS
        sendInfo(ZrtpCodes.MessageSeverity.Info, EnumSet.of(ZrtpCodes.InfoCodes.InfoInitConf1Received));

        if (!confirm1.isLengthOk()) {
            errMsg[0] = ZrtpCodes.ZrtpErrorCodes.CriticalSWError;
            return null;
        }
        hashCtxFunction.doFinal(messageHash, 0);
        hashCtxFunction = null;

        myRole = ZrtpCallback.Role.Initiator;

        generateKeysMultiStream();

        // Use the Responder's keys here to decrypt because we are
        // Initiator and receive packets from Responder
        byte[] dataToSecure = confirm1.getDataToSecure();
        byte[] confMac = computeHmac(hmacKeyR, hashLength, dataToSecure, dataToSecure.length);

        if (ZrtpUtils.byteArrayCompare(confMac, confirm1.getHmac(), 2 * ZrtpPacketBase.ZRTP_WORD_SIZE) != 0) {
            errMsg[0] = ZrtpCodes.ZrtpErrorCodes.ConfirmHMACWrong;
            return null;
        }
        try {
            // Decrypting here
            cipher.cipher.init(false,
                            new ParametersWithIV(new KeyParameter(zrtpKeyR, 0, cipher.keyLength), confirm1.getIv()));
            int done = cipher.cipher.processBytes(dataToSecure, 0, dataToSecure.length, dataToSecure, 0);
            cipher.cipher.doFinal(dataToSecure, done);
        } catch (Exception e) {
            sendInfo(ZrtpCodes.MessageSeverity.Severe, EnumSet.of(ZrtpCodes.SevereCodes.SevereSecurityException));
            errMsg[0] = ZrtpCodes.ZrtpErrorCodes.CriticalSWError;
            return null;
        }
        confirm1.setDataToSecure(dataToSecure);

        // Because we are initiator the protocol engine didn't receive Commit
        // and because we are using multi-stream mode here we also did not
        // receive a DHPart1 and thus could not store a responder's H2 or H1.
        // A two step hash is required to re-compute H1 and H2.
        // Use implicit hash algo
        byte[] tmpHash = new byte[ZrtpConstants.MAX_DIGEST_LENGTH];
        hashFunctionImpl.update(confirm1.getHashH0(), 0, ZrtpPacketBase.HASH_IMAGE_SIZE); // Compute peer's H1 in
                                                                                          // tmpHash
        hashFunctionImpl.doFinal(tmpHash, 0);
        // Compute peer's H2
        hashFunctionImpl.update(tmpHash, 0, ZrtpPacketBase.HASH_IMAGE_SIZE);
        hashFunctionImpl.doFinal(peerH2, 0);

        // Check HMAC of previous Hello packet stored in temporary buffer. The
        // HMAC key of the Hello packet is peer's H2 that was computed above.
        // Refer to chapter 9.1 and chapter 10.
        if (!checkMsgHmac(peerH2)) {
            sendInfo(ZrtpCodes.MessageSeverity.Severe, EnumSet.of(ZrtpCodes.SevereCodes.SevereHelloHMACFailed));
            errMsg[0] = ZrtpCodes.ZrtpErrorCodes.CriticalSWError;
            return null;
        }
        // now generate my Confirm2 message
        zrtpConfirm2.setMessageType(ZrtpConstants.Confirm2Msg);
        zrtpConfirm2.setHashH0(H0);
        zrtpConfirm2.setExpTime(0xFFFFFFFF);
        zrtpConfirm2.setIv(randomIV);

        // Encrypt and HMAC with Initiator's key - we are Initiator here
        dataToSecure = zrtpConfirm2.getDataToSecure();

        try {
            cipher.cipher.init(true, new ParametersWithIV(new KeyParameter(zrtpKeyI, 0, cipher.keyLength), randomIV));
            int done = cipher.cipher.processBytes(dataToSecure, 0, dataToSecure.length, dataToSecure, 0);
            cipher.cipher.doFinal(dataToSecure, done);
        } catch (Exception e) {
            sendInfo(ZrtpCodes.MessageSeverity.Severe, EnumSet.of(ZrtpCodes.SevereCodes.SevereSecurityException));
            errMsg[0] = ZrtpCodes.ZrtpErrorCodes.CriticalSWError;
            return null;
        }
        confMac = computeHmac(hmacKeyI, hashLength, dataToSecure, dataToSecure.length);
        zrtpConfirm2.setDataToSecure(dataToSecure);
        zrtpConfirm2.setHmac(confMac);

        // Inform GUI about security state, don't show SAS and its state
        callback.srtpSecretsOn(cipher.readable, null, true);
        return zrtpConfirm2;
    }

    /**
     * Prepare the Conf2Ack packet.
     * 
     * This method prepare the Conf2Ack packet. The input to this method is the
     * Confirm2 packet received from our peer. The peer sends the Confirm2
     * packet as response of our Confirm1. Here we are in the role of the
     * Initiator
     */
    protected ZrtpPacketConf2Ack prepareConf2Ack(ZrtpPacketConfirm confirm2, ZrtpCodes.ZrtpErrorCodes[] errMsg) {
        sendInfo(ZrtpCodes.MessageSeverity.Info, EnumSet.of(ZrtpCodes.InfoCodes.InfoRespConf2Received));

        if (!confirm2.isLengthOk()) {
            errMsg[0] = ZrtpCodes.ZrtpErrorCodes.CriticalSWError;
            return null;
        }
        // Use the Initiator's keys here because we are Responder here and
        // reveice packets from Initiator
        byte[] dataToSecure = confirm2.getDataToSecure();
        byte[] confMac = computeHmac(hmacKeyI, hashLength, dataToSecure, dataToSecure.length);

        if (ZrtpUtils.byteArrayCompare(confMac, confirm2.getHmac(), 2 * ZrtpPacketBase.ZRTP_WORD_SIZE) != 0) {
            errMsg[0] = ZrtpCodes.ZrtpErrorCodes.ConfirmHMACWrong;
            return null;
        }
        try {
            // Decrypting here
            cipher.cipher.init(false,
                            new ParametersWithIV(new KeyParameter(zrtpKeyI, 0, cipher.keyLength), confirm2.getIv()));
            int done = cipher.cipher.processBytes(dataToSecure, 0, dataToSecure.length, dataToSecure, 0);
            cipher.cipher.doFinal(dataToSecure, done);
        } catch (Exception e) {
            sendInfo(ZrtpCodes.MessageSeverity.Severe, EnumSet.of(ZrtpCodes.SevereCodes.SevereSecurityException));
            errMsg[0] = ZrtpCodes.ZrtpErrorCodes.CriticalSWError;
            return null;
        }
        confirm2.setDataToSecure(dataToSecure);

        if (!multiStream) {
            // Check HMAC of DHPart2 packet stored in temporary buffer. The
            // HMAC key of the DHPart2 packet is peer's H0 that is contained in
            // Confirm2. Refer to chapter 9.1 and chapter 10.
            if (!checkMsgHmac(confirm2.getHashH0())) {
                sendInfo(ZrtpCodes.MessageSeverity.Severe, EnumSet.of(ZrtpCodes.SevereCodes.SevereDH2HMACFailed));
                errMsg[0] = ZrtpCodes.ZrtpErrorCodes.CriticalSWError;
                return null;
            }
            signatureLength = confirm2.getSignatureLength();
            if (signSasSeen && signatureLength > 0) {
                signatureData = confirm2.getSignatureData();
                callback.checkSASSignature(sasHash);
            }
            /*
             * The Confirm2 is ok, handle the Retained secret stuff and inform GUI about state.
             */
            // Did our peer verify the SAS during last session? Get its SAS verified flag.
            boolean sasFlag = confirm2.isSASFlag();

            // Our peer did not confirm the SAS in last session, thus reset our stored SAS 
            // flag too. Reset the flag also if paranoidMode is true.
            if (!sasFlag || paranoidMode) {
                zidRec.resetSasVerified();
            }
            // Now get the resulting SAS verified flag from current RS1 before setting a new RS1.
            // It's a combination of our SAS verfied flag and peer's verified flag. Only if both
            // were set (true) then sasFlag becomes true.
            sasFlag = zidRec.isSasVerified();

            // save new RS1, this inherits the verified flag from old RS1
            zidRec.setNewRs1(newRs1, -1);
            ZidFile.getInstance().saveRecord(zidRec);

            // Ask for enrollment only if enabled via configuration and the
            // confirm packet contains the enrollment flag. The enrolling user
            // agent stores the MitM key only if the user accepts the enrollment
            // request.
            if (enableMitmEnrollment && confirm2.isPBXEnrollment()) {
                computePBXSecret();
                callback.zrtpAskEnrollment(ZrtpCodes.InfoEnrollment.EnrollmentRequest);
            }
            callback.srtpSecretsOn(cipher.readable + "/" + pubKey, SAS, sasFlag);
        }
        else {
            byte[] tmpHash = new byte[ZrtpConstants.MAX_DIGEST_LENGTH];
            hashFunctionImpl.update(confirm2.getHashH0(), 0, ZrtpPacketBase.HASH_IMAGE_SIZE); // Compute initiator's H1
                                                                                              // in tmpHash
            hashFunctionImpl.doFinal(tmpHash, 0);

            if (!checkMsgHmac(tmpHash)) {
                sendInfo(ZrtpCodes.MessageSeverity.Severe, EnumSet.of(ZrtpCodes.SevereCodes.SevereCommitHMACFailed));
                errMsg[0] = ZrtpCodes.ZrtpErrorCodes.CriticalSWError;
                return null;
            }
            // Inform GUI about security state, don't show SAS and its state
            callback.srtpSecretsOn(cipher.readable, null, true);

        }
        return zrtpConf2Ack;
    }

    /**
     * Prepare the ErrorAck packet.
     * 
     * This method prepares the ErrorAck packet. The input to this method is the
     * Error packet received from the peer.
     */
    protected ZrtpPacketErrorAck prepareErrorAck(ZrtpPacketError epkt) {
        int code = epkt.getErrorCode();

        for (ZrtpCodes.ZrtpErrorCodes zc : ZrtpCodes.ZrtpErrorCodes.values()) {
            if (zc.value == code) {
                sendInfo(ZrtpCodes.MessageSeverity.ZrtpError, EnumSet.of(zc));
                break;
            }
        }
        return zrtpErrorAck;
    }

    /**
     * Prepare the Error packet.
     * 
     * This method prepares the Error packet. The input to this method is the
     * error code to be included into the message.
     */
    protected ZrtpPacketError prepareError(ZrtpCodes.ZrtpErrorCodes errMsg) {
        zrtpError.setErrorCode(errMsg.value);
        return zrtpError;
    }

    /**
     * Prepare the PingAck packet.
     * 
     * This method prepares the PingAck packet.
     */
    protected ZrtpPacketPingAck preparePingAck(ZrtpPacketPing ppkt) {
        if (ppkt.getLength() != 6)                    // A PING packet must have a length of 6 words
            return null;

        // Because we do not support ZRTP proxy mode use the truncated ZID.
        // If this code shall be used in ZRTP proxy implementation the
        // computation of the endpoint hash must be enhanced (see 
        // chapters 5.15 and 5.16)
        zrtpPingAck.setLocalEpHash(zid);
        zrtpPingAck.setRemoteEpHash(ppkt.getEpHash());
        zrtpPingAck.setPeerSSRC(peerSSRC);
        return zrtpPingAck;
    }

    protected ZrtpPacketRelayAck prepareRelayAck(ZrtpPacketSASRelay srly, ZrtpCodes.ZrtpErrorCodes[] errMsg) {
        // handle and render SAS relay data only if the peer announced that it is a trusted
        // PBX. Don't handle SAS relay in paranoidMode.
        if (!mitmSeen || paranoidMode)
            return zrtpRelayAck;

        if (!srly.isLengthOk()) {
            errMsg[0] = ZrtpCodes.ZrtpErrorCodes.CriticalSWError;
            return null;
        }
        byte[] hkey, ekey;
        // If we are responder then the PBX used it's Initiator keys
        if (myRole == ZrtpCallback.Role.Responder) {
            hkey = hmacKeyI;
            ekey = zrtpKeyI;
        }
        else {
            hkey = hmacKeyR;
            ekey = zrtpKeyR;
        }
        byte[] dataToSecure = srly.getDataToSecure();
        byte[] relayMac = computeHmac(hkey, hashLength, dataToSecure, dataToSecure.length);

        if (ZrtpUtils.byteArrayCompare(relayMac, srly.getHmac(), 2 * ZrtpPacketBase.ZRTP_WORD_SIZE) != 0) {
            errMsg[0] = ZrtpCodes.ZrtpErrorCodes.ConfirmHMACWrong;
            return null; // TODO - check error handling
        }
        try {
            // Decrypting here
            cipher.cipher.init(false, new ParametersWithIV(new KeyParameter(ekey, 0, cipher.keyLength), srly.getIv()));
            int done = cipher.cipher.processBytes(dataToSecure, 0, dataToSecure.length, dataToSecure, 0);
            cipher.cipher.doFinal(dataToSecure, done);
        } catch (Exception e) {
            sendInfo(ZrtpCodes.MessageSeverity.Severe, EnumSet.of(ZrtpCodes.SevereCodes.SevereSecurityException));
            errMsg[0] = ZrtpCodes.ZrtpErrorCodes.CriticalSWError;
            return null; // TODO - check error handling
        }
        srly.setDataToSecure(dataToSecure);

        SupportedSASTypes render = srly.getSas();
        byte[] newSasHash = srly.getTrustedSas();

        boolean sasHashNull = true;
        for (byte aNewSasHash : newSasHash) {
            if (aNewSasHash != 0) {
                sasHashNull = false;
                break;
            }
        }
        // Check if new SAS is null or a trusted MitM relationship doesn't exist.
        // If this is the case then don't render and don't show the new SAS - use
        // the computed SAS hash but we may use a different SAS rendering algorithm to
        // render the computed SAS.
        String mitm = "/SASviaMitM";
        if (sasHashNull || !peerIsEnrolled) {
            newSasHash = sasHash;
            mitm = "/MitM";
        }
        // If other SAS schemes required - check here and use others
        if (render == ZrtpConstants.SupportedSASTypes.B32 || render == ZrtpConstants.SupportedSASTypes.B32E) {
            byte[] sasBytes = new byte[4];
            sasBytes[0] = newSasHash[0];
            sasBytes[1] = newSasHash[1];
            sasBytes[2] = (byte) (newSasHash[2] & 0xf0);
            sasBytes[3] = 0;
            if (render == ZrtpConstants.SupportedSASTypes.B32)
                SAS = Base32.binary2ascii(sasBytes, 20);
            else
                SAS = EmojiBase32.binary2ascii(sasBytes, 20);
        }
        else {
            SAS = ZrtpConstants.sas256WordsEven[newSasHash[0]] + ":" + ZrtpConstants.sas256WordsOdd[newSasHash[1]];
        }
        callback.srtpSecretsOn(cipher.readable + "/" + pubKey + mitm, SAS, false);
        return zrtpRelayAck;
    }

    /**
     * Prepare a ClearAck packet.
     * 
     * This method checks if the GoClear message is valid. If yes then switch
     * off SRTP processing, stop sending of RTP packets (pause transmit) and
     * inform the user about the fact. Only if user confirms the GoClear message
     * normal RTP processing is resumed.
     * 
     * @return NULL if GoClear could not be authenticated, a ClearAck packet
     *         otherwise.
     */
    // ZrtpPacketClearAck prepareClearAck(ZrtpPacketGoClear gpkt) {return null;}

    /**
     * Prepare a GoClearAck packet w/o HMAC
     * 
     * Prepare a GoCLear packet without a HMAC but with a short error message.
     * This type of GoClear is used if something went wrong during the ZRTP
     * negotiation phase.
     * 
     * @return A goClear packet without HMAC
     */
    // ZrtpPacketGoClear prepareGoClear(ZrtpCodes.ZrtpErrorCodes[] errMsg)
    // {return null;}

    /**
     * Compare the hvi values.
     * 
     * Compare a received Commit packet with our Commit packet and returns which
     * Commit packt is "more important". See chapter 5.2 to get further
     * information how to compare Commit packets.
     * 
     * @param commit
     *            Pointer to the peer's commit packet we just received.
     * @return &lt;0 if our Commit packet is "less important" &gt;0 if ours is "more
     *         important" 0 shouldn't happen because we compare crypto hashes
     */
    protected int compareCommit(ZrtpPacketCommit commit) {
        if (multiStream) {
            return (ZrtpUtils.byteArrayCompare(hvi, commit.getNonce(), 4 * ZrtpPacketBase.ZRTP_WORD_SIZE));
        }
        return (ZrtpUtils.byteArrayCompare(hvi, commit.getHvi(), ZrtpPacketBase.HVI_SIZE));
    }

    /**
     * Verify the H2 hash image.
     * 
     * Verifies the H2 hash contained in a received commit message. This
     * functions just verifies H2 but does not store it.
     * 
     * @param commit
     *            Pointer to the peer's commit packet we just received.
     * @return true if H2 is ok and verified false if H2 could not be verified
     */
    protected boolean verifyH2(ZrtpPacketCommit commit) {

        byte[] tmpH3 = new byte[ZrtpConstants.MAX_DIGEST_LENGTH];
        hashFunctionImpl.update(commit.getH2(), 0, ZrtpPacketBase.HASH_IMAGE_SIZE);
        hashFunctionImpl.doFinal(tmpH3, 0);

        return ZrtpUtils.byteArrayCompare(tmpH3, peerH3, ZrtpPacketBase.HASH_IMAGE_SIZE) == 0;
    }

    /**
     * Send information messages to the hosting environment.
     * 
     * The ZRTP implementation uses this method to send information messages to
     * the host. Along with the message ZRTP provides a severity indicator that
     * defines: Info, Warning, Error, Alert. Refer to the MessageSeverity enum
     * in the ZrtpCallback class.
     * 
     * @param severity
     *            This defines the message's severity
     * @param subCode
     *            The subcode identifying the reason.
     * @see ZrtpCodes.MessageSeverity
     */
    protected void sendInfo(ZrtpCodes.MessageSeverity severity, EnumSet<?> subCode) {

        // We've reached secure state: overwrite the SRTP master key and master salt.
        if (severity == ZrtpCodes.MessageSeverity.Info && subCode == EnumSet.of(ZrtpCodes.InfoCodes.InfoSecureStateOn)) {
            Arrays.fill(srtpKeyI, (byte) 0);
            Arrays.fill(srtpSaltI, (byte) 0);
            Arrays.fill(srtpKeyR, (byte) 0);
            Arrays.fill(srtpSaltR, (byte) 0);
        }
        callback.sendInfo(severity, subCode);
    }

    /**
     * ZRTP state engine calls this if the negotiation failed.
     * 
     * ZRTP calls this method in case ZRTP negotiation failed. The parameters
     * show the severity as well as some explanatory text.
     * 
     * @param severity
     *            This defines the message's severity
     * @param subCode
     *            The subcode identifying the reason.
     * @see ZrtpCodes.MessageSeverity
     */
    protected void zrtpNegotiationFailed(ZrtpCodes.MessageSeverity severity, EnumSet<?> subCode) {
        callback.zrtpNegotiationFailed(severity, subCode);
    }

    /**
     * ZRTP state engine calls this method if the other side does not support
     * ZRTP.
     * 
     * If the other side does not answer the ZRTP <em>Hello</em> packets then
     * ZRTP calls this method,
     * 
     */
    protected void zrtpNotSuppOther() {
        callback.zrtpNotSuppOther();
    }

    /**
     * Signal SRTP secrets are ready.
     * 
     * This method calls a callback method to inform the host that the SRTP
     * secrets are ready.
     * 
     * @param part
     *            Defines for which part (sender or receiver) to switch on
     *            security
     * @return Returns false if something went wrong during initialization of
     *         SRTP context. Propagate error back to state engine.
     */
    protected boolean srtpSecretsReady(ZrtpCallback.EnableSecurity part) {
        ZrtpSrtpSecrets sec = new ZrtpSrtpSecrets();

        sec.symEncAlgorithm = cipher.algo;
        
        sec.keyInitiator = srtpKeyI;
        sec.initKeyLen = cipher.keyLength * 8;
        sec.saltInitiator = srtpSaltI;
        sec.initSaltLen = 112;
        
        sec.keyResponder = srtpKeyR;
        sec.respKeyLen = cipher.keyLength * 8;
        sec.saltResponder = srtpSaltR;
        sec.respSaltLen = 112;
        
        sec.authAlgorithm = authLength.algo;
        sec.srtpAuthTagLen = authLength.length;

        sec.setRole(myRole);

        return callback.srtpSecretsReady(sec, part);
    }

    /**
     * Switch off SRTP secrets.
     * 
     * This method calls a callback method to inform the host that the SRTP
     * secrets shall be cleared.
     * 
     * @param part
     *            Defines for which part (sender or receiver) to clear
     */
    protected void srtpSecretsOff(ZrtpCallback.EnableSecurity part) {
        callback.srtpSecretsOff(part);
    }

    // Private internal methods
    /**
     * Helper function to store ZRTP message data in a temporary buffer
     * 
     * This functions first clears the temporary buffer, then stores the
     * packet's data to it. We use this to check the packet's HMAC after we
     * received the HMAC key in to following packet.
     * 
     * @param pkt
     *            Pointer to the packet's ZRTP message
     */
    private void storeMsgTemp(ZrtpPacketBase pkt) {
        int length = pkt.getLength() * ZrtpPacketBase.ZRTP_WORD_SIZE;
        length = (length > tempMsgBuffer.length) ? tempMsgBuffer.length : length;
        Arrays.fill(tempMsgBuffer, (byte) 0);
        System.arraycopy(pkt.getHeaderBase(), 0, tempMsgBuffer, 0, length);
        lengthOfMsgData = length;
    }

    /**
     * Check a ZRTP message HMAC of a previously stored message.
     * 
     * This function uses a HMAC key to compute a HMAC of a previous received
     * and stored ZRTP message. It compares the computed HMAC and the HMAC
     * stored in the stored message and returns the result. This uses the MACH
     * with the implicit hash algo.
     * 
     * @param keyIn
     *            Pointer to the HMAC key.
     * @return Returns true if the computed HMAC and the stored HMAC match,
     *         false otherwise.
     */
    private boolean checkMsgHmac(byte[] keyIn) {
        // compute HMAC, but exlude the stored HMAC :-)
        // Use HMAC with implicit hash algo
        int len = lengthOfMsgData - (2 * ZrtpPacketBase.ZRTP_WORD_SIZE); // :-)
        KeyParameter key = new KeyParameter(keyIn, 0, ZrtpPacketBase.HASH_IMAGE_SIZE);
        hmacFunctionImpl.init(key);
        hmacFunctionImpl.update(tempMsgBuffer, 0, len);
        byte data[] = new byte[hashLengthImpl];
        hmacFunctionImpl.doFinal(data, 0);

        byte[] storedMac = ZrtpUtils.readRegion(tempMsgBuffer, len, 2 * ZrtpPacketBase.ZRTP_WORD_SIZE);
        return (ZrtpUtils.byteArrayCompare(data, storedMac, 2 * ZrtpPacketBase.ZRTP_WORD_SIZE) == 0);
    }

    private void setNegotiatedHash(ZrtpConstants.SupportedHashes hash) {
        if (hash == ZrtpConstants.SupportedHashes.S256) {
            hashFunction = new SHA256Digest();
            hmacFunction = new HMac(new SHA256Digest());
            hashCtxFunction = new SHA256Digest();
        }
        else if (hash == ZrtpConstants.SupportedHashes.S384) {
            hashFunction = new SHA384Digest();
            hmacFunction = new HMac(new SHA384Digest());
            hashCtxFunction = new SHA384Digest();
        }
        hashLength = hashFunction.getDigestSize();
    }

    /**
     * Set the client ID for ZRTP Hello message.
     * 
     * The user of ZRTP must set its id to identify itself in the ZRTP HELLO
     * message. The maximum length is 16 characters. Shorter id string are
     * allowed, they will be filled with blanks. A longer id is truncated to 16
     * characters.
     * 
     * The identifier is set in the Hello packet of ZRTP. Thus only after
     * setting the identifier ZRTP can compute the HMAC and the final helloHash.
     * 
     * @param hpv
     *        Hello packet version class that holds info about the Hello packet for a
     *        specific protocol version
     *
     * @param id
     *            The client's id
     */
    private void setClientId(String id, HelloPacketVersion hpv) {
        String tmp = "                ";
        if (id.length() < 4 * ZrtpPacketBase.ZRTP_WORD_SIZE) {
            hpv.packet.setClientId(tmp);
        }
        hpv.packet.setClientId(id);
        int len = hpv.packet.getLength() * ZrtpPacketBase.ZRTP_WORD_SIZE;

        // Hello packet is ready now, compute its HMAC with key H2
        // (excluding the HMAC field (2*ZTP_WORD_SIZE)) and store in Hello
        byte data[] = computeHmacImpl(H2, hashLengthImpl, hpv.packet.getHeaderBase(), len
                        - (2 * ZrtpPacketBase.ZRTP_WORD_SIZE));
        hpv.packet.setHMAC(data);

        // calculate hash over the final Hello packet including the computed and
        // stored HMAC, refer to chap 9.1 how to use this hash in SIP/SDP.
        //
        // getHeaderBase() returns the full packetBuffer array. The length of
        // this array includes the CRC which is not part of the helloHash.
        // Thus compute digest only for the real message length.
        // Use implicit hash algo
        hashFunctionImpl.update(hpv.packet.getHeaderBase(), 0, len);
        hashFunctionImpl.doFinal(hpv.helloHash, 0);
    }

    /**
     * Helper function to compute a ZRTP message HMAC.
     * 
     * This function gets a HMAC key and uses it to compute a HMAC with this key
     * and the stored data of a previous received ZRTP message. It compares the
     * computed HMAC and the HMAC stored in the received message and returns the
     * result. This uses the HAMC with the implicit hash algo.
     * 
     * @param keyIn
     *            Pointer to the HMAC key.
     * @return Returns true if the computed HMAC and the stored HMAC match,
     *         false otherwise.
     */
    private byte[] computeMsgHmac(byte[] keyIn, ZrtpPacketBase pkt) {

        // compute HMAC, but exclude the stored HMAC in length computation:-)
        int len = (pkt.getLength() - 2) * ZrtpPacketBase.ZRTP_WORD_SIZE;
        return computeHmacImpl(keyIn, hashLengthImpl, pkt.getHeaderBase(), len);
    }

    /**
     * Compute a HMAC over some data using HMAC with negotiated Hash algorithm.
     * 
     * @param keyIn
     *            The key to use for the HMAC
     * @param keyLen
     *            The lenght of key data
     * @param toSign
     *            The data to sign
     * @param len
     *            the length of the data to sign
     * @return the HMAC data
     */
    private byte[] computeHmac(byte[] keyIn, int keyLen, byte[] toSign, int len) {
        KeyParameter key = new KeyParameter(keyIn, 0, keyLen);
        hmacFunction.init(key);
        hmacFunction.update(toSign, 0, len);
        byte[] retval = new byte[hashLength];
        hmacFunction.doFinal(retval, 0);
        return retval;
    }

    /**
     * Compute a HMAC over some data using HMAC with negotiated Hash algorithm.
     * 
     * @param keyIn
     *            The key to use for the HMAC
     * @param keyLen
     *            The lenght of key data
     * @param toSign
     *            The data to sign
     * @param len
     *            the length of the data to sign
     * @return the HMAC data
     */
    private byte[] computeHmacImpl(byte[] keyIn, int keyLen, byte[] toSign, int len) {
        KeyParameter key = new KeyParameter(keyIn, 0, keyLen);
        hmacFunctionImpl.init(key);
        hmacFunctionImpl.update(toSign, 0, len);
        byte[] retval = new byte[hashLengthImpl];
        hmacFunctionImpl.doFinal(retval, 0);
        return retval;
    }

    /**
     * Compute my hvi value according to ZRTP specification.
     * 
     * This uses the negotiated Hash algorithm.
     */
    private void computeHvi(ZrtpPacketDHPart dh, ZrtpPacketHello hello) {
        hashFunction.update(dh.getHeaderBase(), 0, dh.getLength()
                * ZrtpPacketBase.ZRTP_WORD_SIZE);
        hashFunction.update(hello.getHeaderBase(), 0, hello.getLength()
                * ZrtpPacketBase.ZRTP_WORD_SIZE);
        hashFunction.doFinal(hvi, 0);
    }

    private void computeSharedSecretSet() {
        /*
         * Compute the Initiator's and Reponder's retained shared secret Ids.
         */
        byte[] randBuf = new byte[ZidRecord.RS_LENGTH];

        if (!zidRec.isRs1Valid()) {
            secRand.nextBytes(randBuf);
            rs1IDi = computeHmac(randBuf, ZidRecord.RS_LENGTH, ZrtpConstants.initiator, ZrtpConstants.initiator.length);
            rs1IDr = computeHmac(randBuf, ZidRecord.RS_LENGTH, ZrtpConstants.responder, ZrtpConstants.responder.length);
        }
        else {
            rs1Valid = true;
            rs1IDi = computeHmac(zidRec.getRs1(), ZidRecord.RS_LENGTH, ZrtpConstants.initiator,
                            ZrtpConstants.initiator.length);

            rs1IDr = computeHmac(zidRec.getRs1(), ZidRecord.RS_LENGTH, ZrtpConstants.responder,
                            ZrtpConstants.responder.length);
        }

        if (!zidRec.isRs2Valid()) {
            secRand.nextBytes(randBuf);
            rs2IDi = computeHmac(randBuf, ZidRecord.RS_LENGTH, ZrtpConstants.initiator, ZrtpConstants.initiator.length);
            rs2IDr = computeHmac(randBuf, ZidRecord.RS_LENGTH, ZrtpConstants.responder, ZrtpConstants.responder.length);
        }
        else {
            rs2Valid = true;
            rs2IDi = computeHmac(zidRec.getRs2(), ZidRecord.RS_LENGTH, ZrtpConstants.initiator,
                            ZrtpConstants.initiator.length);
            rs2IDr = computeHmac(zidRec.getRs2(), ZidRecord.RS_LENGTH, ZrtpConstants.responder,
                            ZrtpConstants.responder.length);
        }

        /*
         * For the time being we don't support the following type of shared secrect. Could be easily done: somebody sets
         * some data into our ZRtp object, check it here and use it. Otherwise use the random data.
         */
        secRand.nextBytes(randBuf);
        auxSecretIDi = computeHmac(randBuf, ZidRecord.RS_LENGTH, ZrtpConstants.initiator,
                        ZrtpConstants.initiator.length);
        auxSecretIDr = computeHmac(randBuf, ZidRecord.RS_LENGTH, ZrtpConstants.responder,
                        ZrtpConstants.responder.length);

        if (!zidRec.isMITMKeyAvailable()) {
            secRand.nextBytes(randBuf);
            pbxSecretIDi = computeHmac(randBuf, ZidRecord.RS_LENGTH, ZrtpConstants.initiator,
                            ZrtpConstants.initiator.length);
            pbxSecretIDr = computeHmac(randBuf, ZidRecord.RS_LENGTH, ZrtpConstants.responder,
                            ZrtpConstants.responder.length);
        }
        else {
            pbxSecretIDi = computeHmac(zidRec.getMiTMData(), ZidRecord.RS_LENGTH, ZrtpConstants.initiator,
                            ZrtpConstants.initiator.length);
            pbxSecretIDr = computeHmac(zidRec.getMiTMData(), ZidRecord.RS_LENGTH, ZrtpConstants.responder,
                            ZrtpConstants.responder.length);
        }
    }

    private void generateKeysMultiStream() {
        // Compute the Multi Stream mode s0
        // Construct the KDF context as per ZRTP specification:
        // ZIDi || ZIDr || total_hash
        byte[] KDFcontext = new byte[zid.length + peerZid.length + hashLength];

        if (myRole == ZrtpCallback.Role.Responder) {
            System.arraycopy(peerZid, 0, KDFcontext, 0, peerZid.length);
            System.arraycopy(zid, 0, KDFcontext, peerZid.length, zid.length);
        }
        else {
            System.arraycopy(zid, 0, KDFcontext, 0, zid.length);
            System.arraycopy(peerZid, 0, KDFcontext, zid.length, peerZid.length);
        }
        System.arraycopy(messageHash, 0, KDFcontext, zid.length + peerZid.length, hashLength);

        s0 = KDF(zrtpSession, ZrtpConstants.zrtpMsk, KDFcontext, hashLength * 8);
        computeSRTPKeys();
        Arrays.fill(s0, (byte) 0);
    }

    /*
     * The ZRTP KDF function as per ZRT specification 4.5.1
     */
    private byte[] KDF(byte[] ki, byte[] label, byte[] context, int L) {
        KeyParameter key = new KeyParameter(ki, 0, hashLength);
        hmacFunction.init(key);

        byte[] counter = ZrtpUtils.int32ToArray(1);
        hmacFunction.update(counter, 0, 4);
        hmacFunction.update(label, 0, label.length); // the label includes the 0
                                                     // byte separator
        hmacFunction.update(context, 0, context.length);
        byte[] length = ZrtpUtils.int32ToArray(L);
        hmacFunction.update(length, 0, 4);

        byte[] retval = new byte[hashLength];
        hmacFunction.doFinal(retval, 0);
        return retval;
    }

    private void computeSRTPKeys() {

        // Construct the KDF context as per ZRTP specification:
        // ZIDi || ZIDr || total_hash
        byte[] KDFcontext = new byte[zid.length + peerZid.length + hashLength];

        if (myRole == ZrtpCallback.Role.Responder) {
            System.arraycopy(peerZid, 0, KDFcontext, 0, peerZid.length);
            System.arraycopy(zid, 0, KDFcontext, peerZid.length, zid.length);
        }
        else {
            System.arraycopy(zid, 0, KDFcontext, 0, zid.length);
            System.arraycopy(peerZid, 0, KDFcontext, zid.length, peerZid.length);
        }
        System.arraycopy(messageHash, 0, KDFcontext, zid.length + peerZid.length, hashLength);

        int keyLen = cipher.keyLength * 8;

        // Inititiator key and salt
        srtpKeyI = KDF(s0, ZrtpConstants.iniMasterKey, KDFcontext, keyLen);
        srtpSaltI = KDF(s0, ZrtpConstants.iniMasterSalt, KDFcontext, 112);

        // Responder key and salt
        srtpKeyR = KDF(s0, ZrtpConstants.respMasterKey, KDFcontext, keyLen);
        srtpSaltR = KDF(s0, ZrtpConstants.respMasterSalt, KDFcontext, 112);

        // The HMAC keys
        hmacKeyI = KDF(s0, ZrtpConstants.iniHmacKey, KDFcontext, hashLength * 8);
        hmacKeyR = KDF(s0, ZrtpConstants.respHmacKey, KDFcontext, hashLength * 8);

        // The keys for Confirm messages
        zrtpKeyI = KDF(s0, ZrtpConstants.iniZrtpKey, KDFcontext, keyLen);
        zrtpKeyR = KDF(s0, ZrtpConstants.respZrtpKey, KDFcontext, keyLen);

        if (!multiStream) {
            // Compute the new Retained Secret
            newRs1 = KDF(s0, ZrtpConstants.retainedSec, KDFcontext, ZrtpConstants.SHA256_DIGEST_LENGTH * 8);

            // Compute the ZRTP Session Key
            zrtpSession = KDF(s0, ZrtpConstants.zrtpSessionKey, KDFcontext, hashLength * 8);

            // perform SAS generation according to chapter 5.5 and 8.
            // we don't need a speciai sasValue field. sasValue are the first
            // (leftmost) 32 bits (4 bytes) of sasHash
            sasHash = KDF(s0, ZrtpConstants.sasString, KDFcontext, ZrtpConstants.SHA256_DIGEST_LENGTH * 8);

            // according to chapter 8 only the leftmost 20 bits of sasValue (aka
            // sasHash) are used to create the character SAS string of type SAS
            // base 32 (5 bits per character).
            // If other SAS schemes required - check here and use others
            if (sasType == ZrtpConstants.SupportedSASTypes.B32 || sasType == ZrtpConstants.SupportedSASTypes.B32E) {
                byte[] sasBytes = new byte[4];
                sasBytes[0] = sasHash[0];
                sasBytes[1] = sasHash[1];
                sasBytes[2] = (byte) (sasHash[2] & 0xf0);
                sasBytes[3] = 0;
                if (sasType == ZrtpConstants.SupportedSASTypes.B32)
                    SAS = Base32.binary2ascii(sasBytes, 20);
                else
                    SAS = EmojiBase32.binary2ascii(sasBytes, 20);
            }
            else {
                SAS = ZrtpConstants.sas256WordsEven[sasHash[0]&0xff] + ":" 
                                + ZrtpConstants.sas256WordsOdd[sasHash[1]&0xff]; 
            }
            if (signSasSeen)
                callback.signSAS(sasHash);
        }
    }

    private void computePBXSecret() {
        // Construct the KDF context as per ZRTP specification chap 7.3.1:
        // ZIDi || ZIDr
        byte[] KDFcontext = new byte[zid.length + peerZid.length];

        if (myRole == ZrtpCallback.Role.Responder) {
            System.arraycopy(peerZid, 0, KDFcontext, 0, peerZid.length);
            System.arraycopy(zid, 0, KDFcontext, peerZid.length, zid.length);
        }
        else {
            System.arraycopy(zid, 0, KDFcontext, 0, zid.length);
            System.arraycopy(peerZid, 0, KDFcontext, zid.length, peerZid.length);
        }

        pbxSecretTmp = KDF(zrtpSession, ZrtpConstants.zrtpTrustedMitm, KDFcontext,
                        ZrtpConstants.SHA256_DIGEST_LENGTH * 8);
    }
    
    private void generateKeysInitiator(ZrtpPacketDHPart dhPart) {
        byte[][] setD = new byte[3][];
        int rsFound = 0;

        setD[0] = setD[1] = setD[2] = null;

        /*
         * Select the real secrets into setD
         */
        if (ZrtpUtils.byteArrayCompare(rs1IDr, dhPart.getRs1Id(), 8) == 0) {
            setD[0] = zidRec.getRs1();
            rsFound = 0x1;
        }
        else if (ZrtpUtils.byteArrayCompare(rs1IDr, dhPart.getRs2Id(), 8) == 0) {
            setD[0] = zidRec.getRs1();
            rsFound = 0x2;
        }
        else if (ZrtpUtils.byteArrayCompare(rs2IDr, dhPart.getRs1Id(), 8) == 0) {
            setD[0] = zidRec.getRs2();
            rsFound = 0x4;
        }
        else if (ZrtpUtils.byteArrayCompare(rs2IDr, dhPart.getRs2Id(), 8) == 0) {
            setD[0] = zidRec.getRs2();
            rsFound = 0x8;
        }

        /***********************************************************************
         * Not yet supported: if (ZrtpUtils.byteArrayCompare(auxSecretIDr,
         * dhPart.getAuxSecretId(), 8) == 0) { setD[1] = ; } 
         ********************************************************************* */
        if (ZrtpUtils.byteArrayCompare(pbxSecretIDr, dhPart.getPbxSecretId(), 8) == 0) {
            setD[2] = zidRec.getMiTMData(); 
        }
        // Check if some retained secrets found
        if (rsFound == 0) { // no RS matches found
            if (rs1Valid || rs2Valid) { // but valid RS records in cache
                sendInfo(ZrtpCodes.MessageSeverity.Warning, EnumSet.of(ZrtpCodes.WarningCodes.WarningNoExpectedRSMatch));
                zidRec.resetSasVerified();
            }
            else { // No valid RS record in cache
                sendInfo(ZrtpCodes.MessageSeverity.Warning, EnumSet.of(ZrtpCodes.WarningCodes.WarningNoRSMatch));
            }
        }
        else { // at least one RS matches
            sendInfo(ZrtpCodes.MessageSeverity.Info, EnumSet.of(ZrtpCodes.InfoCodes.InfoRSMatchFound));
        }
        /*
         * Ready to generate s0 here. The formular to compute S0 (Refer to RFC 6189
         * chapter 4.4.1.4):
         * 
         * s0 = hash( counter | DHResult | "ZRTP-HMAC-KDF" | ZIDi | ZIDr | \
         * total_hash | len(s1) | s1 | len(s2) | s2 | len(s3) | s3)
         * 
         * Note: in this function we are Initiator, thus ZIDi is our zid (zid),
         * ZIDr is the peer's zid (peerZid).
         */

        // Very first element is a fixed counter, big endian
        byte[] counter = ZrtpUtils.int32ToArray(1);
        hashFunction.update(counter, 0, 4);

        // Next is the DH result itself
        hashFunction.update(DHss, 0, DHss.length);

        // Next the fixed string "ZRTP-HMAC-KDF"
        hashFunction.update(ZrtpConstants.KDFString, 0, ZrtpConstants.KDFString.length);

        // Next is Initiator's id (ZIDi), in this case as Initiator
        // it is zid
        hashFunction.update(zid, 0, zid.length);

        // Next is Responder's id (ZIDr), in this case our peer's id
        hashFunction.update(peerZid, 0, peerZid.length);

        // Next ist total hash (messageHash) itself
        hashFunction.update(messageHash, 0, hashLength);

        /*
         * For each matching shared secret hash the length of the shared secret
         * as 32 bit big-endian number followd by the shared secret itself. The
         * length of a shared seceret is currently fixed to RS_LENGTH for
         * retained shared secrets. If a shared secret is not used _only_ its
         * length is hased as zero length.
         */
        // prepare 32 bit big-endian number
        byte[] secretHashLen = ZrtpUtils.int32ToArray(ZidRecord.RS_LENGTH);
        byte[] nullinger = new byte[4];
        Arrays.fill(nullinger, (byte) 0);

        for (int i = 0; i < 3; i++) {
            if (setD[i] != null) { // a matching secret: set length, then secret
                hashFunction.update(secretHashLen, 0, secretHashLen.length);
                hashFunction.update(setD[i], 0, setD[i].length);
            }
            else { // no machting secret, set length 0, skip secret
                hashFunction.update(nullinger, 0, nullinger.length);
            }
        }
        s0 = new byte[ZrtpConstants.MAX_DIGEST_LENGTH];
        hashFunction.doFinal(s0, 0);
        // ZrtpUtils.hexdump("S0 I", s0, hashLength);

        Arrays.fill(DHss, (byte) 0);
        DHss = null;

        computeSRTPKeys();
        Arrays.fill(s0, (byte) 0);
    }

    private void generateKeysResponder(ZrtpPacketDHPart dhPart) {
        byte[][] setD = new byte[3][];
        int rsFound = 0;

        setD[0] = setD[1] = setD[2] = null;

        /*
         * Select the real secrets into setD
         */
        if (ZrtpUtils.byteArrayCompare(rs1IDi, dhPart.getRs1Id(), 8) == 0) {
            setD[0] = zidRec.getRs1();
            rsFound = 0x1;
        }
        else if (ZrtpUtils.byteArrayCompare(rs1IDi, dhPart.getRs2Id(), 8) == 0) {
            setD[0] = zidRec.getRs1();
            rsFound = 0x2;
        }
        else if (ZrtpUtils.byteArrayCompare(rs2IDi, dhPart.getRs1Id(), 8) == 0) {
            setD[0] = zidRec.getRs2();
            rsFound = 0x4;
        }
        else if (ZrtpUtils.byteArrayCompare(rs2IDi, dhPart.getRs2Id(), 8) == 0) {
            setD[0] = zidRec.getRs2();
            rsFound = 0x8;
        }
        /***********************************************************************
         * Not yet supported if (ZrtpUtils.byteArrayCompare(auxSecretIDi,
         * dhPart.getAuxSecretId(), 8) == 0) { setD[1] = }
         **********************************************************************/
        if (ZrtpUtils.byteArrayCompare(pbxSecretIDi, dhPart.getPbxSecretId(), 8) == 0) { 
            setD[2] = zidRec.getMiTMData(); 
        }
        // Check if some retained secrets found
        if (rsFound == 0) { // no RS matches found
            if (rs1Valid || rs2Valid) { // but valid RS records in cache
                sendInfo(ZrtpCodes.MessageSeverity.Warning, EnumSet.of(ZrtpCodes.WarningCodes.WarningNoExpectedRSMatch));
                zidRec.resetSasVerified();
            }
            else { // No valid RS record in cache
                sendInfo(ZrtpCodes.MessageSeverity.Warning, EnumSet.of(ZrtpCodes.WarningCodes.WarningNoRSMatch));
            }
        }
        else { // at least one RS matches
            sendInfo(ZrtpCodes.MessageSeverity.Info, EnumSet.of(ZrtpCodes.InfoCodes.InfoRSMatchFound));
        }
        /*
         * ready to generate s0 here. The formular to compute S0 (Refer to RFC 6189
         * chapter 4.4.1.4):
         * 
         * s0 = hash( counter | DHResult | "ZRTP-HMAC-KDF" | ZIDi | ZIDr | \
         * total_hash | len(s1) | s1 | len(s2) | s2 | len(s3) | s3)
         * 
         * Note: in this function we are Responder, thus ZIDi is the peer's zid
         * (peerZid), ZIDr is our zid.
         */

        // Very first element is a fixed counter, big endian
        byte[] counter = ZrtpUtils.int32ToArray(1);
        hashFunction.update(counter, 0, 4);

        // Next is the DH result itself
        hashFunction.update(DHss, 0, DHss.length);

        // Next the fixed string "ZRTP-HMAC-KDF"
        hashFunction.update(ZrtpConstants.KDFString, 0, ZrtpConstants.KDFString.length);

        // Next is Initiator's id (ZIDi), in this case as Responder
        // it is peerZid
        hashFunction.update(peerZid, 0, peerZid.length);

        // Next is Responder's id (ZIDr), in this case our own zid
        hashFunction.update(zid, 0, zid.length);

        // Next ist total hash (messageHash) itself
        hashFunction.update(messageHash, 0, hashLength);

        /*
         * For each matching shared secret hash the length of the shared secret
         * as 32 bit big-endian number followd by the shared secret itself. The
         * length of a shared seceret is currently fixed to RS_LENGTH for
         * retained shared secrets. If a shared secret is not used _only_ its
         * length is hased as zero length.
         */
        // prepare 32 bit big-endian number
        byte[] secretHashLen = ZrtpUtils.int32ToArray(ZidRecord.RS_LENGTH);
        byte[] nullinger = new byte[4];
        Arrays.fill(nullinger, (byte) 0);

        for (int i = 0; i < 3; i++) {
            if (setD[i] != null) { // a matching secret: set length, then secret
                hashFunction.update(secretHashLen, 0, secretHashLen.length);
                hashFunction.update(setD[i], 0, setD[i].length);
            }
            else { // no machting secret, set length 0, skip secret
                hashFunction.update(nullinger, 0, nullinger.length);
            }
        }
        s0 = new byte[ZrtpConstants.MAX_DIGEST_LENGTH];
        hashFunction.doFinal(s0, 0);
        // ZrtpUtils.hexdump("S0 R", s0, hashLength);

        Arrays.fill(DHss, (byte) 0);
        DHss = null;

        computeSRTPKeys();
        Arrays.fill(s0, (byte) 0);
    }

    private boolean checkPubKey(BigInteger pvr, ZrtpConstants.SupportedPubKeys dhtype) {
        if (pvr.equals(BigInteger.ONE)) {
            return false;
        }
        if (dhtype == ZrtpConstants.SupportedPubKeys.DH2K) {
            return !pvr.equals(ZrtpConstants.P2048MinusOne);
        }
        return dhtype == ZrtpConstants.SupportedPubKeys.DH3K && !pvr.equals(ZrtpConstants.P3072MinusOne);
    }

    // public static void main(String argv[]) {
    // byte[] data=
    // {1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20,21,22,23,24,25,26,27,28,29,30,31,32};
    // ZRtp zrtp = null;
    // try {
    // zrtp = new ZRtp(data, null, "GNU ZRTP4J 1.0.0", null);
    // } catch (GeneralSecurityException e) {
    // e.printStackTrace();
    // }
    // ZrtpUtils.hexdump("Hello packet", zrtp.zrtpHello.getHeaderBase(),
    // zrtp.zrtpHello.getHeaderBase().length);
    // System.err.println("ZRtp done");
    // }

}
