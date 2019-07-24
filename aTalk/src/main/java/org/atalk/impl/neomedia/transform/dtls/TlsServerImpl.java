/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.transform.dtls;

import org.bouncycastle.crypto.tls.*;

import java.io.IOException;
import java.util.Hashtable;

import timber.log.Timber;

/**
 * Implements {@link TlsServer} for the purposes of supporting DTLS-SRTP.
 *
 * @author Lyubomir Marinov
 * @author Eng Chong Meng
 */
public class TlsServerImpl extends DefaultTlsServer
{
    /**
     * @see TlsServer#getCertificateRequest()
     */
    private final CertificateRequest certificateRequest
            = new CertificateRequest(new short[]{ClientCertificateType.rsa_sign}, null, null);

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
    private TlsEncryptionCredentials rsaEncryptionCredentials;

    /**
     * @see DefaultTlsServer#getRSASignerCredentials()
     */
    private TlsSignerCredentials rsaSignerCredentials;

    /**
     * Initializes a new <tt>TlsServerImpl</tt> instance.
     *
     * @param packetTransformer the <tt>PacketTransformer</tt> which is initializing the new instance
     */
    public TlsServerImpl(DtlsPacketTransformer packetTransformer)
    {
        this.packetTransformer = packetTransformer;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CertificateRequest getCertificateRequest()
    {
        return certificateRequest;
    }

    /**
     * Gets the <tt>SRTPProtectionProfile</tt> negotiated between this DTLS-SRTP server and its client.
     *
     * @return the <tt>SRTPProtectionProfile</tt> negotiated between this DTLS-SRTP server and its
     * client
     */
    int getChosenProtectionProfile()
    {
        return chosenProtectionProfile;
    }

    /**
     * {@inheritDoc}
     *
     * Overrides the super implementation to explicitly specify cipher suites
     * which we know to be supported by Bouncy Castle and provide Perfect Forward Secrecy.
     */
    @Override
    protected int[] getCipherSuites()
    {
        return new int[]{
                /* core/src/main/java/org/bouncycastle/crypto/tls/DefaultTlsServer.java */
                CipherSuite.TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384,
                CipherSuite.TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256,
                CipherSuite.TLS_ECDHE_RSA_WITH_AES_256_CBC_SHA384,
                CipherSuite.TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA256,
                CipherSuite.TLS_ECDHE_RSA_WITH_AES_256_CBC_SHA,
                CipherSuite.TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA,
                CipherSuite.TLS_DHE_RSA_WITH_AES_256_GCM_SHA384,
                CipherSuite.TLS_DHE_RSA_WITH_AES_128_GCM_SHA256,
                CipherSuite.TLS_DHE_RSA_WITH_AES_256_CBC_SHA256,
                CipherSuite.TLS_DHE_RSA_WITH_AES_128_CBC_SHA256,
                CipherSuite.TLS_DHE_RSA_WITH_AES_256_CBC_SHA,
                CipherSuite.TLS_DHE_RSA_WITH_AES_128_CBC_SHA
        };
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

    /**
     * {@inheritDoc}
     * <p>
     * The implementation of <tt>TlsServerImpl</tt> always returns <tt>ProtocolVersion.DTLSv10</tt>
     * because <tt>ProtocolVersion.DTLSv12</tt> does not work with the Bouncy Castle Crypto APIs at
     * the time of this writing.
     */
    @Override
    protected ProtocolVersion getMaximumVersion()
    {
        return ProtocolVersion.DTLSv10;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected ProtocolVersion getMinimumVersion()
    {
        return ProtocolVersion.DTLSv10;
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
    protected TlsEncryptionCredentials getRSAEncryptionCredentials()
    {
        if (rsaEncryptionCredentials == null) {
            CertificateInfo certificateInfo = getDtlsControl().getCertificateInfo();
            rsaEncryptionCredentials = new DefaultTlsEncryptionCredentials(context,
                    certificateInfo.getCertificate(), certificateInfo.getKeyPair().getPrivate());
        }
        return rsaEncryptionCredentials;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Depending on the <tt>selectedCipherSuite</tt>, <tt>DefaultTlsServer</tt> will require either
     * <tt>rsaEncryptionCredentials</tt> or <tt>rsaSignerCredentials</tt> neither of which is
     * implemented by <tt>DefaultTlsServer</tt>.
     */
    @Override
    protected TlsSignerCredentials getRSASignerCredentials()
    {
        if (rsaSignerCredentials == null) {
            CertificateInfo certificateInfo = getDtlsControl().getCertificateInfo();

            /*
             * FIXME The signature and hash algorithms should be retrieved from the certificate.
             */
            rsaSignerCredentials = new DefaultTlsSignerCredentials(context,
                    certificateInfo.getCertificate(), certificateInfo.getKeyPair().getPrivate(),
                    new SignatureAndHashAlgorithm(HashAlgorithm.sha1, SignatureAlgorithm.rsa));
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
            int chosenProtectionProfile = DtlsControlImpl.chooseSRTPProtectionProfile(
                    useSRTPData.getProtectionProfiles());

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
            serverECPointFormats = new short[]{
                    ECPointFormat.uncompressed,
                    ECPointFormat.ansiX962_compressed_prime,
                    ECPointFormat.ansiX962_compressed_char2,
            };

            TlsECCUtils.addSupportedPointFormatsExtension(checkServerExtensions(), serverECPointFormats);
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
        // TODO Auto-generated method stub
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
            int chosenProtectionProfile = DtlsControlImpl.chooseSRTPProtectionProfile(
                    useSRTPData.getProtectionProfiles());

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
