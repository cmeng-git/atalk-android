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
package net.java.sip.communicator.impl.provdisc.mdns;

import net.java.sip.communicator.service.provdisc.AbstractProvisioningDiscoveryService;
import net.java.sip.communicator.service.provdisc.event.DiscoveryEvent;
import net.java.sip.communicator.service.provdisc.event.DiscoveryListener;

import timber.log.Timber;

/**
 * Class that uses mDNS to retrieve provisioning URL.
 *
 * @author Sebastien Vincent
 * @author Eng Chong Meng
 */
public class ProvisioningDiscoveryServiceMDNSImpl extends AbstractProvisioningDiscoveryService
        implements DiscoveryListener {
    /**
     * Name of the method used to retrieve provisioning URL.
     */
    private static final String METHOD_NAME = "Bonjour";

    /**
     * MDNS provisioning discover object.
     */
    private MDNSProvisioningDiscover discover = null;

    /**
     * Constructor.
     */
    public ProvisioningDiscoveryServiceMDNSImpl() {
        try {
            discover = new MDNSProvisioningDiscover();
            discover.addDiscoveryListener(this);
        } catch (Exception e) {
            Timber.w("Cannot create JmDNS instance: %s", e.getMessage());
        }
    }

    /**
     * Get the name of the method name used to retrieve provisioning URL.
     *
     * @return method name
     */
    @Override
    public String getMethodName() {
        return METHOD_NAME;
    }

    /**
     * Launch a discovery for a provisioning URL. This method is synchronous and
     * may block for some time. Note that you don't have to call
     * <code>startDiscovery</code> method prior to this one to retrieve URL.
     *
     * @return provisioning URL
     */
    @Override
    public String discoverURL() {
        if (discover != null) {
            return discover.discoverProvisioningURL();
        }
        return null;
    }

    /**
     * Launch a mDNS discovery for a provisioning URL.
     * This method is asynchronous, the response will be notified to any
     * <code>ProvisioningListener</code> registered.
     */
    @Override
    public void startDiscovery() {
        if (discover != null) {
            new Thread(discover).start();
        }
    }

    /**
     * Notify the provisioning URL.
     *
     * @param event provisioning event
     */
    public void notifyProvisioningURL(DiscoveryEvent event) {
        fireDiscoveryEvent(event);
    }
}
