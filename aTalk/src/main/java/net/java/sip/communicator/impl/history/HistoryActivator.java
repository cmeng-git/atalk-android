/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.impl.history;

import net.java.sip.communicator.service.history.HistoryService;

import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;

/**
 * Invoke "Service Binder" to parse the service XML and register all services.
 *
 * @author Alexander Pelov
 * @author Lubomir Marinov
 */
public class HistoryActivator implements BundleActivator {
    /**
     * The service registration.
     */
    private ServiceRegistration<?> serviceRegistration;

    /**
     * Initialize and start history service
     *
     * @param bundleContext the <code>BundleContext</code>
     *
     * @throws Exception if initializing and starting history service fails
     */
    public void start(BundleContext bundleContext)
            throws Exception {
        serviceRegistration = bundleContext.registerService(HistoryService.class.getName(),
                new HistoryServiceImpl(bundleContext), null);
    }

    /**
     * Stops this bundle.
     *
     * @param bundleContext the <code>BundleContext</code>
     *
     * @throws Exception if the stop operation goes wrong
     */
    public void stop(BundleContext bundleContext)
            throws Exception {
        if (serviceRegistration != null) {
            serviceRegistration.unregister();
            serviceRegistration = null;
        }
    }
}
