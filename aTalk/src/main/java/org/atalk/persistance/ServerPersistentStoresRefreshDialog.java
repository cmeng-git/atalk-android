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
package org.atalk.persistance;

import android.app.Activity;
import android.content.Context;
import android.os.*;
import android.view.*;
import android.widget.CheckBox;

import net.java.sip.communicator.impl.protocol.jabber.*;
import net.java.sip.communicator.service.protocol.*;
import net.java.sip.communicator.util.Logger;
import net.java.sip.communicator.util.account.AccountUtils;

import org.atalk.android.*;
import org.atalk.android.gui.dialogs.DialogActivity;
import org.atalk.android.gui.util.AndroidUtils;
import org.atalk.android.gui.util.event.EventListener;
import org.atalk.android.util.FileAccess;
import org.atalk.crypto.omemo.SQLiteOmemoStore;
import org.atalk.persistance.migrations.MigrationTo2;
import org.atalk.service.fileaccess.FileCategory;
import org.atalk.service.libjitsi.LibJitsi;
import org.atalk.service.osgi.OSGiFragment;
import org.jivesoftware.smackx.avatar.vcardavatar.VCardAvatarManager;
import org.jivesoftware.smackx.caps.EntityCapsManager;
import org.jivesoftware.smackx.omemo.OmemoConfiguration;
import org.jivesoftware.smackx.omemo.OmemoService;
import org.jivesoftware.smackx.omemo.OmemoStore;

import java.io.*;
import java.util.Collection;

import static net.java.sip.communicator.plugin.loggingutils.LogsCollector.LOGGING_DIR_NAME;
import static org.atalk.android.R.id.*;

/**
 * Dialog allowing user to refresh persistent stores.
 *
 * @author Eng Chong Meng
 */
public class ServerPersistentStoresRefreshDialog extends OSGiFragment
{
	/**
	 * The logger
	 */
	private final static Logger logger = Logger.getLogger(ServerPersistentStoresRefreshDialog.class);

	/**
	 * {@inheritDoc}
	 */
	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			Bundle savedInstanceState)
	{
		View view = inflater.inflate(R.layout.refresh_persistent_stores, container, false);
		if (BuildConfig.DEBUG) {
			view.findViewById(R.id.cb_export_database).setVisibility(View.VISIBLE);
			// view.findViewById(R.id.cb_del_database).setVisibility(View.VISIBLE);
		}
		return view;
	}

	/**
	 * Displays create refresh store dialog. If the source wants to be notified about the result
	 * should pass the listener here or <tt>null</tt> otherwise.
	 *
	 * @param parent
	 * 		the parent <tt>Activity</tt>
	 * @param createListener
	 * 		listener for contact group created event that will receive newly created instance of
	 * 		the contact group or <tt>null</tt> in case user cancels the dialog.
	 */
	public static void showCreateGroupDialog(Activity parent,
			EventListener<ProtocolProviderService> createListener)
	{
		DialogActivity.showCustomDialog(parent,
				parent.getString(R.string.service_gui_REFRESH_STORES),
				ServerPersistentStoresRefreshDialog.class.getName(), null,
				parent.getString(R.string.service_gui_REFRESH_APPLY),
				new DialogListenerImpl(createListener), null);
	}

	/**
	 * Implements <tt>DialogActivity.DialogListener</tt> interface and handles refresh stores
	 * process.
	 */
	static class DialogListenerImpl implements DialogActivity.DialogListener
	{
		/**
		 * Contact created event listener.
		 */
		private final EventListener<ProtocolProviderService> listener;

		/**
		 * Creates new instance of <tt>DialogListenerImpl</tt>.
		 *
		 * @param createListener
		 * 		create group listener if any.
		 */
		private DialogListenerImpl(EventListener<ProtocolProviderService> createListener)
		{
			this.listener = createListener;
		}

		@Override
		public boolean onConfirmClicked(DialogActivity dialog)
		{
			View view = dialog.getContentFragment().getView();
			CheckBox cbRoster = (CheckBox) view.findViewById(cb_roster);
			CheckBox cbCaps = (CheckBox) view.findViewById(cb_caps);
			CheckBox cbDiscoInfo = (CheckBox) view.findViewById(cb_discoInfo);
			CheckBox cbAvatar = (CheckBox) view.findViewById(cb_avatar);
			CheckBox cbOmemo = (CheckBox) view.findViewById(cb_omemo);
			CheckBox cbDebugLog = (CheckBox) view.findViewById(cb_debug_log);
			CheckBox cbExportDB = (CheckBox) view.findViewById(cb_export_database);
			CheckBox cbDeleteDB = (CheckBox) view.findViewById(cb_del_database);

			if (cbRoster.isChecked()) {
				refreshRosterStore();
			}
			if (cbCaps.isChecked()) {
				refreshCapsStore();
			}
			if (cbDiscoInfo.isChecked()) {
				refreshDiscoInfoStore();
			}
			if (cbAvatar.isChecked()) {
				purgeAvatarStorage();
			}
			if (cbOmemo.isChecked()) {
				purgeOmemoStorage();
			}
			if (cbDebugLog.isChecked()) {
				purgeDebugLog();
			}
			if (cbExportDB.isChecked()) {
				exportDB();
			}
			if (cbDeleteDB.isChecked()) {
				deleteDB();
			}
			return true;
		}

		/**
		 * Shows given error message as an alert.
		 *
		 * @param errorMessage
		 * 		the error message to show.
		 */
		private void showErrorMessage(String errorMessage)
		{
			Context ctx = aTalkApp.getGlobalContext();
			AndroidUtils.showAlertDialog(ctx, ctx.getString(R.string.service_gui_ERROR),
					errorMessage);
		}

		@Override
		public void onDialogCancelled(DialogActivity dialog)
		{
		}
	}

	/**
	 * Process to refresh roster store for each registered account
	 * Persistent Store for XEP-0237:Roster Versioning
	 */
	private static void refreshRosterStore()
	{
		Collection<ProtocolProviderService> ppServices = AccountUtils.getRegisteredProviders();
		for (ProtocolProviderService pps : ppServices) {
			ProtocolProviderServiceJabberImpl jabberProvider
					= (ProtocolProviderServiceJabberImpl) pps;

			File rosterStoreDirectory = jabberProvider.getRosterStoreDirectory();
			try {
				FileAccess.delete(rosterStoreDirectory.getAbsolutePath());
			}
			catch (IOException e) {
				logger.error("Failed to purchase store for: "
						+ R.string.service_gui_REFRESH_STORES_ROSTER);
			}
			jabberProvider.initRosterStore();
		}
	}

	/**
	 * Process to refresh the single Entity Capabilities store for all accounts
	 * Persistent Store for XEP-0115:Entity Capabilities
	 */
	private static void refreshCapsStore()
	{
		// stop roster from accessing the store
		EntityCapsManager.setPersistentCache(null);
		EntityCapsManager.clearMemoryCache();

		File entityStoreDirectory = ScServiceDiscoveryManager.getEntityPersistentStore();
		try {
			FileAccess.delete(entityStoreDirectory.getAbsolutePath());
		}
		catch (IOException e) {
			logger.error("Failed to purchase store for: "
					+ R.string.service_gui_REFRESH_STORES_CAPS);
		}
		ScServiceDiscoveryManager.initEntityPersistentStore();
	}

	/**
	 * Process to refresh Disco#info store for each accounts
	 * Persistent Store for XEP-0030:Service Discovery
	 */
	private static void refreshDiscoInfoStore()
	{
		Collection<ProtocolProviderService> ppServices = AccountUtils.getRegisteredProviders();
		for (ProtocolProviderService pps : ppServices) {
			ProtocolProviderServiceJabberImpl jabberProvider
					= (ProtocolProviderServiceJabberImpl) pps;

			ScServiceDiscoveryManager discoveryInfoManager = jabberProvider.getDiscoveryManager();
			if (jabberProvider.isRegistered()) {
				if (RegistrationState.REGISTERED.equals(jabberProvider.getRegistrationState())) {
					// stop discoveryInfoManager from accessing the store
					discoveryInfoManager.setDiscoInfoPersistentStore(null);
					discoveryInfoManager.clearDiscoInfoPersistentCache();
				}
			}

			File discoInfoStoreDirectory = discoveryInfoManager.getDiscoInfoPersistentStore();
			// discoInfoStoreDirectory.listFiles();
			try {
				FileAccess.delete(discoInfoStoreDirectory.getAbsolutePath());
			}
			catch (IOException e) {
				logger.error("Failed to purchase store for: "
						+ R.string.service_gui_REFRESH_STORES_DISCINFO);
			}
			discoveryInfoManager.initDiscoInfoPersistentStore();
		}
	}

	/**
	 * Process to clear the VCard Avatar Index and purge persistent storage for all accounts
	 * XEP-0153: vCard-Based Avatars
	 */
	private static void purgeAvatarStorage()
	{
		VCardAvatarManager.clearPersistentStorage();
	}

	/**
	 * Process to purge persistent storage for OMEMO_Store
	 * XEP-0384: OMEMO Encryption
	 */
	public static void purgeOmemoStorage()
	{
		// accountID omemo key attributes
		String JSONKEY_REGISTRATION_ID = "omemoRegId";
		String JSONKEY_CURRENT_PREKEY_ID = "omemoCurPreKeyId";
		Context ctx = aTalkApp.getGlobalContext();

        OmemoStore omemoStore = OmemoService.getInstance().getOmemoStoreBackend();
        Collection<ProtocolProviderService> ppServices = AccountUtils.getRegisteredProviders();
   		if (omemoStore instanceof SQLiteOmemoStore) {
			DatabaseBackend db = DatabaseBackend.getInstance(ctx);
			for (ProtocolProviderService pps : ppServices) {
				AccountID accountId = pps.getAccountID();
				accountId.unsetKey(JSONKEY_CURRENT_PREKEY_ID);
				accountId.unsetKey(JSONKEY_REGISTRATION_ID);
				db.updateAccount(accountId);
			}
			MigrationTo2.createOmemoTables(db.getWritableDatabase());
		}
		else {
			String OMEMO_Store = "OMEMO_Store";
			String filesDir = ctx.getFilesDir().getAbsolutePath();
			String omemoDir = filesDir + "/" + OMEMO_Store;

			if ((new File(omemoDir)).exists()) {
				try {
					FileAccess.delete(omemoDir);
				}
				catch (IOException e) {
					e.printStackTrace();
				}
			}
		}

		// start to regenerate all Omemo data for registered accounts - has exception
		// SQLiteOmemoStore.loadOmemoSignedPreKey().371 There is no SignedPreKeyRecord for: 0
		// SignedPreKeyRecord.getKeyPair()' on a null object reference
		for (ProtocolProviderService pps : ppServices) {
            AccountID accountId = pps.getAccountID();
            ((SQLiteOmemoStore)omemoStore).regenerate(accountId);
		}
		logger.info("### Omemo store has been refreshed!");
	}

	private static void exportDB()
	{
		String clFileName = "contactlist.xml";
		String filePath = "/aTalk";
		String filesSD = "files";
		String OMEMO_Store = "OMEMO_Store";
		String database = "databases";
		String sharedPrefs = "shared_prefs";
		String history = "history_ver1.0";

		Context ctx = aTalkApp.getGlobalContext();
		String filesDir = ctx.getFilesDir().getAbsolutePath();
		String appDBDir = filesDir.replace(filesSD, database);
		String appSPDir = filesDir.replace(filesSD, sharedPrefs);
		String historyDir = filesDir + "/" + history;
		String omemoDir = filesDir + "/" + OMEMO_Store;

		File historyFP = new File(historyDir);
		File xmlFP = new File(ctx.getFilesDir() + "/" + clFileName);

		File atalkDLDir = new File(Environment
				.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS) + filePath);
		String atalkDLPath = atalkDLDir.getAbsolutePath();

		try {
			if (atalkDLDir.exists())
				FileAccess.delete(atalkDLPath);

			if (!atalkDLDir.exists() && !atalkDLDir.mkdirs()) {
				logger.error("Could not create atalk dir: " + atalkDLPath);
			}

			// To copy everything under files (large amount of data).
			// FileAccess.copy(filesDir, atalkDLPath, filesSD);

			FileAccess.copy(appDBDir, atalkDLPath, database);
			FileAccess.copy(appSPDir, atalkDLPath, sharedPrefs);

			if ((new File(omemoDir)).exists()) {
				FileAccess.copy(omemoDir, atalkDLPath, OMEMO_Store);
				// cmeng - To clean up Note3 when move to SQLite database
				//	FileAccess.delete(omemoDir);
			}
			if (historyFP.exists())
				FileAccess.copy(historyDir, atalkDLPath, history);
			if (xmlFP.exists())
				FileAccess.copy(xmlFP.getAbsolutePath(), atalkDLPath, clFileName);
		}
		catch (Exception e) {
			logger.warn("Export database exception: " + e.getMessage());
		}
	}

	public static void deleteDB()
	{
		Context ctx = aTalkApp.getGlobalContext();
		ctx.deleteDatabase(DatabaseBackend.DATABASE_NAME);
	}

	/**
	 * Process to purge all debug log files in case it gets too large to handle
	 */
	public static void purgeDebugLog()
	{
		File logDir;
		try {
			logDir = LibJitsi.getFileAccessService()
					.getPrivatePersistentDirectory(LOGGING_DIR_NAME, FileCategory.LOG);
			if (logDir.exists()) {
				File[] files = logDir.listFiles();
				boolean status = true;
				for (File file : files) {
					status = file.delete();
				}
			}
		}
		catch (Exception ex) {
			logger.error("Couldn't delete log file directory.", ex);
		}
	}
}
