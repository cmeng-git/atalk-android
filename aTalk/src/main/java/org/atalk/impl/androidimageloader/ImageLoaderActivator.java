/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.androidimageloader;

import net.java.sip.communicator.service.gui.ImageLoaderService;
import net.java.sip.communicator.util.SimpleServiceActivator;

import org.osgi.framework.BundleContext;

/**
 * Android image loader service activator.
 *
 * @author Pawel Domas
 */
public class ImageLoaderActivator extends SimpleServiceActivator<ImageLoaderImpl>
{
	/**
	 * OSGI bundle context
	 */
	static BundleContext bundleContext;

	/**
	 * {@inheritDoc}
	 */
	public ImageLoaderActivator() {
		super(ImageLoaderService.class, "Android image loader service");
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void start(BundleContext bundleContext)
		throws Exception
	{
		super.start(bundleContext);

		ImageLoaderActivator.bundleContext = bundleContext;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	protected ImageLoaderImpl createServiceImpl()
	{
		return new ImageLoaderImpl();
	}
}
