/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.appversion;

import net.java.sip.communicator.util.SimpleServiceActivator;

import org.atalk.service.version.VersionService;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;

/**
 * Android version service activator.
 *
 * @author Pawel Domas
 */
public class VersionActivator extends SimpleServiceActivator<VersionService> {
    /**
     * <code>BundleContext</code> instance.
     */
    public static BundleContext bundleContext;

    private static VersionService versionService = null;

    /**
     * Creates a new instance of <code>VersionActivator</code>.
     */
    public VersionActivator() {
        super(VersionService.class, "Android version");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void start(BundleContext bundleContext)
            throws Exception {
        VersionActivator.bundleContext = bundleContext;
        super.start(bundleContext);
    }

    /**
     * Returns the currently registered instance of version service.
     *
     * @return the current version service.
     */
    public static VersionService getVersionService() {
        if (versionService == null) {
            ServiceReference<?> confReference = bundleContext.getServiceReference(VersionService.class);
            versionService = (VersionService) bundleContext.getService(confReference);
        }
        return versionService;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected VersionService createServiceImpl() {
        return new VersionServiceImpl();
    }
}
