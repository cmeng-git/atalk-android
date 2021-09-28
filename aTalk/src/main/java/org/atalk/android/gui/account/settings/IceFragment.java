/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.android.gui.account.settings;

import static org.atalk.android.gui.account.settings.AccountPreferenceFragment.EXTRA_ACCOUNT_ID;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult;
import androidx.preference.PreferenceFragmentCompat;

import net.java.sip.communicator.service.protocol.AccountID;
import net.java.sip.communicator.service.protocol.ProtocolProviderService;
import net.java.sip.communicator.service.protocol.jabber.JabberAccountRegistration;
import net.java.sip.communicator.util.account.AccountUtils;

import org.atalk.android.R;
import org.atalk.android.aTalkApp;
import org.atalk.android.gui.settings.util.SummaryMapper;

import timber.log.Timber;

/**
 * The preferences fragment implements for ICE settings.
 *
 * @author Eng Chong Meng
 */
public class IceFragment extends PreferenceFragmentCompat
        implements SharedPreferences.OnSharedPreferenceChangeListener
{
    /**
     * The key identifying edit jingle nodes request
     */
    private static final int EDIT_JINGLE_NODES = 3;

    /**
     * The key identifying edit STUN servers list request
     */
    private static final int EDIT_STUN_TURN = 4;

    // ICE (General)
    private static final String P_KEY_ICE_ENABLED = aTalkApp.getResString(R.string.pref_key_ice_enabled);
    private static final String P_KEY_UPNP_ENABLED = aTalkApp.getResString(R.string.pref_key_upnp_enabled);
    private static final String P_KEY_AUTO_DISCOVER_STUN = aTalkApp.getResString(R.string.pref_key_auto_discover_stun);
    private static final String P_KEY_STUN_TURN_SERVERS = aTalkApp.getResString(R.string.pref_key_stun_turn_servers);

    // Jingle Nodes
    private static final String P_KEY_USE_JINGLE_NODES = aTalkApp.getResString(R.string.pref_key_use_jingle_nodes);
    private static final String P_KEY_AUTO_RELAY_DISCOVERY = aTalkApp.getResString(R.string.pref_key_auto_relay_dicovery);
    private static final String P_KEY_JINGLE_NODES_LIST = aTalkApp.getResString(R.string.pref_key_jingle_node_list);

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
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey)
    {
        setPreferencesFromResource(R.xml.ice_preferences, rootKey);

        String accountID = getArguments().getString(EXTRA_ACCOUNT_ID);
        AccountID account = AccountUtils.getAccountIDForUID(accountID);

        ProtocolProviderService pps = AccountUtils.getRegisteredProviderForAccount(account);
        if (pps == null) {
            Timber.w("No protocol provider registered for %s", account);
            return;
        }
        mActivity = (AccountPreferenceActivity) getActivity();
        jbrReg = JabberPreferenceFragment.jbrReg;

        shPrefs = getPreferenceManager().getSharedPreferences();
        shPrefs.registerOnSharedPreferenceChangeListener(this);
        shPrefs.registerOnSharedPreferenceChangeListener(summaryMapper);

        findPreference(P_KEY_STUN_TURN_SERVERS).setOnPreferenceClickListener(pref -> {
            getStunServerList();
            return true;
        });

        findPreference(P_KEY_JINGLE_NODES_LIST).setOnPreferenceClickListener(pref -> {
            getJingleNodeList();
            return true;
        });

        initPreferences();
    }

    /**
     * {@inheritDoc}
     */
    protected void initPreferences()
    {
        // ICE options
        SharedPreferences.Editor editor = shPrefs.edit();
        editor.putBoolean(P_KEY_ICE_ENABLED, jbrReg.isUseIce());
        editor.putBoolean(P_KEY_UPNP_ENABLED, jbrReg.isUseUPNP());
        editor.putBoolean(P_KEY_AUTO_DISCOVER_STUN, jbrReg.isAutoDiscoverStun());

        // Jingle Nodes
        editor.putBoolean(P_KEY_USE_JINGLE_NODES, jbrReg.isUseJingleNodes());
        editor.putBoolean(P_KEY_AUTO_RELAY_DISCOVERY, jbrReg.isAutoDiscoverJingleNodes());

        editor.apply();
    }

    /**
     * Starts {@link ServerListActivity} in order to edit STUN servers list
     */
    private void getStunServerList()
    {
        Intent intent = new Intent(mActivity, ServerListActivity.class);
        intent.putExtra(ServerListActivity.JABBER_REGISTRATION_KEY, jbrReg);
        intent.putExtra(ServerListActivity.REQUEST_CODE_KEY, ServerListActivity.REQUEST_EDIT_STUN_TURN);
        getStunServes.launch(intent);
    }

    /**
     * Stores values changed by STUN nodes edit activities.
     */
    ActivityResultLauncher<Intent> getStunServes = registerForActivityResult(new StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK) {
                    Intent data = result.getData();

                    // Gets edited STUN servers list
                    JabberAccountRegistration serialized = (JabberAccountRegistration)
                            data.getSerializableExtra(ServerListActivity.JABBER_REGISTRATION_KEY);

                    jbrReg.getAdditionalStunServers().clear();
                    jbrReg.getAdditionalStunServers().addAll(serialized.getAdditionalStunServers());
                    JabberPreferenceFragment.setUncommittedChanges();
                }
            }
    );

    /**
     * Start {@link ServerListActivity} in order to edit Jingle Nodes list
     */
    private void getJingleNodeList()
    {
        Intent intent = new Intent(mActivity, ServerListActivity.class);
        intent.putExtra(ServerListActivity.JABBER_REGISTRATION_KEY, jbrReg);
        intent.putExtra(ServerListActivity.REQUEST_CODE_KEY, ServerListActivity.REQUEST_EDIT_JINGLE_NODES);
        getJingleNodes.launch(intent);
    }

    /**
     * Stores values changed by Jingle nodes edit activities.
     */
    ActivityResultLauncher<Intent> getJingleNodes = registerForActivityResult(new StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK) {
                    // Gets edited Jingle Nodes list
                    Intent data = result.getData();
                    JabberAccountRegistration serialized = (JabberAccountRegistration)
                            data.getSerializableExtra(ServerListActivity.JABBER_REGISTRATION_KEY);

                    jbrReg.getAdditionalJingleNodes().clear();
                    jbrReg.getAdditionalJingleNodes().addAll(serialized.getAdditionalJingleNodes());
                    JabberPreferenceFragment.setUncommittedChanges();
                }
            }
    );

    /**
     * {@inheritDoc}
     */
    public void onSharedPreferenceChanged(SharedPreferences shPreferences, String key)
    {
        // Check to ensure a valid key before proceed
        if (findPreference(key) == null)
            return;

        JabberPreferenceFragment.setUncommittedChanges();
        if (key.equals(P_KEY_ICE_ENABLED)) {
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
    }
}

