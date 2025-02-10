/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package net.java.sip.communicator.impl.msghistory;

import net.java.sip.communicator.service.contactlist.MetaContactListService;
import net.java.sip.communicator.service.history.HistoryService;
import net.java.sip.communicator.service.msghistory.MessageHistoryService;
import net.java.sip.communicator.util.ServiceUtils;

import org.atalk.service.configuration.ConfigurationService;
import org.atalk.service.resources.ResourceManagementService;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;

import timber.log.Timber;

/**
 * Activates the MessageHistoryService
 *
 * @author Damian Minkov
 * @author Eng Chong Meng
 */
public class MessageHistoryActivator implements BundleActivator {
    /**
     * The <code>BundleContext</code> of the service.
     */
    static BundleContext bundleContext;
    /**
     * The <code>MessageHistoryService</code> reference.
     */
    private static MessageHistoryServiceImpl msgHistoryService = null;
    /**
     * The <code>ResourceManagementService</code> reference.
     */
    private static ResourceManagementService resourcesService;
    /**
     * The <code>MetaContactListService</code> reference.
     */
    private static MetaContactListService metaCListService;
    /**
     * The <code>ConfigurationService</code> reference.
     */
    private static ConfigurationService configService;

    /**
     * Returns the <code>MetaContactListService</code> obtained from the bundle context.
     *
     * @return the <code>MetaContactListService</code> obtained from the bundle context
     */
    public static MetaContactListService getContactListService() {
        if (metaCListService == null) {
            metaCListService = ServiceUtils.getService(bundleContext, MetaContactListService.class);
        }
        return metaCListService;
    }

    /**
     * Returns the <code>MessageHistoryService</code> registered to the bundle context.
     *
     * @return the <code>MessageHistoryService</code> registered to the bundle context
     */
    public static MessageHistoryServiceImpl getMessageHistoryService() {
        return msgHistoryService;
    }

    /**
     * Returns the <code>ResourceManagementService</code>, through which we will access all resources.
     *
     * @return the <code>ResourceManagementService</code>, through which we will access all resources.
     */
    public static ResourceManagementService getResources() {
        if (resourcesService == null) {
            resourcesService = ServiceUtils.getService(bundleContext, ResourceManagementService.class);
        }
        return resourcesService;
    }

    /**
     * Returns the <code>ConfigurationService</code> obtained from the bundle context.
     *
     * @return the <code>ConfigurationService</code> obtained from the bundle context
     */
    public static ConfigurationService getConfigurationService() {
        if (configService == null) {
            configService = ServiceUtils.getService(bundleContext, ConfigurationService.class);
        }
        return configService;
    }

    /**
     * Initialize and start message history
     *
     * @param bc the BundleContext
     *
     * @throws Exception if initializing and starting message history service fails
     */
    public void start(BundleContext bc)
            throws Exception {
        bundleContext = bc;
        ServiceReference<?> refHistory = bundleContext.getServiceReference(HistoryService.class.getName());
        HistoryService historyService = (HistoryService) bundleContext.getService(refHistory);

        // Create and start the message history service.
        msgHistoryService = new MessageHistoryServiceImpl();
        msgHistoryService.setHistoryService(historyService);
        msgHistoryService.start(bundleContext);

        bundleContext.registerService(MessageHistoryService.class.getName(), msgHistoryService, null);

        Timber.i("Message History Service ...[REGISTERED]");
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
        if (msgHistoryService != null)
            msgHistoryService.stop(bundleContext);
    }
}
