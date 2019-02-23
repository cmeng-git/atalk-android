/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.androidnotification;

import net.java.sip.communicator.service.notification.NotificationService;

import org.osgi.framework.*;

import timber.log.Timber;

/**
 * Bundle adds Android specific notification handlers.
 *
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
public class NotificationActivator implements BundleActivator
{
    /**
     * OSGI bundle context.
     */
    protected static BundleContext bundleContext;

    /**
     * Notification service instance.
     */
    private static NotificationService notificationService;

    /**
     * Vibrate handler instance.
     */
    private VibrateHandlerImpl vibrateHandler;

    /**
     * {@inheritDoc}
     */
    public void start(BundleContext bc)
            throws Exception
    {
        bundleContext = bc;
        // Get the notification service implementation
        ServiceReference notifyReference = bundleContext.getServiceReference(NotificationService.class.getName());

        notificationService = (NotificationService) bundleContext.getService(notifyReference);
        vibrateHandler = new VibrateHandlerImpl();
        notificationService.addActionHandler(vibrateHandler);
        Timber.i("Android notification handler Service...[REGISTERED]");
    }

    /**
     * {@inheritDoc}
     */
    public void stop(BundleContext bc)
            throws Exception
    {
        notificationService.removeActionHandler(vibrateHandler.getActionType());
        Timber.d("Android notification handler Service ...[STOPPED]");
    }
}
