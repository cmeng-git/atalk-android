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

import android.annotation.SuppressLint;
import android.graphics.Bitmap;
import android.text.TextUtils;

import org.jivesoftware.smack.*;
import org.jivesoftware.smack.packet.ExtensionElement;
import org.jivesoftware.smack.packet.Message;
import org.jivesoftware.smackx.avatar.AvatarManager;
import org.jivesoftware.smackx.avatar.useravatar.listener.UserAvatarListener;
import org.jivesoftware.smackx.avatar.useravatar.packet.AvatarData;
import org.jivesoftware.smackx.avatar.useravatar.packet.AvatarMetadata;
import org.jivesoftware.smackx.avatar.useravatar.packet.AvatarMetadata.Info;
import org.jivesoftware.smackx.pep.PepListener;
import org.jivesoftware.smackx.pep.PepManager;
import org.jivesoftware.smackx.pubsub.*;
import org.jivesoftware.smackx.pubsub.packet.PubSubNamespace;
import org.jxmpp.jid.EntityBareJid;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.*;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.logging.Level;
import java.util.logging.Logger;

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
public class UserAvatarManager extends AvatarManager
{
    /*
     * The logger.
     */
    private static final Logger LOGGER = Logger.getLogger(UserAvatarManager.class.getName());

    /*
     * The pubSub event name.
     */
    private static String PUBSUB_EVENT = EventElement.ELEMENT;

    /*
     * The constant String "http://jabber.org/protocol/pubsub#event".
     */
    public static final String PUBSUB_EVENT_NAMESPACE = PubSubNamespace.event.getXmlns();

    /*
     * The pubSub node for avatar metadata.
     */
    public static final String AVATAR_METADATA_NODE = AvatarMetadata.NAMESPACE;

    private static Map<XMPPConnection, UserAvatarManager> instances = new WeakHashMap<>();

    /*
     * Listeners to be informed if there is new avatar updated.
     */
    private final Set<UserAvatarListener> mListeners = new CopyOnWriteArraySet<>();

    /*
     * The PEPManager associated with tis connection.
     */
    private PepManager mPepManager;

    static {
        XMPPConnectionRegistry.addConnectionCreationListener(UserAvatarManager::getInstanceFor);
    }

    public static synchronized UserAvatarManager getInstanceFor(XMPPConnection connection)
    {
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
    private UserAvatarManager(XMPPConnection connection)
    {
        super(connection);
        /*
         * Add Listener to receive PEPEvent
         */
        mPepManager = PepManager.getInstanceFor(connection);
        mPepManager.addPepListener(new PepAvatarListener());
    }

    /* ===================================================================================== */

    /**
     * Publish user avatar - send the XMPP stanza to enable the publication of an avatar.
     * Before updating the avatar metadata node, the publisher MUST make sure that the avatar data
     * is available at the data node or URL
     *
     * @param bitmap the avatar to publish after conversation
     * @return true on success false otherwise
     */
    public boolean publishAvatar(Bitmap bitmap)
    {
        //TODO use the metadata available in the mediaStore
        AvatarMetadata meta = new AvatarMetadata();

        // The png format is mandatory for interoperability
        AvatarMetadata.Info png
                = publishBitmap(bitmap, Bitmap.CompressFormat.PNG, JPEG_QUALITY);
//		AvatarMetadata.Info jpeg
//				= publishBitmap(bitmap, Bitmap.CompressFormat.JPEG, JPEG_QUALITY);
        if (png != null) {
            meta.addInfo(png);
//			if (jpeg != null)
//				meta.addInfo(jpeg);
            return publishAvatarMetaData(png.getId(), meta);
        }
        return false;
    }

    /**
     * Send this bitmap to the avatar data node of the pep server.
     *
     * @param bmp the avatar bitmap
     * @param format the image format to publish this data
     * @param quality the compression quality use for JPEG compression
     * @return the resulting info associate with this bitmap.
     */
    private AvatarMetadata.Info publishBitmap(Bitmap bmp, Bitmap.CompressFormat format, int quality)
    {
        byte[] data = getBitmapByte(bmp, format, quality);
        String dataId = getAvatarHash(data);
        publishAvatarData(dataId, data);

        // save a copy of data in persistent storage and update its index
        addAvatarImageByAvatarId(dataId, data);
        addJidToAvatarHashIndex(mAccount, dataId);

        String mimeType = "image/png";
        if (Bitmap.CompressFormat.JPEG == format)
            mimeType = "image/jpeg";
        AvatarMetadata.Info info = new AvatarMetadata.Info(dataId, mimeType, data.length);
        info.setHeight(bmp.getHeight());
        info.setWidth(bmp.getWidth());
        return info;
    }

    /**
     * Convert the bitmap to a byte array.
     *
     * @param bitmap the avatar bitmap
     * @param format the resulting image format
     * @param quality the compression quality use for JPEG compression
     * @return the bitmap data or a array of 0 element on error
     */
    private byte[] getBitmapByte(Bitmap bitmap, Bitmap.CompressFormat format, int quality)
    {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        if (bitmap.compress(format, quality, bos))
            return bos.toByteArray();
        else
            return new byte[0];
    }

    /**
     * Disable the diffusion of your avatar.
     */
    public boolean disableAvatarPublishing()
    {
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
    private void publishAvatarData(String id, byte[] data)
    {
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
    private boolean publishAvatarMetaData(String id, AvatarMetadata metadata)
    {
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
     * @return <tt>true</tt> if publish is successful
     */
    private boolean nodePublish(Item item, String node)
    {
        LOGGER.warning("Use alternative aTalk nodePublish for avatar!");
        PubSubManager pubSubManager = PubSubManager.getInstanceFor(mConnection, mAccount);
        try {
            LeafNode pubSubNode = getLeafNode(pubSubManager, node);
            if (pubSubNode != null) {
                pubSubNode.publish(item);
                return true;
            }
        } catch (SmackException.NotConnectedException | InterruptedException e) {
            e.printStackTrace();
        } catch (XMPPException.XMPPErrorException e) {
            e.printStackTrace();
        } catch (SmackException.NoResponseException e) {
            e.printStackTrace();
        }
        return false;
    }

    /**
     * Get an instance of leafNode to publish avatar data
     *
     * @param pubSubManager the PubSubManager
     * @param nodeId the node to publish on.
     * @return an instance of leafNode
     */
    @SuppressLint("NewApi")
    private LeafNode getLeafNode(PubSubManager pubSubManager, String nodeId)
    {
        try {
            Constructor getLeafNode = LeafNode.class.getDeclaredConstructor(PubSubManager.class, String.class);
            getLeafNode.setAccessible(true);
            return (LeafNode) getLeafNode.newInstance(pubSubManager, nodeId);
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
     * @param avatarId the id of the avatar
     * @param info the metadata information of the avatar to download
     * @return true if the download was successful
     */
    public boolean downloadAvatar(EntityBareJid from, String avatarId, Info info)
    {
        /* acts if only new avatarId is received. null => client not ready so no action; also it is still connected */
        if ((avatarId != null) && isAvatarNew(from, avatarId) && (mConnection != null)) {
            try {
                String currentAvatarHash = getAvatarHashByJid(from);
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
                if (!TextUtils.isEmpty(currentAvatarHash) && !currentAvatarHash.equals(avatarId)
                        && !isHashMultipleOwner(from, currentAvatarHash))
                    persistentAvatarCache.purgeItemFor(currentAvatarHash);

                LOGGER.log(Level.INFO, "Avatar with new avatarHash received (old => new) "
                        + "from " + from + "\n" + currentAvatarHash + "\n" + avatarId);
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
     * @return the metadata of the avatar to download
     */
    public Info selectAvatar(List<Info> available)
    {
        return available.get(0);
    }

    private class PepAvatarListener implements PepListener
    {
        @Override
        public void eventReceived(EntityBareJid from, EventElement event, Message message)
        {
            if (event.getEventType() != EventElementType.items)
                return;

            ItemsExtension it = (ItemsExtension) event.getEvent();
            if (it.getItemsElementType() != ItemsExtension.ItemsElementType.items)
                return;

            String node = it.getNode();
            if (!AVATAR_METADATA_NODE.equals(node))
                return;

            List<Item> items = (List) it.getItems();
            Item i = items.get(0);
            if (i instanceof PayloadItem) {
                PayloadItem<ExtensionElement> pi = (PayloadItem) i;
                ExtensionElement ex = pi.getPayload();
                if (ex instanceof AvatarMetadata) {
                    AvatarMetadata ext = (AvatarMetadata) ex;
                    String avatarId = i.getId();

                    List<Info> infos = ext.getInfo();
                    if (infos.size() > 0 && mAutoDownload) {
                        Info info = selectAvatar(infos);
                        if (downloadAvatar(from, avatarId, info))
                            fireListeners(from, avatarId, infos);
                    }
                }
            }
        }
    }

    /**
     * Fire the listeners for avatar change.
     *
     * @param from the jid of the contact
     * @param avatarId the new avatar id
     * @param avatarInfo the metadata info of the avatar
     */
    private void fireListeners(EntityBareJid from, String avatarId, List<Info> avatarInfo)
    {
        for (UserAvatarListener l : mListeners)
            l.onAvatarChange(from, avatarId, avatarInfo);
    }

    /**
     * Add an VCardAvatarListener.
     *
     * @param listener the VCardAvatarListener to add
     */
    public void addAvatarListener(UserAvatarListener listener)
    {
        mListeners.add(listener);
    }

    /**
     * Remove an VCardAvatarListener.
     *
     * @param listener the VCardAvatarListener to remove
     */
    public void removeAvatarListener(UserAvatarListener listener)
    {
        mListeners.remove(listener);
    }
}
