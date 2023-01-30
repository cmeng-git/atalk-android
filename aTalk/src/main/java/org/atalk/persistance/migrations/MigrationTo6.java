package org.atalk.persistance.migrations;

import static org.atalk.persistance.DatabaseBackend.CREATE_CHAT_SESSIONS_STATEMENT;

import android.database.sqlite.SQLiteDatabase;

import org.atalk.android.gui.chat.ChatSession;

import java.util.Date;

public class MigrationTo6 {
    public static void updateChatSessionTable(SQLiteDatabase db) {
//        String OLD_TABLE = "chatSessions_old";
//        db.execSQL("DROP TABLE IF EXISTS " + OLD_TABLE);
//        db.execSQL("ALTER TABLE " + ChatSession.TABLE_NAME + " RENAME TO " + OLD_TABLE);
//
//        db.execSQL("DROP TABLE IF EXISTS " + ChatSession.TABLE_NAME);
//        db.execSQL(CREATE_CHAT_SESSIONS_STATEMENT);
//        // Insert will cause attributes and mamDate data swap
//        db.execSQL("INSERT INTO " + ChatSession.TABLE_NAME + " SELECT * FROM " + OLD_TABLE);

        db.execSQL("ALTER TABLE " + ChatSession.TABLE_NAME
                + " ADD " + ChatSession.MAM_DATE + " NUMBER DEFAULT " + new Date().getTime());
    }
}
