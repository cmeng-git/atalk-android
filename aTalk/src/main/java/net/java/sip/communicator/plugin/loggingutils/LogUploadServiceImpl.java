/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.plugin.loggingutils;

import android.content.Intent;
import android.net.Uri;

import org.atalk.android.R;
import org.atalk.android.aTalkApp;
import org.atalk.persistance.FileBackend;
import org.atalk.persistance.ServerPersistentStoresRefreshDialog;
import org.atalk.service.fileaccess.FileCategory;
import org.atalk.service.log.LogUploadService;

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
    private List<File> storedLogFiles = new ArrayList<>();

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
            File logcatFile = null;
            File externalStorageFile = null;
            String logcatFN = new File("log", "atalk-current-logcat.txt").toString();
            try {
                logcatFile = LoggingUtilsActivator.getFileAccessService().getPrivatePersistentFile(logcatFN, FileCategory.LOG);
                Runtime.getRuntime().exec("logcat -v time -f " + logcatFile);
                externalStorageFile = LogsCollector.collectLogs(logStorageDir, null);
            } catch (Exception ex) {
                aTalkApp.showToastMessage("Error creating logs file archive: " + ex.getMessage());
                return;
            }
            // Stores file name to remove it on service shutdown
            storedLogFiles.add(externalStorageFile);

            Intent sendIntent = new Intent(Intent.ACTION_SEND);
            sendIntent.putExtra(Intent.EXTRA_EMAIL, destinations);
            sendIntent.putExtra(Intent.EXTRA_SUBJECT, subject);
            sendIntent.setType("application/zip");

            Uri logsUri = FileBackend.getUriForFile(aTalkApp.getGlobalContext(), externalStorageFile);
            sendIntent.putExtra(Intent.EXTRA_STREAM, logsUri);
            sendIntent.putExtra(Intent.EXTRA_TEXT, aTalkApp.getResString(R.string.service_gui_SEND_LOGS_INFO));

            // we are starting this activity from context that is most probably not from the
            // current activity and this flag is needed in this situation
            Intent chooserIntent = Intent.createChooser(sendIntent, title);
            chooserIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

            aTalkApp.getGlobalContext().startActivity(chooserIntent);
        }
        else {
            Timber.e("Error sending debug log files");
        }
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
