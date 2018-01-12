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

import android.content.*;
import android.database.*;
import android.database.sqlite.*;
import android.util.Base64;
import android.util.Log;

import net.java.sip.communicator.impl.configuration.SQLiteConfigurationStore;
import net.java.sip.communicator.impl.msghistory.MessageSourceService;
import net.java.sip.communicator.service.callhistory.CallHistoryService;
import net.java.sip.communicator.service.contactlist.MetaContactGroup;
import net.java.sip.communicator.service.protocol.*;
import net.java.sip.communicator.util.Logger;

import org.atalk.Config;
import org.atalk.android.*;
import org.atalk.android.gui.chat.*;
import org.atalk.crypto.omemo.*;
import org.atalk.entities.*;
import org.atalk.persistance.migrations.*;
import org.atalk.util.StringUtils;
import org.jivesoftware.smackx.caps.EntityCapsManager;
import org.jivesoftware.smackx.omemo.*;
import org.jivesoftware.smackx.omemo.exceptions.*;
import org.jivesoftware.smackx.omemo.internal.*;
import org.jxmpp.jid.*;
import org.whispersystems.libsignal.*;
import org.whispersystems.libsignal.state.*;

import java.io.*;
import java.security.cert.*;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * The <tt>DatabaseBackend</tt> uses SQLite to store all the aTalk application data in the
 * database "dbRecords.db"
 *
 * @author Eng Chong Meng
 */
public class DatabaseBackend extends SQLiteOpenHelper
{
	/**
	 * The logger
	 */
	private static final Logger logger = Logger.getLogger(DatabaseBackend.class);

	/**
	 * Name of the database and its version number
	 */
	public static final String DATABASE_NAME = "dbRecords.db";
	private static final int DATABASE_VERSION = 2;
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
			+ SQLiteOmemoStore.IDENTITY_KEY + " TEXT, UNIQUE("
			+ SQLiteOmemoStore.BARE_JID + ", " + SQLiteOmemoStore.DEVICE_ID
			+ ") ON CONFLICT REPLACE);";

	// Create session table
	public static String CREATE_SESSIONS_STATEMENT = "CREATE TABLE "
			+ SQLiteOmemoStore.SESSION_TABLE_NAME + "("
			+ SQLiteOmemoStore.BARE_JID + " TEXT, "
			+ SQLiteOmemoStore.DEVICE_ID + " INTEGER, "
			+ SQLiteOmemoStore.KEY + " TEXT, UNIQUE("
			+ SQLiteOmemoStore.BARE_JID + ", " + SQLiteOmemoStore.DEVICE_ID
			+ ") ON CONFLICT REPLACE);";

	private DatabaseBackend(Context context)
	{
		super(context, DATABASE_NAME, null, DATABASE_VERSION);
	}

	/**
	 * Get an instance of the DataBaseBackend and create one if new
	 *
	 * @param context
	 * 		context
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
		logger.info(String.format(Locale.US, "Upgrading database from version %d to version %d",
				oldVersion, newVersion));

		db.beginTransaction();
		try {
		    // cmeng: mProvider == null currently not use - must fixed if use
			RealMigrationsHelper migrationsHelper = new RealMigrationsHelper(mProvider);
			Migrations.upgradeDatabase(db, migrationsHelper);
			db.setTransactionSuccessful();
		}
		catch (Exception e) {
			logger.error("Exception while upgrading database. Resetting the DB to original", e);
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
	 * @param db
	 * 		SQLite database
	 */

	@Override
	public void onCreate(SQLiteDatabase db)
	{
		// db.execSQL("PRAGMA foreign_keys=ON;");
        String query = String.format ("PRAGMA foreign_keys =%s", "ON");
        db.execSQL (query);

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
				+ ChatSession.STATUS + " NUMBER DEFAULT 0, "
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
				+ ChatMessage.READ + " INTEGER DEFAULT 1, "
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
		logger.info("### Starting Database migration! ###");

		db.beginTransaction();
		try {
			MigrationToSqlDB.xmlToSqlDatabase(db);
			db.setTransactionSuccessful();
			logger.info("### Completed SQLite DataBase migration successfully! ###");
		} finally {
			db.endTransaction();
		}
	}

	public void createConversation(ChatSession conversation)
	{
		SQLiteDatabase db = this.getWritableDatabase();
		db.insert(ChatSession.TABLE_NAME, null, conversation.getContentValues());
	}

	public void createMessage(ChatMessageImpl message)
	{
		SQLiteDatabase db = this.getWritableDatabase();
		db.insert(ChatMessage.TABLE_NAME, null, message.getContentValues());
	}

	public void createAccount(AccountID accountId)
	{
		SQLiteDatabase db = this.getWritableDatabase();
		db.insert(AccountID.TABLE_NAME, null, accountId.getContentValues());
	}

	public void insertDiscoveryResult(EntityCapsDatabase result)
	{
		SQLiteDatabase db = this.getWritableDatabase();
		db.insert(EntityCapsDatabase.TABLE_NAME, null, result.getContentValues());
	}

	public EntityCapsManager findDiscoveryResult(final String hash, final String ver)
	{
		SQLiteDatabase db = this.getReadableDatabase();
		String[] selectionArgs = {hash, ver};
		Cursor cursor = db.query(EntityCapsDatabase.TABLE_NAME, null,
				EntityCapsDatabase.HASH + "=? AND " + EntityCapsDatabase.VER + "=?",
				selectionArgs, null, null, null);
		if (cursor.getCount() == 0) {
			cursor.close();
			return null;
		}
		cursor.moveToFirst();

		EntityCapsManager result = null;
//		try {
//			result = new EntityCapsManager(cursor);
//		}
//		catch (JSONException e) { /* result is still null */ }

		cursor.close();
		return result;
	}

	public void insertPresenceTemplate(PresenceTemplate template)
	{
		SQLiteDatabase db = this.getWritableDatabase();
		db.insert(PresenceTemplate.TABLE_NAME, null, template.getContentValues());
	}

	public List<PresenceTemplate> getPresenceTemplates()
	{
		ArrayList<PresenceTemplate> templates = new ArrayList<>();
		SQLiteDatabase db = this.getReadableDatabase();
		Cursor cursor = db.query(PresenceTemplate.TABLE_NAME, null, null, null, null, null,
				PresenceTemplate.LAST_SEEN + " desc");
		while (cursor.moveToNext()) {
			templates.add(PresenceTemplate.fromCursor(cursor));
		}
		cursor.close();
		return templates;
	}

	public void deletePresenceTemplate(PresenceTemplate template)
	{
        Log.d(Config.LOGTAG, "deleting presence template with uuid " + template.getUuid());
		SQLiteDatabase db = this.getWritableDatabase();
		String where = PresenceTemplate.UUID + "=?";
		String[] whereArgs = {template.getUuid()};
		db.delete(PresenceTemplate.TABLE_NAME, where, whereArgs);
	}

	public CopyOnWriteArrayList<ChatSession> getConversations(int status)
	{
		CopyOnWriteArrayList<ChatSession> list = new CopyOnWriteArrayList<>();
		SQLiteDatabase db = this.getReadableDatabase();
		String[] selectionArgs = {Integer.toString(status)};
		Cursor cursor = db.rawQuery("select * from " + ChatSession.TABLE_NAME
				+ " where " + ChatSession.STATUS + " =? order by "
				+ ChatSession.CREATED + " desc", selectionArgs);
		while (cursor.moveToNext()) {
			list.add(ChatSession.fromCursor(cursor));
		}
		cursor.close();
		return list;
	}

	public ArrayList<ChatMessage> getMessages(ChatSession chatSession, int limit)
	{
		return getMessages(chatSession, limit, -1);
	}

	public ArrayList<ChatMessage> getMessages(ChatSession chatSession, int limit, long timestamp)
	{
		ArrayList<ChatMessage> list = new ArrayList<>();
		SQLiteDatabase db = this.getReadableDatabase();
		Cursor cursor;
		if (timestamp == -1) {
			String[] selectionArgs = {chatSession.getSessionUuid()};
			cursor = db.query(ChatMessage.TABLE_NAME, null, ChatMessage.SESSION_UUID
					+ "=?", selectionArgs, null, null, ChatMessage.TIME_STAMP
					+ " DESC", Integer.toString(limit));
		}
		else {
			String[] selectionArgs = {chatSession.getSessionUuid(), Long.toString(timestamp)};
			cursor = db.query(ChatMessage.TABLE_NAME, null, ChatMessage.SESSION_UUID
							+ "=? and " + ChatMessage.TIME_STAMP + "<?", selectionArgs,
					null, null, ChatMessage.TIME_STAMP + " DESC",
					Integer.toString(limit));
		}
		if (cursor.getCount() > 0) {
			cursor.moveToLast();
			do {
				ChatMessageImpl message = ChatMessageImpl.fromCursor(cursor);
				message.setChatSession(chatSession);
				list.add(message);
			} while (cursor.moveToPrevious());
		}
		cursor.close();
		return list;
	}

	public Iterable<ChatMessage> getMessagesIterable(final ChatSession conversation)
	{
		return new Iterable<ChatMessage>()
		{
			@Override
			public Iterator<ChatMessage> iterator()
			{
				class MessageIterator implements Iterator<ChatMessage>
				{
					SQLiteDatabase db = getReadableDatabase();
					String[] selectionArgs = {conversation.getSessionUuid()};
					Cursor cursor = db.query(ChatMessage.TABLE_NAME, null, ChatMessage.SESSION_UUID
							+ "=?", selectionArgs, null, null, ChatMessage.TIME_STAMP
							+ " ASC", null);

					public MessageIterator()
					{
						cursor.moveToFirst();
					}

					@Override
					public boolean hasNext()
					{
						return !cursor.isAfterLast();
					}

					@Override
					public ChatMessage next()
					{
						ChatMessage message = ChatMessageImpl.fromCursor(cursor);
						cursor.moveToNext();
						return message;
					}

					@Override
					public void remove()
					{
						throw new UnsupportedOperationException();
					}
				}
				return new MessageIterator();
			}
		};
	}

	public ChatSession findConversation(final AccountID accountId, final Jid contactJid)
	{
		SQLiteDatabase db = this.getReadableDatabase();
		String[] selectionArgs = {accountId.getAccountUuid(),
				contactJid.asFullJidIfPossible().toString() + "/%",
				contactJid.asFullJidIfPossible().toString()
		};
		Cursor cursor = db.query(ChatSession.TABLE_NAME, null,
				ChatSession.ACCOUNT_UUID + "=? AND (" + ChatSession.ACCOUNT_UID
						+ " like ? OR " + ChatSession.ACCOUNT_UID + "=?)", selectionArgs, null,
				null, null);
		if (cursor.getCount() == 0) {
			cursor.close();
			return null;
		}
		cursor.moveToFirst();
		ChatSession conversation = ChatSession.fromCursor(cursor);
		cursor.close();
		return conversation;
	}

	public void updateConversation(final ChatSession conversation)
	{
		final SQLiteDatabase db = this.getWritableDatabase();
		final String[] args = {conversation.getSessionUuid()};
		db.update(ChatSession.TABLE_NAME, conversation.getContentValues(),
				ChatSession.SESSION_UUID + "=?", args);
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

	public boolean hasEnabledAccounts()
	{
		SQLiteDatabase db = this.getReadableDatabase();
		Cursor cursor = db.rawQuery("select count(" + AccountID.ACCOUNT_UUID + ")  from "
				+ AccountID.TABLE_NAME + " where not options & (1 <<1)", null);
		try {
			cursor.moveToFirst();
			int count = cursor.getInt(0);
			return (count > 0);
		}
		catch (SQLiteCantOpenDatabaseException e) {
			return true; // better safe than sorry
		}
		catch (RuntimeException e) {
			return true; // better safe than sorry
		} finally {
			if (cursor != null) {
				cursor.close();
			}
		}
	}

	@Override
	public SQLiteDatabase getWritableDatabase()
	{
		SQLiteDatabase db = super.getWritableDatabase();
		// db.execSQL("PRAGMA foreign_keys=ON;");
        String query = String.format ("PRAGMA foreign_keys =%s", "ON");
        db.execSQL (query);
		return db;
	}

	public void updateMessage(ChatMessageImpl message)
	{
		SQLiteDatabase db = this.getWritableDatabase();
		String[] args = {message.getUuid()};
		db.update(ChatMessage.TABLE_NAME, message.getContentValues(), ChatMessage.UUID
				+ "=?", args);
	}

	public void updateMessage(ChatMessageImpl message, String uuid)
	{
		SQLiteDatabase db = this.getWritableDatabase();
		String[] args = {uuid};
		db.update(ChatMessage.TABLE_NAME, message.getContentValues(), ChatMessage.UUID
				+ "=?", args);
	}

	public void readRoster(Roster roster)
	{
		SQLiteDatabase db = this.getReadableDatabase();
		Cursor cursor;
		String args[] = {roster.getAccount().getAccountUuid()};
		cursor = db.query(Contact.TABLE_NAME, null, Contact.CONTACT_UUID + "=?",
				args, null, null, null);
		while (cursor.moveToNext()) {
			// roster.initContact(Contact.fromCursor(cursor));
		}
		cursor.close();
	}

	public void writeRoster(final Roster roster)
	{
		final AccountID accountId = roster.getAccount();
		final SQLiteDatabase db = this.getWritableDatabase();
		db.beginTransaction();
		for (Contact contact : roster.getContacts()) {
//			if (contact.getOption(Contact.Options.IN_ROSTER)) {
//				db.insert(Contact.TABLE_NAME, null, contact.getContentValues());
//			}
//			else {
			String where = Contact.CONTACT_UUID + "=? AND " + Contact.CONTACT_JID +
					"=?";
			String[] whereArgs = {accountId.getAccountUuid(), contact.getAddress()};
			db.delete(Contact.TABLE_NAME, where, whereArgs);
//			}
		}
		db.setTransactionSuccessful();
		db.endTransaction();
		accountId.setRosterVersion(roster.getVersion());
		updateAccount(accountId);
	}

	// ========= OMEMO Devices =========
	public int loadOmemoRegId(BareJid user)
	{
		int registrationId = 0;
		SQLiteDatabase db = this.getReadableDatabase();
		String[] columns = {SQLiteOmemoStore.OMEMO_REG_ID};
		String[] selectionArgs = {user.toString()};

		Cursor cursor = db.query(SQLiteOmemoStore.OMEMO_DEVICES_TABLE_NAME, columns,
				SQLiteOmemoStore.OMEMO_JID + "=?", selectionArgs, null, null, null);

		if (cursor.getCount() != 0) {
			cursor.moveToFirst();
			registrationId = cursor.getInt(cursor.getColumnIndex(SQLiteOmemoStore.OMEMO_REG_ID));
		}
		cursor.close();
		return registrationId;
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
			logger.info("### Omemo device added for: " + user + ":" + defaultDeviceId);
		else
			logger.error("### Error in creating Omemo device for: " + user + ":" + defaultDeviceId);
	}

	public int loadCurrentSignedPKeyId(OmemoManager omemoManager)
	{
		int currentSignedPKeyId = getCurrentSignedPreKeyId(omemoManager);
		OmemoDevice device = omemoManager.getOwnDevice();

		SQLiteDatabase db = this.getReadableDatabase();
		String[] columns = {SQLiteOmemoStore.CURRENT_SIGNED_PREKEY_ID};
		String[] selectionArgs = {device.getJid().toString(),
				Integer.toString(device.getDeviceId())};

		Cursor cursor = db.query(SQLiteOmemoStore.OMEMO_DEVICES_TABLE_NAME, columns,
				SQLiteOmemoStore.OMEMO_JID + "=? AND " + SQLiteOmemoStore.OMEMO_REG_ID + "=?",
				selectionArgs, null, null, null);

		if (cursor.getCount() != 0) {
			cursor.moveToFirst();
			currentSignedPKeyId = cursor.getInt(
					cursor.getColumnIndex(SQLiteOmemoStore.CURRENT_SIGNED_PREKEY_ID));
		}
		cursor.close();
		return currentSignedPKeyId;
	}

	public void storeCurrentSignedPKeyId(OmemoManager omemoManager, int currentSignedPreKeyId)
	{
		SQLiteDatabase db = this.getWritableDatabase();
		OmemoDevice device = omemoManager.getOwnDevice();
		String[] selectionArgs = {device.getJid().toString(),
				Integer.toString(device.getDeviceId())};

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
		String[] selectionArgs = {device.getJid().toString(),
				Integer.toString(device.getDeviceId())};

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
		String[] selectionArgs = {device.getJid().toString(),
				Integer.toString(device.getDeviceId())};

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
	private Cursor getCursorForPreKey(OmemoManager omemoManager, int preKeyId)
	{
		SQLiteDatabase db = this.getReadableDatabase();
		OmemoDevice device = omemoManager.getOwnDevice();

		String[] columns = {SQLiteOmemoStore.PRE_KEYS};
		String[] selectionArgs = {device.getJid().toString(),
				Integer.toString(device.getDeviceId()), Integer.toString(preKeyId)};

		return db.query(SQLiteOmemoStore.PREKEY_TABLE_NAME, columns,
				SQLiteOmemoStore.BARE_JID + "=? AND " + SQLiteOmemoStore.DEVICE_ID + "=? AND "
						+ SQLiteOmemoStore.PRE_KEY_ID + "=?",
				selectionArgs, null, null, null);
	}

	public PreKeyRecord loadPreKey(OmemoManager omemoManager, int preKeyId)
	{
		PreKeyRecord record = null;
		Cursor cursor = getCursorForPreKey(omemoManager, preKeyId);
		if (cursor.getCount() != 0) {
			cursor.moveToFirst();
			try {
				record = new PreKeyRecord(Base64.decode(
						cursor.getString(cursor.getColumnIndex(SQLiteOmemoStore.PRE_KEYS)),
						Base64.DEFAULT));
			}
			catch (IOException e) {
				throw new AssertionError(e);
			}
		}
		cursor.close();
		return record;
	}

	public HashMap<Integer, PreKeyRecord> loadPreKeys(OmemoManager omemoManager)
	{
		int preKeyId;
		PreKeyRecord preKeyRecord;
		String ORDER_ASC = SQLiteOmemoStore.PRE_KEY_ID + " ASC";
		HashMap<Integer, PreKeyRecord> PreKeyRecords = new HashMap<>();

		SQLiteDatabase db = this.getReadableDatabase();
		OmemoDevice device = omemoManager.getOwnDevice();

		String[] columns = {SQLiteOmemoStore.PRE_KEY_ID, SQLiteOmemoStore.PRE_KEYS};
		String[] selectionArgs = {device.getJid().toString(),
				Integer.toString(device.getDeviceId())};

		Cursor cursor = db.query(SQLiteOmemoStore.PREKEY_TABLE_NAME, columns,
				SQLiteOmemoStore.BARE_JID + "=? AND " + SQLiteOmemoStore.DEVICE_ID + "=?",
				selectionArgs, null, null, ORDER_ASC);

		while (cursor.moveToNext()) {
			preKeyId = cursor.getInt(0);
			try {
				preKeyRecord
						= new PreKeyRecord(Base64.decode(cursor.getString(1), Base64.DEFAULT));
			}
			catch (IOException e) {
				throw new AssertionError(e);
			}
			PreKeyRecords.put(preKeyId, preKeyRecord);
		}
		cursor.close();
		return PreKeyRecords;
	}

	public void storePreKey(OmemoManager omemoManager, PreKeyRecord record)
	{
		SQLiteDatabase db = this.getWritableDatabase();
		OmemoDevice device = omemoManager.getOwnDevice();

		ContentValues values = new ContentValues();
		values.put(SQLiteOmemoStore.BARE_JID, device.getJid().toString());
		values.put(SQLiteOmemoStore.DEVICE_ID, device.getDeviceId());
		values.put(SQLiteOmemoStore.PRE_KEY_ID, record.getId());
		values.put(SQLiteOmemoStore.PRE_KEYS,
				Base64.encodeToString(record.serialize(), Base64.DEFAULT));
		db.insert(SQLiteOmemoStore.PREKEY_TABLE_NAME, null, values);
	}

	public void deletePreKey(OmemoManager omemoManager, int preKeyId)
	{
		SQLiteDatabase db = this.getWritableDatabase();
		OmemoDevice device = omemoManager.getOwnDevice();
		String[] args = {device.getJid().toString(),
				Integer.toString(device.getDeviceId()), Integer.toString(preKeyId)};

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
		String[] selectionArgs = {device.getJid().toString(),
				Integer.toString(device.getDeviceId())};

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
	private Cursor getCursorForSignedPreKey(OmemoManager omemoManager, int signedPreKeyId)
	{
		SQLiteDatabase db = this.getReadableDatabase();
		OmemoDevice device = omemoManager.getOwnDevice();

		String[] columns = {SQLiteOmemoStore.SIGNED_PRE_KEYS};
		String[] selectionArgs = {device.getJid().toString(),
				Integer.toString(device.getDeviceId()), Integer.toString(signedPreKeyId)};
		Cursor cursor = db.query(SQLiteOmemoStore.SIGNED_PREKEY_TABLE_NAME, columns,
				SQLiteOmemoStore.BARE_JID + "=? AND "
						+ SQLiteOmemoStore.DEVICE_ID + "=? AND "
						+ SQLiteOmemoStore.SIGNED_PRE_KEY_ID + "=?", selectionArgs, null, null,
				null);
		return cursor;
	}

	public SignedPreKeyRecord loadSignedPreKey(OmemoManager omemoManager, int signedPreKeyId)
	{
		SignedPreKeyRecord record = null;
		Cursor cursor = getCursorForSignedPreKey(omemoManager, signedPreKeyId);
		if (cursor.getCount() != 0) {
			cursor.moveToFirst();
			try {
				record = new SignedPreKeyRecord(Base64.decode(
						cursor.getString(cursor.getColumnIndex(SQLiteOmemoStore.SIGNED_PRE_KEYS)),
						Base64.DEFAULT));
			}
			catch (IOException e) {
				cursor.close();
				throw new AssertionError(e);
			}
		}
		cursor.close();
		return record;
	}

	public HashMap<Integer, SignedPreKeyRecord> loadSignedPreKeys(OmemoDevice device)
	{
		int preKeyId;
		SignedPreKeyRecord signedPreKeysRecord;
		HashMap<Integer, SignedPreKeyRecord> preKeys = new HashMap<>();

		SQLiteDatabase db = this.getReadableDatabase();
		String[] columns = {SQLiteOmemoStore.SIGNED_PRE_KEY_ID, SQLiteOmemoStore.SIGNED_PRE_KEYS};
		String[] selectionArgs = {device.getJid().toString(),
				Integer.toString(device.getDeviceId())};
		Cursor cursor = db.query(SQLiteOmemoStore.SIGNED_PREKEY_TABLE_NAME, columns,
				SQLiteOmemoStore.BARE_JID + "=? AND " + SQLiteOmemoStore.DEVICE_ID + "=?",
				selectionArgs, null, null, null);

		while (cursor.moveToNext()) {
			try {
				preKeyId = cursor.getInt(
						cursor.getColumnIndex(SQLiteOmemoStore.SIGNED_PRE_KEY_ID));
				signedPreKeysRecord = new SignedPreKeyRecord(Base64.decode(cursor.getString(
						cursor.getColumnIndex(SQLiteOmemoStore.SIGNED_PRE_KEYS)), Base64.DEFAULT));
				preKeys.put(preKeyId, signedPreKeysRecord);
			}
			catch (IOException ex) {
				ex.printStackTrace();
			}
		}
		cursor.close();
		return preKeys;
	}

	public void storeSignedPreKey(OmemoDevice device, int signedPreKeyId,
			SignedPreKeyRecord record)
	{
		SQLiteDatabase db = this.getWritableDatabase();
		ContentValues values = new ContentValues();
		values.put(SQLiteOmemoStore.BARE_JID, device.getJid().toString());
		values.put(SQLiteOmemoStore.DEVICE_ID, device.getDeviceId());
		values.put(SQLiteOmemoStore.SIGNED_PRE_KEY_ID, signedPreKeyId);
		values.put(SQLiteOmemoStore.SIGNED_PRE_KEYS,
				Base64.encodeToString(record.serialize(), Base64.DEFAULT));
		values.put(SQLiteOmemoStore.LAST_RENEWAL_DATE, record.getTimestamp());
		db.insert(SQLiteOmemoStore.SIGNED_PREKEY_TABLE_NAME, null, values);
	}

	public void deleteSignedPreKey(OmemoManager omemoManager, int signedPreKeyId)
	{
		SQLiteDatabase db = this.getWritableDatabase();
		OmemoDevice device = omemoManager.getOwnDevice();

		String[] args = {device.getJid().toString(), Integer.toString(device.getDeviceId()),
				Integer.toString(signedPreKeyId)};
		db.delete(SQLiteOmemoStore.SIGNED_PREKEY_TABLE_NAME,
				SQLiteOmemoStore.BARE_JID + "=? AND " + SQLiteOmemoStore.DEVICE_ID + "=? AND "
						+ SQLiteOmemoStore.SIGNED_PRE_KEY_ID + "=?", args);
	}

	public Date getLastSignedPreKeyRenewal(OmemoManager omemoManager)
	{
		SQLiteDatabase db = this.getReadableDatabase();
		OmemoDevice device = omemoManager.getOwnDevice();

		String[] columns = {SQLiteOmemoStore.LAST_RENEWAL_DATE};
		String[] selectionArgs = {device.getJid().toString(),
				Integer.toString(device.getDeviceId())};

		Cursor cursor = db.query(SQLiteOmemoStore.SIGNED_PREKEY_TABLE_NAME, columns,
				SQLiteOmemoStore.BARE_JID + "=? AND " + SQLiteOmemoStore.DEVICE_ID + "=?",
				selectionArgs, null, null, null);

		if (cursor.getCount() != 0) {
			cursor.moveToFirst();
			Long ts = cursor.getLong(cursor.getColumnIndex(SQLiteOmemoStore.LAST_RENEWAL_DATE));
			cursor.close();
			return ts != -1 ? new Date(ts) : null;
		}
		return null;
	}

	public void setLastSignedPreKeyRenewal(OmemoManager omemoManager, Date date)
	{
		SQLiteDatabase db = this.getWritableDatabase();
		OmemoDevice device = omemoManager.getOwnDevice();

		ContentValues values = new ContentValues();
		values.put(SQLiteOmemoStore.LAST_RENEWAL_DATE, date.getTime());

		String[] selectionArgs = {device.getJid().toString(),
				Integer.toString(device.getDeviceId())};
		db.update(SQLiteOmemoStore.SIGNED_PREKEY_TABLE_NAME, values,
				SQLiteOmemoStore.BARE_JID + "=? AND " + SQLiteOmemoStore.DEVICE_ID + "=?",
				selectionArgs);
	}

	private int getCurrentSignedPreKeyId(OmemoManager omemoManager)
	{
		int currentSignedPKId = 1;
		SQLiteDatabase db = this.getReadableDatabase();
		OmemoDevice device = omemoManager.getOwnDevice();

		String[] columns = {SQLiteOmemoStore.SIGNED_PRE_KEY_ID};
		String[] selectionArgs = {device.getJid().toString(),
				Integer.toString(device.getDeviceId())};

		Cursor cursor = db.query(SQLiteOmemoStore.SIGNED_PREKEY_TABLE_NAME, columns,
				SQLiteOmemoStore.BARE_JID + "=? AND " + SQLiteOmemoStore.DEVICE_ID + "=?",
				selectionArgs, null, null, null);

		if (cursor.getCount() != 0) {
			cursor.moveToFirst();
			currentSignedPKId
					= cursor.getInt(cursor.getColumnIndex(SQLiteOmemoStore.SIGNED_PRE_KEY_ID));
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
				selectionArgs.toArray(new String[selectionArgs.size()]), null, null, null);
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
				if (!StringUtils.isNullOrEmpty(identityKP)) {
					identityKeyPair = new IdentityKeyPair(Base64.decode(identityKP, Base64.DEFAULT));
				}
			}
			catch (InvalidKeyException e) {
				deleteIdentityKey(device);
				String msg = "Invalid IdentityKeyPair for omemoDevice: " + device + "; " + e.getMessage();
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
				if (!StringUtils.isNullOrEmpty(key)) {
					identityKey = new IdentityKey(Base64.decode(key, Base64.DEFAULT), 0);
				}
			}
			catch (InvalidKeyException e) {
				deleteIdentityKey(device);
				String msg = "Invalid IdentityKey for omemoDevice: " + device + ": "
						+ e.getMessage();
				throw new CorruptedOmemoKeyException(msg);
			}
		}
		return identityKey;
	}

	// Use to delete the device corrupted identityKey - later rebuilt when device restart
	private void deleteIdentityKey(OmemoDevice device)
	{
		SQLiteDatabase db = this.getWritableDatabase();
		String whereArgs[] = {device.getJid().toString(), Integer.toString(device.getDeviceId())};

		db.delete(SQLiteOmemoStore.IDENTITIES_TABLE_NAME, SQLiteOmemoStore.BARE_JID + "=? AND "
				+ SQLiteOmemoStore.DEVICE_ID + "=?", whereArgs);
	}

	public void storeIdentityKeyPair(OmemoManager omemoManager, IdentityKeyPair identityKeyPair,
			String fingerprint)
	{
		OmemoDevice device = omemoManager.getOwnDevice();
		storeIdentityKey(device, fingerprint,
				Base64.encodeToString(identityKeyPair.serialize(), Base64.DEFAULT),
				FingerprintStatus.createActiveVerified(false));
	}

	public void storeIdentityKey(OmemoDevice device, IdentityKey identityKey, String fingerprint,
			FingerprintStatus status)
	{
		storeIdentityKey(device, fingerprint,
				Base64.encodeToString(identityKey.serialize(), Base64.DEFAULT), status);
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
			if (status != null && !FingerprintStatus.fromCursor(cursor).equals(status)) {
				continue;
			}
			try {
				key = cursor.getString(cursor.getColumnIndex(SQLiteOmemoStore.IDENTITY_KEY));
				if (!StringUtils.isNullOrEmpty(key)) {
					identityKeys.add(new IdentityKey(Base64.decode(key, Base64.DEFAULT), 0));
				}
				else {
					logger.debug("Missing key (possibly pre-verified) in database for account: "
							+ device.getJid());
				}
			}
			catch (InvalidKeyException e) {
				logger.debug("Encountered invalid IdentityKey in DB for omemoDevice: "
						+ device);
			}
		}
		cursor.close();
		return identityKeys;
	}

	public CachedDeviceList loadCachedDeviceList(BareJid contact)
	{
		if (contact == null) {
			return null;
		}

		CachedDeviceList cachedDeviceList = new CachedDeviceList();
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

	public void storeCachedDeviceList(OmemoManager omemoManager, BareJid contact, CachedDeviceList deviceList)
            throws CannotEstablishOmemoSessionException, CorruptedOmemoKeyException
	{
		if (contact == null) {
			return;
		}

		final SQLiteDatabase db = this.getWritableDatabase();
		ContentValues values = new ContentValues();

		// Active devices
		values.put(SQLiteOmemoStore.ACTIVE, 1);
		Set<Integer> activeDevices = deviceList.getActiveDevices();
		for (int deviceId : activeDevices) {
			String[] selectionArgs = {contact.toString(), Integer.toString(deviceId)};

			int row = db.update(SQLiteOmemoStore.IDENTITIES_TABLE_NAME, values,
					SQLiteOmemoStore.BARE_JID + "=? AND " + SQLiteOmemoStore.DEVICE_ID + "=?",
					selectionArgs);
			if (row == 0) {
				logger.warn("Identities table contains no activeDevice (fetch new): " + contact
						+ ":" + deviceId);
				// create the identities & preKeys for missing deviceId
				OmemoDevice omemoDevice = new OmemoDevice(contact, deviceId);

				// may throws CannotEstablishOmemoSessionException, CorruptedOmemoKeyException
				OmemoService.getInstance().buildSessionFromOmemoBundle(omemoManager, omemoDevice, false);

//				FingerprintStatus fpStatus = FingerprintStatus.createActiveUndecided();
//				values = fpStatus.toContentValues();
//				values.put(SQLiteOmemoStore.BARE_JID, contact.toString());
//				values.put(SQLiteOmemoStore.DEVICE_ID, deviceId);
//				db.insert(SQLiteOmemoStore.IDENTITIES_TABLE_NAME, null, values);
			}
		}

		// Inactive devices
		values.put(SQLiteOmemoStore.ACTIVE, 0);
		Set<Integer> inActiveDevices = deviceList.getInactiveDevices();
		for (int deviceId : inActiveDevices) {
			String[] selectionArgs = {contact.toString(), Integer.toString(deviceId)};

			int row = db.update(SQLiteOmemoStore.IDENTITIES_TABLE_NAME, values,
					SQLiteOmemoStore.BARE_JID + "=? AND " + SQLiteOmemoStore.DEVICE_ID + "=?",
					selectionArgs);
			if (row == 0) {
				logger.warn("Identities table contains no inactiveDevice: " + contact
						+ ":" + deviceId);
				new Exception().printStackTrace();
			}
		}
	}

	public Date getLastMessageReceiveDate(OmemoDevice device)
	{
		Cursor cursor = getIdentityKeyCursor(device, null);
		if (cursor.getCount() != 0) {
			cursor.moveToFirst();
			Long ts = cursor.getLong(cursor.getColumnIndex(SQLiteOmemoStore.LAST_ACTIVATION));
			cursor.close();
			return ts != -1 ? new Date(ts) : null;
		}
		return null;
	}

	public void setLastMessageReceiveDate(OmemoDevice device, Date date)
	{
		SQLiteDatabase db = this.getWritableDatabase();
		ContentValues values = new ContentValues();
		values.put(SQLiteOmemoStore.LAST_ACTIVATION, date.getTime());

		String[] selectionArgs = {device.getJid().toString(),
				Integer.toString(device.getDeviceId())};

		db.update(SQLiteOmemoStore.IDENTITIES_TABLE_NAME, values,
				SQLiteOmemoStore.BARE_JID + "=? AND " + SQLiteOmemoStore.DEVICE_ID + "=?",
				selectionArgs);
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
						+ SQLiteOmemoStore.DEVICE_ID + "=? AND " + SQLiteOmemoStore.FINGERPRINT +
						"=?",
				selectionArgs);
		return rows == 1;
	}

	public boolean setIdentityKeyCertificate(OmemoDevice device, String fingerprint,
			X509Certificate x509Certificate)
	{
		SQLiteDatabase db = this.getWritableDatabase();
		String[] selectionArgs = {device.getJid().toString(),
				Integer.toString(device.getDeviceId()), fingerprint};
		try {
			ContentValues values = new ContentValues();
			values.put(SQLiteOmemoStore.CERTIFICATE, x509Certificate.getEncoded());
			return db.update(SQLiteOmemoStore.IDENTITIES_TABLE_NAME, values,
					SQLiteOmemoStore.BARE_JID + "=? AND " + SQLiteOmemoStore.DEVICE_ID + "=? AND "
							+ SQLiteOmemoStore.FINGERPRINT + "=?", selectionArgs) == 1;
		}
		catch (CertificateEncodingException e) {
			Log.d(Config.LOGTAG, "could not encode certificate");
			return false;
		}
	}

	public X509Certificate getIdentityKeyCertificate(OmemoDevice device, String fingerprint)
	{
		SQLiteDatabase db = this.getReadableDatabase();
		String[] selectionArgs = {device.getJid().toString(),
				Integer.toString(device.getDeviceId()), fingerprint};
		String[] columns = {SQLiteOmemoStore.CERTIFICATE};
		String selection = SQLiteOmemoStore.BARE_JID + "=? AND "
				+ SQLiteOmemoStore.DEVICE_ID + "=? AND " + SQLiteOmemoStore.FINGERPRINT + "=?";
		Cursor cursor = db.query(SQLiteOmemoStore.IDENTITIES_TABLE_NAME, columns, selection,
				selectionArgs, null, null, null);
		if (cursor.getCount() < 1) {
			return null;
		}
		else {
			cursor.moveToFirst();
			byte[] certificate = cursor.getBlob(
					cursor.getColumnIndex(SQLiteOmemoStore.CERTIFICATE));
			cursor.close();
			if ((certificate == null) || (certificate.length == 0)) {
				return null;
			}
			try {
				CertificateFactory certificateFactory = CertificateFactory.getInstance("X.509");
				return (X509Certificate) certificateFactory.generateCertificate(
						new ByteArrayInputStream(certificate));
			}
			catch (CertificateException e) {
				Log.d(Config.LOGTAG, "certificate exception " + e.getMessage());
				return null;
			}
		}
	}

	// ========= OMEMO session =========
	private Cursor getCursorForSession(OmemoDevice omemoContact)
	{
		final SQLiteDatabase db = this.getReadableDatabase();
		String[] selectionArgs = {omemoContact.getJid().toString(),
				Integer.toString(omemoContact.getDeviceId())};
		return db.query(SQLiteOmemoStore.SESSION_TABLE_NAME, null,
				SQLiteOmemoStore.BARE_JID + "=? AND " + SQLiteOmemoStore.DEVICE_ID + "=?",
				selectionArgs, null, null, null);
	}

	public SessionRecord loadSession(OmemoDevice omemoContact)
	{
		SessionRecord session = null;
		Cursor cursor = getCursorForSession(omemoContact);
		if (cursor.getCount() != 0) {
			cursor.moveToFirst();
			try {
				session = new SessionRecord(Base64.decode(
						cursor.getString(cursor.getColumnIndex(SQLiteOmemoStore.KEY)),
						Base64.DEFAULT));
			}
			catch (IOException e) {
				cursor.close();
				throw new AssertionError(e);
			}
		}
		cursor.close();
		return session;
	}

	public HashMap<Integer, SessionRecord> getSubDeviceSessions(BareJid contact)
	{
		int deviceId;
		SessionRecord session = null;
		HashMap<Integer, SessionRecord> deviceSessions = new HashMap<>();
		final SQLiteDatabase db = this.getReadableDatabase();

		String[] columns = {SQLiteOmemoStore.DEVICE_ID, SQLiteOmemoStore.KEY};
		String[] selectionArgs = {contact.toString()};
		Cursor cursor = db.query(SQLiteOmemoStore.SESSION_TABLE_NAME, columns,
				SQLiteOmemoStore.BARE_JID + "=?", selectionArgs, null, null, null);

		while (cursor.moveToNext()) {
			deviceId = cursor.getInt(cursor.getColumnIndex(SQLiteOmemoStore.DEVICE_ID));
			String sessionKey = cursor.getString(cursor.getColumnIndex(SQLiteOmemoStore.KEY));
			if (!StringUtils.isNullOrEmpty(sessionKey)) {
				try {
					session = new SessionRecord(Base64.decode(sessionKey, Base64.DEFAULT));
				}
				catch (IOException e) {
					e.printStackTrace();
				}
				deviceSessions.put(deviceId, session);
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
		values.put(SQLiteOmemoStore.KEY,
				Base64.encodeToString(session.serialize(), Base64.DEFAULT));
		db.insert(SQLiteOmemoStore.SESSION_TABLE_NAME, null, values);
	}

	public void deleteSession(OmemoDevice omemoContact)
	{
		SQLiteDatabase db = this.getWritableDatabase();
		String[] args = {omemoContact.getJid().toString(),
				Integer.toString(omemoContact.getDeviceId())};
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
		return count != 0;
	}

	// ========= Purge OMEMO dataBase =========
	public void purgeOmemoDb(OmemoDevice device)
	{
		logger.debug(">>> Wiping OMEMO database for account : " + device.getJid());
		SQLiteDatabase db = this.getWritableDatabase();
		String[] deleteArgs = {device.getJid().toString(), Integer.toString(device.getDeviceId())};

		db.delete(SQLiteOmemoStore.OMEMO_DEVICES_TABLE_NAME,
				SQLiteOmemoStore.OMEMO_JID + "=? AND " + SQLiteOmemoStore.OMEMO_REG_ID + "=?",
				deleteArgs);
		db.delete(SQLiteOmemoStore.PREKEY_TABLE_NAME,
				SQLiteOmemoStore.BARE_JID + "=? AND " + SQLiteOmemoStore.DEVICE_ID + "=?",
				deleteArgs);
		db.delete(SQLiteOmemoStore.SIGNED_PREKEY_TABLE_NAME,
				SQLiteOmemoStore.BARE_JID + "=? AND " + SQLiteOmemoStore.DEVICE_ID + "=?",
				deleteArgs);
		db.delete(SQLiteOmemoStore.IDENTITIES_TABLE_NAME,
				SQLiteOmemoStore.BARE_JID + "=? AND " + SQLiteOmemoStore.DEVICE_ID + "=?",
				deleteArgs);
		db.delete(SQLiteOmemoStore.SESSION_TABLE_NAME,
				SQLiteOmemoStore.BARE_JID + "=? AND " + SQLiteOmemoStore.DEVICE_ID + "=?",
				deleteArgs);
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
