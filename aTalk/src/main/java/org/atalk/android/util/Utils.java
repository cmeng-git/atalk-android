package org.atalk.android.util;

import android.content.Context;
import android.content.res.Resources;
import android.os.*;
import android.util.DisplayMetrics;
import android.widget.Toast;

import java.io.*;
import java.util.*;

public class Utils
{
	public static final int IO_BUFFER_SIZE = 8 * 1024;
	private static Random randGen = new Random();

	private Utils()
	{
	}

	public static boolean isExternalStorageRemovable()
	{
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.GINGERBREAD) {
			return Environment.isExternalStorageRemovable();
		}
		return true;
	}

	public static File getExternalCacheDir(Context context)
	{
		if (hasExternalCacheDir()) {
			return context.getExternalCacheDir();
		}

		// Before Froyo we need to construct the external cache dir ourselves
		final String cacheDir = "/Android/data/" + context.getPackageName() + "/cache/";
		return new File(Environment.getExternalStorageDirectory().getPath() + cacheDir);
	}

	public static boolean hasExternalCacheDir()
	{
		return Build.VERSION.SDK_INT >= Build.VERSION_CODES.FROYO;
	}

	/**
	 * Similar to android.os.Environment.getExternalStorageDirectory(), except that here, we
	 * return all possible storage directories. The Environment class only returns one storage
	 * directory. If you have an extended SD card, it does not return the directory path. Here we
	 * are trying to return all of them.
	 *
	 * @return
	 */
	@SuppressWarnings("finally")
	public static String[] getStorageDirectories()
	{
		String[] dirs = null;
		BufferedReader bufReader = null;

		try {
			bufReader = new BufferedReader(new FileReader("/proc/mounts"));
			ArrayList<String> list = new ArrayList<String>();
			String line;
			while ((line = bufReader.readLine()) != null) {
				if (line.contains("vfat") || line.contains("mnt")
						|| line.contains("exfat")
						|| line.contains("fat32")
						|| line.contains("fuse")) {
					StringTokenizer tokens = new StringTokenizer(line, " ");
					String s = tokens.nextToken();
					s = tokens.nextToken(); // Take the second token, i.e. mount point

					// PC external shared directory name
					if ((s.equals(Environment.getExternalStorageDirectory().getPath())
							|| line.contains("/mnt/shared/extSdCard")
							|| line.contains("/storage"))
							&& !line.contains("emulated")) {
						list.add(s);
					}
					else if (line.contains("/dev/block/vold")) {
						if (!line.contains("/mnt/secure")
								&& !line.contains("/mnt/asec")
								&& !line.contains("/mnt/obb")
								&& !line.contains("/dev/mapper")
								&& !line.contains("tmpfs")) {
							list.add(s);
						}
					}
				}
			}

			dirs = new String[list.size()];
			for (int i = 0; i < list.size(); i++) {
				dirs[i] = (String) list.get(i);
			}
		}
		catch (IOException ex) {
			ex.printStackTrace();
		} finally {
			if (bufReader != null) {
				try {
					bufReader.close();
				}
				catch (IOException ex) {
					ex.printStackTrace();
				}
			}
		}
		return dirs;
	}

	/**
	 * This method converts dp unit to equivalent device specific value in pixels.
	 *
	 * @param dp
	 * 		A value in dp(Device independent pixels) unit. Which we need to convert into pixels
	 * @param context
	 * 		Context to get resources and device specific display metrics
	 * @return A float value to represent Pixels equivalent to dp according to device
	 */
	public static float convertDpToPixel(float dp, Context context)
	{
		Resources resources = context.getResources();
		DisplayMetrics metrics = resources.getDisplayMetrics();
		float px = dp * (metrics.densityDpi / 160f);
		return px;
	}

	/**
	 * This method converts device specific pixels to device independent pixels.
	 *
	 * @param px
	 * 		A value in px (pixels) unit. Which we need to convert into db
	 * @param context
	 * 		Context to get resources and device specific display metrics
	 * @return A float value to represent db equivalent to px value
	 */
	public static float convertPixelsToDp(float px, Context context)
	{
		Resources resources = context.getResources();
		DisplayMetrics metrics = resources.getDisplayMetrics();
		float dp = px / (metrics.densityDpi / 160f);
		return dp;
	}

	public static boolean isValidEmail(CharSequence target)
	{
		return (target != null)
				&& android.util.Patterns.EMAIL_ADDRESS.matcher(target).matches();
	}

	public static boolean isValidPhoneNum(CharSequence target)
	{
		return (target != null)
				&& (target.length() >= 4)
				&& android.util.Patterns.PHONE.matcher(target).matches();
	}

	public static void showToastNotification(Context context, int message)
	{
		Toast textToast = Toast.makeText(context, message, Toast.LENGTH_SHORT);
		textToast.show();
	}

	public static String randomString(int length)
	{
		if (length < 1) {
			return null;
		}
		// Create a char buffer to put random letters and numbers in.
		char[] randBuffer = new char[length];
		for (int i = 0; i < randBuffer.length; i++) {
			randBuffer[i] = numbersAndLetters[randGen.nextInt(71)];
		}
		return new String(randBuffer);
	}

	private static char[] numbersAndLetters = ("0123456789abcdefghijklmnopqrstuvwxyz"
			+ "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ").toCharArray();
}
