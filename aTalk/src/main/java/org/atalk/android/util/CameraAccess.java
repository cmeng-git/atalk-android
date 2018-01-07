package org.atalk.android.util;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;

import android.os.Environment;
import android.util.Log;

public class CameraAccess
{

	public static final int MEDIA_TYPE_IMAGE = 1;
	public static final int MEDIA_TYPE_VIDEO = 2;

	/** Create a file Uri for saving an image or video */
	// public static Uri getOutputMediaFileUri(int type) {
	// return Uri.fromFile(getOutputMediaFile(type));
	// }

	public static String getOutputMediaFilePath(int type)
	{
		// return Uri.fromFile(getOutputMediaFile(type));
		File file = getOutputMediaFile(type);
		if (file != null) {
			return file.getAbsolutePath();
		}
		return "";

	}

	public static String getOutputMediaFolder()
	{
		File mediaStorageDir = getOutputMedisFolder();

		if (mediaStorageDir == null)
			return null;

		return mediaStorageDir.getPath();
	}

	private static File getOutputMedisFolder()
	{
		File mediaStorageDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM), "Camera");
		// This location works best if you want the created images to be shared
		// between applications and persist after your app has been uninstalled.

		// Create the storage directory if it does not exist
		if (!mediaStorageDir.exists()) {
			if (!mediaStorageDir.mkdirs()) {
				Log.d("MyCameraApp", "failed to create directory");
				return null;
			}
		}

		return mediaStorageDir;
	}

	/** Create a File for saving an image or video */
	private static File getOutputMediaFile(int type)
	{
		// To be safe, you should check that the SDCard is mounted
		// using Environment.getExternalStorageState() before doing this.

		File mediaStorageDir = getOutputMedisFolder();

		// Create a media file name
		String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
		File mediaFile;
		if (type == MEDIA_TYPE_IMAGE) {
			mediaFile = new File(mediaStorageDir.getPath() + File.separator + "IMG_" + timeStamp + ".jpg");
		}
		else if (type == MEDIA_TYPE_VIDEO) {
			mediaFile = new File(mediaStorageDir.getPath() + File.separator + "VID_" + timeStamp + ".mp4");
		}
		else {
			return null;
		}

		return mediaFile;
	}

}
