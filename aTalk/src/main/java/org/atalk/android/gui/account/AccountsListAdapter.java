/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.android.gui.account;

import android.app.Activity;
import android.graphics.drawable.Drawable;
import android.view.*;
import android.widget.*;

import net.java.sip.communicator.impl.configuration.ConfigurationActivator;
import net.java.sip.communicator.service.protocol.AccountID;
import net.java.sip.communicator.service.protocol.ProtocolProviderService;

import org.atalk.android.R;
import org.atalk.android.gui.util.CollectionAdapter;
import org.atalk.android.gui.util.event.EventListener;
import org.atalk.service.osgi.OSGiActivity;
import org.osgi.framework.*;

import java.util.ArrayList;
import java.util.Collection;

import timber.log.Timber;

/**
 * This is a convenience class which implements an {@link Adapter} interface to put the list of
 * {@link Account}s into Android widgets.
 *
 * The {@link View}s for each row are created from the layout resource id given in constructor.
 * This view should contain: <br/>
 * - <tt>R.id.accountName</tt> for the account name text ({@link TextView}) <br/>
 * - <tt>R.id.accountProtoIcon</tt> for the protocol icon of type ({@link ImageView}) <br/>
 * - <tt>R.id.accountStatusIcon</tt> for the presence status icon ({@link ImageView}) <br/>
 * - <tt>R.id.accountStatus</tt> for the presence status name ({@link TextView}) <br/>
 * It implements {@link EventListener} to refresh the list on any changes to the {@link Account}.
 *
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
public class AccountsListAdapter extends CollectionAdapter<Account>
        implements EventListener<AccountEvent>, ServiceListener
{
    /**
     * The {@link View} resources ID describing list's row
     */
    private final int listRowResourceID;

    /**
     * The {@link View} resources ID describing list's row
     */
    private final int dropDownRowResourceID;

    /**
     * The {@link BundleContext} of parent {@link OSGiActivity}
     */
    private final BundleContext bundleContext;
    /**
     * The flag indicates whether disabled accounts should be filtered out from the list
     */
    private final boolean filterDisabledAccounts;

    /**
     * Creates new instance of {@link AccountsListAdapter}
     *
     * @param parent the {@link Activity} running this adapter
     * @param accounts collection of accounts that will be displayed
     * @param listRowResourceID the layout resource ID see {@link AccountsListAdapter} for detailed description
     * @param filterDisabledAccounts flag indicates if disabled accounts should be filtered out from the list
     */
    public AccountsListAdapter(Activity parent, int listRowResourceID, int dropDownRowResourceID,
            Collection<AccountID> accounts, boolean filterDisabledAccounts)
    {
        super(parent);
        this.filterDisabledAccounts = filterDisabledAccounts;

        this.listRowResourceID = listRowResourceID;
        this.dropDownRowResourceID = dropDownRowResourceID;

        this.bundleContext = ConfigurationActivator.bundleContext;
        bundleContext.addServiceListener(this);
        initAccounts(accounts);
    }

    /**
     * Initialize the list and filters out disabled accounts if necessary.
     *
     * @param collection set of {@link AccountID} that will be displayed
     */
    private void initAccounts(Collection<AccountID> collection)
    {
        ArrayList<Account> accounts = new ArrayList<>();
        for (AccountID acc : collection) {
            Account account = new Account(acc, bundleContext, getParentActivity());
            if (filterDisabledAccounts && !account.isEnabled())
                continue;

            // Skip hidden accounts
            if (acc.isHidden())
                continue;

            account.addAccountEventListener(this);
            accounts.add(account);
        }
        setList(accounts);
    }

    public void serviceChanged(ServiceEvent event)
    {
        // if the event is caused by a bundle being stopped, we don't want to know
        if (event.getServiceReference().getBundle().getState() == Bundle.STOPPING) {
            return;
        }

        // we don't care if the source service is not a protocol provider
        Object sourceService = bundleContext.getService(event.getServiceReference());
        if (!(sourceService instanceof ProtocolProviderService)) {
            return;
        }

        ProtocolProviderService protocolProvider = (ProtocolProviderService) sourceService;

        // Add or remove the protocol provider from our accounts list.
        if (event.getType() == ServiceEvent.REGISTERED) {
            Account acc = findAccountID(protocolProvider.getAccountID());
            if (acc == null) {
                addAccount(new Account(protocolProvider.getAccountID(), bundleContext,
                        getParentActivity().getBaseContext()));
            }
            // Register for account events listener if account exists on this list
            else {
                acc.addAccountEventListener(this);
            }
        }
        else if (event.getType() == ServiceEvent.UNREGISTERING) {
            Account account = findAccountID(protocolProvider.getAccountID());
            // Remove enabled account if exist
            if (account != null && account.isEnabled()) {
                removeAccount(account);
            }
        }
    }

    /**
     * Unregisters status update listeners for accounts
     */
    void deinitStatusListeners()
    {
        for (int accIdx = 0; accIdx < getCount(); accIdx++) {
            Account account = getObject(accIdx);
            account.destroy();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected View getView(boolean isDropDown, Account account, ViewGroup parent, LayoutInflater inflater)
    {
        int rowResID = listRowResourceID;

        if (isDropDown && dropDownRowResourceID != -1) {
            rowResID = dropDownRowResourceID;
        }

        View statusItem = inflater.inflate(rowResID, parent, false);
        TextView accountName = statusItem.findViewById(R.id.protocolProvider);
        ImageView accountProtocol = statusItem.findViewById(R.id.accountProtoIcon);
        ImageView statusIconView = statusItem.findViewById(R.id.accountStatusIcon);
        TextView accountStatus = statusItem.findViewById(R.id.accountStatus);

        // Sets account's properties
        if (accountName != null)
            accountName.setText(account.getAccountName());

        if (accountProtocol != null) {
            Drawable protoIcon = account.getProtocolIcon();
            if (protoIcon != null) {
                accountProtocol.setImageDrawable(protoIcon);
            }
        }

        if (accountStatus != null)
            accountStatus.setText(account.getStatusName());

        if (statusIconView != null) {
            Drawable statusIcon = account.getStatusIcon();
            if (statusIcon != null) {
                statusIconView.setImageDrawable(statusIcon);
            }
        }
        return statusItem;
    }

    /**
     * Check if given <tt>account</tt> exists on the list
     *
     * @param account {@link AccountID} that has to be found on the list
     * @return <tt>true</tt> if account is on the list
     */
    private Account findAccountID(AccountID account)
    {
        for (int i = 0; i < getCount(); i++) {
            Account acc = getObject(i);
            if (acc.getAccountID().equals(account))
                return acc;
        }
        return null;
    }

    /**
     * Adds new account to the list
     *
     * @param account {@link Account} that will be added to the list
     */
    private void addAccount(Account account)
    {
        if (filterDisabledAccounts && !account.isEnabled())
            return;

        if (account.getAccountID().isHidden())
            return;

        Timber.d("Account added: %s", account.getUserID());
        add(account);
        account.addAccountEventListener(this);
    }

    /**
     * Removes the account from the list
     *
     * @param account the {@link Account} that will be removed from the list
     */
    private void removeAccount(Account account)
    {
        if (account != null) {
            Timber.d("Account removed: %s", account.getUserID());
            account.removeAccountEventListener(this);
            remove(account);
        }
    }

    /**
     * Does refresh the list
     *
     * @param accountEvent the {@link AccountEvent} that caused the change event
     */
    public void onChangeEvent(AccountEvent accountEvent)
    {
        // Timber.log(TimberLog.FINE, "Not an Error! Received accountEvent update for: "
        //		+ accountEvent.getSource().getAccountName() + " "
        //		+ accountEvent.toString(), new Throwable());
        doRefreshList();
    }
}
