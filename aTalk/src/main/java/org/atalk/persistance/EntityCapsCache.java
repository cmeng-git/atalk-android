/*
 * aTalk, android VoIP and Instant Messaging client
 * Copyright 2014~2022 Eng Chong Meng
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

import android.annotation.SuppressLint;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.text.TextUtils;

import org.jivesoftware.smack.packet.XmlEnvironment;
import org.jivesoftware.smack.util.PacketParserUtils;
import org.jivesoftware.smackx.caps.cache.EntityCapsPersistentCache;
import org.jivesoftware.smackx.disco.packet.DiscoverInfo;
import org.jxmpp.JxmppContext;

import timber.log.Timber;

/**
 * Simple implementation of an EntityCapsPersistentCache that uses
 * MySQLite to store the Caps information in record for every known node.
 *
 * @author Eng Chong Meng
 */
public class EntityCapsCache implements EntityCapsPersistentCache {
    public static final String TABLE_NAME = "entityCaps";
    public static final String ENTITY_NODE_VER = "nodeVer";
    public static final String ENTITY_DISC_INFO = "discInfo";

    private final SQLiteDatabase mDB;

    /**
     * Creates a new EntityCapsCache Object.
     */
    public EntityCapsCache() {
        mDB = DatabaseBackend.getWritableDB();
    }

    /**
     * Writes the DiscoverInfo stanza to an file
     *
     * @param nodeVer Entity nodeVersion for key reference in DB
     * @param info discoInto to save to DB
     */
    @Override
    public void addDiscoverInfoByNodePersistent(String nodeVer, DiscoverInfo info) {
        ContentValues values = new ContentValues();

        values.put(ENTITY_NODE_VER, nodeVer);
        values.put(ENTITY_DISC_INFO, info.toXML().toString());
        mDB.insert(TABLE_NAME, null, values);
    }

    /**
     * Restore an DiscoverInfo stanza from DB.
     *
     * @param nodeVer Entity nodeVersion for retrieving from DB
     *
     * @return the restored DiscoverInfo
     */
    @SuppressLint("Range")
    @Override
    public DiscoverInfo lookup(String nodeVer) {
        String[] selectionArgs = {nodeVer};
        Cursor cursor = mDB.query(TABLE_NAME, null, ENTITY_NODE_VER + "=?", selectionArgs, null, null, null);

        String content = null;
        while (cursor.moveToNext()) {
            content = cursor.getString(cursor.getColumnIndex(ENTITY_DISC_INFO));
        }
        cursor.close();

        DiscoverInfo info = null;
        if (!TextUtils.isEmpty(content)) {
            try {
                info = (DiscoverInfo) PacketParserUtils.parseStanza(PacketParserUtils.getParserFor(content),
                        XmlEnvironment.EMPTY, JxmppContext.getDefaultContext());
            } catch (Exception e) {
                Timber.w("Could not restore info from DB: %s", e.getMessage());
            }
        }
        return info;
    }

    @Override
    public void emptyCache() {
        mDB.delete(TABLE_NAME, null, null);
    }
}
