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

import org.apache.commons.io.IOUtils;

import java.io.*;

import androidx.annotation.RequiresApi;
import timber.log.Timber;

public class FilePathHelper
{
    /* Get uri related content real local file path. */
    public static String getPath(Context ctx, Uri uri)
    {
        String filePath;
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
            filePath = getFilePathFromURI(ctx, uri);
        }
        return filePath;
    }

    private static String getFilePathFromURI(Context context, Uri contentUri)
    {
        //copy file and send new file path
        String fileName = getFileName(contentUri);
        if (!TextUtils.isEmpty(fileName)) {
            File destFile = new File(FileBackend.getaTalkStore(FileBackend.TMP), fileName);
            Timber.d("FilePath copyFile: %s", destFile);
            copy(context, contentUri, destFile);
            return destFile.getAbsolutePath();
        }
        return null;
    }

    /*
     * Gboard content provider stream:
     * content://com.google.android.inputmethod.latin.inputcontent/inputContent?
     * fileName=/data/data/com.google.android.inputmethod.latin/files/sticker5151310470
     * &packageName=org.atalk.android
     * &mimeType=image/png
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

    public static void copy(Context context, Uri srcUri, File dstFile)
    {
        try {
            InputStream inputStream = context.getContentResolver().openInputStream(srcUri);
            if (inputStream == null)
                return;
            OutputStream outputStream = new FileOutputStream(dstFile);
            IOUtils.copy(inputStream, outputStream); // org.apache.commons.io
            inputStream.close();
            outputStream.close();
        } catch (Exception e) { // IOException
            e.printStackTrace();
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.KITKAT)
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
                    Uri downloadUriAppendId = ContentUris.withAppendedId(downloadUri, Long.valueOf(documentId));
                    filePath = getRealPath(ctx.getContentResolver(), downloadUriAppendId, null);

                }
                else if (isExternalStoreDoc(uriAuthority)) {
                    String[] idArr = documentId.split(":");
                    if (idArr.length == 2) {
                        String type = idArr[0];
                        String realDocId = idArr[1];

                        if ("primary".equalsIgnoreCase(type)) {
                            filePath = Environment.getExternalStorageDirectory() + "/" + realDocId;
                        }
                    }
                }
            }
        }
        return filePath;
    }

    /* Check whether this uri represent a document or not. */
    @RequiresApi(api = Build.VERSION_CODES.KITKAT)
    private static boolean isDocumentUri(Context ctx, Uri uri)
    {
        boolean ret = false;
        if (ctx != null && uri != null) {
            ret = DocumentsContract.isDocumentUri(ctx, uri);
        }
        return ret;
    }

    /* Check whether this uri is a content uri or not.
     *  content uri like content://media/external/images/media/1302716
     *  */
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

    /* Check whether this uri is a file uri or not.
     *  file uri like file:///storage/41B7-12F1/DCIM/Camera/IMG_20180211_095139.jpg
     * */
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