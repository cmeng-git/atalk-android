package net.java.otr4j.session;

import net.java.otr4j.OtrEngineHost;
import net.java.otr4j.OtrException;
import net.java.otr4j.crypto.*;
import net.java.otr4j.crypto.SM.SMException;
import net.java.otr4j.crypto.SM.SMState;
import net.java.otr4j.io.OtrOutputStream;

import java.io.*;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.ArrayList;
import java.util.List;

public class OtrSm
{
    private SMState smstate;
    private OtrEngineHost engineHost;
    private Session session;

    /**
     * Construct an OTR Socialist Millionaire handler object.
     *
     * @param session The session reference.
     * @param engineHost The host where we can present messages or ask for the shared secret.
     */
    public OtrSm(Session session, OtrEngineHost engineHost)
    {
        this.session = session;
        this.engineHost = engineHost;
        reset();
    }

    public void reset()
    {
        smstate = new SMState();
    }

    /* Compute secret session ID as hash of agreed secret */
    private static byte[] computeSessionId(BigInteger s)
            throws SMException
    {
        byte[] sdata;
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            OtrOutputStream oos = new OtrOutputStream(out);
            oos.write(0x00);
            oos.writeBigInt(s);
            sdata = out.toByteArray();
            oos.close();
        } catch (IOException e1) {
            throw new SMException(e1);
        }

        /* Calculate the session id */
        MessageDigest sha256;
        try {
            sha256 = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new SMException("cannot find SHA-256");
        }
        byte[] res = sha256.digest(sdata);
        byte[] secureSessionId = new byte[8];
        System.arraycopy(res, 0, secureSessionId, 0, 8);
        return secureSessionId;

    }

    /**
     * Respond to or initiate an SMP negotiation
     *
     * @param question The question to present to the peer, if initiating. May be {@code null} for no
     * question. If not initiating, then it should be received question in order to clarify
     * whether this is shared secret verification.
     * @param secret The secret.
     * @param initiating Whether we are initiating or responding to an initial request.
     * @return TLVs to send to the peer
     * @throws OtrException MVN_PASS_JAVADOC_INSPECTION
     */
    public List<TLV> initRespondSmp(String question, String secret, boolean initiating)
            throws OtrException
    {
        if (!initiating && !smstate.asked)
            throw new OtrException(
                    new IllegalStateException("There is no question to be answered."));

        /*
         * Construct the combined secret as a SHA256 hash of: Version byte (0x01), Initiator
         * fingerprint (20 bytes), responder fingerprint (20 bytes), secure session id, input secret
         */
        byte[] ourFp = engineHost.getLocalFingerprintRaw(session.getSessionID());
        byte[] theirFp;
        PublicKey remotePublicKey = session.getRemotePublicKey();
        try {
            theirFp = new OtrCryptoEngineImpl().getFingerprintRaw(remotePublicKey);
        } catch (OtrCryptoException e) {
            throw new OtrException(e);
        }

        byte[] sessionId;
        try {
            sessionId = computeSessionId(session.getS());
        } catch (SMException ex) {
            throw new OtrException(ex);
        }

        int combinedBufLen = 41 + sessionId.length + secret.length();
        byte[] combinedBuf = new byte[combinedBufLen];
        combinedBuf[0] = 1;
        if (initiating) {
            System.arraycopy(ourFp, 0, combinedBuf, 1, 20);
            System.arraycopy(theirFp, 0, combinedBuf, 21, 20);
        }
        else {
            System.arraycopy(theirFp, 0, combinedBuf, 1, 20);
            System.arraycopy(ourFp, 0, combinedBuf, 21, 20);
        }
        System.arraycopy(sessionId, 0, combinedBuf, 41, sessionId.length);
        System.arraycopy(secret.getBytes(), 0,
                combinedBuf, 41 + sessionId.length, secret.length());

        MessageDigest sha256;
        try {
            sha256 = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException ex) {
            throw new OtrException(ex);
        }

        byte[] combinedSecret = sha256.digest(combinedBuf);
        byte[] smpmsg;
        try {
            if (initiating) {
                smpmsg = SM.step1(smstate, combinedSecret);
            }
            else {
                smpmsg = SM.step2b(smstate, combinedSecret);
            }
        } catch (SMException ex) {
            throw new OtrException(ex);
        }

        // If we've got a question, attach it to the smpmsg
        if (question != null && initiating) {
            final byte[] bytes;
            bytes = question.getBytes(StandardCharsets.UTF_8);
            byte[] qsmpmsg = new byte[bytes.length + 1 + smpmsg.length];
            System.arraycopy(bytes, 0, qsmpmsg, 0, bytes.length);
            System.arraycopy(smpmsg, 0, qsmpmsg, bytes.length + 1, smpmsg.length);
            smpmsg = qsmpmsg;
        }

        TLV sendtlv = new TLV(
                initiating ? (question != null ? TLV.SMP1Q : TLV.SMP1) : TLV.SMP2, smpmsg);
        smstate.nextExpected = initiating ? SM.EXPECT2 : SM.EXPECT3;
        smstate.approved = initiating || question == null;
        return makeTlvList(sendtlv);
    }

    /**
     * Create an abort TLV and reset our state.
     *
     * @return TLVs to send to the peer
     * @throws OtrException MVN_PASS_JAVADOC_INSPECTION
     */
    public List<TLV> abortSmp()
            throws OtrException
    {
        TLV sendtlv = new TLV(TLV.SMP_ABORT, new byte[0]);
        smstate.nextExpected = SM.EXPECT1;
        return makeTlvList(sendtlv);
    }

    public boolean isSmpInProgress()
    {
        return smstate.nextExpected > SM.EXPECT1;
    }

    public boolean doProcessTlv(TLV tlv)
            throws OtrException
    {
        /* If TLVs contain SMP data, process it */
        int nextMsg = smstate.nextExpected;
        int tlvType = tlv.getType();

        PublicKey pubKey = session.getRemotePublicKey();
        String fingerprint = null;
        try {
            fingerprint = new OtrCryptoEngineImpl().getFingerprint(pubKey);
        } catch (OtrCryptoException e) {
            e.printStackTrace();
        }

        if (tlvType == TLV.SMP1Q && nextMsg == SM.EXPECT1) {
            /*
             * We can only do the verification half now. We must wait for the secret to be entered to continue.
             */
            byte[] question = tlv.getValue();
            int qlen = 0;
            for (; qlen != question.length && question[qlen] != 0; qlen++) {
            }
            if (qlen == question.length)
                qlen = 0;
            else
                qlen++;
            byte[] input = new byte[question.length - qlen];
            System.arraycopy(question, qlen, input, 0, question.length - qlen);
            try {
                SM.step2a(smstate, input, 1);
            } catch (SMException e) {
                throw new OtrException(e);
            }
            if (qlen != 0)
                qlen--;
            byte[] plainq = new byte[qlen];
            System.arraycopy(question, 0, plainq, 0, qlen);
            if (smstate.smProgState != SM.PROG_CHEATED) {
                smstate.asked = true;
                String questionUTF;
                questionUTF = new String(plainq, StandardCharsets.UTF_8);
                engineHost.askForSecret(session.getSessionID(), session.getReceiverInstanceTag(), questionUTF);
            }
            else {
                engineHost.smpError(session.getSessionID(), tlvType, true);
                reset();
            }
        }
        else if (tlvType == TLV.SMP1Q) {
            engineHost.smpError(session.getSessionID(), tlvType, false);
        }
        else if (tlvType == TLV.SMP1 && nextMsg == SM.EXPECT1) {
            /*
             * We can only do the verification half now. We must wait for the secret to be entered to continue.
             */
            try {
                SM.step2a(smstate, tlv.getValue(), 0);
            } catch (SMException e) {
                throw new OtrException(e);
            }
            if (smstate.smProgState != SM.PROG_CHEATED) {
                smstate.asked = true;
                engineHost.askForSecret(session.getSessionID(), session.getReceiverInstanceTag(), null);
            }
            else {
                engineHost.smpError(session.getSessionID(), tlvType, true);
                reset();
            }
        }
        else if (tlvType == TLV.SMP1) {
            engineHost.smpError(session.getSessionID(), tlvType, false);
        }
        else if (tlvType == TLV.SMP2 && nextMsg == SM.EXPECT2) {
            byte[] nextmsg;
            try {
                nextmsg = SM.step3(smstate, tlv.getValue());
            } catch (SMException e) {
                throw new OtrException(e);
            }
            if (smstate.smProgState != SM.PROG_CHEATED) {
                /* Send msg with next smp msg content */
                TLV sendtlv = new TLV(TLV.SMP3, nextmsg);
                smstate.nextExpected = SM.EXPECT4;
                String[] msg = session.transformSending("", makeTlvList(sendtlv));
                for (String part : msg) {
                    engineHost.injectMessage(session.getSessionID(), part);
                }
            }
            else {
                engineHost.smpError(session.getSessionID(), tlvType, true);
                reset();
            }
        }
        else if (tlvType == TLV.SMP2) {
            engineHost.smpError(session.getSessionID(), tlvType, false);
        }
        else if (tlvType == TLV.SMP3 && nextMsg == SM.EXPECT3) {
            byte[] nextmsg;
            try {
                nextmsg = SM.step4(smstate, tlv.getValue());
            } catch (SMException e) {
                throw new OtrException(e);
            }

            /* Set trust level based on result */
            if (smstate.smProgState == SM.PROG_SUCCEEDED) {
                engineHost.verify(session.getSessionID(), fingerprint, smstate.approved);
            }
            else {
                engineHost.unverify(session.getSessionID(), fingerprint);
            }
            if (smstate.smProgState != SM.PROG_CHEATED) {
                /* Send msg with next smp msg content */
                TLV sendtlv = new TLV(TLV.SMP4, nextmsg);
                String[] msg = session.transformSending("", makeTlvList(sendtlv));
                for (String part : msg) {
                    engineHost.injectMessage(session.getSessionID(), part);
                }
            }
            else {
                engineHost.smpError(session.getSessionID(), tlvType, true);
            }
            reset();
        }
        else if (tlvType == TLV.SMP3) {
            engineHost.smpError(session.getSessionID(), tlvType, false);
        }
        else if (tlvType == TLV.SMP4 && nextMsg == SM.EXPECT4) {

            try {
                SM.step5(smstate, tlv.getValue());
            } catch (SMException e) {
                throw new OtrException(e);
            }
            if (smstate.smProgState == SM.PROG_SUCCEEDED) {
                engineHost.verify(session.getSessionID(), fingerprint, smstate.approved);
            }
            else {
                engineHost.unverify(session.getSessionID(), fingerprint);
            }
            if (smstate.smProgState == SM.PROG_CHEATED) {
                engineHost.smpError(session.getSessionID(), tlvType, true);
            }
            reset();

        }
        else if (tlvType == TLV.SMP4) {
            engineHost.smpError(session.getSessionID(), tlvType, false);
        }
        else if (tlvType == TLV.SMP_ABORT) {
            engineHost.smpAborted(session.getSessionID());
            reset();
        }
        else
            return false;
        return true;
    }

    private List<TLV> makeTlvList(TLV sendtlv)
    {
        List<TLV> tlvs = new ArrayList<>(1);
        tlvs.add(sendtlv);
        return tlvs;
    }
}
