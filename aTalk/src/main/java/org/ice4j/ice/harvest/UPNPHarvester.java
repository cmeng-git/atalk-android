/*
 * ice4j, the OpenSource Java Solution for NAT and Firewall Traversal.
 *
 * Copyright @ 2015 Atlassian Pty Ltd
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
package org.ice4j.ice.harvest;

import androidx.annotation.NonNull;

import org.atalk.util.logging.Logger;
import org.bitlet.weupnp.GatewayDevice;
import org.bitlet.weupnp.GatewayDiscover;
import org.bitlet.weupnp.PortMappingEntry;
import org.ice4j.Transport;
import org.ice4j.TransportAddress;
import org.ice4j.ice.Component;
import org.ice4j.ice.ComponentSocket;
import org.ice4j.ice.HostCandidate;
import org.ice4j.ice.LocalCandidate;
import org.ice4j.ice.UPNPCandidate;
import org.ice4j.socket.IceSocketWrapper;
import org.ice4j.socket.IceUdpSocketWrapper;
import org.ice4j.socket.MultiplexingDatagramSocket;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;

/**
 * Implements a <code>CandidateHarvester</code> which gathers <code>Candidate</code>s
 * for a specified {@link Component} using UPnP.
 *
 * @author Sebastien Vincent
 * @author Eng Chong Meng
 */
public class UPNPHarvester extends AbstractCandidateHarvester
{
    /**
     * The logger.
     */
    private static final Logger logger = Logger.getLogger(UPNPHarvester.class.getName());

    /**
     * Maximum port to try to allocate.
     */
    private static final int MAX_RETRIES = 5;

    /**
     * The default gateway types to use in search
     * ST search field for WANIPConnection an WANPPPConnection
     */
    private static final String[] ST_IP_PPP = {
            "urn:schemas-upnp-org:service:WANIPConnection:1",
            "urn:schemas-upnp-org:service:WANPPPConnection:1"
    };

    /**
     * Synchronization object.
     */
    private final Object rootSync = new Object();

    /**
     * Gateway device.
     */
    private GatewayDevice device = null;

    /**
     * Number of UPnP discover threads that have finished.
     */
    private boolean threadStarted = false;

    /**
     * Gathers UPnP candidates for all host <code>Candidate</code>s that are already present in the specified
     * <code>component</code>. This method relies on the specified <code>component</code> to already contain
     * all its host candidates so that it would resolve them.
     *
     * @param component the {@link Component} that we'd like to gather candidate UPnP <code>Candidate</code>s for
     * @return the <code>LocalCandidate</code>s gathered by this <code>CandidateHarvester</code>
     */
    public synchronized Collection<LocalCandidate> harvest(Component component)
    {
        Collection<LocalCandidate> candidates = new HashSet<>();
        int retries = 0;

        logger.info("Begin UPnP harvesting! started: " + threadStarted + "; device: " + device);
        try {
            if (device == null) {
                // do it only once
                if (!threadStarted) {
                    try {
                        UPNPThread wanIP_PPPThread = new UPNPThread(ST_IP_PPP);
                        wanIP_PPPThread.start();
                        threadStarted = true;

                        synchronized (rootSync) {
                            rootSync.wait();
                        }

                        if (wanIP_PPPThread.getDevice() != null) {
                            device = wanIP_PPPThread.getDevice();
                        }
                    } catch (Throwable e) {
                        logger.warn("UPnP discovery failed: " + e.getMessage());
                    }
                }

                if (device == null) {
                    logger.warn("UPnP harvesting found zero device");
                    return candidates;
                }
            }

            InetAddress localAddress = device.getLocalAddress();
            String externalIPAddress = device.getExternalIPAddress();
            PortMappingEntry portMapping = new PortMappingEntry();

            IceSocketWrapper socket = new IceUdpSocketWrapper(
                    new MultiplexingDatagramSocket(0, localAddress));
            int port = socket.getLocalPort();
            int externalPort = socket.getLocalPort();

            while (retries < MAX_RETRIES) {
                if (!device.getSpecificPortMappingEntry(port, "UDP", portMapping)) {
                    if (device.addPortMapping(
                            externalPort,
                            port,
                            localAddress.getHostAddress(),
                            "UDP",
                            "ice4j.org: " + port)) {
                        List<LocalCandidate> cands = createUPNPCandidate(socket,
                                externalIPAddress, externalPort, component, device);

                        logger.info("Add UPnP port mapping: " +
                                externalIPAddress + " " + externalPort);

                        /* we have to add the UPNPCandidate and the base.
                         * if we don't add the base, we won't be able to add the peer
                         * reflexive candidate if someone contacts us on the UPNPCandidate
                         */
                        for (LocalCandidate cand : cands) {
                            //try to add the candidate to the component and then
                            //only add it to the harvest not redundant
                            if (component.addLocalCandidate(cand)) {
                                candidates.add(cand);
                            }
                        }
                        break;
                    }
                    else {
                        port++;
                    }
                }
                else {
                    port++;
                }
                retries++;
            }
        } catch (Throwable e) {
            logger.info("Exception while gathering UPnP candidates: " + e);
        }
        // logger.info("Harvested UPnP candidates: " + candidates);
        return candidates;
    }

    /**
     * Create a UPnP candidate.
     *
     * @param socket local socket
     * @param externalIP external IP address
     * @param port local port
     * @param component parent component
     * @param device the UPnP gateway device
     * @return a new <code>UPNPCandidate</code> instance which represents the specified <code>TransportAddress</code>
     */
    private List<LocalCandidate> createUPNPCandidate(IceSocketWrapper socket,
            String externalIP, int port, Component component, GatewayDevice device)
    {
        List<LocalCandidate> ret = new ArrayList<>();
        TransportAddress addr = new TransportAddress(externalIP, port, Transport.UDP);

        HostCandidate base = new HostCandidate(socket, component);

        UPNPCandidate candidate = new UPNPCandidate(addr, base, component, device);
        IceSocketWrapper stunSocket = candidate.getStunSocket(null);
        candidate.getStunStack().addSocket(stunSocket);
        ComponentSocket componentSocket = component.getComponentSocket();
        if (componentSocket != null) {
            componentSocket.add(candidate.getCandidateIceSocketWrapper());
        }

        ret.add(candidate);
        ret.add(base);

        return ret;
    }

    /**
     * UPnP discover thread.
     */
    private class UPNPThread extends Thread
    {
        /**
         * Gateway device.
         */
        private GatewayDevice device = null;

        /**
         * ST search field.
         */
        private final String[] st;

        /**
         * Constructor.
         *
         * @param st ST search field
         */
        public UPNPThread(String[] st)
        {
            this.st = st;
        }

        /**
         * Returns gateway device.
         *
         * @return gateway device
         */
        public GatewayDevice getDevice()
        {
            return device;
        }

        /**
         * Thread Entry point.
         */
        public void run()
        {
            try {
                GatewayDiscover gd = new GatewayDiscover(st);
                gd.discover();
                if (gd.getValidGateway() != null) {
                    device = gd.getValidGateway();
                }
            } catch (Throwable e) {
                logger.info("Failed to harvest UPnP: " + e);

                /*
                 * The Javadoc on ThreadDeath says: If ThreadDeath is caught by
                 * a method, it is important that it be rethrown so that the
                 * thread actually dies.
                 */
                if (e instanceof ThreadDeath)
                    throw (ThreadDeath) e;
            } finally {
                synchronized (rootSync) {
                    rootSync.notify();
                }
            }
        }
    }

    /**
     * Returns a <code>String</code> representation of this harvester containing its name.
     *
     * @return a <code>String</code> representation of this harvester containing its name.
     */
    @NonNull
    @Override
    public String toString()
    {
        return getClass().getSimpleName();
    }
}
