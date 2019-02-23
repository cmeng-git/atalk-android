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

import net.java.sip.communicator.service.muc.ChatRoomWrapper;

import org.atalk.android.R;
import org.atalk.android.aTalkApp;
import org.atalk.android.gui.chatroomslist.ChatRoomListFragment;
import org.atalk.android.gui.contactlist.model.UIGroupRenderer;
import org.atalk.android.gui.util.ViewUtil;
import org.atalk.service.osgi.OSGiActivity;

import timber.log.Timber;

/**
 * Base class for chatRoom list adapter implementations.
 *
 * @author Eng Chong Meng
 */
public abstract class BaseChatRoomListAdapter extends BaseExpandableListAdapter
{
    /**
     * UI thread handler used to call all operations that access data model. This guarantees that
     * it's accessed from the single thread.
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

    /**
     * Creates the chatRoom list adapter.
     *
     * @param crlFragment the parent <tt>ChatRoomListFragment</tt>
     */
    public BaseChatRoomListAdapter(ChatRoomListFragment crlFragment)
    {
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
        // Expand group view only when chatRoomListView is in focus (UI mode)
        // cmeng - do not use isFocused() - may not in sync with actual
        uiHandler.post(() -> {
            int count = getGroupCount();
            for (int position = 0; position < count; position++)
                chatRoomListView.expandGroup(position);
        });
    }

    /**
     * Refreshes the list view.
     */
    public void invalidateViews()
    {
        if (chatRoomListView != null) {
            chatRoomListFragment.runOnUiThread(() -> chatRoomListView.invalidateViews());
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
        View chatRoomView = chatRoomListView.getChildAt(
                getListIndex(groupIndex, chatRoomIndex) - firstIndex);

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
    protected void updateChatRoomIcon(final int groupIndex, final int chatRoomIndex,
            final Object chatRoomWrapper)
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
     * Returns the flat list index for the given <tt>groupIndex</tt> and <tt>chatRoomIndex</tt>.
     *
     * @param groupIndex the index of the group
     * @param chatRoomIndex the index of the child chatRoom
     * @return an int representing the flat list index for the given <tt>groupIndex</tt> and
     * <tt>chatRoomIndex</tt>
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
     * Returns the identifier of the child contained on the given <tt>groupPosition</tt> and
     * <tt>childPosition</tt>.
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

        if (convertView == null) {
            LayoutInflater inflater = chatRoomListFragment.getLayoutInflater();
            convertView = inflater.inflate(R.layout.chatroom_list_row, parent, false);

            chatRoomViewHolder = new ChatRoomViewHolder();
            chatRoomViewHolder.roomName = convertView.findViewById(R.id.roominfo_name);
            chatRoomViewHolder.statusMessage = convertView.findViewById(R.id.room_status);

            chatRoomViewHolder.roomIcon = convertView.findViewById(R.id.room_icon);
            chatRoomViewHolder.roomIcon.setOnClickListener(roomIconClickListener);
            chatRoomViewHolder.roomIcon.setTag(chatRoomViewHolder);

            convertView.setTag(chatRoomViewHolder);
        }
        else {
            chatRoomViewHolder = (ChatRoomViewHolder) convertView.getTag();
        }

        chatRoomViewHolder.groupPosition = groupPosition;
        chatRoomViewHolder.childPosition = childPosition;

        // return and stop further process if child has been removed
        Object child = getChild(groupPosition, childPosition);
        if ((child == null) || !(child instanceof ChatRoomWrapper))
            return convertView;

        UIChatRoomRenderer renderer = getChatRoomRenderer(groupPosition);
        if (renderer.isSelected(child)) {
            convertView.setBackgroundResource(R.drawable.list_selection_gradient);
        }
        else {
            convertView.setBackgroundResource(R.drawable.chatroom_list_selector);
        }
        // Update display information.
        String roomName = renderer.getDisplayName(child);
        String roomStatus = renderer.getStatusMessage(child);
        chatRoomViewHolder.roomName.setText(roomName);
        chatRoomViewHolder.statusMessage.setText(roomStatus);

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
    public View getGroupView(int groupPosition, boolean isExpanded, View convertView,
            ViewGroup parent)
    {
        // Keeps reference to avoid future findViewById()
        GroupViewHolder groupViewHolder;

        if (convertView == null) {
            LayoutInflater inflater = chatRoomListFragment.getLayoutInflater();
            convertView = inflater.inflate(R.layout.chatroom_list_group_row, parent, false);

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
     * We keep one instance of avatar button listener to avoid unnecessary allocations. Clicked
     * positions are obtained from the view holder.
     */
    private final RoomIconClickListener roomIconClickListener = new RoomIconClickListener();

    private class RoomIconClickListener implements View.OnClickListener
    {
        public void onClick(View view)
        {
            if (!(view.getTag() instanceof ChatRoomViewHolder)) {
                return;
            }

            ChatRoomViewHolder viewHolder = (ChatRoomViewHolder) view.getTag();
            int groupPos = viewHolder.groupPosition;
            int childPos = viewHolder.childPosition;
            Object chatRoomWrapper = getChild(groupPos, childPos);
            if (chatRoomWrapper == null) {
                Timber.w("No chatRoom found at %d:%d", groupPos, childPos);
            }
            else {
                String chatRoomName = getChatRoomRenderer(groupPos).getChatRoomID(chatRoomWrapper);

                // make toast, show chatRoom details
                Toast.makeText(chatRoomListFragment.getActivity(), chatRoomName,
                        Toast.LENGTH_SHORT).show();
            }
        }
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
        ImageView selectedBgView;
        int groupPosition;
        int childPosition;
    }

    private static class GroupViewHolder
    {
        ImageView indicator;
        TextView ppsUserId;
    }
}