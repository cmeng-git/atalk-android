/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.impl.protocol.jabber;

import org.jivesoftware.smack.*;
import org.jxmpp.jid.*;
import net.java.sip.communicator.impl.protocol.jabber.caps.UserCapsNodeListener;
import net.java.sip.communicator.service.protocol.OperationSetContactCapabilities;

import org.atalk.android.aTalkApp;
import org.atalk.android.plugin.timberlog.TimberLog;
import org.atalk.persistance.ServerPersistentStoresRefreshDialog;
import org.jivesoftware.smack.SmackException.NoResponseException;
import org.jivesoftware.smack.SmackException.NotConnectedException;
import org.jivesoftware.smack.XMPPException.XMPPErrorException;
import org.jivesoftware.smack.filter.*;
import org.jivesoftware.smack.packet.*;
import org.jivesoftware.smackx.caps.EntityCapsManager;
import org.jivesoftware.smackx.caps.EntityCapsManager.NodeVerHash;
import org.jivesoftware.smackx.caps.cache.EntityCapsPersistentCache;
import org.jivesoftware.smackx.caps.cache.SimpleDirectoryPersistentCache;
import org.jivesoftware.smackx.caps.packet.CapsExtension;
import org.jivesoftware.smackx.disco.NodeInformationProvider;
import org.jivesoftware.smackx.disco.ServiceDiscoveryManager;
import org.jivesoftware.smackx.disco.packet.DiscoverInfo;
import org.jivesoftware.smackx.disco.packet.DiscoverItems;
import org.jxmpp.util.cache.LruCache;

import java.io.File;
import java.util.*;

import timber.log.Timber;

/**
 * An wrapper to smack's default {@link ServiceDiscoveryManager} that adds support for
 * XEP-0030: Service Discovery.
 * XEP-0115: Entity Capabilities
 *
 * This work is based on Jonas Adahl's smack fork.
 *
 * @author Emil Ivov
 * @author Lubomir Marinov
 * @author Eng Chong Meng
 */
public class ScServiceDiscoveryManager implements NodeInformationProvider
{
    /**
     * The flag which indicates whether we are currently storing no nodeVer caps.
     */
    private final boolean cacheNonCaps;

    /**
     * The cache for storing service discoInfo without nodeVer e.g, proxy.atalk.org, conference.atalk.org.
     * Used only if {@link #cacheNonCaps} is <tt>true</tt>.
     */
    private static final LruCache<Jid, DiscoverInfo> nonCapsCache = new LruCache<>(10000);

    /**
     * The <tt>EntityCapsManager</tt> used by this instance to handle entity capabilities.
     */
    private final EntityCapsManager mEntityCapsManager;

    private static final StanzaFilter PRESENCES_WITH_CAPS = new AndFilter(StanzaTypeFilter.PRESENCE,
            new StanzaExtensionFilter(CapsExtension.ELEMENT, CapsExtension.NAMESPACE));

    /**
     * The {@link ServiceDiscoveryManager} that we are wrapping.
     */
    private final ServiceDiscoveryManager discoveryManager;

    /**
     * The parent provider
     */
    private final ProtocolProviderServiceJabberImpl parentProvider;

    /**
     * The runnable responsible for retrieving discover info.
     */
    private DiscoveryInfoRetriever retriever = new DiscoveryInfoRetriever();

    /**
     * The {@link XMPPConnection} that this manager is responsible for.
     */
    private final XMPPConnection connection;

    /**
     * A {@link List} of the identities we use in our disco answers.
     */
    private final List<DiscoverInfo.Identity> identities;

    /**
     * The list of <tt>UserCapsNodeListener</tt>s interested in events notifying about
     * possible changes in the list of user caps nodes of this <tt>EntityCapsManager</tt>.
     */
    private final List<UserCapsNodeListener> userCapsNodeListeners = new LinkedList<>();

    /**
     * An empty array of <tt>UserCapsNodeListener</tt> elements explicitly defined
     * in order to reduce unnecessary allocations.
     */
    private static final UserCapsNodeListener[] NO_USER_CAPS_NODE_LISTENERS = new UserCapsNodeListener[0];

    /**
     * persistentAvatarCache is used only by ScServiceDiscoveryManager for the specific account entities received
     */
    private static EntityCapsPersistentCache discoInfoPersistentCache = null;

    /**
     * Persistent Storage for ScServiceDiscovery, created per account. Service Discover Features
     * are defined by the account's server capability.
     */
    private File discoInfoStoreDirectory;

    /**
     * A single Persistent Storage for EntityCapsManager to save caps for all accounts
     */
    private static File entityStoreDirectory;

    /**
     * Creates a new <tt>ScServiceDiscoveryManager</tt> wrapping the default discovery manager of
     * the specified <tt>connection</tt>.
     *
     * @param parentProvider the parent provider that creates discovery manager.
     * @param connection Smack connection object that will be used by this instance to handle XMPPTCP connection.
     * @param featuresToRemove an array of <tt>String</tt>s representing the features to be removed from the
     * <tt>ServiceDiscoveryManager</tt> of the specified <tt>connection</tt> which is to be
     * wrapped by the new instance
     * @param featuresToAdd an array of <tt>String</tt>s representing the features to be added to the new instance
     * and to the <tt>ServiceDiscoveryManager</tt> of the specified <tt>connection</tt> which
     * is to be wrapped by the new instance
     */
    public ScServiceDiscoveryManager(ProtocolProviderServiceJabberImpl parentProvider, XMPPConnection connection,
            String[] featuresToRemove, String[] featuresToAdd, boolean cacheNonCaps)
    {
        this.parentProvider = parentProvider;
        this.connection = connection;

        /* setup EntityCapsManager persistent store for XEP-0115: Entity Capabilities */
        // initEntityPersistentStore(); do it in ProtocolProvideServiceJabberImpl

        /* setup persistent store for XEP-0030: Service Discovery */
        initDiscoInfoPersistentStore();

        this.discoveryManager = ServiceDiscoveryManager.getInstanceFor(connection);
        this.identities = new ArrayList<>();
        this.cacheNonCaps = cacheNonCaps;

        // Sync DiscoverInfo.Identity with ServiceDiscoveryManager that has been initialized in
        // ProtocolProviderServiceImpl #ServiceDiscoveryManager.setDefaultIdentity().
        DiscoverInfo.Identity identity = new DiscoverInfo.Identity(
                "client", discoveryManager.getIdentityName(), discoveryManager.getIdentityType());
        identities.add(identity);

        // add support for Entity Capabilities
        discoveryManager.addFeature(CapsExtension.NAMESPACE);

        /*
         * Reflect featuresToRemove and featuresToAdd before updateEntityCapsVersion() in order to
         * persist only the complete node#ver association with our own DiscoverInfo. Otherwise,
         * we'd persist all intermediate ones upon each addFeature() and removeFeature().
         */
        // featuresToRemove
        if (featuresToRemove != null) {
            for (String featureToRemove : featuresToRemove)
                discoveryManager.removeFeature(featureToRemove);
        }
        // featuresToAdd
        if (featuresToAdd != null) {
            for (String featureToAdd : featuresToAdd)
                if (!discoveryManager.includesFeature(featureToAdd))
                    discoveryManager.addFeature(featureToAdd);
        }

        // updateEntityCapsVersion(); cmeng: auto done in mEntityCapsManager init statement
        mEntityCapsManager = EntityCapsManager.getInstanceFor(connection);
        mEntityCapsManager.enableEntityCaps();

        // Listener for received cap packages and take necessary action
        connection.addAsyncStanzaListener(new CapsStanzaListener(), PRESENCES_WITH_CAPS);
    }

    /**
     * Registers that a new feature is supported by this XMPP entity. When this client is queried
     * for its information the registered features will be answered.
     *
     * Since no packet is actually sent to the server it is safe to perform this operation before
     * logging to the server. In fact, you may want to configure the supported features before
     * logging to the server so that the information is already available if it is required upon login.
     *
     * @param feature the feature to register as supported.
     */
    public void addFeature(String feature)
    {
        discoveryManager.addFeature(feature);
    }

    /**
     * Returns <tt>true</tt> if the specified feature is registered in our
     * {@link ServiceDiscoveryManager} and <tt>false</tt> otherwise.
     *
     * @param feature the feature to look for.
     * @return a boolean indicating if the specified featured is registered or not.
     */
    public boolean includesFeature(String feature)
    {
        return discoveryManager.includesFeature(feature);
    }

    /**
     * Removes the specified feature from the supported features by the encapsulated ServiceDiscoveryManager.
     *
     *
     * Since no packet is actually sent to the server it is safe to perform this operation before
     * logging to the server.
     *
     * @param feature the feature to remove from the supported features.
     */
    public void removeFeature(String feature)
    {
        discoveryManager.removeFeature(feature);
    }

    /**
     * ============================================
     * NodeInformationProvider implementation for getNode....()
     *
     * Returns a list of the Items {@link org.jivesoftware.smackx.disco.packet.DiscoverItems.Item}
     * defined in the node or in other words <tt>null</tt> since we don't support any.
     *
     * @return always <tt>null</tt> since we don't support items.
     */
    @Override
    public List<DiscoverItems.Item> getNodeItems()
    {
        return null;
    }

    /**
     * Returns a list of the features defined in the node. For example, the entity caps protocol
     * specifies that an XMPP client should answer with each feature supported by the client version or extension.
     *
     * @return a list of the feature strings defined in the node.
     */
    @Override
    public List<String> getNodeFeatures()
    {
        return discoveryManager.getFeatures();
    }

    /**
     * Returns a list of the identities defined in the node. For example, the x-command protocol
     * must provide an identity of category automation and type command-node for each command.
     *
     * @return a list of the Identities defined in the node.
     */
    @Override
    public List<DiscoverInfo.Identity> getNodeIdentities()
    {
        return identities;
    }

    /**
     * Returns a list of the stanza(/packet) extensions defined in the node.
     *
     * @return a list of the stanza(/packet) extensions defined in the node.
     */
    @Override
    public List<ExtensionElement> getNodePacketExtensions()
    {
        return null;
    }
    /* === End of NodeInformationProvider =================== */

    /**
     * Returns the discovered information of a given XMPP entity addressed by its JID.
     *
     * @param entityJid the address of the XMPP entity. Buddy Jid should be FullJid
     * @return the discovered information.
     * @throws XMPPErrorException if the operation failed for some reason.
     * @throws NoResponseException if there was no response from the server.
     * @throws NotConnectedException if there is no connection
     * @throws InterruptedException if there is an Exception
     */
    public DiscoverInfo discoverInfo(Jid entityJid)
            throws NoResponseException, XMPPErrorException, NotConnectedException, InterruptedException
    {
        // Check if we have it cached in the Entity Capabilities Manager
        DiscoverInfo discoverInfo = EntityCapsManager.getDiscoverInfoByUser(entityJid);
        if (discoverInfo != null) {
            return discoverInfo;
        }

        // Try to get the nvh if it's known, otherwise null is returned e.g. for services
        NodeVerHash nvh = EntityCapsManager.getNodeVerHashByJid(entityJid);

        // if nvh is null; try to retrieve from local nonCapsCache
        if (cacheNonCaps && (nvh == null)) {
            discoverInfo = getDiscoverInfoByEntity(entityJid);
            if (discoverInfo != null)
                return discoverInfo;
        }

        // Not found: Then discover by requesting the information from the remote entity allowing only 10S for blocking access
        discoverInfo = getRemoteDiscoverInfo(entityJid,
                ProtocolProviderServiceJabberImpl.SMACK_REPLY_TIMEOUT_DEFAULT);
        if (discoverInfo != null) {
            // store in local nonCapsCache only if (nvh == null)
            if ((nvh == null) && cacheNonCaps) {
                addDiscoverInfoByEntity(entityJid, discoverInfo);
            }
            return discoverInfo;
        }

        Timber.w("Failed to get DiscoverInfo for: %s", entityJid);
        return null;
    }

    /**
     * Returns the discovered information of a given XMPP entity addressed by its JID if locally
     * cached, otherwise schedules for retrieval.
     *
     * @param entityJid the address of the XMPP entity. Buddy Jid should be FullJid
     * @return the discovered information.
     */
    public DiscoverInfo discoverInfoNonBlocking(Jid entityJid)
    {
        // Check if we have it cached in the Entity Capabilities Manager
        DiscoverInfo discoverInfo = EntityCapsManager.getDiscoverInfoByUser(entityJid);
        if (discoverInfo != null) {
            return discoverInfo;
        }

        // Try to get the nvh if it's known, otherwise null is returned  i.e. for services
        NodeVerHash nvh = EntityCapsManager.getNodeVerHashByJid(entityJid);

        // if nvh is null; try to retrieve from local nonCapsCache
        if (cacheNonCaps && (nvh == null)) {
            discoverInfo = getDiscoverInfoByEntity(entityJid);
            if (discoverInfo != null)
                return discoverInfo;
        }

        // add to retrieve discovery thread
        retriever.addEntityForRetrieve(entityJid);
        return null;
    }

    /**
     * Returns the discovered information of a given XMPP entity addressed by its JID and note attribute.
     * Use this message only when trying to query information which is not directly addressable.
     *
     * @param entityJid the address of the XMPP entity; must be FullJid unless it is for services
     * @param timeout variable timeout to wait: default 10S for blocking and 30S for non-blocking access
     *
     * @return the discovered information.
     * @throws XMPPErrorException if the operation failed for some reason.
     * @throws NoResponseException if there was no response from the server.
     * @throws NotConnectedException not connection exception
     * @throws InterruptedException Interrupt
     */
    private DiscoverInfo getRemoteDiscoverInfo(Jid entityJid, int timeout)
            throws NoResponseException, XMPPErrorException, NotConnectedException, InterruptedException
    {
        // cmeng - "item-not-found" for request on a 5-second wait timeout. Actually server does
        // reply @ 28 seconds after disco#info is sent
        connection.setReplyTimeout(timeout);

        Timber.w("### Remote discovery for: %s", entityJid);
        DiscoverInfo discoInfo = discoveryManager.discoverInfo(entityJid);

        connection.setReplyTimeout(ProtocolProviderServiceJabberImpl.SMACK_REPLY_TIMEOUT_DEFAULT);
        return discoInfo;
    }

    /**
     * Returns the discovered items of a given XMPP entity addressed by its JID.
     *
     * @param entityJid the address of the XMPP entity.
     * @return the discovered information.
     * @throws XMPPErrorException if the operation failed for some reason.
     * @throws NoResponseException if there was no response from the server.
     * @throws NotConnectedException Not connection
     * @throws InterruptedException Interrupt
     */
    public DiscoverItems discoverItems(Jid entityJid)
            throws NoResponseException, XMPPErrorException, NotConnectedException, InterruptedException
    {
        return discoveryManager.discoverItems(entityJid);
    }

    /**
     * Returns the discovered items of a given XMPP entity addressed by its JID and note attribute.
     * Use this message only when trying to query information which is not directly addressable.
     *
     * @param entityJid the address of the XMPP entity.
     * @param node the attribute that supplements the 'jid' attribute.
     * @return the discovered items.
     * @throws XMPPErrorException if the operation failed for some reason.
     * @throws NoResponseException if there was no response from the server.
     * @throws NotConnectedException Not connection
     * @throws InterruptedException Interrupt
     */
    public DiscoverItems discoverItems(Jid entityJid, String node)
            throws NoResponseException, XMPPErrorException, NotConnectedException, InterruptedException
    {
        return discoveryManager.discoverItems(entityJid, node);
    }

    /**
     * Returns <tt>true</tt> if <tt>jid</tt> supports the specified <tt>feature</tt> and
     * <tt>false</tt> otherwise. The method may check the information locally if we've already
     * cached this <tt>jid</tt>'s disco info, or retrieve it from the network.
     *
     * @param jid the jabber ID we'd like to test for support
     * @param feature the URN feature we are interested in
     * @return true if <tt>jid</tt> is discovered to support <tt>feature</tt> and <tt>false</tt> otherwise.
     */
    public boolean supportsFeature(Jid jid, String feature)
    {
        DiscoverInfo info = null;
        try {
            try {
                info = this.discoverInfo(jid);
            } catch (NoResponseException | NotConnectedException | InterruptedException e) {
                e.printStackTrace();
            }
        } catch (XMPPException ex) {
            Timber.i(ex, "failed to retrieve disco info for %s feature %s", jid, feature);
            return false;
        }
        return ((info != null) && info.containsFeature(feature));
    }

    /**
     * Clears/stops what's needed.
     */
    public void stop()
    {
        if (retriever != null)
            retriever.stop();
    }

    /**
     * Add DiscoverInfo to the database only if the entityJid is DomainBareJid.
     *
     * @param entityJid The entity Jid
     * @param info DiscInfo for the specified entity.
     */
    private static void addDiscoverInfoByEntity(Jid entityJid, DiscoverInfo info)
    {
        if (entityJid instanceof DomainBareJid) {
            Timber.w("### Add discInfo for: %s", entityJid);
            nonCapsCache.put(entityJid, info);
            if (discoInfoPersistentCache != null)
                discoInfoPersistentCache.addDiscoverInfoByNodePersistent(entityJid.toString(), info);
        }
    }

    /**
     * Retrieve DiscoverInfo for a specific entity.
     *
     * @param entityJid The entity Jid i.e. DomainJid
     * @return The corresponding DiscoverInfo or null if none is known.
     */
    private static DiscoverInfo getDiscoverInfoByEntity(Jid entityJid)
    {
        // If not in nonCapsCache, try to retrieve the information from discoInfoPersistentCache using entityJid
        DiscoverInfo info = nonCapsCache.lookup(entityJid);
        if (info == null && discoInfoPersistentCache != null) {
            info = discoInfoPersistentCache.lookup(entityJid.toString());
            // Promote the information to nonCapsCache if one was found
            if (info != null) {
                nonCapsCache.put(entityJid, info);
            }
        }

        // If we are able to retrieve information from one of the caches, copy it before returning
        if (info != null)
            info = new DiscoverInfo(info);

        return info;
    }

    // =========================================================

    /**
     * Adds a specific <tt>UserCapsNodeListener</tt> to the list of <tt>UserCapsNodeListener</tt>s
     * interested in events notifying about changes in the list of user caps nodes of the
     * <tt>EntityCapsManager</tt>.
     *
     * @param listener the <tt>UserCapsNodeListener</tt> which is interested in events notifying about
     * changes in the list of user caps nodes of this <tt>EntityCapsManager</tt>
     */
    public void addUserCapsNodeListener(UserCapsNodeListener listener)
    {
        if (listener == null)
            throw new NullPointerException("listener");

        synchronized (userCapsNodeListeners) {
            if (!userCapsNodeListeners.contains(listener))
                userCapsNodeListeners.add(listener);
        }
    }

    /**
     * Removes a specific <tt>UserCapsNodeListener</tt> from the list of
     * <tt>UserCapsNodeListener</tt>s interested in events notifying about changes in the list of
     * user caps nodes of this <tt>EntityCapsManager</tt>.
     *
     * @param listener the <tt>UserCapsNodeListener</tt> which is no longer interested in events notifying
     * about changes in the list of user caps nodes of this <tt>EntityCapsManager</tt>
     */
    public void removeUserCapsNodeListener(UserCapsNodeListener listener)
    {
        if (listener != null) {
            synchronized (userCapsNodeListeners) {
                userCapsNodeListeners.remove(listener);
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
                listeners = userCapsNodeListeners.toArray(NO_USER_CAPS_NODE_LISTENERS);
            }
            for (UserCapsNodeListener listener : listeners)
                listener.userCapsNodeNotify(user, online);
        }
    }

    /**
     * The {@link StanzaListener} that will be registering incoming caps.
     */
    private class CapsStanzaListener implements StanzaListener
    {
        /**
         * Handles incoming presence packets with CapsExtension and alert listeners that the specific user caps
         * node may have changed.
         *
         * @param stanza the incoming presence <tt>Packet</tt> to be handled
         * @see StanzaListener#processStanza(Stanza)
         */
        public void processStanza(Stanza stanza)
        {
            if (!mEntityCapsManager.entityCapsEnabled())
                return;

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

    //==================================================================

    /**
     * Setup the SimpleDirectoryPersistentCache store to support EntityCapsManager persistent
     * store for fast Entity Capabilities and bandwidth improvement.
     * First initialize in {@link ProtocolProviderServiceJabberImpl#initSmackDefaultSettings()}
     * to ensure Persistence store is setup before being access. If necessary later in
     * {@link ServerPersistentStoresRefreshDialog#refreshCapsStore()}
     *
     * Note: {@link #entityStoreDirectory} is a single directory for all jabber accounts to contain
     * all the caps
     */
    public static void initEntityPersistentStore()
    {
        entityStoreDirectory = new File(aTalkApp.getGlobalContext().getFilesDir() + "/entityStore");

        if (!entityStoreDirectory.exists()) {
            if (!entityStoreDirectory.mkdir())
                Timber.e("Entity Store directory creation error: %s", entityStoreDirectory.getAbsolutePath());
        }

        if (entityStoreDirectory.exists()) {
            SimpleDirectoryPersistentCache entityPersistentCache = new SimpleDirectoryPersistentCache(entityStoreDirectory);
            EntityCapsManager.setPersistentCache(entityPersistentCache);
        }
    }

    public static File getEntityPersistentStore()
    {
        return entityStoreDirectory;
    }

    /**
     * Setup the SimpleDirectoryPersistentCache store to support DiscoInfo persistent
     * store for fast discoInfo retrieval and bandwidth performance.
     *
     * Note: {@link #discoInfoStoreDirectory} directory is setup to contain all the disco#info
     * entities for each specific account and is being setup during the account login.
     */
    public void initDiscoInfoPersistentStore()
    {
        String userID = parentProvider.getAccountID().getUserID();
        discoInfoStoreDirectory = new File(aTalkApp.getGlobalContext().getFilesDir() + "/discoInfoStore_" + userID);

        if (!discoInfoStoreDirectory.exists()) {
            if (!discoInfoStoreDirectory.mkdir())
                Timber.e("DiscoInfo Store directory creation error: %s", discoInfoStoreDirectory.getAbsolutePath());
        }

        if (discoInfoStoreDirectory.exists()) {
            SimpleDirectoryPersistentCache persistentCache = new SimpleDirectoryPersistentCache(discoInfoStoreDirectory);
            setDiscoInfoPersistentStore(persistentCache);
        }
    }

    public void setDiscoInfoPersistentStore(SimpleDirectoryPersistentCache cache)
    {
        discoInfoPersistentCache = cache;
    }

    public void clearDiscoInfoPersistentCache()
    {
        nonCapsCache.clear();
    }

    public File getDiscoInfoPersistentStore()
    {
        return discoInfoStoreDirectory;
    }

    /**
     * Thread that runs the discovery info.
     */
    private class DiscoveryInfoRetriever implements Runnable
    {
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
        public void run()
        {
            try {
                stopped = false;

                while (!stopped) {
                    Jid entityToProcess;
                    synchronized (entities) {
                        if (entities.size() == 0) {
                            try {
                                entities.wait();
                            } catch (InterruptedException ex) {
                                ex.printStackTrace();
                            }
                        }
                        entityToProcess = entities.get(0);
                        entities.remove(entityToProcess);
                    }

                    if (entityToProcess != null) {
                        requestDiscoveryInfo(entityToProcess);
                    }
                }
            } catch (Throwable t) {
                Timber.e(t, "Error requesting discovery info, thread ended unexpectedly");
            }
        }

        /**
         * Requests the discovery info and fires the event if retrieved.
         *
         * @param entityJid the entity to request
         */
        private void requestDiscoveryInfo(final Jid entityJid)
        {
            try {
                // Discover by requesting the information from the remote entity;
                // will return null if no nvh in JID_TO_NODEVER_CACHE=>CAPS_CACHE
                DiscoverInfo discoverInfo = getRemoteDiscoverInfo(entityJid,
                        ProtocolProviderServiceJabberImpl.SMACK_REPLY_EXTENDED_TIMEOUT_30);

                // (discoverInfo = null) if iq result with "item-not-found"
                if (discoverInfo != null) {
                    if (cacheNonCaps) {
                        // Timber.w("Add discoverInfo with null nvh for: %s", entityJid);
                        addDiscoverInfoByEntity(entityJid, discoverInfo);
                    }

                    // fire the event
                    if (capabilitiesOpSet != null) {
                        capabilitiesOpSet.fireContactCapabilitiesChanged(entityJid);
                    }
                }
            } catch (NoResponseException | NotConnectedException | XMPPException | InterruptedException e) {
                // print discovery info errors only when trace is enabled
                Timber.log(TimberLog.FINER, e, "Error requesting discover info for %s", entityJid);
            }
        }

        /**
         * Queue entities for retrieval.
         *
         * @param entityJid the entity.
         */
        public void addEntityForRetrieve(Jid entityJid)
        {
            if (entityJid instanceof BareJid)
                Timber.e("Warning! discoInfo for BareJid '%s' repeated access for every call!!!", entityJid.toString());

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
        private void start()
        {
            capabilitiesOpSet = (OperationSetContactCapabilitiesJabberImpl)
                    parentProvider.getOperationSet(OperationSetContactCapabilities.class);
            retrieverThread = new Thread(this, ScServiceDiscoveryManager.class.getName());
            retrieverThread.setDaemon(true);
            retrieverThread.start();
        }

        /**
         * Stops and clears.
         */
        void stop()
        {
            synchronized (entities) {
                stopped = true;
                entities.notifyAll();
                retrieverThread = null;
            }
        }
    }
}
