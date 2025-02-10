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

import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.TreeSet;
import java.util.regex.Pattern;

import net.java.sip.communicator.impl.muc.ChatRoomWrapperImpl;
import net.java.sip.communicator.impl.muc.MUCActivator;
import net.java.sip.communicator.impl.muc.MUCServiceImpl;
import net.java.sip.communicator.service.muc.ChatRoomListChangeEvent;
import net.java.sip.communicator.service.muc.ChatRoomListChangeListener;
import net.java.sip.communicator.service.muc.ChatRoomProviderWrapper;
import net.java.sip.communicator.service.muc.ChatRoomProviderWrapperListener;
import net.java.sip.communicator.service.muc.ChatRoomWrapper;
import net.java.sip.communicator.service.protocol.ProtocolProviderService;

import org.apache.commons.lang3.StringUtils;
import org.atalk.android.BaseActivity;
import org.atalk.android.R;
import org.atalk.android.aTalkApp;
import org.atalk.android.gui.chat.ChatPanel;
import org.atalk.android.gui.chat.ChatSessionManager;
import org.atalk.android.gui.chatroomslist.ChatRoomListFragment;
import org.atalk.android.gui.contactlist.model.UIGroupRenderer;
import org.jivesoftware.smack.SmackException;
import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.XMPPException;
import org.jivesoftware.smackx.bookmarks.BookmarkManager;
import org.jivesoftware.smackx.bookmarks.BookmarkedConference;

import timber.log.Timber;

/**
 * ChatRoom list model is responsible for caching current chatRoomWrapper list obtained from
 * ChatRoomProviderWrapperImpl.(It will apply source filters which result in different output model).
 *
 * @author Eng Chong Meng
 */
public class ChatRoomListAdapter extends BaseChatRoomListAdapter
        implements ChatRoomProviderWrapperListener, ChatRoomListChangeListener, UIGroupRenderer {
    /**
     * The group of original chatRoomProviderWrapper before filtered
     */
    private final LinkedList<ChatRoomProviderWrapper> originalCrpWrapperGroup;

    /**
     * The group of chatRoomProviderWrapper for view display
     */
    private final LinkedList<ChatRoomProviderWrapper> mCrpWrapperGroup;

    /**
     * The original list of chatRoomWrapper before filtered.
     */
    private final LinkedList<TreeSet<ChatRoomWrapper>> originalCrWrapperList;

    /**
     * The list of chatRoomWrapper for view display.
     */
    private final LinkedList<TreeSet<ChatRoomWrapper>> mCrWrapperList;

    /**
     * The <code>MUCService</code>, which is the back end of this chatRoom list adapter.
     */
    private MUCServiceImpl mucService;

    /**
     * <code>ChatRoomRenderer</code> instance used by this adapter.
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

    public ChatRoomListAdapter(ChatRoomListFragment chatRoomListFragment) {
        super(chatRoomListFragment);
        originalCrWrapperList = new LinkedList<>();
        mCrWrapperList = new LinkedList<>();

        originalCrpWrapperGroup = new LinkedList<>();
        mCrpWrapperGroup = new LinkedList<>();
    }

    /**
     * Initializes the adapter data.
     */
    public void initModelData() {
        mucService = MUCActivator.getMUCService();
        if (mucService != null) {
            // Timber.d("ChatRoom list change listener is added %s", this);
            mucService.addChatRoomProviderWrapperListener(this);
            mucService.addChatRoomListChangeListener(this);

            new Thread() {
                @Override
                public void run() {
                    addChatRooms(mucService.getChatRoomProviders());
                }
            }.start();
        }
    }

    /**
     * Releases all resources used by this instance.
     */
    public void dispose() {
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
    public UIGroupRenderer getGroupRenderer(int groupPosition) {
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public UIChatRoomRenderer getChatRoomRenderer(int groupPosition) {
        if (chatRoomRenderer == null) {
            chatRoomRenderer = new ChatRoomRenderer();
        }
        return chatRoomRenderer;
    }

    /**
     * Returns the mCrpWrapperGroup at the given <code>groupPosition</code>.
     *
     * @param groupPosition the index of the mCrpWrapperGroup
     */
    @Override
    public Object getGroup(int groupPosition) {
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
    public int getGroupCount() {
        return mCrpWrapperGroup.size();
    }

    /**
     * Finds mCrpWrapperGroup index for given <code>ChatRoomProviderWrapper</code>.
     *
     * @param group the mCrpWrapperGroup for which we need the index.
     *
     * @return index of given <code>ChatRoomProviderWrapper</code> or -1 if not found
     */
    public int getGroupIndex(ChatRoomProviderWrapper group) {
        return mCrpWrapperGroup.indexOf(group);
    }

    /**
     * Finds <code>ChatRoomWrapper</code> index in <code>ChatRoomProviderWrapper</code> identified by given <code>groupIndex</code>.
     *
     * @param groupIndex index of mCrpWrapperGroup we want to search.
     * @param chatRoomWrapper the <code>ChatRoomWrapper</code> to find inside the mCrpWrapperGroup.
     *
     * @return index of <code>ChatRoomWrapper</code> inside mCrpWrapperGroup identified by given mCrpWrapperGroup index.
     */
    public int getChildIndex(int groupIndex, ChatRoomWrapper chatRoomWrapper) {
        return getChildIndex(getCrWrapperList(groupIndex), chatRoomWrapper);
    }

    /**
     * Returns the count of children contained in the mCrpWrapperGroup given by the <code>groupPosition</code>.
     *
     * @param groupPosition the index of the mCrpWrapperGroup, which children we would like to count
     */
    @Override
    public int getChildrenCount(int groupPosition) {
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
     *
     * @return mCrWrapper list from filtered CrWrapperList list.
     */
    private TreeSet<ChatRoomWrapper> getCrWrapperList(int groupIndex) {
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
     *
     * @return mCrpWrapperGroup list from original list.
     */
    private TreeSet<ChatRoomWrapper> getOriginalCrWrapperList(int groupIndex) {
        if (groupIndex >= 0 && groupIndex < originalCrWrapperList.size()) {
            return originalCrWrapperList.get(groupIndex);
        }
        else {
            return null;
        }
    }

    /**
     * Adds all child mCrWrapperList for all the given <code>mCrpWrapperGroup</code>. Skip adding group of zero child.
     *
     * @param providers the providers mCrpWrapperGroup, which child mCrWrapperList to add
     */
    private void addChatRooms(List<ChatRoomProviderWrapper> providers) {
        for (ChatRoomProviderWrapper provider : providers) {
            List<ChatRoomWrapper> chatRoomWrappers = initBookmarkChatRooms(provider);

            if ((chatRoomWrappers != null) && (!chatRoomWrappers.isEmpty())) {
                addGroup(provider);

                // Use Iterator to avoid ConcurrentModificationException on addChatRoom();
                // Do not use for loop as suggested: ConcurrentModificationException
                Iterator<ChatRoomWrapper> iteratorCRW = chatRoomWrappers.iterator();
                while (iteratorCRW.hasNext()) {
                    addChatRoom(provider, iteratorCRW.next());
                }
            }
        }

        // must refresh list view only after chatRoomWrappers fetch with bookmark info updated
        invalidateViews();
    }

    /**
     * Adds all child mCrWrapperList for all the given <code>mCrpWrapper</code> and update it with bookmark info
     *
     * @param crpWrapper the crpWrapper provider, which child mCrWrapperList to fetch
     */
    private List<ChatRoomWrapper> initBookmarkChatRooms(ChatRoomProviderWrapper crpWrapper) {
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
     * Adds the given <code>crpWrapper</code> to both the originalCrpWrapperGroup and
     * mCrpWrapperGroup only if no existing crpWrapper is found in current lists
     *
     * @param crpWrapper the <code>ChatRoomProviderWrapper</code> to add
     */
    private void addGroup(ChatRoomProviderWrapper crpWrapper) {
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
     * Remove an existing <code>crpWrapper</code> from both the originalCrpWrapperGroup and mCrpWrapperGroup if exist
     *
     * @param crpWrapper the <code>chatRoomProviderWrapper</code> to be removed
     */
    private void removeGroup(ChatRoomProviderWrapper crpWrapper) {
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
     * Adds the given child crWrapper to the <code>crpWrapperGroup</code>.
     *
     * @param crpWrapperGroup the parent ChatRoomProviderWrapper Group of the child ChatRoomWrapper to add
     * @param crWrapper the <code>ChatRoomWrapper</code> to add
     */
    private void addChatRoom(ChatRoomProviderWrapper crpWrapperGroup, ChatRoomWrapper crWrapper) {
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
     * @param providers the <code>ChatRoomProviderWrapper</code>, which content we'd like to remove
     */
    private void removeChatRooms(List<ChatRoomProviderWrapper> providers) {
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
     * Remove the given <code>ChatRoomWrapper</code> from both the original and the filtered list of
     * this adapter. Also remove the group with zero element
     *
     * @param crpWrapper the parent <code>ChatRoomProviderWrapper</code> of the ChatRoomWrapper to remove
     * @param crWrapper the <code>ChatRoomWrapper</code> to remove
     */
    private void removeChatRoom(ChatRoomProviderWrapper crpWrapper, ChatRoomWrapper crWrapper) {
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
     * Returns the chatRoom with the given <code>groupPosition</code> and <code>childPosition</code>.
     *
     * @param groupPosition the index of the mCrpWrapperGroup
     * @param childPosition the index of the child
     *
     * @return the chatRoom with the given <code>groupPosition</code> and <code>childPosition</code>
     */
    @Override
    public Object getChild(int groupPosition, int childPosition) {
        if (!mCrWrapperList.isEmpty()) {
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
    private int getChildIndex(TreeSet<ChatRoomWrapper> crWrapperList, ChatRoomWrapper crWrapper) {
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
     * Filters list data to match the given <code>query</code>.
     *
     * @param query the query we'd like to match
     */
    public void filterData(String query) {
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
                if (!filteredList.isEmpty()) {
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
    public void nonZeroCrpWrapperGroupList() {
        mCrpWrapperGroup.clear();
        mCrWrapperList.clear();

        // hide mCrpWrapperGroup contains zero chatRoomWrapper
        for (ChatRoomProviderWrapper crpWrapper : originalCrpWrapperGroup) {
            int groupIndex = originalCrpWrapperGroup.indexOf(crpWrapper);
            if (groupIndex != -1) {
                TreeSet<ChatRoomWrapper> orgCrwList = getOriginalCrWrapperList(groupIndex);
                if ((orgCrwList != null) && (!orgCrwList.isEmpty())) {
                    mCrpWrapperGroup.add(crpWrapper);
                    mCrWrapperList.add(orgCrwList);
                }
            }
        }
    }

    /**
     * Checks if the given <code>chatRoomWrapper</code> is matching the given <code>query</code>.
     * A <code>ChatRoomWrapper</code> would be matching the filter if one of the following is true:<br>
     * - it is online or user chooses show offline mCrWrapperList
     * - its chatRoom ID or Name matches the filter string
     *
     * @param chatRoomWrapper the <code>chatRoomWrapper</code> to check
     * @param query the query string i.e. chatRoomID to check for matches. A null always return true
     *
     * @return <code>true</code> to indicate that the given <code>chatRoomWrapper</code> is matching the
     * current filter, otherwise returns <code>false</code>
     */
    private boolean isMatching(ChatRoomWrapper chatRoomWrapper, String query) {
        if (TextUtils.isEmpty(query))
            return true;

        String chatRoomID = chatRoomWrapper.getChatRoomID();
        Pattern queryPattern = Pattern.compile(query, Pattern.CASE_INSENSITIVE | Pattern.LITERAL);
        return (queryPattern.matcher(chatRoomID).find());
    }

    /**
     * Checks if the given <code>chatRoomProviderWrapper</code> is matching the current filter.
     * A chatRoomProviderWrapper is matching the current filter only if its protocolProvider
     * matching the current filter.
     *
     * @param chatRoomProviderWrapper the <code>ChatRoomProviderWrapper</code> to check
     * @param query the query string i.e. accountUuid to check for matches. A null will always return true
     *
     * @return <code>true</code> to indicate that the given <code>metaGroup</code> is matching the current
     * filter, otherwise returns <code>false</code>
     */
    public boolean isMatching(ChatRoomProviderWrapper chatRoomProviderWrapper, String query) {
        if (TextUtils.isEmpty(query))
            return true;

        String userUuid = chatRoomProviderWrapper.getProtocolProvider().getAccountID().getAccountUid();
        Pattern queryPattern = Pattern.compile(query, Pattern.CASE_INSENSITIVE | Pattern.LITERAL);
        return (queryPattern.matcher(userUuid).find());
    }

    /**
     * Checks if given <code>ChatRoomWrapper</code> is considered to be selected. That is if the chat
     * session with given <code>ChatRoomWrapper</code> is the one currently visible.
     *
     * @param chatId the <code>ChatID</code> to check.
     *
     * @return <code>true</code> if given <code>ChatRoomWrapper</code> is considered to be selected.
     */
    public static boolean isChatRoomWrapperSelected(String chatId) {
        String currentId = ChatSessionManager.getCurrentChatId();
        ChatPanel activeCP = ChatSessionManager.getActiveChat(chatId);
        return ((currentId != null) && (activeCP != null)
                && currentId.equals(activeCP.getChatSession().getChatId()));
    }

    /**
     * Implements {@link UIGroupRenderer}. {@inheritDoc}
     */
    @Override
    public String getDisplayName(Object groupImpl) {
        return ((ChatRoomProviderWrapper) groupImpl).getProtocolProvider().getAccountID().getAccountUid();
    }

    /**
     * When a provider wrapper is added this method is called to inform listeners.
     * Add group and child only if there is at least one child in the group.
     *
     * @param crpWrapper which was added.
     */
    @Override
    public void chatRoomProviderWrapperAdded(ChatRoomProviderWrapper crpWrapper) {
        // Add the original/filtered chatRoomProvider Wrapper and its list.
        if ((!originalCrpWrapperGroup.contains(crpWrapper))
                || (!mCrpWrapperGroup.contains(crpWrapper))) {

            List<ChatRoomWrapper> chatRoomWrappers = crpWrapper.getChatRooms();
            if (!chatRoomWrappers.isEmpty()) {
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
    public void chatRoomProviderWrapperRemoved(ChatRoomProviderWrapper crpWrapper) {
        // Remove the original/filtered chatRoomProvider Wrapper and its chatRoomWrapper if exist.
        if ((originalCrpWrapperGroup.contains(crpWrapper))
                || (mCrpWrapperGroup.contains(crpWrapper))) {

            removeChatRooms(Collections.singletonList(crpWrapper));
            uiChangeUpdate();
        }
    }

    /**
     * Indicates that a change has occurred in the chatRoom List.
     */
    @Override
    public void contentChanged(ChatRoomListChangeEvent evt) {
        ChatRoomWrapper chatRoomWrapper = evt.getSourceChatRoom();
        switch (evt.getEventID()) {
            case ChatRoomListChangeEvent.CHAT_ROOM_ADDED:
                addChatRoom(chatRoomWrapper.getParentProvider(), chatRoomWrapper);
                break;

            case ChatRoomListChangeEvent.CHAT_ROOM_REMOVED:
                removeChatRoom(chatRoomWrapper.getParentProvider(), chatRoomWrapper);
                aTalkApp.showToastMessage(R.string.chatroom_destroy_successful,
                        chatRoomWrapper.getChatRoomID());
                break;

            case ChatRoomListChangeEvent.CHAT_ROOM_CHANGED:
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
    private void uiChangeUpdate() {
        BaseActivity.uiHandler.post(this::notifyDataSetChanged);
    }
}
