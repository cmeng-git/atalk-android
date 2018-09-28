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

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.SearchManager;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import android.widget.ExpandableListView.*;

import net.java.sip.communicator.service.gui.Chat;
import net.java.sip.communicator.service.muc.*;
import net.java.sip.communicator.service.protocol.ProtocolProviderService;
import net.java.sip.communicator.util.Logger;

import org.atalk.android.R;
import org.atalk.android.gui.AndroidGUIActivator;
import org.atalk.android.gui.chat.ChatPanel;
import org.atalk.android.gui.chat.ChatSessionManager;
import org.atalk.android.gui.chatroomslist.model.*;
import org.atalk.android.gui.util.EntityListHelper;
import org.atalk.service.osgi.OSGiFragment;
import org.atalk.util.StringUtils;
import org.jxmpp.util.XmppStringUtils;

import java.util.List;

/**
 * Class to display the ChatRoom in Expandable List View
 *
 * @author Eng Chong Meng
 */
public class ChatRoomListFragment extends OSGiFragment
        implements OnChildClickListener, OnGroupClickListener
{
    /**
     * The logger
     */
    private final static Logger logger = Logger.getLogger(ChatRoomListFragment.class);

    /**
     * Search options menu items.
     */
    private MenuItem mSearchItem;

    /**
     * ChatRoom list data model.
     */
    protected ChatRoomListAdapter chatRoomListAdapter;

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
    protected ExpandableListView chatRoomListView;

    /**
     * Stores last clicked <tt>chatRoom</tt>.
     */
    protected ChatRoomWrapper mClickedChatRoom;

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

    /**
     * Creates new instance of <tt>ContactListFragment</tt>.
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
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState)
    {
        if (AndroidGUIActivator.bundleContext == null) {
            return null;
        }

        ViewGroup content = (ViewGroup) inflater.inflate(R.layout.chatroom_list, container, false);
        chatRoomListView = content.findViewById(R.id.chatRoomListView);
        chatRoomListView.setSelector(R.drawable.contact_list_selector);
        chatRoomListView.setOnChildClickListener(this);
        chatRoomListView.setOnGroupClickListener(this);

        // Adds context menu for contact list items
        registerForContextMenu(chatRoomListView);
        return content;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onResume()
    {
        super.onResume();
        chatRoomListView.setAdapter(getChatRoomListAdapter());

        // Attach ChatRoomProvider  expand memory
        listExpandHandler = new ChatRoomGroupExpandHandler(chatRoomListAdapter, chatRoomListView);
        listExpandHandler.bindAndRestore();

        // Invalidate view to update
        chatRoomListAdapter.invalidateViews();
        chatRoomListAdapter.filterData("");

        // Restore search state based on entered text
        if (mSearchItem != null) {
            SearchView searchView = (SearchView) mSearchItem.getActionView();
            int id = searchView.getContext().getResources()
                    .getIdentifier("android:id/search_src_text", null, null);
            TextView textView = searchView.findViewById(id);

            filterChatRoomWrapperList(textView.getText().toString());
            bindSearchListener();
        }
        // Restore scroll position
        chatRoomListView.setSelectionFromTop(scrollPosition, scrollTopPosition);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onPause()
    {
        super.onPause();

        // Unbind search listener
        if (mSearchItem != null) {
            SearchView searchView = (SearchView) mSearchItem.getActionView();
            searchView.setOnQueryTextListener(null);
            searchView.setOnCloseListener(null);
        }

        // Save scroll position
        scrollPosition = chatRoomListView.getFirstVisiblePosition();
        View itemView = chatRoomListView.getChildAt(0);
        scrollTopPosition = itemView == null ? 0 : itemView.getTop();

        // Dispose of group expand memory
        if (listExpandHandler != null) {
            listExpandHandler.unbind();
            listExpandHandler = null;
        }

        chatRoomListView.setAdapter((ExpandableListAdapter) null);
        if (chatRoomListAdapter != null) {
            chatRoomListAdapter.dispose();
            chatRoomListAdapter = null;
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
        mSearchItem = menu.findItem(R.id.search);

        // OnActionExpandListener not supported prior API 14
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
        searchView.setSearchableInfo(searchManager.getSearchableInfo(activity.getComponentName()));

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
     * {@inheritDoc}
     */
    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo)
    {
        super.onCreateContextMenu(menu, v, menuInfo);

        if (chatRoomListView.getExpandableListAdapter() != getChatRoomListAdapter()) {
            return;
        }

        ExpandableListContextMenuInfo info = (ExpandableListContextMenuInfo) menuInfo;
        int type = ExpandableListView.getPackedPositionType(info.packedPosition);
        int group = ExpandableListView.getPackedPositionGroup(info.packedPosition);
        int child = ExpandableListView.getPackedPositionChild(info.packedPosition);

        // Only create a context menu for child items
        MenuInflater inflater = getActivity().getMenuInflater();
        if (type == ExpandableListView.PACKED_POSITION_TYPE_GROUP) {
            createGroupCtxMenu(menu, inflater, group);
        }
        else if (type == ExpandableListView.PACKED_POSITION_TYPE_CHILD) {
            createChatRoomCtxMenu(menu, inflater, group, child);
        }
    }

    /**
     * Inflates chatRoom context menu.
     *
     * @param menu the menu to inflate when long press on chatRoom item.
     * @param inflater the menu inflater.
     * @param group clicked group index.
     * @param child clicked contact index.
     */
    private void createChatRoomCtxMenu(ContextMenu menu, MenuInflater inflater, int group, int child)
    {
        // Inflate contact list context menu
        inflater.inflate(R.menu.chatroom_ctx_menu, menu);

        // Remembers clicked chatRoomWrapper
        mClickedChatRoom = (ChatRoomWrapper) chatRoomListAdapter.getChild(group, child);
        menu.setHeaderTitle(mClickedChatRoom.getChatRoomID());

        // Checks if close chat option should be visible for this contact
        boolean closeChatVisible = ChatSessionManager.getActiveChat(mClickedChatRoom.getChatRoomID()) != null;
        menu.findItem(R.id.close_chatroom).setVisible(closeChatVisible);

        // Close all chats option should be visible if chatList is not empty
        List<Chat> chatList = ChatSessionManager.getActiveChats();
        boolean visible = ((chatList.size() > 1) || ((chatList.size() == 1) && !closeChatVisible));
        menu.findItem(R.id.close_all_chatrooms).setVisible(visible);

        // may not want to offer erase all chatRooms chat history
        menu.findItem(R.id.erase_chatroom_history_all).setVisible(false);
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
        mClickedGroup = (ChatRoomProviderWrapper) chatRoomListAdapter.getGroup(group);

        // Inflate chatRoom list context menu
        inflater.inflate(R.menu.group_menu, menu);
        menu.setHeaderTitle(mClickedGroup.getProtocolProvider().getAccountID().getAccountUniqueID());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean onContextItemSelected(MenuItem item)
    {
        ChatPanel chatPanel = ChatSessionManager.getActiveChat(mClickedChatRoom.getChatRoomID());
        switch (item.getItemId()) {
            case R.id.close_chatroom:
                if (chatPanel != null)
                    onCloseChat(chatPanel);
                return true;
            case R.id.close_all_chatrooms:
                onCloseAllChats();
                return true;
            case R.id.erase_chatroom_history:
                EntityListHelper.eraseEntityChatHistory(getActivity(),
                        mClickedChatRoom, null, null);
                return true;
            case R.id.erase_chatroom_history_all:
                EntityListHelper.eraseAllContactHistory(getActivity());
                return true;
            case R.id.destroy_chatroom:
                EntityListHelper.removeEntity(mClickedChatRoom, chatPanel);
                return true;
            case R.id.chatroom_info:
                ChatRoomInfoFragment chatRoomInfoFragment = ChatRoomInfoFragment.newInstance(mClickedChatRoom);
                getActivity().getSupportFragmentManager().beginTransaction()
                        .replace(android.R.id.content, chatRoomInfoFragment).commit();
                return true;
            case R.id.chatroom_ctx_menu_exit:
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
        chatRoomListAdapter.notifyDataSetChanged();
    }

    /**
     * Method fired when all chats are being closed.
     */
    public void onCloseAllChats()
    {
        ChatSessionManager.removeAllActiveChats();
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
     * Callback method to be invoked when a child in this expandable list has
     * been clicked.
     *
     * @param listView The ExpandableListView where the click happened
     * @param v The view within the expandable list/ListView that was clicked
     * @param groupPosition The group position that contains the child that
     * was clicked
     * @param childPosition The child position within the group
     * @param id The row id of the child that was clicked
     * @return True if the click was handled
     */
    @Override
    public boolean onChildClick(ExpandableListView listView, View v, int groupPosition, int childPosition, long id)
    {
        BaseChatRoomListAdapter adapter = (BaseChatRoomListAdapter) listView.getExpandableListAdapter();
        int position = adapter.getListIndex(groupPosition, childPosition);

        chatRoomListView.setSelection(position);
        adapter.invalidateViews();

        Object clicked = adapter.getChild(groupPosition, childPosition);
        if (clicked instanceof ChatRoomWrapper) {
            joinChatRoom((ChatRoomWrapper) clicked);
            return true;
        }
        else {
            logger.debug("No a chatRoomWrapper @: " + groupPosition + ", " + childPosition);
            return false;
        }
    }

    /**
     * Open and join chat conference for the given chatRoomWrapper.
     */
    private void joinChatRoom(ChatRoomWrapper chatRoomWrapper)
    {
        if (chatRoomWrapper != null) {
            ProtocolProviderService pps = chatRoomWrapper.getParentProvider().getProtocolProvider();
            String chatRoomID = chatRoomWrapper.getChatRoomID();
            String nickName = XmppStringUtils.parseLocalpart(pps.getAccountID().getAccountJid());

            // Set chatRoom openAutomatically on_activity
            MUCService.setChatRoomAutoOpenOption(pps, chatRoomID, MUCService.OPEN_ON_ACTIVITY);
            AndroidGUIActivator.getMUCService().joinChatRoom(chatRoomWrapper, nickName, null, null);

            Intent chatIntent = ChatSessionManager.getChatIntent(chatRoomWrapper);
            startActivity(chatIntent);
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
    @TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
    public boolean onGroupClick(ExpandableListView parent, View v, int groupPosition, long id)
    {
        if (chatRoomListView.isGroupExpanded(groupPosition))
            chatRoomListView.collapseGroup(groupPosition);
        else {
            chatRoomListView.expandGroup(groupPosition, true);
        }
        return true;
    }

    public ChatRoomWrapper getClickedChatRoom()
    {
        return mClickedChatRoom;
    }

    /**
     * Filters contact list for given <tt>query</tt>.
     *
     * @param query the query string that will be used for filtering contacts.
     */
    private void filterChatRoomWrapperList(String query)
    {
        if (StringUtils.isNullOrEmpty(query)) {
            // Cancel any pending queries
            disposeSourcesAdapter();

            // Display the contact list
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
}
