/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.android.gui.contactlist;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Spinner;

import net.java.sip.communicator.service.contactlist.MetaContact;
import net.java.sip.communicator.service.contactlist.MetaContactGroup;
import net.java.sip.communicator.service.contactlist.event.MetaContactEvent;
import net.java.sip.communicator.service.contactlist.event.MetaContactListAdapter;
import net.java.sip.communicator.service.contactlist.event.ProtoContactEvent;
import net.java.sip.communicator.service.protocol.AccountID;
import net.java.sip.communicator.service.protocol.OperationSet;
import net.java.sip.communicator.service.protocol.OperationSetPresence;
import net.java.sip.communicator.service.protocol.ProtocolProviderService;
import net.java.sip.communicator.util.Logger;
import net.java.sip.communicator.util.account.AccountUtils;

import org.atalk.android.R;
import org.atalk.android.gui.AndroidGUIActivator;
import org.atalk.android.gui.account.Account;
import org.atalk.android.gui.account.AccountsListAdapter;
import org.atalk.android.gui.util.ViewUtil;
import org.atalk.service.osgi.OSGiActivity;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * This activity allows user to add new contacts.
 *
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
public class AddContactActivity extends OSGiActivity
{
	/**
	 * The logger.
	 */
	private final static Logger logger = Logger.getLogger(AddContactActivity.class);

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);

		setContentView(R.layout.add_contact);
		setTitle(R.string.service_gui_ADD_CONTACT);
		initAccountSpinner();
		initContactGroupSpinner();
	}

	/**
	 * Initializes "select account" spinner with existing accounts.
	 */
	private void initAccountSpinner()
	{
		Spinner accountsSpinner = (Spinner) findViewById(R.id.selectAccountSpinner);

		Collection<ProtocolProviderService> providers = AccountUtils.getRegisteredProviders();
		List<AccountID> accounts = new ArrayList<>();
		int idx = 0;
		int selectedIdx = -1;

		for (ProtocolProviderService provider : providers) {
			OperationSet opSet = provider.getOperationSet(OperationSetPresence.class);
			if (opSet != null) {
				AccountID account = provider.getAccountID();
				accounts.add(account);

				if ((selectedIdx == -1) && account.isPreferredProvider()) {
					selectedIdx = idx;
				}
				idx++;
			}
		}

		AccountsListAdapter accountsAdapter = new AccountsListAdapter(this,
				R.layout.select_account_row, R.layout.select_account_dropdown, accounts, true);
		accountsSpinner.setAdapter(accountsAdapter);

		// if we have only select account option and only one account select the available account
		if (accounts.size() == 1)
			accountsSpinner.setSelection(0);
		else
			accountsSpinner.setSelection(selectedIdx);
	}

	/**
	 * Initializes select contact group spinner with contact groups.
	 */
	private void initContactGroupSpinner()
	{
		Spinner groupSpinner = (Spinner) findViewById(R.id.selectGroupSpinner);
		MetaContactGroupAdapter contactGroupAdapter
				= new MetaContactGroupAdapter(this, R.id.selectGroupSpinner, true, true);

		contactGroupAdapter.setItemLayout(R.layout.simple_spinner_item);
		contactGroupAdapter.setDropDownLayout(R.layout.dropdown_spinner_item);
		groupSpinner.setAdapter(contactGroupAdapter);
	}

	/**
	 * Method fired when "add" button is clicked.
	 *
	 * @param v
	 * 		add button's <tt>View</tt>
	 */
	public void onAddClicked(View v)
	{
		Spinner accountsSpinner = (Spinner) findViewById(R.id.selectAccountSpinner);
		Account selectedAcc = (Account) accountsSpinner.getSelectedItem();
		if (selectedAcc == null) {
			logger.error("No account selected");
			return;
		}

		ProtocolProviderService pps = selectedAcc.getProtocolProvider();
		if (pps == null) {
			logger.error("No provider registered for account " + selectedAcc.getAccountName());
			return;
		}

		View content = findViewById(android.R.id.content);
		String contactAddress = ViewUtil.getTextViewValue(content, R.id.editContactName).trim();

		String displayName = ViewUtil.getTextViewValue(content, R.id.editDisplayName).trim();
		if (!TextUtils.isEmpty(displayName)) {
			addRenameListener(pps, null, contactAddress, displayName);
		}

		Spinner groupSpinner = (Spinner) findViewById(R.id.selectGroupSpinner);
		ContactListUtils.addContact(pps, (MetaContactGroup) groupSpinner.getSelectedItem(), contactAddress);
		finish();
	}

    public void onCancelClicked(View v){
	    finish();
    }

	/**
	 * Adds a rename listener.
	 *
	 * @param protocolProvider
	 * 		the protocol provider to which the contact was added
	 * @param metaContact
	 * 		the <tt>MetaContact</tt> if the new contact was added to an existing meta contact
	 * @param contactAddress
	 * 		the address of the newly added contact
	 * @param displayName
	 * 		the new display name
	 */
	private void addRenameListener(final ProtocolProviderService protocolProvider,
			final MetaContact metaContact,
			final String contactAddress, final String displayName)
	{
		AndroidGUIActivator.getContactListService().addMetaContactListListener(
				new MetaContactListAdapter()
				{
					@Override
					public void metaContactAdded(MetaContactEvent evt)
					{
						if (evt.getSourceMetaContact().getContact(contactAddress, protocolProvider) != null) {
							renameContact(evt.getSourceMetaContact(), displayName);
						}
					}

					@Override
					public void protoContactAdded(ProtoContactEvent evt)
					{
						if (metaContact != null && evt.getNewParent().equals(metaContact)) {
							renameContact(metaContact, displayName);
						}
					}
				});
	}

	/**
	 * Renames the given meta contact.
	 *
	 * @param metaContact
	 * 		the <tt>MetaContact</tt> to rename
	 * @param displayName
	 * 		the new display name
	 */
	private void renameContact(final MetaContact metaContact, final String displayName)
	{
		new Thread()
		{
			@Override
			public void run()
			{
				AndroidGUIActivator.getContactListService().renameMetaContact(metaContact, displayName);
			}
		}.start();
	}

}