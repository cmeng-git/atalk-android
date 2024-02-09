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
package net.java.sip.communicator.impl.protocol.jabber;

import net.java.sip.communicator.impl.protocol.jabber.caps.UserCapsNodeListener;

import org.atalk.android.aTalkApp;
import org.atalk.persistance.ServerPersistentStoresRefreshDialog;
import org.jivesoftware.smack.SmackException.NoResponseException;
import org.jivesoftware.smack.SmackException.NotConnectedException;
import org.jivesoftware.smack.StanzaListener;
import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.XMPPException.XMPPErrorException;
import org.jivesoftware.smack.filter.AndFilter;
import org.jivesoftware.smack.filter.StanzaExtensionFilter;
import org.jivesoftware.smack.filter.StanzaFilter;
import org.jivesoftware.smack.filter.StanzaTypeFilter;
import org.jivesoftware.smack.packet.Presence;
import org.jivesoftware.smack.packet.Stanza;
import org.jivesoftware.smackx.caps.EntityCapsManager;
import org.jivesoftware.smackx.caps.cache.SimpleDirectoryPersistentCache;
import org.jivesoftware.smackx.caps.packet.CapsExtension;
import org.jivesoftware.smackx.disco.ServiceDiscoveryManager;
import org.jivesoftware.smackx.disco.packet.DiscoverInfo;
import org.jxmpp.jid.Jid;

import java.io.File;
import java.util.LinkedList;
import java.util.List;

import timber.log.Timber;

/**
 * Helper to smack's default {@link ServiceDiscoveryManager} that adds support for
 * XEP-0030: Service Discovery.
 * XEP-0115: Entity Capabilities
 *
 * @author Eng Chong Meng
 */
public class ServiceDiscoveryHelper
{
    private static final StanzaFilter PRESENCES_WITH_CAPS = new AndFilter(StanzaTypeFilter.PRESENCE,
            new StanzaExtensionFilter(CapsExtension.ELEMENT, CapsExtension.NAMESPACE));

    /**
     * The {@link ServiceDiscoveryManager} supported in smack
     */
    private final ServiceDiscoveryManager mDiscoveryManager;

    /**
     * The parent provider
     */
    private final ProtocolProviderServiceJabberImpl mPPS;

    /**
     * The {@link XMPPConnection} that this manager is responsible for.
     */
    private final XMPPConnection mConnection;

    /**
     * The list of <code>UserCapsNodeListener</code>s interested in events notifying about
     * possible changes in the list of user caps nodes of this <code>EntityCapsManager</code>.
     */
    private static final List<UserCapsNodeListener> userCapsNodeListeners = new LinkedList<>();

    private static SimpleDirectoryPersistentCache entityCapsPersistentCache;

    /**
     * Creates a new <code>ScServiceDiscoveryManager</code> wrapping the default discovery manager of
     * the specified <code>connection</code>.
     *
     * @param parentProvider the parent provider that creates discovery manager.
     * @param connection Smack connection object that will be used by this instance to handle XMPPTCP connection.
     * @param featuresToRemove an array of <code>String</code>s representing the features to be removed from the
     * <code>ServiceDiscoveryManager</code> of the specified <code>connection</code> which is to be
     * wrapped by the new instance
     * @param featuresToAdd an array of <code>String</code>s representing the features to be added to the new instance
     * and to the <code>ServiceDiscoveryManager</code> of the specified <code>connection</code> which
     * is to be wrapped by the new instance
     */
    public ServiceDiscoveryHelper(ProtocolProviderServiceJabberImpl parentProvider, XMPPConnection connection,
            String[] featuresToRemove, String[] featuresToAdd) {
        mPPS = parentProvider;
        mConnection = connection;

        // Init EntityCapsManager so it is ready to receive Entity Caps Info
        EntityCapsManager entityCapsManager = EntityCapsManager.getInstanceFor(connection);

        // enableEntityCaps(); cmeng: auto performs in EntityCapsManager init statement
        // entityCapsManager.enableEntityCaps();

        // init EntityCapsManager will add support for Entity Capabilities
        // mDiscoveryManager.addFeature(CapsExtension.NAMESPACE);

        mDiscoveryManager = ServiceDiscoveryManager.getInstanceFor(connection);
        /*
         * Reflect featuresToRemove and featuresToAdd before updateEntityCapsVersion() in order to
         * persist only the complete node#ver association with our own DiscoverInfo. Otherwise,
         * we'd persist all intermediate ones upon each addFeature() and removeFeature().
         */
        // featuresToRemove
        if (featuresToRemove != null) {
            for (String featureToRemove : featuresToRemove)
                mDiscoveryManager.removeFeature(featureToRemove);
        }
        // featuresToAdd
        if (featuresToAdd != null) {
            for (String featureToAdd : featuresToAdd)
                if (!mDiscoveryManager.includesFeature(featureToAdd))
                    mDiscoveryManager.addFeature(featureToAdd);
        }

        // Listener for received cap packages and take necessary action
        connection.addAsyncStanzaListener(new CapsStanzaListener(), PRESENCES_WITH_CAPS);
    }

    /**
     * Returns the discovered information of a given XMPP entity addressed by its JID;
     * Using a 30-second Reply timeout
     *
     * @param entityJid the address of the XMPP entity. Buddy Jid should be FullJid
     * @return The corresponding DiscoverInfo or null if none is known.
     */
    public DiscoverInfo discoverInfo(final Jid entityJid) {
        DiscoverInfo discoverInfo = null;

        // cmeng - "item-not-found" for request on a 5-second wait timeout.
        // Actually server does reply @ 28 seconds after disco#info is sent
        mConnection.setReplyTimeout(ProtocolProviderServiceJabberImpl.SMACK_REPLY_EXTENDED_TIMEOUT_30);
        try {
            discoverInfo = mDiscoveryManager.discoverInfo(entityJid);
        } catch (NoResponseException | XMPPErrorException | NotConnectedException | InterruptedException e) {
            Timber.e("DiscoveryManager.discoverInfo failed %s", e.getMessage());
        }
        mConnection.setReplyTimeout(ProtocolProviderServiceJabberImpl.SMACK_REPLY_TIMEOUT_DEFAULT);

        // Unnecessary as the cap changed is notified via UserCapsNodeNotify()
//        if (discoverInfo != null) {
//            OperationSetContactCapabilitiesJabberImpl capabilitiesOpSet
//                    = (OperationSetContactCapabilitiesJabberImpl) mPPS.getOperationSet(OperationSetContactCapabilities.class);
//            // fire the event
//            if (capabilitiesOpSet != null) {
//                capabilitiesOpSet.fireContactCapabilitiesChanged(entityJid);
//            }
//        }
        return discoverInfo;
    }

    /**
     * Setup the SimpleDirectoryPersistentCache store to support EntityCapsManager persistent
     * store for fast Entity Capabilities and bandwidth improvement.
     * First initialize in {@link ProtocolProviderServiceJabberImpl# initSmackDefaultSettings()}
     * to ensure Persistence store is setup before being access.
     */
    public static void initEntityPersistentStore()
    {
        // A single Persistent Storage for EntityCapsManager to save caps for all accounts
        File entityCapsFolder = new File(aTalkApp.getInstance().getFilesDir() + "/entityCapsStore");

        // Rename old folder if present
        File oldFolder = new File(aTalkApp.getInstance().getFilesDir() + "/entityStore");
        if (oldFolder.exists()) {
            if (oldFolder.renameTo(entityCapsFolder)) {
                Timber.d("Cap folder rename successfully");
            };
        }

        if (!entityCapsFolder.exists()) {
            if (!entityCapsFolder.mkdir())
                Timber.e("Entity Store directory creation error: %s", entityCapsFolder.getAbsolutePath());
        }

        if (entityCapsFolder.exists()) {
            entityCapsPersistentCache = new SimpleDirectoryPersistentCache(entityCapsFolder);
            EntityCapsManager.setPersistentCache(entityCapsPersistentCache);
        }
    }

    /**
     * For cleanup EntityManager entity caps persistence storage and cache.
     * {@link ServerPersistentStoresRefreshDialog}
     */
    public static void refreshEntityCapsStore()
    {
        entityCapsPersistentCache.emptyCache();
        EntityCapsManager.clearMemoryCache();
    }

    // ======================= UserCapsNodeListener Implementation ==================================
    /**
     * Adds a specific <code>UserCapsNodeListener</code> to the list of <code>UserCapsNodeListener</code>s
     * interested in events notifying about changes in the list of user caps nodes of the
     * <code>EntityCapsManager</code>.
     *
     * @param listener the <code>UserCapsNodeListener</code> which is interested in events notifying about
     * changes in the list of user caps nodes of this <code>EntityCapsManager</code>
     */
    public static void addUserCapsNodeListener(UserCapsNodeListener listener)
    {
        if (listener == null)
            throw new NullPointerException("listener");

        synchronized (userCapsNodeListeners) {
            if (!userCapsNodeListeners.contains(listener))
                userCapsNodeListeners.add(listener);
        }
    }

    /**
     * Removes a specific <code>UserCapsNodeListener</code> from the list of
     * <code>UserCapsNodeListener</code>s interested in events notifying about changes in the list of
     * user caps nodes of this <code>EntityCapsManager</code>.
     *
     * @param listener the <code>UserCapsNodeListener</code> which is no longer interested in events notifying
     * about changes in the list of user caps nodes of this <code>EntityCapsManager</code>
     */
    public static void removeUserCapsNodeListener(UserCapsNodeListener listener)
    {
        if (listener != null) {
            synchronized (userCapsNodeListeners) {
                userCapsNodeListeners.remove(listener);
            }
        }
    }

    /**
     * The {@link StanzaListener} that will be registering incoming caps.
     */
    private class CapsStanzaListener implements StanzaListener
    {
        /**
         * Handles incoming presence packets with CapsExtension and alert listeners
         * that the specific user caps node may have changed.
         *
         * @param stanza the incoming presence <code>Packet</code> to be handled
         * @see StanzaListener#processStanza(Stanza)
         */
        public void processStanza(Stanza stanza)
        {
            // Check it the packet indicates that the user is online. We will use this
            // information to decide if we're going to send the discover info request.
            boolean online = (stanza instanceof Presence) && ((Presence) stanza).isAvailable();

            CapsExtension capsExtension = CapsExtension.from(stanza);
            Jid fromJid = stanza.getFrom();

            if ((capsExtension != null) && online) {
                /*
                 * Before Version 1.4 of XEP-0115: Entity Capabilities, the 'ver' attribute was
                 * generated differently and the 'hash' attribute was absent. The 'ver'
                 * attribute in Version 1.3 represents the specific version of the client and
                 * thus does not provide a way to validate the DiscoverInfo sent by the client.
                 * If EntityCapsManager  'hash' attribute, it will assume the legacy format and
                 * will not cache it because the DiscoverInfo to be received from the client
                 * later on will not be trustworthy.
                 */
                UserCapsNodeNotify(fromJid, true);
            }
            else if (!online) {
                UserCapsNodeNotify(fromJid, false);
            }
        }
    }

    /**
     * Alert listener that entity caps node of a user may have changed.
     *
     * @param user the user (FullJid): Can either be account or contact
     * @param online indicates if the user is online
     */
    private void UserCapsNodeNotify(Jid user, boolean online)
    {
        if (user != null) {
            // Fire userCapsNodeNotify.
            UserCapsNodeListener[] listeners;
            synchronized (userCapsNodeListeners) {
                listeners = userCapsNodeListeners.toArray(new UserCapsNodeListener[0]);
            }
            for (UserCapsNodeListener listener : listeners)
                listener.userCapsNodeNotify(user, online);
        }
    }
}
