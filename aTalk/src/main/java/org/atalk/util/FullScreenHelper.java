package org.atalk.util;

import android.view.View;

import androidx.fragment.app.FragmentActivity;

/**
 * Class responsible for changing the view from full screen to non-full screen and vice versa.
 *
 * @author Pierfrancesco Soffritti
 * @author Eng Chong Meng
 */
public class FullScreenHelper {
    private final FragmentActivity mFragmentActivity;
    private final View[] mViews;

    /**
     * @param activity FragmentActivity
     * @param views to hide/show
     */
    public FullScreenHelper(FragmentActivity activity, View... views) {
        mFragmentActivity = activity;
        mViews = views;
    }

    /**
     * call this method to enter full screen
     */
    public void enterFullScreen() {
        View decorView = mFragmentActivity.getWindow().getDecorView();

        hideSystemUi(decorView);

        for (View view : mViews) {
            view.setVisibility(View.GONE);
            view.invalidate();
        }
    }

    /**
     * call this method to exit full screen
     */
    public void exitFullScreen() {
        View decorView = mFragmentActivity.getWindow().getDecorView();
        showSystemUi(decorView);

        for (View view : mViews) {
            view.setVisibility(View.VISIBLE);
            view.invalidate();
        }
    }

    private void hideSystemUi(View mDecorView) {
        mDecorView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
    }

    private void showSystemUi(View mDecorView) {
        mDecorView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
    }
}
