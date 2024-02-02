/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.android.plugin.errorhandler;

import android.content.Context;
import android.content.SharedPreferences;

import org.atalk.android.aTalkApp;
import org.atalk.service.fileaccess.FileCategory;

import java.io.File;

import timber.log.Timber;

/**
 * The <code>ExceptionHandler</code> is used to catch unhandled exceptions which occur on the UI
 * <code>Thread</code>. Those exceptions normally cause current <code>Activity</code> to freeze and the
 * process usually must be killed after the Application Not Responding dialog is displayed. This
 * handler kills Jitsi process at the moment when the exception occurs, so that user don't have
 * to wait for ANR dialog. It also marks in <code>SharedPreferences</code> that such crash has
 * occurred. Next time the Jitsi is started it will ask the user if he wants to send the logs.<br/>
 * <p>
 * Usually system restarts Jitsi and it's service automatically after the process was killed.
 * That's because the service was still bound to some <code>Activities</code> at the moment when the
 * exception occurred.<br/>
 * <p>
 * The handler is bound to the <code>Thread</code> in every <code>OSGiActivity</code>.
 *
 * @author Pawel Domas
 */
public class ExceptionHandler implements Thread.UncaughtExceptionHandler
{
	/**
	 * Parent exception handler(system default).
	 */
	private final Thread.UncaughtExceptionHandler parent;

	/**
	 * Creates new instance of <code>ExceptionHandler</code> bound to given <code>Thread</code>.
	 *
	 * @param t
	 * 		the <code>Thread</code> which will be handled.
	 */
	private ExceptionHandler(Thread t)
	{
		parent = t.getUncaughtExceptionHandler();
		t.setUncaughtExceptionHandler(this);
	}

	/**
	 * Checks and attaches the <code>ExceptionHandler</code> if it hasn't been bound already.
	 */
	public static void checkAndAttachExceptionHandler()
	{
		Thread current = Thread.currentThread();
		if (current.getUncaughtExceptionHandler() instanceof ExceptionHandler) {
			return;
		}
		// Creates and binds new handler instance
		new ExceptionHandler(current);
	}

	/**
	 * Marks the crash in <code>SharedPreferences</code> and kills the process.
	 * Storage: /data/data/org.atalk.android/files/log/atalk-crash-logcat.txt
	 * <p>
	 * {@inheritDoc}
	 */
	@Override
	public void uncaughtException(Thread thread, Throwable ex)
	{
		markCrashedEvent();
		parent.uncaughtException(thread, ex);

		Timber.e(ex, "uncaughtException occurred, killing the process...");

		// Save logcat for more information.
		File logcatFile;
		String logcatFN = new File("log", "atalk-crash-logcat.txt").toString();
		try {
			logcatFile = ExceptionHandlerActivator.getFileAccessService()
					.getPrivatePersistentFile(logcatFN, FileCategory.LOG);
			Runtime.getRuntime().exec("logcat -v time -f " + logcatFile.getAbsolutePath());
		}
		catch (Exception e) {
			Timber.e("Couldn't save crash logcat file.");
		}
		android.os.Process.killProcess(android.os.Process.myPid());
		System.exit(10);
	}

	/**
	 * Returns <code>SharedPreferences</code> used to mark the crash event.
	 *
	 * @return <code>SharedPreferences</code> used to mark the crash event.
	 */
	private static SharedPreferences getStorage()
	{
		return aTalkApp.getInstance().getSharedPreferences("crash", Context.MODE_PRIVATE);
	}

	/**
	 * Marks that the crash has occurred in <code>SharedPreferences</code>.
	 */
	private static void markCrashedEvent()
	{
		getStorage().edit().putBoolean("crash", true).apply();
	}

	/**
	 * Returns <code>true</code> if Jitsi crash was detected.
	 *
	 * @return <code>true</code> if Jitsi crash was detected.
	 */
	public static boolean hasCrashed()
	{
		return getStorage().getBoolean("crash", false);
	}

	/**
	 * Clears the "crashed" flag.
	 */
	public static void resetCrashedStatus()
	{
		getStorage().edit().putBoolean("crash", false).apply();
	}
}
