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

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.webkit.WebView;
import android.widget.TextView;

import net.java.sip.communicator.service.update.UpdateService;
import net.java.sip.communicator.util.ServiceUtils;

import org.atalk.android.BaseActivity;
import org.atalk.android.BuildConfig;
import org.atalk.android.R;
import org.atalk.android.aTalkApp;

import de.cketti.library.changelog.ChangeLog;
import timber.log.Timber;

/**
 * About dialog information display.
 *
 * @author Eng Chong Meng
 */
public class About extends BaseActivity implements View.OnClickListener {
    private static final String[][] USED_LIBRARIES = new String[][]{
            new String[]{"Android Support Library", "https://developer.android.com/topic/libraries/support-library/index.html"},
            new String[]{"Android-EasyLocation", "https://github.com/akhgupta/Android-EasyLocation"},
            new String[]{"annotations-java5", "https://mvnrepository.com/artifact/org.jetbrains/annotations"},
            new String[]{"apache-mime4j-core", "https://james.apache.org/mime4j/"},
            new String[]{"bcg720", "https://gitlab.linphone.org/BC/public/bcg729"},
            new String[]{"bouncycastle", "https://github.com/bcgit/bc-java"},
            new String[]{"ckChangeLog", "https://github.com/cketti/ckChangeLog"},
            new String[]{"commons-codec", "https://github.com/apache/commons-codec"},
            new String[]{"commons-text", "https://github.com/apache/commons-text"},
            new String[]{"Dexter", "https://github.com/Karumi/Dexter"},
            new String[]{"dhcp4java", "https://github.com/ggrandes-clones/dhcp4java"},
            new String[]{"FFmpeg", "https://github.com/FFmpeg/FFmpeg"},
            new String[]{"fmj-jitsi", "https://github.com/jitsi/fmj"},
            new String[]{"glide", "https://github.com/bumptech/glide"},
            new String[]{"Google Play Services", "https://developers.google.com/android/guides/overview"},
            new String[]{"IPAddress", "https://github.com/seancfoley/IPAddress"},
            new String[]{"ice4j", "https://github.com/jitsi/ice4j"},
            new String[]{"jbosh", "https://github.com/igniterealtime/jbosh"},
            new String[]{"jitsi", "https://github.com/jitsi/jitsi"},
            new String[]{"jitsi-android", "https://github.com/jitsi/jitsi-android"},
            new String[]{"jmdns", "https://github.com/jmdns/jmdns"},
            new String[]{"jxmpp-jid", "https://github.com/igniterealtime/jxmpp"},
            new String[]{"libjitsi", "https://github.com/jitsi/libjitsi"},
            new String[]{"libvpx", "https://github.com/webmproject/libvpx"},
            new String[]{"media3-exoplayer", "https://github.com/androidx/media"},
            new String[]{"Mime4j", "https://james.apache.org/mime4j/"},
            new String[]{"miniDNS", "https://github.com/MiniDNS/minidns"},
            new String[]{"Noembed", "https://noembed.com/"},
            new String[]{"okhttp", "https://github.com/square/okhttp"},
            new String[]{"opus", "https://ftp.osuosl.org/pub/xiph/releases/opus/"},
            new String[]{"osmdroid", "https://github.com/osmdroid/osmdroid"},
            new String[]{"opensles", "https://github.com/openssl/openssl "},
            new String[]{"osgi.core", "http://grepcode.com/snapshot/repo1.maven.org/maven2/org.osgi/org.osgi.core/6.0.0"},
            new String[]{"sdes4j", "https://github.com/ibauersachs/sdes4j"},
            new String[]{"sdp-api", "https://mvnrepository.com/artifact/org.opentelecoms.sdp/sdp-api"},
            new String[]{"signal-protocol-java", "https://github.com/signalapp/libsignal-protocol-java"},
            new String[]{"Smack", "https://github.com/igniterealtime/Smack"},
            new String[]{"speex", "https://github.com/xiph/speex"},
            new String[]{"Timber", "https://github.com/JakeWharton/timber"},
            new String[]{"TokenAutoComplete", "https://github.com/splitwise/TokenAutoComplete"},
            new String[]{"uCrop", "https://github.com/Yalantis/uCrop"},
            new String[]{"weupnp", "https://github.com/bitletorg/weupnp"},
            new String[]{"x264", "https://git.videolan.org/git/x264.git"},
            new String[]{"zrtp4j-light", "https://github.com/jitsi/zrtp4j"},
    };

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.about);
        // crash if enabled under FragmentActivity
        // requestWindowFeature(Window.FEATURE_LEFT_ICON);
        // setFeatureDrawableResource(Window.FEATURE_LEFT_ICON, android.R.drawable.ic_dialog_info);
        setMainTitle(R.string.About);

        View atakUrl = findViewById(R.id.atalk_link);
        atakUrl.setOnClickListener(this);

        TextView atalkHelp = findViewById(R.id.atalk_help);
        atalkHelp.setTextColor(getResources().getColor(R.color.blue50, null));
        atalkHelp.setOnClickListener(this);

        findViewById(R.id.ok_button).setOnClickListener(this);
        findViewById(R.id.history_log).setOnClickListener(this);

        View btn_submitLogs = findViewById(R.id.submit_logs);
        btn_submitLogs.setOnClickListener(this);

        if (BuildConfig.DEBUG) {
            View btn_update = findViewById(R.id.check_new_version);
            btn_update.setVisibility(View.VISIBLE);
            btn_update.setOnClickListener(this);
        }

        String aboutInfo = getAboutInfo();
        WebView wv = findViewById(R.id.AboutDialog_Info);
        wv.loadDataWithBaseURL("file:///android_res/drawable/", aboutInfo, "text/html", "utf-8", null);

        try {
            PackageInfo pi = getPackageManager().getPackageInfo(getPackageName(), 0);

            TextView textView = findViewById(R.id.AboutDialog_Version);
            textView.setText(String.format(getString(R.string.version_), pi.versionName));
        } catch (PackageManager.NameNotFoundException e) {
            Timber.d("Name Not Found Exception: %s", e.getMessage());
        }
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.check_new_version:
                new Thread() {
                    @Override
                    public void run() {
                        UpdateService updateService
                                = ServiceUtils.getService(AppGUIActivator.bundleContext, UpdateService.class);
                        if (updateService != null) {
                            updateService.checkForUpdates();
                        }
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

            case R.id.atalk_help:
            case R.id.atalk_link:
                atalkUrlAccess(this, getString(R.string.AboutDialog_Link));
                break;

            case R.id.ok_button:
            default:
                finish();
                break;
        }
    }

    public static void atalkUrlAccess(Context context, String url) {
        if (url == null)
            url = context.getString(R.string.AboutDialog_Link);
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setData(Uri.parse(url));
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
    }

    private String getAboutInfo() {
        StringBuilder html = new StringBuilder()
                .append("<meta http-equiv=\"content-type\" content=\"text/html; charset=utf-8\"/>")
                .append("<html><head><style type=\"text/css\">")
                .append("</style></head><body>");

        html.append("<hr/><p>");
        html.append(getString(R.string.atalk_doap))
                .append("</p><hr/><p>");

        StringBuilder libs = new StringBuilder().append("<ul>");
        for (String[] library : USED_LIBRARIES) {
            libs.append("<li><a href=\"")
                    .append(library[1])
                    .append("\">")
                    .append(library[0])
                    .append("</a></li>");
        }
        libs.append("</ul>");

        html.append(String.format(getString(R.string.app_libraries), libs))
                .append("</p><hr/>");
        html.append("</body></html>");

        return html.toString();
    }
}
