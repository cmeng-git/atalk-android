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

import android.os.AsyncTask;
import android.text.TextUtils;
import android.view.*;

import net.java.sip.communicator.service.filehistory.FileRecord;
import net.java.sip.communicator.service.protocol.*;
import net.java.sip.communicator.service.protocol.event.*;
import net.java.sip.communicator.util.ConfigurationUtils;
import net.java.sip.communicator.util.GuiUtils;

import org.atalk.android.R;
import org.atalk.android.aTalkApp;
import org.atalk.android.gui.chat.ChatFragment;

import java.io.File;
import java.util.Date;

import timber.log.Timber;

/**
 * The <tt>ReceiveFileConversationComponent</tt> is the component shown in the conversation area
 * of the chat window to display a incoming file transfer.
 *
 * @author Eng Chong Meng
 */
public class FileReceiveConversation extends FileTransferConversation
        implements ScFileTransferListener, FileTransferStatusListener
{
    private IncomingFileTransferRequest fileTransferRequest;
    private OperationSetFileTransfer fileTransferOpSet;
    private String mSendTo;

    private FileReceiveConversation(ChatFragment cPanel, String dir)
    {
        super(cPanel, dir);
    }

    /**
     * Creates a <tt>ReceiveFileConversationComponent</tt>.
     *
     * @param cPanel the chat panel
     * @param opSet the <tt>OperationSetFileTransfer</tt>
     * @param request the <tt>IncomingFileTransferRequest</tt> associated with this component
     * @param date the received file date
     */
    // Constructor used by ChatFragment to start handle ReceiveFileTransferRequest
    public static FileReceiveConversation newInstance(ChatFragment cPanel, String sendTo,
            OperationSetFileTransfer opSet, IncomingFileTransferRequest request, final Date date)
    {
        FileReceiveConversation fragmentRFC = new FileReceiveConversation(cPanel, FileRecord.IN);
        fragmentRFC.mSendTo = sendTo;
        fragmentRFC.fileTransferOpSet = opSet;
        fragmentRFC.fileTransferRequest = request;
        fragmentRFC.mDate = GuiUtils.formatDateTime(date);

        // need to enable ScFileTransferListener for ReceiveFileConversion reject/cancellation.
        fragmentRFC.fileTransferOpSet.addFileTransferListener(fragmentRFC);
        return fragmentRFC;
    }

    public View ReceiveFileConversionForm(LayoutInflater inflater, ChatFragment.MessageViewHolder msgViewHolder,
            ViewGroup container, int id, boolean init)
    {
        msgViewId = id;
        View convertView = inflateViewForFileTransfer(inflater, msgViewHolder, container, init);
        messageViewHolder.stickerView.setImageDrawable(null);

        long downloadFileSize = fileTransferRequest.getFileSize();
        String fileLabel = getFileLabel(fileTransferRequest.getFileName(), downloadFileSize);
        messageViewHolder.fileLabel.setText(fileLabel);

		/* Must keep track of file transfer status as Android always request view redraw on
		listView scrolling, new message send or received */
        int status = getXferStatus();
        if (status == -1) {
            if (FileTransferConversation.FT_THUMBNAIL_ENABLE) {
                byte[] thumbnail = fileTransferRequest.getThumbnail();
                showThumbnail(thumbnail);
            }

            messageViewHolder.acceptButton.setOnClickListener(v -> {
                updateXferFileViewState(FileTransferStatusChangeEvent.PREPARING,
                        aTalkApp.getResString(R.string.xFile_FILE_TRANSFER_PREPARING, mSendTo));

                // set the download for global display parameter
                mChatFragment.getChatListAdapter().setFileName(msgViewId, mXferFile);
                new acceptFile(mXferFile).execute();
            });

            messageViewHolder.rejectButton.setOnClickListener(v -> {
                updateXferFileViewState(FileTransferStatusChangeEvent.REFUSED,
                        aTalkApp.getResString(R.string.xFile_FILE_TRANSFER_REFUSED));
                hideProgressRelatedComponents();

                try {
                    fileTransferRequest.rejectFile();
                } catch (OperationFailedException e) {
                    Timber.e("Reject file exception: %s", e.getMessage());
                }
                // need to update status here as chatFragment statusListener is enabled for
                // fileTransfer and only after accept
                setXferStatus(FileTransferStatusChangeEvent.CANCELED);
            });

            mXferFile = createOutFile(fileTransferRequest);
            updateXferFileViewState(FileTransferStatusChangeEvent.WAITING,
                    aTalkApp.getResString(R.string.xFile_FILE_TRANSFER_REQUEST_RECEIVED, mSendTo));

            if (ConfigurationUtils.isAutoAcceptFile(downloadFileSize)) {
                messageViewHolder.acceptButton.performClick();
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
        setEncState(mEncryption);
        String statusText = null;

        switch (status) {
            case FileTransferStatusChangeEvent.PREPARING:
                // hideProgressRelatedComponents();
                statusText = aTalkApp.getResString(R.string.xFile_FILE_TRANSFER_PREPARING, mSendTo);
                break;

            case FileTransferStatusChangeEvent.IN_PROGRESS:
                // cmeng: seems to only visible after the file transfer is completed.
//                if (!messageViewHolder.mProgressBar.isShown()) {
//                    messageViewHolder.mProgressBar.setVisibility(View.VISIBLE);
//                    messageViewHolder.mProgressBar.setMax((int) fileTransferRequest.getFileSize());
//                    // setFileTransfer(fileTransfer, fileTransferRequest.getFileSize());
//                }
                statusText = aTalkApp.getResString(R.string.xFile_FILE_RECEIVING_FROM, mSendTo);
                mChatFragment.getChatPanel().setCacheRefresh(true);
                break;

            case FileTransferStatusChangeEvent.COMPLETED:
                if (mXferFile == null) { // Android view redraw happen
                    mXferFile = mChatFragment.getChatListAdapter().getFileName(msgViewId);
                }
                statusText = aTalkApp.getResString(R.string.xFile_FILE_RECEIVE_COMPLETED, mSendTo);
                break;

            case FileTransferStatusChangeEvent.FAILED:
                // hideProgressRelatedComponents(); keep the status info for user view
                statusText = aTalkApp.getResString(R.string.xFile_FILE_RECEIVE_FAILED, mSendTo);
                if (!TextUtils.isEmpty(reason)) {
                    statusText += "\n" + reason;
                }
                break;

            case FileTransferStatusChangeEvent.CANCELED:
                statusText = aTalkApp.getResString(R.string.xFile_FILE_TRANSFER_CANCELED);
                break;

            case FileTransferStatusChangeEvent.REFUSED:
                // hideProgressRelatedComponents();
                statusText = aTalkApp.getResString(R.string.xFile_FILE_TRANSFER_REFUSED);
                break;
        }
        updateXferFileViewState(status, statusText);
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
                fileTransfer.removeStatusListener(FileReceiveConversation.this);
                // removeProgressListener();
            }
        });
    }

    /**
     * Creates the local file to download.
     *
     * @return the local created file to download.
     */
    private File createOutFile(IncomingFileTransferRequest fileTransferRequest)
    {
        String fileName = fileTransferRequest.getFileName();
        String mimeType = fileTransferRequest.getMimeType();
        setTransferFilePath(fileName, mimeType);

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
    private class acceptFile extends AsyncTask<Void, Void, String>
    {
        private final File dFile;
        private FileTransfer fileTransfer;

        private acceptFile(File mFile)
        {
            this.dFile = mFile;
        }

        @Override
        public void onPreExecute()
        {
        }

        @Override
        protected String doInBackground(Void... params)
        {
            fileTransfer = fileTransferRequest.acceptFile(dFile);
            mChatFragment.addActiveFileTransfer(fileTransfer.getID(), fileTransfer, msgViewId);

            // Remove previously added listener (no further required), that notify for request cancellations if any.
            fileTransferOpSet.removeFileTransferListener(FileReceiveConversation.this);
            fileTransfer.addStatusListener(FileReceiveConversation.this);
            return "";
        }

        @Override
        protected void onPostExecute(String result)
        {
            if (fileTransfer != null) {
                setFileTransfer(fileTransfer, fileTransferRequest.getFileSize());
            }
        }
    }

    /**
     * Called when a <tt>FileTransferCreatedEvent</tt> has been received from sendFile.
     *
     * @param event the <tt>FileTransferCreatedEvent</tt> containing the newly received file transfer and
     * other details.
     */
    public void fileTransferCreated(FileTransferCreatedEvent event)
    {
    }

    /**
     * Called when an <tt>IncomingFileTransferRequest</tt> has been canceled from the contact who send it.
     * Note: This is not a standard XMPP FileTransfer protocol - aTalk not implemented yet
     *
     * @param event the <tt>FileTransferRequestEvent</tt> containing the request which was canceled.
     */
    public void fileTransferRequestCanceled(FileTransferRequestEvent event)
    {
        final IncomingFileTransferRequest request = event.getRequest();
        // Event triggered - Must execute in UiThread to Update UI information
        runOnUiThread(() -> {
            if (request.equals(fileTransferRequest)) {
                updateXferFileViewState(FileTransferStatusChangeEvent.REFUSED,
                        aTalkApp.getResString(R.string.xFile_FILE_TRANSFER_CANCELED));
                fileTransferOpSet.removeFileTransferListener(FileReceiveConversation.this);
            }
        });
    }

    /**
     * Called when a new <tt>IncomingFileTransferRequest</tt> has been received.
     *
     * @param event the <tt>FileTransferRequestEvent</tt> containing the newly received request and other details.
     * @see FileTransferActivator#fileTransferRequestReceived(FileTransferRequestEvent)
     */
    public void fileTransferRequestReceived(FileTransferRequestEvent event)
    {
        // Event handled by FileTransferActivator - nothing to do here
    }

    /**
     * Called when an <tt>IncomingFileTransferRequest</tt> has been rejected.
     *
     * @param event the <tt>FileTransferRequestEvent</tt> containing the received request which was rejected.
     */
    public void fileTransferRequestRejected(FileTransferRequestEvent event)
    {
        final IncomingFileTransferRequest request = event.getRequest();
        // Event triggered - Must execute in UiThread to Update UI information
        runOnUiThread(() -> {
            if (request.equals(fileTransferRequest)) {
                updateXferFileViewState(FileTransferStatusChangeEvent.REFUSED,
                        aTalkApp.getResString(R.string.xFile_FILE_TRANSFER_REFUSED));
                fileTransferOpSet.removeFileTransferListener(FileReceiveConversation.this);
                hideProgressRelatedComponents();
            }
        });
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
}
