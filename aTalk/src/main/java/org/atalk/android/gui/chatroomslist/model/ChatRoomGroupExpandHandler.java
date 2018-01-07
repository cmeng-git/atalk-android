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

import android.widget.ExpandableListView;

import net.java.sip.communicator.service.muc.*;

/**
 * Implements contact groups expand memory.
 *
 * @author Eng Chong Meng
 */
public class ChatRoomGroupExpandHandler implements ExpandableListView.OnGroupExpandListener,
	ExpandableListView.OnGroupCollapseListener

{
	/**
	 * Data key used to remember group state.
	 */
	private static final String KEY_EXPAND_MEMORY = "key.expand.memory";

	/**
	 * Meta contact list adapter used by this instance.
	 */
	private final ChatRoomListAdapter chatRoomList;

	/**
	 * The contact list view.
	 */
	private final ExpandableListView chatRoomListView;

	/**
	 * Creates new instance of <tt>MetaGroupExpandHandler</tt>.
	 *
	 * @param chatRoomList
	 *        contact list data model.
	 * @param chatRoomListView
	 *        contact list view.
	 */
	public ChatRoomGroupExpandHandler(ChatRoomListAdapter chatRoomList, ExpandableListView chatRoomListView)
	{
		this.chatRoomList = chatRoomList;
		this.chatRoomListView = chatRoomListView;
	}

	/**
	 * Binds the listener and restores previous groups expanded/collapsed state.
	 */
	public void bindAndRestore()
	{
		for (int gIdx = 0; gIdx < chatRoomList.getGroupCount(); gIdx++) {
			ChatRoomProviderWrapper chatRoomProviderWrapperGroup
					= (ChatRoomProviderWrapper) chatRoomList.getGroup(gIdx);
			if (Boolean.FALSE.equals(chatRoomProviderWrapperGroup.getData(KEY_EXPAND_MEMORY))) {
				chatRoomListView.collapseGroup(gIdx);
			}
			else {
				// Will expand by default
				chatRoomListView.expandGroup(gIdx);
			}
		}
		chatRoomListView.setOnGroupExpandListener(this);
		chatRoomListView.setOnGroupCollapseListener(this);
	}

	/**
	 * Unbinds the listener.
	 */
	public void unbind()
	{
		chatRoomListView.setOnGroupExpandListener(null);
		chatRoomListView.setOnGroupCollapseListener(null);
	}

	@Override
	public void onGroupCollapse(int groupPosition)
	{
		((ChatRoomProviderWrapper) chatRoomList.getGroup(groupPosition)).setData(KEY_EXPAND_MEMORY, false);
	}

	@Override
	public void onGroupExpand(int groupPosition)
	{
		((ChatRoomProviderWrapper) chatRoomList.getGroup(groupPosition))
				.setData(KEY_EXPAND_MEMORY, true);
	}
}
