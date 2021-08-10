/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License. You may
 * obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions
 * and limitations under the License.
 */
package org.atalk.android.gui.chat.conference;

import net.java.sip.communicator.service.protocol.ChatRoomMember;
import net.java.sip.communicator.service.protocol.ChatRoomMemberRole;

import org.apache.commons.lang3.StringUtils;
import org.atalk.android.R;
import org.atalk.android.aTalkApp;
import org.atalk.android.gui.chat.ChatContact;

/**
 * The <tt>ConferenceChatContact</tt> represents a <tt>ChatContact</tt> in a conference chat.
 *
 * @author Yana Stamcheva
 * @author Lubomir Marinov
 */
public class ConferenceChatContact extends ChatContact<ChatRoomMember>
{

    /**
     * Creates an instance of <tt>ChatContact</tt> by passing to it the <tt>ChatRoomMember</tt> for which it is created.
     *
     * @param chatRoomMember the <tt>ChatRoomMember</tt> for which this <tt>ChatContact</tt> is created.
     */
    public ConferenceChatContact(ChatRoomMember chatRoomMember)
    {
        super(chatRoomMember);
    }

    /**
     * Implements ChatContact#getAvatarBytes(). Delegates to chatRoomMember.
     */
    @Override
    public byte[] getAvatarBytes()
    {
        return descriptor.getAvatar();
    }

    /**
     * Returns the contact name.
     *
     * @return the contact name
     */
    @Override
    public String getName()
    {
        String name = descriptor.getNickName();
        if (StringUtils.isEmpty(name))
            name = aTalkApp.getResString(R.string.service_gui_UNKNOWN_USER);

        return name;
    }

    public ChatRoomMemberRole getRole()
    {
        return descriptor.getRole();
    }

    /**
     * Implements ChatContact#getUID(). Delegates to ChatRoomMember#getContactAddress() because it's supposed to be unique.
     */
    @Override
    public String getUID()
    {
        return descriptor.getContactAddress();
    }
}
