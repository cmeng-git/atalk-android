/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.Dictionary;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Map;

import net.java.sip.communicator.service.gui.ConfigurationForm;
import net.java.sip.communicator.service.notification.NotificationAction;
import net.java.sip.communicator.service.notification.NotificationData;
import net.java.sip.communicator.service.notification.NotificationHandler;
import net.java.sip.communicator.service.notification.NotificationService;
import net.java.sip.communicator.service.notification.PopupMessageNotificationHandler;
import net.java.sip.communicator.service.resources.ResourceManagementServiceUtils;
import net.java.sip.communicator.service.systray.SystrayService;
import net.java.sip.communicator.util.ServiceUtils;

import org.atalk.android.R;
import org.atalk.android.aTalkApp;
import org.atalk.android.gui.aTalk;
import org.atalk.impl.neomedia.device.DeviceConfiguration;
import org.atalk.service.audionotifier.AudioNotifierService;
import org.atalk.service.configuration.ConfigurationService;
import org.atalk.service.fileaccess.FileAccessService;
import org.atalk.service.libjitsi.LibJitsi;
import org.atalk.service.neomedia.MediaService;
import org.atalk.service.resources.ResourceManagementService;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;

import timber.log.Timber;

/**
 * Implements <code>BundleActivator</code> for the neomedia bundle.
 *
 * @author Martin Andre
 * @author Emil Ivov
 * @author Lyubomir Marinov
 * @author Boris Grozev
 * @author Eng Chong Meng
 */
public class NeomediaActivator implements BundleActivator {
    /**
     * Indicates if the audio configuration form should be disabled, i.e. not visible to the user.
     */
    private static final String AUDIO_CONFIG_DISABLED_PROP = "neomedia.AUDIO_CONFIG_DISABLED";

    /**
     * Indicates if the video configuration form should be disabled, i.e. not visible to the user.
     */
    private static final String VIDEO_CONFIG_DISABLED_PROP = "neomedia.VIDEO_CONFIG_DISABLED";

    /**
     * Indicates if the H.264 configuration form should be disabled, i.e. not visible to the user.
     */
    private static final String H264_CONFIG_DISABLED_PROP = "neomedia.h264config.DISABLED";

    /**
     * Indicates if the ZRTP configuration form should be disabled, i.e. not visible to the user.
     */
    private static final String ZRTP_CONFIG_DISABLED_PROP = "neomedia.zrtpconfig.DISABLED";

    /**
     * Indicates if the call recording config form should be disabled, i.e. not visible to the user.
     */
    private static final String CALL_RECORDING_CONFIG_DISABLED_PROP = "neomedia.callrecordingconfig.DISABLED";

    /**
     * The name of the notification pop-up event displayed when the device configuration has changed.
     */
    private static final String DEVICE_CONFIGURATION_HAS_CHANGED = "DeviceConfigurationChanged";

    /**
     * The context in which the one and only <code>NeomediaActivator</code> instance has started executing.
     */
    private static BundleContext bundleContext;

    /**
     * The <code>ConfigurationService</code> registered in {@link #bundleContext} and used by the
     * <code>NeomediaActivator</code> instance to read and write configuration properties.
     */
    private static ConfigurationService configurationService;

    /**
     * The <code>FileAccessService</code> registered in {@link #bundleContext} and used by the
     * <code>NeomediaActivator</code> instance to safely access files.
     */
    private static FileAccessService fileAccessService;

    /**
     * The notification service to pop-up messages.
     */
    private static NotificationService notificationService;

    /**
     * The one and only <code>MediaServiceImpl</code> instance registered in {@link #bundleContext} by
     * the <code>NeomediaActivator</code> instance.
     */
    private static MediaServiceImpl mediaServiceImpl;

    /**
     * The <code>ResourceManagementService</code> registered in {@link #bundleContext} and representing
     * the resources such as internationalized and localized text and images used by the neomedia bundle.
     */
    private static ResourceManagementService resources;

    /**
     * A listener to the click on the popup message concerning device configuration changes.
     */
    private AudioDeviceConfigurationListener deviceConfigurationPropertyChangeListener;

    /*
     * A {@link MediaConfigurationService} instance.
     */
    // private static MediaConfigurationImpl mediaConfiguration;

    /**
     * The audio configuration form used to define the capture/notify/playback audio devices.
     */
    private static ConfigurationForm audioConfigurationForm;

    /**
     * Starts the execution of the neomedia bundle in the specified context.
     *
     * @param bundleContext the context in which the neomedia bundle is to start executing
     *
     * @throws Exception if an error occurs while starting the execution of the neomedia bundle in the specified context
     */
    public void start(BundleContext bundleContext)
            throws Exception {
        if (aTalk.disableMediaServiceOnFault)
            return;


        // MediaService
        NeomediaActivator.bundleContext = bundleContext;
        mediaServiceImpl = (MediaServiceImpl) LibJitsi.getMediaService();
        if (mediaServiceImpl == null) {
            Timber.w("Media Service startup failed - jnlibffmpeg failed to load?");
            return;
        }

        bundleContext.registerService(MediaService.class.getName(), mediaServiceImpl, null);
        Timber.d("Media Service ... [REGISTERED]");

        // mediaConfiguration = new MediaConfigurationImpl();
        // bundleContext.registerService(MediaConfigurationService.class.getName(), getMediaConfiguration(), null);
        // Timber.d("Media Configuration ... [REGISTERED]");

        ConfigurationService cfg = NeomediaActivator.getConfigurationService();
        Dictionary<String, String> mediaProps = new Hashtable<>();
        mediaProps.put(ConfigurationForm.FORM_TYPE, ConfigurationForm.GENERAL_TYPE);

        // If the audio configuration form is disabled don't register it.
        //        if ((cfg == null) || !cfg.getBoolean(AUDIO_CONFIG_DISABLED_PROP, false)) {
        //            audioConfigurationForm = new LazyConfigurationForm(AudioConfigurationPanel.class.getName(),
        //                    getClass().getClassLoader(), "plugin.mediaconfig.AUDIO_ICON", "impl.neomedia.configform.AUDIO", 3);
        //
        //            bundleContext.registerService(ConfigurationForm.class.getName(), audioConfigurationForm, mediaProps);
        //
        //            if (deviceConfigurationPropertyChangeListener == null) {
        //                // Initializes and registers the changed device configuration event for the notification service.
        //                getNotificationService();
        //
        //                deviceConfigurationPropertyChangeListener = new AudioDeviceConfigurationListener();
        //                mediaServiceImpl.getDeviceConfiguration().addPropertyChangeListener(deviceConfigurationPropertyChangeListener);
        //            }
        //        }

        //        // If the video configuration form is disabled don't register it.
        //        if ((cfg == null) || !cfg.getBoolean(VIDEO_CONFIG_DISABLED_PROP, false)) {
        //            bundleContext.registerService(ConfigurationForm.class.getName(),
        //                    new LazyConfigurationForm(VideoConfigurationPanel.class.getName(), etClass().getClassLoader(),
        //                            "plugin.mediaconfig.VIDEO_ICON", "impl.neomedia.configform.VIDEO", 4), mediaProps);
        //        }

        // H.264
        // If the H.264 configuration form is disabled don't register it.
        //        if ((cfg == null) || !cfg.getBoolean(H264_CONFIG_DISABLED_PROP, false)) {
        //            Dictionary<String, String> h264Props
        //                    = new Hashtable<String, String>();
        //
        //            h264Props.put(ConfigurationForm.FORM_TYPE, ConfigurationForm.ADVANCED_TYPE);
        //            bundleContext.registerService(
        //                    ConfigurationForm.class.getName(),
        //                    new LazyConfigurationForm(ConfigurationPanel.class.getName(), getClass().getClassLoader(),
        //                            "plugin.mediaconfig.VIDEO_ICON", "impl.neomedia.configform.H264", -1, true), h264Props);
        //        }

        // ZRTP
        // If the ZRTP configuration form is disabled don't register it.
        //        if ((cfg == null) || !cfg.getBoolean(ZRTP_CONFIG_DISABLED_PROP, false)) {
        //            Dictionary<String, String> securityProps = new Hashtable<String, String>();
        //
        //            securityProps.put(ConfigurationForm.FORM_TYPE, ConfigurationForm.SECURITY_TYPE);
        //            bundleContext.registerService(ConfigurationForm.class.getName(),
        //                    new LazyConfigurationForm(SecurityConfigForm.class.getName(), getClass().getClassLoader(),
        //                            "impl.media.security.zrtp.CONF_ICON", "impl.media.security.zrtp.TITLE", 0), securityProps);
        //        }

        // we use the nist-sdp stack to make parse sdp and we need to set the following property to make
        // sure that it would accept java generated IPv6 addresses that contain address scope zones.
        System.setProperty("gov.nist.core.STRIP_ADDR_SCOPES", "true");

        // AudioNotifierService
        AudioNotifierService audioNotifierService = LibJitsi.getAudioNotifierService();

        audioNotifierService.setMute((cfg == null)
                || !cfg.getBoolean("media.sound.isSoundEnabled", true));
        bundleContext.registerService(AudioNotifierService.class.getName(), audioNotifierService, null);

        Timber.i("Audio Notifier Service ...[REGISTERED]");

        //        Call Recording
        //        If the call recording configuration form is disabled don 't continue.
        //        if ((cfg == null) || !cfg.getBoolean(CALL_RECORDING_CONFIG_DISABLED_PROP, false)) {
        //            Dictionary<String, String> callRecordingProps = new Hashtable<String, String>();
        //
        //            callRecordingProps.put(ConfigurationForm.FORM_TYPE, ConfigurationForm.ADVANCED_TYPE);
        //            bundleContext.registerService(ConfigurationForm.class.getName(),
        //                    new LazyConfigurationForm(CallRecordingConfigForm.class.getName(), getClass().getClassLoader(),
        //                            null, "plugin.callrecordingconfig.CALL_RECORDING_CONFIG", 1100, true), callRecordingProps);
        //        }
    }

    /**
     * Stops the execution of the neomedia bundle in the specified context.
     *
     * @param bundleContext the context in which the neomedia bundle is to stop executing
     *
     * @throws Exception if an error occurs while stopping the execution of the neomedia bundle in the specified context
     */
    public void stop(BundleContext bundleContext)
            throws Exception {
        try {
            if (deviceConfigurationPropertyChangeListener != null) {
                mediaServiceImpl.getDeviceConfiguration()
                        .removePropertyChangeListener(deviceConfigurationPropertyChangeListener);
                if (deviceConfigurationPropertyChangeListener != null) {
                    deviceConfigurationPropertyChangeListener.managePopupMessageListenerRegistration(false);
                    deviceConfigurationPropertyChangeListener = null;
                }
            }
        } finally {
            configurationService = null;
            fileAccessService = null;
            mediaServiceImpl = null;
            resources = null;
        }
    }

    /**
     * Returns a reference to a ConfigurationService implementation currently registered in the
     * bundle context or null if no such implementation was found.
     *
     * @return a currently valid implementation of the ConfigurationService.
     */
    public static ConfigurationService getConfigurationService() {
        if (configurationService == null) {
            configurationService = ServiceUtils.getService(bundleContext, ConfigurationService.class);
        }
        return configurationService;
    }

    /**
     * Returns a reference to a FileAccessService implementation currently registered in the bundle
     * context or null if no such implementation was found.
     *
     * @return a currently valid implementation of the FileAccessService .
     */
    public static FileAccessService getFileAccessService() {
        if (fileAccessService == null) {
            fileAccessService = ServiceUtils.getService(bundleContext, FileAccessService.class);
        }
        return fileAccessService;
    }

    /**
     * Gets the <code>MediaService</code> implementation instance registered by the neomedia bundle.
     *
     * @return the <code>MediaService</code> implementation instance registered by the neomedia bundle
     */
    public static MediaServiceImpl getMediaServiceImpl() {
        return mediaServiceImpl;
    }

    // public static MediaConfigurationService getMediaConfiguration()
    // {
    // return mediaConfiguration;
    // }

    /**
     * Gets the <code>ResourceManagementService</code> instance which represents the resources such as
     * internationalized and localized text and images used by the neomedia bundle.
     *
     * @return the <code>ResourceManagementService</code> instance which represents the resources such
     * as internationalized and localized text and images used by the neomedia bundle
     */
    public static ResourceManagementService getResources() {
        if (resources == null) {
            resources = ResourceManagementServiceUtils.getService(bundleContext);
        }
        return resources;
    }

    /**
     * Returns the <code>NotificationService</code> obtained from the bundle context.
     *
     * @return The <code>NotificationService</code> obtained from the bundle context.
     */
    public static NotificationService getNotificationService() {
        if (notificationService == null) {
            // Get the notification service implementation
            ServiceReference<?> notifReference = bundleContext.getServiceReference(NotificationService.class.getName());

            notificationService = (NotificationService) bundleContext.getService(notifReference);
            if (notificationService != null) {
                // Register a popup message for a device configuration changed notification.
                notificationService.registerDefaultNotificationForEvent(DEVICE_CONFIGURATION_HAS_CHANGED,
                        net.java.sip.communicator.service.notification.NotificationAction.ACTION_POPUP_MESSAGE,
                        "Device configuration has changed", null);
            }
        }
        return notificationService;
    }

    /**
     * A listener to the click on the popup message concerning device configuration changes.
     */
    private static class AudioDeviceConfigurationListener implements PropertyChangeListener/*
     * ,
     * SystrayPopupMessageListener
     */ {
        /**
         * A boolean used to verify that this listener registers only once to the popup message notification handler.
         */
        private boolean isRegisteredToPopupMessageListener = false;

        /**
         * Registers or unregister as a popup message listener to detect when a user click on
         * notification saying that the device configuration has changed.
         *
         * @param enable True to register to the popup message notifcation handler. False to unregister.
         */
        public void managePopupMessageListenerRegistration(boolean enable) {
            Iterator<NotificationHandler> notificationHandlers
                    = notificationService.getActionHandlers(NotificationAction.ACTION_POPUP_MESSAGE).iterator();
            NotificationHandler notificationHandler;
            while (notificationHandlers.hasNext()) {
                notificationHandler = notificationHandlers.next();
                if (notificationHandler instanceof PopupMessageNotificationHandler) {
                    // Register.
                    if (enable) {
                        // ((PopupMessageNotificationHandler) notificationHandler).addPopupMessageListener(this);
                    }
                    // Unregister.
                    else {
                        // ((PopupMessageNotificationHandler) notificationHandler).removePopupMessageListener(this);
                    }
                }
            }
        }

        /**
         * Function called when an audio device is plugged or unplugged.
         *
         * @param event The property change event which may concern the audio device.
         */
        public void propertyChange(PropertyChangeEvent event) {
            if (DeviceConfiguration.PROP_AUDIO_SYSTEM_DEVICES.equals(event.getPropertyName())) {
                NotificationService notificationService = getNotificationService();
                if (notificationService != null) {
                    // Registers only once to the popup message notification handler.
                    if (!isRegisteredToPopupMessageListener) {
                        isRegisteredToPopupMessageListener = true;
                        managePopupMessageListenerRegistration(true);
                    }

                    // Fires the popup notification.
                    ResourceManagementService resources = NeomediaActivator.getResources();
                    Map<String, Object> extras = new HashMap<>();

                    extras.put(NotificationData.POPUP_MESSAGE_HANDLER_TAG_EXTRA, this);
                    notificationService.fireNotification(DEVICE_CONFIGURATION_HAS_CHANGED, SystrayService.NONE_MESSAGE_TYPE,
                            aTalkApp.getResString(R.string.media_configform_audio_device_config_changed),
                            aTalkApp.getResString(R.string.media_configform_audio_device_config_management_click),
                            null, extras);
                }
            }
        }

        /*
         * Indicates that user has clicked on the systray popup message.
         *
         * @param evt the event triggered when user clicks on the systray popup message
         */
        //        public void popupMessageClicked(SystrayPopupMessageEvent evt)
        //        {
        //            // Checks if this event is fired from one click on one of our popup  message.
        //            if (evt.getTag() == deviceConfigurationPropertyChangeListener) {
        //                // Get the UI service
        //                ServiceReference uiReference = bundleContext.getServiceReference(UIService.class.getName());
        //
        //                UIService uiService = (UIService) bundleContext.getService(uiReference);
        //
        //                if (uiService != null) {
        //                    // Shows the audio configuration window.
        //                    ConfigurationContainer configurationContainer = uiService.getConfigurationContainer();
        //                    configurationContainer.setSelected(audioConfigurationForm);
        //                    configurationContainer.setVisible(true);
        //                }
        //            }
        //        }
    }

    public static BundleContext getBundleContext() {
        return bundleContext;
    }
}
