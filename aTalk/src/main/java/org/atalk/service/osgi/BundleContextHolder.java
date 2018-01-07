/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.service.osgi;

import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;

/**
 *
 * @author Lyubomir Marinov
 */
public interface BundleContextHolder
{
	public void addBundleActivator(BundleActivator bundleActivator);

	public BundleContext getBundleContext();

	public void removeBundleActivator(BundleActivator bundleActivator);
}
