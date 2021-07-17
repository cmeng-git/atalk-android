/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.android.gui.account.settings;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.KeyEvent;

import net.java.sip.communicator.service.protocol.AccountID;
import net.java.sip.communicator.service.protocol.ProtocolNames;
import net.java.sip.communicator.util.account.AccountUtils;

import org.atalk.android.aTalkApp;
import org.atalk.android.gui.call.AndroidCallUtil;
import org.atalk.service.osgi.OSGiActivity;

/**
 * The activity runs preference fragments for different protocols.
 *
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
public class AccountPreferenceActivity extends OSGiActivity
{
    /**
     * Extra key used to pass the unique user ID using {@link android.content.Intent}
     */
    public static final String EXTRA_USER_ID = "user_id_key";

    /**
     * The {@link AccountPreferenceFragment}
     */
    private AccountPreferenceFragment preferencesFragment;

    /**
     * The {@link Thread} which runs the commit operation in background
     */
    private Thread commitThread;

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

        String userUniqueID = getIntent().getStringExtra(EXTRA_USER_ID);
        AccountID account = AccountUtils.getAccountIDForUID(userUniqueID);

        // account is null before a new user is properly and successfully registered with the server
        if (account != null) {
            // Gets the registration wizard service for account protocol
            String protocolName = account.getProtocolName();

            if (savedInstanceState == null) {
                preferencesFragment = createPreferencesFragment(userUniqueID, protocolName);

                // Display the fragment as the main content.
                getFragmentManager().beginTransaction()
                        .replace(android.R.id.content, preferencesFragment)
                        .commit();
            }
            else {
                preferencesFragment
                        = (AccountPreferenceFragment) getFragmentManager().findFragmentById(android.R.id.content);
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
     * @return impl preference fragment for given <tt>userUniqueID</tt> and <tt>protocolName</tt>.
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
        args.putString(AccountPreferenceFragment.EXTRA_ACCOUNT_ID, userUniqueID);
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
            if (commitThread != null)
                return true;

            this.commitThread = new Thread(() -> {
                preferencesFragment.commitChanges();
                finish();
            });
            commitThread.start();
            return true;
        }
        return super.onKeyUp(keyCode, event);
    }

    /**
     * Creates new <tt>Intent</tt> for starting account preferences activity.
     *
     * @param ctx the context.
     * @param accountID <tt>AccountID</tt> for which preferences will be opened.
     * @return <tt>Intent</tt> for starting account preferences activity parametrized with given <tt>AccountID</tt>.
     */
    public static Intent getIntent(Context ctx, AccountID accountID)
    {
        Intent intent = new Intent(ctx, AccountPreferenceActivity.class);
        intent.putExtra(AccountPreferenceActivity.EXTRA_USER_ID, accountID.getAccountUniqueID());
        return intent;
    }
}
