/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.impl.protocol.jabber;

import android.text.TextUtils;

import net.java.sip.communicator.service.protocol.*;
import net.java.sip.communicator.service.protocol.jabber.JabberAccountID;

import org.jxmpp.jid.EntityBareJid;
import org.jxmpp.jid.impl.JidCreate;
import org.jxmpp.stringprep.XmppStringprepException;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;

import java.util.Map;

import timber.log.Timber;

/**
 * The Jabber implementation of the ProtocolProviderFactory.
 *
 * @author Damian Minkov
 * @author Eng Chong Meng
 */
public class ProtocolProviderFactoryJabberImpl extends ProtocolProviderFactory
{
    /**
     * Indicates if ICE should be used.
     */
    public static final String IS_USE_JINGLE_NODES = "JINGLE_NODES_ENABLED";

    /**
     * Creates an instance of the ProtocolProviderFactoryJabberImpl.
     */
    public ProtocolProviderFactoryJabberImpl()
    {
        super(JabberActivator.getBundleContext(), ProtocolNames.JABBER);
    }

    /**
     * Overrides the original in order give access to specific protocol implementation.
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
            throws IllegalArgumentException, NullPointerException
    {
        BundleContext context = JabberActivator.getBundleContext();
        if (context == null)
            throw new NullPointerException("The specified BundleContext is null");

        if (userID == null)
            throw new IllegalArgumentException("The specified AccountID is null");

        if (accountProperties == null)
            throw new IllegalArgumentException("The specified property map is null");

        // Generate a new accountUuid for new account creation
        String accountUuid = AccountID.ACCOUNT_UUID_PREFIX + System.currentTimeMillis();
        accountProperties.put(ACCOUNT_UUID, accountUuid);

        String accountUID = getProtocolName() + ":" + userID;
        accountProperties.put(ACCOUNT_UID, accountUID);
        accountProperties.put(USER_ID, userID);

        // Create new accountID
        AccountID accountID = new JabberAccountIDImpl(userID, accountProperties);

        // make sure we haven't seen this accountID before.
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
            service.initialize(jid, (JabberAccountID) accountID);
            return service;
        } catch (XmppStringprepException e) {
            Timber.e(e, "%s is not a valid JID", userID);
        }
        return null;
    }

    /**
     * Modify an existing account.
     *
     * @param protocolProvider the <tt>ProtocolProviderService</tt> responsible of the account
     * @param accountProperties modified properties to be set
     */
    @Override
    public void modifyAccount(ProtocolProviderService protocolProvider, Map<String, String> accountProperties)
            throws IllegalArgumentException, NullPointerException
    {
        BundleContext context = JabberActivator.getBundleContext();
        if (context == null)
            throw new NullPointerException("The specified BundleContext is null");

        if (protocolProvider == null)
            throw new IllegalArgumentException("The specified Protocol Provider is null");

        // If the given accountID must be an existing account to modify, else return.
        JabberAccountIDImpl accountID = (JabberAccountIDImpl) protocolProvider.getAccountID();
        if (!registeredAccounts.containsKey(accountID))
            return;

        /*
         * Need to kill the protocolProvider service prior to making and account properties updates
         */
        ServiceRegistration registration = registeredAccounts.get(accountID);
        if (registration != null) {
            try {
                // unregister provider before removing it.
                if (protocolProvider.isRegistered()) {
                    protocolProvider.unregister();
                    protocolProvider.shutdown();
                }
            } catch (Throwable ignore) {
                // don't care as we are modifying and will unregister the service and will register again
            }
            registration.unregister();
        }

        if (accountProperties == null)
            throw new IllegalArgumentException("The specified property map is null");

        if (!accountProperties.containsKey(PROTOCOL))
            accountProperties.put(PROTOCOL, ProtocolNames.JABBER);

        // If user has modified the UserId, then update the accountID parameters (userID, userBareJid and accountUID)
        // Must unload old account if account ID has changed
        String userId = accountProperties.get(USER_ID);
        if (!TextUtils.isEmpty(userId) && !accountID.getAccountJid().equals(userId)) {
            unloadAccount(accountID);
            accountID.updateJabberAccountID(userId);
            getAccountManager().modifyAccountId(accountID);
            // Let purge unused identitity to clean up. incase user change the mind
            // ((SQLiteOmemoStore) OmemoService.getInstance().getOmemoStoreBackend()).cleanUpOmemoDB();
        }
        else {
            accountProperties.put(USER_ID, accountID.getUserID());
        }

        // update the active accountID mAccountProperties with the modified accountProperties
        accountID.setAccountProperties(accountProperties);

        // First store the account and only then load it as the load generates an osgi event, the
        // osgi event triggers (through the UI) a call to the register() method and it needs to
        // access the configuration service and check for a password.
        this.storeAccount(accountID);

        EntityBareJid jid = accountID.getBareJid().asEntityBareJidIfPossible();
        ((ProtocolProviderServiceJabberImpl) protocolProvider).initialize(jid, accountID);

        // We store again the account in order to store all properties added during the protocol provider initialization.
        this.storeAccount(accountID);
        loadAccount(accountID);  // to replace all below
//        Hashtable<String, String> properties = new Hashtable<>();
//        properties.put(PROTOCOL, ProtocolNames.JABBER);
//        properties.put(USER_ID, accountID.getUserID());
//        registration = context.registerService(ProtocolProviderService.class.getName(), protocolProvider, properties);
//        registeredAccounts.put(accountID, registration);
    }
}
