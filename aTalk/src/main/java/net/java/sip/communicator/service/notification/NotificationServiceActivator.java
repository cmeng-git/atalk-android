/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.notification;

import org.atalk.service.configuration.ConfigurationService;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.ServiceRegistration;

import timber.log.Timber;

/**
 * The <code>NotificationActivator</code> is the activator of the notification bundle.
 *
 * @author Yana Stamcheva
 * @author Eng Chong Meng
 */
public class NotificationServiceActivator implements BundleActivator {
    protected static BundleContext bundleContext;
    private static ConfigurationService configService;
    private ServiceRegistration<?> notificationService;

    public void start(BundleContext bc)
            throws Exception {
        bundleContext = bc;
        notificationService = bundleContext.registerService(NotificationService.class.getName(),
                new NotificationServiceImpl(), null);
        Timber.d("Notification Service ...[REGISTERED]");
    }

    public void stop(BundleContext bc)
            throws Exception {
        notificationService.unregister();
        Timber.d("Notification Service ...[STOPPED]");
    }

    /**
     * Returns the <code>ConfigurationService</code> obtained from the bundle context.
     *
     * @return the <code>ConfigurationService</code> obtained from the bundle context
     */
    public static ConfigurationService getConfigurationService() {
        if (configService == null) {
            ServiceReference<?> configReference = bundleContext.getServiceReference(ConfigurationService.class.getName());
            configService = (ConfigurationService) bundleContext.getService(configReference);
        }
        return configService;
    }
}
