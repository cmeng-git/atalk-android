/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.android.gui.account.settings;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.Preference;
import android.preference.PreferenceManager;

import net.java.sip.communicator.impl.protocol.jabber.ProtocolProviderServiceJabberImpl;
import net.java.sip.communicator.plugin.jabberaccregwizz.AccountRegistrationImpl;
import net.java.sip.communicator.service.protocol.*;
import net.java.sip.communicator.service.protocol.jabber.JabberAccountRegistration;
import net.java.sip.communicator.util.Logger;

import org.atalk.android.R;
import org.atalk.android.aTalkApp;
import org.atalk.android.gui.settings.util.SummaryMapper;

/**
 * Preferences fragment for Jabber settings. It maps Jabber specific properties to the
 * {@link Preference}s. Reads from and stores them inside {@link JabberAccountRegistration}.
 *
 * This is an instance of the accountID properties from Account Setting... preference editing. These changes
 * will be merged with the original mAccountProperties and saved to database in doCommitChanges()
 *
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
public class JabberPreferenceFragment extends AccountPreferenceFragment
{
    /**
     * The logger
     */
    private static final Logger logger = Logger.getLogger(JabberPreferenceFragment.class);

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

    // Account General
    private static final String P_KEY_GMAIL_NOTIFICATIONS = aTalkApp.getResString(R.string.pref_key_gmail_notifications);
    private static final String P_KEY_GOOGLE_CONTACTS_ENABLED
            = aTalkApp.getResString(R.string.pref_key_google_contact_enabled);
    private static final String P_KEY_DTMF_METHOD = aTalkApp.getResString(R.string.pref_key_dtmf_method);

    // Server Options
    private static final String P_KEY_IS_KEEP_ALIVE_ENABLE
            = aTalkApp.getResString(R.string.pref_key_is_keep_alive_enable);
    public static final String P_KEY_PING_INTERVAL = aTalkApp.getResString(R.string.pref_key_ping_interval);
    private static final String P_KEY_IS_SERVER_OVERRIDDEN
            = aTalkApp.getResString(R.string.pref_key_is_server_overridden);
    public static final String P_KEY_SERVER_ADDRESS = aTalkApp.getResString(R.string.pref_key_server_address);
    public static final String P_KEY_SERVER_PORT = aTalkApp.getResString(R.string.pref_key_server_port);
    private static final String P_KEY_ALLOW_NON_SECURE_CONN = aTalkApp.getAppResources()
            .getString(R.string.pref_key_allow_non_secure_conn);

    // Jabber Resource
    private static final String P_KEY_AUTO_GEN_RESOURCE = aTalkApp.getResString(R.string.pref_key_auto_gen_resource);
    private static final String P_KEY_RESOURCE_NAME = aTalkApp.getResString(R.string.pref_key_resource_name);
    private static final String P_KEY_RESOURCE_PRIORITY = aTalkApp.getResString(R.string.pref_key_resource_priority);

    // Proxy
    private static final String P_KEY_PROXY_ENABLE = aTalkApp.getResString(R.string.pref_key_proxy_enable);
    private static final String P_KEY_PROXY_TYPE = aTalkApp.getResString(R.string.pref_key_proxy_type);
    private static final String P_KEY_PROXY_ADDRESS = aTalkApp.getResString(R.string.pref_key_proxy_address);
    private static final String P_KEY_PROXY_PORT = aTalkApp.getResString(R.string.pref_key_proxy_port);
    private static final String P_KEY_PROXY_USERNAME = aTalkApp.getResString(R.string.pref_key_proxy_username);
    private static final String P_KEY_PROXY_PASSWORD = aTalkApp.getResString(R.string.pref_key_proxy_password);

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
    private static final String P_KEY_OVERRIDE_PHONE_SUFFIX = aTalkApp.getAppResources()
            .getString(R.string.pref_key_override_phone_suffix);
    private static final String P_KEY_TELE_BYPASS_GTALK_CAPS
            = aTalkApp.getResString(R.string.pref_key_tele_bypass_gtalk_caps);

    /*
     * A new instance of AccountID and is not the same as accountID
     */
    private JabberAccountRegistration jbrReg;

    /**
     * Creates new instance of <tt>JabberPreferenceFragment</tt>
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
        editor.putString(P_KEY_USER_ID, jbrReg.getUserID());
        editor.putString(P_KEY_PASSWORD, jbrReg.getPassword());
        editor.putBoolean(P_KEY_STORE_PASSWORD, jbrReg.isRememberPassword());
        editor.putString(P_KEY_DNSSEC_MODE, jbrReg.getDnssMode());

        // Connection
        editor.putBoolean(P_KEY_GMAIL_NOTIFICATIONS, jbrReg.isGmailNotificationEnabled());
        editor.putBoolean(P_KEY_GOOGLE_CONTACTS_ENABLED, jbrReg.isGoogleContactsEnabled());
        editor.putBoolean(P_KEY_ALLOW_NON_SECURE_CONN, jbrReg.isAllowNonSecure());
        editor.putString(P_KEY_DTMF_METHOD, jbrReg.getDTMFMethod());

        // Keep alive options
        editor.putBoolean(P_KEY_IS_KEEP_ALIVE_ENABLE, jbrReg.isKeepAliveEnable());
        editor.putString(P_KEY_PING_INTERVAL, "" + jbrReg.getPingInterval());

        // Server options
        editor.putBoolean(P_KEY_IS_SERVER_OVERRIDDEN, jbrReg.isServerOverridden());
        editor.putString(P_KEY_SERVER_ADDRESS, jbrReg.getServerAddress());
        editor.putString(P_KEY_SERVER_PORT, "" + jbrReg.getServerPort());

        // Resource
        editor.putBoolean(P_KEY_AUTO_GEN_RESOURCE, jbrReg.isResourceAutoGenerated());
        editor.putString(P_KEY_RESOURCE_NAME, jbrReg.getResource());
        editor.putString(P_KEY_RESOURCE_PRIORITY, "" + jbrReg.getPriority());

        // Proxy options
        editor.putBoolean(P_KEY_PROXY_ENABLE, jbrReg.isUseProxy());
        editor.putString(P_KEY_PROXY_TYPE, jbrReg.getProxyType());
        editor.putString(P_KEY_PROXY_ADDRESS, jbrReg.getProxyAddress());
        editor.putString(P_KEY_PROXY_PORT, jbrReg.getProxyPort());
        editor.putString(P_KEY_PROXY_USERNAME, jbrReg.getProxyUserName());
        editor.putString(P_KEY_PROXY_PASSWORD, jbrReg.getProxyPassword());

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
        editor.putString(P_KEY_TELE_BYPASS_GTALK_CAPS, jbrReg.getTelephonyDomainBypassCaps());

        editor.apply();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void onPreferencesCreated()
    {
        super.onPreferencesCreated();

        findPreference(P_KEY_STUN_TURN_SERVERS).setOnPreferenceClickListener(
                new Preference.OnPreferenceClickListener()
                {
                    public boolean onPreferenceClick(Preference pref)
                    {
                        startStunServerListActivity();
                        return true;
                    }
                });

        findPreference(P_KEY_JINGLE_NODES_LIST).setOnPreferenceClickListener(
                new Preference.OnPreferenceClickListener()
                {
                    public boolean onPreferenceClick(Preference pref)
                    {
                        startJingleNodeListActivity();
                        return true;
                    }
                });
    }

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

        // Connection
        summaryMapper.includePreference(findPreference(P_KEY_DTMF_METHOD), emptyStr);

        // Ping interval
        summaryMapper.includePreference(findPreference(P_KEY_PING_INTERVAL), emptyStr);

        // Server options
        summaryMapper.includePreference(findPreference(P_KEY_SERVER_ADDRESS), emptyStr);
        summaryMapper.includePreference(findPreference(P_KEY_SERVER_PORT), emptyStr);

        // Proxy options
        summaryMapper.includePreference(findPreference(P_KEY_PROXY_TYPE), emptyStr);
        summaryMapper.includePreference(findPreference(P_KEY_PROXY_ADDRESS), emptyStr);
        summaryMapper.includePreference(findPreference(P_KEY_PROXY_PORT), emptyStr);
        summaryMapper.includePreference(findPreference(P_KEY_PROXY_USERNAME), emptyStr);
        summaryMapper.includePreference(findPreference(P_KEY_PROXY_PASSWORD), emptyStr, new SummaryMapper.PasswordMask());

        // Resource
        summaryMapper.includePreference(findPreference(P_KEY_RESOURCE_NAME), emptyStr);
        summaryMapper.includePreference(findPreference(P_KEY_RESOURCE_PRIORITY), emptyStr);

        // Telephony
        summaryMapper.includePreference(findPreference(P_KEY_OVERRIDE_PHONE_SUFFIX), emptyStr);
        summaryMapper.includePreference(findPreference(P_KEY_TELE_BYPASS_GTALK_CAPS), emptyStr);
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
        if (key.equals(P_KEY_PASSWORD)) {
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
        else if (key.equals(P_KEY_GMAIL_NOTIFICATIONS)) {
            jbrReg.setGmailNotificationEnabled(shPrefs.getBoolean(P_KEY_GMAIL_NOTIFICATIONS, false));
        }
        else if (key.equals(P_KEY_GOOGLE_CONTACTS_ENABLED)) {
            jbrReg.setGoogleContactsEnabled(shPrefs.getBoolean(P_KEY_GOOGLE_CONTACTS_ENABLED, false));
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
        else if (key.equals(P_KEY_IS_SERVER_OVERRIDDEN)) {
            jbrReg.setServerOverridden(shPrefs.getBoolean(P_KEY_IS_SERVER_OVERRIDDEN, false));
        }
        else if (key.equals(P_KEY_SERVER_ADDRESS)) {
            jbrReg.setServerAddress(shPrefs.getString(P_KEY_SERVER_ADDRESS, null));
        }
        else if (key.equals(P_KEY_SERVER_PORT)) {
            jbrReg.setServerPort(shPrefs.getString(P_KEY_SERVER_PORT, null));
        }
        else if (key.equals(P_KEY_AUTO_GEN_RESOURCE)) {
            jbrReg.setResourceAutoGenerated(shPrefs.getBoolean(P_KEY_AUTO_GEN_RESOURCE, false));
        }
        else if (key.equals(P_KEY_RESOURCE_NAME)) {
            jbrReg.setResource(shPrefs.getString(P_KEY_RESOURCE_NAME, null));
        }
        else if (key.equals(P_KEY_RESOURCE_PRIORITY)) {
            jbrReg.setPriority(Integer.valueOf(shPrefs.getString(P_KEY_RESOURCE_PRIORITY, null)));
        }
        else if (key.equals(P_KEY_PROXY_ENABLE)) {
            jbrReg.setUseProxy(shPrefs.getBoolean(P_KEY_PROXY_ENABLE, false));
        }
        else if (key.equals(P_KEY_PROXY_TYPE)) {
            jbrReg.setProxyType(shPrefs.getString(P_KEY_PROXY_TYPE, null));
        }
        else if (key.equals(P_KEY_PROXY_ADDRESS)) {
            jbrReg.setProxyAddress(shPrefs.getString(P_KEY_PROXY_ADDRESS, null));
        }
        else if (key.equals(P_KEY_PROXY_PORT)) {
            jbrReg.setProxyPort(shPrefs.getString(P_KEY_PROXY_PORT, null));
        }
        else if (key.equals(P_KEY_PROXY_USERNAME)) {
            jbrReg.setProxyUserName(shPrefs.getString(P_KEY_PROXY_USERNAME, null));
        }
        else if (key.equals(P_KEY_PROXY_PASSWORD)) {
            jbrReg.setProxyPassword(shPrefs.getString(P_KEY_PROXY_PASSWORD, null));
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
        else if (key.equals(P_KEY_TELE_BYPASS_GTALK_CAPS)) {
            jbrReg.setTelephonyDomainBypassCaps(shPrefs.getString(P_KEY_TELE_BYPASS_GTALK_CAPS, null));
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
            logger.error("Failed to store account modifications: " + e.getLocalizedMessage(), e);
        }
    }
}
