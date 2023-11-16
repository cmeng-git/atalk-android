/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.protocol;

import static org.atalk.impl.neomedia.transform.dtls.DtlsControlImpl.DEFAULT_SIGNATURE_AND_HASH_ALGORITHM;

import net.java.sip.communicator.util.UtilActivator;

import org.atalk.impl.neomedia.transform.zrtp.ZrtpControlImpl;
import org.atalk.service.neomedia.SDesControl;
import org.atalk.service.neomedia.SrtpControlType;

import java.io.Serializable;
import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The <code>SecurityAccountRegistration</code> is used to determine security options for different
 * registration protocol (Jabber, SIP). Useful to the SecurityPanel.
 *
 * @author Vincent Lucas
 * @author Pawel Domas
 * @author Lyubomir Marinov
 * @author Eng Chong Meng
 * @author Eng Chong Meng
 * @author MilanKral
 */
public abstract class SecurityAccountRegistration implements Serializable
{
    /**
     * The encryption protocols managed by this SecurityPanel.
     */
    public static final List<String> ENCRYPTION_PROTOCOL = Collections.unmodifiableList(Arrays.asList(
            SrtpControlType.ZRTP.toString(),
            SrtpControlType.DTLS_SRTP.toString(),
            SrtpControlType.SDES.toString()
    ));

    /**
     * Enables support to encrypt calls.
     */
    private boolean mCallEncryptionEnable = true;

    /**
     * Enables ZRTP encryption advertise in jingle session content.
     */
    private boolean mSipZrtpAttribute = true;

    /**
     * Tells if SDES is enabled for this account.
     */
    private boolean mSdesEnable = false;

    /**
     * The list of cipher suites enabled for SDES.
     */
    private String mSdesCipherSuites;

    /**
     * DTLS_SRTP Certificate Signature Algorithm.
     */
    private String mTlsCertificateSA;

    /**
     * The map between encryption protocols and their priority order.
     */
    private Map<String, Integer> mEncryptionProtocol;

    /**
     * The map between encryption protocols and their status (enabled or disabled).
     */
    private Map<String, Boolean> mEncryptionProtocolStatus;

    private static final SecureRandom mSecureRandom = new SecureRandom();

    /**
     * Random salt value used for ZID calculation.
     */
    private String mZIDSalt;

    /**
     * Initializes the security account registration properties with the default values.
     */
    public SecurityAccountRegistration()
    {
        // Sets the default values.
        mEncryptionProtocol = new HashMap<String, Integer>()
        {{
            put("ZRTP", 0);
            put("DTLS_SRTP", 1);
        }};

        mEncryptionProtocolStatus = new HashMap<String, Boolean>()
        {{
            put("ZRTP", true);
            put("DTLS_SRTP", true);
        }};

        randomZIDSalt();
        mTlsCertificateSA = DEFAULT_SIGNATURE_AND_HASH_ALGORITHM;
        mSdesCipherSuites = UtilActivator.getResources().getSettingsString(SDesControl.SDES_CIPHER_SUITES);
    }

    /**
     * If call encryption is enabled
     *
     * @return If call encryption is enabled
     */
    public boolean isCallEncryption()
    {
        return mCallEncryptionEnable;
    }

    /**
     * Sets call encryption enable status
     *
     * @param callEncryption if we want to set call encryption on as default
     */
    public void setCallEncryption(boolean callEncryption)
    {
        mCallEncryptionEnable = callEncryption;
    }

    /**
     * Check if to include the ZRTP attribute to SIP/SDP or to Jabber/ Jingle IQ
     *
     * @return include the ZRTP attribute to SIP/SDP or to Jabber/ Jingle IQ
     */
    public boolean isSipZrtpAttribute()
    {
        return mSipZrtpAttribute;
    }

    /**
     * Sets ZRTP attribute support
     *
     * @param sipZrtpAttribute include the ZRTP attribute to SIP/SDP or to Jabber/IQ
     */
    public void setSipZrtpAttribute(boolean sipZrtpAttribute)
    {
        mSipZrtpAttribute = sipZrtpAttribute;
    }

    /**
     * Tells if SDES is enabled for this account.
     *
     * @return True if SDES is enabled. False, otherwise.
     */
    public boolean isSDesEnable()
    {
        return mSdesEnable;
    }

    /**
     * Enables or disables SDES for this account.
     *
     * @param sdesEnable True to enable SDES. False, otherwise.
     */
    public void setSDesEnable(boolean sdesEnable)
    {
        mSdesEnable = sdesEnable;
    }

    /**
     * Returns the list of cipher suites enabled for SDES.
     *
     * @return The list of cipher suites enabled for SDES. Null if no cipher suite is enabled.
     */
    public String getSDesCipherSuites()
    {
        return mSdesCipherSuites;
    }

    /**
     * Sets the list of cipher suites enabled for SDES.
     *
     * @param cipherSuites The list of cipher suites enabled for SDES. Null if no cipher suite is enabled.
     */
    public void setSDesCipherSuites(String cipherSuites)
    {
        mSdesCipherSuites = cipherSuites;
    }

    /**
     * Returns the tls certificate signature algorithm.
     *
     * @return the tls certificate signature algorithm.
     */
    public String getDtlsCertSa()
    {
        return mTlsCertificateSA;
    }

    /**
     * Set the tls certificate signature algorithm.
     */
    public void setDtlsCertSa(String certSA)
    {
        mTlsCertificateSA = certSA;
    }

    /**
     * Sets the method used for RTP/SAVP indication.
     */
    public abstract void setSavpOption(int savpOption);

    /**
     * Returns the method used for RTP/SAVP indication.
     *
     * @return the method used for RTP/SAVP indication.
     */
    public abstract int getSavpOption();

    /**
     * Returns the map between the encryption protocols and their priority order.
     *
     * @return The map between the encryption protocols and their priority order.
     */
    public Map<String, Integer> getEncryptionProtocol()
    {
        return mEncryptionProtocol;
    }

    /**
     * Sets the map between the encryption protocols and their priority order.
     *
     * @param encryptionProtocol The map between the encryption protocols and their priority order.
     */
    public void setEncryptionProtocol(Map<String, Integer> encryptionProtocol)
    {
        mEncryptionProtocol = encryptionProtocol;
    }

    /**
     * Returns the map between the encryption protocols and their status.
     *
     * @return The map between the encryption protocols and their status.
     */
    public Map<String, Boolean> getEncryptionProtocolStatus()
    {
        return mEncryptionProtocolStatus;
    }

    /**
     * Sets the map between the encryption protocols and their status.
     *
     * @param encryptionProtocolStatus The map between the encryption protocols and their status.
     */
    public void setEncryptionProtocolStatus(Map<String, Boolean> encryptionProtocolStatus)
    {
        mEncryptionProtocolStatus = encryptionProtocolStatus;
    }

    /**
     * Adds the ordered encryption protocol names to the property list given in parameter.
     *
     * @param properties The property list to fill in.
     */
    private void addEncryptionProtocolsToProperties(Map<String, String> properties)
    {
        for (Map.Entry<String, Integer> e : getEncryptionProtocol().entrySet()) {
            properties.put(ProtocolProviderFactory.ENCRYPTION_PROTOCOL + "." + e.getKey(), e.getValue().toString());
        }
    }

    /**
     * Adds the encryption protocol status to the property list given in parameter.
     *
     * @param properties The property list to fill in.
     */
    private void addEncryptionProtocolStatusToProperties(Map<String, String> properties)
    {
        for (Map.Entry<String, Boolean> e : getEncryptionProtocolStatus().entrySet()) {
            properties.put(ProtocolProviderFactory.ENCRYPTION_PROTOCOL_STATUS + "." + e.getKey(), e.getValue().toString());
        }
    }

    /**
     * Stores security properties held by this registration object into given properties map.
     *
     * @param propertiesMap the map that will be used for storing security properties held by this object.
     */
    public void storeProperties(Map<String, String> propertiesMap)
    {
        propertiesMap.put(ProtocolProviderFactory.DEFAULT_ENCRYPTION, Boolean.toString(isCallEncryption()));

        // Sets the ordered list of encryption protocols.
        addEncryptionProtocolsToProperties(propertiesMap);
        // Sets the list of encryption protocol status.
        addEncryptionProtocolStatusToProperties(propertiesMap);

        propertiesMap.put(ProtocolProviderFactory.DEFAULT_SIPZRTP_ATTRIBUTE, Boolean.toString(isSipZrtpAttribute()));
        propertiesMap.put(ProtocolProviderFactory.ZID_SALT, getZIDSalt());
        propertiesMap.put(ProtocolProviderFactory.DTLS_CERT_SIGNATURE_ALGORITHM, getDtlsCertSa());
        propertiesMap.put(ProtocolProviderFactory.SAVP_OPTION, Integer.toString(getSavpOption()));
        propertiesMap.put(ProtocolProviderFactory.SDES_CIPHER_SUITES, getSDesCipherSuites());
    }

    /**
     * Loads security properties for the user account with the given identifier.
     *
     * @param accountID the account identifier.
     */
    public void loadAccount(AccountID accountID)
    {
        // Clear all the default values
        mEncryptionProtocol = new HashMap<>();
        mEncryptionProtocolStatus = new HashMap<>();

        setCallEncryption(accountID.getAccountPropertyBoolean(ProtocolProviderFactory.DEFAULT_ENCRYPTION, true));
        Map<String, Integer> srcEncryptionProtocol = accountID.getIntegerPropertiesByPrefix(
                ProtocolProviderFactory.ENCRYPTION_PROTOCOL, true);
        Map<String, Boolean> srcEncryptionProtocolStatus = accountID.getBooleanPropertiesByPrefix(
                ProtocolProviderFactory.ENCRYPTION_PROTOCOL_STATUS, true, false);
        // Load stored values.
        int prefixeLength = ProtocolProviderFactory.ENCRYPTION_PROTOCOL.length() + 1;

        for (Map.Entry<String, Integer> e : srcEncryptionProtocol.entrySet()) {
            String name = e.getKey().substring(prefixeLength);
            if (isExistingEncryptionProtocol(name)) {
                // Copy the priority
                mEncryptionProtocol.put(name, e.getValue());

                // Extract the status
                boolean isEnable = false;
                String mEncryptProtoKey = ProtocolProviderFactory.ENCRYPTION_PROTOCOL_STATUS + "." + name;
                if (srcEncryptionProtocolStatus.containsKey(mEncryptProtoKey)) {
                    isEnable = Boolean.TRUE.equals(srcEncryptionProtocolStatus.get(mEncryptProtoKey));
                }
                mEncryptionProtocolStatus.put(name, isEnable);
            }
        }

        // Load ZRTP encryption parameters
        setSipZrtpAttribute(accountID.getAccountPropertyBoolean(ProtocolProviderFactory.DEFAULT_SIPZRTP_ATTRIBUTE, true));
        mZIDSalt = ZrtpControlImpl.getAccountZIDSalt(accountID);

        // Load DTLS_SRTP TlsCertificateSA from DB or use DEFAULT_SIGNATURE_ALGORITHM if none is defined
        mTlsCertificateSA = accountID.getAccountPropertyString(
                ProtocolProviderFactory.DTLS_CERT_SIGNATURE_ALGORITHM, DEFAULT_SIGNATURE_AND_HASH_ALGORITHM);

        // Load SDES encryption parameters
        setSavpOption(accountID.getAccountPropertyInt(ProtocolProviderFactory.SAVP_OPTION, ProtocolProviderFactory.SAVP_OFF));
        setSDesCipherSuites(accountID.getAccountPropertyString(ProtocolProviderFactory.SDES_CIPHER_SUITES));
    }

    /**
     * Loads the list of enabled and disabled encryption protocols with their priority into array of
     * <code>String</code> and array of <code>Boolean</code>. The protocols are positioned in the array by
     * the priority and the <code>Boolean</code> array holds the enabled flag on the corresponding index.
     *
     * @param encryptionProtocol The map of encryption protocols with their priority available for this account.
     * @param encryptionProtocolStatus The map of encryption protocol statuses.
     * @return <code>Object[]</code> array holding:<br/>
     * - at [0] <code>String[]</code> the list of extracted protocol names<br/>
     * - at [1] <code>boolean[]</code> the list of of protocol status flags
     */
    public static Object[] loadEncryptionProtocol(Map<String, Integer> encryptionProtocol,
            Map<String, Boolean> encryptionProtocolStatus)
    {
        int nbEncryptionProtocol = ENCRYPTION_PROTOCOL.size();
        String[] encryption = new String[nbEncryptionProtocol];
        boolean[] selectedEncryption = new boolean[nbEncryptionProtocol];

        // Load stored values.
        for (Map.Entry<String, Integer> entry : encryptionProtocol.entrySet()) {
            int index = entry.getValue();

            // If the property is set.
            if (index != -1) {
                String name = entry.getKey();
                if (isExistingEncryptionProtocol(name)) {
                    encryption[index] = name;
                    selectedEncryption[index] = Boolean.TRUE.equals(encryptionProtocolStatus.get(name));
                }
            }
        }

        // Load default values.
        int j = 0;
        for (String encProtocol : ENCRYPTION_PROTOCOL) {
            // Specify a default value only if there is no specific value set.
            if (!encryptionProtocol.containsKey(encProtocol)) {
                boolean set = false;
                // Search for the first empty element.
                while (j < encryption.length && !set) {
                    if (encryption[j] == null) {
                        encryption[j] = encProtocol;
                        // By default only ZRTP is set to true.
                        selectedEncryption[j] = encProtocol.equals("ZRTP");
                        set = true;
                    }
                    ++j;
                }
            }
        }
        return new Object[]{encryption, selectedEncryption};
    }

    /**
     * Checks if a specific <code>protocol</code> is on the list of supported (encryption) protocols.
     *
     * @param protocol the protocol name
     * @return <code>true</code> if <code>protocol</code> is supported; <code>false</code>, otherwise
     */
    private static boolean isExistingEncryptionProtocol(String protocol)
    {
        return ENCRYPTION_PROTOCOL.contains(protocol);
    }

    /**
     * Returns ZID salt
     *
     * @return ZID salt
     */
    public String getZIDSalt()
    {
        return mZIDSalt;
    }

    /**
     * Set ZID salt
     *
     * @param ZIDSalt new ZID salt value
     */
    public void setZIDSalt(final String ZIDSalt)
    {
        mZIDSalt = ZIDSalt;
    }

    /**
     * Generate new random value for the ZID salt and update the ZIDSalt.
     */
    public String randomZIDSalt()
    {
        mZIDSalt = new BigInteger(256, mSecureRandom).toString(32);
        return mZIDSalt;
    }
}
