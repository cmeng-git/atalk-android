/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package net.java.sip.communicator.plugin.jabberaccregwizz;

import android.text.TextUtils;

import net.java.sip.communicator.service.gui.AccountRegistrationWizard;
import net.java.sip.communicator.service.protocol.*;
import net.java.sip.communicator.service.protocol.jabber.JabberAccountRegistration;

import org.atalk.android.plugin.timberlog.TimberLog;
import org.jxmpp.util.XmppStringUtils;
import org.osgi.framework.ServiceReference;

import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;

import timber.log.Timber;

/**
 * The <tt>AccountRegistrationImpl</tt> is an implementation of the <tt>AccountRegistrationWizard</tt> for the
 * Jabber protocol. It should allow the user to create and configure a new Jabber account.
 *
 * The method signin() is also called from JabberPreferenceFragment#doCommitChanges, with isModification set to true
 * to update the accountProperties DB with the preference changes by user
 *
 * @author Yana Stamcheva
 * @author Grigorii Balutsel
 * @author Eng Chong Meng
 */
public class AccountRegistrationImpl extends AccountRegistrationWizard
{
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
     * Install new or modify an account with the given user name and password;
     * pending on the flag isModification setting.
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
     * Create or modify an account for the given user, password and accountProperties pending isModification()
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
        Timber.log(TimberLog.FINER, "Preparing to install account for user %s", userName);

        // if server address is null, just extract it from userID even for when server override option is set
        if (accountProperties.get(ProtocolProviderFactory.SERVER_ADDRESS) == null) {
            String serverAddress = XmppStringUtils.parseDomain(userName);
            if (!TextUtils.isEmpty(serverAddress))
                accountProperties.put(ProtocolProviderFactory.SERVER_ADDRESS, serverAddress);
            else
                throw new OperationFailedException("Should specify a server for user name "
                        + userName + ".", OperationFailedException.SERVER_NOT_SPECIFIED);
        }
        // if server port is null, we will set default value
        if (accountProperties.get(ProtocolProviderFactory.SERVER_PORT) == null) {
            accountProperties.put(ProtocolProviderFactory.SERVER_PORT, "5222");
        }

        // Add additional parameters to accountProperties
        accountProperties.put(ProtocolProviderFactory.IS_PREFERRED_PROTOCOL, Boolean.toString(isPreferredProtocol()));
        accountProperties.put(ProtocolProviderFactory.PROTOCOL, getProtocol());

        String protocolIconPath = getProtocolIconPath();
        String accountIconPath = getAccountIconPath();

        registration.storeProperties(providerFactory, password, protocolIconPath, accountIconPath,
                isModification(), accountProperties);

        // Process account modification and return with the existing protocolProvider
        if (isModification()) {
            providerFactory.modifyAccount(protocolProvider, accountProperties);
            setModification(false);
            return protocolProvider;
        }

        /* isModification() == false; Process to create new account and return the newly created protocolProvider */
        try {
            Timber.i("Installing new account created for user %s", userName);

            AccountID accountID = providerFactory.installAccount(userName, accountProperties);
            ServiceReference serRef = providerFactory.getProviderForAccount(accountID);
            protocolProvider = (ProtocolProviderService) JabberAccountRegistrationActivator.bundleContext.getService(serRef);
        } catch (IllegalArgumentException exc) {
            Timber.w("%s", exc.getMessage());
            throw new OperationFailedException("Username, password or server is null.",
                    OperationFailedException.ILLEGAL_ARGUMENT);
        } catch (IllegalStateException exc) {
            Timber.w("%s", exc.getMessage());
            throw new OperationFailedException("Account already exists.",
                    OperationFailedException.IDENTIFICATION_CONFLICT);
        } catch (Throwable exc) {
            Timber.w("%s", exc.getMessage());
            throw new OperationFailedException("Failed to add account.", OperationFailedException.GENERAL_ERROR);
        }
        return protocolProvider;
    }

    /**
     * Returns the protocol name as listed in "ProtocolNames" or just the name of the service.
     *
     * @return the protocol name
     */
    public String getProtocol()
    {
        return ProtocolNames.JABBER;
    }

    /**
     * Indicates if this wizard is for the preferred protocol. Currently on support XMPP, so always true
     *
     * @return <tt>true</tt> if this wizard corresponds to the preferred protocol, otherwise returns <tt>false</tt>
     */
    public boolean isPreferredProtocol()
    {
          // Check for preferred account through the PREFERRED_ACCOUNT_WIZARD property.
//        String prefWName = JabberAccountRegistrationActivator.getResources().
//            getSettingsString("gui.PREFERRED_ACCOUNT_WIZARD");
//
//        if(!TextUtils.isEmpty(prefWName) > 0 && prefWName.equals(this.getClass().getName()))
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
