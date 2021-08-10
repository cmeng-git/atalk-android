/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.androidupdate;

import net.java.sip.communicator.service.update.UpdateService;
import net.java.sip.communicator.util.ServiceUtils;
import net.java.sip.communicator.util.SimpleServiceActivator;

import org.atalk.service.configuration.ConfigurationService;
import org.osgi.framework.BundleContext;

/**
 * Android update service activator.
 *
 * @author Pawel Domas
 */
public class UpdateActivator extends SimpleServiceActivator<UpdateService>
{
    /**
     * <tt>BundleContext</tt> instance.
     */
    static BundleContext bundleContext;

    /**
     * Creates new instance of <tt>UpdateActivator</tt>.
     */
    public UpdateActivator()
    {
        super(UpdateService.class, "Android update service");
    }

    /**
     * Gets the <tt>ConfigurationService</tt> using current <tt>BundleContext</tt>.
     *
     * @return the <tt>ConfigurationService</tt>
     */
    public static ConfigurationService getConfiguration()
    {
        return ServiceUtils.getService(bundleContext, ConfigurationService.class);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected UpdateService createServiceImpl()
    {
        return new UpdateServiceImpl();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void start(BundleContext bundleContext)
            throws Exception
    {
        UpdateActivator.bundleContext = bundleContext;
        super.start(bundleContext);
        ((UpdateServiceImpl) serviceImpl).removeOldDownloads();
    }
}
