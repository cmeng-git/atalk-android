/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.androidupdate;

import android.app.DownloadManager;
import android.content.*;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;

import net.java.sip.communicator.service.update.UpdateService;
import net.java.sip.communicator.util.ServiceUtils;

import org.atalk.android.*;
import org.atalk.android.gui.dialogs.DialogActivity;
import org.atalk.android.gui.util.AndroidUtils;
import org.atalk.persistance.FilePathHelper;
import org.atalk.service.version.Version;
import org.atalk.service.version.VersionService;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.*;

import timber.log.Timber;

/**
 * aTalk update service implementation. It checks for an update and schedules .apk download using <tt>DownloadManager</tt>.
 *
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
public class UpdateServiceImpl implements UpdateService
{
    // Default update link
    private static final String[] updateLinks = {"https://atalk.sytes.net", "https://atalk.mooo.com"};

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
    private long currentVersionCode;

    /**
     * The latest version string / version code
     */
    private String latestVersion;
    private long latestVersionCode;

    /* DownloadManager Broadcast Receiver Handler */
    private DownloadReceiver downloadReceiver = null;

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
        Timber.i("Is latest: %s\nCurrent version: %s\nLatest version: %s\nDownload link: %s",
                isLatest, currentVersion, latestVersion, downloadLink);
        // Timber.i("Changes link: %s", changesLink);

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
                    File apkFile = new File(FilePathHelper.getPath(aTalkApp.getGlobalContext(), fileUri));

                    // Ask the user if he wants to install if available and valid apk is found
                    if (isValidApkVersion(apkFile, latestVersionCode)) {
                        askInstallDownloadedApk(fileUri);
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
                            latestVersion, Long.toString(latestVersionCode), aTalkApp.getResString(R.string.APPLICATION_NAME)),
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
                            currentVersion, Long.toString(currentVersionCode)));
        }
    }

    /**
     * Asks the user whether to install downloaded .apk.
     *
     * @param fileUri download file uri of the apk to install.
     */
    private void askInstallDownloadedApk(Uri fileUri)
    {
        DialogActivity.showConfirmDialog(aTalkApp.getGlobalContext(),
                R.string.plugin_updatechecker_DIALOG_DOWNLOADED_TITLE,
                R.string.plugin_updatechecker_DIALOG_DOWNLOADED,
                R.string.plugin_updatechecker_BUTTON_INSTALL,
                new DialogActivity.DialogListener()
                {
                    @Override
                    public boolean onConfirmClicked(DialogActivity dialog)
                    {
                        // Need REQUEST_INSTALL_PACKAGES in manifest; Intent.ACTION_VIEW works for both
                        Intent intent;
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)
                            intent = new Intent(Intent.ACTION_INSTALL_PACKAGE);
                        else
                            intent = new Intent(Intent.ACTION_VIEW);

                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        intent.setDataAndType(fileUri, APK_MIME_TYPE);

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

        try (Cursor cursor = downloadManager.query(query)) {
            if (!cursor.moveToFirst())
                return DownloadManager.STATUS_FAILED;
            else
                return cursor.getInt(cursor.getColumnIndex(DownloadManager.COLUMN_STATUS));
        }
    }

    /**
     * Schedules .apk download.
     */
    private void downloadApk()
    {
        Uri uri = Uri.parse(downloadLink);
        String fileName = uri.getLastPathSegment();

        if (downloadReceiver == null) {
            downloadReceiver = new DownloadReceiver();
            aTalkApp.getGlobalContext().registerReceiver(downloadReceiver,
                    new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE));
        }

        DownloadManager.Request request = new DownloadManager.Request(uri);
        request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
        request.setMimeType(APK_MIME_TYPE);
        request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName);

        DownloadManager downloadManager = aTalkApp.getDownloadManager();
        long jobId = downloadManager.enqueue(request);
        rememberDownloadId(jobId);
    }

    private class DownloadReceiver extends BroadcastReceiver
    {
        @Override
        public void onReceive(Context context, Intent intent)
        {
            List<Long> previousDownloads = getOldDownloads();
            if (previousDownloads.size() > 0) {
                long lastDownload = previousDownloads.get(previousDownloads.size() - 1);

                int lastJobStatus = checkDownloadStatus(lastDownload);
                if (lastJobStatus == DownloadManager.STATUS_SUCCESSFUL) {
                    DownloadManager downloadManager = aTalkApp.getDownloadManager();
                    Uri fileUri = downloadManager.getUriForDownloadedFile(lastDownload);
                    File apkFile = new File(FilePathHelper.getPath(aTalkApp.getGlobalContext(), fileUri));

                    // Ask the user if he wants to install if available and valid apk is found
                    if (isValidApkVersion(apkFile, latestVersionCode)) {
                        askInstallDownloadedApk(fileUri);
                        return;
                    }
                }
                else if (lastJobStatus == DownloadManager.STATUS_FAILED) {
                    // Download is in progress or scheduled for retry
                    AndroidUtils.showAlertDialog(aTalkApp.getGlobalContext(),
                            aTalkApp.getResString(R.string.plugin_updatechecker_DIALOG_TITLE),
                            aTalkApp.getResString(R.string.plugin_updatechecker_DOWNLOAD_FAILED));
                    return;
                }
            }

            // unregistered downloadReceiver
            if (downloadReceiver != null) {
                aTalkApp.getGlobalContext().unregisterReceiver(downloadReceiver);
                downloadReceiver = null;
            }
        }
    }

    private SharedPreferences getStore()
    {
        if (store == null) {
            store = aTalkApp.getGlobalContext().getSharedPreferences("store", Context.MODE_PRIVATE);
        }
        return store;
    }

    private void rememberDownloadId(long id)
    {
        SharedPreferences store = getStore();
        String storeStr = store.getString(ENTRY_NAME, "");
        storeStr += id + ",";
        store.edit().putString(ENTRY_NAME, storeStr).apply();
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
                Timber.e("Error parsing apk id for string: %s [%s]", idStr, storeStr);
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
            Timber.d("Removing .apk for id %s", id);
            downloadManager.remove(id);
        }
        getStore().edit().remove(ENTRY_NAME).apply();
    }

    /**
     * Validate the downloaded apk file for correct versionCode and its apk name
     *
     * @param apkFile apk File
     * @param versionCode apk versionCode
     * @return true if apkFile has the specified versionCode
     */
    private boolean isValidApkVersion(File apkFile, long versionCode)
    {
        boolean isValid = false;

        if (apkFile.exists()) {
            PackageManager pm = aTalkApp.getGlobalContext().getPackageManager();
            PackageInfo info = pm.getPackageArchiveInfo(apkFile.getPath(), 0);
            isValid = (info != null) && (versionCode == info.versionCode);
        }

        return isValid;
    }

    /**
     * Gets the current (software) version.
     *
     * @return the current (software) version
     */
    public static Version getCurrentVersion()
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
        Properties mProperties = null;
        String errMsg = "";

        VersionService versionService = getVersionService();
        currentVersion = versionService.getCurrentVersionName();
        currentVersionCode = versionService.getCurrentVersionCode();

        if (updateLinks.length == 0) {
            Timber.d("Updates are disabled, emulates latest version.");
        }
        else {
            for (String aLink : updateLinks) {
                String urlStr = aLink.trim() + filePath;
                try {
                    URL mUrl = new URL(urlStr);
                    HttpURLConnection httpConnection = (HttpURLConnection) mUrl.openConnection();
                    httpConnection.setRequestMethod("GET");
                    httpConnection.setRequestProperty("Content-length", "0");
                    httpConnection.setUseCaches(false);
                    httpConnection.setAllowUserInteraction(false);
                    httpConnection.setConnectTimeout(100000);
                    httpConnection.setReadTimeout(100000);

                    httpConnection.connect();
                    int responseCode = httpConnection.getResponseCode();
                    if (responseCode == HttpURLConnection.HTTP_OK) {
                        InputStream in = httpConnection.getInputStream();
                        mProperties = new Properties();
                        mProperties.load(in);
                        break;
                    }
                } catch (IOException e) {
                    errMsg = e.getMessage();
                }
            }

            if (mProperties != null) {
                latestVersion = mProperties.getProperty("last_version");
                latestVersionCode = Long.parseLong(mProperties.getProperty("last_version_code"));
                if (BuildConfig.DEBUG) {
                    downloadLink = mProperties.getProperty("download_link-debug");
                }
                else {
                    downloadLink = mProperties.getProperty("download_link");
                }
                // return true is current running application is already the latest
                return (currentVersionCode >= latestVersionCode);
            }
            else {
                Timber.w("Could not retrieve version.properties for checking: %s", errMsg);
            }
        }
        return true;
    }
}
