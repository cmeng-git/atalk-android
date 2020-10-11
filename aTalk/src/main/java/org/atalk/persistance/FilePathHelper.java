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
import android.os.Build;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.webkit.MimeTypeMap;

import org.atalk.android.aTalkApp;
import org.atalk.android.gui.share.Attachment;

import java.io.*;

import timber.log.Timber;

/**
 * FilePath Helper utilities
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
    public static String getPath(Context ctx, Attachment attachment)
    {
        Uri uri = attachment.getUri();
        String filePath = getFilePath(ctx, uri);
        if (filePath == null)
            filePath = getFilePathWithCreate(ctx, uri, attachment.getMime());
        return filePath;
    }

    /**
     * Get the real local file path of the given uri; create and copy to a new local file on failure
     *
     * @param ctx the reference Context
     * @param uri content:// or file:// or whatever suitable Uri you want.
     * @return real local file path of uri or newly created file
     */
    public static String getPath(Context ctx, Uri uri)
    {
        String filePath = getFilePath(ctx, uri);
        if (filePath == null)
            filePath = getFilePathWithCreate(ctx, uri, null);
        return filePath;
    }

    /**
     * Get the real local file path of a given uri
     *
     * @param ctx the reference Context
     * @param uri content:// or file:// or whatever suitable Uri you want.
     * @return real local file path or null on access exception
     */
    private static String getFilePath(Context ctx, Uri uri)
    {
        String filePath = null;
        try {
            // Android OS above sdk version 19.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                filePath = getUriRealPathAboveKitkat(ctx, uri);
            }
            // Android OS below sdk version 19
            else {
                filePath = getRealPath(ctx.getContentResolver(), uri, null);
            }
        } catch (Exception e) {
            Timber.d("FilePath Catch: %s", uri.toString());
        }
        return filePath;
    }

    /**
     * To create a new file based on the given uri (usually on ContentResolver failure)
     * Guess the file ext if one is not given, and rename the file accordingly.
     *
     * @param ctx the reference Context
     * @param uri content:// or file:// or whatever suitable Uri you want.
     * @param mime a reference mime type (usually from attachment)
     * @return file name with the guessed ext if none is given.
     */
    private static String getFilePathWithCreate(Context ctx, Uri contentUri, String mimeType)
    {
        String fileName = getFileName(contentUri);
        if (!TextUtils.isEmpty(fileName)) {
            String extension = MimeTypeMap.getFileExtensionFromUrl(fileName);
            // android sdk returns "" for filename contains unicode characters
            if (TextUtils.isEmpty(extension)) {
                int dotPos = fileName.lastIndexOf('.');
                if (0 < dotPos) {
                    extension = fileName.substring(dotPos + 1);
                }
            }
            if (TextUtils.isEmpty(extension)) {
                String guess = FileBackend.getMimeType(ctx, contentUri, mimeType);
                if (!TextUtils.isEmpty(guess)) {
                    String[] mime = guess.split("/");
                    if (!"*".equals(mime[1])) {
                        fileName += "." + mime[1];
                    }
                    else if (!"*".equals(mime[0])) {
                        fileName = mime[0] + ":" + fileName;
                    }
                }
            }
            File destFile = new File(FileBackend.getaTalkStore(FileBackend.TMP, true), fileName);
            if (!destFile.exists()) {
                Timber.d("FilePath copyFile: %s", destFile);
                copy(ctx, contentUri, destFile);
            }
            return destFile.getAbsolutePath();
        }
        return null;
    }

    /**
     * Get the fileName of the given uri
     *
     * Gboard content provider stream:
     * content://com.google.android.inputmethod.latin.inputcontent/inputContent?
     * fileName=/data/data/com.google.android.inputmethod.latin/files/sticker5151310470
     * &packageName=org.atalk.android
     * &mimeType=image/png
     *
     * @param uri content:// or file:// or whatever suitable Uri you want.
     */
    public static String getFileName(Uri uri)
    {
        if (uri == null)
            return null;

        String fileName = null;
        String filePath = uri.getQueryParameter("fileName");
        String mimeType = uri.getQueryParameter("mimeType");
        if (!TextUtils.isEmpty(filePath)) {
            fileName = filePath.substring(filePath.lastIndexOf('/') + 1);
            if (!TextUtils.isEmpty(mimeType)) {
                String ext = mimeType.substring(mimeType.lastIndexOf('/') + 1);
                fileName += "." + ext;
            }
        }
        if (TextUtils.isEmpty(fileName)) {
            String path = uri.getPath();
            fileName = path.substring(path.lastIndexOf('/') + 1);
        }
        return fileName;
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
    private static String getUriRealPathAboveKitkat(Context ctx, Uri uri)
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
                        Uri mediaContentUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
                        if ("image".equals(docType)) {
                            mediaContentUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
                        }
                        else if ("video".equals(docType)) {
                            mediaContentUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
                        }
                        else if ("audio".equals(docType)) {
                            mediaContentUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
                        }
                        // Get where clause with real document id.
                        String whereClause = MediaStore.Images.Media._ID + " = " + realDocId;
                        filePath = getRealPath(ctx.getContentResolver(), mediaContentUri, whereClause);
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
                String columnName = MediaStore.Images.Media.DATA;

                if (uri == MediaStore.Images.Media.EXTERNAL_CONTENT_URI) {
                    columnName = MediaStore.Images.Media.DATA;
                }
                else if (uri == MediaStore.Audio.Media.EXTERNAL_CONTENT_URI) {
                    columnName = MediaStore.Audio.Media.DATA;
                }
                else if (uri == MediaStore.Video.Media.EXTERNAL_CONTENT_URI) {
                    columnName = MediaStore.Video.Media.DATA;
                }
                // Get column index.
                int columnIndex = cursor.getColumnIndex(columnName);

                // Get column value which is the uri related file local path.
                filePath = cursor.getString(columnIndex);
            }
            cursor.close();
        }
        else {
            // throw exception to try using stream copy
            throw new Exception("Cursor is null!");
        }
        return filePath;
    }
}