/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.util.account;

import net.java.sip.communicator.service.protocol.*;
import net.java.sip.communicator.util.ServiceUtils;
import net.java.sip.communicator.util.UtilActivator;

import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceReference;

import java.util.*;

import timber.log.Timber;

/**
 * The <tt>AccountUtils</tt> provides utility methods helping us to easily obtain an account or
 * a groups of accounts or protocol providers by some specific criteria.
 *
 * @author Yana Stamcheva
 * @author Eng Chong Meng
 */
public class AccountUtils
{
    /**
     * Returns an iterator over a list of all stored <tt>AccountID</tt>-s.
     *
     * @return an iterator over a list of all stored <tt>AccountID</tt>-s
     */
    public static Collection<AccountID> getStoredAccounts()
    {
        AccountManager accountManager = ServiceUtils.getService(UtilActivator.bundleContext, AccountManager.class);
        if (accountManager != null)
            return accountManager.getStoredAccounts();
        else
            return Collections.emptyList();
    }

    /**
     * Return the <tt>AccountID</tt> corresponding to the given string account unique identifier.
     *
     * @param accountUID the account unique identifier string
     * @return the <tt>AccountID</tt> corresponding to the given string account unique identifier
     */
    public static AccountID getAccountIDForUID(String accountUID)
    {
        Collection<AccountID> allAccounts = getStoredAccounts();
        for (AccountID account : allAccounts) {
            if (account.getAccountUniqueID().equals(accountUID))
                return account;
        }
        return null;
    }

    /**
     * Return the <tt>AccountID</tt> corresponding to the given string account userID; assuming
     * that userID is unique across all protocolServiceProviders
     *
     * @param userID the account unique identifier string
     * @return the <tt>AccountID</tt> corresponding to the given string account userID
     */
    public static AccountID getAccountIDForUserID(String userID)
    {
        Collection<AccountID> allAccounts = getStoredAccounts();
        for (AccountID account : allAccounts) {
            if (account.getUserID().equals(userID))
                return account;
        }
        return null;
    }

    /**
     * Returns a list of all currently registered providers, which support the given <tt>operationSetClass</tt>.
     *
     * @param opSetClass the operation set class for which we're looking for providers
     * @return a list of all currently registered providers, which support the given <tt>operationSetClass</tt>
     */
    public static List<ProtocolProviderService> getRegisteredProviders(
            Class<? extends OperationSet> opSetClass)
    {
        List<ProtocolProviderService> opSetProviders = new LinkedList<>();
        for (ProtocolProviderFactory providerFactory : UtilActivator.getProtocolProviderFactories().values()) {
            for (AccountID accountID : providerFactory.getRegisteredAccounts()) {
                ServiceReference<ProtocolProviderService> ref = providerFactory.getProviderForAccount(accountID);

                if (ref != null) {
                    ProtocolProviderService protocolProvider = UtilActivator.bundleContext.getService(ref);

                    if ((protocolProvider.getOperationSet(opSetClass) != null) && protocolProvider.isRegistered()) {
                        opSetProviders.add(protocolProvider);
                    }
                }
            }
        }
        return opSetProviders;
    }

    /**
     * Returns a list of all currently registered telephony providers for the given protocol name.
     *
     * @param protocolName the protocol name
     * @param opSetClass the operation set class for which we're looking for providers
     * @return a list of all currently registered providers for the given <tt>protocolName</tt>
     * and supporting the given <tt>operationSetClass</tt>
     */
    public static List<ProtocolProviderService> getRegisteredProviders(String protocolName,
            Class<? extends OperationSet> opSetClass)
    {
        List<ProtocolProviderService> opSetProviders = new LinkedList<>();
        ProtocolProviderFactory providerFactory = getProtocolProviderFactory(protocolName);

        if (providerFactory != null) {
            for (AccountID accountID : providerFactory.getRegisteredAccounts()) {
                ServiceReference<ProtocolProviderService> ref = providerFactory.getProviderForAccount(accountID);

                if (ref != null) {
                    ProtocolProviderService protocolProvider = UtilActivator.bundleContext.getService(ref);

                    if ((protocolProvider.getOperationSet(opSetClass) != null) && protocolProvider.isRegistered()) {
                        opSetProviders.add(protocolProvider);
                    }
                }
            }
        }
        return opSetProviders;
    }

    /**
     * Returns a list of all registered protocol providers that could be used for the operation
     * given by the operation set. Prefers the given preferred protocol provider and preferred
     * protocol name if they're available and registered.
     *
     * @param opSet the operation set for which we're looking for providers
     * @param preferredProvider the preferred protocol provider
     * @param preferredProtocolName the preferred protocol name
     * @return a list of all registered protocol providers that could be used for the operation
     * given by the operation set
     */
    public static List<ProtocolProviderService> getOpSetRegisteredProviders(
            Class<? extends OperationSet> opSet, ProtocolProviderService preferredProvider,
            String preferredProtocolName)
    {
        List<ProtocolProviderService> providers = new ArrayList<>();
        if (preferredProvider != null) {
            if (preferredProvider.isRegistered()) {
                providers.add(preferredProvider);
            }
            // If we have a provider, but it's not registered we try to obtain all registered
            // providers for the same protocol as the given preferred provider.
            else {
                providers = getRegisteredProviders(preferredProvider.getProtocolName(), opSet);
            }
        }
        // If we don't have a preferred provider we try to obtain a preferred protocol name and
        // all registered providers for it.
        else {
            if (preferredProtocolName != null) {
                providers = getRegisteredProviders(preferredProtocolName, opSet);
            }
            // If the protocol name is null we simply obtain all telephony providers.
            else {
                providers = getRegisteredProviders(opSet);
            }
        }
        return providers;
    }

    /**
     * Returns the <tt>ProtocolProviderService</tt> corresponding to the given account identifier
     * that is registered in the given factory
     *
     * @param accountID the identifier of the account
     * @return the <tt>ProtocolProviderService</tt> corresponding to the given account identifier
     * that is registered in the given factory
     */
    public static ProtocolProviderService getRegisteredProviderForAccount(AccountID accountID)
    {
        for (ProtocolProviderFactory factory : UtilActivator.getProtocolProviderFactories().values()) {
            if (factory.getRegisteredAccounts().contains(accountID)) {
                ServiceReference<ProtocolProviderService> ref = factory.getProviderForAccount(accountID);

                if (ref != null) {
                    return UtilActivator.bundleContext.getService(ref);
                }
            }
        }
        return null;
    }

    /**
     * Returns a <tt>ProtocolProviderFactory</tt> for a given protocol provider.
     *
     * @param protocolProvider the <tt>ProtocolProviderService</tt>, which factory we're looking for
     * @return a <tt>ProtocolProviderFactory</tt> for a given protocol provider
     */
    public static ProtocolProviderFactory getProtocolProviderFactory(ProtocolProviderService protocolProvider)
    {
        return getProtocolProviderFactory(protocolProvider.getProtocolName());
    }

    /**
     * Returns a <tt>ProtocolProviderFactory</tt> for a given protocol provider.
     *
     * @param protocolName the name of the protocol
     * @return a <tt>ProtocolProviderFactory</tt> for a given protocol provider
     */
    public static ProtocolProviderFactory getProtocolProviderFactory(String protocolName)
    {
        String osgiFilter = "(" + ProtocolProviderFactory.PROTOCOL + "=" + protocolName + ")";
        ProtocolProviderFactory protocolProviderFactory = null;

        try {
            Collection<ServiceReference<ProtocolProviderFactory>> refs
                    = UtilActivator.bundleContext.getServiceReferences(ProtocolProviderFactory.class, osgiFilter);

            if ((refs != null) && !refs.isEmpty()) {
                protocolProviderFactory = UtilActivator.bundleContext.getService(refs.iterator().next());
            }
        } catch (InvalidSyntaxException ex) {
            Timber.e("AccountUtils : %s", ex.getMessage());
        }
        return protocolProviderFactory;
    }

    /**
     * Returns all registered protocol providers independent of .isRegister().
     *
     * @return a list of all registered providers
     */
    public static Collection<ProtocolProviderService> getRegisteredProviders()
    {
        List<ProtocolProviderService> registeredProviders = new LinkedList<>();
        for (ProtocolProviderFactory providerFactory : UtilActivator.getProtocolProviderFactories().values()) {
            for (AccountID accountID : providerFactory.getRegisteredAccounts()) {
                ServiceReference<ProtocolProviderService> ref = providerFactory.getProviderForAccount(accountID);

                if (ref != null) {
                    ProtocolProviderService protocolProvider = UtilActivator.bundleContext.getService(ref);
                    registeredProviders.add(protocolProvider);
                }
            }
        }
        return registeredProviders;
    }

    /**
     * Returns all registered protocol providers that are online (.isRegister() == true).
     *
     * @return a list of all registered providers
     */
    public static Collection<ProtocolProviderService> getOnlineProviders()
    {
        List<ProtocolProviderService> onlineProviders = new LinkedList<>();

        for (ProtocolProviderFactory providerFactory : UtilActivator.getProtocolProviderFactories().values()) {
            for (AccountID accountID : providerFactory.getRegisteredAccounts()) {
                ServiceReference<ProtocolProviderService> ref = providerFactory.getProviderForAccount(accountID);

                if (ref != null) {
                    ProtocolProviderService protocolProvider = UtilActivator.bundleContext.getService(ref);
                    if (protocolProvider.isRegistered())
                        onlineProviders.add(protocolProvider);
                }
            }
        }
        return onlineProviders;
    }
}
