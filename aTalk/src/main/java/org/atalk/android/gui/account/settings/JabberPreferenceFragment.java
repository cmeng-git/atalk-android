/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.android.gui.account.settings;

import android.app.Activity;
import android.content.*;
import android.preference.*;

import net.java.sip.communicator.plugin.jabberaccregwizz.AccountRegistrationImpl;
import net.java.sip.communicator.service.protocol.*;
import net.java.sip.communicator.service.protocol.jabber.JabberAccountRegistration;
import net.java.sip.communicator.util.Logger;

import org.atalk.android.*;
import org.atalk.android.gui.settings.util.SummaryMapper;

/**
 * Preferences fragment for Jabber settings. It maps Jabber specific properties to the
 * {@link Preference}s. Reads from and stores them inside {@link JabberAccountRegistration}.
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
	private static final String PREF_KEY_USER_ID
			= aTalkApp.getAppResources().getString(R.string.pref_key_user_id);
	private static final String PREF_KEY_PASSWORD
			= aTalkApp.getAppResources().getString(R.string.pref_key_password);
    private static final String PREF_KEY_STORE_PASSWORD
            = aTalkApp.getAppResources().getString(R.string.pref_key_store_password);

	// Account General
	private static final String PREF_KEY_GMAIL_NOTIFICATIONS
			= aTalkApp.getAppResources().getString(R.string.pref_key_gmail_notifications);
	private static final String PREF_KEY_GOOGLE_CONTACTS_ENABLED
			= aTalkApp.getAppResources().getString(
			R.string.pref_key_google_contact_enabled);
	private static final String PREF_KEY_DTMF_METHOD
			= aTalkApp.getAppResources().getString(R.string.pref_key_dtmf_method);

	// Server Options
	private static final String PREF_KEY_IS_KEEP_ALIVE_ENABLE
			= aTalkApp.getAppResources().getString(R.string.pref_key_is_keep_alive_enable);
	public static final String PREF_KEY_PING_INTERVAL
			= aTalkApp.getAppResources().getString(R.string.pref_key_ping_interval);
	private static final String PREF_KEY_IS_SERVER_OVERRIDDEN
			= aTalkApp.getAppResources().getString(R.string.pref_key_is_server_overridden);
	public static final String PREF_KEY_SERVER_ADDRESS
			= aTalkApp.getAppResources().getString(R.string.pref_key_server_address);
	public static final String PREF_KEY_SERVER_PORT
			= aTalkApp.getAppResources().getString(R.string.pref_key_server_port);
	private static final String PREF_KEY_ALLOW_NON_SECURE_CONN = aTalkApp.getAppResources()
			.getString(R.string.pref_key_allow_non_secure_conn);

	// Jabber Resource
	private static final String PREF_KEY_AUTO_GEN_RESOURCE
			= aTalkApp.getAppResources().getString(R.string.pref_key_auto_gen_resource);
	private static final String PREF_KEY_RESOURCE_NAME
			= aTalkApp.getAppResources().getString(R.string.pref_key_resource_name);
	private static final String PREF_KEY_RESOURCE_PRIORITY
			= aTalkApp.getAppResources().getString(R.string.pref_key_resource_priority);

	// Proxy
	private static final String PREF_KEY_PROXY_ENABLE
			= aTalkApp.getAppResources().getString(R.string.pref_key_proxy_enable);
	private static final String PREF_KEY_PROXY_TYPE
			= aTalkApp.getAppResources().getString(R.string.pref_key_proxy_type);
	private static final String PREF_KEY_PROXY_ADDRESS
			= aTalkApp.getAppResources().getString(R.string.pref_key_proxy_address);
	private static final String PREF_KEY_PROXY_PORT
			= aTalkApp.getAppResources().getString(R.string.pref_key_proxy_port);
	private static final String PREF_KEY_PROXY_USERNAME
			= aTalkApp.getAppResources().getString(R.string.pref_key_proxy_username);
	private static final String PREF_KEY_PROXY_PASSWORD
			= aTalkApp.getAppResources().getString(R.string.pref_key_proxy_password);

	// ICE (General)
	private static final String PREF_KEY_ICE_ENABLED
			= aTalkApp.getAppResources().getString(R.string.pref_key_ice_enabled);
	private static final String PREF_KEY_UPNP_ENABLED
			= aTalkApp.getAppResources().getString(R.string.pref_key_upnp_enabled);
	private static final String PREF_KEY_AUTO_DISCOVERY_JINGLE
			= aTalkApp.getAppResources().getString(R.string.pref_key_auto_discover_jingle);
	private static final String PREF_KEY_STUN_TURN_SERVERS
			= aTalkApp.getAppResources().getString(R.string.pref_key_stun_turn_servers);

	// Jingle Nodes
	private static final String PREF_KEY_USE_JINGLE_NODES
			= aTalkApp.getAppResources().getString(R.string.pref_key_use_jingle_nodes);
	private static final String PREF_KEY_AUTO_RELAY_DISCOVERY
			= aTalkApp.getAppResources().getString(R.string.pref_key_auto_relay_dicovery);
	private static final String PREF_KEY_JINGLE_NODES_LIST
			= aTalkApp.getAppResources().getString(R.string.pref_key_jingle_node_list);

	// Telephony
	private static final String PREF_KEY_CALLING_DISABLED
			= aTalkApp.getAppResources().getString(R.string.pref_key_calling_disabled);
	private static final String PREF_KEY_OVERRIDE_PHONE_SUFFIX
			= aTalkApp.getAppResources()
			.getString(R.string.pref_key_override_phone_suffix);
	private static final String PREF_KEY_TELE_BYPASS_GTALK_CAPS
			= aTalkApp
			.getAppResources().getString(R.string.pref_key_tele_bypass_gtalk_caps);

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
	 * Returns jabber registration object.
	 *
	 * @return jabber registration object.
	 */
	private JabberAccountRegistration getAccountRegistration()
	{
		return getJbrWizard().getAccountRegistration();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected EncodingsRegistrationUtil getEncodingsRegistration()
	{
		return getAccountRegistration().getEncodingsRegistration();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected SecurityAccountRegistration getSecurityRegistration()
	{
		return getAccountRegistration().getSecurityRegistration();
	}

	/**
	 * {@inheritDoc}
	 */
	protected void onInitPreferences()
	{
		AccountRegistrationImpl wizard = getJbrWizard();
		JabberAccountRegistration registration = wizard.getAccountRegistration();

		SharedPreferences preferences
				= PreferenceManager.getDefaultSharedPreferences(getActivity());
		SharedPreferences.Editor editor = preferences.edit();

		// User name and password
		editor.putString(PREF_KEY_USER_ID, registration.getUserID());
		editor.putString(PREF_KEY_PASSWORD, registration.getPassword());
        editor.putBoolean(PREF_KEY_STORE_PASSWORD, registration.isRememberPassword());

		// Connection
		editor.putBoolean(PREF_KEY_GMAIL_NOTIFICATIONS, registration.isGmailNotificationEnabled());
		editor.putBoolean(PREF_KEY_GOOGLE_CONTACTS_ENABLED,
				registration.isGoogleContactsEnabled());
		editor.putBoolean(PREF_KEY_ALLOW_NON_SECURE_CONN, registration.isAllowNonSecure());
		editor.putString(PREF_KEY_DTMF_METHOD, registration.getDTMFMethod());

		// Keep alive options
		editor.putBoolean(PREF_KEY_IS_KEEP_ALIVE_ENABLE, registration.isKeepAliveEnable());
		editor.putString(PREF_KEY_PING_INTERVAL, "" + registration.getPingInterval());

		// Server options
		editor.putBoolean(PREF_KEY_IS_SERVER_OVERRIDDEN, registration.isServerOverridden());
		editor.putString(PREF_KEY_SERVER_ADDRESS, registration.getServerAddress());
		editor.putString(PREF_KEY_SERVER_PORT, "" + registration.getServerPort());

		// Resource
		editor.putBoolean(PREF_KEY_AUTO_GEN_RESOURCE, registration.isResourceAutoGenerated());
		editor.putString(PREF_KEY_RESOURCE_NAME, registration.getResource());
		editor.putString(PREF_KEY_RESOURCE_PRIORITY, "" + registration.getPriority());

		// Proxy options
		editor.putBoolean(PREF_KEY_PROXY_ENABLE, registration.isUseProxy());
		editor.putString(PREF_KEY_PROXY_TYPE, registration.getProxyType());
		editor.putString(PREF_KEY_PROXY_ADDRESS, registration.getProxyAddress());
		editor.putString(PREF_KEY_PROXY_PORT, registration.getProxyPort());
		editor.putString(PREF_KEY_PROXY_USERNAME, registration.getProxyUserName());
		editor.putString(PREF_KEY_PROXY_PASSWORD, registration.getProxyPassword());

		// ICE options
		editor.putBoolean(PREF_KEY_ICE_ENABLED, registration.isUseIce());
		editor.putBoolean(PREF_KEY_UPNP_ENABLED, registration.isUseUPNP());

		// Jingle Nodes
		editor.putBoolean(PREF_KEY_USE_JINGLE_NODES, registration.isUseJingleNodes());
		editor.putBoolean(PREF_KEY_AUTO_DISCOVERY_JINGLE, registration.isAutoDiscoverJingleNodes());
		editor.putBoolean(PREF_KEY_AUTO_RELAY_DISCOVERY, registration.isAutoDiscoverStun());

		// Telephony
		editor.putBoolean(PREF_KEY_CALLING_DISABLED, registration.isJingleDisabled());
		editor.putString(PREF_KEY_OVERRIDE_PHONE_SUFFIX, registration.getOverridePhoneSuffix());
		editor.putString(PREF_KEY_TELE_BYPASS_GTALK_CAPS,
				registration.getTelephonyDomainBypassCaps());

		editor.apply();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected void onPreferencesCreated()
	{
		super.onPreferencesCreated();

		findPreference(PREF_KEY_STUN_TURN_SERVERS).setOnPreferenceClickListener(
				new Preference.OnPreferenceClickListener()
				{
					public boolean onPreferenceClick(Preference pref)
					{
						startStunServerListActivity();
						return true;
					}
				});

		findPreference(PREF_KEY_JINGLE_NODES_LIST).setOnPreferenceClickListener(
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
		intent.putExtra(ServerListActivity.JABBER_REGISTRATION_KEY, getAccountRegistration());
		intent.putExtra(ServerListActivity.REQUEST_CODE_KEY,
				ServerListActivity.REQUEST_EDIT_STUN_TURN);
		startActivityForResult(intent, EDIT_STUN_TURN);
		setUncommittedChanges();
	}

	/**
	 * Start {@link ServerListActivity} in order to edit Jingle Nodes list
	 */
	private void startJingleNodeListActivity()
	{
		Intent intent = new Intent(getActivity(), ServerListActivity.class);
		intent.putExtra(ServerListActivity.JABBER_REGISTRATION_KEY, getAccountRegistration());
		intent.putExtra(ServerListActivity.REQUEST_CODE_KEY,
				ServerListActivity.REQUEST_EDIT_JINGLE_NODES);
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
		summaryMapper.includePreference(findPreference(PREF_KEY_USER_ID), emptyStr);
		summaryMapper.includePreference(findPreference(PREF_KEY_PASSWORD), emptyStr,
				new SummaryMapper.PasswordMask());

		// Connection
		summaryMapper.includePreference(findPreference(PREF_KEY_DTMF_METHOD), emptyStr);

		// Ping interval
		summaryMapper.includePreference(findPreference(PREF_KEY_PING_INTERVAL), emptyStr);

		// Server options
		summaryMapper.includePreference(findPreference(PREF_KEY_SERVER_ADDRESS), emptyStr);
		summaryMapper.includePreference(findPreference(PREF_KEY_SERVER_PORT), emptyStr);

		// Proxy options
		summaryMapper.includePreference(findPreference(PREF_KEY_PROXY_TYPE), emptyStr);
		summaryMapper.includePreference(findPreference(PREF_KEY_PROXY_ADDRESS), emptyStr);
		summaryMapper.includePreference(findPreference(PREF_KEY_PROXY_PORT), emptyStr);
		summaryMapper.includePreference(findPreference(PREF_KEY_PROXY_USERNAME), emptyStr);
		summaryMapper.includePreference(findPreference(PREF_KEY_PROXY_PASSWORD), emptyStr,
				new SummaryMapper.PasswordMask());

		// Resource
		summaryMapper.includePreference(findPreference(PREF_KEY_RESOURCE_NAME), emptyStr);
		summaryMapper.includePreference(findPreference(PREF_KEY_RESOURCE_PRIORITY), emptyStr);

		// Telephony
		summaryMapper.includePreference(findPreference(PREF_KEY_OVERRIDE_PHONE_SUFFIX), emptyStr);
		summaryMapper.includePreference(findPreference(PREF_KEY_TELE_BYPASS_GTALK_CAPS), emptyStr);
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
			JabberAccountRegistration current = getAccountRegistration();

			current.getAdditionalJingleNodes().clear();
			current.getAdditionalJingleNodes().addAll(serialized.getAdditionalJingleNodes());
		}
		else if (requestCode == EDIT_STUN_TURN && resultCode == Activity.RESULT_OK) {
			// Gets edited STUN servers list
			JabberAccountRegistration serialized = (JabberAccountRegistration)
					data.getSerializableExtra(ServerListActivity.JABBER_REGISTRATION_KEY);
			JabberAccountRegistration current = getAccountRegistration();

			current.getAdditionalStunServers().clear();
			current.getAdditionalStunServers().addAll(serialized.getAdditionalStunServers());
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
		Preference preference = findPreference(key);
		if (preference == null)
			return;

		super.onSharedPreferenceChanged(shPrefs, key);
		JabberAccountRegistration reg = getAccountRegistration();

		if (key.equals(PREF_KEY_PASSWORD)) {
			reg.setPassword(shPrefs.getString(PREF_KEY_PASSWORD, null));
	    }
        else if(key.equals(PREF_KEY_STORE_PASSWORD)) {
            reg.setRememberPassword(shPrefs.getBoolean(PREF_KEY_STORE_PASSWORD, false));
        }
		else if (key.equals(PREF_KEY_GMAIL_NOTIFICATIONS)) {
			reg.setGmailNotificationEnabled(
					shPrefs.getBoolean(PREF_KEY_GMAIL_NOTIFICATIONS, false));
		}
		else if (key.equals(PREF_KEY_GOOGLE_CONTACTS_ENABLED)) {
			reg.setGoogleContactsEnabled(
					shPrefs.getBoolean(PREF_KEY_GOOGLE_CONTACTS_ENABLED, false));
		}
		else if (key.equals(PREF_KEY_ALLOW_NON_SECURE_CONN)) {
			reg.setAllowNonSecure(shPrefs.getBoolean(PREF_KEY_ALLOW_NON_SECURE_CONN, false));
		}
		else if (key.equals(PREF_KEY_DTMF_METHOD)) {
			reg.setDTMFMethod(shPrefs.getString(PREF_KEY_DTMF_METHOD, null));
		}
		else if (key.equals(PREF_KEY_IS_KEEP_ALIVE_ENABLE)) {
			reg.setKeepAliveOption(shPrefs.getBoolean(PREF_KEY_IS_KEEP_ALIVE_ENABLE, false));
		}
		else if (key.equals(PREF_KEY_PING_INTERVAL)) {
			reg.setPingInterval(shPrefs.getString(PREF_KEY_PING_INTERVAL, null));
		}
		else if (key.equals(PREF_KEY_IS_SERVER_OVERRIDDEN)) {
			reg.setServerOverridden(shPrefs.getBoolean(PREF_KEY_IS_SERVER_OVERRIDDEN, false));
		}
		else if (key.equals(PREF_KEY_SERVER_ADDRESS)) {
			reg.setServerAddress(shPrefs.getString(PREF_KEY_SERVER_ADDRESS, null));
		}
		else if (key.equals(PREF_KEY_SERVER_PORT)) {
			reg.setServerPort(shPrefs.getString(PREF_KEY_SERVER_PORT, null));
		}
		else if (key.equals(PREF_KEY_AUTO_GEN_RESOURCE)) {
			reg.setResourceAutoGenerated(shPrefs.getBoolean(PREF_KEY_AUTO_GEN_RESOURCE, false));
		}
		else if (key.equals(PREF_KEY_RESOURCE_NAME)) {
			reg.setResource(shPrefs.getString(PREF_KEY_RESOURCE_NAME, null));
		}
		else if (key.equals(PREF_KEY_RESOURCE_PRIORITY)) {
			reg.setPriority(Integer.valueOf(shPrefs.getString(PREF_KEY_RESOURCE_PRIORITY, null)));
		}
		else if (key.equals(PREF_KEY_PROXY_ENABLE)) {
			reg.setUseProxy(shPrefs.getBoolean(PREF_KEY_PROXY_ENABLE, false));
		}
		else if (key.equals(PREF_KEY_PROXY_TYPE)) {
			reg.setProxyType(shPrefs.getString(PREF_KEY_PROXY_TYPE, null));
		}
		else if (key.equals(PREF_KEY_PROXY_ADDRESS)) {
			reg.setProxyAddress(shPrefs.getString(PREF_KEY_PROXY_ADDRESS, null));
		}
		else if (key.equals(PREF_KEY_PROXY_PORT)) {
			reg.setProxyPort(shPrefs.getString(PREF_KEY_PROXY_PORT, null));
		}
		else if (key.equals(PREF_KEY_PROXY_USERNAME)) {
			reg.setProxyUserName(shPrefs.getString(PREF_KEY_PROXY_USERNAME, null));
		}
		else if (key.equals(PREF_KEY_PROXY_PASSWORD)) {
			reg.setProxyPassword(shPrefs.getString(PREF_KEY_PROXY_PASSWORD, null));
		}
		else if (key.equals(PREF_KEY_ICE_ENABLED)) {
			reg.setUseIce(shPrefs.getBoolean(PREF_KEY_ICE_ENABLED, true));
		}
		else if (key.equals(PREF_KEY_UPNP_ENABLED)) {
			reg.setUseUPNP(shPrefs.getBoolean(PREF_KEY_UPNP_ENABLED, true));
		}
		else if (key.equals(PREF_KEY_AUTO_DISCOVERY_JINGLE)) {
			reg.setAutoDiscoverJingleNodes(shPrefs.getBoolean(PREF_KEY_AUTO_DISCOVERY_JINGLE, true));
		}
		else if (key.equals(PREF_KEY_USE_JINGLE_NODES)) {
			reg.setUseJingleNodes(shPrefs.getBoolean(PREF_KEY_USE_JINGLE_NODES, true));
		}
		else if (key.equals(PREF_KEY_AUTO_RELAY_DISCOVERY)) {
			reg.setAutoDiscoverStun(shPrefs.getBoolean(PREF_KEY_AUTO_RELAY_DISCOVERY, true));
		}
		else if (key.equals(PREF_KEY_CALLING_DISABLED)) {
			reg.setDisableJingle(shPrefs.getBoolean(PREF_KEY_CALLING_DISABLED, false));
		}
		else if (key.equals(PREF_KEY_OVERRIDE_PHONE_SUFFIX)) {
			reg.setOverridePhoneSuffix(shPrefs.getString(PREF_KEY_OVERRIDE_PHONE_SUFFIX, null));
		}
		else if (key.equals(PREF_KEY_TELE_BYPASS_GTALK_CAPS)) {
			reg.setTelephonyDomainBypassCaps(
					shPrefs.getString(PREF_KEY_TELE_BYPASS_GTALK_CAPS, null));
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected void doCommitChanges()
	{
		try {
			AccountRegistrationImpl accWizard = (AccountRegistrationImpl) getWizard();
			JabberAccountRegistration jbrReg = getAccountRegistration();

            accWizard.setModification(true);
			accWizard.signin(jbrReg.getUserID(), jbrReg.getPassword(), jbrReg.getAccountProperties());
		}
		catch (OperationFailedException e) {
			logger.error("Failed to store account modifications: " + e.getLocalizedMessage(), e);
		}
	}
}
