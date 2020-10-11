/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.transform.dtls;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.crypto.params.AsymmetricKeyParameter;
import org.bouncycastle.tls.*;
import org.bouncycastle.tls.crypto.*;
import org.bouncycastle.tls.crypto.impl.bc.*;

import java.io.IOException;
import java.security.SecureRandom;
import java.util.Hashtable;
import java.util.Vector;

import timber.log.Timber;

/**
 * Implements {@link TlsServer} for the purposes of supporting DTLS-SRTP - DTLSv12/DTLSv10.
 *
 * @author Lyubomir Marinov
 * @author Eng Chong Meng
 */
public class TlsServerImpl extends DefaultTlsServer
{
    /**
     * @see TlsServer#getCertificateRequest()
     */
    private CertificateRequest certificateRequest = null;

    /**
     * If DTLSv12 or higher is negotiated, configures the set of supported signature algorithms in the
     * CertificateRequest (if one is sent). If null, uses the default set.
     */
    private Vector serverCertReqSigAlgs = null;

    /**
     * The <tt>SRTPProtectionProfile</tt> negotiated between this DTLS-SRTP server and its client.
     */
    private int chosenProtectionProfile;

    /**
     * The <tt>PacketTransformer</tt> which has initialized this instance.
     */
    private final DtlsPacketTransformer packetTransformer;

    /**
     * @see DefaultTlsServer#getRSAEncryptionCredentials()
     */
    private TlsCredentialedDecryptor rsaEncryptionCredentials;

    /**
     * @see DefaultTlsServer#getRSASignerCredentials()
     */
    private TlsCredentialedSigner rsaSignerCredentials;

    /**
     * Initializes a new <tt>TlsServerImpl</tt> instance.
     *
     * @param packetTransformer the <tt>PacketTransformer</tt> which is initializing the new instance
     */
    public TlsServerImpl(DtlsPacketTransformer packetTransformer)
    {
        super(new BcTlsCrypto(new SecureRandom()));
        this.packetTransformer = packetTransformer;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CertificateRequest getCertificateRequest()
    {
        if (certificateRequest == null) {
            short[] certificateTypes = new short[]{ ClientCertificateType.rsa_sign,
                    ClientCertificateType.dss_sign, ClientCertificateType.ecdsa_sign};


            Vector serverSigAlgs = null;
            if (TlsUtils.isSignatureAlgorithmsExtensionAllowed(context.getServerVersion())) {
                serverSigAlgs = serverCertReqSigAlgs;
                if (serverSigAlgs == null) {
                    serverSigAlgs = TlsUtils.getDefaultSupportedSignatureAlgorithms(context);
                }
            }

//            Certificate certificate = getDtlsControl().getCertificateInfo().getCertificate();
//            TlsCertificate[] chain = certificate.getCertificateList();
//            try {
//                for (TlsCertificate tlsCertificate : chain) {
//                    org.bouncycastle.asn1.x509.Certificate entry = org.bouncycastle.asn1.x509.Certificate.getInstance(tlsCertificate.getEncoded());
//                    certificateAuthorities.addElement(entry.getIssuer());
//                }
//            } catch (IOException e) {
//                e.printStackTrace();
//            }

            Vector certificateAuthorities = new Vector();
            certificateAuthorities.addElement(new X500Name("CN=atalk.org TLS CA"));

            certificateRequest = new CertificateRequest(certificateTypes, serverSigAlgs, certificateAuthorities);
        }
        return certificateRequest;
    }

    /**
     * Gets the <tt>SRTPProtectionProfile</tt> negotiated between this DTLS-SRTP server and its client.
     *
     * @return the <tt>SRTPProtectionProfile</tt> negotiated between this DTLS-SRTP server and its client
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
     * Gets the <tt>TlsContext</tt> with which this <tt>TlsServer</tt> has been initialized.
     *
     * @return the <tt>TlsContext</tt> with which this <tt>TlsServer</tt> has been initialized
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
     * <p>
     * Depending on the <tt>selectedCipherSuite</tt>, <tt>DefaultTlsServer</tt> will require either
     * <tt>rsaEncryptionCredentials</tt> or <tt>rsaSignerCredentials</tt> neither of which is
     * implemented by <tt>DefaultTlsServer</tt>.
     */
    @Override
    protected TlsCredentialedDecryptor getRSAEncryptionCredentials()
    {
        if (rsaEncryptionCredentials == null) {
            TlsCrypto crypto = context.getCrypto();

            CertificateInfo certInfo = getDtlsControl().getCertificateInfo();
            Certificate certificate = certInfo.getCertificate();
            AsymmetricKeyParameter privateKey = certInfo.getKeyPair().getPrivate();

            rsaEncryptionCredentials = new BcDefaultTlsCredentialedDecryptor((BcTlsCrypto) crypto,
                    certificate, privateKey);
        }
        return rsaEncryptionCredentials;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Depending on the <tt>selectedCipherSuite</tt>, <tt>DefaultTlsServer</tt> will require either
     * <tt>rsaEncryptionCredentials</tt> or <tt>rsaSignerCredentials</tt> neither of which is
     * implemented by <tt>DefaultTlsServer</tt>.
     *
     * @return
     */
    @Override
    protected TlsCredentialedSigner getRSASignerCredentials()
    {
        if (rsaSignerCredentials == null) {
            TlsCrypto crypto = context.getCrypto();
            TlsCryptoParameters cryptoParams = new TlsCryptoParameters(context);

            CertificateInfo certInfo = getDtlsControl().getCertificateInfo();
            AsymmetricKeyParameter privateKey = certInfo.getKeyPair().getPrivate();
            Certificate certificate = certInfo.getCertificate();

            // FIXME The signature and hash algorithms should be retrieved from the certificate.
            SignatureAndHashAlgorithm signatureAndHashAlgorithm
                    = new SignatureAndHashAlgorithm(HashAlgorithm.sha256, SignatureAlgorithm.rsa);

            rsaSignerCredentials = new BcDefaultTlsCredentialedSigner(cryptoParams, (BcTlsCrypto) crypto,
                    privateKey, certificate, signatureAndHashAlgorithm);
        }
        return rsaSignerCredentials;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Includes the <tt>use_srtp</tt> extension in the DTLS extended server hello.
     */
    @Override
    @SuppressWarnings("rawtypes")
    public Hashtable getServerExtensions()
            throws IOException
    {
        Hashtable serverExtensions = getServerExtensionsOverride();
        if (isSrtpDisabled()) {
            return serverExtensions;
        }

        if (TlsSRTPUtils.getUseSRTPExtension(serverExtensions) == null) {
            if (serverExtensions == null)
                serverExtensions = new Hashtable();

            UseSRTPData useSRTPData = TlsSRTPUtils.getUseSRTPExtension(clientExtensions);
            int chosenProtectionProfile = DtlsControlImpl.chooseSRTPProtectionProfile(useSRTPData.getProtectionProfiles());

            /*
             * If there is no shared profile and that is not acceptable, the server SHOULD
             * return an appropriate DTLS alert.
             */
            if (chosenProtectionProfile == 0) {
                String msg = "No chosen SRTP protection profile!";
                TlsFatalAlert tfa = new TlsFatalAlert(AlertDescription.internal_error);

                Timber.e(tfa, "%s", msg);
                throw tfa;
            }
            else {
                /*
                 * Upon receipt of a "use_srtp" extension containing a "srtp_mki" field, the server
                 * MUST include a matching "srtp_mki" value in its "use_srtp" extension to indicate
                 * that it will make use of the MKI.
                 */
                TlsSRTPUtils.addUseSRTPExtension(serverExtensions, new UseSRTPData(
                        new int[]{chosenProtectionProfile}, useSRTPData.getMki()));
                this.chosenProtectionProfile = chosenProtectionProfile;
            }
        }
        return serverExtensions;
    }

    /**
     * FIXME: If Client Hello does not include points format extensions then we will end up with
     * alert 47 failure caused by NPE on serverECPointFormats. It was causing JitsiMeet to fail
     * with Android version of Chrome.
     * <p>
     * The fix has been posted upstream and this method should be removed once it is published.
     */
    @SuppressWarnings("rawtypes")
    private Hashtable getServerExtensionsOverride()
            throws IOException
    {
        if (encryptThenMACOffered && allowEncryptThenMAC()) {
            /*
             * draft-ietf-tls-encrypt-then-mac-03 3. If a server receives an encrypt-then-MAC
             * request extension from a client and then selects a stream or AEAD cipher suite, it
             * MUST NOT send an encrypt-then-MAC response extension back to the client.
             */
            if (TlsUtils.isBlockCipherSuite(selectedCipherSuite)) {
                TlsExtensionsUtils.addEncryptThenMACExtension(checkServerExtensions());
            }
        }

        if (maxFragmentLengthOffered >= 0
                && MaxFragmentLength.isValid(maxFragmentLengthOffered)) {
            TlsExtensionsUtils.addMaxFragmentLengthExtension(checkServerExtensions(), maxFragmentLengthOffered);
        }

        if (truncatedHMacOffered && allowTruncatedHMac()) {
            TlsExtensionsUtils.addTruncatedHMacExtension(checkServerExtensions());
        }

        if (TlsECCUtils.isECCCipherSuite(selectedCipherSuite)) {
            /*
             * RFC 4492 5.2. A server that selects an ECC cipher suite in response to a ClientHello
             * message including a Supported Point Formats Extension appends this extension (along
             * with others) to its ServerHello message, enumerating the point formats it can parse.
             */
            short[] serverECPointFormats = new short[]{
                    ECPointFormat.uncompressed,
                    ECPointFormat.ansiX962_compressed_prime,
                    ECPointFormat.ansiX962_compressed_char2,
            };

            TlsExtensionsUtils.addSupportedPointFormatsExtension(checkServerExtensions(), serverECPointFormats);
        }
        return serverExtensions;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Overrides the super implementation as a simple means of detecting that the security-related
     * negotiations between the local and the remote enpoints are starting. The detection carried
     * out for the purposes of <tt>SrtpListener</tt>.
     */
    @Override
    public void init(TlsServerContext context)
    {
        super.init(context);
    }

    /**
     * Determines whether this {@code TlsServerImpl} is to operate in pure DTLS
     * mode without SRTP extensions or in DTLS/SRTP mode.
     *
     * @return {@code true} for pure DTLS without SRTP extensions or
     * {@code false} for DTLS/SRTP
     */
    private boolean isSrtpDisabled()
    {
        return getProperties().isSrtpDisabled();
    }

    /**
     * {@inheritDoc}
     * <p>
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
     */
    @Override
    public void notifyClientCertificate(Certificate clientCertificate)
            throws IOException
    {
        try {
            getDtlsControl().verifyAndValidateCertificate(clientCertificate);
        } catch (Exception e) {
            Timber.e(e, "Failed to verify and/or validate client certificate!");
            if (e instanceof IOException)
                throw (IOException) e;
            else
                throw new IOException(e);
        }
    }

    /**
     * {@inheritDoc}
     * <p>
     * Makes sure that the DTLS extended client hello contains the <tt>use_srtp</tt> extension.
     */
    @Override
    @SuppressWarnings("rawtypes")
    public void processClientExtensions(Hashtable clientExtensions)
            throws IOException
    {
        if (isSrtpDisabled()) {
            super.processClientExtensions(clientExtensions);
            return;
        }

        UseSRTPData useSRTPData = TlsSRTPUtils.getUseSRTPExtension(clientExtensions);

        if (useSRTPData == null) {
            String msg = "DTLS extended client hello does not include the use_srtp extension!";
            IOException ioe = new IOException(msg);

            Timber.e(ioe, "%s", msg);
            throw ioe;
        }
        else {
            int chosenProtectionProfile = DtlsControlImpl.chooseSRTPProtectionProfile(useSRTPData.getProtectionProfiles());

            /*
             * If there is no shared profile and that is not acceptable, the server SHOULD
             * return an appropriate DTLS alert.
             */
            if (chosenProtectionProfile == 0) {
                String msg = "No chosen SRTP protection profile!";
                TlsFatalAlert tfa = new TlsFatalAlert(AlertDescription.illegal_parameter);

                Timber.e(tfa, "%s", msg);
                throw tfa;
            }
            else
                super.processClientExtensions(clientExtensions);
        }
    }
}
