package org.atalk.android.util;

import android.os.Environment;
import android.webkit.MimeTypeMap;

import org.atalk.util.StringUtils;

import java.io.*;

/**
 * File access utilities
 * @author Eng Chong Meng
*/
public class FileAccess
{
    public static boolean CreateFolder(String folderPath)
            throws IOException
    {
        // String extStorageDirectory = Environment.getExternalStorageDirectory().toString();
        if (StringUtils.isNullOrEmpty(folderPath))
            return false;

        File myNewFolder = new File(folderPath);
        return myNewFolder.mkdir();
    }

    public static boolean CreateFile(String FilePath)
    {
        if (StringUtils.isNullOrEmpty(FilePath))
            return false;

        try {
            File myNewFile = new File(FilePath);
            if (!myNewFile.exists()) {
                return myNewFile.createNewFile();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return false;
    }

    public static boolean IsFileExist(String FilePath)
    {
        if (StringUtils.isNullOrEmpty(FilePath))
            return false;

        try {
            File fc = new File(FilePath);
            return fc.exists();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    public static boolean SaveData2File(String FilePath, byte[] src)
    {
        if (StringUtils.isNullOrEmpty(FilePath))
            return false;

        boolean result = false;
        BufferedOutputStream out = null;
        try {
            String dirPath = FilePath.substring(0, FilePath.lastIndexOf('/'));
            File dir = new File(dirPath);
            if (!dir.exists())
                dir.mkdirs();

            File fc = new File(FilePath);
            if (!fc.exists())
                fc.createNewFile();

            out = new BufferedOutputStream(new FileOutputStream(fc, false));
            out.write(src);
            result = true;
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (out != null) {
                try {
                    out.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                out = null;
            }
        }
        return result;
    }

    public static int GetFileSize(String FilePath)
    {
        if (StringUtils.isNullOrEmpty(FilePath))
            return 0;

        int fileSize = 0;
        InputStream in = null;
        try {
            File fc = new File(FilePath);
            boolean bFileExists = fc.exists();
            if (!bFileExists) {
                return 0;
            }

            byte[] data = new byte[128];
            int nByteNum = 0;
            in = new BufferedInputStream(new FileInputStream(fc));
            do {
                java.util.Arrays.fill(data, (byte) 0);
                nByteNum = in.read(data, 0, 128);

                if (nByteNum > 0)
                    fileSize += nByteNum;

            } while (nByteNum > 0);
        } catch (IOException e) {
            // System.out.println(e.toString());
            e.printStackTrace();
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (IOException e) {
                    // System.out.println(e.toString());
                }
                in = null;
            }
        }
        return fileSize;
    }

    public static byte[] GetDataFromFile(String FilePath)
    {
        if (StringUtils.isNullOrEmpty(FilePath))
            return null;

        InputStream in = null;
        ByteArrayOutputStream out = null;
        byte[] buffer = null;
        try {
            File fc = new File(FilePath);
            boolean bFileExists = fc.exists();
            if (!bFileExists) {
                return null;
            }

            byte[] data = new byte[128];
            int nByteNum = 0;
            out = new ByteArrayOutputStream();
            in = new BufferedInputStream(new FileInputStream(fc));

            do {
                java.util.Arrays.fill(data, (byte) 0);
                nByteNum = in.read(data, 0, 128);

                if (nByteNum > 0)
                    out.write(data, 0, nByteNum);

            } while (nByteNum > 0);

            buffer = out.toByteArray();
        } catch (IOException e) {
            buffer = null;
            // System.out.println(e.toString());
            e.printStackTrace();
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (IOException e) {
                    // System.out.println(e.toString());
                }
                in = null;
            }
            if (out != null) {
                try {
                    out.close();
                } catch (IOException e) {
                    // System.out.println(e.toString());
                }
                out = null;
            }
        }
        return buffer;
    }

    public static void DeleteFile(String FilePath)
            throws IOException
    {
        if (StringUtils.isNullOrEmpty(FilePath))
            return;

        File fc = new File(FilePath);
        if (fc != null) {
            boolean bFileExists = fc.exists();
            if (!bFileExists) {
                return;
            }
            fc.delete();
        }
    }

    public static void WriteFile(String FilePath, byte[] src, int srcOffset, int destOffset,
            boolean createWhenNotExist, boolean eraseOriginContent)
            throws IOException
    {
        if (src == null)
            return;

        File fc = null;
        OutputStream out = null;

        try {
            fc = new File(FilePath);
            boolean bFileExists = fc.exists();
            if (!bFileExists) {
                if (createWhenNotExist) {
                    fc.createNewFile();
                }
                else {
                    fc = null;
                    return;
                }
            }
            else {
                if (eraseOriginContent) {
                    fc.delete();
                    fc.createNewFile();
                }
            }

            byte[] data = new byte[src.length - srcOffset];
            System.arraycopy(src, srcOffset, data, 0, data.length);
            byte[] filedata = FileAccess.GetDataFromFile(FilePath);
            int nFileSize = 0;
            if (filedata != null)
                nFileSize = filedata.length;
            int nOffSet = destOffset;
            out = new BufferedOutputStream(new FileOutputStream(fc, false));

            if (nFileSize < nOffSet) {
                byte[] b = new byte[nOffSet - nFileSize];
                out.write(filedata);
                out.write(b);
                out.write(data);

            }
            else {
                if (filedata != null)
                    out.write(filedata, 0, nOffSet);
                out.write(data);
                nOffSet += data.length;
                if (nFileSize - nOffSet > 0)
                    out.write(filedata, nOffSet, nFileSize - nOffSet);
            }

        } catch (IOException e) {
            throw e;
        } finally {
            if (out != null) {
                try {
                    out.close();
                } catch (IOException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
                out = null;
            }
        }
        return;
    }

    public static boolean IsExternalStorageWriteable()
    {
        // boolean mExternalStorageAvailable = false;
        boolean mExternalStorageWriteable = false;
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

    // url = file path or whatever suitable URL you want.
    public static String getMimeType(String url)
    {
        String mimeType = null;
        String extension = MimeTypeMap.getFileExtensionFromUrl(url);
        extension = null;
        int lastDot = url.lastIndexOf(".");
        if (lastDot != -1) {
            extension = url.substring(lastDot + 1);
        }
        if (extension != null) {
            MimeTypeMap mime = MimeTypeMap.getSingleton();
            mimeType = mime.getMimeTypeFromExtension(extension);
        }
        // default to text if null
        if (mimeType == null)
            mimeType = "text/plain";

        return mimeType;
    }

    public static void copy(File src, File dst)
            throws IOException
    {
        InputStream in = new FileInputStream(src);
        OutputStream out = new FileOutputStream(dst);

        // Transfer bytes from in to out
        byte[] buf = new byte[1024];
        int len;
        while ((len = in.read(buf)) > 0) {
            out.write(buf, 0, len);
        }
        in.close();
        out.close();
    }

    /**
     * Copies a file or directory to a new location. If copying a directory, the entire contents
     * of the directory are copied recursively.
     *
     * @param srcPath the full path of the file or directory to be copied
     * @param targetDirPath the full path of the target directory to which the file or directory should be copied
     * @param newName the new name of the file or directory
     * @throws IllegalArgumentException if an invalid source or destination path is provided
     * @throws FileNotFoundException if the source path cannot be found on the file system
     * @throws SecurityException if unable to create the new file or directory specified by destination path
     * @throws IOException if an attempt is made to copy the contents of a directory into itself, or if the
     * source and destination paths are identical, or if a general error occurs
     */
    public static void copy(String srcPath, String targetDirPath, String newName)
            throws IllegalArgumentException, SecurityException, IOException
    {
        File targetDir = null;
        File src = null;
        File dst = null;
        try {
            src = new File(srcPath);
            targetDir = new File(targetDirPath);
            // ensure source exists
            if (!src.exists()) {
                throw new FileNotFoundException("Path not found: " + srcPath);
            }

            if (!targetDir.exists()) {
                throw new FileNotFoundException("Path not found: " + targetDirPath);
            }

            // ensure target parent directory exists

            if (!targetDir.isDirectory()) {
                throw new FileNotFoundException("Target directory not found: " + targetDirPath);
            }

            // form full destination path
            if (!targetDirPath.endsWith("/")) {
                targetDirPath += "/";
            }
            String dstPath = targetDirPath + newName;

            // source is a directory
            if (src.isDirectory()) {
                // target should also be directory; append file separator
                if (!dstPath.endsWith("/")) {
                    dstPath += "/";
                }

                // can't copy directory into itself
                // file:///SDCard/tmp/ --> file:///SDCard/tmp/tmp/ ==> NO!
                // file:///SDCard/tmp/ --> file:///SDCard/tmp/ ==> NO!
                // file:///SDCard/tmp/ --> file:///SDCard/tmp2/ ==> OK
                String srcURL = src.getPath();
                if (dstPath.startsWith(srcURL)) {
                    throw new IOException("Cannot copy directory into itself.");
                }

                // create the destination directory
                CreateFolder(dstPath);

                // recursively copy directory contents
                File[] contents = src.listFiles();
                if (contents != null && contents.length > 0) {

                    for (File content : contents) {
                        String name = content.getName();
                        copy(srcURL + "/" + name, dstPath, name);
                    }
                }
            }
            // source is a file
            else {
                // can't copy file onto itself
                if (dstPath.equals(srcPath)) {
                    throw new IOException("Cannot copy file onto itself.");
                }

                dst = new File(dstPath);

                // replace existing file, but not directory
                if (dst.exists()) {
                    if (dst.isDirectory()) {
                        throw new IOException("Cannot overwrite existing directory.");
                    }
                    else {
                        dst.delete();
                    }
                }
                dst.createNewFile();

                // copy the contents - wish there was a better way
                InputStream is = null;
                OutputStream os = null;
                try {
                    is = new FileInputStream(src);
                    os = new FileOutputStream(dst);
                    byte[] buf = new byte[1024];
                    int len;
                    while ((len = is.read(buf)) > 0) {
                        os.write(buf, 0, len);
                    }
                } finally {
                    if (is != null)
                        is.close();
                    if (os != null)
                        os.close();
                }
            }
        } finally {
            if (src != null)
                src = null;
            if (dst != null)
                dst = null;
        }
    }

    /**
     * Deletes the specified file or directory from file system. If the specified path is a
     * directory, the deletion is recursive.
     *
     * @param path full path of file or directory to be deleted
     * @throws IOException
     */
    public static void delete(String path)
            throws IOException
    {
        File fconn = null;
        try {
            fconn = new File(path);
            if (fconn.exists()) {
                // file
                if (!fconn.isDirectory()) {
                    fconn.delete();
                    // Logger.log(FileUtils.class.getName() + ":  " + path + " deleted");
                }
                // directory
                else {
                    if (!path.endsWith("/")) {
                        path += "/";
                    }

                    // recursively delete directory contents
                    File[] contents = fconn.listFiles();
                    if (contents != null && contents.length > 0) {
                        for (int i = 0; i < contents.length; i++) {
                            delete(contents[i].getAbsolutePath());
                        }
                        fconn = new File(path);
                    }
                    // now delete this directory
                    fconn.delete();
                    // Logger.log(FileUtils.class.getName() + ":  " + path + " deleted");
                }
            }
        } finally {
            if (fconn != null)
                fconn = null;
        }
    }
}
