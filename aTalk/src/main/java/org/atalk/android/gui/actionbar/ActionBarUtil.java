/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.android.gui.actionbar;

import android.app.ActionBar;
import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.drawable.*;
import android.text.method.ScrollingMovementMethod;
import android.widget.ImageView;
import android.widget.TextView;

import org.atalk.android.R;
import org.atalk.android.gui.util.AndroidImageUtil;

import androidx.annotation.DrawableRes;
import timber.log.Timber;

/**
 * The <tt>ActionBarUtil</tt> provides utility methods for setting action bar avatar and display
 * name.
 *
 * @author Yana Stamcheva
 * @author Eng Chong Meng
 */
public class ActionBarUtil
{
    /**
     * The avatar drawable for display on ActionBar
     */
    private static LayerDrawable avatarDrawable;

    /**
     * Sets the action bar title for the given activity.
     *
     * @param activity the <tt>Activity</tt>, for which we set the action bar title
     * @param title the title string to set
     */
    public static void setTitle(Activity activity, CharSequence title)
    {
        ActionBar actionBar = activity.getActionBar();
        // Some activities don't have ActionBar
        if (actionBar != null) {
            TextView actionBarText = actionBar.getCustomView().findViewById(R.id.actionBarTitle);
            actionBarText.setText(title);
        }
    }

    /**
     * Sets the action bar subtitle for the given activity.
     *
     * @param activity the <tt>Activity</tt>, for which we set the action bar subtitle
     * @param subtitle the subtitle string to set
     */
    public static void setSubtitle(Activity activity, String subtitle)
    {
        ActionBar actionBar = activity.getActionBar();
        if (actionBar != null) {
            TextView statusText = actionBar.getCustomView().findViewById(R.id.actionBarStatus);
            statusText.setText(subtitle);
            statusText.setMovementMethod(new ScrollingMovementMethod());
        }
    }

    /**
     * Set the action bar status for the given activity.
     *
     * @param activity the <tt>Activity</tt>, for which we get the action bar title
     * @param statusIcon display Icon per the user status
     */
    public static void setStatus(Activity activity, byte[] statusIcon)
    {
        ActionBar actionBar = activity.getActionBar();
        if (actionBar != null) {
            Bitmap avatarStatusBmp = AndroidImageUtil.bitmapFromBytes(statusIcon);
            if (avatarStatusBmp != null) {
                ImageView actionBarStatus = actionBar.getCustomView().findViewById(R.id.globalStatusIcon);
                actionBarStatus.setImageBitmap(avatarStatusBmp);
            }
        }
    }

    /**
     * Gets the action bar title for the given activity.
     *
     * @param activity the <tt>Activity</tt>, for which we get the action bar title
     * @return the title string
     */
    public static String getStatus(Activity activity)
    {
        if (activity != null) {
            ActionBar actionBar = activity.getActionBar();
            // Some activities don't have ActionBar
            if (actionBar == null)
                return null;

            TextView actionBarText = actionBar.getCustomView().findViewById(R.id.actionBarStatus);
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
    public static void setAvatar(Activity activity, byte[] avatar)
    {
        // cmeng: always clear old avatar picture when pager scroll to different chat fragment
        // and invalidate Drawable for scrolled page to update Logo properly
        avatarDrawable = getDefaultAvatarDrawable(activity);

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
        // set Logo not supported prior API 14
        ActionBar actionBar = activity.getActionBar();
        if (actionBar != null) {
            actionBar.setLogo(avatarDrawable);
        }
    }

    public static void setAvatar(Activity activity, @DrawableRes int resId)
    {
        ActionBar actionBar = activity.getActionBar();
        if (actionBar != null) {
            actionBar.setLogo(resId);
        }
    }

    /**
     * Returns the default avatar {@link Drawable}
     *
     * @return the default avatar {@link Drawable}
     */
    private static LayerDrawable getDefaultAvatarDrawable(Activity activity)
    {
        return (LayerDrawable) activity.getResources().getDrawable(R.drawable.avatar_layer_drawable);
    }
}
