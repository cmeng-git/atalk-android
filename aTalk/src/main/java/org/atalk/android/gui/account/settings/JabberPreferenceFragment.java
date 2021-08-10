/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.android.gui.account.settings;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.*;
import android.text.TextUtils;

import net.java.sip.communicator.impl.msghistory.MessageHistoryActivator;
import net.java.sip.communicator.impl.msghistory.MessageHistoryServiceImpl;
import net.java.sip.communicator.impl.protocol.jabber.ProtocolProviderServiceJabberImpl;
import net.java.sip.communicator.plugin.jabberaccregwizz.AccountRegistrationImpl;
import net.java.sip.communicator.plugin.jabberaccregwizz.JabberAccountRegistrationActivator;
import net.java.sip.communicator.service.certificate.CertificateConfigEntry;
import net.java.sip.communicator.service.certificate.CertificateService;
import net.java.sip.communicator.service.protocol.*;
import net.java.sip.communicator.service.protocol.jabber.JabberAccountRegistration;

import org.atalk.android.R;
import org.atalk.android.aTalkApp;
import org.atalk.android.gui.dialogs.DialogActivity;
import org.atalk.android.gui.settings.util.SummaryMapper;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

import timber.log.Timber;

/**
 * Preferences fragment for Jabber settings. It maps Jabber specific properties to the
 * {@link Preference}s. Reads from and stores them inside {@link JabberAccountRegistration}.
 *
 * This is an instance of the accountID properties from Account Setting... preference editing. These changes
 * will be merged with the original mAccountProperties and saved to database in doCommitChanges()
 *
 * @author Pawel Domas
 * @author Eng Chong Meng
 * @author MilanKral
 */
public class JabberPreferenceFragment extends AccountPreferenceFragment
{
    /**
     * The key identifying edit jingle nodes request
     */
    private static final int EDIT_JINGLE_NODES = 3;

    /**
     * The key identifying edit STUN servers list request
     */
    private static final int EDIT_STUN_TURN = 4;

    // Account Settings
    private static final String P_KEY_USER_ID = aTalkApp.getResString(R.string.pref_key_user_id);
    private static final String P_KEY_PASSWORD = aTalkApp.getResString(R.string.pref_key_password);
    private static final String P_KEY_STORE_PASSWORD = aTalkApp.getResString(R.string.pref_key_store_password);
    static private final String P_KEY_DNSSEC_MODE = aTalkApp.getResString(R.string.pref_key_dnssec_mode);

    // Client TLS certificate
    private static final String P_KEY_TLS_CERT_ID = aTalkApp.getResString(R.string.pref_key_client_tls_cert);

    // Account General
    private static final String P_KEY_GMAIL_NOTIFICATIONS = aTalkApp.getResString(R.string.pref_key_gmail_notifications);
    private static final String P_KEY_GOOGLE_CONTACTS_ENABLED
            = aTalkApp.getResString(R.string.pref_key_google_contact_enabled);
    private static final String P_KEY_DTMF_METHOD = aTalkApp.getResString(R.string.pref_key_dtmf_method);

    // Jabber Resource
    private static final String P_KEY_AUTO_GEN_RESOURCE = aTalkApp.getResString(R.string.pref_key_auto_gen_resource);
    private static final String P_KEY_RESOURCE_NAME = aTalkApp.getResString(R.string.pref_key_resource_name);
    private static final String P_KEY_RESOURCE_PRIORITY = aTalkApp.getResString(R.string.pref_key_resource_priority);

    // Server Options
    private static final String P_KEY_IS_KEEP_ALIVE_ENABLE
            = aTalkApp.getResString(R.string.pref_key_is_keep_alive_enable);
    public static final String P_KEY_PING_INTERVAL = aTalkApp.getResString(R.string.pref_key_ping_interval);
    private static final String P_KEY_IS_PING_AUTO_TUNE_ENABLE
            = aTalkApp.getResString(R.string.pref_key_ping_auto_tune_enable);
    private static final String P_KEY_IS_SERVER_OVERRIDDEN
            = aTalkApp.getResString(R.string.pref_key_is_server_overridden);
    public static final String P_KEY_SERVER_ADDRESS = aTalkApp.getResString(R.string.pref_key_server_address);
    public static final String P_KEY_SERVER_PORT = aTalkApp.getResString(R.string.pref_key_server_port);
    private static final String P_KEY_ALLOW_NON_SECURE_CONN = aTalkApp.getResString(R.string.pref_key_allow_non_secure_conn);
    private static final String P_KEY_MINIMUM_TLS_VERSION = aTalkApp.getResString(R.string.pref_key_minimum_TLS_version);

    // Proxy
    private static final String P_KEY_PROXY_CONFIG = aTalkApp.getResString(R.string.pref_key_bosh_configuration);

    // ICE (General)
    private static final String P_KEY_ICE_ENABLED = aTalkApp.getResString(R.string.pref_key_ice_enabled);
    private static final String P_KEY_UPNP_ENABLED = aTalkApp.getResString(R.string.pref_key_upnp_enabled);
    private static final String P_KEY_AUTO_DISCOVER_STUN = aTalkApp.getResString(R.string.pref_key_auto_discover_stun);
    private static final String P_KEY_STUN_TURN_SERVERS = aTalkApp.getResString(R.string.pref_key_stun_turn_servers);

    // Jingle Nodes
    private static final String P_KEY_USE_JINGLE_NODES = aTalkApp.getResString(R.string.pref_key_use_jingle_nodes);
    private static final String P_KEY_AUTO_RELAY_DISCOVERY
            = aTalkApp.getResString(R.string.pref_key_auto_relay_dicovery);
    private static final String P_KEY_JINGLE_NODES_LIST = aTalkApp.getResString(R.string.pref_key_jingle_node_list);

    // Telephony
    private static final String P_KEY_CALLING_DISABLED = aTalkApp.getResString(R.string.pref_key_calling_disabled);
    private static final String P_KEY_OVERRIDE_PHONE_SUFFIX = aTalkApp.getResString(R.string.pref_key_override_phone_suffix);
    private static final String P_KEY_TEL_BYPASS_GTALK_CAPS
            = aTalkApp.getResString(R.string.pref_key_tele_bypass_gtalk_caps);

    /*
     * A new instance of AccountID and is not the same as accountID.
     * Defined as static, otherwise it may get clear onActivityResult - on some android devices
     */
    private static JabberAccountRegistration jbrReg;

    /**
     * Current user userName which is being edited.
     */
    private String userNameEdited;

    /**
     * user last entered userName to check for anymore new changes in userName
     */
    private String userNameLastEdited;

    /**
     * Creates a new instance of <tt>JabberPreferenceFragment</tt>
     */
    public JabberPreferenceFragment()
    {
        super(R.xml.acc_jabber_preferences);
    }

    /**
     * Returns jabber registration wizard.
     *
     * @return jabber registration wizard.
     */
    private AccountRegistrationImpl getJbrWizard()
    {
        return (AccountRegistrationImpl) getWizard();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected EncodingsRegistrationUtil getEncodingsRegistration()
    {
        return jbrReg.getEncodingsRegistration();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected SecurityAccountRegistration getSecurityRegistration()
    {
        return jbrReg.getSecurityRegistration();
    }

    /**
     * {@inheritDoc}
     */
    protected void onInitPreferences()
    {
        AccountRegistrationImpl wizard = getJbrWizard();
        jbrReg = wizard.getAccountRegistration();

        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(getActivity());
        SharedPreferences.Editor editor = preferences.edit();

        // User name and password
        userNameEdited = jbrReg.getUserID();
        userNameLastEdited = userNameEdited;

        editor.putString(P_KEY_USER_ID, userNameEdited);
        editor.putString(P_KEY_PASSWORD, jbrReg.getPassword());
        editor.putBoolean(P_KEY_STORE_PASSWORD, jbrReg.isRememberPassword());
        editor.putString(P_KEY_DNSSEC_MODE, jbrReg.getDnssMode());
        editor.putString(P_KEY_TLS_CERT_ID, jbrReg.getTlsClientCertificate());

        // Connection
        editor.putBoolean(P_KEY_GMAIL_NOTIFICATIONS, jbrReg.isGmailNotificationEnabled());
        editor.putBoolean(P_KEY_GOOGLE_CONTACTS_ENABLED, jbrReg.isGoogleContactsEnabled());
        editor.putString(P_KEY_MINIMUM_TLS_VERSION, jbrReg.getMinimumTLSversion());
        editor.putBoolean(P_KEY_ALLOW_NON_SECURE_CONN, jbrReg.isAllowNonSecure());
        editor.putString(P_KEY_DTMF_METHOD, jbrReg.getDTMFMethod());

        // Keep alive options
        editor.putBoolean(P_KEY_IS_KEEP_ALIVE_ENABLE, jbrReg.isKeepAliveEnable());
        editor.putString(P_KEY_PING_INTERVAL, "" + jbrReg.getPingInterval());
        editor.putBoolean(P_KEY_IS_PING_AUTO_TUNE_ENABLE, jbrReg.isPingAutoTuneEnable());

        // Server options
        editor.putBoolean(P_KEY_IS_SERVER_OVERRIDDEN, jbrReg.isServerOverridden());
        editor.putString(P_KEY_SERVER_ADDRESS, jbrReg.getServerAddress());
        editor.putString(P_KEY_SERVER_PORT, "" + jbrReg.getServerPort());

        // Resource
        editor.putBoolean(P_KEY_AUTO_GEN_RESOURCE, jbrReg.isResourceAutoGenerated());
        editor.putString(P_KEY_RESOURCE_NAME, jbrReg.getResource());
        editor.putString(P_KEY_RESOURCE_PRIORITY, "" + jbrReg.getPriority());

        // ICE options
        editor.putBoolean(P_KEY_ICE_ENABLED, jbrReg.isUseIce());
        editor.putBoolean(P_KEY_UPNP_ENABLED, jbrReg.isUseUPNP());
        editor.putBoolean(P_KEY_AUTO_DISCOVER_STUN, jbrReg.isAutoDiscoverStun());

        // Jingle Nodes
        editor.putBoolean(P_KEY_USE_JINGLE_NODES, jbrReg.isUseJingleNodes());
        editor.putBoolean(P_KEY_AUTO_RELAY_DISCOVERY, jbrReg.isAutoDiscoverJingleNodes());

        // Telephony
        editor.putBoolean(P_KEY_CALLING_DISABLED, jbrReg.isJingleDisabled());
        editor.putString(P_KEY_OVERRIDE_PHONE_SUFFIX, jbrReg.getOverridePhoneSuffix());
        editor.putString(P_KEY_TEL_BYPASS_GTALK_CAPS, jbrReg.getTelephonyDomainBypassCaps());

        editor.apply();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void onPreferencesCreated()
    {
        super.onPreferencesCreated();
        initTLSCert();

        findPreference(P_KEY_PROXY_CONFIG).setOnPreferenceClickListener(pref -> {
            BoshProxyDialog boshProxy = new BoshProxyDialog(getActivity(), jbrReg);
            boshProxy.show();
            return true;
        });

        findPreference(P_KEY_STUN_TURN_SERVERS).setOnPreferenceClickListener(pref -> {
            startStunServerListActivity();
            return true;
        });

        findPreference(P_KEY_JINGLE_NODES_LIST).setOnPreferenceClickListener(pref -> {
            startJingleNodeListActivity();
            return true;
        });

//        findPreference(P_KEY_USER_ID).setOnPreferenceClickListener(preference -> {
//            startAccountEditor();
//            return true;
//        });
    }

    /**
     * Initialize the client TLS certificate selection list
     */
    private void initTLSCert()
    {
        List<String> certList = new ArrayList<>();

        CertificateService cvs = JabberAccountRegistrationActivator.getCertificateService();
        List<CertificateConfigEntry> certEntries = cvs.getClientAuthCertificateConfigs();
        certEntries.add(0, CertificateConfigEntry.CERT_NONE);

        for (CertificateConfigEntry e : certEntries) {
            certList.add(e.toString());
        }

        AccountID accountID = getAccountID();
        String currentCert = accountID.getTlsClientCertificate();
        if (!certList.contains(currentCert) && !isInitialized()) {
            // Use the empty one i.e. None cert
            currentCert = certList.get(0);
            getPreferenceManager().getSharedPreferences().edit().putString(P_KEY_TLS_CERT_ID, currentCert).apply();
        }

        String[] entries = new String[certList.size()];
        entries = certList.toArray(entries);
        ListPreference certPreference = (ListPreference) findPreference(P_KEY_TLS_CERT_ID);
        certPreference.setEntries(entries);
        certPreference.setEntryValues(entries);

        if (!isInitialized())
            certPreference.setValue(currentCert);
    }

//    private void startAccountEditor()
//    {
//        // Create AccountLoginFragment fragment
//        String login = "swordfish@atalk.sytes.net";
//        String password = "1234";
//
//        Intent intent = new Intent(getActivity(), AccountLoginActivity.class);
//        intent.putExtra(AccountLoginFragment.ARG_USERNAME, login);
//        intent.putExtra(AccountLoginFragment.ARG_PASSWORD, password);
//        startActivity(intent);
//    }

    /**
     * Starts {@link ServerListActivity} in order to edit STUN servers list
     */
    private void startStunServerListActivity()
    {
        Intent intent = new Intent(getActivity(), ServerListActivity.class);
        intent.putExtra(ServerListActivity.JABBER_REGISTRATION_KEY, jbrReg);
        intent.putExtra(ServerListActivity.REQUEST_CODE_KEY, ServerListActivity.REQUEST_EDIT_STUN_TURN);
        startActivityForResult(intent, EDIT_STUN_TURN);
        setUncommittedChanges();
    }

    /**
     * Start {@link ServerListActivity} in order to edit Jingle Nodes list
     */
    private void startJingleNodeListActivity()
    {
        Intent intent = new Intent(getActivity(), ServerListActivity.class);
        intent.putExtra(ServerListActivity.JABBER_REGISTRATION_KEY, jbrReg);
        intent.putExtra(ServerListActivity.REQUEST_CODE_KEY, ServerListActivity.REQUEST_EDIT_JINGLE_NODES);
        startActivityForResult(intent, EDIT_JINGLE_NODES);
        setUncommittedChanges();
    }

    /**
     * {@inheritDoc}
     */
    protected void mapSummaries(SummaryMapper summaryMapper)
    {
        String emptyStr = getEmptyPreferenceStr();

        // User name and password
        summaryMapper.includePreference(findPreference(P_KEY_USER_ID), emptyStr);
        summaryMapper.includePreference(findPreference(P_KEY_PASSWORD), emptyStr, new SummaryMapper.PasswordMask());
        summaryMapper.includePreference(findPreference(P_KEY_DNSSEC_MODE), emptyStr);
        summaryMapper.includePreference(findPreference(P_KEY_TLS_CERT_ID), emptyStr);

        // DTMF Option
        summaryMapper.includePreference(findPreference(P_KEY_DTMF_METHOD), emptyStr, input -> {
            ListPreference lp = (ListPreference) findPreference(P_KEY_DTMF_METHOD);
            return lp.getEntry().toString();
        });
        // Ping interval
        summaryMapper.includePreference(findPreference(P_KEY_PING_INTERVAL), emptyStr);

        // Server options
        summaryMapper.includePreference(findPreference(P_KEY_SERVER_ADDRESS), emptyStr);
        summaryMapper.includePreference(findPreference(P_KEY_SERVER_PORT), emptyStr);

        // Resource
        summaryMapper.includePreference(findPreference(P_KEY_RESOURCE_NAME), emptyStr);
        summaryMapper.includePreference(findPreference(P_KEY_RESOURCE_PRIORITY), emptyStr);

        // Telephony
        summaryMapper.includePreference(findPreference(P_KEY_OVERRIDE_PHONE_SUFFIX), emptyStr);
        summaryMapper.includePreference(findPreference(P_KEY_TEL_BYPASS_GTALK_CAPS), emptyStr);
    }

    /**
     * Stores values changed by STUN or Jingle nodes edit activities. <br/>
     * {@inheritDoc}
     */
    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data)
    {
        if ((requestCode == EDIT_JINGLE_NODES) && (resultCode == Activity.RESULT_OK)) {
            // Gets edited Jingle Nodes list
            JabberAccountRegistration serialized = (JabberAccountRegistration)
                    data.getSerializableExtra(ServerListActivity.JABBER_REGISTRATION_KEY);

            jbrReg.getAdditionalJingleNodes().clear();
            jbrReg.getAdditionalJingleNodes().addAll(serialized.getAdditionalJingleNodes());
        }
        else if (requestCode == EDIT_STUN_TURN && resultCode == Activity.RESULT_OK) {
            // Gets edited STUN servers list
            JabberAccountRegistration serialized = (JabberAccountRegistration)
                    data.getSerializableExtra(ServerListActivity.JABBER_REGISTRATION_KEY);

            jbrReg.getAdditionalStunServers().clear();
            jbrReg.getAdditionalStunServers().addAll(serialized.getAdditionalStunServers());
        }
        else {
            super.onActivityResult(requestCode, resultCode, data);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onSharedPreferenceChanged(SharedPreferences shPrefs, String key)
    {
        // Check to ensure a valid key before proceed
        if (findPreference(key) == null)
            return;

        super.onSharedPreferenceChanged(shPrefs, key);
        if (key.equals(P_KEY_USER_ID)) {
            getUserConfirmation(shPrefs);
        }
        else if (key.equals(P_KEY_PASSWORD)) {
            // seems the encrypted password is not save to DB but work?- need further investigation in signin if have problem
            jbrReg.setPassword(shPrefs.getString(P_KEY_PASSWORD, null));
        }
        else if (key.equals(P_KEY_STORE_PASSWORD)) {
            jbrReg.setRememberPassword(shPrefs.getBoolean(P_KEY_STORE_PASSWORD, false));
        }
        else if (key.equals(P_KEY_DNSSEC_MODE)) {
            String dnssecMode = shPrefs.getString(P_KEY_DNSSEC_MODE,
                    getResources().getStringArray(R.array.dnssec_Mode_value)[0]);
            jbrReg.setDnssMode(dnssecMode);
        }
        else if (key.equals(P_KEY_TLS_CERT_ID)) {
            jbrReg.setTlsClientCertificate(shPrefs.getString(P_KEY_TLS_CERT_ID, null));
        }
        else if (key.equals(P_KEY_GMAIL_NOTIFICATIONS)) {
            jbrReg.setGmailNotificationEnabled(shPrefs.getBoolean(P_KEY_GMAIL_NOTIFICATIONS, false));
        }
        else if (key.equals(P_KEY_GOOGLE_CONTACTS_ENABLED)) {
            jbrReg.setGoogleContactsEnabled(shPrefs.getBoolean(P_KEY_GOOGLE_CONTACTS_ENABLED, false));
        }
        else if (key.equals(P_KEY_MINIMUM_TLS_VERSION)) {
            String newMinimumTLSVersion = shPrefs.getString(P_KEY_MINIMUM_TLS_VERSION,
                    ProtocolProviderServiceJabberImpl.defaultMinimumTLSversion);
            boolean isSupported = false;
            try {
                String[] supportedProtocols
                        = ((SSLSocket) SSLSocketFactory.getDefault().createSocket()).getSupportedProtocols();
                for (String suppProto : supportedProtocols) {
                    if (suppProto.equals(newMinimumTLSVersion)) {
                        isSupported = true;
                        break;
                    }
                }
            } catch (IOException ignore) {
            }
            if (!isSupported) {
                newMinimumTLSVersion = ProtocolProviderServiceJabberImpl.defaultMinimumTLSversion;
            }
            jbrReg.setMinimumTLSversion(newMinimumTLSVersion);
        }
        else if (key.equals(P_KEY_ALLOW_NON_SECURE_CONN)) {
            jbrReg.setAllowNonSecure(shPrefs.getBoolean(P_KEY_ALLOW_NON_SECURE_CONN, false));
        }
        else if (key.equals(P_KEY_DTMF_METHOD)) {
            jbrReg.setDTMFMethod(shPrefs.getString(P_KEY_DTMF_METHOD, null));
        }
        else if (key.equals(P_KEY_IS_KEEP_ALIVE_ENABLE)) {
            jbrReg.setKeepAliveOption(shPrefs.getBoolean(P_KEY_IS_KEEP_ALIVE_ENABLE, false));
        }
        else if (key.equals(P_KEY_PING_INTERVAL)) {
            jbrReg.setPingInterval(shPrefs.getString(P_KEY_PING_INTERVAL,
                    Integer.toString(ProtocolProviderServiceJabberImpl.defaultPingInterval)));
        }
        else if (key.equals(P_KEY_IS_PING_AUTO_TUNE_ENABLE)) {
            jbrReg.setPingAutoTuneOption(shPrefs.getBoolean(P_KEY_IS_PING_AUTO_TUNE_ENABLE, true));
        }
        else if (key.equals(P_KEY_IS_SERVER_OVERRIDDEN)) {
            jbrReg.setServerOverridden(shPrefs.getBoolean(P_KEY_IS_SERVER_OVERRIDDEN, false));
        }
        else if (key.equals(P_KEY_SERVER_ADDRESS)) {
            jbrReg.setServerAddress(shPrefs.getString(P_KEY_SERVER_ADDRESS, null));
        }
        else if (key.equals(P_KEY_SERVER_PORT)) {
            jbrReg.setServerPort(shPrefs.getString(P_KEY_SERVER_PORT,
                    Integer.toString(ProtocolProviderServiceJabberImpl.DEFAULT_PORT)));
        }
        else if (key.equals(P_KEY_AUTO_GEN_RESOURCE)) {
            jbrReg.setResourceAutoGenerated(shPrefs.getBoolean(P_KEY_AUTO_GEN_RESOURCE, false));
        }
        else if (key.equals(P_KEY_RESOURCE_NAME)) {
            jbrReg.setResource(shPrefs.getString(P_KEY_RESOURCE_NAME, null));
        }
        else if (key.equals(P_KEY_RESOURCE_PRIORITY)) {
            try {
                jbrReg.setPriority(shPrefs.getInt(P_KEY_RESOURCE_PRIORITY, 30));
            } catch (Exception ex) {
                Timber.w("Invalid resource priority: %s", ex.getMessage());
            }
        }
        else if (key.equals(P_KEY_ICE_ENABLED)) {
            jbrReg.setUseIce(shPrefs.getBoolean(P_KEY_ICE_ENABLED, true));
        }
        else if (key.equals(P_KEY_UPNP_ENABLED)) {
            jbrReg.setUseUPNP(shPrefs.getBoolean(P_KEY_UPNP_ENABLED, true));
        }
        else if (key.equals(P_KEY_AUTO_DISCOVER_STUN)) {
            jbrReg.setAutoDiscoverStun(shPrefs.getBoolean(P_KEY_AUTO_DISCOVER_STUN, true));
        }
        else if (key.equals(P_KEY_USE_JINGLE_NODES)) {
            jbrReg.setUseJingleNodes(shPrefs.getBoolean(P_KEY_USE_JINGLE_NODES, true));
        }
        else if (key.equals(P_KEY_AUTO_RELAY_DISCOVERY)) {
            jbrReg.setAutoDiscoverJingleNodes(shPrefs.getBoolean(P_KEY_AUTO_RELAY_DISCOVERY, true));
        }
        else if (key.equals(P_KEY_CALLING_DISABLED)) {
            jbrReg.setDisableJingle(shPrefs.getBoolean(P_KEY_CALLING_DISABLED, false));
        }
        else if (key.equals(P_KEY_OVERRIDE_PHONE_SUFFIX)) {
            jbrReg.setOverridePhoneSuffix(shPrefs.getString(P_KEY_OVERRIDE_PHONE_SUFFIX, null));
        }
        else if (key.equals(P_KEY_TEL_BYPASS_GTALK_CAPS)) {
            jbrReg.setTelephonyDomainBypassCaps(shPrefs.getString(P_KEY_TEL_BYPASS_GTALK_CAPS, null));
        }
    }

    /**
     * Warn and get user confirmation if changes of userName will lead to removal of any old messages
     * of the old account. It also checks for valid userName entry.
     *
     * @param shPrefs SharedPreferences
     */
    private void getUserConfirmation(SharedPreferences shPrefs)
    {
        final String userName = shPrefs.getString(P_KEY_USER_ID, null);
        if (!TextUtils.isEmpty(userName) && userName.contains("@")) {
            String editedAccUid = jbrReg.getAccountUniqueID();
            if (userNameEdited.equals(userName)) {
                jbrReg.setUserID(userName);
                userNameLastEdited = userName;
            }
            else if (!userNameLastEdited.equals(userName)) {
                MessageHistoryServiceImpl mhs = MessageHistoryActivator.getMessageHistoryService();
                int msgCount = mhs.getMessageCountForAccountUuid(editedAccUid);
                if (msgCount > 0) {
                    String msgPrompt = aTalkApp.getResString(R.string.service_gui_USERNAME_CHANGE_WARN,
                            userName, msgCount, userNameEdited);
                    DialogActivity.showConfirmDialog(aTalkApp.getGlobalContext(),
                            aTalkApp.getResString(R.string.service_gui_WARNING), msgPrompt,
                            aTalkApp.getResString(R.string.service_gui_PROCEED), new DialogActivity.DialogListener()
                            {
                                @Override
                                public boolean onConfirmClicked(DialogActivity dialog)
                                {
                                    jbrReg.setUserID(userName);
                                    userNameLastEdited = userName;
                                    return true;
                                }

                                @Override
                                public void onDialogCancelled(DialogActivity dialog)
                                {
                                    jbrReg.setUserID(userNameEdited);
                                    userNameLastEdited = userNameEdited;
                                    SharedPreferences.Editor editor = shPrefs.edit();
                                    editor.putString(P_KEY_USER_ID, jbrReg.getUserID());
                                    editor.apply();
                                }
                            });

                }
                else {
                    jbrReg.setUserID(userName);
                    userNameLastEdited = userName;
                }
            }
        }
        else {
            userNameLastEdited = userNameEdited;
            aTalkApp.showToastMessage(R.string.service_gui_USERNAME_NULL);
        }
    }

    /**
     * This is executed when the user press BackKey. Signin with modification will merge the change properties
     * i.e jbrReg.getAccountProperties() with the accountID mAccountProperties before saving to SQL database
     */
    @Override
    protected void doCommitChanges()
    {
        try {
            AccountRegistrationImpl accWizard = getJbrWizard();
            accWizard.setModification(true);
            accWizard.signin(jbrReg.getUserID(), jbrReg.getPassword(), jbrReg.getAccountProperties());
        } catch (OperationFailedException e) {
            Timber.e(e, "Failed to store account modifications: %s", e.getLocalizedMessage());
        }
    }
}
