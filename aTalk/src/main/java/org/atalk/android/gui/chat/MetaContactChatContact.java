/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.atalk.android.gui.chat;

import net.java.sip.communicator.service.contactlist.MetaContact;

import org.apache.commons.lang3.StringUtils;
import org.atalk.android.R;
import org.atalk.android.aTalkApp;

/**
 * The <tt>MetaContactChatContact</tt> represents a <tt>ChatContact</tt> in a
 * user-to-user chat.
 *
 * @author Yana Stamcheva
 * @author Lubomir Marinov
 */
public class MetaContactChatContact extends ChatContact<MetaContact>
{

    /**
     * Creates an instance of <tt>ChatContact</tt> by passing to it the
     * corresponding <tt>MetaContact</tt> and <tt>Contact</tt>.
     *
     * @param metaContact the <tt>MetaContact</tt> encapsulating the given <tt>Contact</tt>
     */
    public MetaContactChatContact(MetaContact metaContact)
    {
        super(metaContact);
    }

    /*
     * Implements ChatContact#getAvatarBytes(). Delegates to metaContact.
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
        String name = descriptor.getDisplayName();

        if (StringUtils.isEmpty(name))
            name = aTalkApp.getResString(R.string.service_gui_UNKNOWN_USER);

        return name;
    }

    /*
     * Implements ChatContact#getUID(). Delegates to MetaContact#getMetaUID()
     * because it's known to be unique.
     */
    @Override
    public String getUID()
    {
        return descriptor.getMetaUID();
    }
}
