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
package org.atalk.persistance.migrations.initsqldb;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import net.java.sip.communicator.service.protocol.*;

import org.json.JSONObject;

import java.security.KeyPair;
import java.util.*;

import timber.log.Timber;

public class PropertiesMigrate
{
    private static final String ACC = ".acc";
    private static final String IMPL = ".impl.";
    private static final String SERVICE = "service.";
    private static final String PLUGIN = ".plugin";
    private static final String OTR = ".otr.";
    private static final String DNS = ".util.dns.";

    private static final String CHAT_ROOMS = "chatRooms.";

    private static SQLiteDatabase mDB;
    private static ContentValues values = new ContentValues();

    /**
     * Map contains account key-data required for other xml records migration
     */
    public static HashMap<String, String> accountValues = new HashMap<>();

    /**
     * Table contains otr key-data for later migration migration
     */
    public static HashMap<String, String> otrValues = new HashMap<>();

    /**
     * Map containing AccountID private and public KeyPair
     */
    private static HashMap<String, KeyPair> keyPairs = new HashMap<>();

    /**
     * String containing list of valid accounts separated by ","; use to remove ghost accounts'
     * properties not listed in the string.
     */
    private static String accounts = "";

    /**
     * Migrate existing Properties to the new database structure
     * 1. System properties
     * 2. Account ID database
     * 3. Account properties
     */
    public static void migrateProperties(SQLiteDatabase db, SQLiteDatabase propertiesDB)
    {
        /*
         * Map containing the extracted system properties pertaining to account
         */
        Map<String, String> accountProperties = new Hashtable<>();

        /*
         * Map defining the account parameters to be mapped to account table
         */
        Map<String, String> accPara = new Hashtable<>();
        accPara.put("uuid", AccountID.ACCOUNT_UUID);
        accPara.put(ProtocolProviderFactory.PROTOCOL, AccountID.PROTOCOL);
        accPara.put(ProtocolProviderFactory.USER_ID, AccountID.USER_ID);
        accPara.put(ProtocolProviderFactory.ACCOUNT_UID, AccountID.ACCOUNT_UID);

        int idx;
        String TBL_PROPERTIES = "Properties";
        String property;
        String value;

        mDB = db;
        Cursor cursor = propertiesDB.query(TBL_PROPERTIES, null, null, null, null, null, null);
        int columnName = cursor.getColumnIndex("Name");
        int columnValue = cursor.getColumnIndex("Value");

        while (cursor.moveToNext()) {
            property = cursor.getString(columnName);
            value = cursor.getString(columnValue);

            // remove obsoleted keys/values during migration
            if (property.contains("pref_key_Proxy")
                    || property.contains("EntityCapsManager")
                    || property.contains("connectionProxy")
                    || property.contains("reconnectplugin")
                    || property.contains("IBR_REGISTRATION")) {
                continue;
            }

            /*
             * Extract and process OTR properties
             */
            if ((idx = property.lastIndexOf(OTR)) != -1) {
                property = property.substring(idx + 5);
                processOtr(property, value);
                continue;
            }

            /*
             * Extract and migrate account properties to new databases AccountID and
             * accountProperties tables
             */
            idx = property.lastIndexOf(ACC);
            if (idx != -1) {
                property = property.substring(idx + 1);
                accountProperties.put(property, value);
            }
            // Extract and migrate system properties to new 'Properties' tables
            else {
                if ((idx = property.lastIndexOf(IMPL)) != -1) {
                    property = property.substring(idx + 6);
                }
                else if ((idx = property.lastIndexOf(SERVICE)) != -1) {
                    property = property.substring(idx + 8);
                }
                else if ((idx = property.lastIndexOf(PLUGIN)) != -1) {
                    property = property.substring(idx + 8);
                }
                else if ((idx = property.lastIndexOf(DNS)) != -1) {
                    property = property.substring(idx + 6);
                }
                propInsert(property, value);
            }
        }
        cursor.close();

        // Migrate the account properties to new database accountID and accountProperties
        String accUuid;

        for (Map.Entry<String, String> entry : accountProperties.entrySet()) {
            String key = entry.getKey();
            value = entry.getValue();

            idx = key.indexOf(".");
            // extract all the valid account Uuid
            if (idx == -1) {
                if (key.equals(value))
                    accounts += value + ",";
                continue;
            }
            accUuid = key.substring(0, idx);
            property = key.substring(idx + 1);

            // Save the extracted account parameters defined in accPara into account table
            if (accPara.containsKey(property)) {
                // Keep records of some account info for later used by history records migration
                if (ProtocolProviderFactory.ACCOUNT_UID.equals(property)) {
                    // Use original accountUID for later reference but simplify the accountUid by
                    // stripping the unnecessary service name for table entry saving
                    accountValues.put(value, accUuid);
                    value = value.substring(0, value.lastIndexOf("@"));
                    accountValues.put(value, accUuid); // keep a ref copy of the new accountUid
                }
                else if (ProtocolProviderFactory.USER_ID.equals(property)) {
                    accountValues.put(accUuid, value);
                }
                accountUpdate(accUuid, accPara.get(property), value);
            }

            // cmeng!!! - Skip all chatRooms properties - keep a copy for later reference
            // store the rest in the accountProperties table with property but drop
            // any unnecessary properties e.g. "chatRooms"
            else {
                idx = property.indexOf(CHAT_ROOMS);
                if (idx != -1) {
                    //					idx = property.lastIndexOf(".");
                    //					property = property.substring(idx + 1);
                    //					accountValues.put(accUuid + property, value);
                    property = property.substring(idx + 10);
                    if (!property.contains("."))
                        accountValues.put(value, property); // chatRoomName -> chatRoomID
                    else {
                        if (!property.contains(ChatRoom.CHATROOM_NAME))
                            accountValues.put(property, value);
                    }
                    continue;
                }
                accPropInsert(accUuid, property, value);
            }
        }
        cleanAccProperties();
    }

    /*
     * Keep all otr, except global settings, for later processing
     * @see MclStorageMigrate#processOtrProperties()
     */
    private static void processOtr(String property, String value)
    {
        int idx;
        int FP_LEN = 40;
        String accountID, subProperty;
        String CONTACT_POLICY = "contact_policy";
        String FP_VERIFIED = "fingerprint_verified";
        String otrGlobal = "GLOBAL_POLICY,AUTO_INIT_PRIVATE_MESSAGING,PRIVATE_MESSAGING_MANDATORY";

        Timber.i("Process OTR properties: %s: %s", property, value);
        String[] keys = property.split("_");
        // Process all the otr Global settings
        if (otrGlobal.contains(property)) {
            propInsert("otr." + property, value);
        }
        // Process OTR contact_policy
        // Jabber_leopard_atalk_org_atalk_org_Jabber_swan_atalk_orgcontact_policy
        else if (property.contains(CONTACT_POLICY)) {
            if ((idx = property.lastIndexOf("Jabber_")) != -1) {
                // Regenerate the accountID
                keys = property.substring(0, idx).split("_");
                accountID = keys[0] + ":" + keys[1] + "@" + keys[2] + "." + keys[3];

                // Regenerate the contact
                keys = property.substring(idx).split("_");
                idx = keys[3].length() - 7;

                subProperty = keys[0] + "_" + keys[1] + "@" + keys[2] + "."
                        + keys[3].substring(0, idx) + "." + CONTACT_POLICY;

                property = accountID + "_" + subProperty;
                propInsert("otr." + property, value);
            }
        }
        // Store other account OTR properties and also keep a copy for later processing
        else {
            // process publicKey and privateKey
            // Jabber_leopard_atalk_org_atalk_org_publicKey or Jabber_leopard_atalk_org_publicKey
            if (keys[0].contains("Jabber")) {
                accountID = keys[0] + ":" + keys[1] + "@" + keys[2] + "." + keys[3];

                idx = property.lastIndexOf(keys[3]);
                subProperty = property.substring(idx).split("_")[1];
                property = accountID + "." + subProperty;
            }
            // Process fingerprint_verified
            else {
                if (property.contains(FP_VERIFIED)) {
                    idx = keys[2].length() - FP_LEN;
                    subProperty = keys[2].substring(idx) + "." + FP_VERIFIED;
                    keys[2] = keys[2].substring(0, idx);
                }
                // swan_atalk_org_fingerprints
                else {
                    subProperty = keys[3];
                }
                property = keys[0] + "@" + keys[1] + "." + keys[2] + "." + subProperty;

            }
            propInsert("otr." + property, value);
        }
        // keep a copy for later processing
        otrValues.put(property, value);
    }

    /**
     * Update account ID record and create new if not exist
     *
     * @param accountUuid Account UUID value
     * @param name Account parameter variable
     * @param value Account parameter value
     */
    private static void accountUpdate(String accountUuid, String name, String value)
    {
        // Create a new record if not exist
        Timber.i("Account Update: %s: %s: %s", accountUuid, name, value);
        values.clear();
        values.put(AccountID.ACCOUNT_UUID, accountUuid);
        values.put(AccountID.KEYS, (new JSONObject()).toString());
        mDB.insertWithOnConflict(AccountID.TABLE_NAME, null, values,
                SQLiteDatabase.CONFLICT_IGNORE);

        values.clear();
        values.put(name, value);
        final String[] args = {accountUuid};
        mDB.update(AccountID.TABLE_NAME, values, AccountID.ACCOUNT_UUID + "=?", args);
    }

    /**
     * Add account property record
     *
     * @param accountUuid Account UUID value
     * @param name Account property variable
     * @param value Account property value
     */
    private static void accPropInsert(String accountUuid, String name, String value)
    {
        Timber.i("Account Properties: %s; %s; %s", accountUuid, name, value);
        values.clear();
        values.put(AccountID.ACCOUNT_UUID, accountUuid);
        values.put(AccountID.COLUMN_NAME, name);
        values.put(AccountID.COLUMN_VALUE, value);
        mDB.insert(AccountID.TBL_PROPERTIES, null, values);
    }

    /**
     * Add system property record
     *
     * @param name System property variable
     * @param value System property value
     */
    private static void propInsert(String name, String value)
    {
        Timber.i("System Properties: %S: %s", name, value);
        values.clear();
        values.put(AccountID.COLUMN_NAME, name);
        values.put(AccountID.COLUMN_VALUE, value);
        mDB.insert("Properties", null, values);
    }

    /**
     * Cleanup all the ghost account properties left behind
     */
    private static void cleanAccProperties()
    {
        String uuid, para;
        Cursor cursor = mDB.query(AccountID.TBL_PROPERTIES, null, null, null, null, null, null);
        while (cursor.moveToNext()) {
            uuid = cursor.getString(0);
            if (!accounts.contains(uuid)) {
                para = cursor.getString(1);
                Timber.i("Deleting property for non-existence account: %s: %s", uuid, para);
                String[] args = {uuid};
                mDB.delete(AccountID.TBL_PROPERTIES, AccountID.ACCOUNT_UUID + "=?", args);
            }
        }
        cursor.close();
    }
}
