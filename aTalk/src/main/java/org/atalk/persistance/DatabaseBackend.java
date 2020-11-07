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

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.text.TextUtils;
import android.util.Base64;

import net.java.sip.communicator.impl.configuration.SQLiteConfigurationStore;
import net.java.sip.communicator.impl.msghistory.MessageSourceService;
import net.java.sip.communicator.service.callhistory.CallHistoryService;
import net.java.sip.communicator.service.contactlist.MetaContactGroup;
import net.java.sip.communicator.service.protocol.*;

import org.apache.commons.lang3.StringUtils;
import org.atalk.android.*;
import org.atalk.android.gui.chat.*;
import org.atalk.crypto.omemo.FingerprintStatus;
import org.atalk.crypto.omemo.SQLiteOmemoStore;
import org.atalk.persistance.migrations.Migrations;
import org.atalk.persistance.migrations.MigrationsHelper;
import org.jivesoftware.smackx.omemo.OmemoManager;
import org.jivesoftware.smackx.omemo.exceptions.CorruptedOmemoKeyException;
import org.jivesoftware.smackx.omemo.internal.OmemoCachedDeviceList;
import org.jivesoftware.smackx.omemo.internal.OmemoDevice;
import org.jxmpp.jid.BareJid;
import org.jxmpp.jid.impl.JidCreate;
import org.jxmpp.stringprep.XmppStringprepException;
import org.whispersystems.libsignal.*;
import org.whispersystems.libsignal.state.*;

import java.io.IOException;
import java.util.*;

import timber.log.Timber;

/**
 * The <tt>DatabaseBackend</tt> uses SQLite to store all the aTalk application data in the database "dbRecords.db"
 *
 * @author Eng Chong Meng
 */
public class DatabaseBackend extends SQLiteOpenHelper
{
    /**
     * Name of the database and its version number
     * Increment DATABASE_VERSION when there is a change in database records
     */
    public static final String DATABASE_NAME = "dbRecords.db";
    private static final int DATABASE_VERSION = 4;
    private static DatabaseBackend instance = null;
    private ProtocolProviderService mProvider;

    // Create preKeys table
    public static String CREATE_OMEMO_DEVICES_STATEMENT = "CREATE TABLE "
            + SQLiteOmemoStore.OMEMO_DEVICES_TABLE_NAME + "("
            + SQLiteOmemoStore.OMEMO_JID + " TEXT, "
            + SQLiteOmemoStore.OMEMO_REG_ID + " INTEGER, "
            + SQLiteOmemoStore.CURRENT_SIGNED_PREKEY_ID + " INTEGER, "
            + SQLiteOmemoStore.LAST_PREKEY_ID + " INTEGER, UNIQUE("
            + SQLiteOmemoStore.OMEMO_JID + ", " + SQLiteOmemoStore.OMEMO_REG_ID
            + ") ON CONFLICT REPLACE);";

    // Create preKeys table
    public static String CREATE_PREKEYS_STATEMENT = "CREATE TABLE "
            + SQLiteOmemoStore.PREKEY_TABLE_NAME + "("
            + SQLiteOmemoStore.BARE_JID + " TEXT, "
            + SQLiteOmemoStore.DEVICE_ID + " INTEGER, "
            + SQLiteOmemoStore.PRE_KEY_ID + " INTEGER, "
            + SQLiteOmemoStore.PRE_KEYS + " TEXT, UNIQUE("
            + SQLiteOmemoStore.BARE_JID + ", " + SQLiteOmemoStore.DEVICE_ID + ", "
            + SQLiteOmemoStore.PRE_KEY_ID
            + ") ON CONFLICT REPLACE);";

    // Create signed preKeys table
    public static String CREATE_SIGNED_PREKEYS_STATEMENT = "CREATE TABLE "
            + SQLiteOmemoStore.SIGNED_PREKEY_TABLE_NAME + "("
            + SQLiteOmemoStore.BARE_JID + " TEXT, "
            + SQLiteOmemoStore.DEVICE_ID + " INTEGER, "
            + SQLiteOmemoStore.SIGNED_PRE_KEY_ID + " INTEGER, "
            + SQLiteOmemoStore.SIGNED_PRE_KEYS + " TEXT, "
            + SQLiteOmemoStore.LAST_RENEWAL_DATE + " NUMBER, UNIQUE("
            + SQLiteOmemoStore.BARE_JID + ", " + SQLiteOmemoStore.DEVICE_ID + ", "
            + SQLiteOmemoStore.SIGNED_PRE_KEY_ID
            + ") ON CONFLICT REPLACE);";

    // Create identities table
    public static String CREATE_IDENTITIES_STATEMENT = "CREATE TABLE "
            + SQLiteOmemoStore.IDENTITIES_TABLE_NAME + "("
            + SQLiteOmemoStore.BARE_JID + " TEXT, "
            + SQLiteOmemoStore.DEVICE_ID + " INTEGER, "
            + SQLiteOmemoStore.FINGERPRINT + " TEXT, "
            + SQLiteOmemoStore.CERTIFICATE + " BLOB, "
            + SQLiteOmemoStore.TRUST + " TEXT, "
            + SQLiteOmemoStore.ACTIVE + " NUMBER, "
            + SQLiteOmemoStore.LAST_ACTIVATION + " NUMBER, "
            + SQLiteOmemoStore.LAST_DEVICE_ID_PUBLISH + " NUMBER, "
            + SQLiteOmemoStore.LAST_MESSAGE_RX + " NUMBER, "
            + SQLiteOmemoStore.MESSAGE_COUNTER + " INTEGER, "
            + SQLiteOmemoStore.IDENTITY_KEY + " TEXT, UNIQUE("
            + SQLiteOmemoStore.BARE_JID + ", " + SQLiteOmemoStore.DEVICE_ID
            + ") ON CONFLICT REPLACE);";

    // Create session table
    public static String CREATE_SESSIONS_STATEMENT = "CREATE TABLE "
            + SQLiteOmemoStore.SESSION_TABLE_NAME + "("
            + SQLiteOmemoStore.BARE_JID + " TEXT, "
            + SQLiteOmemoStore.DEVICE_ID + " INTEGER, "
            + SQLiteOmemoStore.SESSION_KEY + " TEXT, UNIQUE("
            + SQLiteOmemoStore.BARE_JID + ", " + SQLiteOmemoStore.DEVICE_ID
            + ") ON CONFLICT REPLACE);";

    private DatabaseBackend(Context context)
    {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    /**
     * Get an instance of the DataBaseBackend and create one if new
     *
     * @param context context
     * @return DatabaseBackend instance
     */
    public static synchronized DatabaseBackend getInstance(Context context)
    {
        if (instance == null) {
            instance = new DatabaseBackend(context);
        }
        return instance;
    }

    public static SQLiteDatabase getWritableDB()
    {
        return instance.getWritableDatabase();
    }

    public static SQLiteDatabase getReadableDB()
    {
        return instance.getReadableDatabase();
    }

    @Override
    public void onUpgrade(final SQLiteDatabase db, int oldVersion, int newVersion)
    {
        Timber.i("Upgrading database from version %s to version %s", oldVersion, newVersion);

        db.beginTransaction();
        try {
            // cmeng: mProvider == null currently not use - must fixed if use
            RealMigrationsHelper migrationsHelper = new RealMigrationsHelper(mProvider);
            Migrations.upgradeDatabase(db, migrationsHelper);
            db.setTransactionSuccessful();
        } catch (Exception e) {
            Timber.e("Exception while upgrading database. Resetting the DB to original: %s", e.getMessage());
            db.setVersion(oldVersion);

            if (BuildConfig.DEBUG) {
                db.endTransaction();
                throw new Error("Database upgrade failed! Exception: ", e);
            }
        } finally {
            db.endTransaction();
        }
    }

    /**
     * Create all the required virgin database tables and perform initial data migration
     * a. System properties
     * b. Account Tables: accountID & accountProperties
     * c. Group Tables: metaContactGroup & childContacts
     * d. contacts
     * e. chatSessions
     * f. chatMessages
     * g. callHistory
     * f. recentMessages
     * i. Axolotl tables: identities, sessions, preKeys, signed_preKeys
     * <p>
     * # Initialize and initial data migration
     *
     * @param db SQLite database
     */

    @Override
    public void onCreate(SQLiteDatabase db)
    {
        // db.execSQL("PRAGMA foreign_keys=ON;");
        String query = String.format("PRAGMA foreign_keys =%s", "ON");
        db.execSQL(query);

        // System properties table
        db.execSQL("CREATE TABLE " + SQLiteConfigurationStore.TABLE_NAME + "("
                + SQLiteConfigurationStore.COLUMN_NAME + " TEXT PRIMARY KEY, "
                + SQLiteConfigurationStore.COLUMN_VALUE + " TEXT, UNIQUE("
                + SQLiteConfigurationStore.COLUMN_NAME
                + ") ON CONFLICT REPLACE);");

        // Account info table
        db.execSQL("CREATE TABLE " + AccountID.TABLE_NAME + "("
                + AccountID.ACCOUNT_UUID + " TEXT PRIMARY KEY, "
                + AccountID.PROTOCOL + " TEXT DEFAULT " + AccountID.PROTOCOL_DEFAULT + ", "
                + AccountID.USER_ID + " TEXT, "
                + AccountID.ACCOUNT_UID + " TEXT, "
                + AccountID.KEYS + " TEXT, UNIQUE(" + AccountID.ACCOUNT_UID
                + ") ON CONFLICT REPLACE);");

        // Account properties table
        db.execSQL("CREATE TABLE " + AccountID.TBL_PROPERTIES + "("
                + AccountID.ACCOUNT_UUID + " TEXT, "
                + AccountID.COLUMN_NAME + " TEXT, "
                + AccountID.COLUMN_VALUE + " TEXT, PRIMARY KEY("
                + AccountID.ACCOUNT_UUID + ", "
                + AccountID.COLUMN_NAME + "), FOREIGN KEY("
                + AccountID.ACCOUNT_UUID + ") REFERENCES "
                + AccountID.TABLE_NAME + "(" + AccountID.ACCOUNT_UUID
                + ") ON DELETE CASCADE);");

        // Meta contact groups table
        db.execSQL("CREATE TABLE " + MetaContactGroup.TABLE_NAME + "("
                + MetaContactGroup.ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + MetaContactGroup.ACCOUNT_UUID + " TEXT, "
                + MetaContactGroup.MC_GROUP_NAME + " TEXT, "
                + MetaContactGroup.MC_GROUP_UID + " TEXT, "
                + MetaContactGroup.PARENT_PROTO_GROUP_UID + " TEXT, "
                + MetaContactGroup.PROTO_GROUP_UID + " TEXT, "
                + MetaContactGroup.PERSISTENT_DATA + " TEXT, FOREIGN KEY("
                + MetaContactGroup.ACCOUNT_UUID + ") REFERENCES "
                + AccountID.TABLE_NAME + "(" + AccountID.ACCOUNT_UUID
                + ") ON DELETE CASCADE, UNIQUE(" + MetaContactGroup.ACCOUNT_UUID + ", "
                + MetaContactGroup.MC_GROUP_UID + ", " + MetaContactGroup.PARENT_PROTO_GROUP_UID
                + ") ON CONFLICT REPLACE);");

        /*
         * Meta contact group members table. The entries in the table are linked to the
         * MetaContactGroup.TABLE_NAME each entry by ACCOUNT_UUID && PROTO_GROUP_UID
         */
        db.execSQL("CREATE TABLE " + MetaContactGroup.TBL_CHILD_CONTACTS + "("
                + MetaContactGroup.ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + MetaContactGroup.MC_UID + " TEXT, "
                + MetaContactGroup.ACCOUNT_UUID + " TEXT, "
                + MetaContactGroup.PROTO_GROUP_UID + " TEXT, "
                + MetaContactGroup.CONTACT_JID + " TEXT, "
                + MetaContactGroup.MC_DISPLAY_NAME + " TEXT, "
                + MetaContactGroup.MC_USER_DEFINED + " TEXT DEFAULT 'false',"
                + MetaContactGroup.PERSISTENT_DATA + " TEXT, "
                + MetaContactGroup.MC_DETAILS + " TEXT, FOREIGN KEY("
                + MetaContactGroup.ACCOUNT_UUID + ") REFERENCES "
                + AccountID.TABLE_NAME + "(" + AccountID.ACCOUNT_UUID
                + ") ON DELETE CASCADE, UNIQUE(" + MetaContactGroup.ACCOUNT_UUID + ", "
                + MetaContactGroup.PROTO_GROUP_UID + ", " + MetaContactGroup.CONTACT_JID
                + ") ON CONFLICT REPLACE);");

        // Contacts information table
        db.execSQL("CREATE TABLE " + Contact.TABLE_NAME + "("
                + Contact.CONTACT_UUID + " TEXT PRIMARY KEY, "
                + Contact.PROTOCOL_PROVIDER + " TEXT, "
                + Contact.CONTACT_JID + " TEXT, "
                + Contact.SVR_DISPLAY_NAME + " TEXT, "
                + Contact.OPTIONS + " NUMBER, "
                + Contact.PHOTO_URI + " TEXT, "
                + Contact.AVATAR_HASH + " TEXT, "
                + Contact.LAST_PRESENCE + " TEXT, "
                + Contact.PRESENCE_STATUS + " INTEGER, "
                + Contact.LAST_SEEN + " NUMBER,"
                + Contact.KEYS + " TEXT, UNIQUE("
                + Contact.PROTOCOL_PROVIDER + ", " + Contact.CONTACT_JID
                + ") ON CONFLICT IGNORE);");

        // Chat session information table
        db.execSQL("CREATE TABLE " + ChatSession.TABLE_NAME + " ("
                + ChatSession.SESSION_UUID + " TEXT PRIMARY KEY, "
                + ChatSession.ACCOUNT_UUID + " TEXT, "
                + ChatSession.ACCOUNT_UID + " TEXT, "
                + ChatSession.ENTITY_JID + " TEXT, "
                + ChatSession.CREATED + " NUMBER, "
                + ChatSession.STATUS + " NUMBER DEFAULT " + ChatFragment.MSGTYPE_OMEMO + ", "
                + ChatSession.MODE + " NUMBER, "
                + ChatSession.ATTRIBUTES + " TEXT, FOREIGN KEY("
                + ChatSession.ACCOUNT_UUID + ") REFERENCES "
                + AccountID.TABLE_NAME + "(" + AccountID.ACCOUNT_UUID
                + ") ON DELETE CASCADE, UNIQUE(" + ChatSession.ACCOUNT_UUID
                + ", " + ChatSession.ENTITY_JID
                + ") ON CONFLICT REPLACE);");

        // chat / MUC message table
        db.execSQL("CREATE TABLE " + ChatMessage.TABLE_NAME + "( "
                + ChatMessage.UUID + " TEXT, "
                + ChatMessage.SESSION_UUID + " TEXT, "
                + ChatMessage.TIME_STAMP + " NUMBER, "
                + ChatMessage.ENTITY_JID + " TEXT,"
                + ChatMessage.JID + " TEXT, "
                + ChatMessage.MSG_BODY + " TEXT, "
                + ChatMessage.ENC_TYPE + " TEXT, "
                + ChatMessage.MSG_TYPE + " TEXT, "
                + ChatMessage.DIRECTION + " TEXT, "
                + ChatMessage.STATUS + " TEXT,"
                + ChatMessage.FILE_PATH + " TEXT, "
                + ChatMessage.FINGERPRINT + " TEXT, "
                + ChatMessage.STEALTH_TIMER + "  INTEGER DEFAULT 0, "
                + ChatMessage.CARBON + " INTEGER DEFAULT 0, "
                + ChatMessage.READ + " INTEGER DEFAULT 0, "
                + ChatMessage.OOB + " INTEGER DEFAULT 0, "
                + ChatMessage.ERROR_MSG + " TEXT, "
                + ChatMessage.SERVER_MSG_ID + " TEXT, "
                + ChatMessage.REMOTE_MSG_ID + " TEXT, FOREIGN KEY("
                + ChatMessage.SESSION_UUID + ") REFERENCES "
                + ChatSession.TABLE_NAME + "(" + ChatSession.SESSION_UUID
                + ") ON DELETE CASCADE, UNIQUE(" + ChatMessage.UUID
                + ") ON CONFLICT REPLACE);");

        // Call history table
        db.execSQL("CREATE TABLE " + CallHistoryService.TABLE_NAME + " ("
                + CallHistoryService.UUID + " TEXT PRIMARY KEY, "
                + CallHistoryService.TIME_STAMP + " NUMBER, "
                + CallHistoryService.ACCOUNT_UID + " TEXT, "
                + CallHistoryService.CALL_START + " NUMBER, "
                + CallHistoryService.CALL_END + " NUMBER, "
                + CallHistoryService.DIRECTION + " TEXT, "
                + CallHistoryService.ENTITY_FULL_JID + " TEXT, "
                + CallHistoryService.ENTITY_CALL_START + " NUMBER, "
                + CallHistoryService.ENTITY_CALL_END + " NUMBER, "
                + CallHistoryService.ENTITY_CALL_STATE + " TEXT, "
                + CallHistoryService.CALL_END_REASON + " TEXT, "
                + CallHistoryService.ENTITY_JID + " TEXT, "
                + CallHistoryService.SEC_ENTITY_ID + " TEXT, FOREIGN KEY("
                + CallHistoryService.ACCOUNT_UID + ") REFERENCES "
                + AccountID.TABLE_NAME + "(" + AccountID.ACCOUNT_UID
                + ") ON DELETE CASCADE);");

        // Recent message table
        db.execSQL("CREATE TABLE " + MessageSourceService.TABLE_NAME + " ("
                + MessageSourceService.UUID + " TEXT PRIMARY KEY, "
                + MessageSourceService.ACCOUNT_UID + " TEXT, "
                + MessageSourceService.ENTITY_JID + " TEXT, "
                + MessageSourceService.TIME_STAMP + " NUMBER, "
                + MessageSourceService.VERSION + " TEXT, FOREIGN KEY("
                + MessageSourceService.ACCOUNT_UID + ") REFERENCES "
                + AccountID.TABLE_NAME + "(" + AccountID.ACCOUNT_UID
                + ") ON DELETE CASCADE);");

        // Create all relevant tables for OMEMO support
        db.execSQL(CREATE_OMEMO_DEVICES_STATEMENT);
        db.execSQL(CREATE_PREKEYS_STATEMENT);
        db.execSQL(CREATE_SIGNED_PREKEYS_STATEMENT);
        db.execSQL(CREATE_IDENTITIES_STATEMENT);
        db.execSQL(CREATE_SESSIONS_STATEMENT);

        // Perform the first data migration to SQLite database
        initDatabase(db);
    }

    /**
     * Initialize, migrate and fill the database from old data implementation
     */
    private void initDatabase(SQLiteDatabase db)
    {
        Timber.i("### Starting Database migration! ###");
        db.beginTransaction();
        try {
            db.setTransactionSuccessful();
            Timber.i("### Completed SQLite DataBase migration successfully! ###");
        } finally {
            db.endTransaction();
        }
    }

    /**
     * Create or update the AccountID table for a specified accountId
     *
     * @param accountId AccountID to be replaced/inserted
     */
    public void createAccount(AccountID accountId)
    {
        SQLiteDatabase db = this.getWritableDatabase();
        db.replace(AccountID.TABLE_NAME, null, accountId.getContentValues());
    }

    public List<String> getAllAccountIDs()
    {
        List<String> userIDs = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        String[] columns = {AccountID.USER_ID};

        Cursor cursor = db.query(AccountID.TABLE_NAME, columns, null, null, null, null, null);
        while (cursor.moveToNext()) {
            userIDs.add(cursor.getString(0));
        }
        cursor.close();
        return userIDs;
    }

    public List<AccountID> getAccounts(ProtocolProviderFactory factory)
    {
        List<AccountID> accountIDs = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        String[] args = {factory.getProtocolName()};

        Cursor cursor = db.query(AccountID.TABLE_NAME, null, AccountID.PROTOCOL + "=?",
                args, null, null, null);
        while (cursor.moveToNext()) {
            accountIDs.add(AccountID.fromCursor(db, cursor, factory));
        }
        cursor.close();
        return accountIDs;
    }

    public boolean updateAccount(AccountID accountId)
    {
        SQLiteDatabase db = this.getWritableDatabase();
        String[] args = {accountId.getAccountUuid()};
        final int rows = db.update(AccountID.TABLE_NAME, accountId.getContentValues(),
                AccountID.ACCOUNT_UUID + "=?", args);
        return (rows == 1);
    }

    public boolean deleteAccount(AccountID accountId)
    {
        SQLiteDatabase db = this.getWritableDatabase();
        String[] args = {accountId.getAccountUuid()};
        final int rows = db.delete(AccountID.TABLE_NAME, AccountID.ACCOUNT_UUID + "=?", args);
        return rows == 1;
    }

    @Override
    public SQLiteDatabase getWritableDatabase()
    {
        SQLiteDatabase db = super.getWritableDatabase();
        // db.execSQL("PRAGMA foreign_keys=ON;");
        String query = String.format("PRAGMA foreign_keys =%s", "ON");
        db.execSQL(query);
        return db;
    }

    // ========= OMEMO Devices =========
    public SortedSet<Integer> loadDevideIdsOf(BareJid user)
    {
        SortedSet<Integer> deviceIds = new TreeSet<>();
        int registrationId;
        String ORDER_ASC = SQLiteOmemoStore.OMEMO_REG_ID + " ASC";

        SQLiteDatabase db = this.getReadableDatabase();
        String[] columns = {SQLiteOmemoStore.OMEMO_REG_ID};
        String[] selectionArgs = {user.toString()};

        Cursor cursor = db.query(SQLiteOmemoStore.OMEMO_DEVICES_TABLE_NAME, columns,
                SQLiteOmemoStore.OMEMO_JID + "=?", selectionArgs, null, null, ORDER_ASC);

        while (cursor.moveToNext()) {
            registrationId = cursor.getInt(0);
            deviceIds.add(registrationId);
        }
        cursor.close();
        return deviceIds;
    }

    public HashMap<String, Integer> loadAllOmemoRegIds()
    {
        HashMap<String, Integer> registrationIds = new HashMap<>();
        SQLiteDatabase db = this.getReadableDatabase();
        String[] columns = {SQLiteOmemoStore.OMEMO_JID, SQLiteOmemoStore.OMEMO_REG_ID};

        Cursor cursor = db.query(SQLiteOmemoStore.OMEMO_DEVICES_TABLE_NAME, columns,
                null, null, null, null, null);

        while (cursor.moveToNext()) {
            registrationIds.put(cursor.getString(0), cursor.getInt(1));
        }
        cursor.close();
        return registrationIds;
    }

    public void storeOmemoRegId(BareJid user, int defaultDeviceId)
    {
        SQLiteDatabase db = this.getWritableDatabase();

        ContentValues values = new ContentValues();
        values.put(SQLiteOmemoStore.OMEMO_JID, user.toString());
        values.put(SQLiteOmemoStore.OMEMO_REG_ID, defaultDeviceId);
        values.put(SQLiteOmemoStore.CURRENT_SIGNED_PREKEY_ID, 0);
        long row = db.insert(SQLiteOmemoStore.OMEMO_DEVICES_TABLE_NAME, null, values);
        if (row > 0)
            Timber.i("### Omemo device added for: %s; %s", user, defaultDeviceId);
        else
            Timber.e("### Error in creating Omemo device for: %s: %s", user, defaultDeviceId);
    }

    public int loadCurrentSignedPKeyId(OmemoManager omemoManager)
    {
        int currentSignedPKeyId = getCurrentSignedPreKeyId(omemoManager);
        OmemoDevice device = omemoManager.getOwnDevice();

        SQLiteDatabase db = this.getReadableDatabase();
        String[] columns = {SQLiteOmemoStore.CURRENT_SIGNED_PREKEY_ID};
        String[] selectionArgs = {device.getJid().toString(), Integer.toString(device.getDeviceId())};

        Cursor cursor = db.query(SQLiteOmemoStore.OMEMO_DEVICES_TABLE_NAME, columns,
                SQLiteOmemoStore.OMEMO_JID + "=? AND " + SQLiteOmemoStore.OMEMO_REG_ID + "=?",
                selectionArgs, null, null, null);

        if (cursor.getCount() != 0) {
            cursor.moveToFirst();
            currentSignedPKeyId = cursor.getInt(cursor.getColumnIndex(SQLiteOmemoStore.CURRENT_SIGNED_PREKEY_ID));
        }
        cursor.close();
        return currentSignedPKeyId;
    }

    public void storeCurrentSignedPKeyId(OmemoManager omemoManager, int currentSignedPreKeyId)
    {
        SQLiteDatabase db = this.getWritableDatabase();
        OmemoDevice device = omemoManager.getOwnDevice();
        String[] selectionArgs = {device.getJid().toString(), Integer.toString(device.getDeviceId())};

        ContentValues values = new ContentValues();
        values.put(SQLiteOmemoStore.OMEMO_JID, device.getJid().toString());
        values.put(SQLiteOmemoStore.OMEMO_REG_ID, device.getDeviceId());
        values.put(SQLiteOmemoStore.CURRENT_SIGNED_PREKEY_ID, currentSignedPreKeyId);

        int row = db.update(SQLiteOmemoStore.OMEMO_DEVICES_TABLE_NAME, values,
                SQLiteOmemoStore.OMEMO_JID + "=? AND " + SQLiteOmemoStore.OMEMO_REG_ID + "=?",
                selectionArgs);
        if (row == 0) {
            db.insert(SQLiteOmemoStore.OMEMO_DEVICES_TABLE_NAME, null, values);
        }
    }

    // cmeng: encountered getLastPreKeyId not equal to store lastPreKey causing omemo msg problem!
    // To reset stored lastPreKey???
    public int loadLastPreKeyId(OmemoManager omemoManager)
    {
        int lastPKeyId = getLastPreKeyId(omemoManager);
        OmemoDevice device = omemoManager.getOwnDevice();

        SQLiteDatabase db = this.getReadableDatabase();
        String[] columns = {SQLiteOmemoStore.LAST_PREKEY_ID};
        String[] selectionArgs = {device.getJid().toString(), Integer.toString(device.getDeviceId())};

        Cursor cursor = db.query(SQLiteOmemoStore.OMEMO_DEVICES_TABLE_NAME, columns,
                SQLiteOmemoStore.OMEMO_JID + "=? AND " + SQLiteOmemoStore.OMEMO_REG_ID + "=?",
                selectionArgs, null, null, null);

        if (cursor.getCount() != 0) {
            cursor.moveToFirst();
            lastPKeyId = cursor.getInt(cursor.getColumnIndex(SQLiteOmemoStore.LAST_PREKEY_ID));
        }
        cursor.close();
        return lastPKeyId;
    }

    public void storeLastPreKeyId(OmemoManager omemoManager, int lastPreKeyId)
    {
        SQLiteDatabase db = this.getWritableDatabase();
        OmemoDevice device = omemoManager.getOwnDevice();
        String[] selectionArgs = {device.getJid().toString(), Integer.toString(device.getDeviceId())};

        ContentValues values = new ContentValues();
        values.put(SQLiteOmemoStore.OMEMO_JID, device.getJid().toString());
        values.put(SQLiteOmemoStore.OMEMO_REG_ID, device.getDeviceId());
        values.put(SQLiteOmemoStore.LAST_PREKEY_ID, lastPreKeyId);

        int row = db.update(SQLiteOmemoStore.OMEMO_DEVICES_TABLE_NAME, values,
                SQLiteOmemoStore.OMEMO_JID + "=? AND " + SQLiteOmemoStore.OMEMO_REG_ID + "=?",
                selectionArgs);
        if (row == 0) {
            db.insert(SQLiteOmemoStore.OMEMO_DEVICES_TABLE_NAME, null, values);
        }
    }

    // ========= OMEMO PreKey =========
    private Cursor getCursorForPreKey(OmemoDevice userDevice, int preKeyId)
    {
        SQLiteDatabase db = this.getReadableDatabase();

        String[] columns = {SQLiteOmemoStore.PRE_KEYS};
        String[] selectionArgs = {userDevice.getJid().toString(),
                Integer.toString(userDevice.getDeviceId()), Integer.toString(preKeyId)};

        return db.query(SQLiteOmemoStore.PREKEY_TABLE_NAME, columns,
                SQLiteOmemoStore.BARE_JID + "=? AND " + SQLiteOmemoStore.DEVICE_ID + "=? AND "
                        + SQLiteOmemoStore.PRE_KEY_ID + "=?",
                selectionArgs, null, null, null);
    }

    public TreeMap<Integer, PreKeyRecord> loadPreKeys(OmemoDevice userDevice)
    {
        int preKeyId;
        PreKeyRecord preKeyRecord;
        String ORDER_ASC = SQLiteOmemoStore.PRE_KEY_ID + " ASC";
        TreeMap<Integer, PreKeyRecord> PreKeyRecords = new TreeMap<>();

        SQLiteDatabase db = this.getReadableDatabase();

        String[] columns = {SQLiteOmemoStore.PRE_KEY_ID, SQLiteOmemoStore.PRE_KEYS};
        String[] selectionArgs = {userDevice.getJid().toString(), Integer.toString(userDevice.getDeviceId())};

        Cursor cursor = db.query(SQLiteOmemoStore.PREKEY_TABLE_NAME, columns,
                SQLiteOmemoStore.BARE_JID + "=? AND " + SQLiteOmemoStore.DEVICE_ID + "=?",
                selectionArgs, null, null, ORDER_ASC);

        while (cursor.moveToNext()) {
            preKeyId = cursor.getInt(0);
            try {
                preKeyRecord = new PreKeyRecord(Base64.decode(cursor.getString(1), Base64.DEFAULT));
                PreKeyRecords.put(preKeyId, preKeyRecord);
            } catch (IOException e) {
                Timber.w("Failed to deserialize preKey from store preky: %s: %s", preKeyId, e.getMessage());
            }
        }
        cursor.close();
        return PreKeyRecords;
    }

    public PreKeyRecord loadPreKey(OmemoDevice userDevice, int preKeyId)
    {
        PreKeyRecord record = null;
        Cursor cursor = getCursorForPreKey(userDevice, preKeyId);
        if (cursor.getCount() != 0) {
            cursor.moveToFirst();
            try {
                record = new PreKeyRecord(Base64.decode(
                        cursor.getString(cursor.getColumnIndex(SQLiteOmemoStore.PRE_KEYS)), Base64.DEFAULT));
            } catch (IOException e) {
                Timber.w("Failed to deserialize preKey from store. %s", e.getMessage());
            }
        }
        cursor.close();
        return record;
    }

    public void storePreKey(OmemoDevice userDevice, int preKeyId, PreKeyRecord record)
    {
        SQLiteDatabase db = this.getWritableDatabase();

        ContentValues values = new ContentValues();
        values.put(SQLiteOmemoStore.BARE_JID, userDevice.getJid().toString());
        values.put(SQLiteOmemoStore.DEVICE_ID, userDevice.getDeviceId());
        values.put(SQLiteOmemoStore.PRE_KEY_ID, preKeyId);
        values.put(SQLiteOmemoStore.PRE_KEYS, Base64.encodeToString(record.serialize(), Base64.DEFAULT));
        db.insert(SQLiteOmemoStore.PREKEY_TABLE_NAME, null, values);
    }

    public void deletePreKey(OmemoDevice userDevice, int preKeyId)
    {
        SQLiteDatabase db = this.getWritableDatabase();
        String[] args = {userDevice.getJid().toString(),
                Integer.toString(userDevice.getDeviceId()), Integer.toString(preKeyId)};

        db.delete(SQLiteOmemoStore.PREKEY_TABLE_NAME, SQLiteOmemoStore.BARE_JID + "=? AND "
                + SQLiteOmemoStore.DEVICE_ID + "=? AND " + SQLiteOmemoStore.PRE_KEY_ID + "=?", args);
    }

    public int getLastPreKeyId(OmemoManager omemoManager)
    {
        int lastPreKeyId = 0;
        String ORDER_DESC = SQLiteOmemoStore.PRE_KEY_ID + " DESC";
        OmemoDevice device = omemoManager.getOwnDevice();

        SQLiteDatabase db = this.getReadableDatabase();
        String[] columns = {SQLiteOmemoStore.PRE_KEY_ID};
        String[] selectionArgs = {device.getJid().toString(), Integer.toString(device.getDeviceId())};

        Cursor cursor = db.query(SQLiteOmemoStore.PREKEY_TABLE_NAME, columns,
                SQLiteOmemoStore.BARE_JID + "=? AND " + SQLiteOmemoStore.DEVICE_ID + "=?",
                selectionArgs, null, null, ORDER_DESC, "1");

        if (cursor.getCount() != 0) {
            cursor.moveToFirst();
            lastPreKeyId = cursor.getInt(cursor.getColumnIndex(SQLiteOmemoStore.PRE_KEY_ID));
        }
        cursor.close();
        return lastPreKeyId;
    }

    // ========= OMEMO Signed PreKey =========
    private Cursor getCursorForSignedPreKey(OmemoDevice userDevice, int signedPreKeyId)
    {
        SQLiteDatabase db = this.getReadableDatabase();

        String[] columns = {SQLiteOmemoStore.SIGNED_PRE_KEYS};
        String[] selectionArgs = {userDevice.getJid().toString(),
                Integer.toString(userDevice.getDeviceId()), Integer.toString(signedPreKeyId)};
        Cursor cursor = db.query(SQLiteOmemoStore.SIGNED_PREKEY_TABLE_NAME, columns,
                SQLiteOmemoStore.BARE_JID + "=? AND "
                        + SQLiteOmemoStore.DEVICE_ID + "=? AND "
                        + SQLiteOmemoStore.SIGNED_PRE_KEY_ID + "=?", selectionArgs, null, null,
                null);
        return cursor;
    }

    public SignedPreKeyRecord loadSignedPreKey(OmemoDevice userDevice, int signedPreKeyId)
    {
        SignedPreKeyRecord record = null;
        Cursor cursor = getCursorForSignedPreKey(userDevice, signedPreKeyId);
        if (cursor.getCount() != 0) {
            cursor.moveToFirst();
            try {
                record = new SignedPreKeyRecord(Base64.decode(
                        cursor.getString(cursor.getColumnIndex(SQLiteOmemoStore.SIGNED_PRE_KEYS)), Base64.DEFAULT));
            } catch (IOException e) {
                Timber.w("Could not deserialize signed preKey for %s: %s", userDevice, e.getMessage());
            }
        }
        cursor.close();
        return record;
    }

    public TreeMap<Integer, SignedPreKeyRecord> loadSignedPreKeys(OmemoDevice device)
    {
        int preKeyId;
        SignedPreKeyRecord signedPreKeysRecord;
        TreeMap<Integer, SignedPreKeyRecord> preKeys = new TreeMap<>();

        SQLiteDatabase db = this.getReadableDatabase();
        String[] columns = {SQLiteOmemoStore.SIGNED_PRE_KEY_ID, SQLiteOmemoStore.SIGNED_PRE_KEYS};
        String[] selectionArgs = {device.getJid().toString(), Integer.toString(device.getDeviceId())};
        Cursor cursor = db.query(SQLiteOmemoStore.SIGNED_PREKEY_TABLE_NAME, columns,
                SQLiteOmemoStore.BARE_JID + "=? AND " + SQLiteOmemoStore.DEVICE_ID + "=?",
                selectionArgs, null, null, null);

        while (cursor.moveToNext()) {
            try {
                preKeyId = cursor.getInt(cursor.getColumnIndex(SQLiteOmemoStore.SIGNED_PRE_KEY_ID));
                signedPreKeysRecord = new SignedPreKeyRecord(Base64.decode(cursor.getString(
                        cursor.getColumnIndex(SQLiteOmemoStore.SIGNED_PRE_KEYS)), Base64.DEFAULT));
                preKeys.put(preKeyId, signedPreKeysRecord);
            } catch (IOException e) {
                Timber.w("Could not deserialize signed preKey for %s: %s", device, e.getMessage());
            }
        }
        cursor.close();
        return preKeys;
    }

    public void storeSignedPreKey(OmemoDevice device, int signedPreKeyId, SignedPreKeyRecord record)
    {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(SQLiteOmemoStore.BARE_JID, device.getJid().toString());
        values.put(SQLiteOmemoStore.DEVICE_ID, device.getDeviceId());
        values.put(SQLiteOmemoStore.SIGNED_PRE_KEY_ID, signedPreKeyId);
        values.put(SQLiteOmemoStore.SIGNED_PRE_KEYS, Base64.encodeToString(record.serialize(), Base64.DEFAULT));
        values.put(SQLiteOmemoStore.LAST_RENEWAL_DATE, record.getTimestamp());
        db.insert(SQLiteOmemoStore.SIGNED_PREKEY_TABLE_NAME, null, values);
    }

    public void deleteSignedPreKey(OmemoDevice userDevice, int signedPreKeyId)
    {
        SQLiteDatabase db = this.getWritableDatabase();

        String[] args = {userDevice.getJid().toString(), Integer.toString(userDevice.getDeviceId()),
                Integer.toString(signedPreKeyId)};
        db.delete(SQLiteOmemoStore.SIGNED_PREKEY_TABLE_NAME,
                SQLiteOmemoStore.BARE_JID + "=? AND " + SQLiteOmemoStore.DEVICE_ID + "=? AND "
                        + SQLiteOmemoStore.SIGNED_PRE_KEY_ID + "=?", args);
    }

    public void setLastSignedPreKeyRenewal(OmemoDevice userDevice, Date date)
    {
        SQLiteDatabase db = this.getWritableDatabase();

        ContentValues values = new ContentValues();
        values.put(SQLiteOmemoStore.LAST_RENEWAL_DATE, date.getTime());

        String[] selectionArgs = {userDevice.getJid().toString(), Integer.toString(userDevice.getDeviceId())};
        db.update(SQLiteOmemoStore.SIGNED_PREKEY_TABLE_NAME, values,
                SQLiteOmemoStore.BARE_JID + "=? AND " + SQLiteOmemoStore.DEVICE_ID + "=?", selectionArgs);
    }

    public Date getLastSignedPreKeyRenewal(OmemoDevice userDevice)
    {
        SQLiteDatabase db = this.getReadableDatabase();

        String[] columns = {SQLiteOmemoStore.LAST_RENEWAL_DATE};
        String[] selectionArgs = {userDevice.getJid().toString(), Integer.toString(userDevice.getDeviceId())};

        Cursor cursor = db.query(SQLiteOmemoStore.SIGNED_PREKEY_TABLE_NAME, columns,
                SQLiteOmemoStore.BARE_JID + "=? AND " + SQLiteOmemoStore.DEVICE_ID + "=?",
                selectionArgs, null, null, null);

        if (cursor.getCount() != 0) {
            cursor.moveToFirst();
            Long ts = cursor.getLong(cursor.getColumnIndex(SQLiteOmemoStore.LAST_RENEWAL_DATE));
            cursor.close();
            return (ts != null && ts > 0) ? new Date(ts) : null;
        }
        return null;
    }

    private int getCurrentSignedPreKeyId(OmemoManager omemoManager)
    {
        int currentSignedPKId = 1;
        SQLiteDatabase db = this.getReadableDatabase();
        OmemoDevice device = omemoManager.getOwnDevice();

        String[] columns = {SQLiteOmemoStore.SIGNED_PRE_KEY_ID};
        String[] selectionArgs = {device.getJid().toString(), Integer.toString(device.getDeviceId())};

        Cursor cursor = db.query(SQLiteOmemoStore.SIGNED_PREKEY_TABLE_NAME, columns,
                SQLiteOmemoStore.BARE_JID + "=? AND " + SQLiteOmemoStore.DEVICE_ID + "=?",
                selectionArgs, null, null, null);

        if (cursor.getCount() != 0) {
            cursor.moveToFirst();
            currentSignedPKId = cursor.getInt(cursor.getColumnIndex(SQLiteOmemoStore.SIGNED_PRE_KEY_ID));
        }
        cursor.close();
        return currentSignedPKId;
    }

    // ========= OMEMO Identity =========
    private Cursor getIdentityKeyCursor(OmemoDevice device, String fingerprint)
    {
        final SQLiteDatabase db = this.getReadableDatabase();
        ArrayList<String> selectionArgs = new ArrayList<>(3);

        selectionArgs.add(device.getJid().toString());
        String selectionString = SQLiteOmemoStore.BARE_JID + "=?";

        selectionArgs.add(Integer.toString(device.getDeviceId()));
        selectionString += " AND " + SQLiteOmemoStore.DEVICE_ID + "=?";

        if (fingerprint != null) {
            selectionArgs.add(fingerprint);
            selectionString += " AND " + SQLiteOmemoStore.FINGERPRINT + "=?";
        }

        return db.query(SQLiteOmemoStore.IDENTITIES_TABLE_NAME, null, selectionString,
                selectionArgs.toArray(new String[0]), null, null, null);
    }

    public IdentityKeyPair loadIdentityKeyPair(OmemoDevice device)
            throws CorruptedOmemoKeyException
    {
        IdentityKeyPair identityKeyPair = null;
        Cursor cursor = getIdentityKeyCursor(device, null);
        if (cursor.getCount() != 0) {
            cursor.moveToFirst();
            String identityKP = cursor.getString(cursor.getColumnIndex(SQLiteOmemoStore.IDENTITY_KEY));
            cursor.close();
            try {
                if (StringUtils.isNotEmpty(identityKP)) {
                    identityKeyPair = new IdentityKeyPair(Base64.decode(identityKP, Base64.DEFAULT));
                }
            } catch (InvalidKeyException e) {
                // deleteIdentityKey(device); // may corrupt DB and out of sync with other data
                String msg = aTalkApp.getResString(R.string.omemo_identity_keypairs_invalid, device, e.getMessage());
                throw new CorruptedOmemoKeyException(msg);
            }
        }
        return identityKeyPair;
    }

    public IdentityKey loadIdentityKey(OmemoDevice device)
            throws CorruptedOmemoKeyException
    {
        IdentityKey identityKey = null;
        Cursor cursor = getIdentityKeyCursor(device, null);
        if (cursor.getCount() != 0) {
            cursor.moveToFirst();
            String key = cursor.getString(cursor.getColumnIndex(SQLiteOmemoStore.IDENTITY_KEY));
            cursor.close();
            try {
                if (StringUtils.isNotEmpty(key)) {
                    identityKey = new IdentityKey(Base64.decode(key, Base64.DEFAULT), 0);
                }
            } catch (InvalidKeyException e) {
                // Delete corrupted identityKey, let omemo rebuilt this
                deleteIdentityKey(device);
                String msg = aTalkApp.getResString(R.string.omemo_identity_key_invalid, device, e.getMessage());
                throw new CorruptedOmemoKeyException(msg);
            }
        }
        return identityKey;
    }

    // Use this to delete the device corrupted identityKeyPair/identityKey
    // - Later identityKeyPair gets rebuilt when device restart
    public void deleteIdentityKey(OmemoDevice device)
    {
        SQLiteDatabase db = this.getWritableDatabase();
        String[] whereArgs = {device.getJid().toString(), Integer.toString(device.getDeviceId())};

        db.delete(SQLiteOmemoStore.IDENTITIES_TABLE_NAME, SQLiteOmemoStore.BARE_JID + "=? AND "
                + SQLiteOmemoStore.DEVICE_ID + "=?", whereArgs);
    }

    public void storeIdentityKeyPair(OmemoDevice userDevice, IdentityKeyPair identityKeyPair, String fingerprint)
    {
        storeIdentityKey(userDevice, fingerprint,
                Base64.encodeToString(identityKeyPair.serialize(), Base64.DEFAULT),
                FingerprintStatus.createActiveVerified(false));
    }

    public void storeIdentityKey(OmemoDevice device, IdentityKey identityKey, String fingerprint,
            FingerprintStatus status)
    {
        storeIdentityKey(device, fingerprint, Base64.encodeToString(identityKey.serialize(), Base64.DEFAULT), status);
    }

    private void storeIdentityKey(OmemoDevice device, String fingerprint,
            String base64Serialized, FingerprintStatus status)
    {
        SQLiteDatabase db = this.getWritableDatabase();
        String bareJid = device.getJid().toString();
        String deviceId = Integer.toString(device.getDeviceId());

        ContentValues values = new ContentValues();
        values.put(SQLiteOmemoStore.BARE_JID, bareJid);
        values.put(SQLiteOmemoStore.DEVICE_ID, deviceId);
        values.put(SQLiteOmemoStore.FINGERPRINT, fingerprint);
        values.put(SQLiteOmemoStore.IDENTITY_KEY, base64Serialized);
        values.putAll(status.toContentValues());

        String where = SQLiteOmemoStore.BARE_JID + "=? AND " + SQLiteOmemoStore.DEVICE_ID + "=?";
        String[] whereArgs = {bareJid, deviceId};

        int rows = db.update(SQLiteOmemoStore.IDENTITIES_TABLE_NAME, values, where, whereArgs);
        if (rows == 0) {
            db.insert(SQLiteOmemoStore.IDENTITIES_TABLE_NAME, null, values);
        }
    }

    public Set<IdentityKey> loadIdentityKeys(OmemoDevice device)
    {
        return loadIdentityKeys(device, null);
    }

    public Set<IdentityKey> loadIdentityKeys(OmemoDevice device, FingerprintStatus status)
    {
        Set<IdentityKey> identityKeys = new HashSet<>();
        String key;
        Cursor cursor = getIdentityKeyCursor(device, null);

        while (cursor.moveToNext()) {
            if (status != null && !status.equals(FingerprintStatus.fromCursor(cursor))) {
                continue;
            }
            try {
                key = cursor.getString(cursor.getColumnIndex(SQLiteOmemoStore.IDENTITY_KEY));
                if (StringUtils.isNotEmpty(key)) {
                    identityKeys.add(new IdentityKey(Base64.decode(key, Base64.DEFAULT), 0));
                }
                else {
                    Timber.d("Missing key (possibly pre-verified) in database for account: %s", device.getJid());
                }
            } catch (InvalidKeyException e) {
                Timber.d("Encountered invalid IdentityKey in DB for omemoDevice: %s", device);
            }
        }
        cursor.close();
        return identityKeys;
    }

    public OmemoCachedDeviceList loadCachedDeviceList(BareJid contact)
    {
        if (contact == null) {
            return null;
        }

        OmemoCachedDeviceList cachedDeviceList = new OmemoCachedDeviceList();
        final SQLiteDatabase db = this.getReadableDatabase();
        String[] columns = {SQLiteOmemoStore.DEVICE_ID, SQLiteOmemoStore.ACTIVE};
        String[] selectionArgs = {contact.toString()};

        Cursor cursor = db.query(SQLiteOmemoStore.IDENTITIES_TABLE_NAME, columns,
                SQLiteOmemoStore.BARE_JID + "=?", selectionArgs, null, null, null);

        Set<Integer> activeDevices = cachedDeviceList.getActiveDevices();
        Set<Integer> inActiveDevices = cachedDeviceList.getInactiveDevices();
        while (cursor.moveToNext()) {
            int deviceId = cursor.getInt(cursor.getColumnIndex(SQLiteOmemoStore.DEVICE_ID));
            if (cursor.getInt(cursor.getColumnIndex(SQLiteOmemoStore.ACTIVE)) == 1) {
                activeDevices.add(deviceId);
            }
            else {
                inActiveDevices.add(deviceId);
            }
        }
        cursor.close();
        return cachedDeviceList;
    }

    public void storeCachedDeviceList(OmemoDevice userDevice, BareJid contact, OmemoCachedDeviceList deviceList)
    {
        if (contact == null) {
            return;
        }

        final SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();

        // Active devices
        values.put(SQLiteOmemoStore.ACTIVE, 1);
        Set<Integer> activeDevices = deviceList.getActiveDevices();
        Timber.d("Identities table - updating for activeDevice: %s:%s", contact, activeDevices);
        for (int deviceId : activeDevices) {
            String[] selectionArgs = {contact.toString(), Integer.toString(deviceId)};

            int row = db.update(SQLiteOmemoStore.IDENTITIES_TABLE_NAME, values,
                    SQLiteOmemoStore.BARE_JID + "=? AND " + SQLiteOmemoStore.DEVICE_ID + "=?",
                    selectionArgs);

            if (row == 0) {
                /*
                 * Just logged the error. Any missing buddy identityKey will be handled by
                 * AndroidOmemoService#buddyDeviceListUpdateListener()
                 */
                Timber.d("Identities table - create new activeDevice: %s:%s ", contact, deviceId);
                values.put(SQLiteOmemoStore.BARE_JID, contact.toString());
                values.put(SQLiteOmemoStore.DEVICE_ID, deviceId);
                db.insert(SQLiteOmemoStore.IDENTITIES_TABLE_NAME, null, values);
            }
        }

        /*
         * Inactive devices:
         * Important: Must clear values before use, otherwise update exiting deviceID with new deviceID but still keeping
         * old fingerPrint and identityKey. This forbids update of the fingerPrint and IdentityKey for the new deviceID,
         * Worst it causes aTalk to crash on next access to omemo chat with the identity
         */
        values.clear();
        values.put(SQLiteOmemoStore.ACTIVE, 0);
        Set<Integer> inActiveDevices = deviceList.getInactiveDevices();
        Timber.i("Identities table updated for inactiveDevice: %s:%s", contact, inActiveDevices);
        for (int deviceId : inActiveDevices) {
            String[] selectionArgs = {contact.toString(), Integer.toString(deviceId)};

            int row = db.update(SQLiteOmemoStore.IDENTITIES_TABLE_NAME, values,
                    SQLiteOmemoStore.BARE_JID + "=? AND " + SQLiteOmemoStore.DEVICE_ID + "=?",
                    selectionArgs);
            if (row == 0) {
                Timber.w("Identities table contains no inactiveDevice (create new): %s:%s", contact, deviceId);
                values.put(SQLiteOmemoStore.BARE_JID, contact.toString());
                values.put(SQLiteOmemoStore.DEVICE_ID, deviceId);
                db.insert(SQLiteOmemoStore.IDENTITIES_TABLE_NAME, null, values);
            }
        }
    }

    public int deleteNullIdentityKeyDevices()
    {
        final SQLiteDatabase db = this.getWritableDatabase();
        return db.delete(SQLiteOmemoStore.IDENTITIES_TABLE_NAME, SQLiteOmemoStore.IDENTITY_KEY + " IS NULL", null);
    }

    public void setLastDeviceIdPublicationDate(OmemoDevice device, Date date)
    {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(SQLiteOmemoStore.LAST_MESSAGE_RX, date.getTime());

        String[] selectionArgs = {device.getJid().toString(), Integer.toString(device.getDeviceId())};

        db.update(SQLiteOmemoStore.IDENTITIES_TABLE_NAME, values,
                SQLiteOmemoStore.BARE_JID + "=? AND " + SQLiteOmemoStore.DEVICE_ID + "=?", selectionArgs);
    }

    public Date getLastDeviceIdPublicationDate(OmemoDevice device)
    {
        Cursor cursor = getIdentityKeyCursor(device, null);
        if (cursor.getCount() != 0) {
            cursor.moveToFirst();
            Long ts = cursor.getLong(cursor.getColumnIndex(SQLiteOmemoStore.LAST_MESSAGE_RX));
            cursor.close();
            return (ts != null && ts > 0) ? new Date(ts) : null;
        }
        return null;
    }


    public void setLastMessageReceiveDate(OmemoDevice device, Date date)
    {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(SQLiteOmemoStore.LAST_MESSAGE_RX, date.getTime());

        String[] selectionArgs = {device.getJid().toString(), Integer.toString(device.getDeviceId())};

        db.update(SQLiteOmemoStore.IDENTITIES_TABLE_NAME, values,
                SQLiteOmemoStore.BARE_JID + "=? AND " + SQLiteOmemoStore.DEVICE_ID + "=?", selectionArgs);
    }

    public Date getLastMessageReceiveDate(OmemoDevice device)
    {
        Cursor cursor = getIdentityKeyCursor(device, null);
        if (cursor.getCount() != 0) {
            cursor.moveToFirst();
            Long ts = cursor.getLong(cursor.getColumnIndex(SQLiteOmemoStore.LAST_MESSAGE_RX));
            cursor.close();
            return (ts != null && ts > 0) ? new Date(ts) : null;
        }
        return null;
    }


    public void setOmemoMessageCounter(OmemoDevice device, int count)
    {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(SQLiteOmemoStore.MESSAGE_COUNTER, count);

        String[] selectionArgs = {device.getJid().toString(), Integer.toString(device.getDeviceId())};

        db.update(SQLiteOmemoStore.IDENTITIES_TABLE_NAME, values,
                SQLiteOmemoStore.BARE_JID + "=? AND " + SQLiteOmemoStore.DEVICE_ID + "=?", selectionArgs);
    }

    public int getOmemoMessageCounter(OmemoDevice device)
    {
        Cursor cursor = getIdentityKeyCursor(device, null);
        if (cursor.getCount() != 0) {
            cursor.moveToFirst();
            int count = cursor.getInt(cursor.getColumnIndex(SQLiteOmemoStore.MESSAGE_COUNTER));
            cursor.close();
            return count;
        }
        return 0;
    }


    // ========= Fingerprint =========
    public FingerprintStatus getFingerprintStatus(OmemoDevice device, String fingerprint)
    {
        Cursor cursor = getIdentityKeyCursor(device, fingerprint);
        final FingerprintStatus status;
        if (cursor.getCount() > 0) {
            cursor.moveToFirst();
            status = FingerprintStatus.fromCursor(cursor);
        }
        else {
            status = null;
        }
        cursor.close();
        return status;
    }

    public long numTrustedKeys(String bareJid)
    {
        SQLiteDatabase db = getReadableDatabase();
        String[] args = {bareJid,
                FingerprintStatus.Trust.TRUSTED.toString(),
                FingerprintStatus.Trust.VERIFIED.toString(),
                FingerprintStatus.Trust.VERIFIED_X509.toString()
        };
        return DatabaseUtils.queryNumEntries(db, SQLiteOmemoStore.IDENTITIES_TABLE_NAME,
                SQLiteOmemoStore.BARE_JID + "=? AND ("
                        + SQLiteOmemoStore.TRUST + "=? OR "
                        + SQLiteOmemoStore.TRUST + "=? OR "
                        + SQLiteOmemoStore.TRUST + "=?) AND "
                        + SQLiteOmemoStore.ACTIVE + ">0", args
        );
    }

    public void storePreVerification(OmemoDevice device, String fingerprint,
            FingerprintStatus status)
    {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(SQLiteOmemoStore.BARE_JID, device.getJid().toString());
        values.put(SQLiteOmemoStore.DEVICE_ID, device.getDeviceId());
        values.put(SQLiteOmemoStore.FINGERPRINT, fingerprint);
        values.putAll(status.toContentValues());
        db.insert(SQLiteOmemoStore.IDENTITIES_TABLE_NAME, null, values);
    }

    public boolean setIdentityKeyTrust(OmemoDevice device, String fingerprint,
            FingerprintStatus fingerprintStatus)
    {
        SQLiteDatabase db = this.getWritableDatabase();
        String[] selectionArgs = {device.getJid().toString(),
                Integer.toString((device.getDeviceId())), fingerprint};
        int rows = db.update(SQLiteOmemoStore.IDENTITIES_TABLE_NAME,
                fingerprintStatus.toContentValues(), SQLiteOmemoStore.BARE_JID + "=? AND "
                        + SQLiteOmemoStore.DEVICE_ID + "=? AND " + SQLiteOmemoStore.FINGERPRINT + "=?", selectionArgs);
        return rows == 1;
    }

    // ========= OMEMO session =========
    private Cursor getCursorForSession(OmemoDevice omemoContact)
    {
        final SQLiteDatabase db = this.getReadableDatabase();
        String[] selectionArgs = {omemoContact.getJid().toString(), Integer.toString(omemoContact.getDeviceId())};
        return db.query(SQLiteOmemoStore.SESSION_TABLE_NAME, null,
                SQLiteOmemoStore.BARE_JID + "=? AND " + SQLiteOmemoStore.DEVICE_ID + "=?",
                selectionArgs, null, null, null);
    }

    public SessionRecord loadSession(OmemoDevice omemoContact)
    {
        SessionRecord sessionRecord = null;
        Cursor cursor = getCursorForSession(omemoContact);
        if (cursor.getCount() != 0) {
            cursor.moveToFirst();
            try {
                sessionRecord = new SessionRecord(Base64.decode(
                        cursor.getString(cursor.getColumnIndex(SQLiteOmemoStore.SESSION_KEY)), Base64.DEFAULT));
            } catch (IOException e) {
                Timber.w("Could not deserialize raw session. %s", e.getMessage());
            }
        }
        cursor.close();
        return sessionRecord;
    }

    public HashMap<Integer, SessionRecord> getSubDeviceSessions(BareJid contact)
    {
        int deviceId;
        SessionRecord session = null;
        HashMap<Integer, SessionRecord> deviceSessions = new HashMap<>();
        final SQLiteDatabase db = this.getReadableDatabase();

        String[] columns = {SQLiteOmemoStore.DEVICE_ID, SQLiteOmemoStore.SESSION_KEY};
        String[] selectionArgs = {contact.toString()};
        Cursor cursor = db.query(SQLiteOmemoStore.SESSION_TABLE_NAME, columns,
                SQLiteOmemoStore.BARE_JID + "=?", selectionArgs, null, null, null);

        while (cursor.moveToNext()) {
            deviceId = cursor.getInt(cursor.getColumnIndex(SQLiteOmemoStore.DEVICE_ID));
            String sessionKey = cursor.getString(cursor.getColumnIndex(SQLiteOmemoStore.SESSION_KEY));
            if (StringUtils.isNotEmpty(sessionKey)) {
                try {
                    session = new SessionRecord(Base64.decode(sessionKey, Base64.DEFAULT));
                } catch (IOException e) {
                    Timber.w("Could not deserialize raw session. %s", e.getMessage());
                }
                deviceSessions.put(deviceId, session);
            }
        }
        cursor.close();
        return deviceSessions;
    }


    public HashMap<OmemoDevice, SessionRecord> getAllDeviceSessions()
    {
        OmemoDevice omemoDevice;
        BareJid bareJid;
        int deviceId;
        String sJid;
        SessionRecord session;
        HashMap<OmemoDevice, SessionRecord> deviceSessions = new HashMap<>();
        SQLiteDatabase db = this.getReadableDatabase();

        String[] columns = {SQLiteOmemoStore.BARE_JID, SQLiteOmemoStore.DEVICE_ID, SQLiteOmemoStore.SESSION_KEY};
        Cursor cursor = db.query(SQLiteOmemoStore.SESSION_TABLE_NAME, columns,
                null, null, null, null, null);

        while (cursor.moveToNext()) {
            String sessionKey = cursor.getString(cursor.getColumnIndex(SQLiteOmemoStore.SESSION_KEY));
            if (StringUtils.isNotEmpty(sessionKey)) {
                try {
                    session = new SessionRecord(Base64.decode(sessionKey, Base64.DEFAULT));
                } catch (IOException e) {
                    Timber.w("Could not deserialize raw session! %s", e.getMessage());
                    continue;
                }

                deviceId = cursor.getInt(cursor.getColumnIndex(SQLiteOmemoStore.DEVICE_ID));
                sJid = cursor.getString(cursor.getColumnIndex(SQLiteOmemoStore.BARE_JID));
                try {
                    bareJid = JidCreate.bareFrom(sJid);
                    omemoDevice = new OmemoDevice(bareJid, deviceId);
                    deviceSessions.put(omemoDevice, session);
                } catch (XmppStringprepException e) {
                    Timber.w("Jid creation error for: %s", sJid);
                }
            }
        }
        cursor.close();
        return deviceSessions;
    }

    public void storeSession(OmemoDevice omemoContact, SessionRecord session)
    {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(SQLiteOmemoStore.BARE_JID, omemoContact.getJid().toString());
        values.put(SQLiteOmemoStore.DEVICE_ID, omemoContact.getDeviceId());
        values.put(SQLiteOmemoStore.SESSION_KEY, Base64.encodeToString(session.serialize(), Base64.DEFAULT));
        db.insert(SQLiteOmemoStore.SESSION_TABLE_NAME, null, values);
    }

    public void deleteSession(OmemoDevice omemoContact)
    {
        SQLiteDatabase db = this.getWritableDatabase();
        String[] args = {omemoContact.getJid().toString(), Integer.toString(omemoContact.getDeviceId())};
        db.delete(SQLiteOmemoStore.SESSION_TABLE_NAME,
                SQLiteOmemoStore.BARE_JID + "=? AND " + SQLiteOmemoStore.DEVICE_ID + "=?", args);
    }

    public void deleteAllSessions(BareJid contact)
    {
        SQLiteDatabase db = this.getWritableDatabase();
        String[] args = {contact.toString()};
        db.delete(SQLiteOmemoStore.SESSION_TABLE_NAME, SQLiteOmemoStore.BARE_JID + "=?", args);
    }

    public boolean containsSession(OmemoDevice omemoContact)
    {
        Cursor cursor = getCursorForSession(omemoContact);
        int count = cursor.getCount();
        cursor.close();
        return (count != 0);
    }

    // ========= Purge OMEMO dataBase =========

    /**
     * Call by OMEMO regeneration to perform clean up for:
     * 1. purge own Omemo deviceId
     * 2. All the preKey records for the deviceId
     * 3. Singed preKey data
     * 4. All the identities and sessions that are associated with the accountUuid
     *
     * @param accountId the specified AccountID to regenerate
     */
    public void purgeOmemoDb(AccountID accountId)
    {
        String account = accountId.getAccountJid();
        Timber.d(">>> Wiping OMEMO database for account : %s", account);

        SQLiteDatabase db = this.getWritableDatabase();
        String[] args = {account};

        db.delete(SQLiteOmemoStore.OMEMO_DEVICES_TABLE_NAME, SQLiteOmemoStore.OMEMO_JID + "=?", args);
        db.delete(SQLiteOmemoStore.PREKEY_TABLE_NAME, SQLiteOmemoStore.BARE_JID + "=?", args);
        db.delete(SQLiteOmemoStore.SIGNED_PREKEY_TABLE_NAME, SQLiteOmemoStore.BARE_JID + "=?", args);

        // Cleanup all the session and identities records for own resources and the contacts
        List<String> identityJids = getContactsForAccount(accountId.getAccountUuid());
        identityJids.add(account);
        for (String identityJid : identityJids) {
            args = new String[]{identityJid};
            db.delete(SQLiteOmemoStore.IDENTITIES_TABLE_NAME, SQLiteOmemoStore.BARE_JID + "=?", args);
            db.delete(SQLiteOmemoStore.SESSION_TABLE_NAME, SQLiteOmemoStore.BARE_JID + "=?", args);
        }
    }

    /**
     * Call by OMEMO purgeOwnDeviceKeys, it will clean up:
     * 1. purge own Omemo deviceId
     * 2. All the preKey records for own deviceId
     * 3. Singed preKey data
     * 4. The identities and sessions for the specified omemoDevice
     *
     * @param device the specified omemoDevice for cleanup
     */
    public void purgeOmemoDb(OmemoDevice device)
    {
        Timber.d(">>> Wiping OMEMO database for device : %s", device);
        SQLiteDatabase db = this.getWritableDatabase();
        String[] args = {device.getJid().toString(), Integer.toString(device.getDeviceId())};

        db.delete(SQLiteOmemoStore.OMEMO_DEVICES_TABLE_NAME,
                SQLiteOmemoStore.OMEMO_JID + "=? AND " + SQLiteOmemoStore.OMEMO_REG_ID + "=?", args);
        db.delete(SQLiteOmemoStore.PREKEY_TABLE_NAME,
                SQLiteOmemoStore.BARE_JID + "=? AND " + SQLiteOmemoStore.DEVICE_ID + "=?", args);
        db.delete(SQLiteOmemoStore.SIGNED_PREKEY_TABLE_NAME,
                SQLiteOmemoStore.BARE_JID + "=? AND " + SQLiteOmemoStore.DEVICE_ID + "=?", args);

        db.delete(SQLiteOmemoStore.IDENTITIES_TABLE_NAME,
                SQLiteOmemoStore.BARE_JID + "=? AND " + SQLiteOmemoStore.DEVICE_ID + "=?", args);
        db.delete(SQLiteOmemoStore.SESSION_TABLE_NAME,
                SQLiteOmemoStore.BARE_JID + "=? AND " + SQLiteOmemoStore.DEVICE_ID + "=?", args);
    }

    /**
     * Fetch all the contacts of the specified accountUuid
     *
     * @param accountUuid Account Uuid
     * @return List of contacts for the specified accountUuid
     */
    public List<String> getContactsForAccount(String accountUuid)
    {
        SQLiteDatabase db = this.getWritableDatabase();
        List<String> childContacts = new ArrayList<>();

        String[] columns = {MetaContactGroup.CONTACT_JID};
        String[] args = new String[]{accountUuid};
        Cursor cursor = db.query(MetaContactGroup.TBL_CHILD_CONTACTS, columns,
                MetaContactGroup.ACCOUNT_UUID + "=?", args, null, null, null);

        while (cursor.moveToNext()) {
            String contact = cursor.getString(0);
            if (!TextUtils.isEmpty(contact))
                childContacts.add(contact);
        }
        cursor.close();
        return childContacts;
    }


    private static class RealMigrationsHelper implements MigrationsHelper
    {
        ProtocolProviderService mProvider;

        public RealMigrationsHelper(ProtocolProviderService provider)
        {
            mProvider = provider;
        }

        @Override
        public AccountID getAccountId()
        {
            return mProvider.getAccountID();
        }

        @Override
        public Context getContext()
        {
            return aTalkApp.getGlobalContext();
        }

        //        @Override
        //        public String serializeFlags(List<Flag> flags) {
        //            return LocalStore.serializeFlags(flags);
        //        }
    }
}
