package org.atalk.persistance.migrations;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;

import net.java.sip.communicator.impl.configuration.SQLiteConfigurationStore;

import org.atalk.android.aTalkApp;
import org.atalk.android.util.FileAccess;
import org.jivesoftware.smackx.avatar.vcardavatar.VCardAvatarManager;

import java.io.File;

public class MigrationTo3
{
    public static void createOmemoTables(SQLiteDatabase db) {
        // remove old property name
        String[] args = {"replacement.%"};
        db.delete(SQLiteConfigurationStore.TABLE_NAME, SQLiteConfigurationStore.COLUMN_NAME +
                        "LIKE ", args);
    }

    private void deleteOldDatabase() {
        // Proceed to delete if "SQLiteConfigurationStore.db" exist
        String PROPERTIES_DB
                = "net.java.sip.communicator.impl.configuration.SQLiteConfigurationStore.db";
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
                FileAccess.delete(historyDir.getAbsolutePath());

            if (xmlFP.exists())
                FileAccess.delete(xmlFP.getAbsolutePath());

            if (omemoDir.exists())
                FileAccess.delete(omemoDir.getAbsolutePath());
        }
        catch (Exception ignore) {
        }

        // Clean up avatar store to remove files named with old userID
        VCardAvatarManager.clearPersistentStorage();
    }
}
