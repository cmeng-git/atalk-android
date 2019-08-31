/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.android.gui.settings;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.*;
import android.text.TextUtils;

import net.java.sip.communicator.service.msghistory.MessageHistoryService;
import net.java.sip.communicator.service.systray.PopupMessageHandler;
import net.java.sip.communicator.service.systray.SystrayService;
import net.java.sip.communicator.util.ConfigurationUtils;
import net.java.sip.communicator.util.ServiceUtils;

import org.atalk.android.R;
import org.atalk.android.aTalkApp;
import org.atalk.android.gui.AndroidGUIActivator;
import org.atalk.android.gui.aTalk;
import org.atalk.android.gui.settings.util.SummaryMapper;
import org.atalk.android.gui.util.*;
import org.atalk.android.util.java.awt.Dimension;
import org.atalk.impl.neomedia.MediaServiceImpl;
import org.atalk.impl.neomedia.NeomediaActivator;
import org.atalk.impl.neomedia.device.*;
import org.atalk.impl.neomedia.device.util.AndroidCamera;
import org.atalk.impl.neomedia.device.util.CameraUtils;
import org.atalk.service.configuration.ConfigurationService;
import org.atalk.service.osgi.OSGiActivity;
import org.atalk.service.osgi.OSGiPreferenceFragment;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;

import java.util.*;

import javax.media.MediaLocator;

import timber.log.Timber;

/**
 * <tt>Activity</tt> implements Jitsi settings.
 *
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
public class SettingsActivity extends OSGiActivity
{
    // PreferenceScreen and PreferenceCategories
    static private final String PC_KEY_MEDIA_CALL = aTalkApp.getResString(R.string.pref_cat_settings_media_call);
    static private final String PC_KEY_CALL = aTalkApp.getResString(R.string.pref_cat_settings_call);

    // Advance video/audio settings
    static private final String PC_KEY_VIDEO = aTalkApp.getResString(R.string.pref_cat_settings_video);
    static private final String PC_KEY_AUDIO = aTalkApp.getResString(R.string.pref_cat_settings_audio);
    static private final String PC_KEY_ADVANCED = aTalkApp.getResString(R.string.pref_cat_settings_advanced);


    // Interface Display settings
    static private final String P_KEY_LOCALE = aTalkApp.getResString(R.string.pref_key_locale);
    static public final String P_KEY_THEME = aTalkApp.getResString(R.string.pref_key_theme);

    static private final String P_KEY_WEB_PAGE = aTalkApp.getResString(R.string.pref_key_webview_PAGE);

    // Message section
    static private final String P_KEY_AUTO_START = aTalkApp.getResString(R.string.pref_key_atalk_auto_start);
    static private final String P_KEY_LOG_CHAT_HISTORY = aTalkApp.getResString(R.string.pref_key_history_logging);
    static private final String P_KEY_SHOW_HISTORY = aTalkApp.getResString(R.string.pref_key_show_history);
    static private final String P_KEY_HISTORY_SIZE = aTalkApp.getResString(R.string.pref_key_chat_history_size);
    static private final String P_KEY_MESSAGE_DELIVERY_RECEIPT = aTalkApp.getResString(R.string.pref_key_message_delivery_receipt);
    static private final String P_KEY_CHAT_STATE_NOTIFICATIONS = aTalkApp.getResString(R.string.pref_key_chat_state_notifications);
    static private final String P_KEY_XFER_THUMBNAIL_PREVIEW = aTalkApp.getResString(R.string.pref_key_send_thumbnail);
    static private final String P_KEY_AUTO_ACCEPT_FILE = aTalkApp.getResString(R.string.pref_key_auto_accept_file);
    static private final String P_KEY_PRESENCE_SUBSCRIBE_MODE = aTalkApp.getResString(R.string.pref_key_presence_subscribe_mode);

    // static private final String P_KEY_AUTO_UPDATE_CHECK_ENABLE
    //      = aTalkApp.getResString(R.string.pref_key_auto_update_check_enable);

    /*
     * Chat alerter is not implemented on Android
     * static private final String P_KEY_CHAT_ALERTS = aTalkApp.getResString(R.string.pref_key_chat_alerts);
     */

    // Notifications
    static private final String P_KEY_POPUP_HANDLER = aTalkApp.getResString(R.string.pref_key_popup_handler);

    // Call section
    static private final String P_KEY_NORMALIZE_PNUMBER = aTalkApp.getResString(R.string.pref_key_normalize_pnumber);
    static private final String P_KEY_ACCEPT_ALPHA_PNUMBERS = aTalkApp.getResString(R.string.pref_key_accept_alpha_pnumbers);

    // Audio settings
    static private final String P_KEY_AUDIO_ECHO_CANCEL = aTalkApp.getResString(R.string.pref_key_audio_echo_cancel);
    static private final String P_KEY_AUDIO_AGC = aTalkApp.getResString(R.string.pref_key_audio_agc);
    static private final String P_KEY_AUDIO_DENOISE = aTalkApp.getResString(R.string.pref_key_audio_denoise);

    // Video settings
    static private final String P_KEY_VIDEO_CAMERA = aTalkApp.getResString(R.string.pref_key_video_camera);
    // Hardware encoding(API16)
    static private final String P_KEY_VIDEO_HW_ENCODE = aTalkApp.getResString(R.string.pref_key_video_hw_encode);
    // Direct surface encoding(hw encoding required and API18)
    static private final String P_KEY_VIDEO_ENC_DIRECT_SURFACE = aTalkApp.getResString(R.string.pref_key_video_surface_encode);
    // Hardware decoding(API16)
    static private final String P_KEY_VIDEO_HW_DECODE = aTalkApp.getResString(R.string.pref_key_video_hw_decode);
    // Video resolutions
    static private final String P_KEY_VIDEO_RES = aTalkApp.getResString(R.string.pref_key_video_resolution);
    // Video advanced settings
    static private final String P_KEY_VIDEO_LIMIT_FPS = aTalkApp.getResString(R.string.pref_key_video_limit_fps);
    static private final String P_KEY_VIDEO_TARGET_FPS = aTalkApp.getResString(R.string.pref_key_video_target_fps);
    static private final String P_KEY_VIDEO_MAX_BANDWIDTH = aTalkApp.getResString(R.string.pref_key_video_max_bandwidth);
    static private final String P_KEY_VIDEO_BITRATE = aTalkApp.getResString(R.string.pref_key_video_bitrate);

    // User option property names
    public static final String AUTO_UPDATE_CHECK_ENABLE = "user.AUTO_UPDATE_CHECK_ENABLE";

    /**
     * {@inheritDoc}
     */
    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);

        // We do not allow opening settings if there is a call currently active
        if (AndroidCallUtil.checkCallInProgress(this))
            return;

        if (savedInstanceState == null) {
            // Display the fragment as the main content.
            getFragmentManager().beginTransaction().replace(android.R.id.content, new SettingsFragment()).commit();
        }
    }

    /**
     * The preferences fragment implements Jitsi settings.
     */
    public static class SettingsFragment extends OSGiPreferenceFragment
            implements SharedPreferences.OnSharedPreferenceChangeListener
    {
        /**
         * The device configuration
         */
        private DeviceConfiguration deviceConfig;

        private AudioSystem audioSystem;

        /**
         * Summary mapper used to display preferences values as summaries.
         */
        private SummaryMapper summaryMapper = new SummaryMapper();

        /**
         * {@inheritDoc}
         */
        @Override
        public void onCreate(Bundle savedInstanceState)
        {
            super.onCreate(savedInstanceState);
            addPreferencesFromResource(R.xml.preferences);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void onStart()
        {
            super.onStart();

            // init display locale
            initLocale();

            initWebPagePreference();
            // Init display theme - not implemented
            // aTalkApp.initTheme();

            // Messages section
            initMessagesPreferences();

            // Notifications section
            initNotificationPreferences();

            if (!aTalk.disableMediaServiceOnFault) {
                // Call section
                initCallPreferences();

                // Audio section
                initAudioPreferences();

                // Video section
                initVideoPreferences();
            }
            else {
                disableMediaOptions();
            }

            SharedPreferences shPrefs = getPreferenceManager().getSharedPreferences();
            shPrefs.registerOnSharedPreferenceChangeListener(this);
            shPrefs.registerOnSharedPreferenceChangeListener(summaryMapper);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void onStop()
        {
            SharedPreferences shPrefs = getPreferenceManager().getSharedPreferences();

            shPrefs.unregisterOnSharedPreferenceChangeListener(this);
            shPrefs.unregisterOnSharedPreferenceChangeListener(summaryMapper);
            super.onStop();
        }

        /**
         * Initialize web default access page
         */
        private void initWebPagePreference() {
            // Updates displayed history size summary.
            EditTextPreference webPagePref = (EditTextPreference) findPreference(P_KEY_WEB_PAGE);
            webPagePref.setText(ConfigurationUtils.getWebPage());
            updateWebPageSummary();
        }

        private void updateWebPageSummary() {
            EditTextPreference webPagePref = (EditTextPreference) findPreference(P_KEY_WEB_PAGE);
            webPagePref.setSummary(ConfigurationUtils.getWebPage());
        }

        /**
         * Initialize interface locale
         */
        protected void initLocale()
        {
            // Immutable empty {@link CharSequence} array
            CharSequence[] EMPTY_CHAR_SEQUENCE_ARRAY = new CharSequence[0];
            final ListPreference localePreference = (ListPreference) findPreference(P_KEY_LOCALE);

            List<CharSequence> entryVector = new ArrayList<>(Arrays.asList(localePreference.getEntries()));
            List<CharSequence> entryValueVector = new ArrayList<>(Arrays.asList(localePreference.getEntryValues()));
            String[] supportedLanguages = getResources().getStringArray(R.array.supported_languages);
            Set<String> supportedLanguageSet = new HashSet<>(Arrays.asList(supportedLanguages));
            for (int i = entryVector.size() - 1; i > -1; --i) {
                if (!supportedLanguageSet.contains(entryValueVector.get(i).toString())) {
                    entryVector.remove(i);
                    entryValueVector.remove(i);
                }
            }

            CharSequence[] entries = entryVector.toArray(EMPTY_CHAR_SEQUENCE_ARRAY);
            CharSequence[] entryValues = entryValueVector.toArray(EMPTY_CHAR_SEQUENCE_ARRAY);
            String language = aTalk.getATLanguage();

            localePreference.setEntries(entries);
            localePreference.setEntryValues(entryValues);
            localePreference.setValue(language);
            localePreference.setSummary(localePreference.getEntry());

            // summaryMapper not working for initLocal ?? so use this instead
            localePreference.setOnPreferenceChangeListener((preference, value) -> {
                String language1 = value.toString();
                localePreference.setValue(language1);
                localePreference.setSummary(localePreference.getEntry());

                AndroidGUIActivator.getConfigurationService().setProperty(P_KEY_LOCALE, language1);
                aTalk.setATLanguage(language1);
                aTalk.setLanguage(aTalkApp.getGlobalContext(), language1);
                aTalk.setPrefChange(true);
                return true;
            });
        }

        // Disable all media options when MediaServiceImpl is not initialized due to text-relocation in ffmpeg
        private void disableMediaOptions()
        {
            PreferenceScreen preferenceScreen = getPreferenceScreen();
            PreferenceCategory myPrefCat = (PreferenceCategory) findPreference(PC_KEY_MEDIA_CALL);
            if (myPrefCat != null)
                preferenceScreen.removePreference(myPrefCat);

            myPrefCat = (PreferenceCategory) findPreference(PC_KEY_CALL);
            if (myPrefCat != null)
                preferenceScreen.removePreference(myPrefCat);

            // android OS cannot support removal of nested PreferenceCategory, so just disable all advance settings
            myPrefCat = (PreferenceCategory) findPreference(PC_KEY_ADVANCED);
            if (myPrefCat != null)
                preferenceScreen.removePreference(myPrefCat);

            // myPrefCat = (PreferenceCategory) findPreference(PC_KEY_VIDEO);
            // if (myPrefCat != null) {
            //     preferenceScreen.removePreference(myPrefCat);
            // }

            // myPrefCat = (PreferenceCategory) findPreference(PC_KEY_AUDIO);
            // if (myPrefCat != null) {
            //     preferenceScreen.removePreference(myPrefCat);
            // }
        }

        /**
         * Initializes messages section
         */
        private void initMessagesPreferences()
        {
            // mhs may be null if user access settings before the mhs service is properly setup
            MessageHistoryService mhs = AndroidGUIActivator.getMessageHistoryService();
            boolean isHistoryLoggingEnabled = (mhs != null) && mhs.isHistoryLoggingEnabled();
            PreferenceUtil.setCheckboxVal(getPreferenceScreen(), P_KEY_LOG_CHAT_HISTORY, isHistoryLoggingEnabled);

            PreferenceUtil.setCheckboxVal(getPreferenceScreen(), P_KEY_SHOW_HISTORY, ConfigurationUtils.isHistoryShown());

            // Updates displayed history size summary.
            EditTextPreference historySizePref = (EditTextPreference) findPreference(P_KEY_HISTORY_SIZE);
            historySizePref.setText("" + ConfigurationUtils.getChatHistorySize());
            updateHistorySizeSummary();

            PreferenceUtil.setCheckboxVal(getPreferenceScreen(), P_KEY_AUTO_START,
                    ConfigurationUtils.isAutoStartEnable());

            PreferenceUtil.setCheckboxVal(getPreferenceScreen(), P_KEY_MESSAGE_DELIVERY_RECEIPT,
                    ConfigurationUtils.isSendMessageDeliveryReceipt());

            PreferenceUtil.setCheckboxVal(getPreferenceScreen(), P_KEY_CHAT_STATE_NOTIFICATIONS,
                    ConfigurationUtils.isSendChatStateNotifications());

            PreferenceUtil.setCheckboxVal(getPreferenceScreen(), P_KEY_XFER_THUMBNAIL_PREVIEW,
                    ConfigurationUtils.isSendThumbnail());

            PreferenceUtil.setCheckboxVal(getPreferenceScreen(), P_KEY_PRESENCE_SUBSCRIBE_MODE,
                    ConfigurationUtils.isPresenceSubscribeAuto());

            initAutoAcceptFileSize();
            // PreferenceUtil.setCheckboxVal(this, P_KEY_CHAT_ALERTS, ConfigurationUtils.isAlerterEnabled());
        }

        /**
         * Updates displayed history size summary.
         */
        private void updateHistorySizeSummary()
        {
            EditTextPreference historySizePref = (EditTextPreference) findPreference(P_KEY_HISTORY_SIZE);
            historySizePref.setSummary(getString(R.string.service_gui_settings_CHAT_HISTORY_SUMMARY,
                    ConfigurationUtils.getChatHistorySize()));
        }

        /**
         * Initialize auto accept file size
         */
        protected void initAutoAcceptFileSize()
        {
            final ListPreference fileSizeList = (ListPreference) findPreference(P_KEY_AUTO_ACCEPT_FILE);
            fileSizeList.setEntries(R.array.filesizes);
            fileSizeList.setEntryValues(R.array.filesizes_values);
            long filesSize = ConfigurationUtils.getAutoAcceptFileSize();
            fileSizeList.setValue(String.valueOf(filesSize));
            fileSizeList.setSummary(fileSizeList.getEntry());

            // summaryMapper not working for initLocal ?? so use this instead
            fileSizeList.setOnPreferenceChangeListener((preference, value) -> {
                String fileSize = value.toString();
                fileSizeList.setValue(fileSize);
                fileSizeList.setSummary(fileSizeList.getEntry());

                ConfigurationUtils.setAutoAcceptFileSizeSize(Integer.parseInt(fileSize));
                return true;
            });
        }

        /**
         * Initializes notifications section
         */
        private void initNotificationPreferences()
        {
            ConfigurationService cfg = AndroidGUIActivator.getConfigurationService();

            // Remove for android play store release
            // PreferenceUtil.setCheckboxVal(getPreferenceScreen(), P_KEY_AUTO_UPDATE_CHECK_ENABLE,
            //		cfg.getBoolean(AUTO_UPDATE_CHECK_ENABLE, true));

            BundleContext bc = AndroidGUIActivator.bundleContext;
            ServiceReference[] handlerRefs = ServiceUtils.getServiceReferences(bc, PopupMessageHandler.class);
            if (handlerRefs == null) {
                Timber.w("No popup handlers found");
                handlerRefs = new ServiceReference[0]; // Collections.emptyList();
            }

            String[] names = new String[handlerRefs.length + 1]; // +1 Auto
            String[] values = new String[handlerRefs.length + 1];
            names[0] = getString(R.string.impl_popup_auto);
            values[0] = "Auto";
            int selectedIdx = 0; // Auto by default

            String configuredHandler = (String) cfg.getProperty("systray.POPUP_HANDLER");
            int idx = 1;
            for (ServiceReference<PopupMessageHandler> ref : handlerRefs) {
                PopupMessageHandler handler = bc.getService(ref);

                names[idx] = handler.toString();
                values[idx] = handler.getClass().getName();

                if (configuredHandler != null && configuredHandler.equals(handler.getClass().getName())) {
                    selectedIdx = idx;
                }
            }

            // Configures ListPreference
            ListPreference handlerList = (ListPreference) findPreference(P_KEY_POPUP_HANDLER);
            handlerList.setEntries(names);
            handlerList.setEntryValues(values);
            handlerList.setValueIndex(selectedIdx);
            // Summaries mapping
            summaryMapper.includePreference(handlerList, "Auto");
        }

        /**
         * Initializes call section
         */
        private void initCallPreferences()
        {
            PreferenceUtil.setCheckboxVal(getPreferenceScreen(), P_KEY_NORMALIZE_PNUMBER,
                    ConfigurationUtils.isNormalizePhoneNumber());
            PreferenceUtil.setCheckboxVal(getPreferenceScreen(), P_KEY_ACCEPT_ALPHA_PNUMBERS,
                    ConfigurationUtils.acceptPhoneNumberWithAlphaChars());

            MediaServiceImpl mediaServiceImpl = NeomediaActivator.getMediaServiceImpl();
            if (mediaServiceImpl != null) {
                this.deviceConfig = mediaServiceImpl.getDeviceConfiguration();
                this.audioSystem = deviceConfig.getAudioSystem();
            }
        }

        /**
         * Initializes video preferences part.
         */
        private void initVideoPreferences()
        {
            AndroidCamera[] cameras = AndroidCamera.getCameras();
            String[] names = new String[cameras.length];
            String[] values = new String[cameras.length];
            for (int i = 0; i < cameras.length; i++) {
                names[i] = cameras[i].getName();
                values[i] = cameras[i].getLocator().toString();
            }

            ListPreference cameraList = (ListPreference) findPreference(P_KEY_VIDEO_CAMERA);
            cameraList.setEntries(names);
            cameraList.setEntryValues(values);

            // Get camera from configuration
            AndroidCamera currentCamera = AndroidCamera.getSelectedCameraDevInfo();
            if (currentCamera != null)
                cameraList.setValue(currentCamera.getLocator().toString());

            updateHwCodecStatus(currentCamera);

            // Resolutions
            int resolutionSize = CameraUtils.PREFERRED_SIZES.length;
            String[] resolutionValues = new String[resolutionSize];
            for (int i = 0; i < resolutionSize; i++) {
                resolutionValues[i] = resToStr(CameraUtils.PREFERRED_SIZES[i]);
            }

            ListPreference resList = (ListPreference) findPreference(P_KEY_VIDEO_RES);
            resList.setEntries(resolutionValues);
            resList.setEntryValues(resolutionValues);

            // Init current resolution
            resList.setValue(resToStr(deviceConfig.getVideoSize()));

            // Frame rate
            String defaultFpsStr = "20";
            CheckBoxPreference limitFpsPref = (CheckBoxPreference) findPreference(P_KEY_VIDEO_LIMIT_FPS);
            int targetFps = deviceConfig.getFrameRate();
            limitFpsPref.setChecked(targetFps != -1);

            EditTextPreference targetFpsPref = (EditTextPreference) findPreference(P_KEY_VIDEO_TARGET_FPS);
            targetFpsPref.setText(targetFps != DeviceConfiguration.DEFAULT_VIDEO_FRAMERATE
                    ? Integer.toString(targetFps) : defaultFpsStr);

            // Max bandwidth
            int videoMaxBandwith = deviceConfig.getVideoRTPPacingThreshold();
            // Accord the current value with the maximum allowed value. Fixes existing
            // configurations that have been set to a number larger than the advised maximum value.
            videoMaxBandwith = ((videoMaxBandwith > 999) ? 999 : videoMaxBandwith);

            EditTextPreference maxBWPref = (EditTextPreference) findPreference(P_KEY_VIDEO_MAX_BANDWIDTH);
            maxBWPref.setText(Integer.toString(videoMaxBandwith));

            // Video bitrate
            int bitrate = deviceConfig.getVideoBitrate();
            EditTextPreference bitratePref = (EditTextPreference) findPreference(P_KEY_VIDEO_BITRATE);
            bitratePref.setText(Integer.toString(bitrate));

            // Summaries mapping
            summaryMapper.includePreference(cameraList, getString(R.string.service_gui_settings_NO_CAMERA));
            summaryMapper.includePreference(resList, "1280x720");
            summaryMapper.includePreference(targetFpsPref, defaultFpsStr);
            summaryMapper.includePreference(maxBWPref, Integer.toString(DeviceConfiguration.DEFAULT_VIDEO_RTP_PACING_THRESHOLD));
            summaryMapper.includePreference(bitratePref, Integer.toString(DeviceConfiguration.DEFAULT_VIDEO_BITRATE));
        }

        /**
         * Converts resolution to string.
         *
         * @param d resolution as <tt>Dimension</tt>
         * @return resolution string.
         */
        private static String resToStr(Dimension d)
        {
            return ((int) d.getWidth()) + "x" + ((int) d.getHeight());
        }

        /**
         * Selects resolution from supported resolutions list for given string.
         *
         * @param resStr resolution string created with method {@link #resToStr(Dimension)}.
         * @return resolution <tt>Dimension</tt> for given string representation created with method
         * {@link #resToStr(Dimension)}
         */
        private static Dimension resolutionForStr(String resStr)
        {
            Dimension[] resolutions = MediaRecorderSystem.SUPPORTED_SIZES;
            for (Dimension resolution : resolutions) {
                if (resToStr(resolution).equals(resStr))
                    return resolution;
            }
            // "Auto" string won't match the defined resolution strings so will return default for auto
            return new Dimension(DeviceConfiguration.DEFAULT_VIDEO_WIDTH, DeviceConfiguration.DEFAULT_VIDEO_HEIGHT);
        }

        /**
         * Initializes audio settings.
         */
        private void initAudioPreferences()
        {
            AudioSystem audioSystem = deviceConfig.getAudioSystem();
            int audioSystemFeatures = audioSystem.getFeatures();

            // Echo cancellation
            CheckBoxPreference echoCancelPRef = (CheckBoxPreference) findPreference(P_KEY_AUDIO_ECHO_CANCEL);
            boolean hasEchoFeature = (AudioSystem.FEATURE_ECHO_CANCELLATION & audioSystemFeatures) != 0;
            echoCancelPRef.setEnabled(hasEchoFeature);
            echoCancelPRef.setChecked(hasEchoFeature && audioSystem.isEchoCancel());

            // Automatic gain control
            CheckBoxPreference agcPRef = (CheckBoxPreference) findPreference(P_KEY_AUDIO_AGC);
            boolean hasAgcFeature = (AudioSystem.FEATURE_AGC & audioSystemFeatures) != 0;
            agcPRef.setEnabled(hasAgcFeature);
            agcPRef.setChecked(hasAgcFeature && audioSystem.isAutomaticGainControl());

            // Denoise
            CheckBoxPreference denoisePref = (CheckBoxPreference) findPreference(P_KEY_AUDIO_DENOISE);
            boolean hasDenoiseFeature = (AudioSystem.FEATURE_DENOISE & audioSystemFeatures) != 0;
            denoisePref.setEnabled(hasDenoiseFeature);
            denoisePref.setChecked(hasDenoiseFeature && audioSystem.isDenoise());
        }

        /**
         * Updates preferences enabled status based on selected camera device.
         *
         * @param selectedCamera currently selected camera device.
         */
        private void updateHwCodecStatus(AndroidCamera selectedCamera)
        {
            if (!AndroidUtils.hasAPI(16))
                return;

            // MediaCodecs only work with AndroidCameraSystem(at least for now)
            boolean enableMediaCodecs = selectedCamera != null
                    && DeviceSystem.LOCATOR_PROTOCOL_ANDROIDCAMERA.equals(selectedCamera.getCameraProtocol());

            findPreference(P_KEY_VIDEO_HW_ENCODE).setEnabled(enableMediaCodecs);
            findPreference(P_KEY_VIDEO_ENC_DIRECT_SURFACE).setEnabled(AndroidUtils.hasAPI(18));
            findPreference(P_KEY_VIDEO_HW_DECODE).setEnabled(enableMediaCodecs);
        }

        /**
         * Retrieves currently registered <tt>PopupMessageHandler</tt> for given <tt>clazz</tt> name.
         *
         * @param clazz the class name of <tt>PopupMessageHandler</tt> implementation.
         * @return implementation of <tt>PopupMessageHandler</tt> for given class name registered in OSGI context.
         */
        private PopupMessageHandler getHandlerForClassName(String clazz)
        {
            BundleContext bc = AndroidGUIActivator.bundleContext;
            ServiceReference[] handlerRefs = ServiceUtils.getServiceReferences(bc, PopupMessageHandler.class);

            if (handlerRefs == null)
                return null;

            for (ServiceReference<PopupMessageHandler> sRef : handlerRefs) {
                PopupMessageHandler handler = bc.getService(sRef);
                if (handler.getClass().getName().equals(clazz))
                    return handler;
            }
            return null;
        }

        /**
         * {@inheritDoc}
         */
        public void onSharedPreferenceChanged(SharedPreferences shPreferences, String key)
        {
            if (key.equals(P_KEY_LOG_CHAT_HISTORY)) {
                MessageHistoryService mhs = AndroidGUIActivator.getMessageHistoryService();
                mhs.setHistoryLoggingEnabled(shPreferences.getBoolean(P_KEY_LOG_CHAT_HISTORY,
                        mhs.isHistoryLoggingEnabled()));
            }
            else if (key.equals(P_KEY_SHOW_HISTORY)) {
                ConfigurationUtils.setHistoryShown(shPreferences.getBoolean(P_KEY_SHOW_HISTORY,
                        ConfigurationUtils.isHistoryShown()));
            }
            else if (key.equals(P_KEY_HISTORY_SIZE)) {
                String intStr = shPreferences.getString(P_KEY_HISTORY_SIZE,
                        Integer.toString(ConfigurationUtils.getChatHistorySize()));
                assert intStr != null;
                ConfigurationUtils.setChatHistorySize(Integer.parseInt(intStr));
                updateHistorySizeSummary();
            }
            else if (key.equals(P_KEY_WEB_PAGE)) {
                String wpStr = shPreferences.getString(P_KEY_WEB_PAGE, ConfigurationUtils.getWebPage());
                ConfigurationUtils.setWebPage(wpStr);
                updateWebPageSummary();
            }
            else if (key.equals(P_KEY_AUTO_START)) {
                ConfigurationUtils.setAutoStart(shPreferences.getBoolean(
                        P_KEY_AUTO_START, ConfigurationUtils.isAutoStartEnable()));
            }
            else if (key.equals(P_KEY_MESSAGE_DELIVERY_RECEIPT)) {
                ConfigurationUtils.setSendMessageDeliveryReceipt(shPreferences.getBoolean(
                        P_KEY_MESSAGE_DELIVERY_RECEIPT, ConfigurationUtils.isSendMessageDeliveryReceipt()));
            }
            else if (key.equals(P_KEY_CHAT_STATE_NOTIFICATIONS)) {
                ConfigurationUtils.setSendChatStateNotifications(shPreferences.getBoolean(
                        P_KEY_CHAT_STATE_NOTIFICATIONS, ConfigurationUtils.isSendChatStateNotifications()));
            }
            else if (key.equals(P_KEY_XFER_THUMBNAIL_PREVIEW)) {
                ConfigurationUtils.setSendThumbnail(shPreferences.getBoolean(
                        P_KEY_XFER_THUMBNAIL_PREVIEW, ConfigurationUtils.isSendThumbnail()));
            }
            else if (key.equals(P_KEY_PRESENCE_SUBSCRIBE_MODE)) {
                ConfigurationUtils.setPresenceSubscribeAuto(shPreferences.getBoolean(
                        P_KEY_PRESENCE_SUBSCRIBE_MODE, ConfigurationUtils.isPresenceSubscribeAuto()));
            }

            // Disable for android play store
            /* else if (key.equals(P_KEY_AUTO_UPDATE_CHECK_ENABLE)) {
				Boolean isEnable = shPreferences.getBoolean(P_KEY_AUTO_UPDATE_CHECK_ENABLE, true);
				AndroidGUIActivator.getConfigurationService().setProperty(AUTO_UPDATE_CHECK_ENABLE, isEnable);

				// Perform software version update check on first launch
				Intent intent = new Intent(this.getActivity(), OnlineUpdateService.class);
				if (isEnable)
					intent.setAction(OnlineUpdateService.ACTION_AUTO_UPDATE_START);
				else
					intent.setAction(OnlineUpdateService.ACTION_AUTO_UPDATE_STOP);
				this.getActivity().startService(intent);
			}*/

            /*
             * Chat alerter is not implemented on Android
             * else if(key.equals(P_KEY_CHAT_ALERTS)) {
             *  ConfigurationUtils.setAlerterEnabled( shPreferences.getBoolean( P_KEY_CHAT_ALERTS,
             *  ConfigurationUtils.isAlerterEnabled()));
             * }
             */
            else if (key.equals(P_KEY_POPUP_HANDLER)) {
                String handler = shPreferences.getString(P_KEY_POPUP_HANDLER, "Auto");
                SystrayService systray = AndroidGUIActivator.getSystrayService();
                if ("Auto".equals(handler)) {
                    // "Auto" selected. Delete the user's preference and select the best available handler.
                    ConfigurationUtils.setPopupHandlerConfig(null);
                    systray.selectBestPopupMessageHandler();
                }
                else {
                    ConfigurationUtils.setPopupHandlerConfig(handler);
                    PopupMessageHandler handlerInstance = getHandlerForClassName(handler);
                    if (handlerInstance == null) {
                        Timber.w("No handler found for name: %s", handler);
                    }
                    else {
                        systray.setActivePopupMessageHandler(handlerInstance);
                    }
                }
            }
            // Normalize phone number
            else if (key.equals(P_KEY_NORMALIZE_PNUMBER)) {
                ConfigurationUtils.setNormalizePhoneNumber(shPreferences.getBoolean(P_KEY_NORMALIZE_PNUMBER, true));
            }
            else if (key.equals(P_KEY_ACCEPT_ALPHA_PNUMBERS)) {
                // Accept alphanumeric characters in phone number
                ConfigurationUtils.setAcceptPhoneNumberWithAlphaChars(
                        shPreferences.getBoolean(P_KEY_ACCEPT_ALPHA_PNUMBERS, true));
            }
            // Echo cancellation
            else if (key.equals(P_KEY_AUDIO_ECHO_CANCEL)) {
                audioSystem.setEchoCancel(shPreferences.getBoolean(P_KEY_AUDIO_ECHO_CANCEL, true));
            }
            // Auto gain control
            else if (key.equals(P_KEY_AUDIO_AGC)) {
                audioSystem.setAutomaticGainControl(shPreferences.getBoolean(P_KEY_AUDIO_AGC, true));
            }
            // Noise reduction
            else if (key.equals(P_KEY_AUDIO_DENOISE)) {
                audioSystem.setDenoise(shPreferences.getBoolean(P_KEY_AUDIO_DENOISE, true));
            }
            // Camera
            else if (key.equals(P_KEY_VIDEO_CAMERA)) {
                String cameraName = shPreferences.getString(P_KEY_VIDEO_CAMERA, null);
                updateHwCodecStatus(AndroidCamera.setSelectedCamera(new MediaLocator(cameraName)));
            }
            // Video resolution
            else if (key.equals(P_KEY_VIDEO_RES)) {
                String resStr = shPreferences.getString(P_KEY_VIDEO_RES, null);
                Dimension videoRes = resolutionForStr(resStr);
                deviceConfig.setVideoSize(videoRes);
            }
            // Frame rate
            else if (key.equals(P_KEY_VIDEO_LIMIT_FPS) || key.equals(P_KEY_VIDEO_TARGET_FPS)) {
                boolean isLimitOn = shPreferences.getBoolean(P_KEY_VIDEO_LIMIT_FPS, false);
                if (isLimitOn) {
                    EditTextPreference fpsPref = (EditTextPreference) findPreference(P_KEY_VIDEO_TARGET_FPS);
                    String fpsStr = fpsPref.getText();
                    if (!TextUtils.isEmpty(fpsStr)) {
                        int fps = Integer.parseInt(fpsStr);
                        if (fps > 30) {
                            fps = 30;
                        }
                        else if (fps < 5) {
                            fps = 5;
                        }
                        deviceConfig.setFrameRate(fps);
                        fpsPref.setText(Integer.toString(fps));
                    }
                }
                else {
                    deviceConfig.setFrameRate(DeviceConfiguration.DEFAULT_VIDEO_FRAMERATE);
                }
            }
            // Max bandwidth
            else if (key.equals(P_KEY_VIDEO_MAX_BANDWIDTH)) {
                String resStr = shPreferences.getString(P_KEY_VIDEO_MAX_BANDWIDTH, null);
                if (!TextUtils.isEmpty(resStr)) {
                    int maxBw = Integer.parseInt(resStr);
                    if (maxBw > 999) {
                        maxBw = 999;
                    }
                    else if (maxBw < 1) {
                        maxBw = 1;
                    }
                    deviceConfig.setVideoRTPPacingThreshold(maxBw);
                }
                else {
                    deviceConfig.setVideoRTPPacingThreshold(DeviceConfiguration.DEFAULT_VIDEO_RTP_PACING_THRESHOLD);
                }
                ((EditTextPreference) findPreference(P_KEY_VIDEO_MAX_BANDWIDTH))
                        .setText(Integer.toString(deviceConfig.getVideoRTPPacingThreshold()));
            }
            // Video bit rate
            else if (key.equals(P_KEY_VIDEO_BITRATE)) {
                String bitrateStr = shPreferences.getString(P_KEY_VIDEO_BITRATE, "");
                int bitrate = 0;
                if (bitrateStr != null) {
                    bitrate = !TextUtils.isEmpty(bitrateStr)
                            ? Integer.parseInt(bitrateStr) : DeviceConfiguration.DEFAULT_VIDEO_BITRATE;
                }
                if (bitrate < 1) {
                    bitrate = 1;
                }
                deviceConfig.setVideoBitrate(bitrate);
                ((EditTextPreference) findPreference(P_KEY_VIDEO_BITRATE)).setText(Integer.toString(bitrate));
            }
            // Interface Display Theme
            else if (key.equals(P_KEY_THEME)) {
                int theme = shPreferences.getInt(P_KEY_THEME, aTalkApp.mTheme.ordinal());
                AndroidGUIActivator.getConfigurationService().setProperty(P_KEY_THEME, theme);
                aTalkApp.initTheme();
            }
        }
    }
}
