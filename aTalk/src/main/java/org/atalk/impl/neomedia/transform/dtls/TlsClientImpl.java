/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.transform.dtls;

import org.bouncycastle.crypto.params.AsymmetricKeyParameter;
import org.bouncycastle.tls.*;
import org.bouncycastle.tls.crypto.*;
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
     * The <tt>SRTPProtectionProfile</tt> negotiated between this DTLS-SRTP client and its server.
     */
    private int chosenProtectionProfile;

    /**
     * The SRTP Master Key Identifier (MKI) used by the <tt>SrtpCryptoContext</tt> associated with
     * this instance. Since the <tt>SrtpCryptoContext</tt> class does not utilize it, the value is
     * {@link TlsUtils#EMPTY_BYTES}.
     */
    private final byte[] mki = TlsUtils.EMPTY_BYTES;

    /**
     * The <tt>PacketTransformer</tt> which has initialized this instance.
     */
    private final DtlsPacketTransformer packetTransformer;

    /**
     * Initializes a new <tt>TlsClientImpl</tt> instance.
     *
     * @param packetTransformer the <tt>PacketTransformer</tt> which is initializing the new instance
     */
    public TlsClientImpl(DtlsPacketTransformer packetTransformer)
    {
        super(new BcTlsCrypto(new SecureRandom()));
        this.packetTransformer = packetTransformer;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public synchronized TlsAuthentication getAuthentication()
            throws IOException
    {
        return authentication;
    }

    /**
     * Gets the <tt>SRTPProtectionProfile</tt> negotiated between this DTLS-SRTP client and its server.
     *
     * @return the <tt>SRTPProtectionProfile</tt> negotiated between this DTLS-SRTP client and its server
     */
    private int getChosenProtectionProfile()
    {
        return chosenProtectionProfile;
    }

    /**
     * {@inheritDoc}
     * The implementation of <tt>TlsClientImpl</tt> always returns <tt>ProtocolVersion.DTLSv12 & DTLSv10</tt>
     */
    @Override
    protected ProtocolVersion[] getSupportedVersions()
    {
        return ProtocolVersion.DTLSv12.downTo(ProtocolVersion.DTLSv10);
    }

    /**
     * {@inheritDoc}
     *
     * Includes the <tt>use_srtp</tt> extension in the DTLS extended client hello.
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
     * Gets the <tt>TlsContext</tt> with which this <tt>TlsClient</tt> has been initialized.
     *
     * @return the <tt>TlsContext</tt> with which this <tt>TlsClient</tt> has been initialized
     */
    TlsContext getContext()
    {
        return context;
    }

    /**
     * Gets the <tt>DtlsControl</tt> implementation associated with this instance.
     *
     * @return the <tt>DtlsControl</tt> implementation associated with this instance
     */
    private DtlsControlImpl getDtlsControl()
    {
        return packetTransformer.getDtlsControl();
    }

    private Properties getProperties()
    {
        return packetTransformer.getProperties();
    }

    /**
     * {@inheritDoc}
     *
     * Overrides the super implementation as a simple means of detecting that the security-related
     * negotiations between the local and the remote enpoints are starting. The detection carried
     * out for the purposes of <tt>SrtpListener</tt>.
     */
    @Override
    public void init(TlsClientContext context)
    {
        super.init(context);
    }

    /**
     * Determines whether this {@code TlsClientImpl} is to operate in pure DTLS
     * mode without SRTP extensions or in DTLS/SRTP mode.
     *
     * @return {@code true} for pure DTLS without SRTP extensions or {@code false} for DTLS/SRTP
     */
    private boolean isSrtpDisabled()
    {
        return getProperties().isSrtpDisabled();
    }

    /**this
     * {@inheritDoc}
     *
     * Forwards to {@link #packetTransformer}.
     */
    @Override
    public void notifyAlertRaised(short alertLevel, short alertDescription, String message, Throwable cause)
    {
        packetTransformer.notifyAlertRaised(this, alertLevel, alertDescription, message, cause);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void notifyHandshakeComplete()
            throws IOException
    {
        super.notifyHandshakeComplete();
        packetTransformer.initializeSRTPTransformer(getChosenProtectionProfile(), context);
    }

    /**
     * {@inheritDoc}
     *
     * Makes sure the DTLS extended server hello contains the <tt>use_srtp</tt> extension.
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

                    this.chosenProtectionProfile = chosenProtectionProfile;
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
                throws IOException
        {
            if (clientCredentials == null) {
                short[] certificateTypes = certificateRequest.getCertificateTypes();
                if (certificateTypes == null || !org.bouncycastle.util.Arrays.contains(certificateTypes, ClientCertificateType.rsa_sign)) {
                    return null;
                }

                TlsCrypto crypto = context.getCrypto();
                TlsCryptoParameters cryptoParams = new TlsCryptoParameters(context);

                CertificateInfo certInfo = getDtlsControl().getCertificateInfo();
                AsymmetricKeyParameter privateKey = certInfo.getKeyPair().getPrivate();
                Certificate certificate = certInfo.getCertificate();

                // FIXME The signature and hash algorithms should be retrieved from the certificate.
                SignatureAndHashAlgorithm signatureAndHashAlgorithm
                        = new SignatureAndHashAlgorithm(HashAlgorithm.sha256, SignatureAlgorithm.rsa);

                clientCredentials = new BcDefaultTlsCredentialedSigner(cryptoParams, (BcTlsCrypto) crypto,
                        privateKey, certificate, signatureAndHashAlgorithm);
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
                getDtlsControl().verifyAndValidateCertificate(serverCertificate.getCertificate());
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
