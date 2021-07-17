/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.impl.protocol.jabber;

import org.apache.commons.lang3.StringUtils;
import org.jivesoftware.smack.*;
import org.jivesoftware.smack.SmackException.NotConnectedException;
import org.jivesoftware.smack.packet.Presence;
import org.jivesoftware.smack.roster.Roster;
import org.jivesoftware.smack.roster.RosterEntry;
import org.jivesoftware.smackx.disco.packet.DiscoverItems;
import org.jxmpp.jid.Jid;
import org.jxmpp.jid.impl.JidCreate;
import org.jxmpp.stringprep.XmppStringprepException;
import org.xmpp.jnodes.smack.*;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import timber.log.Timber;

/**
 * Search for jingle nodes.
 *
 * @author Damian Minkov
 * @author Eng Chong Meng
 */
public class JingleNodesServiceDiscovery implements Runnable
{
    /**
     * Property containing jingle nodes prefix to search for.
     */
    private static final String JINGLE_NODES_SEARCH_PREFIX_PROP = "protocol.jabber.JINGLE_NODES_SEARCH_PREFIXES";

    /**
     * Property containing jingle nodes prefix to search for.
     */
    private static final String JINGLE_NODES_SEARCH_PREFIXES_STOP_ON_FIRST_PROP
            = "protocol.jabber.JINGLE_NODES_SEARCH_PREFIXES_STOP_ON_FIRST";

    /**
     * Synchronization object to monitor auto discovery.
     */
    private final Object jingleNodesSyncRoot;

    /**
     * The service.
     */
    private final SmackServiceNode service;

    /**
     * The connection, must be connected.
     */
    private final XMPPConnection connection;

    /**
     * Our account.
     */
    private final JabberAccountIDImpl accountID;

    /**
     * Creates discovery
     *
     * @param service the service.
     * @param connection the connected connection.
     * @param accountID our account.
     * @param syncRoot the synchronization object while discovering.
     */
    JingleNodesServiceDiscovery(SmackServiceNode service, XMPPConnection connection,
            JabberAccountIDImpl accountID, Object syncRoot)
    {
        this.jingleNodesSyncRoot = syncRoot;
        this.service = service;
        this.connection = connection;
        this.accountID = accountID;
    }

    /**
     * The actual discovery.
     */
    public void run()
    {
        synchronized (jingleNodesSyncRoot) {
            long start = System.currentTimeMillis();
            Timber.i("Start Jingle Nodes discovery!");

            SmackServiceNode.MappedNodes nodes = null;
            String searchNodesWithPrefix = JabberActivator.getResources().getSettingsString(JINGLE_NODES_SEARCH_PREFIX_PROP);
            if (searchNodesWithPrefix == null || searchNodesWithPrefix.length() == 0)
                searchNodesWithPrefix = JabberActivator.getConfigurationService().getString(JINGLE_NODES_SEARCH_PREFIX_PROP);

            // if there are no default prefix settings or this option is turned off, just process
            // with default service discovery making list empty.
            if (searchNodesWithPrefix == null || searchNodesWithPrefix.length() == 0
                    || searchNodesWithPrefix.equalsIgnoreCase("off")) {
                searchNodesWithPrefix = "";
            }

            try {
                nodes = searchServicesWithPrefix(service, connection, 6, 3, 20, JingleChannelIQ.UDP,
                        accountID.isJingleNodesSearchBuddiesEnabled(),
                        accountID.isJingleNodesAutoDiscoveryEnabled(), searchNodesWithPrefix);
            } catch (NotConnectedException | InterruptedException e) {
                Timber.e(e, "Search failed");
            }

            Timber.i("End of Jingle Nodes discovery!\nFound %s Jingle Nodes relay for account: %s in %s ms",
                    (nodes != null ? nodes.getRelayEntries().size() : "0"), accountID.getAccountJid(),
                    (System.currentTimeMillis() - start));
            if (nodes != null)
                service.addEntries(nodes);
        }
    }

    /**
     * Searches for services as the prefix list has priority. If it is set return after first found
     * service.
     *
     * @param service the service.
     * @param xmppConnection the connection.
     * @param maxEntries maximum entries to be searched.
     * @param maxDepth the depth while recursively searching.
     * @param maxSearchNodes number of nodes to query
     * @param protocol the protocol
     * @param searchBuddies should we search our buddies in contactlist.
     * @param autoDiscover is auto discover turned on
     * @param prefix the coma separated list of prefixes to be searched first.
     * @return
     */
    private SmackServiceNode.MappedNodes searchServicesWithPrefix(SmackServiceNode service,
            XMPPConnection xmppConnection, int maxEntries, int maxDepth, int maxSearchNodes,
            String protocol, boolean searchBuddies, boolean autoDiscover, String prefix)
            throws NotConnectedException, InterruptedException
    {
        if (xmppConnection == null || !xmppConnection.isConnected()) {
            return null;
        }

        SmackServiceNode.MappedNodes mappedNodes = new SmackServiceNode.MappedNodes();
        ConcurrentHashMap<Jid, Jid> visited = new ConcurrentHashMap<>();

        // Request to our pre-configured trackerEntries
        for (Map.Entry<Jid, TrackerEntry> entry : service.getTrackerEntries().entrySet()) {
            SmackServiceNode.deepSearch(xmppConnection, maxEntries, entry.getValue().getJid(),
                    mappedNodes, maxDepth - 1, maxSearchNodes, protocol, visited);
        }

        if (autoDiscover) {
            boolean continueSearch = searchDiscoItems(service, xmppConnection, maxEntries,
                    xmppConnection.getXMPPServiceDomain(), mappedNodes, maxDepth - 1, maxSearchNodes,
                    protocol, visited, prefix);

            // option to stop after first found is turned on, lets exit
            if (continueSearch) {
                // Request to Server
                try {
                    SmackServiceNode.deepSearch(xmppConnection, maxEntries, JidCreate.from(xmppConnection.getHost()),
                            mappedNodes, maxDepth - 1, maxSearchNodes, protocol, visited);
                } catch (XmppStringprepException | IllegalArgumentException e) {
                    e.printStackTrace();
                }

                // Request to Buddies
                Roster roster = Roster.getInstanceFor(xmppConnection);
                if ((roster != null) && searchBuddies) {
                    for (final RosterEntry re : roster.getEntries()) {
                        final List<Presence> i = roster.getPresences(re.getJid());
                        for (final Presence presence : i) {
                            if (presence.isAvailable()) {
                                SmackServiceNode.deepSearch(xmppConnection, maxEntries, presence.getFrom(),
                                        mappedNodes, maxDepth - 1, maxSearchNodes, protocol, visited);
                            }
                        }
                    }
                }
            }
        }
        return mappedNodes;
    }

    /**
     * Discover services and query them.
     *
     * @param service the service.
     * @param xmppConnection the connection.
     * @param maxEntries maximum entries to be searched.
     * @param startPoint the start point to search recursively
     * @param mappedNodes nodes found
     * @param maxDepth the depth while recursively searching.
     * @param maxSearchNodes number of nodes to query
     * @param protocol the protocol
     * @param visited nodes already visited
     * @param prefix the coma separated list of prefixes to be searched first.
     * @return
     */
    private static boolean searchDiscoItems(SmackServiceNode service,
            XMPPConnection xmppConnection, int maxEntries, Jid startPoint,
            SmackServiceNode.MappedNodes mappedNodes, int maxDepth, int maxSearchNodes,
            String protocol, ConcurrentHashMap<Jid, Jid> visited, String prefix)
            throws InterruptedException, NotConnectedException
    {
        String[] prefixes = prefix.split(",");

        // default is to stop when first one is found
        boolean stopOnFirst = true;

        String stopOnFirstDefaultValue
                = JabberActivator.getResources().getSettingsString(JINGLE_NODES_SEARCH_PREFIXES_STOP_ON_FIRST_PROP);
        if (stopOnFirstDefaultValue != null) {
            stopOnFirst = Boolean.parseBoolean(stopOnFirstDefaultValue);
        }
        stopOnFirst = JabberActivator.getConfigurationService()
                .getBoolean(JINGLE_NODES_SEARCH_PREFIXES_STOP_ON_FIRST_PROP, stopOnFirst);

        final DiscoverItems items = new DiscoverItems();
        items.setTo(startPoint);
        StanzaCollector collector = xmppConnection.createStanzaCollectorAndSend(items);

        DiscoverItems result = null;
        try {
            result = collector.nextResult(Math.round(SmackConfiguration.getDefaultReplyTimeout() * 1.5));
        } finally {
            collector.cancel();
        }

        if (result != null) {
            // first search priority items
            for (DiscoverItems.Item item : result.getItems()) {
                if (item != null) {
                    for (String pref : prefixes) {
                        if (StringUtils.isNotEmpty(pref) && item.getEntityID().toString().startsWith(pref.trim())) {
                            SmackServiceNode.deepSearch(xmppConnection, maxEntries, item.getEntityID(),
                                    mappedNodes, maxDepth, maxSearchNodes, protocol, visited);

                            if (stopOnFirst)
                                return false;// stop and don't continue
                        }
                    }
                }
            }
            // now search rest
            for (DiscoverItems.Item item : result.getItems()) {
                if (item != null) {
                    // we may searched already this node if it starts with some of the prefixes
                    if (!visited.containsKey(item.getEntityID()))
                        SmackServiceNode.deepSearch(xmppConnection, maxEntries, item.getEntityID(),
                                mappedNodes, maxDepth, maxSearchNodes, protocol, visited);

                    if (stopOnFirst)
                        return false;// stop and don't continue
                }
            }
        }
        // true we should continue searching
        return true;
    }
}
