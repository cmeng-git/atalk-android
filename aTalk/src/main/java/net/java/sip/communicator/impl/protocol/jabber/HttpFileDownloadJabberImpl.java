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
package net.java.sip.communicator.impl.protocol.jabber;

import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;

import androidx.core.content.ContextCompat;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.Hashtable;
import java.util.Map;

import javax.crypto.Cipher;
import javax.crypto.CipherOutputStream;

import net.java.sip.communicator.service.protocol.AbstractFileTransfer;
import net.java.sip.communicator.service.protocol.Contact;
import net.java.sip.communicator.service.protocol.IMessage;
import net.java.sip.communicator.service.protocol.event.FileTransferStatusChangeEvent;

import org.atalk.android.R;
import org.atalk.android.aTalkApp;
import org.atalk.persistance.FileBackend;
import org.atalk.persistance.FilePathHelper;
import org.jivesoftware.smackx.omemo_media_sharing.AesgcmUrl;

import timber.log.Timber;

/**
 * The Jabber protocol HttpFileDownloadJabberImpl extension of the <code>AbstractFileTransfer</code>.
 *
 * @author Eng Chong Meng
 */
public class HttpFileDownloadJabberImpl extends AbstractFileTransfer {
    /* DownloadManager Broadcast Receiver Handler */
    private DownloadReceiver downloadReceiver = null;
    private final DownloadManager downloadManager = aTalkApp.getDownloadManager();

    /* previousDownloads <DownloadJobId, Download Link> */
    private final Hashtable<Long, String> previousDownloads = new Hashtable<>();

    private final String msgUuid;
    private final Contact mSender;

    /*
     * The advertised downloadable file info:
     * mFile: server url link last segment: File
     * mFileName: mFile filename
     * dnLink: server url link for download
     * mFileSize: the query size of the dnLink file
     */
    private final File mFile;
    private final String mFileName;
    private final String dnLink;
    // https download uri link; extracted from dnLink if it is AesgcmUrl
    private final Uri mUri;
    private long mFileSize;

    // Downloading tmp file.
    private File tmpFile = null;

    /**
     * The transfer file full path for saving the received file.
     */
    protected File mXferFile;

    /*
     * Transfer file encryption type, default to ENCRYPTION_NONE.
     */
    protected int mEncryption;

    /**
     * Creates an <code>IncomingFileTransferJabberImpl</code>.
     *
     * @param sender the sender of the file
     * @param id the message Uuid uniquely identify  record in DB
     * @param dnLinkDescription the download link may contains other options e.g. file.length()
     */
    public HttpFileDownloadJabberImpl(Contact sender, String id, String dnLinkDescription) {
        mSender = sender;

        // Create a new msg Uuid if none provided
        msgUuid = (id == null) ? String.valueOf(System.currentTimeMillis()) + hashCode() : id;

        String[] dnLinkInfos = dnLinkDescription.split("\\s+|,|\\t|\\n");
        dnLink = dnLinkInfos[0];
        String url;
        if (dnLink.matches("^aesgcm:.*")) {
            AesgcmUrl aesgcmUrl = new AesgcmUrl(dnLink);
            url = aesgcmUrl.getDownloadUrl().toString();
            mEncryption = IMessage.ENCRYPTION_OMEMO;
        }
        else {
            url = dnLink;
            mEncryption = IMessage.ENCRYPTION_NONE;
        }

        mUri = Uri.parse(url);
        mFileName = mUri.getLastPathSegment();
        mFile = (mFileName != null) ? new File(mFileName) : null;

        if (dnLinkInfos.length > 1 && "fileSize".matches(dnLinkInfos[1])) {
            mFileSize = Long.parseLong(dnLinkInfos[1].split("[:=]")[1]);
        }
        else
            mFileSize = -1;
    }

    /**
     * Unregister the HttpDownload transfer downloadReceiver.
     */
    @Override
    public void cancel() {
        doCleanup(-1);
    }

    /**
     * The direction is incoming.
     *
     * @return IN
     */
    public int getDirection() {
        return IN;
    }

    /**
     * Returns the sender of the file.
     *
     * @return the sender of the file
     */
    public Contact getContact() {
        return mSender;
    }

    /**
     * Returns the identifier of this file transfer.
     *
     * @return the identifier of this file transfer
     */
    public String getID() {
        return msgUuid;
    }

    /**
     * Returns the local file that is being transferred or to which we transfer.
     *
     * @return the file
     */
    public File getLocalFile() {
        return mFile;
    }

    /**
     * Returns the name of the file corresponding to this request.
     *
     * @return the name of the file corresponding to this request
     */
    public String getFileName() {
        return mFileName;
    }

    /**
     * Returns the size of the file corresponding to this request.
     *
     * @return the size of the file corresponding to this request
     */
    public long getFileSize() {
        return mFileSize;
    }

    /**
     * Returns the description of the file corresponding to this request.
     *
     * @return the description of the file corresponding to this request
     */
    public String getDnLink() {
        return dnLink;
    }

    /**
     * Returns the encryption of the file corresponding to this request.
     *
     * @return the encryption of the file corresponding to this request
     */
    public int getEncryptionType() {
        return mEncryption;
    }

    // ********************************************************************************************//
    // Routines supporting HTTP File Download

    /**
     * Method fired when the HttpFileDownload message is received.
     */
    public void initHttpFileDownload() {
        if (previousDownloads.contains(dnLink))
            return;

        // queryFileSize will also trigger onReceived; just ignore
        if (downloadReceiver == null) {
            downloadReceiver = new DownloadReceiver();
            ContextCompat.registerReceiver(aTalkApp.getInstance(), downloadReceiver,
                    new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE), ContextCompat.RECEIVER_EXPORTED);
        }
        if (mFileSize == -1) {
            mFileSize = queryFileSize();
        }
        // Timber.d("Download receiver registered %s: file size: %s", downloadReceiver, mFileSize);
    }

    /**
     * Query the http uploaded file size for auto download.
     */
    private long queryFileSize() {
        mFileSize = -1;
        DownloadManager.Request request = new DownloadManager.Request(mUri);
        long id = downloadManager.enqueue(request);
        DownloadManager.Query query = new DownloadManager.Query();
        query.setFilterById(id);

        // allow loop for 3 seconds for slow server. Server may return size == 0 ?
        int wait = 3;
        while ((wait-- > 0) && (mFileSize <= 0)) {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Timber.w("Download Manager query file size exception: %s", e.getMessage());
                return -1;
            }
            Cursor cursor = downloadManager.query(query);
            if (cursor.moveToFirst()) {
                // Value checking COLUMN_BYTES_DOWNLOADED_SO_FAR contains file size value, but COLUMN_TOTAL_SIZE_BYTES (0);
                // DownloadManager implementation error?
                mFileSize = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES));
                long fileSize = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR));
                mFileSize = Math.max(mFileSize, fileSize);
            }
            cursor.close();
        }

        // Timber.d("Download Manager file size query id: %s %s (%s)", id, mFileSize, wait);
        return mFileSize;
    }

    /**
     * Schedules media file download.
     *
     * @param xferFile The saved filename on download completed.
     */
    public void download(File xferFile) {
        mXferFile = xferFile;
        DownloadManager.Request request = new DownloadManager.Request(mUri);
        request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
        try {
            // Create a tmp file to receive download
            tmpFile = new File(FileBackend.getaTalkStore(FileBackend.TMP, true), mFileName);
            request.setDestinationUri(Uri.fromFile(tmpFile));
            long jobId = downloadManager.enqueue(request);
            if (jobId > 0) {
                previousDownloads.put(jobId, dnLink);
                startProgressChecker();
                fireStatusChangeEvent(FileTransferStatusChangeEvent.IN_PROGRESS, null);
            }
        } catch (SecurityException e) {
            aTalkApp.showToastMessage(e.getMessage());
        } catch (Exception e) {
            aTalkApp.showToastMessage(R.string.file_does_not_exist);
        }
    }

    /**
     * Queries the <code>DownloadManager</code> for the status of download job identified by given <code>id</code>.
     *
     * @param id download identifier which status will be returned.
     *
     * @return download status of the job identified by given id. If given job is not found
     * {@link DownloadManager#STATUS_FAILED} will be returned.
     */
    private int checkDownloadStatus(long id) {
        DownloadManager.Query query = new DownloadManager.Query();
        query.setFilterById(id);

        try (Cursor cursor = downloadManager.query(query)) {
            if (!cursor.moveToFirst())
                return DownloadManager.STATUS_FAILED;
            else {
                // update fileSize if last queryFileSize failed within the given timeout
                if (mFileSize <= 0) {
                    mFileSize = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES));
                }
                return cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS));
            }
        }
    }

    private class DownloadReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            // Fetching the download id received with the broadcast and proceed only
            // if the received broadcast is for our enqueued download by matching download id
            long lastDownloadId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1);
            int lastJobStatus = checkDownloadStatus(lastDownloadId);
            // Timber.d("Download receiver %s (%s): %s", lastDownloadId, previousDownloads, lastJobStatus);

            // Just ignore all unrelated download JobId, else proceed.
            if (previousDownloads.containsKey(lastDownloadId)) {
                if (lastJobStatus == DownloadManager.STATUS_SUCCESSFUL) {
                    String dnLink = previousDownloads.get(lastDownloadId);
                    Uri fileUri = downloadManager.getUriForDownloadedFile(lastDownloadId);
                    File inFile = new File(FilePathHelper.getFilePath(context, fileUri));

                    // update fileSize for progress bar update, in case it is still not updated by download Manager
                    mFileSize = inFile.length();
                    if (inFile.exists()) {
                        // OMEMO media file sharing - need to decrypt file content.
                        if ((dnLink != null) && dnLink.matches("^aesgcm:.*")) {
                            try {
                                AesgcmUrl aesgcmUrl = new AesgcmUrl(dnLink);
                                Cipher decryptCipher = aesgcmUrl.getDecryptionCipher();

                                FileInputStream fis = new FileInputStream(inFile);
                                FileOutputStream outputStream = new FileOutputStream(mXferFile);
                                CipherOutputStream cipherOutputStream = new CipherOutputStream(outputStream, decryptCipher);

                                int count;
                                byte[] buffer = new byte[4096];
                                while ((count = fis.read(buffer)) != -1) {
                                    cipherOutputStream.write(buffer, 0, count);
                                }

                                fis.close();
                                outputStream.flush();
                                cipherOutputStream.close();
                                // inFile.delete();

                                fireStatusChangeEvent(FileTransferStatusChangeEvent.COMPLETED, null);
                            } catch (Exception e) {
                                fireStatusChangeEvent(FileTransferStatusChangeEvent.FAILED,
                                        "Failed to decrypt OMEMO media file: " + inFile);
                            }
                        }
                        else {
                            // Plain media file sharing; rename will move the infile to outfile dir.
                            if (inFile.renameTo(mXferFile)) {
                                fireStatusChangeEvent(FileTransferStatusChangeEvent.COMPLETED, null);
                                // Timber.d("Downloaded completed: %s (%s)", mFileName, mFileSize);
                            }
                        }
                    }
                }
                else if (lastJobStatus == DownloadManager.STATUS_FAILED) {
                    Timber.d("Downloaded failed: %s (%s)", mFileName, mFileSize);
                    fireStatusChangeEvent(FileTransferStatusChangeEvent.FAILED, dnLink);
                }
                doCleanup(lastDownloadId);
            }
        }
    }

    /**
     * Get the jobId for the given dnLink
     *
     * @param dnLink previously download link
     *
     * @return jobId for the dnLink if available else -1
     */
    private long getJobId(String dnLink) {
        for (Map.Entry<Long, String> entry : previousDownloads.entrySet()) {
            if (entry.getValue().equals(dnLink)) {
                return entry.getKey();
            }
        }
        return -1;
    }

    //=========================================================
    /*
     * Monitoring file download progress at UPDATE_INTERVAL ms interval
     */
    private static final int UPDATE_INTERVAL = 50;

    // Maximum download idle time (60mS) i.e, MAX_IDLE_TIME * UPDATE_INTERVAL before giving up and force to stop
    private static final int MAX_IDLE_TIME = 60000 / UPDATE_INTERVAL;

    private boolean isProgressCheckerRunning = false;
    private final Handler handler = new Handler();
    private int waitTime;

    /**
     * Checks http download progress.
     */
    private void checkDnProgress() {
        long lastDownloadId = getJobId(dnLink);
        if (lastDownloadId == -1)
            return;

        int lastJobStatus = checkDownloadStatus(lastDownloadId);
        // Timber.d("Downloading file last jobId: %s; lastJobStatus: %s; dnProgress: %s (%s)",
        //       lastDownloadId, lastJobStatus, previousProgress, waitTime);

        // Terminate stopProgressChecker if completed.
        if (lastJobStatus == DownloadManager.STATUS_SUCCESSFUL) {
            stopProgressChecker();
            return;
        }
        // Terminate the file download process if failed or idleTime timeout.
        else if (lastJobStatus == DownloadManager.STATUS_FAILED || waitTime < 0) {
            Timber.d("Downloaded failed fileSize: %s (%s); %s", tmpFile.length(), mFileSize, waitTime);
            fireStatusChangeEvent(FileTransferStatusChangeEvent.FAILED, null);
            doCleanup(lastDownloadId);
            return;
        }

        DownloadManager.Query query = new DownloadManager.Query();
        query.setFilterById(lastDownloadId);
        Cursor cursor = downloadManager.query(query);
        if (!cursor.moveToFirst()) {
            waitTime--;
        }
        else {
            do {
                long byteReceive = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR));
                if (byteReceive <= mByteTransfer)
                    waitTime--;
                else {
                    waitTime = MAX_IDLE_TIME;
                    fireProgressChangeEvent(System.currentTimeMillis(), byteReceive);
                }
            } while (cursor.moveToNext());
        }
        cursor.close();
    }

    /**
     * Perform cleanup at end of http file transfer process: passed, failed or cancel.
     * Stops watching download progress.
     */
    private void doCleanup(long jobId) {
        stopProgressChecker();
        if (jobId == -1) {
            jobId = getJobId(dnLink);
        }
        if (jobId != -1) {
            previousDownloads.remove(jobId);
            downloadManager.remove(jobId);
        }

        // Unregister the HttpDownload transfer downloadReceiver.
        // Receiver not registered exception - may occur if window is refreshed while download is in progress?
        if (downloadReceiver != null) {
            try {
                aTalkApp.getInstance().unregisterReceiver(downloadReceiver);
            } catch (IllegalArgumentException ie) {
                Timber.w("Unregister download receiver exception: %s", ie.getMessage());
            }
            downloadReceiver = null;
        }
        // Timber.d("Download Manager for JobId: %s; File: %s (status: %s)", jobId, dnLink, status);
        // Purge any tmp file that was created only after cleanup.
        if (tmpFile != null && tmpFile.exists() && tmpFile.delete()) {
            Timber.w("HttpFileDownload tmp file deleted: %s", tmpFile.getPath());
        }
    }

    /**
     * Starts watching download progress.
     * This method is safe to call multiple times. Starting an already running progress checker is a no-op.
     */
    private void startProgressChecker() {
        if (!isProgressCheckerRunning) {
            isProgressCheckerRunning = true;
            waitTime = MAX_IDLE_TIME;
            mByteTransfer = 0;
            progressChecker.run();
        }
    }

    /**
     * Stops watching download progress.
     */
    private void stopProgressChecker() {
        isProgressCheckerRunning = false;
        handler.removeCallbacks(progressChecker);
    }

    /**
     * Checks download progress and updates status, then re-schedules itself.
     */
    private final Runnable progressChecker = new Runnable() {
        @Override
        public void run() {
            if (isProgressCheckerRunning) {
                checkDnProgress();
                handler.postDelayed(this, UPDATE_INTERVAL);
            }
        }
    };
}
