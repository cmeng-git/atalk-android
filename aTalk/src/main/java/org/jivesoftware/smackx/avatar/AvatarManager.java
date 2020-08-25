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

package org.jivesoftware.smackx.avatar;

import android.content.Context;
import android.text.TextUtils;
import android.util.LruCache;

import org.atalk.android.aTalkApp;
import org.jivesoftware.smack.*;
import org.jivesoftware.smack.roster.Roster;
import org.jivesoftware.smack.roster.RosterEntry;
import org.jivesoftware.smack.util.StringUtils;
import org.jivesoftware.smackx.avatar.cache.*;
import org.jivesoftware.smackx.avatar.vcardavatar.packet.VCardTempXUpdate;
import org.jxmpp.jid.BareJid;
import org.jxmpp.jid.impl.JidCreate;
import org.jxmpp.stringprep.XmppStringprepException;

import java.io.File;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

import timber.log.Timber;

/**
 * An AvatarManager for aTalk. Base class for both the
 * XEP-0153: vCard-Based Avatar protocol Implementation
 * XEP-0084: User Avatar protocol Implementation
 */
public class AvatarManager extends Manager
{
    /**
     * The logger.
     */
    private static final Logger LOGGER = Logger.getLogger(AvatarManager.class.getName());

    protected static final int JPEG_QUALITY = 100;

    /**
     * Map of avatarHash" to Avatar byte data
     */
    private static final AvatarCacheMemory cacheAvatar = new AvatarCacheMemory(1000, -1);

    /**
     * Use for the persistent avatar storage in additional to cacheAvatar
     */
    protected static AvatarCacheFile persistentAvatarCache = null;

    /**
     * Map of bareJid to avatarId (Hash of avatar). Definition of avatarId:
     * 1. null ==> not allow (no defined)
     * 2. "" ==> user has no photo specified (not use with XEP-0084 enabled)
     * 3. {avatar Hash} ==> photo in cache and/or persistent storage
     * <p>
     * Note: Server stores only one copy of VCard Info (avatar) for each jid irrespective of its
     * resources.
     */
    private static final LruCache<BareJid, String> cacheJidToAvatarId = new LruCache<>(1000);

    private static final Map<XMPPConnection, AvatarManager> instances = new WeakHashMap<>();

    /**
     * Use for the persistent JidToHash Index storage in additional to cacheJidToAvatarId
     */
    protected static JidToHashCacheFile persistentJidToHashIndex = null;

    /**
     * The VCardTempXUpdate Extension class
     * - use by VCardAvatar only to send avatarHash in presence stanza
     */
    protected VCardTempXUpdate vCardTempXUpdate = null;

    /**
     * If <tt>true</tt>, then proceed to download VCard when a new photoHash is received from
     * <presence/>. Default to false as client application may want to extract VCard info other
     * than the photo image
     */
    protected static boolean mAutoDownload = true;

    /**
     * The registered account (bareJid) for this connection (must not be static).
     */
    protected BareJid mAccount;

    /**
     * Flag indicates if XEP-0084 User Avatar is enabled. When true, vCardTempXUpdate should not
     * include avatar hash in <photo/> element
     */
    protected static boolean isUserAvatarEnable = true;

    protected XMPPConnection mConnection;
    protected Context mContext;

    public static synchronized AvatarManager getInstanceFor(XMPPConnection connection)
    {
        AvatarManager avatarManager = instances.get(connection);
        if (avatarManager == null) {
            avatarManager = new AvatarManager(connection);
        }
        return avatarManager;
    }

    /**
     * Create an AvatarManager.
     *
     * @param connection the registered account XMPP connection
     */
    protected AvatarManager(XMPPConnection connection)
    {
        super(connection);
        mContext = aTalkApp.getGlobalContext();
        mConnection = connection;
        instances.put(connection, this);

        connection.addConnectionListener(new ConnectionListener()
        {
            @Override
            public void connected(XMPPConnection connection)
            {
                mConnection = connection;
            }

            /**
             * Upon user authentication, get the account for the connection.
             */
            @Override
            public void authenticated(XMPPConnection connection, boolean resumed)
            {
                mAccount = connection.getUser().asBareJid();
            }
        });
    }

    /**
     * Set to false (DEFAULT) if application needs to extract all VCard info other than
     * the photo image
     *
     * @param autoDownload true to enable auto download of avatars (photo image)
     */
    public static void setAutoDownload(boolean autoDownload)
    {
        mAutoDownload = autoDownload;
    }

    /**
     * Set to <tt>false</tt> if XEP-0153 VCard Avatar is used for avatar update.
     * Then <presence/> will include VCardTempXUpdate x-extension with avatarId in <photo/>
     *
     * @param userAvatarMode true (default) to enable XEP-0054 User Avatar Mode
     */
    public static void setUserAvatar(boolean userAvatarMode)
    {
        isUserAvatarEnable = userAvatarMode;
    }

    /**
     * Set the persistent cache implementation
     *
     * @param storeDir the file directory which will store the avatars
     */
    public static void setPersistentCache(File storeDir)
    {
        if (storeDir != null) {
            persistentJidToHashIndex = new JidToHashCacheFile(storeDir);
            persistentAvatarCache = new AvatarCacheFile(storeDir);
        }
    }

    /**
     * If avatar is new, add it to the cache; and persistent storage database if enabled.
     *
     * @param avatarId The avatarId, i.e SHA-1 Hash of the avatarImage.
     * @param avatarImage Byte[] value of avatar image data.
     */
    protected static boolean addAvatarImageByAvatarId(String avatarId, byte[] avatarImage)
    {
        if (!TextUtils.isEmpty(avatarId) && isAvatarNew(null, avatarId)) {
            cacheAvatar.addAvatarByHash(avatarId, avatarImage);
            if (persistentAvatarCache != null)
                persistentAvatarCache.addAvatarByHash(avatarId, avatarImage);
            return true;
        }
        return false;
    }

    /**
     * Calculate the avatarId and save its image to cache
     *
     * @param avatarImage Byte[] value of avatar image data.
     * @return <tt>avatarId</tt> i.e SHA-1 Hash of the avatarImage. Return "" when avatar is empty
     */
    protected static String addAvatarImage(byte[] avatarImage)
    {
        if (avatarImage.length == 0)
            return "";

        String avatarId = getAvatarHash(avatarImage);
        if (!TextUtils.isEmpty(avatarId)) {
            addAvatarImageByAvatarId(avatarId, avatarImage);
        }
        return avatarId;
    }

    /**
     * Get an avatar image from the cache with the given avatarId
     *
     * @param avatarId the id of the avatar (Hash)
     * @return the avatar or null if it cannot be retrieved from the cache or file
     */
    public static byte[] getAvatarImageByHash(String avatarId)
    {
        if (avatarId == null)
            return null;

        byte[] avatarImage = new byte[0];  // default to no photo
        if (avatarId.length() != 0) {
            avatarImage = cacheAvatar.getAvatarForHash(avatarId);
            if ((avatarImage == null) && (persistentAvatarCache != null)) {
                avatarImage = persistentAvatarCache.getAvatarForHash(avatarId);
                // sync cacheAvatar with persistentAvatarCache for future access
                if (avatarImage != null) {
                    cacheAvatar.addAvatarByHash(avatarId, avatarImage);
                }
            }
        }
        return avatarImage;
    }

    /**
     * Get the Avatar for a jid. Returns the avatar or null if
     * AvatarManager does not have any information.
     *
     * @param jid the user (BareJid)
     * @return the byte[] avatarImage (can be zero byte) with found avatarId, otherwise null
     */
    public static byte[] getAvatarImageByJid(BareJid jid)
    {
        String avatarId = getAvatarHashByJid(jid);
        LOGGER.log(Level.FINE, "Fetching avatar from local storage for: (" + jid + ") => " + avatarId);

        return (avatarId == null) ? null : getAvatarImageByHash(avatarId);
    }


    /**
     * Calculate the avatarId and save its image to cache on condition is forced or none is found.
     * The method is created for aTalk external access
     *
     * @param userId the bareJid of the avatarImage
     * @param avatarImage Byte[] value of avatar image data.
     * @param force override existing even if it exists
     * @return return true is success
     */
    public static boolean addAvatarImage(BareJid userId, byte[] avatarImage, boolean force)
    {
        if ((userId == null) || (avatarImage.length == 0))
            return false;

        String avatarId = getAvatarHash(avatarImage);
        if (force || getAvatarImageByHash(avatarId).length == 0) {
            if (!TextUtils.isEmpty(avatarId)) {
                addAvatarImageByAvatarId(avatarId, avatarImage);
                addJidToAvatarHashIndex(userId, avatarId);
                return true;
            }
        }
        return false;
    }

    /**
     * Get the avatarHash for jid
     *
     * @param jid the bareJid
     * @return the avatarHash for the jid or null
     */
    public static String getAvatarHashByJid(BareJid jid)
    {
        String hash = cacheJidToAvatarId.get(jid);
        if ((hash == null) && (persistentJidToHashIndex != null)) {
            hash = persistentJidToHashIndex.getHashForJid(jid);
            if (hash != null) {
                cacheJidToAvatarId.put(jid, hash);
            }
        }
        return hash;
    }

    /**
     * add a new JidToAvatar Index to the cache
     *
     * @param userId the bareJid
     * @param avatarHash the avatar Hash cannot be empty
     */
    protected static void addJidToAvatarHashIndex(BareJid userId, String avatarHash)
    {
        // Create an index hash for the jid
        if (!TextUtils.isEmpty(avatarHash)) {
            cacheJidToAvatarId.put(userId, avatarHash);
            if (persistentJidToHashIndex != null) {
                persistentJidToHashIndex.addHashByJid(userId, avatarHash);
            }
        }
    }

    /**
     * Purge all Avatar related info for the given jid.
     * Must purge both the persistentJidToHashIndex link and the actual image file persistentAvatarCache
     *
     * @param jid the user (BareJid)
     */
    public static void purgeAvatarImageByJid(BareJid jid)
    {
        String avatarId = getAvatarHashByJid(jid);
        LOGGER.log(Level.INFO, "Purge avatar from store for: (" + jid + ") => " + avatarId);

        if (!TextUtils.isEmpty(avatarId)) {
            if (persistentAvatarCache != null) {
                persistentAvatarCache.purgeItemFor(avatarId);
            }
            cacheAvatar.purgeItemFor(avatarId);
        }

        if (persistentJidToHashIndex != null) {
            persistentJidToHashIndex.purgeItemFor(jid);
        }
        cacheJidToAvatarId.remove(jid);
    }

    /**
     * Check if we have the user avatar in store, and the Jid2Hash link is not broken
     * i.e. must be in persistentAvatarCache if enabled; otherwise search in cacheAvatar
     *
     * @param avatarId the id of the avatar (Hash)
     * @return <tt>true</tt> if avatarId is avatarId is new. <tt>false</tt> otherwise
     */
    protected static boolean isAvatarNew(BareJid jid, String avatarId)
    {
        boolean isFound;
        // If jid is given, then check for Jid2Hash is not broken
        if ((jid != null) && getAvatarHashByJid(jid) == null)
            return true;

        if (persistentAvatarCache != null) {
            isFound = persistentAvatarCache.contains(avatarId);
        }
        else
            isFound = cacheAvatar.contains(avatarId);

        return !isFound;
    }

    /**
     * Check if the given avatarHash is owned by multiple user.
     *
     * @param userId the known owner of the avatarHash
     * @param avatarHash the id of the avatar (Hash)
     * @return <tt>true</tt> if avatarHash is owned by more than one user. <tt>false</tt> otherwise
     */
    protected boolean isHashMultipleOwner(BareJid userId, String avatarHash)
    {
        Roster roster = Roster.getInstanceFor(mConnection);
        Set<RosterEntry> rosterEntries = roster.getEntries();
        for (RosterEntry rosterEntry : rosterEntries) {
            BareJid contactJid = rosterEntry.getJid();
            if (!contactJid.equals(userId.toString())) {
                String imageHash = getAvatarHashByJid(contactJid);
                if (!TextUtils.isEmpty(imageHash) && imageHash.equals(avatarHash)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Save a copy of the account Roster's contact names in persistent storage delimited by ",".
     * Later used by method clearPersistentStorage() when account is removed even when offline.
     * <p>
     * rosterFileName is mimic as BareJid so as to use common persistent storage
     *
     * @param account the roster information belong to this account
     * @see #clearPersistentStorage(BareJid)
     */
    public void saveAccountRoster(BareJid account)
            throws XmppStringprepException
    {
        if ((account != null) && (persistentJidToHashIndex != null)) {
            BareJid rosterFileName = JidCreate.bareFrom(account + "_roster");

            StringBuilder sb = new StringBuilder();
            Roster roster = Roster.getInstanceFor(mConnection);
            Set<RosterEntry> rosterEntries = roster.getEntries();

            if (!rosterEntries.isEmpty()) {
                for (RosterEntry rosterEntry : rosterEntries) {
                    String contact = rosterEntry.getJid() + ",";
                    sb.append(contact);
                }
                sb.deleteCharAt(sb.length() - 1);
                persistentJidToHashIndex.addHashByJid(rosterFileName, sb.toString());
            }
        }
    }

    /**
     * Clear the persistent storage for the given account even when is offline:
     * - User has unregistered the account
     * - Allow user to only clean avatar info pertaining to the specific account
     * - User roster group change
     * Warning! may delete multipleOwner's avatar info with current implementation
     *
     * @param account all the avatar information belong to this account are to be purged
     */
    public static void clearPersistentStorage(BareJid account)
            throws XmppStringprepException
    {
        if ((account != null) && (persistentJidToHashIndex != null)) {
            BareJid rosterFileName = JidCreate.bareFrom(account + "_roster");
            String rosterContacts = persistentJidToHashIndex.getHashForJid(rosterFileName);

            // Remove avatar info for all contacts of this account
            if (!StringUtils.isNullOrEmpty(rosterContacts)) {
                String[] contacts = rosterContacts.split(",");
                for (String contact : contacts) {
                    BareJid contactJid = JidCreate.bareFrom(contact);
                    String imageHash = getAvatarHashByJid(contactJid);

                    // May purge multipleOwner's avatar info - leave them as it???
                    if (!TextUtils.isEmpty(imageHash)){ // && isHashMultipleOwner(contactJid, imageHash))
                        persistentJidToHashIndex.purgeItemFor(contactJid);
                        if (persistentAvatarCache != null)
                            persistentAvatarCache.purgeItemFor(imageHash);

                        cacheAvatar.purgeItemFor(imageHash);
                        cacheJidToAvatarId.remove(contactJid);
                    }
                }

                // Remove the account roster info file
                persistentJidToHashIndex.purgeItemFor(rosterFileName);

                // Finally remove account own avatar info
                String imageHash = getAvatarHashByJid(account);
                if (!TextUtils.isEmpty(imageHash))
                    persistentAvatarCache.purgeItemFor(imageHash);
                persistentJidToHashIndex.purgeItemFor(account);

                cacheAvatar.purgeItemFor(imageHash);
                cacheJidToAvatarId.remove(account);
            }
        }
    }

    /**
     * Clear all the VCard persistent storage for the device when:
     * - to cleanup and refresh VCard fetching
     * - Persistent storage option has been disabled by user
     */
    public static void clearPersistentStorage()
    {
        if (persistentJidToHashIndex != null)
            persistentJidToHashIndex.emptyCache();
        if (persistentAvatarCache != null)
            persistentAvatarCache.emptyCache();
    }

    /**
     * Gets the String representation in hexadecimal of the SHA-1 hash of the image given in
     * parameter.
     *
     * @param imageData The image to getHashForJid the hexadecimal representation of the SHA-1 hash.
     * @return The SHA-1 hash hexadecimal representation of the image. Null if the image is null or
     * if the SHA1 is not recognized as a valid algorithm.
     */
    protected static String getAvatarHash(byte[] imageData)
    {
        byte[] imageHash = null;
        String avatarHash = null;

        try {
            if (imageData != null) {
                MessageDigest messageDigest = MessageDigest.getInstance("SHA1");
                imageHash = messageDigest.digest(imageData);
            }
        } catch (NoSuchAlgorithmException ex) {
            Timber.e("Failed to getHashForJid message digest: %s", ex.getMessage());
        }

        if (imageHash != null)
            avatarHash = StringUtils.encodeHex(imageHash);

        return avatarHash;
    }

    /**
     * Updates the avatar image manged by this presence extension. Should only be called from
     * registered account of this connection.
     *
     * @param imageBytes The avatar image and must not be null
     * @param updateVcardTemp <tt>true</tt> to setAvatarHash() if new
     * @return "false" if the new avatar image is the same as the current one. "true" if this
     * presence extension has been updated with the new avatar image.
     */
    public boolean updateVCardAvatarHash(byte[] imageBytes, boolean updateVcardTemp)
    {
        if (imageBytes == null)
            return false;

        boolean isImageUpdated = false;
        if (imageBytes.length != 0) {
            // Computes the SHA-1 hash in hexadecimal representation.
            String avatarHash = getAvatarHash(imageBytes);

            // if (!isUserAvatarEnable && updateVcardTemp && vCardTempXUpdate != null)
            if (updateVcardTemp && vCardTempXUpdate != null)
                isImageUpdated = vCardTempXUpdate.setAvatarHash(avatarHash);

            if (isImageUpdated || isAvatarNew(null, avatarHash)) {
                // If the image has changed, then update all caches info and cache image content
                addAvatarImageByAvatarId(avatarHash, imageBytes);

                // Note: user does not have photo if avatar == ""
                addJidToAvatarHashIndex(mAccount, avatarHash);

                LOGGER.log(Level.INFO, "Avatar Hash updated for : " + mAccount + "; Hash = " + avatarHash);
                isImageUpdated = true;
            }
        }
        return isImageUpdated;
    }
}
