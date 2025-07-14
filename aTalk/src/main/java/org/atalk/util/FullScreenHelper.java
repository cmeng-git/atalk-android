package org.atalk.util;

import android.view.View;
import android.view.Window;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.fragment.app.FragmentActivity;

/**
 * Class responsible for changing the view from full screen to non-full screen and vice versa.
 *
 * @author Eng Chong Meng
 */
public class FullScreenHelper {
    private final ActionBar mActionBar;
    private final WindowInsetsControllerCompat windowInsetsController;

    /**
     * @param activity FragmentActivity
     */
    public FullScreenHelper(FragmentActivity activity) {
        mActionBar = ((AppCompatActivity) activity).getSupportActionBar();

        Window window = activity.getWindow();
        View mDecorView = activity.getWindow().getDecorView();
        windowInsetsController = WindowCompat.getInsetsController(window, mDecorView);
    }

    /**
     * call this method to enter full screen
     */
    public void enterFullScreen() {
//        mDecorView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_IMMERSIVE  // Enables regular immersive mode.
                // Set the content to appear under the system bars so that the
                // content doesn't resize when the system bars hide and show.
//                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
//                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
//                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                // Hide the nav bar and status bar
//                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
//                | View.SYSTEM_UI_FLAG_FULLSCREEN);

        // Configure the behavior of the hidden system bars.
        windowInsetsController.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
        // Hide both the status bar and the navigation bar; Custom ActionBar has to handle separately
        windowInsetsController.hide(WindowInsetsCompat.Type.systemBars());
        if (mActionBar != null)
            mActionBar.hide();
    }

    /**
     * call this method to exit full screen
     */
    public void exitFullScreen() {
        // mDecorView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        // Show both the status bar and the navigation bar.
        windowInsetsController.show(WindowInsetsCompat.Type.systemBars());
        if (mActionBar != null)
            mActionBar.show();
    }
}
