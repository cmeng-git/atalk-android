/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may
 * obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for
 * the specific language governing permissions
 * and limitations under the License.
 */
package org.atalk.android.gui.chat.conference;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.ContextMenu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ExpandableListView;
import android.widget.ExpandableListView.OnChildClickListener;
import android.widget.ExpandableListView.OnGroupClickListener;

import net.java.sip.communicator.service.contactlist.MetaContact;
import net.java.sip.communicator.service.contactlist.MetaContactGroup;
import net.java.sip.communicator.service.contactsource.SourceContact;
import net.java.sip.communicator.service.gui.ContactList;
import net.java.sip.communicator.service.gui.UIContact;
import net.java.sip.communicator.service.protocol.Contact;
import net.java.sip.communicator.service.protocol.OperationSetMultiUserChat;
import net.java.sip.communicator.service.protocol.ProtocolProviderService;
import net.java.sip.communicator.util.Logger;

import org.atalk.android.R;
import org.atalk.android.gui.AndroidGUIActivator;
import org.atalk.android.gui.chat.ChatActivity;
import org.atalk.android.gui.chat.ChatPanel;
import org.atalk.android.gui.chat.ChatTransport;
import org.atalk.android.gui.chat.MetaContactChatSession;
import org.atalk.android.gui.contactlist.ContactListFragment;
import org.atalk.android.gui.contactlist.model.MetaGroupExpandHandler;
import org.atalk.android.gui.contactlist.contactsource.ProtocolContactSourceServiceImpl;
import org.atalk.android.gui.contactlist.model.BaseContactListAdapter;
import org.atalk.android.gui.contactlist.model.MetaContactListAdapter;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * The invite dialog is the one shown when the user clicks on the conference button in the chat
 * toolbar.
 *
 * @author Eng Chong Meng
 */
public class ChatInviteDialog extends Dialog
		implements OnChildClickListener, OnGroupClickListener, DialogInterface.OnShowListener
{
	/**
	 * The logger
	 */
	private final static Logger logger = Logger.getLogger(ChatInviteDialog.class);
	private static boolean MUC_OFFLINE_ALLOW = true;

	private ChatActivity mParent = null;
	private final ChatPanel chatPanel;
	private ChatTransport inviteChatTransport;

	private EditText reasonText;
	private Button mInviteButton;

	/**
	 * Contact list data model.
	 */
	protected MetaContactListAdapter contactListAdapter;

	/**
	 * Meta contact groups expand memory.
	 */
	private MetaGroupExpandHandler listExpandHandler;

	/**
	 * The contact list view.
	 */
	protected ExpandableListView contactListView;

	/**
	 * Stores last clicked <tt>MetaContact</tt>.
	 */
	public MetaContact clickedContact;

	/**
	 * Stores recently clicked contact group.
	 */
	private MetaContactGroup clickedGroup;

	/**
	 * The source contact list.
	 */
	protected ContactList srcContactList;

	/**
	 * The destination contact list.
	 */
	protected ContactList destContactList;

	/**
	 * A map of all active chats i.e. metaContactChat, MUC etc.
	 */
	private static final Map<String, MetaContact> mucContactList = new LinkedHashMap<>();

	/**
	 * The invite contact transfer handler.
	 */
	// private InviteContactTransferHandler inviteContactTransferHandler;

	/**
	 * Currently selected protocol provider.
	 */
	// private ProtocolProviderService currentProvider;

	/**
	 * Constructs the <tt>ChatInviteDialog</tt>.
	 *
	 * @param mChatPanel
	 * 		the <tt>ChatPanel</tt> corresponding to the <tt>ChatRoom</tt>, where the contact is
	 * 		invited.
	 */
	public ChatInviteDialog(Context mContext, ChatPanel mChatPanel)
	{
		super(mContext);
		this.mParent = (ChatActivity) mContext;
		this.chatPanel = mChatPanel;
		this.inviteChatTransport = chatPanel.findInviteChatTransport();
		setOnShowListener(this);
	}

	@Override
	public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		setTitle(R.string.service_gui_INVITE_CONTACT_TO_CHAT);

		this.setContentView(R.layout.muc_invite_dialog);
		reasonText =  this.findViewById(R.id.text_reason);

		contactListView = this.findViewById(R.id.ContactListView);
		contactListView.setSelector(R.drawable.contact_list_selector);
		contactListView.setOnChildClickListener(this);
		contactListView.setOnGroupClickListener(this);

		// Adds context menu for contact list items
		registerForContextMenu(contactListView);
		initListAdapter();

		mInviteButton = this.findViewById(R.id.button_invite);
		if (chatPanel.getChatSession() instanceof MetaContactChatSession) {
			mucContactList.put(chatPanel.getMetaContact().getMetaUID(), chatPanel.getMetaContact());
			mInviteButton.setEnabled(true);
		}
		else {
			mInviteButton.setAlpha(.3f);
		}

		mInviteButton.setOnClickListener(new View.OnClickListener()
		{
			public void onClick(View v)
			{
				inviteContacts();
				closeDialog();
			}
		});

		Button mCancelButton = this.findViewById(R.id.buttonCancel);
		mCancelButton.setOnClickListener(new View.OnClickListener()
		{
			public void onClick(View v)
			{
				closeDialog();
			}
		});
		// this.initContactListData();
	}

	private void initListAdapter()
	{
		contactListView.setAdapter(getContactListAdapter());

		// Attach contact groups expand memory
		listExpandHandler = new MetaGroupExpandHandler(contactListAdapter, contactListView);
		listExpandHandler.bindAndRestore();

		// setDialogMode to true to avoid contacts being filtered
		contactListAdapter.setDialogMode(true);

		// Update ExpandedList View
		contactListAdapter.invalidateViews();
	}

	public void closeDialog()
	{
		// must clear dialogMode on exit dialog
		contactListAdapter.setDialogMode(false);
		this.cancel();
	}

	private MetaContactListAdapter getContactListAdapter()
	{
		ContactListFragment clf = AndroidGUIActivator.getContactListFragment();
		if (contactListAdapter == null) {
			contactListAdapter = new MetaContactListAdapter(clf, false);
			contactListAdapter.initModelData();
		}
		contactListAdapter.nonZeroContactGroupList();
		return contactListAdapter;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void onCreateContextMenu(ContextMenu menu, View v,
			ContextMenu.ContextMenuInfo menuInfo)
	{
		super.onCreateContextMenu(menu, v, menuInfo);

		if (contactListView.getExpandableListAdapter() != getContactListAdapter()) {
			return;
		}

		ExpandableListView.ExpandableListContextMenuInfo info
				= (ExpandableListView.ExpandableListContextMenuInfo) menuInfo;
		int type = ExpandableListView.getPackedPositionType(info.packedPosition);
		int group = ExpandableListView.getPackedPositionGroup(info.packedPosition);
		int child = ExpandableListView.getPackedPositionChild(info.packedPosition);

		// Only create a context menu for child items
		MenuInflater inflater = mParent.getMenuInflater();
		if (type == ExpandableListView.PACKED_POSITION_TYPE_GROUP) {
			createGroupCtxMenu(menu, inflater, group);
		}
		else if (type == ExpandableListView.PACKED_POSITION_TYPE_CHILD) {
			createContactCtxMenu(menu, inflater, group, child);
		}
	}

	/**
	 * Inflates group context menu.
	 *
	 * @param menu
	 * 		the menu to inflate into.
	 * @param inflater
	 * 		the inflater.
	 * @param group
	 * 		clicked group index.
	 */
	private void createGroupCtxMenu(ContextMenu menu, MenuInflater inflater, int group)
	{
		this.clickedGroup = (MetaContactGroup) contactListAdapter.getGroup(group);

		// Inflate contact list context menu
		inflater.inflate(R.menu.group_menu, menu);
		menu.setHeaderTitle(clickedGroup.getGroupName());
	}

	/**
	 * Inflates contact context menu.
	 *
	 * @param menu
	 * 		the menu to inflate into.
	 * @param inflater
	 * 		the menu inflater.
	 * @param group
	 * 		clicked group index.
	 * @param child
	 * 		clicked contact index.
	 */
	private void createContactCtxMenu(ContextMenu menu, MenuInflater inflater, int group,
			int child)
	{
		// Inflate muc contact list context menu
		inflater.inflate(R.menu.muc_menu, menu);

		// Remembers clicked contact
		clickedContact = ((MetaContact) contactListAdapter.getChild(group, child));
		menu.setHeaderTitle(clickedContact.getDisplayName());
		Contact contact = clickedContact.getDefaultContact();
		if (contact == null) {
			logger.warn("No default contact for: " + clickedContact);
			return;
		}

		ProtocolProviderService pps = contact.getProtocolProvider();
		if (pps == null) {
			logger.warn("No protocol provider found for: " + contact);
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean onContextItemSelected(MenuItem item)
	{
		switch (item.getItemId()) {
			case R.id.muc_Kick:
				return true;
			case R.id.muc_PrivateMessage:
				// EntityListHelper.eraseEntityChatHistory(ChatActivity.this, clickedContact);
				return true;
			case R.id.muc_send_file:
				// ChatPanel clickedChat = ChatSessionManager.getActiveChat(clickedContact);
				// FragmentActivity parent = getActivity();
				// AttachOptionDialog attachOptionDialog = new AttachOptionDialog(parent,
				// clickedContact);
				// attachOptionDialog.show();
				return true;
		}
		return super.onContextItemSelected(item);
	}

	/**
	 * Returns the contact list view.
	 *
	 * @return the contact list view
	 */
	public ExpandableListView getContactListView()
	{
		return contactListView;
	}

	/**
	 *
	 */
	@Override
	public boolean onChildClick(ExpandableListView listView, View v, int groupPosition,
			int childPosition, long id)
	{
		BaseContactListAdapter adapter
				= (BaseContactListAdapter) listView.getExpandableListAdapter();
		int position = adapter.getListIndex(groupPosition, childPosition);
		contactListView.setSelection(position);
		// adapter.invalidateViews();

		// Get v index for multiple selection highlight
		int index = listView.getFlatListPosition(
				ExpandableListView.getPackedPositionForChild(groupPosition, childPosition));

		Object clicked = adapter.getChild(groupPosition, childPosition);
		if ((clicked instanceof MetaContact)) {
			MetaContact metaContact = (MetaContact) clicked;
			if (MUC_OFFLINE_ALLOW || !metaContact.getContactsForOperationSet(
					OperationSetMultiUserChat.class).isEmpty()) {
				// Toggle muc Contact Selection
				String key = metaContact.getMetaUID();
				if (mucContactList.containsKey(key)) {
					mucContactList.remove(key);
					listView.setItemChecked(index, false);
					// v.setSelected(false);
				}
				else {
					mucContactList.put(key, metaContact);
					listView.setItemChecked(index, true);
					// v.setSelected(true); for single item selection only
				}

				if (mucContactList.isEmpty()) {
					mInviteButton.setEnabled(false);
					mInviteButton.setAlpha(.3f);
				}
				else {
					mInviteButton.setEnabled(true);
					mInviteButton.setAlpha(1.0f);
				}
				return true;
			}
			return false;
		}
		return false;
	}

	/**
	 * Expands/collapses the group given by <tt>groupPosition</tt>.
	 *
	 * @param parent
	 * 		the parent expandable list view
	 * @param v
	 * 		the view
	 * @param groupPosition
	 * 		the position of the group
	 * @param id
	 * 		the identifier
	 * @return <tt>true</tt> if the group click action has been performed
	 */
	@Override
	public boolean onGroupClick(ExpandableListView parent, View v, int groupPosition, long id)
	{
		// cmeng - group collapse will destroy sub-contacts selected
		// if (contactListView.isGroupExpanded(groupPosition))
		// contactListView.collapseGroup(groupPosition);
		// else {
		// // Expand animation is supported since API14
		// if (AndroidUtils.hasAPI(14)) {
		// contactListView.expandGroup(groupPosition, true);
		// }
		// else {
		// contactListView.expandGroup(groupPosition);
		// }
		// }
		return true;
	}

	/**
	 * Initializes the left contact list with the contacts that could be added to the current chat
	 * session.
	 */
	private void initContactListData()
	{
		this.inviteChatTransport = chatPanel.findInviteChatTransport();
		srcContactList.addContactSource(
				new ProtocolContactSourceServiceImpl(inviteChatTransport.getProtocolProvider(),
						OperationSetMultiUserChat.class));
		// srcContactList.setDefaultFilter(new ChatInviteContactListFilter(srcContactList));
		srcContactList.applyDefaultFilter();
	}

	/**
	 * Returns the reason of this invite, if the user has specified one.
	 *
	 * @return the reason of this invite
	 */
	public String getReason()
	{
		return reasonText.getText().toString();
	}

	/**
	 * The <tt>ChatInviteContactListFilter</tt> is <tt>InviteContactListFilter</tt> which doesn't
	 * list contact that don't have persistence
	 * addresses (for example private messaging contacts are not listed).
	 */
	private class ChatInviteContactListFilter // extends InviteContactListFilter
	{
		/**
		 * The Multi User Chat operation set instance.
		 */
		private OperationSetMultiUserChat opSetMUC;

		/**
		 * Creates an instance of <tt>InviteContactListFilter</tt>.
		 *
		 * @param sourceContactList
		 * 		the contact list to filter
		 */
		public ChatInviteContactListFilter(ContactList sourceContactList)
		{
			// super(sourceContactList);
			opSetMUC = inviteChatTransport.getProtocolProvider().getOperationSet(
					OperationSetMultiUserChat.class);
		}

		// @Override
		public boolean isMatching(UIContact uiContact)
		{
			SourceContact contact = (SourceContact) uiContact.getDescriptor();
			if (opSetMUC.isPrivateMessagingContact(contact.getContactAddress())) {
				return false;
			}
			return true;
		}
	}

	/**
	 * Invites the contacts to the chat conference.
	 */
	private void inviteContacts()
	{
		Collection<String> selectedContactAddresses = new ArrayList<>();

		List<MetaContact> mContacts = new LinkedList<>(mucContactList.values());
		if (mContacts.size() == 0)
			return;

		for (MetaContact mContact : mContacts) {
			String mAddress = mContact.getDefaultContact().getAddress();
			selectedContactAddresses.add(mAddress);
		}

		// Obtain selected contacts.
		// Collection<UIContact> contacts = destContactList.getContacts(null);

		// Iterator<UIContact> selectedContacts = contacts.iterator();
		// if (selectedContacts != null) {
		// while (selectedContacts.hasNext()) {
		// UIContact uiContact = selectedContacts.next();
		//
		// Iterator<UIContactDetail> contactsIter
		// = uiContact.getContactDetailsForOperationSet(OperationSetMultiUserChat.class)
		// .iterator();
		//
		// // We invite the first protocol contact that corresponds to the invite provider.
		// if (contactsIter.hasNext()) {
		// UIContactDetail inviteDetail = contactsIter.next();
		// selectedContactAddresses.add(inviteDetail.getAddress());
		// }
		// }
		// }

		// Invite all selected.
		if (selectedContactAddresses.size() > 0) {
			chatPanel.inviteContacts(inviteChatTransport, selectedContactAddresses,
					this.getReason());
		}
	}

	@Override
	public void onShow(DialogInterface arg0)
	{
		List<MetaContact> mContacts = new LinkedList<>(mucContactList.values());
		int indexes = contactListAdapter.getGroupCount();
		for (MetaContact mContact : mContacts) {
			int childIdx;
			for (int gIdx = 0; gIdx < indexes; gIdx++) {
				childIdx = contactListAdapter.getChildIndex(gIdx, mContact);
				if (childIdx != -1) {
					childIdx += gIdx + 1;
					contactListView.setItemChecked(childIdx, true);
					break;
				}
			}
		}
	}
}
