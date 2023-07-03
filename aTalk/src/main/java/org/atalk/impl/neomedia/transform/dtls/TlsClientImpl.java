/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.transform.dtls;

import org.atalk.impl.neomedia.transform.SinglePacketTransformer;
import org.bouncycastle.crypto.params.AsymmetricKeyParameter;
import org.bouncycastle.tls.AlertDescription;
import org.bouncycastle.tls.Certificate;
import org.bouncycastle.tls.CertificateRequest;
import org.bouncycastle.tls.DefaultTlsClient;
import org.bouncycastle.tls.ProtocolVersion;
import org.bouncycastle.tls.SignatureAndHashAlgorithm;
import org.bouncycastle.tls.TlsAuthentication;
import org.bouncycastle.tls.TlsClientContext;
import org.bouncycastle.tls.TlsCredentials;
import org.bouncycastle.tls.TlsFatalAlert;
import org.bouncycastle.tls.TlsSRTPUtils;
import org.bouncycastle.tls.TlsServerCertificate;
import org.bouncycastle.tls.TlsUtils;
import org.bouncycastle.tls.UseSRTPData;
import org.bouncycastle.tls.crypto.TlsCrypto;
import org.bouncycastle.tls.crypto.TlsCryptoParameters;
import org.bouncycastle.tls.crypto.impl.bc.BcDefaultTlsCredentialedSigner;
import org.bouncycastle.tls.crypto.impl.bc.BcTlsCrypto;

import java.io.IOException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Hashtable;

import timber.log.Timber;

/**
 * Implements {@link TlsClientContext} for the purposes of supporting DTLS-SRTP - DTLSv12/DTLSv10.
 *
 * @author Lyubomir Marinov
 * @author Eng Chong Meng
 */
public class TlsClientImpl extends DefaultTlsClient
{
    private final TlsAuthentication authentication = new TlsAuthenticationImpl();

    /**
     * The <code>SRTPProtectionProfile</code> negotiated between this DTLS-SRTP client and its server.
     */
    private int mChosenProtectionProfile;

    /**
     * The SRTP Master Key Identifier (MKI) used by the <code>SrtpCryptoContext</code> associated with
     * this instance. Since the <code>SrtpCryptoContext</code> class does not utilize it, the value is
     * {@link TlsUtils#EMPTY_BYTES}.
     */
    private final byte[] mki = TlsUtils.EMPTY_BYTES;

    /**
     * The <code>PacketTransformer</code> which has initialized this instance.
     */
    private final DtlsPacketTransformer mPacketTransformer;

    /**
     * Initializes a new <code>TlsClientImpl</code> instance.
     *
     * @param packetTransformer the <code>PacketTransformer</code> which is initializing the new instance
     */
    public TlsClientImpl(DtlsPacketTransformer packetTransformer)
    {
        super(new BcTlsCrypto(new SecureRandom()));
        mPacketTransformer = packetTransformer;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public synchronized TlsAuthentication getAuthentication()
    {
        return authentication;
    }

    /**
     * {@inheritDoc}
     * The implementation of <code>TlsClientImpl</code> always returns <code>ProtocolVersion.DTLSv12 & DTLSv10</code>
     */
    @Override
    protected ProtocolVersion[] getSupportedVersions()
    {
        return ProtocolVersion.DTLSv12.downTo(ProtocolVersion.DTLSv10);
    }

    /**
     * {@inheritDoc}
     *
     * Includes the <code>use_srtp</code> extension in the DTLS extended client hello.
     */
    @Override
    public Hashtable getClientExtensions()
            throws IOException
    {
        Hashtable clientExtensions = super.getClientExtensions();

        if (!isSrtpDisabled()
                && TlsSRTPUtils.getUseSRTPExtension(clientExtensions) == null) {
            if (clientExtensions == null)
                clientExtensions = new Hashtable();
            TlsSRTPUtils.addUseSRTPExtension(clientExtensions,
                    new UseSRTPData(DtlsControlImpl.SRTP_PROTECTION_PROFILES, mki));
        }
        return clientExtensions;
    }

    /**
     * Determines whether this {@code TlsClientImpl} is to operate in pure DTLS
     * mode without SRTP extensions or in DTLS/SRTP mode.
     *
     * @return {@code true} for pure DTLS without SRTP extensions or {@code false} for DTLS/SRTP
     */
    private boolean isSrtpDisabled()
    {
        return mPacketTransformer.getProperties().isSrtpDisabled();
    }

    /**
     * {@inheritDoc}
     *
     * Forwards to {@link #mPacketTransformer}.
     */
    @Override
    public void notifyAlertRaised(short alertLevel, short alertDescription, String message, Throwable cause)
    {
        mPacketTransformer.notifyAlertRaised(this, alertLevel, alertDescription, message, cause);
    }

    @Override
    public void notifyHandshakeComplete()
    {
        if (isSrtpDisabled()) {
            // SRTP is disabled, nothing to do. Why did we get here in the first place?
            return;
        }

        SinglePacketTransformer srtpTransformer
                = mPacketTransformer.initializeSRTPTransformer(mChosenProtectionProfile, context);
        synchronized (mPacketTransformer) {
            mPacketTransformer.setSrtpTransformer(srtpTransformer);
        }
    }

    /**
     * {@inheritDoc}
     *
     * Makes sure the DTLS extended server hello contains the <code>use_srtp</code> extension.
     */
    @Override
    @SuppressWarnings("rawtypes")
    public void processServerExtensions(Hashtable serverExtensions)
            throws IOException
    {
        if (isSrtpDisabled()) {
            super.processServerExtensions(serverExtensions);
            return;
        }

        UseSRTPData useSRTPData = TlsSRTPUtils.getUseSRTPExtension(serverExtensions);

        if (useSRTPData == null) {
            String msg = "DTLS extended server hello does not include the use_srtp extension!";
            IOException ioe = new IOException(msg);

            Timber.e(ioe, "%s", msg);
            throw ioe;
        }
        else {
            int[] protectionProfiles = useSRTPData.getProtectionProfiles();
            int chosenProtectionProfile = (protectionProfiles.length == 1)
                    ? DtlsControlImpl.chooseSRTPProtectionProfile(protectionProfiles[0]) : 0;

            if (chosenProtectionProfile == 0) {
                String msg = "No chosen SRTP protection profile!";
                TlsFatalAlert tfa = new TlsFatalAlert(AlertDescription.illegal_parameter);

                Timber.e(tfa, "%s", msg);
                throw tfa;
            }
            else {
                /*
                 * If the client detects a nonzero-length MKI in the server's response that is
                 * different than the one the client offered, then the client MUST abort the
                 * handshake and SHOULD send an invalid_parameter alert.
                 */
                byte[] mki = useSRTPData.getMki();
                if (Arrays.equals(mki, this.mki)) {
                    super.processServerExtensions(serverExtensions);

                    mChosenProtectionProfile = chosenProtectionProfile;
                }
                else {
                    String msg = "Server's MKI does not match the one offered by this client!";
                    TlsFatalAlert tfa = new TlsFatalAlert(AlertDescription.illegal_parameter);

                    Timber.e(tfa, "%s", msg);
                    throw tfa;
                }
            }
        }
    }

    /**
     * Implements {@link TlsAuthentication} for the purposes of supporting DTLS-SRTP.
     */
    private class TlsAuthenticationImpl implements TlsAuthentication
    {
        private TlsCredentials clientCredentials;

        /**
         * {@inheritDoc}
         */
        @Override
        public TlsCredentials getClientCredentials(CertificateRequest certificateRequest)
        {
            if (clientCredentials == null) {
                // get the certInfo for the certificate that was used in Jingle session-accept setup
                CertificateInfo certInfo = mPacketTransformer.getDtlsControl().getCertificateInfo();
                Certificate certificate = certInfo.getCertificate();

                SignatureAndHashAlgorithm sigAndHashAlg = TlsServerImpl.getSigAndHashAlg(certificate);
                if (sigAndHashAlg == null)
                    return null;

                TlsCrypto crypto = context.getCrypto();
                TlsCryptoParameters cryptoParams = new TlsCryptoParameters(context);
                AsymmetricKeyParameter privateKey = certInfo.getKeyPair().getPrivate();

                clientCredentials = new BcDefaultTlsCredentialedSigner(
                        cryptoParams, (BcTlsCrypto) crypto, privateKey, certificate, sigAndHashAlg);
            }
            return clientCredentials;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void notifyServerCertificate(TlsServerCertificate serverCertificate)
                throws IOException
        {
            try {
                mPacketTransformer.getDtlsControl().verifyAndValidateCertificate(serverCertificate.getCertificate());
            } catch (Exception e) {
                Timber.e(e, "Failed to verify and/or validate server certificate!");
                if (e instanceof IOException)
                    throw (IOException) e;
                else
                    throw new IOException(e);
            }
        }
    }
}
