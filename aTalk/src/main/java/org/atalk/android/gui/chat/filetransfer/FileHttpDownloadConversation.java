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
import net.java.sip.communicator.service.protocol.Message;
import net.java.sip.communicator.service.protocol.event.*;
import net.java.sip.communicator.util.ConfigurationUtils;

import org.atalk.android.*;
import org.atalk.android.gui.AndroidGUIActivator;
import org.atalk.android.gui.chat.ChatFragment;
import org.atalk.persistance.FileBackend;
import org.atalk.persistance.FilePathHelper;
import org.jivesoftware.smack.util.StringUtils;
import org.jivesoftware.smackx.httpfileupload.HttpFileUploadManager.AesgcmUrl;

import java.io.*;
import java.util.Date;
import java.util.Hashtable;

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

        String dnLink = httpFileTransferJabber.getDnLink();
        String fileName = httpFileTransferJabber.getFileName();
        long fileSize = httpFileTransferJabber.getFileSize();
        mEncryption = httpFileTransferJabber.getEncType();
        setEncState(mEncryption);
        String fileLabel = getFileLabel(fileName, fileSize);
        messageViewHolder.fileLabel.setText(fileLabel);

		/* Must keep track of file transfer status as Android always request view redraw on
		listView scrolling, new message send or received */
        int status = getXferStatus();
        if (status == -1) {
            messageViewHolder.acceptButton.setVisibility(View.VISIBLE);
            messageViewHolder.acceptButton.setOnClickListener(v -> {
                // set the download for global display parameter
                // mChatFragment.getChatListAdapter().setFileName(msgId, fileName);
                initHttpFileDownload(dnLink);
            });

            boolean isAutoAccept = (fileSize > 0) && (fileSize < ConfigurationUtils.getAutoAcceptFileSize());
            if (isAutoAccept)
                initHttpFileDownload(dnLink);

            messageViewHolder.rejectButton.setVisibility(View.VISIBLE);
            messageViewHolder.rejectButton.setOnClickListener(v -> {
                hideProgressRelatedComponents();
                messageViewHolder.titleLabel.setText(
                        aTalkApp.getResString(R.string.xFile_FILE_TRANSFER_REFUSED, mDate));
                messageViewHolder.acceptButton.setVisibility(View.GONE);
                messageViewHolder.rejectButton.setVisibility(View.GONE);
                // need to update status here as chatFragment statusListener is enabled for
                // fileTransfer and only after accept
                setXferStatus(FileTransferStatusChangeEvent.CANCELED);
            });
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
        setEncState(mEncryption);

        switch (status) {
            case FileTransferStatusChangeEvent.PREPARING:
                // hideProgressRelatedComponents();
                messageViewHolder.titleLabel.setText(
                        aTalkApp.getResString(R.string.xFile_FILE_TRANSFER_PREPARING, mDate, mSender));
                break;

            case FileTransferStatusChangeEvent.IN_PROGRESS:
//                if (!messageViewHolder.mProgressBar.isShown()) {
//                    messageViewHolder.mProgressBar.setVisibility(View.VISIBLE);
//                    // messageViewHolder.mProgressBar.setMax((int) fileTransferRequest.getFileSize());
//                    // setFileTransfer(fileTransfer, fileTransferRequest.getFileSize());
//                }
                messageViewHolder.cancelButton.setVisibility(View.VISIBLE);
                messageViewHolder.titleLabel.setText(
                        aTalkApp.getResString(R.string.xFile_FILE_RECEIVING_FROM, mDate, mSender));
                break;

            case FileTransferStatusChangeEvent.COMPLETED:
                if (mXferFile == null) { // Android view redraw happen
                    mXferFile = mChatFragment.getChatListAdapter().getFileName(msgId);
                }
                MyGlideApp.loadImage(messageViewHolder.stickerView, mXferFile, false);

                String fileLabel = getFileLabel(mXferFile.getName(), mXferFile.length());
                messageViewHolder.fileLabel.setText(fileLabel);

                setCompletedDownloadFile(mChatFragment, mXferFile);
                messageViewHolder.titleLabel.setText(
                        aTalkApp.getResString(R.string.xFile_FILE_RECEIVE_COMPLETED, mDate, mSender));
                messageViewHolder.cancelButton.setVisibility(View.GONE);
                messageViewHolder.rejectButton.setVisibility(View.GONE);

                // Update the DB record status
                mFHS.updateFTStatusToDB(msgUuid, FileRecord.COMPLETED, mXferFile.toString(), mEncryption);

                // Get chatFragment to refresh only when file transfer has Completed.
                // Otherwise cache msg will re-trigger the transfer request
                mChatFragment.getChatPanel().setCacheRefresh(true);
                break;

            case FileTransferStatusChangeEvent.FAILED:
                // hideProgressRelatedComponents(); keep the status info for user view
                messageViewHolder.titleLabel.setText(
                        aTalkApp.getResString(R.string.xFile_FILE_RECEIVE_FAILED, mDate, mSender, reason));
                messageViewHolder.cancelButton.setVisibility(View.GONE);
                bgAlert = true;
                mFHS.updateFTStatusToDB(msgUuid, FileRecord.FAILED, null, mEncryption);
                break;

            // remote cancel the file transfer
            case FileTransferStatusChangeEvent.CANCELED:
                messageViewHolder.titleLabel.setText(
                        aTalkApp.getResString(R.string.xFile_FILE_TRANSFER_CANCELED, mDate));
                messageViewHolder.cancelButton.setVisibility(View.GONE);
                bgAlert = true;
                mFHS.updateFTStatusToDB(msgUuid, FileRecord.CANCELED, null, mEncryption);
                break;

            case FileTransferStatusChangeEvent.REFUSED:
                // hideProgressRelatedComponents();
                messageViewHolder.titleLabel.setText(
                        aTalkApp.getResString(R.string.xFile_FILE_TRANSFER_REFUSED, mDate));
                messageViewHolder.cancelButton.setVisibility(View.GONE);
                bgAlert = true;
                break;
        }
        if (bgAlert) {
            messageViewHolder.titleLabel.setTextColor(
                    AndroidGUIActivator.getResources().getColor("red"));
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
        setXferStatus(status);

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
        String mimeType = FileBackend.getMimeType(mChatFragment.getActivity(), uri);

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
     * Called when a <tt>FileTransferCreatedEvent</tt> has been received.
     *
     * @param event the <tt>FileTransferCreatedEvent</tt> containing the newly received file transfer and
     * other details.
     */
    public void fileTransferCreated(FileTransferCreatedEvent event)
    {
    }

    /**
     * Called when an <tt>IncomingFileTransferRequest</tt> has been canceled from the contact who
     * sent it.
     *
     * @param event the <tt>FileTransferRequestEvent</tt> containing the request which was canceled.
     */
//    public void fileTransferRequestCanceled(FileTransferRequestEvent event)
//    {
//        final IncomingFileTransferRequest request = event.getRequest();
//        // Different thread - Must execute in UiThread to Update UI information
//        runOnUiThread(() -> {
//            if (request.equals(fileTransferRequest)) {
//                messageViewHolder.acceptButton.setVisibility(View.GONE);
//                messageViewHolder.rejectButton.setVisibility(View.GONE);
//                fileTransferOpSet.removeFileTransferListener(FileHttpDownloadConversation.this);
//                messageViewHolder.titleLabel.setText(
//                        aTalkApp.getResString(R.string.xFile_FILE_TRANSFER_CANCELED, mDate));
//            }
//        });
//    }

    /**
     * Called when an <tt>IncomingFileTransferRequest</tt> has been rejected.
     *
     * @param event the <tt>FileTransferRequestEvent</tt> containing the received request which was
     * rejected.
     */
//    public void fileTransferRequestRejected(FileTransferRequestEvent event)
//    {
//        final IncomingFileTransferRequest request = event.getRequest();
//        // Different thread - Must execute in UiThread to Update UI information
//        runOnUiThread(() -> {
//            if (request.equals(fileTransferRequest)) {
//                messageViewHolder.acceptButton.setVisibility(View.GONE);
//                messageViewHolder.rejectButton.setVisibility(View.GONE);
//                // fileTransferOpSet.removeFileTransferListener(FileHttpDownloadConversation.this);
//
//                hideProgressRelatedComponents();
//                // delete created mXferFile???
//                messageViewHolder.titleLabel.setText(
//                        aTalkApp.getResString(R.string.xFile_FILE_TRANSFER_REFUSED, mDate));
//            }
//        });
//    }

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
    private void initHttpFileDownload(String dnLink)
    {
        String url;
        if (previousDownloads.contains(dnLink))
            return;

        messageViewHolder.titleLabel.setText(
                aTalkApp.getResString(R.string.xFile_FILE_TRANSFER_PREPARING, mDate, mSender));
        messageViewHolder.acceptButton.setVisibility(View.GONE);
        messageViewHolder.rejectButton.setVisibility(View.GONE);
        setXferStatus(FileTransferStatusChangeEvent.IN_PROGRESS);

        if (downloadReceiver == null) {
            downloadReceiver = new DownloadReceiver();
            mChatFragment.getActivity().registerReceiver(downloadReceiver,
                    new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE));
        }

        if (dnLink.matches("^aesgcm:.*")) {
            AesgcmUrl aesgcmUrl = new AesgcmUrl(dnLink);
            url = aesgcmUrl.getDownloadUrl().toString();
        }
        else {
            url = dnLink;
        }
        Uri uri = Uri.parse(url);
        String fileName = uri.getLastPathSegment();

        Long jobId = download(uri, fileName);
        previousDownloads.put(jobId, dnLink);
    }

    /**
     * Schedules media file download.
     */
    private long download(Uri uri, String fileName)
    {
        DownloadManager.Request request = new DownloadManager.Request(uri);
        request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
        request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName);

        mFHS.updateFTStatusToDB(msgUuid, FileRecord.IN_PROGRESS, null, mEncryption);
        return downloadManager.enqueue(request);
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
                // mimeTypes.put(id, cursor.getString(cursor.getColumnIndex(DownloadManager.COLUMN_MEDIA_TYPE)));
                return cursor.getInt(cursor.getColumnIndex(DownloadManager.COLUMN_STATUS));
            }
        }
    }

    private class DownloadReceiver extends BroadcastReceiver
    {
        @Override
        public void onReceive(Context context, Intent intent)
        {
            String url;
            // Fetching the download id received with the broadcast and
            // if the received broadcast is for our enqueued download by matching download id
            long lastDownloadId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1);

            if (previousDownloads.containsKey(lastDownloadId)) {
                int lastJobStatus = checkDownloadStatus(lastDownloadId);
                if (lastJobStatus == DownloadManager.STATUS_SUCCESSFUL) {
                    String dnLink = previousDownloads.get(lastDownloadId);

                    Uri fileUri = downloadManager.getUriForDownloadedFile(lastDownloadId);
                    File inFile = new File(FilePathHelper.getPath(context, fileUri));

                    if (inFile.exists()) {
                        // Create outFile
                        // String mimeType = mimeTypes.get(lastDownloadId);
                        File outFile = createOutFile(inFile);

                        // OMEMO media file sharing
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
                        previousDownloads.remove(lastDownloadId);
                        downloadManager.remove(lastDownloadId);
                    }
                }
                else if (lastJobStatus == DownloadManager.STATUS_FAILED) {
                    updateView(FileTransferStatusChangeEvent.FAILED, "File downloading failed!");
                }

                // unregistered downloadReceiver
                if (downloadReceiver != null) {
                    mChatFragment.getActivity().unregisterReceiver(downloadReceiver);
                    downloadReceiver = null;
                }

//                else if (lastJobStatus != DownloadManager.STATUS_FAILED) {
//                    // Download is in progress or scheduled for retry
//                    AndroidUtils.showAlertDialog(aTalkApp.getGlobalContext(),
//                            aTalkApp.getResString(R.string.plugin_updatechecker_DIALOG_IN_PROGRESS_TITLE),
//                            aTalkApp.getResString(R.string.plugin_updatechecker_DIALOG_IN_PROGRESS));
//                }
            }
        }
    }

    //=========================================================
    /*
     * Monitoring file download progress
     */

    private static final int PROGRESS_DELAY = 1000;
    Handler handler = new Handler();
    private boolean isProgressCheckerRunning = false;

    // when the first download starts
    // startProgressChecker();

    // when the last download finishes or the Activity is destroyed
    // stopProgressChecker();

    /**
     * Checks download progress.
     */
    private void checkProgress()
    {
        DownloadManager.Query query = new DownloadManager.Query();
        query.setFilterByStatus(~(DownloadManager.STATUS_FAILED | DownloadManager.STATUS_SUCCESSFUL));
        Cursor cursor = downloadManager.query(query);
        if (!cursor.moveToFirst()) {
            cursor.close();
            return;
        }
        do {
            long reference = cursor.getLong(cursor.getColumnIndex(DownloadManager.COLUMN_ID));
            long progress = cursor.getLong(cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR));
            // do whatever you need with the progress
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
            progressChecker.run();
            isProgressCheckerRunning = true;
        }
    }

    /**
     * Stops watching download progress.
     */
    private void stopProgressChecker()
    {
        handler.removeCallbacks(progressChecker);
        isProgressCheckerRunning = false;
    }

    /**
     * Checks download progress and updates status, then re-schedules itself.
     */
    private Runnable progressChecker = new Runnable()
    {
        @Override
        public void run()
        {
            try {
                checkProgress();
                // manager reference not found. Commenting the code for compilation
                //manager.refresh();
            } finally {
                handler.postDelayed(progressChecker, PROGRESS_DELAY);
            }
        }
    };
}

