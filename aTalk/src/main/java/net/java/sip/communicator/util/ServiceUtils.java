/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.util;

import org.osgi.framework.BundleContext;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceReference;

/**
 * Gathers utility functions related to OSGi services such as getting a service registered in a BundleContext.
 *
 * @author Lyubomir Marinov
 * @author Pawel Domas
 */
public class ServiceUtils {
    /**
     * Gets an OSGi service registered in a specific <code>BundleContext</code> by its <code>Class</code>
     *
     * @param <T> the very type of the OSGi service to get
     * @param bundleContext the <code>BundleContext</code> in which the service to get has been registered
     * @param serviceClass the <code>Class</code> with which the service to get has been registered in the
     * <code>bundleContext</code>
     *
     * @return the OSGi service registered in <code>bundleContext</code> with the specified
     * <code>serviceClass</code> if such a service exists there; otherwise, <code>null</code>
     */
    public static <T> T getService(BundleContext bundleContext, Class<T> serviceClass) {
        ServiceReference<T> serviceReference = null;
        if ((bundleContext != null) && (serviceClass != null))
            serviceReference = bundleContext.getServiceReference(serviceClass);

        return (serviceReference == null) ? null : bundleContext.getService(serviceReference);
    }

    /**
     * Gets an OSGi service references registered in a specific <code>BundleContext</code> by its <code>Class</code>.
     *
     * @param bundleContext the <code>BundleContext</code> in which the services to get have been registered
     * @param serviceClass the <code>Class</code> of the OSGi service references to get
     *
     * @return the OSGi service references registered in <code>bundleContext</code> with the specified
     * <code>serviceClass</code> if such a services exists there; otherwise, an empty <code>Collection</code>
     */
    public static ServiceReference[] getServiceReferences(BundleContext bundleContext, Class<?> serviceClass) {
        ServiceReference[] serviceReferences;
        try {
            serviceReferences = bundleContext.getServiceReferences(serviceClass.getName(), null);
        } catch (InvalidSyntaxException | NullPointerException ex) {
            serviceReferences = null;
        }

        if (serviceReferences == null)
            serviceReferences = new ServiceReference[0]; // Collections.emptyList();

        return serviceReferences;
    }

    /**
     * Prevents the creation of <code>ServiceUtils</code> instances.
     */
    private ServiceUtils() {
    }
}
