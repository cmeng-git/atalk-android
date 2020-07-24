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

import org.atalk.android.aTalkApp;
import org.atalk.persistance.FileBackend;

import java.io.File;
import java.io.IOException;

import timber.log.Timber;

/**
 * Migrate the existing aTalk project directory to aTalk root directory.
 *
 * @author Eng Chong Meng
 */
public class MigrateDir
{
    // ============ for aTalk v2.2.0 and below ============

    /**
     * Remove obsoleted AvatarCacheUtils avatars stored directory and its contents.
     */
    public static void purgeAvatarCache()
    {
        File avatarCacheDir = new File(aTalkApp.getGlobalContext().getFilesDir() + "/avatarcache");
        if (avatarCacheDir.exists()) {
            try {
                FileBackend.deleteRecursive(avatarCacheDir);
            } catch (IOException e) {
                Timber.e("Failed to purge aTalk avatarCache directory: %s", e.getMessage());
            }
        }
    }

//    /**
//     * cmeng 20200314: currently not use
//     * Fill up all the Outgoing messages' Jid
//     */
//    public static void updateSessionMessageJid() {
//        Map<String, String> sessionTable = new LinkedHashMap<>();
//        String sessionUuid, userId;
//
//        SQLiteDatabase mDB = DatabaseBackend.getWritableDB();
//        ContentValues values = new ContentValues();
//        String[] columns = {ChatSession.SESSION_UUID, ChatSession.ACCOUNT_UUID};
//
//        Cursor cursor = mDB.query(ChatSession.TABLE_NAME, columns, null, null, null, null, null);
//        while (cursor.moveToNext()) {
//            sessionUuid = cursor.getString(0);
//            userId = cursor.getString(0).split(":")[1];
//            sessionTable.put(sessionUuid, userId);
//        }
//        cursor.close();
//
//        String[] selectionArgs = {"0", "1", "55"};
//        columns = new String[] {ChatMessage.SESSION_UUID, ChatMessage.TIME_STAMP};
//        cursor = mDB.query(ChatMessage.TABLE_NAME, columns, ChatMessage.MSG_TYPE + "=? OR " + ChatMessage.MSG_TYPE
//                        + "=? OR " + ChatMessage.MSG_TYPE + "=?", selectionArgs, ChatMessage.SESSION_UUID, null, null);
//        while (cursor.moveToNext()) {
//            sessionUuid = cursor.getString(0);
//            userId = sessionTable.get(sessionUuid);
//            if (!TextUtils.isEmpty(userId)) {
//                values.clear();
//                values.put(ChatMessage.JID, userId);
//                String[] args = {sessionUuid};
//                mDB.update(ChatMessage.TABLE_NAME, values, ChatMessage.SESSION_UUID + "=?", args);
//            }
//        }
//        cursor.close();
//    }
}
