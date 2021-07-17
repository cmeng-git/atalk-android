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

package org.jivesoftware.smackx.avatar.useravatar.listener;

import org.jivesoftware.smackx.avatar.useravatar.packet.AvatarMetadata.Info;
import org.jxmpp.jid.EntityBareJid;

import java.util.List;

/**
 * A listener for userAvatar changes event.
 *
 * @author Eng Chong Meng
 */
public interface UserAvatarListener
{
    /**
     * Event which is fired when a contact change avatar.
     *
     * @param from the contact EntityBareJid who change his avatar
     * @param avatarId the new avatar id, may be null if the contact set no avatar
     * @param avatarInfo the metadata info of the userAvatar, may be empty if the contact set no avatar
     */
    void onAvatarChange(EntityBareJid from, String avatarId, List<Info> avatarInfo);

}
