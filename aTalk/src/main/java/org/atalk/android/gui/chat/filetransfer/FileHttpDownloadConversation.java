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
import android.os.Environment;
import android.os.Handler;
import android.view.*;

import net.java.sip.communicator.impl.filehistory.FileHistoryServiceImpl;
import net.java.sip.communicator.impl.protocol.jabber.HttpFileDownloadJabberImpl;
import net.java.sip.communicator.service.filehistory.FileRecord;
import net.java.sip.communicator.service.protocol.FileTransfer;
import net.java.sip.communicator.service.protocol.event.FileTransferStatusChangeEvent;
import net.java.sip.communicator.service.protocol.event.FileTransferStatusListener;
import net.java.sip.communicator.util.ConfigurationUtils;

import org.atalk.android.*;
import org.atalk.android.gui.AndroidGUIActivator;
import org.atalk.android.gui.chat.ChatFragment;
import org.atalk.android.gui.chat.ChatMessage;
import org.atalk.persistance.FileBackend;
import org.atalk.persistance.FilePathHelper;
import org.jivesoftware.smack.util.StringUtils;
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
    private long fileSize;
    private String fileName;
    private String dnLink;
    private String mDate;
    private String mSender;

    /* previousDownloads <DownloadJobId, Download Link> */
    private final Hashtable<Long, String> previousDownloads = new Hashtable<>();

    /* previousDownloads <DownloadJobId, DownloadFileMimeType Link> */
    // private final Hashtable<Long, String> mimeTypes = new Hashtable<>();

    /* DownloadManager Broadcast Receiver Handler */
    private DownloadManager downloadManager;
    private DownloadReceiver downloadReceiver = null;
    private FileHistoryServiceImpl mFHS;

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
        FileHttpDownloadConversation fragmentRFC = new FileHttpDownloadConversation();
        fragmentRFC.mChatFragment = cPanel;
        fragmentRFC.mSender = sender;
        fragmentRFC.httpFileTransferJabber = httpFileTransferJabber;
        fragmentRFC.mDate = date.toString();
        fragmentRFC.msgUuid = httpFileTransferJabber.getID();
        fragmentRFC.downloadManager = aTalkApp.getDownloadManager();
        fragmentRFC.mFHS = (FileHistoryServiceImpl) AndroidGUIActivator.getFileHistoryService();

        return fragmentRFC;
    }

    public View HttpFileDownloadConversionForm(LayoutInflater inflater, ChatFragment.MessageViewHolder msgViewHolder,
            ViewGroup container, int id, boolean init)
    {
        msgId = id;
        View convertView = inflateViewForFileTransfer(inflater, msgViewHolder, container, init);
        messageViewHolder.arrowDir.setImageResource(R.drawable.filexferarrowin);
        messageViewHolder.stickerView.setImageDrawable(null);

        messageViewHolder.titleLabel.setText(
                aTalkApp.getResString(R.string.xFile_FILE_TRANSFER_REQUEST_RECEIVED, mDate, mSender));

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
                initHttpFileDownload();
            });

            boolean isAutoAccept = (fileSize > 0) && (fileSize < ConfigurationUtils.getAutoAcceptFileSize());
            if (isAutoAccept)
                initHttpFileDownload();

            messageViewHolder.retryButton.setOnClickListener(v -> initHttpFileDownload());

            messageViewHolder.rejectButton.setOnClickListener(
                    v -> updateView(FileTransferStatusChangeEvent.REFUSED, ""));

            messageViewHolder.cancelButton.setOnClickListener(
                    v -> updateView(FileTransferStatusChangeEvent.CANCELED, ""));
        }
        else {
            updateView(status, "");
        }
        return convertView;
    }

    /**
     * Handles file transfer status changes. Updates the interface to reflect the changes.
     */
    private void updateView(final int status, final String reason)
    {
        boolean bgAlert = false;
        setXferStatus(status);
        switch (status) {
            case FileTransferStatusChangeEvent.PREPARING:
                messageViewHolder.titleLabel.setText(
                        aTalkApp.getResString(R.string.xFile_FILE_TRANSFER_PREPARING, mDate, mSender));
                break;

            case FileTransferStatusChangeEvent.IN_PROGRESS:
                messageViewHolder.acceptButton.setVisibility(View.GONE);
                messageViewHolder.rejectButton.setVisibility(View.GONE);
                messageViewHolder.retryButton.setVisibility(View.GONE);

                messageViewHolder.cancelButton.setVisibility(View.VISIBLE);
                messageViewHolder.titleLabel.setText(
                        aTalkApp.getResString(R.string.xFile_FILE_RECEIVING_FROM, mDate, mSender));
                mChatFragment.addActiveFileTransfer(httpFileTransferJabber.getID(), httpFileTransferJabber, msgId);
                startProgressChecker();

                mFHS.updateFTStatusToDB(msgUuid, FileRecord.STATUS_IN_PROGRESS, null, mEncryption,
                        ChatMessage.MESSAGE_HTTP_FILE_DOWNLOAD);
                break;

            case FileTransferStatusChangeEvent.COMPLETED:
                if (mXferFile == null) { // Android view redraw happen
                    mXferFile = mChatFragment.getChatListAdapter().getFileName(msgId);
                }
                MyGlideApp.loadImage(messageViewHolder.stickerView, mXferFile, false);

                setCompletedDownloadFile(mChatFragment, mXferFile);
                messageViewHolder.cancelButton.setVisibility(View.GONE);
                messageViewHolder.rejectButton.setVisibility(View.GONE);
                messageViewHolder.titleLabel.setText(
                        aTalkApp.getResString(R.string.xFile_FILE_RECEIVE_COMPLETED, mDate, mSender));

                String fileLabel = getFileLabel(mXferFile.getName(), mXferFile.length());
                messageViewHolder.fileLabel.setText(fileLabel);

                // set to complete for progressBar
                onUploadProgress(fileSize, fileSize);

                // Update the DB record status
                mFHS.updateFTStatusToDB(msgUuid, FileRecord.STATUS_COMPLETED, mXferFile.toString(), mEncryption,
                        ChatMessage.MESSAGE_FILE_TRANSFER_HISTORY);

                // Get chatFragment to refresh only when file transfer has Completed.
                // Otherwise cache msg will re-trigger the transfer request
                mChatFragment.getChatPanel().setCacheRefresh(true);
                break;

            case FileTransferStatusChangeEvent.FAILED:
                // hideProgressRelatedComponents(); keep the status info for user view
                messageViewHolder.cancelButton.setVisibility(View.GONE);
                messageViewHolder.retryButton.setVisibility(View.VISIBLE);
                messageViewHolder.titleLabel.setText(
                        aTalkApp.getResString(R.string.xFile_FILE_RECEIVE_FAILED, mDate, mSender, reason));

                mFHS.updateFTStatusToDB(msgUuid, FileRecord.STATUS_FAILED, null, mEncryption,
                        ChatMessage.MESSAGE_HTTP_FILE_DOWNLOAD);
                bgAlert = true;
                break;

            // local cancel the file download process
            case FileTransferStatusChangeEvent.CANCELED:
                messageViewHolder.cancelButton.setVisibility(View.GONE);
                messageViewHolder.retryButton.setVisibility(View.VISIBLE);
                messageViewHolder.titleLabel.setText(
                        aTalkApp.getResString(R.string.xFile_FILE_TRANSFER_CANCELED, mDate));

                mFHS.updateFTStatusToDB(msgUuid, FileRecord.STATUS_CANCELED, null, mEncryption,
                        ChatMessage.MESSAGE_HTTP_FILE_DOWNLOAD);
                bgAlert = true;
                break;

            // user reject the incoming http download
            case FileTransferStatusChangeEvent.REFUSED:
                // hideProgressRelatedComponents();
                messageViewHolder.acceptButton.setVisibility(View.GONE);
                messageViewHolder.rejectButton.setVisibility(View.GONE);
                messageViewHolder.titleLabel.setText(
                        aTalkApp.getResString(R.string.xFile_FILE_TRANSFER_REFUSED, mDate));
                // need to update status here as chatFragment statusListener is enabled for
                // fileTransfer and only after accept
                mFHS.updateFTStatusToDB(msgUuid, FileRecord.STATUS_REFUSED, null, mEncryption,
                        ChatMessage.MESSAGE_HTTP_FILE_DOWNLOAD);
                bgAlert = true;
                break;
        }
        if (bgAlert) {
            messageViewHolder.titleLabel.setTextColor(
                    AndroidGUIActivator.getResources().getColor("red"));
        }

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
                    Timber.w("Unregistration download receiver exception: %s", ie.getMessage());
                }
                downloadReceiver = null;
            }
            Timber.d("Cleaning up Download Manager for JobId: %s; Received file size: %s ", jobId, fileSize);
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
    private File createOutFile(File file)
    {
        String fileName = file.getName();
        long fileSize = file.length();

        Uri uri = Uri.parse(file.getName());
        String mimeType = FileBackend.getMimeType(getActivity(), uri);

        String downloadPath = FileBackend.MEDIA_DOCUMENT;
        if (fileName.contains("voice-"))
            downloadPath = FileBackend.MEDIA_VOICE_RECEIVE;
        else if (!StringUtils.isNullOrEmpty(mimeType)) {
            downloadPath = FileBackend.MEDIA + File.separator + mimeType.split("/")[0];
        }

        File downloadDir = FileBackend.getaTalkStore(downloadPath);
        if (!downloadDir.exists() && !downloadDir.mkdirs()) {
            Timber.e("Could not create the download directory: %s", downloadDir.getAbsolutePath());
        }

        mXferFile = new File(downloadDir, fileName);
        // If a file with the given name already exists, add an index to the file name.
        int index = 0;
        int filenameLength = fileName.lastIndexOf(".");
        if (filenameLength == -1) {
            filenameLength = fileName.length();
        }
        while (mXferFile.exists()) {
            String newFileName = fileName.substring(0, filenameLength) + "-"
                    + ++index + fileName.substring(filenameLength);
            mXferFile = new File(downloadDir, newFileName);
        }

        // Change the file name to the name we would use on the local file system.
        if (!mXferFile.getName().equals(fileName)) {
            String label = getFileLabel(mXferFile.getName(), fileSize);
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
     */
    private void initHttpFileDownload()
    {
        String url;
        AesgcmUrl aesgcmUrl = null;

        if (previousDownloads.contains(dnLink))
            return;

        messageViewHolder.titleLabel.setText(
                aTalkApp.getResString(R.string.xFile_FILE_TRANSFER_PREPARING, mDate, mSender));

        if (downloadReceiver == null) {
            downloadReceiver = new DownloadReceiver();
            aTalkApp.getGlobalContext().registerReceiver(downloadReceiver,
                    new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE));
            httpFileTransferJabber.setDownloadReceiver(downloadReceiver);
        }

        if (dnLink.matches("^aesgcm:.*")) {
            aesgcmUrl = new AesgcmUrl(dnLink);
            url = aesgcmUrl.getDownloadUrl().toString();
        }
        else {
            url = dnLink;
        }
        // for testing only to display url in chat window
        // mChatFragment.getChatPanel().addMessage("", new Date(), IMessage.ENCODE_PLAIN, IMessage.ENCODE_PLAIN, aesgcmUrl.getAesgcmUrl());

        Uri uri = Uri.parse(url);
        Long jobId = download(uri);
        if (jobId > 0) {
            previousDownloads.put(jobId, dnLink);
            updateView(FileTransferStatusChangeEvent.IN_PROGRESS, "");
            Timber.d("Init HttpFileDownload: %s", previousDownloads.toString());
        }
    }

    /**
     * Schedules media file download.
     */
    private long download(Uri uri)
    {
        DownloadManager.Request request = new DownloadManager.Request(uri);
        request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
        request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName);
        // request.addRequestHeader("User-Agent", getUserAgent());

        try {
            return downloadManager.enqueue(request);
        } catch (SecurityException e) {
            aTalkApp.showToastMessage(e.getMessage());
        }
        return -1;
    }

//    private String getUserAgent() {
//        return aTalkApp.getResString(R.string.APPLICATION_NAME) + '/' + UpdateServiceImpl.getCurrentVersion();
//    }

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
                        if (dnLink.matches("^aesgcm:.*")) {
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
                                inFile.delete();
                                updateView(FileTransferStatusChangeEvent.COMPLETED, "");
                            } catch (Exception e) {
                                updateView(FileTransferStatusChangeEvent.FAILED, "Failed to decrypt OMEMO media file: " + inFile);
                            }
                        }
                        // Plain media file sharing; just do a direct copy
                        else {
                            inFile.renameTo(outFile);
                            updateView(FileTransferStatusChangeEvent.COMPLETED, "");
                        }

                        // Timber.d("Downloaded fileSize: %s (%s)", outFile.length(), fileSize);
                        previousDownloads.remove(lastDownloadId);
                        downloadManager.remove(lastDownloadId);
                    }
                }
                else if (lastJobStatus == DownloadManager.STATUS_FAILED) {
                    updateView(FileTransferStatusChangeEvent.FAILED, "File downloading failed!");
                }
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
        for (Map.Entry entry : previousDownloads.entrySet()) {
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
    private Handler handler = new Handler();

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
            File tmpFile = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), fileName);
            Timber.d("Downloaded fileSize (failed): %s (%s)", tmpFile.length(), fileSize);

            updateView(FileTransferStatusChangeEvent.FAILED, "Download unsuccessful!");
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

            if (messageViewHolder.mProgressBar.isShown()) {
                messageViewHolder.fileLabel.setText(getFileLabel(fileName, fileSize));
                messageViewHolder.mProgressBar.setMax((int) fileSize);
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

