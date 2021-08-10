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

package org.jivesoftware.smackx.avatar.vcardavatar;

import android.text.TextUtils;

import org.jivesoftware.smack.*;
import org.jivesoftware.smack.SmackException.NoResponseException;
import org.jivesoftware.smack.SmackException.NotConnectedException;
import org.jivesoftware.smack.XMPPException.XMPPErrorException;
import org.jivesoftware.smack.filter.*;
import org.jivesoftware.smack.packet.Presence;
import org.jivesoftware.smack.packet.Stanza;
import org.jivesoftware.smackx.avatar.AvatarManager;
import org.jivesoftware.smackx.avatar.vcardavatar.listener.VCardAvatarListener;
import org.jivesoftware.smackx.avatar.vcardavatar.packet.VCardTempXUpdate;
import org.jivesoftware.smackx.muc.MultiUserChatManager;
import org.jivesoftware.smackx.vcardtemp.VCardManager;
import org.jivesoftware.smackx.vcardtemp.packet.VCard;
import org.jxmpp.jid.*;

import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * This class deals with the avatar data in XEP-0054: vcard-temp
 * The class provides a persistent storage in memoryCache and filesystem if specified.
 * Basing on XEP-0153 vCard-Based Avatars protocol, this class can be configured to auto
 * retrieve the avatar from server and store in the persistent storage for later retrieval.
 *
 * @author Eng Chong Meng
 */
public class VCardAvatarManager extends AvatarManager
{
    /**
     * The logger.
     */
    private static final Logger LOGGER = Logger.getLogger(VCardAvatarManager.class.getName());

    /**
     * This presence x-extension element name.
     */
    private static final String ELEMENT = VCardTempXUpdate.ELEMENT;

    /**
     * This presence x-extension namespace.
     */
    private static final String NAMESPACE = VCardTempXUpdate.NAMESPACE;

    private static Map<XMPPConnection, VCardAvatarManager> instances = new WeakHashMap<>();

    /**
     * Listeners to be informed if there is a new avatar updated.
     */
    private final List<VCardAvatarListener> mListeners = new LinkedList<>();

    /**
     * The VCard info for this user.
     */
    private VCard mVCard = null;

    /**
     * The VCardManager associated with the VCardAvatarManager.
     */
    private final VCardManager vCardMgr;

    private final MultiUserChatManager mucManager;

    /**
     * Creates a filter to only listen to presence stanza with the element name "x" and the
     * namespace "vcard-temp:x:update".
     */
    private static final StanzaFilter PRESENCES_WITH_VCARD
            = new AndFilter(new StanzaTypeFilter(Presence.class), new StanzaExtensionFilter(ELEMENT, NAMESPACE));

//    /**
//     * Creates a filter to only listen to presence stanza with the element name "x" but without the
//     * namespace "vcard-temp:x:update".
//     * cmeng (20190298) - ejabberd will auto add - so remove
//     */
//    private static final StanzaFilter PRESENCES_WITHOUT_VCARD
//            = new AndFilter(PresenceTypeFilter.AVAILABLE, new NotFilter(new StanzaExtensionFilter(ELEMENT, NAMESPACE)));

    static {
        XMPPConnectionRegistry.addConnectionCreationListener(VCardAvatarManager::getInstanceFor);
    }

    public static synchronized VCardAvatarManager getInstanceFor(XMPPConnection connection)
    {
        VCardAvatarManager vCardAvatarManager = instances.get(connection);
        if (vCardAvatarManager == null) {
            vCardAvatarManager = new VCardAvatarManager(connection);
        }
        return vCardAvatarManager;
    }

    /**
     * Create an UserAvatarManager.
     *
     * @param connection the registered account XMPP connection
     */
    private VCardAvatarManager(XMPPConnection connection)
    {
        super(connection);
        mucManager = MultiUserChatManager.getInstanceFor(connection);
        vCardMgr = VCardManager.getInstanceFor(connection);
        vCardTempXUpdate = new VCardTempXUpdate(null);
        instances.put(connection, this);

        connection.addConnectionListener(new ConnectionListener()
        {
            /**
             * Upon user authentication, update account avatarHash so it is ready for
             * x-extension inclusion in <presence/> sending.
             * @see XMPPConnection#addStanzaInterceptor(StanzaListener, StanzaFilter)
             * @see VCardAvatarManager#processContactPhotoPresence(Stanza)
             *
             * Application can also update the avatarHash anytime using
             * @see VCardTempXUpdate#setAvatarHash(String)
             */
            @Override
            public void authenticated(XMPPConnection connection, boolean resumed)
            {
                // if (!isUserAvatarEnable && (persistentJidToHashIndex != null)) {
                if (persistentJidToHashIndex != null) {
                    String hash = getAvatarHashByJid(mAccount);
                    if (hash != null)
                        vCardTempXUpdate.setAvatarHash(hash);
                }
            }
        });

        /*
         * The Presence stanza interceptor with type=available to insert VCardTempXUpdate element
         * cmeng (20190298) - ejabberd will auto add - so remove
         */
        // connection.addStanzaInterceptor(this, PRESENCES_WITHOUT_VCARD);

        /*
         * Listen for remote presence stanzas with the vCardTemp:x:update extension. If we
         * receive such a stanza, process the stanza and acts if necessary
         */
        connection.addAsyncStanzaListener(this::processContactPhotoPresence, PRESENCES_WITH_VCARD);
    }

    /**
     * Download VCard for the given userId, and update avatar information if available.
     * A null userId indicates that the download is for the connection registered account.
     * <p>
     * Do not cache avatarId in persistent store if VCard does not have <PHOTO/>#photoBinval item.
     * We may never get the updated VCard info if contact client does not support XEP-0153 protocol
     *
     * @param userId The bareJid of the user. Load VCard of the current user if null.
     * @return VCard if the download was successful otherwise null is returned
     */
    public VCard downloadVCard(BareJid userId)
    {
        if (userId == null)
            userId = mAccount;

        try {
            mVCard = vCardMgr.loadVCard(userId.asEntityBareJidIfPossible());
        } catch (SmackException.NoResponseException | XMPPException.XMPPErrorException
                | SmackException.NotConnectedException | InterruptedException e) {
            LOGGER.log(Level.WARNING, "Error while downloading VCard for: '" + userId + "'. " + e.getMessage());
        }

        if (mVCard != null) {
            String currentAvatarHash = getAvatarHashByJid(userId);

            String avatarHash = "";  // default to no photo specified
            byte[] avatarImage = mVCard.getAvatar();
            // Proceed only if vCard has photo specified. XEP-0084 will handle later on
            if ((avatarImage != null) && (avatarImage.length > 0)) {
                // save if new image to persistent cache and get its avatarHash
                avatarHash = addAvatarImage(avatarImage);
                if (!avatarHash.equals(currentAvatarHash)) {
                    /*
                     * set <presence/> avatarHash if the userId is the registered account for this
                     * connection @see VCardTempXUpdate#mAvatarHash
                     */
                    // if (!isUserAvatarEnable && (mAccount != null) && mAccount.equals(userId)) {
                    if ((mAccount != null) && mAccount.equals(userId)) {
                        vCardTempXUpdate.setAvatarHash(avatarHash);
                    }

                    // add an index hash for the jid
                    addJidToAvatarHashIndex(userId, avatarHash);

                    /*
                     * Purge old avatar item from the persistent storage if:
                     * - Current avatar hash is not empty i.e. "" => a newly received avatar
                     * - the currentAvatarHash is single user owner
                     * - skip if currentAvatarHash.equals(avatarId)
                     *	   => cache and persistent not sync (pre-checked)
                     */
                    if (!TextUtils.isEmpty(currentAvatarHash)
                            && !isHashMultipleOwner(userId, currentAvatarHash))
                        persistentAvatarCache.purgeItemFor(currentAvatarHash);
                }
            }
            LOGGER.log(Level.INFO, "Downloaded vcard info for: " + userId + "; Hash = " + avatarHash);
        }
        return mVCard;
    }

    /**
     * Save this vCard for the user connected by 'connection'. XMPPConnection should be
     * authenticated and not anonymous.
     *
     * @throws XMPPErrorException thrown if there was an issue setting the VCard in the server.
     * @throws NoResponseException if there was no response from the server.
     * @throws NotConnectedException if there was no connection to the server.
     */
    public boolean saveVCard(VCard vCard)
            throws NoResponseException, XMPPErrorException, NotConnectedException, InterruptedException
    {
        boolean isImageUpdated = false;
        if (vCard != null) {
            isImageUpdated = updateVCardAvatarHash(vCard.getAvatar(), true);
            vCardMgr.saveVCard(vCard);
        }
        return isImageUpdated;
    }

    /* ===================================================================================== */

    /**
     * Parses a contact presence stanza with the element name "x" and the namespace
     * "vcard-temp:x:update". If new avatarHash received, Download if autoDownload is
     * enabled and/or fireListeners registered with this event if change detected.
     *
     * Note: to attributes is missing from <presence/> send from old jabber server
     *
     * @param stanza The stanza received to parse.
     */
    private void processContactPhotoPresence(Stanza stanza)
    {
        // Do not process if received own <presence/> from server or from system service entity
        Jid jidFrom = stanza.getFrom();
        // if (!jidFrom.isEntityFullJid() || (mAccount != null && mAccount.isParentOf(jidFrom))) {
        if (!jidFrom.isEntityFullJid() || jidFrom.equals(stanza.getTo())) {
            return;
        }

        // Do not process <presence/>s send by chatRoom participants
        Set<EntityBareJid> chatRooms = mucManager.getJoinedRooms();
        if (chatRooms.contains(jidFrom.asEntityBareJidIfPossible())) {
            // LOGGER.log(Level.INFO, "Skip process presence from chatRoom participant: " + jidFrom);
            return;
        }

        // Retrieves the user current avatarHash
        String currentAvatarHash = getAvatarHashByJid(jidFrom.asBareJid());

        // Get the stanza extension which contains the photo tag.
        VCardTempXUpdate vCardXExtension = stanza.getExtension(VCardTempXUpdate.class);
        if (vCardXExtension != null) {
            /*
             * Retrieved avatarHash info may contains [null | "" | "{avatarHash}"
             * null => no <photo/>
             * "" => <photo/>
             * {avatarHash} => <photo>avatarHash</photo>
             */
            String avatarHash = vCardXExtension.getAvatarHash();

            /* acts if only new avatarHash is received. null => client not ready so no action*/
            if ((avatarHash != null) && isAvatarNew(jidFrom.asBareJid(), avatarHash)) {
                /*
                 * If autoDownload is enabled, download VCard and it will also update all
                 * relevant avatar information if download is successful
                 */
                if (mAutoDownload) {
                    mVCard = downloadVCard(jidFrom.asBareJid());

                    if ((mVCard != null) && (mVCard.getAvatar() != null)) {
                        LOGGER.log(Level.INFO, "Presence with new avatarHash received (old => new) from: "
                                + jidFrom + "\n" + currentAvatarHash + "\n" + avatarHash);
                        fireListeners(jidFrom, avatarHash);
                    }
                    else {
                        LOGGER.warning("vCard contains no avatar information!");
                    }
                }
                else {
                    // Invalid mVcard on new avatarHash received
                    mVCard = null;
                }
            }
        }
    }

    /* ===================================================================================== */

//    /**
//     * Intercepts sent presence packets in order to add VCardTempXUpdate extension.
//     * cmeng (20190298) - ejabberd will auto add - so remove
//     *
//     * @param stanza The sent presence packet.
//     */
//    @Override
//    public void processStanza(Stanza stanza)
//    {
//        // Do not add an elementExtension without hash value
//        if (vCardTempXUpdate.getAvatarHash() != null) {
//            LOGGER.warning("Stanza EE before add: " + stanza.toXML(XmlEnvironment.EMPTY) + "\n" + stanza.getExtensions()
//                    + "\n" + vCardTempXUpdate.toXML(XmlEnvironment.EMPTY));
//            stanza.addExtension(vCardTempXUpdate);
//            LOGGER.warning("New Stanza EE after add: " + stanza.toXML(XmlEnvironment.EMPTY) + "\n"  + stanza.getExtensions());
//        }
//    }

    /* ===================================================================================== */

    /**
     * Fire the listeners if there is a change in the avatarHash, and the auto downloaded VCard
     * info if any; otherwise null vCard is sent to the listeners
     *
     * @param from the full jid of the contact sending the <presence/> stanza
     * @param avatarId the new avatar id can be "" or {avatarHash}
     */
    private void fireListeners(Jid from, String avatarId)
    {
        for (VCardAvatarListener l : mListeners)
            l.onAvatarChange(from, avatarId, mVCard);
    }

    /**
     * Add an VCardAvatarListener that will be informed if there is change in the avatarHash.
     *
     * @param listener the VCardAvatarListener to add
     */
    public void addVCardAvatarChangeListener(VCardAvatarListener listener)
    {
        if (!mListeners.contains(listener))
            mListeners.add(listener);
    }

    /**
     * Remove an VCardAvatarListener.
     *
     * @param listener the VCardAvatarListener to remove
     */
    public void removeVCardAvatarChangeListener(VCardAvatarListener listener)
    {
        mListeners.remove(listener);
    }
}
