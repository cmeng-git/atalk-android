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

import org.jivesoftware.smack.*;
import org.jivesoftware.smackx.avatar.useravatar.packet.AvatarData;
import org.jivesoftware.smackx.pubsub.*;
import org.jxmpp.jid.BareJid;

import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * An AvatarRetriever which retrieve the avatar over the XMPP connection.
 */
public class XmppAvatarRetriever implements AvatarRetriever
{
    /**
     * The logger.
     */
    private static final Logger LOGGER = Logger.getLogger(XmppAvatarRetriever.class.getName());

    private PubSubManager mPubSubManager;
    private String mId;

    /**
     * Create an XmppAvatarRetriever.
     *
     * @param conn the xmpp connection
     * @param toAddress the contact from which we retrieve the avatar
     * @param id the id of the avatar to retrieve
     */
    public XmppAvatarRetriever(XMPPConnection conn, BareJid toAddress, String id)
    {
        mPubSubManager = PubSubManager.getInstanceFor(conn, toAddress);
        mId = id;
    }

    @Override
    public byte[] getAvatar()
    {
        List<Item> items = new ArrayList<>();
        try {
            LeafNode node = mPubSubManager.getLeafNode(AvatarData.NAMESPACE);
            items = node.getItems(Collections.singletonList(mId));
        } catch (XMPPException.XMPPErrorException | SmackException.NoResponseException
                | SmackException.NotConnectedException | InterruptedException
                | PubSubException.NotAPubSubNodeException
                | PubSubException.NotALeafNodeException e) {
            LOGGER.log(Level.WARNING, "Error while retrieving avatar data: " + e.getMessage());
        }

        if (items != null && !items.isEmpty()) {
            PayloadItem<AvatarData> item = (PayloadItem<AvatarData>) items.get(0);
            AvatarData avatar = item.getPayload();
            return avatar.getData();
        }
        return null;
    }
}
