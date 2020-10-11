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

import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.view.*;
import android.widget.*;

import net.java.sip.communicator.impl.muc.MUCActivator;
import net.java.sip.communicator.service.muc.ChatRoomWrapper;
import net.java.sip.communicator.service.protocol.ProtocolProviderService;

import org.atalk.android.R;
import org.atalk.android.aTalkApp;
import org.atalk.android.gui.chatroomslist.ChatRoomBookmarkDialog;
import org.atalk.android.gui.chatroomslist.ChatRoomListFragment;
import org.atalk.android.gui.contactlist.model.UIGroupRenderer;
import org.atalk.android.gui.util.ViewUtil;
import org.atalk.android.gui.widgets.UnreadCountCustomView;
import org.atalk.service.osgi.OSGiActivity;
import org.jivesoftware.smack.SmackException;
import org.jivesoftware.smack.XMPPException;
import org.jivesoftware.smackx.bookmarks.BookmarkManager;
import org.jxmpp.jid.EntityBareJid;

import java.util.HashMap;
import java.util.Map;

import androidx.fragment.app.FragmentTransaction;
import timber.log.Timber;

/**
 * Base class for chatRoom list adapter implementations.
 *
 * @author Eng Chong Meng
 */
public abstract class BaseChatRoomListAdapter extends BaseExpandableListAdapter
        implements View.OnClickListener, View.OnLongClickListener, ChatRoomBookmarkDialog.OnFinishedCallback
{
    /**
     * UI thread handler used to call all operations that access data model. This guarantees that
     * it's accessed from the main thread.
     */
    protected final Handler uiHandler = OSGiActivity.uiHandler;

    /**
     * The chatRoom list view.
     */
    private final ChatRoomListFragment chatRoomListFragment;

    /**
     * The list view.
     */
    private final ExpandableListView chatRoomListView;

    private ChatRoomViewHolder mViewHolder;

    /**
     * A map reference of ChatRoomWrapper to ChatRoomViewHolder for the unread message count update
     */
    private Map<ChatRoomWrapper, ChatRoomViewHolder> crwViewHolder = new HashMap<>();

    private LayoutInflater mInflater;

    /**
     * Creates the chatRoom list adapter.
     *
     * @param crlFragment the parent <tt>ChatRoomListFragment</tt>
     */
    public BaseChatRoomListAdapter(ChatRoomListFragment crlFragment)
    {
        // cmeng - must use this mInflater as crlFragment may not always attached to FragmentManager
        mInflater = LayoutInflater.from(aTalkApp.getGlobalContext());
        chatRoomListFragment = crlFragment;
        chatRoomListView = chatRoomListFragment.getChatRoomListView();
    }

    /**
     * Initializes model data. Is called before adapter is used for the first time.
     */
    public abstract void initModelData();

    /**
     * Filter the chatRoom list with given <tt>queryString</tt>
     *
     * @param queryString the query string we want to match.
     */
    public abstract void filterData(String queryString);

    /**
     * Returns the <tt>UIChatRoomRenderer</tt> for chatRoom of group at given <tt>groupIndex</tt>.
     *
     * @param groupIndex index of the chatRoomWrapper group.
     * @return the <tt>UIChatRoomRenderer</tt> for chatRoom of group at given <tt>groupIndex</tt>.
     */
    protected abstract UIChatRoomRenderer getChatRoomRenderer(int groupIndex);

    /**
     * Returns the <tt>UIGroupRenderer</tt> for group at given <tt>groupPosition</tt>.
     *
     * @param groupPosition index of the chatRoom group.
     * @return the <tt>UIGroupRenderer</tt> for group at given <tt>groupPosition</tt>.
     */
    protected abstract UIGroupRenderer getGroupRenderer(int groupPosition);

    /**
     * Releases all resources used by this instance.
     */
    public void dispose()
    {
        notifyDataSetInvalidated();
    }

    /**
     * Expands all contained groups.
     */
    public void expandAllGroups()
    {
        // Expand group view only when chatRoomListView is in focus (UI mode) - not null
        // cmeng - do not use isFocused() - may not in sync with actual
        uiHandler.post(() -> {
            // FFR:  v2.1.5 NPE even with pre-check for non-null, so add catch exception
            if (chatRoomListView != null) {
                int count = getGroupCount();
                for (int position = 0; position < count; position++) {
                    try {
                        chatRoomListView.expandGroup(position);
                    } catch (Exception e) {
                        Timber.e(e, "Expand group Exception %s; %s", position, chatRoomListFragment);
                    }

                }
            }
        });
    }

    /**
     * Refreshes the view with expands group and invalid view.
     */
    public void invalidateViews()
    {
        if (chatRoomListView != null) {
            chatRoomListFragment.runOnUiThread(chatRoomListView::invalidateViews);
        }
    }

    /**
     * Updates the chatRoomWrapper display name.
     *
     * @param groupIndex the index of the group to update
     * @param chatRoomIndex the index of the chatRoomWrapper to update
     */
    protected void updateDisplayName(final int groupIndex, final int chatRoomIndex)
    {
        int firstIndex = chatRoomListView.getFirstVisiblePosition();
        View chatRoomView = chatRoomListView.getChildAt(getListIndex(groupIndex, chatRoomIndex) - firstIndex);

        if (chatRoomView != null) {
            ChatRoomWrapper crWrapper = (ChatRoomWrapper) getChild(groupIndex, chatRoomIndex);
            ViewUtil.setTextViewValue(chatRoomView, R.id.displayName, crWrapper.getChatRoomID());
        }
    }

    /**
     * Updates the chatRoom icon.
     *
     * @param groupIndex the index of the group to update
     * @param chatRoomIndex the index of the chatRoom to update
     * @param chatRoomWrapper ChatRoomWrapper implementation object instance
     */
    protected void updateChatRoomIcon(final int groupIndex, final int chatRoomIndex, final Object chatRoomWrapper)
    {
        int firstIndex = chatRoomListView.getFirstVisiblePosition();
        View chatRoomView = chatRoomListView.getChildAt(getListIndex(groupIndex, chatRoomIndex) - firstIndex);

        if (chatRoomView != null) {
            ImageView avatarView = chatRoomView.findViewById(R.id.room_icon);
            if (avatarView != null)
                setRoomIcon(avatarView, getChatRoomRenderer(groupIndex).getChatRoomIcon(chatRoomWrapper));
        }
    }

    /**
     * Updates the chatRoomWrapper unread message count.
     * Hide widget if (count == 0)
     *
     * @param groupIndex the index of the group to update
     * @param chatRoomIndex the index of the chatRoomWrapper to update
     */
    public void updateUnreadCount(final ChatRoomWrapper chatRoomWrapper, final int count)
    {
        ChatRoomViewHolder chatRoomViewHolder = crwViewHolder.get(chatRoomWrapper);
        if (chatRoomViewHolder == null)
            return;

        if (count == 0) {
            chatRoomViewHolder.unreadCount.setVisibility(View.GONE);
        }
        else {
            chatRoomViewHolder.unreadCount.setVisibility(View.VISIBLE);
            chatRoomViewHolder.unreadCount.setUnreadCount(count);
        }
    }

    /**
     * Returns the flat list index for the given <tt>groupIndex</tt> and <tt>chatRoomIndex</tt>.
     *
     * @param groupIndex the index of the group
     * @param chatRoomIndex the index of the child chatRoom
     * @return an int representing the flat list index for the given <tt>groupIndex</tt> and <tt>chatRoomIndex</tt>
     */
    public int getListIndex(int groupIndex, int chatRoomIndex)
    {
        int lastIndex = chatRoomListView.getLastVisiblePosition();

        for (int i = 0; i <= lastIndex; i++) {
            long lPosition = chatRoomListView.getExpandableListPosition(i);

            int groupPosition = ExpandableListView.getPackedPositionGroup(lPosition);
            int childPosition = ExpandableListView.getPackedPositionChild(lPosition);

            if ((groupIndex == groupPosition) && (chatRoomIndex == childPosition)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Returns the identifier of the child contained on the given <tt>groupPosition</tt> and <tt>childPosition</tt>.
     *
     * @param groupPosition the index of the group
     * @param childPosition the index of the child
     * @return the identifier of the child contained on the given <tt>groupPosition</tt> and <tt>childPosition</tt>
     */
    @Override
    public long getChildId(int groupPosition, int childPosition)
    {
        return childPosition;
    }

    /**
     * Returns the child view for the given <tt>groupPosition</tt>, <tt>childPosition</tt>.
     *
     * @param groupPosition the group position of the desired view
     * @param childPosition the child position of the desired view
     * @param isLastChild indicates if this is the last child
     * @param convertView the view to fill with data
     * @param parent the parent view group
     */
    @Override
    public View getChildView(int groupPosition, int childPosition, boolean isLastChild,
            View convertView, ViewGroup parent)
    {
        // Keeps reference to avoid future findViewById()
        ChatRoomViewHolder chatRoomViewHolder;
        Object child = getChild(groupPosition, childPosition);

        if (convertView == null) {
            convertView = mInflater.inflate(R.layout.chatroom_list_row, parent, false);

            chatRoomViewHolder = new ChatRoomViewHolder();
            chatRoomViewHolder.roomName = convertView.findViewById(R.id.room_name);
            chatRoomViewHolder.statusMessage = convertView.findViewById(R.id.room_status);

            chatRoomViewHolder.roomIcon = convertView.findViewById(R.id.room_icon);
            chatRoomViewHolder.roomIcon.setOnClickListener(this);
            chatRoomViewHolder.roomIcon.setTag(chatRoomViewHolder);

            chatRoomViewHolder.unreadCount = convertView.findViewById(R.id.unread_count);
            chatRoomViewHolder.unreadCount.setTag(chatRoomViewHolder);

            chatRoomViewHolder.autojoin = convertView.findViewById(R.id.cb_autojoin);
            chatRoomViewHolder.autojoin.setOnClickListener(this);
            chatRoomViewHolder.autojoin.setTag(chatRoomViewHolder);

            chatRoomViewHolder.bookmark = convertView.findViewById(R.id.cb_bookmark);
            chatRoomViewHolder.bookmark.setOnClickListener(this);
            chatRoomViewHolder.bookmark.setTag(chatRoomViewHolder);

            convertView.setTag(chatRoomViewHolder);
        }
        else {
            chatRoomViewHolder = (ChatRoomViewHolder) convertView.getTag();
        }

        chatRoomViewHolder.groupPosition = groupPosition;
        chatRoomViewHolder.childPosition = childPosition;

        // return and stop further process if child has been removed
        if (!(child instanceof ChatRoomWrapper))
            return convertView;

        // Must init child Tag here as reused convertView may not necessary contains the correct crWrapper
        View roomView = convertView.findViewById(R.id.room_view);
        roomView.setOnClickListener(this);
        roomView.setOnLongClickListener(this);
        roomView.setTag(child);

        ChatRoomWrapper crWrapper = (ChatRoomWrapper) child;
        crwViewHolder.put(crWrapper, chatRoomViewHolder);
        updateUnreadCount(crWrapper, crWrapper.getUnreadCount());

        UIChatRoomRenderer renderer = getChatRoomRenderer(groupPosition);
        if (renderer.isSelected(child)) {
            convertView.setBackgroundResource(R.drawable.color_blue_gradient);
        }
        else {
            convertView.setBackgroundResource(R.drawable.list_selector_state);
        }
        // Update display information.
        String roomName = renderer.getDisplayName(child);
        String roomStatus = renderer.getStatusMessage(child);
        chatRoomViewHolder.roomName.setText(roomName);
        chatRoomViewHolder.statusMessage.setText(roomStatus);

        chatRoomViewHolder.autojoin.setChecked(renderer.isAutoJoin(child));
        chatRoomViewHolder.bookmark.setChecked(renderer.isBookmark(child));

        if (renderer.isDisplayBold(child)) {
            chatRoomViewHolder.roomName.setTypeface(Typeface.DEFAULT_BOLD);
        }
        else {
            chatRoomViewHolder.roomName.setTypeface(Typeface.DEFAULT);
        }

        // Set room Icon.
        setRoomIcon(chatRoomViewHolder.roomIcon, renderer.getChatRoomIcon(child));
        return convertView;
    }

    /**
     * Returns the group view for the given <tt>groupPosition</tt>.
     *
     * @param groupPosition the group position of the desired view
     * @param isExpanded indicates if the view is currently expanded
     * @param convertView the view to fill with data
     * @param parent the parent view group
     */
    @Override
    public View getGroupView(int groupPosition, boolean isExpanded, View convertView, ViewGroup parent)
    {
        // Keeps reference to avoid future findViewById()
        GroupViewHolder groupViewHolder;

        if (convertView == null) {
            convertView = mInflater.inflate(R.layout.chatroom_list_group_row, parent, false);

            groupViewHolder = new GroupViewHolder();
            groupViewHolder.ppsUserId = convertView.findViewById(R.id.displayName);
            groupViewHolder.indicator = convertView.findViewById(R.id.groupIndicatorView);
            convertView.setTag(groupViewHolder);
        }
        else {
            groupViewHolder = (GroupViewHolder) convertView.getTag();
        }

        Object group = getGroup(groupPosition);
        if (group != null) {
            UIGroupRenderer groupRenderer = getGroupRenderer(groupPosition);
            groupViewHolder.ppsUserId.setText(groupRenderer.getDisplayName(group));
        }

        // Group expand indicator
        int indicatorResId = isExpanded ? R.drawable.expanded_dark : R.drawable.collapsed_dark;
        groupViewHolder.indicator.setImageResource(indicatorResId);
        return convertView;
    }

    /**
     * Returns the identifier of the group given by <tt>groupPosition</tt>.
     *
     * @param groupPosition the index of the group, which identifier we're looking for
     */
    @Override
    public long getGroupId(int groupPosition)
    {
        return groupPosition;
    }

    /**
     *
     */
    @Override
    public boolean hasStableIds()
    {
        return true;
    }

    /**
     * Indicates that all children are selectable.
     */
    @Override
    public boolean isChildSelectable(int groupPosition, int childPosition)
    {
        return true;
    }

    /**
     * We keep one instance of view click listener to avoid unnecessary allocations.
     * Clicked positions are obtained from the view holder.
     */
    public void onClick(View view)
    {
        Object object = view.getTag();
        if (object instanceof ChatRoomViewHolder) {
            mViewHolder = (ChatRoomViewHolder) view.getTag();
            int groupPos = mViewHolder.groupPosition;
            int childPos = mViewHolder.childPosition;
            object = getChild(groupPos, childPos);
        }

        if (object instanceof ChatRoomWrapper) {
            ChatRoomWrapper chatRoomWrapper = (ChatRoomWrapper) object;

            switch (view.getId()) {
                case R.id.room_view:
                    chatRoomListFragment.joinChatRoom(chatRoomWrapper);
                    break;

                case R.id.cb_autojoin:
                    // Set chatRoom autoJoin on first login
                    chatRoomWrapper.setAutoJoin(mViewHolder.autojoin.isChecked());
                    if (chatRoomWrapper.isAutoJoin()) {
                        MUCActivator.getMUCService().joinChatRoom(chatRoomWrapper);
                    }

                    // Continue to update server BookMarkConference data if bookmark is checked
                    if (mViewHolder.bookmark.isChecked()) {
                        ProtocolProviderService pps = chatRoomWrapper.getProtocolProvider();
                        BookmarkManager bookmarkManager = BookmarkManager.getBookmarkManager(pps.getConnection());
                        EntityBareJid entityBareJid = chatRoomWrapper.getEntityBareJid();
                        chatRoomWrapper.setBookmark(mViewHolder.bookmark.isChecked());
                        try {
                            if (mViewHolder.bookmark.isChecked()) {
                                bookmarkManager.addBookmarkedConference(chatRoomWrapper.getBookmarkName(), entityBareJid,
                                        chatRoomWrapper.isAutoJoin(), chatRoomWrapper.getNickResource(),
                                        chatRoomWrapper.loadPassword());
                            }
                            else {
                                bookmarkManager.removeBookmarkedConference(entityBareJid);
                            }
                        } catch (SmackException.NoResponseException | SmackException.NotConnectedException
                                | XMPPException.XMPPErrorException | InterruptedException e) {
                            Timber.w("Failed to update Bookmarks: %s", e.getMessage());
                        }
                    }
                    break;

                case R.id.room_icon:
                case R.id.cb_bookmark:
                    FragmentTransaction ft = chatRoomListFragment.getParentFragmentManager().beginTransaction();
                    ft.addToBackStack(null);
                    ChatRoomBookmarkDialog chatRoomBookmarkFragment
                            = ChatRoomBookmarkDialog.getInstance(chatRoomWrapper, this);
                    chatRoomBookmarkFragment.show(ft, "bmDdialog");
                    break;
                default:
                    break;
            }
        }
        else {
            Timber.w("Clicked item is not a chatRoom Wrapper");
        }
    }

    @Override
    public boolean onLongClick(View view)
    {
        Object chatRoomWrapper = view.getTag();
        if (chatRoomWrapper instanceof ChatRoomWrapper) {
            chatRoomListFragment.showPopupMenu(view, (ChatRoomWrapper) chatRoomWrapper);
            return true;
        }
        return false;
    }

    /**
     * update bookmark check on dialog close
     */
    @Override
    public void onCloseDialog()
    {
        // retain current state unless change by user in dialog
        ChatRoomWrapper chatRoomWrapper
                = (ChatRoomWrapper) getChild(mViewHolder.groupPosition, mViewHolder.childPosition);
        if (chatRoomWrapper != null)
            mViewHolder.bookmark.setChecked((chatRoomWrapper.isBookmarked()));
    }

    /**
     * Sets the room icon of the chatRoom row.
     *
     * @param roomIconView the room Icon image view
     * @param roomImage the room Icon image view
     */
    private void setRoomIcon(ImageView roomIconView, Drawable roomImage)
    {
        if (roomImage == null) {
            roomImage = aTalkApp.getAppResources().getDrawable(R.drawable.ic_chatroom);
        }
        roomIconView.setImageDrawable(roomImage);
    }

    private static class ChatRoomViewHolder
    {
        TextView roomName;
        TextView statusMessage;
        ImageView roomIcon;
        CheckBox autojoin;
        CheckBox bookmark;
        UnreadCountCustomView unreadCount;
        int groupPosition;
        int childPosition;
    }

    private static class GroupViewHolder
    {
        ImageView indicator;
        TextView ppsUserId;
    }
}