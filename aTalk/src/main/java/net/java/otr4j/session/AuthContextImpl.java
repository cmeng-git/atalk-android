/*
 * otr4j, the open source java otr library.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.otr4j.session;

import net.java.otr4j.OtrException;
import net.java.otr4j.crypto.OtrCryptoEngine;
import net.java.otr4j.crypto.OtrCryptoEngineImpl;
import net.java.otr4j.io.SerializationUtils;
import net.java.otr4j.io.messages.*;
import net.java.otr4j.session.Session.OTRv;

import org.atalk.android.plugin.timberlog.TimberLog;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.security.KeyPair;
import java.security.PublicKey;
import java.util.*;

import javax.crypto.interfaces.DHPublicKey;

import timber.log.Timber;

/**
 * @author George Politis
 * @author Eng Chong Meng
 */
class AuthContextImpl extends AuthContext
{
    private Session session;
    private int authenticationState;

    private DHPublicKey remoteDHPublicKey;
    private byte[] remoteDHPublicKeyEncrypted;
    private byte[] remoteDHPublicKeyHash;

    private int localDHPrivateKeyID;

    private BigInteger s;
    private byte[] c;
    private byte[] m1;
    private byte[] m2;
    private byte[] cp;
    private byte[] m1p;
    private byte[] m2p;

    private KeyPair localLongTermKeyPair;

    private Boolean isSecure = false;

    private final MessageFactory messageFactory = new MessageFactoryImpl();

    private PublicKey remoteLongTermPublicKey;

    public AuthContextImpl(Session session)
    {
        this.setSession(session);
        this.reset();
    }

    class MessageFactoryImpl extends MessageFactory
    {
        @Override
        QueryMessage getQueryMessage()
        {
            List<Integer> versions = Arrays.asList(OTRv.TWO, OTRv.THREE);
            return new QueryMessage(versions);
        }

        @Override
        DHCommitMessage getDHCommitMessage()
                throws OtrException
        {
            DHCommitMessage message = new DHCommitMessage(getSession().getProtocolVersion(),
                    getLocalDHPublicKeyHash(), getLocalDHPublicKeyEncrypted());
            message.senderInstanceTag = session.getSenderInstanceTag().getValue();
            message.receiverInstanceTag = InstanceTag.ZERO_VALUE;
            return message;
        }

        @Override
        DHKeyMessage getDHKeyMessage()
                throws OtrException
        {
            DHKeyMessage dhKeyMessage = new DHKeyMessage(getSession().getProtocolVersion(),
                    (DHPublicKey) getLocalDHKeyPair().getPublic());
            dhKeyMessage.senderInstanceTag = getSession().getSenderInstanceTag().getValue();
            dhKeyMessage.receiverInstanceTag = getSession().getReceiverInstanceTag().getValue();
            return dhKeyMessage;
        }

        @Override
        RevealSignatureMessage getRevealSignatureMessage()
                throws OtrException
        {
            try {
                SignatureM m = new SignatureM((DHPublicKey) getLocalDHKeyPair().getPublic(),
                        getRemoteDHPublicKey(), getLocalLongTermKeyPair().getPublic(), getLocalDHKeyPairID());

                OtrCryptoEngine otrCryptoEngine = new OtrCryptoEngineImpl();
                byte[] mhash = otrCryptoEngine.sha256Hmac(SerializationUtils.toByteArray(m), getM1());
                byte[] signature = otrCryptoEngine.sign(mhash, getLocalLongTermKeyPair().getPrivate());

                SignatureX mysteriousX
                        = new SignatureX(getLocalLongTermKeyPair().getPublic(), getLocalDHKeyPairID(), signature);
                byte[] xEncrypted
                        = otrCryptoEngine.aesEncrypt(getC(), null, SerializationUtils.toByteArray(mysteriousX));

                byte[] tmp = SerializationUtils.writeData(xEncrypted);
                byte[] xEncryptedHash = otrCryptoEngine.sha256Hmac160(tmp, getM2());

                RevealSignatureMessage revealSignatureMessage
                        = new RevealSignatureMessage(getSession().getProtocolVersion(), xEncrypted, xEncryptedHash, getR());
                revealSignatureMessage.senderInstanceTag
                        = getSession().getSenderInstanceTag().getValue();
                revealSignatureMessage.receiverInstanceTag
                        = getSession().getReceiverInstanceTag().getValue();
                return revealSignatureMessage;
            } catch (IOException e) {
                throw new OtrException(e);
            }
        }

        @Override
        SignatureMessage getSignatureMessage()
                throws OtrException
        {
            SignatureM m = new SignatureM((DHPublicKey) getLocalDHKeyPair().getPublic(),
                    getRemoteDHPublicKey(), getLocalLongTermKeyPair().getPublic(), getLocalDHKeyPairID());

            OtrCryptoEngine otrCryptoEngine = new OtrCryptoEngineImpl();
            byte[] mhash;
            try {
                mhash = otrCryptoEngine.sha256Hmac(SerializationUtils.toByteArray(m), getM1p());
            } catch (IOException e) {
                throw new OtrException(e);
            }

            byte[] signature = otrCryptoEngine.sign(mhash, getLocalLongTermKeyPair().getPrivate());
            SignatureX mysteriousX
                    = new SignatureX(getLocalLongTermKeyPair().getPublic(), getLocalDHKeyPairID(), signature);

            byte[] xEncrypted;
            try {
                xEncrypted = otrCryptoEngine.aesEncrypt(getCp(), null, SerializationUtils.toByteArray(mysteriousX));
                byte[] tmp = SerializationUtils.writeData(xEncrypted);
                byte[] xEncryptedHash = otrCryptoEngine.sha256Hmac160(tmp, getM2p());
                SignatureMessage signatureMessage
                        = new SignatureMessage(getSession().getProtocolVersion(), xEncrypted, xEncryptedHash);
                signatureMessage.senderInstanceTag = getSession().getSenderInstanceTag().getValue();
                signatureMessage.receiverInstanceTag = getSession().getReceiverInstanceTag().getValue();
                return signatureMessage;
            } catch (IOException e) {
                throw new OtrException(e);
            }
        }
    }

    @Override
    public void reset()
    {
        Timber.log(TimberLog.FINER, "Resetting authentication state.");
        authenticationState = AuthContext.NONE;
        r = null;

        remoteDHPublicKey = null;
        remoteDHPublicKeyEncrypted = null;
        remoteDHPublicKeyHash = null;

        localDHKeyPair = null;
        localDHPrivateKeyID = 1;
        localDHPublicKeyBytes = null;
        localDHPublicKeyHash = null;
        localDHPublicKeyEncrypted = null;

        s = null;
        c = null;
        m1 = null;
        m2 = null;
        cp = null;
        m1p = null;
        m2p = null;

        localLongTermKeyPair = null;
        setIsSecure(false);
    }

    private void setIsSecure(Boolean isSecure)
    {
        this.isSecure = isSecure;
    }

    @Override
    public boolean getIsSecure()
    {
        return isSecure;
    }

    void setAuthenticationState(int authenticationState)
    {
        this.authenticationState = authenticationState;
    }

    private int getAuthenticationState()
    {
        return authenticationState;
    }

    private byte[] getR()
    {
        if (r == null) {
            Timber.log(TimberLog.FINER, "Picking random key r.");
            r = new byte[OtrCryptoEngine.AES_KEY_BYTE_LENGTH];
            new Random().nextBytes(r);
        }
        return r;
    }

    private void setRemoteDHPublicKey(DHPublicKey dhPublicKey)
    {
        // Verifies that Alice's gy is a legal value (2 <= gy <= modulus-2)
        if (dhPublicKey.getY().compareTo(OtrCryptoEngine.MODULUS_MINUS_TWO) > 0) {
            throw new IllegalArgumentException("Illegal D-H Public Key value, Ignoring message.");
        }
        else if (dhPublicKey.getY().compareTo(OtrCryptoEngine.BIGINTEGER_TWO) < 0) {
            throw new IllegalArgumentException("Illegal D-H Public Key value, Ignoring message.");
        }
        Timber.log(TimberLog.FINER, "Received D-H Public Key is a legal value.");
        this.remoteDHPublicKey = dhPublicKey;
    }

	@Override
    public DHPublicKey getRemoteDHPublicKey()
    {
        return remoteDHPublicKey;
    }

    private void setRemoteDHPublicKeyEncrypted(byte[] remoteDHPublicKeyEncrypted)
    {
        Timber.log(TimberLog.FINER, "Storing encrypted remote public key.");
        this.remoteDHPublicKeyEncrypted = remoteDHPublicKeyEncrypted;
    }

    private byte[] getRemoteDHPublicKeyEncrypted()
    {
        return remoteDHPublicKeyEncrypted;
    }

    private void setRemoteDHPublicKeyHash(byte[] remoteDHPublicKeyHash)
    {
        Timber.log(TimberLog.FINER, "Storing encrypted remote public key hash.");
        this.remoteDHPublicKeyHash = remoteDHPublicKeyHash;
    }

    private byte[] getRemoteDHPublicKeyHash()
    {
        return remoteDHPublicKeyHash;
    }

    @Override
    public KeyPair getLocalDHKeyPair()
            throws OtrException
    {
        if (localDHKeyPair == null) {
            localDHKeyPair = new OtrCryptoEngineImpl().generateDHKeyPair();
            Timber.log(TimberLog.FINER, "Generated local D-H key pair.");
        }
        return localDHKeyPair;
    }

    private int getLocalDHKeyPairID()
    {
        return localDHPrivateKeyID;
    }

    private byte[] getLocalDHPublicKeyHash()
            throws OtrException
    {
        if (localDHPublicKeyHash == null) {
            localDHPublicKeyHash = new OtrCryptoEngineImpl().sha256Hash(getLocalDHPublicKeyBytes());
            Timber.log(TimberLog.FINER, "Hashed local D-H public key.");
        }
        return localDHPublicKeyHash;
    }

    private byte[] getLocalDHPublicKeyEncrypted()
            throws OtrException
    {
        if (localDHPublicKeyEncrypted == null) {
            localDHPublicKeyEncrypted = new OtrCryptoEngineImpl().aesEncrypt(getR(), null, getLocalDHPublicKeyBytes());
            Timber.log(TimberLog.FINER, "Encrypted our D-H public key.");
        }
        return localDHPublicKeyEncrypted;
    }

    @Override
    public BigInteger getS()
            throws OtrException
    {
        if (s == null) {
            s = new OtrCryptoEngineImpl().generateSecret(this.getLocalDHKeyPair().getPrivate(), this.getRemoteDHPublicKey());
            Timber.log(TimberLog.FINER, "Generated shared secret.");
        }
        return s;
    }

    private byte[] getC()
            throws OtrException
    {
        if (c != null)
            return c;

        byte[] h2 = h2(C_START);
        ByteBuffer buff = ByteBuffer.wrap(h2);
        this.c = new byte[OtrCryptoEngine.AES_KEY_BYTE_LENGTH];
        buff.get(this.c);
        Timber.log(TimberLog.FINER, "Computed c.");
        return c;

    }

    private byte[] getM1()
            throws OtrException
    {
        if (m1 != null)
            return m1;

        byte[] h2 = h2(M1_START);
        ByteBuffer buff = ByteBuffer.wrap(h2);
        byte[] m1 = new byte[OtrCryptoEngine.SHA256_HMAC_KEY_BYTE_LENGTH];
        buff.get(m1);
        Timber.log(TimberLog.FINER, "Computed m1.");
        this.m1 = m1;
        return m1;
    }

    private byte[] getM2()
            throws OtrException
    {
        if (m2 != null)
            return m2;

        byte[] h2 = h2(M2_START);
        ByteBuffer buff = ByteBuffer.wrap(h2);
        byte[] m2 = new byte[OtrCryptoEngine.SHA256_HMAC_KEY_BYTE_LENGTH];
        buff.get(m2);
        Timber.log(TimberLog.FINER, "Computed m2.");
        this.m2 = m2;
        return m2;
    }

    private byte[] getCp()
            throws OtrException
    {
        if (cp != null)
            return cp;

        byte[] h2 = h2(C_START);
        ByteBuffer buff = ByteBuffer.wrap(h2);
        byte[] cp = new byte[OtrCryptoEngine.AES_KEY_BYTE_LENGTH];
        buff.position(OtrCryptoEngine.AES_KEY_BYTE_LENGTH);
        buff.get(cp);
        Timber.log(TimberLog.FINER, "Computed c'.");
        this.cp = cp;
        return cp;
    }

    private byte[] getM1p()
            throws OtrException
    {
        if (m1p != null)
            return m1p;

        byte[] h2 = h2(M1p_START);
        ByteBuffer buff = ByteBuffer.wrap(h2);
        byte[] m1p = new byte[OtrCryptoEngine.SHA256_HMAC_KEY_BYTE_LENGTH];
        buff.get(m1p);
        this.m1p = m1p;
        Timber.log(TimberLog.FINER, "Computed m1'.");
        return m1p;
    }

    private byte[] getM2p()
            throws OtrException
    {
        if (m2p != null)
            return m2p;

        byte[] h2 = h2(M2p_START);
        ByteBuffer buff = ByteBuffer.wrap(h2);
        byte[] m2p = new byte[OtrCryptoEngine.SHA256_HMAC_KEY_BYTE_LENGTH];
        buff.get(m2p);
        this.m2p = m2p;
        Timber.log(TimberLog.FINER, "Computed m2'.");
        return m2p;
    }

    @Override
    public KeyPair getLocalLongTermKeyPair()
            throws OtrException
    {
        if (localLongTermKeyPair == null) {
            localLongTermKeyPair = getSession().getLocalKeyPair();
        }
        return localLongTermKeyPair;
    }

    private byte[] h2(byte b)
            throws OtrException
    {
        byte[] secbytes;
        try {
            secbytes = SerializationUtils.writeMpi(getS());
        } catch (IOException e) {
            throw new OtrException(e);
        }

        int len = secbytes.length + 1;
        ByteBuffer buff = ByteBuffer.allocate(len);
        buff.put(b);
        buff.put(secbytes);
        byte[] sdata = buff.array();
        return new OtrCryptoEngineImpl().sha256Hash(sdata);
    }

    private byte[] getLocalDHPublicKeyBytes()
            throws OtrException
    {
        if (localDHPublicKeyBytes == null) {
            try {
                this.localDHPublicKeyBytes = SerializationUtils.writeMpi(((DHPublicKey)
                        getLocalDHKeyPair().getPublic()).getY());
            } catch (IOException e) {
                throw new OtrException(e);
            }
        }
        return localDHPublicKeyBytes;
    }

    @Override
    public void handleReceivingMessage(AbstractMessage m)
            throws OtrException
    {
        switch (m.messageType) {
            case AbstractEncodedMessage.MESSAGE_DH_COMMIT:
                handleDHCommitMessage((DHCommitMessage) m);
                break;
            case AbstractEncodedMessage.MESSAGE_DHKEY:
                handleDHKeyMessage((DHKeyMessage) m);
                break;
            case AbstractEncodedMessage.MESSAGE_REVEALSIG:
                handleRevealSignatureMessage((RevealSignatureMessage) m);
                break;
            case AbstractEncodedMessage.MESSAGE_SIGNATURE:
                handleSignatureMessage((SignatureMessage) m);
                break;
            default:
                throw new UnsupportedOperationException();
        }
    }

    /**
     * Returns a simple, human-readable name for a type of message.
     *
     * @return this returns {@code getClass().getSimpleName()},
     * removing the string "Message" in the end, if it is present,
     * thus "my.pkg.MyMessage" would return "My"
     */
    private static String extractMessageTypeName(final AbstractMessage msg)
    {
        return msg.getClass().getSimpleName().replaceFirst("Message$", "");
    }

    private boolean validateMessage(final AbstractEncodedMessage m)
            throws OtrException
    {
        final String messageTypeName = extractMessageTypeName(m);
        Session mySession = getSession();
        SessionID sessionID = mySession.getSessionID();
        Timber.log(TimberLog.FINER, "%s received a %s message from %s through %s.",
                sessionID.getAccountID(), messageTypeName, sessionID.getUserID(), sessionID.getProtocolName());

        if (m.protocolVersion == OTRv.TWO && !mySession.getSessionPolicy().getAllowV2()) {
            Timber.log(TimberLog.FINER, "ALLOW_V2 is not set, ignore this message.");
            return false;
        }
        else if (m.protocolVersion == OTRv.THREE && !mySession.getSessionPolicy().getAllowV3()) {
            Timber.log(TimberLog.FINER, "ALLOW_V3 is not set, ignore this message.");
            return false;
        }
        else if (m.protocolVersion == OTRv.THREE
                && mySession.getSenderInstanceTag().getValue() != m.receiverInstanceTag
                && (m.messageType != AbstractEncodedMessage.MESSAGE_DH_COMMIT
                || m.receiverInstanceTag != 0))
                // from the protocol specification: "For a commit message this will often be 0,
                // since the other party may not have identified their instance tag yet."
        {
            Timber.log(TimberLog.FINER, "Received a %s Message with receiver instance tag that is different from ours, ignore this message",
                    messageTypeName);
            return false;
        }
        else {
            return true;
        }
    }

    private void handleSignatureMessage(SignatureMessage m)
            throws OtrException
    {
        switch (this.getAuthenticationState()) {
            case AWAITING_SIG:
                // Verify MAC.
                if (!m.verify(this.getM2p())) {
                    Timber.log(TimberLog.FINER, "Signature MACs are not equal, ignoring message.");
                    return;
                }

                // Decrypt X.
                byte[] remoteXDecrypted = m.decrypt(this.getCp());
                SignatureX remoteX;
                try {
                    remoteX = SerializationUtils.toMysteriousX(remoteXDecrypted);
                } catch (IOException e) {
                    throw new OtrException(e);
                }
                // Compute signature.
                PublicKey localRemoteLongTermPublicKey = remoteX.longTermPublicKey;
                SignatureM remoteM = new SignatureM(this.getRemoteDHPublicKey(),
                        (DHPublicKey) this.getLocalDHKeyPair().getPublic(),
                        localRemoteLongTermPublicKey, remoteX.dhKeyID);
                OtrCryptoEngine otrCryptoEngine = new OtrCryptoEngineImpl();
                // Verify signature.
                byte[] signature;
                try {
                    signature = otrCryptoEngine.sha256Hmac(SerializationUtils.toByteArray(remoteM), this.getM1p());
                } catch (IOException e) {
                    throw new OtrException(e);
                }
                if (!otrCryptoEngine.verify(signature, localRemoteLongTermPublicKey, remoteX.signature)) {
                    Timber.log(TimberLog.FINER, "Signature verification failed.");
                    return;
                }

                this.setIsSecure(true);
                this.setRemoteLongTermPublicKey(localRemoteLongTermPublicKey);
                break;
            default:
                Timber.log(TimberLog.FINER, "We were not expecting a signature, ignoring message.");
                break;
        }
    }

    private void handleRevealSignatureMessage(RevealSignatureMessage m)
            throws OtrException
    {
        switch (this.getAuthenticationState()) {
            case AWAITING_REVEALSIG:
                // Use the received value of r to decrypt the value of gx received
                // in the D-H Commit Message, and verify the hash therein.
                // Decrypt the encrypted signature, and verify the signature and the MACs.
                // If everything checks out:

                // * Reply with a Signature Message.
                // * Transition authstate to AUTHSTATE_NONE.
                // * Transition msgstate to MSGSTATE_ENCRYPTED.
                // * TODO If there is a recent stored message, encrypt it and send it as a Data Message.

                OtrCryptoEngine otrCryptoEngine = new OtrCryptoEngineImpl();
                // Uses r to decrypt the value of gx sent earlier
                byte[] remoteDHPublicKeyDecrypted = otrCryptoEngine.aesDecrypt(m.revealedKey,
                        null, this.getRemoteDHPublicKeyEncrypted());

                // Verifies that HASH(gx) matches the value sent earlier
                byte[] remoteDHPublicKeyHash = otrCryptoEngine.sha256Hash(remoteDHPublicKeyDecrypted);
                if (!Arrays.equals(remoteDHPublicKeyHash, this.getRemoteDHPublicKeyHash())) {
                    Timber.log(TimberLog.FINER, "Hashes don't match, ignoring message.");
                    return;
                }

                // Verifies that Bob's gx is a legal value (2 <= gx <= modulus-2)
                BigInteger remoteDHPublicKeyMpi;
                try {
                    remoteDHPublicKeyMpi = SerializationUtils.readMpi(remoteDHPublicKeyDecrypted);
                } catch (IOException e) {
                    throw new OtrException(e);
                }

                this.setRemoteDHPublicKey(otrCryptoEngine.getDHPublicKey(remoteDHPublicKeyMpi));

                // Verify received Data.
                if (!m.verify(this.getM2())) {
                    Timber.log(TimberLog.FINER, "Signature MACs are not equal, ignoring message.");
                    return;
                }

                // Decrypt X.
                byte[] remoteXDecrypted = m.decrypt(this.getC());
                SignatureX remoteX;
                try {
                    remoteX = SerializationUtils.toMysteriousX(remoteXDecrypted);
                } catch (IOException e) {
                    throw new OtrException(e);
                }

                // Compute signature.
                PublicKey remoteLongTermPublicKey = remoteX.longTermPublicKey;
                SignatureM remoteM = new SignatureM(this.getRemoteDHPublicKey(),
                        (DHPublicKey) this.getLocalDHKeyPair().getPublic(),
                        remoteLongTermPublicKey, remoteX.dhKeyID);

                // Verify signature.
                byte[] signature;
                try {
                    signature = otrCryptoEngine.sha256Hmac(SerializationUtils.toByteArray(remoteM), this.getM1());
                } catch (IOException e) {
                    throw new OtrException(e);
                }

                if (!otrCryptoEngine.verify(signature, remoteLongTermPublicKey, remoteX.signature)) {
                    Timber.log(TimberLog.FINER, "Signature verification failed.");
                    return;
                }
                Timber.log(TimberLog.FINER, "Signature verification succeeded.");

                this.setAuthenticationState(AuthContext.NONE);
                this.setIsSecure(true);
                this.setRemoteLongTermPublicKey(remoteLongTermPublicKey);
                getSession().injectMessage(messageFactory.getSignatureMessage());
                break;
            default:
                Timber.log(TimberLog.FINER, "Ignoring message.");
                break;
        }
    }

    private void handleDHKeyMessage(DHKeyMessage m)
            throws OtrException
    {
        getSession().setReceiverInstanceTag(new InstanceTag(m.senderInstanceTag));
        switch (this.getAuthenticationState()) {
            case NONE:
            case AWAITING_DHKEY:
                // Reply with a Reveal Signature Message and transition authstate to AUTHSTATE_AWAITING_SIG
                this.setRemoteDHPublicKey(m.dhPublicKey);
                this.setAuthenticationState(AuthContext.AWAITING_SIG);
                getSession().injectMessage(messageFactory.getRevealSignatureMessage());
                Timber.log(TimberLog.FINER, "Sent Reveal Signature.");
                break;

            case AWAITING_SIG:
                if (m.dhPublicKey.getY().equals(this.getRemoteDHPublicKey().getY())) {
                    // If this D-H Key message is the same the one you received
                    // earlier (when you entered AUTHSTATE_AWAITING_SIG):
                    // Retransmit your Reveal Signature Message.
                    getSession().injectMessage(messageFactory.getRevealSignatureMessage());
                    Timber.log(TimberLog.FINER, "Resent Reveal Signature.");
                }
                else {
                    // Otherwise: Ignore the message.
                    Timber.log(TimberLog.FINER, "Ignoring message.");
                }
                break;
            default:
                // Ignore the message
                break;
        }
    }

    private void handleDHCommitMessage(DHCommitMessage m)
            throws OtrException
    {
        getSession().setReceiverInstanceTag(new InstanceTag(m.senderInstanceTag));
        switch (this.getAuthenticationState()) {
            case NONE:
                // Reply with a D-H Key Message, and transition authstate to AUTHSTATE_AWAITING_REVEALSIG.
                this.reset();
                getSession().setProtocolVersion(m.protocolVersion);
                this.setRemoteDHPublicKeyEncrypted(m.dhPublicKeyEncrypted);
                this.setRemoteDHPublicKeyHash(m.dhPublicKeyHash);
                this.setAuthenticationState(AuthContext.AWAITING_REVEALSIG);
                getSession().injectMessage(messageFactory.getDHKeyMessage());
                Timber.log(TimberLog.FINER, "Sent D-H key.");
                break;

            case AWAITING_DHKEY:
                // This is the trickiest transition in the whole protocol. It
                // indicates that you have already sent a D-H Commit message to your
                // correspondent, but that he either didn't receive it, or just
                // didn't receive it yet, and has sent you one as well. The symmetry
                // will be broken by comparing the hashed gx you sent in your D-H
                // Commit Message with the one you received, considered as 32-byte
                // unsigned big-endian values.
                BigInteger ourHash = new BigInteger(1, this.getLocalDHPublicKeyHash());
                BigInteger theirHash = new BigInteger(1, m.dhPublicKeyHash);

    			if (theirHash.compareTo(ourHash) == -1) {
                    // Ignore the incoming D-H Commit message, but resend your D-H
                    // Commit message.
                    getSession().injectMessage(messageFactory.getDHCommitMessage());
                    Timber.log(TimberLog.FINER, "Ignored the incoming D-H Commit message," +
                            " but resent our D-H Commit message.");
                }
                else {
                    // *Forget* your old gx value that you sent (encrypted) earlier,
                    // and pretend you're in AUTHSTATE_NONE; i.e. reply with a D-H
                    // Key Message, and transition authstate to
                    // AUTHSTATE_AWAITING_REVEALSIG.
                    this.reset();
                    getSession().setProtocolVersion(m.protocolVersion);
                    this.setRemoteDHPublicKeyEncrypted(m.dhPublicKeyEncrypted);
                    this.setRemoteDHPublicKeyHash(m.dhPublicKeyHash);
                    this.setAuthenticationState(AuthContext.AWAITING_REVEALSIG);
                    getSession().injectMessage(messageFactory.getDHKeyMessage());
                    Timber.log(TimberLog.FINER, "Forgot our old gx value that we sent (encrypted) earlier," +
                            " and pretended we're in AUTHSTATE_NONE -> Sent D-H key.");
                }
                break;

            case AWAITING_REVEALSIG:
                // Retransmit your D-H Key Message (the same one as you sent when
                // you entered AUTHSTATE_AWAITING_REVEALSIG). Forget the old D-H
                // Commit message, and use this new one instead.
                this.setRemoteDHPublicKeyEncrypted(m.dhPublicKeyEncrypted);
                this.setRemoteDHPublicKeyHash(m.dhPublicKeyHash);
                getSession().injectMessage(messageFactory.getDHKeyMessage());
                Timber.log(TimberLog.FINER, "Sent D-H key.");
                break;
            case AWAITING_SIG:
                // Reply with a new D-H Key message, and transition authstate to AUTHSTATE_AWAITING_REVEALSIG
                this.reset();
                this.setRemoteDHPublicKeyEncrypted(m.dhPublicKeyEncrypted);
                this.setRemoteDHPublicKeyHash(m.dhPublicKeyHash);
                this.setAuthenticationState(AuthContext.AWAITING_REVEALSIG);
                getSession().injectMessage(messageFactory.getDHKeyMessage());
                Timber.log(TimberLog.FINER, "Sent D-H key.");
                break;
            case V1_SETUP:
                // fall-through
            default:
                throw new UnsupportedOperationException("Can not handle message in auth. state "
                        + getAuthenticationState());
        }
    }

    @Override
    public void startAuth()
            throws OtrException
    {
        Timber.log(TimberLog.FINER, "Starting Authenticated Key Exchange, sending query message");
        getSession().injectMessage(messageFactory.getQueryMessage());
    }

    @Override
    public DHCommitMessage respondAuth(Integer version)
            throws OtrException
    {
        if (version != OTRv.TWO && version != OTRv.THREE)
            throw new OtrException(new Exception("Only allowed versions are: 2, 3"));

        Timber.log(TimberLog.FINER, "Responding to Query Message");
        this.reset();
        getSession().setProtocolVersion(version);
        this.setAuthenticationState(AuthContext.AWAITING_DHKEY);
        Timber.log(TimberLog.FINER, "Generating D-H Commit.");
        DHCommitMessage message = messageFactory.getDHCommitMessage();
        return message;
    }

    private void setSession(Session session)
    {
        this.session = session;
    }

    private Session getSession()
    {
        return session;
    }

    @Override
    public PublicKey getRemoteLongTermPublicKey()
    {
        return remoteLongTermPublicKey;
    }

    private void setRemoteLongTermPublicKey(PublicKey pubKey)
    {
        this.remoteLongTermPublicKey = pubKey;
    }
}
