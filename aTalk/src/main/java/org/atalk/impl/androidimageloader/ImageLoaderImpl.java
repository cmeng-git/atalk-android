/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.androidimageloader;

import java.util.HashMap;

import net.java.sip.communicator.service.gui.ImageLoaderService;
import net.java.sip.communicator.service.resources.ImageID;
import net.java.sip.communicator.util.ServiceUtils;

import org.atalk.android.gui.util.AndroidImageUtil;
import org.atalk.service.resources.ResourceManagementService;

import android.graphics.drawable.Drawable;

/**
 * Android <tt>ImageLoaderService</tt> implementation which uses <tt>ResourceManagementService</tt> to load the images.
 *
 * @author Pawel Domas
 */
public class ImageLoaderImpl implements ImageLoaderService<Drawable>
{
	/**
	 * Raw images data cache.
	 */
	private final HashMap<String, byte[]> rawCache = new HashMap<String, byte[]>();

	/**
	 * Drawable cache.
	 */
	private final HashMap<String, Drawable> drawableCache = new HashMap<String, Drawable>();

	/**
	 * {@inheritDoc}
	 */
	@Override
	public Drawable getImage(ImageID imageID)
	{
		if (!drawableCache.containsKey(imageID.getId())) {
			drawableCache.put(imageID.getId(), AndroidImageUtil.drawableFromBytes(getImageBytes(imageID)));
		}
		return drawableCache.get(imageID.getId());
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public byte[] getImageBytes(ImageID imageID)
	{
		if (!rawCache.containsKey(imageID.getId())) {
			ResourceManagementService rms = ServiceUtils.getService(ImageLoaderActivator.bundleContext, ResourceManagementService.class);
			rawCache.put(imageID.getId(), rms.getImageInBytes(imageID.getId()));
		}
		return rawCache.get(imageID.getId());
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void clearCache()
	{
		rawCache.clear();
		drawableCache.clear();
	}
}
