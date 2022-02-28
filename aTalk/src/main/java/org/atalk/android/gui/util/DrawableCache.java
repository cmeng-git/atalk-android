/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.android.gui.util;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;

import org.atalk.android.aTalkApp;

import androidx.collection.LruCache;

/**
 * Implements bitmap cache using <code>LruCache</code> utility class. Single cache instance uses up to 1/8 of total runtime memory available.
 *
 * @author Pawel Domas
 */
public class DrawableCache
{
    // TODO: there is no LruCache prior API 12
    /**
     * The cache
     */
    private LruCache<String, BitmapDrawable> cache;

    /**
     * Creates new instance of <code>DrawableCache</code>.
     */
    public DrawableCache()
    {
        // Get max available VM memory, exceeding this amount will throw an OutOfMemory exception. Stored in kilobytes as LruCache takes an
        // int in its constructor.
        final int maxMemory = (int) (Runtime.getRuntime().maxMemory() / 1024);

        // Use 1/8th of the available memory for this memory cache.
        final int cacheSize = maxMemory / 8;
        cache = new LruCache<String, BitmapDrawable>(cacheSize)
        {
            @Override
            protected int sizeOf(String key, BitmapDrawable value)
            {
                Bitmap bmp = value.getBitmap();
                int byteSize = bmp.getByteCount();
                return byteSize / 1024;
            }
        };
    }

    /**
     * Gets cached <code>BitmapDrawable</code> for given <code>resId</code>. If it doesn't exist in the cache it will be loaded and stored for later
     * use.
     *
     * @param resId bitmap drawable resource id(it must be bitmap resource)
     * @return <code>BitmapDrawable</code> for given <code>resId</code>
     * @throws Resources.NotFoundException if there's no bitmap for given <code>resId</code>
     */
    public BitmapDrawable getBitmapFromMemCache(Integer resId)
            throws Resources.NotFoundException
    {
        String key = "res:" + resId;
        // Check for cached bitmap
        BitmapDrawable img = cache.get(key);
        // Eventually loads the bitmap
        if (img == null) {
            // Load and store the bitmap
            Resources res = aTalkApp.getAppResources();
            Bitmap bmp = BitmapFactory.decodeResource(res, resId);
            img = new BitmapDrawable(res, bmp);
            img.setBounds(0, 0, img.getIntrinsicWidth(), img.getIntrinsicHeight());
            cache.put(key, img);
        }
        return cache.get(key);
    }

    /**
     * Gets bitmap from the cache.
     *
     * @param key drawable key string.
     * @return bitmap from the cache if it exists or <code>null</code> otherwise.
     */
    public BitmapDrawable getBitmapFromMemCache(String key)
    {
        return cache.get(key);
    }

    /**
     * Puts given <code>BitmapDrawable</code> to the cache.
     *
     * @param key drawable key string.
     * @param bmp the <code>BitmapDrawable</code> to be cached.
     */
    public void cacheImage(String key, BitmapDrawable bmp)
    {
        cache.put(key, bmp);
    }
}
