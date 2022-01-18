/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.androidupdate;

import android.annotation.SuppressLint;
import android.app.DownloadManager;
import android.content.*;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;

import net.java.sip.communicator.service.update.UpdateService;
import net.java.sip.communicator.util.ServiceUtils;

import org.atalk.android.*;
import org.atalk.android.gui.dialogs.DialogActivity;
import org.atalk.persistance.FileBackend;
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
    private static final String[] updateLinks = {"https://atalk.sytes.net"};

    /**
     * Apk mime type constant.
     */
    private static final String APK_MIME_TYPE = "application/vnd.android.package-archive";

    // path are case sensitive
    private static final String filePath = "/releases/atalk-android/versionupdate.properties";

    /**
     * Current installed version string / version Code
     */
    private String currentVersion;
    private long currentVersionCode;

    /**
     * The latest version string / version code
     */
    private String latestVersion;
    private long latestVersionCode;

    private boolean mIsLatest = false;

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
        // cmeng: reverse the logic to !isLatestVersion() for testing
        mIsLatest = isLatestVersion();
        Timber.i("Is latest: %s\nCurrent version: %s\nLatest version: %s\nDownload link: %s",
                mIsLatest, currentVersion, latestVersion, downloadLink);
        // Timber.i("Changes link: %s", changesLink);

        if (!mIsLatest && (downloadLink != null)) {
            if (checkLastDLFileAction() < DownloadManager.ERROR_UNKNOWN)
                return;

            DialogActivity.showConfirmDialog(aTalkApp.getGlobalContext(),
                    R.string.plugin_update_Install_Update,
                    R.string.plugin_update_Update_Available,
                    R.string.plugin_update_Download,
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
                    }, latestVersion, Long.toString(latestVersionCode), aTalkApp.getResString(R.string.APPLICATION_NAME)
            );
        }
        else if (notifyAboutNewestVersion) {
            // Notify that running version is up to date
            DialogActivity.showConfirmDialog(aTalkApp.getGlobalContext(),
                    R.string.plugin_update_New_Version_None,
                    R.string.plugin_update_UpToDate,
                    R.string.plugin_update_Download,
                    new DialogActivity.DialogListener()
                    {
                        @Override
                        public boolean onConfirmClicked(DialogActivity dialog)
                        {
                            if (checkLastDLFileAction() >= DownloadManager.ERROR_UNKNOWN)
                                downloadApk();
                            return true;
                        }

                        @Override
                        public void onDialogCancelled(DialogActivity dialog)
                        {
                        }
                    }, currentVersion, currentVersionCode
            );
        }
    }

    /**
     * Check for any existing downloaded file and take appropriate action;
     *
     * @return Last DownloadManager status; default to DownloadManager.ERROR_UNKNOWN if status unknown
     */
    private int checkLastDLFileAction()
    {
        // Check old or scheduled downloads
        int lastJobStatus = DownloadManager.ERROR_UNKNOWN;

        List<Long> previousDownloads = getOldDownloads();
        if (previousDownloads.size() > 0) {
            long lastDownload = previousDownloads.get(previousDownloads.size() - 1);

            lastJobStatus = checkDownloadStatus(lastDownload);
            if (lastJobStatus == DownloadManager.STATUS_SUCCESSFUL) {
                DownloadManager downloadManager = aTalkApp.getDownloadManager();
                Uri fileUri = downloadManager.getUriForDownloadedFile(lastDownload);

                // Ask the user if he wants to install the valid apk when found
                if (isValidApkVersion(fileUri, latestVersionCode)) {
                    askInstallDownloadedApk(fileUri);
                }
            }
            else if (lastJobStatus != DownloadManager.STATUS_FAILED) {
                // Download is in progress or scheduled for retry
                DialogActivity.showDialog(aTalkApp.getGlobalContext(),
                        R.string.plugin_update_InProgress,
                        R.string.plugin_update_Download_InProgress);
            }
            else {
                // Download is in progress or scheduled for retry
                DialogActivity.showDialog(aTalkApp.getGlobalContext(),
                        R.string.plugin_update_Install_Update, R.string.plugin_update_Download_failed);
            }
        }
        return lastJobStatus;
    }

    /**
     * Asks the user whether to install downloaded .apk.
     *
     * @param fileUri download file uri of the apk to install.
     */
    private void askInstallDownloadedApk(Uri fileUri)
    {
        DialogActivity.showConfirmDialog(aTalkApp.getGlobalContext(),
                R.string.plugin_update_Download_Completed,
                R.string.plugin_update_Download_Ready,
                mIsLatest ? R.string.plugin_update_ReInstall : R.string.plugin_update_Install,
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

                        intent.setDataAndType(fileUri, APK_MIME_TYPE);
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        aTalkApp.getGlobalContext().startActivity(intent);
                        return true;
                    }

                    @Override
                    public void onDialogCancelled(DialogActivity dialog)
                    {
                    }
                }, latestVersion);
    }

    /**
     * Queries the <tt>DownloadManager</tt> for the status of download job identified by given <tt>id</tt>.
     *
     * @param id download identifier which status will be returned.
     * @return download status of the job identified by given id. If given job is not found
     * {@link DownloadManager#STATUS_FAILED} will be returned.
     */
    @SuppressLint("Range")
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
        File dnFile = new File(FileBackend.getaTalkStore(FileBackend.TMP, true), fileName);
        request.setDestinationUri(Uri.fromFile(dnFile));

        DownloadManager downloadManager = aTalkApp.getDownloadManager();
        long jobId = downloadManager.enqueue(request);
        rememberDownloadId(jobId);
    }

    private class DownloadReceiver extends BroadcastReceiver
    {
        @Override
        public void onReceive(Context context, Intent intent)
        {
            if (checkLastDLFileAction() < DownloadManager.ERROR_UNKNOWN)
                return;

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
     * @param fileUri apk Uri
     * @param versionCode use the given versionCode to check against the apk versionCode
     * @return true if apkFile has the specified versionCode
     */
    private boolean isValidApkVersion(Uri fileUri, long versionCode)
    {
        // Default to valid as getPackageArchiveInfo() always return null; but sometimes OK
        boolean isValid = true;
        File apkFile = new File(FilePathHelper.getFilePath(aTalkApp.getGlobalContext(), fileUri));

        if (apkFile.exists()) {
            // Get downloaded apk actual versionCode and check its versionCode validity
            PackageManager pm = aTalkApp.getGlobalContext().getPackageManager();
            PackageInfo pckgInfo = pm.getPackageArchiveInfo(apkFile.getPath(), 0);

            if (pckgInfo != null) {
                long apkVersionCode;
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P)
                    apkVersionCode = pckgInfo.versionCode;
                else
                    apkVersionCode = pckgInfo.getLongVersionCode();

                isValid = (versionCode == apkVersionCode);
                if (!isValid) {
                    aTalkApp.showToastMessage(R.string.plugin_update_Version_Invalid, apkVersionCode, versionCode);
                    Timber.d("Downloaded apk actual version code: %s (%s)", apkVersionCode, versionCode);
                }
            }
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
     * Gets the current (software) version.
     *
     * @return the current (software) version
     */
    public static long getCurrentVersionCode()
    {
        return getVersionService().getCurrentVersionCode();
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
