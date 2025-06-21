/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.java.sip.communicator.service.netaddr;

import java.io.IOException;
import java.net.BindException;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;

import net.java.sip.communicator.service.netaddr.event.NetworkConfigurationChangeListener;

import org.ice4j.ice.Agent;
import org.ice4j.ice.IceMediaStream;
import org.ice4j.ice.harvest.StunCandidateHarvester;

/**
 * The NetworkAddressManagerService takes care of problems such as
 *
 * @author Emil Ivov
 * @author Eng Chong Meng
 */
public interface NetworkAddressManagerService {
    /**
     * The default number of binds that a <code>NetworkAddressManagerService</code> implementation
     * should execute in case a port is already bound to (each retry would be on a different port).
     */
    public static final int BIND_RETRIES_DEFAULT_VALUE = 50;

    /**
     * The name of the property containing number of binds that a
     * <code>NetworkAddressManagerService</code> implementation should execute in case a port is
     * already bound to (each retry would be on a different port).
     */
    public static final String BIND_RETRIES_PROPERTY_NAME = "netaddr.BIND_RETRIES";

    /**
     * Returns an InetAddress instance that represents the localhost, and that a socket can bind
     * upon or distribute to peers as a contact address.
     * <p>
     * This method tries to make for the ambiguity in the implementation of the InetAddress
     * .getLocalHost() method.
     * (see <a href="http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=4665037">...</a>).
     * <p>
     * To put it briefly, the issue is about choosing a local source address to bind to or to
     * distribute to peers. It is possible and even quite probable to expect that a machine may
     * dispose with multiple addresses and each of them may be valid for a specific destination.
     * Example cases include:
     * <p>
     * 1) A dual stack IPv6/IPv4 box. <br>
     * 2) A double NIC box with a leg on the Internet and another one in a private LAN <br>
     * 3) In the presence of a virtual interface over a VPN or a MobileIP(v6) tunnel.
     * <p>
     * In all such cases a source local address needs to be chosen according to the intended
     * destination and after consulting the local routing table.
     *
     * @param intendedDestination the address of the destination that we'd like to access through
     * the local address that we are requesting.
     *
     * @return an InetAddress instance representing the local host, and that a socket can bind
     * upon or distribute to peers as a contact address.
     */
    InetAddress getLocalHost(InetAddress intendedDestination);

    /**
     * Tries to obtain a mapped/public address for the specified port. If the STUN lib fails,
     * tries to retrieve localhost, if that fails too, returns null.
     *
     * @param intendedDestination the destination that we'd like to use this address with.
     * @param port the port whose mapping we are interested in.
     *
     * @return a public address corresponding to the specified port or null if all attempts to
     * retrieve such an address have failed.
     *
     * @throws IOException if an error occurs while the underlying resolver lib is using sockets.
     * @throws BindException if the port is already in use.
     */
    InetSocketAddress getPublicAddressFor(InetAddress intendedDestination, int port)
            throws IOException, BindException;

    /**
     * Returns the hardware address (i.e. MAC address) of the specified interface name.
     *
     * @param iface the <code>NetworkInterface</code>
     *
     * @return array of bytes representing the layer 2 address
     */
    byte[] getHardwareAddress(NetworkInterface iface);

    /**
     * Creates a <code>DatagramSocket</code> and binds it to on the specified <code>localAddress</code>
     * and a port in the range specified by the <code>minPort</code> and <code>maxPort</code> parameters.
     * We first try to bind the newly created socket on the <code>preferredPort</code> port number and
     * then proceed incrementally upwards until we succeed or reach the bind retries limit. If we
     * reach the <code>maxPort</code> port number before the bind retries limit, we will then start
     * over again at <code>minPort</code> and keep going until we run out of retries.
     *
     * @param laddr the address that we'd like to bind the socket on.
     * @param preferredPort the port number that we should try to bind to first.
     * @param minPort the port number where we should first try to bind before moving to the next
     * one (i.e. <code>minPort + 1</code>)
     * @param maxPort the maximum port number where we should try binding before giving up and throwing an exception.
     *
     * @return the newly created <code>DatagramSocket</code>.
     *
     * @throws IllegalArgumentException if either <code>minPort</code> or <code>maxPort</code> is not a valid port number.
     * @throws IOException if an error occurs while the underlying resolver lib is using sockets.
     * @throws BindException if we couldn't find a free port between <code>minPort</code> and
     * <code>maxPort</code> before reaching the maximum allowed number of retries.
     */
    DatagramSocket createDatagramSocket(InetAddress laddr, int preferredPort, int minPort, int maxPort)
            throws IllegalArgumentException, IOException, BindException;

    /**
     * Adds new <code>NetworkConfigurationChangeListener</code> which will be informed for network configuration changes.
     *
     * @param listener the listener.
     */
    void addNetworkConfigurationChangeListener(NetworkConfigurationChangeListener listener);

    /**
     * Remove <code>NetworkConfigurationChangeListener</code>.
     *
     * @param listener the listener.
     */
    void removeNetworkConfigurationChangeListener(NetworkConfigurationChangeListener listener);

    /**
     * Creates and returns an ICE agent that a protocol could use for the negotiation of media
     * transport addresses. One ICE agent should only be used for a single session negotiation.
     *
     * @return the newly created ICE Agent.
     */
    Agent createIceAgent();

    /**
     * Tries to discover a TURN or a STUN server for the specified <code>domainName</code>. The
     * method would first try to discover a TURN server and then fall back to STUN only. In both
     * cases we would only care about a UDP transport.
     *
     * @param domainName the domain name that we are trying to discover a TURN server for.
     * @param userName the name of the user we'd like to use when connecting to a TURN server (we
     * won't be using credentials in case we only have a STUN server).
     * @param password the password that we'd like to try when connecting to a TURN server (we
     * won't be using credentials in case we only have a STUN server).
     *
     * @return A {@link StunCandidateHarvester} corresponding to the TURN or STUN server we
     * discovered or <code>null</code> if there were no such records for the specified <code>domainName</code>
     */
    StunCandidateHarvester discoverStunServer(String domainName, byte[] userName, byte[] password);

    /**
     * Creates an <code>IceMediaStream</code> and adds to it an RTP and and RTCP component, which
     * also implies running the currently installed harvesters so that they would.
     *
     * @param rtpPort the port that we should try to bind the RTP component on (the RTCP one
     * would automatically go to rtpPort + 1)
     * @param streamName the name of the stream to create
     * @param agent the <code>Agent</code> that should create the stream.
     *
     * @return the newly created <code>IceMediaStream</code>.
     *
     * @throws IllegalArgumentException if <code>rtpPort</code> is not a valid port number.
     * @throws IOException if an error occurs while the underlying resolver is using sockets.
     * @throws BindException if we couldn't find a free port between within the default number of retries.
     */
    IceMediaStream createIceStream(int rtpPort, String streamName, Agent agent)
            throws IllegalArgumentException, IOException, BindException;

    /**
     * Creates an <code>IceMediaStream</code> and adds to it one or two components, which also
     * implies running the currently installed harvesters.
     *
     * @param portBase the port that we should try to bind first component on (the second one
     * would automatically go to portBase + 1)
     * @param streamName the name of the stream to create
     * @param agent the <code>Agent</code> that should create the stream.
     *
     * @return the newly created <code>IceMediaStream</code>.
     *
     * @throws IllegalArgumentException if <code>portBase</code> is not a valid port number. If
     * <code>numComponents</code> is neither 1 nor 2.
     * @throws IOException if an error occurs while the underlying resolver is using sockets.
     * @throws BindException if we couldn't find a free port between within the default number of retries.
     */
    IceMediaStream createIceStream(int numComponents, int portBase, String streamName, Agent agent)
            throws IllegalArgumentException, IOException, BindException;
}
