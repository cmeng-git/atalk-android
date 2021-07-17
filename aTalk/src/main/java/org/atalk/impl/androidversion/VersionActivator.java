/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.androidversion;

import net.java.sip.communicator.util.SimpleServiceActivator;

import org.atalk.service.version.VersionService;
import org.osgi.framework.BundleContext;

/**
 * Android version service activator.
 *
 * @author Pawel Domas
 */
public class VersionActivator extends SimpleServiceActivator<VersionService>
{
    /**
     * <tt>BundleContext</tt> instance.
     */
    public static BundleContext bundleContext;

    /**
     * Creates a new instance of <tt>VersionActivator</tt>.
     */
    public VersionActivator()
    {
        super(VersionService.class, "Android version");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void start(BundleContext bundleContext)
            throws Exception
    {
        VersionActivator.bundleContext = bundleContext;
        super.start(bundleContext);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected VersionService createServiceImpl()
    {
        return new VersionServiceImpl();
    }
}
