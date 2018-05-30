/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.impl.protocol.jabber;

import net.java.sip.communicator.service.protocol.AccountID;
import net.java.sip.communicator.service.protocol.ProtocolNames;
import net.java.sip.communicator.service.protocol.ProtocolProviderFactory;
import net.java.sip.communicator.service.protocol.ProtocolProviderService;

import org.atalk.util.Logger;
import org.jxmpp.jid.EntityBareJid;
import org.jxmpp.jid.impl.JidCreate;
import org.jxmpp.stringprep.XmppStringprepException;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;

import java.util.Hashtable;
import java.util.Map;

/**
 * The Jabber implementation of the ProtocolProviderFactory.
 *
 * @author Damian Minkov
 * @author Eng Chong Meng
 */
public class ProtocolProviderFactoryJabberImpl extends ProtocolProviderFactory
{
    private static final Logger logger = Logger.getLogger(ProtocolProviderFactoryJabberImpl.class);

    /**
     * Indicates if ICE should be used.
     */
    public static final String IS_USE_JINGLE_NODES = "JINGLE_NODES_ENABLED";

    /**
     * Creates an instance of the ProtocolProviderFactoryJabberImpl.
     */
    protected ProtocolProviderFactoryJabberImpl()
    {
        super(JabberActivator.getBundleContext(), ProtocolNames.JABBER);
    }

    /**
     * Overrides the original in order give access to protocol implementation.
     *
     * @param accountID the account identifier.
     */
    @Override
    protected void storeAccount(AccountID accountID)
    {
        super.storeAccount(accountID);
    }

    /**
     * Initializes and creates an account corresponding to the specified accountProperties and
     * registers the resulting ProtocolProvider in the <tt>context</tt> BundleContext parameter.
     * This method has a persistent effect. Once created the resulting account will remain installed
     * until removed through the uninstall account method.
     *
     * @param userID the user identifier for the new account
     * @param accountProperties a set of protocol (or implementation) specific properties defining the new account.
     * @return the AccountID of the newly created account
     */
    @Override
    public AccountID installAccount(String userID, Map<String, String> accountProperties)
    {
        BundleContext context = JabberActivator.getBundleContext();
        if (context == null)
            throw new NullPointerException("The specified BundleContext was null");

        if (userID == null)
            throw new NullPointerException("The specified AccountID was null");

        if (accountProperties == null)
            throw new NullPointerException("The specified property map was null");

        String accountUuid = AccountID.ACCOUNT_UUID_PREFIX + Long.toString(System.currentTimeMillis());
        accountProperties.put(ACCOUNT_UUID, accountUuid);

        String accountUID = getProtocolName() + ":" + userID;
        accountProperties.put(ACCOUNT_UID, accountUID);
        accountProperties.put(USER_ID, userID);

        // Create new accountID
        AccountID accountID = new JabberAccountIDImpl(userID, accountProperties);

        // make sure we haven't seen this account id before.
        if (registeredAccounts.containsKey(accountID))
            throw new IllegalStateException("Attempt to install an existing account: " + userID);

        // first store the account and only then load it as the load generates an osgi event, the
        // osgi event triggers (through the UI) a call to the register() method and it needs to
        // access the configuration service and check for a password.
        this.storeAccount(accountID, false);
        loadAccount(accountID);
        return accountID;
    }

    /**
     * Create an account.
     *
     * @param userID the user ID
     * @param accountProperties the properties associated with the user ID
     * @return new <tt>AccountID</tt>
     */
    @Override
    protected AccountID createAccountID(String userID, Map<String, String> accountProperties)
    {
        return new JabberAccountIDImpl(userID, accountProperties);
    }

    @Override
    protected ProtocolProviderService createService(String userID, AccountID accountID)
    {
        ProtocolProviderServiceJabberImpl service = new ProtocolProviderServiceJabberImpl();
        try {
            EntityBareJid jid = JidCreate.entityBareFrom(userID);
            service.initialize(jid, accountID);
            return service;
        } catch (XmppStringprepException e) {
            logger.error(userID + " is not a valid JID", e);
            return null;
        }
    }

    /**
     * Modify an existing account.
     *
     * @param protocolProvider the <tt>ProtocolProviderService</tt> responsible of the account
     * @param accountProperties modified properties to be set
     */
    @Override
    public void modifyAccount(ProtocolProviderService protocolProvider, Map<String, String> accountProperties)
            throws NullPointerException
    {
        BundleContext context = JabberActivator.getBundleContext();
        if (context == null)
            throw new NullPointerException("The specified BundleContext is null");

        if (protocolProvider == null)
            throw new NullPointerException("The specified Protocol Provider is null");

        // If the given accountID doesn't correspond to an existing account we return.
        JabberAccountIDImpl accountID = (JabberAccountIDImpl) protocolProvider.getAccountID();
        if (!registeredAccounts.containsKey(accountID))
            return;

        ServiceRegistration registration = registeredAccounts.get(accountID);
        // kill the service
        if (registration != null) {
            // unregister provider before removing it.
            try {
                if (protocolProvider.isRegistered()) {
                    protocolProvider.unregister();
                    protocolProvider.shutdown();
                }
            } catch (Throwable e) {
                // don't care as we are modifying and will unregister the service and will register again
            }
            registration.unregister();
        }

        if (accountProperties == null)
            throw new NullPointerException("The specified property map was null");

        accountProperties.put(USER_ID, accountID.getUserID());
        String serverAddress = accountProperties.get(SERVER_ADDRESS);

        if (serverAddress == null)
            throw new NullPointerException("null is not a valid ServerAddress");

        // if server port is null, we will set default value
        if (accountProperties.get(SERVER_PORT) == null) {
            accountProperties.put(SERVER_PORT, "5222");
        }

        if (!accountProperties.containsKey(PROTOCOL))
            accountProperties.put(PROTOCOL, ProtocolNames.JABBER);

        // update the active accountID mAccountProperties with the modified accountProperties
        accountID.setAccountProperties(accountProperties);

        // First store the account and only then load it as the load generates an osgi event, the
        // osgi event triggers (through the UI) a call to the register() method and it needs to
        // access the configuration service and check for a password.
        this.storeAccount(accountID);

        Hashtable<String, String> properties = new Hashtable<>();
        properties.put(PROTOCOL, ProtocolNames.JABBER);
        properties.put(USER_ID, accountID.getUserID());

        try {
            EntityBareJid jid = JidCreate.entityBareFrom(accountID.getUserID());
            ((ProtocolProviderServiceJabberImpl) protocolProvider).initialize(jid, accountID);
        } catch (XmppStringprepException e) {
            logger.error(accountID.getUserID() + " is not a valid JID", e);
            throw new NullPointerException("UserID is not a valid JID");
        }

        // We store again the account in order to store all properties added during the protocol provider initialization.
        this.storeAccount(accountID);
        registration = context.registerService(ProtocolProviderService.class.getName(), protocolProvider, properties);
        registeredAccounts.put(accountID, registration);
    }
}
