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

/**
 * About activity
 *
 * @author Eng Chong Meng
 */
public class About extends BaseActivity implements View.OnClickListener {
    private static final String[][] USED_LIBRARIES = new String[][]{
            new String[]{"Android Support Library", "https://developer.android.com/topic/libraries/support-library/index.html"},
            new String[]{"android-betterpickers", "https://github.com/code-troopers/android-betterpickers"},
            new String[]{"Android-EasyLocation", "https://github.com/akhgupta/Android-EasyLocation"},
            new String[]{"annotations-java5", "https://mvnrepository.com/artifact/org.jetbrains/annotations"},
            new String[]{"bouncycastle", "https://github.com/bcgit/bc-java"},
            new String[]{"ckChangeLog", "https://github.com/cketti/ckChangeLog"},
            new String[]{"commons-lang", "https://commons.apache.org/proper/commons-lang/"},
            new String[]{"Dexter", "https://github.com/Karumi/Dexter"},
            new String[]{"dhcp4java", "https://github.com/ggrandes-clones/dhcp4java"},
            new String[]{"ExoPlayer", "https://github.com/google/ExoPlayer"},
            new String[]{"FFmpeg", "https://github.com/FFmpeg/FFmpeg"},
            new String[]{"glide", "https://github.com/bumptech/glide"},
            new String[]{"Google Play Services", "https://developers.google.com/android/guides/overview"},
            new String[]{"httpclient-android", "https://github.com/smarek/httpclient-android"},
            new String[]{"IPAddress", "https://github.com/seancfoley/IPAddress"},
            new String[]{"ice4j", "https://github.com/jitsi/ice4j"},
            new String[]{"jitsi", "https://github.com/jitsi/jitsi"},
            new String[]{"jitsi-android", "https://github.com/jitsi/jitsi-android"},
            new String[]{"jmdns", "https://github.com/jmdns/jmdns"},
            new String[]{"jxmpp-jid", "https://github.com/igniterealtime/jxmpp"},
            new String[]{"libjitsi", "https://github.com/jitsi/libjitsi"},
            new String[]{"libphonenumber", "https://github.com/googlei18n/libphonenumber"},
            new String[]{"libvpx", "https://github.com/webmproject/libvpx"},
            new String[]{"Mime4j", "https://james.apache.org/mime4j/"},
            new String[]{"miniDNS", "https://github.com/MiniDNS/minidns"},
            new String[]{"Noembed", "https://noembed.com/"},
            new String[]{"osmdroid", "https://github.com/osmdroid/osmdroid"},
            new String[]{"otr4j", "https://github.com/jitsi/otr4j"},
            new String[]{"opensles", "https://github.com/openssl/openssl "},
            new String[]{"osgi.core", "http://grepcode.com/snapshot/repo1.maven.org/maven2/org.osgi/org.osgi.core/6.0.0"},
            new String[]{"sdes4j", "https://github.com/ibauersachs/sdes4j"},
            new String[]{"sdp-api", "https://mvnrepository.com/artifact/org.opentelecoms.sdp/sdp-api"},
            new String[]{"Smack", "https://github.com/igniterealtime/Smack"},
            new String[]{"speex", "https://github.com/xiph/speex"},
            new String[]{"Timber", "https://github.com/JakeWharton/timber"},
            new String[]{"TokenAutoComplete", "https://github.com/splitwise/TokenAutoComplete"},
            new String[]{"uCrop", "https://github.com/Yalantis/uCrop"},
            new String[]{"weupnp", "https://github.com/bitletorg/weupnp"},
            new String[]{"x264", "https://git.videolan.org/git/x264.git"},
            new String[]{"zrtp4j-light", "https://github.com/jitsi/zrtp4j"},
    };

    private static final String[][] SUPPORTED_XEP = new String[][]{
            new String[]{"XEP-0012: Last Activity 2.0", "https://xmpp.org/extensions/xep-0012.html"},
            new String[]{"XEP-0030: Service Discovery 2.5rc3", "https://xmpp.org/extensions/xep-0030.html"},
            new String[]{"XEP-0045: Multi-User Chat 1.34.3", "https://xmpp.org/extensions/xep-0045.html"},
            new String[]{"XEP-0047: In-Band Bytestreams 2.0.1", "https://xmpp.org/extensions/xep-0047.html"},
            new String[]{"XEP-0048: Bookmarks 1.2", "https://xmpp.org/extensions/xep-0048.html"},
            new String[]{"XEP-0054: vcard-temp 1.2", "https://xmpp.org/extensions/xep-0054.html"},
            new String[]{"XEP-0060: Publish-Subscribe 1.24.1", "https://xmpp.org/extensions/xep-0060.html"},
            new String[]{"XEP-0065: SOCKS5 Bytestreams 1.8.2", "https://xmpp.org/extensions/xep-0065.html"},
            new String[]{"XEP-0066: Out of Band Data 1.5", "https://xmpp.org/extensions/xep-0066.html"},
            new String[]{"XEP-0070: Verifying HTTP Requests via XMPP 1.0.1", "https://xmpp.org/extensions/xep-0070.html"},
            new String[]{"XEP-0071: XHTML-IM 1.5.4", "https://xmpp.org/extensions/xep-0071.html"},
            new String[]{"XEP-0077: In-Band Registration 2.4", "https://xmpp.org/extensions/xep-0077.html"},
            new String[]{"XEP-0084: User Avatar 1.1.4", "https://xmpp.org/extensions/xep-0084.html"},
            new String[]{"XEP-0085: Chat State Notifications 2.1", "https://xmpp.org/extensions/xep-0085.html"},
            new String[]{"XEP-0092: Software Version 1.1", "https://xmpp.org/extensions/xep-0092.html"},
            new String[]{"XEP-0095: Stream Initiation 1.2", "https://xmpp.org/extensions/xep-0095.html"},
            new String[]{"XEP-0096: SI File Transfer 1.3.1", "https://xmpp.org/extensions/xep-0096.html"},
            new String[]{"XEP-0100: Gateway Interaction 1.0", "https://xmpp.org/extensions/xep-0100.html"},
            new String[]{"XEP-0115: Entity Capabilities 1.6.0", "https://xmpp.org/extensions/xep-0115.html"},
            new String[]{"XEP-0124: Bidirectional-streams Over Synchronous HTTP (BOSH) 1.11.2", "https://xmpp.org/extensions/xep-0124.html"},
            new String[]{"XEP-0138: Stream Compression 2.1", "https://xmpp.org/extensions/xep-0138.html"},
            new String[]{"XEP-0153: vCard-Based Avatar 1.1", "https://xmpp.org/extensions/xep-0153.html"},
            new String[]{"XEP-0158: CAPTCHA Forms 1.5.8", "https://xmpp.org/extensions/xep-0158.html"},
            new String[]{"XEP-0163: Personal Eventing Protocol 1.2.2", "https://xmpp.org/extensions/xep-0163.html"},
            new String[]{"XEP-0166: Jingle 1.1.2", "https://xmpp.org/extensions/xep-0166.html"},
            new String[]{"XEP-0167: Jingle RTP Sessions 1.2.1", "https://xmpp.org/extensions/xep-0167.html"},
            new String[]{"XEP-0172: User Nickname 1.1", "https://xmpp.org/extensions/xep-0172.html"},
            new String[]{"XEP-0176: Jingle ICE-UDP Transport Method 1.1.1", "https://xmpp.org/extensions/xep-0176.html"},
            new String[]{"XEP-0177: Jingle Raw UDP Transport Method 1.1.1", "https://xmpp.org/extensions/xep-0177.html"},
            new String[]{"XEP-0178: Best Practices for Use of SASL EXTERNAL with Certificates 1.2", "https://xmpp.org/extensions/xep-0178.html"},
            new String[]{"XEP-0184: Message Delivery Receipts 1.4.0", "https://xmpp.org/extensions/xep-0184.html"},
            new String[]{"XEP-0191: Blocking command", "https://xmpp.org/extensions/xep-0191.html"},
            new String[]{"XEP-0198: Stream Management 1.6", "https://xmpp.org/extensions/xep-0198.html"},
            new String[]{"XEP-0199: XMPP Ping 2.0.1", "https://xmpp.org/extensions/xep-0199.html"},
            new String[]{"XEP-0203: Delayed Delivery 2.0", "https://xmpp.org/extensions/xep-0203.html"},
            new String[]{"XEP-0206: XMPP Over BOSH 1.4", "https://xmpp.org/extensions/xep-0206.html"},
            new String[]{"XEP-0215: External Service Discovery 1.0.0", "https://xmpp.org/extensions/xep-0215.html"},
            new String[]{"XEP-0231: Bits of Binary 1.0", "https://xmpp.org/extensions/xep-0231.html"},
            new String[]{"XEP-0234: Jingle File Transfer 0.19.1", "https://xmpp.org/extensions/xep-0234.html"},
            new String[]{"XEP-0237: Roster Versioning 1.3", "https://xmpp.org/extensions/xep-0237.html"},
            new String[]{"XEP-0249: Direct MUC Invitations 1.2", "https://xmpp.org/extensions/xep-0249.html"},
            new String[]{"XEP-0251: Jingle Session Transfer 0.2", "https://xmpp.org/extensions/xep-0251.html"},
            new String[]{"XEP-0260: Jingle SOCKS5 Bytestreams Transport Method 1.0.3", "https://xmpp.org/extensions/xep-0260.html"},
            new String[]{"XEP-0261: Jingle In-Band Bytestreams Transport Method 1.0", "https://xmpp.org/extensions/xep-0261.html"},
            new String[]{"XEP-0262: Use of ZRTP in Jingle RTP Sessions 1.0", "https://xmpp.org/extensions/xep-0262.html"},
            new String[]{"XEP-0264: File Transfer Thumbnails 0.4", "https://xmpp.org/extensions/xep-0264.html"},
            new String[]{"XEP-0278: Jingle Relay Nodes 0.4.1", "https://xmpp.org/extensions/xep-0278.html"},
            new String[]{"XEP-0280: Message Carbons 1.0.1", "https://xmpp.org/extensions/xep-0280.html"},
            new String[]{"XEP-0293: Jingle RTP Feedback Negotiation 1.0.1", "https://xmpp.org/extensions/xep-0293.html"},
            new String[]{"XEP-0294: Jingle RTP Header Extensions Negotiation 1.1.1", "https://xmpp.org/extensions/xep-0294.html"},
            new String[]{"XEP-0298: Delivering Conference Information to Jingle Participants (Coin) 0.2", "https://xmpp.org/extensions/xep-0298.html"},
            new String[]{"XEP-0308: Last Message Correction 1.2.0", "https://xmpp.org/extensions/xep-0308.html"},
            new String[]{"XEP-0313: Message Archive Management 1.0.1", "https://xmpp.org/extensions/xep-0313.html"},
            new String[]{"XEP-0319: Last User Interaction in Presence 1.0.2", "https://xmpp.org/extensions/xep-0319.html"},
            new String[]{"XEP-0320: Use of DTLS-SRTP in Jingle Sessions 1.0.0", "https://xmpp.org/extensions/xep-0320.html"},
            new String[]{"XEP-0338: Jingle Grouping Framework 1.0.0", "https://xmpp.org/extensions/xep-0338.html"},
            new String[]{"XEP-0339: Source-Specific Media Attributes in Jingle 1.0.1", "https://xmpp.org/extensions/xep-0339.html"},
            new String[]{"XEP-0343: Signaling WebRTC datachannels in Jingle 0.3.1", "https://xmpp.org/extensions/xep-0343.html"},
            new String[]{"XEP-0352: Client State Indication 1.0.0", "https://xmpp.org/extensions/xep-0352.html"},
            new String[]{"XEP-0353: Jingle Message Initiation 0.4.0", "https://xmpp.org/extensions/xep-0353.html"},
            new String[]{"XEP-0363: HTTP File Upload 1.1.0", "https://xmpp.org/extensions/xep-0363.html"},
            new String[]{"XEP-0364: Off-the-Record Messaging (V2/3) 0.3.2", "https://xmpp.org/extensions/xep-0364.html"},
            new String[]{"XEP-0371: Jingle ICE Transport Method 0.3.1", "https://xmpp.org/extensions/xep-0371.html"},
            // new String[]{"XEP-0368: SRV records for XMPP over TLS", "https://xmpp.org/extensions/xep-0368.html"},
            new String[]{"XEP-0384: OMEMO Encryption 0.8.3", "https://xmpp.org/extensions/xep-0384.html"},
            new String[]{"XEP-0391: Jingle Encrypted Transports 0.1.2", "https://xmpp.org/extensions/xep-0391.html"},
            new String[]{"XEP-0441: Message Archive Management Preferences 0.2.0", "https://xmpp.org/extensions/xep-0441.htmll"},
            new String[]{"XEP-xxxx: OMEMO Media sharing 0.0.2", "https://xmpp.org/extensions/inbox/omemo-media-sharing.html"},
            // new String[]{"", ""},
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
            e.printStackTrace();
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

        StringBuilder xeps = new StringBuilder().append("<ul>");
        for (String[] feature : SUPPORTED_XEP) {
            xeps.append("<li><a href=\"")
                    .append(feature[1])
                    .append("\">")
                    .append(feature[0])
                    .append("</a></li>");
        }
        xeps.append("</ul>");

        html.append(String.format(getString(R.string.app_xep), xeps))
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
                .append("</p><hr/><p>");
        html.append("</body></html>");

        return html.toString();
    }
}
