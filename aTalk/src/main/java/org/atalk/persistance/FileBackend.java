package org.atalk.persistance;

import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.text.TextUtils;
import android.webkit.MimeTypeMap;

import org.apache.commons.io.IOUtils;

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

    public static String FP_aTALK = "aTalk";
    public static String EXPROT_DB = "EXPROT_DB";

    public static String MEDIA = "Media";
    public static String MEDIA_DOCUMENT = "Media/Text";
    public static String MEDIA_VOICE_RECEIVE = "Media/Voice_Receive";
    public static String MEDIA_VOICE_SEND = "Media/Voice_Send";
    public static String TMP = "tmp";

    public static boolean IsExternalStorageWriteable()
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

        // ensure target is a directory and exists
        if ((targetPath == null) || !targetPath.exists() || !targetPath.isDirectory()) {
            throw new FileNotFoundException("Target directory not found: " + targetPath);
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

            if (dstPath == srcPath) {
                throw new IOException("Cannot copy directory into itself.");
            }

            // create the destination directory
            if (!dstPath.mkdir())
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
            if (!dstPath.createNewFile())
                throw new IOException("Cannot create file to copy. " + dstPath);
            try {
                InputStream inputStream = new FileInputStream(srcPath);
                OutputStream outputStream = new FileOutputStream(dstPath);
                IOUtils.copy(inputStream, outputStream); // org.apache.commons.io
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
            // Finally, delete the root directory now that all of the files in the directory have been properly deleted.
            if (!filePath.delete()) {
                throw new IOException("Could not deleteRecursive: " + filePath);
            }
        }
    }

    /**
     * Default aTalk downloadable directory i.e. Download/aTalk
     *
     * @param subFolder subFolder to be created under aTalk downloadable directory, null if root
     * @return aTalk default directory
     */
    public static File getaTalkStore(String subFolder)
    {
        String filePath = FP_aTALK;
        if (!TextUtils.isEmpty(subFolder))
            filePath += File.separator + subFolder;

        File atalkDLDir
                = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), filePath);
        if (!atalkDLDir.exists() && !atalkDLDir.mkdirs()) {
            Timber.e("Could not create atalk folder: %s", atalkDLDir);
        }
        return atalkDLDir;
    }

    /**
     * Create a File for saving an image or video
     */
    public static File getOutputMediaFile(int type)
    {
        File appMediaDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM);
        File aTalkMediaDir = new File(appMediaDir, FP_aTALK);
        if (!aTalkMediaDir.exists() && !aTalkMediaDir.mkdirs()) {
            Timber.d("MyCameraApp failed to create directory");
            return null;
        }

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

    // url = content:// or file:// or whatever suitable URL you want.
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
        return mimeType;
    }
}
