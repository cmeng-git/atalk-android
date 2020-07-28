/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.protocol;

import net.java.sip.communicator.service.calendar.CalendarService;

import org.atalk.android.plugin.timberlog.TimberLog;
import org.atalk.service.configuration.ConfigurationService;
import org.atalk.service.resources.ResourceManagementService;
import org.osgi.framework.*;

import java.util.ArrayList;
import java.util.List;

import timber.log.Timber;

/**
 * Implements {@code BundleActivator} for the purposes of
 * protocol.jar/protocol.provider.manifest.mf and in order to register and start services
 * independent of the specifics of a particular protocol.
 *
 * @author Lubomir Marinov
 * @author Yana Stamcheva
 * @author Eng Chong Meng
 */
public class ProtocolProviderActivator implements BundleActivator
{
    /**
     * The {@code ServiceRegistration} of the {@code AccountManager} implementation
     * registered as a service by this activator and cached so that the service in question can be
     * properly disposed of upon stopping this activator.
     */
    private ServiceRegistration<?> accountManagerServiceRegistration;

    /**
     * The account manager.
     */
    private static AccountManager accountManager;

    /**
     * The {@code BundleContext} of the one and only {@code ProtocolProviderActivator}
     * instance which is currently started.
     */
    private static BundleContext bundleContext;

    /**
     * The {@code ConfigurationService} used by the classes in the bundle represented by
     * {@code ProtocolProviderActivator}.
     */
    private static ConfigurationService configurationService;

    /**
     * The resource service through which we obtain localized strings.
     */
    private static ResourceManagementService resourceService;

    /**
     * The calendar service instance.
     */
    private static CalendarService calendarService;

    /**
     * The {@code SingleCallInProgressPolicy} making sure that the {@code Call}s
     * accessible in the {@code BundleContext} of this activator will obey to the rule that a
     * new {@code Call} should put the other existing {@code Call}s on hold.
     */
    private SingleCallInProgressPolicy singleCallInProgressPolicy;

    /**
     * Gets the {@code ConfigurationService} to be used by the classes in the bundle
     * represented by {@code ProtocolProviderActivator}.
     *
     * @return the {@code ConfigurationService} to be used by the classes in the bundle
     * represented by {@code ProtocolProviderActivator}
     */
    public static ConfigurationService getConfigurationService()
    {
        if ((configurationService == null) && (bundleContext != null)) {
            ServiceReference<?> svrReference = bundleContext.getServiceReference(ConfigurationService.class.getName());
            configurationService = (ConfigurationService) bundleContext.getService(svrReference);
        }
        return configurationService;
    }

    /**
     * Gets the {@code ResourceManagementService} to be used by the classes in the bundle
     * represented by {@code ProtocolProviderActivator}.
     *
     * @return the {@code ResourceManagementService} to be used by the classes in the bundle
     * represented by {@code ProtocolProviderActivator}
     */
    public static ResourceManagementService getResourceService()
    {
        if (resourceService == null) {
            resourceService = (ResourceManagementService) bundleContext.getService(bundleContext
                    .getServiceReference(ResourceManagementService.class.getName()));
        }
        return resourceService;
    }

    /**
     * Gets the {@code CalendarService} to be used by the classes in the bundle represented by
     * {@code ProtocolProviderActivator}.
     *
     * @return the {@code CalendarService} to be used by the classes in the bundle represented
     * by {@code ProtocolProviderActivator}
     */
    public static CalendarService getCalendarService()
    {
        if (calendarService == null) {
            ServiceReference<?> serviceReference = bundleContext
                    .getServiceReference(CalendarService.class.getName());
            if (serviceReference == null)
                return null;
            calendarService = (CalendarService) bundleContext.getService(serviceReference);
        }
        return calendarService;
    }

    /**
     * Returns a <tt>ProtocolProviderFactory</tt> for a given protocol provider.
     *
     * @param protocolName the name of the protocol, which factory we're looking for
     * @return a <tt>ProtocolProviderFactory</tt> for a given protocol provider
     */
    public static ProtocolProviderFactory getProtocolProviderFactory(String protocolName)
    {
        String osgiFilter = "(" + ProtocolProviderFactory.PROTOCOL + "=" + protocolName + ")";
        ProtocolProviderFactory protocolProviderFactory = null;

        try {
            ServiceReference[] serRefs
                    = bundleContext.getServiceReferences(ProtocolProviderFactory.class.getName(), osgiFilter);

            if ((serRefs != null) && (serRefs.length != 0)) {
                protocolProviderFactory = (ProtocolProviderFactory) bundleContext.getService(serRefs[0]);
            }
        } catch (InvalidSyntaxException ex) {
            Timber.i("ProtocolProviderActivator : %s", ex.getMessage());
        }
        return protocolProviderFactory;
    }

    /**
     * Registers a new {@code AccountManagerImpl} instance as an {@code AccountManager}
     * service and starts a new {@code SingleCallInProgressPolicy} instance to ensure that only
     * one of the {@code Call}s accessible in the {@code BundleContext} in which this activator
     * is to execute will be in progress and the others will automatically be put on hold.
     *
     * @param bundleContext the {@code BundleContext} in which the bundle activation represented by this
     * {@code BundleActivator} executes
     */
    public void start(BundleContext bundleContext)
    {
        ProtocolProviderActivator.bundleContext = bundleContext;
        accountManager = new AccountManager(bundleContext);
        accountManagerServiceRegistration
                = bundleContext.registerService(AccountManager.class.getName(), accountManager, null);
        Timber.log(TimberLog.FINER, "ProtocolProviderActivator will create SingleCallInProgressPolicy instance.");
        singleCallInProgressPolicy = new SingleCallInProgressPolicy(bundleContext);
    }

    /**
     * Unregisters the {@code AccountManagerImpl} instance registered as an
     * {@code AccountManager} service in {@link #start(BundleContext)} and stops the
     * {@code SingleCallInProgressPolicy} started there as well.
     *
     * @param bundleContext the {@code BundleContext} in which the bundle activation represented by this
     * {@code BundleActivator} executes
     */
    public void stop(BundleContext bundleContext)
    {
        if (accountManagerServiceRegistration != null) {
            accountManagerServiceRegistration.unregister();
            accountManagerServiceRegistration = null;
            accountManager = null;
        }

        if (singleCallInProgressPolicy != null) {
            singleCallInProgressPolicy.dispose();
            singleCallInProgressPolicy = null;
        }

        if (bundleContext.equals(ProtocolProviderActivator.bundleContext))
            ProtocolProviderActivator.bundleContext = null;

        configurationService = null;
        resourceService = null;
    }

    /**
     * Returns all protocol providers currently registered.
     *
     * @return all protocol providers currently registered.
     */
    public static List<ProtocolProviderService> getProtocolProviders()
    {
        ServiceReference[] serRefs = null;
        try {
            // get all registered provider factories
            serRefs = bundleContext.getServiceReferences(ProtocolProviderService.class.getName(), null);
        } catch (InvalidSyntaxException e) {
            Timber.e("ProtocolProviderActivator: %s", e.getMessage());
        }

        List<ProtocolProviderService> providersList = new ArrayList<ProtocolProviderService>();
        if (serRefs != null) {
            for (ServiceReference<?> serRef : serRefs) {
                ProtocolProviderService pp = (ProtocolProviderService) bundleContext.getService(serRef);
                providersList.add(pp);
            }
        }
        return providersList;
    }

    /**
     * Get the <tt>AccountManager</tt> of the protocol.
     *
     * @return <tt>AccountManager</tt> of the protocol
     */
    public static AccountManager getAccountManager()
    {
        return accountManager;
    }

    /**
     * Returns OSGI bundle context.
     *
     * @return OSGI bundle context.
     */
    public static BundleContext getBundleContext()
    {
        return bundleContext;
    }
}
