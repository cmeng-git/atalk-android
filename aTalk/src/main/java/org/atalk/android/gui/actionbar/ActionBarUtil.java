/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.android.gui.actionbar;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
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
public class ActionBarUtil {
    /**
     * Sets the action bar title for the given activity.
     *
     * @param activity the <code>Activity</code>, for which we set the action bar title
     * @param title the title string to set
     */
    public static void setTitle(AppCompatActivity activity, CharSequence title) {
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

//    public static void setPrefTitle(AppCompatActivity activity, int resId) {
//        ActionBar actionBar = activity.getSupportActionBar();
//        String title = activity.getResources().getString(resId);
//
//        // Some activities don't have ActionBar
//        if (actionBar != null) {
//            if (actionBar.getCustomView() != null) {
//                TextView titleText = activity.findViewById(R.id.actionBarTitle);
//                if (titleText != null) {
//                    RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams) titleText.getLayoutParams();
//                    params.addRule(RelativeLayout.CENTER_IN_PARENT);
//                    titleText.setLayoutParams(params);
//
//                    titleText.setTextSize(Dimension.PX, 20f);
//                    titleText.setText(title);
//                }
//            }
//            else
//                actionBar.setTitle(title);
//        }
//    }

    /**
     * Sets the action bar subtitle for the given activity. The text may contain
     * a, Account user online status
     * b. The chat buddy last seen date or online status
     * c. Callee Jid during media call
     *
     * @param activity the <code>Activity</code>, for which we set the action bar subtitle
     * @param subtitle the subtitle string to set
     */
    public static void setSubtitle(AppCompatActivity activity, String subtitle) {
        ActionBar actionBar = activity.getSupportActionBar();
        if (actionBar != null) {
            TextView statusText = activity.findViewById(R.id.actionBarStatus);
            // statusText is null while search option is selected
            if (statusText != null) {
                statusText.setText(subtitle);
                // statusText.setMovementMethod(new ScrollingMovementMethod());
                // Must have setSelected() to get text to start scroll
                statusText.setSelected(true);
            }
        }
    }

    /**
     * Gets the action bar subTitle for the given activity.
     *
     * @param activity the <code>Activity</code>, for which we get the action bar title
     *
     * @return the title string
     */
    public static String getStatus(AppCompatActivity activity) {
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
     * Get the user offline status during the selected Locale.
     * Quiet messy to use this method as the user online status is being updated from multiple places
     * including server presence status sending etc.
     *
     * @param activity the caller context
     *
     * @return use online status
     */
    public static boolean isOffline(AppCompatActivity activity) {
        String offlineLabel = activity.getResources().getString(R.string.offline);
        return offlineLabel.equals(ActionBarUtil.getStatus(activity));
    }

    /**
     * Set the action bar status for the given activity.
     *
     * @param activity the <code>Activity</code>, for which we get the action bar title
     * @param statusIcon display Icon per the user status
     */
    public static void setStatusIcon(AppCompatActivity activity, byte[] statusIcon) {
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
     * Sets the avatar icon of the action bar.
     *
     * @param activity the current activity where the status should be displayed
     * @param avatar the avatar to display
     */
    public static void setAvatar(AppCompatActivity activity, byte[] avatar) {
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

    public static void setAvatar(AppCompatActivity activity, @DrawableRes int resId) {
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
    private static LayerDrawable getDefaultAvatarDrawable(AppCompatActivity activity) {
        Resources res = activity.getResources();
        return (LayerDrawable) ResourcesCompat.getDrawable(res, R.drawable.avatar_layer_drawable, null);
    }
}
