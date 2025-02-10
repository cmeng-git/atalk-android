/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.android.gui;

import android.content.Intent;
import android.os.Bundle;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.ActionBar;

import org.atalk.android.BaseActivity;
import org.atalk.android.R;
import org.atalk.android.gui.actionbar.ActionBarUtil;
import org.atalk.service.osgi.OSGiService;

/**
 * Activity displayed when shutdown procedure is in progress.
 *
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
public class ShutdownActivity extends BaseActivity {
    private ProgressBar mProgressBar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!OSGiService.hasStarted()) {
            startActivity(new Intent(this, LauncherActivity.class));
            finish();
            return;
        }

        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            // Disable up arrow
            actionBar.setDisplayHomeAsUpEnabled(false);
            actionBar.setHomeButtonEnabled(false);
            ActionBarUtil.setTitle(this, getTitle());
        }

        setContentView(R.layout.splash);
        TextView shutDown = findViewById(R.id.stateInfo);
        shutDown.setText(R.string.shutting_down);

        mProgressBar = findViewById(R.id.progressBar);
        mProgressBar.setVisibility(ProgressBar.VISIBLE);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mProgressBar != null)
            mProgressBar.clearAnimation();
    }
}
