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
package org.atalk.android.gui.chatroomslist.model;

import android.text.TextUtils;

import net.java.sip.communicator.impl.muc.*;
import net.java.sip.communicator.service.muc.*;
import net.java.sip.communicator.service.protocol.ProtocolProviderService;

import org.apache.commons.lang3.StringUtils;
import org.atalk.android.R;
import org.atalk.android.aTalkApp;
import org.atalk.android.gui.chat.ChatPanel;
import org.atalk.android.gui.chat.ChatSessionManager;
import org.atalk.android.gui.chatroomslist.ChatRoomListFragment;
import org.atalk.android.gui.contactlist.model.UIGroupRenderer;
import org.jivesoftware.smack.*;
import org.jivesoftware.smackx.bookmarks.BookmarkManager;
import org.jivesoftware.smackx.bookmarks.BookmarkedConference;

import java.util.*;
import java.util.regex.Pattern;

import timber.log.Timber;

/**
 * ChatRoom list model is responsible for caching current chatRoomWrapper list obtained from
 * ChatRoomProviderWrapperImpl.(It will apply source filters which result in different output model).
 *
 * @author Eng Chong Meng
 */
public class ChatRoomListAdapter extends BaseChatRoomListAdapter
        implements ChatRoomProviderWrapperListener, ChatRoomListChangeListener, UIGroupRenderer
{
    /**
     * The group of original chatRoomProviderWrapper before filtered
     */
    private LinkedList<ChatRoomProviderWrapper> originalCrpWrapperGroup;

    /**
     * The group of chatRoomProviderWrapper for view display
     */
    private LinkedList<ChatRoomProviderWrapper> mCrpWrapperGroup;

    /**
     * The original list of chatRoomWrapper before filtered.
     */
    private LinkedList<TreeSet<ChatRoomWrapper>> originalCrWrapperList;

    /**
     * The list of chatRoomWrapper for view display.
     */
    private LinkedList<TreeSet<ChatRoomWrapper>> mCrWrapperList;

    /**
     * The <tt>MUCService</tt>, which is the back end of this chatRoom list adapter.
     */
    private MUCServiceImpl mucService;

    /**
     * <tt>ChatRoomRenderer</tt> instance used by this adapter.
     */
    private ChatRoomRenderer chatRoomRenderer;

    /**
     * The currently used filter query.
     */
    private String currentFilterQuery;

    /**
     * A local reference of the last fetched bookmarks list
     */
    private List<BookmarkedConference> bookmarksList = null;

    public ChatRoomListAdapter(ChatRoomListFragment chatRoomListFragment)
    {
        super(chatRoomListFragment);
        originalCrWrapperList = new LinkedList<>();
        mCrWrapperList = new LinkedList<>();
        originalCrpWrapperGroup = new LinkedList<>();
        mCrpWrapperGroup = new LinkedList<>();
    }

    /**
     * Initializes the adapter data.
     */
    public void initModelData()
    {
        mucService = MUCActivator.getMUCService();
        if (mucService != null) {
            // Timber.d("ChatRoom list change listener is added %s", this);
            mucService.addChatRoomProviderWrapperListener(this);
            mucService.addChatRoomListChangeListener(this);

            new Thread()
            {
                @Override
                public void run()
                {
                    addChatRooms(mucService.getChatRoomProviders());
                }
            }.start();
        }
    }

    /**
     * Releases all resources used by this instance.
     */
    public void dispose()
    {
        if (mucService != null) {
            // Timber.d("ChatRoom list change listener is removed %s", this);
            mucService.removeChatRoomProviderWrapperListener(this);
            mucService.removeChatRoomListChangeListener(this);
            removeChatRooms(mucService.getChatRoomProviders());
        }
    }

    /**
     * Locally implemented UIGroupRenderer
     * {@inheritDoc}
     */
    @Override
    public UIGroupRenderer getGroupRenderer(int groupPosition)
    {
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public UIChatRoomRenderer getChatRoomRenderer(int groupPosition)
    {
        if (chatRoomRenderer == null) {
            chatRoomRenderer = new ChatRoomRenderer();
        }
        return chatRoomRenderer;
    }

    /**
     * Returns the mCrpWrapperGroup at the given <tt>groupPosition</tt>.
     *
     * @param groupPosition the index of the mCrpWrapperGroup
     */
    @Override
    public Object getGroup(int groupPosition)
    {
        if (groupPosition >= 0 && groupPosition < mCrpWrapperGroup.size())
            return mCrpWrapperGroup.get(groupPosition);
        else {
            return null;
        }
    }

    /**
     * Returns the count of all ChatRoomProviderWrapper contained in this adapter.
     */
    @Override
    public int getGroupCount()
    {
        return mCrpWrapperGroup.size();
    }

    /**
     * Finds mCrpWrapperGroup index for given <tt>ChatRoomProviderWrapper</tt>.
     *
     * @param group the mCrpWrapperGroup for which we need the index.
     * @return index of given <tt>ChatRoomProviderWrapper</tt> or -1 if not found
     */
    public int getGroupIndex(ChatRoomProviderWrapper group)
    {
        return mCrpWrapperGroup.indexOf(group);
    }

    /**
     * Finds <tt>ChatRoomWrapper</tt> index in <tt>ChatRoomProviderWrapper</tt> identified by given <tt>groupIndex</tt>.
     *
     * @param groupIndex index of mCrpWrapperGroup we want to search.
     * @param chatRoomWrapper the <tt>ChatRoomWrapper</tt> to find inside the mCrpWrapperGroup.
     * @return index of <tt>ChatRoomWrapper</tt> inside mCrpWrapperGroup identified by given mCrpWrapperGroup index.
     */
    public int getChildIndex(int groupIndex, ChatRoomWrapper chatRoomWrapper)
    {
        return getChildIndex(getCrWrapperList(groupIndex), chatRoomWrapper);
    }

    /**
     * Returns the count of children contained in the mCrpWrapperGroup given by the <tt>groupPosition</tt>.
     *
     * @param groupPosition the index of the mCrpWrapperGroup, which children we would like to count
     */
    @Override
    public int getChildrenCount(int groupPosition)
    {
        TreeSet<ChatRoomWrapper> chatRoomList = getCrWrapperList(groupPosition);
        if (chatRoomList != null)
            return chatRoomList.size();
        else
            return 0;
    }

    /**
     * Get mCrpWrapperGroup list from filtered CrWrapperList list.
     *
     * @param groupIndex mCrpWrapperGroup index.
     * @return mCrWrapper list from filtered CrWrapperList list.
     */
    private TreeSet<ChatRoomWrapper> getCrWrapperList(int groupIndex)
    {
        if (groupIndex >= 0 && groupIndex < mCrWrapperList.size()) {
            return mCrWrapperList.get(groupIndex);
        }
        else {
            return null;
        }
    }

    /**
     * Get mCrpWrapperGroup list from original chatRoomWrapper list.
     *
     * @param groupIndex mCrpWrapperGroup index.
     * @return mCrpWrapperGroup list from original list.
     */
    private TreeSet<ChatRoomWrapper> getOriginalCrWrapperList(int groupIndex)
    {
        if (groupIndex >= 0 && groupIndex < originalCrWrapperList.size()) {
            return originalCrWrapperList.get(groupIndex);
        }
        else {
            return null;
        }
    }

    /**
     * Adds all child mCrWrapperList for all the given <tt>mCrpWrapperGroup</tt>. Skip adding group of zero child.
     *
     * @param providers the providers mCrpWrapperGroup, which child mCrWrapperList to add
     */
    private void addChatRooms(List<ChatRoomProviderWrapper> providers)
    {
        for (ChatRoomProviderWrapper provider : providers) {
            List<ChatRoomWrapper> chatRoomWrappers = initBookmarkChatRooms(provider);
            if ((chatRoomWrappers != null) && (chatRoomWrappers.size() > 0)) {
                addGroup(provider);

                // Use Iterator to avoid ConcurrentModificationException on addChatRoom(); do not user foreach
                Iterator<ChatRoomWrapper> iteratorCRW = chatRoomWrappers.iterator();
                while (iteratorCRW.hasNext()) {
                    addChatRoom(provider, iteratorCRW.next());
                }
                // for (ChatRoomWrapper crWrapper : chatRoomWrappers) {
                //     addChatRoom(provider, crWrapper); // ConcurrentModificationException
                // }
            }
        }

        // must refresh list view only after chatRoomWrappers fetch with bookmark info updated
        invalidateViews();
    }

    /**
     * Adds all child mCrWrapperList for all the given <tt>mCrpWrapper</tt> and update it with bookmark info
     *
     * @param crpWrapper the crpWrapper provider, which child mCrWrapperList to fetch
     */
    private List<ChatRoomWrapper> initBookmarkChatRooms(ChatRoomProviderWrapper crpWrapper)
    {
        if (crpWrapper != null) {
            XMPPConnection connection;
            ProtocolProviderService pps = crpWrapper.getProtocolProvider();
            if ((pps == null) || ((connection = pps.getConnection()) == null) || !connection.isAuthenticated()) {
                // reset bookmarks when user log off is detected.
                bookmarksList = null;
                return null;
            }

            // Just return room lists if bookmarks have been fetched and updated earlier
            if (bookmarksList != null)
                return crpWrapper.getChatRooms();

            Timber.d("Update conference bookmarks started.");
            BookmarkManager bookmarkManager = BookmarkManager.getBookmarkManager(connection);
            try {
                bookmarksList = bookmarkManager.getBookmarkedConferences();
                for (BookmarkedConference bookmarkedConference : bookmarksList) {
                    String chatRoomId = bookmarkedConference.getJid().toString();
                    ChatRoomWrapper chatRoomWrapper = crpWrapper.findChatRoomWrapperForChatRoomID(chatRoomId);
                    if (chatRoomWrapper == null) {
                        chatRoomWrapper = new ChatRoomWrapperImpl(crpWrapper, chatRoomId);
                        crpWrapper.addChatRoom(chatRoomWrapper);
                    }
                    // cmeng: not working - problem chatRoom list empty
                    // else {
                    //     crWrappers.remove(chatRoomWrapper);
                    // }
                    chatRoomWrapper.setBookmark(true);
                    chatRoomWrapper.setBookmarkName(bookmarkedConference.getName());
                    chatRoomWrapper.setAutoJoin(bookmarkedConference.isAutoJoin());

                    String password = bookmarkedConference.getPassword();
                    if (StringUtils.isNotEmpty(password))
                        chatRoomWrapper.savePassword(password);
                }
            } catch (SmackException.NoResponseException | SmackException.NotConnectedException
                    | XMPPException.XMPPErrorException | InterruptedException e) {
                Timber.w("Failed to fetch Bookmarks for %s: %s",
                        crpWrapper.getProtocolProvider().getAccountID(), e.getMessage());
            }
            Timber.d("Update conference bookmarks completed");

            // Auto join chatRoom if any - not need
            // crpWrapper.synchronizeProvider();
            return crpWrapper.getChatRooms();
        }
        return null;
    }

    /**
     * Adds the given <tt>crpWrapper</tt> to both the originalCrpWrapperGroup and
     * mCrpWrapperGroup only if no existing crpWrapper is found in current lists
     *
     * @param crpWrapper the <tt>ChatRoomProviderWrapper</tt> to add
     */
    private void addGroup(ChatRoomProviderWrapper crpWrapper)
    {
        if (!originalCrpWrapperGroup.contains(crpWrapper)) {
            originalCrpWrapperGroup.add(crpWrapper);
            originalCrWrapperList.add(new TreeSet<>());
        }

        if (!mCrpWrapperGroup.contains(crpWrapper)) {
            mCrpWrapperGroup.add(crpWrapper);
            mCrWrapperList.add(new TreeSet<>());
        }
    }

    /**
     * Remove an existing <tt>crpWrapper</tt> from both the originalCrpWrapperGroup and mCrpWrapperGroup if exist
     *
     * @param crpWrapper the <tt>chatRoomProviderWrapper</tt> to be removed
     */
    private void removeGroup(ChatRoomProviderWrapper crpWrapper)
    {
        int origGroupIndex = originalCrpWrapperGroup.indexOf(crpWrapper);
        if (origGroupIndex != -1) {
            originalCrWrapperList.remove(origGroupIndex);
            originalCrpWrapperGroup.remove(crpWrapper);
        }

        int groupIndex = mCrpWrapperGroup.indexOf(crpWrapper);
        if (groupIndex != -1) {
            mCrWrapperList.remove(groupIndex);
            mCrpWrapperGroup.remove(crpWrapper);
        }
    }

    /**
     * Adds the given child crWrapper to the <tt>crpWrapperGroup</tt>.
     *
     * @param crpWrapperGroup the parent ChatRoomProviderWrapper Group of the child ChatRoomWrapper to add
     * @param crWrapper the <tt>ChatRoomWrapper</tt> to add
     */
    private void addChatRoom(ChatRoomProviderWrapper crpWrapperGroup, ChatRoomWrapper crWrapper)
    {
        int origGroupIndex = originalCrpWrapperGroup.indexOf(crpWrapperGroup);
        int groupIndex = mCrpWrapperGroup.indexOf(crpWrapperGroup);

        boolean isMatchingQuery = isMatching(crWrapper, currentFilterQuery);

        // Add new crpWrapperGroup element (original and filtered) and
        // update both with the new Indexes (may be difference)
        if ((origGroupIndex < 0) || (isMatchingQuery && (groupIndex < 0))) {
            addGroup(crpWrapperGroup);
            origGroupIndex = originalCrpWrapperGroup.indexOf(crpWrapperGroup);
            groupIndex = mCrpWrapperGroup.indexOf(crpWrapperGroup);
        }

        TreeSet<ChatRoomWrapper> origCrWrapperList = getOriginalCrWrapperList(origGroupIndex);
        if (origCrWrapperList != null && getChildIndex(origCrWrapperList, crWrapper) < 0) {
            origCrWrapperList.add(crWrapper);
        }

        // New crWrapper is added to filtered crWrapperList only if isMatchingQuery
        if (isMatchingQuery) {
            TreeSet<ChatRoomWrapper> crWrapperList = getCrWrapperList(groupIndex);
            if ((crWrapperList != null) && (getChildIndex(crWrapperList, crWrapper) < 0)) {
                crWrapperList.add(crWrapper);
            }
        }
    }

    /**
     * Removes all the ChatRoomProviderWrappers and ChatRoomWrappers for the given providers.
     *
     * @param providers the <tt>ChatRoomProviderWrapper</tt>, which content we'd like to remove
     */
    private void removeChatRooms(List<ChatRoomProviderWrapper> providers)
    {
        for (ChatRoomProviderWrapper provider : providers) {
            List<ChatRoomWrapper> crWrapperList = provider.getChatRooms();
            for (ChatRoomWrapper crWrapper : crWrapperList) {
                removeChatRoom(provider, crWrapper);
            }

            // May not be necessary as remove all children will also remove the provider with zero child
            // must do this last as the provider is used in removeChatRoom();
            removeGroup(provider);
        }
    }

    /**
     * Remove the given <tt>ChatRoomWrapper</tt> from both the original and the filtered list of
     * this adapter. Also remove the group with zero element
     *
     * @param crpWrapper the parent <tt>ChatRoomProviderWrapper</tt> of the ChatRoomWrapper to remove
     * @param crWrapper the <tt>ChatRoomWrapper</tt> to remove
     */
    private void removeChatRoom(ChatRoomProviderWrapper crpWrapper, ChatRoomWrapper crWrapper)
    {
        // Remove the chatRoomWrapper from the original list and its crpWrapperGroup if empty.
        int origGroupIndex = originalCrpWrapperGroup.indexOf(crpWrapper);
        if (origGroupIndex != -1) {
            TreeSet<ChatRoomWrapper> origChatRoomList = getOriginalCrWrapperList(origGroupIndex);
            if (origChatRoomList != null) {
                origChatRoomList.remove(crWrapper);

                if (origChatRoomList.isEmpty())
                    removeGroup(crpWrapper);
            }
        }

        // Remove the chatRoomWrapper from the filtered list and its crpWrapperGroup if empty
        int groupIndex = mCrpWrapperGroup.indexOf(crpWrapper);
        if (groupIndex != -1) {
            TreeSet<ChatRoomWrapper> crWrapperList = getCrWrapperList(groupIndex);
            if (crWrapperList != null) {
                crWrapperList.remove(crWrapper);

                if (crWrapperList.isEmpty())
                    removeGroup(crpWrapper);
            }
        }
    }

    /**
     * Returns the chatRoom with the given <tt>groupPosition</tt> and <tt>childPosition</tt>.
     *
     * @param groupPosition the index of the mCrpWrapperGroup
     * @param childPosition the index of the child
     * @return the chatRoom with the given <tt>groupPosition</tt> and <tt>childPosition</tt>
     */
    @Override
    public Object getChild(int groupPosition, int childPosition)
    {
        if (mCrWrapperList.size() > 0) {
            TreeSet<ChatRoomWrapper> crWrapperList = getCrWrapperList(groupPosition);
            if (crWrapperList != null) {
                int i = 0;
                for (ChatRoomWrapper crWrapper : crWrapperList) {
                    if (i == childPosition) {
                        return crWrapper;
                    }
                    i++;
                }
            }
        }
        return null;
    }

    /**
     * Return crWrapper index in the originalCrWrapperList
     **/
    private int getChildIndex(TreeSet<ChatRoomWrapper> crWrapperList, ChatRoomWrapper crWrapper)
    {
        if (crWrapperList != null) {
            int i = 0;
            for (ChatRoomWrapper chatRoomWrapper : crWrapperList) {
                if (chatRoomWrapper.equals(crWrapper))
                    return i;
                i++;
            }
        }
        return -1;
    }

    /**
     * Filters list data to match the given <tt>query</tt>.
     *
     * @param query the query we'd like to match
     */
    public void filterData(String query)
    {
        currentFilterQuery = query.toLowerCase();
        mCrpWrapperGroup.clear();
        mCrWrapperList.clear();

        for (ChatRoomProviderWrapper crpWrapper : originalCrpWrapperGroup) {
            TreeSet<ChatRoomWrapper> filteredList = new TreeSet<>();
            int groupIndex = originalCrpWrapperGroup.indexOf(crpWrapper);
            TreeSet<ChatRoomWrapper> crWrapperList = getOriginalCrWrapperList(groupIndex);

            if (crWrapperList != null) {
                for (ChatRoomWrapper crWrapper : crWrapperList) {
                    if (StringUtils.isEmpty(currentFilterQuery)
                            || isMatching(crWrapper, query)) {
                        filteredList.add(crWrapper);
                    }
                }
                if (filteredList.size() > 0) {
                    mCrpWrapperGroup.add(crpWrapper);
                    mCrWrapperList.add(filteredList);
                }
            }
        }
        uiChangeUpdate();
        expandAllGroups();
    }

    /**
     * Create mCrpWrapperGroup/mCrWrapperList TreeView with non-zero mCrpWrapperGroup
     */
    public void nonZeroCrpWrapperGroupList()
    {
        mCrpWrapperGroup.clear();
        mCrWrapperList.clear();

        // hide mCrpWrapperGroup contains zero chatRoomWrapper
        for (ChatRoomProviderWrapper crpWrapper : originalCrpWrapperGroup) {
            int groupIndex = originalCrpWrapperGroup.indexOf(crpWrapper);
            if (groupIndex != -1) {
                TreeSet<ChatRoomWrapper> orgCrwList = getOriginalCrWrapperList(groupIndex);
                if ((orgCrwList != null) && (orgCrwList.size() > 0)) {
                    mCrpWrapperGroup.add(crpWrapper);
                    mCrWrapperList.add(orgCrwList);
                }
            }
        }
    }

    /**
     * Checks if the given <tt>chatRoomWrapper</tt> is matching the given <tt>query</tt>.
     * A <tt>ChatRoomWrapper</tt> would be matching the filter if one of the following is true:<br>
     * - it is online or user chooses show offline mCrWrapperList
     * - its chatRoom ID or Name matches the filter string
     *
     * @param chatRoomWrapper the <tt>chatRoomWrapper</tt> to check
     * @param query the query string i.e. chatRoomID to check for matches. A null always return true
     * @return <tt>true</tt> to indicate that the given <tt>chatRoomWrapper</tt> is matching the
     * current filter, otherwise returns <tt>false</tt>
     */
    private boolean isMatching(ChatRoomWrapper chatRoomWrapper, String query)
    {
        if (TextUtils.isEmpty(query))
            return true;

        String chatRoomID = chatRoomWrapper.getChatRoomID();
        Pattern queryPattern = Pattern.compile(query, Pattern.CASE_INSENSITIVE | Pattern.LITERAL);
        return (queryPattern.matcher(chatRoomID).find());
    }

    /**
     * Checks if the given <tt>chatRoomProviderWrapper</tt> is matching the current filter.
     * A chatRoomProviderWrapper is matching the current filter only if its protocolProvider
     * matching the current filter.
     *
     * @param chatRoomProviderWrapper the <tt>ChatRoomProviderWrapper</tt> to check
     * @param query the query string i.e. accountUuid to check for matches. A null will always return true
     * @return <tt>true</tt> to indicate that the given <tt>metaGroup</tt> is matching the current
     * filter, otherwise returns <tt>false</tt>
     */
    public boolean isMatching(ChatRoomProviderWrapper chatRoomProviderWrapper, String query)
    {
        if (TextUtils.isEmpty(query))
            return true;

        String userUuid = chatRoomProviderWrapper.getProtocolProvider().getAccountID().getAccountUniqueID();
        Pattern queryPattern = Pattern.compile(query, Pattern.CASE_INSENSITIVE | Pattern.LITERAL);
        return (queryPattern.matcher(userUuid).find());
    }

    /**
     * Checks if given <tt>ChatRoomWrapper</tt> is considered to be selected. That is if the chat
     * session with given <tt>ChatRoomWrapper</tt> is the one currently visible.
     *
     * @param chatId the <tt>ChatID</tt> to check.
     * @return <tt>true</tt> if given <tt>ChatRoomWrapper</tt> is considered to be selected.
     */
    public static boolean isChatRoomWrapperSelected(String chatId)
    {
        String currentId = ChatSessionManager.getCurrentChatId();
        ChatPanel activeCP = ChatSessionManager.getActiveChat(chatId);
        return ((currentId != null) && (activeCP != null)
                && currentId.equals(activeCP.getChatSession().getChatId()));
    }

    /**
     * Implements {@link UIGroupRenderer}. {@inheritDoc}
     */
    @Override
    public String getDisplayName(Object groupImpl)
    {
        return ((ChatRoomProviderWrapper) groupImpl).getProtocolProvider().getAccountID().getAccountUniqueID();
    }

    /**
     * When a provider wrapper is added this method is called to inform listeners.
     * Add group and child only if there is at least one child in the group.
     *
     * @param crpWrapper which was added.
     */
    @Override
    public void chatRoomProviderWrapperAdded(ChatRoomProviderWrapper crpWrapper)
    {
        // Add the original/filtered chatRoomProvider Wrapper and its list.
        if ((originalCrpWrapperGroup.indexOf(crpWrapper) < 0)
                || (mCrpWrapperGroup.indexOf(crpWrapper) < 0)) {

            List<ChatRoomWrapper> chatRoomWrappers = crpWrapper.getChatRooms();
            if (chatRoomWrappers.size() > 0) {
                addGroup(crpWrapper);

                for (ChatRoomWrapper crWrapper : chatRoomWrappers) {
                    addChatRoom(crpWrapper, crWrapper);
                }
                uiChangeUpdate();
            }
        }
    }

    /**
     * When a provider wrapper is removed this method is called to inform listeners.
     *
     * @param crpWrapper which was removed.
     */
    @Override
    public void chatRoomProviderWrapperRemoved(ChatRoomProviderWrapper crpWrapper)
    {
        // Remove the original/filtered chatRoomProvider Wrapper and its chatRoomWrapper if exist.
        if ((originalCrpWrapperGroup.indexOf(crpWrapper) >= 0)
                || (mCrpWrapperGroup.indexOf(crpWrapper) >= 0)) {

            removeChatRooms(Collections.singletonList(crpWrapper));
            uiChangeUpdate();
        }
    }

    /**
     * Indicates that a change has occurred in the chatRoom List.
     */
    @Override
    public void contentChanged(ChatRoomListChangeEvent evt)
    {
        ChatRoomWrapper chatRoomWrapper = evt.getSourceChatRoom();
        switch (evt.getEventID()) {
            case ChatRoomListChangeEvent.CHAT_ROOM_ADDED:
                addChatRoom(chatRoomWrapper.getParentProvider(), chatRoomWrapper);
                break;
            case ChatRoomListChangeEvent.CHAT_ROOM_REMOVED:
                removeChatRoom(chatRoomWrapper.getParentProvider(), chatRoomWrapper);
                aTalkApp.showToastMessage(R.string.service_gui_CHATROOM_DESTROY_SUCCESSFUL,
                        chatRoomWrapper.getChatRoomID());
                break;
            case ChatRoomListChangeEvent.CHAT_ROOM_CHANGED:
                break;
            default:
                break;
        }
        uiChangeUpdate();
    }

    /**
     * All chatRoom fragment view update must be perform on UI thread
     * UI thread handler used to call all operations that access data model. This guarantees that
     * it's accessed from the single thread.
     */
    private void uiChangeUpdate()
    {
        uiHandler.post(this::notifyDataSetChanged);
    }
}
