/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.android.gui.contactlist;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.SearchManager;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.FragmentTransaction;
import android.view.*;
import android.widget.*;
import android.widget.ExpandableListView.OnChildClickListener;
import android.widget.ExpandableListView.OnGroupClickListener;

import net.java.sip.communicator.service.contactlist.MetaContact;
import net.java.sip.communicator.service.contactlist.MetaContactGroup;
import net.java.sip.communicator.service.gui.Chat;
import net.java.sip.communicator.service.protocol.*;
import net.java.sip.communicator.util.Logger;

import org.atalk.android.R;
import org.atalk.android.aTalkApp;
import org.atalk.android.gui.AndroidGUIActivator;
import org.atalk.android.gui.account.Account;
import org.atalk.android.gui.account.AccountInfoPresenceActivity;
import org.atalk.android.gui.call.telephony.TelephonyFragment;
import org.atalk.android.gui.chat.ChatPanel;
import org.atalk.android.gui.chat.ChatSessionManager;
import org.atalk.android.gui.contactlist.model.*;
import org.atalk.android.gui.dialogs.DialogActivity;
import org.atalk.android.gui.util.EntityListHelper;
import org.atalk.service.osgi.OSGiFragment;
import org.atalk.util.StringUtils;
import org.jxmpp.jid.DomainJid;

import java.util.List;

/**
 * Class to display the MetaContacts in Expandable List View
 *
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
public class ContactListFragment extends OSGiFragment
        implements OnChildClickListener, OnGroupClickListener
{
    /**
     * The logger
     */
    private final static Logger logger = Logger.getLogger(ContactListFragment.class);

    /**
     * Search options menu items.
     */
    private MenuItem searchItem;

    /**
     * Contact list data model.
     */
    protected MetaContactListAdapter contactListAdapter;

    /**
     * Meta contact groups expand memory.
     */
    private MetaGroupExpandHandler listExpandHandler;

    /**
     * List model used to search contact list and contact sources.
     */
    private QueryContactListAdapter sourcesAdapter;

    /**
     * The contact list view.
     */
    protected ExpandableListView contactListView;

    /**
     * Stores last clicked <tt>MetaContact</tt>.
     */
    protected MetaContact clickedContact;

    /**
     * Stores recently clicked contact group.
     */
    private MetaContactGroup clickedGroup;

    /**
     * Contact list item scroll position.
     */
    private static int scrollPosition;

    /**
     * Contact list scroll top position.
     */
    private static int scrollTopPosition;

    /**
     * Creates new instance of <tt>ContactListFragment</tt>.
     */
    public ContactListFragment()
    {
        super();
        // This fragment will create options menu.
        setHasOptionsMenu(true);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState)
    {
        if (AndroidGUIActivator.bundleContext == null) {
            return null;
        }

        ViewGroup content = (ViewGroup) inflater.inflate(R.layout.contact_list, container, false);
        contactListView = content.findViewById(R.id.contactListView);
        contactListView.setSelector(R.drawable.contact_list_selector);
        contactListView.setOnChildClickListener(this);
        contactListView.setOnGroupClickListener(this);

        // Adds context menu for contact list items
        registerForContextMenu(contactListView);
        return content;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onResume()
    {
        super.onResume();
        contactListView.setAdapter(getContactListAdapter());

        // Attach contact groups expand memory
        listExpandHandler = new MetaGroupExpandHandler(contactListAdapter, contactListView);
        listExpandHandler.bindAndRestore();

        // Invalidate view to update
        contactListAdapter.invalidateViews();
        contactListAdapter.filterData("");

        // Restore search state based on entered text
        if (searchItem != null) {
            SearchView searchView = (SearchView) searchItem.getActionView();
            int id = searchView.getContext().getResources().getIdentifier("android:id/search_src_text", null, null);
            TextView textView = searchView.findViewById(id);

            filterContactList(textView.getText().toString());
            bindSearchListener();
        }
        // Restore scroll position
        contactListView.setSelectionFromTop(scrollPosition, scrollTopPosition);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onPause()
    {
        super.onPause();

        // Unbind search listener
        if (searchItem != null) {
            SearchView searchView = (SearchView) searchItem.getActionView();
            searchView.setOnQueryTextListener(null);
            searchView.setOnCloseListener(null);
        }

        // Save scroll position
        scrollPosition = contactListView.getFirstVisiblePosition();
        View itemView = contactListView.getChildAt(0);
        scrollTopPosition = itemView == null ? 0 : itemView.getTop();

        // Dispose of group expand memory
        if (listExpandHandler != null) {
            listExpandHandler.unbind();
            listExpandHandler = null;
        }

        contactListView.setAdapter((ExpandableListAdapter) null);
        if (contactListAdapter != null) {
            contactListAdapter.dispose();
            contactListAdapter = null;
        }
        disposeSourcesAdapter();
    }

    /**
     * Invoked when the options menu is created. Creates our own options menu from the corresponding xml.
     *
     * @param menu the options menu
     */
    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater menuInflater)
    {
        super.onCreateOptionsMenu(menu, menuInflater);
        Activity activity = getActivity();

        // Get the SearchView and set the search configuration
        SearchManager searchManager = (SearchManager) activity.getSystemService(Context.SEARCH_SERVICE);
        this.searchItem = menu.findItem(R.id.search);

        searchItem.setOnActionExpandListener(new MenuItem.OnActionExpandListener()
        {
            @Override
            public boolean onMenuItemActionCollapse(MenuItem item)
            {
                filterContactList("");
                return true; // Return true to collapse action view
            }

            public boolean onMenuItemActionExpand(MenuItem item)
            {
                return true; // Return true to expand action view
            }
        });

        SearchView searchView = (SearchView) searchItem.getActionView();
        searchView.setSearchableInfo(searchManager.getSearchableInfo(activity.getComponentName()));

        int id = searchView.getContext().getResources().getIdentifier("android:id/search_src_text", null, null);
        TextView textView = searchView.findViewById(id);
        textView.setTextColor(getResources().getColor(R.color.white));
        textView.setHintTextColor(getResources().getColor(R.color.white));
        bindSearchListener();
    }

    private void bindSearchListener()
    {
        if (searchItem != null) {
            SearchView searchView = (SearchView) searchItem.getActionView();
            SearchViewListener listener = new SearchViewListener();
            searchView.setOnQueryTextListener(listener);
            searchView.setOnCloseListener(listener);
        }
    }

    private MetaContactListAdapter getContactListAdapter()
    {
        if (contactListAdapter == null) {
            // enable the display of call buttons
            contactListAdapter = new MetaContactListAdapter(this, true);
            contactListAdapter.initModelData();
            aTalkApp.setContactListAdapter(contactListAdapter);
        }
        return contactListAdapter;
    }

    private QueryContactListAdapter getSourcesAdapter()
    {
        if (sourcesAdapter == null) {
            sourcesAdapter = new QueryContactListAdapter(this, getContactListAdapter());
            sourcesAdapter.initModelData();
        }
        return sourcesAdapter;
    }

    private void disposeSourcesAdapter()
    {
        if (sourcesAdapter != null) {
            sourcesAdapter.dispose();
        }
        sourcesAdapter = null;
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

        ExpandableListView.ExpandableListContextMenuInfo info = (ExpandableListView.ExpandableListContextMenuInfo) menuInfo;
        int type = ExpandableListView.getPackedPositionType(info.packedPosition);
        int group = ExpandableListView.getPackedPositionGroup(info.packedPosition);
        int child = ExpandableListView.getPackedPositionChild(info.packedPosition);

        // Only create a context menu for child items
        MenuInflater inflater = getActivity().getMenuInflater();
        if (type == ExpandableListView.PACKED_POSITION_TYPE_GROUP) {
            createGroupCtxMenu(menu, inflater, group);
        }
        else if (type == ExpandableListView.PACKED_POSITION_TYPE_CHILD) {
            createContactCtxMenu(menu, inflater, group, child);
        }
    }

    /**
     * Inflates contact context menu.
     *
     * @param menu the menu to inflate when long press on contact item.
     * @param inflater the menu inflater.
     * @param group clicked group index.
     * @param child clicked contact index.
     */
    private void createContactCtxMenu(ContextMenu menu, MenuInflater inflater, int group, int child)
    {
        // Inflate contact list context menu
        inflater.inflate(R.menu.contact_ctx_menu, menu);

        // Remembers clicked contact
        clickedContact = ((MetaContact) contactListAdapter.getChild(group, child));
        menu.setHeaderTitle(clickedContact.getDisplayName());

        // Checks if close chat option should be visible for this contact
        boolean closeChatVisible = ChatSessionManager.getActiveChat(clickedContact) != null;
        menu.findItem(R.id.close_chat).setVisible(closeChatVisible);

        // Close all chats option should be visible if chatList is not empty
        List<Chat> chatList = ChatSessionManager.getActiveChats();
        boolean visible = ((chatList.size() > 1) || ((chatList.size() == 1) && !closeChatVisible));
        menu.findItem(R.id.close_all_chats).setVisible(visible);

        // may not want to offer erase all contacts' chat history
        menu.findItem(R.id.erase_all_contact_chat_history).setVisible(false);

        // Checks if the re-request authorization item should be visible
        Contact contact = clickedContact.getDefaultContact();
        if (contact == null) {
            logger.warn("No default contact for: " + clickedContact);
            return;
        }

        ProtocolProviderService pps = contact.getProtocolProvider();
        if (pps == null) {
            logger.warn("No protocol provider found for: " + contact);
            return;
        }

        OperationSetExtendedAuthorizations authOpSet = pps.getOperationSet(OperationSetExtendedAuthorizations.class);
        boolean reRequestVisible = false;
        if (authOpSet != null
                && authOpSet.getSubscriptionStatus(contact) != null
                && !authOpSet.getSubscriptionStatus(contact).equals(
                OperationSetExtendedAuthorizations.SubscriptionStatus.Subscribed)) {
            reRequestVisible = true;
        }
        menu.findItem(R.id.re_request_auth).setVisible(reRequestVisible);
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
     * {@inheritDoc}
     */
    @Override
    public boolean onContextItemSelected(MenuItem item)
    {
        FragmentTransaction ft;
        ChatPanel chatPanel = ChatSessionManager.getActiveChat(clickedContact);
        switch (item.getItemId()) {
            case R.id.close_chat:
                if (chatPanel != null)
                    onCloseChat(chatPanel);
                return true;
            case R.id.close_all_chats:
                onCloseAllChats();
                return true;
            case R.id.erase_contact_chat_history:
                EntityListHelper.eraseEntityChatHistory(getContext(), clickedContact, null, null);
                return true;
            case R.id.erase_all_contact_chat_history:
                EntityListHelper.eraseAllContactHistory(getContext());
                return true;
            case R.id.rename_contact:
                // Show rename contact dialog
                ft = getFragmentManager().beginTransaction();
                ft.addToBackStack(null);
                DialogFragment renameFragment = ContactRenameDialog.getInstance(clickedContact);
                renameFragment.show(ft, "dialog");
                return true;
            case R.id.remove_contact:
                EntityListHelper.removeEntity(clickedContact, chatPanel);
                return true;
            case R.id.move_contact:
                // Show move contact dialog
                ft = getFragmentManager().beginTransaction();
                ft.addToBackStack(null);
                DialogFragment newFragment = MoveToGroupDialog.getInstance(clickedContact);
                newFragment.show(ft, "dialog");
                return true;
            case R.id.re_request_auth:
                if (clickedContact != null)
                    requestAuthorization(clickedContact.getDefaultContact());
                return true;
            case R.id.send_contact_file:
                // ChatPanel clickedChat = ChatSessionManager.getActiveChat(clickedContact);
                // FragmentActivity parent = getActivity();
                // AttachOptionDialog attachOptionDialog = new AttachOptionDialog(parent,
                // clickedContact);
                // attachOptionDialog.show();
                return true;
            case R.id.remove_group:
                EntityListHelper.removeMetaContactGroup(clickedGroup);
                return true;
            case R.id.contact_info:
                startContactInfoActivity(clickedContact);
                return true;
            case R.id.contact_ctx_menu_exit:
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    /**
     * Method fired when given chat is being closed.
     *
     * @param closedChat closed <tt>ChatPanel</tt>.
     */
    public void onCloseChat(ChatPanel closedChat)
    {
        ChatSessionManager.removeActiveChat(closedChat);
        if (contactListAdapter != null)
            contactListAdapter.notifyDataSetChanged();
    }

    /**
     * Method fired when all chats are being closed.
     */
    public void onCloseAllChats()
    {
        ChatSessionManager.removeAllActiveChats();
        if (contactListAdapter != null)
            contactListAdapter.notifyDataSetChanged();
    }

    /**
     * Requests authorization for contact.
     *
     * @param contact the contact for which we request authorization
     */
    private void requestAuthorization(final Contact contact)
    {
        final OperationSetExtendedAuthorizations authOpSet
                = contact.getProtocolProvider().getOperationSet(OperationSetExtendedAuthorizations.class);
        if (authOpSet == null)
            return;

        new Thread()
        {
            @Override
            public void run()
            {
                AuthorizationRequest request = AndroidGUIActivator.getLoginRenderer()
                        .getAuthorizationHandler().createAuthorizationRequest(contact);
                if (request == null)
                    return;

                try {
                    authOpSet.reRequestAuthorization(request, contact);
                } catch (OperationFailedException e) {
                    Context ctx = aTalkApp.getGlobalContext();
                    DialogActivity.showConfirmDialog(ctx, ctx.getString(R.string.service_gui_RE_REQUEST_AUTHORIZATION),
                            e.getMessage(), null, null);
                }
            }
        }.start();
    }

    /**
     * Starts the {@link AccountInfoPresenceActivity} for clicked {@link Account}
     *
     * @param metaContact the <tt>Contact</tt> for which info to be opened.
     */
    private void startContactInfoActivity(MetaContact metaContact)
    {
        Intent statusIntent = new Intent(getActivity(), ContactInfoActivity.class);
        statusIntent.putExtra(ContactInfoActivity.INTENT_CONTACT_ID, metaContact.getDisplayName());
        startActivity(statusIntent);
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
     * cmeng: when metaContact is owned by two different accounts, the first launched chatSession
     * will take predominant over subsequent metaContact chat session launches by another account
     */
    @Override
    public boolean onChildClick(ExpandableListView listView, View v, int groupPosition, int childPosition, long id)
    {
        BaseContactListAdapter adapter = (BaseContactListAdapter) listView.getExpandableListAdapter();
        int position = adapter.getListIndex(groupPosition, childPosition);

        contactListView.setSelection(position);
        adapter.invalidateViews();

        Object clicked = adapter.getChild(groupPosition, childPosition);
        if (!(clicked instanceof MetaContact)) {
            logger.debug("No metaContact at position: " + groupPosition + ", " + childPosition);
            return false;
        }

        MetaContact metaContact = (MetaContact) clicked;
        if (metaContact.getDefaultContact() == null) {
            aTalkApp.showToastMessage(R.string.service_gui_CONTACT_INVALID,  metaContact.getDisplayName());
            return false;
        }

        // Default to telephony access if contact is not a valid entityJid
        if (metaContact.getDefaultContact().getJid() instanceof DomainJid) {
            String domainJid = metaContact.getDefaultContact().getJid().toString();
            TelephonyFragment extPhone = TelephonyFragment.newInstance(domainJid);
            getActivity().getSupportFragmentManager().beginTransaction().replace(android.R.id.content, extPhone).commit();
            return true;
        }
        if (!metaContact.getContactsForOperationSet(OperationSetBasicInstantMessaging.class).isEmpty()) {
            startChatActivity(metaContact);
            return true;
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
    @TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
    public boolean onGroupClick(ExpandableListView parent, View v, int groupPosition, long id)
    {
        if (contactListView.isGroupExpanded(groupPosition))
            contactListView.collapseGroup(groupPosition);
        else {
            contactListView.expandGroup(groupPosition, true);
        }
        return true;
    }

    public MetaContact getClickedContact()
    {
        return clickedContact;
    }

    /**
     * Starts the chat activity for the given metaContact.
     *
     * @param descriptor <tt>MetaContact</tt> for which chat activity will be started.
     */
    private void startChatActivity(Object descriptor)
    {
        Intent chatIntent = ChatSessionManager.getChatIntent(descriptor);
        if (chatIntent != null) {
            startActivity(chatIntent);
        }
        else {
            logger.warn("Failed to start chat with " + descriptor);
        }
    }

    /**
     * Filters contact list for given <tt>query</tt>.
     *
     * @param query the query string that will be used for filtering contacts.
     */
    private void filterContactList(String query)
    {
        if (StringUtils.isNullOrEmpty(query)) {
            // Cancel any pending queries
            disposeSourcesAdapter();

            // Display the contact list
            if (contactListView.getExpandableListAdapter() != getContactListAdapter()) {
                contactListView.setAdapter(getContactListAdapter());
                contactListAdapter.filterData("");
            }

            // Restore previously collapsed groups
            if (listExpandHandler != null) {
                listExpandHandler.bindAndRestore();
            }
        }
        else {
            // Unbind group expand memory
            if (listExpandHandler != null)
                listExpandHandler.unbind();

            // Display search results
            if (contactListView.getExpandableListAdapter() != getSourcesAdapter()) {
                contactListView.setAdapter(getSourcesAdapter());
            }

            // Update query string
            sourcesAdapter.filterData(query);
        }
    }

    /**
     * Class used to implement <tt>SearchView</tt> listeners for compatibility purposes.
     */
    class SearchViewListener implements SearchView.OnQueryTextListener, SearchView.OnCloseListener
    {
        @Override
        public boolean onClose()
        {
            filterContactList("");
            return true;
        }

        @Override
        public boolean onQueryTextChange(String query)
        {
            filterContactList(query);
            return true;
        }

        @Override
        public boolean onQueryTextSubmit(String query)
        {
            filterContactList(query);
            return true;
        }
    }
}
