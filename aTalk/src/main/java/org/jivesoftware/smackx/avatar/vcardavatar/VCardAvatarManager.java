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

import java.util.Map;
import java.util.WeakHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.jivesoftware.smack.ConnectionListener;
import org.jivesoftware.smack.SmackException;
import org.jivesoftware.smack.SmackException.NoResponseException;
import org.jivesoftware.smack.SmackException.NotConnectedException;
import org.jivesoftware.smack.StanzaListener;
import org.jivesoftware.smack.util.StringUtils;
import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.XMPPConnectionRegistry;
import org.jivesoftware.smack.XMPPException;
import org.jivesoftware.smack.XMPPException.XMPPErrorException;
import org.jivesoftware.smack.filter.AndFilter;
import org.jivesoftware.smack.filter.StanzaExtensionFilter;
import org.jivesoftware.smack.filter.StanzaFilter;
import org.jivesoftware.smack.filter.StanzaTypeFilter;
import org.jivesoftware.smack.packet.Presence;
import org.jivesoftware.smack.packet.Stanza;
import org.jivesoftware.smackx.avatar.AvatarManager;
import org.jivesoftware.smackx.avatar.vcardavatar.packet.VCardTempXUpdate;
import org.jivesoftware.smackx.muc.packet.MUCUser;
import org.jivesoftware.smackx.vcardtemp.VCardManager;
import org.jivesoftware.smackx.vcardtemp.packet.VCard;
import org.jxmpp.jid.EntityBareJid;
import org.jxmpp.jid.Jid;

import timber.log.Timber;

/**
 * This class deals with the avatar data in XEP-0054: vcard-temp
 * The class provides a persistent storage in memoryCache and filesystem if specified.
 * Basing on XEP-0153 vCard-Based Avatars protocol, this class can be configured to auto
 * retrieve the avatar from server and store in the persistent storage for later retrieval.
 *
 * @author Eng Chong Meng
 */
public class VCardAvatarManager extends AvatarManager {
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

    private static final Map<XMPPConnection, VCardAvatarManager> instances = new WeakHashMap<>();

    /**
     * The VCardManager associated with the VCardAvatarManager.
     */
    private final VCardManager vCardMgr;

    // Currently in process of the contact#avatarId
    private String ipContactHash = null;

    /**
     * Creates a filter to only listen to presence stanza with the element name "x" and the
     * namespace "vcard-temp:x:update".
     */
    private static final StanzaFilter PRESENCES_WITH_VCARD_TEMP
            = new AndFilter(new StanzaTypeFilter(Presence.class), new StanzaExtensionFilter(ELEMENT, NAMESPACE));

    /*
     * Creates a filter to only listen to presence stanza with the element name "x" but without the
     * namespace "vcard-temp:x:update".
     * cmeng (20190298) - ejabberd will auto add with vcard_temp_update
     */
    // private static final StanzaFilter PRESENCES_WITHOUT_VCARD_TEMP
    //        = new AndFilter(PresenceTypeFilter.AVAILABLE, new NotFilter(new StanzaExtensionFilter(ELEMENT, NAMESPACE)));

    static {
        XMPPConnectionRegistry.addConnectionCreationListener(VCardAvatarManager::getInstanceFor);
    }

    public static synchronized VCardAvatarManager getInstanceFor(XMPPConnection connection) {
        VCardAvatarManager vCardAvatarManager = instances.get(connection);
        if (vCardAvatarManager == null) {
            vCardAvatarManager = new VCardAvatarManager(connection);
        }
        return vCardAvatarManager;
    }

    /**
     * Create an VCardAvatarManager.
     *
     * @param connection the registered account XMPP connection
     */
    private VCardAvatarManager(XMPPConnection connection) {
        super(connection);
        vCardMgr = VCardManager.getInstanceFor(connection);
        vCardTempXUpdate = new VCardTempXUpdate(null);
        instances.put(connection, this);

        connection.addConnectionListener(new ConnectionListener() {
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
            public void authenticated(XMPPConnection connection, boolean resumed) {
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
         * cmeng (20190298) - ejabberd will auto add with vcard_temp_update
         */
        // connection.addAsyncStanzaListener(this::processPresenceStanza, PRESENCES_WITHOUT_VCARD_TEMP);

        /*
         * Listen for remote presence stanzas with the vCardTemp:x:update extension. If we
         * receive such a stanza, process the stanza and acts if necessary
         */
        connection.addAsyncStanzaListener(this::processContactPhotoPresence, PRESENCES_WITH_VCARD_TEMP);
    }

    /**
     * Download VCard for the given userId, and update avatar information if available.
     * A null userId indicates that the download is for the connection registered account.
     * <p>
     * Do not cache avatarId in persistent store if VCard does not have <PHOTO/>#photoBinval item.
     * We may never get the updated VCard info if contact client does not support XEP-0153 protocol
     *
     * @param from The bareJid of the user. Load VCard of the current user if null.
     *
     * @return VCard if the download was successful otherwise null is returned
     */
    public VCard downloadVCard(EntityBareJid from) {
        if (from == null)
            from = mAccount;

        VCard vCard = null;
        try {
            vCard = vCardMgr.loadVCard(from);
        } catch (SmackException.NoResponseException | XMPPException.XMPPErrorException
                 | SmackException.NotConnectedException | InterruptedException e) {
            LOGGER.log(Level.WARNING, "Error while downloading VCard for: '" + from + "'. " + e.getMessage());
        }

        // Proceed only if vCard not null, and has photo specified. Fall back to XEP-0084: User Avatar to handle.
        if (vCard != null) {
            byte[] avatarImage = vCard.getAvatar();
            if ((avatarImage != null) && (avatarImage.length > 0)) {
                String oldAvatarId = getAvatarHashByJid(from);

                // save if new image to persistent cache and get its avatarHash
                String newAvatarId = addAvatarImage(avatarImage);
                if (newAvatarId != null && !newAvatarId.equals(oldAvatarId)) {
                    // add an index hash for the jid
                    addJidToAvatarHashIndex(from, newAvatarId);
                    /*
                     * Purge old avatar item from the persistent storage if:
                     * - Current avatar hash is not empty i.e. "" => a newly received avatar
                     * - the currentAvatarHash is single user owner
                     * - skip if currentAvatarHash.equals(avatarId)
                     *	   => cache and persistent not sync (pre-checked)
                     */
                    if (StringUtils.isNotEmpty(oldAvatarId)
                            && !isHashMultipleOwner(from, oldAvatarId))
                        persistentAvatarCache.purgeItemFor(oldAvatarId);

                    /*
                     * set <presence/> avatarHash if the userId is the registered account for this
                     * connection @see VCardTempXUpdate#mAvatarHash
                     */
                    // if (!isUserAvatarEnable && (mAccount != null) && mAccount.equals(userId)) {
                    if ((mAccount != null) && mAccount.isParentOf(from)) {
                        vCardTempXUpdate.setAvatarHash(newAvatarId);
                    }
                }
                LOGGER.log(Level.INFO, "Downloaded vcard info for: " + from + "; Hash = " + newAvatarId);
            }
        }
        return vCard;
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
            throws NoResponseException, XMPPErrorException, NotConnectedException, InterruptedException {
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
     * "vcard-temp:x:update". If new photo id received, download if autoDownload is
     * enabled, and fireListeners registered listener with this event.
     * Note: to attributes is missing from <presence/> send from old jabber server
     *
     * @param stanza The stanza received for parse.
     */
    private void processContactPhotoPresence(Stanza stanza) {
        // Do not process <presence/> sent by chatRoom participant
        Jid jidFrom = stanza.getFrom();
        if (stanza.getExtension(MUCUser.class) != null) {
            LOGGER.log(Level.INFO, "Skip process avatar for chatRoom participant: "
                    + stanza.getStanzaId() + " => " + jidFrom);
            return;
        }

        EntityBareJid from = jidFrom.asEntityBareJidIfPossible();
        // Retrieves the user current avatarHash
        String oldAvatarId = getAvatarHashByJid(from);

        /*
         * Get the stanza extension which contains the photo element.
         * https://xmpp.org/extensions/xep-0153.html
         * Retrieved avatarHash info may contains [null | "" | "{avatarHash}"
         * no <photo/> element => null; not ready
         * <photo/> element without text => ""; no photo
         * {avatarHash} => <photo>avatarHash</photo>
         */
        VCardTempXUpdate vCardTemp = stanza.getExtension(VCardTempXUpdate.class);
        String newAvatarId = vCardTemp.getAvatarHash(); // can be null
        // Timber.d("Received vcard-temp: %s %s '%s'", from, oldAvatarId, newAvatarId);

        /* acts if only avatarId is received. null => client not ready so no action */
        if (StringUtils.isNotEmpty(newAvatarId) && isAvatarNew(from, newAvatarId)) {
            /*
             * If autoDownload is enabled, download VCard and it will also update all
             * relevant avatar information if download is successful
             */
            if (mAutoDownload) {
                // Filtered all repeated request on user login (x4 requests on aTalk).
                String newKey = from.toString() + '#' + newAvatarId;
                if (newKey.equals(ipContactHash)) {
                    Timber.w("VCard download request repeated: %s", newKey);
                    return;
                }
                else {
                    ipContactHash = newKey;
                }

                VCard vCard = downloadVCard(from);
                if ((vCard != null) && (vCard.getAvatar() != null)) {
                    LOGGER.log(Level.INFO, "Presence with new avatarId received (old => new) from: "
                            + jidFrom + "\n" + oldAvatarId + "\n" + newAvatarId);
                    fireListeners(from, oldAvatarId, newAvatarId);
                }
                else {
                    LOGGER.warning("vCard contains no avatar information!");
                }
            }
        }

        // i.e. photo element without avatar id specified. null => not ready so not process
        else if (newAvatarId != null && newAvatarId.isEmpty() && getAvatarHashByJid(from) != null) {
            purgeAvatarImageByJid(from);
            fireListeners(from, oldAvatarId, null);
            // LOGGER.log(Level.WARNING, "Disable process vcard-temp for photo without hash; send by ejabberd server"
            //        + "on vcard update.\nThis causes aTalk user/contact to purge and display no avatar");

        }
    }

    /* ===================================================================================== */

    /*
     * Intercepts sent presence packets in order to add VCardTempXUpdate extension.
     * cmeng (20190298) - ejabberd will auto add with vcard_temp_update
     *
     * @param stanza The sent presence packet.
     */
//    private void processPresenceStanza(Stanza stanza)
//    {
//        // Do not add an elementExtension without hash value
//        if (vCardTempXUpdate.getAvatarHash() != null) {
//            LOGGER.warning("Stanza EE before add: " + stanza.toXML(XmlEnvironment.EMPTY) + "\n" + stanza.getExtensions()
//                    + "\n" + vCardTempXUpdate.toXML(XmlEnvironment.EMPTY));
//            stanza.addExtension(vCardTempXUpdate);
//            LOGGER.warning("New Stanza EE after add: " + stanza.toXML(XmlEnvironment.EMPTY) + "\n"  + stanza.getExtensions());
//        }
//    }
}
