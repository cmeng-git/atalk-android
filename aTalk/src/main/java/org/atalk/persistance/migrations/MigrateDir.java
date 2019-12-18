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

import android.Manifest;
import android.content.ContentValues;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Environment;
import android.text.TextUtils;

import org.atalk.android.R;
import org.atalk.android.aTalkApp;
import org.atalk.android.gui.chat.ChatMessage;
import org.atalk.android.gui.dialogs.DialogActivity;
import org.atalk.persistance.DatabaseBackend;
import org.atalk.persistance.FileBackend;

import java.io.File;
import java.io.IOException;

import androidx.core.content.ContextCompat;
import timber.log.Timber;

/**
 * Migrate the existing aTalk project directory to aTalk root directory.
 *
 * @author Eng Chong Meng
 */
public class MigrateDir
{
    /**
     * Migrate aTalk old storage structure to new structure
     * 1. Update database filePath
     * 2. Move old dir to new dir
     * 3. Purge old dir
     */
    public static void aTalkDirMigrate()
    {
        File srcPath = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                FileBackend.FP_aTALK);
        File targetPath = FileBackend.getaTalkStore(null, false);

        // Proceed if there is an old aTalk directory
        if (!srcPath.exists())
            return;

        if (ContextCompat.checkSelfPermission(aTalkApp.getInstance(),
                Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            Context ctx = aTalkApp.getGlobalContext();
            DialogActivity.showConfirmDialog(ctx, ctx.getString(R.string.storage_permission_denied_dialog_title),
                    ctx.getString(R.string.storage_permission_denied_feedback),
                    ctx.getString(R.string.service_gui_OK), new ExitListener());
            return;
        }

        try {
            // Now move all the aTalk old directory to new directory
            FileBackend.copyRecursive(srcPath, targetPath, null);
            FileBackend.deleteRecursive(srcPath);

            // aTalk DCIM files
            srcPath = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM),
                    FileBackend.FP_aTALK);
            targetPath = FileBackend.getaTalkStore(FileBackend.MEDIA_CAMERA, false);
            FileBackend.copyRecursive(srcPath, targetPath, null);
            FileBackend.deleteRecursive(srcPath);

            // Update the database to point to the new locations on successful dir move
            updateDBFilePath();
        } catch (IOException e) {
            Timber.e("Failed to migrate aTalk to new directory structure: %s", e.getMessage());
        }
    }

    /**
     * Update the database old filePath to the new filePath
     */
    private static void updateDBFilePath()
    {
        String atalkOldDir = File.separator + Environment.DIRECTORY_DOWNLOADS;
        String atalkOldDCIM = Environment.DIRECTORY_DCIM + File.separator + FileBackend.FP_aTALK;
        String atalkNewCIM = FileBackend.FP_aTALK + File.separator + FileBackend.MEDIA_CAMERA;

        SQLiteDatabase mDB = DatabaseBackend.getWritableDB();
        ContentValues values = new ContentValues();
        String[] columns = {ChatMessage.UUID, ChatMessage.FILE_PATH};

        Cursor cursor = mDB.query(ChatMessage.TABLE_NAME, columns, ChatMessage.
                FILE_PATH + " IS NOT NULL", null, null, null, null);
        while (cursor.moveToNext()) {
            String filePath = cursor.getString(cursor.getColumnIndex(ChatMessage.FILE_PATH));
            if (!TextUtils.isEmpty(filePath) && (filePath.contains(atalkOldDir) || filePath.contains(atalkOldDCIM))) {
                filePath = filePath
                        .replace(atalkOldDir, "")
                        .replace(atalkOldDCIM, atalkNewCIM);

                values.clear();
                values.put(ChatMessage.FILE_PATH, filePath);
                String[] args = {cursor.getString(cursor.getColumnIndex(ChatMessage.UUID))};
                mDB.update(ChatMessage.TABLE_NAME, values, ChatMessage.UUID + "=?", args);
            }
        }
        cursor.close();
    }

    private static class ExitListener implements DialogActivity.DialogListener
    {
        @Override
        public boolean onConfirmClicked(DialogActivity dialog)
        {
            // cmeng: not working so just abort aTalk
            // Context ctx = aTalkApp.getGlobalContext();
            // Intent intent = new Intent(ctx, PermissionsActivity.class);
            // intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            // ctx.startActivity(intent);

            aTalkApp.shutdownApplication();
            return true;
        }

        @Override
        public void onDialogCancelled(DialogActivity dialog)
        {
            aTalkApp.shutdownApplication();
        }
    }
}
