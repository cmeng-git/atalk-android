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

import android.graphics.drawable.Drawable;

import net.java.sip.communicator.service.muc.ChatRoomWrapper;

import org.atalk.android.R;
import org.atalk.android.aTalkApp;
import org.atalk.android.gui.chat.ChatPanel;
import org.atalk.android.gui.chat.ChatSessionManager;
import org.jivesoftware.smack.util.StringUtils;

/**
 * Class used to obtain UI specific data for <tt>ChatRoom</tt> instances.
 *
 * @author Eng Chong MEng
 */
public class ChatRoomRenderer implements UIChatRoomRenderer
{
    @Override
    public boolean isSelected(Object chatRoomWrapper)
    {
        return ChatRoomListAdapter.isChatRoomWrapperSelected(((ChatRoomWrapper) chatRoomWrapper).getChatRoomID());
    }

    @Override
    public String getDisplayName(Object chatRoomWrapper)
    {
        return ((ChatRoomWrapper) chatRoomWrapper).getChatRoomID();
    }

    @Override
    public String getStatusMessage(Object chatRoomWrapper)
    {
        String displayDetail = getDisplayDetail(chatRoomWrapper);
        if (StringUtils.isEmpty(displayDetail))
            displayDetail = getChatRoomID(chatRoomWrapper).split("@")[0];
        return displayDetail;
    }

    @Override
    public boolean isDisplayBold(Object crWrapper)
    {
        ChatRoomWrapper chatRoomWrapper = (ChatRoomWrapper) crWrapper;
        ChatPanel chatPanel = ChatSessionManager.getActiveChat(chatRoomWrapper.getChatRoomID());

        if (chatPanel != null) {
            if (chatRoomWrapper.getChatRoom().isJoined())
                return true;
            else {
                ChatSessionManager.removeActiveChat(chatPanel);
            }
        }
        return false;

        // return ChatSessionManager.getActiveChat(chatRoomWrapper.getChatRoomID()) != null;
    }

    @Override
    public Drawable getChatRoomIcon(Object chatRoomWrapper)
    {
        return aTalkApp.getAppResources().getDrawable(R.drawable.ic_chatroom);
    }

    @Override
    public String getChatRoomID(Object chatRoomWrapper)
    {
        return ((ChatRoomWrapper) chatRoomWrapper).getChatRoomID();
    }

    @Override
    public boolean isAutoJoin(Object chatRoomWrapper)
    {
        return ((ChatRoomWrapper) chatRoomWrapper).isAutoJoin();
    }

    @Override
    public boolean isBookmark(Object chatRoomWrapper)
    {
        return ((ChatRoomWrapper) chatRoomWrapper).isBookmarked();
    }

    /**
     * Returns the display details for the underlying <tt>ChatRoomWrapper</tt>.
     *
     * @param chatRoomWrapper the <tt>ChatRoomWrapper</tt>, which details we're looking for
     * @return the display details for the underlying <tt>ChatRoomWrapper</tt>
     */
    private static String getDisplayDetail(Object chatRoomWrapper)
    {
        return ((ChatRoomWrapper) chatRoomWrapper).getBookmarkName();
    }
}
