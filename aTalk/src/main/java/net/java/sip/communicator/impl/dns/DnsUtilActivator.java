/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
pHideExtendedAwayStatus * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may
 * obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for
 * the specific language governing permissions
 * and limitations under the License.
 */
package net.java.sip.communicator.impl.dns;

import net.java.sip.communicator.service.dns.CustomResolver;
import net.java.sip.communicator.service.netaddr.NetworkAddressManagerService;
import net.java.sip.communicator.service.netaddr.event.ChangeEvent;
import net.java.sip.communicator.service.netaddr.event.NetworkConfigurationChangeListener;
import net.java.sip.communicator.service.notification.NotificationService;
import net.java.sip.communicator.service.protocol.ProxyInfoX;
import net.java.sip.communicator.service.resources.ResourceManagementServiceUtils;
import net.java.sip.communicator.util.Logger;
import net.java.sip.communicator.util.ServiceUtils;

import org.atalk.android.aTalkApp;
import org.atalk.android.util.AndroidUsingExecLowPriority;
import org.atalk.android.util.AndroidUsingLinkProperties;
import org.atalk.service.configuration.ConfigurationService;
import org.atalk.service.resources.ResourceManagementService;
import org.atalk.util.StringUtils;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceEvent;
import org.osgi.framework.ServiceListener;
import org.xbill.DNS.ExtendedResolver;
import org.xbill.DNS.Lookup;
import org.xbill.DNS.Options;
import org.xbill.DNS.ResolverConfig;
import org.xbill.DNS.SimpleResolver;

import java.net.InetSocketAddress;

import de.measite.minidns.DNSClient;
import de.measite.minidns.dnsserverlookup.AndroidUsingExec;

/**
 * The DNS Util activator registers the DNSSEC resolver if enabled.
 *
 * @author Emil Ivov
 * @author Ingo Bauersachs
 */
public class DnsUtilActivator implements BundleActivator, ServiceListener
{
    /**
     * Class logger
     */
    private static final Logger logger = Logger.getLogger(DnsUtilActivator.class);

    /**
     * The name of the property that sets custom nameservers to use for all DNS lookups when
     * DNSSEC is enabled. Multiple servers are separated by a comma (,).
     */
    public static final String PNAME_DNSSEC_NAMESERVERS = "dns.DNSSEC_NAMESERVERS";

    private static ConfigurationService configurationService;
    private static NotificationService notificationService;
    private static ResourceManagementService resourceService;
    private static BundleContext bundleContext;

    /**
     * The address of the backup resolver we would use by default.
     */
    public static final String DEFAULT_BACKUP_RESOLVER = "backup-resolver.jitsi.net";

    /**
     * The name of the property that users may use to override the port of our backup DNS resolver.
     */
    public static final String PNAME_BACKUP_RESOLVER_PORT = "dns.BACKUP_RESOLVER_PORT";

    /**
     * The name of the property that users may use to override the IP address of our backup DNS
     * resolver. This is only used when the backup resolver name cannot be determined.
     */
    public static final String PNAME_BACKUP_RESOLVER_FALLBACK_IP = "dns.BACKUP_RESOLVER_FALLBACK_IP";

    /**
     * The default of the property that users may use to disable our backup DNS resolver.
     */
    public static final boolean PDEFAULT_BACKUP_RESOLVER_ENABLED = true;

    /**
     * The name of the property that users may use to disable our backup DNS resolver.
     */
    public static final String PNAME_BACKUP_RESOLVER_ENABLED = "dns.BACKUP_RESOLVER_ENABLED";

    /**
     * The name of the property that users may use to override the address of our backup DNS
     * resolver.
     */
    public static final String PNAME_BACKUP_RESOLVER = "dns.BACKUP_RESOLVER";

    /**
     * Calls <tt>Thread.setUncaughtExceptionHandler()</tt>
     *
     * @param context The execution context of the bundle being started (unused).
     * @throws Exception If this method throws an exception, this bundle is marked as stopped and the Framework
     * will remove this bundle's listeners, unregister all services registered by this
     * bundle, and release all services used by this bundle.
     */
    public void start(BundleContext context)
            throws Exception
    {
        logger.info("DNS service ... [STARTING]");
        bundleContext = context;
        context.addServiceListener(this);

        if (Logger.getLogger("org.xbill").isTraceEnabled())
            Options.set("verbose", "1");

        // Init miniDNS Resolver for android 21/23 requirements
        initResolver();

        // cmeng: disable packet logger to resource?
        Lookup.setPacketLogger(new DnsJavaLogger());

        // If dns is forced to go through a proxy then skip any further settings
        if (loadDNSProxyForward()) {
            return;
        }

        if (getConfigurationService().getBoolean(CustomResolver.PNAME_DNSSEC_RESOLVER_ENABLED,
                CustomResolver.PDEFAULT_DNSSEC_RESOLVER_ENABLED)) {
            bundleContext.registerService(CustomResolver.class.getName(),
                    new ConfigurableDnssecResolver(new ExtendedResolver()), null);
            logger.info("DnssecResolver ... [REGISTERED]");
        }
        else if (getConfigurationService().getBoolean(PNAME_BACKUP_RESOLVER_ENABLED, PDEFAULT_BACKUP_RESOLVER_ENABLED)) {
            bundleContext.registerService(CustomResolver.class.getName(), new ParallelResolverImpl(), null);
            logger.info("ParallelResolver ... [REGISTERED]");
        }
    }

    public static void initResolver()
    {
        DNSClient.removeDNSServerLookupMechanism(AndroidUsingExec.INSTANCE);
        DNSClient.addDnsServerLookupMechanism(AndroidUsingExecLowPriority.INSTANCE);
        DNSClient.addDnsServerLookupMechanism(new AndroidUsingLinkProperties(aTalkApp.getGlobalContext()));
    }

    /**
     * Checks settings and if needed load forwarding of dns to the server that is specified.
     *
     * @return whether loading was successful or <tt>false</tt> if it is not or was not enabled.
     */
    private static boolean loadDNSProxyForward()
    {
        if (getConfigurationService().getBoolean(ProxyInfoX.CONNECTION_PROXY_FORWARD_DNS_PROPERTY_NAME, false)) {
            try {
                // enabled forward of dns
                String serverAddress = (String) getConfigurationService().getProperty(
                        ProxyInfoX.CONNECTION_PROXY_FORWARD_DNS_ADDRESS_PROPERTY_NAME);
                if (StringUtils.isNullOrEmpty(serverAddress, true))
                    return false;

                int port = SimpleResolver.DEFAULT_PORT;
                try {
                    port = getConfigurationService().getInt(
                            ProxyInfoX.CONNECTION_PROXY_FORWARD_DNS_PORT_PROPERTY_NAME, SimpleResolver.DEFAULT_PORT);
                } catch (NumberFormatException ne) {
                    logger.error("Wrong port value", ne);
                }

                // initially created with localhost setting
                SimpleResolver sResolver = new SimpleResolver("0");
                // then set the desired address and port
                sResolver.setAddress(new InetSocketAddress(serverAddress, port));
                Lookup.setDefaultResolver(sResolver);
                return true;
            } catch (Throwable t) {
                logger.error("Creating simple forwarding resolver", t);
            }
        }
        return false;
    }

    /**
     * Listens when network is going from down to up and resets dns configuration.
     */
    private static class NetworkListener implements NetworkConfigurationChangeListener
    {
        /**
         * Fired when a change has occurred in the computer network configuration.
         *
         * @param event the change event.
         */
        public void configurationChanged(ChangeEvent event)
        {
            if (event.getType() == ChangeEvent.IFACE_UP || event.getType() == ChangeEvent.IFACE_DOWN
                    || event.getType() == ChangeEvent.DNS_CHANGE) {
                if (event.isInitial())
                    logDNSServers();
                else
                    reloadDnsResolverConfig();
            }
        }
    }

    /**
     * Reloads dns server configuration in the resolver.
     */
    public static void reloadDnsResolverConfig()
    {
        // reread system dns configuration
        ResolverConfig.refresh();
        logDNSServers();

        // now reset an eventually present custom resolver
        if (Lookup.getDefaultResolver() instanceof CustomResolver) {
            if (logger.isInfoEnabled()) {
                logger.info("Resetting custom resolver " + Lookup.getDefaultResolver().getClass().getSimpleName());
            }
            ((CustomResolver) Lookup.getDefaultResolver()).reset();
        }
        else {
            // or the default otherwise
            if (!loadDNSProxyForward())
                Lookup.refreshDefault();
        }
    }

    /**
     * Logs the currently configured dns servers.
     */
    private static void logDNSServers()
    {
        if (logger.isInfoEnabled()) {
            StringBuilder sb = new StringBuilder();
            sb.append("Loading or Reloading resolver config, ").append("default DNS servers are: ");
            ResolverConfig config = ResolverConfig.getCurrentConfig();
            if (config != null && config.servers() != null) {
                for (String s : config.servers()) {
                    sb.append(s);
                    sb.append(", ");
                }
            }
            else {
                sb.append("undefined");
            }
            logger.info(sb.toString());
        }
    }

    /**
     * Doesn't do anything.
     *
     * @param context The execution context of the bundle being stopped.
     * @throws Exception If this method throws an exception, the bundle is still marked as stopped, and the
     * Framework will remove the bundle's listeners, unregister all services registered by
     * the bundle, and release all services used by the bundle.
     */
    public void stop(BundleContext context)
            throws Exception
    {
    }

    /**
     * Returns the <tt>ConfigurationService</tt> obtained from the bundle context.
     *
     * @return the <tt>ConfigurationService</tt> obtained from the bundle context
     */
    public static ConfigurationService getConfigurationService()
    {
        if (configurationService == null) {
            configurationService = ServiceUtils.getService(bundleContext, ConfigurationService.class);
        }
        return configurationService;
    }

    /**
     * Returns the <tt>NotificationService</tt> obtained from the bundle context.
     *
     * @return the <tt>NotificationService</tt> obtained from the bundle context
     */
    public static NotificationService getNotificationService()
    {
        if (notificationService == null) {
            notificationService = ServiceUtils.getService(bundleContext, NotificationService.class);
        }
        return notificationService;
    }

    /**
     * Returns the service giving access to all application resources.
     *
     * @return the service giving access to all application resources.
     */
    public static ResourceManagementService getResources()
    {
        if (resourceService == null) {
            resourceService = ResourceManagementServiceUtils.getService(bundleContext);
        }
        return resourceService;
    }

    /**
     * Listens on OSGi service changes and registers a listener for network changes as soon as the
     * change-notification service is available
     */
    public void serviceChanged(ServiceEvent event)
    {
        if (event.getType() != ServiceEvent.REGISTERED) {
            return;
        }

        Object service = bundleContext.getService(event.getServiceReference());
        if (!(service instanceof NetworkAddressManagerService)) {
            return;
        }

        ((NetworkAddressManagerService) service).addNetworkConfigurationChangeListener(new NetworkListener());
    }
}
