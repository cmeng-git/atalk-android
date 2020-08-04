/*
 * aTalk, android VoIP and Instant Messaging client
 * Copyright 2014 Eng Chong Meng
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.atalk.android.gui.chatroomslist;

import android.app.SearchManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import android.widget.ExpandableListView.OnGroupClickListener;
import android.widget.PopupMenu.OnMenuItemClickListener;

import net.java.sip.communicator.impl.muc.MUCActivator;
import net.java.sip.communicator.service.gui.Chat;
import net.java.sip.communicator.service.muc.ChatRoomProviderWrapper;
import net.java.sip.communicator.service.muc.ChatRoomWrapper;
import net.java.sip.communicator.service.protocol.ChatRoomMemberRole;
import net.java.sip.communicator.service.protocol.ProtocolProviderService;
import net.java.sip.communicator.util.ConfigurationUtils;

import org.apache.commons.lang3.StringUtils;
import org.atalk.android.R;
import org.atalk.android.aTalkApp;
import org.atalk.android.gui.AndroidGUIActivator;
import org.atalk.android.gui.aTalk;
import org.atalk.android.gui.chat.ChatPanel;
import org.atalk.android.gui.chat.ChatSessionManager;
import org.atalk.android.gui.chat.chatsession.ChatSessionFragment;
import org.atalk.android.gui.chatroomslist.model.*;
import org.atalk.android.gui.share.ShareActivity;
import org.atalk.android.gui.util.EntityListHelper;
import org.atalk.android.gui.util.ViewUtil;
import org.atalk.service.osgi.OSGiFragment;
import org.jetbrains.annotations.NotNull;
import org.jxmpp.util.XmppStringUtils;

import java.util.List;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;
import timber.log.Timber;

/**
 * Class to display the ChatRoom in Expandable List View
 *
 * @author Eng Chong Meng
 */
public class ChatRoomListFragment extends OSGiFragment implements OnGroupClickListener
{
    /**
     * Search options menu items.
     */
    private MenuItem mSearchItem;

    /**
     * ChatRoom TTS option item
     */
    private MenuItem mChatRoomTtsEnable;

    /**
     * ChatRoom list data model.
     */
    private ChatRoomListAdapter chatRoomListAdapter;

    /**
     * ChatRoom groups expand memory.
     */
    private ChatRoomGroupExpandHandler listExpandHandler;

    /**
     * List model used to search chatRoom list and chatRoom sources.
     */
    private QueryChatRoomListAdapter sourcesAdapter;

    /**
     * The chatRoom list view.
     */
    private ExpandableListView chatRoomListView;

    /**
     * Stores last clicked <tt>chatRoom</tt>.
     */
    private ChatRoomWrapper mClickedChatRoom;

    /**
     * Stores recently clicked chatRoom group.
     */
    private ChatRoomProviderWrapper mClickedGroup;

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
     * Creates a new instance of <tt>ContactListFragment</tt>.
     */
    public ChatRoomListFragment()
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

        ViewGroup content = (ViewGroup) inflater.inflate(R.layout.chatroom_list, container, false);
        chatRoomListView = content.findViewById(R.id.chatRoomListView);
        chatRoomListView.setOnGroupClickListener(this);
        initChatRoomListAdapter();

        return content;
    }

    /**
     * Initialize the chatRoom list adapter;
     * Leave invalidateViews() to BaseChatRoomListAdapter as data update is async in new thread
     */
    private void initChatRoomListAdapter()
    {
        chatRoomListView.setAdapter(getChatRoomListAdapter());

        // Attach ChatRoomProvider expand memory
        listExpandHandler = new ChatRoomGroupExpandHandler(chatRoomListAdapter, chatRoomListView);
        listExpandHandler.bindAndRestore();

        // Restore search state based on entered text
        if (mSearchItem != null) {
            SearchView searchView = (SearchView) mSearchItem.getActionView();
            int id = searchView.getContext().getResources()
                    .getIdentifier("android:id/search_src_text", null, null);

            String filter = ViewUtil.toString(searchView.findViewById(id));
            filterChatRoomWrapperList(filter);
            bindSearchListener();
        }
        else {
            chatRoomListAdapter.filterData("");
        }

        // Restore scroll position
        chatRoomListView.setSelectionFromTop(scrollPosition, scrollTopPosition);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onResume()
    {
        super.onResume();

        // Invalidate view to update read counter and expand groups (collapsed when access settings)
        if (chatRoomListAdapter != null) {
            chatRoomListAdapter.expandAllGroups();
            chatRoomListAdapter.invalidateViews();
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

        // Save scroll position
        if (chatRoomListView != null) {
            scrollPosition = chatRoomListView.getFirstVisiblePosition();
            View itemView = chatRoomListView.getChildAt(0);
            scrollTopPosition = (itemView == null) ? 0 : itemView.getTop();

            chatRoomListView.setAdapter((ExpandableListAdapter) null);
        }

        // Dispose of group expand memory
        if (listExpandHandler != null) {
            listExpandHandler.unbind();
            listExpandHandler = null;
        }

        if (chatRoomListAdapter != null) {
            chatRoomListAdapter.dispose();
            chatRoomListAdapter = null;
        }

        disposeSourcesAdapter();
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
                filterChatRoomWrapperList("");
                return true; // Return true to collapse action view
            }

            public boolean onMenuItemActionExpand(MenuItem item)
            {
                return true; // Return true to expand action view
            }
        });

        SearchView searchView = (SearchView) mSearchItem.getActionView();
        searchView.setSearchableInfo(searchManager.getSearchableInfo(getActivity().getComponentName()));

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

    private ChatRoomListAdapter getChatRoomListAdapter()
    {
        if (chatRoomListAdapter == null) {
            chatRoomListAdapter = new ChatRoomListAdapter(this);
            chatRoomListAdapter.initModelData();
        }
        return chatRoomListAdapter;
    }

    private QueryChatRoomListAdapter getSourcesAdapter()
    {
        if (sourcesAdapter == null) {
            sourcesAdapter = new QueryChatRoomListAdapter(this, getChatRoomListAdapter());
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
     * Inflates chatRoom Item popup menu.
     * Avoid using android contextMenu (in fragment) - truncated menu list
     *
     * @param roomView click view.
     * @param crWrapper an instance of ChatRoomWrapper.
     */
    public void showPopupMenu(View roomView, ChatRoomWrapper crWrapper)
    {
        // Inflate chatRoom list popup menu
        PopupMenu popup = new PopupMenu(mContext, roomView);
        Menu menu = popup.getMenu();
        popup.getMenuInflater().inflate(R.menu.chatroom_ctx_menu, menu);
        popup.setOnMenuItemClickListener(new PopupMenuItemClick());

        // Remember clicked chatRoomWrapper
        mClickedChatRoom = crWrapper;

        // update contact TTS enable option title
        String tts_option = aTalkApp.getResString(crWrapper.isTtsEnable()
                ? R.string.service_gui_TTS_DISABLE : R.string.service_gui_TTS_ENABLE);
        mChatRoomTtsEnable = menu.findItem(R.id.chatroom_tts_enable);
        mChatRoomTtsEnable.setTitle(tts_option);
        mChatRoomTtsEnable.setVisible(ConfigurationUtils.isTtsEnable());

        // Only room owner is allowed to destroy chatRoom, or non-joined room (un-deterministic)
        ChatRoomMemberRole role = mClickedChatRoom.getChatRoom().getUserRole();
        boolean allowDestroy = ((role == null) || ChatRoomMemberRole.OWNER.equals(role));
        menu.findItem(R.id.destroy_chatroom).setVisible(allowDestroy);

        // Checks if close chat option should be visible for this chatRoom
        boolean closeChatVisible = ChatSessionManager.getActiveChat(mClickedChatRoom.getChatRoomID()) != null;
        menu.findItem(R.id.close_chatroom).setVisible(closeChatVisible);

        // Close all chats option should be visible if chatList is not empty
        List<Chat> chatList = ChatSessionManager.getActiveChats();
        boolean visible = ((chatList.size() > 1) || ((chatList.size() == 1) && !closeChatVisible));
        menu.findItem(R.id.close_all_chatrooms).setVisible(visible);

        // may not want to offer erase all chatRooms chat history
        menu.findItem(R.id.erase_all_chatroom_history).setVisible(false);
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
            ChatPanel chatPanel = ChatSessionManager.getActiveChat(mClickedChatRoom.getChatRoomID());
            switch (item.getItemId()) {
                case R.id.chatroom_tts_enable:
                    if (mClickedChatRoom.isTtsEnable()) {
                        mClickedChatRoom.setTtsEnable(false);
                        mChatRoomTtsEnable.setTitle(R.string.service_gui_TTS_ENABLE);
                    }
                    else {
                        mClickedChatRoom.setTtsEnable(true);
                        mChatRoomTtsEnable.setTitle(R.string.service_gui_TTS_DISABLE);
                    }
                    ChatSessionManager.getMultiChat(mClickedChatRoom, true).updateChatTtsOption();
                    return true;

                case R.id.close_chatroom:
                    if (chatPanel != null)
                        onCloseChat(chatPanel);
                    return true;

                case R.id.close_all_chatrooms:
                    onCloseAllChats();
                    return true;

                case R.id.erase_chatroom_history:
                    EntityListHelper.eraseEntityChatHistory(mContext, mClickedChatRoom, null, null);
                    return true;

                case R.id.erase_all_chatroom_history:
                    EntityListHelper.eraseAllEntityHistory(mContext);
                    return true;

                case R.id.destroy_chatroom:
                    new ChatRoomDestroyDialog().show(mContext, mClickedChatRoom, chatPanel);
                    return true;

                case R.id.chatroom_info:
                    ChatRoomInfoDialog chatRoomInfoDialog = ChatRoomInfoDialog.newInstance(mClickedChatRoom);
                    FragmentTransaction ft = getParentFragmentManager().beginTransaction();
                    ft.addToBackStack(null);
                    chatRoomInfoDialog.show(ft, "infoDialog");
                    return true;

                case R.id.chatroom_ctx_menu_exit:
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
    private void onCloseChat(ChatPanel closedChat)
    {
        ChatSessionManager.removeActiveChat(closedChat);
        if (chatRoomListAdapter != null)
            chatRoomListAdapter.notifyDataSetChanged();
    }

    /**
     * Method fired when all chats are being closed.
     */
    private void onCloseAllChats()
    {
        ChatSessionManager.removeAllActiveChats();
        if (chatRoomListAdapter != null)
            chatRoomListAdapter.notifyDataSetChanged();
    }

    /**
     * Returns the chatRoom list view.
     *
     * @return the chatRoom list view
     */
    public ExpandableListView getChatRoomListView()
    {
        return chatRoomListView;
    }

    /**
     * Open and join chat conference for the given chatRoomWrapper.
     */
    public void joinChatRoom(ChatRoomWrapper chatRoomWrapper)
    {
        if (chatRoomWrapper != null) {
            ProtocolProviderService pps = chatRoomWrapper.getProtocolProvider();
            String nickName = XmppStringUtils.parseLocalpart(pps.getAccountID().getAccountJid());
            MUCActivator.getMUCService().joinChatRoom(chatRoomWrapper, nickName, null, null);

            Intent chatIntent = ChatSessionManager.getChatIntent(chatRoomWrapper);
            if (chatIntent != null) {
                Intent shareIntent = ShareActivity.getShareIntent(chatIntent);
                if (shareIntent != null) {
                    chatIntent = shareIntent;
                }
                startActivity(chatIntent);
            }
            else {
                Timber.w("Failed to start chat with %s", chatRoomWrapper);
            }
        }
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
        if (chatRoomListView.isGroupExpanded(groupPosition))
            chatRoomListView.collapseGroup(groupPosition);
        else {
            chatRoomListView.expandGroup(groupPosition, true);
        }
        return true;
    }

    /**
     * Filters chatRoom list for given <tt>query</tt>.
     *
     * @param query the query string that will be used for filtering chat rooms.
     */
    private void filterChatRoomWrapperList(String query)
    {
        // FFR: 2.1.5 Samsung Galaxy J2 Prime (grandpplte), Android 6.0, NPE for chatRoomListView
        if (chatRoomListView == null)
            return;

        if (StringUtils.isEmpty(query)) {
            // Cancel any pending queries
            disposeSourcesAdapter();

            // Display the chatRoom list
            if (chatRoomListView.getExpandableListAdapter() != getChatRoomListAdapter()) {
                chatRoomListView.setAdapter(getChatRoomListAdapter());
                chatRoomListAdapter.filterData("");
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
            if (chatRoomListView.getExpandableListAdapter() != getSourcesAdapter()) {
                chatRoomListView.setAdapter(getSourcesAdapter());
            }

            // Update query string
            sourcesAdapter.filterData(query);
        }
    }

    /**
     * Class used to implement <tt>SearchView</tt> listeners for compatibility purposes.
     */
    private class SearchViewListener implements SearchView.OnQueryTextListener, SearchView.OnCloseListener
    {
        @Override
        public boolean onClose()
        {
            filterChatRoomWrapperList("");
            return true;
        }

        @Override
        public boolean onQueryTextChange(String query)
        {
            filterChatRoomWrapperList(query);
            return true;
        }

        @Override
        public boolean onQueryTextSubmit(String query)
        {
            filterChatRoomWrapperList(query);
            return true;
        }
    }

    /**
     * Update the unread message badge for the specified ChatRoomWrapper
     * The unread count is pre-stored in the crWrapper
     *
     * @param crWrapper The ChatRoomWrapper to be updated
     */
    public void updateUnreadCount(final ChatRoomWrapper crWrapper)
    {
        runOnUiThread(() -> {
            if ((crWrapper != null) && (chatRoomListAdapter != null)) {
                int unreadCount = crWrapper.getUnreadCount();
                chatRoomListAdapter.updateUnreadCount(crWrapper, unreadCount);

                Fragment csf = aTalk.getFragment(aTalk.CHAT_SESSION_FRAGMENT);
                if (csf instanceof ChatSessionFragment) {
                    ((ChatSessionFragment) csf).updateUnreadCount(crWrapper.getChatRoomID(), unreadCount);
                }
            }
        });
    }
}
