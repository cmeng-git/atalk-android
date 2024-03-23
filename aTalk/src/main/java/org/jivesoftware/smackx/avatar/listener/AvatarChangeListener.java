/*
 * aTalk, android VoIP and Instant Messaging client
 * Copyright 2014~2024 Eng Chong Meng
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

package org.jivesoftware.smackx.avatar.listener;

import org.jxmpp.jid.EntityBareJid;

public interface AvatarChangeListener {
    /**
     * Event which is fired when a contact change avatar.
     *
     * @param from the contact EntityBareJid who change his avatar.
     * @param olAvatarId the old avatar id, may be null.
     * @param newAvatarId the new avatar id, may be null if the contact set no avatar.
     */
    void onAvatarChange(EntityBareJid from, String olAvatarId, String newAvatarId);
}
