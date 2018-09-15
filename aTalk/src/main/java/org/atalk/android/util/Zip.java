package org.atalk.android.util;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.*;

public class Zip
{
    /**
     * get file list from zip package
     *
     * @param zipFileString name of zip package
     * @param bContainFolder whether includes folder
     * @param bContainFile whether includes file
     * @return
     * @throws Exception
     */
    public static List<File> getFileList(String zipFileString, boolean bContainFolder, boolean bContainFile)
            throws Exception
    {
        List<File> fileList = new ArrayList<>();
        ZipInputStream inZip = new ZipInputStream(new FileInputStream(zipFileString));
        ZipEntry zipEntry;
        String szName = "";
        while ((zipEntry = inZip.getNextEntry()) != null) {
            szName = zipEntry.getName();
            if (zipEntry.isDirectory()) {
                // get the folder name of the widget
                szName = szName.substring(0, szName.length() - 1);
                File folder = new File(szName);
                if (bContainFolder) {
                    fileList.add(folder);
                }
            }
            else {
                File file = new File(szName);
                if (bContainFile) {
                    fileList.add(file);
                }
            }
        }// end of while
        inZip.close();
        return fileList;
    }

    /**
     * get InputStream of file/folder in zip file
     *
     * @param zipFilePath name of zip file
     * @param fileString name of decompressed file
     * @return InputStream
     * @throws Exception
     */
    public static InputStream upZip(String zipFilePath, String fileString)
            throws Exception
    {
        ZipFile zipFile = new ZipFile(zipFilePath);
        ZipEntry zipEntry = zipFile.getEntry(fileString);
        return zipFile.getInputStream(zipEntry);
    }

    /**
     * extract a zip package to a specified folder
     *
     * @param input name of zip file
     * @param outPathString specified folder
     * @throws Exception
     */
    public static void unZipFolder(InputStream input, String outPathString)
            throws Exception
    {
        ZipInputStream inZip = new ZipInputStream(input);
        ZipEntry zipEntry = null;
        String szName = "";

        while ((zipEntry = inZip.getNextEntry()) != null) {
            szName = zipEntry.getName();

            if (zipEntry.isDirectory()) {
                // get the folder name of the widget
                szName = szName.substring(0, szName.length() - 1);
                File folder = new File(outPathString + File.separator + szName);
                folder.mkdirs();
            }
            else {
                String fullPath = outPathString + File.separator + szName;
                String folderPath = fullPath.substring(0, fullPath.lastIndexOf(File.separator));
                File folder = new File(folderPath);
                folder.mkdirs();

                File file = new File(outPathString + File.separator + szName);
                file.createNewFile();
                // get the output stream of the file
                FileOutputStream out = new FileOutputStream(file);
                int len;
                byte[] buffer = new byte[1024];
                // read (len) bytes into buffer
                while ((len = inZip.read(buffer)) != -1) {
                    // write (len) byte from buffer at the position 0
                    out.write(buffer, 0, len);
                    out.flush();
                }
                out.close();
            }
        }// end of while
        inZip.close();
    }

    /**
     * extract a zip package to a specified folder
     *
     * @param zipFileString name of zip file
     * @param outPathString specified folder
     * @throws Exception
     */
    public static void unZipFolder(String zipFileString, String outPathString)
            throws Exception
    {
        unZipFolder(new FileInputStream(zipFileString), outPathString);
    }// end of func

    /**
     * zip file/folder
     *
     * @param srcFilePath name of file/folder will be compressed
     * @param zipFilePath path of zip file
     * @throws Exception
     */
    public static void zipFolder(String srcFilePath, String zipFilePath)
            throws Exception
    {
        // create zip file
        ZipOutputStream outZip = new ZipOutputStream(new FileOutputStream(zipFilePath));

        // open zip file
        File file = new File(srcFilePath);

        // compress
        zipFiles(file.getParent() + File.separator, file.getName(), outZip);

        // finish
        outZip.finish();
        outZip.close();

    }// end of func

    /**
     * compress file
     *
     * @param folderPath
     * @param filePath
     * @param zipOut
     * @throws Exception
     */
    private static void zipFiles(String folderPath, String filePath, ZipOutputStream zipOut)
            throws Exception
    {
        if (zipOut == null) {
            return;
        }

        File file = new File(folderPath + filePath);

        // is file?
        if (file.isFile()) {
            ZipEntry zipEntry = new ZipEntry(filePath);
            FileInputStream inputStream = new FileInputStream(file);
            zipOut.putNextEntry(zipEntry);

            int len;
            byte[] buffer = new byte[4096];

            while ((len = inputStream.read(buffer)) != -1) {
                zipOut.write(buffer, 0, len);
            }

            inputStream.close();
            zipOut.closeEntry();
        }
        else {
            // get subfile under the folder
            String fileList[] = file.list();

            // if no subfile, just add to zip file.
            if (fileList.length <= 0) {
                ZipEntry zipEntry = new ZipEntry(filePath + File.separator);
                zipOut.putNextEntry(zipEntry);
                zipOut.closeEntry();
            }

            // if has subfile, travel all subfolder.
            for (int i = 0; i < fileList.length; i++) {
                zipFiles(folderPath, filePath + File.separator + fileList[i], zipOut);
            }// end of for

        }// end of if

    }// end of func
}