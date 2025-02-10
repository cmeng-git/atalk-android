/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.appstray;

import net.java.sip.communicator.service.systray.SystrayService;

import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;

import timber.log.Timber;

/**
 * Android tray service activator.
 *
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
public class AppTrayActivator implements BundleActivator {
    /**
     * OSGI bundle context
     */
    public static BundleContext bundleContext;

    /**
     * <code>SystrayServiceImpl</code> instance.
     */
    private SystrayServiceImpl systrayService;

    /**
     * {@inheritDoc}
     */
    public void start(BundleContext bundleContext)
            throws Exception {
        AppTrayActivator.bundleContext = bundleContext;

        // Create the notification service implementation
        this.systrayService = new SystrayServiceImpl();

        bundleContext.registerService(SystrayService.class.getName(), systrayService, null);
        systrayService.start();

        Timber.i("Systray Service ...[REGISTERED]");
    }

    /**
     * {@inheritDoc}
     */
    public void stop(BundleContext bundleContext)
            throws Exception {
        systrayService.stop();
    }
}
