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

package org.jivesoftware.smackx.avatar.useravatar;

import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smackx.avatar.useravatar.packet.AvatarMetadata.Info;
import org.jxmpp.jid.EntityBareJid;

/**
 * A factory for AvatarRetriever.
 */
public final class AvatarRetrieverFactory
{
    /**
     * Private constructor.
     */
    private AvatarRetrieverFactory()
    {
    }

    /**
     * Get a AvatarRetriever to retrieve this avatar.
     *
     * @param con the connection
     * @param from the user which own the avatar
     * @param info the metadata information of the avatar to retrieve
     * @return an AvatarRetriever null if none can retrieve this avatar
     */
    public static AvatarRetriever getRetriever(XMPPConnection con, EntityBareJid from, Info info)
    {
        String url = info.getUrl();
        if (url != null) {
            return new HttpAvatarRetriever(url);
        }
        return new XmppAvatarRetriever(con, from, info.getId());
    }
}
