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

import android.net.Uri;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import java.io.File;
import java.util.Date;

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

/**
 * The <code>ReceiveFileConversationComponent</code> is the component shown in the conversation area
 * of the chat window to display a incoming file transfer.
 *
 * @author Eng Chong Meng
 */
public class FileHttpDownloadConversation extends FileTransferConversation
        implements FileTransferStatusListener {
    private HttpFileDownloadJabberImpl httpFileTransferJabber;
    private int xferStatus;
    private long fileSize;
    private String fileName;
    private String mSender;

    /* previousDownloads <DownloadJobId, DownloadFileMimeType Link> */
    // private final Hashtable<Long, String> mimeTypes = new Hashtable<>();

    /* DownloadManager Broadcast Receiver Handler */
    // private DownloadReceiver downloadReceiver = null;
    private FileHistoryServiceImpl mFHS;

    private FileHttpDownloadConversation(ChatFragment cPanel, String dir) {
        super(cPanel, dir);
    }

    /**
     * Creates a <code>FileHttpDownloadConversation</code>.
     *
     * @param cPanel the chat panel
     * @param sender the message <code>sender</code>
     * @param date the date
     */
    // Constructor used by ChatFragment to start handle ReceiveFileTransferRequest
    public static FileHttpDownloadConversation newInstance(ChatFragment cPanel, String sender,
            HttpFileDownloadJabberImpl httpFileTransferJabber, final Date date) {
        FileHttpDownloadConversation fragmentRFC = new FileHttpDownloadConversation(cPanel, FileRecord.IN);
        fragmentRFC.mSender = sender;
        fragmentRFC.httpFileTransferJabber = httpFileTransferJabber;
        fragmentRFC.mDate = GuiUtils.formatDateTime(date);
        fragmentRFC.msgUuid = httpFileTransferJabber.getID();
        fragmentRFC.xferStatus = httpFileTransferJabber.getStatus();
        fragmentRFC.mFHS = (FileHistoryServiceImpl) AndroidGUIActivator.getFileHistoryService();

        return fragmentRFC;
    }

    public View HttpFileDownloadConversionForm(LayoutInflater inflater, ChatFragment.MessageViewHolder msgViewHolder,
            ViewGroup container, int id, boolean init) {
        View convertView = inflateViewForFileTransfer(inflater, msgViewHolder, container, init);

        msgViewId = id;
        mFileTransfer = httpFileTransferJabber;
        mFileTransfer.addStatusListener(this);

        String dnLink = httpFileTransferJabber.getDnLink();
        mXferFile = createOutFile(httpFileTransferJabber.getLocalFile());
        fileName = httpFileTransferJabber.getFileName();
        fileSize = httpFileTransferJabber.getFileSize();
        mEncryption = httpFileTransferJabber.getEncryptionType();
        setEncState(mEncryption);

        messageViewHolder.stickerView.setImageDrawable(null);
        messageViewHolder.fileLabel.setText(getFileLabel(fileName, fileSize));

		/* Must keep track of file transfer status as Android always request view redraw on
		listView scrolling, new message send or received */
        int status = getXferStatus();
        if (status != FileTransferStatusChangeEvent.DECLINED
                && status != FileTransferStatusChangeEvent.COMPLETED) {
            // Must reset button image to fileIcon on new(); else reused view may contain an old thumbnail image
            messageViewHolder.fileIcon.setImageResource(R.drawable.file_icon);

            messageViewHolder.acceptButton.setOnClickListener(v -> startDownload());
            messageViewHolder.declineButton.setOnClickListener(
                    v -> updateView(FileTransferStatusChangeEvent.DECLINED, null)
            );

            updateXferFileViewState(FileTransferStatusChangeEvent.WAITING,
                    aTalkApp.getResString(R.string.file_transfer_request_received, mSender));

            // Do not auto retry if it had failed previously; otherwise ANR if multiple such items exist
            if (FileRecord.STATUS_FAILED == xferStatus) {
                updateView(FileTransferStatusChangeEvent.FAILED, dnLink);
            }
            else {
                doInit();
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
    @Override
    protected void updateView(final int status, final String reason) {
        // setXferStatus(status);
        String statusText = null;
        if (FileTransferStatusChangeEvent.COMPLETED != status) {
            updateFTStatus(status, null, ChatMessage.MESSAGE_HTTP_FILE_DOWNLOAD);
        }

        switch (status) {
            case FileTransferStatusChangeEvent.PREPARING:
                statusText = aTalkApp.getResString(R.string.file_transfer_preparing, mSender);
                break;

            case FileTransferStatusChangeEvent.IN_PROGRESS:
                statusText = aTalkApp.getResString(R.string.file_receive_from, mSender);
                mChatFragment.addActiveFileTransfer(httpFileTransferJabber.getID(), httpFileTransferJabber, msgViewId);
                break;

            case FileTransferStatusChangeEvent.COMPLETED:
                statusText = aTalkApp.getResString(R.string.file_receive_completed, mSender);
                if (mXferFile == null) { // Android view redraw happen?
                    mXferFile = mChatFragment.getChatListAdapter().getFileName(msgViewId);
                }
                // Update the DB record status
                updateFTStatus(status, mXferFile.toString(), ChatMessage.MESSAGE_FILE_TRANSFER_HISTORY);
                break;

            case FileTransferStatusChangeEvent.FAILED:
                statusText = aTalkApp.getResString(R.string.file_receive_failed, mSender);
                if (!TextUtils.isEmpty(reason)) {
                    statusText += "\n" + reason;
                }
                break;

            // local cancel the file download process
            case FileTransferStatusChangeEvent.CANCELED:
                statusText = aTalkApp.getResString(R.string.file_transfer_canceled);
                if (!TextUtils.isEmpty(reason)) {
                    statusText += ": " + reason;
                }
                break;

            // user reject the incoming http download
            case FileTransferStatusChangeEvent.DECLINED:
                statusText = aTalkApp.getResString(R.string.file_transfer_declined);
                // need to update status here as chatFragment statusListener is enabled for
                // fileTransfer and only after accept
                break;
        }
        updateXferFileViewState(status, statusText);
        mChatFragment.scrollToBottom();
    }

    /**
     * Update the file transfer status into the DB, and also the msgCache to ensure the file send request will not
     * get trigger again. The msgCache record will be used for DisplayMessage on chat session resume.
     *
     * @param status File transfer status
     * @param fileName the downloaded fileName
     * @param msgType File transfer type see ChatMessage MESSAGE_FILE_
     */
    private void updateFTStatus(int status, String fileName, int msgType) {
        mFHS.updateFTStatusToDB(msgUuid, status, fileName, mEncryption, msgType);
        mChatFragment.updateFTStatus(msgUuid, status, fileName, mEncryption, msgType);
    }

    /**
     * Handles status changes in file transfer.
     */
    public void statusChanged(FileTransferStatusChangeEvent event) {
        final FileTransfer fileTransfer = event.getFileTransfer();
        final int status = event.getNewStatus();
        final String reason = event.getReason();

        // Event thread - Must execute in UiThread to Update UI information
        runOnUiThread(() -> {
            updateView(status, reason);
            if (status == FileTransferStatusChangeEvent.COMPLETED
                    || status == FileTransferStatusChangeEvent.CANCELED
                    || status == FileTransferStatusChangeEvent.FAILED
                    || status == FileTransferStatusChangeEvent.DECLINED) {
                // must update this in UI, otherwise the status is not being updated to FileRecord
                fileTransfer.removeStatusListener(this);
            }
        });
    }

    /**
     * Creates the local file to save to.
     *
     * @return the local created file to save to.
     */
    public File createOutFile(File infile) {
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
     *
     * @return the label to show on the progress bar
     */
    @Override
    protected String getProgressLabel(long bytesString) {
        return aTalkApp.getResString(R.string.received_, bytesString);
    }

    /**
     * Init the HttpFileTransferJabberImpl, and retrieve server fileSize info.
     * Proceed to startDownload() if isAutoAcceptFile() is met.
     */
    private void doInit() {
        httpFileTransferJabber.initHttpFileDownload();
        fileSize = httpFileTransferJabber.getFileSize();
        setFileTransfer(httpFileTransferJabber, fileSize);
        messageViewHolder.fileLabel.setText(getFileLabel(fileName, fileSize));

        // Check for auto-download only if the file size is not zero
        if (fileSize > 0 && ConfigurationUtils.isAutoAcceptFile(fileSize)) {
            startDownload();
        }
    }

    /**
     * Start Http download and save the file into mXerFile.
     * Method fired when the file transfer chat message 'Accept' is clicked.
     * Or when the fileSize is within autoAccept fileSize limit.
     */
    private void startDownload() {
        httpFileTransferJabber.download(mXferFile);
    }
}
