/*
 * aTalk / Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.protocol;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import net.java.sip.communicator.impl.protocol.jabber.ProtocolProviderServiceJabberImpl;
import net.java.sip.communicator.service.credentialsstorage.CredentialsStorageService;
import net.java.sip.communicator.util.ServiceUtils;
import net.java.sip.communicator.util.account.AccountUtils;

import org.apache.commons.lang3.StringUtils;
import org.atalk.android.R;
import org.atalk.android.aTalkApp;
import org.atalk.android.gui.account.settings.BoshProxyDialog;
import org.atalk.service.configuration.ConfigurationService;
import org.atalk.service.neomedia.SrtpControlType;
import org.jivesoftware.smack.tcp.XMPPTCPConnection;
import org.json.JSONException;
import org.json.JSONObject;
import org.jxmpp.jid.BareJid;
import org.jxmpp.jid.DomainBareJid;
import org.osgi.framework.BundleContext;

import java.util.*;

import timber.log.Timber;

/**
 * The AccountID is an account identifier that, uniquely represents a specific user account over a
 * specific protocol. The class needs to be extended by every protocol implementation because of its
 * protected constructor. The reason why this constructor is protected is mostly avoiding confusion
 * and letting people (using the protocol provider service) believe that they are the ones who are
 * supposed to instantiate the AccountID class.
 *
 * Every instance of the <tt>ProtocolProviderService</tt>, created through the
 * ProtocolProviderFactory is assigned an AccountID instance, that uniquely represents it and whose
 * string representation (obtained through the getAccountUniqueID() method) can be used for
 * identification of persistently stored account details.
 *
 * Account id's are guaranteed to be different for different accounts and in the same time are bound
 * to be equal for multiple installations of the same account.
 *
 * @author Emil Ivov
 * @author Lubomir Marinov
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
public class AccountID
{
    /**
     * Table accountID columns
     */
    public static final String TABLE_NAME = "accountID";
    public static final String ACCOUNT_UUID = "accountUuid"; // ACCOUNT_UUID_PREFIX + System.currentTimeMillis()
    public static final String PROTOCOL = "protocolName";    // Default to Jabber
    public static final String USER_ID = "userID";           // abc123@atalk.org i.e. BareJid
    public static final String ACCOUNT_UID = "accountUid";   // jabber:abc123@atalk.org (uuid)
    public static final String KEYS = "keys";

    // Not use
    public static final String SERVICE_NAME = "serviceName"; // domainPart of jid
    public static final String STATUS = "status";
    public static final String STATUS_MESSAGE = "statusMessage";

    /**
     * Table accountProperties columns
     */
    public static final String TBL_PROPERTIES = "accountProperties";
    // public static final String ACCOUNT_UID = "accountUuid";
    public static final String COLUMN_NAME = "Name";
    public static final String COLUMN_VALUE = "Value";

    public static final String PROTOCOL_DEFAULT = "'Jabber'";

    /**
     * The prefix of the account unique identifier.
     */
    public static final String ACCOUNT_UUID_PREFIX = "acc";

    private static final String KEY_PGP_SIGNATURE = "pgp_signature";
    private static final String KEY_PGP_ID = "pgp_id";

    public static final String DEFAULT_PORT = "5222";

    protected String avatarHash;
    protected String rosterVersion;
    protected String otrFingerprint;

    protected String statusMessage = "status_Message";

    protected JSONObject mKeys = new JSONObject();


    /**
     * The default properties common key prefix used in lib/atalk-defaults.properties which are
     * independent of protocol.
     */
    protected static final String DEFAULT_PREFIX = "protocol.";

    /**
     * The real protocol name.
     */
    private final String protocolName;

    /**
     * The protocol display name. In the case of overridden protocol name this would be the new name.
     */
    private final String protocolDisplayName;

    /**
     * Contains all implementation specific properties that define the account. The exact names
     * of the keys are protocol (and sometimes implementation) specific. Currently, only String
     * property keys and values will get properly stored. If you need something else, please
     * consider converting it through custom accessors (get/set) in your implementation.
     */
    protected Map<String, String> mAccountProperties = null;

    /**
     * A String uniquely identifying the user for this particular account with prefix "acc", and
     * is used as link in the account properties retrieval
     */
    private final String uuid;

    /**
     * A String uniquely identifying this account, that can also be used for storing and
     * unambiguously retrieving details concerning it. e.g. jabber:abc123@example.org
     */
    protected String accountUID;

    /**
     * A String uniquely identifying the user for this particular account. e.g. abc123@example.org
     */
    protected String userID;

    /**
     * An XMPP Jabber ID associated with this particular account. e.g. abc123@example.org
     */
    protected BareJid userBareJid;

    /**
     * The name of the service that defines the context for this account. e.g. example.org
     */
    private final String serviceName;

    /**
     * Creates an account id for the specified provider userId and accountProperties. If account
     * uid exists in account properties, we are loading the account and so load its value from
     * there, prevent changing account uid when server changed (serviceName has changed).
     *
     * @param userID a String that uniquely identifies the user.
     * @param accountProperties a Map containing any other protocol and implementation specific account
     * initialization properties
     * @param protocolName the protocol name implemented by the provider that this id is meant for e.g. Jabber
     * @param serviceName the name of the service is what follows after the '@' sign in XMPP addresses (JIDs).
     * (e.g. iptel.org, jabber.org, icq.com) the service of the account registered with.
     *
     * Note: parameters userID is null and new empty accountProperties when called from
     * @see net.java.sip.communicator.service.protocol.jabber.JabberAccountRegistration or
     * @see net.java.sip.communicator.service.protocol.sip.SIPAccountRegistration constructor
     */
    protected AccountID(String userID, Map<String, String> accountProperties, String protocolName, String serviceName)
    {
        /*
         * Allow account registration wizards to override the default protocol name through
         * accountProperties for the purposes of presenting a well-known protocol name associated
         * with the account that is different from the name of the effective protocol.
         */
        this.protocolName = protocolName;
        this.protocolDisplayName = getOverriddenProtocolName(accountProperties, protocolName);
        this.userID = userID;
        mAccountProperties = new HashMap<>(accountProperties);
        this.serviceName = serviceName;

        this.uuid = accountProperties.get(ProtocolProviderFactory.ACCOUNT_UUID);
        this.accountUID = accountProperties.get(ProtocolProviderFactory.ACCOUNT_UID);

        JSONObject tmp = new JSONObject();
        String strKeys = accountProperties.get(ProtocolProviderFactory.KEYS);
        if (StringUtils.isNotEmpty(strKeys)) {
            try {
                tmp = new JSONObject(strKeys);
            } catch (JSONException e) {
                Timber.w("Cannot convert JSONObject from: %s", strKeys);
            }
        }
        mKeys = tmp;
        Timber.d("### Set Account UUID to: %s: %s for %s", uuid, accountUID, userID);
    }

    /**
     * Allows a specific set of account properties to override a given default protocol name (e.g.
     * account registration wizards which want to present a well-known protocol name associated
     * with the account that is different from the name of the effective protocol).
     *
     * Note: The logic of the SIP protocol implementation at the time of this writing modifies
     * <tt>accountProperties</tt> to contain the default protocol name if an override hasn't been
     * defined. Since the desire is to enable all account registration wizards to override the
     * protocol name, the current implementation places the specified <tt>defaultProtocolName</tt>
     * in a similar fashion.
     *
     * @param accountProperties a Map containing any other protocol and implementation specific
     * account initialization properties
     * @param defaultProtocolName the protocol name to be used in case <tt>accountProperties</tt>
     * doesn't provide an overriding value
     * @return the protocol name
     */
    private static String getOverriddenProtocolName(Map<String, String> accountProperties, String defaultProtocolName)
    {
        String key = ProtocolProviderFactory.PROTOCOL;
        String protocolName = accountProperties.get(key);
        if (StringUtils.isEmpty(protocolName) && StringUtils.isNotEmpty(defaultProtocolName)) {
            protocolName = defaultProtocolName;
            accountProperties.put(key, protocolName);
        }
        return protocolName;
    }

    /**
     * @return e.g. acc1567990097080
     */
    public String getAccountUuid()
    {
        return this.uuid;
    }

    /**
     * Returns the user id associated with this account BareJid.toString e.g. abc123@example.org.
     *
     * @return A String identifying the user inside this particular service.
     */
    public String getUserID()
    {
        return userID;
    }

    // Override for Jabber implementation for the BareJid e.g. abc123@example.org.
    public BareJid getBareJid()
    {
        return userBareJid;
    }

    /**
     * Get the Entity XMPP domain. The XMPP domain is what follows after the '@' sign in XMPP addresses (JIDs).
     *
     * @return XMPP service domain.
     */
    public DomainBareJid getXmppDomain()
    {
        return userBareJid.asDomainBareJid();
    }

    /**
     * Returns a name that can be displayed to the user when referring to this account.
     * e.g. abc123@example.org or abc123@example.org (jabber). Create one if none is found
     *
     * @return A String identifying the user inside this particular service.
     */
    public String getDisplayName()
    {
        // If the ACCOUNT_DISPLAY_NAME property has been set for this account, we'll be using it
        // as a display name.
        String key = ProtocolProviderFactory.ACCOUNT_DISPLAY_NAME;
        String accountDisplayName = mAccountProperties.get(key);

        if (StringUtils.isNotEmpty(accountDisplayName)) {
            return accountDisplayName;
        }

        // Otherwise construct a display name.
        String returnValue = userID;
        String protocolName = getProtocolDisplayName();

        if (StringUtils.isNotEmpty(protocolName)) {
            returnValue += " (" + protocolName + ")";
        }
        return returnValue;
    }

    /**
     * Sets {@link ProtocolProviderFactory#DISPLAY_NAME} property value.
     *
     * @param displayName the display name value to set.
     */
    public void setDisplayName(String displayName)
    {
        setOrRemoveIfEmpty(ProtocolProviderFactory.DISPLAY_NAME, displayName);
    }

    /**
     * Returns the display name of the protocol.
     *
     * @return the display name of the protocol
     */
    public String getProtocolDisplayName()
    {
        return protocolDisplayName;
    }

    /**
     * Returns the name of the protocol.
     *
     * @return the name of the protocol.
     */
    public String getProtocolName()
    {
        return protocolName;
    }

    /**
     * Gets the ProtocolProviderService for mAccountID
     *
     * @return the ProtocolProviderService if currently registered for the AccountID or <tt>null</tt> otherwise
     */
    public ProtocolProviderService getProtocolProvider()
    {
        return AccountUtils.getRegisteredProviderForAccount(this);
    }

    /**
     * Returns a String uniquely identifying this account, guaranteed to remain the same across
     * multiple installations of the same account and to always be unique for differing accounts.
     *
     * @return String
     */
    public String getAccountUniqueID()
    {
        return accountUID;
    }

    /**
     * Returns a Map containing protocol and implementation account initialization properties.
     *
     * @return a Map containing protocol and implementation account initialization properties.
     */
    public Map<String, String> getAccountProperties()
    {
        return new HashMap<>(mAccountProperties);
    }

    /**
     * Returns the specific account property.
     *
     * @param key property key
     * @param defaultValue default value if the property does not exist
     * @return property value corresponding to property key
     */
    public boolean getAccountPropertyBoolean(Object key, boolean defaultValue)
    {
        String value = getAccountPropertyString(key);
        return (value == null) ? defaultValue : Boolean.parseBoolean(value);
    }

    /**
     * Gets the value of a specific property as a signed decimal integer. If the specified property
     * key is associated with a value in this <tt>AccountID</tt>, the string representation of the
     * value is parsed into a signed decimal integer according to the rules of
     * {@link Integer#parseInt(String)}. If parsing the value as a signed decimal integer fails or
     * there is no value associated with the specified property key, <tt>defaultValue</tt> is returned.
     *
     * @param key the key of the property to get the value of as a signed decimal integer
     * @param defaultValue the value to be returned if parsing the value of the specified property key as a
     * signed decimal integer fails or there is no value associated with the specified
     * property key in this <tt>AccountID</tt>
     * @return the value of the property with the specified key in this <tt>AccountID</tt> as a
     * signed decimal integer; <tt>defaultValue</tt> if parsing the value of the specified
     * property key fails or no value is associated in this <tt>AccountID</tt> with the
     * specified property name
     */
    public int getAccountPropertyInt(Object key, int defaultValue)
    {
        int intValue = defaultValue;
        String stringValue = getAccountPropertyString(key);

        if (StringUtils.isNotEmpty(stringValue)) {
            try {
                intValue = Integer.parseInt(stringValue);
            } catch (NumberFormatException ex) {
                Timber.e("Failed to parse account property %s value %s as an integer: %s",
                        key, stringValue, ex.getMessage());
            }
        }
        return intValue;
    }

    /**
     * Returns the account property string corresponding to the given key.
     *
     * @param key the key, corresponding to the property string we're looking for
     * @return the account property string corresponding to the given key
     */
    public String getAccountPropertyString(Object key)
    {
        return getAccountPropertyString(key, null);
    }

    /**
     * Returns the account property string corresponding to the given key.
     *
     * @param key the key, corresponding to the property string we're looking for
     * @param defValue the default value returned when given <tt>key</tt> is not present
     * @return the account property string corresponding to the given key
     */
    public String getAccountPropertyString(Object key, String defValue)
    {
        String property = key.toString();
        String value = mAccountProperties.get(property);
        if (value == null) {
            // try load from accountProperties and keep a copy in mAccountProperties if found
            // for later retrieval
            ConfigurationService configService = ProtocolProviderActivator.getConfigurationService();
            if (configService != null) {
                value = configService.getString(uuid + "." + property);
                if (StringUtils.isNotEmpty(value)) {
                    putAccountProperty(key.toString(), value);
                }
                else {
                    value = getDefaultString(property);
                }
            }
            else {
                value = getDefaultString(property);
            }
        }
        return (value == null) ? defValue : value;
    }

    /**
     * Store the value to the account property of the given key to persistence store.
     *
     * @param key the name of the property to change.
     * @param property the new value of the specified property. Null will remove the propertyName item
     */
    public void storeAccountProperty(String key, Object property)
    {
        String accPropertyName = uuid + "." + key;
        ConfigurationService configService = ProtocolProviderActivator.getConfigurationService();
        if (configService != null) {
            if (property != null)
                putAccountProperty(key, property);
            else
                removeAccountProperty(key);
            configService.setProperty(accPropertyName, property);
        }
    }

    /**
     * Adds a property to the map of properties for this account identifier.
     *
     * @param key the key of the property
     * @param value the property value.
     */
    public void putAccountProperty(String key, String value)
    {
        mAccountProperties.put(key, value);
    }

    /**
     * Adds property to the map of properties for this account identifier.
     *
     * @param key the key of the property
     * @param value the property value
     */
    public void putAccountProperty(String key, Object value)
    {
        mAccountProperties.put(key, String.valueOf(value));
    }

    /**
     * Removes specified account property.
     *
     * @param key the key to remove.
     */
    public void removeAccountProperty(String key)
    {
        mAccountProperties.remove(key);
    }

    /**
     * Returns a hash code value for the object. This method is supported for the benefit of
     * HashTables such as those provided by <tt>java.util.Hashtable</tt>.
     *
     * @return a hash code value for this object.
     * @see java.lang.Object#equals(java.lang.Object)
     * @see java.util.Hashtable
     */
    @Override
    public int hashCode()
    {
        return (accountUID == null) ? 0 : accountUID.hashCode();
    }

    /**
     * Indicates whether some other object is "equal to" this account id.
     *
     * @param obj the reference object with which to compare.
     * @return <tt>true</tt> if this object is the same as the obj argument; <tt>false</tt>
     * otherwise.
     * @see #hashCode()
     * @see java.util.Hashtable
     */
    @Override
    public boolean equals(Object obj)
    {
        return (this == obj) || (obj != null) && getClass().isInstance(obj)
                && accountUID.equals(((AccountID) obj).accountUID);
    }

    /**
     * Returns a string representation of this account id (same as calling getAccountUniqueID()).
     *
     * @return a string representation of this account id.
     */
    @Override
    public String toString()
    {
        return getAccountUniqueID();
    }

    /**
     * Returns the name of the service that defines the context for this account. Often this name
     * would be an FQDN or even an ipAddress but this would not always be the case (e.g. p2p
     * providers may return a name that does not directly correspond to an IP address or host name).
     *
     * @return the name of the service that defines the context for this account.
     */
    public String getService()
    {
        return this.serviceName;
    }

    /**
     * Returns a string that could be directly used (or easily converted to) an address that other
     * users of the protocol can use to communicate with us. By default this string is set to
     * userID@serviceName. Protocol implementors should override it if they'd need it to respect a
     * different syntax.
     *
     * @return a String in the form of userID@service that other protocol users should be able to
     * parse into a meaningful address and use it to communicate with us.
     */
    public String getAccountJid()
    {
        return (userID.indexOf('@') > 0) ? userID : (userID + "@" + getService());
    }

    /**
     * Indicates if this account is currently enabled.
     *
     * @return <tt>true</tt> if this account is enabled, <tt>false</tt> - otherwise.
     */
    public boolean isEnabled()
    {
        return !getAccountPropertyBoolean(ProtocolProviderFactory.IS_ACCOUNT_DISABLED, false);
    }

    /**
     * Get the {@link ProtocolProviderFactory#ACCOUNT_DISPLAY_NAME} property.
     *
     * @return the {@link ProtocolProviderFactory#ACCOUNT_DISPLAY_NAME} property value.
     */
    public String getAccountDisplayName()
    {
        return getAccountPropertyString(ProtocolProviderFactory.ACCOUNT_DISPLAY_NAME);
    }

    /**
     * Sets {@link ProtocolProviderFactory#ACCOUNT_DISPLAY_NAME} property value.
     *
     * @param displayName the account display name value to set.
     */
    public void setAccountDisplayName(String displayName)
    {
        setOrRemoveIfEmpty(ProtocolProviderFactory.ACCOUNT_DISPLAY_NAME, displayName);
    }

    /**
     * Returns the password of the account.
     *
     * @return the password of the account.
     */
    public String getPassword()
    {
        return getAccountPropertyString(ProtocolProviderFactory.PASSWORD);
    }

    /**
     * Sets the password of the account.
     *
     * @param password the password of the account.
     */
    public void setPassword(String password)
    {
        setOrRemoveIfEmpty(ProtocolProviderFactory.PASSWORD, password);
    }

    /**
     * Specifies whether or not the passWord is to be stored persistently (insecure!) or not.
     *
     * @param storePassword indicates whether password is to be stored persistently.
     */
    public void setPasswordPersistent(boolean storePassword)
    {
        putAccountProperty(ProtocolProviderFactory.PASSWORD_PERSISTENT, storePassword);
    }

    /**
     * Determines whether or not the passWord is to be stored persistently (insecure!) or not.
     *
     * @return true if the underlying protocol provider is to persistently (and possibly
     * insecurely) store the passWord and false otherwise.
     * Note: Default must set to be the same default as in, until user changes it
     * @link JabberPreferenceFragment.rememberPassword
     */
    public boolean isPasswordPersistent()
    {
        return getAccountPropertyBoolean(ProtocolProviderFactory.PASSWORD_PERSISTENT, true);
    }

    /**
     * Returns the password of the account.
     *
     * @return the password of the account.
     */
    public String getDnssMode()
    {
        return getAccountPropertyString(ProtocolProviderFactory.DNSSEC_MODE,
                aTalkApp.getAppResources().getStringArray(R.array.dnssec_Mode_value)[0]);
    }

    /**
     * Sets the dnssMode of the account.
     *
     * @param dnssMode the dnssMode of the account.
     */
    public void setDnssMode(String dnssMode)
    {
        setOrRemoveIfEmpty(ProtocolProviderFactory.DNSSEC_MODE, dnssMode);
    }

    /**
     * The authorization name
     *
     * @return String auth name
     */
    public String getAuthorizationName()
    {
        return getAccountPropertyString(ProtocolProviderFactory.AUTHORIZATION_NAME);
    }

    /**
     * Sets authorization name.
     *
     * @param authName String
     */
    public void setAuthorizationName(String authName)
    {
        setOrRemoveIfEmpty(ProtocolProviderFactory.AUTHORIZATION_NAME, authName);
    }

    /**
     * Determines whether sending of keep alive packets is enabled.
     *
     * @return <tt>true</tt> if keep alive packets are to be sent for this account and
     * <tt>false</tt> otherwise.
     */
    public boolean isKeepAliveEnable()
    {
        return getAccountPropertyBoolean(ProtocolProviderFactory.IS_KEEP_ALIVE_ENABLE, false);
    }

    /**
     * Specifies whether SIP Communicator should send keep alive packets to keep this account
     * registered.
     *
     * @param isKeepAliveEnable <tt>true</tt> if we are to send keep alive packets and <tt>false</tt> otherwise.
     */
    public void setKeepAliveOption(boolean isKeepAliveEnable)
    {
        putAccountProperty(ProtocolProviderFactory.IS_KEEP_ALIVE_ENABLE, isKeepAliveEnable);
    }

    /**
     * Determines whether ping interval auto optimization is enabled.
     *
     * @return <tt>true</tt> if ping interval optimization for this account is enabled and <tt>false</tt> otherwise.
     */
    public boolean isPingAutoTuneEnable()
    {
        return getAccountPropertyBoolean(ProtocolProviderFactory.IS_PING_AUTO_TUNE_ENABLE, true);
    }

    /**
     * Specifies whether protocol provide should perform auto ping optimization for this account registered.
     *
     * @param isPingAutoEnable <tt>true</tt> if allow to perform ping auto optimization, <tt>false</tt> otherwise.
     */
    public void setPingAutoTuneOption(boolean isPingAutoEnable)
    {
        putAccountProperty(ProtocolProviderFactory.IS_PING_AUTO_TUNE_ENABLE, isPingAutoEnable);
    }

    /**
     * Get the network Ping Interval default to aTalk default
     *
     * @return int
     */
    public String getPingInterval()
    {
        return getAccountPropertyString(ProtocolProviderFactory.PING_INTERVAL,
                Integer.toString(ProtocolProviderServiceJabberImpl.defaultPingInterval));
    }

    /**
     * Sets the network ping interval.
     *
     * @param interval Keep alive ping interval
     */
    public void setPingInterval(String interval)
    {
        setOrRemoveIfEmpty(ProtocolProviderFactory.PING_INTERVAL, interval);
    }

    /**
     * Returns <tt>true</tt> if server was overridden.
     *
     * @return <tt>true</tt> if server was overridden.
     */
    public boolean isServerOverridden()
    {
        return getAccountPropertyBoolean(ProtocolProviderFactory.IS_SERVER_OVERRIDDEN, false);
    }

    /**
     * Sets <tt>isServerOverridden</tt> property.
     *
     * @param isServerOverridden indicates if the server is overridden
     */
    public void setServerOverridden(boolean isServerOverridden)
    {
        putAccountProperty(ProtocolProviderFactory.IS_SERVER_OVERRIDDEN, isServerOverridden);
    }

    /**
     * The address of the server we will use for this account.  Default to serviceName if null.
     *
     * @return String
     */
    public String getServerAddress()
    {
        return getAccountPropertyString(ProtocolProviderFactory.SERVER_ADDRESS, serviceName);
    }

    /**
     * Sets the server
     *
     * @param serverAddress String
     */
    public void setServerAddress(String serverAddress)
    {
        setOrRemoveIfEmpty(ProtocolProviderFactory.SERVER_ADDRESS, serverAddress);
    }

    /**
     * The port on the specified server. Return DEFAULT_PORT if null.
     *
     * @return int
     */
    public String getServerPort()
    {
        return getAccountPropertyString(ProtocolProviderFactory.SERVER_PORT, DEFAULT_PORT);
    }

    /**
     * Sets the server port.
     *
     * @param port proxy server port
     */
    public void setServerPort(String port)
    {
        setOrRemoveIfEmpty(ProtocolProviderFactory.SERVER_PORT, port);
    }

    /**
     * Indicates if proxy should be used for this account if Type != NONE.
     *
     * @return <tt>true</tt> if (Type != NONE) for this account, otherwise returns <tt>false</tt>
     */
    public boolean isUseProxy()
    {
        // The isUseProxy state is to take care of old DB?
        boolean isUseProxy = "true".equals(getAccountPropertyString(ProtocolProviderFactory.IS_USE_PROXY));
        return isUseProxy && !BoshProxyDialog.NONE.equals(getAccountPropertyString(ProtocolProviderFactory.PROXY_TYPE));
    }

    /**
     * Sets the <tt>useProxy</tt> property.
     *
     * @param isUseProxy <tt>true</tt> to indicate that Proxy should be used for this account, <tt>false</tt> - otherwise.
     */
    public void setUseProxy(boolean isUseProxy)
    {
        putAccountProperty(ProtocolProviderFactory.IS_USE_PROXY, isUseProxy);
    }

    /**
     * The Type of proxy we will use for this account
     *
     * @return String
     */
    public String getProxyType()
    {
        return getAccountPropertyString(ProtocolProviderFactory.PROXY_TYPE);
    }

    /**
     * Sets the Proxy Type
     *
     * @param proxyType String
     */
    public void setProxyType(String proxyType)
    {
        setOrRemoveIfEmpty(ProtocolProviderFactory.PROXY_TYPE, proxyType);
    }

    /**
     * The address of the proxy we will use for this account
     *
     * @return String
     */
    public String getProxyAddress()
    {
        return getAccountPropertyString(ProtocolProviderFactory.PROXY_ADDRESS);
    }

    /**
     * Sets the proxy address
     *
     * @param proxyAddress String
     */
    public void setProxyAddress(String proxyAddress)
    {
        setOrRemoveIfEmpty(ProtocolProviderFactory.PROXY_ADDRESS, proxyAddress);
    }

    /**
     * The port on the specified proxy
     *
     * @return int
     */
    public String getProxyPort()
    {
        return getAccountPropertyString(ProtocolProviderFactory.PROXY_PORT);
    }

    /**
     * Sets the proxy port.
     *
     * @param proxyPort int
     */
    public void setProxyPort(String proxyPort)
    {
        setOrRemoveIfEmpty(ProtocolProviderFactory.PROXY_PORT, proxyPort);
    }

    /**
     * The port on the specified server
     *
     * @return int
     */
    public String getProxyUserName()
    {
        return getAccountPropertyString(ProtocolProviderFactory.PROXY_USERNAME);
    }

    /**
     * Sets the server port.
     *
     * @param port int
     */
    public void setProxyUserName(String port)
    {
        setOrRemoveIfEmpty(ProtocolProviderFactory.PROXY_USERNAME, port);
    }

    /**
     * The port on the specified server
     *
     * @return int
     */
    public String getProxyPassword()
    {
        return getAccountPropertyString(ProtocolProviderFactory.PROXY_PASSWORD);
    }

    /**
     * Sets the server port.
     *
     * @param port int
     */
    public void setProxyPassword(String port)
    {
        setOrRemoveIfEmpty(ProtocolProviderFactory.PROXY_PASSWORD, port);
    }

    /**
     * Returns <tt>true</tt> if the account requires IB Registration with the server
     *
     * @return <tt>true</tt> if account requires IB Registration with the server
     */
    public boolean isIbRegistration()
    {
        return getAccountPropertyBoolean(ProtocolProviderFactory.IBR_REGISTRATION, false);
    }

    /**
     * Sets <tt>IBR_REGISTRATION</tt> property.
     *
     * @param ibRegistration indicates if the account wants to perform an IBR registration with the server
     */
    public void setIbRegistration(boolean ibRegistration)
    {
        putAccountProperty(ProtocolProviderFactory.IBR_REGISTRATION, ibRegistration);
    }

    /**
     * Returns the protocol icon path stored under
     * {@link ProtocolProviderFactory#PROTOCOL_ICON_PATH} key.
     *
     * @return the protocol icon path.
     */
    public String getProtocolIconPath()
    {
        return getAccountPropertyString(ProtocolProviderFactory.PROTOCOL_ICON_PATH);
    }

    /**
     * Sets the protocol icon path that will be held under
     * {@link ProtocolProviderFactory#PROTOCOL_ICON_PATH} key.
     *
     * @param iconPath a path to the protocol icon to set.
     */
    public void setProtocolIconPath(String iconPath)
    {
        putAccountProperty(ProtocolProviderFactory.PROTOCOL_ICON_PATH, iconPath);
    }

    /**
     * Returns the protocol icon path stored under
     * {@link ProtocolProviderFactory#ACCOUNT_ICON_PATH} key.
     *
     * @return the protocol icon path.
     */
    public String getAccountIconPath()
    {
        return getAccountPropertyString(ProtocolProviderFactory.ACCOUNT_ICON_PATH);
    }

    /**
     * Sets the account icon path that will be held under
     * {@link ProtocolProviderFactory#ACCOUNT_ICON_PATH} key.
     *
     * @param iconPath a path to the account icon to set.
     */
    public void setAccountIconPath(String iconPath)
    {
        putAccountProperty(ProtocolProviderFactory.ACCOUNT_ICON_PATH, iconPath);
    }

    /**
     * Returns the DTMF method.
     *
     * @return the DTMF method.
     */
    public String getDTMFMethod()
    {
        return getAccountPropertyString(ProtocolProviderFactory.DTMF_METHOD);
    }

    /**
     * Sets the DTMF method.
     *
     * @param dtmfMethod the DTMF method to set
     */
    public void setDTMFMethod(String dtmfMethod)
    {
        putAccountProperty(ProtocolProviderFactory.DTMF_METHOD, dtmfMethod);
    }

    /**
     * Returns the minimal DTMF tone duration.
     *
     * @return The minimal DTMF tone duration.
     */
    public String getDtmfMinimalToneDuration()
    {
        return getAccountPropertyString(ProtocolProviderFactory.DTMF_MINIMAL_TONE_DURATION);
    }

    /**
     * Sets the minimal DTMF tone duration.
     *
     * @param dtmfMinimalToneDuration The minimal DTMF tone duration to set.
     */
    public void setDtmfMinimalToneDuration(String dtmfMinimalToneDuration)
    {
        putAccountProperty(ProtocolProviderFactory.DTMF_MINIMAL_TONE_DURATION, dtmfMinimalToneDuration);
    }

    /**
     * Gets the ID of the client certificate configuration.
     *
     * @return the ID of the client certificate configuration.
     */
    public String getTlsClientCertificate()
    {
        return getAccountPropertyString(ProtocolProviderFactory.CLIENT_TLS_CERTIFICATE);
    }

    /**
     * Sets the ID of the client certificate configuration.
     *
     * @param id the client certificate configuration template ID.
     */
    public void setTlsClientCertificate(String id)
    {
        setOrRemoveIfEmpty(ProtocolProviderFactory.CLIENT_TLS_CERTIFICATE, id);
    }

    /**
     * Checks if the account is hidden.
     *
     * @return <tt>true</tt> if this account is hidden or <tt>false</tt> otherwise.
     */
    public boolean isHidden()
    {
        return getAccountPropertyString(ProtocolProviderFactory.IS_PROTOCOL_HIDDEN) != null;
    }

    /**
     * Checks if the account config is hidden.
     *
     * @return <tt>true</tt> if the account config is hidden or <tt>false</tt> otherwise.
     */
    public boolean isConfigHidden()
    {
        return getAccountPropertyString(ProtocolProviderFactory.IS_ACCOUNT_CONFIG_HIDDEN) != null;
    }

    /**
     * Checks if the account status menu is hidden.
     *
     * @return <tt>true</tt> if the account status menu is hidden or <tt>false</tt> otherwise.
     */
    public boolean isStatusMenuHidden()
    {
        return getAccountPropertyString(ProtocolProviderFactory.IS_ACCOUNT_STATUS_MENU_HIDDEN) != null;
    }

    /**
     * Checks if the account is marked as readonly.
     *
     * @return <tt>true</tt> if the account is marked as readonly or <tt>false</tt> otherwise.
     */
    public boolean isReadOnly()
    {
        return getAccountPropertyString(ProtocolProviderFactory.IS_ACCOUNT_READ_ONLY) != null;
    }

    /**
     * Returns the first <tt>ProtocolProviderService</tt> implementation corresponding to the
     * preferred protocol
     *
     * @return the <tt>ProtocolProviderService</tt> corresponding to the preferred protocol
     */
    public boolean isPreferredProvider()
    {
        String preferredProtocolProp = getAccountPropertyString(ProtocolProviderFactory.IS_PREFERRED_PROTOCOL);

        return StringUtils.isNotEmpty(preferredProtocolProp) && Boolean.parseBoolean(preferredProtocolProp);
    }

    /**
     * Set the account properties.
     *
     * @param accountProperties the properties of the account
     */
    public void setAccountProperties(Map<String, String> accountProperties)
    {
        mAccountProperties = accountProperties;
    }

    /**
     * Returns if the encryption protocol given in parameter is enabled.
     *
     * @param srtpType The name of the encryption protocol ("ZRTP", "SDES" or "MIKEY").
     */
    public boolean isEncryptionProtocolEnabled(SrtpControlType srtpType)
    {
        // The default value is false, except for ZRTP.
        boolean defaultValue = (srtpType == SrtpControlType.ZRTP);
        return getAccountPropertyBoolean(ProtocolProviderFactory.ENCRYPTION_PROTOCOL_STATUS
                + "." + srtpType.toString(), defaultValue);
    }

    /**
     * Returns the list of STUN servers that this account is currently configured to use.
     *
     * @return the list of STUN servers that this account is currently configured to use.
     */
    public List<StunServerDescriptor> getStunServers(BundleContext bundleContext)
    {
        Map<String, String> accountProperties = getAccountProperties();
        List<StunServerDescriptor> stunServerList = new ArrayList<>();

        for (int i = 0; i < StunServerDescriptor.MAX_STUN_SERVER_COUNT; i++) {
            StunServerDescriptor stunServer = StunServerDescriptor.loadDescriptor(
                    accountProperties, ProtocolProviderFactory.STUN_PREFIX + i);

            // If we don't find a stun server with the given index, it means there are no more
            // servers left in the table so we've nothing more to do here.
            if (stunServer == null)
                break;

            String password = loadStunPassword(bundleContext, this, ProtocolProviderFactory.STUN_PREFIX + i);
            if (password != null)
                stunServer.setPassword(password);
            stunServerList.add(stunServer);
        }
        return stunServerList;
    }

    /**
     * Returns the password for the STUN server with the specified prefix.
     *
     * @param bundleContext the OSGi bundle context that we are currently running in.
     * @param accountID account ID
     * @param namePrefix name prefix
     * @return password or null if empty
     */
    protected static String loadStunPassword(BundleContext bundleContext, AccountID accountID,
            String namePrefix)
    {
        ProtocolProviderFactory providerFactory
                = ProtocolProviderFactory.getProtocolProviderFactory(bundleContext, accountID.getSystemProtocolName());

        String password;
        String className = providerFactory.getClass().getName();
        String packageSourceName = className.substring(0, className.lastIndexOf('.'));

        String accountPrefix = ProtocolProviderFactory.findAccountPrefix(bundleContext, accountID, packageSourceName);
        CredentialsStorageService credentialsService
                = ServiceUtils.getService(bundleContext, CredentialsStorageService.class);

        try {
            password = credentialsService.loadPassword(accountPrefix + "." + namePrefix);
        } catch (Exception e) {
            return null;
        }
        return password;
    }

    /**
     * Determines whether this account's provider is supposed to auto discover STUN and TURN servers.
     *
     * @return <tt>true</tt> if this provider would need to discover STUN/TURN servers
     * otherwise false if serverOverride is enabled; serviceDomain is likely not reachable.
     */
    public boolean isStunServerDiscoveryEnabled()
    {
        return getAccountPropertyBoolean(ProtocolProviderFactory.AUTO_DISCOVER_STUN, !isServerOverridden());
    }

    /**
     * Determines whether this account's provider uses UPnP (if available).
     *
     * @return <tt>true</tt> if this provider would use UPnP (if available), <tt>false</tt>
     * otherwise
     */
    public boolean isUPNPEnabled()
    {
        return getAccountPropertyBoolean(ProtocolProviderFactory.IS_USE_UPNP, true);
    }

    /**
     * Determines whether this account's provider uses the default STUN server provided by Jitsi
     * (stun.jitsi.net) if there is no other STUN/TURN server discovered/configured.
     *
     * @return <tt>true</tt> if this provider would use the default STUN server, <tt>false</tt>
     * otherwise
     */
    public boolean isUseDefaultStunServer()
    {
        return getAccountPropertyBoolean(ProtocolProviderFactory.USE_DEFAULT_STUN_SERVER, true);
    }

    /**
     * Returns the actual name of the protocol used rather than a branded variant. The method is
     * primarily meant for open protocols such as SIP or XMPP so that it would always return SIP
     * or XMPP even in branded protocols who otherwise return things like GTalk and ippi for
     * PROTOCOL_NAME.
     *
     * @return the real non-branded name of the protocol.
     */
    public String getSystemProtocolName()
    {
        return getProtocolName();
    }

    /**
     * Sorts the enabled encryption protocol list given in parameter to match the preferences set
     * for this account.
     *
     * @return Sorts the enabled encryption protocol list given in parameter to match the
     * preferences set for this account.
     */
    public List<SrtpControlType> getSortedEnabledEncryptionProtocolList()
    {
        Map<String, Integer> encryptionProtocols
                = getIntegerPropertiesByPrefix(ProtocolProviderFactory.ENCRYPTION_PROTOCOL, true);
        Map<String, Boolean> encryptionProtocolStatus
                = getBooleanPropertiesByPrefix(ProtocolProviderFactory.ENCRYPTION_PROTOCOL_STATUS, true, false);

        // If the account is not yet configured, then ZRTP is activated by default.
        if (encryptionProtocols.size() == 0) {
            encryptionProtocols.put(ProtocolProviderFactory.ENCRYPTION_PROTOCOL + ".ZRTP", 0);
            encryptionProtocolStatus.put(ProtocolProviderFactory.ENCRYPTION_PROTOCOL_STATUS + ".ZRTP", true);
        }

        List<SrtpControlType> sortedEncryptionProtocols = new ArrayList<>(encryptionProtocols.size());

        // First: add all protocol in the right order.
        for (Map.Entry<String, Integer> e : encryptionProtocols.entrySet()) {
            int index = e.getValue();
            if (index != -1) {
                // If the key is set. {
                if (index > sortedEncryptionProtocols.size()) {
                    index = sortedEncryptionProtocols.size();
                }
                String name = e.getKey().substring(ProtocolProviderFactory.ENCRYPTION_PROTOCOL.length() + 1);

                try {
                    sortedEncryptionProtocols.add(index, SrtpControlType.valueOf(name));
                } catch (IllegalArgumentException exc) {
                    Timber.e(exc, "Failed to get SRTP control type for name: '%s', key: '%s'", name, e.getKey());
                }
            }
        }
        // Second: remove all disabled protocols.
        for (Iterator<SrtpControlType> i = sortedEncryptionProtocols.iterator(); i.hasNext(); ) {
            String encryptProtoName = "ENCRYPTION_PROTOCOL_STATUS." + i.next().toString();

            if (!encryptionProtocolStatus.containsKey(encryptProtoName)) {
                i.remove();
            }
        }
        return sortedEncryptionProtocols;
    }

    /**
     * Returns a <tt>java.util.Map</tt> of <tt>String</tt>s containing the all property names that
     * have the specified prefix and <tt>Boolean</tt> containing the value for each property
     * selected. Depending on the value of the <tt>exactPrefixMatch</tt> parameter the method will
     * (when false) or will not (when exactPrefixMatch is true) include property names that have
     * prefixes longer than the specified <tt>prefix</tt> param.
     *
     * Example:
     * Imagine a configuration service instance containing 2 properties only:<br>
     * <code>
     * net.java.sip.communicator.PROP1=value1<br>
     * net.java.sip.communicator.service.protocol.PROP1=value2
     * </code>
     *
     * A call to this method with a prefix="net.java.sip.communicator" and exactPrefixMatch=true
     * would only return the first property - net.java.sip.communicator.PROP1, whereas the same
     * call with exactPrefixMatch=false would return both properties as the second prefix includes
     * the requested prefix string.
     *
     * @param prefix a String containing the prefix (the non dotted non-caps part of a property name) that
     * we're looking for.
     * @param exactPrefixMatch a boolean indicating whether the returned property names should all have a prefix that
     * is an exact match of the the <tt>prefix</tt> param or whether properties with prefixes
     * that contain it but are longer than it are also accepted.
     * @param defaultValue the default value if the key is not set.
     * @return a <tt>java.util.Map</tt> containing all property name String-s matching the specified
     * conditions and the corresponding values as Boolean.
     */
    public Map<String, Boolean> getBooleanPropertiesByPrefix(String prefix,
            boolean exactPrefixMatch, boolean defaultValue)
    {
        List<String> propertyNames = getPropertyNamesByPrefix(prefix, exactPrefixMatch);
        Map<String, Boolean> properties = new HashMap<>(propertyNames.size());

        for (String propertyName : propertyNames) {
            properties.put(propertyName, getAccountPropertyBoolean(propertyName, defaultValue));
        }
        return properties;
    }

    /**
     * Returns a <tt>java.util.Map</tt> of <tt>String</tt>s containing the all property names that
     * have the specified prefix and <tt>Integer</tt> containing the value for each property
     * selected. Depending on the value of the <tt>exactPrefixMatch</tt> parameter the method will
     * (when false) or will not (when exactPrefixMatch is true) include property names that have
     * prefixes longer than the specified <tt>prefix</tt> param.
     *
     * Example:
     * Imagine a configuration service instance containing 2 properties only:<br>
     * <code>
     * net.java.sip.communicator.PROP1=value1<br>
     * net.java.sip.communicator.service.protocol.PROP1=value2
     * </code>
     *
     * A call to this method with a prefix="net.java.sip.communicator" and exactPrefixMatch=true
     * would only return the first property - net.java.sip.communicator.PROP1, whereas the same
     * call with exactPrefixMatch=false would return both properties as the second prefix includes
     * the requested prefix string.
     *
     * @param prefix a String containing the prefix (the non dotted non-caps part of a property name) that
     * we're looking for.
     * @param exactPrefixMatch a boolean indicating whether the returned property names should all have a prefix that
     * is an exact match of the the <tt>prefix</tt> param or whether properties with prefixes
     * that contain it but are longer than it are also accepted.
     * @return a <tt>java.util.Map</tt> containing all property name String-s matching the specified
     * conditions and the corresponding values as Integer.
     */
    public Map<String, Integer> getIntegerPropertiesByPrefix(String prefix, boolean exactPrefixMatch)
    {
        List<String> propertyNames = getPropertyNamesByPrefix(prefix, exactPrefixMatch);
        Map<String, Integer> properties = new HashMap<>(propertyNames.size());

        for (String propertyName : propertyNames) {
            properties.put(propertyName, getAccountPropertyInt(propertyName, -1));
        }
        return properties;
    }

    /**
     * Returns a <tt>java.util.List</tt> of <tt>String</tt>s containing the all property names that
     * have the specified prefix. Depending on the value of the <tt>exactPrefixMatch</tt> parameter
     * the method will (when false) or will not (when exactPrefixMatch is true) include property
     * names that have prefixes longer than the specified <tt>prefix</tt> param.
     *
     * Example:
     * Imagine a configuration service instance containing 2 properties only:<br>
     * <code>
     * net.java.sip.communicator.PROP1=value1<br>
     * net.java.sip.communicator.service.protocol.PROP1=value2
     * </code>
     *
     * A call to this method with a prefix="net.java.sip.communicator" and exactPrefixMatch=true
     * would only return the first property - net.java.sip.communicator.PROP1, whereas the same call
     * with exactPrefixMatch=false would return both properties as the second prefix includes the
     * requested prefix string.
     *
     * @param prefix a String containing the prefix (the non dotted non-caps part of a property name) that
     * we're looking for.
     * @param exactPrefixMatch a boolean indicating whether the returned property names should all have a prefix that
     * is an exact match of the the <tt>prefix</tt> param or whether properties with prefixes
     * that contain it but are longer than it are also accepted.
     * @return a <tt>java.util.List</tt>containing all property name String-s matching the
     * specified conditions.
     */
    public List<String> getPropertyNamesByPrefix(String prefix, boolean exactPrefixMatch)
    {
        List<String> resultKeySet = new LinkedList<>();

        for (String key : mAccountProperties.keySet()) {
            int ix = key.lastIndexOf('.');
            if (ix != -1) {
                String keyPrefix = key.substring(0, ix);
                if (exactPrefixMatch) {
                    if (prefix.equals(keyPrefix))
                        resultKeySet.add(key);
                }
                else if (keyPrefix.startsWith(prefix)) {
                    resultKeySet.add(key);
                }
            }
        }
        return resultKeySet;
    }

    /**
     * Sets the property a new value, but only if it's not <tt>null</tt> or the property is removed
     * from the map.
     *
     * @param key the property key
     * @param value the property value
     */
    public void setOrRemoveIfNull(String key, String value)
    {
        if (value != null) {
            putAccountProperty(key, value);
        }
        else {
            removeAccountProperty(key);
        }
    }

    /**
     * Puts the new property value if it's not <tt>null</tt> nor empty.
     *
     * @param key the property key
     * @param value the property value
     */
    public void setOrRemoveIfEmpty(String key, String value)
    {
        setOrRemoveIfEmpty(key, value, false);
    }

    /**
     * Puts the new property value if it's not <tt>null</tt> nor empty. If <tt>trim</tt> parameter
     * is set to <tt>true</tt> the string will be trimmed, before checked for emptiness.
     *
     * @param key the property key
     * @param value the property value
     * @param trim <tt>true</tt> if the value will be trimmed, before <tt>isEmpty()</tt> is called.
     */
    public void setOrRemoveIfEmpty(String key, String value, boolean trim)
    {
        if ((value != null) && (trim ? !value.trim().isEmpty() : !value.isEmpty())) {
            putAccountProperty(key, value);
        }
        else {
            removeAccountProperty(key);
        }
    }

    /**
     * Stores configuration properties held by this object into given <tt>accountProperties</tt>
     * map.
     *
     * @param protocolIconPath the path to the protocol icon is used
     * @param accountIconPath the path to the account icon if used
     * @param accountProperties output properties map
     */
    public void storeProperties(String protocolIconPath, String accountIconPath, Map<String, String> accountProperties)
    {
        if (protocolIconPath != null)
            setProtocolIconPath(protocolIconPath);

        if (accountIconPath != null)
            setAccountIconPath(accountIconPath);

        // cmeng - mergeProperties mAccountProperties into accountProperties and later save accountProperties to database
        mergeProperties(mAccountProperties, accountProperties);

        // Removes encrypted password property, as it will be restored during account storage.
        accountProperties.remove("ENCRYPTED_PASSWORD");
    }

    /**
     * Gets default property value for given <tt>key</tt>.
     *
     * @param key the property key
     * @return default property value for given<tt>key</tt>
     */
    protected String getDefaultString(String key)
    {
        return getDefaultStr(key);
    }

    /**
     * Gets default property value for given <tt>key</tt>.
     *
     * @param key the property key
     * @return default property value for given<tt>key</tt>
     */
    public static String getDefaultStr(String key)
    {
        return ProtocolProviderActivator.getConfigurationService().getString(DEFAULT_PREFIX + key);
    }

    /**
     * Copies all properties from <tt>input</tt> map to <tt>output</tt> map overwritten any value in output.
     *
     * @param input source properties map
     * @param output destination properties map
     */
    public static void mergeProperties(Map<String, String> input, Map<String, String> output)
    {
        for (String key : input.keySet()) {
            output.put(key, input.get(key));
        }
    }

    // *********************************************************

    /**
     * Create the new accountID based on two separate tables data i.e.
     * accountID based on given cursor and accountProperties table
     *
     * @param db aTalk SQLite Database
     * @param cursor AccountID table cursor for properties extraction
     * @param factory Account protocolProvider Factory
     * @return the new AccountID constructed
     */
    public static AccountID fromCursor(SQLiteDatabase db, Cursor cursor, ProtocolProviderFactory factory)
    {
        String accountUuid = cursor.getString(cursor.getColumnIndex(ACCOUNT_UUID));

        Map<String, String> accountProperties = new Hashtable<>();
        accountProperties.put(ProtocolProviderFactory.ACCOUNT_UUID, accountUuid);
        accountProperties.put(ProtocolProviderFactory.PROTOCOL, cursor.getString(cursor.getColumnIndex(PROTOCOL)));
        accountProperties.put(ProtocolProviderFactory.USER_ID, cursor.getString(cursor.getColumnIndex(USER_ID)));
        accountProperties.put(ProtocolProviderFactory.ACCOUNT_UID, cursor.getString(cursor.getColumnIndex(ACCOUNT_UID)));
        accountProperties.put(ProtocolProviderFactory.KEYS, cursor.getString(cursor.getColumnIndex(KEYS)));

        // Retrieve the remaining account properties from table
        String[] args = {accountUuid};
        cursor = db.query(TBL_PROPERTIES, null, ACCOUNT_UUID + "=?", args, null, null, null);
        int columnName = cursor.getColumnIndex("Name");
        int columnValue = cursor.getColumnIndex("Value");

        while (cursor.moveToNext()) {
            accountProperties.put(cursor.getString(columnName), cursor.getString(columnValue));
        }
        cursor.close();
        return factory.createAccount(accountProperties);
    }

    public ContentValues getContentValues()
    {
        final ContentValues values = new ContentValues();
        values.put(ACCOUNT_UUID, getAccountUuid());
        values.put(PROTOCOL, protocolName);
        values.put(USER_ID, userID);
        values.put(ACCOUNT_UID, accountUID);
        synchronized (mKeys) {
            values.put(KEYS, mKeys.toString());
        }
        return values;
    }

    public void setRosterVersion(final String version)
    {
        this.rosterVersion = version;
    }

    public String getKey(final String name)
    {
        synchronized (mKeys) {
            return mKeys.optString(name, null);
        }
    }

    public JSONObject getKeys()
    {
        return mKeys;
    }

    public int getKeyAsInt(final String name, int defaultValue)
    {
        String key = getKey(name);
        try {
            return key == null ? defaultValue : Integer.parseInt(key);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    public boolean setKey(final String keyName, final String keyValue)
    {
        synchronized (mKeys) {
            try {
                mKeys.put(keyName, keyValue);
                return true;
            } catch (final JSONException e) {
                return false;
            }
        }
    }

    public String getOtrFingerprint()
    {
        if (this.otrFingerprint == null) {
            //			try {
            //				if (this.mOtrService == null) {
            //					return null;
            //				}
            //				final PublicKey publicKey = this.mOtrService.getPublicKey();
            //				if (publicKey == null || !(publicKey instanceof DSAPublicKey)) {
            return null;
            //				}
            //				this.otrFingerprint = new OtrCryptoEngineImpl().getFingerprint(publicKey)
            //						.toLowerCase(Locale.US);
            //				return this.otrFingerprint;
            //			} catch (final OtrCryptoException ignored) {
            //				return null;
            //			}
        }
        else {
            return this.otrFingerprint;
        }
    }

    public boolean unsetKey(String key)
    {
        synchronized (mKeys) {
            return mKeys.remove(key) != null;
        }
    }
}
