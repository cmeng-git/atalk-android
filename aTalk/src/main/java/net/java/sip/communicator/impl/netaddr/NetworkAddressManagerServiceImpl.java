/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See
 * the License for the
 * specific language governing permissions and limitations under the License.
 */
package net.java.sip.communicator.impl.netaddr;

import java.beans.PropertyChangeEvent;
import java.io.IOException;
import java.lang.reflect.Method;
import java.net.BindException;
import java.net.DatagramSocket;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Enumeration;

import net.java.sip.communicator.service.netaddr.NetworkAddressManagerService;
import net.java.sip.communicator.service.netaddr.event.NetworkConfigurationChangeListener;
import net.java.sip.communicator.util.NetworkUtils;

import org.atalk.impl.timberlog.TimberLog;
import org.atalk.service.configuration.ConfigurationService;
import org.atalk.util.OSUtils;
import org.ice4j.Transport;
import org.ice4j.TransportAddress;
import org.ice4j.ice.Agent;
import org.ice4j.ice.IceMediaStream;
import org.ice4j.ice.harvest.StunCandidateHarvester;
import org.ice4j.ice.harvest.TurnCandidateHarvester;
import org.ice4j.security.LongTermCredential;
import org.minidns.record.SRV;

import timber.log.Timber;

/**
 * This implementation of the Network Address Manager allows you to intelligently retrieve the
 * address of your localhost according to the destinations that you will be trying to reach. It
 * also provides an interface to the ICE implementation in ice4j.
 *
 * @author Emil Ivov
 * @author Eng Chong Meng
 */
public class NetworkAddressManagerServiceImpl implements NetworkAddressManagerService {
    /**
     * The socket that we use for dummy connections during selection of a local address that has
     * to be used when communicating with a specific location.
     */
    DatagramSocket localHostFinderSocket = null;

    /**
     * A random (unused)local port to use when trying to select a local host address to use when
     * sending messages to a specific destination.
     */
    private static final int RANDOM_ADDR_DISC_PORT = 55721;

    /**
     * Default STUN server port.
     */
    public static final int DEFAULT_STUN_SERVER_PORT = 3478;

    /**
     * A thread which periodically scans network interfaces and reports changes in network configuration.
     */
    private NetworkConfigurationWatcher networkConfigurationWatcher = null;

    /**
     * The service name to use when discovering TURN servers through DNS using SRV requests as per RFC 5766.
     */
    public static final String TURN_SRV_NAME = "turn";

    /**
     * The service name to use when discovering STUN servers through DNS using SRV requests as per RFC 5389.
     */
    public static final String STUN_SRV_NAME = "stun";

    /**
     * Initializes this network address manager service implementation.
     */
    public void start() {
        this.localHostFinderSocket = initRandomPortSocket();
    }

    /**
     * Kills all threads/processes launched by this thread (if any) and prepares it for shutdown.
     * You may use this method as a reinitialization technique (you'll have to call start afterwards)
     */
    public void stop() {
        if (networkConfigurationWatcher != null)
            networkConfigurationWatcher.stop();
    }

    /**
     * Returns an InetAddress instance that represents the localhost, and that a socket can bind
     * upon or distribute to peers as a contact address.
     *
     * @param intendedDestination the destination that we'd like to use the localhost address with.
     *
     * @return an InetAddress instance representing the local host, and that a socket can bind
     * upon or distribute to peers as a contact address.
     */
    public synchronized InetAddress getLocalHost(InetAddress intendedDestination) {
        InetAddress localHost;
        Timber.log(TimberLog.FINER, "Querying for a localhost address for intended destination '%s", intendedDestination);

        /*
         * For other systems than windows, we used method based on DatagramSocket.connect which will returns us source address.
         * The reason why we cannot use it on Windows is because its socket implementation returns any address...
         */
        // no point in making sure that the localHostFinderSocket is initialized.
        // better let it through a NullPointerException.
        localHostFinderSocket.connect(intendedDestination, RANDOM_ADDR_DISC_PORT);
        localHost = localHostFinderSocket.getLocalAddress();
        localHostFinderSocket.disconnect();

        // windows socket implementations return the any address so we need to find something else here ...
        // InetAddress.getLocalHost seems to work better on windows so let's hope it'll do the trick.
        if (localHost == null) {
            try {
                localHost = InetAddress.getLocalHost();
            } catch (UnknownHostException e) {
                Timber.w(e, "Failed to get localhost");
            }
        }
        if ((localHost != null) && localHost.isAnyLocalAddress()) {
            Timber.log(TimberLog.FINER, "Socket returned the ANY local address. Trying a workaround.");
            try {
                // all that's inside the if is an ugly IPv6 hack (good ol' IPv6 - always causing more problems than it solves.)
                if (intendedDestination instanceof Inet6Address) {
                    // return the first globally route-able ipv6 address we find on the machine (and hope it's a good one)
                    boolean done = false;
                    Enumeration<NetworkInterface> ifaces = NetworkInterface.getNetworkInterfaces();

                    while (!done && ifaces.hasMoreElements()) {
                        Enumeration<InetAddress> addresses = ifaces.nextElement().getInetAddresses();

                        while (addresses.hasMoreElements()) {
                            InetAddress address = addresses.nextElement();

                            if ((address instanceof Inet6Address) && !address.isAnyLocalAddress()
                                    && !address.isLinkLocalAddress() && !address.isLoopbackAddress()
                                    && !address.isSiteLocalAddress()) {
                                localHost = address;
                                done = true;
                                break;
                            }
                        }
                    }
                }
                // an IPv4 destination
                else {
                    // Make sure we got an IPv4 address.
                    if (intendedDestination instanceof Inet4Address) {
                        // return the first non-loopback interface we find.
                        boolean done = false;
                        Enumeration<NetworkInterface> ifaces = NetworkInterface.getNetworkInterfaces();

                        while (!done && ifaces.hasMoreElements()) {
                            Enumeration<InetAddress> addresses = ifaces.nextElement().getInetAddresses();

                            while (addresses.hasMoreElements()) {
                                InetAddress address = addresses.nextElement();

                                if ((address instanceof Inet4Address) && !address.isLoopbackAddress()) {
                                    localHost = address;
                                    done = true;
                                    break;
                                }
                            }
                        }
                    }
                }
            } catch (Exception e) {
                // sigh ... ok return 0.0.0.0
                Timber.w(e, "Failed to get localhost");
            }
        }
        Timber.log(TimberLog.FINER, "Returning the localhost address '%s'", localHost);
        return localHost;
    }

    /**
     * Returns the hardware address (i.e. MAC address) of the specified interface name.
     *
     * @param iface the <code>NetworkInterface</code>
     *
     * @return array of bytes representing the layer 2 address or null if interface does not exist
     */
    public byte[] getHardwareAddress(NetworkInterface iface) {
        String ifName;
        byte[] hwAddress;

        /* try reflection */
        try {
            Method method = iface.getClass().getMethod("getHardwareAddress");
            hwAddress = (byte[]) method.invoke(iface, new Object[]{});
            return hwAddress;
        } catch (Exception e) {
            Timber.e("get Hardware Address failed: %s", e.getMessage());
        }
        /*
         * maybe getHardwareAddress not available on this JVM try with our JNI
         */
        if (OSUtils.IS_WINDOWS) {
            ifName = iface.getDisplayName();
        }
        else {
            ifName = iface.getName();
        }
        hwAddress = HardwareAddressRetriever.getHardwareAddress(ifName);
        return hwAddress;
    }

    /**
     * Tries to obtain an for the specified port.
     *
     * @param dst the destination that we'd like to use this address with.
     * @param port the port whose mapping we are interested in.
     *
     * @return a public address corresponding to the specified port or null if all attempts to
     * retrieve such an address have failed.
     *
     * @throws IOException if an error occurs while creating the socket.
     * @throws BindException if the port is already in use.
     */
    public InetSocketAddress getPublicAddressFor(InetAddress dst, int port)
            throws IOException, BindException {
        // we'll try to bind so that we could notify the caller if the port has been taken already.
        DatagramSocket bindTestSocket = new DatagramSocket(port);
        bindTestSocket.close();

        // if we're here then the port was free.
        return new InetSocketAddress(getLocalHost(dst), port);
    }

    /**
     * This method gets called when a bound property is changed.
     *
     * @param evt A PropertyChangeEvent object describing the event source and the property that has changed.
     */
    public void propertyChange(PropertyChangeEvent evt) {
        // there's no point in implementing this method as we have no way of knowing whether the
        // current property change event is the only event we're going to get or whether another
        // one is going to follow..

        // in the case of a STUN_SERVER_ADDRESS property change for example there's no way of
        // knowing whether a STUN_SERVER_PORT property change will follow or not.

        // Reinitialization will therefore only happen if the reinitialize() method is called.
    }

    /**
     * Initializes and binds a socket that on a random port number. The method would try to bind
     * on a random port and retry 5 times until a free port is found.
     *
     * @return the socket that we have initialized on a random port number.
     */
    private DatagramSocket initRandomPortSocket() {
        DatagramSocket resultSocket = null;
        String bindRetriesStr = NetaddrActivator.getConfigurationService().getString(BIND_RETRIES_PROPERTY_NAME);

        int bindRetries = 5;
        if (bindRetriesStr != null) {
            try {
                bindRetries = Integer.parseInt(bindRetriesStr);
            } catch (NumberFormatException ex) {
                Timber.e(ex, "%s does not appear to be an integer. Defaulting port bind retries to %s",
                        bindRetriesStr, bindRetries);
            }
        }
        int currentlyTriedPort = NetworkUtils.getRandomPortNumber();

        // we'll first try to bind to a random port. if this fails we'll try again
        // (bindRetries times in all) until we find a free local port.
        for (int i = 0; i < bindRetries; i++) {
            try {
                resultSocket = new DatagramSocket(currentlyTriedPort);
                // we succeeded - break so that we don't try to bind again
                break;
            } catch (SocketException exc) {
                if (!exc.getMessage().contains("Address already in use")) {
                    Timber.e(exc, "An exception occurred while trying to create a local host discovery socket.");
                    return null;
                }
                // port seems to be taken. try another one.
                Timber.d("Port %d seems to be in use.", currentlyTriedPort);
                currentlyTriedPort = NetworkUtils.getRandomPortNumber();
                Timber.d("Retrying bind on port %s", currentlyTriedPort);
            }
        }
        return resultSocket;
    }

    /**
     * Creates a <code>DatagramSocket</code> and binds it to the specified <code>localAddress</code> and a
     * port in the range specified by the <code>minPort</code> and <code>maxPort</code> parameters. We
     * first try to bind the newly created socket on the <code>preferredPort</code> port number
     * (unless it is outside the <code>[minPort, maxPort]</code> range in which case we first try the
     * <code>minPort</code>) and then proceed incrementally upwards until we succeed or reach the bind
     * retries limit. If we reach the <code>maxPort</code> port number before the bind retries limit,
     * we will then start over again at <code>minPort</code> and keep going until we run out of retries.
     *
     * @param laddr the address that we'd like to bind the socket on.
     * @param preferredPort the port number that we should try to bind to first.
     * @param minPort the port number where we should first try to bind before moving to the next one
     * (i.e. <code>minPort + 1</code>)
     * @param maxPort the maximum port number where we should try binding before giving up and throwing an exception.
     *
     * @return the newly created <code>DatagramSocket</code>.
     *
     * @throws IllegalArgumentException if either <code>minPort</code> or <code>maxPort</code> is not a valid
     * port number or if <code>minPort > maxPort</code>.
     * @throws IOException if an error occurs while the underlying resolver lib is using sockets.
     * @throws BindException if we couldn't find a free port between <code>minPort</code> and <code>maxPort</code> before
     * reaching the maximum allowed number of retries.
     */
    public DatagramSocket createDatagramSocket(InetAddress laddr, int preferredPort, int minPort, int maxPort)
            throws IllegalArgumentException, IOException, BindException {
        // make sure port numbers are valid
        if (!NetworkUtils.isValidPortNumber(minPort) || !NetworkUtils.isValidPortNumber(maxPort)) {
            throw new IllegalArgumentException("minPort (" + minPort + ") and maxPort (" + maxPort
                    + ")  should be integers between 1024 and 65535.");
        }

        // make sure minPort comes before maxPort.
        if (minPort > maxPort) {
            throw new IllegalArgumentException("minPort (" + minPort
                    + ") should be less than or equal to maxPort (" + maxPort + ")");
        }

        // if preferredPort is not in the allowed range, place it at min.
        if (minPort > preferredPort || preferredPort > maxPort) {
            throw new IllegalArgumentException("preferred Port (" + preferredPort
                    + ") must be between minPort (" + minPort + ") and maxPort (" + maxPort + ")");
        }

        ConfigurationService config = NetaddrActivator.getConfigurationService();
        int bindRetries = config.getInt(BIND_RETRIES_PROPERTY_NAME, BIND_RETRIES_DEFAULT_VALUE);

        int port = preferredPort;
        for (int i = 0; i < bindRetries; i++) {
            try {
                return new DatagramSocket(port, laddr);
            } catch (SocketException se) {
                Timber.i("Retrying a bind because of a failure to bind to address: %s and port: %d", laddr, port);
                Timber.log(TimberLog.FINER, se, "Since you seem, here's a stack");
            }
            port++;
            if (port > maxPort)
                port = minPort;
        }
        throw new BindException("Could not bind to any port between " + minPort + " and " + (port - 1));
    }

    /**
     * Adds new <code>NetworkConfigurationChangeListener</code> which will be informed for network configuration changes.
     *
     * @param listener the listener.
     */
    public synchronized void addNetworkConfigurationChangeListener(NetworkConfigurationChangeListener listener) {
        if (networkConfigurationWatcher == null)
            networkConfigurationWatcher = new NetworkConfigurationWatcher();

        networkConfigurationWatcher.addNetworkConfigurationChangeListener(listener);
    }

    /**
     * Remove <code>NetworkConfigurationChangeListener</code>.
     *
     * @param listener the listener.
     */
    public synchronized void removeNetworkConfigurationChangeListener(NetworkConfigurationChangeListener listener) {
        if (networkConfigurationWatcher != null)
            networkConfigurationWatcher.removeNetworkConfigurationChangeListener(listener);
    }

    /**
     * Creates and returns an ICE agent that a protocol could use for the negotiation of media
     * transport addresses. One ICE agent should only be used for a single session negotiation.
     *
     * @return the newly created ICE Agent.
     */
    public Agent createIceAgent() {
        return new Agent();
    }

    /**
     * Tries to discover a TURN or a STUN server for the specified <code>domainName</code>. The method
     * would first try to discover a TURN server and then fall back to STUN only. In both cases
     * we would only care about a UDP transport.
     *
     * @param domainName the domain name that we are trying to discover a TURN server for.
     * @param userName the name of the user we'd like to use when connecting to a TURN server (we won't be
     * using credentials in case we only have a STUN server).
     * @param password the password that we'd like to try when connecting to a TURN server (we won't be using
     * credentials in case we only have a STUN server).
     *
     * @return A {@link StunCandidateHarvester} corresponding to the TURN or STUN server we
     * discovered or <code>null</code> if there were no such records for the specified <code>domainName</code>
     */
    public StunCandidateHarvester discoverStunServer(String domainName, byte[] userName, byte[] password) {
        // cmeng - Do not proceed to check further if the domainName is not reachable, just return null
        try {
            InetAddress inetAddress = InetAddress.getByName(domainName);
        } catch (UnknownHostException e) {
            Timber.w("Unreachable host for TURN/STUN discovery: %s", domainName);
            return null;
        }

        String srvrAddress = null;
        int port = 0;
        try {
            SRV[] srvRecords = NetworkUtils.getSRVRecords(TURN_SRV_NAME, Transport.UDP.toString(), domainName);
            if (srvRecords != null) {
                srvrAddress = srvRecords[0].target.toString();
            }

            // Seem to have a TURN server, so we'll be using it for both TURN and STUN harvesting.
            if (srvrAddress != null) {
                return new TurnCandidateHarvester(new TransportAddress(srvrAddress, srvRecords[0].port, Transport.UDP),
                        new LongTermCredential(userName, password));
            }

            // srvrAddress was null. try for a STUN only server.
            srvRecords = NetworkUtils.getSRVRecords(STUN_SRV_NAME, Transport.UDP.toString(), domainName);
            if (srvRecords != null) {
                srvrAddress = srvRecords[0].target.toString();
                port = srvRecords[0].port;
            }
        } catch (IOException e) {
            Timber.w("Failed to fetch STUN/TURN SRV RR for %s: %s", domainName, e.getMessage());
        }

        if (srvrAddress != null) {
            return new StunCandidateHarvester(new TransportAddress(srvrAddress, port, Transport.UDP));
        }
        // srvrAddress was still null. sigh ...
        return null;
    }

    /**
     * Creates an <code>IceMediaStream</code> and adds to it an RTP and and RTCP component,
     * which also implies running the currently installed harvesters so that they would.
     *
     * @param rtpPort the port that we should try to bind the RTP component on
     * (the RTCP one would automatically go to rtpPort + 1)
     * @param streamName the name of the stream to create
     * @param agent the <code>Agent</code> that should create the stream.
     *
     * @return the newly created <code>IceMediaStream</code>.
     *
     * @throws IllegalArgumentException if <code>rtpPort</code> is not a valid port number.
     * @throws IOException if an error occurs while the underlying resolver is using sockets.
     * @throws BindException if we couldn't find a free port between within the default number of retries.
     */
    public IceMediaStream createIceStream(int rtpPort, String streamName, Agent agent)
            throws IllegalArgumentException, IOException, BindException {
        return createIceStream(2, rtpPort, streamName, agent);
    }

    /**
     * {@inheritDoc}
     */
    public IceMediaStream createIceStream(int numComponents, int portBase, String streamName, Agent agent)
            throws IllegalArgumentException, IOException, BindException {
        if (numComponents < 1 || numComponents > 2)
            throw new IllegalArgumentException("Invalid numComponents value: " + numComponents);

        IceMediaStream stream = agent.createMediaStream(streamName);
        agent.createComponent(stream, portBase, portBase, portBase + 100);

        if (numComponents > 1) {
            agent.createComponent(stream, portBase + 1, portBase + 1, portBase + 101);
        }
        return stream;
    }
}
