/*
 * aTalk, android VoIP and Instant Messaging client
 * Copyright 2014 Eng Chong Meng
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.atalk.impl.appupdate;

import android.annotation.SuppressLint;
import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;

import androidx.core.content.ContextCompat;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import net.java.sip.communicator.service.update.UpdateService;
import net.java.sip.communicator.util.ServiceUtils;

import org.atalk.android.BuildConfig;
import org.atalk.android.R;
import org.atalk.android.aTalkApp;
import org.atalk.android.gui.aTalk;
import org.atalk.android.gui.dialogs.DialogActivity;
import org.atalk.persistance.FileBackend;
import org.atalk.persistance.FilePathHelper;
import org.atalk.service.version.Version;
import org.atalk.service.version.VersionService;
import org.jetbrains.annotations.NotNull;

import timber.log.Timber;

/**
 * aTalk update service implementation. It checks for an update and schedules .apk download using <code>DownloadManager</code>.
 * It is only activated for the debug version. Android initials the auto-update from PlayStore for release version.
 *
 * @author Eng Chong Meng
 */
public class UpdateServiceImpl implements UpdateService {
    // Default update link; path is case-sensitive.
    private static final String[] updateLinks = {
            "https://raw.githubusercontent.com/cmeng-git/atalk-android/master/aTalk/release/version.properties",
            "https://atalk.sytes.net/releases/atalk-android/version.properties"
    };

    // filename is case-sensitive
    private static final String fileNameApk = String.format("aTalk-%s-%s.apk", BuildConfig.FLAVOR, BuildConfig.BUILD_TYPE);
    // github apk is in the release directory; for apk download
    private static final String urlApk = "https://github.com/cmeng-git/atalk-android/releases/download/%s/";

    /**
     * Apk mime type constant.
     */
    private static final String APK_MIME_TYPE = "application/vnd.android.package-archive";

    /**
     * The download link for the installed application
     */
    private String downloadLink = null;

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
    private HttpURLConnection mHttpConnection;

    /**
     * <code>SharedPreferences</code> used to store download ids.
     */
    private SharedPreferences store;

    /**
     * Name of <code>SharedPreferences</code> entry used to store old download ids. Ids are stored in
     * single string separated by ",".
     */
    private static final String ENTRY_NAME = "apk_ids";

    /**
     * Checks for updates and notify user of any new version, and take necessary action.
     */
    @Override
    public void checkForUpdates() {
        // cmeng: reverse the logic to !isLatestVersion() for testing
        mIsLatest = isLatestVersion();
        Timber.i("Is latest: %s\nCurrent version: %s\nLatest version: %s\nDownload link: %s",
                mIsLatest, currentVersion, latestVersion, downloadLink);

        if ((downloadLink != null)) {
            if (!mIsLatest) {
                if (checkLastDLFileAction() < DownloadManager.ERROR_UNKNOWN)
                    return;

                DialogActivity.showConfirmDialog(aTalkApp.getInstance(),
                        R.string.update_install_update,
                        R.string.update_update_available,
                        R.string.update_download,
                        new DialogActivity.DialogListener() {
                            @Override
                            public boolean onConfirmClicked(DialogActivity dialog) {
                                downloadApk();
                                return true;
                            }

                            @Override
                            public void onDialogCancelled(@NotNull DialogActivity dialog) {
                            }
                        }, latestVersion, latestVersionCode, fileNameApk, currentVersion, currentVersionCode
                );
            }
            else {
                // Notify that running version is up to date
                DialogActivity.showConfirmDialog(aTalkApp.getInstance(),
                        R.string.update_new_version_none,
                        R.string.update_up_to_date,
                        R.string.update_download,
                        new DialogActivity.DialogListener() {
                            @Override
                            public boolean onConfirmClicked(DialogActivity dialog) {
                                if (aTalk.hasWriteStoragePermission(aTalk.getInstance(), true)) {
                                    if (checkLastDLFileAction() >= DownloadManager.ERROR_UNKNOWN) {
                                        downloadApk();
                                    }
                                }
                                return true;
                            }

                            @Override
                            public void onDialogCancelled(@NotNull DialogActivity dialog) {
                            }
                        }, currentVersion, currentVersionCode, latestVersion, latestVersionCode
                );
            }
        }
        else {
            aTalkApp.showToastMessage(R.string.update_new_version_none);
        }
    }

    /**
     * Check for any existing downloaded file and take appropriate action;
     *
     * @return Last DownloadManager status; default to DownloadManager.ERROR_UNKNOWN if status unknown
     */
    private int checkLastDLFileAction() {
        // Check old or scheduled downloads
        int lastJobStatus = DownloadManager.ERROR_UNKNOWN;

        List<Long> previousDownloads = getOldDownloads();
        if (!previousDownloads.isEmpty()) {
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
                DialogActivity.showDialog(aTalkApp.getInstance(),
                        R.string.update_in_progress,
                        R.string.update_download_in_progress);
            }
            else {
                // Download id return failed status, remove failed id and retry
                removeOldDownloads();
                DialogActivity.showDialog(aTalkApp.getInstance(),
                        R.string.update_install_update, R.string.update_download_failed);
            }
        }
        return lastJobStatus;
    }

    /**
     * Asks the user whether to install downloaded .apk.
     *
     * @param fileUri download file uri of the apk to install.
     */
    private void askInstallDownloadedApk(Uri fileUri) {
        DialogActivity.showConfirmDialog(aTalkApp.getInstance(),
                R.string.update_download_completed,
                R.string.update_download_ready,
                mIsLatest ? R.string.update_reInstall : R.string.update_install,
                new DialogActivity.DialogListener() {
                    @Override
                    public boolean onConfirmClicked(DialogActivity dialog) {
                        // Need REQUEST_INSTALL_PACKAGES in manifest; Intent.ACTION_VIEW works for both
                        Intent intent;
                        intent = new Intent(Intent.ACTION_INSTALL_PACKAGE);
                        intent.setDataAndType(fileUri, APK_MIME_TYPE);
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        aTalkApp.getInstance().startActivity(intent);
                        return true;
                    }

                    @Override
                    public void onDialogCancelled(@NotNull DialogActivity dialog) {
                    }
                }, latestVersion);
    }

    /**
     * Queries the <code>DownloadManager</code> for the status of download job identified by given <code>id</code>.
     *
     * @param id download identifier which status will be returned.
     *
     * @return download status of the job identified by given id. If given job is not found
     * {@link DownloadManager#STATUS_FAILED} will be returned.
     */
    @SuppressLint("Range")
    private int checkDownloadStatus(long id) {
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
    private void downloadApk() {
        Uri uri = Uri.parse(downloadLink);
        String fileName = uri.getLastPathSegment();

        if (downloadReceiver == null) {
            downloadReceiver = new DownloadReceiver();
            ContextCompat.registerReceiver(aTalkApp.getInstance(), downloadReceiver,
                    new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE), ContextCompat.RECEIVER_EXPORTED);
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

    private class DownloadReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (checkLastDLFileAction() < DownloadManager.ERROR_UNKNOWN)
                return;

            // unregistered downloadReceiver
            if (downloadReceiver != null) {
                aTalkApp.getInstance().unregisterReceiver(downloadReceiver);
                downloadReceiver = null;
            }
        }
    }

    private SharedPreferences getStore() {
        if (store == null) {
            store = aTalkApp.getInstance().getSharedPreferences("store", Context.MODE_PRIVATE);
        }
        return store;
    }

    private void rememberDownloadId(long id) {
        SharedPreferences store = getStore();
        String storeStr = store.getString(ENTRY_NAME, "");
        storeStr += id + ",";
        store.edit().putString(ENTRY_NAME, storeStr).apply();
    }

    private List<Long> getOldDownloads() {
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
    void removeOldDownloads() {
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
     *
     * @return true if apkFile has the specified versionCode
     */
    private boolean isValidApkVersion(Uri fileUri, long versionCode) {
        // Default to valid as getPackageArchiveInfo() always return null; but sometimes OK
        boolean isValid = true;
        File apkFile = new File(FilePathHelper.getFilePath(aTalkApp.getInstance(), fileUri));

        if (apkFile.exists()) {
            // Get downloaded apk actual versionCode and check its versionCode validity
            PackageManager pm = aTalkApp.getInstance().getPackageManager();
            PackageInfo pckgInfo = pm.getPackageArchiveInfo(apkFile.getAbsolutePath(), 0);

            if (pckgInfo != null) {
                long apkVersionCode;
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P)
                    apkVersionCode = pckgInfo.versionCode;
                else
                    apkVersionCode = pckgInfo.getLongVersionCode();

                isValid = (versionCode == apkVersionCode);
                if (!isValid) {
                    aTalkApp.showToastMessage(R.string.update_version_invalid, apkVersionCode, versionCode);
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
    public static Version getCurrentVersion() {
        return getVersionService().getCurrentVersion();
    }

    /**
     * Gets the current (software) version.
     *
     * @return the current (software) version
     */
    public static long getCurrentVersionCode() {
        return getVersionService().getCurrentVersionCode();
    }

    /**
     * Gets the latest available (software) version online.
     *
     * @return the latest (software) version
     */
    @Override
    public String getLatestVersion() {
        return latestVersion;
    }

    /**
     * Returns the currently registered instance of version service.
     *
     * @return the current version service.
     */
    private static VersionService getVersionService() {
        return ServiceUtils.getService(UpdateActivator.bundleContext, VersionService.class);
    }

    /**
     * Determines whether we are currently running the latest version.
     *
     * @return <code>true</code> if current running application is the latest version; otherwise, <code>false</code>
     */
    @Override
    public boolean isLatestVersion() {
        VersionService versionService = getVersionService();
        currentVersion = versionService.getCurrentVersionName();
        currentVersionCode = versionService.getCurrentVersionCode();

        for (String aLink : updateLinks) {
            try {
                if (isValidateLink(aLink)) {
                    InputStream inputStream = mHttpConnection.getInputStream();
                    Properties mProperties = new Properties();
                    mProperties.load(inputStream);
                    inputStream.close();

                    latestVersion = mProperties.getProperty("last_version");
                    latestVersionCode = Long.parseLong(mProperties.getProperty("last_version_code"));

                    if (aLink.contains("github")) {
                        downloadLink = urlApk.replace("%s", latestVersion) + fileNameApk;
                    }
                    else {
                        String aLinkPrefix = aLink.substring(0, aLink.lastIndexOf("/") + 1);
                        downloadLink = aLinkPrefix + fileNameApk;
                    }
                    if (isValidateLink(downloadLink)) {
                        // return true if current running application is already the latest
                        return (currentVersionCode >= latestVersionCode);
                    }
                    else {
                        downloadLink = null;
                    }
                }
            } catch (IOException e) {
                Timber.w("Could not retrieve version.properties for checking: %s", e.getMessage());
            }
        }
        // return true if all failed to force update.
        return true;
    }

    /**
     * Check if the given link is accessible.
     *
     * @param link the link to check
     *
     * @return true if link is accessible
     */
    private boolean isValidateLink(String link) {
        try {
            URL mUrl = new URL(link);
            mHttpConnection = (HttpURLConnection) mUrl.openConnection();
            mHttpConnection.setRequestMethod("GET");
            mHttpConnection.setRequestProperty("Content-length", "0");
            mHttpConnection.setUseCaches(false);
            mHttpConnection.setAllowUserInteraction(false);
            mHttpConnection.setConnectTimeout(100000);
            mHttpConnection.setReadTimeout(100000);

            mHttpConnection.connect();
            int responseCode = mHttpConnection.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                return true;
            }
        } catch (IOException e) {
            Timber.d("Invalid url: %s", e.getMessage());
            return false;
        }
        return false;
    }
}
