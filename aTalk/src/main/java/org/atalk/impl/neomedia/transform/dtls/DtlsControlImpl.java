/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.transform.dtls;

import android.text.TextUtils;

import org.atalk.android.R;
import org.atalk.android.aTalkApp;
import org.atalk.android.plugin.timberlog.TimberLog;
import org.atalk.impl.neomedia.AbstractRTPConnector;
import org.atalk.service.libjitsi.LibJitsi;
import org.atalk.service.neomedia.AbstractSrtpControl;
import org.atalk.service.neomedia.DtlsControl;
import org.atalk.service.neomedia.SrtpControl;
import org.atalk.service.neomedia.SrtpControlType;
import org.atalk.service.neomedia.event.SrtpListener;
import org.atalk.util.ConfigUtils;
import org.atalk.util.MediaType;
import org.bouncycastle.asn1.ASN1Encoding;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x500.X500NameBuilder;
import org.bouncycastle.asn1.x500.style.BCStyle;
import org.bouncycastle.asn1.x509.AlgorithmIdentifier;
import org.bouncycastle.asn1.x509.Certificate;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.bc.BcX509v3CertificateBuilder;
import org.bouncycastle.crypto.AsymmetricCipherKeyPair;
import org.bouncycastle.crypto.Digest;
import org.bouncycastle.crypto.generators.ECKeyPairGenerator;
import org.bouncycastle.crypto.generators.RSAKeyPairGenerator;
import org.bouncycastle.crypto.params.ECDomainParameters;
import org.bouncycastle.crypto.params.ECKeyGenerationParameters;
import org.bouncycastle.crypto.params.RSAKeyGenerationParameters;
import org.bouncycastle.crypto.params.RSAKeyParameters;
import org.bouncycastle.jce.ECNamedCurveTable;
import org.bouncycastle.jce.spec.ECNamedCurveParameterSpec;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.DefaultDigestAlgorithmIdentifierFinder;
import org.bouncycastle.operator.DefaultSignatureAlgorithmIdentifierFinder;
import org.bouncycastle.operator.bc.BcDefaultDigestProvider;
import org.bouncycastle.operator.bc.BcECContentSignerBuilder;
import org.bouncycastle.operator.bc.BcRSAContentSignerBuilder;
import org.bouncycastle.tls.AlertDescription;
import org.bouncycastle.tls.AlertLevel;
import org.bouncycastle.tls.SRTPProtectionProfile;
import org.bouncycastle.tls.TlsPeer;
import org.bouncycastle.tls.crypto.TlsCertificate;
import org.bouncycastle.tls.crypto.impl.bc.BcTlsCertificate;
import org.bouncycastle.tls.crypto.impl.bc.BcTlsCrypto;

import java.io.IOException;
import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import timber.log.Timber;

/**
 * Implements {@link DtlsControl} i.e. {@link SrtpControl} for DTLS-SRTP.
 *
 * @author Lyubomir Marinov
 * @author Eng Chong Meng
 */
public class DtlsControlImpl extends AbstractSrtpControl<DtlsTransformEngine> implements DtlsControl
{
    /**
     * The map which specifies which hash functions are to be considered
     * &quot;upgrades&quot; of which other hash functions. The keys are the hash
     * functions which have &quot;upgrades&quot; defined and are written in lower case.
     */
    private static final Map<String, String[]> HASH_FUNCTION_UPGRADES = new HashMap<>();

    /**
     * The table which maps half-<code>byte</code>s to their hex characters.
     */
    private static final char[] HEX_ENCODE_TABLE = {
            '0', '1', '2', '3', '4', '5', '6', '7',
            '8', '9', 'A', 'B', 'C', 'D', 'E', 'F'
    };

    /**
     * The number of milliseconds within a day i.e. 24 hours.
     */
    private static final long ONE_DAY = 1000L * 60L * 60L * 24L;

    public static final String DEFAULT_SIGNATURE_ALGORITHM = "SHA256withECDSA";
    // public static final String DEFAULT_SIGNATURE_ALGORITHM = "SHA256withRSA";

    /**
     * The name of the property to specify RSA Key length.
     */
    public static final String RSA_KEY_SIZE_PNAME = "neomedia.transform.dtls.RSA_KEY_SIZE";

    /**
     * The default RSA key size when configuration properties are not found.
     */
    public static final int DEFAULT_RSA_KEY_SIZE = 2048;

    /**
     * The RSA key size to use.
     * The default value is {@code DEFAULT_RSA_KEY_SIZE} but may be overridden
     * by the {@code ConfigurationService} and/or {@code System} property {@code RSA_KEY_SIZE_PNAME}.
     */
    public static final int RSA_KEY_SIZE;

    /**
     * The name of the property to specify RSA key size certainty.
     * https://docs.oracle.com/javase/7/docs/api/java/math/BigInteger.html
     */
    public static final String RSA_KEY_SIZE_CERTAINTY_PNAME = "neomedia.transform.dtls.RSA_KEY_SIZE_CERTAINTY";

    /**
     * The RSA key size certainty to use.
     * The default value is {@code DEFAULT_RSA_KEY_SIZE_CERTAINTY} but may be overridden by the
     * {@code ConfigurationService} and/or {@code System} property {@code RSA_KEY_SIZE_CERTAINTY_PNAME}.
     * For more on certainty, look at the three parameter constructor here:
     * https://docs.oracle.com/javase/7/docs/api/java/math/BigInteger.html
     */
    public static final int RSA_KEY_SIZE_CERTAINTY;

    /**
     * The default RSA key size certainty when config properties are not found.
     */
    public static final int DEFAULT_RSA_KEY_SIZE_CERTAINTY = 80;

    /**
     * The name of the property which specifies the signature algorithm used
     * during certificate creation. When a certificate is created and this
     * property is not set, a default value of "SHA256withRSA" will be used.
     */
    public static final String CERT_TLS_SIGNATURE_ALGORITHM = "neomedia.transform.dtls.SIGNATURE_ALGORITHM";

    /**
     * The name of the property to specify DTLS certificate cache expiration.
     */
    public static final String CERT_CACHE_EXPIRE_TIME_PNAME = "neomedia.transform.dtls.CERT_CACHE_EXPIRE_TIME";

    /**
     * The certificate cache expiration time to use, in milliseconds.
     * The default value is {@code DEFAULT_CERT_CACHE_EXPIRE_TIME} but may be overridden by the
     * {@code ConfigurationService} and/or {@code System} property {@code CERT_CACHE_EXPIRE_TIME_PNAME}.
     */
    private static final long CERT_CACHE_EXPIRE_TIME;

    /**
     * The default certificate cache expiration time, when config properties are not found.
     */
    private static final long DEFAULT_CERT_CACHE_EXPIRE_TIME = ONE_DAY;

    /**
     * The public exponent to always use for RSA key generation.
     */
    public static final BigInteger RSA_KEY_PUBLIC_EXPONENT = new BigInteger("10001", 16);

    /**
     * The <code>SRTPProtectionProfile</code>s supported by <code>DtlsControlImpl</code>.
     */
    static final int[] SRTP_PROTECTION_PROFILES = {
            // RFC 5764 4.1.2.
            SRTPProtectionProfile.SRTP_AES128_CM_HMAC_SHA1_80,
            SRTPProtectionProfile.SRTP_AES128_CM_HMAC_SHA1_32,
            // SRTPProtectionProfile.SRTP_NULL_HMAC_SHA1_80,
            // SRTPProtectionProfile.SRTP_NULL_HMAC_SHA1_32,

            // RFC 7714 14.2.
            // SRTPProtectionProfile.SRTP_AEAD_AES_128_GCM,
            // SRTPProtectionProfile.SRTP_AEAD_AES_256_GCM
    };

    /**
     * The indicator which specifies whether {@code DtlsControlImpl} is to tear down the media session
     * if the fingerprint does not match the hashed certificate. The default value is {@code true} and
     * may be overridden by the {@code ConfigurationService} and/or {@code System} property
     * {@code VERIFY_AND_VALIDATE_CERTIFICATE_PNAME}.
     */
    private static final boolean VERIFY_AND_VALIDATE_CERTIFICATE;

    /**
     * The name of the {@code ConfigurationService} and/or {@code System} property which specifies whether
     * {@code DtlsControlImpl} is to tear down the media session if the fingerprint does not match the hashed
     * certificate. The default value is {@code true}.
     */
    private static final String VERIFY_AND_VALIDATE_CERTIFICATE_PNAME = "neomedia.transform.dtls.verifyAndValidateCertificate";

    /**
     * The cache of {@link #certificateInfo} so that we do not invoke CPU
     * intensive methods for each new {@code DtlsControlImpl} instance.
     */
    private static CertificateInfo certificateInfoCache;

    static {
        // Set configurable options using ConfigurationService.

        VERIFY_AND_VALIDATE_CERTIFICATE = ConfigUtils.getBoolean(
                LibJitsi.getConfigurationService(),
                VERIFY_AND_VALIDATE_CERTIFICATE_PNAME,
                true);

        RSA_KEY_SIZE = ConfigUtils.getInt(
                LibJitsi.getConfigurationService(),
                RSA_KEY_SIZE_PNAME,
                DEFAULT_RSA_KEY_SIZE);

        RSA_KEY_SIZE_CERTAINTY = ConfigUtils.getInt(
                LibJitsi.getConfigurationService(),
                RSA_KEY_SIZE_CERTAINTY_PNAME,
                DEFAULT_RSA_KEY_SIZE_CERTAINTY);

        CERT_CACHE_EXPIRE_TIME = ConfigUtils.getLong(
                LibJitsi.getConfigurationService(),
                CERT_CACHE_EXPIRE_TIME_PNAME,
                DEFAULT_CERT_CACHE_EXPIRE_TIME);

        // HASH_FUNCTION_UPGRADES
        HASH_FUNCTION_UPGRADES.put("sha-1", new String[]{"sha-224", "sha-256", "sha-384", "sha-512"});
    }

    private boolean mSecurityState = false;

    /**
     * The certificate with which the local endpoint represented by this instance authenticates its
     * ends of DTLS sessions.
     */
    private final CertificateInfo mCertificateInfo;

    private static String mSignatureAlgorithm;

    /**
     * The indicator which determines whether this instance has been disposed
     * i.e. prepared for garbage collection by {@link #doCleanup()}.
     */
    private boolean disposed = false;

    /**
     * The fingerprints presented by the remote endpoint via the signaling path.
     */
    private Map<String, String> remoteFingerprints;

    /**
     * The properties of {@code DtlsControlImpl} and their values which this
     * instance shares with {@link DtlsTransformEngine} and {@link DtlsPacketTransformer}.
     */
    private final Properties mProperties;

    /**
     * Initializes a new <code>DtlsControlImpl</code> instance.
     * By default aTalk works in DTLS/SRTP mode.
     */
    public DtlsControlImpl()
    {
        this(false);
    }

    /**
     * Initializes a new <code>DtlsControlImpl</code> instance.
     *
     * @param srtpDisabled <code>true</code> if pure DTLS mode without SRTP
     * extensions is to be used; otherwise, <code>false</code>
     */
    public DtlsControlImpl(boolean srtpDisabled)
    {
        super(SrtpControlType.DTLS_SRTP);
        CertificateInfo certificateInfo;
        // Timber.e(new Exception("TLS Certificate Signature Algorithm: " + mSignatureAlgorithm + "; " + certificateInfoCache));

        // The methods generateKeyPair(), generateX509Certificate(), findHashFunction(), and/or
        // computeFingerprint() may be too CPU intensive to invoke for each new DtlsControlImpl instance.
        // That's why we've decided to reuse their return values within a certain time frame (Default 1 day).
        // Attempt to retrieve from the cache.
        synchronized (DtlsControlImpl.class) {
            certificateInfo = certificateInfoCache;
            // The cache doesn't exist yet or has outlived its lifetime. Rebuild the cache.
            if (certificateInfo == null
                    || certificateInfo.timestamp + CERT_CACHE_EXPIRE_TIME < System.currentTimeMillis()) {
                certificateInfoCache = certificateInfo = generateCertificateInfo();
            }
        }

        mCertificateInfo = certificateInfo;
        mProperties = new Properties(srtpDisabled);
        Timber.d("getCertificateInfo => DtlsControlImpl' %s", mCertificateInfo.getCertificateType());
    }

    /**
     * Generates a new certificate from a new key pair, determines the hash function, and computes the fingerprint.
     *
     * @return CertificateInfo a new certificate generated from a new key pair, its hash function, and fingerprint
     */
    private static CertificateInfo generateCertificateInfo()
    {
        AsymmetricCipherKeyPair keyPair = generateKeyPair();
        Certificate x509Certificate = generateX509Certificate(generateCN(), keyPair);

        BcTlsCertificate tlsCertificate = new BcTlsCertificate(new BcTlsCrypto(new SecureRandom()), x509Certificate);
        org.bouncycastle.tls.Certificate certificate
                = new org.bouncycastle.tls.Certificate(new TlsCertificate[]{tlsCertificate});

        String localFingerprintHashFunction = findHashFunction(x509Certificate);
        String localFingerprint = computeFingerprint(x509Certificate, localFingerprintHashFunction);

        long timestamp = System.currentTimeMillis();
        return new CertificateInfo(keyPair, certificate, localFingerprintHashFunction, localFingerprint, timestamp);
    }

    /**
     * Return a pair of RSA private and public keys.
     *
     * @return a pair of private and public keys
     */
    private static AsymmetricCipherKeyPair generateKeyPair()
    {
        // The signature algorithm of the generated certificate defaults to SHA256.
        // However, allow the overriding of the default via the ConfigurationService.
        if (mSignatureAlgorithm.toUpperCase(Locale.ROOT).endsWith("RSA")) {
            RSAKeyPairGenerator generator = new RSAKeyPairGenerator();

            generator.init(new RSAKeyGenerationParameters(
                    RSA_KEY_PUBLIC_EXPONENT, new SecureRandom(), RSA_KEY_SIZE, RSA_KEY_SIZE_CERTAINTY));
            return generator.generateKeyPair();
        }
        else if (mSignatureAlgorithm.toUpperCase(Locale.ROOT).endsWith("ECDSA")) {
            ECKeyPairGenerator generator = new ECKeyPairGenerator();
            ECNamedCurveParameterSpec curve = ECNamedCurveTable.getParameterSpec("secp256r1");
            ECDomainParameters domainParams =
                    new ECDomainParameters(curve.getCurve(), curve.getG(), curve.getN(), curve.getH(), curve.getSeed());
            generator.init(new ECKeyGenerationParameters(domainParams, new SecureRandom()));
            return generator.generateKeyPair();
        }

        throw new IllegalArgumentException("Unknown signature algorithm: " + mSignatureAlgorithm);
    }

    /**
     * Generates a new subject for a self-signed certificate to be generated by <code>DtlsControlImpl</code>.
     *
     * @return an <code>X500Name</code> which is to be used as the subject of a self-signed certificate
     * to be generated by <code>DtlsControlImpl</code>
     */
    private static X500Name generateCN()
    {
        X500NameBuilder builder = new X500NameBuilder(BCStyle.INSTANCE);

        final SecureRandom secureRandom = new SecureRandom();
        final byte[] bytes = new byte[16];
        secureRandom.nextBytes(bytes);

        final char[] chars = new char[32];

        for (int i = 0; i < 16; i++) {
            final int b = bytes[i] & 0xff;
            chars[i * 2] = HEX_ENCODE_TABLE[b >>> 4];
            chars[i * 2 + 1] = HEX_ENCODE_TABLE[b & 0x0f];
        }
        builder.addRDN(BCStyle.CN, (new String(chars)).toLowerCase());

        return builder.build();
    }

    /**
     * Generates a new self-signed certificate with a specific subject and a specific pair of
     * private and public keys.
     *
     * @param subject the subject (and issuer) of the new certificate to be generated
     * @param keyPair the pair of private and public keys of the certificate to be generated
     * @return a new self-signed certificate with the specified <code>subject</code> and <code>keyPair</code>
     */
    private static Certificate generateX509Certificate(X500Name subject, AsymmetricCipherKeyPair keyPair)
    {
        Timber.d("Signature algorithm: %s", mSignatureAlgorithm);
        try {
            long now = System.currentTimeMillis();
            Date notBefore = new Date(now - ONE_DAY);
            Date notAfter = new Date(now + ONE_DAY * 6 + CERT_CACHE_EXPIRE_TIME);
            X509v3CertificateBuilder builder = new BcX509v3CertificateBuilder(
                    /* issuer */ subject,
                    /* serial */ BigInteger.valueOf(now), notBefore, notAfter, subject,
                    /* publicKey */ keyPair.getPublic());

            AlgorithmIdentifier sigAlgId = new DefaultSignatureAlgorithmIdentifierFinder().find(mSignatureAlgorithm);
            AlgorithmIdentifier digAlgId = new DefaultDigestAlgorithmIdentifierFinder().find(sigAlgId);
            ContentSigner signer;
            if (keyPair.getPrivate() instanceof RSAKeyParameters) {
                signer = new BcRSAContentSignerBuilder(sigAlgId, digAlgId).build(keyPair.getPrivate());
            }
            else {
                signer = new BcECContentSignerBuilder(sigAlgId, digAlgId).build(keyPair.getPrivate());
            }
            return builder.build(signer).toASN1Structure();
        } catch (Throwable t) {
            if (t instanceof ThreadDeath)
                throw (ThreadDeath) t;
            else {
                Timber.e(t, "Failed to generate self-signed X.509 certificate");
                if (t instanceof RuntimeException)
                    throw (RuntimeException) t;
                else
                    throw new RuntimeException(t);
            }
        }
    }

    /**
     * Set the default TLS certificate signature algorithm; This value must be set prior to DtlsControlImpl().
     * Init certificateInfoCache if the mSignatureAlgorithm is s new user defined SignatureAlgorithm
     *
     * @param tlsCertSA TLS certificate signature algorithm
     */
    public static void setTlsCertificateSA(String tlsCertSA)
    {
        if (mSignatureAlgorithm != null && !mSignatureAlgorithm.equals(tlsCertSA)) {
            certificateInfoCache = null;
        }
        mSignatureAlgorithm = tlsCertSA;
    }

    /**
     * Chooses the first from a list of <code>SRTPProtectionProfile</code>s that is supported by <code>DtlsControlImpl</code>.
     *
     * @param theirs the list of <code>SRTPProtectionProfile</code>s to choose from
     * @return the first from the specified <code>theirs</code> that is supported by <code>DtlsControlImpl</code>
     */
    static int chooseSRTPProtectionProfile(int... theirs)
    {
        if (theirs != null) {
            for (int their : theirs) {
                for (int our : SRTP_PROTECTION_PROFILES) {
                    if (their == our) {
                        return their;
                    }
                }
            }
        }
        return 0;
    }

    /**
     * Computes the fingerprint of a specific certificate using a specific hash function.
     *
     * @param certificate the certificate the fingerprint of which is to be computed
     * @param hashFunction the hash function to be used in order to compute the fingerprint
     * of the specified <code>certificate</code>
     * @return the fingerprint of the specified <code>certificate</code> computed using the specified <code>hashFunction</code>
     */
    private static String computeFingerprint(Certificate certificate, String hashFunction)
    {
        try {
            AlgorithmIdentifier digAlgId
                    = new DefaultDigestAlgorithmIdentifierFinder().find(hashFunction.toUpperCase());
            Digest digest = BcDefaultDigestProvider.INSTANCE.get(digAlgId);
            byte[] in = certificate.getEncoded(ASN1Encoding.DER);
            byte[] out = new byte[digest.getDigestSize()];

            digest.update(in, 0, in.length);
            digest.doFinal(out, 0);

            return toHex(out);
        } catch (Throwable t) {
            if (t instanceof ThreadDeath) {
                throw (ThreadDeath) t;
            }
            else {
                Timber.e(t, "Failed to generate certificate fingerprint!");
                if (t instanceof RuntimeException)
                    throw (RuntimeException) t;
                else
                    throw new RuntimeException(t);
            }
        }
    }

    /**
     * Determines the hash function i.e. the digest algorithm of the signature
     * algorithm of a specific certificate.
     *
     * @param certificate the certificate the hash function of which is to be determined
     * @return the hash function of the specified <code>certificate</code> written in lower case
     */
    private static String findHashFunction(Certificate certificate)
    {
        try {
            AlgorithmIdentifier sigAlgId = certificate.getSignatureAlgorithm();
            AlgorithmIdentifier digAlgId = new DefaultDigestAlgorithmIdentifierFinder().find(sigAlgId);

            return BcDefaultDigestProvider.INSTANCE.get(digAlgId).getAlgorithmName().toLowerCase();
        } catch (Throwable t) {
            if (t instanceof ThreadDeath) {
                throw (ThreadDeath) t;
            }
            else {
                Timber.w(t, "Failed to find the hash function of the signature algorithm of a certificate!");
                if (t instanceof RuntimeException)
                    throw (RuntimeException) t;
                else
                    throw new RuntimeException(t);
            }
        }
    }

    /**
     * Finds a hash function which is an &quot;upgrade&quot; of a specific hash
     * function and has a fingerprint associated with it.
     *
     * @param hashFunction the hash function which is not associated with a
     * fingerprint and for which an &quot;upgrade&quot; associated with a fingerprint is to be found
     * @param fingerprints the set of available hash function-fingerprint associations
     * @return a hash function written in lower case which is an &quot;upgrade&quot; of the specified
     * {@code hashFunction} and has a fingerprint associated with it in {@code fingerprints} if
     * there is such a hash function; otherwise, {@code null}
     */
    private static String findHashFunctionUpgrade(String hashFunction, Map<String, String> fingerprints)
    {
        String[] hashFunctionUpgrades = HASH_FUNCTION_UPGRADES.get(hashFunction);

        if (hashFunctionUpgrades != null) {
            for (String hashFunctionUpgrade : hashFunctionUpgrades) {
                String fingerprint = fingerprints.get(hashFunctionUpgrade);
                if (fingerprint != null)
                    return hashFunctionUpgrade.toLowerCase();
            }
        }
        return null;
    }

    /**
     * Gets the <code>String</code> representation of a fingerprint specified in the form of an
     * array of <code>byte</code>s in accord with RFC 4572.
     *
     * @param fingerprint an array of <code>bytes</code> which represents a fingerprint the <code>String</code>
     * representation in accord with RFC 4572 of which is to be returned
     * @return the <code>String</code> representation in accord with RFC 4572 of the specified <code>fingerprint</code>
     */
    private static String toHex(byte[] fingerprint)
    {
        if (fingerprint.length == 0)
            throw new IllegalArgumentException("fingerprint");

        char[] chars = new char[3 * fingerprint.length - 1];

        for (int f = 0, fLast = fingerprint.length - 1, c = 0; f <= fLast; f++) {
            int b = fingerprint[f] & 0xff;

            chars[c++] = HEX_ENCODE_TABLE[b >>> 4];
            chars[c++] = HEX_ENCODE_TABLE[b & 0x0f];
            if (f != fLast)
                chars[c++] = ':';
        }
        return new String(chars);
    }

    /**
     * Initializes a new <code>DtlsTransformEngine</code> instance to be associated with and used by
     * this <code>DtlsControlImpl</code> instance. The method is implemented as a factory.
     *
     * @return a new <code>DtlsTransformEngine</code> instance to be associated with
     * and used by this <code>DtlsControlImpl</code> instance
     */
    @Override
    protected DtlsTransformEngine createTransformEngine()
    {
        return new DtlsTransformEngine(this);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void doCleanup()
    {
        super.doCleanup();
        setConnector(null);
        synchronized (this) {
            disposed = true;
            notifyAll();
        }
    }

    /**
     * Gets the certificate with which the local endpoint represented by this instance
     * authenticates its ends of DTLS sessions.
     *
     * @return the certificate with which the local endpoint represented by this instance
     * authenticates its ends of DTLS sessions.
     */
    CertificateInfo getCertificateInfo()
    {
        return mCertificateInfo;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getLocalFingerprint()
    {
        // Timber.d("getCertificateInfo => getLocalFingerprint' %s\n%s",
        //        mCertificateInfo.getCertificateType(), getCertificateInfo().localFingerprint);
        return getCertificateInfo().localFingerprint;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getLocalFingerprintHashFunction()
    {
        // Timber.d("getCertificateInfo => getLocalFingerprintHashFunction' %s: %s",
        //        mCertificateInfo.getCertificateType(), getCertificateInfo().localFingerprintHashFunction);
        return getCertificateInfo().localFingerprintHashFunction;
    }

    /**
     * Gets the properties of {@code DtlsControlImpl} and their values which this instance shares
     * with {@link DtlsTransformEngine} and {@link DtlsPacketTransformer}.
     *
     * @return the properties of {@code DtlsControlImpl} and their values which this instance
     * shares with {@code DtlsTransformEngine} and {@code DtlsPacketTransformer}
     */
    Properties getProperties()
    {
        return mProperties;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean getSecureCommunicationStatus()
    {
        return mSecurityState;
    }

    /**
     * Gets the value of the {@code setup} SDP attribute defined by RFC 4145
     * &quot;TCP-Based Media Transport in the Session Description Protocol (SDP)&quot;
     * which determines whether this instance acts as a DTLS client or a DTLS server.
     *
     * @return the value of the {@code setup} SDP attribute defined by RFC 4145 &quot;TCP-Based
     * Media Transport in the Session Description Protocol (SDP)&quot; which determines whether
     * this instance acts as a DTLS client or a DTLS server
     */
    public DtlsControl.Setup getSetup()
    {
        return getProperties().getSetup();
    }

    /**
     * {@inheritDoc}
     * <p>
     * The implementation of <code>DtlsControlImpl</code> always returns <code>true</code>.
     */
    @Override
    public boolean requiresSecureSignalingTransport()
    {
        return true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setConnector(AbstractRTPConnector connector)
    {
        mProperties.put(Properties.CONNECTOR_PNAME, connector);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setRemoteFingerprints(Map<String, String> remoteFingerprints)
    {
        if (remoteFingerprints == null)
            throw new NullPointerException("remoteFingerprints");

        // Don't pass an empty list to the stack in order to avoid wiping
        // certificates that were contained in a previous request.
        if (remoteFingerprints.isEmpty()) {
            return;
        }

        // Make sure that the hash functions (which are keys of the field
        // remoteFingerprints) are written in lower case.
        Map<String, String> rfs = new HashMap<>(remoteFingerprints.size());

        for (Map.Entry<String, String> e : remoteFingerprints.entrySet()) {
            String k = e.getKey();

            // It makes no sense to provide a fingerprint without a hash function.
            if (k != null) {
                String v = e.getValue();

                // It makes no sense to provide a hash function without a fingerprint.
                if (v != null)
                    rfs.put(k.toLowerCase(), v);
            }
        }
        this.remoteFingerprints = rfs;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setRtcpmux(boolean rtcpmux)
    {
        mProperties.put(Properties.RTCPMUX_PNAME, rtcpmux);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setSetup(Setup setup)
    {
        mProperties.put(Properties.SETUP_PNAME, setup);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void start(MediaType mediaType)
    {
        mProperties.put(Properties.MEDIA_TYPE_PNAME, mediaType);
    }

    /**
     * Update app on the security status of the handshake result
     *
     * @param securityState Security state
     */
    protected void secureOnOff(boolean securityState)
    {
        SrtpListener srtpListener = getSrtpListener();
        MediaType mediaType = (MediaType) mProperties.get(Properties.MEDIA_TYPE_PNAME);
        mSecurityState = securityState;

        if (securityState)
            srtpListener.securityTurnedOn(mediaType, getSrtpControlType().toString(), this);
        else
            srtpListener.securityTurnedOff(mediaType);
    }

    /**
     * Notifies this instance that the DTLS record layer associated with a specific <code>TlsPeer</code> has raised an alert.
     *
     * @param alertLevel {@link AlertLevel} has similar values as SrtpListener
     * @param alertDescription {@link AlertDescription}
     * @param message a human-readable message explaining what caused the alert. May be <code>null</code>.
     * @param cause the exception that caused the alert to be raised. May be <code>null</code>.
     */
    protected void notifyAlertRaised(TlsPeer tlsPeer, short alertLevel, short alertDescription,
            String message, Throwable cause)
    {
        SrtpListener srtpListener = getSrtpListener();
        String errDescription = AlertDescription.getName(alertDescription);

        int srtError = SrtpListener.INFORMATION;
        if (AlertLevel.fatal == alertLevel) {
            srtError = SrtpListener.SEVERE;
        }
        else if (AlertLevel.warning == alertLevel) {
            srtError = SrtpListener.WARNING;
        }

        /* The client and the server must share knowledge that the connection is ending in order to avoid a truncation
         * a initiate the exchange of closing messages.
         * close_notify: This message notifies the recipient that the sender will not send any more messages on this connection.
         * Note that as of TLS 1.1, failure to properly close a connection no longer requires that a session not be resumed.
         * This is a change from TLS 1.0 to conform with widespread implementation practice.
         */
        if (TextUtils.isEmpty(message)) {
            if (AlertDescription.close_notify == alertDescription) {
                srtError = SrtpListener.INFORMATION;  // change to for info only
                message = aTalkApp.getResString(R.string.imp_media_security_ENCRYPTION_ENDED, errDescription);
            }
            else {
                message = aTalkApp.getResString(R.string.impl_media_security_INTERNAL_PROTOCOL_ERROR, errDescription);
            }
        }
        srtpListener.securityMessageReceived(errDescription, message, srtError);
    }

    /**
     * Verifies and validates a specific certificate against the fingerprints presented by the
     * remote endpoint via the signaling path.
     *
     * @param certificate the certificate to be verified and validated against the fingerprints
     * presented by the remote endpoint via the signaling path
     * @throws Exception if the specified <code>certificate</code> failed to verify and validate
     * against the fingerprints presented by the remote endpoint via the signaling path
     */
    private void verifyAndValidateCertificate(Certificate certificate)
            throws Exception
    {
        /*
         * RFC 4572 "Connection-Oriented Media Transport over the Transport Layer Security (TLS)
         * Protocol in the Session Description Protocol (SDP)" defines that "[a] certificate
         * fingerprint MUST be computed using the same one-way hash function as is used in the
         * certificate's signature algorithm."
         */
        String hashFunction = findHashFunction(certificate);

        /*
         * As RFC 5763 "Framework for Establishing a Secure Real-time Transport Protocol (SRTP)
         * Security Context Using Datagram Transport Layer Security (DTLS)" states, "the
         * certificate presented during the DTLS handshake MUST match the fingerprint exchanged
         * via the signaling path in the SDP."
         */
        String remoteFingerprint;

        synchronized (this) {
            if (disposed) {
                throw new IllegalStateException("disposed");
            }

            Map<String, String> remoteFingerprints = this.remoteFingerprints;

            if (remoteFingerprints == null) {
                throw new IOException("No fingerprints declared over the signaling" + " path!");
            }
            remoteFingerprint = remoteFingerprints.get(hashFunction);

            // Unfortunately, Firefox does not comply with RFC 5763 at the time of this writing.
            // Its certificate uses SHA-1 and it sends a fingerprint computed with SHA-256. We
            // could, of course, wait for Mozilla to make Firefox compliant. However, we would
            // like to support Firefox in the meantime. That is why we will allow the fingerprint
            // to "upgrade" the hash function of the certificate much like SHA-256 is an "upgrade" of SHA-1.
            if (remoteFingerprint == null) {
                String hashFunctionUpgrade = findHashFunctionUpgrade(hashFunction, remoteFingerprints);

                if (hashFunctionUpgrade != null
                        && !hashFunctionUpgrade.equalsIgnoreCase(hashFunction)) {
                    remoteFingerprint = remoteFingerprints.get(hashFunctionUpgrade);
                    if (remoteFingerprint != null)
                        hashFunction = hashFunctionUpgrade;
                }
            }
        }
        if (remoteFingerprint == null) {
            throw new IOException("No fingerprint declared over the signaling path with hash function: "
                    + hashFunction + "!");
        }

        String fingerprint = computeFingerprint(certificate, hashFunction);

        if (remoteFingerprint.equals(fingerprint)) {
            Timber.log(TimberLog.FINER, "Fingerprint %s matches the %s-hashed certificate.",
                    remoteFingerprint, hashFunction);
        }
        else {
            throw new IOException("Fingerprint " + remoteFingerprint + " does not match the "
                    + hashFunction + "-hashed certificate " + fingerprint + "!");
        }
    }

    /**
     * Verifies and validates a specific certificate against the fingerprints presented by the
     * remote endpoint via the signaling path.
     *
     * @param certificate the certificate to be verified and validated against the fingerprints
     * presented by the remote endpoint via the signaling path
     * @throws Exception if the specified <code>certificate</code> failed to verify and validate against the
     * fingerprints presented by the remote endpoint over the signaling path
     */
    public void verifyAndValidateCertificate(org.bouncycastle.tls.Certificate certificate)
            throws Exception
    {
        try {
            if (certificate.isEmpty()) {
                throw new IllegalArgumentException("certificate.certificateList");
            }
            else {
                TlsCertificate[] chain = certificate.getCertificateList();
                for (TlsCertificate tlsCertificate : chain) {
                    Certificate entry = Certificate.getInstance(tlsCertificate.getEncoded());
                    verifyAndValidateCertificate(entry);
                }
            }
        } catch (Exception e) {
            String message = "Failed to verify and/or validate a certificate offered over"
                    + " the media path against fingerprints declared over the signaling path!";
            String throwableMessage = e.getMessage();

            if (VERIFY_AND_VALIDATE_CERTIFICATE) {
                if ((throwableMessage == null) || (throwableMessage.length() == 0))
                    Timber.e(e, "%s", message);
                else
                    Timber.e("%s %s", message, throwableMessage);
                throw e;
            }
            else {
                // XXX Contrary to RFC 5763 "Framework for Establishing a Secure
                // Real-time Transport Protocol (SRTP) Security Context Using
                // Datagram Transport Layer Security (DTLS)", we do NOT want to
                // teardown the media session if the fingerprint does not match
                // the hashed certificate. We want to notify the user via the SrtpListener.
                if (throwableMessage == null || throwableMessage.length() == 0)
                    Timber.w(e, "%s", message);
                else
                    Timber.w("%s %s", message, throwableMessage);
            }
        }
    }
}
