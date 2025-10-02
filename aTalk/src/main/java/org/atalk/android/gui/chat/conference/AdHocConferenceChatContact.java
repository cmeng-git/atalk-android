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

import net.java.sip.communicator.service.protocol.Contact;

import org.apache.commons.lang3.StringUtils;
import org.atalk.android.R;
import org.atalk.android.aTalkApp;
import org.atalk.android.gui.chat.ChatContact;

/**
 * The <code>AdHocConferenceChatContact</code> represents a <code>ChatContact</code> in an ad-hoc conference chat.
 *
 * @author Valentin Martinet
 * @author Lubomir Marinov
 */
public class AdHocConferenceChatContact extends ChatContact<Contact> {

    /**
     * Creates an instance of <code>AdHocConferenceChatContact</code> by passing to it the <code>Contact</code> for which it is created.
     *
     * @param participant the <code>Contact</code> for which this <code>AdHocConferenceChatContact</code> is created.
     */
    public AdHocConferenceChatContact(Contact participant) {
        super(participant);
    }

    @Override
    protected byte[] getAvatarBytes() {
        return descriptor.getImage(false);
    }

    /**
     * Returns the contact name.
     *
     * @return the contact name
     */
    @Override
    public String getName() {
        String name = descriptor.getDisplayName();
        if (StringUtils.isEmpty(name))
            name = aTalkApp.getResString(R.string.unknown_user);
        return name;
    }

    /*
     * Implements ChatContact#getUID(). Delegates to Contact#getAddress() because it's supposed to be unique.
     */
    @Override
    public String getUID() {
        return descriptor.getAddress();
    }
}
