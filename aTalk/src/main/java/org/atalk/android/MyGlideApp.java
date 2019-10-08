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
    public static void loadImage(ImageView viewHolder, File file, Boolean isHistory)
    {
        if (!file.exists()) {
            viewHolder.setImageDrawable(null);
            return;
        }

        Context ctx = aTalkApp.getGlobalContext();
        if (FileBackend.isMediaFile(file)) {
            // History file image view is only a small preview
            if (isHistory) {
                GlideApp.with(ctx)
                        .load(file)
                        .centerCrop()
                        .placeholder(R.drawable.ic_file_open)
                        .into(viewHolder);
            }
            // sent or received file will be full image
            else {
                GlideApp.with(ctx)
                        .load(file)
                        .error(R.drawable.ic_file_open)
                        .into(viewHolder);
            }
        }
        else {
            viewHolder.setImageResource(R.drawable.ic_file_open);
        }
    }
}
