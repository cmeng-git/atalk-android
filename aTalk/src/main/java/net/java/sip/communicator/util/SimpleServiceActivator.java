/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package net.java.sip.communicator.util;

import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;

import timber.log.Timber;

/**
 * Base class for activators which only register new service in bundle context.
 * Service registration activity is logged on <code>Debug</code> level.
 *
 * @param <T> service implementation template type (for convenient instance access)
 *
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
public abstract class SimpleServiceActivator<T> implements BundleActivator {
    /**
     * Class of the service
     */
    private final Class<?> serviceClass;

    /**
     * Service name that will be used in log messages
     */
    private final String serviceName;

    /**
     * Instance of service implementation
     */
    protected T serviceImpl;

    /**
     * Creates new instance of <code>SimpleServiceActivator</code>
     *
     * @param serviceClass class of service that will be registered on bundle startup
     * @param serviceName service name that wil be used in log messages
     */
    public SimpleServiceActivator(Class<?> serviceClass, String serviceName) {
        this.serviceClass = serviceClass;
        this.serviceName = serviceName;
    }

    /**
     * Initialize and start the service.
     *
     * @param bundleContext the <code>BundleContext</code>
     *
     * @throws Exception if initializing and starting this service fails
     */
    public void start(BundleContext bundleContext)
            throws Exception {
        serviceImpl = createServiceImpl();
        bundleContext.registerService(serviceClass.getName(), serviceImpl, null);
        Timber.i("%s REGISTERED", serviceName);
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
    }

    /**
     * Called on bundle startup in order to create service implementation instance.
     *
     * @return should return new instance of service implementation.
     */
    protected abstract T createServiceImpl();
}
