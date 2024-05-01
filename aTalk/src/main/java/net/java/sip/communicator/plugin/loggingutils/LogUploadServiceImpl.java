/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.plugin.loggingutils;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import net.java.sip.communicator.impl.protocol.jabber.JabberActivator;

import org.atalk.android.R;
import org.atalk.android.aTalkApp;
import org.atalk.persistance.FileBackend;
import org.atalk.persistance.ServerPersistentStoresRefreshDialog;
import org.atalk.service.fileaccess.FileCategory;
import org.atalk.service.log.LogUploadService;
import org.atalk.service.version.VersionService;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import timber.log.Timber;

/**
 * Send/upload logs, to specified destination.
 *
 * @author Damian Minkov
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
public class LogUploadServiceImpl implements LogUploadService
{
    /**
     * List of log files created for sending logs purpose. There is no easy way of waiting until
     * email is sent and deleting temp log file, so they are cached and removed on OSGI service stop action.
     */
    private final List<File> storedLogFiles = new ArrayList<>();

    /**
     * Retrieve logcat file from Android and Send the log files.
     *
     * @param destinations array of destination addresses
     * @param subject the subject if available
     * @param title the title for the action, used any intermediate dialogs that need to be shown, like "Choose action:".
     */
    public void sendLogs(String[] destinations, String subject, String title)
    {
        /* The path pointing to directory used to store temporary log archives. */
        File logStorageDir = FileBackend.getaTalkStore("atalk-logs", true);
        if (logStorageDir != null) {
            File logcatFile;
            File externalStorageFile;
            String logcatFN = new File("log", "atalk-current-logcat.txt").toString();
            try {
                debugPrintInfo();
                logcatFile = LoggingUtilsActivator.getFileAccessService().getPrivatePersistentFile(logcatFN, FileCategory.LOG);
                Runtime.getRuntime().exec("logcat -v time -f " + logcatFile.getAbsolutePath());
                // just wait for 100ms before collect logs - note: process redirect to file, and does not exit
                Thread.sleep(100);
                externalStorageFile = LogsCollector.collectLogs(logStorageDir, null);
            } catch (Exception ex) {
                aTalkApp.showToastMessage("Error creating logs file archive: " + ex.getMessage());
                return;
            }
            // Stores file name to remove it on service shutdown
            storedLogFiles.add(externalStorageFile);
            Context ctx = aTalkApp.getInstance();
            Uri logsUri = FileBackend.getUriForFile(ctx, externalStorageFile);

            Intent sendIntent = new Intent(Intent.ACTION_SEND);
            sendIntent.putExtra(Intent.EXTRA_EMAIL, destinations);
            sendIntent.putExtra(Intent.EXTRA_SUBJECT, subject);
            sendIntent.setType("application/zip");
            sendIntent.putExtra(Intent.EXTRA_STREAM, logsUri);
            sendIntent.putExtra(Intent.EXTRA_TEXT, ctx.getString(R.string.send_log_info));

            Intent chooserIntent = Intent.createChooser(sendIntent, title);
            // List<ResolveInfo> resInfoList = ctx.getPackageManager().queryIntentActivities(chooserIntent, PackageManager.MATCH_DEFAULT_ONLY);
            // for (ResolveInfo resolveInfo : resInfoList) {
            //     String packageName = resolveInfo.activityInfo.packageName;
            //     Timber.d("ResolveInfo package name: %s", packageName);
            //     ctx.grantUriPermission(packageName, logsUri, Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
            // }
            ctx.grantUriPermission("android", logsUri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
            // chooserIntent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION); not working; need above statement

            // Starting this activity from context that is not from the current activity; this flag is needed in this situation
            chooserIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            ctx.startActivity(chooserIntent);
        }
        else {
            Timber.e("Error sending debug log files");
        }
    }

    /**
     * Extract debug info for android device OS and aTalk installed version
     */
    private void debugPrintInfo()
    {
        String property = "http.agent";
        try {
            String sysProperty = System.getProperty(property);
            Timber.i("%s = %s", property, sysProperty);
        } catch (Exception e) {
            Timber.w(e, "An exception occurred while writing debug info");
        }

        VersionService versionSerVice = JabberActivator.getVersionService();
        Timber.i("Device installed with aTalk version: %s, version code: %s",
                versionSerVice.getCurrentVersion(), versionSerVice.getCurrentVersionCode());
    }

    /**
     * Frees resources allocated by this service.
     * Purge all files log directory and log sent.
     */
    public void dispose()
    {
        System.err.println("DISPOSE!!!!!!!!!!!!!");
        for (File logFile : storedLogFiles) {
            logFile.delete();
        }
        storedLogFiles.clear();
        // clean debug log directory after log sent
        ServerPersistentStoresRefreshDialog.purgeDebugLog();
    }
}
