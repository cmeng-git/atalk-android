package org.atalk.persistance.migrations;

import android.database.sqlite.SQLiteDatabase;

import org.atalk.persistance.DatabaseBackend;
import org.atalk.persistance.EntityCapsCache;

public class MigrationTo7
{
    // Create the entity caps store in DB
    public static void createEntityCapsTable(SQLiteDatabase db) {
        db.execSQL("DROP TABLE IF EXISTS " + EntityCapsCache.TABLE_NAME);
        db.execSQL(DatabaseBackend.CREATE_ENTITY_CAPS_STATEMENT);
    }
}
