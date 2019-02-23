package org.xmpp.jnodes.smack;

import org.jivesoftware.smack.ConnectionConfiguration;
import org.jivesoftware.smack.ConnectionListener;
import org.jivesoftware.smack.SmackConfiguration;
import org.jivesoftware.smack.SmackException;
import org.jivesoftware.smack.SmackException.NoResponseException;
import org.jivesoftware.smack.SmackException.NotConnectedException;
import org.jivesoftware.smack.StanzaCollector;
import org.jivesoftware.smack.StanzaListener;
import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.XMPPException;
import org.jivesoftware.smack.filter.StanzaFilter;
import org.jivesoftware.smack.filter.StanzaIdFilter;
import org.jivesoftware.smack.packet.IQ;
import org.jivesoftware.smack.packet.Presence;
import org.jivesoftware.smack.packet.Stanza;
import org.jivesoftware.smack.provider.ProviderManager;
import org.jivesoftware.smack.roster.Roster;
import org.jivesoftware.smack.roster.RosterEntry;
import org.jivesoftware.smack.sasl.SASLErrorException;
import org.jivesoftware.smack.tcp.XMPPTCPConnection;
import org.jivesoftware.smack.tcp.XMPPTCPConnectionConfiguration;
import org.jivesoftware.smackx.disco.packet.DiscoverItems;
import org.jivesoftware.smackx.iqregister.AccountManager;
import org.jxmpp.jid.Jid;
import org.jxmpp.jid.impl.JidCreate;
import org.jxmpp.jid.parts.Localpart;
import org.jxmpp.stringprep.XmppStringprepException;
import org.xmpp.jnodes.RelayChannel;

import java.io.IOException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class SmackServiceNode implements ConnectionListener, StanzaListener
{
    private final XMPPTCPConnection connection;
    private final ConcurrentHashMap<String, RelayChannel> channels = new ConcurrentHashMap<>();
    private final Map<Jid, TrackerEntry> trackerEntries
            = Collections.synchronizedMap(new LinkedHashMap<Jid, TrackerEntry>());
    private long timeout;
    private final static ExecutorService executorService = Executors.newCachedThreadPool();
    private final ScheduledThreadPoolExecutor scheduledExecutor = new ScheduledThreadPoolExecutor(1);
    private final AtomicInteger ids = new AtomicInteger(0);

    static {
        ProviderManager.addIQProvider(JingleChannelIQ.NAME, JingleChannelIQ.NAMESPACE, new JingleNodesProvider());
        ProviderManager.addIQProvider(JingleTrackerIQ.NAME, JingleTrackerIQ.NAMESPACE, new JingleTrackerProvider());
    }

    public SmackServiceNode(final XMPPTCPConnection connection, final long timeout)
    {
        this.connection = connection;
        this.timeout = timeout;
        setup();
    }

    public SmackServiceNode(final String server, final int port, final long timeout)
    {
        XMPPTCPConnectionConfiguration.Builder config = XMPPTCPConnectionConfiguration.builder();
        try {
            config.setXmppDomain(JidCreate.domainBareFrom(server));
        } catch (XmppStringprepException e) {
            e.printStackTrace();
        }
        config.setHost(server);
        config.setPort(port);
        // config.setSASLAuthenticationEnabled(false);
        config.setSecurityMode(ConnectionConfiguration.SecurityMode.disabled);
        connection = new XMPPTCPConnection(config.build());
        this.timeout = timeout;
    }

    public void connect(final String user, final String password)
            throws XMPPException, XmppStringprepException
    {
        connect(user, password, false, Roster.SubscriptionMode.accept_all);
    }

    public void connect(final String user, final String password,
            final boolean tryCreateAccount,
            final Roster.SubscriptionMode mode)
            throws XMPPException, XmppStringprepException
    {
        try {
            connection.connect();
        } catch (SmackException | IOException | InterruptedException e2) {
            e2.printStackTrace();
        }
        connection.addConnectionListener(this);

        if (tryCreateAccount) {
            try {
                AccountManager.getInstance(connection).createAccount(Localpart.from(user), password);
                Thread.sleep(200);
            } catch (NoResponseException | NotConnectedException | InterruptedException e1) {
                e1.printStackTrace();
            }
        }
        try {
            connection.login(user, password);
        } catch (SASLErrorException | IOException | SmackException | InterruptedException e) {
            e.printStackTrace();
        }
        Roster.getInstanceFor(connection).setSubscriptionMode(mode);
        setup();
    }

    private void setup()
    {
        scheduledExecutor.scheduleWithFixedDelay(() -> {
            for (final RelayChannel c : channels.values()) {
                final long current = System.currentTimeMillis();
                final long da = current - c.getLastReceivedTimeA();
                final long db = current - c.getLastReceivedTimeB();

                if (da > timeout || db > timeout) {
                    removeChannel(c);
                }
            }
        }, timeout, timeout, TimeUnit.MILLISECONDS);

        connection.addAsyncStanzaListener(this, new StanzaFilter()
        {
            public boolean accept(Stanza packet)
            {
                return packet instanceof JingleChannelIQ || packet instanceof JingleTrackerIQ;
            }
        });
    }

    public void connectionClosed()
    {
        closeAllChannels();
        scheduledExecutor.shutdownNow();
    }

    private void closeAllChannels()
    {
        for (final RelayChannel c : channels.values()) {
            removeChannel(c);
        }
    }

    private void removeChannel(final RelayChannel c)
    {
        channels.remove(c.getAttachment());
        c.close();
    }

    public void connectionClosedOnError(Exception e)
    {
        closeAllChannels();
    }

    protected IQ createUdpChannel(final JingleChannelIQ iq)
    {
        try {
            final RelayChannel rc = RelayChannel.createLocalRelayChannel("0.0.0.0", 10000, 40000);
            final int id = ids.incrementAndGet();
            final String sId = String.valueOf(id);
            rc.setAttachment(sId);
            channels.put(sId, rc);

            final JingleChannelIQ result = new JingleChannelIQ();
            result.setType(IQ.Type.result);
            result.setTo(iq.getFrom());
            result.setFrom(iq.getTo());
            result.setStanzaId(iq.getStanzaId());
            result.setHost(rc.getIp());
            result.setLocalport(rc.getPortA());
            result.setRemoteport(rc.getPortB());
            result.setId(sId);
            return result;

        } catch (IOException e) {
            e.printStackTrace();
            return JingleChannelIQ.createEmptyError();
        }
    }

    public void processStanza(final Stanza packet)
    {
        // System.out.println("Received: " + packet.toXML(XmlEnvironment.EMPTY));
        if (packet instanceof JingleChannelIQ) {
            final JingleChannelIQ request = (JingleChannelIQ) packet;
            if (request.isRequest()) {
                try {
                    connection.sendStanza(createUdpChannel(request));
                } catch (NotConnectedException | InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
        else if (packet instanceof JingleTrackerIQ) {
            final JingleTrackerIQ iq = (JingleTrackerIQ) packet;
            if (iq.isRequest()) {
                final JingleTrackerIQ result = createKnownNodes();
                result.setStanzaId(packet.getStanzaId());
                result.setFrom(packet.getTo());
                result.setTo(packet.getFrom());
                try {
                    connection.sendStanza(result);
                } catch (NotConnectedException | InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public XMPPTCPConnection getConnection()
    {
        return connection;
    }

    public static JingleChannelIQ getChannel(final XMPPTCPConnection xmppConnection, final Jid serviceNode)
    {
        if (xmppConnection == null || !xmppConnection.isConnected()) {
            return null;
        }

        final JingleChannelIQ iq = new JingleChannelIQ();
        iq.setFrom(xmppConnection.getUser());
        iq.setTo(serviceNode);

        StanzaCollector collector = xmppConnection.createStanzaCollector(new StanzaIdFilter(iq.getStanzaId()));
        try {
            xmppConnection.sendStanza(iq);
        } catch (NotConnectedException | InterruptedException e) {
            e.printStackTrace();
        }
        JingleChannelIQ result = null;
        try {
            result = collector.nextResult(Math.round(SmackConfiguration.getDefaultReplyTimeout() * 1.5));
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        collector.cancel();
        return result;
    }

    public static JingleTrackerIQ getServices(final XMPPTCPConnection xmppConnection, final Jid serviceNode)
    {
        if (xmppConnection == null || !xmppConnection.isConnected()) {
            return null;
        }

        final JingleTrackerIQ iq = new JingleTrackerIQ();
        iq.setFrom(xmppConnection.getUser());
        iq.setTo(serviceNode);

        StanzaCollector collector = xmppConnection.createStanzaCollector(new StanzaIdFilter(iq.getStanzaId()));
        try {
            xmppConnection.sendStanza(iq);
        } catch (NotConnectedException | InterruptedException e) {
            e.printStackTrace();
        }
        Stanza result = null;
        try {
            result = collector.nextResult(Math.round(SmackConfiguration.getDefaultReplyTimeout() * 1.5));
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        collector.cancel();
        return result instanceof JingleTrackerIQ ? (JingleTrackerIQ) result : null;
    }

    public static void deepSearch(final XMPPTCPConnection xmppConnection, final int maxEntries,
            final Jid startPoint, final MappedNodes mappedNodes,
            final int maxDepth, final int maxSearchNodes,
            final String protocol,
            final ConcurrentHashMap<Jid, Jid> visited)
    {
        if (xmppConnection == null || !xmppConnection.isConnected()) {
            return;
        }
        if (mappedNodes.getRelayEntries().size() > maxEntries || maxDepth <= 0) {
            return;
        }
        if (startPoint.equals(xmppConnection.getUser())) {
            return;
        }
        if (visited.size() > maxSearchNodes) {
            return;
        }

        JingleTrackerIQ result = getServices(xmppConnection, startPoint);
        visited.put(startPoint, startPoint);
        if ((result != null) && (result.getType() == IQ.Type.result)) {
            for (final TrackerEntry entry : result.getEntries()) {
                if (entry.getType() == TrackerEntry.Type.tracker) {
                    mappedNodes.getTrackerEntries().put(entry.getJid(), entry);
                    deepSearch(xmppConnection, maxEntries, entry.getJid(),
                            mappedNodes, maxDepth - 1, maxSearchNodes, protocol, visited);
                }
                else if (entry.getType().equals(TrackerEntry.Type.relay)) {
                    if (protocol == null || protocol.equals(entry.getProtocol())) {
                        mappedNodes.getRelayEntries().put(entry.getJid(), entry);
                    }
                }
            }
        }
    }

    public static MappedNodes aSyncSearchServices(final XMPPTCPConnection xmppConnection,
            final int maxEntries, final int maxDepth, final int maxSearchNodes,
            final String protocol, final boolean searchBuddies)
    {
        final MappedNodes mappedNodes = new MappedNodes();
        final Runnable bgTask = () -> searchServices(new ConcurrentHashMap<>(), xmppConnection, maxEntries,
                maxDepth, maxSearchNodes, protocol, searchBuddies, mappedNodes);
        executorService.submit(bgTask);
        return mappedNodes;
    }

    public static MappedNodes searchServices(final XMPPTCPConnection xmppConnection,
            final int maxEntries, final int maxDepth, final int maxSearchNodes,
            final String protocol, final boolean searchBuddies)
    {
        return searchServices(new ConcurrentHashMap<>(), xmppConnection, maxEntries,
                maxDepth, maxSearchNodes, protocol, searchBuddies, new MappedNodes());
    }

    private static MappedNodes searchServices(final ConcurrentHashMap<Jid, Jid> visited,
            final XMPPTCPConnection xmppConnection, final int maxEntries,
            final int maxDepth, final int maxSearchNodes, final String protocol,
            final boolean searchBuddies, final MappedNodes mappedNodes)
    {
        if (xmppConnection == null || !xmppConnection.isConnected()) {
            return null;
        }

        searchDiscoItems(xmppConnection, maxEntries, xmppConnection.getXMPPServiceDomain(),
                mappedNodes, maxDepth - 1, maxSearchNodes, protocol, visited);

        // Request to Server
        try {
            deepSearch(xmppConnection, maxEntries, JidCreate.from(xmppConnection.getHost()),
                    mappedNodes, maxDepth - 1, maxSearchNodes, protocol, visited);
        } catch (XmppStringprepException e) {
            e.printStackTrace();
        }

        // Request to Buddies
        Roster roster = Roster.getInstanceFor(xmppConnection);
        if ((roster != null) && searchBuddies) {
            for (final RosterEntry re : roster.getEntries()) {
                final List<Presence> i = roster.getPresences(re.getJid());
                for (final Presence presence : i) {
                    if (presence.isAvailable()) {
                        deepSearch(xmppConnection, maxEntries, presence.getFrom(), mappedNodes,
                                maxDepth - 1, maxSearchNodes, protocol, visited);
                    }
                }
            }
        }
        return mappedNodes;
    }

    private static void searchDiscoItems(final XMPPTCPConnection xmppConnection,
            final int maxEntries, final Jid startPoint, final MappedNodes mappedNodes,
            final int maxDepth, final int maxSearchNodes, final String protocol,
            final ConcurrentHashMap<Jid, Jid> visited)
    {
        final DiscoverItems items = new DiscoverItems();
        items.setTo(startPoint);
        StanzaCollector collector = xmppConnection.createStanzaCollector(new StanzaIdFilter(items.getStanzaId()));
        try {
            xmppConnection.sendStanza(items);
        } catch (NotConnectedException | InterruptedException e) {
            e.printStackTrace();
        }
        DiscoverItems result = null;
        try {
            result = collector.nextResult(Math.round(SmackConfiguration.getDefaultReplyTimeout() * 1.5));
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        if (result != null) {
            final List<DiscoverItems.Item> i = result.getItems();

            for (DiscoverItems.Item item : i) {
                if (item != null) {
                    deepSearch(xmppConnection, maxEntries, item.getEntityID(), mappedNodes,
                            maxDepth, maxSearchNodes, protocol, visited);
                }
            }
        }
        collector.cancel();
    }

    public static class MappedNodes
    {
        final Map<Jid, TrackerEntry> relayEntries = Collections.synchronizedMap(new LinkedHashMap<Jid, TrackerEntry>());
        final Map<Jid, TrackerEntry> trackerEntries = Collections.synchronizedMap(new LinkedHashMap<Jid, TrackerEntry>());

        public Map<Jid, TrackerEntry> getRelayEntries()
        {
            return relayEntries;
        }

        public Map<Jid, TrackerEntry> getTrackerEntries()
        {
            return trackerEntries;
        }
    }

    ConcurrentHashMap<String, RelayChannel> getChannels()
    {
        return channels;
    }

    public JingleTrackerIQ createKnownNodes()
    {

        final JingleTrackerIQ iq = new JingleTrackerIQ();
        iq.setType(IQ.Type.result);

        for (final TrackerEntry entry : trackerEntries.values()) {
            if (!entry.getPolicy().equals(TrackerEntry.Policy._roster)) {
                iq.addEntry(entry);
            }
        }

        return iq;
    }

    public void addTrackerEntry(final TrackerEntry entry)
    {
        trackerEntries.put(entry.getJid(), entry);
    }

    public void addEntries(final MappedNodes entries)
    {
        for (final TrackerEntry t : entries.getRelayEntries().values()) {
            addTrackerEntry(t);
        }
        for (final TrackerEntry t : entries.getTrackerEntries().values()) {
            addTrackerEntry(t);
        }
    }

    public Map<Jid, TrackerEntry> getTrackerEntries()
    {
        return trackerEntries;
    }

    public TrackerEntry getPreferredRelay()
    {
        for (final TrackerEntry trackerEntry : trackerEntries.values()) {
            if (TrackerEntry.Type.relay.equals(trackerEntry.getType())) {
                return trackerEntry;
            }
        }
        return null;
    }

    @Override
    public void connected(XMPPConnection paramXMPPConnection)
    {
        // TODO Auto-generated method stub
    }

    @Override
    public void authenticated(XMPPConnection arg0, boolean arg1)
    {
        // TODO Auto-generated method stub
    }
}
