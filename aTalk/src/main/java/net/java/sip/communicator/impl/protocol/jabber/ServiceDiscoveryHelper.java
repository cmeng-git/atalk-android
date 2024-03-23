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

import java.io.File;
import java.util.LinkedList;
import java.util.List;

import net.java.sip.communicator.impl.protocol.jabber.caps.UserCapsNodeListener;

import org.atalk.android.aTalkApp;
import org.atalk.persistance.EntityCapsCache;
import org.atalk.persistance.ServerPersistentStoresRefreshDialog;
import org.jivesoftware.smack.SmackException.NoResponseException;
import org.jivesoftware.smack.SmackException.NotConnectedException;
import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.XMPPException.XMPPErrorException;
import org.jivesoftware.smackx.caps.EntityCapsManager;
import org.jivesoftware.smackx.disco.ServiceDiscoveryManager;
import org.jivesoftware.smackx.disco.packet.DiscoverInfo;
import org.jxmpp.jid.Jid;

import timber.log.Timber;

/**
 * Helper to smack's default {@link ServiceDiscoveryManager} that adds support for
 * XEP-0030: Service Discovery.
 * XEP-0115: Entity Capabilities
 *
 * @author Eng Chong Meng
 */
public class ServiceDiscoveryHelper {
    /**
     * The {@link ServiceDiscoveryManager} supported in smack
     */
    private final ServiceDiscoveryManager mDiscoveryManager;

    /**
     * The {@link XMPPConnection} that this manager is responsible for.
     */
    private final XMPPConnection mConnection;

    /**
     * The list of <code>UserCapsNodeListener</code>s interested in events notifying about
     * possible changes in the list of user caps nodes of this <code>EntityCapsManager</code>.
     */
    private static final List<UserCapsNodeListener> userCapsNodeListeners = new LinkedList<>();

    private static EntityCapsCache entityCapsPersistentCache;

    /**
     * Creates a new <code>ScServiceDiscoveryManager</code> wrapping the default discovery manager of
     * the specified <code>connection</code>.
     *
     * @param connection Smack connection object that will be used by this instance to handle XMPPTCP connection.
     * @param featuresToRemove an array of <code>String</code>s representing the features to be removed from the
     * <code>ServiceDiscoveryManager</code> of the specified <code>connection</code> which is to be
     * wrapped by the new instance
     * @param featuresToAdd an array of <code>String</code>s representing the features to be added to the new instance
     * and to the <code>ServiceDiscoveryManager</code> of the specified <code>connection</code> which
     * is to be wrapped by the new instance
     */
    public ServiceDiscoveryHelper(XMPPConnection connection, String[] featuresToRemove, String[] featuresToAdd) {
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
    }

    /**
     * Returns the discovered information of a given XMPP entity addressed by its JID;
     * Using a 30-second Reply timeout
     *
     * @param entityJid the address of the XMPP entity. Buddy Jid should be FullJid
     *
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
        mConnection.setReplyTimeout(ProtocolProviderServiceJabberImpl.SMACK_DEFAULT_REPLY_TIMEOUT);
        return discoverInfo;
    }

    /**
     * Setup the EntityCapsCache store to support EntityCapsManager persistent
     * store for fast Entity Capabilities and bandwidth improvement.
     * First initialize in {@link ProtocolProviderServiceJabberImpl# initSmackDefaultSettings()}
     * to ensure Persistence store is setup before being access.
     */
    public static void initEntityPersistentStore() {
        // Remove previous folder for entityCaps cache
        File entityCapsFolder = new File(aTalkApp.getInstance().getFilesDir() + "/entityCapsStore");
        if (delFolder(entityCapsFolder)) {
            Timber.d("%s folder deleted successfully", entityCapsFolder);
        }
        else {
            // older folder if present
            entityCapsFolder = new File(aTalkApp.getInstance().getFilesDir() + "/entityStore");
            if (delFolder(entityCapsFolder)) {
                Timber.d("%s folder deleted successfully", entityCapsFolder);
            }
        }
        entityCapsPersistentCache = new EntityCapsCache();
        EntityCapsManager.setPersistentCache(entityCapsPersistentCache);
    }

    private static boolean delFolder(File folder) {
        File[] files = folder.listFiles();
        if (files == null) {
            return false;
        }
        for (File f : files) {
            f.delete();
        }
        return folder.delete();
    }

    /**
     * For cleanup EntityManager entity caps persistence storage and cache.
     * {@link ServerPersistentStoresRefreshDialog}
     */
    public static void refreshEntityCapsStore() {
        entityCapsPersistentCache.emptyCache();
        EntityCapsManager.clearMemoryCache();
    }
}
