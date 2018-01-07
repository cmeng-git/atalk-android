/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.osgi;

import org.atalk.service.osgi.OSGiService;
import org.atalk.service.osgi.BundleContextHolder;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;

import android.content.ComponentName;
import android.content.Intent;

/**
 *
 * @author Lyubomir Marinov
 */
public class OSGiServiceActivator implements BundleActivator
{
	private BundleActivator bundleActivator;

	private OSGiService osgiService;

	public void start(BundleContext bundleContext)
		throws Exception
	{
		startService(bundleContext);
		startBundleContextHolder(bundleContext);
	}

	private void startBundleContextHolder(BundleContext bundleContext)
		throws Exception
	{
		ServiceReference<BundleContextHolder> serviceReference = bundleContext.getServiceReference(BundleContextHolder.class);

		if (serviceReference != null) {
			BundleContextHolder bundleContextHolder = bundleContext.getService(serviceReference);

			if (bundleContextHolder instanceof BundleActivator) {
				BundleActivator bundleActivator = (BundleActivator) bundleContextHolder;

				this.bundleActivator = bundleActivator;

				boolean started = false;

				try {
					bundleActivator.start(bundleContext);
					started = true;
				}
				finally {
					if (!started)
						this.bundleActivator = null;
				}
			}
		}
	}

	private void startService(BundleContext bundleContext)
		throws Exception
	{
		ServiceReference<OSGiService> serviceReference = bundleContext.getServiceReference(OSGiService.class);

		if (serviceReference != null) {
			OSGiService osgiService = bundleContext.getService(serviceReference);

			if (osgiService != null) {
				ComponentName componentName = osgiService.startService(new Intent(osgiService, OSGiService.class));

				if (componentName != null)
					this.osgiService = osgiService;
			}
		}
	}

	public void stop(BundleContext bundleContext)
		throws Exception
	{
		try {
			stopBundleContextHolder(bundleContext);
		}
		finally {
			stopService(bundleContext);
		}
	}

	private void stopBundleContextHolder(BundleContext bundleContext)
		throws Exception
	{
		if (bundleActivator != null) {
			try {
				bundleActivator.stop(bundleContext);
			}
			finally {
				bundleActivator = null;
			}
		}
	}

	private void stopService(BundleContext bundleContext)
		throws Exception
	{
		if (osgiService != null) {
			try {
				// Triggers service shutdown and removes the notification
				osgiService.stopForegroundService();
			}
			finally {
				osgiService = null;
			}
		}
	}
}
