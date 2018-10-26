package org.atalk.android;

import android.content.Context;
import android.net.Uri;
import android.text.TextUtils;
import android.util.Log;
import android.widget.ImageView;

import com.bumptech.glide.annotation.GlideModule;
import com.bumptech.glide.module.AppGlideModule;

import org.atalk.persistance.FileBackend;

import java.io.File;

@GlideModule
public class MyGlideApp extends AppGlideModule
{
    private static String TAG = MyGlideApp.class.toString();

    public static void loadImage(ImageView viewHolder, File filePath, Boolean isHistory)
    {
        if (!filePath.exists()) {
            viewHolder.setImageDrawable(null);
            return;
        }

        Context ctx = aTalkApp.getGlobalContext();
        if (isMediaFile(ctx, filePath)) {
            // History file image view is only a small preview
            if (isHistory) {
                GlideApp.with(ctx)
                        .load(filePath)
                        .centerCrop()
                        .placeholder(R.drawable.ic_file_open)
                        .into(viewHolder);
            }
            // sent or received file will be full image
            else {
                GlideApp.with(ctx)
                        .load(filePath)
                        .error(R.drawable.ic_file_open)
                        .into(viewHolder);
            }
        }
        else {
            viewHolder.setImageResource(R.drawable.ic_file_open);
        }
    }

    /*
     * Check if the file has media content
     */
    private static boolean isMediaFile(Context ctx, File file)
    {
        Uri uri = FileBackend.getUriForFile(ctx, file);
        String mimeType = FileBackend.getMimeType(ctx, uri);

        // mimeType is null if file contains no ext on old android or else "application/octet-stream"
        if (TextUtils.isEmpty(mimeType)) {
            Log.e(TAG, "File mimeType is null: " + file.getPath());
            return false;
        }

        // Android return 3gp and vidoe/3gp
        if (!mimeType.contains("3gp") && (mimeType.contains("image") || mimeType.contains("video"))) {
            return true;
        }
        else {
            // Log.e(TAG, "File mimeType is " + file.getPath() + ": " + mimeType);
            return false;
        }
    }
}
