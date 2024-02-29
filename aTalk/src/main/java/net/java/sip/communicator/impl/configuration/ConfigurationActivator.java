/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.impl.configuration;

import org.atalk.service.configuration.ConfigurationService;
import org.atalk.service.libjitsi.LibJitsi;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;

/**
 * @author Emil Ivov
 * @author Lyubomir Marinov
 */
public class ConfigurationActivator implements BundleActivator {
    /**
     * The <code>BundleContext</code> in which the configuration bundle has been started and has not been stopped yet.
     */
    public static BundleContext bundleContext;

    /**
     * Starts the configuration service
     *
     * @param bundleContext the <code>BundleContext</code> as provided by the OSGi framework.
     *
     * @throws Exception if anything goes wrong
     */
    public void start(BundleContext bundleContext)
            throws Exception {
        ConfigurationActivator.bundleContext = bundleContext;
        ConfigurationService configurationService = LibJitsi.getConfigurationService();

        configurationService.setProperty("protocol.sip.DESKTOP_STREAMING_DISABLED", "true");
        configurationService.setProperty("protocol.jabber.DESKTOP_STREAMING_DISABLED", "true");
        configurationService.setProperty("protocol.jabber.DISABLE_CUSTOM_DIGEST_MD5", "true");
        bundleContext.registerService(ConfigurationService.class.getName(), configurationService, null);
    }

    /**
     * Causes the configuration service to store the properties object and unregisters the
     * configuration service.
     *
     * @param bundleContext <code>BundleContext</code>
     *
     * @throws Exception if anything goes wrong while storing the properties managed by the
     * <code>ConfigurationService</code> implementation provided by this bundle and while
     * unregistering the service in question
     */
    public void stop(BundleContext bundleContext)
            throws Exception {
    }

    /**
     * Gets the <code>BundleContext</code> in which the configuration bundle has been started and has
     * not been stopped yet.
     *
     * @return the <code>BundleContext</code> in which the configuration bundle has been started and
     * has not been stopped yet
     */
    public static BundleContext getBundleContext() {
        return bundleContext;
    }
}
