/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.android.gui.util;

import android.app.ActionBar;
import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.drawable.*;
import android.text.method.ScrollingMovementMethod;
import android.widget.ImageView;
import android.widget.TextView;

import net.java.sip.communicator.util.Logger;

import org.atalk.android.R;

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
     * The logger
     */
    private final static Logger logger = Logger.getLogger(ActionBarUtil.class);

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
                TextView actionBarText = actionBar.getCustomView().findViewById(R.id.actionBarText);
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
            TextView statusText = actionBar.getCustomView().findViewById(R.id.actionBarStatusText);
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

            TextView actionBarText = actionBar.getCustomView().findViewById(R.id.actionBarStatusText);
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
        avatarDrawable.invalidateDrawable(avatarDrawable);

        BitmapDrawable avatarBmp = null;
        if (avatar != null) {
            if (avatar.length < 256 * 1024) {
                avatarBmp = AndroidImageUtil.roundedDrawableFromBytes(avatar);
            }
            else {
                logger.error("Avatar image is too large: " + avatar.length);
            }
            if (avatarBmp != null) {
                avatarDrawable.setDrawableByLayerId(R.id.avatarDrawable, avatarBmp);
            }
            else {
                logger.error("Failed to get avatar drawable from bytes");
            }
        }
        // set Logo not supported prior API 14
        ActionBar actionBar = activity.getActionBar();
        if (actionBar != null) {
            actionBar.setLogo(avatarDrawable);
        }
    }

    /**
     * Returns the default avatar {@link Drawable}
     *
     * @return the default avatar {@link Drawable}
     */
    @SuppressWarnings("deprecation")
    private static LayerDrawable getDefaultAvatarDrawable(Activity activity)
    {
        //return (LayerDrawable) aTalkApplication.getAppResources()
        return (LayerDrawable) activity.getResources().getDrawable(R.drawable.avatar_layer_drawable);
    }
}
