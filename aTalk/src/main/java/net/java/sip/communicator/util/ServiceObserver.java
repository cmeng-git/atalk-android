/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package net.java.sip.communicator.util;

import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceEvent;
import org.osgi.framework.ServiceListener;
import org.osgi.framework.ServiceReference;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Class keeps up to date list of services that implement given interface.
 * Can be used as a replacement for expensive calls to <code>getServiceReferences</code>.
 *
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
public class ServiceObserver<T>
        implements ServiceListener
{
    /**
     * Service class name.
     */
    private final Class<T> clazz;

    /**
     * The OSGi context.
     */
    private BundleContext context;

    /**
     * Service instances list.
     */
    private final List<T> services = new ArrayList<T>();

    /**
     * Creates new instance of <code>ServiceObserver</code> that will observe services of given <code>className</code>.
     *
     * @param clazz the <code>Class</code> of the service to observe.
     */
    public ServiceObserver(Class<T> clazz)
    {
        this.clazz = clazz;
    }

    /**
     * Returns list of services compatible with service class observed by this instance.
     *
     * @return list of services compatible with service class observed by this instance.
     */
    public List<T> getServices()
    {
        return Collections.unmodifiableList(services);
    }

    /**
     * {@inheritDoc}
     */
    @SuppressWarnings("unchecked")
    @Override
    public void serviceChanged(ServiceEvent serviceEvent)
    {
        Object service = context.getService(serviceEvent.getServiceReference());
        if (!clazz.isInstance(service)) {
            return;
        }

        int eventType = serviceEvent.getType();
        if (eventType == ServiceEvent.REGISTERED) {
            services.add((T) service);
        }
        else if (eventType == ServiceEvent.UNREGISTERING) {
            services.remove(service);
        }
    }

    /**
     * This method must be called when OSGi i s starting to initialize the  observer.
     *
     * @param ctx the OSGi bundle context.
     */
    public void start(BundleContext ctx)
    {
        this.context = ctx;
        ctx.addServiceListener(this);

        ServiceReference<T>[] refs = ServiceUtils.getServiceReferences(ctx, clazz);
        for (ServiceReference<T> ref : refs)
            services.add(ctx.getService(ref));
    }

    /**
     * This method should be called on bundle shutdown to properly release the resources.
     *
     * @param ctx OSGi context
     */
    public void stop(BundleContext ctx)
    {
        ctx.removeServiceListener(this);
        services.clear();
        this.context = null;
    }
}
