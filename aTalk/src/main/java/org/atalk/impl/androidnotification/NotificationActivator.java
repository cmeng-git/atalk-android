/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.androidnotification;

import net.java.sip.communicator.service.notification.NotificationService;
import net.java.sip.communicator.util.Logger;

import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;

/**
 * Bundle adds Android specific notification handlers.
 *
 * @author Pawel Domas
 */
public class NotificationActivator implements BundleActivator
{
	/**
	 * The logger
	 */
	private final Logger logger = Logger.getLogger(NotificationActivator.class);

	/**
	 * OSGI bundle context.
	 */
	protected static BundleContext bundleContext;

	/**
	 * Notification service instance.
	 */
	private static NotificationService notificationService;

	/**
	 * Vibrate handler instance.
	 */
	private VibrateHandlerImpl vibrateHandler;

	/**
	 * {@inheritDoc}
	 */
	public void start(BundleContext bc)
		throws Exception
	{
		bundleContext = bc;
		try {
			logger.logEntry();
			// Get the notification service implementation
			ServiceReference notifyReference = bundleContext.getServiceReference(NotificationService.class.getName());

			notificationService = (NotificationService) bundleContext.getService(notifyReference);
			vibrateHandler = new VibrateHandlerImpl();
			notificationService.addActionHandler(vibrateHandler);
			logger.info("Android notification handler Service...[REGISTERED]");
		}
		finally {
			logger.logExit();
		}
	}

	/**
	 * {@inheritDoc}
	 */
	public void stop(BundleContext bc)
		throws Exception
	{
		notificationService.removeActionHandler(vibrateHandler.getActionType());
		logger.info("Android notification handler Service ...[STOPPED]");
	}
}
