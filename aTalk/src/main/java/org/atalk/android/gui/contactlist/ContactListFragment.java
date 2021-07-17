/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.android.gui.contactlist;

import android.app.SearchManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import android.widget.ExpandableListView.OnGroupClickListener;
import android.widget.PopupMenu.OnMenuItemClickListener;

import net.java.sip.communicator.service.contactlist.MetaContact;
import net.java.sip.communicator.service.contactlist.MetaContactGroup;
import net.java.sip.communicator.service.gui.Chat;
import net.java.sip.communicator.service.protocol.*;
import net.java.sip.communicator.util.ConfigurationUtils;

import org.apache.commons.lang3.StringUtils;
import org.atalk.android.R;
import org.atalk.android.aTalkApp;
import org.atalk.android.gui.AndroidGUIActivator;
import org.atalk.android.gui.aTalk;
import org.atalk.android.gui.account.Account;
import org.atalk.android.gui.account.AccountInfoPresenceActivity;
import org.atalk.android.gui.chat.ChatPanel;
import org.atalk.android.gui.chat.ChatSessionManager;
import org.atalk.android.gui.chat.chatsession.ChatSessionFragment;
import org.atalk.android.gui.contactlist.model.*;
import org.atalk.android.gui.dialogs.DialogActivity;
import org.atalk.android.gui.share.ShareActivity;
import org.atalk.android.gui.util.EntityListHelper;
import org.atalk.android.gui.util.ViewUtil;
import org.atalk.service.osgi.OSGiFragment;
import org.jetbrains.annotations.NotNull;
import org.jxmpp.jid.DomainJid;
import org.jxmpp.jid.Jid;

import java.util.List;

import androidx.fragment.app.*;
import timber.log.Timber;

/**
 * Class to display the MetaContacts in Expandable List View
 *
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
public class ContactListFragment extends OSGiFragment implements OnGroupClickListener
{
    /**
     * Search options menu items.
     */
    private MenuItem mSearchItem;

    /**
     * Contact TTS option item
     */
    private MenuItem mContactTtsEnable;

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
     * Stores last clicked <tt>MetaContact</tt>; take care activity destroyed by OS.
     */
    protected static MetaContact mClickedContact;

    /**
     * Stores recently clicked contact group.
     */
    private MetaContactGroup mClickedGroup;

    /**
     * Contact list item scroll position.
     */
    private static int scrollPosition;

    /**
     * Contact list scroll top position.
     */
    private static int scrollTopPosition;

    private Context mContext = null;

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
    public void onAttach(Context context)
    {
        super.onAttach(context);
        mContext = context;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public View onCreateView(@NotNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState)
    {
        if (AndroidGUIActivator.bundleContext == null) {
            return null;
        }

        ViewGroup content = (ViewGroup) inflater.inflate(R.layout.contact_list, container, false);
        contactListView = content.findViewById(R.id.contactListView);
        contactListView.setSelector(R.drawable.list_selector_state);
        contactListView.setOnGroupClickListener(this);
        initContactListAdapter();

        return content;
    }

    /**
     * Initialize the contact list adapter;
     */
    private void initContactListAdapter()
    {
        contactListView.setAdapter(getContactListAdapter());

        // Attach contact groups expand memory
        listExpandHandler = new MetaGroupExpandHandler(contactListAdapter, contactListView);
        listExpandHandler.bindAndRestore();

        // Restore search state based on entered text
        if (mSearchItem != null) {
            SearchView searchView = (SearchView) mSearchItem.getActionView();
            int id = searchView.getContext().getResources()
                    .getIdentifier("android:id/search_src_text", null, null);

            String filter = ViewUtil.toString(searchView.findViewById(id));
            filterContactList(filter);
            bindSearchListener();
        }
        else {
            contactListAdapter.filterData("");
        }

        // Restore scroll position
        contactListView.setSelectionFromTop(scrollPosition, scrollTopPosition);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onResume()
    {
        super.onResume();

        // Invalidate view to update read counter and expand groups (collapsed when access settings)
        if (contactListAdapter != null) {
            contactListAdapter.expandAllGroups();
            contactListAdapter.invalidateViews();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onDestroy()
    {
        // Unbind search listener
        if (mSearchItem != null) {
            SearchView searchView = (SearchView) mSearchItem.getActionView();
            searchView.setOnQueryTextListener(null);
            searchView.setOnCloseListener(null);
        }

        if (contactListView != null) {
            // Save scroll position
            scrollPosition = contactListView.getFirstVisiblePosition();
            View itemView = contactListView.getChildAt(0);
            scrollTopPosition = (itemView == null) ? 0 : itemView.getTop();

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
        super.onDestroy();
    }

    /**
     * Invoked when the options menu is created. Creates our own options menu from the corresponding xml.
     *
     * @param menu the options menu
     */
    @Override
    public void onCreateOptionsMenu(@NotNull Menu menu, @NotNull MenuInflater menuInflater)
    {
        super.onCreateOptionsMenu(menu, menuInflater);

        // Get the SearchView and set the search configuration
        SearchManager searchManager = (SearchManager) aTalkApp.getGlobalContext().getSystemService(Context.SEARCH_SERVICE);
        mSearchItem = menu.findItem(R.id.search);

        mSearchItem.setOnActionExpandListener(new MenuItem.OnActionExpandListener()
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

        SearchView searchView = (SearchView) mSearchItem.getActionView();
        searchView.setSearchableInfo(searchManager.getSearchableInfo(((FragmentActivity) mContext).getComponentName()));

        int id = searchView.getContext().getResources()
                .getIdentifier("android:id/search_src_text", null, null);
        TextView textView = searchView.findViewById(id);
        textView.setTextColor(getResources().getColor(R.color.white));
        textView.setHintTextColor(getResources().getColor(R.color.white));
        bindSearchListener();
    }

    private void bindSearchListener()
    {
        if (mSearchItem != null) {
            SearchView searchView = (SearchView) mSearchItem.getActionView();
            SearchViewListener listener = new SearchViewListener();
            searchView.setOnQueryTextListener(listener);
            searchView.setOnCloseListener(listener);
        }
    }

    /**
     * Get the MetaContact list with media buttons
     *
     * @return MetaContact list showing the media buttons
     */
    public MetaContactListAdapter getContactListAdapter()
    {
        if (contactListAdapter == null) {
            contactListAdapter = new MetaContactListAdapter(this, true);
            contactListAdapter.initModelData();
        }

        // Do not include groups with zero member in main contact list
        // contactListAdapter.nonZeroContactGroupList();
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

    public void showPopUpMenuGroup(View groupView, MetaContactGroup group)
    {
        // Inflate chatRoom list popup menu
        PopupMenu popup = new PopupMenu(mContext, groupView);
        Menu menu = popup.getMenu();
        popup.getMenuInflater().inflate(R.menu.group_menu, menu);
        popup.setOnMenuItemClickListener(new PopupMenuItemClick());

        // Remembers clicked metaContactGroup
        mClickedGroup = group;
        popup.show();
    }

    /**
     * Inflates contact Item popup menu.
     * Avoid using android contextMenu (in fragment) - truncated menu list
     *
     * @param contactView click view.
     * @param metaContact an instance of MetaContact.
     */
    public void showPopupMenuContact(View contactView, MetaContact metaContact)
    {
        // Inflate contact list popup menu
        PopupMenu popup = new PopupMenu(mContext, contactView);
        Menu menu = popup.getMenu();
        popup.getMenuInflater().inflate(R.menu.contact_ctx_menu, menu);
        popup.setOnMenuItemClickListener(new PopupMenuItemClick());

        // Remembers clicked contact
        mClickedContact = metaContact;

        // Checks if close chat option should be visible for this contact
        boolean closeChatVisible = ChatSessionManager.getActiveChat(mClickedContact) != null;
        menu.findItem(R.id.close_chat).setVisible(closeChatVisible);

        // Close all chats option should be visible if chatList is not empty
        List<Chat> chatList = ChatSessionManager.getActiveChats();
        boolean visible = ((chatList.size() > 1) || ((chatList.size() == 1) && !closeChatVisible));
        menu.findItem(R.id.close_all_chats).setVisible(visible);

        // Do not want to offer erase all contacts' chat history
        menu.findItem(R.id.erase_all_contact_chat_history).setVisible(false);

        // Checks if the re-request authorization item should be visible
        Contact contact = mClickedContact.getDefaultContact();
        if (contact == null) {
            Timber.w("No default contact for: %s", mClickedContact);
            return;
        }

        // update TTS enable option item title for the contact only if not DomainJid
        mContactTtsEnable = menu.findItem(R.id.contact_tts_enable);
        Jid jid = contact.getJid();
        if ((jid == null) || jid instanceof DomainJid) {
            mContactTtsEnable.setVisible(false);
        }
        else {
            String tts_option = aTalkApp.getResString(contact.isTtsEnable()
                    ? R.string.service_gui_TTS_DISABLE : R.string.service_gui_TTS_ENABLE);
            mContactTtsEnable.setTitle(tts_option);
            mContactTtsEnable.setVisible(ConfigurationUtils.isTtsEnable());
        }

        ProtocolProviderService pps = contact.getProtocolProvider();
        if (pps == null) {
            Timber.w("No protocol provider found for: %s", contact);
            return;
        }

        // Cannot send unsubscribed or move group if user in not online
        boolean userRegistered = pps.isRegistered();
        menu.findItem(R.id.remove_contact).setVisible(userRegistered);
        menu.findItem(R.id.move_contact).setVisible(userRegistered);

        OperationSetExtendedAuthorizations authOpSet = pps.getOperationSet(OperationSetExtendedAuthorizations.class);
        boolean reRequestVisible = false;
        if (authOpSet != null
                && authOpSet.getSubscriptionStatus(contact) != null
                && !authOpSet.getSubscriptionStatus(contact).equals(
                OperationSetExtendedAuthorizations.SubscriptionStatus.Subscribed)) {
            reRequestVisible = true;
        }
        menu.findItem(R.id.re_request_auth).setVisible(reRequestVisible);
        popup.show();
    }

    /**
     * Interface responsible for receiving menu item click events if the items
     * themselves do not have individual item click listeners.
     */
    private class PopupMenuItemClick implements OnMenuItemClickListener
    {
        /**
         * This method will be invoked when a menu item is clicked if the item
         * itself did not already handle the event.
         *
         * @param item the menu item that was clicked
         * @return {@code true} if the event was handled, {@code false} otherwise
         */
        @Override
        public boolean onMenuItemClick(MenuItem item)
        {
            FragmentTransaction ft;
            ChatPanel chatPanel = ChatSessionManager.getActiveChat(mClickedContact);

            switch (item.getItemId()) {
                case R.id.close_chat:
                    if (chatPanel != null)
                        onCloseChat(chatPanel);
                    return true;

                case R.id.close_all_chats:
                    onCloseAllChats();
                    return true;

                case R.id.erase_contact_chat_history:
                    EntityListHelper.eraseEntityChatHistory(getContext(), mClickedContact, null, null);
                    return true;

                case R.id.erase_all_contact_chat_history:
                    EntityListHelper.eraseAllEntityHistory(getContext());
                    return true;

                case R.id.contact_tts_enable:
                    if (mClickedContact != null) {
                        Contact contact = mClickedContact.getDefaultContact();
                        if (contact.isTtsEnable()) {
                            contact.setTtsEnable(false);
                            mContactTtsEnable.setTitle(R.string.service_gui_TTS_ENABLE);
                        }
                        else {
                            contact.setTtsEnable(true);
                            mContactTtsEnable.setTitle(R.string.service_gui_TTS_DISABLE);
                        }
                        ChatSessionManager.createChatForChatId(mClickedContact.getMetaUID(),
                                ChatSessionManager.MC_CHAT).updateChatTtsOption();
                    }
                    return true;

                case R.id.rename_contact:
                    // Show rename contact dialog
                    ft = getParentFragmentManager().beginTransaction();
                    ft.addToBackStack(null);
                    DialogFragment renameFragment = ContactRenameDialog.getInstance(mClickedContact);
                    renameFragment.show(ft, "renameDialog");
                    return true;

                case R.id.remove_contact:
                    EntityListHelper.removeEntity(mContext, mClickedContact, chatPanel);
                    return true;

                case R.id.move_contact:
                    // Show move contact dialog
                    ft = getParentFragmentManager().beginTransaction();
                    ft.addToBackStack(null);
                    DialogFragment newFragment = MoveToGroupDialog.getInstance(mClickedContact);
                    newFragment.show(ft, "moveDialog");
                    return true;

                case R.id.re_request_auth:
                    if (mClickedContact != null)
                        requestAuthorization(mClickedContact.getDefaultContact());
                    return true;

                case R.id.send_contact_file:
                    // ChatPanel clickedChat = ChatSessionManager.getActiveChat(clickedContact);
                    // AttachOptionDialog attachOptionDialog = new AttachOptionDialog(mActivity,
                    // clickedContact);
                    // attachOptionDialog.show();
                    return true;

                case R.id.remove_group:
                    EntityListHelper.removeMetaContactGroup(mClickedGroup);
                    return true;

                case R.id.contact_info:
                    startContactInfoActivity(mClickedContact);
                    return true;

                case R.id.contact_ctx_menu_exit:
                    return true;
                default:
                    return false;
            }
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
        Intent statusIntent = new Intent(mContext, ContactInfoActivity.class);
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
     * Expands/collapses the group given by <tt>groupPosition</tt>.
     *
     * @param parent the parent expandable list view
     * @param v the view
     * @param groupPosition the position of the group
     * @param id the identifier
     * @return <tt>true</tt> if the group click action has been performed
     */
    public boolean onGroupClick(ExpandableListView parent, View v, int groupPosition, long id)
    {
        if (contactListView.isGroupExpanded(groupPosition))
            contactListView.collapseGroup(groupPosition);
        else {
            contactListView.expandGroup(groupPosition, true);
        }
        return true;
    }

    /**
     * cmeng: when metaContact is owned by two different user accounts, the first launched chatSession
     * will take predominant over subsequent metaContact chat session launches by another account
     */
    public boolean startChat(MetaContact metaContact)
    {
        if (metaContact.getDefaultContact() == null) {
            aTalkApp.showToastMessage(R.string.service_gui_CONTACT_INVALID, metaContact.getDisplayName());
            return false;
        }

        // Default for domainJid - always show chat session
        if (metaContact.getDefaultContact().getJid() instanceof DomainJid) {
            startChatActivity(metaContact);
            return true;
        }

        if (!metaContact.getContactsForOperationSet(OperationSetBasicInstantMessaging.class).isEmpty()) {
            startChatActivity(metaContact);
            return true;
        }
        return false;
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
            Intent shareIntent = ShareActivity.getShareIntent(chatIntent);
            if (shareIntent != null) {
                chatIntent = shareIntent;
            }
            startActivity(chatIntent);
        }
        else {
            Timber.w("Failed to start chat with %s", descriptor);
        }
    }

    public MetaContact getClickedContact()
    {
        return mClickedContact;
    }

    /**
     * Filters contact list for given <tt>query</tt>.
     *
     * @param query the query string that will be used for filtering contacts.
     */
    private void filterContactList(String query)
    {
        if (StringUtils.isEmpty(query)) {
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

    /**
     * Update the unread message badge for the specified metaContact
     * The unread count is pre-stored in the metaContact
     *
     * @param metaContact The MetaContact to be updated
     */
    public void updateUnreadCount(final MetaContact metaContact)
    {
        runOnUiThread(() -> {
            if ((metaContact != null) && (contactListAdapter != null)) {
                int unreadCount = metaContact.getUnreadCount();
                contactListAdapter.updateUnreadCount(metaContact, unreadCount);

                Fragment csf = aTalk.getFragment(aTalk.CHAT_SESSION_FRAGMENT);
                if (csf instanceof ChatSessionFragment) {
                    ((ChatSessionFragment) csf).updateUnreadCount(metaContact.getDefaultContact().getAddress(), unreadCount);
                }
            }
        });
    }
}
