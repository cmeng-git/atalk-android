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
package org.atalk.android.gui.chat.filetransfer;

import android.app.DownloadManager;
import android.content.*;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.text.TextUtils;
import android.view.*;

import net.java.sip.communicator.impl.filehistory.FileHistoryServiceImpl;
import net.java.sip.communicator.impl.protocol.jabber.HttpFileDownloadJabberImpl;
import net.java.sip.communicator.service.filehistory.FileRecord;
import net.java.sip.communicator.service.protocol.FileTransfer;
import net.java.sip.communicator.service.protocol.event.FileTransferStatusChangeEvent;
import net.java.sip.communicator.service.protocol.event.FileTransferStatusListener;
import net.java.sip.communicator.util.ConfigurationUtils;
import net.java.sip.communicator.util.GuiUtils;

import org.atalk.android.R;
import org.atalk.android.aTalkApp;
import org.atalk.android.gui.AndroidGUIActivator;
import org.atalk.android.gui.chat.ChatFragment;
import org.atalk.android.gui.chat.ChatMessage;
import org.atalk.persistance.FileBackend;
import org.atalk.persistance.FilePathHelper;
import org.jivesoftware.smackx.omemo_media_sharing.AesgcmUrl;

import java.io.*;
import java.util.*;

import javax.crypto.Cipher;
import javax.crypto.CipherOutputStream;

import timber.log.Timber;

/**
 * The <tt>ReceiveFileConversationComponent</tt> is the component shown in the conversation area
 * of the chat window to display a incoming file transfer.
 *
 * @author Eng Chong Meng
 */
public class FileHttpDownloadConversation extends FileTransferConversation
        implements FileTransferStatusListener
{
    private HttpFileDownloadJabberImpl httpFileTransferJabber;
    private int xferStatus;
    private long fileSize;
    private String fileName;
    private String dnLink;
    private String mSender;

    /* previousDownloads <DownloadJobId, Download Link> */
    private final Hashtable<Long, String> previousDownloads = new Hashtable<>();

    /* previousDownloads <DownloadJobId, DownloadFileMimeType Link> */
    // private final Hashtable<Long, String> mimeTypes = new Hashtable<>();

    /* DownloadManager Broadcast Receiver Handler */
    private DownloadManager downloadManager;
    private DownloadReceiver downloadReceiver = null;
    private FileHistoryServiceImpl mFHS;

    private FileHttpDownloadConversation(ChatFragment cPanel, String dir)
    {
        super(cPanel, dir);
    }

    /**
     * Creates a <tt>FileHttpDownloadConversation</tt>.
     *
     * @param cPanel the chat panel
     * @param sender the message <tt>sender</tt>
     * @param date the date
     */
    // Constructor used by ChatFragment to start handle ReceiveFileTransferRequest
    public static FileHttpDownloadConversation newInstance(ChatFragment cPanel, String sender,
            HttpFileDownloadJabberImpl httpFileTransferJabber, final Date date)
    {
        FileHttpDownloadConversation fragmentRFC = new FileHttpDownloadConversation(cPanel, FileRecord.IN);
        fragmentRFC.mSender = sender;
        fragmentRFC.httpFileTransferJabber = httpFileTransferJabber;
        fragmentRFC.mDate = GuiUtils.formatDateTime(date);
        fragmentRFC.msgUuid = httpFileTransferJabber.getID();
        fragmentRFC.xferStatus = httpFileTransferJabber.getStatus();
        fragmentRFC.downloadManager = aTalkApp.getDownloadManager();
        fragmentRFC.mFHS = (FileHistoryServiceImpl) AndroidGUIActivator.getFileHistoryService();

        return fragmentRFC;
    }

    public View HttpFileDownloadConversionForm(LayoutInflater inflater, ChatFragment.MessageViewHolder msgViewHolder,
            ViewGroup container, int id, boolean init)
    {
        msgViewId = id;
        View convertView = inflateViewForFileTransfer(inflater, msgViewHolder, container, init);
        messageViewHolder.stickerView.setImageDrawable(null);

        dnLink = httpFileTransferJabber.getDnLink();
        fileName = httpFileTransferJabber.getFileName();
        fileSize = httpFileTransferJabber.getFileSize();
        mEncryption = httpFileTransferJabber.getEncType();
        setEncState(mEncryption);
        messageViewHolder.fileLabel.setText(getFileLabel(fileName, fileSize));

		/* Must keep track of file transfer status as Android always request view redraw on
		listView scrolling, new message send or received */
        int status = getXferStatus();
        if (status == -1) {
            messageViewHolder.acceptButton.setVisibility(View.VISIBLE);
            messageViewHolder.rejectButton.setVisibility(View.VISIBLE);

            messageViewHolder.acceptButton.setOnClickListener(v -> {
                // set the download for global display parameter
                // mChatFragment.getChatListAdapter().setFileName(msgId, fileName);
                initHttpFileDownload(false);
            });

            messageViewHolder.retryButton.setOnClickListener(v -> initHttpFileDownload(false));

            messageViewHolder.rejectButton.setOnClickListener(
                    v -> updateView(FileTransferStatusChangeEvent.REFUSED, null));

            messageViewHolder.cancelButton.setOnClickListener(
                    v -> updateView(FileTransferStatusChangeEvent.CANCELED, null));

            updateXferFileViewState(FileTransferStatusChangeEvent.WAITING,
                    aTalkApp.getResString(R.string.xFile_FILE_TRANSFER_REQUEST_RECEIVED, mSender));

            // Do not auto retry if it had failed previously; otherwise ANR if multiple such items exist
            if (FileRecord.STATUS_FAILED == xferStatus) {
                updateView(FileTransferStatusChangeEvent.FAILED, dnLink);
            }
            else if ((fileSize == -1) || ConfigurationUtils.isAutoAcceptFile(fileSize)) {
                initHttpFileDownload(true);
            }
        }
        else {
            updateView(status, null);
        }
        return convertView;
    }

    /**
     * Handles file transfer status changes. Updates the interface to reflect the changes.
     */
    private void updateView(final int status, final String reason)
    {
        setXferStatus(status);
        String statusText = null;

        switch (status) {
            case FileTransferStatusChangeEvent.PREPARING:
                statusText = aTalkApp.getResString(R.string.xFile_FILE_TRANSFER_PREPARING, mSender);
                break;

            case FileTransferStatusChangeEvent.IN_PROGRESS:
                statusText = aTalkApp.getResString(R.string.xFile_FILE_RECEIVING_FROM, mSender);
                mChatFragment.addActiveFileTransfer(httpFileTransferJabber.getID(), httpFileTransferJabber, msgViewId);
                startProgressChecker();

                mFHS.updateFTStatusToDB(msgUuid, FileRecord.STATUS_IN_PROGRESS, null, mEncryption,
                        ChatMessage.MESSAGE_HTTP_FILE_DOWNLOAD);
                break;

            case FileTransferStatusChangeEvent.COMPLETED:
                statusText = aTalkApp.getResString(R.string.xFile_FILE_RECEIVE_COMPLETED, mSender);

                if (mXferFile == null) { // Android view redraw happen?
                    mXferFile = mChatFragment.getChatListAdapter().getFileName(msgViewId);
                }
                // Update the DB record status
                mFHS.updateFTStatusToDB(msgUuid, FileRecord.STATUS_COMPLETED, mXferFile.toString(),
                        mEncryption, ChatMessage.MESSAGE_FILE_TRANSFER_HISTORY);

                // Get chatFragment to refresh only when file transfer has Completed.
                // Otherwise cache msg will re-trigger the transfer request
                mChatFragment.getChatPanel().setCacheRefresh(true);
                break;

            case FileTransferStatusChangeEvent.FAILED:
                statusText = aTalkApp.getResString(R.string.xFile_FILE_RECEIVE_FAILED, mSender);
                if (!TextUtils.isEmpty(reason)) {
                    statusText += "\n" + reason;
                }
                mFHS.updateFTStatusToDB(msgUuid, FileRecord.STATUS_FAILED, null, mEncryption,
                        ChatMessage.MESSAGE_HTTP_FILE_DOWNLOAD);
                break;

            // local cancel the file download process
            case FileTransferStatusChangeEvent.CANCELED:
                statusText = aTalkApp.getResString(R.string.xFile_FILE_TRANSFER_CANCELED);

                mFHS.updateFTStatusToDB(msgUuid, FileRecord.STATUS_CANCELED, null, mEncryption,
                        ChatMessage.MESSAGE_HTTP_FILE_DOWNLOAD);
                break;

            // user reject the incoming http download
            case FileTransferStatusChangeEvent.REFUSED:
                statusText = aTalkApp.getResString(R.string.xFile_FILE_TRANSFER_REFUSED);
                // need to update status here as chatFragment statusListener is enabled for
                // fileTransfer and only after accept
                mFHS.updateFTStatusToDB(msgUuid, FileRecord.STATUS_REFUSED, null, mEncryption,
                        ChatMessage.MESSAGE_HTTP_FILE_DOWNLOAD);
                break;
        }
        updateXferFileViewState(status, statusText);

        // Must clean up all Download Manager parameters if passed IN_PROGRESS stage
        if ((FileTransferStatusChangeEvent.IN_PROGRESS != status) && (FileTransferStatusChangeEvent.PREPARING != status)) {
            stopProgressChecker();
            long jobId = getJobId(dnLink);
            if (jobId != -1) {
                previousDownloads.remove(jobId);
                downloadManager.remove(jobId);
            }

            // unregistered downloadReceiver
            // Receiver not registered exception - may occur if window is refreshed while download is in progress?
            if (downloadReceiver != null) {
                try {
                    aTalkApp.getGlobalContext().unregisterReceiver(downloadReceiver);
                } catch (IllegalArgumentException ie) {
                    Timber.w("Unregister download receiver exception: %s", ie.getMessage());
                }
                downloadReceiver = null;
            }
            // Timber.d("Download Manager for JobId: %s; File: %s (status: %s)", jobId, dnLink, status);
        }
    }

    /**
     * Handles status changes in file transfer.
     */
    public void statusChanged(FileTransferStatusChangeEvent event)
    {
        final FileTransfer fileTransfer = event.getFileTransfer();
        final int status = event.getNewStatus();
        final String reason = event.getReason();

        // Event thread - Must execute in UiThread to Update UI information
        runOnUiThread(() -> {
            updateView(status, reason);
            if (status == FileTransferStatusChangeEvent.COMPLETED
                    || status == FileTransferStatusChangeEvent.CANCELED
                    || status == FileTransferStatusChangeEvent.FAILED
                    || status == FileTransferStatusChangeEvent.REFUSED) {
                // must do this in UI, otherwise the status is not being updated to FileRecord
                fileTransfer.removeStatusListener(FileHttpDownloadConversation.this);
                // removeProgressListener();
            }
        });
    }

    /**
     * Creates the local file to save to.
     *
     * @return the local created file to save to.
     */
    private File createOutFile(File infile)
    {
        String fileName = infile.getName();
        String mimeType = FileBackend.getMimeType(getActivity(), Uri.fromFile(infile));
        setTransferFilePath(fileName, mimeType);

        // Change the file name to the name we would use on the local file system.
        if (!mXferFile.getName().equals(fileName)) {
            String label = getFileLabel(mXferFile.getName(), infile.length());
            messageViewHolder.fileLabel.setText(label);
        }
        return mXferFile;
    }

    /**
     * Returns the label to show on the progress bar.
     *
     * @param bytesString the bytes that have been transferred
     * @return the label to show on the progress bar
     */
    @Override
    protected String getProgressLabel(long bytesString)
    {
        return aTalkApp.getResString(R.string.service_gui_RECEIVED, bytesString);
    }

    // ********************************************************************************************//
    // Routines supporting File Download

    /**
     * Method fired when the chat message is clicked. {@inheritDoc}
     * Trigger from @see ChatFragment#
     *
     * @param checkFileSize check acceptable file Size limit before download if true
     */
    private void initHttpFileDownload(boolean checkFileSize)
    {
        String url;
        if (previousDownloads.contains(dnLink))
            return;

        if (downloadReceiver == null) {
            downloadReceiver = new DownloadReceiver();
            aTalkApp.getGlobalContext().registerReceiver(downloadReceiver,
                    new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE));
            httpFileTransferJabber.setDownloadReceiver(downloadReceiver);
        }

        if (dnLink.matches("^aesgcm:.*")) {
            AesgcmUrl aesgcmUrl = new AesgcmUrl(dnLink);
            url = aesgcmUrl.getDownloadUrl().toString();
        }
        else {
            url = dnLink;
        }
        // for testing only to display url in chat window
        // mChatFragment.getChatPanel().addMessage("", new Date(), IMessage.ENCODE_PLAIN, IMessage.ENCODE_PLAIN, aesgcmUrl.getAesgcmUrl());

        Uri uri = Uri.parse(url);
        if (fileSize == -1) {
            fileSize = queryFileSize(uri);
            messageViewHolder.fileLabel.setText(getFileLabel(fileName, fileSize));
            if (checkFileSize && !ConfigurationUtils.isAutoAcceptFile(fileSize)) {
                return;
            }
        }

        messageViewHolder.timeView.setText(mDate);
        messageViewHolder.fileStatus.setText(aTalkApp.getResString(R.string.xFile_FILE_TRANSFER_PREPARING, mSender));
        long jobId = download(uri);
        if (jobId > 0) {
            previousDownloads.put(jobId, dnLink);
            updateView(FileTransferStatusChangeEvent.IN_PROGRESS, null);
            Timber.d("Download Manager init HttpFileDownload Size: %s %s", fileSize, previousDownloads.toString());
        }
    }

    /**
     * Schedules media file download.
     */
    private long download(Uri uri)
    {
        DownloadManager.Request request = new DownloadManager.Request(uri);
        request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);

        try {
            File tmpFile = new File(FileBackend.getaTalkStore(FileBackend.TMP, true), fileName);
            request.setDestinationUri(Uri.fromFile(tmpFile));
            // request.addRequestHeader("User-Agent", getUserAgent());

            return downloadManager.enqueue(request);
        } catch (SecurityException e) {
            aTalkApp.showToastMessage(e.getMessage());
        } catch (Exception e) {
            aTalkApp.showToastMessage(R.string.service_gui_FILE_DOES_NOT_EXIST);
        }
        return -1;
    }

    /**
     * Query the http uploaded file size for auto download.
     */
    private long queryFileSize(Uri uri)
    {
        Timber.d("Download Manager file size query started");
        int size = -1;
        DownloadManager.Request request = new DownloadManager.Request(uri);
        long id = downloadManager.enqueue(request);
        DownloadManager.Query query = new DownloadManager.Query();
        query.setFilterById(id);

        // allow loop for 3 seconds for slow server. Server can return size == 0 ?
        int wait = 3;
        while ((wait-- > 0) && (size <= 0)) {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Timber.w("Download Manager query file size exception: %s", e.getMessage());
                return -1;
            }
            Cursor cursor = downloadManager.query(query);
            if (cursor.moveToFirst()) {
                size = cursor.getInt(cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES));
            }
            cursor.close();
        }
        Timber.d("Download Manager file size query end: %s (%s)", size, wait);
        return size;
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
        DownloadManager.Query query = new DownloadManager.Query();
        query.setFilterById(id);

        try (Cursor cursor = downloadManager.query(query)) {
            if (!cursor.moveToFirst())
                return DownloadManager.STATUS_FAILED;
            else {
                return cursor.getInt(cursor.getColumnIndex(DownloadManager.COLUMN_STATUS));
            }
        }
    }

    private class DownloadReceiver extends BroadcastReceiver
    {
        @Override
        public void onReceive(Context context, Intent intent)
        {
            // Fetching the download id received with the broadcast and
            // if the received broadcast is for our enqueued download by matching download id
            long lastDownloadId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1);
            int lastJobStatus = checkDownloadStatus(lastDownloadId);
            Timber.d("Download receiver %s: %s", lastDownloadId, lastJobStatus);

            if (previousDownloads.containsKey(lastDownloadId)) {
                if (lastJobStatus == DownloadManager.STATUS_SUCCESSFUL) {
                    String dnLink = previousDownloads.get(lastDownloadId);

                    Uri fileUri = downloadManager.getUriForDownloadedFile(lastDownloadId);
                    File inFile = new File(FilePathHelper.getPath(context, fileUri));

                    // update fileSize for progress bar update, in case it is still not updated by download Manager
                    fileSize = inFile.length();

                    if (inFile.exists()) {
                        // Create outFile
                        File outFile = createOutFile(inFile);

                        // OMEMO media file sharing - need to decrypt file content
                        if ((dnLink != null) && dnLink.matches("^aesgcm:.*")) {
                            try {
                                AesgcmUrl aesgcmUrl = new AesgcmUrl(dnLink);
                                Cipher decryptCipher = aesgcmUrl.getDecryptionCipher();

                                FileInputStream fis = new FileInputStream(inFile);
                                FileOutputStream outputStream = new FileOutputStream(outFile);
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

                                mXferFile = outFile;
                                updateView(FileTransferStatusChangeEvent.COMPLETED, null);
                            } catch (Exception e) {
                                updateView(FileTransferStatusChangeEvent.FAILED, "Failed to decrypt OMEMO media file: " + inFile);
                            }
                        }
                        else {
                            // Plain media file sharing; rename will move the infile to outfile dir.
                            if (inFile.renameTo(outFile)) {
                                mXferFile = outFile;
                                updateView(FileTransferStatusChangeEvent.COMPLETED, null);
                            }
                        }

                        // Timber.d("Downloaded fileSize: %s (%s)", outFile.length(), fileSize);
                        previousDownloads.remove(lastDownloadId);
                        // Remove lastDownloadId from downloadManager record and delete the tmp file
                        downloadManager.remove(lastDownloadId);
                    }
                }
                else if (lastJobStatus == DownloadManager.STATUS_FAILED) {
                    updateView(FileTransferStatusChangeEvent.FAILED, dnLink);
                }
            }
            else if (DownloadManager.STATUS_FAILED == lastJobStatus) {
                updateView(FileTransferStatusChangeEvent.FAILED, dnLink);
            }
        }
    }

    /**
     * Get the jobId for the given dnLink
     *
     * @param dnLink previously download link
     * @return jobId for the dnLink if available else -1
     */
    private long getJobId(String dnLink)
    {
        for (Map.Entry<Long, String> entry : previousDownloads.entrySet()) {
            if (entry.getValue().equals(dnLink)) {
                return (long) entry.getKey();
            }
        }
        return -1;
    }

    //=========================================================
    /*
     * Monitoring file download progress
     */
    private static final int PROGRESS_DELAY = 1000;

    // Maximum download idle time (60 seconds) before it is forced stop
    private static final int MAX_IDLE_TIME = 60;

    private boolean isProgressCheckerRunning = false;
    private final Handler handler = new Handler();

    private long previousProgress;
    private int waitTime;

    /**
     * Checks download progress.
     */
    private void checkProgress()
    {
        long lastDownloadId = getJobId(dnLink);
        int lastJobStatus = checkDownloadStatus(lastDownloadId);
        Timber.d("Downloading file jobId: %s; status: %s; dnProgress: %s (%s)", lastDownloadId, lastJobStatus,
                previousProgress, waitTime);

        // Terminate downloading task if failed or idleTime timeout
        if (lastJobStatus == DownloadManager.STATUS_FAILED || waitTime < 0) {
            File tmpFile = new File(FileBackend.getaTalkStore(FileBackend.TMP, true), fileName);
            Timber.d("Downloaded fileSize (failed): %s (%s)", tmpFile.length(), fileSize);

            updateView(FileTransferStatusChangeEvent.FAILED, null);
        }

        DownloadManager.Query query = new DownloadManager.Query();
        query.setFilterByStatus(~(DownloadManager.STATUS_FAILED | DownloadManager.STATUS_SUCCESSFUL));
        Cursor cursor = downloadManager.query(query);
        if (!cursor.moveToFirst()) {
            cursor.close();
            return;
        }
        do {
            fileSize = cursor.getLong(cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES));
            long progress = cursor.getLong(cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR));

            if (messageViewHolder.progressBar.isShown()) {
                messageViewHolder.fileLabel.setText(getFileLabel(fileName, fileSize));
                messageViewHolder.progressBar.setMax((int) fileSize);
            }

            if (progress <= previousProgress)
                waitTime--;
            else {
                waitTime = MAX_IDLE_TIME;
                previousProgress = progress;
            }

            onUploadProgress(progress, fileSize);
        } while (cursor.moveToNext());
        cursor.close();
    }

    /**
     * Starts watching download progress.
     *
     * This method is safe to call multiple times. Starting an already running progress checker is a no-op.
     */
    private void startProgressChecker()
    {
        if (!isProgressCheckerRunning) {
            isProgressCheckerRunning = true;
            waitTime = MAX_IDLE_TIME;
            previousProgress = -1;

            progressChecker.run();
        }
    }

    /**
     * Stops watching download progress.
     */
    private void stopProgressChecker()
    {
        isProgressCheckerRunning = false;
        handler.removeCallbacks(progressChecker);
    }

    /**
     * Checks download progress and updates status, then re-schedules itself.
     */
    private Runnable progressChecker = new Runnable()
    {
        @Override
        public void run()
        {
            if (isProgressCheckerRunning) {
                checkProgress();
                handler.postDelayed(this, PROGRESS_DELAY);
            }
        }
    };
}

