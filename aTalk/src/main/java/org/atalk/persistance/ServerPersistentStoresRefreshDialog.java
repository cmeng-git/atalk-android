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

import static net.java.sip.communicator.plugin.loggingutils.LogsCollector.LOGGING_DIR_NAME;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;

import java.io.File;
import java.io.IOException;
import java.util.Collection;

import net.java.sip.communicator.impl.protocol.jabber.ProtocolProviderServiceJabberImpl;
import net.java.sip.communicator.impl.protocol.jabber.ServiceDiscoveryHelper;
import net.java.sip.communicator.service.protocol.AccountID;
import net.java.sip.communicator.service.protocol.ProtocolProviderService;
import net.java.sip.communicator.util.account.AccountUtils;

import org.atalk.android.BaseFragment;
import org.atalk.android.BuildConfig;
import org.atalk.android.R;
import org.atalk.android.aTalkApp;
import org.atalk.android.gui.dialogs.DialogActivity;
import org.atalk.crypto.omemo.SQLiteOmemoStore;
import org.atalk.persistance.migrations.MigrationTo2;
import org.atalk.service.fileaccess.FileCategory;
import org.atalk.service.libjitsi.LibJitsi;
import org.jivesoftware.smackx.avatar.vcardavatar.VCardAvatarManager;
import org.jivesoftware.smackx.omemo.OmemoService;
import org.jivesoftware.smackx.omemo.OmemoStore;

import timber.log.Timber;

/**
 * Dialog allowing user to refresh persistent stores.
 *
 * @author Eng Chong Meng
 */
public class ServerPersistentStoresRefreshDialog extends BaseFragment {
    /**
     * {@inheritDoc}
     */
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.refresh_persistent_stores, container, false);
        if (BuildConfig.DEBUG) {
            view.findViewById(R.id.cb_export_database).setVisibility(View.VISIBLE);
            // view.findViewById(R.id.cb_del_database).setVisibility(View.VISIBLE);
        }
        return view;
    }

    /**
     * Displays create refresh store dialog. If the source wants to be notified about the result
     * should pass the listener here or <code>null</code> otherwise.
     *
     * @param parent the parent <code>Activity</code>
     */
    public void show(Activity parent) {
        DialogActivity.showCustomDialog(parent,
                parent.getString(R.string.refresh_store),
                ServerPersistentStoresRefreshDialog.class.getName(), null,
                parent.getString(R.string.refresh_apply),
                new DialogListenerImpl(), null);
    }

    /**
     * Implements <code>DialogActivity.DialogListener</code> interface and handles refresh stores process.
     */
    class DialogListenerImpl implements DialogActivity.DialogListener {
        @Override
        public boolean onConfirmClicked(DialogActivity dialog) {
            View view = dialog.getContentFragment().getView();
            if (view == null)
                return false;

            CheckBox cbRoster = view.findViewById(R.id.cb_roster);
            CheckBox cbCaps = view.findViewById(R.id.cb_caps);
            CheckBox cbAvatar = view.findViewById(R.id.cb_avatar);
            CheckBox cbOmemo = view.findViewById(R.id.cb_omemo);
            CheckBox cbDebugLog = view.findViewById(R.id.cb_debug_log);
            CheckBox cbExportDB = view.findViewById(R.id.cb_export_database);
            CheckBox cbDeleteDB = view.findViewById(R.id.cb_del_database);

            if (cbRoster.isChecked()) {
                refreshRosterStore();
            }
            if (cbCaps.isChecked()) {
                ServiceDiscoveryHelper.refreshEntityCapsStore();
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

        @Override
        public void onDialogCancelled(DialogActivity dialog) {
        }
    }

    /**
     * Process to refresh roster store for each registered account
     * Persistent Store for XEP-0237:Roster Versioning
     */
    private void refreshRosterStore() {
        Collection<ProtocolProviderService> ppServices = AccountUtils.getRegisteredProviders();
        for (ProtocolProviderService pps : ppServices) {
            ProtocolProviderServiceJabberImpl jabberProvider = (ProtocolProviderServiceJabberImpl) pps;

            File rosterStoreDirectory = jabberProvider.getRosterStoreDirectory();
            if ((rosterStoreDirectory != null) && rosterStoreDirectory.exists()) {
                try {
                    FileBackend.deleteRecursive(rosterStoreDirectory);
                } catch (IOException e) {
                    Timber.e("Failed to purge store for: %s", R.string.refresh_store_roster);
                }
                jabberProvider.initRosterStore();
            }
        }
    }

    /**
     * Process to clear the VCard Avatar Index and purge persistent storage for all accounts
     * XEP-0153: vCard-Based Avatars
     */
    private void purgeAvatarStorage() {
        VCardAvatarManager.clearPersistentStorage();
    }

    /**
     * Process to purge persistent storage for OMEMO_Store
     * XEP-0384: OMEMO Encryption
     */
    private void purgeOmemoStorage() {
        // accountID omemo key attributes
        String JSONKEY_REGISTRATION_ID = "omemoRegId";
        String JSONKEY_CURRENT_PREKEY_ID = "omemoCurPreKeyId";
        Context ctx = aTalkApp.getInstance();

        OmemoStore<?, ?, ?, ?, ?, ?, ?, ?, ?> omemoStore = OmemoService.getInstance().getOmemoStoreBackend();
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

            // start to regenerate all Omemo data for registered accounts - has exception
            // SQLiteOmemoStore.loadOmemoSignedPreKey().371 There is no SignedPreKeyRecord for: 0
            // SignedPreKeyRecord.getKeyPair()' on a null object reference
            for (ProtocolProviderService pps : ppServices) {
                AccountID accountId = pps.getAccountID();
                ((SQLiteOmemoStore) omemoStore).regenerate(accountId);
            }
        }

        // This is here for file-based implementation and not use anymore
        else {
            String OMEMO_Store = "OMEMO_Store";
            File omemoDir = new File(ctx.getFilesDir(), OMEMO_Store);
            if (omemoDir.exists()) {
                try {
                    FileBackend.deleteRecursive(omemoDir);
                } catch (IOException e) {
                    Timber.w("Exception %s", e.getMessage());
                }
            }
        }
        Timber.i("### Omemo store has been refreshed!");
    }

    private void exportDB() {
        String clFileName = "contactlist.xml";
        String OMEMO_Store = "OMEMO_Store";
        String database = "databases";
        String sharedPrefs = "shared_prefs";
        String history = "history_ver1.0";

        File appFilesDir = aTalkApp.getInstance().getFilesDir();
        File appRootDir = appFilesDir.getParentFile();

        File appDBDir = new File(appRootDir, database);
        File appSPDir = new File(appRootDir, sharedPrefs);
        File appHistoryDir = new File(appFilesDir, history);
        File appOmemoDir = new File(appFilesDir, OMEMO_Store);
        File appXmlFP = new File(appRootDir, clFileName);

        File atalkExportDir = FileBackend.getaTalkStore(FileBackend.EXPROT_DB, true);
        try {
            // Clean up old contents before create new
            FileBackend.deleteRecursive(atalkExportDir);
            if (!atalkExportDir.mkdirs()) {
                Timber.e("Could not create atalk dir: %s", atalkExportDir);
            }
            // To copy everything under files (large amount of data).
            // FileBackend.copyRecursive(appDBDir, atalkDLDir, null);

            FileBackend.copyRecursive(appDBDir, atalkExportDir, database);
            FileBackend.copyRecursive(appSPDir, atalkExportDir, sharedPrefs);

            if (appOmemoDir.exists()) {
                FileBackend.copyRecursive(appOmemoDir, atalkExportDir, OMEMO_Store);
            }
            if (appHistoryDir.exists()) {
                FileBackend.copyRecursive(appHistoryDir, atalkExportDir, history);
            }
            if (appXmlFP.exists()) {
                FileBackend.copyRecursive(appXmlFP, atalkExportDir, clFileName);
            }
        } catch (Exception e) {
            Timber.w("Export database exception: %s", e.getMessage());
        }
    }

    /**
     * Warn: Delete the aTalk dataBase
     * Static access from other module
     */
    public static void deleteDB() {
        Context ctx = aTalkApp.getInstance();
        ctx.deleteDatabase(DatabaseBackend.DATABASE_NAME);
    }

    /**
     * Process to purge all debug log files in case it gets too large to handle
     * Static access from other module
     */
    public static void purgeDebugLog() {
        File logDir;
        try {
            logDir = LibJitsi.getFileAccessService().getPrivatePersistentDirectory(LOGGING_DIR_NAME, FileCategory.LOG);
            if ((logDir != null) && logDir.exists()) {
                final File[] files = logDir.listFiles();
                for (File file : files) {
                    if (!file.delete())
                        Timber.w("Couldn't delete log file: %s", file.getName());
                }
            }
        } catch (Exception ex) {
            Timber.e(ex, "Couldn't delete log file directory.");
        }
    }
}
