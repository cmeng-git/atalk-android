/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.libjitsi;

import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;

import java.util.Objects;

/**
 * Represents an implementation of the <tt>libjitsi</tt> library which utilizes OSGi.
 *
 * @author Lyubomir Marinov
 * @author Eng Chong Meng
 */
public class LibJitsiOSGiImpl extends LibJitsiImpl
{
    /**
     * The <tt>BundleContext</tt> discovered by this instance during its initialization and
     * used to look for registered services.
     */
    private final BundleContext bundleContext;

    /**
     * Initializes a new <tt>LibJitsiOSGiImpl</tt> instance with a specific <tt>BundleContext</tt>.
     *
     * @param bundleContext the <tt>BundleContext</tt> to be used by the new
     * instance to look for registered services
     */
    public LibJitsiOSGiImpl(BundleContext bundleContext)
    {
        this.bundleContext = Objects.requireNonNull(bundleContext, "bundleContext");
    }

    /**
     * Gets a service of a specific type associated with this implementation of the <tt>libjitsi</tt> library.
     *
     * @param serviceClass the type of the service to be retrieved
     * @return a service of the specified type if there is such an association known to this
     * implementation of the <tt>libjitsi</tt> library; otherwise, <tt>null</tt>
     */
    @Override
    protected <T> T getService(Class<T> serviceClass)
    {
		@SuppressWarnings("rawtypes")
        ServiceReference serviceReference = bundleContext.getServiceReference(serviceClass);
		@SuppressWarnings("unchecked")
        T service = (serviceReference == null) ? null : (T) bundleContext.getService(serviceReference);

        if (service == null)
            service = super.getService(serviceClass);

        return service;
    }
}
