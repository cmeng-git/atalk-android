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
import java.util.ArrayList;

import net.java.sip.communicator.service.protocol.OperationSetContactCapabilities;

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

import org.jxmpp.jid.BareJid;
import org.jxmpp.jid.Jid;
import org.jxmpp.util.cache.LruCache;

import timber.log.Timber;

/**
 * Helper to smack's default {@link ServiceDiscoveryManager} that adds support for
 * XEP-0030: Service Discovery.
 * XEP-0115: Entity Capabilities
 *
 * @author Eng Chong Meng
 */
public class ServiceDiscoveryHelper {
    private final ServiceDiscoveryManager mDiscoveryManager;
    private final ProtocolProviderServiceJabberImpl mPPS;
    private final XMPPConnection mConnection;

    // for storing of nonCaps discoInfo; in not found EntityCapsManager.getDiscoverInfoByUser(entityJid)
    private static final LruCache<Jid, DiscoverInfo> nonCapsCache = new LruCache<>(100);

    /**
     * The runnable responsible for retrieving discover info.
     */
    private final DiscoveryInfoRetriever retriever = new DiscoveryInfoRetriever();
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
    public ServiceDiscoveryHelper(ProtocolProviderServiceJabberImpl pps, XMPPConnection connection, String[] featuresToRemove, String[] featuresToAdd) {
        mPPS = pps;
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
     * Allow only the default of 10-second Reply timeout to avoid long wait ANR.
     *
     * @param entityJid the address of the XMPP entity. Buddy Jid should be FullJid
     *
     * @return The corresponding DiscoverInfo or null if none is known.
     */
    public DiscoverInfo discoverInfo(final Jid entityJid) {
        DiscoverInfo discoverInfo = null;
        try {
            discoverInfo = mDiscoveryManager.discoverInfo(entityJid);
        }
        catch (NoResponseException | XMPPErrorException | NotConnectedException | InterruptedException e) {
            Timber.e("Discovery info failed for: %s; %s", entityJid, e.getMessage());
        }
        return discoverInfo;
    }

    /**
     * Returns the discovered information of a given XMPP entity addressed by its Jid
     * if cached, otherwise schedules for retrieval only if nvh is not null.
     *
     * @param entityJid the address of the XMPP entity. DiscInfo only valid for Contact with FullJid.
     *
     * @return the discovered information.
     */
    public DiscoverInfo discoverInfoNonBlocking(Jid entityJid) {
        // BareJid does not have DiscoverInfo.
        if (entityJid instanceof BareJid) {
            Timber.e(new Exception("Warning! invalid discoInfo for BareJid: " + entityJid));
            return null;
        }

        // Check if we have it cached in the Entity Capabilities Manager
        // Always return null if JID_TO_NODEVER_CACHE.lookup(user) == null
        DiscoverInfo discoverInfo = EntityCapsManager.getDiscoverInfoByUser(entityJid);
        if (discoverInfo != null) {
            return discoverInfo;
        }
        // Check for local cache for discoverInfo
        else {
            discoverInfo = nonCapsCache.get(entityJid);
            if (discoverInfo != null) {
                return discoverInfo;
            }
        }

        // Start retriever to request for discoInfo. Saved in nonCapsCache for later retrieval, else endless loop.
        Timber.d("Retrieve nonCap discoInfo for Jid: %s", entityJid);
        retriever.addEntityForRetrieve(entityJid);
        return null;
    }

    /**
     * Thread that runs the discovery info without blocking the main UI.
     */
    private class DiscoveryInfoRetriever implements Runnable {
        /**
         * start/stop.
         */
        private boolean stopped = true;

        /**
         * The thread that runs this dispatcher.
         */
        private Thread retrieverThread = null;

        /**
         * Entities to be processed and their nvh. HashMap so we can store null nvh.
         */
        final private ArrayList<Jid> entities = new ArrayList<>();

        /**
         * Our capability operation set.
         */
        private OperationSetContactCapabilitiesJabberImpl capabilitiesOpSet;

        /**
         * Runs in a different thread.
         */
        public void run() {
            try {
                stopped = false;
                while (!stopped) {
                    Jid entityToProcess;
                    synchronized (entities) {
                        if (entities.isEmpty()) {
                            try {
                                entities.wait();
                            }
                            catch (InterruptedException ex) {
                                Timber.e("DiscoveryInfoRetriever: %s", ex.getMessage());
                            }
                        }
                        entityToProcess = entities.get(0);
                        entities.remove(entityToProcess);
                    }

                    if (entityToProcess != null) {
                        requestDiscoveryInfo(entityToProcess);
                    }
                }
            }
            catch (Throwable t) {
                // May happen on aTalk shutDown, where entities array outOfBound
                Timber.w("Error requesting discovery info, thread ended: %s", t.getMessage());
            }
        }

        /**
         * Requests the discovery info and fires the event if retrieved.
         *
         * @param entityJid the entity to request
         */
        private void requestDiscoveryInfo(final Jid entityJid) {
            // cmeng - "item-not-found" for request on a 5-second wait timeout.
            // Discover by requesting the information from the DiscoveryManager.
            // Actually server does reply @ 28 seconds after disco#info is sent
            mConnection.setReplyTimeout(ProtocolProviderServiceJabberImpl.SMACK_REPLY_EXTENDED_TIMEOUT_30);
            DiscoverInfo discoverInfo = null;
            try {
                discoverInfo = mDiscoveryManager.discoverInfo(entityJid);
                nonCapsCache.put(entityJid, discoverInfo);

                // Contain private function call: not use
                // CapsVersionAndHash versionAndHash = EntityCapsManager.generateVerificationString(discoverInfo, StringUtils.SHA1);
                // String nodeVer = discoverInfo.getNode() + "#" + versionAndHash.version;
                // EntityCapsManager.addDiscoverInfoByNode(nodeVer, nodeVer);
            }
            catch (NoResponseException | XMPPErrorException | NotConnectedException | InterruptedException e) {
                Timber.e("DiscoveryManager.discoverInfo failed %s", e.getMessage());
            }
            if (discoverInfo != null) {
                if (capabilitiesOpSet != null) {
                    capabilitiesOpSet.fireContactCapabilitiesChanged(entityJid);
                }
            }
            mConnection.setReplyTimeout(ProtocolProviderServiceJabberImpl.SMACK_DEFAULT_REPLY_TIMEOUT);
        }

        /**
         * Queue entities for retrieval.
         *
         * @param entityJid the entity.
         */
        public void addEntityForRetrieve(Jid entityJid) {
            synchronized (entities) {
                if (!entities.contains(entityJid)) {
                    entities.add(entityJid);
                    entities.notifyAll();
                    if (retrieverThread == null) {
                        start();
                    }
                }
            }
        }

        /**
         * Start thread.
         */
        private void start() {
            capabilitiesOpSet = (OperationSetContactCapabilitiesJabberImpl)
                    mPPS.getOperationSet(OperationSetContactCapabilities.class);
            retrieverThread = new Thread(this, ServiceDiscoveryHelper.class.getName());
            retrieverThread.setDaemon(true);
            retrieverThread.start();
        }

        /**
         * Stops and clears.
         */
        void stop() {
            synchronized (entities) {
                stopped = true;
                entities.notifyAll();
                retrieverThread = null;
            }
        }
    }

    /**
     * Setup the EntityCapsCache store in mySql DB to support EntityCapsManager persistent
     * store for fast Entity Capabilities and bandwidth improvement.
     * First initialize in {@link ProtocolProviderServiceJabberImpl# initSmackDefaultSettings()}
     * to ensure Persistence store is setup before being access.
     */
    public static void initEntityPersistentStore() {
        // Cleanup previous file folder for entityCaps cache
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

        // Init and setup entityCap persistent store in DB.
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
