/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.transform.dtls;

import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.crypto.params.AsymmetricKeyParameter;
import org.bouncycastle.operator.DefaultAlgorithmNameFinder;
import org.bouncycastle.tls.AlertDescription;
import org.bouncycastle.tls.Certificate;
import org.bouncycastle.tls.CertificateRequest;
import org.bouncycastle.tls.CipherSuite;
import org.bouncycastle.tls.ClientCertificateType;
import org.bouncycastle.tls.DefaultTlsServer;
import org.bouncycastle.tls.ECPointFormat;
import org.bouncycastle.tls.HashAlgorithm;
import org.bouncycastle.tls.MaxFragmentLength;
import org.bouncycastle.tls.ProtocolVersion;
import org.bouncycastle.tls.SignatureAlgorithm;
import org.bouncycastle.tls.SignatureAndHashAlgorithm;
import org.bouncycastle.tls.TlsCredentialedDecryptor;
import org.bouncycastle.tls.TlsCredentialedSigner;
import org.bouncycastle.tls.TlsECCUtils;
import org.bouncycastle.tls.TlsExtensionsUtils;
import org.bouncycastle.tls.TlsFatalAlert;
import org.bouncycastle.tls.TlsSRTPUtils;
import org.bouncycastle.tls.TlsServer;
import org.bouncycastle.tls.TlsServerContext;
import org.bouncycastle.tls.TlsUtils;
import org.bouncycastle.tls.UseSRTPData;
import org.bouncycastle.tls.crypto.TlsCrypto;
import org.bouncycastle.tls.crypto.TlsCryptoParameters;
import org.bouncycastle.tls.crypto.impl.bc.BcDefaultTlsCredentialedDecryptor;
import org.bouncycastle.tls.crypto.impl.bc.BcDefaultTlsCredentialedSigner;
import org.bouncycastle.tls.crypto.impl.bc.BcTlsCrypto;

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
 * @See <a href="https://www.rfc-editor.org/rfc/rfc6347">Datagram Transport Layer Security Version 1.2</a>
 * @See <a href="https://www.rfc-editor.org/rfc/rfc5246">The Transport Layer Security (TLS) Protocol Version 1.2</a>
 * or https://www.ietf.org/rfc/rfc5246.html
 */
public class TlsServerImpl extends DefaultTlsServer {
    /**
     * @see TlsServer#getCertificateRequest()
     */
    private CertificateRequest certificateRequest = null;

    /**
     * If DTLSv12 or higher is negotiated, configures the set of supported signature algorithms in the
     * CertificateRequest (if one is sent). If null, uses the default set.
     */
    private final Vector<?> serverCertReqSigAlgs = null;

    /**
     * The <code>SRTPProtectionProfile</code> negotiated between this DTLS-SRTP server and its client.
     */
    private int chosenProtectionProfile;

    /**
     * The <code>PacketTransformer</code> which has initialized this instance.
     */
    private final DtlsPacketTransformer mPacketTransformer;

    /**
     * @see DefaultTlsServer#getRSAEncryptionCredentials()
     */
    private TlsCredentialedDecryptor rsaEncryptionCredentials;

    /**
     * @see DefaultTlsServer#getRSASignerCredentials()
     */
    private TlsCredentialedSigner rsaSignerCredentials;

    /**
     * @see DefaultTlsServer#getECDSASignerCredentials()
     */
    private TlsCredentialedSigner ecdsaSignerCredentials;

    /**
     * Initializes a new <code>TlsServerImpl</code> instance.
     *
     * @param packetTransformer the <code>PacketTransformer</code> which is initializing the new instance
     */
    public TlsServerImpl(DtlsPacketTransformer packetTransformer) {
        super(new BcTlsCrypto(new SecureRandom()));
        mPacketTransformer = packetTransformer;
    }

    /**
     * Gets the <code>SRTPProtectionProfile</code> negotiated between this DTLS-SRTP server and its client.
     *
     * @return the <code>SRTPProtectionProfile</code> negotiated between this DTLS-SRTP server and its client
     */
    private int getChosenProtectionProfile() {
        return chosenProtectionProfile;
    }

    /**
     * {@inheritDoc}
     *
     * Overrides the super implementation to explicitly specify cipher suites
     * which we know to be supported by Bouncy Castle and provide Perfect Forward Secrecy.
     *
     * @see org/bouncycastle/crypto/tls/DefaultTlsServer.java
     * @see https://www.acunetix.com/blog/articles/tls-ssl-cipher-hardening/ Preferred Cipher Suite Order
     * CipherSuite.TLS_ECDHE_ECDSA_WITH_AES_256_GCM_SHA384,
     * CipherSuite.TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384,
     * CipherSuite.TLS_ECDHE_ECDSA_WITH_CHACHA20_POLY1305_SHA256,
     * CipherSuite.TLS_ECDHE_RSA_WITH_CHACHA20_POLY1305_SHA256,
     * CipherSuite.TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256,
     * CipherSuite.TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256,
     */
    @Override
    public int[] getSupportedCipherSuites() {
        // [52393, 49196, 49195]
        int[] cipherSuites_ecdsa = new int[]{
                CipherSuite.TLS_ECDHE_ECDSA_WITH_CHACHA20_POLY1305_SHA256,
                CipherSuite.TLS_ECDHE_ECDSA_WITH_AES_256_GCM_SHA384,
                CipherSuite.TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256,
        };

        // [52392, 49200, 49199, 49192, 49191, 49172, 49171, 52394, 159, 158, 107, 103, 57, 51]
        int[] cipherSuites_rsa = new int[]{
                CipherSuite.TLS_ECDHE_RSA_WITH_CHACHA20_POLY1305_SHA256,
                CipherSuite.TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384,
                CipherSuite.TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256,
                CipherSuite.TLS_ECDHE_RSA_WITH_AES_256_CBC_SHA384,
                CipherSuite.TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA256,
                CipherSuite.TLS_ECDHE_RSA_WITH_AES_256_CBC_SHA,
                CipherSuite.TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA,

                CipherSuite.TLS_DHE_RSA_WITH_CHACHA20_POLY1305_SHA256,
                CipherSuite.TLS_DHE_RSA_WITH_AES_256_GCM_SHA384,
                CipherSuite.TLS_DHE_RSA_WITH_AES_128_GCM_SHA256,
                CipherSuite.TLS_DHE_RSA_WITH_AES_256_CBC_SHA256,
                CipherSuite.TLS_DHE_RSA_WITH_AES_128_CBC_SHA256,
                CipherSuite.TLS_DHE_RSA_WITH_AES_256_CBC_SHA,
                CipherSuite.TLS_DHE_RSA_WITH_AES_128_CBC_SHA
        };

        /*
         * Do not offer CipherSuite.TLS_ECDHE_ECDSA... if the local certificateType is rsa_signed,
         * i.e. user has selected e.g. SHA256withRSA. This is used in certificate generation, and its
         * fingerPrint is advertised in jingle session-initiate. Changing the signer will cause fingerPrint
         * mismatch error in DtlsControlImpl#verifyAndValidateCertificate(Certificate certificate).
         *
         * Note: conversations/webrtc offers CipherSuite.TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256, but webrtc:113.0.0
         * does not support SignatureAndHashAlgorithm for ECDSASignerCredential and failed decode_error(50)
         * See BC TlsUtils#isValidSignatureAlgorithmForServerKeyExchangeshort signatureAlgorithm, int keyExchangeAlgorithm)
         * for matching between SignatureAlgorithm and KeyExchangeAlgorithm.
         */
        if (getDtlsControl().getCertificateInfo().getCertificateType() == ClientCertificateType.rsa_sign) {
            return TlsUtils.getSupportedCipherSuites(getCrypto(), cipherSuites_rsa);
        } else {
            return TlsUtils.getSupportedCipherSuites(getCrypto(), cipherSuites_ecdsa);
        }
    }

    /**
     * {@inheritDoc}
     * The implementation of <code>TlsServerImpl</code> always returns <code>ProtocolVersion.DTLSv12 & DTLSv10</code>
     */
    @Override
    protected ProtocolVersion[] getSupportedVersions() {
        return ProtocolVersion.DTLSv12.downTo(ProtocolVersion.DTLSv10);
    }

    /**
     * Gets the <code>DtlsControl</code> implementation associated with this instance.
     *
     * @return the <code>DtlsControl</code> implementation associated with this instance
     */
    private DtlsControlImpl getDtlsControl() {
        return mPacketTransformer.getDtlsControl();
    }

    private Properties getProperties() {
        return mPacketTransformer.getProperties();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CertificateRequest getCertificateRequest() {
        if (certificateRequest == null) {
            short[] certificateTypes = new short[]{ClientCertificateType.rsa_sign,
                    ClientCertificateType.dss_sign, ClientCertificateType.ecdsa_sign};

            Vector<?> serverSigAlgs = null;
            if (TlsUtils.isSignatureAlgorithmsExtensionAllowed(context.getServerVersion())) {
                serverSigAlgs = serverCertReqSigAlgs;
                if (serverSigAlgs == null) {
                    serverSigAlgs = TlsUtils.getDefaultSupportedSignatureAlgorithms(context);
                }
            }

            Vector<X500Name> certificateAuthorities = new Vector<>();
            certificateAuthorities.addElement(new X500Name("CN=atalk.org TLS CA"));

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

            certificateRequest = new CertificateRequest(certificateTypes, serverSigAlgs, certificateAuthorities);
        }

        // Timber.w("getCertificateRequest = CertificateTypes: %s;\nSupportedSignatureAlgorithms: %s;\nCertificateAuthorities: %s",
        //        Shorts.asList(certificateRequest.getCertificateTypes()), certificateRequest.getSupportedSignatureAlgorithms(), certificateRequest.getCertificateAuthorities());
        return certificateRequest;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Depending on the <code>selectedCipherSuite</code>, <code>DefaultTlsServer</code> will require either
     * <code>rsaEncryptionCredentials</code> or <code>rsaSignerCredentials</code> neither of which is
     * implemented by <code>DefaultTlsServer</code>.
     */
    @Override
    protected TlsCredentialedDecryptor getRSAEncryptionCredentials() {
        if (rsaEncryptionCredentials == null) {
            CertificateInfo certInfo = getDtlsControl().getCertificateInfo();
            Certificate certificate = certInfo.getCertificate();

            TlsCrypto crypto = context.getCrypto();
            AsymmetricKeyParameter privateKey = certInfo.getKeyPair().getPrivate();

            rsaEncryptionCredentials
                    = new BcDefaultTlsCredentialedDecryptor((BcTlsCrypto) crypto, certificate, privateKey);
        }
        return rsaEncryptionCredentials;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Depending on the <code>selectedCipherSuite</code>, <code>DefaultTlsServer</code> will require either
     * <code>rsaEncryptionCredentials</code> or <code>rsaSignerCredentials</code> neither of which is
     * implemented by <code>DefaultTlsServer</code>.
     *
     * @return TlsCredentialedSigner: rsaSignerCredentials
     */
    @Override
    protected TlsCredentialedSigner getRSASignerCredentials() {
        if (rsaSignerCredentials == null) {
            CertificateInfo certInfo = getDtlsControl().getCertificateInfo();
            Certificate certificate = certInfo.getCertificate();

            SignatureAndHashAlgorithm sigAndHashAlg = getSigAndHashAlg(certificate);
            if (sigAndHashAlg == null)
                return null;

            TlsCrypto crypto = context.getCrypto();
            TlsCryptoParameters cryptoParams = new TlsCryptoParameters(context);
            AsymmetricKeyParameter privateKey = certInfo.getKeyPair().getPrivate();

            rsaSignerCredentials = new BcDefaultTlsCredentialedSigner(
                    cryptoParams, (BcTlsCrypto) crypto, privateKey, certificate, sigAndHashAlg);
        }
        return rsaSignerCredentials;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Depending on the <code>selectedCipherSuite</code>, <code>DefaultTlsServer</code> will require
     * <code>ecdsaSignerCredentials</code> which is not implemented by <code>DefaultTlsServer</code>
     * when cipherSuite is KeyExchangeAlgorithm.ECDHE_ECDSA
     *
     * @return TlsCredentialedSigner: ecdsaSignerCredentials
     */
    @Override
    protected TlsCredentialedSigner getECDSASignerCredentials() {
        if (ecdsaSignerCredentials == null) {
            // CertificateInfo certInfo = getDtlsControl().getCertificateInfo(getAlgorithmName("ECDSA"));
            CertificateInfo certInfo = getDtlsControl().getCertificateInfo();
            Certificate certificate = certInfo.getCertificate();

            SignatureAndHashAlgorithm sigAndHashAlg = getSigAndHashAlg(certificate);
            if (sigAndHashAlg == null)
                return null;

            TlsCrypto crypto = context.getCrypto();
            TlsCryptoParameters cryptoParams = new TlsCryptoParameters(context);
            AsymmetricKeyParameter privateKey = certInfo.getKeyPair().getPrivate();

            ecdsaSignerCredentials = new BcDefaultTlsCredentialedSigner(
                    cryptoParams, (BcTlsCrypto) crypto, privateKey, certificate, sigAndHashAlg);
        }
        return ecdsaSignerCredentials;
    }

    /**
     * Obtain the SignatureAndHashAlgorithm based on the given certificate
     *
     * @param certificate containing info for SignatureAndHashAlgorithm
     *
     * @return SignatureAndHashAlgorithm
     */
    public static SignatureAndHashAlgorithm getSigAndHashAlg(Certificate certificate) {
        // FIXME ed448/ed25519? multiple certificates?
        String algName = new DefaultAlgorithmNameFinder().getAlgorithmName(
                new ASN1ObjectIdentifier(certificate.getCertificateAt(0).getSigAlgOID())
        );

        SignatureAndHashAlgorithm sigAndHashAlg;
        switch (algName) {
            case "SHA1WITHRSA":
                sigAndHashAlg = SignatureAndHashAlgorithm.getInstance(HashAlgorithm.sha1, SignatureAlgorithm.rsa);
                break;
            case "SHA224WITHRSA":
                sigAndHashAlg = SignatureAndHashAlgorithm.getInstance(HashAlgorithm.sha224, SignatureAlgorithm.rsa);
                break;
            case "SHA256WITHRSA":
                sigAndHashAlg = SignatureAndHashAlgorithm.getInstance(HashAlgorithm.sha256, SignatureAlgorithm.rsa);
                break;
            case "SHA384WITHRSA":
                sigAndHashAlg = SignatureAndHashAlgorithm.getInstance(HashAlgorithm.sha384, SignatureAlgorithm.rsa);
                break;
            case "SHA512WITHRSA":
                sigAndHashAlg = SignatureAndHashAlgorithm.getInstance(HashAlgorithm.sha512, SignatureAlgorithm.rsa);
                break;
            case "SHA1WITHECDSA":
                sigAndHashAlg = SignatureAndHashAlgorithm.getInstance(HashAlgorithm.sha1, SignatureAlgorithm.ecdsa);
                break;
            case "SHA224WITHECDSA":
                sigAndHashAlg = SignatureAndHashAlgorithm.getInstance(HashAlgorithm.sha224, SignatureAlgorithm.ecdsa);
                break;
            case "SHA256WITHECDSA":
                sigAndHashAlg = SignatureAndHashAlgorithm.getInstance(HashAlgorithm.sha256, SignatureAlgorithm.ecdsa);
                break;
            case "SHA384WITHECDSA":
                sigAndHashAlg = SignatureAndHashAlgorithm.getInstance(HashAlgorithm.sha384, SignatureAlgorithm.ecdsa);
                break;
            case "SHA512WITHECDSA":
                sigAndHashAlg = SignatureAndHashAlgorithm.getInstance(HashAlgorithm.sha512, SignatureAlgorithm.ecdsa);
                break;
            default:
                Timber.w("Unsupported algOID in certificate: %s", algName);
                return null;
        }

        Timber.d("TLS Certificate SignatureAndHashAlgorithm: %s", sigAndHashAlg);
        return sigAndHashAlg;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Includes the <code>use_srtp</code> extension in the DTLS extended server hello.
     */
    @Override
    @SuppressWarnings("rawtypes")
    public Hashtable getServerExtensions()
            throws IOException {
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
            throws IOException {
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
     * negotiations between the local and the remote endpoints are starting. The detection carried
     * out for the purposes of <code>SrtpListener</code>.
     */
    @Override
    public void init(TlsServerContext context) {
        super.init(context);
    }

    /**
     * Determines whether this {@code TlsServerImpl} is to operate in pure DTLS
     * mode without SRTP extensions or in DTLS/SRTP mode.
     *
     * @return {@code true} for pure DTLS without SRTP extensions or
     * {@code false} for DTLS/SRTP
     */
    private boolean isSrtpDisabled() {
        return getProperties().isSrtpDisabled();
    }

    /**
     * {@inheritDoc}
     * <p>
     * Forwards to {@link #mPacketTransformer}.
     */
    @Override
    public void notifyAlertRaised(short alertLevel, short alertDescription, String message, Throwable cause) {
        mPacketTransformer.notifyAlertRaised(this, alertLevel, alertDescription, message, cause);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void notifyHandshakeComplete()
            throws IOException {
        super.notifyHandshakeComplete();
        mPacketTransformer.initializeSRTPTransformer(getChosenProtectionProfile(), context);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void notifyClientCertificate(Certificate clientCertificate)
            throws IOException {
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
     * Makes sure that the DTLS extended client hello contains the <code>use_srtp</code> extension.
     */
    @Override
    public void processClientExtensions(Hashtable clientExtensions)
            throws IOException {
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
