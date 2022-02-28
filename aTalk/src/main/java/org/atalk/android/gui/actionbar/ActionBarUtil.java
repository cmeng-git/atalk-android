/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.android.gui.actionbar;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.drawable.*;
import android.text.method.ScrollingMovementMethod;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.res.ResourcesCompat;

import org.atalk.android.R;
import org.atalk.android.gui.util.AndroidImageUtil;

import timber.log.Timber;

/**
 * The <code>ActionBarUtil</code> provides utility methods for setting action bar avatar and display name.
 *
 * @author Yana Stamcheva
 * @author Eng Chong Meng
 */
public class ActionBarUtil
{

    /**
     * Sets the action bar title for the given activity.
     *
     * @param activity the <code>Activity</code>, for which we set the action bar title
     * @param title the title string to set
     */
    public static void setTitle(AppCompatActivity activity, CharSequence title)
    {
        ActionBar actionBar = activity.getSupportActionBar();
        // Some activities don't have ActionBar
        if (actionBar != null) {
            if (actionBar.getCustomView() != null) {
                TextView actionBarText = activity.findViewById(R.id.actionBarTitle);
                if (actionBarText != null)
                    actionBarText.setText(title);
            }
            else
                actionBar.setTitle(title);
        }
    }

    /**
     * Sets the action bar subtitle for the given activity.
     *
     * @param activity the <code>Activity</code>, for which we set the action bar subtitle
     * @param subtitle the subtitle string to set
     */
    public static void setSubtitle(AppCompatActivity activity, String subtitle)
    {
        ActionBar actionBar = activity.getSupportActionBar();
        if (actionBar != null) {
            TextView statusText = activity.findViewById(R.id.actionBarStatus);
            // statusText is null while search option is selected
            if (statusText != null) {
                statusText.setText(subtitle);
                statusText.setMovementMethod(new ScrollingMovementMethod());
            }
        }
    }

    /**
     * Set the action bar status for the given activity.
     *
     * @param activity the <code>Activity</code>, for which we get the action bar title
     * @param statusIcon display Icon per the user status
     */
    public static void setStatus(AppCompatActivity activity, byte[] statusIcon)
    {
        ActionBar actionBar = activity.getSupportActionBar();
        if (actionBar != null) {
            Bitmap avatarStatusBmp = AndroidImageUtil.bitmapFromBytes(statusIcon);
            if (avatarStatusBmp != null) {
                ImageView actionBarStatus = activity.findViewById(R.id.globalStatusIcon);
                // actionBarStatus is null while search option is selected
                if (actionBarStatus != null)
                    actionBarStatus.setImageBitmap(avatarStatusBmp);
            }
        }
    }

    /**
     * Gets the action bar title for the given activity.
     *
     * @param activity the <code>Activity</code>, for which we get the action bar title
     * @return the title string
     */
    public static String getStatus(AppCompatActivity activity)
    {
        if (activity != null) {
            ActionBar actionBar = activity.getSupportActionBar();
            // Some activities don't have ActionBar
            if (actionBar == null)
                return null;

            TextView actionBarText = activity.findViewById(R.id.actionBarStatus);
            return (actionBarText.getText().toString());
        }
        return null;
    }

    /**
     * Sets the avatar icon of the action bar.
     *
     * @param activity the current activity where the status should be displayed
     * @param avatar the avatar to display
     */
    public static void setAvatar(AppCompatActivity activity, byte[] avatar)
    {
        // The default avatar drawable for display on ActionBar
        LayerDrawable avatarDrawable = getDefaultAvatarDrawable(activity);

        // cmeng: always clear old avatar picture when pager scroll to different chat fragment
        // and invalidate Drawable for scrolled page to update Logo properly
        // cmeng: 20200312: seems no necessary anymore? so disable it seems ok now
        // avatarDrawable.invalidateDrawable(avatarDrawable);

        BitmapDrawable avatarBmp = null;
        if (avatar != null) {
            if (avatar.length < 256 * 1024) {
                avatarBmp = AndroidImageUtil.roundedDrawableFromBytes(avatar);
            }
            else {
                Timber.e("Avatar image is too large: %s", avatar.length);
            }
            if (avatarBmp != null) {
                avatarDrawable.setDrawableByLayerId(R.id.avatarDrawable, avatarBmp);
            }
            else {
                Timber.e("Failed to get avatar drawable from bytes");
            }
        }
        // set Logo is only available when there is no customView attached or during search
        ActionBar actionBar = activity.getSupportActionBar();
        if (actionBar != null) {
            if (actionBar.getCustomView() == null)
                actionBar.setLogo(avatarDrawable);
            else {
                ImageView logo = activity.findViewById(R.id.logo);
                if (logo != null)
                    logo.setImageDrawable(avatarDrawable);
            }
        }
    }

    public static void setAvatar(AppCompatActivity activity, @DrawableRes int resId)
    {
        ActionBar actionBar = activity.getSupportActionBar();
        if (actionBar != null) {
            if (actionBar.getCustomView() == null)
                actionBar.setLogo(resId);
            else {
                ImageView logo = activity.findViewById(R.id.logo);
                if (logo != null)
                    logo.setImageResource(resId);
            }
        }
    }

    /**
     * Returns the default avatar {@link Drawable}
     *
     * @return the default avatar {@link Drawable}
     */
    private static LayerDrawable getDefaultAvatarDrawable(AppCompatActivity activity)
    {
        Resources res = activity.getResources();
        return (LayerDrawable) ResourcesCompat.getDrawable(res, R.drawable.avatar_layer_drawable, null);
    }
}
