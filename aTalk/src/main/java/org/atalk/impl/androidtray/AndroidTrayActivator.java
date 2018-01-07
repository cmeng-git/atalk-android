/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.androidtray;

import net.java.sip.communicator.service.systray.SystrayService;
import net.java.sip.communicator.util.Logger;

import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;

/**
 * Android tray service activator.
 *
 * @author Pawel Domas
 */
public class AndroidTrayActivator implements BundleActivator
{
	/**
	 * The logger.
	 */
	private static final Logger logger = Logger.getLogger(AndroidTrayActivator.class);

	/**
	 * OSGI bundle context
	 */
	public static BundleContext bundleContext;

	/**
	 * <tt>SystrayServiceImpl</tt> instance.
	 */
	private SystrayServiceImpl systrayService;

	/**
	 * {@inheritDoc}
	 */
	public void start(BundleContext bundleContext)
		throws Exception
	{
		AndroidTrayActivator.bundleContext = bundleContext;

		// Create the notification service implementation
		this.systrayService = new SystrayServiceImpl();

		bundleContext.registerService(SystrayService.class.getName(), systrayService, null);
		systrayService.start();

		if (logger.isInfoEnabled())
			logger.info("Systray Service ...[REGISTERED]");
	}

	/**
	 * {@inheritDoc}
	 */
	public void stop(BundleContext bundleContext)
		throws Exception
	{
		systrayService.stop();
	}
}
