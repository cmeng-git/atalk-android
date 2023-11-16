package org.atalk.persistance.migrations;

import static org.atalk.persistance.DatabaseBackend.CREATE_OMEMO_DEVICES_STATEMENT;

import android.database.sqlite.SQLiteDatabase;

import org.atalk.crypto.omemo.SQLiteOmemoStore;

import timber.log.Timber;

public class MigrationTo5
{
    public static void updateOmemoDevicesTable(SQLiteDatabase db)
    {
        String OLD_TABLE = "omemo_devices_old";
        db.execSQL("DROP TABLE IF EXISTS " + OLD_TABLE);
        db.execSQL("ALTER TABLE " + SQLiteOmemoStore.OMEMO_DEVICES_TABLE_NAME + " RENAME TO " + OLD_TABLE);

        db.execSQL("DROP TABLE IF EXISTS " + SQLiteOmemoStore.OMEMO_DEVICES_TABLE_NAME);
        db.execSQL(CREATE_OMEMO_DEVICES_STATEMENT);
        db.execSQL("INSERT INTO " + SQLiteOmemoStore.OMEMO_DEVICES_TABLE_NAME + " SELECT * FROM " + OLD_TABLE);

        db.execSQL("DROP TABLE IF EXISTS " + OLD_TABLE);
        Timber.d("Updated omemo_devices table successfully!");
    }
}
