package org.atalk.persistance.migrations;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;

import net.java.sip.communicator.impl.configuration.SQLiteConfigurationStore;

import org.atalk.android.aTalkApp;
import org.atalk.persistance.FileBackend;
import org.atalk.crypto.omemo.SQLiteOmemoStore;
import org.jivesoftware.smackx.avatar.vcardavatar.VCardAvatarManager;

import java.io.File;

public class MigrationTo3
{
    public static void updateSQLDatabase(SQLiteDatabase db) {
        updateOmemoIdentitiesTable(db);
        clearUnsedTableEntries(db);
        deleteOldDatabase();
    }

    private static void updateOmemoIdentitiesTable(SQLiteDatabase db) {
        db.execSQL("ALTER TABLE " + SQLiteOmemoStore.IDENTITIES_TABLE_NAME
                + " ADD " + SQLiteOmemoStore.LAST_DEVICE_ID_PUBLISH + " NUMBER");
        db.execSQL("ALTER TABLE " + SQLiteOmemoStore.IDENTITIES_TABLE_NAME
                + " ADD " + SQLiteOmemoStore.LAST_MESSAGE_RX + " NUMBER");
    }

    private static void clearUnsedTableEntries(SQLiteDatabase db) {
        // remove old property name
        String[] args = {"replacement.%"};
        db.delete(SQLiteConfigurationStore.TABLE_NAME, SQLiteConfigurationStore.COLUMN_NAME + " LIKE ?", args);
    }

    private static void deleteOldDatabase() {
        // Proceed to delete if "SQLiteConfigurationStore.db" exist
        String PROPERTIES_DB = "net.java.sip.communicator.impl.configuration.SQLiteConfigurationStore.db";
        Context ctx = aTalkApp.getGlobalContext();
        String DBPath = ctx.getDatabasePath(PROPERTIES_DB).getPath();
        File dbFile = new File(DBPath);
        if (dbFile.exists()) {
            ctx.deleteDatabase(PROPERTIES_DB);
        }

        // Delete old history files
        String filesDir = ctx.getFilesDir().getAbsolutePath();
        File omemoDir = new File(filesDir + "/OMEMO_Store");
        File historyDir = new File(filesDir + "/history_ver1.0");
        File xmlFP = new File(filesDir + "/contactlist.xml");

        try {
            if (historyDir.exists())
                FileBackend.deleteRecursive(historyDir);

            if (xmlFP.exists())
                FileBackend.deleteRecursive(xmlFP);

            if (omemoDir.exists())
                FileBackend.deleteRecursive(omemoDir);
        }
        catch (Exception ignore) {
        }

        // Clean up avatar store to remove files named with old userID
        VCardAvatarManager.clearPersistentStorage();
    }
}
