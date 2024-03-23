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
package net.java.sip.communicator.service.contactlist.event;

import net.java.sip.communicator.service.contactlist.MetaContact;

/**
 * Indicates that a meta contact has changed or added an avatar.
 *
 * @author Emil Ivov
 */
public class MetaContactAvatarUpdateEvent extends MetaContactPropertyChangeEvent
{
    /**
     * Serial version UID.
     */
    private static final long serialVersionUID = 0L;

    /**
     * Creates an instance of this event using the specified arguments.
     *
     * @param source the <code>MetaContact</code> that this event is about.
     * @param oldAvatar the new avatar just of this meta contact.
     * @param newAvatar the old avatar that just got replaced or <code>null</code>.
     */
    public MetaContactAvatarUpdateEvent(MetaContact source, String oldAvatar, String newAvatar)
    {
        super(source, META_CONTACT_AVATAR_UPDATE, oldAvatar, newAvatar);
    }

    /**
     * Returns the updated avatar of the source meta contact as it is now, after the change.
     *
     * @return the newly changed avatar for this meta contact.
     */
    public byte[] getNewAvatar()
    {
        return (byte[]) getNewValue();
    }

    /**
     * Returns the previously active avatar of the source meta contact as it was now, before the change.
     *
     * @return the avatar that got replaced by the new one.
     */
    public byte[] getOldAvatar()
    {
        return (byte[]) getOldValue();
    }
}
