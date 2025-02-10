/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.android.gui.account.settings;

import android.content.SharedPreferences;
import android.os.Bundle;

import net.java.sip.communicator.service.protocol.AccountID;
import net.java.sip.communicator.service.protocol.ProtocolProviderService;
import net.java.sip.communicator.service.protocol.jabber.JabberAccountRegistration;
import net.java.sip.communicator.util.account.AccountUtils;

import org.atalk.android.R;
import org.atalk.android.gui.settings.BasePreferenceFragment;
import org.atalk.android.gui.settings.util.SummaryMapper;

import timber.log.Timber;

/**
 * The preferences fragment implements for Telephony settings.
 *
 * @author Eng Chong Meng
 */
public class TelephonySettingFragment extends BasePreferenceFragment
        implements SharedPreferences.OnSharedPreferenceChangeListener {
    // Telephony
    private static final String P_KEY_CALLING_DISABLED = "pref_key_calling_disabled";
    private static final String P_KEY_OVERRIDE_PHONE_SUFFIX = "pref_key_override_phone_suffix";
    private static final String P_KEY_TEL_BYPASS_GTALK_CAPS = "pref_key_tele_bypass_gtalk_caps";

    /*
     * A new instance of AccountID and is not the same as accountID.
     * Defined as static, otherwise it may get clear onActivityResult - on some android devices
     */
    private static JabberAccountRegistration jbrReg;
    protected AccountPreferenceActivity mActivity;

    protected SharedPreferences shPrefs;

    /**
     * Summary mapper used to display preferences values as summaries.
     */
    private final SummaryMapper summaryMapper = new SummaryMapper();

    /**
     * {@inheritDoc}
     */
    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        String accountID = getArguments().getString(AccountPreferenceFragment.EXTRA_ACCOUNT_ID);
        AccountID account = AccountUtils.getAccountIDForUID(accountID);
        ProtocolProviderService pps = AccountUtils.getRegisteredProviderForAccount(account);
        if (pps == null) {
            Timber.w("No protocol provider registered for %s", account);
            return;
        }

        initPreferences();
        setPreferencesFromResource(R.xml.telephony_preference, rootKey);
        setPrefTitle(R.string.jbr_telephony_voip_pbx);

        shPrefs.registerOnSharedPreferenceChangeListener(this);
        shPrefs.registerOnSharedPreferenceChangeListener(summaryMapper);

        mapSummaries(summaryMapper);
    }

    /**
     * {@inheritDoc}
     */
    protected void initPreferences() {
        jbrReg = JabberPreferenceFragment.jbrReg;
        shPrefs = getPreferenceManager().getSharedPreferences();
        SharedPreferences.Editor editor = shPrefs.edit();

        // Telephony
        editor.putBoolean(P_KEY_CALLING_DISABLED, jbrReg.isJingleDisabled());
        editor.putString(P_KEY_OVERRIDE_PHONE_SUFFIX, jbrReg.getOverridePhoneSuffix());
        editor.putString(P_KEY_TEL_BYPASS_GTALK_CAPS, jbrReg.getTelephonyDomainBypassCaps());

        editor.apply();
    }

    /**
     * {@inheritDoc}
     */
    protected void mapSummaries(SummaryMapper summaryMapper) {
        String emptyStr = getString(R.string.settings_not_set);

        // Telephony
        summaryMapper.includePreference(findPreference(P_KEY_OVERRIDE_PHONE_SUFFIX), emptyStr);
        summaryMapper.includePreference(findPreference(P_KEY_TEL_BYPASS_GTALK_CAPS), emptyStr);
    }

    /**
     * {@inheritDoc}
     */
    public void onSharedPreferenceChanged(SharedPreferences shPreferences, String key) {
        // Check to ensure a valid key before proceed
        if (findPreference(key) == null)
            return;

        JabberPreferenceFragment.setUncommittedChanges();
        switch (key) {
            case P_KEY_CALLING_DISABLED:
                jbrReg.setDisableJingle(shPrefs.getBoolean(P_KEY_CALLING_DISABLED, false));
                break;
            case P_KEY_OVERRIDE_PHONE_SUFFIX:
                jbrReg.setOverridePhoneSuffix(shPrefs.getString(P_KEY_OVERRIDE_PHONE_SUFFIX, null));
                break;
            case P_KEY_TEL_BYPASS_GTALK_CAPS:
                jbrReg.setTelephonyDomainBypassCaps(shPrefs.getString(P_KEY_TEL_BYPASS_GTALK_CAPS, null));
                break;
        }
    }
}

