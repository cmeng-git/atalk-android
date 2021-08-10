/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package net.java.sip.communicator.impl.metahistory;

import net.java.sip.communicator.service.metahistory.MetaHistoryService;

import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;

import timber.log.Timber;

/**
 * Activates the MetaHistoryService
 *
 * @author Damian Minkov
 * @author Eng Chong Meng
 */
public class MetaHistoryActivator implements BundleActivator
{
    /**
     * The <tt>MetaHistoryService</tt> reference.
     */
    private MetaHistoryServiceImpl metaHistoryService = null;

    /**
     * Initialize and start meta history
     *
     * @param bundleContext BundleContext
     * @throws Exception if initializing and starting meta history service fails
     */
    public void start(BundleContext bundleContext)
            throws Exception
    {
        // Create and start the meta history service.
        metaHistoryService = new MetaHistoryServiceImpl();
        metaHistoryService.start(bundleContext);
        bundleContext.registerService(MetaHistoryService.class.getName(), metaHistoryService, null);

        Timber.i("Meta History Service ...[REGISTERED]");
    }

    /**
     * Stops this bundle.
     *
     * @param bundleContext the <tt>BundleContext</tt>
     * @throws Exception if the stop operation goes wrong
     */
    public void stop(BundleContext bundleContext)
            throws Exception
    {
        if (metaHistoryService != null)
            metaHistoryService.stop(bundleContext);
    }
}
