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

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.imageio.ImageIO;

import org.jivesoftware.smack.SmackException;
import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.XMPPConnectionRegistry;
import org.jivesoftware.smack.XMPPException;
import org.jivesoftware.smack.packet.Message;
import org.jivesoftware.smack.util.StringUtils;
import org.jivesoftware.smackx.avatar.AvatarManager;
import org.jivesoftware.smackx.avatar.useravatar.packet.AvatarData;
import org.jivesoftware.smackx.avatar.useravatar.packet.AvatarMetadata;
import org.jivesoftware.smackx.avatar.useravatar.packet.AvatarMetadata.Info;
import org.jivesoftware.smackx.pep.PepEventListener;
import org.jivesoftware.smackx.pep.PepManager;
import org.jivesoftware.smackx.pubsub.Item;
import org.jivesoftware.smackx.pubsub.LeafNode;
import org.jivesoftware.smackx.pubsub.PayloadItem;
import org.jivesoftware.smackx.pubsub.PubSubException;
import org.jivesoftware.smackx.pubsub.PubSubManager;
import org.jxmpp.jid.EntityBareJid;

/**
 * The XEP-0084: User Avatar protocol Implementation. The XMPP protocol extension is used for
 * exchanging user avatars, which are small images or icons associated with human users. The
 * protocol specifies payload formats for both avatar metadata and the image data itself. The
 * payload formats are typically transported using the personal eventing profile of XMPP
 * publish-subscribe as specified in XEP-0163.
 * <p>
 *
 * @author Eng Chong Meng
 */
public class UserAvatarManager extends AvatarManager {
    /*
     * The logger.
     */
    private static final Logger LOGGER = Logger.getLogger(UserAvatarManager.class.getName());

    /*
     * The pubSub node for avatar metadata.
     */
    public static final String AVATAR_METADATA_NODE = AvatarMetadata.NAMESPACE;

    private static final Map<XMPPConnection, UserAvatarManager> instances = new WeakHashMap<>();

    /*
     * The PEPManager associated with tis connection.
     */
    private final PepManager mPepManager;

    static {
        XMPPConnectionRegistry.addConnectionCreationListener(UserAvatarManager::getInstanceFor);
    }

    public static synchronized UserAvatarManager getInstanceFor(XMPPConnection connection) {
        UserAvatarManager userAvatarManager = instances.get(connection);
        if (userAvatarManager == null) {
            userAvatarManager = new UserAvatarManager(connection);
            instances.put(connection, userAvatarManager);
        }
        return userAvatarManager;
    }

    /**
     * Create an UserAvatarManager for each registered account.
     *
     * @param connection the registered account XMPP connection
     */
    private UserAvatarManager(XMPPConnection connection) {
        super(connection);
        /*
         * Add Listener to receive PEPEvent
         */
        mPepManager = PepManager.getInstanceFor(connection);
        mPepManager.addPepEventListener(AVATAR_METADATA_NODE, AvatarMetadata.class, new PepEventAvatarListener());
    }

    /* ===================================================================================== */

    /**
     * Publish user avatar - send the XMPP stanza to enable the publication of an avatar.
     * Before updating the avatar metadata node, the publisher MUST make sure that the avatar data
     * is available at the data node or URL
     *
     * @param avatar the avatar to publish after conversation
     *
     * @return true on success false otherwise
     */
    public boolean publishAvatar(byte[] avatar) {
        AvatarMetadata meta = new AvatarMetadata();

        // The png format is mandatory for interoperability
        AvatarMetadata.Info avInfo = getAvInfo(avatar);
        meta.addInfo(avInfo);
        return publishAvatarMetaData(avInfo.getId(), meta);
    }

    /**
     * Send this bitmap to the avatar data node of the pep server.
     *
     * @param byteData the avatar bytes
     *
     * @return the resulting info associate with this bitmap.
     */
    private AvatarMetadata.Info getAvInfo(byte[] byteData) {
        String dataId = getAvatarHash(byteData);
        publishAvatarData(dataId, byteData);

        // save a copy of data in persistent storage and update its index
        addAvatarImageByAvatarId(dataId, byteData);
        addJidToAvatarHashIndex(mAccount, dataId);

        String mimeType = "image/png";
        ByteArrayInputStream stream = new ByteArrayInputStream(byteData);
        Bitmap avatar = BitmapFactory.decodeStream(stream);

        AvatarMetadata.Info info = new AvatarMetadata.Info(dataId, mimeType, byteData.length);
        info.setHeight(avatar.getHeight());
        info.setWidth(avatar.getWidth());
        return info;
    }

    /**
     * Disable the diffusion of your avatar.
     */
    public boolean disableAvatarPublishing() {
        purgeAvatarImageByJid(mConnection.getUser().asBareJid());
        AvatarMetadata metadata = new AvatarMetadata();
        return publishAvatarMetaData(null, metadata);
    }

    /**
     * Send an avatar image to the pep server.
     *
     * @param id the id of the avatar data
     * @param data the byteData of the avatar
     */
    private void publishAvatarData(String id, byte[] data) {
        AvatarData avatar = new AvatarData(data);
        PayloadItem<AvatarData> item = new PayloadItem<>(id, avatar);
        String node = AvatarData.NAMESPACE;

        try {
            mPepManager.publish(node, item);
        } catch (SmackException.NotConnectedException
                 | InterruptedException
                 | SmackException.NoResponseException
                 | XMPPException.XMPPErrorException
                 | PubSubException.NotALeafNodeException e) {
            nodePublish(item, node);
            LOGGER.log(Level.WARNING, "Use aTalk own nodePublish: " + e.getMessage());
        }
    }

    /**
     * Send the metadata of the avatar you want to publish.
     * By sending this metadata, you publish an avatar.
     *
     * @param id the id of the metadata item
     * @param metadata the metadata to publish
     */
    private boolean publishAvatarMetaData(String id, AvatarMetadata metadata) {
        PayloadItem<AvatarMetadata> item = new PayloadItem<>(id, metadata);
        String node = AvatarMetadata.NAMESPACE;

        try {
            mPepManager.publish(node, item);
            return true;
        } catch (SmackException.NotConnectedException
                 | InterruptedException
                 | SmackException.NoResponseException
                 | XMPPException.XMPPErrorException
                 | PubSubException.NotALeafNodeException e) {
            LOGGER.log(Level.WARNING, "Use aTalk own nodePublish: " + e.getMessage());
            return nodePublish(item, node);
        }
    }

    // ============== Patch to Smack 4.2.1-beta2-SNAPSHOT =================

    /**
     * Alternate aTalk nodePublish if PepManager#publish() failed
     *
     * @param item the item to publish.
     * @param node the node to publish on.
     *
     * @return <code>true</code> if publish is successful
     */
    private boolean nodePublish(Item item, String node) {
        LOGGER.warning("Use alternative aTalk nodePublish for avatar!");
        PubSubManager pubSubManager = PubSubManager.getInstanceFor(mConnection, mAccount);
        try {
            LeafNode pubSubNode = getLeafNode(pubSubManager, node);
            if (pubSubNode != null) {
                pubSubNode.publish(item);
                return true;
            }
        } catch (SmackException.NotConnectedException | InterruptedException
                 | XMPPException.XMPPErrorException | SmackException.NoResponseException e) {
            LOGGER.log(Level.WARNING, "Error in nodePublish: " + e.getMessage());
        }
        return false;
    }

    /**
     * Get an instance of leafNode to publish avatar data
     *
     * @param pubSubManager the PubSubManager
     * @param nodeId the node to publish on.
     *
     * @return an instance of leafNode
     */
    private LeafNode getLeafNode(PubSubManager pubSubManager, String nodeId) {
        try {
            Constructor<LeafNode> getLeafNode = LeafNode.class.getDeclaredConstructor(PubSubManager.class, String.class);
            getLeafNode.setAccessible(true);
            return getLeafNode.newInstance(pubSubManager, nodeId);
        } catch (NoSuchMethodException | IllegalAccessException
                 | InstantiationException | InvocationTargetException e) {
            LOGGER.log(Level.WARNING, "Exception in geLeafNode");
        }
        return null;
    }

    /* ===================================================================================== */

    /**
     * Download an avatar.
     *
     * @param from The jid of the user
     * @param avatarId the id of the avatar being received.
     * @param info the metadata information of the avatar to download
     *
     * @return true if the download was successful
     */
    public boolean downloadAvatar(EntityBareJid from, String avatarId, Info info) {
        /* acts if only new avatarId is received. null => client not ready so no action; also it is still connected */
        if (StringUtils.isNotEmpty(avatarId) && isAvatarNew(from, avatarId) && (mConnection != null)) {
            try {
                String oldAvatarId = getAvatarHashByJid(from);
                AvatarRetriever retriever = AvatarRetrieverFactory.getRetriever(mConnection, from, info);
                byte[] avatar = retriever.getAvatar();
                // FCR indicates avatar can be null => system crash
                if (avatar == null)
                    return false;

                addAvatarImageByAvatarId(avatarId, avatar);
                // add an index hash for the jid
                addJidToAvatarHashIndex(from, avatarId);

                /*
                 * Purge old avatar item from the persistent storage if:
                 * - Current avatar hash is not empty i.e. "" => a newly received avatar
                 * - the currentAvatarHash is single user owner
                 * - skip if currentAvatarHash.equals(avatarId) => cache and persistent not sync
                 */
                if (StringUtils.isNotEmpty(oldAvatarId) && !oldAvatarId.equals(avatarId)
                        && !isHashMultipleOwner(from, oldAvatarId))
                    persistentAvatarCache.purgeItemFor(oldAvatarId);

                LOGGER.log(Level.INFO, "Avatar with new avatarHash received (old => new) "
                        + "from " + from + "\n" + oldAvatarId + "\n" + avatarId);
                return true;
            } catch (IOException e) {
                LOGGER.log(Level.WARNING, "Error while downloading userAvatar: " + e.getMessage());
                return false;
            }
        }
        return false;
    }

    /**
     * Select the avatar to download.
     * Subclass should override this method to take control over the selection process.
     * This implementation select the first element.
     *
     * @param available list of the avatar metadata information
     *
     * @return the metadata of the avatar to download
     */
    public Info selectAvatar(List<Info> available) {
        return available.get(0);
    }

    private class PepEventAvatarListener implements PepEventListener<AvatarMetadata> {
        /**
         * Handler for onPepEvent received. Special case to handle if received from own self.
         * Otherwise, the avatar for user (added as contact entity) will not be updated.
         *
         * @param from the sender
         * @param event AvatarMetadata element containing child 'info'
         * @param id node item id
         * @param message carrier message if any
         */
        @Override
        public void onPepEvent(EntityBareJid from, AvatarMetadata event, String id, Message message) {
            String oldAvatarId = getAvatarHashByJid(from);
            // Timber.d("Received PepEvent: %s %s %s", from, oldAvatarId, id);
            boolean isOwnSelf = (mAccount != null) && mAccount.isParentOf(from);

            List<Info> infos = event.getInfo();
            if (!infos.isEmpty()) {
                Info info = selectAvatar(infos);
                String newAvatarId = info.getId();

                if (isOwnSelf) {
                    fireListeners(from, null, newAvatarId);
                }
                if (mAutoDownload) {
                    if (downloadAvatar(from, newAvatarId, info)) {
                        fireListeners(from, oldAvatarId, newAvatarId);
                    }
                }
            }
            else if (getAvatarHashByJid(from) != null) {
                purgeAvatarImageByJid(from);
                fireListeners(from, oldAvatarId, null);
            }
            else if (isOwnSelf) {
                fireListeners(from, oldAvatarId, null);
            }
        }
    }
}
