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

package org.jivesoftware.smackx.avatar.vcardavatar.listener;

import org.jivesoftware.smackx.vcardtemp.packet.VCard;
import org.jxmpp.jid.Jid;

/**
 * A listener for avatar changes event.
 *
 * @author Eng Chong Meng
 */
public interface VCardAvatarListener
{
    /**
     * Event which is fired when a contact change avatar.
     *
     * @param from the contact fullJid who changes his avatar
     * @param avatarId the new avatar id, may be null if the contact set no avatar
     * @param vCardInfo the VCard info, may be empty if the no VCard is retrieved
     */
    void onAvatarChange(Jid from, String avatarId, VCard vCardInfo);
}
