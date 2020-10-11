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

import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.text.TextUtils;
import android.webkit.MimeTypeMap;

import org.atalk.android.aTalkApp;

import java.io.*;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import androidx.core.content.FileProvider;
import timber.log.Timber;

/**
 * File Backend utilities
 *
 * @author Eng Chong Meng
 */
public class FileBackend
{
    private static final String FILE_PROVIDER = ".files";

    public static final int MEDIA_TYPE_IMAGE = 1;
    public static final int MEDIA_TYPE_VIDEO = 2;
    /**
     * The default buffer size to use.
     */
    private static final int DEFAULT_BUFFER_SIZE = 1024 * 4;

    // android-Q accessible path to apk is: /storage/emulated/0/Android/data/org.atalk.android/files
    public static String FP_aTALK = "/aTalk";
    public static String EXPROT_DB = "EXPORT_DB";

    public static String MEDIA = "Media";
    public static String MEDIA_CAMERA = "Media/Camera";
    public static String MEDIA_DOCUMENT = "Media/Documents";
    public static String MEDIA_VOICE_RECEIVE = "Media/Voice_Receive";
    public static String MEDIA_VOICE_SEND = "Media/Voice_Send";
    public static String TMP = "tmp";

    public static boolean IsExternalStorageWritable()
    {
        // boolean mExternalStorageAvailable = false;
        boolean mExternalStorageWriteable;
        String state = Environment.getExternalStorageState();

        if (Environment.MEDIA_MOUNTED.equals(state)) {
            // We can read and write the media
            /* mExternalStorageAvailable = */
            mExternalStorageWriteable = true;
        }
        else if (Environment.MEDIA_MOUNTED_READ_ONLY.equals(state)) {
            // We can only read the media
            // mExternalStorageAvailable = true;
            mExternalStorageWriteable = false;
        }
        else {
            // Something else is wrong. It may be one of many other states, but all we need
            // to know is we can neither read nor write
            /* mExternalStorageAvailable = */
            mExternalStorageWriteable = false;
        }
        return mExternalStorageWriteable;
    }

    /**
     * Copies a file or directory to a new location. If copying a directory, the entire contents
     * of the directory are copied recursively.
     *
     * @param srcPath the full path of the file or directory to be copied
     * @param targetPath the full path of the target directory to which the file or directory should be copied
     * @param subFolder the new name of the file or directory
     * @throws IllegalArgumentException if an invalid source or destination path is provided
     * @throws FileNotFoundException if the source path cannot be found on the file system
     * @throws SecurityException if unable to create the new file or directory specified by destination path
     * @throws IOException if an attempt is made to copy the contents of a directory into itself, or if the
     * source and destination paths are identical, or if a general error occurs
     */
    public static void copyRecursive(File srcPath, File targetPath, String subFolder)
            throws IllegalArgumentException, SecurityException, IOException
    {
        // ensure source exists
        if ((srcPath == null) || !srcPath.exists()) {
            throw new FileNotFoundException("Source Path not found: " + srcPath);
        }

        // ensure target is a directory if exists
        if ((targetPath == null) || (targetPath.exists() && !targetPath.isDirectory())) {
            throw new FileNotFoundException("Target is null or not a directory: " + targetPath);
        }
        // Form full destination path
        File dstPath = targetPath;
        if (subFolder != null)
            dstPath = new File(targetPath, subFolder);

        // source is a directory
        if (srcPath.isDirectory()) {
            // can't copy directory into itself
            // file:///SDCard/tmp/ --> file:///SDCard/tmp/tmp/ ==> NO!
            // file:///SDCard/tmp/ --> file:///SDCard/tmp/ ==> NO!
            // file:///SDCard/tmp/ --> file:///SDCard/tmp2/ ==> OK

            if (dstPath.equals(srcPath)) {
                throw new IOException("Cannot copy directory into itself.");
            }

            // create the destination directory if non-exist
            if (!dstPath.exists() && !dstPath.mkdir())
                throw new IOException("Cannot create destination directory.");

            // recursively copy directory contents
            File[] files = srcPath.listFiles();
            for (File file : files) {
                String fileName = file.getName();
                copyRecursive(new File(srcPath, fileName), dstPath, fileName);
            }
        }
        // source srcPath is a file
        else {
            // can't copy file onto itself
            if (dstPath.equals(srcPath)) {
                throw new IOException("Cannot copy file onto itself.");
            }

            // replace existing file, but not directory
            if (dstPath.exists()) {
                if (dstPath.isDirectory()) {
                    throw new IOException("Cannot overwrite existing directory.");
                }
                else if (!dstPath.delete())
                    throw new IOException("Cannot delete old file. " + dstPath);
            }
            if (!dstPath.exists() && !dstPath.createNewFile())
                throw new IOException("Cannot create file to copy. " + dstPath);
            try {
                InputStream inputStream = new FileInputStream(srcPath);
                OutputStream outputStream = new FileOutputStream(dstPath);
                copy(inputStream, outputStream); // org.apache.commons.io
                inputStream.close();
                outputStream.close();
            } catch (Exception e) { // IOException
                e.printStackTrace();
            }
        }
    }

    /**
     * Deletes the specified file or directory from file system. If the specified path is a
     * directory, the deletion is recursive.
     *
     * @param filePath full path of file or directory to be deleted
     * @throws IOException throws exception if any
     */
    public static void deleteRecursive(File filePath)
            throws IOException
    {
        if ((filePath != null) && filePath.exists()) {
            // If the file is a directory, we will recursively call deleteRecursive on it.
            if (filePath.isDirectory()) {
                File[] files = filePath.listFiles();
                for (File file : files) {
                    deleteRecursive(file);
                }
            }
            // Finally, delete the root directory, after all the files in the directory have been deleted.
            if (!filePath.delete()) {
                throw new IOException("Could not deleteRecursive: " + filePath);
            }
        }
    }

    /**
     * Copy bytes from a large (over 2GB) <code>InputStream</code> to an <code>OutputStream</code>.
     *
     * This method buffers the input internally, so there is no need to use a <code>BufferedInputStream</code>.
     *
     * @param input the <code>InputStream</code> to read from
     * @param output the <code>OutputStream</code> to write to
     * @return the number of bytes copied
     * @throws NullPointerException if the input or output is null
     * @throws IOException if an I/O error occurs
     * @since Commons IO 1.3
     */
    public static long copy(InputStream input, OutputStream output)
            throws IOException
    {
        byte[] buffer = new byte[DEFAULT_BUFFER_SIZE];
        long count = 0;
        int n;
        while (-1 != (n = input.read(buffer))) {
            output.write(buffer, 0, n);
            count += n;
        }
        return count;
    }

    /**
     * Default aTalk downloadable directory i.e. Download/aTalk
     *
     * @param subFolder subFolder to be created under aTalk downloadable directory, null if root
     * @return aTalk default directory
     */
    public static File getaTalkStore(String subFolder, boolean createNew)
    {
        String filePath = FP_aTALK;
        if (!TextUtils.isEmpty(subFolder))
            filePath += File.separator + subFolder;

        // https://developer.android.com/reference/android/os/Environment#getExternalStorageDirectory()
        // File atalkDLDir = aTalkApp.getGlobalContext().getExternalFilesDir(filePath);
        // File atalkDLDir = new File(Environment.getExternalStorageDirectory(), filePath);
        File atalkDLDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS) + filePath);

        if (createNew && !atalkDLDir.exists() && !atalkDLDir.mkdirs()) {
            Timber.e("Could not create aTalk folder: %s", atalkDLDir);
        }
        return atalkDLDir;
    }

    /**
     * Create a new File for saving image or video captured with camera
     */
    public static File getOutputMediaFile(int type)
    {
        File aTalkMediaDir = getaTalkStore(MEDIA_CAMERA, true);

        // Create a media file name
        File mediaFile = null;
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        if (type == MEDIA_TYPE_IMAGE) {
            mediaFile = new File(aTalkMediaDir, "IMG_" + timeStamp + ".jpg");
        }
        else if (type == MEDIA_TYPE_VIDEO) {
            mediaFile = new File(aTalkMediaDir, "VID_" + timeStamp + ".mp4");
        }
        return mediaFile;
    }

    /**
     * Get the correct Uri path according to android API
     *
     * @param context context
     * @param file the specific file path
     * @return the actual Uri
     */
    public static Uri getUriForFile(Context context, File file)
    {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                String packageId = context.getPackageName();
                return FileProvider.getUriForFile(context, packageId + FILE_PROVIDER, file);
            } catch (IllegalArgumentException e) {
                throw new SecurityException(e);
            }
        }
        else {
            return Uri.fromFile(file);
        }
    }

    /**
     * To guess if a given link string is a file link address
     *
     * @param link a string to be checked for file link
     * @return true if the string is likely to be a Http File Download link
     */
    public static boolean isHttpFileDnLink(String link)
    {
        if (link != null) {
            if (link.matches("(?s)^aesgcm:.*")) {
                return true;
            }
            else if (link.matches("(?s)^http[s]:.*") && !link.contains("\\s")) {
                // return false if there is no ext or 2 < ext.length() > 5
                String ext = link.replaceAll("(?s)^.+/[\\w-]+\\.([\\w-]{2,5})$", "$1");
                if (ext.length() > 5) {
                    return false;
                } else {
                    // web common extensions: asp, cgi, [s]htm[l], js, php, pl
                    // android asp, cgi shtm, shtml, js, php, pl => (mimeType == null)
                    return !ext.matches("s*[achjp][sgthl][pim]*[l]*");
                }
            }
        }
        return false;
    }

    /**
     * To guess the mime type of the given uri using the mimeMap or from path name
     *
     * @param ctx the reference Context
     * @param uri content:// or file:// or whatever suitable Uri you want.
     * @param mime a reference mime type (from attachment)
     * @return mime type of the given uri
     */
    public static String getMimeType(final Context ctx, final Uri uri, final String mime)
    {
        Timber.d("guessMimeTypeFromUriAndMime %s and mimeType = %s", uri, mime);
        if (mime == null || mime.equals("application/octet-stream")) {
            final String guess = getMimeType(ctx, uri);
            if (guess != null) {
                return guess;
            }
            else {
                return mime;
            }
        }
        return getMimeType(ctx, uri);
    }

    /**
     * To guess the mime type of the given uri using the mimeMap or from path name
     *
     * @param ctx the reference Context
     * @param uri content:// or file:// or whatever suitable Uri you want.
     * @return mime type of the given uri
     */
    public static String getMimeType(Context ctx, Uri uri)
    {
        String mimeType = null;
        if (ContentResolver.SCHEME_CONTENT.equals(uri.getScheme())) {
            ContentResolver cr = ctx.getContentResolver();
            mimeType = cr.getType(uri);
        }
        else {
            String fileExtension = MimeTypeMap.getFileExtensionFromUrl(uri.toString());
            if (fileExtension != null)
                mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(fileExtension.toLowerCase());
        }

        // Make a guess base on filePath
        if ((mimeType == null) || mimeType.equals(("application/octet-stream"))) {
            String fileName = uri.getPath();
            if (fileName != null) {
                if (fileName.contains("image"))
                    mimeType = "image/*";
                else if (fileName.contains("video"))
                    mimeType = "video/*";
            }
        }
        return mimeType;
    }

    /**
     * Check if the file has media content
     *
     * @param file File to be check
     * @return true if the given file has media content
     */
    public static boolean isMediaFile(File file)
    {
        Context ctx = aTalkApp.getGlobalContext();
        Uri uri = getUriForFile(ctx, file);
        String mimeType = getMimeType(ctx, uri);

        // Make a last ditch to guess ContentType From FileInputStream
        if ((mimeType == null) || mimeType.equals(("application/octet-stream"))) {
            try {
                InputStream is = new FileInputStream(file);
                String tmp = guessContentTypeFromStream(is);
                if (tmp != null)
                    mimeType = tmp;
            } catch (IOException ignore) {
            }
        }

        // mimeType is null if file contains no ext on old android or else "application/octet-stream"
        if (TextUtils.isEmpty(mimeType)) {
            Timber.e("File mimeType is null: %s", file.getPath());
            return false;
        }

        // Android returns 3gp and vidoe/3gp
        return !mimeType.contains("3gp") && (mimeType.contains("image") || mimeType.contains("video"));
    }

    /**
     * cmeng: modified from URLConnection class
     *
     * Try to determine the type of input stream based on the characters at the beginning of the input stream.
     * This method  be used by subclasses that override the {@code getContentType} method.
     *
     * Ideally, this routine would not be needed, but many {@code http} servers return the incorrect content type;
     * in addition, there are many nonstandard extensions. Direct inspection of the bytes to determine the content
     * type is often more accurate than believing the content type claimed by the {@code http} server.
     *
     * @param is an input stream that supports marks.
     * @return a guess at the content type, or {@code null} if none can be determined.
     * @throws IOException if an I/O error occurs while reading the input stream.
     * @see java.io.InputStream#mark(int)
     * @see java.io.InputStream#markSupported()
     * @see java.net.URLConnection#getContentType()
     */
    private static String guessContentTypeFromStream(InputStream is)
            throws IOException
    {
        int c1 = is.read();
        int c2 = is.read();
        int c3 = is.read();
        int c4 = is.read();
        int c5 = is.read();
        int c6 = is.read();
        int c7 = is.read();
        int c8 = is.read();
        int c9 = is.read();
        int c10 = is.read();
        int c11 = is.read();
        int c12 = is.read();
        int c13 = is.read();
        int c14 = is.read();
        int c15 = is.read();
        int c16 = is.read();

        if (c1 == 'G' && c2 == 'I' && c3 == 'F' && c4 == '8') {
            return "image/gif";
        }

        if (c1 == '#' && c2 == 'd' && c3 == 'e' && c4 == 'f') {
            return "image/x-bitmap";
        }

        if (c1 == '!' && c2 == ' ' && c3 == 'X' && c4 == 'P' &&
                c5 == 'M' && c6 == '2') {
            return "image/x-pixmap";
        }

        if (c1 == 137 && c2 == 80 && c3 == 78 &&
                c4 == 71 && c5 == 13 && c6 == 10 &&
                c7 == 26 && c8 == 10) {
            return "image/png";
        }

        if (c1 == 0xFF && c2 == 0xD8 && c3 == 0xFF) {
            if (c4 == 0xE0 || c4 == 0xEE) {
                return "image/jpeg";
            }

            /*
             * File format used by digital cameras to store images.
             * Exif Format can be read by any application supporting JPEG.
             * Exif Spec can be found at:
             * http://www.pima.net/standards/it10/PIMA15740/Exif_2-1.PDF
             */
            if ((c4 == 0xE1) &&
                    (c7 == 'E' && c8 == 'x' && c9 == 'i' && c10 == 'f' && c11 == 0)) {
                return "image/jpeg";
            }
        }

        if (c1 == 0x2E && c2 == 0x73 && c3 == 0x6E && c4 == 0x64) {
            return "audio/basic";  // .au format, big endian
        }

        if (c1 == 0x64 && c2 == 0x6E && c3 == 0x73 && c4 == 0x2E) {
            return "audio/basic";  // .au format, little endian
        }

        if (c1 == 'R' && c2 == 'I' && c3 == 'F' && c4 == 'F') {
            /* I don't know if this is official but evidence
             * suggests that .wav files start with "RIFF" - brown
             */
            return "audio/x-wav";
        }

        if (c1 == 0xCA && c2 == 0xFE && c3 == 0xBA && c4 == 0xBE) {
            return "application/java-vm";
        }

        if (c1 == 0xAC && c2 == 0xED) {
            // next two bytes are version number, currently 0x00 0x05
            return "application/x-java-serialized-object";
        }

        if (c1 == '<') {
            if (c2 == '!'
                    || ((c2 == 'h' && (c3 == 't' && c4 == 'm' && c5 == 'l' ||
                    c3 == 'e' && c4 == 'a' && c5 == 'd') ||
                    (c2 == 'b' && c3 == 'o' && c4 == 'd' && c5 == 'y'))) ||
                    ((c2 == 'H' && (c3 == 'T' && c4 == 'M' && c5 == 'L' ||
                            c3 == 'E' && c4 == 'A' && c5 == 'D') ||
                            (c2 == 'B' && c3 == 'O' && c4 == 'D' && c5 == 'Y')))) {
                return "text/html";
            }

            if (c2 == '?' && c3 == 'x' && c4 == 'm' && c5 == 'l' && c6 == ' ') {
                return "application/xml";
            }
        }

        // big and little (identical) endian UTF-8 encodings, with BOM
        if (c1 == 0xef && c2 == 0xbb && c3 == 0xbf) {
            if (c4 == '<' && c5 == '?' && c6 == 'x') {
                return "application/xml";
            }
        }

        // big and little endian UTF-16 encodings, with byte order mark
        if (c1 == 0xfe && c2 == 0xff) {
            if (c3 == 0 && c4 == '<' && c5 == 0 && c6 == '?' &&
                    c7 == 0 && c8 == 'x') {
                return "application/xml";
            }
        }

        if (c1 == 0xff && c2 == 0xfe) {
            if (c3 == '<' && c4 == 0 && c5 == '?' && c6 == 0 &&
                    c7 == 'x' && c8 == 0) {
                return "application/xml";
            }
        }

        // big and little endian UTF-32 encodings, with BOM
        if (c1 == 0x00 && c2 == 0x00 && c3 == 0xfe && c4 == 0xff) {
            if (c5 == 0 && c6 == 0 && c7 == 0 && c8 == '<' &&
                    c9 == 0 && c10 == 0 && c11 == 0 && c12 == '?' &&
                    c13 == 0 && c14 == 0 && c15 == 0 && c16 == 'x') {
                return "application/xml";
            }
        }

        if (c1 == 0xff && c2 == 0xfe && c3 == 0x00 && c4 == 0x00) {
            if (c5 == '<' && c6 == 0 && c7 == 0 && c8 == 0 &&
                    c9 == '?' && c10 == 0 && c11 == 0 && c12 == 0 &&
                    c13 == 'x' && c14 == 0 && c15 == 0 && c16 == 0) {
                return "application/xml";
            }
        }
        return null;
    }
}
