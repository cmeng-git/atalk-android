/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.android.gui.account;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;

import net.java.sip.communicator.service.protocol.AccountID;
import net.java.sip.communicator.service.protocol.ProtocolProviderFactory;
import net.java.sip.communicator.util.account.AccountUtils;

import org.atalk.android.R;
import org.atalk.crypto.omemo.SQLiteOmemoStore;
import org.jivesoftware.smackx.omemo.OmemoService;

import static net.java.sip.communicator.plugin.otr.OtrActivator.configService;

/**
 * Helper class that produces "remove account dialog". It asks the user for account removal
 * confirmation and finally removes the account. Interface <tt>OnAccountRemovedListener</tt> is
 * used to notify about account removal which will not be fired if the user cancels the dialog.
 *
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
public class RemoveAccountDialog
{
    public static AlertDialog create(Context ctx, final Account account, final OnAccountRemovedListener listener)
    {
        AlertDialog.Builder alert = new AlertDialog.Builder(ctx);
        return alert.setTitle(R.string.service_gui_REMOVE_ACCOUNT)
                .setMessage(ctx.getString(R.string.service_gui_REMOVE_ACCOUNT_MESSAGE, account.getAccountID()))
                .setPositiveButton(R.string.service_gui_YES, (dialog, which) -> onRemoveClicked(dialog, account, listener))
                .setNegativeButton(R.string.service_gui_NO, (dialog, which) -> dialog.dismiss()).create();
    }

    private static void onRemoveClicked(DialogInterface dialog, final Account account, OnAccountRemovedListener l)
    {
        // Fix "network on main thread"
        final Thread removeAccountThread = new Thread()
        {
            @Override
            public void run()
            {
                // cleanup omemo data for the deleted user account
                AccountID accountId = account.getAccountID();
                SQLiteOmemoStore omemoStore = (SQLiteOmemoStore) OmemoService.getInstance().getOmemoStoreBackend();
                omemoStore.purgeUserOmemoData(accountId);

                // purge persistent storage must happen before removeAccount action
                AccountsListActivity.removeAccountPersistentStore(accountId);
                removeAccount(accountId);
            }
        };
        removeAccountThread.start();

        try {
            // Simply block UI thread as it shouldn't take too long to uninstall; ANR from field - wait on 3S timeout
            removeAccountThread.join(3000);
            // Notify about results
            l.onAccountRemoved(account);
            dialog.dismiss();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Remove all the properties of the given <tt>Account</tt> from the accountProperties database.
     * Note: accountUuid without any suffix as propertyName will remove all the properties in
     * the accountProperties for the specified accountUuid
     *
     * @param accountId the accountId that will be uninstalled from the system.
     */
    private static void removeAccount(AccountID accountId)
    {
        ProtocolProviderFactory providerFactory = AccountUtils.getProtocolProviderFactory(accountId.getProtocolName());
        String accountUuid = accountId.getAccountUuid();
        configService.setProperty(accountUuid, null);

        boolean isUninstalled = providerFactory.uninstallAccount(accountId);
        if (!isUninstalled)
            throw new RuntimeException("Failed to uninstall account");
    }

    /**
     * Interfaces used to notify about account removal which happens after the user confirms the action.
     */
    interface OnAccountRemovedListener
    {
        /**
         * Fired after <tt>Account</tt> is removed from the system which happens after user
         * confirms the action. Will not be fired when user dismisses the dialog.
         *
         * @param account removed <tt>Account</tt>.
         */
        void onAccountRemoved(Account account);
    }

}
