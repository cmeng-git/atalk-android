/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.android.gui.menu;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;

import net.java.sip.communicator.service.protocol.CallConference;
import net.java.sip.communicator.service.protocol.Contact;
import net.java.sip.communicator.service.protocol.OperationSetVideoBridge;
import net.java.sip.communicator.service.protocol.ProtocolProviderService;
import net.java.sip.communicator.service.protocol.event.ContactPresenceStatusChangeEvent;
import net.java.sip.communicator.service.protocol.event.ContactPresenceStatusListener;
import net.java.sip.communicator.service.protocol.globalstatus.GlobalStatusEnum;
import net.java.sip.communicator.service.protocol.globalstatus.GlobalStatusService;
import net.java.sip.communicator.util.ConfigurationUtils;
import net.java.sip.communicator.util.Logger;
import net.java.sip.communicator.util.account.AccountUtils;

import org.atalk.android.R;
import org.atalk.android.aTalkApp;
import org.atalk.android.gui.About;
import org.atalk.android.gui.AndroidGUIActivator;
import org.atalk.android.gui.account.AccountsListActivity;
import org.atalk.android.gui.call.conference.ConferenceInviteDialog;
import org.atalk.android.gui.chatroomslist.ChatRoomCreateDialog;
import org.atalk.android.gui.contactlist.AddContactActivity;
import org.atalk.android.gui.contactlist.model.MetaContactListAdapter;
import org.atalk.android.gui.settings.SettingsActivity;
import org.atalk.android.gui.util.ActionBarUtil;
import org.atalk.service.osgi.OSGiActivity;
import org.osgi.framework.ServiceEvent;
import org.osgi.framework.ServiceListener;
import org.osgi.framework.ServiceReference;

import java.util.ArrayList;
import java.util.List;

/**
 * The main options menu. Every <tt>Activity</tt> that desires to have the general options menu
 * shown have to extend this class.
 * <p>
 * The <tt>MainMenuActivity</tt> is an <tt>OSGiActivity</tt>.
 *
 * @author Yana Stamcheva
 * @author Eng Chong Meng
 */
public class MainMenuActivity extends ExitMenuActivity
        implements ServiceListener, ContactPresenceStatusListener
{
    /**
     * The logger
     */
    private final static Logger logger = Logger.getLogger(MainMenuActivity.class);

    private MenuItem mShowHideOffline;
    private MenuItem mOnOffLine;

    /**
     * Video bridge conference call menu. In the case of more than one account.
     */
    private MenuItem videoBridgeMenuItem = null;
    private VideoBridgeProviderMenuItem menuVbItem = null;

    public static final int STARTING = 8;
    public static final int STOPPING = 16;

    private static boolean done = false;
    public Context mContext;

    private static String[][] USED_LIBRARIES = new String[][]{
            new String[]{"Android Support Library", "https://developer.android.com/topic/libraries/support-library/index.html"},
            new String[]{"android-betterpickers", "https://github.com/code-troopers/android-betterpickers"},
            new String[]{"Android-EasyLocation", "https://github.com/akhgupta/Android-EasyLocation"},
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

    /**
     * The {@link CallConference} instance depicted by this <tt>CallPanel</tt>.
     */
    // private final CallConference callConference = null;
    // private ProtocolProviderService preselectedProvider = null;
    // private List<ProtocolProviderService> videoBridgeProviders = null;

    /**
     * Called when the activity is starting. Initializes the corresponding call interface.
     *
     * @param savedInstanceState
     *         If the activity is being re-initialized after previously being shut down then this
     *         Bundle contains the data it most recently supplied in onSaveInstanceState(Bundle).
     *         Note: Otherwise it is null.
     */
    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        mContext = this;
        // cmeng - not implemented yet, do not set
        // setTheme(aTalkApp.getAppThemeResourceId());
    }

    @Override
    protected void onResume()
    {
        super.onResume();
        AndroidGUIActivator.bundleContext.addServiceListener(this);
        if ((videoBridgeMenuItem != null) && (menuVbItem == null)) {
            initVideoBridge();
        }
    }

    @Override
    protected void onPause()
    {
        super.onPause();
        AndroidGUIActivator.bundleContext.removeServiceListener(this);
    }

    /**
     * Invoked when the options menu is created. Creates our own options menu from the
     * corresponding xml.
     *
     * @param menu
     *         the options menu
     */
    @Override
    public boolean onCreateOptionsMenu(Menu menu)
    {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.main_menu, menu);

        this.videoBridgeMenuItem = menu.findItem(R.id.create_videobridge);
        /* Need this on first start up */
        initVideoBridge();
        videoBridgeMenuItem.setEnabled(true);

        mShowHideOffline = menu.findItem(R.id.show_hide_offline);
        int itemId = ConfigurationUtils.isShowOffline()
                ? R.string.service_gui_HIDE_OFFLINE_CONTACTS
                : R.string.service_gui_SHOW_OFFLINE_CONTACTS;
        mShowHideOffline.setTitle(getString(itemId));

        mOnOffLine = menu.findItem(R.id.sign_in_off);
        itemId = GlobalStatusEnum.OFFLINE_STATUS.equals(ActionBarUtil.getStatus(this))
                ? R.string.service_gui_SIGN_IN
                : R.string.service_gui_SIGN_OUT;
        mOnOffLine.setTitle(getString(itemId));

        // Adds exit option from super class
        super.onCreateOptionsMenu(menu);
        return true;
    }

    /**
     * Put initVideoBridge as separate task as it takes time to filtered server advertised
     * features/info (long list)
     * TODO: cmeng: Need more works for multiple accounts where not all servers support videoBridge
     */
    private void initVideoBridge_task()
    {
        final Boolean enableMenu;
        if (menuVbItem == null)
            this.menuVbItem = new VideoBridgeProviderMenuItem();

        List<ProtocolProviderService> videoBridgeProviders = getVideoBridgeProviders();
        int videoBridgeProviderCount = (videoBridgeProviders == null)
                ? 0 : videoBridgeProviders.size();

        if (videoBridgeProviderCount >= 1) {
            enableMenu = true;
            if (videoBridgeProviderCount == 1) {
                menuVbItem.setPreselectedProvider(videoBridgeProviders.get(0));
            }
            else {
                menuVbItem.setPreselectedProvider(null);
                menuVbItem.setVideoBridgeProviders(videoBridgeProviders);
            }
        }
        else
            enableMenu = false;

        // runOnUiThread to update view
        this.runOnUiThread(new Runnable()
        {
            @Override
            public void run()
            {
                // videoBridgeMenuItem is always enabled - allow user to re-trigger if earlier init failed
                videoBridgeMenuItem.setEnabled(true);

                if (enableMenu) {
                    videoBridgeMenuItem.getIcon().setAlpha(255);
                }
                else {
                    videoBridgeMenuItem.getIcon().setAlpha(80);
                    menuVbItem = null;
                }
            }
        });
    }

    /**
     * Progressing dialog to inform user while fetching xmpp server advertised features.
     * May takes time as some servers have many features & slow response.
     * Auto cancel after menu is displayed - end of fetching cycle
     */
    private void initVideoBridge()
    {
        final ProgressDialog progressDialog;
        if (!done) {
            progressDialog = ProgressDialog.show(MainMenuActivity.this,
                    getString(R.string.service_gui_WAITING),
                    getString(R.string.service_gui_SERVER_INFO_FETCH), true);
            progressDialog.setCancelable(true);
        }
        else {
            progressDialog = null;
        }

        new Thread(new Runnable()
        {
            @Override
            public void run()
            {
                try {
                    initVideoBridge_task();
                    Thread.sleep(100);
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
                if (progressDialog != null) {
                    done = true;
                    progressDialog.dismiss();
                }
            }
        }).start();
    }

    public MenuItem getMenuItemOnOffLine()
    {
        return mOnOffLine;
    }

    /**
     * Invoked when an options item has been selected.
     *
     * @param item
     *         the item that has been selected
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item)
    {
        String itemTextKey;

        // Handle item selection
        switch (item.getItemId()) {
            case R.id.search:
                break;
            case R.id.add_chat_room:
                ChatRoomCreateDialog chatRoomCreateDialog = new ChatRoomCreateDialog(this);
                chatRoomCreateDialog.show();
                break;
            case R.id.create_videobridge:
                if (menuVbItem == null) {
                    initVideoBridge();
                }
                else
                    menuVbItem.actionPerformed();
                break;
            case R.id.show_hide_offline:
                boolean isShowOffline = ConfigurationUtils.isShowOffline();
                MetaContactListAdapter.presenceFilter.setShowOffline(!isShowOffline);
                MetaContactListAdapter contactListAdapter = AndroidGUIActivator.getContactListAdapter();
                contactListAdapter.filterData("");

                itemTextKey = !isShowOffline
                        ? "service.gui.HIDE_OFFLINE_CONTACTS" : "service.gui.SHOW_OFFLINE_CONTACTS";
                mShowHideOffline.setTitle(AndroidGUIActivator.getResources().getI18NString(itemTextKey));
                break;
            case R.id.add_contact:
                startActivity(AddContactActivity.class);
                break;
            case R.id.main_settings:
                startActivity(SettingsActivity.class);
                break;
            case R.id.account_settings:
                startActivity(AccountsListActivity.class);
                break;
            case R.id.sign_in_off:
                // Toggle current account presence status
                boolean isOffline = GlobalStatusEnum.OFFLINE_STATUS.equals(ActionBarUtil.getStatus(this));
                GlobalStatusService globalStatusService = AndroidGUIActivator.getGlobalStatusService();
                if (isOffline)
                    globalStatusService.publishStatus(GlobalStatusEnum.ONLINE);
                else
                    globalStatusService.publishStatus(GlobalStatusEnum.OFFLINE);
                break;
            case R.id.about:
                showAboutDialog();
                break;
            default:
                return super.onOptionsItemSelected(item);
        }
        return true;
    }

    private void showAboutDialog()
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

        Intent intent = new Intent(this, About.class);
        intent.putExtra(Intent.EXTRA_TEXT, html.toString());
        startActivity(intent);
    }

    /**
     * The <tt>VideoBridgeProviderMenuItem</tt> for each protocol provider.
     */
    private class VideoBridgeProviderMenuItem
    {
        private ProtocolProviderService preselectedProvider;
        private List<ProtocolProviderService> videoBridgeProviders;

        /**
         * Creates an instance of <tt>VideoBridgeProviderMenuItem</tt>
         * <p>
         * // @param preselectedProvider
         * the <tt>ProtocolProviderService</tt> that provides the video bridge
         */
        public VideoBridgeProviderMenuItem()
        {
            preselectedProvider = null;
            videoBridgeProviders = null;
        }

        /**
         * Opens a conference invite dialog when this menu is selected.
         */
        public void actionPerformed()
        {
            ConferenceInviteDialog inviteDialog = null;

            if (preselectedProvider != null)
                inviteDialog = new ConferenceInviteDialog(mContext, preselectedProvider, true);
            else if (videoBridgeProviders != null)
                inviteDialog = new ConferenceInviteDialog(mContext, videoBridgeProviders, true);

            if (inviteDialog != null)
                inviteDialog.show();
        }

        public void setPreselectedProvider(ProtocolProviderService protocolProvider)
        {
            this.preselectedProvider = protocolProvider;
        }

        public void setVideoBridgeProviders(List<ProtocolProviderService> videoBridgeProviders)
        {
            this.videoBridgeProviders = videoBridgeProviders;
        }
    }

    /**
     * Returns a list of all available video bridge providers.
     *
     * @return a list of all available video bridge providers
     */
    private List<ProtocolProviderService> getVideoBridgeProviders()
    {
        List<ProtocolProviderService> activeBridgeProviders = new ArrayList<>();

        for (ProtocolProviderService videoBridgeProvider
                : AccountUtils.getRegisteredProviders(OperationSetVideoBridge.class)) {
            OperationSetVideoBridge videoBridgeOpSet
                    = videoBridgeProvider.getOperationSet(OperationSetVideoBridge.class);

            // Check if the video bridge is actually active before adding it to the list of
            // active providers.
            if (videoBridgeOpSet.isActive())
                activeBridgeProviders.add(videoBridgeProvider);
        }
        return activeBridgeProviders;
    }

    /**
     * Implements the <tt>ServiceListener</tt> method. Verifies whether the passed event concerns
     * a <tt>ProtocolProviderService</tt> and adds the corresponding UI controls in the menu.
     *
     * @param event
     *         The <tt>ServiceEvent</tt> object.
     */
    public void serviceChanged(ServiceEvent event)
    {
        ServiceReference serviceRef = event.getServiceReference();

        // if the event is caused by a bundle being stopped, we don't want to know
        if (serviceRef.getBundle().getState() == STOPPING) {
            return;
        }

        Object service = AndroidGUIActivator.bundleContext.getService(serviceRef);

        // we don't care if the source service is not a protocol provider
        if (!(service instanceof ProtocolProviderService)) {
            return;
        }

        switch (event.getType()) {
            case ServiceEvent.REGISTERED:
            case ServiceEvent.UNREGISTERING:
                OSGiActivity.uiHandler.post(new Runnable()
                {
                    @Override
                    public void run()
                    {
                        if (videoBridgeMenuItem != null) {
                            initVideoBridge();
                        }
                    }
                });
                break;
        }
    }

    @Override
    public void contactPresenceStatusChanged(final ContactPresenceStatusChangeEvent evt)
    {
        // cmeng - how to add the listener onResume - multiple protocol providers???
        OSGiActivity.uiHandler.post(new Runnable()
        {
            @Override
            public void run()
            {
                Contact sourceContact = evt.getSourceContact();
                if (videoBridgeMenuItem != null) {
                    initVideoBridge();
                }
            }
        });
    }
}
