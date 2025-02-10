/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.impl.filehistory;

import net.java.sip.communicator.service.filehistory.FileHistoryService;
import net.java.sip.communicator.service.history.HistoryService;

import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;

import timber.log.Timber;

/**
 * @author Damian Minkov
 * @author Eng Chong Meng
 */
public class FileHistoryActivator implements BundleActivator {
    /**
     * A <code>FileHistoryService</code> service reference.
     */
    private FileHistoryServiceImpl fileHistoryService = null;

    /**
     * Initialize and start file history
     *
     * @param bundleContext BundleContext
     *
     * @throws Exception if initializing and starting file history fails
     */
    public void start(BundleContext bundleContext)
            throws Exception {
        ServiceReference<?> refHistory = bundleContext.getServiceReference(HistoryService.class.getName());
        HistoryService historyService = (HistoryService) bundleContext.getService(refHistory);

        // Create and start the file history service.
        fileHistoryService = new FileHistoryServiceImpl();
        // set the history service
        fileHistoryService.setHistoryService(historyService);
        fileHistoryService.start(bundleContext);
        bundleContext.registerService(FileHistoryService.class.getName(), fileHistoryService, null);

        Timber.d("File History Service ...[REGISTERED]");
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
        if (fileHistoryService != null)
            fileHistoryService.stop(bundleContext);
    }
}
