/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.android.gui.account;

import android.content.Intent;
import android.os.Bundle;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListView;
import android.widget.Toast;
import android.widget.ToggleButton;

import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.FragmentTransaction;

import java.io.File;
import java.io.IOException;
import java.util.Collection;

import net.java.sip.communicator.impl.protocol.jabber.ProtocolProviderServiceJabberImpl;
import net.java.sip.communicator.service.protocol.AccountID;
import net.java.sip.communicator.service.protocol.AccountManager;
import net.java.sip.communicator.service.protocol.OperationFailedException;
import net.java.sip.communicator.service.protocol.ProtocolProviderService;
import net.java.sip.communicator.util.ConfigurationUtils;
import net.java.sip.communicator.util.ServiceUtils;
import net.java.sip.communicator.util.account.AccountUtils;

import org.atalk.android.BaseActivity;
import org.atalk.android.R;
import org.atalk.android.aTalkApp;
import org.atalk.android.gui.AppGUIActivator;
import org.atalk.android.gui.account.settings.AccountPreferenceActivity;
import org.atalk.android.gui.contactlist.AddGroupDialog;
import org.atalk.android.gui.dialogs.DialogActivity;
import org.atalk.android.gui.dialogs.ProgressDialog;
import org.atalk.android.plugin.certconfig.TLS_Configuration;
import org.atalk.persistance.FileBackend;
import org.atalk.persistance.ServerPersistentStoresRefreshDialog;
import org.jivesoftware.smackx.avatar.vcardavatar.VCardAvatarManager;
import org.jxmpp.jid.BareJid;
import org.jxmpp.stringprep.XmppStringprepException;

import timber.log.Timber;

/**
 * The activity display list of currently stored accounts showing the associated protocol and current status.
 *
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
public class AccountsListActivity extends BaseActivity {
    /**
     * The list adapter for accounts
     */
    private AccountStatusListAdapter listAdapter;
    /**
     * The {@link AccountManager} used to operate on {@link AccountID}s
     */
    private AccountManager accountManager;

    /**
     * Stores clicked account in member field, as context info is not available. That's because account
     * list contains on/off buttons and that prevents from "normal" list item clicks / long clicks handling.
     */
    private Account clickedAccount;

    /**
     * Keeps track of displayed "in progress" dialog during account registration.
     */
    private static long pDialogId;

    /**
     * Keeps track of thread used to register accounts and prevents from starting multiple at one time.
     */
    private static AccountEnableThread accEnableThread;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setMainTitle(R.string.account);

        if (AppGUIActivator.bundleContext == null) {
            // No OSGi Exists
            Timber.e("OSGi not initialized");
            finish();
            return;
        }
        setContentView(R.layout.account_list);
        this.accountManager = ServiceUtils.getService(AppGUIActivator.bundleContext, AccountManager.class);
    }

    @Override
    protected void onResume() {
        super.onResume();

        // Need to refresh the list each time in case account might be removed in other Activity.
        // Also it can't be removed on "unregistered" event, because on/off buttons will cause the account to disappear
        accountsInit();
    }

    @Override
    protected void onDestroy() {
        // Unregisters presence status listeners
        if (listAdapter != null) {
            listAdapter.deinitStatusListeners();
        }
        super.onDestroy();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        getMenuInflater().inflate(R.menu.account_settings_menu, menu);
        menu.findItem(R.id.add_group).setVisible(!ConfigurationUtils.isCreateGroupDisabled());
        return true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle item selection
        switch (item.getItemId()) {
            case R.id.add_account:
                Intent intent = new Intent(this, AccountLoginActivity.class);
                startActivity(intent);
                return true;

            case R.id.add_group:
                AddGroupDialog.showCreateGroupDialog(this, null);
                return true;

            case R.id.TLS_Configuration:
                TLS_Configuration tlsConfiguration = new TLS_Configuration();
                FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
                ft.addToBackStack(null);
                ft.replace(android.R.id.content, tlsConfiguration).commit();
                return true;

            case R.id.refresh_database:
                new ServerPersistentStoresRefreshDialog().show(this);
                return true;

            default:
                return super.onOptionsItemSelected(item);
        }
    }

    /**
     * Initializes the accounts table.
     */
    private void accountsInit() {
        // Create accounts array
        Collection<AccountID> accountIDCollection = AccountUtils.getStoredAccounts();

        // Create account list adapter
        listAdapter = new AccountStatusListAdapter(accountIDCollection);

        // Puts the adapter into accounts ListView
        ListView lv = findViewById(R.id.accountListView);
        lv.setAdapter(listAdapter);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);
        getMenuInflater().inflate(R.menu.account_ctx_menu, menu);

        // Set menu title
        menu.setHeaderTitle(clickedAccount.getAccountName());

        // No access for account settings or info if not registered
        MenuItem accountSettings = menu.findItem(R.id.account_settings);
        accountSettings.setVisible(clickedAccount.getProtocolProvider() != null);

        MenuItem accountInfo = menu.findItem(R.id.account_info);
        accountInfo.setVisible(clickedAccount.getProtocolProvider() != null);
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.remove:
                AccountDeleteDialog.create(this, clickedAccount, account -> listAdapter.remove(account));
                return true;

            case R.id.account_settings:
                startPreferenceActivity(clickedAccount);
                return true;

            case R.id.account_info:
                startPresenceActivity(clickedAccount);
                return true;

            case R.id.account_cancel:
                return true;

            default:
                return super.onContextItemSelected(item);
        }
    }

    /**
     * Starts the {@link AccountPreferenceActivity} for clicked {@link Account}
     *
     * @param account the <code>Account</code> for which preference settings will be opened.
     */
    private void startPreferenceActivity(Account account) {
        Intent preferences = AccountPreferenceActivity.getIntent(this, account.getAccountID());
        startActivity(preferences);
    }

    /**
     * Starts the {@link AccountInfoPresenceActivity} for clicked {@link Account}
     *
     * @param account the <code>Account</code> for which settings will be opened.
     */
    private void startPresenceActivity(Account account) {
        Intent statusIntent = new Intent(this, AccountInfoPresenceActivity.class);
        statusIntent.putExtra(AccountInfoPresenceActivity.INTENT_ACCOUNT_ID,
                account.getAccountID().getAccountUid());
        startActivity(statusIntent);
    }

    /**
     * Removes the account persistent storage from the device
     *
     * @param accountId the {@link AccountID} for whom the persistent to be purged from the device
     */
    public static void removeAccountPersistentStore(AccountID accountId) {
        ProtocolProviderService pps = accountId.getProtocolProvider();
        if (pps instanceof ProtocolProviderServiceJabberImpl) {
            ProtocolProviderServiceJabberImpl jabberProvider = (ProtocolProviderServiceJabberImpl) pps;

            // Purge avatarHash and avatarImages of all contacts belong to the account roster
            BareJid userJid = accountId.getEntityBareJid();
            try {
                VCardAvatarManager.clearPersistentStorage(userJid);
            } catch (XmppStringprepException e) {
                Timber.e("Failed to purge store for: %s", R.string.refresh_store_avatar);
            }

            File rosterStoreDirectory = jabberProvider.getRosterStoreDirectory();
            try {
                if (rosterStoreDirectory != null)
                    FileBackend.deleteRecursive(rosterStoreDirectory);
            } catch (IOException e) {
                Timber.e("Failed to purge store for: %s", R.string.refresh_store_roster);
            }

            // Account in unRegistering so discoveryInfoManager == null
            // ServiceDiscoveryManager discoveryInfoManager = jabberProvider.getDiscoveryManager();
            // File discoInfoStoreDirectory = discoveryInfoManager.getDiscoInfoPersistentStore();
            File discoInfoStoreDirectory = new File(aTalkApp.getInstance().getFilesDir()
                    + "/discoInfoStore_" + userJid);
            try {
                FileBackend.deleteRecursive(discoInfoStoreDirectory);
            } catch (IOException e) {
                Timber.e("Failed to purge store for: %s", R.string.refresh_store_disc_info);
            }
        }
    }

    /**
     * Class responsible for creating list row Views
     */
    class AccountStatusListAdapter extends AccountsListAdapter {
        /**
         * Toast instance
         */
        private Toast offlineToast;

        /**
         * Creates new instance of {@link AccountStatusListAdapter}
         *
         * @param accounts array of currently stored accounts
         */
        AccountStatusListAdapter(Collection<AccountID> accounts) {
            super(AccountsListActivity.this, R.layout.account_list_row, -1, accounts, false);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        protected View getView(boolean isDropDown, final Account account, ViewGroup parent, LayoutInflater inflater) {
            // Creates the list view
            View rowView = super.getView(isDropDown, account, parent, inflater);

            rowView.setClickable(true);
            rowView.setOnClickListener(v -> {
                // Start only for registered accounts
                if (account.getProtocolProvider() != null) {
                    startPreferenceActivity(account);
                }
                else {
                    String msg = getString(R.string.account_unregistered, account.getAccountName());
                    if (offlineToast == null) {
                        offlineToast = Toast.makeText(AccountsListActivity.this, msg, Toast.LENGTH_SHORT);
                    }
                    else {
                        offlineToast.setText(msg);
                    }
                    offlineToast.show();
                }
            });
            rowView.setOnLongClickListener(v -> {
                registerForContextMenu(v);
                clickedAccount = account;
                openContextMenu(v);
                return true;
            });

            ToggleButton button = rowView.findViewById(R.id.accountToggleButton);
            button.setChecked(account.isEnabled());

            button.setOnCheckedChangeListener((compoundButton, enable) -> {
                if (accEnableThread != null) {
                    Timber.e("Ongoing operation in progress");
                    return;
                }
                Timber.d("Toggle %s -> %s", account, enable);

                // Prevents from switching the state after key pressed. Refresh will be
                // triggered by the thread when it finishes the operation.
                compoundButton.setChecked(account.isEnabled());

                accEnableThread = new AccountEnableThread(account.getAccountID(), enable);
                String message = enable ? getString(R.string.connecting_, account.getAccountName())
                        : getString(R.string.disconnecting_, account.getAccountName());
                pDialogId = ProgressDialog.show(AccountsListActivity.this,
                        getString(R.string.info), message, false);
                accEnableThread.start();
            });
            return rowView;
        }
    }

    /**
     * The thread that runs enable/disable operations
     */
    class AccountEnableThread extends Thread {
        /**
         * The {@link AccountID} that will be enabled or disabled
         */
        private final AccountID account;
        /**
         * Flag decides whether account shall be disabled or enabled
         */
        private final boolean enable;

        /**
         * Creates new instance of {@link AccountEnableThread}
         *
         * @param account the {@link AccountID} that will be enabled or disabled
         * @param enable flag indicates if this is enable or disable operation
         */
        AccountEnableThread(AccountID account, boolean enable) {
            this.account = account;
            this.enable = enable;
        }

        @Override
        public void run() {
            try {
                if (enable)
                    accountManager.loadAccount(account);
                else {
                    accountManager.unloadAccount(account);
                }
            } catch (OperationFailedException e) {
                String message = "Failed to " + (enable ? "load" : "unload") + " " + account;
                runOnUiThread(() ->
                        new AlertDialog.Builder(AccountsListActivity.this)
                                .setTitle(R.string.error)
                                .setMessage(message)
                                .setPositiveButton(R.string.ok, null)
                                .show());
                Timber.e("Account de/activate Exception: %s", e.getMessage());
            } finally {
                if (DialogActivity.waitForDialogOpened(pDialogId)) {
                    ProgressDialog.dismiss(pDialogId);
                }
                else {
                    Timber.e("Failed to wait for the dialog: %s", pDialogId);
                }
                accEnableThread = null;
            }
        }
    }
}
