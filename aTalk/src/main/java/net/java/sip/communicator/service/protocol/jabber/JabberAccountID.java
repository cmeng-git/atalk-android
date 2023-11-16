/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.protocol.jabber;

import net.java.sip.communicator.service.protocol.AccountID;
import net.java.sip.communicator.service.protocol.JingleNodeDescriptor;
import net.java.sip.communicator.service.protocol.ProtocolNames;
import net.java.sip.communicator.service.protocol.ProtocolProviderActivator;
import net.java.sip.communicator.service.protocol.ProtocolProviderFactory;

import org.atalk.android.gui.account.settings.BoshProxyDialog;
import org.atalk.service.configuration.ConfigurationService;
import org.jivesoftware.smack.util.TLSUtils;
import org.jxmpp.jid.impl.JidCreate;
import org.jxmpp.stringprep.XmppStringprepException;
import org.jxmpp.util.XmppStringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import timber.log.Timber;

/**
 * The Jabber implementation of a sip-communicator AccountID
 *
 * @author Damian Minkov
 * @author Sebastien Vincent
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
public class JabberAccountID extends AccountID
{
    /**
     * Default properties prefix used in atalk-defaults.properties file for Jabber protocol.
     */
    private static final String JBR_DEFAULT_PREFIX = AccountID.DEFAULT_PREFIX + "jabber.";

    /**
     * Uses anonymous XMPP login if set to <code>true</code>.
     */
    public static final String ANONYMOUS_AUTH = "ANONYMOUS_AUTH";

    /**
     * Account suffix for Google service.
     */
    public static final String GOOGLE_USER_SUFFIX = "gmail.com";

    /**
     * XMPP server for Google service.
     */
    public static final String GOOGLE_CONNECT_SRV = "talk.google.com";

    /**
     * The default value of stun server port for jabber accounts.
     */
    public static final String DEFAULT_STUN_PORT = "3478";

    /**
     * Indicates if gmail notifications should be enabled.
     */
    public static final String GMAIL_NOTIFICATIONS_ENABLED = "GMAIL_NOTIFICATIONS_ENABLED";

    /**
     * Always call with gtalk property.
     *
     * It is used to bypass capabilities checks: some software do not advertise GTalk support (but
     * they support it).
     */
    public static final String BYPASS_GTALK_CAPABILITIES = "BYPASS_GTALK_CAPABILITIES";

    /**
     * Indicates if Google Contacts should be enabled.
     */
    public static final String GOOGLE_CONTACTS_ENABLED = "GOOGLE_CONTACTS_ENABLED";

    /**
     * Domain name that will bypass GTalk caps.
     */
    public static final String TELEPHONY_BYPASS_GTALK_CAPS = "TELEPHONY_BYPASS_GTALK_CAPS";
    /**
     * The override domain for phone call.
     *
     * If Jabber account is able to call PSTN number and if domain name of the switch is different
     * than the domain of the account (gw.domain.org vs domain.org), you can use this property to
     * set the switch domain.
     */
    public static final String OVERRIDE_PHONE_SUFFIX = "OVERRIDE_PHONE_SUFFIX";

    /**
     * Creates an account id from the specified id and account properties.
     *
     * @param userId the id identifying this account i.e hawk@example.org
     * @param accountProperties any other properties necessary for the account.
     */
    public JabberAccountID(String userId, Map<String, String> accountProperties)
            throws IllegalArgumentException
    {
        super(userId, accountProperties, ProtocolNames.JABBER, getServiceName(accountProperties));

        // id can be null on initial startup
        if (userId != null) {
            try {
                userBareJid = JidCreate.bareFrom(userId);
            } catch (XmppStringprepException e) {
                Timber.e("Unable to create BareJid for user account: %s", userId);
                throw new IllegalArgumentException("User ID is not a valid xmpp BareJid");
            }
        }
    }

    /**
     * Default constructor for serialization purposes. Do not removed - required by serialization
     */
    public JabberAccountID()
    {
        this(null, new HashMap<>());
    }

    /**
     * change to the new userId for the current AccountID. Mainly use of userId change in account settings
     * Need to change the userID, userBareJid, accountUID; and mAccountProperties.USER_ID if Account ID changed
     *
     * @param userId new userId
     */
    public void updateJabberAccountID(String userId)
    {
        if (userId != null) {
            this.mUserID = userId;
            this.accountUID = getProtocolName() + ":" + mUserID;
            mAccountProperties.put(USER_ID, userId);
            try {
                userBareJid = JidCreate.bareFrom(userId);
            } catch (XmppStringprepException e) {
                Timber.e("Unable to create BareJid for user account: %s", userId);
            }
        }
    }

    /**
     * Returns the BOSH URL which should be used to connect to the XMPP server.
     * The value must not be null if BOSH transport is enabled.
     *
     * @return a <code>String</code> with the URL which should be used for BOSH transport
     */
    public String getBoshUrl()
    {
        return getAccountPropertyString(ProtocolProviderFactory.BOSH_URL);
    }

    /**
     * Sets new URL which should be used for the BOSH transport.
     *
     * @param boshPath a <code>String</code> with the new BOSH URL
     */
    public void setBoshUrl(String boshPath)
    {
        putAccountProperty(ProtocolProviderFactory.BOSH_URL, boshPath);
    }

    /**
     * Returns true is Type is BOSH else false.
     *
     * @return <code>true</code> if (Type == BOSH) else false
     */
    public boolean isBOSHEnable()
    {
        return BoshProxyDialog.BOSH.equals(getAccountPropertyString(ProtocolProviderFactory.PROXY_TYPE));
    }

    /**
     * Indicates if HTTP proxy should be used for with BOSH protocol. Only HTTP proxy is supported for BOSH
     *
     * @return <code>true</code> if Bosh Http Proxy should be used, otherwise returns <code>false</code>
     */
    public boolean isBoshHttpProxyEnabled()
    {
        return getAccountPropertyBoolean(ProtocolProviderFactory.BOSH_PROXY_HTTP_ENABLED, false);
    }

    /**
     * set HTTP proxy should be used for with BOSH protocol.
     *
     * @param isBoshHttp <code>true to enable HTTP proxy for BOSH</code>
     */
    public void setBoshHttpProxyEnabled(boolean isBoshHttp)
    {
        putAccountProperty(ProtocolProviderFactory.BOSH_PROXY_HTTP_ENABLED, isBoshHttp);
    }

    /**
     * Returns the override phone suffix.
     *
     * @return the phone suffix
     */
    public String getOverridePhoneSuffix()
    {
        return getAccountPropertyString(OVERRIDE_PHONE_SUFFIX);
    }

    /**
     * Returns the actual name of this protocol: {@link ProtocolNames#JABBER}.
     *
     * @return Jabber: the name of this protocol.
     */
    public String getSystemProtocolName()
    {
        return ProtocolNames.JABBER;
    }

    /**
     * Returns the alwaysCallWithGtalk value.
     *
     * @return the alwaysCallWithGtalk value
     */
    public boolean getBypassGtalkCaps()
    {
        return getAccountPropertyBoolean(BYPASS_GTALK_CAPABILITIES, false);
    }

    /**
     * Returns telephony domain that bypass GTalk caps.
     *
     * @return telephony domain
     */
    public String getTelephonyDomainBypassCaps()
    {
        return getAccountPropertyString(TELEPHONY_BYPASS_GTALK_CAPS);
    }

    /**
     * Indicates whether anonymous authorization method is used by this account.
     *
     * @return <code>true</code> if anonymous login is enabled on this account.
     */
    public boolean isAnonymousAuthUsed()
    {
        return getAccountPropertyBoolean(ANONYMOUS_AUTH, false);
    }

    /**
     * Gets if Jingle is disabled for this account.
     *
     * @return True if jingle is disabled for this account. False otherwise.
     */
    public boolean isJingleDisabled()
    {
        return getAccountPropertyBoolean(ProtocolProviderFactory.IS_CALLING_DISABLED_FOR_ACCOUNT, false);
    }

    /**
     * Determines whether SIP Communicator should be querying Gmail servers for unread mail messages.
     *
     * @return <code>true</code> if we are to enable Gmail notifications and <code>false</code> otherwise.
     */
    public boolean isGmailNotificationEnabled()
    {
        return getAccountPropertyBoolean(GMAIL_NOTIFICATIONS_ENABLED, false);
    }

    /**
     * Determines whether SIP Communicator should use Google Contacts as ContactSource
     *
     * @return <code>true</code> if we are to enable Google Contacts and <code>false</code> otherwise.
     */
    public boolean isGoogleContactsEnabled()
    {
        return getAccountPropertyBoolean(GOOGLE_CONTACTS_ENABLED, true);
    }

    /**
     * Enables anonymous authorization mode on this XMPP account.
     *
     * @param useAnonymousAuth <code>true</code> to use anonymous login.
     */
    public void setUseAnonymousAuth(boolean useAnonymousAuth)
    {
        putAccountProperty(ANONYMOUS_AUTH, useAnonymousAuth);
    }

    /**
     * Sets the override value of the phone suffix.
     *
     * @param phoneSuffix the phone name suffix (the domain name after the @ sign)
     */
    public void setOverridePhoneSuffix(String phoneSuffix)
    {
        setOrRemoveIfEmpty(OVERRIDE_PHONE_SUFFIX, phoneSuffix);
    }

    /**
     * Sets value for alwaysCallWithGtalk.
     *
     * @param bypassGtalkCaps true to enable, false otherwise
     */
    public void setBypassGtalkCaps(boolean bypassGtalkCaps)
    {
        putAccountProperty(BYPASS_GTALK_CAPABILITIES, bypassGtalkCaps);
    }

    /**
     * Sets telephony domain that bypass GTalk caps.
     *
     * @param text telephony domain to set
     */
    public void setTelephonyDomainBypassCaps(String text)
    {
        setOrRemoveIfEmpty(TELEPHONY_BYPASS_GTALK_CAPS, text);
    }

    /**
     * Sets if Jingle is disabled for this account.
     *
     * @param disabled True if jingle is disabled for this account. False otherwise.
     */
    public void setDisableJingle(boolean disabled)
    {
        putAccountProperty(ProtocolProviderFactory.IS_CALLING_DISABLED_FOR_ACCOUNT, disabled);
    }

    /**
     * Specifies whether SIP Communicator should be querying Gmail servers for unread mail messages.
     *
     * @param enabled <code>true</code> if we are to enable Gmail notification and <code>false</code> otherwise.
     */
    public void setGmailNotificationEnabled(boolean enabled)
    {
        putAccountProperty(GMAIL_NOTIFICATIONS_ENABLED, enabled);
    }

    /**
     * Specifies whether SIP Communicator should use Google Contacts as ContactSource.
     *
     * @param enabled <code>true</code> if we are to enable Google Contacts and <code>false</code> otherwise.
     */
    public void setGoogleContactsEnabled(boolean enabled)
    {
        putAccountProperty(GOOGLE_CONTACTS_ENABLED, enabled);
    }

    /**
     * Returns the resource.
     *
     * @return the resource
     */
    public String getResource()
    {
        return getAccountPropertyString(ProtocolProviderFactory.RESOURCE);
    }

    /**
     * Sets the resource.
     *
     * @param resource the resource for the jabber account
     */
    public void setResource(String resource)
    {
        putAccountProperty(ProtocolProviderFactory.RESOURCE, resource);
    }

    /**
     * Returns the priority property.
     *
     * @return priority
     */
    public int getPriority()
    {
        return getAccountPropertyInt(ProtocolProviderFactory.RESOURCE_PRIORITY, 30);
    }

    /**
     * Sets the priority property.
     *
     * @param priority the priority to set
     */
    public void setPriority(int priority)
    {
        putAccountProperty(ProtocolProviderFactory.RESOURCE_PRIORITY, priority);
    }

    /**
     * Indicates if ice should be used for this account.
     *
     * @return <code>true</code> if ICE should be used for this account, otherwise returns <code>false</code>
     */
    public boolean isUseIce()
    {
        return getAccountPropertyBoolean(ProtocolProviderFactory.IS_USE_ICE, true);
    }

    /**
     * Sets the <code>useIce</code> property.
     *
     * @param isUseIce <code>true</code> to indicate that ICE should be used for this account, <code>false</code> -
     * otherwise.
     */
    public void setUseIce(boolean isUseIce)
    {
        putAccountProperty(ProtocolProviderFactory.IS_USE_ICE, isUseIce);
    }

    /**
     * Indicates if the stun server should be automatically discovered.
     *
     * @return <code>true</code> if the stun server should be automatically discovered,
     * otherwise false if serverOverride is enabled; serviceDomain is likely not reachable.
     */
    public boolean isAutoDiscoverStun()
    {
        return getAccountPropertyBoolean(ProtocolProviderFactory.AUTO_DISCOVER_STUN, !isServerOverridden());
    }

    /**
     * Sets the <code>autoDiscoverStun</code> property.
     *
     * @param isAutoDiscover <code>true</code> to indicate that stun server should be auto-discovered, <code>false</code> -
     * otherwise.
     */
    public void setAutoDiscoverStun(boolean isAutoDiscover)
    {
        putAccountProperty(ProtocolProviderFactory.AUTO_DISCOVER_STUN, isAutoDiscover);
    }

    /**
     * Sets the <code>useDefaultStunServer</code> property.
     *
     * @param isUseDefaultStunServer <code>true</code> to indicate that default stun server should be used if no others are
     * available, <code>false</code> otherwise.
     */
    public void setUseDefaultStunServer(boolean isUseDefaultStunServer)
    {
        putAccountProperty(ProtocolProviderFactory.USE_DEFAULT_STUN_SERVER, isUseDefaultStunServer);
    }

    /**
     * Sets the <code>autoDiscoverJingleNodes</code> property.
     *
     * @param isAutoDiscoverJingleNodes <code>true</code> to indicate that relay server should be auto-discovered,
     * <code>false</code> - otherwise.
     */
    public void setAutoDiscoverJingleNodes(boolean isAutoDiscoverJingleNodes)
    {
        putAccountProperty(ProtocolProviderFactory.AUTO_DISCOVER_JINGLE_NODES, isAutoDiscoverJingleNodes);
    }

    /**
     * Indicates if the JingleNodes relay server should be automatically discovered.
     *
     * @return <code>true</code> if the relay server should be automatically discovered, otherwise returns <code>false</code>.
     */
    public boolean isAutoDiscoverJingleNodes()
    {
        return getAccountPropertyBoolean(ProtocolProviderFactory.AUTO_DISCOVER_JINGLE_NODES, true);
    }

    /**
     * Sets the <code>useJingleNodes</code> property.
     *
     * @param isUseJingleNodes <code>true</code> to indicate that Jingle Nodes should be used for this account,
     * <code>false</code> - otherwise.
     */
    public void setUseJingleNodes(boolean isUseJingleNodes)
    {
        putAccountProperty(ProtocolProviderFactory.IS_USE_JINGLE_NODES, isUseJingleNodes);
    }

    /**
     * Indicates if JingleNodes relay should be used.
     *
     * @return <code>true</code> if JingleNodes should be used, <code>false</code> otherwise
     */
    public boolean isUseJingleNodes()
    {
        return getAccountPropertyBoolean(ProtocolProviderFactory.IS_USE_JINGLE_NODES, true);
    }

    /**
     * Indicates if UPnP should be used for this account.
     *
     * @return <code>true</code> if UPnP should be used for this account, otherwise returns <code>false</code>
     */
    public boolean isUseUPNP()
    {
        return getAccountPropertyBoolean(ProtocolProviderFactory.IS_USE_UPNP, true);
    }

    /**
     * Sets the <code>useUPNP</code> property.
     *
     * @param isUseUPNP <code>true</code> to indicate that UPnP should be used for this account, <code>false</code> - otherwise.
     */
    public void setUseUPNP(boolean isUseUPNP)
    {
        putAccountProperty(ProtocolProviderFactory.IS_USE_UPNP, isUseUPNP);
    }

    /**
     * Minimum TLS protocol version used for TLS connections.
     *
     * @return minimum TLS protocol version. Default TLS 1.2
     */
    public String getMinimumTLSversion()
    {
        return getAccountPropertyString(ProtocolProviderFactory.MINUMUM_TLS_VERSION, TLSUtils.PROTO_TLSV1_2);
    }

    /**
     * Sets the <code>minimumTLSversion</code> property.
     *
     * @param minimumTLSversion minimum TLS protocol version
     */
    public void setMinimumTLSversion(String minimumTLSversion)
    {
        putAccountProperty(ProtocolProviderFactory.MINUMUM_TLS_VERSION, minimumTLSversion);
    }

    /**
     * Indicates if non-TLS is allowed for this account
     *
     * @return <code>true</code> if non-TLS is allowed for this account, otherwise returns <code>false</code>
     */
    public boolean isAllowNonSecure()
    {
        return getAccountPropertyBoolean(ProtocolProviderFactory.IS_ALLOW_NON_SECURE, false);
    }

    /**
     * Sets the <code>isAllowNonSecure</code> property.
     *
     * @param isAllowNonSecure <code>true</code> to indicate that non-TLS is allowed for this account, <code>false</code> otherwise.
     */
    public void setAllowNonSecure(boolean isAllowNonSecure)
    {
        putAccountProperty(ProtocolProviderFactory.IS_ALLOW_NON_SECURE, isAllowNonSecure);
    }

    /**
     * Indicates if message carbons are allowed for this account
     *
     * @return <code>true</code> if message carbons are allowed for this account, otherwise returns <code>false</code>
     */
    public boolean isCarbonDisabled()
    {
        return getAccountPropertyBoolean(ProtocolProviderFactory.IS_CARBON_DISABLED, false);
    }

    /**
     * Sets the <code>IS_CARBON_DISABLED</code> property.
     *
     * @param isCarbonEnabled <code>true</code> to indicate that message carbons are allowed for this account,
     * <code>false</code> otherwise.
     */
    public void setDisableCarbon(boolean isCarbonEnabled)
    {
        putAccountProperty(ProtocolProviderFactory.IS_CARBON_DISABLED, isCarbonEnabled);
    }

    /**
     * Is resource auto generate enabled.
     *
     * @return true if resource is auto generated
     */
    public boolean isResourceAutoGenerated()
    {
        return getAccountPropertyBoolean(ProtocolProviderFactory.AUTO_GENERATE_RESOURCE, true);
    }

    /**
     * Set whether resource auto generation is enabled.
     *
     * @param resourceAutoGenerated <code>true</code> to indicate that the resource is to be auto generated,
     * <code>false</code> otherwise.
     */
    public void setResourceAutoGenerated(boolean resourceAutoGenerated)
    {
        putAccountProperty(ProtocolProviderFactory.AUTO_GENERATE_RESOURCE, resourceAutoGenerated);
    }

    /**
     * Returns the default sms server.
     *
     * @return the account default sms server
     */
    public String getSmsServerAddress()
    {
        return getAccountPropertyString(ProtocolProviderFactory.SMS_SERVER_ADDRESS);
    }

    /**
     * Sets the default sms server.
     *
     * @param serverAddress the sms server to set as default
     */
    public void setSmsServerAddress(String serverAddress)
    {
        setOrRemoveIfEmpty(ProtocolProviderFactory.SMS_SERVER_ADDRESS, serverAddress);
    }

    /**
     * Returns the service name - the virtualHost (server) we are logging to if it is null which is
     * not supposed to be - we return for compatibility the string we used in the first release for
     * creating AccountID (Using this string is wrong, but used for compatibility for now)
     *
     * @param accountProperties Map
     * @return String
     */
    private static String getServiceName(Map<String, String> accountProperties)
    {
        // return accountProperties.get(ProtocolProviderFactory.SERVER_ADDRESS);
        String jid = accountProperties.get(ProtocolProviderFactory.USER_ID);
        if (jid != null)
            return XmppStringUtils.parseDomain(jid);
        else
            return null;
    }

    /**
     * Returns the list of JingleNodes trackers/relays that this account is currently configured to use.
     *
     * @return the list of JingleNodes trackers/relays that this account is currently configured to use.
     */
    public List<JingleNodeDescriptor> getJingleNodes()
    {
        Map<String, String> accountProperties = getAccountProperties();
        List<JingleNodeDescriptor> serList = new ArrayList<>();

        for (int i = 0; i < JingleNodeDescriptor.MAX_JN_RELAY_COUNT; i++) {
            JingleNodeDescriptor node = JingleNodeDescriptor.loadDescriptor(accountProperties,
                    JingleNodeDescriptor.JN_PREFIX + i);

            // If we don't find a relay server with the given index, it means that there're no
            // more servers left in the table so we've nothing more to do here.
            if (node == null)
                break;
            serList.add(node);
        }
        return serList;
    }

    /**
     * Determines whether this account's provider is supposed to auto discover JingleNodes relay.
     *
     * @return <code>true</code> if this provider would need to discover JingleNodes relay,
     * <code>false</code> otherwise
     */
    public boolean isJingleNodesAutoDiscoveryEnabled()
    {
        return getAccountPropertyBoolean(ProtocolProviderFactory.AUTO_DISCOVER_JINGLE_NODES, true);
    }

    /**
     * Determines whether this account's provider is supposed to auto discover JingleNodes relay by
     * searching our contacts.
     *
     * @return <code>true</code> if this provider would need to discover JingleNodes relay by searching
     * buddies, <code>false</code> otherwise
     */
    public boolean isJingleNodesSearchBuddiesEnabled()
    {
        return getAccountPropertyBoolean(ProtocolProviderFactory.JINGLE_NODES_SEARCH_BUDDIES, false);
    }

    /**
     * Determines whether this account's provider uses JingleNodes relay (if available).
     *
     * @return <code>true</code> if this provider would use JingleNodes relay (if available),
     * <code>false</code> otherwise
     */
    public boolean isJingleNodesRelayEnabled()
    {
        return getAccountPropertyBoolean(ProtocolProviderFactory.IS_USE_JINGLE_NODES, true);
    }

    /**
     * {@inheritDoc}
     */
    protected String getDefaultString(String key)
    {
        return JabberAccountID.getDefaultStr(key);
    }

    /**
     * Gets default property value for given <code>key</code>.
     *
     * @param key the property key
     * @return default property value for given<code>key</code>
     */
    public static String getDefaultStr(String key)
    {
        String value = null;
        ConfigurationService configService = ProtocolProviderActivator.getConfigurationService();
        if (configService != null)
            value = configService.getString(JBR_DEFAULT_PREFIX + key);

        return (value == null) ? AccountID.getDefaultStr(key) : value;
    }

    /**
     * Gets default boolean property value for given <code>key</code>.
     *
     * @param key the property key
     * @return default property value for given<code>key</code>
     */
    public static boolean getDefaultBool(String key)
    {
        return Boolean.parseBoolean(getDefaultStr(key));
    }
}
