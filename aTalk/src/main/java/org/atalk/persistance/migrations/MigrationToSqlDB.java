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
package org.atalk.persistance.migrations;

import android.content.*;
import android.database.sqlite.SQLiteDatabase;

import net.java.sip.communicator.util.Logger;

import org.atalk.android.aTalkApp;
import org.atalk.persistance.migrations.initsqldb.*;

import java.io.File;

/**
 * Migrate the existing database and xml file records into SQL Lite databases.
 *
 * @author: Eng Chong Meng
 *
 */
public class MigrationToSqlDB
{
	/**
	 * The <tt>Logger</tt> used by this <tt>AccountManagerImpl</tt> instance for logging output.
	 */
	private final static Logger logger = Logger.getLogger(MigrationToSqlDB.class);

	private static SQLiteDatabase mDB;
	private static ContentValues values = new ContentValues();

	/**
	 * Migrate the existing database and xml file records into SQL Lite databases.
	 * <p>
	 * ### Do not change the order of migrations as it affects inter-dependency variables use
	 * during the migrations.
	 * <p>
	 * 1. Properties
	 * 2. MetaContact List group and child contacts
	 * 3. messages (chat & muc) and file history xml records
	 * 4. call history xml records
	 * 5. Recent messages xml records
	 *
	 * @param db
	 * 		the SQLiteDatabase dbRecords.db
	 */
	public static void xmlToSqlDatabase(SQLiteDatabase db)
	{
		String PROPERTIES_DB // = SQLiteConfigurationStore.class.getName() + "" + ".db";
				= "net.java.sip.communicator.impl.configuration.SQLiteConfigurationStore.db";

		/*
		 * Do not proceed with data migration if this is new apk installation
		 * i.e. "SQLiteConfigurationStore.db" is missing
		 */
		Context ctx = aTalkApp.getGlobalContext();
		String DBPath = ctx.getDatabasePath(PROPERTIES_DB).getPath();
		File dbFile = new File(DBPath);
		// File not found, just initialize virgin metaContactGroup table and return
		if (!dbFile.exists()) {
			logger.info("Init metaContactList table for newly installed apk.");
			MclStorageMigrate.initMCLDataBase(db);
			return;
		}

		/*
		 * Convert and split old properties to system properties and account properties
		 */
		SQLiteDatabase propertiesDB
				= SQLiteDatabase.openDatabase(DBPath, null, SQLiteDatabase.OPEN_READONLY);
		PropertiesMigrate.migrateProperties(db, propertiesDB);
		propertiesDB.close();

		/*
		 * Migrate and convert contactlist.xml to sql database
		 */
		MclStorageMigrate.migrateMetaContactList(db);

		/*
		 * Migrate chat/muc history and file history records
		 */
		ChatHistoryMigrate.migrateChatHistory(db);

		/*
		 * Migrate call history records
		 */
		CallHistoryMigrate.migrateCallHistory(db);

		/*
		 * Migrate recent message records
		 */
		RecentMessageMigrate.migrateRecentMessage(db);
	}
}
