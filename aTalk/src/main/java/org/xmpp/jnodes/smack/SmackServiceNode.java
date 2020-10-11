package org.xmpp.jnodes.smack;

import org.jivesoftware.smack.*;
import org.jivesoftware.smack.SmackException.NoResponseException;
import org.jivesoftware.smack.SmackException.NotConnectedException;
import org.jivesoftware.smack.filter.StanzaIdFilter;
import org.jivesoftware.smack.iqrequest.AbstractIqRequestHandler;
import org.jivesoftware.smack.packet.*;
import org.jivesoftware.smack.provider.ProviderManager;
import org.jivesoftware.smack.roster.Roster;
import org.jivesoftware.smack.roster.RosterEntry;
import org.jivesoftware.smackx.disco.ServiceDiscoveryManager;
import org.jivesoftware.smackx.disco.packet.DiscoverItems;
import org.jivesoftware.smackx.iqregister.AccountManager;
import org.jxmpp.jid.Jid;
import org.jxmpp.jid.impl.JidCreate;
import org.jxmpp.jid.parts.Localpart;
import org.jxmpp.stringprep.XmppStringprepException;
import org.xmpp.jnodes.RelayChannel;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class SmackServiceNode implements ConnectionListener
{
    private final AbstractXMPPConnection connection;
    private final ConcurrentHashMap<String, RelayChannel> channels = new ConcurrentHashMap<>();
    private final Map<Jid, TrackerEntry> trackerEntries = Collections.synchronizedMap(new LinkedHashMap<>());
    private long timeout = 60000;
    private final static ExecutorService executorService = Executors.newCachedThreadPool();
    private final ScheduledThreadPoolExecutor scheduledExecutor = new ScheduledThreadPoolExecutor(1);
    private final AtomicInteger ids = new AtomicInteger(0);

    static {
        ProviderManager.addIQProvider(JingleChannelIQ.ELEMENT, JingleChannelIQ.NAMESPACE, new JingleNodesProvider());
        ProviderManager.addIQProvider(JingleTrackerIQ.ELEMENT, JingleTrackerIQ.NAMESPACE, new JingleTrackerProvider());
    }

    public SmackServiceNode(final AbstractXMPPConnection connection, final long timeout)
    {
        this.connection = connection;
        this.timeout = timeout;
        setup();
    }

    public void connect(final String user, final String password)
            throws XMPPException, IOException, SmackException, InterruptedException
    {
        connect(user, password, false, Roster.SubscriptionMode.accept_all);
    }

    public void connect(final String user, final String password, final boolean tryCreateAccount,
            final Roster.SubscriptionMode mode)
            throws XMPPException, SmackException, IOException, InterruptedException
    {
        connection.addConnectionListener(this);
        connection.connect();
        if (tryCreateAccount) {
            try {
                AccountManager.getInstance(connection).createAccount(Localpart.from(user), password);
                Thread.sleep(200);
            } catch (NoResponseException | NotConnectedException | InterruptedException e1) {
                // Do Nothing as account may exists
            }
        }
        connection.login(user, password);
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

        ServiceDiscoveryManager.getInstanceFor(connection).addFeature(JingleChannelIQ.NAMESPACE);
        connection.registerIQRequestHandler(new JingleChannelIqRequestHandler());
        connection.registerIQRequestHandler(new JingleTrackerIqRequestHandler());
    }


    private class JingleChannelIqRequestHandler extends AbstractIqRequestHandler
    {
        protected JingleChannelIqRequestHandler()
        {
            super(JingleChannelIQ.ELEMENT, JingleChannelIQ.NAMESPACE, IQ.Type.get, Mode.sync);
        }

        @Override
        public IQ handleIQRequest(IQ iqRequest)
        {
            return createUdpChannel((JingleChannelIQ) iqRequest);
        }
    }

    private class JingleTrackerIqRequestHandler extends AbstractIqRequestHandler
    {
        protected JingleTrackerIqRequestHandler()
        {
            super(JingleTrackerIQ.ELEMENT, JingleTrackerIQ.NAMESPACE, IQ.Type.get, Mode.sync);
        }

        @Override
        public IQ handleIQRequest(IQ iqRequest)
        {
            final JingleTrackerIQ result = createKnownNodes();
            result.setStanzaId(iqRequest.getStanzaId());
            result.setFrom(iqRequest.getTo());
            result.setTo(iqRequest.getFrom());
            return result;
        }
    }

    @Override
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

    @Override
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

    public AbstractXMPPConnection getConnection()
    {
        return connection;
    }

    public static JingleChannelIQ getChannel(final XMPPConnection xmppConnection, final Jid serviceNode)
            throws NotConnectedException, InterruptedException
    {
        if (xmppConnection == null || !xmppConnection.isConnected()) {
            return null;
        }

        final JingleChannelIQ iq = new JingleChannelIQ();
        iq.setFrom(xmppConnection.getUser());
        iq.setTo(serviceNode);

        StanzaCollector collector = xmppConnection.createStanzaCollectorAndSend(iq);
        JingleChannelIQ result = collector.nextResult(Math.round(SmackConfiguration.getDefaultReplyTimeout() * 10.5));
        collector.cancel();
        return result;
    }

    public static JingleTrackerIQ getServices(final XMPPConnection xmppConnection, final Jid serviceNode)
            throws NotConnectedException, InterruptedException
    {
        if (xmppConnection == null || !xmppConnection.isConnected()) {
            return null;
        }

        final JingleTrackerIQ iq = new JingleTrackerIQ();
        iq.setFrom(xmppConnection.getUser());
        iq.setTo(serviceNode);

        StanzaCollector collector = xmppConnection.createStanzaCollectorAndSend(iq);
        Stanza result = collector.nextResult(Math.round(SmackConfiguration.getDefaultReplyTimeout() * 1.5));
        collector.cancel();
        return result instanceof JingleTrackerIQ ? (JingleTrackerIQ) result : null;
    }

    public static void deepSearch(final XMPPConnection xmppConnection, final int maxEntries,
            final Jid startPoint, final MappedNodes mappedNodes,
            final int maxDepth, final int maxSearchNodes,
            final String protocol,
            final ConcurrentHashMap<Jid, Jid> visited)
            throws NotConnectedException, InterruptedException
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
        if ((result != null) && result.getType().equals(IQ.Type.result)) {
            for (final TrackerEntry entry : result.getEntries()) {
                if (entry.getType().equals(TrackerEntry.Type.tracker)) {
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

    public static MappedNodes aSyncSearchServices(final XMPPConnection xmppConnection,
            final int maxEntries, final int maxDepth, final int maxSearchNodes,
            final String protocol, final boolean searchBuddies)
    {
        final MappedNodes mappedNodes = new MappedNodes();
        final Runnable bgTask = () -> {
            try {
                searchServices(new ConcurrentHashMap<>(), xmppConnection, maxEntries,
                        maxDepth, maxSearchNodes, protocol, searchBuddies, mappedNodes);
            } catch (NotConnectedException | InterruptedException e) {
                throw new RuntimeException(e);
            }
        };
        executorService.submit(bgTask);
        return mappedNodes;
    }

    public static MappedNodes searchServices(final XMPPConnection xmppConnection,
            final int maxEntries, final int maxDepth, final int maxSearchNodes,
            final String protocol, final boolean searchBuddies)
            throws NotConnectedException, InterruptedException
    {
        return searchServices(new ConcurrentHashMap<>(), xmppConnection, maxEntries,
                maxDepth, maxSearchNodes, protocol, searchBuddies, new MappedNodes());
    }

    private static MappedNodes searchServices(final ConcurrentHashMap<Jid, Jid> visited,
            final XMPPConnection xmppConnection, final int maxEntries,
            final int maxDepth, final int maxSearchNodes, final String protocol,
            final boolean searchBuddies, final MappedNodes mappedNodes)
            throws NotConnectedException, InterruptedException
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
        } catch (XmppStringprepException | IllegalArgumentException ignore) {
        }

        // Request to Buddies
        Roster roster = Roster.getInstanceFor(xmppConnection);
        if ((roster != null) && searchBuddies) {
            for (final RosterEntry re : roster.getEntries()) {
                for (final Presence presence : roster.getPresences(re.getJid())) {
                    if (presence.isAvailable()) {
                        deepSearch(xmppConnection, maxEntries, presence.getFrom(), mappedNodes,
                                maxDepth - 1, maxSearchNodes, protocol, visited);
                    }
                }
            }
        }
        return mappedNodes;
    }

    private static void searchDiscoItems(final XMPPConnection xmppConnection,
            final int maxEntries, final Jid startPoint, final MappedNodes mappedNodes,
            final int maxDepth, final int maxSearchNodes, final String protocol,
            final ConcurrentHashMap<Jid, Jid> visited)
            throws NotConnectedException, InterruptedException
    {
        final DiscoverItems items = new DiscoverItems();
        items.setTo(startPoint);
        StanzaCollector collector = xmppConnection.createStanzaCollector(new StanzaIdFilter(items.getStanzaId()));
        xmppConnection.sendStanza(items);
        DiscoverItems result = collector.nextResult(Math.round(SmackConfiguration.getDefaultReplyTimeout() * 1.5));

        if (result != null) {
            for (DiscoverItems.Item item : result.getItems()) {
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
        final Map<Jid, TrackerEntry> relayEntries = Collections.synchronizedMap(new LinkedHashMap<>());
        final Map<Jid, TrackerEntry> trackerEntries = Collections.synchronizedMap(new LinkedHashMap<>());

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
    public void connected(XMPPConnection connection)
    {
    }

    @Override
    public void authenticated(XMPPConnection connection, boolean resumed)
    {
    }
}
