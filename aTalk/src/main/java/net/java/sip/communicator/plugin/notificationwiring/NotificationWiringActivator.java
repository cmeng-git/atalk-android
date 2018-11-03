/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.plugin.notificationwiring;

import net.java.sip.communicator.service.gui.UIService;
import net.java.sip.communicator.service.notification.NotificationService;
import net.java.sip.communicator.util.Logger;
import net.java.sip.communicator.util.ServiceUtils;

import org.atalk.service.neomedia.MediaService;
import org.atalk.service.resources.ResourceManagementService;
import org.osgi.framework.*;

/**
 * The <tt>NotificationActivator</tt> is the activator of the notification bundle.
 *
 * @author Yana Stamcheva
 */
public class NotificationWiringActivator implements BundleActivator
{
    private final Logger logger = Logger.getLogger(NotificationWiringActivator.class);

    protected static BundleContext bundleContext;
    private static NotificationService notificationService;
    private static ResourceManagementService resourcesService;
    private static UIService uiService = null;
    private static MediaService mediaService;

    /**
     * Returns the <tt>NotificationService</tt> obtained from the bundle context.
     *
     * @return the <tt>NotificationService</tt> obtained from the bundle context
     */
    public static NotificationService getNotificationService()
    {
        return notificationService;
    }

    /**
     * Returns the <tt>ResourceManagementService</tt>, through which we will access all resources.
     *
     * @return the <tt>ResourceManagementService</tt>, through which we will access all resources.
     */
    public static ResourceManagementService getResources()
    {
        if (resourcesService == null) {
            resourcesService = ServiceUtils.getService(bundleContext, ResourceManagementService.class);
        }
        return resourcesService;
    }

    /**
     * Returns the current implementation of the <tt>UIService</tt>.
     *
     * @return the current implementation of the <tt>UIService</tt>
     */
    public static UIService getUIService()
    {
        if (uiService == null) {
            uiService = ServiceUtils.getService(bundleContext, UIService.class);
        }

        return uiService;
    }

    /**
     * Returns an instance of the <tt>MediaService</tt> obtained from the bundle context.
     *
     * @return an instance of the <tt>MediaService</tt> obtained from the bundle context
     */
    public static MediaService getMediaService()
    {
        if (mediaService == null) {
            mediaService = ServiceUtils.getService(bundleContext, MediaService.class);
        }
        return mediaService;
    }

    public void start(BundleContext bc)
            throws Exception
    {
        bundleContext = bc;
        try {
            logger.logEntry();

            // Get the notification service implementation
            ServiceReference notifReference = bundleContext.getServiceReference(NotificationService.class.getName());
            notificationService = (NotificationService) bundleContext.getService(notifReference);
            new NotificationManager().init();
            logger.info("Notification wiring plugin ...[REGISTERED]");
        } finally {
            logger.logExit();
        }
    }

    public void stop(BundleContext bc)
            throws Exception
    {
        logger.info("Notification handler Service ...[STOPPED]");
    }
}
