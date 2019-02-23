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

import net.java.sip.communicator.service.provdisc.ProvisioningDiscoveryService;

import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;

import timber.log.Timber;

/**
 * Implements <tt>BundleActivator</tt> for the mDNS provisioning bundle.
 *
 * @author Sebastien Vincent
 * @author Eng Chong Meng
 */
public class ProvisioningDiscoveryMDNSActivator
        implements BundleActivator
{
    /**
     * MDNS provisioning service.
     */
    private static ProvisioningDiscoveryServiceMDNSImpl provisioningService =
            new ProvisioningDiscoveryServiceMDNSImpl();

    /**
     * Starts the mDNS provisioning service
     *
     * @param bundleContext the <tt>BundleContext</tt> as provided by the OSGi framework.
     * @throws Exception if anything goes wrong
     */
    public void start(BundleContext bundleContext)
            throws Exception
    {
        bundleContext.registerService(ProvisioningDiscoveryService.class.getName(),
                provisioningService, null);
        Timber.i("DNS provisioning discovery Service [REGISTERED]");
    }

    /**
     * Stops the mDNS provisioning service.
     *
     * @param bundleContext the <tt>BundleContext</tt> as provided by the OSGi framework.
     * @throws Exception if anything goes wrong
     */
    public void stop(BundleContext bundleContext)
            throws Exception
    {
        Timber.i("mDNS provisioning discovery Service ...[STOPPED]");
    }
}
