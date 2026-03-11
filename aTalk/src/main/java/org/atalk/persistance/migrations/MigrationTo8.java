package org.atalk.persistance.migrations;

import android.database.sqlite.SQLiteDatabase;

import org.atalk.crypto.omemo.SQLiteOmemoStore;

public class MigrationTo8 {
    public static void addOmemoDeviceLabel(SQLiteDatabase db) {
        db.execSQL("ALTER TABLE " + SQLiteOmemoStore.OMEMO_DEVICES_TABLE_NAME
                + " ADD " + SQLiteOmemoStore.OMEMO_LABEL + " TEXT");
        db.execSQL("ALTER TABLE " + SQLiteOmemoStore.OMEMO_DEVICES_TABLE_NAME
                + " ADD " + SQLiteOmemoStore.OMEMO_LABELSIG + " TEXT");

        db.execSQL("ALTER TABLE " + SQLiteOmemoStore.IDENTITIES_TABLE_NAME
                + " ADD " + SQLiteOmemoStore.OMEMO_LABEL + " TEXT");
        db.execSQL("ALTER TABLE " + SQLiteOmemoStore.IDENTITIES_TABLE_NAME
                + " ADD " + SQLiteOmemoStore.OMEMO_LABELSIG + " TEXT");
    }
}
