/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.util;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import net.java.sip.communicator.impl.configuration.SQLiteConfigurationStore;
import net.java.sip.communicator.impl.protocol.jabber.OperationSetContactCapabilitiesJabberImpl;
import net.java.sip.communicator.service.msghistory.MessageHistoryService;
import net.java.sip.communicator.service.protocol.*;
import net.java.sip.communicator.util.account.AccountUtils;

import org.apache.commons.lang3.StringUtils;
import org.atalk.android.R;
import org.atalk.android.aTalkApp;
import org.atalk.android.gui.AndroidGUIActivator;
import org.atalk.android.gui.chat.ChatFragment;
import org.atalk.android.gui.chat.ChatSession;
import org.atalk.android.gui.chat.filetransfer.FileTransferConversation;
import org.atalk.android.gui.settings.SettingsActivity;
import org.atalk.android.gui.settings.TimePreference;
import org.atalk.android.gui.util.LocaleHelper;
import org.atalk.android.gui.util.ThemeHelper;
import org.atalk.android.gui.util.ThemeHelper.Theme;
import org.atalk.android.gui.webview.WebViewFragment;
import org.atalk.android.util.java.awt.Color;
import org.atalk.persistance.DatabaseBackend;
import org.atalk.service.configuration.ConfigurationService;
import org.atalk.service.neomedia.codec.EncodingConfiguration;
import org.atalk.util.MediaType;
import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.roster.Roster;
import org.jivesoftware.smackx.chatstates.ChatStateManager;
import org.jivesoftware.smackx.disco.ServiceDiscoveryManager;
import org.jivesoftware.smackx.receipts.DeliveryReceipt;
import org.jivesoftware.smackx.receipts.DeliveryReceiptManager;
import org.json.JSONException;
import org.json.JSONObject;
import org.jxmpp.jid.Jid;
import org.osgi.framework.ServiceReference;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.IOException;
import java.util.*;

import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

import timber.log.Timber;

/**
 * Cares about all common configurations. Storing and retrieving configuration values.
 *
 * @author Yana Stamcheva
 * @author Damian Minkov
 * @author Eng Chong Meng
 */
public class ConfigurationUtils
{
    /**
     * The send message command defined by the Enter key.
     */
    public static final String ENTER_COMMAND = "Enter";

    /**
     * The send message command defined by the Ctrl-Enter key.
     */
    public static final String CTRL_ENTER_COMMAND = "Ctrl-Enter";

    /**
     * Indicates whether the message automatic pop-up is enabled.
     */
    private static boolean autoPopupNewMessage = false;

    /**
     * The send message command. ENTER or Ctrl-ENTER
     */
    private static String sendMessageCommand;

    /**
     * The Web View access page
     */
    private static String mWebPage;

    /**
     * Indicates if the call panel is shown.
     */
    private static boolean isCallPanelShown = true;

    /**
     * Indicates if the offline contacts are shown.
     */
    private static boolean isShowOffline = true;

    /**
     * Indicates if the application main window is visible by default.
     */
    private static boolean isApplicationVisible = true;

    /**
     * Indicates if the quit warning should be shown.
     */
    private static boolean isQuitWarningShown = true;

    /**
     * Indicates if aTalk will auto start on device reboot.
     */
    private static boolean isAutoStartOnBoot = true;

    /**
     * Indicates if TTS is enable.
     */
    private static boolean isTtsEnable = false;

    /**
     * Indicates if message delivery receipt should be sent.
     */
    private static boolean isSendMessageDeliveryReceipt = true;

    /**
     * Indicates if chat state notifications should be sent.
     */
    private static boolean isSendChatStateNotifications = true;

    /**
     * Indicates if is send thumbnail option is offer during image file transfer.
     */
    private static boolean isSendThumbnail = true;

    /**
     * Indicates if presence subscription mode is auto approval.
     */
    private static boolean isPresenceSubscribeAuto = true;

    /**
     * Indicates if confirmation should be requested before really moving a contact.
     */
    private static boolean isMoveContactConfirmationRequested = true;

    /**
     * Indicates if tabs in chat window are enabled.
     */
    private static boolean isMultiChatWindowEnabled;

    /**
     * Indicates whether we will leave chat room on window closing.
     */
    private static boolean isLeaveChatRoomOnWindowCloseEnabled;

    /**
     * Indicates if private messaging is enabled for chat rooms.
     */
    private static boolean isPrivateMessagingInChatRoomDisabled;

    /**
     * Indicates if the history should be shown in the chat window.
     */
    private static boolean isHistoryShown;

    /**
     * Indicates if the recent messages should be shown.
     */
    private static boolean isRecentMessagesShown = true;


    /**
     * Initial default wait time for incoming message alert to end before start TTS
     */
    private static int ttsDelay = 1200;

    /**
     * The size of the chat history to show in chat window.
     */
    private static int chatHistorySize;

    /**
     * The auto accept file size.
     */
    private static int acceptFileSize;

    /**
     * The size of the chat write area.
     */
    private static int chatWriteAreaSize;

    /**
     * The transparency of the window.
     */
    private static int windowTransparency;

    /**
     * Indicates if transparency is enabled.
     */
    private static boolean isTransparentWindowEnabled;

    /**
     * Indicates if the window is decorated.
     */
    private static boolean isWindowDecorated;

    /**
     * Indicates if the chat tool bar is visible.
     */
    private static boolean isChatToolbarVisible;

    /**
     * Indicates if the chat style bar is visible.
     */
    private static boolean isChatStyleBarVisible;

    /**
     * Indicates if the chat simple theme is activated.
     */
    private static boolean isChatSimpleThemeEnabled;

    /**
     * Indicates if the add contact functionality is disabled.
     */
    private static boolean isAddContactDisabled;

    /**
     * Indicates if the merge contact functionality is disabled.
     */
    private static boolean isMergeContactDisabled;

    /**
     * Indicates if the go to chatRoom functionality is disabled.
     */
    private static boolean isGoToChatRoomDisabled;

    /**
     * Indicates if the create group functionality is disabled.
     */
    private static boolean isCreateGroupDisabled;

    /**
     * Indicates if the create group functionality is enabled.
     */
    private static boolean isFlattenGroupEnabled;

    /**
     * Indicates if the remove contact functionality is disabled.
     */
    private static boolean isRemoveContactDisabled;

    /**
     * Indicates if the move contact functionality is disabled.
     */
    private static boolean isContactMoveDisabled;

    /**
     * Indicates if the rename contact functionality is disabled.
     */
    private static boolean isContactRenameDisabled;

    /**
     * Indicates if the remove group functionality is disabled.
     */
    private static boolean isGroupRemoveDisabled;

    /**
     * Indicates if the rename group functionality is disabled.
     */
    private static boolean isGroupRenameDisabled;

    /**
     * Indicates if the pre set status messages are enabled.
     */
    private static boolean isPresetStatusMessagesEnabled;

    private static boolean isQuiteHoursEnable = true;
    private static Long quiteHoursStart = 0L;
    private static Long quiteHoursEnd = 0L;

    private static boolean isHeadsUpEnable = true;

    /**
     * The last directory used in file transfer.
     */
    private static String sendFileLastDir;

    /**
     * The configuration service.
     */
    private static ConfigurationService configService = UtilActivator.getConfigurationService();

    /**
     * The parent of the last contact.
     */
    private static String lastContactParent = null;

    /**
     * The last conference call provider.
     */
    private static ProtocolProviderService lastCallConferenceProvider = null;

    /**
     * Indicates if the "Advanced" configurations for an account should be disabled for the user.
     */
    private static boolean isAdvancedAccountConfigDisabled;

    /**
     * The default font family used in chat windows.
     */
    private static String defaultFontFamily;

    /**
     * The default font size used in chat windows.
     */
    private static String defaultFontSize;

    /**
     * Indicates if the font is bold in chat windows.
     */
    private static boolean isDefaultFontBold = false;

    /**
     * Indicates if the font is italic in chat windows.
     */
    private static boolean isDefaultFontItalic = false;

    /**
     * Indicates if the font is underline in chat windows.
     */
    private static boolean isDefaultFontUnderline = false;

    /**
     * The default font color used in chat windows.
     */
    private static int defaultFontColor = -1;

    /**
     * whether to show the status changed message in chat history area.
     */
    private static boolean showStatusChangedInChat = false;

    /**
     * When enabled, allow to use the additional phone numbers to route video calls and desktop
     * sharing through it if possible.
     */
    private static boolean routeVideoAndDesktopUsingPhoneNumber = false;

    /**
     * Indicates that when we have a single account we can hide the select account option when possible.
     */
    private static boolean hideAccountSelectionWhenPossible = false;

    /**
     * Hide accounts from accounts status list.
     */
    private static boolean hideAccountStatusSelectors = false;

    /**
     * Hide extended away status.
     */
    private static boolean hideExtendedAwayStatus = false;

    /**
     * Whether to disable creation of auto answer submenu.
     */
    private static boolean autoAnswerDisableSubmenu = false;

    /**
     * Whether the chat room user configuration functionality is disabled.
     */
    private static boolean isChatRoomConfigDisabled = false;

    /**
     * Indicates if the single window interface is enabled.
     */
    private static boolean isSingleWindowInterfaceEnabled = false;

    /**
     * Whether addresses will be shown in call history tooltips.
     */
    private static boolean isHideAddressInCallHistoryTooltipEnabled = false;

    /**
     * The name of the property, whether to show addresses in call history tooltip.
     */
    private static final String HIDE_ADDR_IN_CALL_HISTORY_TOOLTIP_PROPERTY
            = "gui.contactlist.HIDE_ADDRESS_IN_CALL_HISTORY_TOOLTIP_ENABLED";

    /**
     * Texts to notify that sms has been sent or sms has been received.
     */
    private static boolean isSmsNotifyTextDisabled = false;

    /**
     * To disable displaying sms delivered message or sms received.
     */
    private static final String SMS_MSG_NOTIFY_TEXT_DISABLED_PROP = "gui.contactlist.SMS_MSG_NOTIFY_TEXT_DISABLED_PROP";

    /**
     * Whether domain will be shown in receive call dialog.
     */
    private static boolean isHideDomainInReceivedCallDialogEnabled = false;

    /**
     * The name of the property, whether to show addresses in call history tooltip.
     */
    private static final String HIDE_DOMAIN_IN_RECEIVE_CALL_DIALOG_PROPERTY
            = "gui.call.HIDE_DOMAIN_IN_RECEIVE_CALL_DIALOG_ENABLED";

    /**
     * The name of the simple theme property.
     */
    private static final String CHAT_SIMPLE_THEME_ENABLED_PROP = "gui.CHAT_SIMPLE_THEME_ENABLED";

    /**
     * The name of the chat room configuration property.
     */
    private static final String CHAT_ROOM_CONFIG_DISABLED_PROP = "gui.CHAT_ROOM_CONFIG_DISABLED";

    /**
     * The name of the single interface property.
     */
    private static final String SINGLE_WINDOW_INTERFACE_ENABLED = "gui.SINGLE_WINDOW_INTERFACE_ENABLED";

    /**
     * The names of the configuration properties.
     */
    private static String pAcceptFileSize = "gui.AUTO_ACCEPT_FILE_SIZE";
    private static String pAutoAnswerDisableSubmenu = "gui.AUTO_ANSWER_DISABLE_SUBMENU";
    private static String pAutoPopupNewMessage = "gui.AUTO_POPUP_NEW_MESSAGE";
    public static String pAutoStart = "gui.AUTO_START_ON_REBOOT";
    private static String pChatHistorySize = "gui.MESSAGE_HISTORY_SIZE";
    private static String pChatWriteAreaSize = "gui.CHAT_WRITE_AREA_SIZE";
    private static String pHideAccountMenu = "gui.HIDE_SELECTION_ON_SINGLE_ACCOUNT";
    private static String pHideAccountStatusSelectors = "gui.HIDE_ACCOUNT_STATUS_SELECTORS";
    private static String pHideExtendedAwayStatus = "protocol.globalstatus.HIDE_EXTENDED_AWAY_STATUS";
    private static String pIsWindowDecorated = "gui.IS_WINDOW_DECORATED";
    public static String pLanguage = aTalkApp.getResString(R.string.pref_key_locale);
    private static String pLeaveChatRoomOnWindowClose = "gui.LEAVE_CHATROOM_ON_WINDOW_CLOSE";
    private static String pMsgCommand = "gui.SEND_MESSAGE_COMMAND";
    private static String pMessageDeliveryReceipt = "gui.SEND_MESSAGE_DELIVERY_RECEIPT";
    private static String pMessageHistoryShown = "gui.IS_MESSAGE_HISTORY_SHOWN";
    private static String pMultiChatWindowEnabled = "gui.IS_MULTI_CHAT_WINDOW_ENABLED";
    private static String pPresenceSubscribeAuto = "gui.PRESENCE_SUBSCRIBE_MODE_AUTO";
    public static String pQuiteHoursEnable = aTalkApp.getResString(R.string.pref_key_quiet_hours_enable);
    public static String pQuiteHoursStart = aTalkApp.getResString(R.string.pref_key_quiet_hours_start);
    public static String pQuiteHoursEnd = aTalkApp.getResString(R.string.pref_key_quiet_hours_end);
    private static String pRouteVideoAndDesktopUsingPhoneNumber = "gui.ROUTE_VIDEO_AND_DESKTOP_TO_PNONENUMBER";
    private static String pSendThumbnail = "gui.sendThumbnail";
    private static String pShowStatusChangedInChat = "gui.SHOW_STATUS_CHANGED_IN_CHAT";
    private static String pTransparentWindowEnabled = "gui.IS_TRANSPARENT_WINDOW_ENABLED";
    public static String pTTSEnable = "gui.TTS_ENABLE";
    public static String pTTSDelay = "gui.TTS_DELAY";
    private static String pTypingNotification = "gui.SEND_TYPING_NOTIFICATIONS_ENABLED";
    public static String pWebPage = aTalkApp.getResString(R.string.pref_key_webview_PAGE);
    private static String pWindowTransparency = "gui.WINDOW_TRANSPARENCY";
    private static String pHeadsUpEnable = aTalkApp.getResString(R.string.pref_key_heads_up_enable);

    /**
     * Indicates if phone numbers should be normalized before dialed.
     */
    private static boolean isNormalizePhoneNumber;

    /**
     * Indicates if a string containing alphabetical characters might be considered as a phone number.
     */
    private static boolean acceptPhoneNumberWithAlphaChars;

    /**
     * The name of the single interface property.
     */
    public static final String ALERTER_ENABLED_PROP = "chatalerter.ENABLED";

    /**
     * The name of the property which indicates whether the user should be
     * warned that master password is not set.
     */
    private static final String MASTER_PASS_WARNING_PROP = "gui.main.SHOW_MASTER_PASSWORD_WARNING";

    /**
     * Indicates whether the user should be warned that master password is not set.
     */
    private static boolean showMasterPasswordWarning;

    /**
     * Indicates if window (task bar or dock icon) alerter is enabled.
     */
    private static boolean alerterEnabled;

    private static SQLiteDatabase mDB;
    private static ContentValues contentValues = new ContentValues();

    /**
     * Loads all user interface configurations.
     */
    public static void loadGuiConfigurations()
    {
        // Do it here one more time, sometime see accessing preference crash with configService == null
        configService = UtilActivator.getConfigurationService();
        if (configService == null)
            return;

        configService.addPropertyChangeListener(new ConfigurationChangeListener());
        mDB = DatabaseBackend.getWritableDB();

        // Init the aTalk app Theme before any activity use
        initAppTheme();

        // Load the UI language last selected by user or default to system language i.e. ""
        String language = configService.getString(aTalkApp.getResString(R.string.pref_key_locale), "");
        LocaleHelper.setLanguage(language);

        // Load the "webPage" property.
        mWebPage = configService.getString(pWebPage);
        if (StringUtils.isEmpty(mWebPage))
            mWebPage = aTalkApp.getResString(R.string.service_gui_settings_WEBVIEW_SUMMARY);

        // Load the "auPopupNewMessage" property.
        String autoPopup = configService.getString(pAutoPopupNewMessage);
        if (StringUtils.isEmpty(autoPopup))
            autoPopup = UtilActivator.getResources().getSettingsString(pAutoPopupNewMessage);

        if (StringUtils.isNotEmpty(autoPopup) && autoPopup.equalsIgnoreCase("yes"))
            autoPopupNewMessage = true;

        // Load the "sendMessageCommand" property.
        String messageCommand = configService.getString(pMsgCommand);
        if (StringUtils.isEmpty(messageCommand))
            messageCommand = UtilActivator.getResources().getSettingsString(pMsgCommand);

        if (StringUtils.isNotEmpty(messageCommand))
            sendMessageCommand = messageCommand;

        // Load the showCallPanel property.
        isCallPanelShown = configService.getBoolean("gui.showCallPanel", isCallPanelShown);

        // Load the "isAutoStartOnBoot" property.
        isAutoStartOnBoot = configService.getBoolean(pAutoStart, isAutoStartOnBoot);

        // Load the "isTtsEnable" and delay property.
        isTtsEnable = configService.getBoolean(pTTSEnable, isTtsEnable());
        ttsDelay = configService.getInt(pTTSDelay, ttsDelay);

        // Load the "showOffline" property.
        isShowOffline = configService.getBoolean("gui.showOffline", isShowOffline);

        // Load the "showApplication" property.
        isApplicationVisible = configService.getBoolean("systray.showApplication", isApplicationVisible);

        // Load the "showAppQuitWarning" property.
        isQuitWarningShown = configService.getBoolean("gui.quitWarningShown", isQuitWarningShown);

        // Load the "isSendMessageDeliveryReceipt" property.
        isSendMessageDeliveryReceipt = configService.getBoolean(pMessageDeliveryReceipt, isSendMessageDeliveryReceipt);

        // Load the "sendTypingNotifications" property.
        String isSendTypingNotification = configService.getString(pTypingNotification);
        if (StringUtils.isEmpty(isSendTypingNotification))
            isSendTypingNotification = UtilActivator.getResources().getSettingsString(pTypingNotification);

        if (StringUtils.isNotEmpty(isSendTypingNotification))
            isSendChatStateNotifications = Boolean.parseBoolean(isSendTypingNotification);

        // Load the "sendThumbnail" property.
        String sendThumbNail = configService.getString(pSendThumbnail);
        if (StringUtils.isNotEmpty(sendThumbNail)) {
            isSendThumbnail = Boolean.parseBoolean(sendThumbNail);
            FileTransferConversation.FT_THUMBNAIL_ENABLE = isSendThumbnail;
        }

        // Load the "isPresenceSubscribeMode" property.
        isPresenceSubscribeAuto = configService.getBoolean(pPresenceSubscribeAuto, isPresenceSubscribeAuto);

        // Load the "isMoveContactConfirmationRequested" property.
        String isMoveContactConfirmationRequestedString
                = configService.getString("gui.isMoveContactConfirmationRequested");

        if (StringUtils.isNotEmpty(isMoveContactConfirmationRequestedString)) {
            isMoveContactConfirmationRequested = Boolean.parseBoolean(isMoveContactConfirmationRequestedString);
        }

        // Load the "isMultiChatWindowEnabled" property.
        String isMultiChatWindowEnabledString = configService.getString(pMultiChatWindowEnabled);

        if (StringUtils.isEmpty(isMultiChatWindowEnabledString))
            isMultiChatWindowEnabledString = UtilActivator.getResources().getSettingsString(pMultiChatWindowEnabled);

        if (StringUtils.isNotEmpty(isMultiChatWindowEnabledString)) {
            isMultiChatWindowEnabled = Boolean.parseBoolean(isMultiChatWindowEnabledString);
        }

        isPrivateMessagingInChatRoomDisabled
                = configService.getBoolean("gui.IS_PRIVATE_CHAT_IN_CHATROOM_DISABLED", false);

        // Load the "isLeaveChatroomOnWindowCloseEnabled" property.
        String isLeaveChatRoomOnWindowCloseEnabledString = configService.getString(pLeaveChatRoomOnWindowClose);
        if (StringUtils.isEmpty(isLeaveChatRoomOnWindowCloseEnabledString)) {
            isLeaveChatRoomOnWindowCloseEnabledString
                    = UtilActivator.getResources().getSettingsString(pLeaveChatRoomOnWindowClose);
        }

        if (StringUtils.isNotEmpty(isLeaveChatRoomOnWindowCloseEnabledString)) {
            isLeaveChatRoomOnWindowCloseEnabled = Boolean.parseBoolean(isLeaveChatRoomOnWindowCloseEnabledString);
        }

        // Load the "isHistoryShown" property.
        String isHistoryShownString = configService.getString(pMessageHistoryShown);
        if (StringUtils.isEmpty(isHistoryShownString))
            isHistoryShownString = UtilActivator.getResources().getSettingsString(pMessageHistoryShown);

        if (StringUtils.isNotEmpty(isHistoryShownString)) {
            isHistoryShown = Boolean.parseBoolean(isHistoryShownString);
        }

        // Load the "isRecentMessagesShown" property.
        // isRecentMessagesShown = !configService.getBoolean(MessageHistoryService
        // .PNAME_IS_RECENT_MESSAGES_DISABLED, !isRecentMessagesShown);

        // Load the "acceptFileSize" property.
        String fileSize = configService.getString(pAcceptFileSize, aTalkApp.getResString(R.string.auto_accept_filesize));
        acceptFileSize = Integer.parseInt(fileSize);

        // Load the "chatHistorySize" property.
        String chatHistorySizeString = configService.getString(pChatHistorySize);
        if (StringUtils.isEmpty(chatHistorySizeString))
            chatHistorySizeString = UtilActivator.getResources().getSettingsString(pChatHistorySize);

        if (StringUtils.isNotEmpty(chatHistorySizeString)) {
            chatHistorySize = Integer.parseInt(chatHistorySizeString);
        }

        // Load the "CHAT_WRITE_AREA_SIZE" property.
        String chatWriteAreaSizeString = configService.getString(pChatWriteAreaSize);
        if (StringUtils.isEmpty(chatWriteAreaSizeString))
            chatWriteAreaSizeString = UtilActivator.getResources().getSettingsString(pChatWriteAreaSize);

        if (StringUtils.isNotEmpty(chatWriteAreaSizeString)) {
            chatWriteAreaSize = Integer.parseInt(chatWriteAreaSizeString);
        }

        // Load the "isTransparentWindowEnabled" property.
        String isTransparentWindowEnabledString = configService.getString(pTransparentWindowEnabled);
        if (StringUtils.isEmpty(isTransparentWindowEnabledString))
            isTransparentWindowEnabledString = UtilActivator.getResources().getSettingsString(pTransparentWindowEnabled);

        if (StringUtils.isNotEmpty(isTransparentWindowEnabledString)) {
            isTransparentWindowEnabled = Boolean.parseBoolean(isTransparentWindowEnabledString);
        }

        // Load the "windowTransparency" property.
        String windowTransparencyString = configService.getString(pWindowTransparency);
        if (StringUtils.isEmpty(windowTransparencyString))
            windowTransparencyString = UtilActivator.getResources().getSettingsString(pWindowTransparency);

        if (StringUtils.isNotEmpty(windowTransparencyString)) {
            windowTransparency = Integer.parseInt(windowTransparencyString);
        }

        // Load the "isWindowDecorated" property.
        String isWindowDecoratedString = configService.getString(pIsWindowDecorated);

        if (StringUtils.isEmpty(isWindowDecoratedString))
            isWindowDecoratedString = UtilActivator.getResources().getSettingsString(pIsWindowDecorated);

        if (StringUtils.isNotEmpty(isWindowDecoratedString)) {
            isWindowDecorated = Boolean.parseBoolean(isWindowDecoratedString);
        }

        // Load the "isChatToolbarVisible" property
        isChatToolbarVisible = configService.getBoolean("gui.chat.ChatWindow.showToolbar", true);
        // Load the "isChatToolbarVisible" property
        isChatStyleBarVisible = configService.getBoolean("gui.chat.ChatWindow.showStylebar", true);

        // Load the "isChatSimpleThemeEnabled" property.
        isChatSimpleThemeEnabled = configService.getBoolean(CHAT_SIMPLE_THEME_ENABLED_PROP, true);

        // Load the "lastContactParent" property.
        lastContactParent = configService.getString("gui.addcontact.lastContactParent");

        // Load the "sendFileLastDir" property.
        sendFileLastDir = configService.getString("gui.chat.filetransfer.SEND_FILE_LAST_DIR");

        // Load the "ADD_CONTACT_DISABLED" property.
        isAddContactDisabled = configService.getBoolean("gui.contactlist.CONTACT_ADD_DISABLED", false);

        // Load the "MERGE_CONTACT_DISABLED" property.
        isMergeContactDisabled = configService.getBoolean("gui.contactlist.CONTACT_MERGE_DISABLED", false);

        // Load the "CREATE_GROUP_DISABLED" property.
        isCreateGroupDisabled = configService.getBoolean("gui.contactlist.CREATE_GROUP_DISABLED", false);

        // Load the "FLATTEN_GROUP_ENABLED" property.
        isFlattenGroupEnabled = configService.getBoolean("gui.contactlist.FLATTEN_GROUP_ENABLED", false);

        // Load the "GO_TO_CHATROOM_DISABLED" property.
        isGoToChatRoomDisabled = configService.getBoolean("gui.chatroomslist.GO_TO_CHATROOM_DISABLED", false);

        // Load the "REMOVE_CONTACT_DISABLED" property.
        isRemoveContactDisabled = configService.getBoolean("gui.contactlist.CONTACT_REMOVE_DISABLED", false);

        // Load the "CONTACT_MOVE_DISABLED" property.
        isContactMoveDisabled = configService.getBoolean("gui.contactlist.CONTACT_MOVE_DISABLED", false);

        // Load the "CONTACT_RENAME_DISABLED" property.
        isContactRenameDisabled = configService.getBoolean("gui.contactlist.CONTACT_RENAME_DISABLED", false);

        // Load the "GROUP_REMOVE_DISABLED" property.
        isGroupRemoveDisabled = configService.getBoolean("gui.contactlist.GROUP_REMOVE_DISABLED", false);

        // Load the "GROUP_RENAME_DISABLED" property.
        isGroupRenameDisabled = configService.getBoolean("gui.contactlist.GROUP_RENAME_DISABLED", false);

        // Load the "PRESET_STATUS_MESSAGES" property.
        isPresetStatusMessagesEnabled = configService.getBoolean("gui.presence.PRESET_STATUS_MESSAGES", true);

        // Load the gui.main.account.ADVANCED_CONFIG_DISABLED" property.
        String advancedConfigDisabledDefaultProp
                = UtilActivator.getResources().getSettingsString("gui.account.ADVANCED_CONFIG_DISABLED");

        boolean isAdvancedConfigDisabled = false;
        if (StringUtils.isNotEmpty(advancedConfigDisabledDefaultProp))
            isAdvancedConfigDisabled = Boolean.parseBoolean(advancedConfigDisabledDefaultProp);

        // Load the advanced account configuration disabled.
        isAdvancedAccountConfigDisabled
                = configService.getBoolean("gui.account.ADVANCED_CONFIG_DISABLED", isAdvancedConfigDisabled);

        // Single interface enabled property.
        String singleInterfaceEnabledProp = UtilActivator.getResources().getSettingsString(SINGLE_WINDOW_INTERFACE_ENABLED);

        boolean isEnabled;
        if (StringUtils.isNotEmpty(singleInterfaceEnabledProp))
            isEnabled = Boolean.parseBoolean(singleInterfaceEnabledProp);
        else
            isEnabled = Boolean.parseBoolean(UtilActivator.getResources().getSettingsString("gui.SINGLE_WINDOW_INTERFACE"));

        // Load the advanced account configuration disabled.
        isSingleWindowInterfaceEnabled = configService.getBoolean(SINGLE_WINDOW_INTERFACE_ENABLED, isEnabled);

        if (isFontSupportEnabled()) {
            // Load default font family string.
            defaultFontFamily = configService.getString("gui.chat.DEFAULT_FONT_FAMILY");

            // Load default font size.
            defaultFontSize = configService.getString("gui.chat.DEFAULT_FONT_SIZE");

            // Load isBold chat property.
            isDefaultFontBold = configService.getBoolean("gui.chat.DEFAULT_FONT_BOLD", isDefaultFontBold);

            // Load isItalic chat property.
            isDefaultFontItalic = configService.getBoolean("gui.chat.DEFAULT_FONT_ITALIC", isDefaultFontItalic);

            // Load isUnderline chat property.
            isDefaultFontUnderline = configService.getBoolean("gui.chat.DEFAULT_FONT_UNDERLINE", isDefaultFontUnderline);

            // Load default font color property.
            int colorSetting = configService.getInt("gui.chat.DEFAULT_FONT_COLOR", -1);

            if (colorSetting != -1)
                defaultFontColor = colorSetting;
        }

        String showStatusChangedInChatDefault = UtilActivator.getResources().getSettingsString(pShowStatusChangedInChat);

        // if there is a default value use it
        if (StringUtils.isNotEmpty(showStatusChangedInChatDefault))
            showStatusChangedInChat = Boolean.parseBoolean(showStatusChangedInChatDefault);

        showStatusChangedInChat = configService.getBoolean(pShowStatusChangedInChat, showStatusChangedInChat);

        String routeVideoAndDesktopUsingPhoneNumberDefault
                = UtilActivator.getResources().getSettingsString(pRouteVideoAndDesktopUsingPhoneNumber);

        if (StringUtils.isNotEmpty(routeVideoAndDesktopUsingPhoneNumberDefault))
            routeVideoAndDesktopUsingPhoneNumber = Boolean.parseBoolean(routeVideoAndDesktopUsingPhoneNumberDefault);

        routeVideoAndDesktopUsingPhoneNumber
                = configService.getBoolean(pRouteVideoAndDesktopUsingPhoneNumber, routeVideoAndDesktopUsingPhoneNumber);

        String hideAccountMenuDefaultValue = UtilActivator.getResources().getSettingsString(pHideAccountMenu);

        if (StringUtils.isNotEmpty(hideAccountMenuDefaultValue))
            hideAccountSelectionWhenPossible = Boolean.parseBoolean(hideAccountMenuDefaultValue);

        hideAccountSelectionWhenPossible = configService.getBoolean(pHideAccountMenu, hideAccountSelectionWhenPossible);

        String hideAccountsStatusDefaultValue
                = UtilActivator.getResources().getSettingsString(pHideAccountStatusSelectors);

        if (StringUtils.isNotEmpty(hideAccountsStatusDefaultValue))
            hideAccountStatusSelectors = Boolean.parseBoolean(hideAccountsStatusDefaultValue);

        hideAccountStatusSelectors = configService.getBoolean(pHideAccountStatusSelectors,
                hideAccountStatusSelectors);

        String autoAnswerDisableSubmenuDefaultValue
                = UtilActivator.getResources().getSettingsString(pAutoAnswerDisableSubmenu);

        if (StringUtils.isNotEmpty(autoAnswerDisableSubmenuDefaultValue))
            autoAnswerDisableSubmenu = Boolean.parseBoolean(autoAnswerDisableSubmenuDefaultValue);

        autoAnswerDisableSubmenu = configService.getBoolean(pAutoAnswerDisableSubmenu, autoAnswerDisableSubmenu);

        isChatRoomConfigDisabled = configService.getBoolean(CHAT_ROOM_CONFIG_DISABLED_PROP, isChatRoomConfigDisabled);

        isNormalizePhoneNumber = configService.getBoolean("gui.NORMALIZE_PHONE_NUMBER", true);

        alerterEnabled = configService.getBoolean(ALERTER_ENABLED_PROP, true);

        // Load the "ACCEPT_PHONE_NUMBER_WITH_ALPHA_CHARS" property.
        acceptPhoneNumberWithAlphaChars = configService.getBoolean("gui.ACCEPT_PHONE_NUMBER_WITH_ALPHA_CHARS", true);

        isHideAddressInCallHistoryTooltipEnabled = configService.getBoolean(HIDE_ADDR_IN_CALL_HISTORY_TOOLTIP_PROPERTY,
                isHideAddressInCallHistoryTooltipEnabled);

        isHideDomainInReceivedCallDialogEnabled = configService.getBoolean(HIDE_DOMAIN_IN_RECEIVE_CALL_DIALOG_PROPERTY,
                isHideDomainInReceivedCallDialogEnabled);

        String hideExtendedAwayStatusDefaultValue = UtilActivator.getResources().getSettingsString(pHideExtendedAwayStatus);

        if (StringUtils.isNotEmpty(hideExtendedAwayStatusDefaultValue))
            hideExtendedAwayStatus = Boolean.parseBoolean(hideExtendedAwayStatusDefaultValue);

        hideExtendedAwayStatus = configService.getBoolean(pHideExtendedAwayStatus, hideExtendedAwayStatus);

        isSmsNotifyTextDisabled = configService.getBoolean(SMS_MSG_NOTIFY_TEXT_DISABLED_PROP, isSmsNotifyTextDisabled);
        showMasterPasswordWarning = configService.getBoolean(MASTER_PASS_WARNING_PROP, true);

        // Quite Time settings
        isQuiteHoursEnable = configService.getBoolean(pQuiteHoursEnable, true);
        quiteHoursStart = configService.getLong(pQuiteHoursStart, TimePreference.DEFAULT_VALUE);
        quiteHoursEnd = configService.getLong(pQuiteHoursEnd, TimePreference.DEFAULT_VALUE);

        isHeadsUpEnable = configService.getBoolean(pHeadsUpEnable, true);
    }

    /**
     * Checks whether font support is disabled, checking in default settings for the default value.
     *
     * @return is font support disabled.
     */
    public static boolean isFontSupportEnabled()
    {
        String fontDisabledProp = "gui.FONT_SUPPORT_ENABLED";
        boolean defaultValue = false;

        String defaultSettingStr = UtilActivator.getResources().getSettingsString(fontDisabledProp);

        if (StringUtils.isNotEmpty(defaultSettingStr))
            defaultValue = Boolean.parseBoolean(defaultSettingStr);

        return configService.getBoolean(fontDisabledProp, defaultValue);
    }

    /**
     * Return TRUE if "autoPopupNewMessage" property is true, otherwise - return FALSE. Indicates
     * to the user interface whether new messages should be opened and bring to front.
     *
     * @return TRUE if "autoPopupNewMessage" property is true, otherwise - return FALSE.
     */
    public static boolean isAutoPopupNewMessage()
    {
        return autoPopupNewMessage;
    }

    /**
     * Updates the "autoPopupNewMessage" property.
     *
     * @param autoPopup indicates to the user interface whether new messages should be opened and bring to front.
     **/
    public static void setAutoPopupNewMessage(boolean autoPopup)
    {
        autoPopupNewMessage = autoPopup;

        if (autoPopupNewMessage)
            configService.setProperty(pAutoPopupNewMessage, "yes");
        else
            configService.setProperty(pAutoPopupNewMessage, "no");
    }

    /**
     * Return TRUE if "showCallPanel" property is true, otherwise - return FALSE. Indicates to the
     * user interface whether the panel containing the call and hangup buttons should be shown.
     *
     * @return TRUE if "showCallPanel" property is true, otherwise - return FALSE.
     */
    public static boolean isCallPanelShown()
    {
        return isCallPanelShown;
    }

    /**
     * Return TRUE if "showOffline" property is true, otherwise - return FALSE. Indicates to the
     * user interface whether offline user should be shown in the contact list or not.
     *
     * @return TRUE if "showOffline" property is true, otherwise - return FALSE.
     */
    public static boolean isShowOffline()
    {
        return isShowOffline;
    }

    /**
     * Return TRUE if "showApplication" property is true, otherwise - return FALSE. Indicates to
     * the user interface whether the main application window should shown or hidden on startup.
     *
     * @return TRUE if "showApplication" property is true, otherwise - return FALSE.
     */
    public static boolean isApplicationVisible()
    {
        return isApplicationVisible;
    }

    /**
     * Return TRUE if "quitWarningShown" property is true, otherwise - return FALSE. Indicates to the user
     * interface whether the quit warning dialog should be shown when user clicks on the X button.
     *
     * @return TRUE if "quitWarningShown" property is true, otherwise - return FALSE. Indicates to
     * the user interface whether the quit warning dialog should be shown when user clicks on the X button.
     */
    public static boolean isQuitWarningShown()
    {
        return isQuitWarningShown;
    }

    /**
     * Return TRUE if "iaAuotStart" property is true, otherwise - return FALSE.
     * Indicates if aTalk will auto start on device reboot.
     *
     * @return TRUE if "iaAuotStart" property is true, otherwise - return FALSE.
     */
    public static boolean isAutoStartEnable()
    {
        return isAutoStartOnBoot;
    }

    /**
     * Updates the "isAutoStartOnBoot" property through the <tt>ConfigurationService</tt>.
     *
     * @param autoStart {@code true} to auto start aTalk on device reboot
     */
    public static void setAutoStart(boolean autoStart)
    {
        isAutoStartOnBoot = autoStart;
        configService.setProperty(pAutoStart, Boolean.toString(autoStart));
    }


    /**
     * Return TRUE if "isTtsEnable" property is true, otherwise - return FALSE.
     * Indicates if TTS is enabled.
     *
     * @return TRUE if "isTtsEnable" property is true, otherwise - return FALSE.
     */
    public static boolean isTtsEnable()
    {
        return isTtsEnable;
    }

    /**
     * Updates the "isTtsEnable" property through the <tt>ConfigurationService</tt>.
     *
     * @param ttsEnable {@code true} to enable tts option
     */
    public static void setTtsEnable(boolean ttsEnable)
    {
        isTtsEnable = ttsEnable;
        configService.setProperty(pTTSEnable, Boolean.toString(ttsEnable));
    }

    public static int getTtsDelay()
    {
        return ttsDelay;
    }

    /**
     * Updates the "isTtsEnable" property through the <tt>ConfigurationService</tt>.
     *
     * @param ttsEnable {@code true} to enable tts option
     */
    public static void setTtsDelay(int delay)
    {
        ttsDelay = delay;
        configService.setProperty(pTTSDelay, delay);
    }

    /**
     * Return TRUE if "sendMessageDeliveryReceipt" property is true, otherwise - return FALSE.
     * Indicates to the user interface whether message delivery receipts are enabled or disabled.
     *
     * @return TRUE if "sendTypingNotifications" property is true, otherwise - return FALSE.
     */
    public static boolean isSendMessageDeliveryReceipt()
    {
        return isSendMessageDeliveryReceipt;
    }

    /**
     * Updates the "sendChatStateNotifications" property through the <tt>ConfigurationService</tt>.
     *
     * @param isDeliveryReceipt {@code true} to indicate that message delivery receipts are enabled,
     * {@code false} otherwise.
     */
    public static void setSendMessageDeliveryReceipt(boolean isDeliveryReceipt)
    {
        isSendMessageDeliveryReceipt = isDeliveryReceipt;
        configService.setProperty(pMessageDeliveryReceipt, Boolean.toString(isDeliveryReceipt));
        updateDeliveryReceiptFeature(isDeliveryReceipt);
    }

    /**
     * Return TRUE if "sendChatStateNotifications" property is true, otherwise - return FALSE.
     * Indicates to the user interface whether chat state notifications are enabled or disabled.
     *
     * @return TRUE if "sendTypingNotifications" property is true, otherwise - return FALSE.
     */
    public static boolean isSendChatStateNotifications()
    {
        return isSendChatStateNotifications;
    }

    /**
     * Updates the "sendChatStateNotifications" property through the <tt>ConfigurationService</tt>.
     *
     * @param isChatStateNotification {@code true} to indicate that chat state notifications are enabled,
     * {@code false} otherwise.
     */
    public static void setSendChatStateNotifications(boolean isChatStateNotification)
    {
        isSendChatStateNotifications = isChatStateNotification;
        configService.setProperty(pTypingNotification, Boolean.toString(isChatStateNotification));
        updateChatStateCapsFeature(isChatStateNotification);
    }

    /**
     * Return TRUE if "sendChatStateNotifications" property is true, otherwise - return FALSE.
     * Indicates to the user interface whether chat state notifications are enabled or disabled.
     *
     * @return TRUE if "sendTypingNotifications" property is true, otherwise - return FALSE.
     */
    public static boolean isSendThumbnail()
    {
        return isSendThumbnail;
    }

    /**
     * Updates the "sendChatStateNotifications" property through the <tt>ConfigurationService</tt>.
     *
     * @param sendThumbnail {@code true} to indicate that chat state notifications are enabled,
     * {@code false} otherwise.
     */
    public static void setSendThumbnail(boolean sendThumbnail)
    {
        isSendThumbnail = sendThumbnail;
        configService.setProperty(pSendThumbnail, Boolean.toString(isSendThumbnail));
        FileTransferConversation.FT_THUMBNAIL_ENABLE = sendThumbnail;
    }

    /**
     * Check to see the file size specified is autoAcceptable:
     * 1. size > 0
     * 2. acceptFileSize != 0 (never)
     * 3. size <= acceptFileSize
     *
     * @param size current file size
     * @return true is auto aceept to download
     */
    public static boolean isAutoAcceptFile(long size)
    {
        return (size > 0) && (acceptFileSize != 0) && (size <= acceptFileSize);
    }

    /**
     * The maximum file size that user will automatically accept for download.
     *
     * @return the auto accept file size.
     */
    public static long getAutoAcceptFileSize()
    {
        return acceptFileSize;
    }

    /**
     * Updates the "acceptFileSize" property through the <tt>ConfigurationService</tt>.
     *
     * @param fileSize indicates if the maximum file size for auto accept.
     */
    public static void setAutoAcceptFileSizeSize(int fileSize)
    {
        acceptFileSize = fileSize;
        configService.setProperty(pAcceptFileSize, Integer.toString(acceptFileSize));
    }

    /**
     * Return TRUE if "isPresenceSubscribeAuto" property is true, otherwise - return FALSE.
     * Indicates to user whether presence subscription mode is auto or manual approval.
     *
     * @return TRUE if "isPresenceSubscribeAuto" property is true, otherwise - return FALSE.
     */
    public static boolean isPresenceSubscribeAuto()
    {
        return isPresenceSubscribeAuto;
    }

    /**
     * Updates the "isPresenceSubscribeAuto" property through the <tt>ConfigurationService</tt>.
     *
     * @param presenceSubscribeAuto {@code true} to indicate that chat state notifications are enabled,
     * {@code false} otherwise.
     */
    public static void setPresenceSubscribeAuto(boolean presenceSubscribeAuto)
    {
        isPresenceSubscribeAuto = presenceSubscribeAuto;
        configService.setProperty(pPresenceSubscribeAuto, Boolean.toString(presenceSubscribeAuto));
        if (presenceSubscribeAuto)
            Roster.setDefaultSubscriptionMode(Roster.SubscriptionMode.accept_all);
        else
            Roster.setDefaultSubscriptionMode(Roster.SubscriptionMode.manual);
    }

    /**
     * Returns TRUE if the "isMoveContactConfirmationRequested" property is true, otherwise -
     * returns FALSE. Indicates to the user interface whether the confirmation window during the
     * move contact process is enabled or not.
     *
     * @return TRUE if the "isMoveContactConfirmationRequested" property is true, otherwise - returns FALSE
     */
    public static boolean isMoveContactConfirmationRequested()
    {
        return isMoveContactConfirmationRequested;
    }

    /**
     * Returns {@code true} if the "isMultiChatWindowEnabled" property is true, otherwise -
     * returns {@code false}. Indicates to the user interface whether the chat window could
     * contain multiple chats or just one chat.
     *
     * @return {@code true} if the "isMultiChatWindowEnabled" property is true, otherwise - returns {@code false}.
     */
    public static boolean isMultiChatWindowEnabled()
    {
        return isMultiChatWindowEnabled;
    }

    /**
     * Returns {@code true} if the "isPrivateMessagingInChatRoomDisabled" property is true,
     * otherwise - returns {@code false}. Indicates to the user interface whether the
     * private messaging is disabled in chat rooms.
     *
     * @return {@code true} if the "isPrivateMessagingInChatRoomDisabled" property is true,
     * otherwise - returns {@code false}.
     */
    public static boolean isPrivateMessagingInChatRoomDisabled()
    {
        return isPrivateMessagingInChatRoomDisabled;
    }

    /**
     * Updates the "isMultiChatWindowEnabled" property through the <tt>ConfigurationService</tt>.
     *
     * @param isEnabled indicates if the chat window could contain multiple chats or only one chat.
     */
    public static void setMultiChatWindowEnabled(boolean isEnabled)
    {
        isMultiChatWindowEnabled = isEnabled;
        configService.setProperty(pMultiChatWindowEnabled, Boolean.toString(isMultiChatWindowEnabled));
    }

    /**
     * Returns {@code true} if the "isLeaveChatRoomOnWindowCloseEnabled" property is true,
     * otherwise - returns {@code false}. Indicates to the user interface whether when
     * closing the chat window we would leave the chat room.
     *
     * @return {@code true} if the "isLeaveChatRoomOnWindowCloseEnabled" property is true,
     * otherwise - returns {@code false}.
     */
    public static boolean isLeaveChatRoomOnWindowCloseEnabled()
    {
        return isLeaveChatRoomOnWindowCloseEnabled;
    }

    /**
     * Updates the "isLeaveChatRoomOnWindowClose" property through the <tt>ConfigurationService</tt>.
     *
     * @param isLeave indicates whether to leave chat room on window close.
     */
    public static void setLeaveChatRoomOnWindowClose(boolean isLeave)
    {
        isLeaveChatRoomOnWindowCloseEnabled = isLeave;
        configService.setProperty(pLeaveChatRoomOnWindowClose, Boolean.toString(isLeaveChatRoomOnWindowCloseEnabled));
    }

    /**
     * Returns {@code true} if the "isHistoryShown" property is true, otherwise - returns
     * {@code false}. Indicates to the user whether the history is shown in the chat window.
     *
     * @return {@code true} if the "isHistoryShown" property is true, otherwise - returns {@code false}.
     */
    public static boolean isHistoryShown()
    {
        return isHistoryShown;
    }

    /**
     * Returns {@code true} if the "isRecentMessagesShown" property is true, otherwise -
     * returns {@code false}. Indicates to the user whether the recent messages are shown.
     *
     * @return {@code true} if the "isRecentMessagesShown" property is true, otherwise {@code false}
     * .
     */
    public static boolean isRecentMessagesShown()
    {
        return isRecentMessagesShown;
    }

    /**
     * Updates the "isHistoryShown" property through the <tt>ConfigurationService</tt>.
     *
     * @param isShown indicates if the message history is shown
     */
    public static void setHistoryShown(boolean isShown)
    {
        isHistoryShown = isShown;
        configService.setProperty(pMessageHistoryShown, Boolean.toString(isHistoryShown));
    }

    /**
     * Updates the "isRecentMessagesShown" property through the <tt>ConfigurationService</tt>.
     *
     * @param isShown indicates if the recent messages is shown
     */
    public static void setRecentMessagesShown(boolean isShown)
    {
        isRecentMessagesShown = isShown;
        // configService.setProperty(MessageHistoryService.PNAME_IS_RECENT_MESSAGES_DISABLED,
        // Boolean.toString(!isRecentMessagesShown));
    }

    /**
     * Returns {@code true} if the "isWindowDecorated" property is true, otherwise - returns {@code false}..
     *
     * @return {@code true} if the "isWindowDecorated" property is true, otherwise - returns {@code false}.
     */
    public static boolean isWindowDecorated()
    {
        return isWindowDecorated;
    }

    /**
     * Returns {@code true}if the "isChatToolbarVisible" property is true, otherwise - returns {@code false}..
     *
     * @return {@code true} if the "isChatToolbarVisible" property is true, otherwise - returns {@code false}.
     */
    public static boolean isChatToolbarVisible()
    {
        return isChatToolbarVisible;
    }

    /**
     * Returns {@code true} if the "isChatStyleBarVisible" property is true, otherwise - returns {@code false}..
     *
     * @return {@code true}if the "isChatStyleBarVisible" property is true, otherwise - returns {@code false}
     * .
     */
    public static boolean isChatStyleBarVisible()
    {
        return isChatStyleBarVisible;
    }

    /**
     * Returns {@code true} if the "isChatSimpleTheme" property is true, otherwise - returns {@code false}..
     *
     * @return {@code true} if the "isChatSimpleTheme" property is true, otherwise - returns {@code false}.
     */
    public static boolean isChatSimpleThemeEnabled()
    {
        return isChatSimpleThemeEnabled;
    }

    /**
     * Returns {@code true} if the "ADD_CONTACT_DISABLED" property is true, otherwise - returns {@code false}..
     *
     * @return {@code true} if the "ADD_CONTACT_DISABLED" property is true, otherwise - returns {@code false}.
     */
    public static boolean isAddContactDisabled()
    {
        return isAddContactDisabled;
    }

    /**
     * Returns {@code true} if the "MERGE_CONTACT_DISABLED" property is true, otherwise - returns {@code false}.
     *
     * @return {@code true} if the "MERGE_CONTACT_DISABLED" property is true, otherwise - returns {@code false}.
     */
    public static boolean isMergeContactDisabled()
    {
        return isMergeContactDisabled;
    }

    /**
     * Returns {@code true} if the "CREATE_GROUP_DISABLED" property is true, otherwise - returns {@code false} ..
     *
     * @return {@code true} if the "CREATE_GROUP_DISABLED" property is true, otherwise - returns {@code false}
     * .
     */
    public static boolean isCreateGroupDisabled()
    {
        return isCreateGroupDisabled;
    }

    /**
     * Returns {@code true} if the "FLATTEN_GROUP_ENABLED" property is true, otherwise - returns {@code false} ..
     *
     * @return {@code true} if the "FLATTEN_GROUP_ENABLED" property is true, otherwise - returns {@code false}
     * .
     */
    public static boolean isFlattenGroupEnabled()
    {
        return isFlattenGroupEnabled;
    }

    /**
     * Returns {@code true} if the "GO_TO_CHATROOM_DISABLED" property is true, otherwise - returns {@code false}..
     *
     * @return {@code true} if the "GO_TO_CHATROOM_DISABLED" property is true, otherwise - returns {@code false}.
     */
    public static boolean isGoToChatroomDisabled()
    {
        return isGoToChatRoomDisabled;
    }

    /**
     * Returns {@code true} if the "REMOVE_CONTACT_DISABLED" property is true, otherwise - returns {@code false}.
     *
     * @return {@code true} if the "REMOVE_CONTACT_DISABLED" property is true, otherwise - returns {@code false}.
     */
    public static boolean isRemoveContactDisabled()
    {
        return isRemoveContactDisabled;
    }

    /**
     * Returns {@code true} if the "CONTACT_MOVE_DISABLED" property is true, otherwise - returns {@code false}.
     *
     * @return {@code true} if the "CONTACT_MOVE_DISABLED" property is true, otherwise - returns {@code false}.
     */
    public static boolean isContactMoveDisabled()
    {
        return isContactMoveDisabled;
    }

    /**
     * Returns {@code true} if the "CONTACT_RENAME_DISABLED" property is true, otherwise - returns {@code false}.
     *
     * @return {@code true} if the "CONTACT_RENAME_DISABLED" property is true, otherwise - returns {@code false}.
     */
    public static boolean isContactRenameDisabled()
    {
        return isContactRenameDisabled;
    }

    /**
     * Returns {@code true} if the "GROUP_REMOVE_DISABLED" property is true, otherwise - returns {@code false}.
     *
     * @return {@code true} if the "GROUP_REMOVE_DISABLED" property is true, otherwise - returns {@code false}.
     */
    public static boolean isGroupRemoveDisabled()
    {
        return isGroupRemoveDisabled;
    }

    /**
     * returns {@code true} if the "GROUP_RENAME_DISABLED" property is true, otherwise - returns {@code false}.
     *
     * @return {@code true} if the "GROUP_RENAME_DISABLED" property is true, otherwise - returns {@code false}.
     */
    public static boolean isGroupRenameDisabled()
    {
        return isGroupRenameDisabled;
    }

    /**
     * returns {@code true} if the "PRESET_STATUS_MESSAGES" property is true, otherwise - returns {@code false}.
     *
     * @return {@code true} if the "PRESET_STATUS_MESSAGES" property is true, otherwise - returns {@code false}.
     */
    public static boolean isPresetStatusMessagesEnabled()
    {
        return isPresetStatusMessagesEnabled;
    }

    /**
     * returns {@code true} if the "ADVANCED_CONFIG_DISABLED" property is true, otherwise - returns {@code false}..
     *
     * @return {@code true} if the "ADVANCED_CONFIG_DISABLED" property is true, otherwise - returns {@code false}.
     */
    public static boolean isAdvancedAccountConfigDisabled()
    {
        return isAdvancedAccountConfigDisabled;
    }

    /**
     * Indicates if the chat room user configuration functionality is disabled.
     *
     * @return <tt>true</tt> if the chat room configuration is disabled, <tt>false</tt> - otherwise
     */
    public static boolean isChatRoomConfigDisabled()
    {
        return isChatRoomConfigDisabled;
    }

    /**
     * returns the default chat font family.
     *
     * @return the default chat font family
     */
    public static String getChatDefaultFontFamily()
    {
        return defaultFontFamily;
    }

    /**
     * Returns the default chat font size.
     *
     * @return the default chat font size
     */
    public static int getChatDefaultFontSize()
    {
        if (StringUtils.isNotEmpty(defaultFontSize))
            return Integer.parseInt(defaultFontSize);
        return -1;
    }

    /**
     * Returns the default chat font color.
     *
     * @return the default chat font color
     */
    public static Color getChatDefaultFontColor()
    {
        return defaultFontColor == -1 ? null : new Color(defaultFontColor);
    }

    /**
     * Returns the default chat font bold.
     *
     * @return the default chat font bold
     */
    public static boolean isChatFontBold()
    {
        return isDefaultFontBold;
    }

    /**
     * Returns the default chat font italic.
     *
     * @return the default chat font italic
     */
    public static boolean isChatFontItalic()
    {
        return isDefaultFontItalic;
    }

    /**
     * Returns the default chat font underline.
     *
     * @return the default chat font underline
     */
    public static boolean isChatFontUnderline()
    {
        return isDefaultFontUnderline;
    }

    /**
     * Sets the advanced account config disabled property.
     *
     * @param disabled the new value to set
     */
    public static void setAdvancedAccountConfigDisabled(boolean disabled)
    {
        isAdvancedAccountConfigDisabled = disabled;
        configService.setProperty("gui.account.ADVANCED_CONFIG_DISABLED",
                Boolean.toString(isAdvancedAccountConfigDisabled));
    }

    /**
     * Return the "sendMessageCommand" property that was saved previously through the
     * <tt>ConfigurationService</tt>. Indicates to the user interface whether the default send
     * message command is Enter or  CTRL-Enter.
     *
     * @return "Enter" or "CTRL-Enter" message commands.
     */
    public static String getSendMessageCommand()
    {
        return sendMessageCommand;
    }

    /**
     * Updates the "sendMessageCommand" property through the <tt>ConfigurationService</tt>.
     *
     * @param newMessageCommand the command used to send a message ( it could be ENTER_COMMAND or CTRL_ENTER_COMMAND)
     */
    public static void setSendMessageCommand(String newMessageCommand)
    {
        sendMessageCommand = newMessageCommand;
        configService.setProperty(pMsgCommand, newMessageCommand);
    }

    /**
     * Return the "lastContactParent" property that was saved previously through the
     * <tt>ConfigurationService</tt>. Indicates the last selected group on adding new contact
     *
     * @return group name of the last selected group when adding contact.
     */
    public static String getLastContactParent()
    {
        return lastContactParent;
    }

    /**
     * Returns the call conference provider used for the last conference call.
     *
     * @return the call conference provider used for the last conference call
     */
    public static ProtocolProviderService getLastCallConferenceProvider()
    {
        if (lastCallConferenceProvider != null)
            return lastCallConferenceProvider;

        // Obtain the "lastCallConferenceAccount" property from the configuration service
        return findProviderFromAccountId(configService.getString("gui.call.lastCallConferenceProvider"));
    }

    /**
     * Returns the protocol provider associated with the given <tt>accountId</tt>.
     *
     * @param savedAccountId the identifier of the account
     * @return the protocol provider associated with the given <tt>accountId</tt>
     */
    private static ProtocolProviderService findProviderFromAccountId(String savedAccountId)
    {
        ProtocolProviderService protocolProvider = null;

        for (ProtocolProviderFactory providerFactory : UtilActivator.getProtocolProviderFactories().values()) {
            for (AccountID accountId : providerFactory.getRegisteredAccounts()) {
                // We're interested only in the savedAccountId
                if (!accountId.getAccountUniqueID().equals(savedAccountId))
                    continue;

                ServiceReference<ProtocolProviderService> serRef = providerFactory.getProviderForAccount(accountId);

                protocolProvider = UtilActivator.bundleContext.getService(serRef);
                if (protocolProvider != null)
                    break;
            }
        }
        return protocolProvider;
    }

    /**
     * Returns the number of messages from chat history that would be shown in the chat window.
     *
     * @return the number of messages from chat history that would be shown in the chat window.
     */
    public static String getWebPage()
    {
        WebViewFragment.initWebView();
        return StringUtils.isBlank(mWebPage)
                ? aTalkApp.getResString(R.string.service_gui_settings_WEBVIEW_SUMMARY) : mWebPage;
    }

    /**
     * Updates the "webPage" property through the <tt>ConfigurationService</tt>.
     *
     * @param webPage the web page for access.
     */
    public static void setWebPage(String webPage)
    {
        mWebPage = StringUtils.isEmpty(webPage) ? webPage : webPage.trim();
        configService.setProperty(pWebPage, webPage);
    }

    /**
     * Returns the number of messages from chat history that would be shown in the chat window.
     *
     * @return the number of messages from chat history that would be shown in the chat window.
     */
    public static int getChatHistorySize()
    {
        return chatHistorySize;
    }

    /**
     * Updates the "chatHistorySize" property through the <tt>ConfigurationService</tt>.
     *
     * @param historySize indicates if the history logging is enabled.
     */
    public static void setChatHistorySize(int historySize)
    {
        chatHistorySize = historySize;
        configService.setProperty(pChatHistorySize, Integer.toString(chatHistorySize));
    }

    /**
     * Returns the preferred height of the chat write area.
     *
     * @return the preferred height of the chat write area.
     */
    public static int getChatWriteAreaSize()
    {
        return chatWriteAreaSize;
    }

    /**
     * Returns {@code true} if transparent windows are enabled, {@code false} otherwise.
     *
     * @return {@code true} if transparent windows are enabled, {@code false} otherwise.
     */
    public static boolean isTransparentWindowEnabled()
    {
        return isTransparentWindowEnabled;
    }

    /**
     * Returns the transparency value for all transparent windows.
     *
     * @return the transparency value for all transparent windows.
     */
    public static int getWindowTransparency()
    {
        return windowTransparency;
    }

    /**
     * Returns the last opened directory of the send file file chooser.
     *
     * @return the last opened directory of the send file file chooser
     */
    public static String getSendFileLastDir()
    {
        return sendFileLastDir;
    }

    /**
     * Returns {@code true} if phone numbers should be normalized, {@code false} otherwise.
     *
     * @return {@code true} if phone numbers should be normalized, {@code false} otherwise.
     */
    public static boolean isNormalizePhoneNumber()
    {
        return isNormalizePhoneNumber;
    }

    /**
     * Updates the "NORMALIZE_PHONE_NUMBER" property.
     *
     * @param isNormalize indicates to the user interface whether all dialed phone numbers should be normalized
     */
    public static void setNormalizePhoneNumber(boolean isNormalize)
    {
        isNormalizePhoneNumber = isNormalize;
        configService.setProperty("gui.NORMALIZE_PHONE_NUMBER", Boolean.toString(isNormalize));
    }

    /**
     * Returns {@code true} if window alerter is enabled (tack bar or dock icon).
     *
     * @return {@code true} if window alerter is enables, {@code false} otherwise.
     */
    public static boolean isAlerterEnabled()
    {
        return alerterEnabled;
    }

    /**
     * Updates the "chatalerter.ENABLED" property.
     *
     * @param isEnabled indicates whether to enable or disable alerter.
     */
    public static void setAlerterEnabled(boolean isEnabled)
    {
        alerterEnabled = isEnabled;
        configService.setProperty(ALERTER_ENABLED_PROP, Boolean.toString(isEnabled));
    }

    public static void setQuiteHour(String property, Object value)
    {
        if (value instanceof Boolean) {
            setQuiteHoursEnable(Boolean.valueOf(value.toString()));
        }
        else if (pQuiteHoursStart.equals(property)) {
            setQuiteHoursStart((Long) value);
        }
        else {
            setQuiteHoursEnd((Long) value);
        }
    }

    /**
     * Returns {@code true} if Quite Hours is enabled
     *
     * @return {@code true} if Quite Hours is enables, {@code false} otherwise.
     */
    public static boolean isQuiteHoursEnable()
    {
        return isQuiteHoursEnable;
    }

    /**
     * Updates the Quite Hours property.
     *
     * @param isEnabled indicates whether to enable or disable quite hours.
     */
    public static void setQuiteHoursEnable(boolean isEnabled)
    {
        isQuiteHoursEnable = isEnabled;
        configService.setProperty(pQuiteHoursEnable, Boolean.toString(isEnabled));
    }

    /**
     * Returns Quite Hours start time.
     *
     * @return {@code true} get the Quite Hours start time.
     */
    public static long getQuiteHoursStart()
    {
        return quiteHoursStart;
    }

    /**
     * Updates the Quite Hours start time
     *
     * @param isEnabled set the quite hours start time.
     */
    public static void setQuiteHoursStart(long time)
    {
        quiteHoursStart = time;
        configService.setProperty(pQuiteHoursStart, time);
    }

    /**
     * Returns Quite Hours end time.
     *
     * @return {@code true} get the Quite Hours end time.
     */
    public static long getQuiteHoursEnd()
    {
        return quiteHoursEnd;
    }

    /**
     * Updates the Quite Hours end time
     *
     * @param isEnabled set the quite hours end time.
     */
    public static void setQuiteHoursEnd(long time)
    {
        quiteHoursEnd = time;
        configService.setProperty(pQuiteHoursEnd, time);
    }

    /**
     * Return TRUE if "sendChatStateNotifications" property is true, otherwise - return FALSE.
     * Indicates to the user interface whether chat state notifications are enabled or disabled.
     *
     * @return TRUE if "sendTypingNotifications" property is true, otherwise - return FALSE.
     */
    public static boolean isHeadsUpEnable()
    {
        return isHeadsUpEnable;
    }

    /**
     * Updates the "sendChatStateNotifications" property through the <tt>ConfigurationService</tt>.
     *
     * @param sendThumbnail {@code true} to indicate that chat state notifications are enabled,
     * {@code false} otherwise.
     */
    public static void setHeadsUp(boolean headsUp)
    {
        isHeadsUpEnable = headsUp;
        configService.setProperty(pHeadsUpEnable, Boolean.toString(isHeadsUpEnable));
    }

    /**
     * Returns {@code true} if a string with a alphabetical character might be considered as
     * a phone number. {@code false} otherwise.
     *
     * @return {@code true} if a string with a alphabetical character might be considered as
     * a phone number. {@code false} otherwise.
     */
    public static boolean acceptPhoneNumberWithAlphaChars()
    {
        return acceptPhoneNumberWithAlphaChars;
    }

    /**
     * Updates the "ACCEPT_PHONE_NUMBER_WITH_CHARS" property.
     *
     * @param accept indicates to the user interface whether a string with alphabetical characters might be
     * accepted as a phone number.
     */
    public static void setAcceptPhoneNumberWithAlphaChars(boolean accept)
    {
        acceptPhoneNumberWithAlphaChars = accept;
        configService.setProperty("gui.ACCEPT_PHONE_NUMBER_WITH_ALPHA_CHARS",
                Boolean.toString(acceptPhoneNumberWithAlphaChars));
    }

    /**
     * Returns {@code true} if status changed should be shown in chat history area, {@code false} otherwise.
     *
     * @return {@code true} if status changed should be shown in chat history area, {@code false} otherwise.
     */
    public static boolean isShowStatusChangedInChat()
    {
        return showStatusChangedInChat;
    }

    /**
     * Whether allow to use additional phone numbers to route video calls and desktop sharing through it.
     *
     * @return whether allow to use additional phone numbers to route video calls and desktop sharing through it.
     */
    public static boolean isRouteVideoAndDesktopUsingPhoneNumberEnabled()
    {
        return routeVideoAndDesktopUsingPhoneNumber;
    }

    /**
     * Whether allow user to select account when only a single account is available.
     *
     * @return whether allow user to select account when only a single account is available.
     */
    public static boolean isHideAccountSelectionWhenPossibleEnabled()
    {
        return hideAccountSelectionWhenPossible;
    }

    /**
     * Whether to hide account statuses from global menu.
     *
     * @return whether to hide account statuses.
     */
    public static boolean isHideAccountStatusSelectorsEnabled()
    {
        return hideAccountStatusSelectors;
    }

    /**
     * Whether to hide extended away status from global menu.
     *
     * @return whether to hide extended away status.
     */
    public static boolean isHideExtendedAwayStatus()
    {
        return hideExtendedAwayStatus;
    }

    /**
     * Whether creation of separate submenu for auto answer is disabled.
     *
     * @return whether creation of separate submenu for auto answer is disabled.
     */
    public static boolean isAutoAnswerDisableSubmenu()
    {
        return autoAnswerDisableSubmenu;
    }

    /**
     * Indicates if the single interface is enabled.
     *
     * @return <tt>true</tt> if the single window interface is enabled, <tt>false</tt> - otherwise
     */
    public static boolean isSingleWindowInterfaceEnabled()
    {
        return isSingleWindowInterfaceEnabled;
    }

    /**
     * Whether addresses will be shown in call history tooltips.
     *
     * @return whether addresses will be shown in call history tooltips.
     */
    public static boolean isHideAddressInCallHistoryTooltipEnabled()
    {
        return isHideAddressInCallHistoryTooltipEnabled;
    }

    /**
     * Whether to display or not the text notifying that a message is a incoming or outgoing sms message.
     *
     * @return whether to display the text notifying that a message is sms.
     */
    public static boolean isSmsNotifyTextDisabled()
    {
        return isSmsNotifyTextDisabled;
    }

    /**
     * Whether domain will be shown in receive call dialog.
     *
     * @return whether domain will be shown in receive call dialog.
     */
    public static boolean isHideDomainInReceivedCallDialogEnabled()
    {
        return isHideDomainInReceivedCallDialogEnabled;
    }

    /**
     * Whether to show or not the master password warning.
     *
     * @return {@code true} to show it, and {@code false} otherwise.
     */
    public static boolean showMasterPasswordWarning()
    {
        return showMasterPasswordWarning;
    }

    /**
     * Updates the value of whether to show master password warning.
     *
     * @param value the new value to set.
     */
    public static void setShowMasterPasswordWarning(boolean value)
    {
        showMasterPasswordWarning = value;
        configService.setProperty(MASTER_PASS_WARNING_PROP, value);
    }

    /**
     * Updates the "singleWindowInterface" property through the <tt>ConfigurationService</tt>.
     *
     * @param isEnabled {@code true} to indicate that the single window interface is enabled, <tt>false</tt> - otherwise
     */
    public static void setSingleWindowInterfaceEnabled(boolean isEnabled)
    {
        isSingleWindowInterfaceEnabled = isEnabled;
        configService.setProperty(SINGLE_WINDOW_INTERFACE_ENABLED, isEnabled);
    }

    /**
     * Sets the transparency value for all transparent windows.
     *
     * @param transparency the transparency value for all transparent windows.
     */
    public static void setWindowTransparency(int transparency)
    {
        windowTransparency = transparency;
    }

    /**
     * Updates the "showOffline" property through the <tt>ConfigurationService</tt>.
     *
     * @param isShowOffline {@code true} to indicate that the offline users should be shown, {@code false} otherwise.
     */
    public static void setShowOffline(boolean isShowOffline)
    {
        ConfigurationUtils.isShowOffline = isShowOffline;
        configService.setProperty("gui.showOffline", Boolean.toString(isShowOffline));
    }

    /**
     * Updates the "showCallPanel" property through the <tt>ConfigurationService</tt>.
     *
     * @param isCallPanelShown {@code true} to indicate that the call panel should be shown, {@code false} otherwise.
     */
    public static void setShowCallPanel(boolean isCallPanelShown)
    {
        ConfigurationUtils.isCallPanelShown = isCallPanelShown;
        configService.setProperty("gui.showCallPanel", Boolean.toString(isCallPanelShown));
    }

    /**
     * Updates the "showApplication" property through the <tt>ConfigurationService</tt>.
     *
     * @param isVisible {@code true} to indicate that the application should be shown, {@code false} otherwise.
     */
    public static void setApplicationVisible(boolean isVisible)
    {
        // If we're already in the desired visible state, don't change anything.
        if (isApplicationVisible == isVisible)
            return;

        isApplicationVisible = isVisible;
        configService.setProperty("systray.showApplication", Boolean.toString(isVisible));
    }

    /**
     * Updates the "showAppQuitWarning" property through the <tt>ConfigurationService</tt>.
     *
     * @param isWarningShown indicates if the message warning the user that the application would not be closed if
     * she clicks the X button would be shown again.
     */
    public static void setQuitWarningShown(boolean isWarningShown)
    {
        isQuitWarningShown = isWarningShown;
        configService.setProperty("gui.quitWarningShown", Boolean.toString(isQuitWarningShown));
    }

    /**
     * Saves the popup handler choice made by the user.
     *
     * @param handler the handler which will be used
     */
    public static void setPopupHandlerConfig(String handler)
    {
        configService.setProperty("systray.POPUP_HANDLER", handler);
    }

    /**
     * Updates the "lastContactParent" property through the <tt>ConfigurationService</tt>.
     *
     * @param groupName the group name of the selected group when adding last contact
     */
    public static void setLastContactParent(String groupName)
    {
        lastContactParent = groupName;
        configService.setProperty("gui.addcontact.lastContactParent", groupName);
    }

    /**
     * Updates the "isMoveContactQuestionEnabled" property through the <tt>ConfigurationService</tt>.
     *
     * @param isRequested indicates if a confirmation would be requested from user during the move contact process.
     */
    public static void setMoveContactConfirmationRequested(boolean isRequested)
    {
        isMoveContactConfirmationRequested = isRequested;
        configService.setProperty("gui.isMoveContactConfirmationRequested",
                Boolean.toString(isMoveContactConfirmationRequested));
    }

    /**
     * Updates the "isTransparentWindowEnabled" property through the <tt>ConfigurationService</tt>.
     *
     * @param isTransparent indicates if the transparency is enabled in the application.
     */
    public static void setTransparentWindowEnabled(boolean isTransparent)
    {
        isTransparentWindowEnabled = isTransparent;
        configService.setProperty(pTransparentWindowEnabled, Boolean.toString(isTransparentWindowEnabled));
    }

    /**
     * Updates the "isChatToolbarVisible" property through the <tt>ConfigurationService</tt>.
     *
     * @param isVisible indicates if the chat toolbar is visible.
     */
    public static void setChatToolbarVisible(boolean isVisible)
    {
        isChatToolbarVisible = isVisible;
        configService.setProperty("gui.chat.ChatWindow.showToolbar", Boolean.toString(isChatToolbarVisible));
    }

    /**
     * Updates the "isChatSimpleThemeEnabled" property through the <tt>ConfigurationService</tt>.
     *
     * @param isEnabled indicates if the chat simple theme is enabled
     */
    public static void setChatSimpleThemeEnabled(boolean isEnabled)
    {
        isChatSimpleThemeEnabled = isEnabled;
        configService.setProperty(CHAT_SIMPLE_THEME_ENABLED_PROP, Boolean.toString(isChatSimpleThemeEnabled));
    }

    /**
     * Updates the "isChatStyleBarVisible" property through the <tt>ConfigurationService</tt>.
     *
     * @param isVisible indicates if the chat styleBar is visible.
     */
    public static void setChatStyleBarVisible(boolean isVisible)
    {
        isChatStyleBarVisible = isVisible;
        configService.setProperty("gui.chat.ChatWindow.showStylebar", Boolean.toString(isChatStyleBarVisible));
    }

    /**
     * Updates the pChatWriteAreaSize property through the <tt>ConfigurationService</tt>.
     *
     * @param size the new size to set
     */
    public static void setChatWriteAreaSize(int size)
    {
        chatWriteAreaSize = size;
        configService.setProperty(pChatWriteAreaSize, Integer.toString(chatWriteAreaSize));
    }

    /**
     * Updates the "SEND_FILE_LAST_DIR" property through the <tt>ConfigurationService</tt>.
     *
     * @param lastDir last download directory
     */
    public static void setSendFileLastDir(String lastDir)
    {
        sendFileLastDir = lastDir;
        configService.setProperty("gui.chat.filetransfer.SEND_FILE_LAST_DIR", lastDir);
    }

    /**
     * Sets the call conference provider used for the last conference call.
     *
     * @param protocolProvider the call conference provider used for the last conference call
     */
    public static void setLastCallConferenceProvider(ProtocolProviderService protocolProvider)
    {
        lastCallConferenceProvider = protocolProvider;
        configService.setProperty("gui.call.lastCallConferenceProvider",
                protocolProvider.getAccountID().getAccountUniqueID());
    }

    /**
     * Sets the default font family.
     *
     * @param fontFamily the default font family name
     */
    public static void setChatDefaultFontFamily(String fontFamily)
    {
        defaultFontFamily = fontFamily;
        configService.setProperty("gui.chat.DEFAULT_FONT_FAMILY", fontFamily);
    }

    /**
     * Sets the default font size.
     *
     * @param fontSize the default font size
     */
    public static void setChatDefaultFontSize(int fontSize)
    {
        defaultFontSize = String.valueOf(fontSize);
        configService.setProperty("gui.chat.DEFAULT_FONT_SIZE", fontSize);
    }

    /**
     * Sets the default isBold property.
     *
     * @param isBold indicates if the default chat font is bold
     */
    public static void setChatFontIsBold(boolean isBold)
    {
        isDefaultFontBold = isBold;
        configService.setProperty("gui.chat.DEFAULT_FONT_BOLD", isBold);
    }

    /**
     * Sets the default isItalic property.
     *
     * @param isItalic indicates if the default chat font is italic
     */
    public static void setChatFontIsItalic(boolean isItalic)
    {
        isDefaultFontItalic = isItalic;
        configService.setProperty("gui.chat.DEFAULT_FONT_ITALIC", isItalic);
    }

    /**
     * Sets the default isUnderline property.
     *
     * @param isUnderline indicates if the default chat font is underline
     */
    public static void setChatFontIsUnderline(boolean isUnderline)
    {
        isDefaultFontUnderline = isUnderline;
        configService.setProperty("gui.chat.DEFAULT_FONT_UNDERLINE", isUnderline);
    }

    /**
     * Sets the default font color.
     *
     * @param fontColor the default font color
     */
    public static void setChatDefaultFontColor(Color fontColor)
    {
        defaultFontColor = fontColor.getRGB();
        configService.setProperty("gui.chat.DEFAULT_FONT_COLOR", defaultFontColor);
    }

    /**
     * Initialize aTalk app Theme;default to Theme.DARK if not defined
     */
    public static void initAppTheme()
    {
        Theme theme;
        int themeValue = configService.getInt(SettingsActivity.P_KEY_THEME, Theme.DARK.ordinal());
        if (themeValue == Theme.DARK.ordinal() || themeValue == android.R.style.Theme) {
            theme = Theme.DARK;
        }
        else {
            theme = Theme.LIGHT;
        }
        ThemeHelper.setAppTheme(theme);
    }

    /**
     * Updates the value of a contact option property through the <tt>ConfigurationService</tt>.
     * The property-value pair is stored a JSONObject element in contact options
     *
     * @param contactJid the identifier/BareJid of the contact table to update
     * @param property the property name in the contact options
     * @param value the value of the contact options property if null, property will be removed
     */
    public static void updateContactProperty(Jid contactJid, String property, String value)
    {
        JSONObject options = getContactOptions(contactJid);
        try {
            if (value == null)
                options.remove(property);
            else
                options.put(property, value);
        } catch (JSONException e) {
            Timber.w("Contact property update failed: %s: %s", contactJid, property);
        }

        String[] args = {contactJid.toString()};
        contentValues.clear();
        contentValues.put(Contact.OPTIONS, options.toString());

        mDB.update(Contact.TABLE_NAME, contentValues, Contact.CONTACT_JID + "=?", args);
    }

    /**
     * Returns the contact options, saved via the <tt>ConfigurationService</tt>.
     *
     * @param contactJid the identifier/BareJid of the contact table to retrieve
     * @param property the property name in the contact options
     * @return the value of the contact options property, saved via the <tt>ConfigurationService</tt>.
     */
    public static String getContactProperty(Jid contactJid, String property)
    {
        JSONObject options = getContactOptions(contactJid);
        try {
            return options.getString(property);
        } catch (JSONException e) {
            // Timber.w("ChatRoom property not found for: " + chatRoomId + ": " + property);
        }
        return null;
    }

    /**
     * Returns the options saved in <tt>ConfigurationService</tt> associated with the <tt>Contact</tt>.
     *
     * @param contactJid the identifier/BareJid of the contact table to update
     * @return the contact options saved in <tt>ConfigurationService</tt>.
     */
    private static JSONObject getContactOptions(Jid contactJid)
    {
        // mDB is null when access during restoring process
        if (mDB == null)
            mDB = DatabaseBackend.getWritableDB();

        String[] columns = {Contact.OPTIONS};
        String[] args = {contactJid.asBareJid().toString()};

        Cursor cursor = mDB.query(Contact.TABLE_NAME, columns, Contact.CONTACT_JID + "=?", args,
                null, null, null);

        JSONObject options = new JSONObject();
        while (cursor.moveToNext()) {
            String value = cursor.getString(0);
            try {
                options = new JSONObject(value == null ? "" : value);
            } catch (JSONException e) {
                options = new JSONObject();
            }
        }
        cursor.close();
        return options;
    }

    /**
     * Saves a chat room through the <tt>ConfigurationService</tt>.
     *
     * @param protocolProvider the protocol provider to which the chat room belongs
     * @param oldChatRoomId the old identifier of the chat room
     * @param newChatRoomId the new identifier of the chat room  = newChatRoomName
     */
    public static void saveChatRoom(ProtocolProviderService protocolProvider, String oldChatRoomId, String newChatRoomId)
    {
        String[] columns = {ChatSession.SESSION_UUID};
        String accountUid = protocolProvider.getAccountID().getAccountUniqueID();
        String[] args = {accountUid, oldChatRoomId};

        Cursor cursor = mDB.query(ChatSession.TABLE_NAME, columns, ChatSession.ACCOUNT_UID
                + "=? AND " + ChatSession.ENTITY_JID + "=?", args, null, null, null);

        contentValues.clear();
        if (cursor.getCount() > 0) {
            if (!oldChatRoomId.equals(newChatRoomId)) {
                cursor.moveToNext();
                args = new String[]{cursor.getString(0)};
                contentValues.put(ChatSession.ENTITY_JID, newChatRoomId);
                mDB.update(ChatSession.TABLE_NAME, contentValues, ChatSession.SESSION_UUID + "=?", args);
            }
        }
        else {
            String timeStamp = String.valueOf(System.currentTimeMillis());
            String sessionUuid = timeStamp + Math.abs(timeStamp.hashCode());
            String accountUuid = protocolProvider.getAccountID().getAccountUuid();
            String attributes = new JSONObject().toString();

            contentValues.put(ChatSession.SESSION_UUID, sessionUuid);
            contentValues.put(ChatSession.ACCOUNT_UUID, accountUuid);
            contentValues.put(ChatSession.ACCOUNT_UID, accountUid);
            contentValues.put(ChatSession.ENTITY_JID, newChatRoomId);
            contentValues.put(ChatSession.CREATED, timeStamp);
            contentValues.put(ChatSession.STATUS, ChatFragment.MSGTYPE_OMEMO);
            contentValues.put(ChatSession.MODE, ChatSession.MODE_MULTI);
            contentValues.put(ChatSession.ATTRIBUTES, attributes);

            mDB.insert(ChatSession.TABLE_NAME, null, contentValues);
        }
        cursor.close();
    }

    /**
     * Removes a chatRoom through the <tt>ConfigurationService</tt>.
     *
     * @param protocolProvider the protocol provider to which the chat room belongs
     * @param chatRoomId the identifier of the chat room to remove
     */
    public static void removeChatRoom(ProtocolProviderService protocolProvider, String chatRoomId)
    {
        String accountUid = protocolProvider.getAccountID().getAccountUniqueID();
        String[] args = {accountUid, chatRoomId};

        mDB.delete(ChatSession.TABLE_NAME, ChatSession.ACCOUNT_UID + "=? AND "
                + ChatSession.ENTITY_JID + "=?", args);
    }

    /**
     * Updates the status of the chat room through the <tt>ConfigurationService</tt>.
     *
     * @param protocolProvider the protocol provider to which the chat room belongs
     * @param chatRoomId the identifier of the chat room to update
     * @param chatRoomStatus the new status of the chat room
     */
    public static void updateChatRoomStatus(ProtocolProviderService protocolProvider,
            String chatRoomId, String chatRoomStatus)
    {
        updateChatRoomProperty(protocolProvider, chatRoomId, ChatRoom.CHATROOM_LAST_STATUS, chatRoomStatus);
    }

    /**
     * Returns the last chat room status, saved through the <tt>ConfigurationService</tt>.
     *
     * @param protocolProvider the protocol provider, to which the chat room belongs
     * @param chatRoomId the identifier of the chat room
     * @return the last chat room status, saved through the <tt>ConfigurationService</tt>.
     */
    public static String getChatRoomStatus(ProtocolProviderService protocolProvider, String chatRoomId)
    {
        return getChatRoomProperty(protocolProvider, chatRoomId, ChatRoom.CHATROOM_LAST_STATUS);
    }

    /**
     * Updates the value of a chat room property through the <tt>ConfigurationService</tt>.
     *
     * @param protocolProvider the protocol provider to which the chat room belongs
     * @param chatRoomId the identifier of the chat room to update
     * @param property the name of the property of the chat room
     * @param value the value of the property if null, property will be removed
     */
    public static void updateChatRoomProperty(ProtocolProviderService protocolProvider,
            String chatRoomId, String property, String value)
    {
        JSONObject attributes = getChatRoomAttributes(protocolProvider, chatRoomId);
        try {
            if (value == null)
                attributes.remove(property);
            else
                attributes.put(property, value);
        } catch (JSONException e) {
            Timber.w("ChatRoom property update failed: %s: %s", chatRoomId, property);
        }

        String accountUid = protocolProvider.getAccountID().getAccountUniqueID();
        String[] args = {accountUid, chatRoomId};
        contentValues.clear();
        contentValues.put(ChatSession.ATTRIBUTES, attributes.toString());

        mDB.update(ChatSession.TABLE_NAME, contentValues, ChatSession.ACCOUNT_UID
                + "=? AND " + ChatSession.ENTITY_JID + "=?", args);
    }

    /**
     * Returns the chat room property, saved through the <tt>ConfigurationService</tt>.
     *
     * @param protocolProvider the protocol provider, to which the chat room belongs
     * @param chatRoomId the identifier of the chat room
     * @param property the property name, saved through the <tt>ConfigurationService</tt>.
     * @return the value of the property, saved through the <tt>ConfigurationService</tt>.
     */
    public static String getChatRoomProperty(ProtocolProviderService protocolProvider,
            String chatRoomId, String property)
    {
        JSONObject attributes = getChatRoomAttributes(protocolProvider, chatRoomId);
        try {
            return attributes.getString(property);
        } catch (JSONException e) {
            // Timber.w("ChatRoom property not found for: " + chatRoomId + ": " + property);
        }
        return null;
    }

    /**
     * Returns the chat room prefix saved in <tt>ConfigurationService</tt> associated with the
     * <tt>accountID</tt> and <tt>chatRoomID</tt>.
     *
     * @param protocolProvider the protocol provider, to which the chat room belongs
     * @param chatRoomId the chat room id
     * @return the chat room prefix saved in <tt>ConfigurationService</tt>.
     */
    private static JSONObject getChatRoomAttributes(ProtocolProviderService protocolProvider, String chatRoomId)
    {
        //mDB is null when access during restoring process
        if (mDB == null)
            mDB = DatabaseBackend.getWritableDB();

        String[] columns = {ChatSession.ATTRIBUTES};
        String accountUid = protocolProvider.getAccountID().getAccountUniqueID();
        String[] args = {accountUid, chatRoomId};

        Cursor cursor = mDB.query(ChatSession.TABLE_NAME, columns, ChatSession.ACCOUNT_UID
                + "=? AND " + ChatSession.ENTITY_JID + "=?", args, null, null, null);

        JSONObject attributes = new JSONObject();
        while (cursor.moveToNext()) {
            String value = cursor.getString(0);
            try {
                attributes = new JSONObject(value == null ? "" : value);
            } catch (JSONException e) {
                attributes = new JSONObject();
            }
        }
        cursor.close();
        return attributes;
    }

    /**
     * Returns the chatRoom prefix saved in <tt>ConfigurationService</tt> associated with the
     * <tt>accountID</tt> and <tt>chatRoomID</tt>.
     * <p>
     * chatRoomPrefix is used as property to store encrypted password in DB. So need to start
     * with AccountUuid for it to handle properly and to auto-clean when the account is removed.
     *
     * @param protocolProvider the protocol provider, to which the chat room belongs
     * @param chatRoomID the chat room id (cmeng: can contain account serviceName e.g. example.org??)
     * @return the chatRoom sessionUid saved in <tt>ConfigurationService</tt>.
     */
    public static String getChatRoomPrefix(ProtocolProviderService protocolProvider, String chatRoomID)
    {
        AccountID accountID = protocolProvider.getAccountID();
        MessageHistoryService mhs = AndroidGUIActivator.getMessageHistoryService();
        String sessionUuid = mhs.getSessionUuidByJid(accountID, chatRoomID);

        if (StringUtils.isEmpty(sessionUuid)) {
            Timber.w("Failed to get MUC prefix for chatRoom: %s", chatRoomID);
            return null;
        }

        return accountID.getAccountUuid() + ".muc_" + sessionUuid;
    }

    /**
     * Stores the last group <tt>status</tt> for the given <tt>groupID</tt>.
     *
     * @param groupID the identifier of the group (prefixed with group)
     * @param isCollapsed indicates if the group is collapsed or expanded
     */
    public static void setContactListGroupCollapsed(String groupID, boolean isCollapsed)
    {
        String prefix = "gui.contactlist.groups";
        List<String> groups = configService.getPropertyNamesByPrefix(prefix, true);

        boolean isExistingGroup = false;
        for (String groupRootPropName : groups) {
            String storedID = configService.getString(groupRootPropName);

            if (storedID.equals(groupID)) {
                configService.setProperty(groupRootPropName + ".isClosed", Boolean.toString(isCollapsed));
                isExistingGroup = true;
                break;
            }
        }
        if (!isExistingGroup) {
            String groupNodeName = "group" + Long.toString(System.currentTimeMillis());
            String groupPackage = prefix + "." + groupNodeName;

            configService.setProperty(groupPackage, groupID);
            configService.setProperty(groupPackage + ".isClosed", Boolean.toString(isCollapsed));
        }
    }

    /**
     * Returns <tt>true</tt> if the group given by <tt>groupID</tt> is collapsed or <tt>false</tt> otherwise.
     *
     * @param groupID the identifier of the group
     * @return <tt>true</tt> if the group given by <tt>groupID</tt> is collapsed or <tt>false</tt> otherwise
     */
    public static boolean isContactListGroupCollapsed(String groupID)
    {
        String prefix = "gui.contactlist.groups";

        List<String> groups = configService.getPropertyNamesByPrefix(prefix, true);
        for (String groupRootPropName : groups) {
            String storedID = configService.getString(groupRootPropName);

            if (storedID.equals(groupID)) {
                String status = (String) configService.getProperty(groupRootPropName + ".isClosed");
                return Boolean.parseBoolean(status);
            }
        }
        return false;
    }

    /**
     * Indicates if the account configuration is disabled.
     *
     * @return <tt>true</tt> if the account manual configuration and creation is disabled, otherwise return <tt>false</tt>
     */
    public static boolean isShowAccountConfig()
    {
        final String SHOW_ACCOUNT_CONFIG_PROP = "gui.configforms.SHOW_ACCOUNT_CONFIG";
        boolean defaultValue = !Boolean.parseBoolean(UtilActivator.getResources()
                .getSettingsString("gui.account.ACCOUNT_CONFIG_DISABLED"));
        return configService.getBoolean(SHOW_ACCOUNT_CONFIG_PROP, defaultValue);
    }

    /**
     * Listens for changes of the properties.
     */
    private static class ConfigurationChangeListener implements PropertyChangeListener
    {
        public void propertyChange(PropertyChangeEvent evt)
        {
            // All properties we're interested in here are Strings.
            if (!(evt.getNewValue() instanceof String))
                return;

            String newValue = (String) evt.getNewValue();

            if (evt.getPropertyName().equals("gui.addcontact.lastContactParent")) {
                lastContactParent = newValue;
            }
            else if (evt.getPropertyName().equals(pAutoPopupNewMessage)) {
                autoPopupNewMessage = "yes".equalsIgnoreCase(newValue);
            }
            else if (evt.getPropertyName().equals(pMsgCommand)) {
                sendMessageCommand = newValue;
            }
            else if (evt.getPropertyName().equals("gui.showCallPanel")) {
                isCallPanelShown = Boolean.parseBoolean(newValue);
            }
            else if (evt.getPropertyName().equals(pAutoStart)) {
                isAutoStartOnBoot = Boolean.parseBoolean(newValue);
            }
            else if (evt.getPropertyName().equals("gui.showOffline")) {
                isShowOffline = Boolean.parseBoolean(newValue);
            }
            else if (evt.getPropertyName().equals("systray.showApplication")) {
                isApplicationVisible = Boolean.parseBoolean(newValue);
            }
            else if (evt.getPropertyName().equals("gui.quitWarningShown")) {
                isQuitWarningShown = Boolean.parseBoolean(newValue);
            }
            else if (evt.getPropertyName().equals(pMessageDeliveryReceipt)) {
                isSendMessageDeliveryReceipt = Boolean.parseBoolean(newValue);
            }
            else if (evt.getPropertyName().equals(pTypingNotification)) {
                isSendChatStateNotifications = Boolean.parseBoolean(newValue);
            }
            else if (evt.getPropertyName().equals(pPresenceSubscribeAuto)) {
                isPresenceSubscribeAuto = Boolean.parseBoolean(newValue);
            }
            else if (evt.getPropertyName().equals("gui.isMoveContactConfirmationRequested")) {
                isMoveContactConfirmationRequested = Boolean.parseBoolean(newValue);
            }
            else if (evt.getPropertyName().equals(pMultiChatWindowEnabled)) {
                isMultiChatWindowEnabled = Boolean.parseBoolean(newValue);
            }
            else if (evt.getPropertyName()
                    .equals("gui.IS_PRIVATE_CHAT_IN_CHATROOM_DISABLED")) {
                isPrivateMessagingInChatRoomDisabled = Boolean.parseBoolean(newValue);
            }
            else if (evt.getPropertyName().equals(pLeaveChatRoomOnWindowClose)) {
                isLeaveChatRoomOnWindowCloseEnabled = Boolean.parseBoolean(newValue);
            }
            else if (evt.getPropertyName().equals(pMessageHistoryShown)) {
                isHistoryShown = Boolean.parseBoolean(newValue);
            }
            else if (evt.getPropertyName().equals(pChatHistorySize)) {
                chatHistorySize = Integer.parseInt(newValue);
            }
            else if (evt.getPropertyName().equals(pChatWriteAreaSize)) {
                chatWriteAreaSize = Integer.parseInt(newValue);
            }
            else if (evt.getPropertyName().equals(pTransparentWindowEnabled)) {
                isTransparentWindowEnabled = Boolean.parseBoolean(newValue);
            }
            else if (evt.getPropertyName().equals(pWindowTransparency)) {
                windowTransparency = Integer.parseInt(newValue);
            }
            else if (evt.getPropertyName().equals("gui.chat.ChatWindow.showStylebar")) {
                isChatStyleBarVisible = Boolean.parseBoolean(newValue);
            }
            else if (evt.getPropertyName().equals("gui.chat.ChatWindow.showToolbar")) {
                isChatToolbarVisible = Boolean.parseBoolean(newValue);
            }
            else if (evt.getPropertyName().equals("gui.call.lastCallConferenceProvider")) {
                lastCallConferenceProvider = findProviderFromAccountId(newValue);
            }
            else if (evt.getPropertyName().equals(pShowStatusChangedInChat)) {
                showStatusChangedInChat = Boolean.parseBoolean(newValue);
            }
        }
    }

    /**
     * Returns the package name under which we would store information for the given factory.
     *
     * @param factory the <tt>ProtocolProviderFactory</tt>, which package name we're looking for
     * @return the package name under which we would store information for the given factory
     */
    public static String getFactoryImplPackageName(ProtocolProviderFactory factory)
    {
        String className = factory.getClass().getName();
        return className.substring(0, className.lastIndexOf('.'));
    }

    /**
     * Returns the configured client port.
     *
     * @return the client port
     */
    public static int getClientPort()
    {
        return configService.getInt(ProtocolProviderFactory.PREFERRED_CLEAR_PORT_PROPERTY_NAME, 5060);
    }

    /**
     * Sets the client port.
     *
     * @param port the port to set
     */
    public static void setClientPort(int port)
    {
        configService.setProperty(ProtocolProviderFactory.PREFERRED_CLEAR_PORT_PROPERTY_NAME, port);
    }

    /**
     * Returns the client secure port.
     *
     * @return the client secure port
     */
    public static int getClientSecurePort()
    {
        return configService.getInt(ProtocolProviderFactory.PREFERRED_SECURE_PORT_PROPERTY_NAME, 5061);
    }

    /**
     * Sets the client secure port.
     *
     * @param port the port to set
     */
    public static void setClientSecurePort(int port)
    {
        configService.setProperty(ProtocolProviderFactory.PREFERRED_SECURE_PORT_PROPERTY_NAME, port);
    }

    /**
     * Returns the list of enabled SSL protocols.
     *
     * @return the list of enabled SSL protocols
     */
    public static String[] getEnabledSslProtocols()
    {
        String enabledSslProtocols = configService.getString("gov.nist.javax.sip.TLS_CLIENT_PROTOCOLS");
        if (StringUtils.isBlank(enabledSslProtocols)) {
            SSLSocket temp;
            try {
                temp = (SSLSocket) SSLSocketFactory.getDefault().createSocket();
                return temp.getEnabledProtocols();
            } catch (IOException e) {
                Timber.e(e);
                return getAvailableSslProtocols();
            }
        }
        return enabledSslProtocols.split("(,)|(,\\s)");
    }

    /**
     * Returns the list of available SSL protocols.
     *
     * @return the list of available SSL protocols
     */
    public static String[] getAvailableSslProtocols()
    {
        SSLSocket temp;
        try {
            temp = (SSLSocket) SSLSocketFactory.getDefault().createSocket();
            return temp.getSupportedProtocols();
        } catch (IOException e) {
            Timber.e(e);
            return new String[]{};
        }
    }

    /**
     * Sets the enables SSL protocols list.
     *
     * @param enabledProtocols the list of enabled SSL protocols to set
     */
    public static void setEnabledSslProtocols(String[] enabledProtocols)
    {
        if (enabledProtocols == null || enabledProtocols.length == 0)
            configService.removeProperty("gov.nist.javax.sip.TLS_CLIENT_PROTOCOLS");
        else {
            String protocols = Arrays.toString(enabledProtocols);
            configService.setProperty("gov.nist.javax.sip.TLS_CLIENT_PROTOCOLS",
                    protocols.substring(1, protocols.length() - 1));
        }
    }

    /**
     * Returns <tt>true</tt> if the account associated with <tt>protocolProvider</tt> has at least
     * one video format enabled in it's configuration, <tt>false</tt> otherwise.
     *
     * @return <tt>true</tt> if the account associated with <tt>protocolProvider</tt> has at least
     * one video format enabled in it's configuration, <tt>false</tt> otherwise.
     */
    public static boolean hasEnabledVideoFormat(ProtocolProviderService protocolProvider)
    {
        Map<String, String> accountProperties = protocolProvider.getAccountID().getAccountProperties();

        EncodingConfiguration encodingConfiguration;
        String overrideEncodings = accountProperties.get(ProtocolProviderFactory.OVERRIDE_ENCODINGS);
        if (Boolean.parseBoolean(overrideEncodings)) {
            encodingConfiguration = UtilActivator.getMediaService().createEmptyEncodingConfiguration();
            encodingConfiguration.loadProperties(accountProperties, ProtocolProviderFactory.ENCODING_PROP_PREFIX);
        }
        else {
            encodingConfiguration = UtilActivator.getMediaService().getCurrentEncodingConfiguration();
        }
        return encodingConfiguration.hasEnabledFormat(MediaType.VIDEO);
    }

    /**
     * Update EntityCaps when <DeliveryReceipt/> feature is enabled or disable
     *
     * @param isDeliveryReceiptEnable indicates whether Message Delivery Receipt feature is enable or disable
     */
    private static void updateDeliveryReceiptFeature(boolean isDeliveryReceiptEnable)
    {
        Collection<ProtocolProviderService> ppServices = AccountUtils.getRegisteredProviders();

        for (ProtocolProviderService pps : ppServices) {
            XMPPConnection connection = pps.getConnection();
            if (connection != null) {
                /* XEP-0184: Message Delivery Receipts - global option */
                ServiceDiscoveryManager discoveryManager = ServiceDiscoveryManager.getInstanceFor(connection);
                DeliveryReceiptManager deliveryReceiptManager = DeliveryReceiptManager.getInstanceFor(connection);

                if (isDeliveryReceiptEnable) {
                    discoveryManager.addFeature(DeliveryReceipt.NAMESPACE);
                    deliveryReceiptManager.setAutoReceiptMode(DeliveryReceiptManager.AutoReceiptMode.ifIsSubscribed);
                }
                else {
                    discoveryManager.removeFeature(DeliveryReceipt.NAMESPACE);
                    deliveryReceiptManager.setAutoReceiptMode(DeliveryReceiptManager.AutoReceiptMode.disabled);
                }
            }
        }
    }

    /**
     * Update EntityCaps when <ChatState/> feature is enabled or disable
     *
     * @param isChatStateEnable indicates whether ChatState feature is enable or disable
     */
    private static void updateChatStateCapsFeature(boolean isChatStateEnable)
    {
        Collection<ProtocolProviderService> ppServices = AccountUtils.getRegisteredProviders();
        for (ProtocolProviderService pps : ppServices) {
            XMPPConnection connection = pps.getConnection();
            if (connection != null) {
                ServiceDiscoveryManager discoveryManager = ServiceDiscoveryManager.getInstanceFor(connection);

                OperationSetContactCapabilitiesJabberImpl.setOperationSetChatStateFeatures(isChatStateEnable);
                // cmeng: not required for both?
                // MetaContactChatTransport.setChatStateSupport(isChatStateEnable);
                // ConferenceChatTransport.setChatStateSupport(isChatStateEnable);

                if (isChatStateEnable) {
                    discoveryManager.addFeature(ChatStateManager.NAMESPACE);
                }
                else {
                    discoveryManager.removeFeature(ChatStateManager.NAMESPACE);
                }
            }
        }
    }

    // ====================== Function use when aTalk app is not fully initialize e.g. init UI language =======================
    // Note: aTalkApp get initialize much earlier than ConfigurationUtils

    private static SQLiteConfigurationStore sqlStore = new SQLiteConfigurationStore(aTalkApp.getInstance());

    /**
     * Direct fetching of the property from SQLiteConfigurationStore on system startup
     *
     * @param propertyName of the value to retrieve
     * @param defValue default value to use
     * @return the retrieve value
     */
    public static String getProperty(String propertyName, String defValue)
    {
        Object objValue = sqlStore.getProperty(propertyName);
        return (objValue == null)
                ? defValue
                : objValue.toString();
    }
}