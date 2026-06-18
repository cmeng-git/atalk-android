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

import android.annotation.SuppressLint;
import android.app.SearchManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.TextView;

import androidx.appcompat.widget.SearchView;
import androidx.fragment.app.Fragment;

import net.java.sip.communicator.service.protocol.Contact;
import net.java.sip.communicator.service.protocol.ProtocolProviderService;
import net.java.sip.communicator.service.protocol.event.ContactPresenceStatusChangeEvent;
import net.java.sip.communicator.service.protocol.event.ContactPresenceStatusListener;
import net.java.sip.communicator.service.protocol.globalstatus.GlobalStatusEnum;
import net.java.sip.communicator.service.protocol.globalstatus.GlobalStatusService;
import net.java.sip.communicator.util.ConfigurationUtils;

import org.atalk.android.BaseActivity;
import org.atalk.android.R;
import org.atalk.android.gui.AppGUIActivator;
import org.atalk.android.gui.aTalk;
import org.atalk.android.gui.account.AccountsListActivity;
import org.atalk.android.gui.actionbar.ActionBarUtil;
import org.atalk.android.gui.call.telephony.TelephonyFragment;
import org.atalk.android.gui.chatroomslist.ChatRoomBookmarksDialog;
import org.atalk.android.gui.chatroomslist.ChatRoomCreateDialog;
import org.atalk.android.gui.contactlist.AddContactActivity;
import org.atalk.android.gui.contactlist.ContactBlockListActivity;
import org.atalk.android.gui.contactlist.ContactListFragment;
import org.atalk.android.gui.contactlist.model.MetaContactListAdapter;
import org.atalk.android.gui.settings.SettingsActivity;
import org.atalk.android.plugin.geolocation.GeoLocationActivity;
import org.atalk.android.plugin.permissions.PermissionsActivity;
import org.atalk.android.plugin.textspeech.TTSActivity;
import org.atalk.impl.osgi.framework.BundleImpl;

import org.osgi.framework.ServiceEvent;
import org.osgi.framework.ServiceListener;
import org.osgi.framework.ServiceReference;

/**
 * The main options menu. Every <code>Activity</code> that desires to have the general options menu
 * shown have to extend this class.
 * The <code>MainMenuActivity</code> is an <code>OSGiActivity</code>.
 *
 * @author Eng Chong Meng
 */
@SuppressLint("Registered")
public class MainMenuActivity extends ExitMenuActivity implements ServiceListener, ContactPresenceStatusListener {
    /**
     * Common options menu items.
     */
    protected MenuItem mShowHideOffline;
    protected MenuItem mOnOffLine;
    protected TelephonyFragment mTelephony = null;

    public Context mContext;

    public static boolean disableMediaServiceOnFault = false;

    /*
     * The {@link CallConference} instance depicted by this <code>CallPanel</code>.
     */
    // private final CallConference callConference = null;
    // private ProtocolProviderService preselectedProvider = null;

    /**
     * Called when the activity is starting. Initializes the corresponding call interface.
     *
     * @param savedInstanceState If the activity is being re-initialized after previously being shut down then this
     * Bundle contains the data it most recently supplied in onSaveInstanceState(Bundle).
     * Note: Otherwise it is null.
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mContext = this;
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (AppGUIActivator.bundleContext != null) {
            AppGUIActivator.bundleContext.addServiceListener(this);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        // FFR v3.0.5: NullPointerException; may have stop() in AppGUIActivator
        if (AppGUIActivator.bundleContext != null)
            AppGUIActivator.bundleContext.removeServiceListener(this);
    }

    /**
     * Invoked when the options menu is created. Creates our own options menu from the corresponding xml.
     *
     * @param menu the options menu
     */
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.main_menu, menu);
        menu.findItem(R.id.add_contact).setVisible(!ConfigurationUtils.isAddContactDisabled());

        // Get the SearchView and set the search theme
        SearchManager searchManager = (SearchManager) getSystemService(Context.SEARCH_SERVICE);
        MenuItem searchItem = menu.findItem(R.id.search);

        SearchView searchView = (SearchView) searchItem.getActionView();
        if (searchView != null) {
            searchView.setSearchableInfo(searchManager.getSearchableInfo(getComponentName()));
            TextView textView = searchView.findViewById(R.id.search_src_text);
            textView.setTextColor(getResources().getColor(R.color.white, null));
            textView.setHintTextColor(getResources().getColor(R.color.white, null));
            textView.setHint(R.string.enter_name_or_number);
        }

        mShowHideOffline = menu.findItem(R.id.show_hide_offline);
        int itemId = ConfigurationUtils.isShowOffline()
                ? R.string.contact_offline_hide
                : R.string.contact_offline_show;
        mShowHideOffline.setTitle(itemId);

        mOnOffLine = menu.findItem(R.id.sign_in_off);
        boolean isOffline = GlobalStatusEnum.OFFLINE_STATUS.equals(ActionBarUtil.getStatus(this));
        itemId = isOffline ? R.string.sign_in : R.string.sign_out;
        mOnOffLine.setTitle(itemId);
        mOnOffLine.setVisible(isOffline);

        // Adds exit option from super class
        super.onCreateOptionsMenu(menu);
        return true;
    }

    public MenuItem getMenuItemOnOffLine() {
        return mOnOffLine;
    }

    /**
     * Invoked when an options item has been selected.
     *
     * @param item the item that has been selected
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle item selection
        switch (item.getItemId()) {
        case R.id.search:
            break;
        case R.id.add_chat_room:
            ChatRoomCreateDialog chatRoomCreateDialog = new ChatRoomCreateDialog(this);
            chatRoomCreateDialog.show();
            break;
        case R.id.show_location:
            Intent intent = new Intent(this, GeoLocationActivity.class);
            intent.putExtra(GeoLocationActivity.SHARE_ALLOW, false);
            startActivity(intent);
            break;
        case R.id.telephony:
            mTelephony = new TelephonyFragment();
            getSupportFragmentManager().beginTransaction()
                    .replace(android.R.id.content, mTelephony, TelephonyFragment.TELEPHONY_TAG).commit();
            break;
        case R.id.muc_bookmarks:
            ChatRoomBookmarksDialog chatRoomBookmarksDialog = new ChatRoomBookmarksDialog(this);
            chatRoomBookmarksDialog.show();
            break;
        case R.id.add_contact:
            startActivity(AddContactActivity.class);
            break;
        case R.id.block_list:
            startActivity(ContactBlockListActivity.class);
            break;
        case R.id.main_settings:
            startActivity(SettingsActivity.class);
            break;
        case R.id.account_settings:
            startActivity(AccountsListActivity.class);
            break;
        case R.id.app_info:
            PermissionsActivity.onAppInfoButtonClicked(this);
            break;
        case R.id.tts_settings:
            Intent ttsIntent = new Intent(this, TTSActivity.class);
            startActivity(ttsIntent);
            break;

        case R.id.show_hide_offline:
            boolean isShowOffline = !ConfigurationUtils.isShowOffline(); // toggle
            MetaContactListAdapter.presenceFilter.setShowOffline(isShowOffline);
            Fragment clf = aTalk.getFragment(aTalk.CL_FRAGMENT);
            if (clf instanceof ContactListFragment) {
                MetaContactListAdapter contactListAdapter = ((ContactListFragment) clf).getContactListAdapter();
                contactListAdapter.filterData("");
            }
            int itemId = isShowOffline
                    ? R.string.contact_offline_hide
                    : R.string.contact_offline_show;
            mShowHideOffline.setTitle(itemId);
            break;

        case R.id.notification_setting:
            openNotificationSettings();
            break;

        case R.id.sign_in_off:
            // Toggle current account presence status
            boolean isOffline = GlobalStatusEnum.OFFLINE_STATUS.equals(ActionBarUtil.getStatus(this));
            GlobalStatusService globalStatusService = AppGUIActivator.getGlobalStatusService();
            if (isOffline)
                globalStatusService.publishStatus(GlobalStatusEnum.ONLINE);
            else
                globalStatusService.publishStatus(GlobalStatusEnum.OFFLINE);
            break;

        default:
            return super.onOptionsItemSelected(item);
        }
        return true;
    }

    /**
     * Start the application notification settings page
     */
    private void openNotificationSettings() {
        Intent intent = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS);
        intent.putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName());
        startActivity(intent);
    }

    //========================================================

    /**
     * Implements the <code>ServiceListener</code> method. Verifies whether the passed event concerns
     * a <code>ProtocolProviderService</code> and adds the corresponding UI controls in the menu.
     *
     * @param event The <code>ServiceEvent</code> object.
     */
    public void serviceChanged(ServiceEvent event) {
        ServiceReference serviceRef = event.getServiceReference();

        // Timber.d("Bundle State: %s: ", serviceRef.getBundle().getState());
        // if the event is caused by a bundle being stopped, we don't want to know
        if (serviceRef.getBundle().getState() == BundleImpl.STOPPING) {
            return;
        }

        // we don't care if the source service is not a protocol provider
        Object service = AppGUIActivator.bundleContext.getService(serviceRef);
        if (!(service instanceof ProtocolProviderService)) {
            return;
        }

        switch (event.getType()) {
        case ServiceEvent.REGISTERED:
        case ServiceEvent.UNREGISTERING:
            break;
        }
    }

    @Override
    public void contactPresenceStatusChanged(final ContactPresenceStatusChangeEvent evt) {
        // cmeng - how to add the listener onResume - multiple protocol providers???
        BaseActivity.uiHandler.post(() -> {
            Contact sourceContact = evt.getSourceContact();
        });
    }
}
