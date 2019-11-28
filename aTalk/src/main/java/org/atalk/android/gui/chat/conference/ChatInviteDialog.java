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
import android.view.*;
import android.widget.*;
import android.widget.ExpandableListView.OnChildClickListener;
import android.widget.ExpandableListView.OnGroupClickListener;

import net.java.sip.communicator.service.contactlist.MetaContact;
import net.java.sip.communicator.service.contactlist.MetaContactGroup;
import net.java.sip.communicator.service.contactsource.SourceContact;
import net.java.sip.communicator.service.gui.ContactList;
import net.java.sip.communicator.service.gui.UIContact;
import net.java.sip.communicator.service.protocol.*;

import org.atalk.android.R;
import org.atalk.android.aTalkApp;
import org.atalk.android.gui.aTalk;
import org.atalk.android.gui.chat.*;
import org.atalk.android.gui.contactlist.ContactListFragment;
import org.atalk.android.gui.contactlist.model.*;
import org.atalk.android.gui.util.ViewUtil;

import java.util.*;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import timber.log.Timber;

/**
 * The invite dialog is the one shown when the user clicks on the conference button in the chat toolbar.
 *
 * @author Eng Chong Meng
 */
public class ChatInviteDialog extends Dialog
        implements OnChildClickListener, OnGroupClickListener, DialogInterface.OnShowListener
{
    private static boolean MUC_OFFLINE_ALLOW = true;

    private ChatActivity mParent;
    private final ChatPanel chatPanel;
    private ChatTransport inviteChatTransport;

    private Button mInviteButton;

    /**
     * Contact list data model.
     */
    private MetaContactListAdapter contactListAdapter;

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
    private MetaContact clickedContact;

    /**
     * Stores recently clicked contact group.
     */
    private MetaContactGroup clickedGroup;

    /**
     * A map of all active chats i.e. metaContactChat, MUC etc.
     */
    private static final Map<String, MetaContact> mucContactList = new LinkedHashMap<>();

    /**
     * Constructs the <tt>ChatInviteDialog</tt>.
     *
     * @param mChatPanel the <tt>ChatPanel</tt> corresponding to the <tt>ChatRoom</tt>, where the contact is invited.
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

        contactListView = this.findViewById(R.id.ContactListView);
        contactListView.setSelector(R.drawable.array_list_selector);
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

        mInviteButton.setOnClickListener(v -> {
            inviteContacts();
            closeDialog();
        });

        Button mCancelButton = this.findViewById(R.id.buttonCancel);
        mCancelButton.setOnClickListener(v -> closeDialog());
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

    private void closeDialog()
    {
        // must clear dialogMode on exit dialog
        contactListAdapter.setDialogMode(false);
        this.cancel();
    }

    private MetaContactListAdapter getContactListAdapter()
    {
        ContactListFragment clf = (ContactListFragment) aTalk.getFragment(aTalk.CL_FRAGMENT);
        if (contactListAdapter == null) {
            contactListAdapter = new MetaContactListAdapter(clf, false);
            contactListAdapter.initModelData();
        }
        // Do not include groups with zero member in main contact list
        contactListAdapter.nonZeroContactGroupList();
        return contactListAdapter;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo)
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
     * @param menu the menu to inflate into.
     * @param inflater the inflater.
     * @param group clicked group index.
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
     * @param menu the menu to inflate into.
     * @param inflater the menu inflater.
     * @param group clicked group index.
     * @param child clicked contact index.
     */
    private void createContactCtxMenu(ContextMenu menu, MenuInflater inflater, int group, int child)
    {
        // Inflate muc contact list context menu
        inflater.inflate(R.menu.muc_menu, menu);

        // Remembers clicked contact
        clickedContact = ((MetaContact) contactListAdapter.getChild(group, child));
        menu.setHeaderTitle(clickedContact.getDisplayName());
        Contact contact = clickedContact.getDefaultContact();
        if (contact == null) {
            Timber.w("No default contact for: %s", clickedContact);
            return;
        }

        ProtocolProviderService pps = contact.getProtocolProvider();
        if (pps == null) {
            Timber.w("No protocol provider found for: %s", contact);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean onContextItemSelected(@NonNull MenuItem item)
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
    public boolean onChildClick(ExpandableListView listView, View v, int groupPosition, int childPosition, long id)
    {
        BaseContactListAdapter adapter = (BaseContactListAdapter) listView.getExpandableListAdapter();
        int position = adapter.getListIndex(groupPosition, childPosition);
        contactListView.setSelection(position);
        // adapter.invalidateViews();

        // Get v index for multiple selection highlight
        int index = listView.getFlatListPosition(
                ExpandableListView.getPackedPositionForChild(groupPosition, childPosition));

        Object clicked = adapter.getChild(groupPosition, childPosition);
        if ((clicked instanceof MetaContact)) {
            MetaContact metaContact = (MetaContact) clicked;
            if (MUC_OFFLINE_ALLOW
                    || !metaContact.getContactsForOperationSet(OperationSetMultiUserChat.class).isEmpty()) {
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
     * @param parent the parent expandable list view
     * @param v the view
     * @param groupPosition the position of the group
     * @param id the identifier
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
     * The <tt>ChatInviteContactListFilter</tt> is <tt>InviteContactListFilter</tt> which doesn't list
     * contact that don't have persistence addresses (for example private messaging contacts are not listed).
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
         * @param sourceContactList the contact list to filter
         */
        public ChatInviteContactListFilter(ContactList sourceContactList)
        {
            // super(sourceContactList);
            opSetMUC = inviteChatTransport.getProtocolProvider().getOperationSet(OperationSetMultiUserChat.class);
        }

        // @Override
        public boolean isMatching(UIContact uiContact)
        {
            SourceContact contact = (SourceContact) uiContact.getDescriptor();
            return !opSetMUC.isPrivateMessagingContact(contact.getContactAddress());
        }
    }

    /**
     * Invites the contacts to the chat conference.
     */
    private void inviteContacts()
    {
        Collection<String> selectedContactAddresses = new ArrayList<>();

        List<MetaContact> selectedContacts = new LinkedList<>(mucContactList.values());
        if (selectedContacts.size() == 0)
            return;

        // Obtain selected contacts.
        for (MetaContact uiContact : selectedContacts) {
            // skip server/system account
            if (uiContact.getDefaultContact().getJid() == null) {
                aTalkApp.showToastMessage(R.string.service_gui_SEND_MESSAGE_NOT_SUPPORTED, uiContact.getDisplayName());
                continue;
            }
            String mAddress = uiContact.getDefaultContact().getAddress();
            selectedContactAddresses.add(mAddress);
        }

        // Invite all selected.
        if (selectedContactAddresses.size() > 0) {
            chatPanel.inviteContacts(inviteChatTransport, selectedContactAddresses,
                    ViewUtil.toString(this.findViewById(R.id.text_reason)));
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
