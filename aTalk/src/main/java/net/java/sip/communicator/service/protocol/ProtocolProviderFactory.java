/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.protocol;

import net.java.sip.communicator.service.credentialsstorage.CredentialsStorageService;
import net.java.sip.communicator.util.ServiceUtils;

import org.atalk.android.plugin.timberlog.TimberLog;
import org.atalk.service.configuration.ConfigurationService;
import org.osgi.framework.BundleContext;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.ServiceRegistration;

import java.lang.reflect.UndeclaredThrowableException;
import java.util.ArrayList;
import java.util.Dictionary;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;

import timber.log.Timber;

/**
 * The ProtocolProviderFactory is what actually creates instances of a ProtocolProviderService
 * implementation. A provider factory would register, persistently store, and remove when necessary,
 * ProtocolProviders. The way things are in the SIP Communicator, a user account is represented (in
 * a 1:1 relationship) by an AccountID and a ProtocolProvider. In other words - one would have as
 * many protocol providers installed in a given moment as they would user account registered through
 * the various services.
 *
 * @author Emil Ivov
 * @author Lubomir Marinov
 * @author Eng Chong Meng
 * @author MilanKral
 */
public abstract class ProtocolProviderFactory
{
    /**
     * The name of a property which represents a password.
     */
    public static final String PASSWORD = "PASSWORD";

    /**
     * The name of a property which indicate if password is persistent.
     */
    public static final String PASSWORD_PERSISTENT = "PASSWORD_PERSISTENT";

    /**
     * The name of a property which indicates dnssecMode i.e. disabled, needsDnssec or needsDnssecAndDane.
     */
    public static final String DNSSEC_MODE = "DNSSEC_MODE";

    /**
     * The name of a property representing the name of the protocol for an ProtocolProviderFactory.
     */
    public static final String PROTOCOL = "PROTOCOL_NAME";

    /**
     * The name of a property representing the path to protocol icons.
     */
    public static final String PROTOCOL_ICON_PATH = "PROTOCOL_ICON_PATH";

    /**
     * The name of a property representing the path to the account icon to be used in the user
     * interface, when the protocol provider service is not available.
     */
    public static final String ACCOUNT_ICON_PATH = "ACCOUNT_ICON_PATH";

    /**
     * The name of a property which represents the AccountID i.e. Entity BareJid (string) of a
     * ProtocolProvider and that, together with a password is used to login the protocol network.
     */
    public static final String USER_ID = "USER_ID";

    /**
     * The name that should be displayed to others when we are calling or writing them.
     */
    public static final String DISPLAY_NAME = "DISPLAY_NAME";

    /**
     * The name that should be displayed to the user on call via and chat via lists.
     */
    public static final String ACCOUNT_DISPLAY_NAME = "ACCOUNT_DISPLAY_NAME";

    /**
     * The unique identifier of the property under which we store protocol - AccountID accxxxxx...
     */
    public static final String ACCOUNT_UUID = "ACCOUNT_UUID";

    /**
     * The name of the property under which we store protocol AccountID - Jabber:abc123@atalk.org.
     */
    public static final String ACCOUNT_UID = "ACCOUNT_UID";

    /**
     * The name of the property under which we store CRYPTO keys information.
     */
    public static final String KEYS = "KEYS";

    /**
     * The options of the property under which we store protocol AccountID options.
     */
    // public static final String OPTIONS = "OPTIONS";

    // cmeng - xmpp domain setup with SRV record ???
    // custom xmpp domain to be resolved for SRV records used for connection.
    // e.g. one server (server1.domain.com) setup as "server1.domain.com", then DNS records is
    // setup as _xmpp-client._tcp.domain.com. IN SRV 0 5 5222 server1.domain.com.
    public static final String CUSTOM_XMPP_DOMAIN = "CUSTOM_XMPP_DOMAIN";

    /**
     * Indicates if the server settings are overridden
     */
    public static final String IS_SERVER_OVERRIDDEN = "IS_SERVER_OVERRIDDEN";

    /**
     * The name of the property under which we store protocol the address of a protocol centric
     * entity (any protocol server).
     */
    public static final String SERVER_ADDRESS = "SERVER_ADDRESS";

    /**
     * The name of the property under which we store the number of the port where the server stored
     * against the SERVER_ADDRESS property is expecting connections to be made via this protocol.
     */
    public static final String SERVER_PORT = "SERVER_PORT";

    /**
     * The name of the property under which we store the name of the transport protocol that needs
     * to be used to access the server.
     */
    public static final String SERVER_TRANSPORT = "SERVER_TRANSPORT";

    /**
     * Indicates if Proxy should be used.
     */
    public static final String IS_USE_PROXY = "PROXY_ENABLED";

    /**
     * Configures the URL which is to be used with BOSH transport.
     */
    public static final String BOSH_URL = "BOSH_URL";

    /**
     * Indicates HTTP proxy is enabled with BOSH protocol. Only HTTP proxy is allowwed with BOSH
     */
    public static final String BOSH_PROXY_HTTP_ENABLED = "BOSH_PROXY_HTTP_ENABLED";

    /**
     * The name of the property under which we store the the type of the proxy stored against the
     * PROXY_ADDRESS property. Exact type values depend on protocols and among them are socks4,
     * socks5, http and possibly others.
     */
    public static final String PROXY_TYPE = "PROXY_TYPE";

    /**
     * The name of the property under which we store protocol the address of a protocol proxy.
     */
    public static final String PROXY_ADDRESS = "PROXY_ADDRESS";

    /**
     * The name of the property under which we store the number of the port where the proxy stored
     * against the PROXY_ADDRESS property is expecting connections to be made via this protocol.
     */
    public static final String PROXY_PORT = "PROXY_PORT";

    /**
     * The name of the property under which we store the the username for the proxy stored against
     * the PROXY_ADDRESS property.
     */
    public static final String PROXY_USERNAME = "PROXY_USERNAME";

    /**
     * The name of the property under which we store the password for the proxy stored against the
     * PROXY_ADDRESS property.
     */
    public static final String PROXY_PASSWORD = "PROXY_PASSWORD";

    /**
     * The name of the property which defines whether proxy is auto configured by the protocol by
     * using known methods such as specific DNS queries.
     */
    public static final String PROXY_AUTO_CONFIG = "PROXY_AUTO_CONFIG";

    /**
     * The name of the property under which we store the name of the transport protocol that needs
     * to be used to access the proxy.
     */
    public static final String PROXY_TRANSPORT = "PROXY_TRANSPORT";

    /**
     * The name of the property that indicates whether loose routing should be forced for all
     * traffic in an account, rather than routing through an outbound proxy which is the default
     * for aTalk.
     */
    public static final String FORCE_PROXY_BYPASS = "FORCE_PROXY_BYPASS";

    /**
     * The name of the property under which we store the the authorization name for the proxy
     * stored against the PROXY_ADDRESS property.
     */
    public static final String AUTHORIZATION_NAME = "AUTHORIZATION_NAME";

    /**
     * The property indicating the preferred UDP and TCP port to bind to for clear communications.
     */
    public static final String PREFERRED_CLEAR_PORT_PROPERTY_NAME = "SIP_PREFERRED_CLEAR_PORT";

    /**
     * The property indicating the preferred TLS (TCP) port to bind to for secure communications.
     */
    public static final String PREFERRED_SECURE_PORT_PROPERTY_NAME = "SIP_PREFERRED_SECURE_PORT";

    /**
     * The name of the property under which we store the user preference for a transport
     * protocol to use (i.e. tcp or udp).
     */
    public static final String PREFERRED_TRANSPORT = "PREFERRED_TRANSPORT";

    /**
     * The name of the property under which we store whether we generate resource values or we just
     * use the stored one.
     */
    public static final String AUTO_GENERATE_RESOURCE = "AUTO_GENERATE_RESOURCE";

    /**
     * The name of the property under which we store resources such as the jabber resource property.
     */
    public static final String RESOURCE = "RESOURCE";

    /**
     * The name of the property under which we store resource priority.
     */
    public static final String RESOURCE_PRIORITY = "RESOURCE_PRIORITY";

    /**
     * The name of the property which defines that the call is encrypted by default
     */
    public static final String DEFAULT_ENCRYPTION = "DEFAULT_ENCRYPTION";

    /**
     * The name of the property that indicates the encryption protocols for this account.
     */
    public static final String ENCRYPTION_PROTOCOL = "ENCRYPTION_PROTOCOL";

    /**
     * The name of the property that indicates the status (enabled or disabled) encryption
     * protocols for this account.
     */
    public static final String ENCRYPTION_PROTOCOL_STATUS = "ENCRYPTION_PROTOCOL_STATUS";

    /**
     * The name of the property which defines if to include the ZRTP attribute to SIP/SDP
     */
    public static final String DEFAULT_SIPZRTP_ATTRIBUTE = "DEFAULT_SIPZRTP_ATTRIBUTE";

    /*
     * DTLS-SRTP TLS certificate signature algorithm e.g. SHA256withECDSA, SHA256withRSA
     */
    public static final String DTLS_CERT_SIGNATURE_ALGORITHM = "DTLS_CERT_SIGNATURE_ALGORITHM";

    /**
     * The name of the property which defines the ID of the client TLS certificate configuration entry.
     */
    public static final String CLIENT_TLS_CERTIFICATE = "CLIENT_TLS_CERTIFICATE";

    /**
     * The name of the property under which we store the boolean value indicating if the user name
     * should be automatically changed if the specified name already exists. This property is meant
     * to be used by IRC implementations.
     */
    public static final String AUTO_CHANGE_USER_NAME = "AUTO_CHANGE_USER_NAME";

    /**
     * The name of the property under which we store the boolean value indicating if a password is
     * required. Initially this property is meant to be used by IRC implementations.
     */
    public static final String NO_PASSWORD_REQUIRED = "NO_PASSWORD_REQUIRED";

    /**
     * The name of the property under which we store if the presence is enabled.
     */
    public static final String IS_PRESENCE_ENABLED = "IS_PRESENCE_ENABLED";

    /**
     * The name of the property under which we store if the p2p mode for SIMPLE should be forced.
     */
    public static final String FORCE_P2P_MODE = "FORCE_P2P_MODE";

    /**
     * The name of the property under which we store the offline contact polling period for SIMPLE.
     */
    public static final String POLLING_PERIOD = "POLLING_PERIOD";

    /**
     * The name of the property under which we store the chosen default subscription expiration
     * value for SIMPLE.
     */
    public static final String SUBSCRIPTION_EXPIRATION = "SUBSCRIPTION_EXPIRATION";

    /**
     * Indicates if the server address has been validated.
     */
    public static final String SERVER_ADDRESS_VALIDATED = "SERVER_ADDRESS_VALIDATED";

    /**
     * Indicates if the proxy address has been validated.
     */
    public static final String PROXY_ADDRESS_VALIDATED = "PROXY_ADDRESS_VALIDATED";

    /**
     * Indicates the search strategy chosen for the DICT protocol.
     */
    public static final String STRATEGY = "STRATEGY";

    /**
     * Indicates a protocol that would not be shown in the user interface as an account.
     */
    public static final String IS_PROTOCOL_HIDDEN = "IS_PROTOCOL_HIDDEN";

    /**
     * Indicates if the given account is the preferred account.
     */
    public static final String IS_PREFERRED_PROTOCOL = "IS_PREFERRED_PROTOCOL";

    /**
     * The name of the property that would indicate if a given account is currently enabled or disabled.
     */
    public static final String IS_ACCOUNT_DISABLED = "IS_ACCOUNT_DISABLED";

    /**
     * The name of the property that indicates if a given account needs InBand registration with the server
     */
    public static final String IBR_REGISTRATION = "IBR_REGISTRATION";

    /**
     * The name of the property that would indicate if a given account configuration form is currently hidden.
     */
    public static final String IS_ACCOUNT_CONFIG_HIDDEN = "IS_CONFIG_HIDDEN";

    /**
     * The name of the property that would indicate if a given account status menu is currently hidden.
     */
    public static final String IS_ACCOUNT_STATUS_MENU_HIDDEN = "IS_STATUS_MENU_HIDDEN";

    /**
     * The name of the property that would indicate if a given account configuration is read only.
     */
    public static final String IS_ACCOUNT_READ_ONLY = "IS_READ_ONLY";

    /**
     * The name of the property that would indicate if a given account groups are readonly, values
     * can be all or a comma separated group names including root.
     */
    public static final String ACCOUNT_READ_ONLY_GROUPS = "READ_ONLY_GROUPS";

    /**
     * Indicates if ICE should be used.
     */
    public static final String IS_USE_ICE = "ICE_ENABLED";

    /**
     * Indicates if UPnP should be used with ICE.
     */
    public static final String IS_USE_UPNP = "UPNP_ENABLED";

    /**
     * Indicates if STUN server should be automatically discovered.
     */
    public static final String AUTO_DISCOVER_STUN = "AUTO_DISCOVER_STUN";

    /**
     * Indicates if default STUN server would be used if no other STUN/TURN server are available.
     */
    public static final String USE_DEFAULT_STUN_SERVER = "USE_DEFAULT_STUN_SERVER";

    /**
     * The name of the boolean account property which indicates whether Jitsi
     * will use translator for media, instead of mixing, for conference calls.
     * By default if supported mixing is used (audio mixed, video relayed).
     */
    public static final String USE_TRANSLATOR_IN_CONFERENCE = "USE_TRANSLATOR_IN_CONFERENCE";

    /**
     * The property name prefix for all stun server properties. We generally use this prefix in
     * conjunction with an index which is how we store multiple servers.
     */
    public static final String STUN_PREFIX = "STUN";

    /**
     * The base property name for address of additional STUN servers specified.
     */
    public static final String STUN_ADDRESS = "STUN_ADDRESS";

    /**
     * The base property name for port of additional STUN servers specified.
     */
    public static final String STUN_PORT = "PORT";

    /**
     * The base property name for username of additional STUN servers specified.
     */
    public static final String STUN_USERNAME = "USERNAME";

    /**
     * The base property name for password of additional STUN servers specified.
     */
    public static final String STUN_PASSWORD = "PASSWORD";

    /**
     * The base property name for protocol of additional STUN servers specified.
     */
    public static final String STUN_TURN_PROTOCOL = "TURN_PROTOCOL";

    /**
     * The base property name for the turn supported property of additional STUN servers specified.
     */
    public static final String STUN_IS_TURN_SUPPORTED = "IS_TURN_SUPPORTED";

    /**
     * Indicates if JingleNodes should be used with ICE.
     */
    public static final String IS_USE_JINGLE_NODES = "JINGLE_NODES_ENABLED";

    /**
     * Indicates if JingleNodes should be used with ICE.
     */
    public static final String AUTO_DISCOVER_JINGLE_NODES = "AUTO_DISCOVER_JINGLE_NODES";

    /**
     * Indicates if JingleNodes should use buddies to search for nodes.
     */
    public static final String JINGLE_NODES_SEARCH_BUDDIES = "JINGLE_NODES_SEARCH_BUDDIES";

    /**
     * The name of the boolean account property which indicates whether Jitsi Videobridge is to be
     * used, if available and supported, for conference calls.
     */
    public static final String USE_JITSI_VIDEO_BRIDGE = "USE_JITSI_VIDEO_BRIDGE";

    /**
     * Minimum TLS protocol version.
     */
    public static final String MINUMUM_TLS_VERSION = "MINUMUM_TLS_VERSION";

    /**
     * Indicates if we allow non-TLS connection.
     */
    public static final String IS_ALLOW_NON_SECURE = "ALLOW_NON_SECURE";

    /**
     * Enable notifications for new voicemail messages.
     */
    public static final String VOICEMAIL_ENABLED = "VOICEMAIL_ENABLED";

    /**
     * Address used to reach voicemail box, by services able to subscribe for voicemail new messages notifications.
     */
    public static final String VOICEMAIL_URI = "VOICEMAIL_URI";

    /**
     * Address used to call to hear your messages stored on the server for your voicemail.
     */
    public static final String VOICEMAIL_CHECK_URI = "VOICEMAIL_CHECK_URI";

    /**
     * Indicates if calling is disabled for a certain account.
     */
    public static final String IS_CALLING_DISABLED_FOR_ACCOUNT = "CALLING_DISABLED";

    /**
     * Indicates if video calling is disabled for a certain account.
     */
    public static final String IS_VIDEO_CALLING_DISABLED_FOR_ACCOUNT = "VIDEO_CALLING_DISABLED";

    /**
     * Indicates if desktop streaming/sharing is disabled for a certain account.
     */
    public static final String IS_DESKTOP_STREAMING_DISABLED = "DESKTOP_STREAMING_DISABLED";

    /**
     * Indicates if desktop remote control is disabled for a certain account.
     */
    public static final String IS_DESKTOP_REMOTE_CONTROL_DISABLED = "DESKTOP_REMOTE_CONTROL_DISABLED";

    /**
     * The sms default server address.
     */
    public static final String SMS_SERVER_ADDRESS = "SMS_SERVER_ADDRESS";

    /**
     * Keep-alive method used by the protocol.
     */
    public static final String KEEP_ALIVE_METHOD = "KEEP_ALIVE_METHOD";

    /**
     * The keep-alive option enable / disable.
     */
    public static final String IS_KEEP_ALIVE_ENABLE = "IS_KEEP_ALIVE_ENABLE";

    /**
     * The Ping auto optimization option enable / disable.
     */
    public static final String IS_PING_AUTO_TUNE_ENABLE = "IS_PING_AUTO_TUNE_ENABLE";

    /**
     * The interval for keep-alive if any.
     */
    public static final String PING_INTERVAL = "PING_INTERVAL";

    /**
     * The name of the property holding DTMF method.
     */
    public static final String DTMF_METHOD = "DTMF_METHOD";

    /**
     * The minimal DTMF tone duration.
     */
    public static final String DTMF_MINIMAL_TONE_DURATION = "DTMF_MINIMAL_TONE_DURATION";

    /**
     * Paranoia mode when turned on requires all calls to be secure and indicated as such.
     */
    public static final String MODE_PARANOIA = "MODE_PARANOIA";

    /**
     * The name of the "override encodings" property
     */
    public static final String OVERRIDE_ENCODINGS = "OVERRIDE_ENCODINGS";

    /**
     * The prefix used to store account encoding properties
     */
    public static final String ENCODING_PROP_PREFIX = "Encodings";

    /**
     * An account property to provide a connected account to check for its status. Used when the
     * current provider need to reject calls but is missing presence operation set and need to
     * check other provider for status.
     */
    public static final String CUSAX_PROVIDER_ACCOUNT_PROP = "cusax.XMPP_ACCOUNT_ID";

    /**
     * The <code>BundleContext</code> containing (or to contain) the service registration of this factory.
     */
    private final BundleContext bundleContext;

    /**
     * The name of the protocol this factory registers its <code>ProtocolProviderService</code>s
     * with and to be placed in the properties of the accounts created by this factory.
     */
    private final String protocolName;

    /**
     * The configuration service.
     */
    private ConfigurationService configurationService;

    /**
     * The table that we store our accounts in.
     *
     * TODO Synchronize the access to the field which may in turn be better achieved by also hiding
     * it from protected into private access.
     */
    protected final Map<AccountID, ServiceRegistration<ProtocolProviderService>> registeredAccounts = new HashMap<>();

    /**
     * The name of the property that indicates the AVP type.
     * <ul>
     * <li>{@link #SAVP_OFF}</li>
     * <li>{@link #SAVP_MANDATORY}</li>
     * <li>{@link #SAVP_OPTIONAL}</li>
     * </ul>
     */
    public static final String SAVP_OPTION = "SAVP_OPTION";

    /**
     * Always use RTP/AVP
     */
    public static final int SAVP_OFF = 0;

    /**
     * Always use RTP/SAVP
     */
    public static final int SAVP_MANDATORY = 1;

    /**
     * Sends two media description, with RTP/SAVP being first.
     */
    public static final int SAVP_OPTIONAL = 2;

    /**
     * The name of the property that defines the enabled SDES cipher suites. Enabled suites are
     * listed as CSV by their RFC name.
     */
    public static final String SDES_CIPHER_SUITES = "SDES_CIPHER_SUITES";

    /**
     * The name of the property that defines the enabled/disabled state of message carbons.
     */
    public static final String IS_CARBON_DISABLED = "CARBON_DISABLED";

    /**
     * The name of the property that stores salt value for ZID computation.
     */
    public static final String ZID_SALT = "ZID_SALT";

    /**
     * Creates a new <code>ProtocolProviderFactory</code>.
     *
     * @param bundleContext the bundle context reference of the service
     * @param protocolName the name of the protocol
     */
    protected ProtocolProviderFactory(BundleContext bundleContext, String protocolName)
    {
        this.bundleContext = bundleContext;
        this.protocolName = protocolName;
        configurationService = ServiceUtils.getService(bundleContext, ConfigurationService.class);
    }

    /**
     * Gets the {@code BundleContext} containing (or to contain) the service registration of this factory.
     *
     * @return the {@code BundleContext} containing (or to contain) the service registration of this factory
     */
    public BundleContext getBundleContext()
    {
        return bundleContext;
    }

    /**
     * Initializes and creates an account corresponding to the specified accountProperties and
     * registers the resulting ProtocolProvider in the <code>context</code> BundleContext parameter.
     * Note that account registration is persistent and accounts that are registered during a
     * particular sip-communicator session would be automatically reloaded during all following
     * sessions until they are removed through the removeAccount method.
     *
     * @param userID the user identifier uniquely representing the newly created account within the protocol namespace.
     * @param accountProperties a set of protocol (or implementation) specific properties defining the new account.
     * @return the AccountID of the newly created account.
     * @throws java.lang.IllegalArgumentException if userID does not correspond to an identifier in the context of the
     * underlying protocol or if accountProperties does not contain a complete set of account installation properties.
     * @throws java.lang.IllegalStateException if the account has already been installed.
     * @throws java.lang.NullPointerException if any of the arguments is null.
     */
    public abstract AccountID installAccount(String userID, Map<String, String> accountProperties)
            throws IllegalArgumentException, IllegalStateException, NullPointerException;

    /**
     * Modifies the account corresponding to the specified accountID. This method is meant to be
     * used to change properties of already existing accounts. Note that if the given accountID
     * doesn't correspond to any registered account this method would do nothing.
     *
     * @param protocolProvider the protocol provider service corresponding to the modified account.
     * @param accountProperties a set of protocol (or implementation) specific properties defining the new account.
     * @throws java.lang.IllegalArgumentException if userID does not correspond to an identifier in the context of the
     * underlying protocol or if accountProperties does not contain a complete set of account installation properties.
     * @throws java.lang.NullPointerException if any of the arguments is null.
     */
    public abstract void modifyAccount(ProtocolProviderService protocolProvider, Map<String, String> accountProperties)
            throws IllegalArgumentException, NullPointerException;

    /**
     * Returns a copy of the list containing the <code>AccountID</code>s of all accounts currently
     * registered in this protocol provider.
     *
     * @return a copy of the list containing the <code>AccountID</code>s of all accounts currently
     * registered in this protocol provider.
     */
    public ArrayList<AccountID> getRegisteredAccounts()
    {
        synchronized (registeredAccounts) {
            return new ArrayList<>(registeredAccounts.keySet());
        }
    }

    /**
     * Returns the ServiceReference for the protocol provider corresponding to the specified
     * accountID or null if the accountID is unknown.
     *
     * @param accountID the accountID of the protocol provider we'd like to get
     * @return a ServiceReference object to the protocol provider with the specified account id and
     * null if the account id is unknown to the provider factory.
     */
    public ServiceReference<ProtocolProviderService> getProviderForAccount(AccountID accountID)
    {
        ServiceRegistration<ProtocolProviderService> registration;
        synchronized (registeredAccounts) {
            registration = registeredAccounts.get(accountID);
        }

        try {
            if (registration != null)
                return registration.getReference();
        } catch (IllegalStateException ise) {
            synchronized (registeredAccounts) {
                registeredAccounts.remove(accountID);
            }
        }
        return null;
    }

    /**
     * Removes the specified account from the list of accounts that this provider factory is
     * handling. If the specified accountID is unknown to the ProtocolProviderFactory, the call has
     * no effect and false is returned. This method is persistent in nature and once called the
     * account corresponding to the specified ID will not be loaded during future runs of the project.
     *
     * @param accountID the ID of the account to remove.
     * @return true if an account with the specified ID existed and was removed and false otherwise.
     */
    public boolean uninstallAccount(AccountID accountID)
    {
        // If the protocol provider service is registered, first unregister the service.
        ServiceReference<ProtocolProviderService> serRef = getProviderForAccount(accountID);
        if (serRef != null) {
            BundleContext bundleContext = getBundleContext();
            ProtocolProviderService protocolProvider = bundleContext.getService(serRef);
            try {
                protocolProvider.unregister();
            } catch (OperationFailedException ex) {
                Timber.e("Failed to unregister protocol provider for account: %s caused by: %s",
                        accountID, ex.getMessage());
            }
        }

        ServiceRegistration<ProtocolProviderService> registration;
        synchronized (registeredAccounts) {
            registration = registeredAccounts.remove(accountID);
        }

        // first remove the stored account so when PP is unregistered we can distinguish between
        // deleted or just disabled account
        boolean wasAccountExisting = removeStoredAccount(accountID);
        if (registration != null) {
            // Kill the service.
            registration.unregister();
        }
        return wasAccountExisting;
    }

    /**
     * The method stores the specified account in the configuration service under the package name
     * of the source factory. The restore and remove account methods are to be used to obtain
     * access to and control the stored accounts.
     *
     * In order to store all account properties, the method would create an entry in the
     * configuration service corresponding (beginning with) the <code>sourceFactory</code>'s package
     * name and add to it a unique identifier (e.g. the current milliseconds.)
     *
     * @param accountID the AccountID corresponding to the account that we would like to store.
     */
    protected void storeAccount(AccountID accountID)
    {
        this.storeAccount(accountID, true);
    }

    /**
     * The method stores the specified account in the configuration service under the package name
     * of the source factory. The restore and remove account methods are to be used to obtain
     * access to and control the stored accounts.
     *
     * In order to store all account properties, the method would create an entry in the
     * configuration service corresponding (beginning with) the <code>sourceFactory</code>'s package
     * name and add to it a unique identifier (e.g. the current milliseconds.)
     *
     * @param accountID the AccountID corresponding to the account that we would like to store.
     * @param isModification if <code>false</code> there must be no such already loaded account, it <code>true</code> ist
     * modification of an existing account. Usually we use this method with <code>false</code> in
     * method installAccount and with <code>true</code> or the overridden method in method
     * modifyAccount.
     */
    protected void storeAccount(AccountID accountID, boolean isModification)
    {
        if (!isModification && getAccountManager().getStoredAccounts().contains(accountID)) {
            throw new IllegalStateException("An account for id " + accountID.getUserID() + " was already loaded!");
        }
        try {
            getAccountManager().storeAccount(this, accountID);
        } catch (OperationFailedException ofex) {
            throw new UndeclaredThrowableException(ofex);
        }
    }

    /**
     * Saves the password for the specified account after scrambling it a bit so that it is not
     * visible from first sight. (The method remains highly insecure).
     *
     * @param accountID the AccountID for the account whose password we're storing
     * @param password the password itself
     * @throws IllegalArgumentException if no account corresponding to {@code accountID} has been previously stored
     */
    public void storePassword(AccountID accountID, String password)
            throws IllegalArgumentException
    {
        try {
            storePassword(getBundleContext(), accountID, password);
        } catch (OperationFailedException ofex) {
            throw new UndeclaredThrowableException(ofex);
        }
    }

    /**
     * Saves the password for the specified account after scrambling it a bit so that it is not
     * visible from first sight (Method remains highly insecure).
     *
     * TODO Delegate the implementation to {@link AccountManager} because it knows the format in
     * which the password (among the other account properties) is to be saved.
     *
     * @param bundleContext a currently valid bundle context.
     * @param accountID the <code>AccountID</code> of the account whose password is to be stored
     * @param password the password to be stored
     * @throws IllegalArgumentException if no account corresponding to <code>accountID</code> has been previously stored.
     * @throws OperationFailedException if anything goes wrong while storing the specified <code>password</code>
     */
    protected void storePassword(BundleContext bundleContext, AccountID accountID, String password)
            throws IllegalArgumentException, OperationFailedException
    {
        String accountUuid = accountID.getAccountUuid();
        if (accountUuid == null) {
            throw new IllegalArgumentException("No previous records found for account ID: "
                    + accountID.getAccountUniqueID());
        }

        CredentialsStorageService credentialsStorage
                = ServiceUtils.getService(bundleContext, CredentialsStorageService.class);
        if (!credentialsStorage.storePassword(accountUuid, password)) {
            throw new OperationFailedException("CredentialsStorageService failed to storePassword",
                    OperationFailedException.GENERAL_ERROR);
        }
        // Update password property also in the AccountID to prevent it from being removed during
        // account reload in some cases.
        accountID.setPassword(password);
    }

    /**
     * Saves the dnssec Mode for the specified account.
     *
     * @param accountID the AccountID for the account whose password we're storing
     * @param dnssecMode see DNSSEC_MODE definition
     * @throws IllegalArgumentException if no account corresponding to {@code accountID} has been previously stored
     */
    public void storeDnssecMode(AccountID accountID, String dnssecMode)
            throws IllegalArgumentException
    {
        try {
            storeDnssecMode(getBundleContext(), accountID, dnssecMode);
        } catch (OperationFailedException ofex) {
            throw new UndeclaredThrowableException(ofex);
        }
    }

    /**
     * Saves the password for the specified account after scrambling it a bit so that it is not
     * visible from first sight (Method remains highly insecure).
     *
     * TODO Delegate the implementation to {@link AccountManager} because it knows the format in
     * which the password (among the other account properties) is to be saved.
     *
     * @param bundleContext a currently valid bundle context.
     * @param accountID the <code>AccountID</code> of the account whose password is to be stored
     * @param dnssecMode the dnssecMode to be stored
     * @throws IllegalArgumentException if no account corresponding to <code>accountID</code> has been previously stored.
     * @throws OperationFailedException if anything goes wrong while storing the specified <code>password</code>
     */
    protected void storeDnssecMode(BundleContext bundleContext, AccountID accountID, String dnssecMode)
            throws IllegalArgumentException, OperationFailedException
    {
        String accountUuid = accountID.getAccountUuid();
        if (accountUuid == null) {
            throw new IllegalArgumentException("No previous records found for account ID: "
                    + accountID.getAccountUniqueID());
        }
        configurationService.setProperty(accountUuid + "." + DNSSEC_MODE, dnssecMode);

        // Update dnssecMode in the AccountID to prevent it from being removed during account reload in some cases.
        accountID.setDnssMode(dnssecMode);
    }

    //=======================================

    /**
     * Returns the password last saved for the specified account.
     *
     * @param accountID the AccountID for the account whose password we're looking for
     * @return a String containing the password for the specified accountID
     */
    public String loadPassword(AccountID accountID)
    {
        return loadPassword(getBundleContext(), accountID);
    }

    /**
     * Returns the password last saved for the specified account.
     *
     * TODO Delegate the implementation to {@link AccountManager} because it knows the format in
     * which the password (among the other account properties) was saved.
     *
     * @param bundleContext a currently valid bundle context.
     * @param accountID the AccountID for the account whose password we're looking for..
     * @return a String containing the password for the specified accountID.
     */
    protected String loadPassword(BundleContext bundleContext, AccountID accountID)
    {
        CredentialsStorageService credentialsStorage
                = ServiceUtils.getService(bundleContext, CredentialsStorageService.class);
        return credentialsStorage.loadPassword(accountID.getAccountUuid());
    }

    /**
     * Initializes and creates an account corresponding to the specified accountProperties and
     * registers the resulting ProtocolProvider in the <code>context</code> BundleContext parameter.
     * This method has a persistent effect. Once created the resulting account will remain
     * installed until removed through the uninstallAccount method.
     *
     * @param accountProperties a set of protocol (or implementation) specific properties defining the new account.
     * @return the AccountID of the newly loaded account
     */
    public AccountID loadAccount(Map<String, String> accountProperties)
    {
        AccountID accountID = createAccount(accountProperties);
        loadAccount(accountID);
        return accountID;
    }

    /**
     * Creates a protocol provider for the given <code>accountID</code> and registers it in the bundle
     * context. This method has a persistent effect. Once created the resulting account will remain
     * installed until removed through the uninstallAccount method.
     *
     * @param accountID the account identifier
     * @return <code>true</code> if the account with the given <code>accountID</code> is successfully
     * loaded, otherwise returns <code>false</code>
     */
    public boolean loadAccount(AccountID accountID)
    {
        // Need to obtain the original user id property, instead of calling accountID.getUserID(),
        // because this method could return a modified version of the user id property.
        String userID = accountID.getAccountPropertyString(ProtocolProviderFactory.USER_ID);
        ProtocolProviderService service = createService(userID, accountID);
        if (service == null)
            return false;

        Dictionary<String, String> properties = new Hashtable<>();
        properties.put(PROTOCOL, protocolName);
        properties.put(USER_ID, userID);

        ServiceRegistration<ProtocolProviderService> serviceRegistration
                = bundleContext.registerService(ProtocolProviderService.class, service, properties);

        if (serviceRegistration == null) {
            return false;
        }
        else {
            synchronized (registeredAccounts) {
                registeredAccounts.put(accountID, serviceRegistration);
            }
            return true;
        }
    }

    /**
     * Unloads the account corresponding to the given <code>accountID</code>. Unregisters the corresponding
     * protocol provider, but keeps the account in contrast to the uninstallAccount method.
     *
     * @param accountID the account identifier
     * @return true if an account with the specified ID existed and was unloaded and false otherwise.
     */
    public boolean unloadAccount(AccountID accountID)
    {
        // Unregister the protocol provider.
        ServiceReference<ProtocolProviderService> serRef = getProviderForAccount(accountID);
        if (serRef == null) {
            return false;
        }
        BundleContext bundleContext = getBundleContext();
        ProtocolProviderService protocolProvider = bundleContext.getService(serRef);
        try {
            protocolProvider.unregister();
        } catch (OperationFailedException ex) {
            Timber.e("Failed to unregister protocol provider for account: %s caused by: %s",
                    accountID, ex.getMessage());
        }

        ServiceRegistration<ProtocolProviderService> registration;
        synchronized (registeredAccounts) {
            registration = registeredAccounts.remove(accountID);
        }
        if (registration == null) {
            return false;
        }

        // Kill the service. // Catch based on Field Failure
        try {
            registration.unregister();
        } catch (IllegalStateException ex) {
            return false;
        }
        return true;
    }

    /**
     * Initializes and creates an account corresponding to the specified accountProperties.
     *
     * @param accountProperties a set of protocol (or implementation) specific properties defining the new account.
     * @return the AccountID of the newly created account
     */
    public AccountID createAccount(Map<String, String> accountProperties)
    {
        BundleContext bundleContext = getBundleContext();
        if (bundleContext == null)
            throw new NullPointerException("The specified BundleContext was null");

        if (accountProperties == null)
            throw new NullPointerException("The specified property map was null");

        String userID = accountProperties.get(USER_ID);
        if (userID == null)
            throw new NullPointerException("The account properties contained no user id.");

        String protocolName = getProtocolName();
        if (!accountProperties.containsKey(PROTOCOL))
            accountProperties.put(PROTOCOL, protocolName);

        return createAccountID(userID, accountProperties);
    }

    /**
     * Creates a new {@code AccountID} instance with a specific user ID to represent a given
     * set of account properties.
     *
     * The method is a pure factory allowing implementers to specify the runtime type of the created
     * {@code AccountID} and customize the instance. The returned {@code AccountID} will
     * later be associated with a {@code ProtocolProviderService} by the caller (e.g. using
     * {@link #createService(String, AccountID)}).
     *
     * @param userID the user ID of the new instance
     * @param accountProperties the set of properties to be represented by the new instance
     * @return a new {@code AccountID} instance with the specified user ID representing the
     * given set of account properties
     */
    protected abstract AccountID createAccountID(String userID, Map<String, String> accountProperties);

    /**
     * Gets the name of the protocol this factory registers its {@code ProtocolProviderService}s
     * with and to be placed in the properties of the accounts created by this factory.
     *
     * @return the name of the protocol this factory registers its
     * {@code ProtocolProviderService}s with and to be placed in the properties of the
     * accounts created by this factory
     */
    public String getProtocolName()
    {
        return protocolName;
    }

    /**
     * Initializes a new {@code ProtocolProviderService} instance with a specific user ID to
     * represent a specific {@code AccountID}.
     *
     * The method is a pure factory allowing implementers to specify the runtime type of the created
     * {@code ProtocolProviderService} and customize the instance. The caller will later
     * register the returned service with the {@code BundleContext} of this factory.
     *
     * @param userID the user ID to initialize the new instance with
     * @param accountID the {@code AccountID} to be represented by the new instance
     * @return a new {@code ProtocolProviderService} instance with the specific user ID
     * representing the specified {@code AccountID}
     */
    protected abstract ProtocolProviderService createService(String userID, AccountID accountID);

    /**
     * Removes the account with <code>accountID</code> from the set of accounts that are persistently
     * stored inside the configuration service.
     *
     * @param accountID the AccountID of the account to remove.
     * @return true if an account has been removed and false otherwise.
     */
    protected boolean removeStoredAccount(AccountID accountID)
    {
        return getAccountManager().removeStoredAccount(this, accountID);
    }

    /**
     * Returns the prefix for all persistently stored properties of the account with the specified id.
     *
     * @param bundleContext a currently valid bundle context.
     * @param accountID the AccountID of the account whose properties we're looking for.
     * @param sourcePackageName a String containing the package name of the concrete factory class that extends us.
     * @return a String indicating the ConfigurationService property name prefix under which all
     * account properties are stored or null if no account corresponding to the specified id was found.
     */
    public static String findAccountPrefix(BundleContext bundleContext, AccountID accountID, String sourcePackageName)
    {
        ServiceReference<ConfigurationService> confReference = bundleContext.getServiceReference(ConfigurationService.class);
        ConfigurationService configurationService = bundleContext.getService(confReference);

        // first retrieve all accounts that we've registered
        List<String> storedAccounts = configurationService.getPropertyNamesByPrefix(sourcePackageName, true);

        // find an account with the corresponding id.
        for (String accountRootPropertyName : storedAccounts) {
            // unregister the account in the configuration service.
            // all the properties must have been registered in the following hierarchy:
            // net.java.sip.communicator.impl.protocol.PROTO_NAME.ACC_ID.PROP_NAME
            String accountUID = configurationService.getString(accountRootPropertyName + "." + ACCOUNT_UID);
            // node idpropname

            if (accountID.getAccountUniqueID().equals(accountUID)) {
                return accountRootPropertyName;
            }
        }
        return null;
    }

    /**
     * Returns the name of the package that we're currently running in (i.e. the name of the
     * package containing the proto factory that extends us).
     *
     * @return a String containing the package name of the concrete factory class that extends us.
     */
    private String getFactoryImplPackageName()
    {
        String className = getClass().getName();
        return className.substring(0, className.lastIndexOf('.'));
    }

    /**
     * Prepares the factory for bundle shutdown.
     */
    public void stop()
    {
        Timber.log(TimberLog.FINER, "Preparing to stop all protocol providers of: %s", this);
        synchronized (registeredAccounts) {
            for (ServiceRegistration<ProtocolProviderService> reg : registeredAccounts.values()) {
                stop(reg);
                reg.unregister();
            }
            registeredAccounts.clear();
        }
    }

    /**
     * Shuts down the {@code ProtocolProviderService} representing an account registered with
     * this factory.
     *
     * @param registeredAccount the {@code ServiceRegistration} of the {@code ProtocolProviderService}
     * representing an account registered with this factory
     */
    protected void stop(ServiceRegistration<ProtocolProviderService> registeredAccount)
    {
        ProtocolProviderService protocolProviderService = getBundleContext().getService(registeredAccount.getReference());
        protocolProviderService.shutdown();
    }

    /**
     * Get the <code>AccountManager</code> of the protocol.
     *
     * @return <code>AccountManager</code> of the protocol
     */
    public AccountManager getAccountManager()
    {
        BundleContext bundleContext = getBundleContext();
        ServiceReference<AccountManager> serviceReference = bundleContext.getServiceReference(AccountManager.class);

        return bundleContext.getService(serviceReference);
    }

    /**
     * Finds registered <code>ProtocolProviderFactory</code> for given <code>protocolName</code>.
     *
     * @param bundleContext the OSGI bundle context that will be used.
     * @param protocolName the protocol name.
     * @return Registered <code>ProtocolProviderFactory</code> for given protocol name or <code>null</code>
     * if no provider was found.
     */
    public static ProtocolProviderFactory getProtocolProviderFactory(BundleContext bundleContext, String protocolName)
    {
        ServiceReference[] serRefs;
        String osgiFilter = "(PROTOCOL_NAME=" + protocolName + ")";
        try {
            serRefs = bundleContext.getServiceReferences(ProtocolProviderFactory.class.getName(), osgiFilter);
        } catch (InvalidSyntaxException ex) {
            Timber.e(ex);
            return null;
        }
        return (ProtocolProviderFactory) bundleContext.getService(serRefs[0]);
    }
}
