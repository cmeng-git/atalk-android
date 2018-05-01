/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.plugin.loggingutils;

import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.support.v4.content.FileProvider;

import net.java.sip.communicator.util.Logger;

import org.atalk.android.aTalkApp;
import org.atalk.android.gui.aTalk;
import org.atalk.persistance.ServerPersistentStoresRefreshDialog;
import org.atalk.service.fileaccess.FileCategory;
import org.atalk.service.log.LogUploadService;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

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
     * The logger.
     */
    private Logger logger = Logger.getLogger(LogUploadServiceImpl.class.getName());

    /**
     * List of log files created for sending logs purpose. There is no easy way of waiting until
     * email is sent and deleting temp log file, so they are cached and removed on OSGI service stop action.
     */
    private List<File> storedLogFiles = new ArrayList<>();

    /**
     * The path pointing to directory used to store temporary log archives.
     */
    private static final String storagePath = Environment.getExternalStorageDirectory().getPath() + "/atalk-logs/";

    /**
     * Retrieve logcat file from Android and Send the log files.
     *
     * @param destinations array of destination addresses
     * @param subject the subject if available
     * @param title the title for the action, used any intermediate dialogs that need to be shown, like "Choose action:".
     */
    public void sendLogs(String[] destinations, String subject, String title)
    {
        try {
            File storageDir = new File(storagePath);
            if (!storageDir.exists())
                storageDir.mkdir();

            File logcatFile;
            String logcatFN = new File("log", "atalk-current-logcat.txt").toString();
            try {
                logcatFile = LoggingUtilsActivator.getFileAccessService().getPrivatePersistentFile(logcatFN, FileCategory.LOG);

                Runtime.getRuntime().exec("logcat -c -v time -f " + logcatFile.getAbsolutePath());
            } catch (Exception e) {
                logger.error("Couldn't save current logcat file.");
            }
            System.err.println("STORAGE DIR======" + storageDir);
            File externalStorageFile = LogsCollector.collectLogs(storageDir, null);

            // Stores file name to remove it on service shutdown
            storedLogFiles.add(externalStorageFile);

            Intent sendIntent = new Intent(Intent.ACTION_SEND);
            sendIntent.putExtra(Intent.EXTRA_EMAIL, destinations);
            sendIntent.putExtra(Intent.EXTRA_SUBJECT, subject);
            sendIntent.setType("application/zip");

            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.M) {
                Uri logsUri = FileProvider.getUriForFile(aTalkApp.getGlobalContext(), aTalk.APP_FILE_PROVIDER,
                        externalStorageFile);
                sendIntent.putExtra(Intent.EXTRA_STREAM, logsUri);
            }
            else {
                sendIntent.putExtra(Intent.EXTRA_STREAM, externalStorageFile);
            }

            // we are starting this activity from context that is most probably not from the
            // current activity and this flag is needed in this situation
            Intent chooserIntent = Intent.createChooser(sendIntent, title);
            chooserIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

            aTalkApp.getGlobalContext().startActivity(chooserIntent);
        } catch (Exception e) {
            logger.error("Error sending files", e);
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
