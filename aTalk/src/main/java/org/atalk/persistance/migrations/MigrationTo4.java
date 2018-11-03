package org.atalk.persistance.migrations;

import android.database.sqlite.SQLiteDatabase;

import org.atalk.crypto.omemo.SQLiteOmemoStore;

public class MigrationTo4
{
    public static void updateOmemoIdentitiesTable(SQLiteDatabase db)
    {
        db.execSQL("ALTER TABLE " + SQLiteOmemoStore.IDENTITIES_TABLE_NAME
                + " ADD " + SQLiteOmemoStore.MESSAGE_COUNTER + " INTEGER");
    }
}
