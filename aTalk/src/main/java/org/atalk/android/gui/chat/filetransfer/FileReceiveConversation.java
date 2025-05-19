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

import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import java.io.File;
import java.util.Date;
import java.util.concurrent.Executors;

import net.java.sip.communicator.impl.filehistory.FileHistoryServiceImpl;
import net.java.sip.communicator.service.filehistory.FileRecord;
import net.java.sip.communicator.service.protocol.FileTransfer;
import net.java.sip.communicator.service.protocol.IncomingFileTransferRequest;
import net.java.sip.communicator.service.protocol.OperationFailedException;
import net.java.sip.communicator.service.protocol.OperationSetFileTransfer;
import net.java.sip.communicator.service.protocol.event.FileTransferCreatedEvent;
import net.java.sip.communicator.service.protocol.event.FileTransferRequestEvent;
import net.java.sip.communicator.service.protocol.event.FileTransferStatusChangeEvent;
import net.java.sip.communicator.service.protocol.event.FileTransferStatusListener;
import net.java.sip.communicator.service.protocol.event.ScFileTransferListener;
import net.java.sip.communicator.util.ConfigurationUtils;
import net.java.sip.communicator.util.GuiUtils;

import org.atalk.android.R;
import org.atalk.android.aTalkApp;
import org.atalk.android.gui.AppGUIActivator;
import org.atalk.android.gui.chat.ChatFragment;
import org.atalk.android.gui.chat.ChatMessage;

import timber.log.Timber;

/**
 * The <code>ReceiveFileConversationComponent</code> is the component shown in the conversation area
 * of the chat window to display a incoming file transfer.
 * This UI is used by both the JingleFile and Legacy ByteStream incoming file.
 *
 * @author Eng Chong Meng
 */
public class FileReceiveConversation extends FileTransferConversation
        implements ScFileTransferListener, FileTransferStatusListener {
    private IncomingFileTransferRequest fileTransferRequest;
    private OperationSetFileTransfer fileTransferOpSet;
    private FileHistoryServiceImpl mFHS;
    private String mSender;

    private FileReceiveConversation(ChatFragment cPanel, String dir) {
        super(cPanel, dir);
    }

    /**
     * Creates a <code>ReceiveFileConversationComponent</code>.
     *
     * @param cPanel the chat panel
     * @param sender the file transfer sender
     * @param opSet the <code>OperationSetFileTransfer</code>
     * @param request the <code>IncomingFileTransferRequest</code> associated with this component
     * @param date the received file date
     */
    // Constructor used by ChatFragment to start handle ReceiveFileTransferRequest
    public static FileReceiveConversation newInstance(ChatFragment cPanel, String sender,
            OperationSetFileTransfer opSet, IncomingFileTransferRequest request, final Date date) {
        FileReceiveConversation fragmentRFC = new FileReceiveConversation(cPanel, FileRecord.IN);
        fragmentRFC.mSender = sender;
        fragmentRFC.fileTransferOpSet = opSet;
        fragmentRFC.fileTransferRequest = request;
        fragmentRFC.msgUuid = request.getId();
        fragmentRFC.mDate = GuiUtils.formatDateTime(date);
        fragmentRFC.mFHS = (FileHistoryServiceImpl) AppGUIActivator.getFileHistoryService();

        // need to enable ScFileTransferListener for FileReceiveConversation reject/cancellation.
        opSet.addFileTransferListener(fragmentRFC);
        return fragmentRFC;
    }

    public View ReceiveFileConversationForm(LayoutInflater inflater, ChatFragment.MessageViewHolder msgViewHolder,
            ViewGroup container, int id, boolean init) {
        msgViewId = id;
        View convertView = inflateViewForFileTransfer(inflater, msgViewHolder, container, init);
        messageViewHolder.stickerView.setImageDrawable(null);

        // ReceiveFileConversationForm get call again on UI refresh; create new file only if null.
        if (mXferFile == null)
            mXferFile = createOutFile(fileTransferRequest);
        mFileTransfer = fileTransferRequest.onPrepare(mXferFile);
        mFileTransfer.addStatusListener(this);
        mEncryption = fileTransferRequest.getEncryptionType();
        setEncryptionState(mEncryption);

        long downloadFileSize = fileTransferRequest.getFileSize();
        String fileLabel = getFileLabel(mXferFile.getName(), downloadFileSize);
        messageViewHolder.fileLabel.setText(fileLabel);

        // Must reset button image to fileIcon on new(); else reused view may contain an old thumbnail image
        messageViewHolder.fileIcon.setImageResource(R.drawable.file_icon);
        if (ConfigurationUtils.isSendThumbnail()) {
            byte[] thumbnail = fileTransferRequest.getThumbnail(this);
            showThumbnail(thumbnail);
        }

        // must init all buttons action as inflateViewForFileTransfer will change its references.
        messageViewHolder.acceptButton.setOnClickListener(v -> {
            updateXferFileViewState(FileTransferStatusChangeEvent.ACCEPT,
                    aTalkApp.getResString(R.string.file_transfer_accepted, mSender));

            // set the download for global display parameter
            mChatFragment.getChatListAdapter().setFileName(msgViewId, mXferFile);
            new AcceptFile().execute();
            updateStatus(FileTransferStatusChangeEvent.ACCEPT, null);
        });

        messageViewHolder.declineButton.setOnClickListener(v -> {
            updateXferFileViewState(FileTransferStatusChangeEvent.DECLINED,
                    aTalkApp.getResString(R.string.file_transfer_declined));
            hideProgressRelatedComponents();

            try {
                fileTransferRequest.declineFile();
            } catch (OperationFailedException e) {
                Timber.e("Decline file exception: %s", e.getMessage());
            }
            // need to update status here as chatFragment statusListener is enabled for
            // fileTransfer and only after accept
            updateStatus(FileTransferStatusChangeEvent.DECLINED, null);
        });

        /*
         * Must keep track of file transfer status from the cache as Android always request view
         * redraw on listView scrolling, new message send or received
         */
        int status = getXferStatus();
        // check for status UNKNOWN has problem. UI messed up and cannot receive file probably in multiple files transfer.
        if (status == FileTransferStatusChangeEvent.UNKNOWN) {
            status = FileTransferStatusChangeEvent.WAITING;
            updateStatus(status, null);
            checkAutoAccept(downloadFileSize);
        }
        else {
            updateStatus(status, null);
        }
        return convertView;
    }

    /**
     * Handles file transfer status changes. Updates the interface to reflect the changes.
     * Presently the file receive statusChanged event is only trigger by non-encrypted file transfer protocol
     * i.e. mEncryption = IMessage.ENCRYPTION_NONE
     */
    @Override
    protected void updateStatus(final int status, final String reason) {
        String statusText = null;

        switch (status) {
            case FileTransferStatusChangeEvent.PREPARING:
                // hideProgressRelatedComponents();
                statusText = aTalkApp.getResString(R.string.file_transfer_preparing, mSender);
                break;

            case FileTransferStatusChangeEvent.WAITING:
                statusText = aTalkApp.getResString(R.string.file_transfer_request_received, mSender);
                break;

            case FileTransferStatusChangeEvent.ACCEPT:
                statusText = aTalkApp.getResString(R.string.file_transfer_accepted);
                break;

            // Briefly visible for legacy Si file transfer; as transfer takes ~200ms to send 1.1 MB.
            // JFT encrypted takes ~7s to send/receive 1.1 MB.
            case FileTransferStatusChangeEvent.IN_PROGRESS:
                statusText = aTalkApp.getResString(R.string.file_receive_from, mSender);
                break;

            case FileTransferStatusChangeEvent.COMPLETED:
                statusText = aTalkApp.getResString(R.string.file_receive_completed, mSender);
                if (mXferFile == null) { // Android view redraw happen
                    mXferFile = mChatFragment.getChatListAdapter().getFileName(msgViewId);
                }
                break;

            case FileTransferStatusChangeEvent.FAILED:
                // hideProgressRelatedComponents(); keep the status info for user view
                statusText = aTalkApp.getResString(R.string.file_receive_failed, mSender);
                if (!TextUtils.isEmpty(reason)) {
                    statusText += "\n" + reason;
                }
                break;

            case FileTransferStatusChangeEvent.CANCELED:
                statusText = aTalkApp.getResString(R.string.file_transfer_canceled);
                break;

            case FileTransferStatusChangeEvent.DECLINED:
                // hideProgressRelatedComponents();
                statusText = aTalkApp.getResString(R.string.file_transfer_declined);
                break;
        }
        updateFTStatus(msgUuid, status);
        updateXferFileViewState(status, statusText);
        mChatFragment.scrollToBottom();
    }

    /**
     * Creates the local file to download.
     *
     * @return the local created file to download.
     */
    private File createOutFile(IncomingFileTransferRequest fileTransferRequest) {
        String fileName = fileTransferRequest.getFileName();
        String mimeType = fileTransferRequest.getMimeType();
        setTransferFilePath(fileName, mimeType);

        // Timber.d("Create Output File: %s (%s)", mXferFile, fileName);
        // Change the file name to the name we would use on the local file system.
        if (!mXferFile.getName().equals(fileName)) {
            String label = getFileLabel(mXferFile.getName(), fileTransferRequest.getFileSize());
            messageViewHolder.fileLabel.setText(label);
        }
        return mXferFile;
    }

    /**
     * Accepts the file in a new thread.
     */
    private class AcceptFile {
        public void execute() {
            Executors.newSingleThreadExecutor().execute(() -> {
                doInBackground();

                runOnUiThread(() -> {
                    if (mFileTransfer != null) {
                        setFileTransfer(mFileTransfer, fileTransferRequest.getFileSize());
                    }
                });
            });
        }

        protected void doInBackground() {
            fileTransferRequest.acceptFile();
            // Remove previously added listener (no further required), that notify for request cancellations if any.
            fileTransferOpSet.removeFileTransferListener(FileReceiveConversation.this);
            if (mFileTransfer != null) {
                mChatFragment.addActiveFileTransfer(mFileTransfer.getID(), mFileTransfer, msgViewId);
            }
        }
    }

    /**
     * Update the file transfer status into the DB, also the msgCache to ensure the file send request
     * will not get trigger again. The msgCache record will be used for view display on chat session resume.
     * Delete file with zero length; not to cluster directory with invalid files
     *
     * @param msgUuid The message UUID
     * @param status File transfer status
     */
    private void updateFTStatus(String msgUuid, int status) {
        String fileName = (mXferFile == null) ? "" : mXferFile.getPath();
        if (isFileTransferEnd(status)) {
            if (mXferFile != null && mXferFile.exists() && mXferFile.length() == 0 && mXferFile.delete()) {
                Timber.d("Deleted file with zero length: %s", mXferFile);
            }
        }
        Timber.d("File status change (Receive): %s: %s", status, mXferFile);

        int ftState = isFileTransferEnd(status) ?
                ChatMessage.MESSAGE_FILE_TRANSFER_HISTORY : ChatMessage.MESSAGE_FILE_TRANSFER_RECEIVE;
        mFHS.updateFTStatusToDB(msgUuid, status, fileName, mEncryption, ftState);
        mChatFragment.updateFTStatus(msgUuid, status, fileName, mEncryption, ftState);
    }

    /**
     * Handles file transfer status changes. Updates the interface to reflect the changes.
     * Listens for changes in file transfers and update the DB record status if known.
     * Translate FileTransfer status to FileRecord status before updateFTStatus()
     *
     * @param event the event containing information about the change
     */
    @Override
    public void statusChanged(FileTransferStatusChangeEvent event) {
        final FileTransfer fileTransfer = event.getFileTransfer();
        final int status = event.getNewStatus();
        final String reason = event.getReason();

        // Event thread - Must execute in UiThread to Update UI information
        runOnUiThread(() -> {
            updateStatus(status, reason);
            if (isFileTransferEnd(status)) {
                // must update this in UI, otherwise the status is not being updated to FileRecord
                fileTransfer.removeStatusListener(this);
            }
        });
    }

    /* ========== ScFileTransferListener class method implementation ========== */

    /**
     * Called when a new <code>IncomingFileTransferRequest</code> has been received. Too late to handle here.
     *
     * @param event the <code>FileTransferRequestEvent</code> containing the newly received request and other details.
     *
     * @see FileTransferActivator#fileTransferRequestReceived(FileTransferRequestEvent)
     * @see FileHistoryServiceImpl#fileTransferRequestReceived(FileTransferRequestEvent)
     */
    public void fileTransferRequestReceived(FileTransferRequestEvent event) {
        // Event is being handled by FileTransferActivator and FileHistoryServiceImpl
        // ScFileTransferListener is only being added after this event - nothing can do here.
    }

    /**
     * Called when a <code>FileTransferCreatedEvent</code> has been received from sendFile.
     *
     * @param event the <code>FileTransferCreatedEvent</code> containing the newly received
     * file transfer and other details.
     *
     * @see FileHistoryServiceImpl#fileTransferCreated(FileTransferCreatedEvent)
     */
    public void fileTransferCreated(FileTransferCreatedEvent event) {
        // Event is being handled by FileHistoryServiceImpl for both incoming, outgoing and
        // used by FileSendConversion#createHttpFileUploadRecord - so not doing anything here
    }

    /**
     * Called when an <code>IncomingFileTransferRequest</code> has been rejected.
     *
     * @param event the <code>FileTransferRequestEvent</code> containing the received request which was rejected.
     */
    public void fileTransferRequestRejected(FileTransferRequestEvent event) {
        final IncomingFileTransferRequest request = event.getRequest();
        updateFTStatus(request.getId(), FileTransferStatusChangeEvent.DECLINED);

        // Event triggered - Must execute in UiThread to Update UI information
        runOnUiThread(() -> {
            if (request.equals(fileTransferRequest)) {
                updateXferFileViewState(FileTransferStatusChangeEvent.DECLINED,
                        aTalkApp.getResString(R.string.file_transfer_declined));
                fileTransferOpSet.removeFileTransferListener(FileReceiveConversation.this);
                hideProgressRelatedComponents();
            }
        });
    }

    /**
     * Called when an <code>IncomingFileTransferRequest</code> has been canceled from the remote sender.
     * Note: This is not a standard XMPP Legacy FileTransfer protocol - aTalk yet to implement this
     *
     * @param event the <code>FileTransferRequestEvent</code> containing the request which was canceled.
     */
    public void fileTransferRequestCanceled(FileTransferRequestEvent event) {
        final IncomingFileTransferRequest request = event.getRequest();
        updateFTStatus(request.getId(), FileTransferStatusChangeEvent.CANCELED);

        // Event triggered - Must execute in UiThread to Update UI information
        runOnUiThread(() -> {
            if (request.equals(fileTransferRequest)) {
                updateXferFileViewState(FileTransferStatusChangeEvent.DECLINED,
                        aTalkApp.getResString(R.string.file_transfer_canceled));
                fileTransferOpSet.removeFileTransferListener(FileReceiveConversation.this);
            }
        });
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
}
