/*
 * aTalk, android VoIP and Instant Messaging client
 * Copyright 2014 Eng Chong Meng
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.atalk.android.gui;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.webkit.WebView;
import android.widget.TextView;

import net.java.sip.communicator.service.update.UpdateService;
import net.java.sip.communicator.util.ServiceUtils;

import org.atalk.android.BuildConfig;
import org.atalk.android.R;
import org.atalk.android.aTalkApp;

import de.cketti.library.changelog.ChangeLog;

/**
 * About activity
 */
public class About extends Activity implements OnClickListener, View.OnLongClickListener
{
    private final int FETCH_ERROR = 10;
    private final int NO_NEW_VERSION = 20;
    private final int DOWNLOAD_ERROR = 30;

    private final static int CHECK_NEW_VERSION = 10;
    private static String appVersion = "";

    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        Bundle extras = getIntent().getExtras();
        if (extras != null) {
            String aboutInfo = extras.getString(Intent.EXTRA_TEXT);
            if (aboutInfo != null) {
                requestWindowFeature(Window.FEATURE_LEFT_ICON);
                setContentView(R.layout.about);

                findViewById(R.id.ok_button).setOnClickListener(this);
                findViewById(R.id.history_log).setOnClickListener(this);

                View btn_submitLogs = findViewById(R.id.submit_logs);
                btn_submitLogs.setOnClickListener(this);

                View btn_update = findViewById(R.id.check_new_version);
                btn_update.setOnClickListener(this);

                if (BuildConfig.DEBUG) {
                    btn_update.setVisibility(View.VISIBLE);
                } else {
                    btn_update.setVisibility(View.GONE);
                    btn_submitLogs.setOnLongClickListener(this);
                }

                WebView wv = (WebView) findViewById(R.id.AboutDialog_Other_license);
                wv.loadDataWithBaseURL("file:///android_res/drawable/", aboutInfo, "text/html", "utf-8", null);

                setFeatureDrawableResource(Window.FEATURE_LEFT_ICON, android.R.drawable.ic_dialog_info);
                this.setTitle(getString(R.string.AboutDialog_title));
                try {
                    PackageInfo pi = getPackageManager().getPackageInfo(getPackageName(), 0);

                    TextView textView = (TextView) findViewById(R.id.AboutDialog_Version);
                    textView.setText(String.format(aTalkApp.getResString(R.string.AboutDialog_Version), pi.versionName));
                } catch (PackageManager.NameNotFoundException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    @Override
    public void onResume()
    {
        super.onResume();
    }

    @Override
    public void onClick(View view)
    {
        boolean cancelUpdate = false;

        switch (view.getId()) {
            case R.id.ok_button:
                finish();
                break;
            case R.id.check_new_version:
                new Thread()
                {
                    @Override
                    public void run()
                    {
                        UpdateService updateService = ServiceUtils.getService(
                                AndroidGUIActivator.bundleContext, UpdateService.class);
                        if (updateService != null)
                            updateService.checkForUpdates(true);
                    }
                }.start();
                break;
            case R.id.submit_logs:
                aTalkApp.showSendLogsDialog();
                break;
            case R.id.history_log:
                ChangeLog cl = new ChangeLog(this);
                cl.getFullLogDialog().show();
                break;
            default:
                finish();
                break;
        }
    }

    @Override
    public boolean onLongClick(View view)
    {
        if (view.getId() == R.id.submit_logs) {
            new Thread()
            {
                @Override
                public void run()
                {
                    UpdateService updateService = ServiceUtils.getService(
                            AndroidGUIActivator.bundleContext, UpdateService.class);
                    if (updateService != null)
                        updateService.checkForUpdates(true);
                }
            }.start();
            return true;
        }
        return false;
    }
}
