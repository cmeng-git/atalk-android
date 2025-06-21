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
package net.java.sip.communicator.impl.provdisc.dhcp;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Random;

import net.java.sip.communicator.service.netaddr.NetworkAddressManagerService;
import net.java.sip.communicator.service.provdisc.event.DiscoveryEvent;
import net.java.sip.communicator.service.provdisc.event.DiscoveryListener;

import org.dhcp4java.DHCPConstants;
import org.dhcp4java.DHCPOption;
import org.dhcp4java.DHCPPacket;

import timber.log.Timber;

/**
 * Class that will perform DHCP provisioning discovery.
 *
 * @author Sebastien Vincent
 * @author Eng Chong Meng
 */
public class DHCPProvisioningDiscover implements Runnable {
    /**
     * DHCP socket timeout (in milliseconds).
     */
    private static final int DHCP_TIMEOUT = 10000;

    /**
     * UDP socket.
     */
    private final DatagramSocket socket;

    /**
     * DHCP transaction number.
     */
    private final int xid;

    /**
     * Listening port of the client. Note that the socket will send packet to DHCP server on port - 1.
     */
    private final int mPort;

    /**
     * Option code of the specific provisioning option.
     */
    private final byte mOption;

    /**
     * List of <code>ProvisioningListener</code> that will be notified when a provisioning URL is retrieved.
     */
    private final List<DiscoveryListener> listeners = new ArrayList<>();

    /**
     * Constructor.
     *
     * @param port port on which we will bound and listen for DHCP response
     * @param option code of the specific provisioning option
     *
     * @throws Exception if anything goes wrong during initialization
     */
    public DHCPProvisioningDiscover(int port, byte option)
            throws Exception {
        mPort = port;
        mOption = option;
        xid = new Random().nextInt();

        socket = new DatagramSocket(port);

        /*
         * set timeout so that we will not blocked forever if we have no response from DHCP server
         */
        socket.setSoTimeout(DHCP_TIMEOUT);
    }

    /**
     * It sends a DHCPINFORM message from all interfaces and wait for a response. Thread stops
     * after first successful answer that contains specific option and thus the provisioning URL.
     *
     * @return provisioning URL
     */
    public String discoverProvisioningURL() {
        DHCPPacket inform = new DHCPPacket();
        byte[] macAddress;
        byte[] zeroIPAddress = {0x00, 0x00, 0x00, 0x00};
        byte[] broadcastIPAddr = {(byte) 255, (byte) 255, (byte) 255, (byte) 255};
        DHCPOption[] dhcpOpts = new DHCPOption[1];
        List<DHCPTransaction> transactions = new ArrayList<DHCPTransaction>();

        try {
            inform.setOp(DHCPConstants.BOOTREQUEST);
            inform.setHtype(DHCPConstants.HTYPE_ETHER);
            inform.setHlen((byte) 6);
            inform.setHops((byte) 0);
            inform.setXid(xid);
            inform.setSecs((short) 0);
            inform.setFlags((short) 0);
            inform.setYiaddr(InetAddress.getByAddress(zeroIPAddress));
            inform.setSiaddr(InetAddress.getByAddress(zeroIPAddress));
            inform.setGiaddr(InetAddress.getByAddress(zeroIPAddress));
            // inform.setChaddr(macAddress);
            inform.setDhcp(true);
            inform.setDHCPMessageType(DHCPConstants.DHCPINFORM);

            dhcpOpts[0] = new DHCPOption(DHCPConstants.DHO_DHCP_PARAMETER_REQUEST_LIST, new byte[]{mOption});
            inform.setOptions(dhcpOpts);
            Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces();

            while (en.hasMoreElements()) {
                NetworkInterface iface = en.nextElement();
                Enumeration<InetAddress> enAddr = iface.getInetAddresses();
                while (enAddr.hasMoreElements()) {
                    InetAddress addr = enAddr.nextElement();

                    /* just take IPv4 address */
                    if (addr instanceof Inet4Address) {
                        NetworkAddressManagerService netaddr
                                = ProvisioningDiscoveryDHCPActivator.getNetworkAddressManagerService();

                        if (!addr.isLoopbackAddress()) {
                            macAddress = netaddr.getHardwareAddress(iface);
                            DHCPPacket p = inform.clone();

                            p.setCiaddr(addr);
                            p.setChaddr(macAddress);

                            byte[] msg = p.serialize();
                            DatagramPacket pkt = new DatagramPacket(msg,
                                    msg.length, InetAddress.getByAddress(broadcastIPAddr), mPort - 1);

                            DHCPTransaction transaction = new DHCPTransaction(socket, pkt);
                            transaction.schedule();
                            transactions.add(transaction);
                            msg = null;
                            pkt = null;
                            p = null;
                        }
                    }
                }
            }

            /*
             * now see if we receive DHCP ACK response and if it contains our custom option
             */
            boolean found = false;
            try {
                DatagramPacket pkt2 = new DatagramPacket(new byte[1500], 1500);

                while (!found) {
                    /* we timeout after some seconds if no DHCP response are received
                     */
                    socket.receive(pkt2);
                    DHCPPacket dhcp = DHCPPacket.getPacket(pkt2);
                    if (dhcp.getXid() != xid) {
                        continue;
                    }

                    /* notify */
                    DHCPOption optProvisioning = dhcp.getOption(mOption);
                    if (optProvisioning != null) {
                        found = true;
                        for (DHCPTransaction t : transactions) {
                            t.cancel();
                        }
                        return new String(optProvisioning.getValue());
                    }
                }
            } catch (SocketTimeoutException est) {
                Timber.w("Timeout, no DHCP answer received: %s", est.getMessage());
            }
        } catch (Exception e) {
            Timber.w("Exception occurred during DHCP discover: %s", e.getMessage());
        }

        for (DHCPTransaction t : transactions) {
            t.cancel();
        }
        return null;
    }

    /**
     * Thread entry point. It runs <code>discoverProvisioningURL</code> in a separate thread.
     */
    public void run() {
        String url = discoverProvisioningURL();

        if (url != null) {
            /* as we run in an asynchronous manner, notify the listener */
            DiscoveryEvent evt = new DiscoveryEvent(this, url);

            for (DiscoveryListener listener : listeners) {
                listener.notifyProvisioningURL(evt);
            }
        }
    }

    /**
     * Add a listener that will be notified when the <code>discoverProvisioningURL</code> has finished.
     *
     * @param listener <code>ProvisioningListener</code> to add
     */
    public void addDiscoveryListener(DiscoveryListener listener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    /**
     * Add a listener that will be notified when the <code>discoverProvisioningURL</code> has finished.
     *
     * @param listener <code>ProvisioningListener</code> to add
     */
    public void removeDiscoveryListener(DiscoveryListener listener) {
        listeners.remove(listener);
    }
}
