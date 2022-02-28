/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.android.gui.account.settings;

import static org.atalk.android.gui.account.settings.AccountPreferenceFragment.EXTRA_ACCOUNT_ID;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.KeyEvent;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

import net.java.sip.communicator.service.protocol.AccountID;
import net.java.sip.communicator.service.protocol.ProtocolNames;
import net.java.sip.communicator.util.account.AccountUtils;

import org.atalk.android.aTalkApp;
import org.atalk.android.gui.call.AndroidCallUtil;
import org.atalk.service.osgi.OSGiActivity;

import java.util.List;

/**
 * The activity runs preference fragments for different protocols.
 *
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
public class AccountPreferenceActivity extends OSGiActivity
        implements PreferenceFragmentCompat.OnPreferenceStartFragmentCallback
{
    /**
     * Extra key used to pass the unique user ID using {@link android.content.Intent}
     */
    public static final String EXTRA_USER_ID = "user_id_key";

    /**
     * The {@link AccountPreferenceFragment}
     */
    private AccountPreferenceFragment preferencesFragment;

    private String userUniqueID;

    /**
     * Creates new <code>Intent</code> for starting account preferences activity.
     *
     * @param ctx the context.
     * @param accountID <code>AccountID</code> for which preferences will be opened.
     * @return <code>Intent</code> for starting account preferences activity parametrized with given <code>AccountID</code>.
     */
    public static Intent getIntent(Context ctx, AccountID accountID)
    {
        Intent intent = new Intent(ctx, AccountPreferenceActivity.class);
        intent.putExtra(EXTRA_USER_ID, accountID.getAccountUniqueID());
        return intent;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);

        // Settings cannot be opened during a call
        if (AndroidCallUtil.checkCallInProgress(this))
            return;

        userUniqueID = getIntent().getStringExtra(EXTRA_USER_ID);
        AccountID account = AccountUtils.getAccountIDForUID(userUniqueID);

        // account is null before a new user is properly and successfully registered with the server
        if (account != null) {
            // Gets the registration wizard service for account protocol
            String protocolName = account.getProtocolName();

            if (savedInstanceState == null) {
                preferencesFragment = createPreferencesFragment(userUniqueID, protocolName);

                // Display the fragment as the main content.
                getSupportFragmentManager().beginTransaction()
                        .replace(android.R.id.content, preferencesFragment)
                        .commit();
            }
            else {
                preferencesFragment
                        = (AccountPreferenceFragment) getSupportFragmentManager().findFragmentById(android.R.id.content);
            }
        }
        else {
            aTalkApp.showToastMessage("No valid registered account found: " + userUniqueID);
            finish();
        }
    }

    /**
     * Creates impl preference fragment based on protocol name.
     *
     * @param userUniqueID the account unique ID identifying edited account.
     * @param protocolName protocol name for which the impl fragment will be created.
     * @return impl preference fragment for given <code>userUniqueID</code> and <code>protocolName</code>.
     */
    private AccountPreferenceFragment createPreferencesFragment(String userUniqueID, String protocolName)
    {
        AccountPreferenceFragment preferencesFragment;
        switch (protocolName) {
            case ProtocolNames.SIP:
                preferencesFragment = new SipPreferenceFragment();
                break;
            case ProtocolNames.JABBER:
                preferencesFragment = new JabberPreferenceFragment();
                break;
            default:
                throw new IllegalArgumentException("Unsupported protocol name: " + protocolName);
        }

        Bundle args = new Bundle();
        args.putString(EXTRA_ACCOUNT_ID, userUniqueID);
        preferencesFragment.setArguments(args);
        return preferencesFragment;
    }

    /**
     * Catches the back key and commits the changes if any.
     * {@inheritDoc}
     */
    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event)
    {
        // Catch the back key code and perform commit operation
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            List<Fragment> fragments = getSupportFragmentManager().getFragments();
            if (!fragments.isEmpty()) {
                Fragment fragment = fragments.get(fragments.size() - 1);
                if ((fragment instanceof JabberPreferenceFragment) || (fragment instanceof SipPreferenceFragment)) {
                    preferencesFragment.commitChanges();
                    return true;
                }
            }
        }
        return super.onKeyUp(keyCode, event);
    }

    /**
     *  Called when a preference in the tree rooted at the parent Preference has been clicked.
     *
     * @param caller The caller reference
     * @param pref The click preference to launch
     *
     * @return true always
     */
    @Override
    public boolean onPreferenceStartFragment(PreferenceFragmentCompat caller, Preference pref)
    {
        // Instantiate the new Fragment
        final Bundle args = pref.getExtras();
        args.putString(EXTRA_ACCOUNT_ID, userUniqueID);
        FragmentManager fm = getSupportFragmentManager();
        final Fragment fragment = fm.getFragmentFactory().instantiate(
                getClassLoader(),
                pref.getFragment());
        fragment.setArguments(args);
        fragment.setTargetFragment(caller, 0);

        // Replace the existing Fragment with the new Fragment
        fm.beginTransaction()
                .replace(android.R.id.content, fragment)
                .addToBackStack(null)
                .commit();
        return true;
    }
}
