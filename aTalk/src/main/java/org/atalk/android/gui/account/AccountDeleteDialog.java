/*
 * aTalk, android VoIP and Instant Messaging client
 * Copyright 2014~2024 Eng Chong Meng
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.atalk.android.gui.account;

import android.content.Context;
import android.os.Bundle;
import android.widget.CheckBox;

import net.java.sip.communicator.impl.protocol.jabber.ProtocolProviderServiceJabberImpl;
import net.java.sip.communicator.service.protocol.AccountID;
import net.java.sip.communicator.service.protocol.ProtocolProviderFactory;
import net.java.sip.communicator.util.account.AccountUtils;

import org.atalk.android.R;
import org.atalk.android.gui.AndroidGUIActivator;
import org.atalk.android.gui.dialogs.CustomDialogCbox;
import org.atalk.android.gui.dialogs.DialogActivity;
import org.atalk.crypto.omemo.SQLiteOmemoStore;
import org.jivesoftware.smackx.omemo.OmemoService;

/**
 * Helper class that produces "account delete dialog". It asks the user for account removal
 * confirmation and finally removes the account. Interface <code>OnAccountRemovedListener</code> is
 * used to notify about account removal which will not be fired if the user cancels the dialog.
 *
 * @author Eng Chong Meng
 */
public class AccountDeleteDialog {
    public static void create(Context ctx, final Account account, final OnAccountRemovedListener listener) {
        String title = ctx.getString(R.string.service_gui_REMOVE_ACCOUNT);

        String message = ctx.getString(R.string.service_gui_REMOVE_ACCOUNT_MESSAGE, account.getAccountID());
        String cbMessage = ctx.getString(R.string.account_delete_on_server);
        String btnText = ctx.getString(R.string.service_gui_DELETE);

        Bundle args = new Bundle();
        args.putString(CustomDialogCbox.ARG_MESSAGE, message);
        args.putString(CustomDialogCbox.ARG_CB_MESSAGE, cbMessage);
        args.putBoolean(CustomDialogCbox.ARG_CB_CHECK, false);
        args.putBoolean(CustomDialogCbox.ARG_CB_ENABLE, true);

        // Displays the history delete dialog and waits for user confirmation
        DialogActivity.showCustomDialog(ctx, title, CustomDialogCbox.class.getName(), args, btnText,
                new DialogActivity.DialogListener() {
                    public boolean onConfirmClicked(DialogActivity dialog) {
                        CheckBox cbAccountDelete = dialog.findViewById(R.id.cb_option);
                        boolean accountDelete = cbAccountDelete.isChecked();
                        onRemoveClicked(account, accountDelete, listener);
                        return true;
                    }

                    @Override
                    public void onDialogCancelled(DialogActivity dialog) {
                    }
                }, null);
    }

    private static void onRemoveClicked(final Account account, boolean serverAccountDelete, OnAccountRemovedListener l) {
        // Fix "network access on the main thread"
        final Thread removeAccountThread = new Thread() {
            @Override
            public void run() {
                // cleanup omemo data for the deleted user account
                AccountID accountId = account.getAccountID();
                SQLiteOmemoStore omemoStore = (SQLiteOmemoStore) OmemoService.getInstance().getOmemoStoreBackend();
                omemoStore.purgeUserOmemoData(accountId);

                // purge persistent storage must happen before removeAccount action
                AccountsListActivity.removeAccountPersistentStore(accountId);

                // Delete account on server
                if (serverAccountDelete) {
                    ProtocolProviderServiceJabberImpl pps = (ProtocolProviderServiceJabberImpl) account.getProtocolProvider();
                    pps.deleteAccountOnServer();
                }

                // Update account status
                removeAccount(accountId);
            }
        };
        removeAccountThread.start();

        try {
            // Simply block UI thread as it shouldn't take too long to uninstall; ANR from field - wait on 3S timeout
            removeAccountThread.join(3000);
            // Notify about results
            l.onAccountRemoved(account);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Remove all the properties of the given <code>Account</code> from the accountProperties database.
     * Note: accountUuid without any suffix as propertyName will remove all the properties in
     * the accountProperties for the specified accountUuid
     *
     * @param accountId the accountId that will be uninstalled from the system.
     */
    private static void removeAccount(AccountID accountId) {
        ProtocolProviderFactory providerFactory = AccountUtils.getProtocolProviderFactory(accountId.getProtocolName());
        String accountUuid = accountId.getAccountUuid();
        AndroidGUIActivator.getConfigurationService().setProperty(accountUuid, null);

        boolean isUninstalled = providerFactory.uninstallAccount(accountId);
        if (!isUninstalled)
            throw new RuntimeException("Failed to uninstall account");
    }

    /**
     * Interfaces used to notify about account removal which happens after the user confirms the action.
     */
    interface OnAccountRemovedListener {
        /**
         * Fired after <code>Account</code> is removed from the system which happens after user
         * confirms the action. Will not be fired when user dismisses the dialog.
         *
         * @param account removed <code>Account</code>.
         */
        void onAccountRemoved(Account account);
    }

}
