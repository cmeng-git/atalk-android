/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.android.gui.contactlist;

import android.content.DialogInterface;
import android.os.Bundle;
import android.view.*;
import android.widget.*;

import net.java.sip.communicator.service.contactlist.*;
import net.java.sip.communicator.util.Logger;

import org.atalk.android.*;
import org.atalk.android.gui.AndroidGUIActivator;
import org.atalk.android.gui.util.AndroidUtils;
import org.atalk.service.osgi.OSGiDialogFragment;

/**
 * Dialog that allows user to move the contact to selected group.
 *
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
public class MoveToGroupDialog extends OSGiDialogFragment
		implements DialogInterface.OnClickListener
{
	/**
	 * Meta UID arg key.
	 */
	private static final String META_CONTACT_UID = "meta_uid";

	/**
	 * Meta account UserID.
	 */
	private static final String USER_ID = "userId";

	/**
	 * The logger.
	 */
	private final static Logger logger = Logger.getLogger(AddContactActivity.class);

	/**
	 * The meta contact that will be moved.
	 */
	private MetaContact metaContact;

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			Bundle savedInstanceState)
	{
		getDialog().setTitle(R.string.service_gui_MOVE_CONTACT);
		this.metaContact = AndroidGUIActivator.getContactListService()
				.findMetaContactByMetaUID(getArguments().getString(META_CONTACT_UID));

		View contentView = getActivity().getLayoutInflater().inflate(R.layout.move_to_group, container, false);

		String UserId = getArguments().getString(USER_ID);
		TextView accountOwner = contentView.findViewById(R.id.accountOwner);
		accountOwner.setText(getString(R.string.service_gui_CONTACT_OWNER, UserId));

		final AdapterView groupList = contentView.findViewById(R.id.selectGroupSpinner);
		MetaContactGroupAdapter contactGroupAdapter
				= new MetaContactGroupAdapter(getActivity(), groupList, false, true);
		groupList.setAdapter(contactGroupAdapter);

		contentView.findViewById(R.id.move).setOnClickListener(new View.OnClickListener()
		{
			@Override
			public void onClick(View v)
			{
				moveContact((MetaContactGroup) groupList.getSelectedItem());
				dismiss();
			}
		});

		contentView.findViewById(R.id.cancel).setOnClickListener(new View.OnClickListener()
		{
			@Override
			public void onClick(View v)
			{
				dismiss();
			}
		});
		return contentView;
	}

	private void moveContact(final MetaContactGroup selectedItem)
	{
		new Thread()
		{
			@Override
			public void run()
			{
				try {
					AndroidGUIActivator.getContactListService().moveMetaContact(metaContact, selectedItem);
				}
				catch (MetaContactListException e) {
					logger.error(e, e);
					AndroidUtils.showAlertDialog(aTalkApp.getGlobalContext(), "Error", e.getMessage());
				}
			}
		}.start();
	}

	@Override
	public void onClick(DialogInterface dialog, int which)
	{
	}

	/**
	 * Creates new instance of <tt>MoveToGroupDialog</tt>.
	 *
	 * @param metaContact
	 * 		the contact that will be moved.
	 * @return parametrized instance of <tt>MoveToGroupDialog</tt>.
	 */
	public static MoveToGroupDialog getInstance(MetaContact metaContact)
	{
		Bundle args = new Bundle();
		String userName = metaContact.getDefaultContact().getProtocolProvider().getAccountID().getUserID();
		args.putString(USER_ID, userName);
		args.putString(META_CONTACT_UID, metaContact.getMetaUID());

		MoveToGroupDialog dialog = new MoveToGroupDialog();
		dialog.setArguments(args);

		return dialog;
	}
}
