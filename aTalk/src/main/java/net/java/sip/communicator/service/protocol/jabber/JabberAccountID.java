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
import net.java.sip.communicator.util.Logger;

import org.atalk.service.configuration.ConfigurationService;
import org.jxmpp.jid.BareJid;
import org.jxmpp.jid.impl.JidCreate;
import org.jxmpp.stringprep.XmppStringprepException;
import org.jxmpp.util.XmppStringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
     * The <tt>Logger</tt> used by the <tt>AccountID</tt> class and its instances for logging output.
     */
    private static final Logger logger = Logger.getLogger(JabberAccountID.class);

    /**
     * Default properties prefix used in atalk-defaults.properties file for Jabber protocol.
     */
    private static final String JBR_DEFAULT_PREFIX = AccountID.DEFAULT_PREFIX + "jabber.";

    /**
     * Uses anonymous XMPP login if set to <tt>true</tt>.
     */
    public static final String ANONYMOUS_AUTH = "ANONYMOUS_AUTH";

    /**
     * Configures the URL which is to be used with BOSH transport. If the value
     * is <tt>null</tt> or empty then the TCP transport will be used instead.
     */
    public static final String BOSH_URL = "BOSH_URL";

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
     * @param id the id identifying this account i.e hawk@example.org
     * @param accountProperties any other properties necessary for the account.
     */
    public JabberAccountID(String id, Map<String, String> accountProperties)
    {
        super(id, accountProperties, ProtocolNames.JABBER, getServiceName(accountProperties));

        // id can be null on initial startup
        if (id != null) {
            try {
                BareJid bareJid = JidCreate.bareFrom(id);
                setBareJid(bareJid);
            } catch (XmppStringprepException e) {
                logger.error("Unable to create BareJid for user account: " + id);
            }
        }
    }

    /**
     * Default constructor for serialization purposes.
     */
    public JabberAccountID()
    {
        this(null, new HashMap<String, String>());
    }

    /**
     * Returns the BOSH URL which should be used to connect to the XMPP server.
     * If the value is set then BOSH transport instead of TCP will be used.
     *
     * @return a <tt>String</tt> with the URL which should be used for BOSH
     * transport or <tt>null</tt> if disabled.
     */
    public String getBoshUrl()
    {
        return getAccountPropertyString(BOSH_URL);
    }

    /**
     * Sets new URL which should be used for the BOSH transport.
     *
     * @param boshPath a <tt>String</tt> with the new BOSH URL or <tt>null</tt>
     * to disable BOSH.
     */
    public void setBoshUrl(String boshPath)
    {
        putAccountProperty(BOSH_URL, boshPath);
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
     * @return <tt>true</tt> if anonymous login is enabled on this account.
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
     * Determines whether SIP Communicator should be querying Gmail servers for unread mail
     * messages.
     *
     * @return <tt>true</tt> if we are to enable Gmail notifications and <tt>false</tt> otherwise.
     */
    public boolean isGmailNotificationEnabled()
    {
        return getAccountPropertyBoolean(GMAIL_NOTIFICATIONS_ENABLED, false);
    }

    /**
     * Determines whether SIP Communicator should use Google Contacts as ContactSource
     *
     * @return <tt>true</tt> if we are to enable Google Contacts and <tt>false</tt> otherwise.
     */
    public boolean isGoogleContactsEnabled()
    {
        return getAccountPropertyBoolean(GOOGLE_CONTACTS_ENABLED, true);
    }

    /**
     * Enables anonymous authorization mode on this XMPP account.
     *
     * @param useAnonymousAuth <tt>true</tt> to use anonymous login.
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
     * @param enabled <tt>true</tt> if we are to enable Gmail notification and <tt>false</tt> otherwise.
     */
    public void setGmailNotificationEnabled(boolean enabled)
    {
        putAccountProperty(GMAIL_NOTIFICATIONS_ENABLED, enabled);
    }

    /**
     * Specifies whether SIP Communicator should use Google Contacts as ContactSource.
     *
     * @param enabled <tt>true</tt> if we are to enable Google Contacts and <tt>false</tt> otherwise.
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
     * @return <tt>true</tt> if ICE should be used for this account, otherwise returns
     * <tt>false</tt>
     */
    public boolean isUseIce()
    {
        return getAccountPropertyBoolean(ProtocolProviderFactory.IS_USE_ICE, true);
    }

    /**
     * Sets the <tt>useIce</tt> property.
     *
     * @param isUseIce <tt>true</tt> to indicate that ICE should be used for this account, <tt>false</tt> -
     * otherwise.
     */
    public void setUseIce(boolean isUseIce)
    {
        putAccountProperty(ProtocolProviderFactory.IS_USE_ICE, isUseIce);
    }

    /**
     * Indicates if the stun server should be automatically discovered.
     *
     * @return <tt>true</tt> if the stun server should be automatically discovered, otherwise
     * returns <tt>false</tt>.
     */
    public boolean isAutoDiscoverStun()
    {
        return getAccountPropertyBoolean(ProtocolProviderFactory.AUTO_DISCOVER_STUN, true);
    }

    /**
     * Sets the <tt>autoDiscoverStun</tt> property.
     *
     * @param autoDiscoverStun <tt>true</tt> to indicate that stun server should be auto-discovered, <tt>false</tt> -
     * otherwise.
     */
    public void setAutoDiscoverStun(boolean autoDiscoverStun)
    {
        putAccountProperty(ProtocolProviderFactory.AUTO_DISCOVER_STUN, autoDiscoverStun);
    }

    /**
     * Sets the <tt>useDefaultStunServer</tt> property.
     *
     * @param useDefaultStunServer <tt>true</tt> to indicate that default stun server should be used if no others are
     * available, <tt>false</tt> otherwise.
     */
    public void setUseDefaultStunServer(boolean useDefaultStunServer)
    {
        putAccountProperty(ProtocolProviderFactory.USE_DEFAULT_STUN_SERVER, useDefaultStunServer);
    }

    /**
     * Sets the <tt>autoDiscoverJingleNodes</tt> property.
     *
     * @param autoDiscoverJingleNodes <tt>true</tt> to indicate that relay server should be auto-discovered,
     * <tt>false</tt> - otherwise.
     */
    public void setAutoDiscoverJingleNodes(boolean autoDiscoverJingleNodes)
    {
        putAccountProperty(ProtocolProviderFactory.AUTO_DISCOVER_JINGLE_NODES, autoDiscoverJingleNodes);
    }

    /**
     * Indicates if the JingleNodes relay server should be automatically discovered.
     *
     * @return <tt>true</tt> if the relay server should be automatically discovered, otherwise
     * returns <tt>false</tt>.
     */
    public boolean isAutoDiscoverJingleNodes()
    {
        return getAccountPropertyBoolean(ProtocolProviderFactory.AUTO_DISCOVER_JINGLE_NODES, true);
    }

    /**
     * Sets the <tt>useJingleNodes</tt> property.
     *
     * @param isUseJingleNodes <tt>true</tt> to indicate that Jingle Nodes should be used for this account,
     * <tt>false</tt> - otherwise.
     */
    public void setUseJingleNodes(boolean isUseJingleNodes)
    {
        putAccountProperty(ProtocolProviderFactory.IS_USE_JINGLE_NODES, isUseJingleNodes);
    }

    /**
     * Indicates if JingleNodes relay should be used.
     *
     * @return <tt>true</tt> if JingleNodes should be used, <tt>false</tt> otherwise
     */
    public boolean isUseJingleNodes()
    {
        return getAccountPropertyBoolean(ProtocolProviderFactory.IS_USE_JINGLE_NODES, true);
    }

    /**
     * Indicates if UPnP should be used for this account.
     *
     * @return <tt>true</tt> if UPnP should be used for this account, otherwise returns
     * <tt>false</tt>
     */
    public boolean isUseUPNP()
    {
        return getAccountPropertyBoolean(ProtocolProviderFactory.IS_USE_UPNP, true);
    }

    /**
     * Sets the <tt>useUPNP</tt> property.
     *
     * @param isUseUPNP <tt>true</tt> to indicate that UPnP should be used for this account, <tt>false</tt> -
     * otherwise.
     */
    public void setUseUPNP(boolean isUseUPNP)
    {
        putAccountProperty(ProtocolProviderFactory.IS_USE_UPNP, isUseUPNP);
    }

    /**
     * Indicates if non-TLS is allowed for this account
     *
     * @return <tt>true</tt> if non-TLS is allowed for this account, otherwise returns
     * <tt>false</tt>
     */
    public boolean isAllowNonSecure()
    {
        return getAccountPropertyBoolean(ProtocolProviderFactory.IS_ALLOW_NON_SECURE, false);
    }

    /**
     * Sets the <tt>isAllowNonSecure</tt> property.
     *
     * @param isAllowNonSecure <tt>true</tt> to indicate that non-TLS is allowed for this account, <tt>false</tt> -
     * otherwise.
     */
    public void setAllowNonSecure(boolean isAllowNonSecure)
    {
        putAccountProperty(ProtocolProviderFactory.IS_ALLOW_NON_SECURE, isAllowNonSecure);
    }

    /**
     * Indicates if message carbons are allowed for this account
     *
     * @return <tt>true</tt> if message carbons are allowed for this account, otherwise returns
     * <tt>false</tt>
     */
    public boolean isCarbonDisabled()
    {
        return getAccountPropertyBoolean(ProtocolProviderFactory.IS_CARBON_DISABLED, false);
    }

    /**
     * Sets the <tt>IS_CARBON_DISABLED</tt> property.
     *
     * @param isCarbonEnabled <tt>true</tt> to indicate that message carbons are allowed for this account,
     * <tt>false</tt> - otherwise.
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
     * @param resourceAutoGenerated <tt>true</tt> to indicate that the resource is to be auto generated,
     * <tt>false</tt> - otherwise.
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
     * Returns the list of JingleNodes trackers/relays that this account is currently configured to
     * use.
     *
     * @return the list of JingleNodes trackers/relays that this account is currently configured to
     * use.
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
     * @return <tt>true</tt> if this provider would need to discover JingleNodes relay,
     * <tt>false</tt> otherwise
     */
    public boolean isJingleNodesAutoDiscoveryEnabled()
    {
        return getAccountPropertyBoolean(ProtocolProviderFactory.AUTO_DISCOVER_JINGLE_NODES, true);
    }

    /**
     * Determines whether this account's provider is supposed to auto discover JingleNodes relay by
     * searching our contacts.
     *
     * @return <tt>true</tt> if this provider would need to discover JingleNodes relay by searching
     * buddies, <tt>false</tt> otherwise
     */
    public boolean isJingleNodesSearchBuddiesEnabled()
    {
        return getAccountPropertyBoolean(ProtocolProviderFactory.JINGLE_NODES_SEARCH_BUDDIES, false);
    }

    /**
     * Determines whether this account's provider uses JingleNodes relay (if available).
     *
     * @return <tt>true</tt> if this provider would use JingleNodes relay (if available),
     * <tt>false</tt> otherwise
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
     * Gets default property value for given <tt>key</tt>.
     *
     * @param key the property key
     * @return default property value for given<tt>key</tt>
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
     * Gets default boolean property value for given <tt>key</tt>.
     *
     * @param key the property key
     * @return default property value for given<tt>key</tt>
     */
    public static boolean getDefaultBool(String key)
    {
        return Boolean.parseBoolean(getDefaultStr(key));
    }
}
