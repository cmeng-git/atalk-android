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
package net.java.sip.communicator.impl.netaddr;

import net.java.sip.communicator.service.netaddr.NetworkAddressManagerService;
import net.java.sip.communicator.util.ServiceUtils;

import org.atalk.service.configuration.ConfigurationService;
import org.ice4j.ice.harvest.MappingCandidateHarvesters;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;

import timber.log.Timber;

/**
 * The activator manage the the bundles between OSGi framework and the Network address manager
 *
 * @author Emil Ivov
 * @author Eng Chong Meng
 */
public class NetaddrActivator implements BundleActivator
{
    /**
     * The OSGi bundle context.
     */
    private static BundleContext bundleContext = null;

    /**
     * The network address manager implementation.
     */
    private NetworkAddressManagerServiceImpl networkAMS = null;

    /**
     * The configuration service.
     */
    private static ConfigurationService configurationService = null;

    /**
     * Creates a NetworkAddressManager, starts it, and registers it as a NetworkAddressManagerService.
     *
     * @param bundleContext OSGI bundle context
     * @throws Exception if starting the NetworkAddressManagerFails.
     */
    public void start(BundleContext bundleContext)
            throws Exception
    {
        //in here we load static properties that should be else where
        //System.setProperty("java.net.preferIPv4Stack", "false");
        //System.setProperty("java.net.preferIPv6Addresses", "true");

        // cmeng: ice4j 2.0.0 settings for aTalk - must set this to true otherwise ice4j hangs
        System.setProperty(MappingCandidateHarvesters.DISABLE_AWS_HARVESTER_PNAME, "true");
        //end ugly property set

        //keep a reference to the bundle context for later usage.
        NetaddrActivator.bundleContext = bundleContext;

        //Create and start the network address manager.
        networkAMS = new NetworkAddressManagerServiceImpl();

        // give references to the NetworkAddressManager implementation
        networkAMS.start();

        bundleContext.registerService(NetworkAddressManagerService.class.getName(), networkAMS, null);
        Timber.i("Network Address Manager ICE Service ...[REGISTERED]");
    }

    /**
     * Returns a reference to a ConfigurationService implementation currently
     * registered in the bundle context or null if no such implementation was
     * found.
     *
     * @return a currently valid implementation of the ConfigurationService.
     */
    public static ConfigurationService getConfigurationService()
    {
        if (configurationService == null) {
            configurationService = ServiceUtils.getService(bundleContext, ConfigurationService.class);
        }
        return configurationService;
    }

    /**
     * Stops the Network Address Manager bundle
     *
     * @param bundleContext the OSGI bundle context
     */
    public void stop(BundleContext bundleContext)
    {
        if (networkAMS != null)
            networkAMS.stop();
        Timber.d("Network Address Manager Service ...[STOPPED]");

        configurationService = null;
    }

    /**
     * Returns a reference to the bundle context that we were started with.
     *
     * @return a reference to the BundleContext instance that we were started
     * with.
     */
    static BundleContext getBundleContext()
    {
        return bundleContext;
    }
}
