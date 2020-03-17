package org.atalk.android;

import android.content.Context;
import android.widget.ImageView;

import com.bumptech.glide.annotation.GlideModule;
import com.bumptech.glide.module.AppGlideModule;

import org.atalk.persistance.FileBackend;

import java.io.File;

@GlideModule
public class MyGlideApp extends AppGlideModule
{
    /**
     * Display file as thumbnail preview if it is a media file
     *
     * @param viewHolder image preview holder
     * @param file the image file
     * @param isHistory History file image view is only a small preview
     * @param isTypeMedia True if pre-determined mimeType from caller is a media file
     */
    public static void loadImage(ImageView viewHolder, File file, Boolean isHistory)
    {
        if (!file.exists()) {
            viewHolder.setImageDrawable(null);
            return;
        }

        Context ctx = aTalkApp.getGlobalContext();
        if (FileBackend.isMediaFile(file)) {
            // History file image view is only a small preview (192 px max height)
            if (isHistory) {
                GlideApp.with(ctx)
                        .load(file)
                        .override(640, 192)
                        .placeholder(R.drawable.ic_file_open)
                        .into(viewHolder);
            }
            // sent or received file will be large image
            else {
                GlideApp.with(ctx)
                        .load(file)
                        .override(1280, 608)
                        .error(R.drawable.ic_file_open)
                        .into(viewHolder);
            }
        }
        else {
            viewHolder.setImageResource(R.drawable.ic_file_open);
        }
    }
}
