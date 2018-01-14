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

package org.atalk.android.gui.menu;

import android.app.ProgressDialog;
import android.content.Context;
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
                startActivity(About.class);
                // showAboutDialog();
                break;
            default:
                return super.onOptionsItemSelected(item);
        }
        return true;
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

        // we don't care if the source service is not a protocol provider
        Object service = AndroidGUIActivator.bundleContext.getService(serviceRef);
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
