/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.androidupdate;

import android.app.DownloadManager;
import android.content.*;
import android.database.Cursor;
import android.net.Uri;
import android.os.Environment;

import net.java.sip.communicator.service.httputil.HttpUtils;
import net.java.sip.communicator.service.update.UpdateService;
import net.java.sip.communicator.util.Logger;
import net.java.sip.communicator.util.ServiceUtils;

import org.atalk.android.*;
import org.atalk.android.gui.dialogs.DialogActivity;
import org.atalk.android.gui.util.AndroidUtils;
import org.atalk.service.version.Version;
import org.atalk.service.version.VersionService;

import java.io.File;
import java.io.InputStream;
import java.util.*;

/**
 * Android update service implementation. It checks for update and schedules .apk download using
 * <tt>DownloadManager</tt>.
 *
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
public class UpdateServiceImpl implements UpdateService
{
    /**
     * The logger
     */
    private final static Logger logger = Logger.getLogger(UpdateServiceImpl.class);

    /**
     * The name of the property which specifies the update link in the configuration file.
     */
    private static final String PROP_UPDATE_LINK = "org.atalk.android.UPDATE_LINK";

    /**
     * Apk mime type constant.
     */
    private static final String APK_MIME_TYPE = "application/vnd.android.package-archive";

    // path are case sensitive
    private static final String filePath = "/releases/atalk-android/versionupdate.properties";

    /**
     * Current installed version string
     */
    private String currentVersion;
    private int currentVersionCode;

    /**
     * Latest version string
     */
    private String latestVersion;
    private int latestVersionCode;

    /**
     * The download link
     */
    private String downloadLink;
    // private String changesLink;

    /**
     * <tt>SharedPreferences</tt> used to store download ids.
     */
    private SharedPreferences store;

    /**
     * Name of <tt>SharedPreferences</tt> entry used to store old download ids. Ids are stored in
     * single string separated by ",".
     */
    private static final String ENTRY_NAME = "apk_ids";

    /**
     * Checks for updates.
     *
     * @param notifyAboutNewestVersion <tt>true</tt> if the user is to be notified if they have the
     * newest version already; otherwise, <tt>false</tt>
     */
    @Override
    public void checkForUpdates(boolean notifyAboutNewestVersion)
    {
        boolean isLatest = isLatestVersion();
        logger.info("Is latest: " + isLatest);
        logger.info("Current version: " + currentVersion);
        logger.info("Latest version: " + latestVersion);
        logger.info("Download link: " + downloadLink);
        // logger.info("Changes link: " + changesLink);

        // cmeng: reverse the logic for !isLast for testing
        if (!isLatest && (downloadLink != null)) {
            // Check old or scheduled downloads
            List<Long> previousDownloads = getOldDownloads();
            if (previousDownloads.size() > 0) {
                long lastDownload = previousDownloads.get(previousDownloads.size() - 1);

                int lastJobStatus = checkDownloadStatus(lastDownload);
                if (lastJobStatus == DownloadManager.STATUS_SUCCESSFUL) {
                    DownloadManager downloadManager = aTalkApp.getDownloadManager();
                    Uri fileUri = downloadManager.getUriForDownloadedFile(lastDownload);
                    File file = new File(fileUri.getPath());

                    if (file.exists()) {
                        // Ask the user if he wants to install
                        askInstallDownloadedApk(lastDownload);
                        return;
                    }
                }
                else if (lastJobStatus != DownloadManager.STATUS_FAILED) {
                    // Download is in progress or scheduled for retry
                    AndroidUtils.showAlertDialog(aTalkApp.getGlobalContext(),
                            aTalkApp.getResString(R.string.plugin_updatechecker_DIALOG_IN_PROGRESS_TITLE),
                            aTalkApp.getResString(R.string.plugin_updatechecker_DIALOG_IN_PROGRESS));
                    return;
                }
            }

            AndroidUtils.showAlertConfirmDialog(aTalkApp.getGlobalContext(),
                    aTalkApp.getResString(R.string.plugin_updatechecker_DIALOG_TITLE),
                    aTalkApp.getResString(R.string.plugin_updatechecker_DIALOG_MESSAGE,
                            latestVersion, Integer.toString(latestVersionCode), aTalkApp.getResString(R.string.APPLICATION_NAME)),
                    aTalkApp.getResString(R.string.plugin_updatechecker_BUTTON_DOWNLOAD),
                    new DialogActivity.DialogListener()
                    {
                        @Override
                        public boolean onConfirmClicked(DialogActivity dialog)
                        {
                            downloadApk();
                            return true;
                        }

                        @Override
                        public void onDialogCancelled(DialogActivity dialog)
                        {
                        }
                    }
            );
        }
        else if (notifyAboutNewestVersion) {
            // Notify that running version is up to date
            AndroidUtils.showAlertDialog(aTalkApp.getGlobalContext(),
                    aTalkApp.getResString(R.string.plugin_updatechecker_DIALOG_NOUPDATE_TITLE),
                    aTalkApp.getResString(R.string.plugin_updatechecker_DIALOG_NOUPDATE,
                            currentVersion, Integer.toString(currentVersionCode)));
        }
    }

    /**
     * Asks the user whether to install downloaded .apk.
     *
     * @param apkDownloadId download id of the apk to install.
     */
    private void askInstallDownloadedApk(final long apkDownloadId)
    {
        AndroidUtils.showAlertConfirmDialog(aTalkApp.getGlobalContext(),
                aTalkApp.getResString(R.string.plugin_updatechecker_DIALOG_DOWNLOADED_TITLE),
                aTalkApp.getResString(R.string.plugin_updatechecker_DIALOG_DOWNLOADED),
                aTalkApp.getResString(R.string.plugin_updatechecker_BUTTON_INSTALL),
                new DialogActivity.DialogListener()
                {
                    @Override
                    public boolean onConfirmClicked(DialogActivity dialog)
                    {
                        DownloadManager downloadManager = aTalkApp.getDownloadManager();
                        Uri fileUri = downloadManager.getUriForDownloadedFile(apkDownloadId);

                        Intent intent = new Intent(Intent.ACTION_VIEW);
                        intent.setDataAndType(fileUri, APK_MIME_TYPE);
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

                        aTalkApp.getGlobalContext().startActivity(intent);
                        return true;
                    }

                    @Override
                    public void onDialogCancelled(DialogActivity dialog)
                    {
                    }
                });
    }

    /**
     * Queries the <tt>DownloadManager</tt> for the status of download job identified by given <tt>id</tt>.
     *
     * @param id download identifier which status will be returned.
     * @return download status of the job identified by given id. If given job is not found
     * {@link DownloadManager#STATUS_FAILED} will be returned.
     */
    private int checkDownloadStatus(long id)
    {
        DownloadManager downloadManager = aTalkApp.getDownloadManager();
        DownloadManager.Query query = new DownloadManager.Query();
        query.setFilterById(id);

        Cursor cursor = downloadManager.query(query);
        try {
            if (!cursor.moveToFirst())
                return DownloadManager.STATUS_FAILED;
            else
                return cursor.getInt(cursor.getColumnIndex(DownloadManager.COLUMN_STATUS));
        } finally {
            cursor.close();
        }
    }

    /**
     * Schedules .apk download.
     */
    private void downloadApk()
    {
        Uri uri = Uri.parse(downloadLink);
        String fileName = uri.getLastPathSegment();

        DownloadManager.Request request = new DownloadManager.Request(uri);
        request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
        request.setMimeType(APK_MIME_TYPE);
        request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName);

        DownloadManager downloadManager = aTalkApp.getDownloadManager();
        long jobId = downloadManager.enqueue(request);
        rememberDownloadId(jobId);
    }

    private void rememberDownloadId(long id)
    {
        SharedPreferences store = getStore();
        String storeStr = store.getString(ENTRY_NAME, "");
        storeStr += id + ",";
        store.edit().putString(ENTRY_NAME, storeStr).apply();
    }

    private SharedPreferences getStore()
    {
        if (store == null) {
            store = aTalkApp.getGlobalContext().getSharedPreferences("store", Context.MODE_PRIVATE);
        }
        return store;
    }

    private List<Long> getOldDownloads()
    {
        String storeStr = getStore().getString(ENTRY_NAME, "");
        String[] idStrs = storeStr.split(",");
        List<Long> apkIds = new ArrayList<>(idStrs.length);
        for (String idStr : idStrs) {
            try {
                if (!idStr.isEmpty())
                    apkIds.add(Long.parseLong(idStr));
            } catch (NumberFormatException e) {
                logger.error("Error parsing apk id for string: " + idStr + " [" + storeStr + "]");
            }
        }
        return apkIds;
    }

    /**
     * Removes old downloads.
     */
    void removeOldDownloads()
    {
        List<Long> apkIds = getOldDownloads();

        DownloadManager downloadManager = aTalkApp.getDownloadManager();
        for (long id : apkIds) {
            logger.debug("Removing .apk for id " + id);
            downloadManager.remove(id);
        }
        getStore().edit().remove(ENTRY_NAME).apply();
    }

    /**
     * Gets the current (software) version.
     *
     * @return the current (software) version
     */
    private static Version getCurrentVersion()
    {
        return getVersionService().getCurrentVersion();
    }

    /**
     * Gets the latest available (software) version online.
     *
     * @return the latest (software) version
     */
    @Override
    public String getLatestVersion()
    {
        return latestVersion;
    }

    /**
     * Returns the currently registered instance of version service.
     *
     * @return the current version service.
     */
    private static VersionService getVersionService()
    {
        return ServiceUtils.getService(UpdateActivator.bundleContext, VersionService.class);
    }

    /**
     * Determines whether we are currently running the latest version.
     *
     * @return <tt>true</tt> if current running application is the latest version; otherwise, <tt>false</tt>
     */
    @Override
    public boolean isLatestVersion()
    {
        String[] aLinks;
        HttpUtils.HTTPResponseResult res = null;

        VersionService versionService = getVersionService();
        currentVersion = versionService.getCurrentVersionName();
        currentVersionCode = versionService.getCurrentVersionCode();

        try {
            String updateLink = UpdateActivator.getConfiguration().getString(PROP_UPDATE_LINK);
            if (updateLink == null) {
                if (logger.isDebugEnabled())
                    logger.debug("Updates are disabled, faking latest version.");
            }
            else {
                aLinks = updateLink.split(",");
                for (String aLink : aLinks) {
                    aLink = aLink.trim() + filePath;
                    res = HttpUtils.openURLConnection(aLink);
                    if (res != null)
                        break;
                }

                if (res != null) {
                    InputStream in = null;
                    Properties props = new Properties();

                    try {
                        in = res.getContent();
                        props.load(in);
                    } finally {
                        if (in != null)
                            in.close();
                    }

                    latestVersion = props.getProperty("last_version");
                    latestVersionCode = Integer.valueOf(props.getProperty("last_version_code"));
                    if (BuildConfig.DEBUG) {
                        downloadLink = props.getProperty("download_link-debug");
                    }
                    else {
                        downloadLink = props.getProperty("download_link");
                    }
                    // return true is current running application is already the latest
                    return (currentVersionCode >= latestVersionCode);
                }
            }
        } catch (Exception e) {
            logger.warn("Could not retrieve latest version for checking! ", e);
            return false;
        }
        return true;
    }
}
