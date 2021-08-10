/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.impl.notification;

import net.java.sip.communicator.service.gui.UIService;
import net.java.sip.communicator.service.notification.*;
import net.java.sip.communicator.service.systray.SystrayService;
import net.java.sip.communicator.util.ServiceUtils;

import org.atalk.service.audionotifier.AudioNotifierService;
import org.atalk.service.configuration.ConfigurationService;
import org.osgi.framework.*;

import timber.log.Timber;

/**
 * The <tt>NotificationActivator</tt> is the activator of the notification bundle.
 *
 * @author Yana Stamcheva
 */
public class NotificationActivator implements BundleActivator
{
    protected static BundleContext bundleContext;

    private static AudioNotifierService audioNotifierService;
    private static SystrayService systrayService;
    private static NotificationService notificationService;

    /**
     * A reference to the <tt>UIService</tt> currently in use in Jitsi.
     */
    private static UIService uiService = null;

    private CommandNotificationHandler commandHandler;
    private LogMessageNotificationHandler logMessageHandler;
    private PopupMessageNotificationHandler popupMessageHandler;
    private SoundNotificationHandler soundHandler;

    /**
     * The <tt>ConfigurationService</tt> registered in {@link #bundleContext} and used by the
     * <tt>NotificationActivator</tt> instance to read and write configuration properties.
     */
    private static ConfigurationService configurationService;

    public void start(BundleContext bc)
            throws Exception
    {
        bundleContext = bc;
        // Get the notification service implementation
        ServiceReference notifReference = bundleContext.getServiceReference(NotificationService.class.getName());
        notificationService = (NotificationService) bundleContext.getService(notifReference);

        commandHandler = new CommandNotificationHandlerImpl();
        logMessageHandler = new LogMessageNotificationHandlerImpl();
        popupMessageHandler = new PopupMessageNotificationHandlerImpl();
        soundHandler = new SoundNotificationHandlerImpl();

        notificationService.addActionHandler(commandHandler);
        notificationService.addActionHandler(logMessageHandler);
        notificationService.addActionHandler(popupMessageHandler);
        notificationService.addActionHandler(soundHandler);

        Timber.i("Notification handler Service ...[REGISTERED]");
    }

    public void stop(BundleContext bc)
            throws Exception
    {
        notificationService.removeActionHandler(commandHandler.getActionType());
        notificationService.removeActionHandler(logMessageHandler.getActionType());
        notificationService.removeActionHandler(popupMessageHandler.getActionType());
        notificationService.removeActionHandler(soundHandler.getActionType());

        Timber.i("Notification handler Service ...[STOPPED]");
    }

    /**
     * Returns the <tt>AudioNotifierService</tt> obtained from the bundle context.
     *
     * @return the <tt>AudioNotifierService</tt> obtained from the bundle context
     */
    public static AudioNotifierService getAudioNotifier()
    {
        if (audioNotifierService == null) {
            ServiceReference serviceReference = bundleContext.getServiceReference(AudioNotifierService.class.getName());

            if (serviceReference != null)
                audioNotifierService = (AudioNotifierService) bundleContext.getService(serviceReference);
        }
        return audioNotifierService;
    }

    /**
     * Returns the <tt>SystrayService</tt> obtained from the bundle context.
     *
     * @return the <tt>SystrayService</tt> obtained from the bundle context
     */
    public static SystrayService getSystray()
    {
        if (systrayService == null) {
            systrayService = ServiceUtils.getService(bundleContext, SystrayService.class);
        }
        return systrayService;
    }

    /**
     * Returns a reference to an UIService implementation currently registered in the bundle context
     * or null if no such implementation was found.
     *
     * @return a reference to an UIService implementation currently registered in the bundle context
     * or null if no such implementation was found.
     */
    public static UIService getUIService()
    {
        if (uiService == null)
            uiService = ServiceUtils.getService(bundleContext, UIService.class);
        return uiService;
    }

    /**
     * Returns a reference to a ConfigurationService implementation currently registered in the bundle context
     * or null if no such implementation was found.
     *
     * @return a currently valid implementation of the ConfigurationService.
     */
    public static ConfigurationService getConfigurationService()
    {
        if (configurationService == null) {
            configurationService = ServiceUtils.getService(bundleContext, ConfigurationService.class);
        }
        return configurationService;
    }
}
