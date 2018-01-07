package org.atalk.android.util;

import java.io.InputStream;

public class Zip
{
	/**
	 * get file list from zip package
	 * 
	 * @param zipFileString
	 *        name of zip package
	 * @param bContainFolder
	 *        whether includes folder
	 * @param bContainFile
	 *        whether includes file
	 * @return
	 * @throws Exception
	 */
	public static java.util.List<java.io.File> getFileList(String zipFileString, boolean bContainFolder, boolean bContainFile)
		throws Exception
	{
		java.util.List<java.io.File> fileList = new java.util.ArrayList<java.io.File>();
		java.util.zip.ZipInputStream inZip = new java.util.zip.ZipInputStream(new java.io.FileInputStream(zipFileString));
		java.util.zip.ZipEntry zipEntry;
		String szName = "";
		while ((zipEntry = inZip.getNextEntry()) != null) {
			szName = zipEntry.getName();
			if (zipEntry.isDirectory()) {
				// get the folder name of the widget
				szName = szName.substring(0, szName.length() - 1);
				java.io.File folder = new java.io.File(szName);
				if (bContainFolder) {
					fileList.add(folder);
				}
			}
			else {
				java.io.File file = new java.io.File(szName);
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
	 * @param zipFilePath
	 *        name of zip file
	 * @param fileString
	 *        name of decompressed file
	 * @return InputStream
	 * @throws Exception
	 */
	public static java.io.InputStream upZip(String zipFilePath, String fileString)
		throws Exception
	{
		java.util.zip.ZipFile zipFile = new java.util.zip.ZipFile(zipFilePath);
		java.util.zip.ZipEntry zipEntry = zipFile.getEntry(fileString);

		return zipFile.getInputStream(zipEntry);
	}

	/**
	 * extract a zip package to a specified folder
	 * 
	 * @param zipFileString
	 *        name of zip file
	 * @param outPathString
	 *        specified folder
	 * @throws Exception
	 */
	public static void unZipFolder(InputStream input, String outPathString)
		throws Exception
	{
		java.util.zip.ZipInputStream inZip = new java.util.zip.ZipInputStream(input);
		java.util.zip.ZipEntry zipEntry = null;
		String szName = "";

		while ((zipEntry = inZip.getNextEntry()) != null) {
			szName = zipEntry.getName();

			if (zipEntry.isDirectory()) {
				// get the folder name of the widget
				szName = szName.substring(0, szName.length() - 1);
				java.io.File folder = new java.io.File(outPathString + java.io.File.separator + szName);
				folder.mkdirs();
			}
			else {
				String fullPath = outPathString + java.io.File.separator + szName;
				String folderPath = fullPath.substring(0, fullPath.lastIndexOf(java.io.File.separator));
				java.io.File folder = new java.io.File(folderPath);
				folder.mkdirs();

				java.io.File file = new java.io.File(outPathString + java.io.File.separator + szName);
				file.createNewFile();
				// get the output stream of the file
				java.io.FileOutputStream out = new java.io.FileOutputStream(file);
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
	 * @param zipFileString
	 *        name of zip file
	 * @param outPathString
	 *        specified folder
	 * @throws Exception
	 */
	public static void unZipFolder(String zipFileString, String outPathString)
		throws Exception
	{
		unZipFolder(new java.io.FileInputStream(zipFileString), outPathString);
	}// end of func

	/**
	 * zip file/folder
	 * 
	 * @param srcFilePath
	 *        name of file/folder will be compressed
	 * @param zipFilePath
	 *        path of zip file
	 * @throws Exception
	 */
	public static void zipFolder(String srcFilePath, String zipFilePath)
		throws Exception
	{
		// create zip file
		java.util.zip.ZipOutputStream outZip = new java.util.zip.ZipOutputStream(new java.io.FileOutputStream(zipFilePath));

		// open zip file
		java.io.File file = new java.io.File(srcFilePath);

		// compress
		zipFiles(file.getParent() + java.io.File.separator, file.getName(), outZip);

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
	private static void zipFiles(String folderPath, String filePath, java.util.zip.ZipOutputStream zipOut)
		throws Exception
	{
		if (zipOut == null) {
			return;
		}

		java.io.File file = new java.io.File(folderPath + filePath);

		// is file?
		if (file.isFile()) {
			java.util.zip.ZipEntry zipEntry = new java.util.zip.ZipEntry(filePath);
			java.io.FileInputStream inputStream = new java.io.FileInputStream(file);
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
				java.util.zip.ZipEntry zipEntry = new java.util.zip.ZipEntry(filePath + java.io.File.separator);
				zipOut.putNextEntry(zipEntry);
				zipOut.closeEntry();
			}

			// if has subfile, travel all subfolder.
			for (int i = 0; i < fileList.length; i++) {
				zipFiles(folderPath, filePath + java.io.File.separator + fileList[i], zipOut);
			}// end of for

		}// end of if

	}// end of func
}