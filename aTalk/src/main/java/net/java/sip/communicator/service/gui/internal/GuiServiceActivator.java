/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.gui.internal;

import net.java.sip.communicator.util.ServiceUtils;

import org.atalk.service.resources.ResourceManagementService;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;

/**
 * @author Lubomir Marinov
 * @author Yana Stamcheva
 */
public class GuiServiceActivator implements BundleActivator
{
    /**
     * The <code>BundleContext</code> of the service.
     */
    private static BundleContext bundleContext;

    /**
     * The <code>ResourceManagementService</code>, which gives access to application resources.
     */
    private static ResourceManagementService resourceService;

    /**
     * Returns the <code>BundleContext</code>.
     *
     * @return bundle context
     */
    public static BundleContext getBundleContext()
    {
        return bundleContext;
    }

    /**
     * Initialize and start GUI service
     *
     * @param bundleContext the <code>BundleContext</code>
     */
    public void start(BundleContext bundleContext)
    {
        GuiServiceActivator.bundleContext = bundleContext;
    }

    /**
     * Stops this bundle.
     *
     * @param bundleContext the <code>BundleContext</code>
     */
    public void stop(BundleContext bundleContext)
    {
        if (GuiServiceActivator.bundleContext == bundleContext)
            GuiServiceActivator.bundleContext = null;
    }

    /**
     * Returns the <code>ResourceManagementService</code>, through which we will access all resources.
     *
     * @return the <code>ResourceManagementService</code>, through which we will access all resources.
     */
    public static ResourceManagementService getResources()
    {
        if (resourceService == null) {
            resourceService = ServiceUtils.getService(bundleContext, ResourceManagementService.class);
        }
        return resourceService;
    }
}
