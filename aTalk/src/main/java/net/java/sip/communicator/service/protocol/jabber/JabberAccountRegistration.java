/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.protocol.jabber;

import net.java.sip.communicator.service.protocol.AccountID;
import net.java.sip.communicator.service.protocol.AccountManager;
import net.java.sip.communicator.service.protocol.EncodingsRegistrationUtil;
import net.java.sip.communicator.service.protocol.JingleNodeDescriptor;
import net.java.sip.communicator.service.protocol.OperationFailedException;
import net.java.sip.communicator.service.protocol.ProtocolNames;
import net.java.sip.communicator.service.protocol.ProtocolProviderActivator;
import net.java.sip.communicator.service.protocol.ProtocolProviderFactory;
import net.java.sip.communicator.service.protocol.SecurityAccountRegistration;
import net.java.sip.communicator.service.protocol.StunServerDescriptor;
import net.java.sip.communicator.util.ServiceUtils;

import org.apache.commons.lang3.StringUtils;
import org.atalk.android.gui.aTalk;
import org.atalk.service.configuration.ConfigurationService;
import org.atalk.service.neomedia.MediaService;
import org.jxmpp.util.XmppStringUtils;
import org.osgi.framework.BundleContext;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The <code>JabberAccountRegistration</code> is used to store all user input data through the
 * <code>JabberAccountRegistrationWizard</code>.
 *
 * @author Yana Stamcheva
 * @author Boris Grozev
 * @author Eng Chong Meng
 */
public class JabberAccountRegistration extends JabberAccountID implements Serializable
{
    private static final long serialVersionUID = 1L;

    /**
     * The default domain.
     */
    private String defaultUserSuffix;

    /**
     * Indicates if the password should be remembered.
     */
    private boolean rememberPassword = true;

    /**
     * UID of edited account in form: jabber:user@example.com
     */
    private String editedAccUID;

    /**
     * The list of additional STUN servers entered by user.
     */
    private final List<StunServerDescriptor> additionalStunServers = new ArrayList<>();

    /**
     * The list of additional JingleNodes (tracker or relay) entered by user.
     */
    private final List<JingleNodeDescriptor> additionalJingleNodes = new ArrayList<>();

    /**
     * The encodings registration object
     */
    private final EncodingsRegistrationUtil encodingsRegistration = new EncodingsRegistrationUtil();

    /**
     * The security registration object
     */
    private final SecurityAccountRegistration securityRegistration = new SecurityAccountRegistration()
    {
        /**
         * Sets the method used for RTP/SAVP indication.
         */
        @Override
        public void setSavpOption(int savpOption)
        {
            // SAVP option is not useful for XMPP account. Thereby, do nothing.
        }

        /**
         * RTP/SAVP is disabled for Jabber protocol.
         *
         * @return Always <code>ProtocolProviderFactory.SAVP_OFF</code>.
         */
        @Override
        public int getSavpOption()
        {
            return ProtocolProviderFactory.SAVP_OFF;
        }
    };

    /**
     * Initializes a new JabberAccountRegistration.
     */
    public JabberAccountRegistration()
    {
        super(null, new HashMap<>());
    }

    /**
     * Overrides to return UID loaded from edited AccountID.
     *
     * @return UID of edited account e.g. jabber:user@example.com.
     */
    public String getAccountUniqueID()
    {
        return editedAccUID;
    }

    /**
     * Sets the User ID of the jabber registration account e.g. user@example.com.
     *
     * @param userID the identifier of the jabber registration account.
     */
    public void setUserID(String userID)
    {
        setOrRemoveIfEmpty(ProtocolProviderFactory.USER_ID, userID);
    }

    /**
     * {@inheritDoc}
     */
    public String getUserID()
    {
        return getAccountPropertyString(ProtocolProviderFactory.USER_ID);
    }

    /**
     * Returns TRUE if password has to remembered, FALSE otherwise.
     *
     * @return TRUE if password has to remembered, FALSE otherwise
     */
    public boolean isRememberPassword()
    {
        return rememberPassword;
    }

    /**
     * Sets the rememberPassword value of this jabber account registration.
     *
     * @param rememberPassword TRUE if password has to remembered, FALSE otherwise
     */
    public void setRememberPassword(boolean rememberPassword)
    {
        this.rememberPassword = rememberPassword;
    }

    /**
     * Adds the given <code>stunServer</code> to the list of additional stun servers.
     *
     * @param stunServer the <code>StunServer</code> to add
     */
    public void addStunServer(StunServerDescriptor stunServer)
    {
        additionalStunServers.add(stunServer);
    }

    /**
     * Returns the <code>List</code> of all additional stun servers entered by the user. The list is
     * guaranteed not to be <code>null</code>.
     *
     * @return the <code>List</code> of all additional stun servers entered by the user.
     */
    public List<StunServerDescriptor> getAdditionalStunServers()
    {
        return additionalStunServers;
    }

    /**
     * Adds the given <code>node</code> to the list of additional JingleNodes.
     *
     * @param node the <code>node</code> to add
     */
    public void addJingleNodes(JingleNodeDescriptor node)
    {
        additionalJingleNodes.add(node);
    }

    /**
     * Returns the <code>List</code> of all additional stun servers entered by the user. The list is
     * guaranteed not to be <code>null</code>.
     *
     * @return the <code>List</code> of all additional stun servers entered by the user.
     */
    public List<JingleNodeDescriptor> getAdditionalJingleNodes()
    {
        return additionalJingleNodes;
    }

    /**
     * Returns <code>EncodingsRegistrationUtil</code> object which stores encodings configuration.
     *
     * @return <code>EncodingsRegistrationUtil</code> object which stores encodings configuration.
     */
    public EncodingsRegistrationUtil getEncodingsRegistration()
    {
        return encodingsRegistration;
    }

    /**
     * Returns <code>SecurityAccountRegistration</code> object which stores security settings.
     *
     * @return <code>SecurityAccountRegistration</code> object which stores security settings.
     */
    public SecurityAccountRegistration getSecurityRegistration()
    {
        return securityRegistration;
    }

    /**
     * Merge Jabber account configuration held by this registration account (after cleanup and updated with
     * new STUN/JN, Security and Encoding settings into the given <code>accountProperties</code> map.
     *
     * @param passWord the password for this account.
     * @param protocolIconPath the path to protocol icon if used, or <code>null</code> otherwise.
     * @param accountIconPath the path to account icon if used, or <code>null</code> otherwise.
     * @param accountProperties the map used for storing account properties.
     * @throws OperationFailedException if properties are invalid.
     */
    public void storeProperties(ProtocolProviderFactory factory, String passWord, String protocolIconPath,
            String accountIconPath, Boolean isModification, Map<String, String> accountProperties)
            throws OperationFailedException
    {
        // Remove all the old account properties value before populating with modified or new default settings
        mAccountProperties.clear();
        if (rememberPassword) {
            setPassword(passWord);
        }
        else {
            setPassword(null);
        }

        // aTalk STUN/JN implementation can only be added/modified via account modification
        if (isModification) {
            String accountUuid = null;
            // cmeng - editedAccUID contains the last edited account e.g. jabber:xxx@atalk.org.
            if (StringUtils.isNotEmpty(editedAccUID)) {
                AccountManager accManager = ProtocolProviderActivator.getAccountManager();
                accountUuid = accManager.getStoredAccountUUID(factory, editedAccUID);
            }

            if (accountUuid != null) {
                // Must remove all the old STUN/JN settings in database and old copies in accountProperties
                ConfigurationService configSrvc = ProtocolProviderActivator.getConfigurationService();
                List<String> allProperties = configSrvc.getAllPropertyNames(accountUuid);
                for (String property : allProperties) {
                    if (property.startsWith(ProtocolProviderFactory.STUN_PREFIX)
                            || property.startsWith(JingleNodeDescriptor.JN_PREFIX)) {
                        configSrvc.setProperty(accountUuid + "." + property, null);
                    }
                }

                // Also must remove STUN/JN settings from this instance of accountProperties - otherwise remove will not work
                String[] accKeys = accountProperties.keySet().toArray(new String[0]);
                for (String property : accKeys) {
                    if (property.startsWith(ProtocolProviderFactory.STUN_PREFIX)
                            || property.startsWith(JingleNodeDescriptor.JN_PREFIX)) {
                        accountProperties.remove(property);
                    }
                }

                List<StunServerDescriptor> stunServers = getAdditionalStunServers();
                int serverIndex = -1;
                for (StunServerDescriptor stunServer : stunServers) {
                    serverIndex++;
                    stunServer.storeDescriptor(mAccountProperties, ProtocolProviderFactory.STUN_PREFIX + serverIndex);
                }

                List<JingleNodeDescriptor> jnRelays = getAdditionalJingleNodes();
                serverIndex = -1;
                for (JingleNodeDescriptor jnRelay : jnRelays) {
                    serverIndex++;
                    jnRelay.storeDescriptor(mAccountProperties, JingleNodeDescriptor.JN_PREFIX + serverIndex);
                }
            }
        }
        // Must include other jabber account default/modified properties (ZRTP and Encoding) for account saving to DB
        securityRegistration.storeProperties(mAccountProperties);
        if (encodingsRegistration != null)
            encodingsRegistration.storeProperties(mAccountProperties);

        super.storeProperties(protocolIconPath, accountIconPath, accountProperties);
    }

    /**
     * Fills this registration object with configuration properties from given <code>account</code>.
     *
     * @param account the account object that will be used.
     * @param bundleContext the OSGi bundle context required for some operations.
     */
    public void loadAccount(AccountID account, BundleContext bundleContext)
    {
        // cmeng - both same ???
        mergeProperties(account.getAccountProperties(), mAccountProperties);

        String password = ProtocolProviderFactory
                .getProtocolProviderFactory(bundleContext, ProtocolNames.JABBER).loadPassword(account);
        setUserID(account.getUserID());
        editedAccUID = account.getAccountUniqueID();
        setPassword(password);
        // rememberPassword = (password != null);
        rememberPassword = account.isPasswordPersistent();

        // Security properties
        securityRegistration.loadAccount(account);

        // ICE
        this.additionalStunServers.clear();
        for (int i = 0; i < StunServerDescriptor.MAX_STUN_SERVER_COUNT; i++) {
            StunServerDescriptor stunServer = StunServerDescriptor.loadDescriptor(
                    mAccountProperties, ProtocolProviderFactory.STUN_PREFIX + i);

            // If we don't find a stun server with the given index, it means that there're no more
            // servers left in the table so we've nothing more to do here.
            if (stunServer == null)
                break;

            String stunPassword = loadStunPassword(bundleContext, account, ProtocolProviderFactory.STUN_PREFIX + i);
            if (stunPassword != null) {
                stunServer.setPassword(stunPassword);
            }
            addStunServer(stunServer);
        }

        this.additionalJingleNodes.clear();
        for (int i = 0; i < JingleNodeDescriptor.MAX_JN_RELAY_COUNT; i++) {
            JingleNodeDescriptor jn = JingleNodeDescriptor.loadDescriptor(mAccountProperties,
                    JingleNodeDescriptor.JN_PREFIX + i);

            // If we don't find a jingle server with the given index, it means that there is no
            // more servers left in the table so we've nothing more to do here.
            if (jn == null)
                break;
            addJingleNodes(jn);
        }

        // Encodings
        if (!aTalk.disableMediaServiceOnFault)
            encodingsRegistration.loadAccount(account, ServiceUtils.getService(bundleContext, MediaService.class));
    }

    /**
     * Parse the server part from the jabber id and set it to server as default value. If Advanced
     * option is enabled Do nothing.
     *
     * @param userName the full JID that we'd like to parse.
     * @return returns the server part of a full JID
     */
    protected String getServerFromUserName(String userName)
    {
        String newServerAddr = XmppStringUtils.parseDomain(userName);
        if (StringUtils.isNotEmpty(newServerAddr)) {
            return newServerAddr.equals(GOOGLE_USER_SUFFIX) ? GOOGLE_CONNECT_SRV : newServerAddr;
        }
        return null;
    }
}
