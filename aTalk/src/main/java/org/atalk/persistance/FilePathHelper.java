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

package org.atalk.persistance;

import android.annotation.SuppressLint;
import android.content.*;
import android.database.Cursor;
import android.net.Uri;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.text.TextUtils;

import org.atalk.android.gui.share.Attachment;

import java.io.*;

import timber.log.Timber;

/**
 * FilePath Helper utilities to handle android content:// scheme uri
 *
 * @author Eng Chong Meng
 */

public class FilePathHelper
{
    /**
     * Get the real local file path of the given attachement; create and copy to a new local file on failure
     *
     * @param ctx the reference Context
     * @param attachment a wrapper of file with other properties {@see Attachment}
     * @return real local file path of uri or newly created file
     */
    public static String getFilePath(Context ctx, Attachment attachment)
    {
        Uri uri = attachment.getUri();
        String filePath = getFilePath(ctx, uri);
        if (filePath == null)
            filePath = getFilePathWithCreate(ctx, uri);
        return filePath;
    }

    /**
     * Get the real local file path of the given uri if accessible;
     * Else create and copy to a new local file on failure
     *
     * @param ctx the reference Context
     * @param uri content:// or file:// or whatever suitable Uri you want.
     * @return real local file path of uri or newly created file
     */
    public static String getFilePath(Context ctx, Uri uri)
    {
        String filePath = null;
        try {
            filePath = getUriRealPath(ctx, uri);
        } catch (Exception e) {
            Timber.d("FilePath Catch: %s", uri.toString());
        }
        if (TextUtils.isEmpty(filePath))
            filePath = getFilePathWithCreate(ctx, uri);
        return filePath;
    }

    /**
     * To create a new file based on the given uri (usually on ContentResolver failure)
     *
     * @param ctx the reference Context
     * @param uri content:// or file:// or whatever suitable Uri you want.
     * @return file name with the guessed ext if none is given.
     */
    private static String getFilePathWithCreate(Context ctx, Uri uri)
    {
        String fileName = null;

        if (!TextUtils.isEmpty(uri.getPath())) {
            Cursor cursor = ctx.getContentResolver().query(uri, null, null, null, null);
            if (cursor == null)
                fileName = uri.getPath();
            else {
                cursor.moveToFirst();
                int idx = cursor.getColumnIndex(MediaStore.Files.FileColumns.DISPLAY_NAME);
                fileName = cursor.getString(idx);
                cursor.close();
            }
        }

        if (!TextUtils.isEmpty(fileName)) {
            File destFile = new File(FileBackend.getaTalkStore(FileBackend.TMP, true), fileName);
            if (!destFile.exists()) {
                Timber.d("FilePath copyFile: %s", destFile);
                copy(ctx, uri, destFile);
            }
            return destFile.getAbsolutePath();
        }
        return null;
    }

    /**
     * Copy the content from the given uri to the defined destFile
     *
     * @param context the reference Context
     * @param srcUri content:// or file:// or whatever suitable Uri you want.
     * @param dstFile the destination file to be copied to
     */
    public static void copy(Context context, Uri srcUri, File dstFile)
    {
        try {
            InputStream inputStream = context.getContentResolver().openInputStream(srcUri);
            if (inputStream == null)
                return;
            OutputStream outputStream = new FileOutputStream(dstFile);
            FileBackend.copy(inputStream, outputStream);
            inputStream.close();
            outputStream.close();
        } catch (Exception e) { // IOException
            e.printStackTrace();
        }
    }

    /**
     * Get the uri real path for OS with KitKat and above.
     *
     * @param ctx the reference Context
     * @param uri content:// or file:// or whatever suitable Uri you want.
     */
    private static String getUriRealPath(Context ctx, Uri uri)
            throws Exception
    {
        String filePath = "";
        if (ctx != null && uri != null) {
            // Get uri authority.
            String uriAuthority = uri.getAuthority();

            if (isContentUri(uri)) {
                if (isGooglePhotoDoc(uriAuthority)) {
                    filePath = uri.getLastPathSegment();
                }
                else {
                    filePath = getRealPath(ctx.getContentResolver(), uri, null);
                }
            }
            else if (isFileUri(uri)) {
                filePath = uri.getPath();
            }
            else if (isDocumentUri(ctx, uri)) {
                // Get uri related document id.
                String documentId = DocumentsContract.getDocumentId(uri);

                if (isMediaDoc(uriAuthority)) {
                    String[] idArr = documentId.split(":");
                    if (idArr.length == 2) {
                        // First item is document type.
                        String docType = idArr[0];

                        // Second item is document real id.
                        String realDocId = idArr[1];

                        // Get content uri by document type.
                        Uri mediaContentUri = null;
                        if ("image".equals(docType)) {
                            mediaContentUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
                        }
                        else if ("video".equals(docType)) {
                            mediaContentUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
                        }
                        else if ("audio".equals(docType)) {
                            mediaContentUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
                        }

                        if (mediaContentUri != null) {
                            // Get where clause with real document id.
                            String whereClause = MediaStore.Images.Media._ID + " = " + realDocId;
                            filePath = getRealPath(ctx.getContentResolver(), mediaContentUri, whereClause);
                        }
                    }
                }
                else if (isDownloadDoc(uriAuthority)) {
                    // Build download uri.
                    Uri downloadUri = Uri.parse("content://downloads/public_downloads");

                    // Append download document id at uri end.
                    Uri downloadUriAppendId = ContentUris.withAppendedId(downloadUri, Long.parseLong(documentId));
                    filePath = getRealPath(ctx.getContentResolver(), downloadUriAppendId, null);

                }
                else if (isExternalStoreDoc(uriAuthority)) {
                    String[] idArr = documentId.split(":");
                    if (idArr.length == 2) {
                        String type = idArr[0];
                        String realDocId = idArr[1];

                        if ("primary".equalsIgnoreCase(type)) {
                            filePath = ctx.getExternalFilesDir(realDocId).getAbsolutePath();
                        }
                    }
                }
            }
        }
        return filePath;
    }

    /**
     * Check whether this uri represent a document or not.
     *
     * @param uri content:// or file:// or whatever suitable Uri you want.
     */
    private static boolean isDocumentUri(Context ctx, Uri uri)
    {
        boolean ret = false;
        if (ctx != null && uri != null) {
            ret = DocumentsContract.isDocumentUri(ctx, uri);
        }
        return ret;
    }

    /**
     * Check whether this uri is a content uri or not.
     *
     * @param uri content uri e.g. content://media/external/images/media/1302716
     */
    private static boolean isContentUri(Uri uri)
    {
        boolean ret = false;
        if (uri != null) {
            String uriSchema = uri.getScheme();
            if ("content".equalsIgnoreCase(uriSchema)) {
                ret = true;
            }
        }
        return ret;
    }

    /**
     * Check whether this uri is a file uri or not.
     *
     * @param uri file uri e.g. file:///storage/41B7-12F1/DCIM/Camera/IMG_20180211_095139.jpg
     */
    private static boolean isFileUri(Uri uri)
    {
        boolean ret = false;
        if (uri != null) {
            String uriSchema = uri.getScheme();
            if ("file".equalsIgnoreCase(uriSchema)) {
                ret = true;
            }
        }
        return ret;
    }

    /* Check whether this document is provided by ExternalStorageProvider. */
    private static boolean isExternalStoreDoc(String uriAuthority)
    {
        return "com.android.externalstorage.documents".equals(uriAuthority);
    }

    /* Check whether this document is provided by DownloadsProvider. */
    private static boolean isDownloadDoc(String uriAuthority)
    {
        return "com.android.providers.downloads.documents".equals(uriAuthority);
    }

    /* Check whether this document is provided by MediaProvider. */
    private static boolean isMediaDoc(String uriAuthority)
    {
        return ("com.android.providers.media.documents".equals(uriAuthority));
    }

    /* Check whether this document is provided by google photo. */
    private static boolean isGooglePhotoDoc(String uriAuthority)
    {
        return "com.google.android.apps.photos.content".equals(uriAuthority);
    }

    /* Return uri represented document file real local path.*/
    @SuppressLint("Recycle")
    private static String getRealPath(ContentResolver contentResolver, Uri uri, String whereClause)
            throws Exception
    {
        String filePath = "";
        // Query the uri with condition.
        Cursor cursor = contentResolver.query(uri, null, whereClause, null, null);
        if (cursor != null) {
            boolean moveToFirst = cursor.moveToFirst();
            if (moveToFirst) {
                // Get columns name by uri type.
                String columnName = null;

                if (uri == MediaStore.Images.Media.EXTERNAL_CONTENT_URI) {
                    columnName = MediaStore.Images.Media.DATA;
                }
                else if (uri == MediaStore.Audio.Media.EXTERNAL_CONTENT_URI) {
                    columnName = MediaStore.Audio.Media.DATA;
                }
                else if (uri == MediaStore.Video.Media.EXTERNAL_CONTENT_URI) {
                    columnName = MediaStore.Video.Media.DATA;
                }

                // Get column value which is the uri related file local-path.
                if (columnName != null) {
                    int columnIndex = cursor.getColumnIndex(columnName);
                    filePath = cursor.getString(columnIndex);
                }
            }
            cursor.close();
        }
        // throw exception to try using stream copy
        else {
            throw new Exception("Cursor is null!");
        }
        return filePath;
    }
}