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
import android.net.Uri;
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

    private static String[][] USED_LIBRARIES = new String[][]{
            new String[]{"Android Support Library", "https://developer.android.com/topic/libraries/support-library/index.html"},
            new String[]{"android-betterpickers", "https://github.com/code-troopers/android-betterpickers"},
            new String[]{"Android-EasyLocation", "https://github.com/akhgupta/Android-EasyLocation"},
            new String[]{"annotations-java5", "https://mvnrepository.com/artifact/org.jetbrains/annotations"},
            new String[]{"bouncycastle", "https://github.com/bcgit/bc-java"},
            new String[]{"butterknife", "https://github.com/JakeWharton/butterknife"},
            new String[]{"ckChangeLog", "https://github.com/cketti/ckChangeLog"},
            new String[]{"commons-lang", "http://commons.apache.org/proper/commons-lang/"},
            new String[]{"Dexter", "https://github.com/Karumi/Dexter"},
            new String[]{"dhcp4java", "https://github.com/ggrandes-clones/dhcp4java"},
            new String[]{"dnsjava", "https://github.com/dnsjava/dnsjava"},
            new String[]{"dnssecjava", "https://github.com/ibauersachs/dnssecjava"},
            new String[]{"ews-android-api", "https://github.com/alipov/ews-android-api"},
            new String[]{"FFmpeg", "https://github.com/FFmpeg/FFmpeg"},
            new String[]{"Google Play Services", "https://developers.google.com/android/guides/overview"},
            new String[]{"guava", "// https://mvnrepository.com/artifact/com.google.guava/guava"},
            new String[]{"httpclient-android", "https://github.com/smarek/httpclient-android"},
            new String[]{"ice4j", "https://github.com/jitsi/ice4j"},
            new String[]{"jitsi", "https://github.com/jitsi/jitsi"},
            new String[]{"jitsi-android", "https://github.com/jitsi/jitsi-android"},
            new String[]{"jmdns", "https://github.com/jmdns/jmdns"},
            new String[]{"json-simple", "https://github.com/fangyidong/json-simple"},
            new String[]{"jxmpp-jid", "https://github.com/igniterealtime/jxmpp"},
            new String[]{"libjitsi", "https://github.com/jitsi/libjitsi"},
            new String[]{"libphonenumber", "https://github.com/googlei18n/libphonenumber"},
            new String[]{"libvpx", "https://github.com/webmproject/libvpx"},
            new String[]{"otr4j", "https://github.com/jitsi/otr4j"},
            new String[]{"opensles", "https://github.com/openssl/openssl "},
            new String[]{"osgi.core", "http://grepcode.com/snapshot/repo1.maven.org/maven2/org.osgi/org.osgi.core/6.0.0"},
            new String[]{"sdes4j", "https://github.com/ibauersachs/sdes4j"},
            new String[]{"sdp-api", "https://mvnrepository.com/artifact/org.opentelecoms.sdp/sdp-api"},
            new String[]{"Smack", "https://github.com/igniterealtime/Smack"},
            new String[]{"speex", "https://github.com/xiph/speex"},
            new String[]{"uCrop", "https://github.com/Yalantis/uCrop"},
            new String[]{"weupnp", "https://github.com/bitletorg/weupnp"},
            new String[]{"x264", "http://git.videolan.org/git/x264.git"},
            new String[]{"zrtp4j-light", "https://github.com/jitsi/zrtp4j"},
    };

    private static String[][] SUPPORTED_XEP = new String[][]{
            new String[]{"XEP-0030: Service Discovery", "https://xmpp.org/extensions/xep-0030.html"},
            new String[]{"XEP-0045: Multi-User Chat", "https://xmpp.org/extensions/xep-0045.html"},
            new String[]{"XEP-0047: In-Band Bytestreams", "https://xmpp.org/extensions/xep-00047.html"},
            new String[]{"XEP-0054: vcard-temp", "https://xmpp.org/extensions/xep-0054.html"},
            new String[]{"XEP-0060:	Publish-Subscribe", "https://xmpp.org/extensions/xep-0060.html"},
            new String[]{"XEP-0065: SOCKS5 Bytestreams", "https://xmpp.org/extensions/xep-0065.html"},
            new String[]{"XEP-0077:	In-Band Registration", "https://xmpp.org/extensions/xep-0077.html"},
            new String[]{"XEP-0084: User Avatar", "https://xmpp.org/extensions/xep-0084.html"},
            new String[]{"XEP-0085: Chat State Notifications", "https://xmpp.org/extensions/xep-0085.html"},
            new String[]{"XEP-0092: Software Version", "https://xmpp.org/extensions/xep-0092.html"},
            new String[]{"XEP-0095: Stream Initiation", "https://xmpp.org/extensions/xep-0095.html"},
            new String[]{"XEP-0096: SI File Transfer", "https://xmpp.org/extensions/xep-0096.html"},
            new String[]{"XEP-0115: Entity Capabilities", "https://xmpp.org/extensions/xep-0115.html"},
            new String[]{"XEP-0138:	Stream Compression", "https://xmpp.org/extensions/xep-0138.html"},
            new String[]{"XEP-0153: vCard-Based Avatar", "https://xmpp.org/extensions/xep-0153.html"},
            new String[]{"XEP-0163: Personal Eventing Protocol (avatars and nicks)", "https://xmpp.org/extensions/xep-0163.html"},
            new String[]{"XEP-0166: Jingle", "https://xmpp.org/extensions/xep-0166.html"},
            new String[]{"XEP-0167: Jingle RTP Sessions", "https://xmpp.org/extensions/xep-0167.html"},
            new String[]{"XEP-0172: User Nickname", "https://xmpp.org/extensions/xep-0172.html"},
            new String[]{"XEP-0176: Jingle ICE-UDP Transport Method", "https://xmpp.org/extensions/xep-0176.html"},
            new String[]{"XEP-0177: Jingle Raw UDP Transport Method", "https://xmpp.org/extensions/xep-0177.html"},
            new String[]{"XEP-0184: Message Delivery Receipts (NI)", "https://xmpp.org/extensions/xep-0184.html"},
            new String[]{"XEP-0191: Blocking command (NI)", "https://xmpp.org/extensions/xep-0191.html"},
            new String[]{"XEP-0198: Stream Management", "https://xmpp.org/extensions/xep-0198.html"},
            new String[]{"XEP-0199: XMPP Ping", "https://xmpp.org/extensions/xep-0199.html"},
            new String[]{"XEP-0203:	Delayed Delivery", "https://xmpp.org/extensions/xep-0203.html"},
            new String[]{"XEP-0231: Bits of Binary", "https://xmpp.org/extensions/xep-0231.html"},
            new String[]{"XEP-0234: Jingle File Transfer", "https://xmpp.org/extensions/xep-0234.html"},
            new String[]{"XEP-0237: Roster Versioning", "https://xmpp.org/extensions/xep-0237.html"},
            new String[]{"XEP-0249: Direct MUC Invitations", "https://xmpp.org/extensions/xep-0249.html"},
            new String[]{"XEP-0251: Jingle Session Transfer", "https://xmpp.org/extensions/xep-0251.html"},
            new String[]{"XEP-0260: Jingle SOCKS5 Bytestreams Transport Method", "https://xmpp.org/extensions/xep-0260.html"},
            new String[]{"XEP-0261: Jingle In-Band Bytestreams Transport Method", "https://xmpp.org/extensions/xep-0261.html"},
            new String[]{"XEP-0262: Use of ZRTP in Jingle RTP Sessions", "https://xmpp.org/extensions/xep-0262.html"},
            new String[]{"XEP-0264: File Transfer Thumbnails", "https://xmpp.org/extensions/xep-0264.html"},
            new String[]{"XEP-0278: Jingle Relay Nodes", "https://xmpp.org/extensions/xep-0278.html"},
            new String[]{"XEP-0280: Message Carbons", "https://xmpp.org/extensions/xep-0280.html"},
            new String[]{"XEP-0294: Jingle RTP Header Extensions Negotiation", "https://xmpp.org/extensions/xep-0294.html"},
            new String[]{"XEP-0298: Delivering Conference Information to Jingle Participants (Coin)", "https://xmpp.org/extensions/xep-0298.html"},
            new String[]{"XEP-0308: Last Message Correction", "https://xmpp.org/extensions/xep-0308.html"},
            new String[]{"XEP-0319: Last User Interaction in Presence", "https://xmpp.org/extensions/xep-0319.html"},
            new String[]{"XEP-0320: Use of DTLS-SRTP in Jingle Sessions", "https://xmpp.org/extensions/xep-0320.html"},
            new String[]{"XEP-0352: Client State Indication", "https://xmpp.org/extensions/xep-052.html"},
            new String[]{"XEP-0364: Off-the-Record Messaging (V2/3)", "https://xmpp.org/extensions/xep-0364.html"},
            new String[]{"XEP-0384: OMEMO Encryption", "https://xmpp.org/extensions/xep-0384.html"},
    };

    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);

        requestWindowFeature(Window.FEATURE_LEFT_ICON);
        setContentView(R.layout.about);

        View atakUrl = findViewById(R.id.atalk_link);
        atakUrl.setOnClickListener(this);

        findViewById(R.id.ok_button).setOnClickListener(this);
        findViewById(R.id.history_log).setOnClickListener(this);

        View btn_submitLogs = findViewById(R.id.submit_logs);
        btn_submitLogs.setOnClickListener(this);

        View btn_update = findViewById(R.id.check_new_version);
        btn_update.setOnClickListener(this);

        if (BuildConfig.DEBUG) {
            btn_update.setVisibility(View.VISIBLE);
        }
        else {
            btn_update.setVisibility(View.GONE);
            btn_submitLogs.setOnLongClickListener(this);
        }

        String aboutInfo = getAboutInfo();
        WebView wv = (WebView) findViewById(R.id.AboutDialog_Info);
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
            case R.id.atalk_link:
                atalkUrlAccess();
                break;
            default:
                finish();
                break;
        }
    }

    private void atalkUrlAccess(){
        String url = getString(R.string.AboutDialog_website);
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setData(Uri.parse(url));
        startActivity(intent);
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

    private String getAboutInfo()
    {
        StringBuilder html = new StringBuilder()
                .append("<meta http-equiv=\"content-type\" content=\"text/html; charset=utf-8\"/>");

        StringBuilder xeps = new StringBuilder().append("<ul>");
        for (String[] feature : SUPPORTED_XEP) {
            xeps.append("<li><a href=\"")
                    .append(feature[1])
                    .append("\">")
                    .append(feature[0])
                    .append("</a></li>");
        }
        xeps.append("</ul>");

        html.append(String.format(getString(R.string.app_xeps), xeps.toString()))
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

        html.append(String.format(getString(R.string.app_libraries), libs.toString()))
                .append("</p><hr/><p>");

        return html.toString();
    }
}
