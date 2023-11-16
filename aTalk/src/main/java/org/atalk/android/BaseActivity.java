package org.atalk.android;

import android.content.Context;
import android.os.Bundle;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;

import org.atalk.android.gui.util.LocaleHelper;
import org.atalk.android.gui.util.ThemeHelper;

/**
 * BaseActivity implements the support of user set Theme and locale.
 * All app activities must extend BaseActivity inorder to support Theme and locale.
 */
public class BaseActivity extends AppCompatActivity {
    /**
     * Override AppCompatActivity#onCreate() to support Theme setting
     * Must setTheme() before super.onCreate(), otherwise user selected Theme is not working
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Always call setTheme() method in base class and before super.onCreate()
        ThemeHelper.setTheme(this);
        super.onCreate(savedInstanceState);
    }

    /**
     * Override AppCompatActivity#attachBaseContext() to support Locale setting.
     * Language value is initialized in Application class with user selected language.
     */
    @Override
    protected void attachBaseContext(Context base) {
        Context context = LocaleHelper.setLocale(base);
        super.attachBaseContext(context);
    }

    /**
     * Set preference title using android inbuilt toolbar
     *
     * @param resId preference tile resourceID
     */
    public void setMainTitle(int resId) {
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayOptions(ActionBar.DISPLAY_SHOW_HOME
                    | ActionBar.DISPLAY_USE_LOGO
                    | ActionBar.DISPLAY_SHOW_TITLE);

            actionBar.setLogo(R.drawable.ic_icon);
            actionBar.setTitle(resId);
        }
    }
}
