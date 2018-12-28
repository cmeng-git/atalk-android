/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.android.gui.settings;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceScreen;

import net.java.otr4j.OtrPolicy;
import net.java.sip.communicator.plugin.otr.OtrActivator;
import net.java.sip.communicator.util.Logger;

import org.atalk.android.*;
import org.atalk.android.gui.AndroidGUIActivator;
import org.atalk.android.gui.util.PreferenceUtil;
import org.atalk.service.configuration.ConfigurationService;
import org.atalk.service.osgi.*;

/**
 * Chat security settings screen with OTR preferences.
 *
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
public class ChatSecuritySettings extends OSGiActivity
{
	/**
	 * The logger
	 */
	private static final Logger logger = Logger.getLogger(SettingsActivity.class);

	// Preference mKeys
	static private final String P_KEY_CRYPTO_ENABLE = aTalkApp.getResString(R.string.pref_key_crypto_enable);
	static private final String P_KEY_CRYPTO_AUTO = aTalkApp.getResString(R.string.pref_key_otr_auto);
	static private final String P_KEY_CRYPTO_REQUIRE = aTalkApp.getResString(R.string.pref_key_crypto_require);

	// OMEMO Security section
	static private final String P_KEY_OMEMO_KEY_BLIND_TRUST
			= aTalkApp.getResString(R.string.pref_key_omemo_key_blind_trust);

	static private ConfigurationService mConfig = null;

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		if (savedInstanceState == null) {
			// Display the fragment as the main content.
			getFragmentManager().beginTransaction().replace(android.R.id.content, new SettingsFragment()).commit();
		}
	}

	/**
	 * The preferences fragment implements OTR settings.
	 */
	public static class SettingsFragment extends OSGiPreferenceFragment
			implements SharedPreferences.OnSharedPreferenceChangeListener
	{
		/**
		 * {@inheritDoc}
		 */
		@Override
		public void onCreate(Bundle savedInstanceState)
		{
			super.onCreate(savedInstanceState);
			addPreferencesFromResource(org.atalk.android.R.xml.security_preferences);
		}

		/**
		 * {@inheritDoc}
		 */
		@Override
		public void onStart()
		{
			super.onStart();

			mConfig = AndroidGUIActivator.getConfigurationService();
			OtrPolicy otrPolicy = OtrActivator.scOtrEngine.getGlobalPolicy();
			PreferenceScreen screen = getPreferenceScreen();
			PreferenceUtil.setCheckboxVal(screen, P_KEY_CRYPTO_ENABLE, otrPolicy.getEnableManual());
			PreferenceUtil.setCheckboxVal(screen, P_KEY_CRYPTO_AUTO, otrPolicy.getEnableAlways());
			PreferenceUtil.setCheckboxVal(screen, P_KEY_CRYPTO_REQUIRE, otrPolicy.getRequireEncryption());

			PreferenceUtil.setCheckboxVal(screen, P_KEY_OMEMO_KEY_BLIND_TRUST,
					mConfig.getBoolean(mConfig.PNAME_OMEMO_KEY_BLIND_TRUST, true));

			SharedPreferences shPrefs = getPreferenceManager().getSharedPreferences();
			shPrefs.registerOnSharedPreferenceChangeListener(this);
		}

		/**
		 * {@inheritDoc}
		 */
		@Override
		public void onStop()
		{
			SharedPreferences shPrefs = getPreferenceManager().getSharedPreferences();
			shPrefs.unregisterOnSharedPreferenceChangeListener(this);
			super.onStop();
		}

		/**
		 * {@inheritDoc}
		 */
		public void onSharedPreferenceChanged(SharedPreferences shPreferences, String key)
		{
			OtrPolicy otrPolicy = OtrActivator.scOtrEngine.getGlobalPolicy();
			if (key.equals(P_KEY_CRYPTO_ENABLE)) {
				otrPolicy.setEnableManual(shPreferences.getBoolean(P_KEY_CRYPTO_ENABLE,
						otrPolicy.getEnableManual()));
			}
			else if (key.equals(P_KEY_CRYPTO_AUTO)) {
				boolean isAutoInit = shPreferences.getBoolean(P_KEY_CRYPTO_AUTO,
						otrPolicy.getEnableAlways());

				otrPolicy.setEnableAlways(isAutoInit);
				OtrActivator.configService.setProperty(OtrActivator.AUTO_INIT_OTR_PROP,
						Boolean.toString(isAutoInit));
			}
			else if (key.equals(P_KEY_CRYPTO_REQUIRE)) {
				boolean isRequired = shPreferences.getBoolean(P_KEY_CRYPTO_REQUIRE,
						otrPolicy.getRequireEncryption());
				otrPolicy.setRequireEncryption(isRequired);
				OtrActivator.configService.setProperty(OtrActivator.OTR_MANDATORY_PROP,
						Boolean.toString(isRequired));
			}
			else if (key.equals(P_KEY_OMEMO_KEY_BLIND_TRUST)) {
				mConfig.setProperty(mConfig.PNAME_OMEMO_KEY_BLIND_TRUST,
						shPreferences.getBoolean(P_KEY_OMEMO_KEY_BLIND_TRUST, true));
			}

			// Store changes immediately
			OtrActivator.scOtrEngine.setGlobalPolicy(otrPolicy);
		}
	}
}
