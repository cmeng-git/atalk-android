/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.android.gui.util;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;

import org.atalk.android.aTalkApp;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Class containing utility methods for Android's Displayable and Bitmap
 *
 * @author Eng Chong Meng
 */
public class AndroidImageUtil
{
    /**
     * Converts given array of bytes to {@link Bitmap}
     *
     * @param imageBlob array of bytes with raw image data
     * @return {@link Bitmap} created from <code>imageBlob</code>
     */
    static public Bitmap bitmapFromBytes(byte[] imageBlob)
    {
        if (imageBlob != null) {
            return BitmapFactory.decodeByteArray(imageBlob, 0, imageBlob.length);
        }
        return null;
    }

    /**
     * Creates the {@link Drawable} from raw image data
     *
     * @param imageBlob the array of bytes containing raw image data
     * @return the {@link Drawable} created from given <code>imageBlob</code>
     */
    static public Drawable drawableFromBytes(byte[] imageBlob)
    {
        Bitmap bmp = bitmapFromBytes(imageBlob);
        if (bmp == null)
            return null;

        return new BitmapDrawable(aTalkApp.getAppResources(), bmp);
    }

    /**
     * Creates a <code>Drawable</code> from the given image byte array and scales it to the given
     * <code>width</code> and <code>height</code>.
     *
     * @param imageBytes the raw image data
     * @param width the width to which to scale the image
     * @param height the height to which to scale the image
     * @return the newly created <code>Drawable</code>
     */
    static public Drawable scaledDrawableFromBytes(byte[] imageBytes, int width, int height)
    {
        Bitmap bmp = scaledBitmapFromBytes(imageBytes, width, height);
        if (bmp == null)
            return null;

        return new BitmapDrawable(aTalkApp.getAppResources(), bmp);
    }

    /**
     * Creates a <code>Bitmap</code> from the given image byte array and scales it to the given
     * <code>width</code> and <code>height</code>.
     *
     * @param imageBytes the raw image data
     * @param reqWidth the width to which to scale the image
     * @param reqHeight the height to which to scale the image
     * @return the newly created <code>Bitmap</code>
     */
    static public Bitmap scaledBitmapFromBytes(byte[] imageBytes, int reqWidth, int reqHeight)
    {
        // First decode with inJustDecodeBounds=true to check dimensions
        final BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length, options);

        // Calculate inSampleSize
        options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight);

        // Decode bitmap with inSampleSize set
        options.inJustDecodeBounds = false;
        return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length, options);
    }

    /**
     * Calculates <code>options.inSampleSize</code> for requested width and height.
     *
     * @param options the <code>Options</code> object that contains image <code>outWidth</code> and <code>outHeight</code>.
     * @param reqWidth requested width.
     * @param reqHeight requested height.
     * @return <code>options.inSampleSize</code> for requested width and height.
     */
    public static int calculateInSampleSize(BitmapFactory.Options options, int reqWidth, int reqHeight)
    {
        // Raw height and width of image
        final int height = options.outHeight;
        final int width = options.outWidth;
        int inSampleSize = 1;

        if (height > reqHeight || width > reqWidth) {

            final int halfHeight = height / 2;
            final int halfWidth = width / 2;

            // Calculate the largest inSampleSize value that is a power of 2
            // and keeps both height and width larger than the requested height
            // and width.
            while ((halfHeight / inSampleSize) > reqHeight
                    && (halfWidth / inSampleSize) > reqWidth) {
                inSampleSize *= 2;
            }
        }
        return inSampleSize;
    }

    /**
     * Decodes <code>Bitmap</code> identified by given <code>resId</code> scaled to requested width and height.
     *
     * @param res the <code>Resources</code> object.
     * @param resId bitmap resource id.
     * @param reqWidth requested width.
     * @param reqHeight requested height.
     * @return <code>Bitmap</code> identified by given <code>resId</code> scaled to requested width and height.
     */
    public static Bitmap scaledBitmapFromResource(Resources res, int resId, int reqWidth, int reqHeight)
    {
        // First decode with inJustDecodeBounds=true to check dimensions
        final BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeResource(res, resId, options);

        // Calculate inSampleSize
        options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight);

        // Decode bitmap with inSampleSize set
        options.inJustDecodeBounds = false;
        return BitmapFactory.decodeResource(res, resId, options);
    }

    /**
     * Reads <code>Bitmap</code> from given <code>uri</code> using <code>ContentResolver</code>. Output image is scaled
     * to given <code>reqWidth</code> and <code>reqHeight</code>. Output size is not guaranteed to match exact
     * given values, because only powers of 2 are used as scale factor. Algorithm tries to scale image down
     * as long as the output size stays larger than requested value.
     *
     * @param ctx the context used to create <code>ContentResolver</code>.
     * @param uri the <code>Uri</code> that points to the image.
     * @param reqWidth requested width.
     * @param reqHeight requested height.
     * @return <code>Bitmap</code> from given <code>uri</code> retrieved using <code>ContentResolver</code>
     * and down sampled as close as possible to match requested width and height.
     * @throws IOException
     */
    public static Bitmap scaledBitmapFromContentUri(Context ctx, Uri uri, int reqWidth, int reqHeight)
            throws IOException
    {
        InputStream imageStream = null;
        try {
            // First decode with inJustDecodeBounds=true to check dimensions
            imageStream = ctx.getContentResolver().openInputStream(uri);
            if (imageStream == null)
                return null;

            final BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeStream(imageStream, null, options);
            imageStream.close();

            // Calculate inSampleSize
            options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight);

            // Decode bitmap with inSampleSize set
            options.inJustDecodeBounds = false;
            imageStream = ctx.getContentResolver().openInputStream(uri);

            return BitmapFactory.decodeStream(imageStream, null, options);
        } finally {
            if (imageStream != null) {
                imageStream.close();
            }
        }
    }

    /**
     * Encodes given <code>Bitmap</code> to array of bytes using given compression <code>quality</code> in PNG format.
     *
     * @param bmp the bitmap to encode.
     * @param quality encoding quality in range 0-100.
     * @return raw bitmap data PNG encoded using given <code>quality</code>.
     */
    public static byte[] convertToBytes(Bitmap bmp, int quality)
    {
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        bmp.compress(Bitmap.CompressFormat.PNG, quality, stream);
        return stream.toByteArray();
    }

    /**
     * Loads an image from a given image identifier and return bytes of the image.
     *
     * @param imageID The identifier of the image i.e. R.drawable.
     * @return The image bytes for the given identifier.
     */
    public static byte[] getImageBytes(Context ctx, int imageID)
    {
        Bitmap bitmap = BitmapFactory.decodeResource(ctx.getResources(), imageID);
        return convertToBytes(bitmap, 100);
    }

    /**
     * Creates a <code>Bitmap</code> with rounded corners.
     *
     * @param bitmap the bitmap that will have it's corners rounded.
     * @param factor factor used to calculate corners radius based on width and height of the image.
     * @return a <code>Bitmap</code> with rounded corners created from given <code>bitmap</code>.
     */
    public static Bitmap getRoundedCornerBitmap(Bitmap bitmap, float factor)
    {
        Bitmap output = Bitmap.createBitmap(bitmap.getWidth(), bitmap.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(output);

        final int color = 0xff424242;
        final Paint paint = new Paint();
        final Rect rect = new Rect(0, 0, bitmap.getWidth(), bitmap.getHeight());
        final RectF rectF = new RectF(rect);

        paint.setAntiAlias(true);
        canvas.drawARGB(0, 0, 0, 0);
        paint.setColor(color);

        float rX = ((float) bitmap.getWidth() / 2); // * factor;
        float rY = ((float) bitmap.getHeight() / 2); // * factor ;
        // float r = (rX+rY)/2;

        //canvas.drawRoundRect(rectF, rX, rY, paint);
        canvas.drawCircle(rX, rY, rX, paint);

        paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_IN));
        canvas.drawBitmap(bitmap, rect, rect, paint);

        return output;
    }

    /**
     * Creates <code>BitmapDrawable</code> with rounded corners from raw image data.
     *
     * @param rawData raw bitmap data
     * @return <code>BitmapDrawable</code> with rounded corners from raw image data.
     */
    public static BitmapDrawable roundedDrawableFromBytes(byte[] rawData)
    {
        Bitmap bmp = bitmapFromBytes(rawData);
        if (bmp == null)
            return null;
        bmp = getRoundedCornerBitmap(bmp, 0.10f);
        return new BitmapDrawable(aTalkApp.getAppResources(), bmp);
    }

    /**
     * Creates a rounded corner scaled image.
     *
     * @param imageBytes The bytes of the image to be scaled.
     * @param width The maximum width of the scaled image.
     * @param height The maximum height of the scaled image.
     * @return The rounded corner scaled image.
     */
    public static Drawable getScaledRoundedIcon(byte[] imageBytes, int width, int height)
    {
        Bitmap bmp = getRoundedCornerBitmap(scaledBitmapFromBytes(imageBytes, width, height), 0.1f);
        return new BitmapDrawable(aTalkApp.getAppResources(), bmp);
    }
}
