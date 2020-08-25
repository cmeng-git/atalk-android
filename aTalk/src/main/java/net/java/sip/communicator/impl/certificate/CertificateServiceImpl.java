/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may
 * obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for
 * the specific language governing permissions
 * and limitations under the License.
 */
package net.java.sip.communicator.impl.certificate;

import android.annotation.SuppressLint;

import net.java.sip.communicator.service.certificate.*;
import net.java.sip.communicator.service.credentialsstorage.CredentialsStorageService;
import net.java.sip.communicator.service.gui.AuthenticationWindowService;

import org.atalk.android.R;
import org.atalk.android.aTalkApp;
import org.atalk.service.configuration.ConfigurationService;
import org.atalk.service.httputil.HttpUtils;
import org.atalk.util.OSUtils;
import org.bouncycastle.asn1.DERIA5String;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.*;
import org.bouncycastle.cert.jcajce.JcaX509ExtensionUtils;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.*;
import java.lang.reflect.Constructor;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.*;
import java.security.KeyStore.Builder;
import java.security.cert.Certificate;
import java.security.cert.*;
import java.util.*;

import javax.net.ssl.*;
import javax.security.auth.callback.*;

import timber.log.Timber;

/**
 * Implementation of the CertificateService. It asks the user to trust a certificate when
 * the automatic verification fails.
 *
 * @author Ingo Bauersachs
 * @author Damian Minkov
 * @author Eng Chong Meng
 */
public class CertificateServiceImpl implements CertificateService, PropertyChangeListener
{
    // ------------------------------------------------------------------------
    // static data
    // ------------------------------------------------------------------------
    private final List<KeyStoreType> supportedTypes = new LinkedList<KeyStoreType>()
    {
        /**
         * Serial version UID.
         */
        private static final long serialVersionUID = 0L;

        {
            if (!OSUtils.IS_WINDOWS64) {
                add(new KeyStoreType("PKCS11", new String[]{".dll", ".so"}, false));
            }
            add(new KeyStoreType("PKCS12", new String[]{".p12", ".pfx"}, true));
            add(new KeyStoreType(KeyStore.getDefaultType(), new String[]{".ks", ".jks"}, true));
        }
    };

    // ------------------------------------------------------------------------
    // services
    // ------------------------------------------------------------------------
    private final ConfigurationService config = CertificateVerificationActivator.getConfigurationService();

    private final CredentialsStorageService credService = CertificateVerificationActivator.getCredService();

    // ------------------------------------------------------------------------
    // properties
    // ------------------------------------------------------------------------
    /**
     * Base property name for the storage of certificate user preferences.
     */
    public final static String PNAME_CERT_TRUST_PREFIX = "certservice";

    public final static String CERT_TRUST_SERVER_SUBFIX = ".server.";
    public final static String CERT_TRUST_PARAM_SUBFIX = ".param.";

    public final static String CERT_XMPP_CLIENT_SUBFIX = "_xmpp-client.";

    /**
     * Hash algorithm for the cert thumbprint
     */
    private final static String THUMBPRINT_HASH_ALGORITHM = "SHA1";

    // ------------------------------------------------------------------------
    // fields
    // ------------------------------------------------------------------------
    /**
     * Stores the certificates that are trusted as long as this service lives.
     */
    private Map<String, List<String>> sessionAllowedCertificates = new HashMap<>();

    /**
     * Caches retrievals of AIA information (downloaded certs or failures).
     */
    private Map<URI, AiaCacheEntry> aiaCache = new HashMap<>();

    // ------------------------------------------------------------------------
    // Map access helpers
    // ------------------------------------------------------------------------

    /**
     * Helper method to avoid accessing null-lists in the session allowed certificate map
     *
     * @param propName the key to access
     * @return the list for the given list or a new, empty list put in place for the key
     */
    private List<String> getSessionCertEntry(String propName)
    {
        List<String> entry = sessionAllowedCertificates.get(propName);
        if (entry == null) {
            entry = new LinkedList<>();
            sessionAllowedCertificates.put(propName, entry);
        }
        return entry;
    }

    public void purgeSessionCertificate()
    {
        sessionAllowedCertificates.clear();
    }

    /**
     * AIA cache retrieval entry.
     */
    private static class AiaCacheEntry
    {
        Date cacheDate;
        X509Certificate cert;

        AiaCacheEntry(Date cacheDate, X509Certificate cert)
        {
            this.cacheDate = cacheDate;
            this.cert = cert;
        }
    }

    // ------------------------------------------------------------------------
    // TrustStore configuration
    // ------------------------------------------------------------------------

    /**
     * Initializes a new <tt>CertificateServiceImpl</tt> instance.
     */
    public CertificateServiceImpl()
    {
        setTrustStore();
        config.addPropertyChangeListener(PNAME_TRUSTSTORE_TYPE, this);

        System.setProperty(SECURITY_CRLDP_ENABLE, config.getString(PNAME_REVOCATION_CHECK_ENABLED, "false"));
        System.setProperty(SECURITY_SSL_CHECK_REVOCATION, config.getString(PNAME_REVOCATION_CHECK_ENABLED, "false"));
        Security.setProperty(SECURITY_OCSP_ENABLE, config.getString(PNAME_OCSP_ENABLED, "false"));
    }

    public void propertyChange(PropertyChangeEvent evt)
    {
        setTrustStore();
    }

    private void setTrustStore()
    {
        String tsType = (String) config.getProperty(PNAME_TRUSTSTORE_TYPE);
        String tsFile = (String) config.getProperty(PNAME_TRUSTSTORE_FILE);
        String tsPassword = credService.loadPassword(PNAME_TRUSTSTORE_PASSWORD);

        // use the OS store as default store on Windows
        if (((tsType == null) || !"meta:default".equals(tsType)) && OSUtils.IS_WINDOWS) {
            tsType = "Windows-ROOT";
            config.setProperty(PNAME_TRUSTSTORE_TYPE, tsType);
        }

        if (tsType != null && !"meta:default".equals(tsType))
            System.setProperty("javax.net.ssl.trustStoreType", tsType);
        else
            System.getProperties().remove("javax.net.ssl.trustStoreType");

        if (tsFile != null)
            System.setProperty("javax.net.ssl.trustStore", tsFile);
        else
            System.getProperties().remove("javax.net.ssl.trustStore");

        if (tsPassword != null)
            System.setProperty("javax.net.ssl.trustStorePassword", tsPassword);
        else
            System.getProperties().remove("javax.net.ssl.trustStorePassword");
    }

    // ------------------------------------------------------------------------
    // Client authentication configuration
    // ------------------------------------------------------------------------

    /**
     * (non-Javadoc)
     *
     * @see CertificateService#getSupportedKeyStoreTypes()
     */
    public List<KeyStoreType> getSupportedKeyStoreTypes()
    {
        return supportedTypes;
    }

    /**
     * (non-Javadoc)
     *
     * @see CertificateService#getClientAuthCertificateConfigs()
     */
    public List<CertificateConfigEntry> getClientAuthCertificateConfigs()
    {
        List<CertificateConfigEntry> map = new LinkedList<>();
        for (String propName : config.getPropertyNamesByPrefix(PNAME_CLIENTAUTH_CERTCONFIG_BASE, false)) {
            String propValue = config.getString(propName);
            if (propValue == null || !propName.endsWith(propValue))
                continue;

            String pnBase = PNAME_CLIENTAUTH_CERTCONFIG_BASE + "." + propValue;
            CertificateConfigEntry e = new CertificateConfigEntry(null);
            e.setId(propValue);
            e.setAlias(config.getString(pnBase + ".alias"));
            e.setDisplayName(config.getString(pnBase + ".displayName"));
            e.setKeyStore(config.getString(pnBase + ".keyStore"));
            e.setSavePassword(config.getBoolean(pnBase + ".savePassword", false));
            if (e.isSavePassword()) {
                e.setKeyStorePassword(credService.loadPassword(pnBase));
            }
            String type = config.getString(pnBase + ".keyStoreType");
            for (KeyStoreType kt : getSupportedKeyStoreTypes()) {
                if (kt.getName().equals(type)) {
                    e.setKeyStoreType(kt);
                    break;
                }
            }
            map.add(e);
        }
        return map;
    }

    /**
     * (non-Javadoc)
     *
     * @see CertificateService#setClientAuthCertificateConfig(CertificateConfigEntry)
     */
    public void setClientAuthCertificateConfig(CertificateConfigEntry e)
    {
        if (e.getId() == null)
            e.setId("conf" + Math.abs(new Random().nextInt()));
        String pn = PNAME_CLIENTAUTH_CERTCONFIG_BASE + "." + e.getId();
        config.setProperty(pn, e.getId());
        config.setProperty(pn + ".alias", e.getAlias());
        config.setProperty(pn + ".displayName", e.getDisplayName());
        config.setProperty(pn + ".keyStore", e.getKeyStore());
        config.setProperty(pn + ".savePassword", e.isSavePassword());
        if (e.isSavePassword())
            credService.storePassword(pn, e.getKeyStorePassword());
        else
            credService.removePassword(pn);
        config.setProperty(pn + ".keyStoreType", e.getKeyStoreType());
    }

    /**
     * (non-Javadoc)
     *
     * @see CertificateService#removeClientAuthCertificateConfig(String)
     */
    public void removeClientAuthCertificateConfig(String id)
    {
        for (String p : config.getPropertyNamesByPrefix(PNAME_CLIENTAUTH_CERTCONFIG_BASE + "." + id, true)) {
            config.removeProperty(p);
        }
        config.removeProperty(PNAME_CLIENTAUTH_CERTCONFIG_BASE + "." + id);
    }

    // ------------------------------------------------------------------------
    // Certificate trust handling
    // ------------------------------------------------------------------------

    /**
     * (non-Javadoc)
     *
     * @see CertificateService#addCertificateToTrust(Certificate, String, int)
     */
    public void addCertificateToTrust(Certificate cert, String trustFor, int trustMode)
            throws CertificateException
    {
        String propName = PNAME_CERT_TRUST_PREFIX + CERT_TRUST_PARAM_SUBFIX + trustFor;
        String thumbprint = getThumbprint(cert, THUMBPRINT_HASH_ALGORITHM);
        switch (trustMode) {
            case DO_NOT_TRUST:
                throw new IllegalArgumentException("Cannot add a certificate to trust when no trust is requested.");
            case TRUST_ALWAYS:
                String current = config.getString(propName);
                if (current.contains(thumbprint))
                    break;

                String newValue = thumbprint;
                newValue += "," + current;
                config.setProperty(propName, newValue);
                break;
            case TRUST_THIS_SESSION_ONLY:
                getSessionCertEntry(propName).add(thumbprint);
                break;
        }
    }

    /**
     * Fetch all the server authenticated certificates
     *
     * @return all the server authenticated certificates
     */
    public List<String> getAllServerAuthCertificates()
    {
        return config.getPropertyNamesByPrefix(PNAME_CERT_TRUST_PREFIX, false);
    }

    /**
     * Remove server certificate for the given certEntry
     *
     * @param certEntry to be removed
     */
    public void removeCertificateEntry(String certEntry)
    {
        config.setProperty(certEntry, null);
    }

    /**
     * (non-Javadoc)
     *
     * @see CertificateService#getSSLContext()
     */
    public SSLContext getSSLContext()
            throws GeneralSecurityException
    {
        return getSSLContext(getTrustManager((Iterable<String>) null));
    }

    /**
     * (non-Javadoc)
     *
     * @see CertificateService#getSSLContext(X509TrustManager)
     */
    public SSLContext getSSLContext(X509TrustManager trustManager)
            throws GeneralSecurityException
    {
        try {
            KeyStore ks = KeyStore.getInstance(System.getProperty("javax.net.ssl.keyStoreType",
                    KeyStore.getDefaultType()));
            KeyManagerFactory kmFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());

            String keyStorePassword = System.getProperty("javax.net.ssl.keyStorePassword");
            if (System.getProperty("javax.net.ssl.keyStore") != null) {
                ks.load(new FileInputStream(System.getProperty("javax.net.ssl.keyStore")), null);
            }
            else {
                ks.load(null, null);
            }
            kmFactory.init(ks, keyStorePassword == null ? null : keyStorePassword.toCharArray());
            return getSSLContext(kmFactory.getKeyManagers(), trustManager);
        } catch (Exception e) {
            throw new GeneralSecurityException("Cannot init SSLContext", e);
        }
    }

    private Builder loadKeyStore(final CertificateConfigEntry entry)
            throws KeyStoreException, UnrecoverableEntryException
    {
        final File f = new File(entry.getKeyStore());
        final String keyStoreType = entry.getKeyStoreType().getName();
        if ("PKCS11".equals(keyStoreType)) {
            String config = "name=" + f.getName() + "\nlibrary=" + f.getAbsoluteFile();
            try {
                Class<?> pkcs11c = Class.forName("sun.security.pkcs11.SunPKCS11");
                Constructor<?> c = pkcs11c.getConstructor(InputStream.class);
                Provider p = (Provider) c.newInstance(new ByteArrayInputStream(config.getBytes()));
                Security.insertProviderAt(p, 0);
            } catch (Exception e) {
                Timber.e("Access PKCS11 provider on an unsupported platform or the load failed: %s", e.getMessage());
            }
        }
        KeyStore.Builder ksBuilder = KeyStore.Builder.newInstance(keyStoreType, null, f,
                new KeyStore.CallbackHandlerProtection(callbacks -> {
                    for (Callback cb : callbacks) {
                        if (!(cb instanceof PasswordCallback))
                            throw new UnsupportedCallbackException(cb);

                        PasswordCallback pwcb = (PasswordCallback) cb;
                        if (entry.isSavePassword()) {
                            pwcb.setPassword(entry.getKSPassword());
                            return;
                        }
                        else {
                            AuthenticationWindowService authenticationWindowService
                                    = CertificateVerificationActivator.getAuthenticationWindowService();
                            if (authenticationWindowService == null) {
                                Timber.e("No AuthenticationWindowService implementation");
                                throw new IOException("User cancel");
                            }

                            AuthenticationWindowService.AuthenticationWindow aw
                                    = authenticationWindowService.create(f.getName(), null, keyStoreType, false,
                                    false, null, null, null, null, null, null, null);

                            aw.setAllowSavePassword(true);
                            aw.setVisible(true);
                            if (!aw.isCanceled()) {
                                pwcb.setPassword(aw.getPassword());
                                entry.setKeyStorePassword(new String(aw.getPassword()));
                            }
                            else
                                throw new IOException("User cancel");
                        }
                    }
                }));
        return ksBuilder;
    }

    /**
     * (non-Javadoc)
     *
     * @see CertificateService#getSSLContext(String, X509TrustManager)
     */
    public SSLContext getSSLContext(String clientCertConfig, X509TrustManager trustManager)
            throws GeneralSecurityException
    {
        try {
            if (clientCertConfig == null)
                return getSSLContext(trustManager);

            CertificateConfigEntry entry = null;
            for (CertificateConfigEntry e : getClientAuthCertificateConfigs()) {
                if (e.toString().equals(clientCertConfig)) {
                    entry = e;
                    break;
                }
            }
            if (entry == null) {
                throw new GeneralSecurityException("Client certificate config with id <" + clientCertConfig + "> not found.");
            }

            KeyStore clientKeyStore = loadKeyStore(entry).getKeyStore();
            char[] clientKeyStorePass = entry.getKSPassword();

            // cmeng: "NewSunX509" or "SunX509": NoSuchAlgorithmException: SunX509 KeyManagerFactory not available
            // final KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509"); OR "NewSunX509"

            // Not supported: GeneralSecurityException: Cannot init SSLContext: ManagerFactoryParameters not supported
            // KeyStoreBuilderParameters ksBuilerParm = new KeyStoreBuilderParameters(loadKeyStore(entry));
            // kmf.init(ksBuilerParm);

            // so use getDefaultAlgorithm() => "PKIX"
            String kmfAlgorithm = KeyManagerFactory.getDefaultAlgorithm();
            final KeyManagerFactory kmf = KeyManagerFactory.getInstance(kmfAlgorithm);

            kmf.init(clientKeyStore, clientKeyStorePass);
            KeyManager[] kms = kmf.getKeyManagers();
            return getSSLContext(kms, trustManager);
        } catch (Exception e) {
            throw new GeneralSecurityException("Cannot init SSLContext: " + e.getMessage(), e);
        }
    }

    /**
     * (non-Javadoc)
     *
     * @see CertificateService#getSSLContext(KeyManager[], X509TrustManager)
     */
    public SSLContext getSSLContext(KeyManager[] keyManagers, X509TrustManager trustManager)
            throws GeneralSecurityException
    {
        // cmeng: need to take care o daneVerifier? from abstractXMPPConnection#getSmackTlsContext()
        // if (daneVerifier != null) {
        //     // User requested DANE verification.
        //     daneVerifier.init(context, kms, customTrustManager, secureRandom);
        // }

        try {
            final SecureRandom secureRandom = new java.security.SecureRandom();
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(keyManagers, new TrustManager[]{trustManager}, secureRandom);
            return sslContext;
        } catch (Exception e) {
            throw new GeneralSecurityException("Cannot init SSLContext", e);
        }
    }

    /**
     * (non-Javadoc)
     *
     * @see CertificateService#getTrustManager(Iterable)
     */
    public X509TrustManager getTrustManager(Iterable<String> identitiesToTest)
            throws GeneralSecurityException
    {
        return getTrustManager(identitiesToTest, new EMailAddressMatcher(), new BrowserLikeHostnameMatcher());
    }

    /**
     * (non-Javadoc)
     *
     * @see CertificateService#getTrustManager(String)
     */
    public X509TrustManager getTrustManager(String identityToTest)
            throws GeneralSecurityException
    {
        return getTrustManager(Collections.singletonList(identityToTest),
                new EMailAddressMatcher(), new BrowserLikeHostnameMatcher());
    }

    /**
     * (non-Javadoc)
     *
     * @see CertificateService#getTrustManager(String, CertificateMatcher, CertificateMatcher)
     */
    public X509TrustManager getTrustManager(String identityToTest,
            CertificateMatcher clientVerifier, CertificateMatcher serverVerifier)
            throws GeneralSecurityException
    {
        return getTrustManager(Collections.singletonList(identityToTest), clientVerifier, serverVerifier);
    }

    /**
     * (non-Javadoc)
     *
     * @see CertificateService#getTrustManager(Iterable, CertificateMatcher, CertificateMatcher)
     */
    public X509TrustManager getTrustManager(final Iterable<String> identitiesToTest,
            final CertificateMatcher clientVerifier, final CertificateMatcher serverVerifier)
            throws GeneralSecurityException
    {
        // Obtain the system default X509 trust manager
        X509TrustManager defaultTm = null;
        TrustManagerFactory tmFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        // TrustManagerFactory tmFactory = TrustManagerFactory.getInstance("X509");

        // Workaround for https://bugs.openjdk.java.net/browse/JDK-6672015
        KeyStore ks = null;
        String tsType = System.getProperty("javax.net.ssl.trustStoreType", null);
        if ("Windows-ROOT".equals(tsType)) {
            try {
                ks = KeyStore.getInstance(tsType);
                ks.load(null, null);
            } catch (Exception e) {
                Timber.e(e, "Could not rename Windows-ROOT aliases");
            }
        }

        tmFactory.init(ks);
        for (TrustManager m : tmFactory.getTrustManagers()) {
            if (m instanceof X509TrustManager) {
                defaultTm = (X509TrustManager) m;
                break;
            }
        }
        if (defaultTm == null)
            throw new GeneralSecurityException("No default X509 trust manager found");

        final X509TrustManager tm = defaultTm;
        return new EntityTrustManager(tm, identitiesToTest, clientVerifier, serverVerifier);
    }


    /**
     * Creates a trustManager that validates the certificate based on the specified verifiers and
     * asks the user when the validation fails. When <tt>null</tt> is passed as the
     * <tt>identityToTest</tt> then no check is performed whether the certificate is valid for a
     * specific server or client.
     *
     * The trust manager which asks the client whether to trust particular certificate which is not
     * android root's CA trusted.
     *
     * Return TrustManager to use in an SSLContext
     */
    private class EntityTrustManager implements X509TrustManager
    {
        private final X509TrustManager tm;
        private final Iterable<String> identitiesToTest;
        private final CertificateMatcher clientVerifier;
        private final CertificateMatcher serverVerifier;

        /**
         * Creates the custom trust manager.
         *
         * @param tm the default trust manager for verification.
         * @param identitiesToTest The identities to match against the supplied verifiers.
         * @param clientVerifier The verifier to use in calls to checkClientTrusted
         * @param serverVerifier The verifier to use in calls to checkServerTrusted
         */
        EntityTrustManager(X509TrustManager tm, final Iterable<String> identitiesToTest,
                final CertificateMatcher clientVerifier, final CertificateMatcher serverVerifier)
        {
            this.tm = tm;
            this.identitiesToTest = identitiesToTest;
            this.clientVerifier = clientVerifier;
            this.serverVerifier = serverVerifier;
        }

        public X509Certificate[] getAcceptedIssuers()
        {
            return tm.getAcceptedIssuers();
        }

        public void checkServerTrusted(X509Certificate[] chain, String authType)
                throws CertificateException
        {
            checkCertTrusted(chain, authType, true);
        }

        public void checkClientTrusted(X509Certificate[] chain, String authType)
                throws CertificateException
        {
            checkCertTrusted(chain, authType, false);
        }

        private void checkCertTrusted(X509Certificate[] chain, String authType, Boolean serverCheck)
                throws CertificateException
        {
            // check and default configurations for property if missing default is null - false
            String defaultAlwaysTrustMode = CertificateVerificationActivator.getResources()
                    .getSettingsString(CertificateService.PNAME_ALWAYS_TRUST);
            if (config.getBoolean(PNAME_ALWAYS_TRUST, Boolean.parseBoolean(defaultAlwaysTrustMode)))
                return;

            try {
                // check the certificate itself (issuer, validity)
                try {
                    chain = tryBuildChain(chain);
                } catch (Exception e) {
                } // don't care and take the chain as is

                /*
                 * Domain specific configurations require that hostname aware
                 * checkServerTrusted(X509Certificate[], String, String) is used
                 * but required X509ExtenderTrustManager (API=24)
                 */
                if (serverCheck)
                    tm.checkServerTrusted(chain, authType);
                else
                    tm.checkClientTrusted(chain, authType);

                if ((identitiesToTest == null) || !identitiesToTest.iterator().hasNext())
                    return;
                else if (serverCheck)
                    serverVerifier.verify(identitiesToTest, chain[0]);
                else
                    clientVerifier.verify(identitiesToTest, chain[0]);
                // ok, globally valid cert
            } catch (CertificateException e) {
                String thumbprint = getThumbprint(chain[0], THUMBPRINT_HASH_ALGORITHM);
                String message;
                List<String> propNames = new LinkedList<>();
                List<String> storedCerts = new LinkedList<>();
                String appName = aTalkApp.getResString(R.string.APPLICATION_NAME);

                Timber.w("SSL certificate untrusted chain: %s", e.getMessage());
                if ((identitiesToTest == null) || !identitiesToTest.iterator().hasNext()) {
                    String propName = PNAME_CERT_TRUST_PREFIX + CERT_TRUST_SERVER_SUBFIX + thumbprint;
                    propNames.add(propName);
                    message = aTalkApp.getResString(R.string.service_gui_CERT_DIALOG_DESCRIPTION_TXT_NOHOST, appName);

                    // get the thumbprints from the permanent allowances
                    String hashes = config.getString(propName);
                    if (hashes != null)
                        Collections.addAll(storedCerts, hashes.split(","));

                    // get the thumbprints from the session allowances
                    List<String> sessionCerts = sessionAllowedCertificates.get(propName);
                    if (sessionCerts != null)
                        storedCerts.addAll(sessionCerts);
                }
                else {
                    if (serverCheck) {
                        message = aTalkApp.getResString(R.string.service_gui_CERT_DIALOG_DESCRIPTION_TXT,
                                appName, identitiesToTest.toString());
                    }
                    else {
                        message = aTalkApp.getResString(R.string.service_gui_CERT_DIALOG_PEER_DESCRIPTION_TXT,
                                appName, identitiesToTest.toString());
                    }
                    for (String identity : identitiesToTest) {
                        String propName = PNAME_CERT_TRUST_PREFIX + CERT_TRUST_PARAM_SUBFIX + identity;
                        propNames.add(propName);

                        // get the thumbprints from the permanent allowances
                        String hashes = config.getString(propName);
                        if (hashes != null)
                            Collections.addAll(storedCerts, hashes.split(","));

                        // get the thumbprints from the session allowances
                        List<String> sessionCerts = sessionAllowedCertificates.get(propName);
                        if (sessionCerts != null)
                            storedCerts.addAll(sessionCerts);
                    }
                }

                if (!storedCerts.contains(thumbprint)) {
                    switch (verify(chain, message)) {
                        case DO_NOT_TRUST:
                            throw new CertificateException("Peer provided certificate with Subject <"
                                    + chain[0].getSubjectDN() + "> is not trusted", e);
                        case TRUST_ALWAYS:
                            for (String propName : propNames) {
                                String current = config.getString(propName);
                                String newValue = thumbprint;
                                if (current != null)
                                    newValue += "," + current;
                                // Timber.w(new Exception("Add Certificate To Trust: " + propName + ": " + newValue));
                                config.setProperty(propName, newValue);
                            }
                            break;
                        case TRUST_THIS_SESSION_ONLY:
                            for (String propName : propNames)
                                getSessionCertEntry(propName).add(thumbprint);
                            break;
                    }
                }
                // ok, we've seen this certificate before
            }
        }

        /*
         * Only try to build chains for servers that send only their own cert, but no issuer.
         * This also matches self signed (will be ignored later) and Root-CA signed certs.
         * In this case we throw the Root-CA away after the lookup
         */
        private X509Certificate[] tryBuildChain(X509Certificate[] chain)
                throws IOException, URISyntaxException, CertificateException
        {
            if (chain.length != 1)
                return chain;

            // ignore self signed certs (issuer == signer)
            if (chain[0].getIssuerDN().equals(chain[0].getSubjectDN()))
                return chain;

            // prepare for the newly created chain
            List<X509Certificate> newChain = new ArrayList<>(chain.length + 4);
            Collections.addAll(newChain, chain);

            // search from the topmost certificate upwards
            CertificateFactory certFactory = CertificateFactory.getInstance("X.509");
            X509Certificate current = chain[chain.length - 1];
            boolean foundParent;
            int chainLookupCount = 0;
            do {
                foundParent = false;
                // extract the url(s) where the parent certificate can be found
                byte[] aiaBytes = current.getExtensionValue(Extension.authorityInfoAccess.getId());
                if (aiaBytes == null)
                    break;
                AuthorityInformationAccess aia
                        = AuthorityInformationAccess.getInstance(JcaX509ExtensionUtils.parseExtensionValue(aiaBytes));

                // the AIA may contain different URLs and types, try all of them
                for (AccessDescription ad : aia.getAccessDescriptions()) {
                    // we are only interested in the issuer certificate, not in OCSP urls the like
                    if (!ad.getAccessMethod().equals(AccessDescription.id_ad_caIssuers))
                        continue;

                    GeneralName gn = ad.getAccessLocation();
                    if (!(gn.getTagNo() == GeneralName.uniformResourceIdentifier
                            && gn.getName() instanceof DERIA5String))
                        continue;

                    URI uri = new URI(((DERIA5String) gn.getName()).getString());
                    // only http(s) urls; LDAP is taken care of in the default implementation
                    if (!(uri.getScheme().equalsIgnoreCase("http")
                            || uri.getScheme().equals("https")))
                        continue;

                    X509Certificate cert = null;

                    // try to get cert from cache first to avoid consecutive-slow http lookups
                    AiaCacheEntry cache = aiaCache.get(uri);
                    if (cache != null && cache.cacheDate.after(new Date())) {
                        cert = cache.cert;
                    }
                    else {
                        // download if no cache entry or if it has expired
                        Timber.d("Downloading parent certificate for <%s> from <%s>",
                                current.getSubjectDN(), uri);
                        try {
                            InputStream is = HttpUtils.openURLConnection(uri.toString()).getContent();
                            cert = (X509Certificate) certFactory.generateCertificate(is);
                        } catch (Exception e) {
                            Timber.d("Could not download from <" + uri + ">");
                        }
                        // cache for 10mins
                        aiaCache.put(uri, new AiaCacheEntry(new Date(new Date().getTime() + 10 * 60 * 1000), cert));
                    }
                    if (cert != null) {
                        if (!cert.getIssuerDN().equals(cert.getSubjectDN())) {
                            newChain.add(cert);
                            foundParent = true;
                            current = cert;
                            break; // an AD was valid, ignore others
                        }
                        else
                            Timber.d("Parent is self-signed, ignoring");
                    }
                }
                chainLookupCount++;
            } while (foundParent && chainLookupCount < 10);
            chain = newChain.toArray(chain);
            return chain;
        }
    }

    protected class BrowserLikeHostnameMatcher implements CertificateMatcher
    {
        public void verify(Iterable<String> identitiesToTest, X509Certificate cert)
                throws CertificateException
        {
            // check whether one of the hostname is present in the certificate
            boolean oneMatched = false;
            for (String identity : identitiesToTest) {
                try {
                    org.apache.http.conn.ssl.SSLSocketFactory.BROWSER_COMPATIBLE_HOSTNAME_VERIFIER.verify(identity, cert);
                    oneMatched = true;
                    break;
                } catch (SSLException e) {
                }
            }

            if (!oneMatched)
                throw new CertificateException("None of <" + identitiesToTest
                        + "> matched the cert with CN=" + cert.getSubjectDN());
        }
    }

    protected class EMailAddressMatcher implements CertificateMatcher
    {
        public void verify(Iterable<String> identitiesToTest, X509Certificate cert)
                throws CertificateException
        {
            // check if the certificate contains the E-Mail address(es) in the SAN(s)
            // TODO: extract address from DN (E-field) too?
            boolean oneMatched = false;
            Iterable<String> emails = getSubjectAltNames(cert, 6);
            for (String identity : identitiesToTest) {
                for (String email : emails) {
                    if (identity.equalsIgnoreCase(email)) {
                        oneMatched = true;
                        break;
                    }
                }
            }
            if (!oneMatched)
                throw new CertificateException("The peer provided certificate with Subject <"
                        + cert.getSubjectDN() + "> contains no SAN for <" + identitiesToTest + ">");
        }
    }

    /**
     * Asks the user whether he trusts the supplied chain of certificates.
     *
     * @param chain The chain of the certificates to check with user.
     * @param message A text that describes why the verification failed.
     * @return The result of the user interaction. One of
     * {@link CertificateService#DO_NOT_TRUST},
     * {@link CertificateService#TRUST_THIS_SESSION_ONLY},
     * {@link CertificateService#TRUST_ALWAYS}
     */
    protected int verify(final X509Certificate[] chain, final String message)
    {
        if (config.getBoolean(PNAME_NO_USER_INTERACTION, false))
            return DO_NOT_TRUST;

        if (CertificateVerificationActivator.getCertificateDialogService() == null) {
            Timber.e("Missing CertificateDialogService by default will not trust!");
            return DO_NOT_TRUST;
        }

        // show for proper moment, other may be obscure by others
        aTalkApp.waitForFocus();
        VerifyCertificateDialogService.VerifyCertificateDialog dialog
                = CertificateVerificationActivator.getCertificateDialogService().createDialog(chain, null, message);
        dialog.setVisible(true);

        if (!dialog.isTrusted())
            return DO_NOT_TRUST;
        else if (dialog.isAlwaysTrustSelected())
            return TRUST_ALWAYS;
        else
            return TRUST_THIS_SESSION_ONLY;
    }

    /**
     * Calculates the hash of the certificate known as the "thumbprint" and returns it as a string representation.
     *
     * @param cert The certificate to hash.
     * @param algorithm The hash algorithm to use.
     * @return The SHA-1 hash of the certificate.
     * @throws CertificateException
     */
    @SuppressLint("NewApi")
    private static String getThumbprint(Certificate cert, String algorithm)
            throws CertificateException
    {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance(algorithm);
        } catch (NoSuchAlgorithmException e) {
            throw new CertificateException(e);
        }
        byte[] encodedCert = cert.getEncoded();
        StringBuilder sb = new StringBuilder(encodedCert.length * 2);
        try (Formatter f = new Formatter(sb)) {
            for (byte b : digest.digest(encodedCert))
                f.format("%02x", b);
        }
        return sb.toString();
    }

    /**
     * Gets the SAN (Subject Alternative Name) of the specified type.
     *
     * @param cert the certificate to extract from
     * @param altNameType The type to be returned
     * @return SAN of the type
     * <p>
     * <PRE>
     * GeneralName ::= CHOICE {
     * otherName                   [0]   OtherName,
     * rfc822Name                  [1]   IA5String,
     * dNSName                     [2]   IA5String,
     * x400Address                 [3]   ORAddress,
     * directoryName               [4]   Name,
     * ediPartyName                [5]   EDIPartyName,
     * uniformResourceIdentifier   [6]   IA5String,
     * iPAddress                   [7]   OCTET STRING,
     * registeredID                [8]   OBJECT IDENTIFIER
     * }
     * <p>
     * <PRE>
     */
    private static Iterable<String> getSubjectAltNames(X509Certificate cert, int altNameType)
    {
        Collection<List<?>> altNames;
        try {
            altNames = cert.getSubjectAlternativeNames();
        } catch (CertificateParsingException e) {
            return Collections.emptyList();
        }

        List<String> matchedAltNames = new LinkedList<>();
        for (List<?> item : altNames) {
            if (item.contains(altNameType)) {
                Integer type = (Integer) item.get(0);
                if (type == altNameType)
                    matchedAltNames.add((String) item.get(1));
            }
        }
        return matchedAltNames;
    }
}
