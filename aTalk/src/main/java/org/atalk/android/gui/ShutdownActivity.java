/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.android.gui;

import android.annotation.TargetApi;
import android.app.*;
import android.content.Intent;
import android.os.*;
import android.view.View;
import android.view.Window;
import android.widget.TextView;

import org.atalk.android.R;
import org.atalk.android.gui.util.*;
import org.atalk.service.osgi.OSGiService;

/**
 * Activity displayed when shutdown procedure is in progress.
 *
 * @author Pawel Domas
 */
public class ShutdownActivity extends Activity
{
	@TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
		super.onCreate(savedInstanceState);

		if (!OSGiService.hasStarted()) {
			startActivity(new Intent(this, LauncherActivity.class));
			finish();
			return;
		}

		ActionBar actionBar = getActionBar();
		if (actionBar != null) {
			// Disable up arrow
			actionBar.setDisplayHomeAsUpEnabled(false);
			actionBar.setHomeButtonEnabled(false);
			ActionBarUtil.setTitle(this, getTitle());
		}

		setProgressBarIndeterminateVisibility(true);
		setContentView(R.layout.splash);
		((TextView) findViewById(R.id.restoring)).setText(R.string.service_gui_SHUTDOWN_IN_PROGRESS);
	}
}
