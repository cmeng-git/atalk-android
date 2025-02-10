/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.protocol.media;

import net.java.sip.communicator.service.netaddr.NetworkAddressManagerService;
import net.java.sip.communicator.util.ServiceUtils;

import org.atalk.service.configuration.ConfigurationService;
import org.atalk.service.neomedia.MediaService;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;

import timber.log.Timber;

/**
 * The activator doesn't really start anything as this service is mostly stateless, it's simply here
 * to allow us to obtain references to the services that we may need.
 *
 * @author Emil Ivov
 * @author Eng Chong Meng
 */
public class ProtocolMediaActivator implements BundleActivator {
    /**
     * Bundle context from OSGi.
     */
    static BundleContext bundleContext = null;

    /**
     * Configuration service.
     */
    private static ConfigurationService configurationService = null;

    /**
     * Media service.
     */
    private static MediaService mediaService = null;

    /**
     * A reference to the currently valid {@link NetworkAddressManagerService}.
     */
    private static NetworkAddressManagerService networkAddressManagerService = null;

    /**
     * Called when this bundle is started so the Framework can perform the bundle-specific
     * activities necessary to start this bundle.
     *
     * @param context The execution context of the bundle being started.
     *
     * @throws Exception If this method throws an exception, this bundle is marked as stopped and the
     * Framework will remove this bundle's listeners, unregister all services registered by
     * this bundle, and release all services used by this bundle.
     */
    public void start(BundleContext context)
            throws Exception {
        ProtocolMediaActivator.bundleContext = context;
        Timber.i("Protocol Media Started.");
    }

    /**
     * Called when this bundle is stopped so the Framework can perform the bundle-specific
     * activities necessary to stop the bundle.
     *
     * @param context The execution context of the bundle being stopped.
     *
     * @throws Exception If this method throws an exception, the bundle is still marked as stopped, and the
     * Framework will remove the bundle's listeners, unregister all services registered by
     * the bundle, and release all services used by the bundle.
     */
    public void stop(BundleContext context)
            throws Exception {
        configurationService = null;
        mediaService = null;
        networkAddressManagerService = null;
    }

    /**
     * Returns a reference to the bundle context that we were started with.
     *
     * @return a reference to the BundleContext instance that we were started with.
     */
    public static BundleContext getBundleContext() {
        return bundleContext;
    }

    /**
     * Returns a reference to a ConfigurationService implementation currently registered in the
     * bundle context or null if no such implementation was found.
     *
     * @return a currently valid implementation of the ConfigurationService.
     */
    public static ConfigurationService getConfigurationService() {
        if (configurationService == null) {
            configurationService = ServiceUtils.getService(bundleContext, ConfigurationService.class);
        }
        return configurationService;
    }

    /**
     * Returns a reference to a <code>MediaService</code> implementation currently registered in the
     * bundle context or null if no such implementation was found.
     *
     * @return a reference to a <code>MediaService</code> implementation currently registered in the
     * bundle context or null if no such implementation was found.
     */
    public static MediaService getMediaService() {
        if (mediaService == null) {
            mediaService = ServiceUtils.getService(bundleContext, MediaService.class);
        }
        return mediaService;
    }

    /**
     * Returns a reference to a NetworkAddressManagerService implementation currently registered in
     * the bundle context or null if no such implementation was found.
     *
     * @return a currently valid implementation of the <code>NetworkAddressManagerService</code>
     */
    public static NetworkAddressManagerService getNetworkAddressManagerService() {
        if (networkAddressManagerService == null) {
            networkAddressManagerService = ServiceUtils.getService(bundleContext, NetworkAddressManagerService.class);
        }
        return networkAddressManagerService;
    }
}
