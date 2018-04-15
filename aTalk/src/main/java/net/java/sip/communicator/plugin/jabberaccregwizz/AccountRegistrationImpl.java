/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package net.java.sip.communicator.plugin.jabberaccregwizz;

import net.java.sip.communicator.service.gui.AccountRegistrationWizard;
import net.java.sip.communicator.service.gui.WizardPage;
import net.java.sip.communicator.service.protocol.AccountID;
import net.java.sip.communicator.service.protocol.OperationFailedException;
import net.java.sip.communicator.service.protocol.ProtocolNames;
import net.java.sip.communicator.service.protocol.ProtocolProviderFactory;
import net.java.sip.communicator.service.protocol.ProtocolProviderService;
import net.java.sip.communicator.service.protocol.jabber.JabberAccountRegistration;
import net.java.sip.communicator.util.Logger;

import org.jivesoftware.smack.util.StringUtils;
import org.jxmpp.util.XmppStringUtils;
import org.osgi.framework.ServiceReference;

import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;

/**
 * The <tt>IPPIAccountRegistrationWizard</tt> is an implementation of the
 * <tt>AccountRegistrationWizard</tt> for the Jabber protocol. It should allow
 * the user to create and configure a new Jabber account.
 *
 * @author Yana Stamcheva
 * @author Grigorii Balutsel
 * @author Eng Chong Meng
 */
public class AccountRegistrationImpl extends AccountRegistrationWizard
{
    /*
     * The logger.
     */
    private static final Logger logger = Logger.getLogger(JabberAccountRegistration.class);

    /*
     * The protocol provider.
     */
    private ProtocolProviderService protocolProvider;

    private JabberAccountRegistration registration = new JabberAccountRegistration();

    public String getProtocolName()
    {
        return ProtocolNames.JABBER;
    }

    /**
     * Installs the account with the given user name and password.
     *
     * @param userName the account user name
     * @param password the password
     * @param accountProperties additional account parameters for setting up new account/modify e.g. server and port
     * @return the <tt>ProtocolProviderService</tt> corresponding to the newly created account.
     * @throws OperationFailedException problem signing in.
     */
    public ProtocolProviderService signin(String userName, String password, Map<String, String> accountProperties)
            throws OperationFailedException
    {
        ProtocolProviderFactory factory = JabberAccountRegistrationActivator.getJabberProtocolProviderFactory();
        ProtocolProviderService pps = null;
        if (factory != null)
            pps = installAccount(factory, userName, password, accountProperties);
        return pps;
    }

    /**
     * Creates an account for the given user and password.
     *
     * @param providerFactory the ProtocolProviderFactory which will create the account
     * @param userName the user identifier
     * @param password the password
     * @param accountProperties additional account parameters for setting up new account/modify e.g. server and port
     * @return the <tt>ProtocolProviderService</tt> for the new account.
     * @throws OperationFailedException if the operation didn't succeed
     */
    protected ProtocolProviderService installAccount(ProtocolProviderFactory providerFactory,
            String userName, String password, Map<String, String> accountProperties)
            throws OperationFailedException
    {
        if (logger.isTraceEnabled()) {
            logger.trace("Preparing to install account for user " + userName);
        }

        // Add additional parameters to accountProperties
        accountProperties.put(ProtocolProviderFactory.IS_PREFERRED_PROTOCOL, Boolean.toString(isPreferredProtocol()));
        accountProperties.put(ProtocolProviderFactory.PROTOCOL, getProtocol());

        // if server address is null, we must extract it from userID
        if (accountProperties.get(ProtocolProviderFactory.SERVER_ADDRESS) == null) {
            String serverAddress = XmppStringUtils.parseDomain(userName);
            if (!StringUtils.isNullOrEmpty(serverAddress))
                accountProperties.put(ProtocolProviderFactory.SERVER_ADDRESS, XmppStringUtils.parseDomain(userName));
            else
                throw new OperationFailedException("Should specify a server for user name "
                        + userName + ".", OperationFailedException.SERVER_NOT_SPECIFIED);
        }
        // if server port is null, we will set default value
        if (accountProperties.get(ProtocolProviderFactory.SERVER_PORT) == null) {
            accountProperties.put(ProtocolProviderFactory.SERVER_PORT, "5222");
        }

        String protocolIconPath = getProtocolIconPath();
        String accountIconPath = getAccountIconPath();
        registration.storeProperties(providerFactory, userName, password, protocolIconPath,
                accountIconPath, accountProperties);

        if (isModification()) {
            providerFactory.modifyAccount(protocolProvider, accountProperties);
            setModification(false);
            return protocolProvider;
        }

        /* Process to create new account */
        try {
            if (logger.isTraceEnabled()) {
                logger.trace("Will install account for user " + userName
                        + " with the following properties." + accountProperties);
            }

            // Retrieve the newly created AccountID accountProperties
            accountProperties = registration.getAccountProperties();
            AccountID accountID = providerFactory.installAccount(userName, accountProperties);
            ServiceReference serRef = providerFactory.getProviderForAccount(accountID);
            protocolProvider
                    = (ProtocolProviderService) JabberAccountRegistrationActivator.bundleContext.getService(serRef);
        } catch (IllegalArgumentException exc) {
            logger.warn(exc.getMessage());
            throw new OperationFailedException("Username, password or server is null.",
                    OperationFailedException.ILLEGAL_ARGUMENT);
        } catch (IllegalStateException exc) {
            logger.warn(exc.getMessage());
            throw new OperationFailedException("Account already exists.",
                    OperationFailedException.IDENTIFICATION_CONFLICT);
        } catch (Throwable exc) {
            logger.warn(exc.getMessage());
            throw new OperationFailedException("Failed to add account.", OperationFailedException.GENERAL_ERROR);
        }
        return protocolProvider;
    }

    /**
     * Returns the protocol name as listed in "ProtocolNames" or just the name
     * of the service.
     *
     * @return the protocol name
     */
    public String getProtocol()
    {
        return ProtocolNames.JABBER;
    }

    /**
     * Indicates if this wizard is for the preferred protocol.
     *
     * @return <tt>true</tt> if this wizard corresponds to the preferred protocol, otherwise
     * returns <tt>false</tt>
     */
    public boolean isPreferredProtocol()
    {
        // Check for preferred account through the PREFERRED_ACCOUNT_WIZARD property.
//        String prefWName = JabberAccountRegistrationActivator.getResources().
//            getSettingsString("gui.PREFERRED_ACCOUNT_WIZARD");
//
//        if(prefWName != null && prefWName.length() > 0
//            && prefWName.equals(this.getClass().getName()))
//            return true;

        return true;
    }

    /**
     * Returns the protocol icon path.
     *
     * @return the protocol icon path
     */
    public String getProtocolIconPath()
    {
        return null;
    }

    /**
     * Returns the account icon path.
     *
     * @return the account icon path
     */
    public String getAccountIconPath()
    {
        return null;
    }

    @Override
    public ProtocolProviderService signin()
            throws OperationFailedException
    {
        return null;
    }

    @Override
    public byte[] getIcon()
    {
        return null;
    }

    @Override
    public byte[] getPageImage()
    {
        return null;
    }

    @Override
    public String getProtocolDescription()
    {
        return null;
    }

    @Override
    public String getUserNameExample()
    {
        return null;
    }

    @Override
    public void loadAccount(ProtocolProviderService protocolProvider)
    {
        setModification(true);
        this.protocolProvider = protocolProvider;
        registration = new JabberAccountRegistration();
        AccountID accountID = protocolProvider.getAccountID();

        // Loads account properties into registration object
        registration.loadAccount(accountID, JabberAccountRegistrationActivator.bundleContext);
    }

    @Override
    public Iterator<WizardPage> getPages()
    {
        return null;
    }

    @Override
    public Object getFirstPageIdentifier()
    {
        return null;
    }

    @Override
    public Object getLastPageIdentifier()
    {
        return null;
    }

    @Override
    public Iterator<Entry<String, String>> getSummary()
    {
        return null;
    }

    @Override
    public Object getSimpleForm(boolean isCreateAccount)
    {
        return null;
    }

    public JabberAccountRegistration getAccountRegistration()
    {
        return registration;
    }

    @Override
    public boolean isInBandRegistrationSupported()
    {
        return true;
    }
}
