/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.android.gui.contactlist;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ExpandableListAdapter;
import android.widget.ExpandableListView;
import android.widget.ExpandableListView.OnGroupClickListener;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.PopupMenu;
import androidx.appcompat.widget.PopupMenu.OnMenuItemClickListener;
import androidx.appcompat.widget.SearchView;
import androidx.core.view.MenuProvider;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;

import java.util.List;

import net.java.sip.communicator.service.contactlist.MetaContact;
import net.java.sip.communicator.service.contactlist.MetaContactGroup;
import net.java.sip.communicator.service.gui.Chat;
import net.java.sip.communicator.service.protocol.AuthorizationRequest;
import net.java.sip.communicator.service.protocol.Contact;
import net.java.sip.communicator.service.protocol.OperationFailedException;
import net.java.sip.communicator.service.protocol.OperationSetBasicInstantMessaging;
import net.java.sip.communicator.service.protocol.OperationSetExtendedAuthorizations;
import net.java.sip.communicator.service.protocol.OperationSetExtendedAuthorizations.SubscriptionStatus;
import net.java.sip.communicator.service.protocol.ProtocolProviderService;
import net.java.sip.communicator.util.ConfigurationUtils;

import org.apache.commons.lang3.StringUtils;
import org.atalk.android.BaseFragment;
import org.atalk.android.R;
import org.atalk.android.aTalkApp;
import org.atalk.android.gui.AppGUIActivator;
import org.atalk.android.gui.aTalk;
import org.atalk.android.gui.account.Account;
import org.atalk.android.gui.account.AccountInfoPresenceActivity;
import org.atalk.android.gui.account.AndroidLoginRenderer;
import org.atalk.android.gui.chat.ChatPanel;
import org.atalk.android.gui.chat.ChatSessionManager;
import org.atalk.android.gui.chat.chatsession.ChatSessionFragment;
import org.atalk.android.gui.contactlist.model.MetaContactListAdapter;
import org.atalk.android.gui.contactlist.model.MetaGroupExpandHandler;
import org.atalk.android.gui.contactlist.model.QueryContactListAdapter;
import org.atalk.android.gui.dialogs.DialogActivity;
import org.atalk.android.gui.share.ShareActivity;
import org.atalk.android.gui.util.EntityListHelper;
import org.atalk.android.gui.util.ViewUtil;
import org.jetbrains.annotations.NotNull;
import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smackx.blocking.BlockingCommandManager;
import org.jxmpp.jid.DomainJid;
import org.jxmpp.jid.Jid;

import timber.log.Timber;

/**
 * Class to display the MetaContacts in Expandable List View
 *
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
public class ContactListFragment extends BaseFragment
        implements MenuProvider, OnGroupClickListener, EntityListHelper.TaskCompleteListener {
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
     * Stores last clicked <code>MetaContact</code>; take care activity destroyed by OS.
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

    private int eraseMode = -1;

    /**
     * Creates new instance of <code>ContactListFragment</code>.
     */
    public ContactListFragment() {
        super();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public View onCreateView(@NotNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        if (AppGUIActivator.bundleContext == null) {
            return null;
        }

        ViewGroup content = (ViewGroup) inflater.inflate(R.layout.contact_list, container, false);
        contactListView = content.findViewById(R.id.contactListView);
        contactListView.setSelector(R.drawable.list_selector_state);
        contactListView.setOnGroupClickListener(this);
        initContactListAdapter();

        requireActivity().addMenuProvider(this);
        return content;
    }

    /**
     * Initialize the contact list adapter;
     */
    private void initContactListAdapter() {
        contactListView.setAdapter(getContactListAdapter());

        // Attach contact groups expand memory
        listExpandHandler = new MetaGroupExpandHandler(contactListAdapter, contactListView);
        listExpandHandler.bindAndRestore();

        // Restore search state based on entered text
        if (mSearchItem != null) {
            SearchView searchView = (SearchView) mSearchItem.getActionView();
            if (searchView != null) {
                String filter = ViewUtil.toString(searchView.findViewById(R.id.search_src_text));
                filterContactList(filter);
                bindSearchListener();
            }
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
    public void onResume() {
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
    public void onDestroy() {
        // Unbind search listener
        if (mSearchItem != null) {
            SearchView searchView = (SearchView) mSearchItem.getActionView();
            if (searchView != null) {
                searchView.setOnQueryTextListener(null);
                searchView.setOnCloseListener(null);
            }
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
     * Creates our own options menu from the corresponding xml.
     */
    @Override
    public void onCreateMenu(@NonNull Menu menu, @NonNull MenuInflater menuInflater) {
        // Get the SearchView MenuItem
        mSearchItem = menu.findItem(R.id.search);
        if (mSearchItem == null)
            return;

        mSearchItem.setOnActionExpandListener(new MenuItem.OnActionExpandListener() {
            @Override
            public boolean onMenuItemActionCollapse(@NonNull MenuItem item) {
                filterContactList("");
                return true; // Return true to collapse action view
            }

            public boolean onMenuItemActionExpand(@NonNull MenuItem item) {
                return true; // Return true to expand action view
            }
        });
        bindSearchListener();
    }

    @Override
    public boolean onMenuItemSelected(@NonNull MenuItem menuItem) {
        return false;
    }

    private void bindSearchListener() {
        if (mSearchItem != null) {
            SearchView searchView = (SearchView) mSearchItem.getActionView();
            if (searchView != null) {
                SearchViewListener listener = new SearchViewListener();
                searchView.setOnQueryTextListener(listener);
                searchView.setOnCloseListener(listener);
            }
        }
    }

    /**
     * Get the MetaContact list with media buttons
     *
     * @return MetaContact list showing the media buttons
     */
    public MetaContactListAdapter getContactListAdapter() {
        if (contactListAdapter == null) {
            contactListAdapter = new MetaContactListAdapter(this, true);
            contactListAdapter.initModelData();
        }

        // Do not include groups with zero member in main contact list
        // contactListAdapter.nonZeroContactGroupList();
        return contactListAdapter;
    }

    private QueryContactListAdapter getSourcesAdapter() {
        if (sourcesAdapter == null) {
            sourcesAdapter = new QueryContactListAdapter(this, getContactListAdapter());
            sourcesAdapter.initModelData();
        }
        return sourcesAdapter;
    }

    private void disposeSourcesAdapter() {
        if (sourcesAdapter != null) {
            sourcesAdapter.dispose();
        }
        sourcesAdapter = null;
    }

    public void showPopUpMenuGroup(View groupView, MetaContactGroup group) {
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
    public void showPopupMenuContact(View contactView, MetaContact metaContact) {
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
        Jid contactJid = contact.getJid();
        if ((contactJid == null) || contactJid instanceof DomainJid) {
            mContactTtsEnable.setVisible(false);
        }
        else {
            String tts_option = aTalkApp.getResString(contact.isTtsEnable()
                    ? R.string.tts_disable : R.string.tts_enable);
            mContactTtsEnable.setTitle(tts_option);
            mContactTtsEnable.setVisible(ConfigurationUtils.isTtsEnable());
        }

        ProtocolProviderService pps = contact.getProtocolProvider();
        if (pps == null) {
            Timber.w("No protocol provider found for: %s", contact);
            return;
        }
        boolean isOnline = pps.isRegistered();

        MenuItem miContactBlock = menu.findItem(R.id.contact_blocking);
        if (isOnline) {
            XMPPConnection connection = pps.getConnection();
            boolean isParent = (contactJid != null) && contactJid.isParentOf(connection.getUser());

            // Do not allow user to block himself
            miContactBlock.setEnabled(!isParent);
            miContactBlock.setTitle(contact.isContactBlock() ? R.string.contact_unblock : R.string.contact_block);

            try {
                boolean isSupported = BlockingCommandManager.getInstanceFor(connection).isSupportedByServer();
                miContactBlock.setVisible(isSupported);

            } catch (Exception e) {
                Timber.w("Blocking Command: %s", e.getMessage());
            }
        }
        else {
            miContactBlock.setVisible(false);
        }

        // Cannot send unsubscribed or move group if user in not online
        menu.findItem(R.id.remove_contact).setVisible(isOnline);
        menu.findItem(R.id.move_contact).setVisible(isOnline);
        menu.findItem(R.id.contact_info).setVisible(isOnline);

        OperationSetExtendedAuthorizations authOpSet = pps.getOperationSet(OperationSetExtendedAuthorizations.class);
        boolean reRequestVisible = isOnline
                && (authOpSet != null)
                && authOpSet.getSubscriptionStatus(contact) != null
                && !authOpSet.getSubscriptionStatus(contact).equals(SubscriptionStatus.Subscribed);
        menu.findItem(R.id.re_request_auth).setVisible(reRequestVisible);

        // Show content menu
        popup.show();
    }

    /**
     * Interface responsible for receiving menu item click events if the items
     * themselves do not have individual item click listeners.
     */
    private class PopupMenuItemClick implements OnMenuItemClickListener {
        /**
         * This method will be invoked when a menu item is clicked if the item
         * itself did not already handle the event.
         *
         * @param item the menu item that was clicked
         *
         * @return {@code true} if the event was handled, {@code false} otherwise
         */
        @Override
        public boolean onMenuItemClick(MenuItem item) {
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
                    eraseMode = EntityListHelper.SINGLE_ENTITY;
                    EntityListHelper.eraseEntityChatHistory(ContactListFragment.this, mClickedContact, null, null);
                    return true;

                case R.id.erase_all_contact_chat_history:
                    eraseMode = EntityListHelper.ALL_ENTITY;
                    EntityListHelper.eraseAllEntityHistory(ContactListFragment.this);
                    return true;

                case R.id.contact_tts_enable:
                    if (mClickedContact != null) {
                        Contact contact = mClickedContact.getDefaultContact();
                        if (contact.isTtsEnable()) {
                            contact.setTtsEnable(false);
                            mContactTtsEnable.setTitle(R.string.tts_enable);
                        }
                        else {
                            contact.setTtsEnable(true);
                            mContactTtsEnable.setTitle(R.string.tts_disable);
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

                case R.id.contact_blocking:
                    if (mClickedContact != null) {
                        Contact contact = mClickedContact.getDefaultContact();
                        EntityListHelper.setEntityBlockState(mContext, contact, !contact.isContactBlock());
                    }
                    return true;

                case R.id.remove_contact:
                    eraseMode = EntityListHelper.SINGLE_ENTITY;
                    EntityListHelper.removeEntity(ContactListFragment.this, mClickedContact, chatPanel);
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
     * @param closedChat closed <code>ChatPanel</code>.
     */
    public void onCloseChat(ChatPanel closedChat) {
        ChatSessionManager.removeActiveChat(closedChat);
        if (contactListAdapter != null)
            contactListAdapter.notifyDataSetChanged();
    }

    /**
     * Method fired when all chats are being closed.
     */
    public void onCloseAllChats() {
        ChatSessionManager.removeAllActiveChats();
        if (contactListAdapter != null)
            contactListAdapter.notifyDataSetChanged();
    }

    @Override
    public void onTaskComplete(int msgCount, List<String> deletedUUIDs) {
        aTalkApp.showToastMessage(R.string.history_purge_count, msgCount);
        if (EntityListHelper.SINGLE_ENTITY == eraseMode) {
            ChatPanel clickedChat = ChatSessionManager.getActiveChat(mClickedContact);
            if (clickedChat != null) {
                onCloseChat(clickedChat);
            }
        }
        else if (EntityListHelper.ALL_ENTITY == eraseMode) {
            onCloseAllChats();
        }
        else { // failed
            String errMsg = getString(R.string.history_purge_error, mClickedContact.getDisplayName());
            aTalkApp.showToastMessage(errMsg);
        }
    }

    /**
     * Requests authorization for contact.
     *
     * @param contact the contact for which we request authorization
     */
    private void requestAuthorization(final Contact contact) {
        final OperationSetExtendedAuthorizations authOpSet
                = contact.getProtocolProvider().getOperationSet(OperationSetExtendedAuthorizations.class);
        if (authOpSet == null)
            return;

        new Thread() {
            @Override
            public void run() {
                AndroidLoginRenderer loginRenderer = AppGUIActivator.getLoginRenderer();
                AuthorizationRequest request = (loginRenderer == null) ?
                        null : loginRenderer.getAuthorizationHandler().createAuthorizationRequest(contact);
                if (request == null)
                    return;

                try {
                    authOpSet.reRequestAuthorization(request, contact);
                } catch (OperationFailedException e) {
                    Context ctx = aTalkApp.getInstance();
                    DialogActivity.showConfirmDialog(ctx, ctx.getString(R.string.request_authorization),
                            e.getMessage(), null, null);
                }
            }
        }.start();
    }

    /**
     * Starts the {@link AccountInfoPresenceActivity} for clicked {@link Account}
     *
     * @param metaContact the <code>Contact</code> for which info to be opened.
     */
    private void startContactInfoActivity(MetaContact metaContact) {
        Intent statusIntent = new Intent(mContext, ContactInfoActivity.class);
        statusIntent.putExtra(ContactInfoActivity.INTENT_CONTACT_ID, metaContact.getDisplayName());
        startActivity(statusIntent);
    }

    /**
     * Returns the contact list view.
     *
     * @return the contact list view
     */
    public ExpandableListView getContactListView() {
        return contactListView;
    }

    /**
     * Expands/collapses the group given by <code>groupPosition</code>.
     *
     * @param parent the parent expandable list view
     * @param v the view
     * @param groupPosition the position of the group
     * @param id the identifier
     *
     * @return <code>true</code> if the group click action has been performed
     */
    public boolean onGroupClick(ExpandableListView parent, View v, int groupPosition, long id) {
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
    public void startChat(MetaContact metaContact) {
        if (metaContact.getDefaultContact() == null) {
            aTalkApp.showToastMessage(R.string.contact_invalid, metaContact.getDisplayName());
        }

        // Default for domainJid - always show chat session
        if (metaContact.getDefaultContact().getJid() instanceof DomainJid) {
            startChatActivity(metaContact);
        }

        if (!metaContact.getContactsForOperationSet(OperationSetBasicInstantMessaging.class).isEmpty()) {
            startChatActivity(metaContact);
        }
    }

    /**
     * Starts the chat activity for the given metaContact.
     *
     * @param descriptor <code>MetaContact</code> for which chat activity will be started.
     */
    private void startChatActivity(Object descriptor) {
        Intent chatIntent = ChatSessionManager.getChatIntent(descriptor);

        if (chatIntent != null) {
            // Get share object parameters for use with chatIntent if any.
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

    public MetaContact getClickedContact() {
        return mClickedContact;
    }

    /**
     * Filters contact list for given <code>query</code>.
     *
     * @param query the query string that will be used for filtering contacts.
     */
    private void filterContactList(String query) {
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
     * Class used to implement <code>SearchView</code> listeners for compatibility purposes.
     */
    class SearchViewListener implements SearchView.OnQueryTextListener, SearchView.OnCloseListener {
        @Override
        public boolean onQueryTextSubmit(String query) {
            filterContactList(query);
            return true;
        }

        @Override
        public boolean onQueryTextChange(String query) {
            filterContactList(query);
            return true;
        }

        @Override
        public boolean onClose() {
            filterContactList("");
            return true;
        }
    }

    /**
     * Update the unread message badge for the specified metaContact
     * The unread count is pre-stored in the metaContact
     *
     * @param metaContact The MetaContact to be updated
     */
    public void updateUnreadCount(final MetaContact metaContact) {
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
