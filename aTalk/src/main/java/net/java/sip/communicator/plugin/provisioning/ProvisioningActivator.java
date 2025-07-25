/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.plugin.provisioning;

import net.java.sip.communicator.service.credentialsstorage.CredentialsStorageService;
import net.java.sip.communicator.service.netaddr.NetworkAddressManagerService;
import net.java.sip.communicator.service.provdisc.ProvisioningDiscoveryService;
import net.java.sip.communicator.service.provisioning.ProvisioningService;

import org.apache.commons.lang3.StringUtils;
import org.atalk.service.configuration.ConfigurationService;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;

import timber.log.Timber;

/**
 * Activator the provisioning system. It will gather provisioning URL depending on the
 * configuration (DHCP, manual, ...), retrieve configuration file and push properties to the
 * <code>ConfigurationService</code>.
 */
public class ProvisioningActivator implements BundleActivator {
    /**
     * The current BundleContext.
     */
    static BundleContext bundleContext = null;

    /**
     * A reference to the CredentialsStorageService implementation instance
     * that is registered with the bundle context.
     */
    private static CredentialsStorageService credentialsService = null;

    /**
     * A reference to the NetworkAddressManagerService implementation instance
     * that is registered with the bundle context.
     */
    private static NetworkAddressManagerService netaddrService = null;

    /**
     * A reference to the ConfigurationService implementation instance that
     * is currently registered with the bundle context.
     */
    private static ConfigurationService configurationService = null;

    /**
     * Provisioning service.
     */
    private static ProvisioningServiceImpl provisioningService = null;

    /**
     * Starts this bundle
     *
     * @param bundleContext BundleContext
     *
     * @throws Exception if anything goes wrong during the start of the bundle
     */
    public void start(BundleContext bundleContext)
            throws Exception {
        ProvisioningActivator.bundleContext = bundleContext;
        String url = null;

        provisioningService = new ProvisioningServiceImpl();
        String method = provisioningService.getProvisioningMethod();
        if (StringUtils.isEmpty(method) || method.equals("NONE")) {
            return;
        }

        /*
         * search the provisioning discovery implementation that correspond to the method name
         */
        ServiceReference<?>[] serviceReferences
                = bundleContext.getServiceReferences(ProvisioningDiscoveryService.class.getName(), null);
        if (serviceReferences != null) {
            for (ServiceReference<?> ref : serviceReferences) {
                ProvisioningDiscoveryService provdisc = (ProvisioningDiscoveryService) bundleContext.getService(ref);
                if (provdisc.getMethodName().equals(method)) {
                    /* may block for sometime depending on the method used */
                    url = provdisc.discoverURL();
                    break;
                }
            }
        }

        provisioningService.start(url);
        bundleContext.registerService(ProvisioningService.class, provisioningService, null);
        Timber.d("Provisioning discovery [REGISTERED]");
    }

    /**
     * Stops this bundle
     *
     * @param bundleContext BundleContext
     *
     * @throws Exception if anything goes wrong during the stop of the bundle
     */
    public void stop(BundleContext bundleContext)
            throws Exception {
        ProvisioningActivator.bundleContext = null;
        Timber.d("Provisioning discovery [STOPPED]");
    }

    /**
     * Returns a reference to a ConfigurationService implementation currently registered in the
     * bundle context or null if no such implementation was found.
     *
     * @return a currently valid implementation of the ConfigurationService.
     */
    public static ConfigurationService getConfigurationService() {
        if (configurationService == null) {
            ServiceReference<?> confReference = bundleContext.getServiceReference(ConfigurationService.class);
            configurationService = (ConfigurationService) bundleContext.getService(confReference);
        }
        return configurationService;
    }

    /**
     * Returns a reference to a CredentialsStorageService implementation currently registered in
     * the bundle context or null if no such implementation was found.
     *
     * @return a currently valid implementation of the CredentialsStorageService.
     */
    public static CredentialsStorageService getCredentialsStorageService() {
        if (credentialsService == null) {
            ServiceReference<?> credentialsReference = bundleContext.getServiceReference(CredentialsStorageService.class);
            credentialsService = (CredentialsStorageService) bundleContext.getService(credentialsReference);
        }
        return credentialsService;
    }

    /**
     * Returns a reference to a NetworkAddressManagerService implementation currently registered
     * in the bundle context or null if no such implementation was found.
     *
     * @return a currently valid implementation of the NetworkAddressManagerService.
     */
    public static NetworkAddressManagerService getNetworkAddressManagerService() {
        if (netaddrService == null) {
            ServiceReference<?> netAddrReference = bundleContext.getServiceReference(NetworkAddressManagerService.class);
            netaddrService = (NetworkAddressManagerService) bundleContext.getService(netAddrReference);
        }
        return netaddrService;
    }

    /**
     * Returns a reference to a <code>ProvisioningService</code> implementation.
     *
     * @return a currently valid implementation of <code>ProvisioningService</code>
     */
    public static ProvisioningServiceImpl getProvisioningService() {
        return provisioningService;
    }
}
