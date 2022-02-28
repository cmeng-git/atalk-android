/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.service.osgi;

import org.osgi.framework.BundleContext;

/**
 * Interface should be implemented by all <code>Fragments</code> that want to make use of OSGi and live inside
 * <code>OSGiActivities</code>. Methods {@link #start(BundleContext)} and {@link #stop(BundleContext)} are fired
 * automatically when OSGI context is available.
 *
 * @author Pawel Domas
 */
public interface OSGiUiPart
{
    /**
     * Fired when OSGI is started and the <code>bundleContext</code> is available.
     *
     * @param bundleContext the OSGI bundle context.
     */
    void start(BundleContext bundleContext)
            throws Exception;

    /**
     * Fired when parent <code>OSGiActivity</code> is being stopped or this fragment is being detached.
     *
     * @param bundleContext the OSGI bundle context.
     */
    void stop(BundleContext bundleContext)
            throws Exception;
}
