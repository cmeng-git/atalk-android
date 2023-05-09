/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.android.gui.settings;

import static net.java.sip.communicator.util.account.AccountUtils.getRegisteredProviders;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.EditTextPreference;
import androidx.preference.ListPreference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceScreen;

import net.java.sip.communicator.impl.msghistory.MessageHistoryActivator;
import net.java.sip.communicator.impl.protocol.jabber.ProtocolProviderServiceJabberImpl;
import net.java.sip.communicator.service.msghistory.MessageHistoryService;
import net.java.sip.communicator.service.protocol.ProtocolProviderService;
import net.java.sip.communicator.service.systray.PopupMessageHandler;
import net.java.sip.communicator.service.systray.SystrayService;
import net.java.sip.communicator.util.ConfigurationUtils;
import net.java.sip.communicator.util.ServiceUtils;
import net.java.sip.communicator.util.UtilActivator;

import org.atalk.android.R;
import org.atalk.android.aTalkApp;
import org.atalk.android.gui.AndroidGUIActivator;
import org.atalk.android.gui.aTalk;
import org.atalk.android.gui.settings.util.SummaryMapper;
import org.atalk.android.gui.util.LocaleHelper;
import org.atalk.android.gui.util.PreferenceUtil;
import org.atalk.android.gui.util.ThemeHelper;
import org.atalk.android.gui.util.ThemeHelper.Theme;
import org.atalk.impl.neomedia.MediaServiceImpl;
import org.atalk.impl.neomedia.NeomediaActivator;
import org.atalk.impl.neomedia.device.AndroidCameraSystem;
import org.atalk.impl.neomedia.device.DeviceConfiguration;
import org.atalk.impl.neomedia.device.util.AndroidCamera;
import org.atalk.impl.neomedia.device.util.CameraUtils;
import org.atalk.service.configuration.ConfigurationService;
import org.atalk.service.osgi.OSGiPreferenceFragment;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;

import java.awt.Dimension;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.media.MediaLocator;

import timber.log.Timber;

/**
 * The preferences fragment implements aTalk settings.
 *
 * @author Pawel Domas
 * @author Eng Chong Meng
 * @author MilanKral
 */
public class SettingsFragment extends OSGiPreferenceFragment
        implements SharedPreferences.OnSharedPreferenceChangeListener {
    // PreferenceScreen and PreferenceCategories
    private static final String P_KEY_MEDIA_CALL = "pref.cat.settings.media_call";
    private static final String P_KEY_CALL = "pref.cat.settings.call";

    // Advance video/audio & Provisioning preference settings
    private static final String P_KEY_ADVANCED = "pref.cat.settings.advanced";

    // Interface Display settings
    public static final String P_KEY_LOCALE = "pref.key.locale";
    public static final String P_KEY_THEME = "pref.key.theme";

    private static final String P_KEY_WEB_PAGE = "gui.WEB_PAGE_ACCESS";

    // Message section
    private static final String P_KEY_AUTO_START = "org.atalk.android.auto_start";
    private static final String P_KEY_LOG_CHAT_HISTORY = "pref.key.msg.history_logging";
    private static final String P_KEY_SHOW_HISTORY = "pref.key.msg.show_history";
    private static final String P_KEY_HISTORY_SIZE = "pref.key.msg.chat_history_size";
    private static final String P_KEY_MESSAGE_DELIVERY_RECEIPT = "pref.key.message_delivery_receipt";
    private static final String P_KEY_CHAT_STATE_NOTIFICATIONS = "pref.key.msg.chat_state_notifications";
    private static final String P_KEY_XFER_THUMBNAIL_PREVIEW = "pref.key.send_thumbnail";
    private static final String P_KEY_AUTO_ACCEPT_FILE = "pref.key.auto_accept_file";
    private static final String P_KEY_PRESENCE_SUBSCRIBE_MODE = "pref.key.presence_subscribe_mode";

    // Notifications
    private static final String P_KEY_POPUP_HANDLER = "pref.key.notification.popup_handler";
    public static final String P_KEY_HEADS_UP_ENABLE = "pref.key.notification.heads_up_enable";

    // Call section
    private static final String P_KEY_NORMALIZE_PNUMBER = "pref.key.call.remove.special";
    private static final String P_KEY_ACCEPT_ALPHA_PNUMBERS = "pref.key.call.convert.letters";

    // Video settings
    private static final String P_KEY_VIDEO_CAMERA = "pref.key.video.camera";
    // Video resolutions
    private static final String P_KEY_VIDEO_RES = "pref.key.video.resolution";

    // User option property names
    public static final String AUTO_UPDATE_CHECK_ENABLE = "user.AUTO_UPDATE_CHECK_ENABLE";

    /**
     * The device configuration
     */
    private DeviceConfiguration mDeviceConfig;

    private static ConfigurationService mConfigService;
    private PreferenceScreen mPreferenceScreen;
    private SharedPreferences shPrefs;

    private AppCompatActivity mActivity;

    /**
     * Summary mapper used to display preferences values as summaries.
     */
    private final SummaryMapper summaryMapper = new SummaryMapper();

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        mActivity = (AppCompatActivity) context;
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        // Load the preferences from an XML resource
        super.onCreatePreferences(savedInstanceState, rootKey);
        setPreferencesFromResource(R.xml.preferences, rootKey);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onResume() {
        super.onResume();
        setPrefTitle(R.string.system_settings);

        // FFR: v2.1.5 NPE; use UtilActivator instead of AndroidGUIActivator which was initialized much later
        mConfigService = UtilActivator.getConfigurationService();
        mPreferenceScreen = getPreferenceScreen();
        shPrefs = getPreferenceManager().getSharedPreferences();
        shPrefs.registerOnSharedPreferenceChangeListener(this);
        shPrefs.registerOnSharedPreferenceChangeListener(summaryMapper);

        // init display locale and theme (not implemented)
        initLocale();
        initTheme();
        initWebPagePreference();

        // Messages section
        initMessagesPreferences();

        // Notifications section
        initNotificationPreferences();
        initAutoStart();

        if (!aTalk.disableMediaServiceOnFault) {
            MediaServiceImpl mediaServiceImpl = NeomediaActivator.getMediaServiceImpl();
            if (mediaServiceImpl != null) {
                mDeviceConfig = mediaServiceImpl.getDeviceConfiguration();
            } else {
                // Do not proceed if mediaServiceImpl == null; else system crashes on NPE
                disableMediaOptions();
                return;
            }

            // Call section
            initCallPreferences();

            // Video section
            initVideoPreferences();
        } else {
            disableMediaOptions();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onStop() {
        shPrefs.unregisterOnSharedPreferenceChangeListener(this);
        shPrefs.unregisterOnSharedPreferenceChangeListener(summaryMapper);
        super.onStop();
    }

    private void initAutoStart() {
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
            ConfigurationUtils.setAutoStart(false);
            findPreference(P_KEY_AUTO_START).setEnabled(false);
        }
        PreferenceUtil.setCheckboxVal(mPreferenceScreen, P_KEY_AUTO_START,
                ConfigurationUtils.isAutoStartEnable());
    }

    /**
     * Initialize web default access page
     */
    private void initWebPagePreference() {
        // Updates displayed history size summary.
        EditTextPreference webPagePref = findPreference(P_KEY_WEB_PAGE);
        webPagePref.setText(ConfigurationUtils.getWebPage());
        updateWebPageSummary();
    }

    private void updateWebPageSummary() {
        EditTextPreference webPagePref = findPreference(P_KEY_WEB_PAGE);
        webPagePref.setSummary(ConfigurationUtils.getWebPage());
    }

    /**
     * Initialize interface Locale
     */
    protected void initLocale() {
        // Immutable empty {@link CharSequence} array
        CharSequence[] EMPTY_CHAR_SEQUENCE_ARRAY = new CharSequence[0];
        final ListPreference pLocale = findPreference(P_KEY_LOCALE);

        List<CharSequence> entryVector = new ArrayList<>(Arrays.asList(pLocale.getEntries()));
        List<CharSequence> entryValueVector = new ArrayList<>(Arrays.asList(pLocale.getEntryValues()));
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
        String language = LocaleHelper.getLanguage();

        pLocale.setEntries(entries);
        pLocale.setEntryValues(entryValues);
        pLocale.setValue(language);
        pLocale.setSummary(pLocale.getEntry());

        // summaryMapper not working for Locale, so use this instead
        pLocale.setOnPreferenceChangeListener((preference, value) -> {
            String language1 = value.toString();
            pLocale.setValue(language1);
            pLocale.setSummary(pLocale.getEntry());

            // Save selected language in DB
            mConfigService.setProperty(P_KEY_LOCALE, language1);

            // Need to destroy and restart to set new language if there is a change
            if (!language.equals(value) && (mActivity != null)) {
                // All language setting changes must call via aTalkApp so its contextWrapper is updated
                LocaleHelper.setLocale(getContext(), language1);

                // must get aTalk to restart onResume to show correct UI for preference menu
                aTalk.setPrefChange(aTalk.Locale_Change);

                // do destroy activity last
                mActivity.startActivity(new Intent(mActivity, SettingsActivity.class));
                mActivity.finish();
            }
            return true;
        });
    }

    /**
     * Initialize interface Theme
     */
    protected void initTheme() {
        final ListPreference pTheme = findPreference(P_KEY_THEME);
        String nTheme = ThemeHelper.isAppTheme(Theme.LIGHT) ? "light" : "dark";
        pTheme.setValue(nTheme);
        pTheme.setSummary(pTheme.getEntry());

        // summaryMapper not working for Theme. so use this instead
        pTheme.setOnPreferenceChangeListener((preference, value) -> {
            pTheme.setValue((String) value);
            pTheme.setSummary(pTheme.getEntry());

            // Save Display Theme to DB
            Theme vTheme = value.equals("light") ? Theme.LIGHT : Theme.DARK;
            mConfigService.setProperty(P_KEY_THEME, vTheme.ordinal());

            // Need to destroy and restart to set new Theme if there is a change
            if (!nTheme.equals(value) && (mActivity != null)) {
                ThemeHelper.setTheme(mActivity, vTheme);

                // must get aTalk to restart onResume to show new Theme
                aTalk.setPrefChange(aTalk.Theme_Change);

                mActivity.startActivity(new Intent(mActivity, SettingsActivity.class));
                mActivity.finish();
            }
            return true;
        });
    }

    /**
     * Initializes messages section
     */
    private void initMessagesPreferences() {
        // mhs may be null if user access settings before the mhs service is properly setup
        MessageHistoryService mhs = MessageHistoryActivator.getMessageHistoryService();
        boolean isHistoryLoggingEnabled = (mhs != null) && mhs.isHistoryLoggingEnabled();
        PreferenceUtil.setCheckboxVal(mPreferenceScreen, P_KEY_LOG_CHAT_HISTORY, isHistoryLoggingEnabled);

        PreferenceUtil.setCheckboxVal(mPreferenceScreen, P_KEY_SHOW_HISTORY, ConfigurationUtils.isHistoryShown());

        // Updates displayed history size summary.
        EditTextPreference historySizePref = findPreference(P_KEY_HISTORY_SIZE);
        historySizePref.setText(Integer.toString(ConfigurationUtils.getChatHistorySize()));
        updateHistorySizeSummary();

        PreferenceUtil.setCheckboxVal(mPreferenceScreen, P_KEY_MESSAGE_DELIVERY_RECEIPT,
                ConfigurationUtils.isSendMessageDeliveryReceipt());

        PreferenceUtil.setCheckboxVal(mPreferenceScreen, P_KEY_CHAT_STATE_NOTIFICATIONS,
                ConfigurationUtils.isSendChatStateNotifications());

        PreferenceUtil.setCheckboxVal(mPreferenceScreen, P_KEY_XFER_THUMBNAIL_PREVIEW,
                ConfigurationUtils.isSendThumbnail());

        PreferenceUtil.setCheckboxVal(mPreferenceScreen, P_KEY_PRESENCE_SUBSCRIBE_MODE,
                ConfigurationUtils.isPresenceSubscribeAuto());

        initAutoAcceptFileSize();
        // GeoPreferenceUtil.setCheckboxVal(this, P_KEY_CHAT_ALERTS, ConfigurationUtils.isAlerterEnabled());
    }

    /**
     * Updates displayed history size summary.
     */
    private void updateHistorySizeSummary() {
        EditTextPreference historySizePref = findPreference(P_KEY_HISTORY_SIZE);
        historySizePref.setSummary(getString(R.string.service_gui_settings_CHAT_HISTORY_SUMMARY,
                ConfigurationUtils.getChatHistorySize()));
    }

    /**
     * Initialize auto accept file size
     */
    protected void initAutoAcceptFileSize() {
        final ListPreference fileSizeList = findPreference(P_KEY_AUTO_ACCEPT_FILE);
        fileSizeList.setEntries(R.array.filesizes);
        fileSizeList.setEntryValues(R.array.filesizes_values);
        long filesSize = ConfigurationUtils.getAutoAcceptFileSize();
        fileSizeList.setValue(String.valueOf(filesSize));
        fileSizeList.setSummary(fileSizeList.getEntry());

        // summaryMapper not working for auto accept fileSize so use this instead
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
    private void initNotificationPreferences() {
        // Remove for android play store release
        // GeoPreferenceUtil.setCheckboxVal(preferenceScreen, P_KEY_AUTO_UPDATE_CHECK_ENABLE,
        //		cfg.getBoolean(AUTO_UPDATE_CHECK_ENABLE, true));

        BundleContext bc = AndroidGUIActivator.bundleContext;
        ServiceReference[] handlerRefs = ServiceUtils.getServiceReferences(bc, PopupMessageHandler.class);

        String[] names = new String[handlerRefs.length + 1]; // +1 Auto
        String[] values = new String[handlerRefs.length + 1];
        names[0] = getString(R.string.impl_popup_auto);
        values[0] = "Auto";
        int selectedIdx = 0; // Auto by default

        // mCongService may be null feedback NPE from the field report, so just assume null i.e.
        // "Auto" selected. Delete the user's preference and select the best available handler.
        String configuredHandler = (mConfigService == null) ?
                null : mConfigService.getString("systray.POPUP_HANDLER");
        int idx = 1;
        for (ServiceReference<PopupMessageHandler> ref : handlerRefs) {
            PopupMessageHandler handler = bc.getService(ref);

            names[idx] = handler.toString();
            values[idx] = handler.getClass().getName();

            if ((configuredHandler != null) && configuredHandler.equals(handler.getClass().getName())) {
                selectedIdx = idx;
            }
        }

        // Configures ListPreference
        ListPreference handlerList = findPreference(P_KEY_POPUP_HANDLER);
        handlerList.setEntries(names);
        handlerList.setEntryValues(values);
        handlerList.setValueIndex(selectedIdx);
        // Summaries mapping
        summaryMapper.includePreference(handlerList, "Auto");

        PreferenceUtil.setCheckboxVal(mPreferenceScreen, P_KEY_HEADS_UP_ENABLE,
                ConfigurationUtils.isHeadsUpEnable());
    }

    // Disable all media options when MediaServiceImpl is not initialized due to text-relocation in ffmpeg
    private void disableMediaOptions() {
        PreferenceCategory myPrefCat = findPreference(P_KEY_MEDIA_CALL);
        if (myPrefCat != null)
            mPreferenceScreen.removePreference(myPrefCat);

        myPrefCat = findPreference(P_KEY_CALL);
        if (myPrefCat != null)
            mPreferenceScreen.removePreference(myPrefCat);

        // android OS cannot support removal of nested PreferenceCategory, so just disable all advance settings
        myPrefCat = findPreference(P_KEY_ADVANCED);
        if (myPrefCat != null) {
            mPreferenceScreen.removePreference(myPrefCat);
        }
    }

    /**
     * Initializes call section
     */
    private void initCallPreferences() {
        PreferenceUtil.setCheckboxVal(mPreferenceScreen, P_KEY_NORMALIZE_PNUMBER,
                ConfigurationUtils.isNormalizePhoneNumber());
        PreferenceUtil.setCheckboxVal(mPreferenceScreen, P_KEY_ACCEPT_ALPHA_PNUMBERS,
                ConfigurationUtils.acceptPhoneNumberWithAlphaChars());
    }

    /**
     * Initializes video preferences part.
     */
    private void initVideoPreferences() {
        AndroidCamera[] cameras = AndroidCamera.getCameras();
        String[] names = new String[cameras.length];
        String[] values = new String[cameras.length];
        for (int i = 0; i < cameras.length; i++) {
            names[i] = cameras[i].getName();
            values[i] = cameras[i].getLocator().toString();
        }

        ListPreference cameraList = findPreference(P_KEY_VIDEO_CAMERA);
        cameraList.setEntries(names);
        cameraList.setEntryValues(values);

        // Get camera from configuration
        AndroidCamera currentCamera = AndroidCamera.getSelectedCameraDevInfo();
        if (currentCamera != null)
            cameraList.setValue(currentCamera.getLocator().toString());

        // Resolutions
        int resolutionSize = CameraUtils.PREFERRED_SIZES.length;
        String[] resolutionValues = new String[resolutionSize];
        for (int i = 0; i < resolutionSize; i++) {
            resolutionValues[i] = resToStr(CameraUtils.PREFERRED_SIZES[i]);
        }

        ListPreference resList = findPreference(P_KEY_VIDEO_RES);
        resList.setEntries(resolutionValues);
        resList.setEntryValues(resolutionValues);

        // Init current resolution
        resList.setValue(resToStr(mDeviceConfig.getVideoSize()));

        // Summaries mapping
        summaryMapper.includePreference(cameraList, getString(R.string.service_gui_settings_NO_CAMERA));
        summaryMapper.includePreference(resList, "720x480");
    }

    /**
     * Converts resolution to string.
     *
     * @param d resolution as <code>Dimension</code>
     *
     * @return resolution string.
     */
    private static String resToStr(Dimension d) {
        return ((int) d.getWidth()) + "x" + ((int) d.getHeight());
    }

    /**
     * Selects resolution from supported resolutions list for given string.
     *
     * @param resStr resolution string created with method {@link #resToStr(Dimension)}.
     *
     * @return resolution <code>Dimension</code> for given string representation created with method
     * {@link #resToStr(Dimension)}
     */
    private static Dimension resolutionForStr(String resStr) {
        Dimension[] resolutions = AndroidCameraSystem.SUPPORTED_SIZES;
        for (Dimension resolution : resolutions) {
            if (resToStr(resolution).equals(resStr))
                return resolution;
        }
        // "Auto" string won't match the defined resolution strings so will return default for auto
        return new Dimension(DeviceConfiguration.DEFAULT_VIDEO_WIDTH, DeviceConfiguration.DEFAULT_VIDEO_HEIGHT);
    }

    /**
     * Retrieves currently registered <code>PopupMessageHandler</code> for given <code>clazz</code> name.
     *
     * @param clazz the class name of <code>PopupMessageHandler</code> implementation.
     *
     * @return implementation of <code>PopupMessageHandler</code> for given class name registered in OSGI context.
     */
    private PopupMessageHandler getHandlerForClassName(String clazz) {
        BundleContext bc = AndroidGUIActivator.bundleContext;
        ServiceReference[] handlerRefs = ServiceUtils.getServiceReferences(bc, PopupMessageHandler.class);

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
    public void onSharedPreferenceChanged(SharedPreferences shPreferences, String key) {
        switch (key) {
            case P_KEY_LOG_CHAT_HISTORY:
                MessageHistoryService mhs = MessageHistoryActivator.getMessageHistoryService();
                boolean enable = false;
                if (mhs != null) {
                    enable = shPreferences.getBoolean(P_KEY_LOG_CHAT_HISTORY, mhs.isHistoryLoggingEnabled());
                    mhs.setHistoryLoggingEnabled(enable);
                }
                enableMam(enable);
                break;
            case P_KEY_SHOW_HISTORY:
                ConfigurationUtils.setHistoryShown(shPreferences.getBoolean(P_KEY_SHOW_HISTORY, ConfigurationUtils.isHistoryShown()));
                break;
            case P_KEY_HISTORY_SIZE:
                String intStr = shPreferences.getString(P_KEY_HISTORY_SIZE, Integer.toString(ConfigurationUtils.getChatHistorySize()));
                ConfigurationUtils.setChatHistorySize(Integer.parseInt(intStr));
                updateHistorySizeSummary();
                break;
            case P_KEY_WEB_PAGE:
                String wpStr = shPreferences.getString(P_KEY_WEB_PAGE, ConfigurationUtils.getWebPage());
                ConfigurationUtils.setWebPage(wpStr);
                updateWebPageSummary();
                break;
            case P_KEY_AUTO_START:
                ConfigurationUtils.setAutoStart(shPreferences.getBoolean(
                        P_KEY_AUTO_START, ConfigurationUtils.isAutoStartEnable()));
                break;
            case P_KEY_MESSAGE_DELIVERY_RECEIPT:
                ConfigurationUtils.setSendMessageDeliveryReceipt(shPreferences.getBoolean(
                        P_KEY_MESSAGE_DELIVERY_RECEIPT, ConfigurationUtils.isSendMessageDeliveryReceipt()));
                break;
            case P_KEY_CHAT_STATE_NOTIFICATIONS:
                ConfigurationUtils.setSendChatStateNotifications(shPreferences.getBoolean(
                        P_KEY_CHAT_STATE_NOTIFICATIONS, ConfigurationUtils.isSendChatStateNotifications()));
                break;
            case P_KEY_XFER_THUMBNAIL_PREVIEW:
                ConfigurationUtils.setSendThumbnail(shPreferences.getBoolean(
                        P_KEY_XFER_THUMBNAIL_PREVIEW, ConfigurationUtils.isSendThumbnail()));
                break;
            case P_KEY_PRESENCE_SUBSCRIBE_MODE:
                ConfigurationUtils.setPresenceSubscribeAuto(shPreferences.getBoolean(
                        P_KEY_PRESENCE_SUBSCRIBE_MODE, ConfigurationUtils.isPresenceSubscribeAuto()));
                break;

            // Disable for android play store
            /* else if (key.equals(P_KEY_AUTO_UPDATE_CHECK_ENABLE)) {
				Boolean isEnable = shPreferences.getBoolean(P_KEY_AUTO_UPDATE_CHECK_ENABLE, true);
				mConfigService.setProperty(AUTO_UPDATE_CHECK_ENABLE, isEnable);

				// Perform software version update check on first launch
				Intent intent = new Intent(mActivity, OnlineUpdateService.class);
				if (isEnable)
					intent.setAction(OnlineUpdateService.ACTION_AUTO_UPDATE_START);
				else
					intent.setAction(OnlineUpdateService.ACTION_AUTO_UPDATE_STOP);
				mActivity.startService(intent);
			}*/

            /*
             * Chat alerter is not implemented on Android
             * else if(key.equals(P_KEY_CHAT_ALERTS)) {
             *  ConfigurationUtils.setAlerterEnabled( shPreferences.getBoolean( P_KEY_CHAT_ALERTS,
             *  ConfigurationUtils.isAlerterEnabled()));
             * }
             */
            case P_KEY_POPUP_HANDLER:
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
                break;
            case P_KEY_HEADS_UP_ENABLE:
                ConfigurationUtils.setHeadsUp(shPreferences.getBoolean(P_KEY_HEADS_UP_ENABLE, true));
                break;
            // Normalize phone number
            case P_KEY_NORMALIZE_PNUMBER:
                ConfigurationUtils.setNormalizePhoneNumber(shPreferences.getBoolean(P_KEY_NORMALIZE_PNUMBER, true));
                break;
            // Camera
            case P_KEY_VIDEO_CAMERA:
                String cameraName = shPreferences.getString(P_KEY_VIDEO_CAMERA, null);
                AndroidCamera.setSelectedCamera(new MediaLocator(cameraName));
                break;
            // Video resolution
            case P_KEY_VIDEO_RES:
                String resStr = shPreferences.getString(P_KEY_VIDEO_RES, null);
                Dimension videoRes = resolutionForStr(resStr);
                mDeviceConfig.setVideoSize(videoRes);
                break;
        }
    }

    /**
     * Enable or disable MAM service according per the P_KEY_LOG_CHAT_HISTORY new setting.
     *
     * @param enable mam state to be updated with.
     */
    private void enableMam(boolean enable) {
        Collection<ProtocolProviderService> providers = getRegisteredProviders();
        for (ProtocolProviderService pps : providers) {
            if (pps.isRegistered()) {
                ProtocolProviderServiceJabberImpl.enableMam(pps.getConnection(), enable);
            } else {
                aTalkApp.showToastMessage(R.string.service_gui_settings_HISTORY_WARNING, pps.getAccountID().getBareJid());
            }
        }
    }
}
