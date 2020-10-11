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
import android.os.Handler;
import android.view.View;
import android.widget.Button;
import android.widget.ExpandableListView;
import android.widget.ExpandableListView.OnChildClickListener;
import android.widget.ExpandableListView.OnGroupClickListener;

import net.java.sip.communicator.service.contactlist.MetaContact;
import net.java.sip.communicator.service.protocol.OperationSetMultiUserChat;

import org.atalk.android.R;
import org.atalk.android.aTalkApp;
import org.atalk.android.gui.aTalk;
import org.atalk.android.gui.chat.*;
import org.atalk.android.gui.contactlist.ContactListFragment;
import org.atalk.android.gui.contactlist.model.*;
import org.atalk.android.gui.util.ViewUtil;
import org.jxmpp.jid.DomainJid;
import org.jxmpp.jid.Jid;

import java.util.*;

/**
 * The invite dialog is the one shown when the user clicks on the conference button in the chat toolbar.
 * this
 *
 * @author Eng Chong Meng
 */
public class ChatInviteDialog extends Dialog
        implements OnChildClickListener, OnGroupClickListener, DialogInterface.OnShowListener
{
    /**
     * Allow offline contact selection for invitation
     */
    private static boolean MUC_OFFLINE_ALLOW = true;

    private final ChatPanel chatPanel;
    private final ChatTransport inviteChatTransport;

    /**
     * A reference map of all invitees i.e. MetaContact UID to MetaContact .
     */
    private static final Map<String, MetaContact> mucContactInviteList = new LinkedHashMap<>();

    /**
     * Contact list data model.
     */
    private MetaContactListAdapter contactListAdapter;

    /**
     * The contact list view.
     */
    private ExpandableListView contactListView;

    private Button mInviteButton;

    /**
     * Constructs the <tt>ChatInviteDialog</tt>.
     *
     * @param mChatPanel the <tt>ChatPanel</tt> corresponding to the <tt>ChatRoom</tt>, where the contact is invited.
     */
    public ChatInviteDialog(Context mContext, ChatPanel mChatPanel)
    {
        super(mContext);
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
        contactListView.setSelector(R.drawable.list_selector_state);
        contactListView.setOnChildClickListener(this);
        contactListView.setOnGroupClickListener(this);
        initListAdapter();

        mInviteButton = this.findViewById(R.id.button_invite);
        mInviteButton.setOnClickListener(v -> {
            inviteContacts();
            closeDialog();
        });

        // Default to include the current contact of the MetaContactChatSession to be invited
        if (chatPanel.getChatSession() instanceof MetaContactChatSession) {
            MetaContact mContact = chatPanel.getMetaContact();
            mucContactInviteList.put(mContact.getMetaUID(), mContact);
        }
        updateInviteState();

        Button mCancelButton = this.findViewById(R.id.buttonCancel);
        mCancelButton.setOnClickListener(v -> closeDialog());
    }

    /**
     * Enable the Invite button if mucContactInviteList is not empty
     */
    private void updateInviteState()
    {
        if (mucContactInviteList.isEmpty()) {
            mInviteButton.setEnabled(false);
            mInviteButton.setAlpha(.3f);
        }
        else {
            mInviteButton.setEnabled(true);
            mInviteButton.setAlpha(1.0f);
        }
    }

    private void initListAdapter()
    {
        contactListView.setAdapter(getContactListAdapter());

        // Attach contact groups expand memory
        MetaGroupExpandHandler listExpandHandler = new MetaGroupExpandHandler(contactListAdapter, contactListView);
        listExpandHandler.bindAndRestore();

        // setDialogMode to true to avoid contacts being filtered
        contactListAdapter.setDialogMode(true);

        // Update ExpandedList View
        contactListAdapter.invalidateViews();
    }

    private MetaContactListAdapter getContactListAdapter()
    {
        if (contactListAdapter == null) {
            // FFR: clf may be null; use new instance will crash dialog on select contact
            ContactListFragment clf = (ContactListFragment) aTalk.getFragment(aTalk.CL_FRAGMENT);
            contactListAdapter = new MetaContactListAdapter(clf, false);
            contactListAdapter.initModelData();
        }
        // Do not include groups with zero member in main contact list
        contactListAdapter.nonZeroContactGroupList();

        return contactListAdapter;
    }

    /**
     * Callback method to be invoked when a child in this expandable list has been clicked.
     *
     * @param parent The ExpandableListView where the click happened
     * @param v The view within the expandable list/ListView that was clicked
     * @param groupPosition The group position that contains the child that was clicked
     * @param childPosition The child position within the group
     * @param id The row id of the child that was clicked
     * @return True if the click was handled
     */
    @Override
    public boolean onChildClick(ExpandableListView listView, View v, int groupPosition, int childPosition, long id)
    {
        // Get v index for multiple selection highlight
        int index = listView.getFlatListPosition(
                ExpandableListView.getPackedPositionForChild(groupPosition, childPosition));

        BaseContactListAdapter adapter = (BaseContactListAdapter) listView.getExpandableListAdapter();
        Object clicked = adapter.getChild(groupPosition, childPosition);
        if ((clicked instanceof MetaContact)) {
            MetaContact metaContact = (MetaContact) clicked;

            if (MUC_OFFLINE_ALLOW
                    || !metaContact.getContactsForOperationSet(OperationSetMultiUserChat.class).isEmpty()) {
                // Toggle muc Contact Selection
                String key = metaContact.getMetaUID();
                if (mucContactInviteList.containsKey(key)) {
                    mucContactInviteList.remove(key);
                    listView.setItemChecked(index, false);
                    // v.setSelected(false);
                }
                else {
                    mucContactInviteList.put(key, metaContact);
                    listView.setItemChecked(index, true);
                    // v.setSelected(true); for single item selection only
                }
                updateInviteState();
                return true;
            }
            return false;
        }
        return false;
    }

    /**
     * Expands/collapses the group given by <tt>groupPosition</tt>.
     *
     * Group collapse will clear all highlight of selected contacts; On expansion
     * allow time for view to expand before proceed to refresh the selected contacts' highlight
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
        if (contactListView.isGroupExpanded(groupPosition))
            contactListView.collapseGroup(groupPosition);
        else {
            contactListView.expandGroup(groupPosition, true);
            new Handler().postDelayed(() -> {
                refreshContactSelected(groupPosition);
            }, 500);
        }
        return true;
    }

//    /**
//     * The <tt>ChatInviteContactListFilter</tt> is <tt>InviteContactListFilter</tt> which doesn't list
//     * contact that don't have persistence addresses (for example private messaging contacts are not listed).
//     */
//    private class ChatInviteContactListFilter // extends InviteContactListFilter
//    {
//        /**
//         * The Multi User Chat operation set instance.
//         */
//        private OperationSetMultiUserChat opSetMUC;
//
//        /**
//         * Creates an instance of <tt>InviteContactListFilter</tt>.
//         *
//         * @param sourceContactList the contact list to filter
//         */
//        public ChatInviteContactListFilter(ContactList sourceContactList)
//        {
//            // super(sourceContactList);
//            opSetMUC = inviteChatTransport.getProtocolProvider().getOperationSet(OperationSetMultiUserChat.class);
//        }
//
//        // @Override
//        public boolean isMatching(UIContact uiContact)
//        {
//            SourceContact contact = (SourceContact) uiContact.getDescriptor();
//            return !opSetMUC.isPrivateMessagingContact(contact.getContactAddress());
//        }
//    }

    /**
     * Invites the contacts to the chat conference.
     */
    private void inviteContacts()
    {
        Collection<String> selectedContactAddresses = new ArrayList<>();

        List<MetaContact> selectedContacts = new LinkedList<>(mucContactInviteList.values());
        if (selectedContacts.size() == 0)
            return;

        // Obtain selected contacts.
        for (MetaContact uiContact : selectedContacts) {
            // skip server/system account
            Jid jid =  uiContact.getDefaultContact().getJid();
            if ((jid == null) || (jid instanceof DomainJid)) {
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

    /**
     * Refresh highlight for all the selected contacts when:
     * a. Dialog onShow
     * b. User collapse and expand group
     *
     * @param grpPosition the contact list group position
     */
    private void refreshContactSelected(int grpPosition)
    {
        Collection<MetaContact> mContactList = mucContactInviteList.values();
        int lastIndex = contactListView.getCount();

        for (int index = 0; index <= lastIndex; index++) {
            long lPosition = contactListView.getExpandableListPosition(index);

            int groupPosition = ExpandableListView.getPackedPositionGroup(lPosition);
            if ((grpPosition == -1) || (groupPosition == grpPosition)) {
                int childPosition = ExpandableListView.getPackedPositionChild(lPosition);
                MetaContact mContact = ((MetaContact) contactListAdapter.getChild(groupPosition, childPosition));
                if (mContact == null)
                    continue;

                for (MetaContact metaContact : mContactList) {
                    if (metaContact.equals(mContact)) {
                        contactListView.setItemChecked(index, true);
                        break;
                    }
                }
            }
        }
    }

    @Override
    public void onShow(DialogInterface arg0)
    {
        int indexes = contactListAdapter.getGroupCount();
        refreshContactSelected(-1);
        updateInviteState();
    }

    private void closeDialog()
    {
        // must clear dialogMode on exit dialog
        contactListAdapter.setDialogMode(false);
        this.cancel();
    }
}
