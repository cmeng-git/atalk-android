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
import org.atalk.android.gui.chat.ChatSessionManager;

/**
 * Class used to obtain UI specific data for <tt>MetaContact</tt> instances.
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
        String displayDetails = getDisplayDetails((ChatRoomWrapper) chatRoomWrapper);
        return (displayDetails != null) ? displayDetails : "";
    }

    @Override
    public boolean isDisplayBold(Object chatRoomWrapper)
    {
        return ChatSessionManager.getActiveChat(((ChatRoomWrapper) chatRoomWrapper).getChatRoomID()) != null;
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

    /**
     * Returns the display details for the underlying <tt>MetaContact</tt>.
     *
     * @param chatRoomWrapper the <tt>MetaContact</tt>, which details we're looking for
     * @return the display details for the underlying <tt>MetaContact</tt>
     */
    private static String getDisplayDetails(ChatRoomWrapper chatRoomWrapper)
    {
        // String displayDetails "not implement";
        return null;
    }
}
