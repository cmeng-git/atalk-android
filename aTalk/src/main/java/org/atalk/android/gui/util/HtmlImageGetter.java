/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.android.gui.util;

import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.text.Html;

import net.java.sip.communicator.util.Logger;

import org.atalk.android.aTalkApp;

import timber.log.Timber;

/**
 * Utility class that implements <tt>Html.ImageGetter</tt> interface and can be used to display images in
 * <tt>TextView</tt> through the HTML syntax.<br/>
 * Source image URI should be formatted as follows:<br/>
 * <br/>
 * atalk.resource://{Integer drawable id}, example: atalk.resource://2130837599 <br/>
 * <br/>
 * This format is used by Android <tt>ResourceManagementService</tt> to return image URLs.
 *
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
public class HtmlImageGetter implements Html.ImageGetter
{
	/**
	 * {@inheritDoc}
	 */
	@Override
	public Drawable getDrawable(String source)
	{
		try {
			// Image resource id is returned here in form:
			// atalk.resource://{Integer drawable id}
			// Example: atalk.resource://2130837599
			Integer resId = Integer.parseInt(source.substring(17));

			// Gets application global bitmap cache
			DrawableCache cache = aTalkApp.getImageCache();

			return cache.getBitmapFromMemCache(resId);
		}
		catch (IndexOutOfBoundsException e) {
			// Invalid string format for source.substring(17)
			Timber.e(e, "Error parsing: %s", source);
			return null;
		}
		catch (NumberFormatException e) {
			// Error parsing Integer.parseInt(source.substring(17))
			Timber.e(e, "Error parsing: %s", source);
			return null;
		}
		catch (Resources.NotFoundException e) {
			// Resource for given id is not found
			Timber.e(e, "Error parsing: %s", source);
			return null;
		}
	}
}
