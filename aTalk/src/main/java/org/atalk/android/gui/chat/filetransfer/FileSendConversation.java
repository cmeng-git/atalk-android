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

import android.view.*;

import net.java.sip.communicator.impl.filehistory.FileHistoryServiceImpl;
import net.java.sip.communicator.impl.protocol.jabber.HttpFileUploadJabberImpl;
import net.java.sip.communicator.service.filehistory.FileRecord;
import net.java.sip.communicator.service.protocol.FileTransfer;
import net.java.sip.communicator.service.protocol.IMessage;
import net.java.sip.communicator.service.protocol.event.*;
import net.java.sip.communicator.util.GuiUtils;

import org.atalk.android.R;
import org.atalk.android.aTalkApp;
import org.atalk.android.gui.AndroidGUIActivator;
import org.atalk.android.gui.chat.ChatFragment;

import java.io.File;
import java.util.Date;

/**
 * The <tt>SendFileConversationComponent</tt> is the component added in the chat conversation
 * when user sends a file either via legacy file transfer or httpFileUpload protocol.
 *
 * @author Eng Chong Meng
 */
public class FileSendConversation extends FileTransferConversation implements FileTransferStatusListener
{
    private String mSendTo;
    private boolean mStickerMode;
    private FileHistoryServiceImpl mFHS;

    private FileSendConversation(ChatFragment cPanel, String dir)
    {
        super(cPanel, dir);
    }

    /**
     * Creates a <tt>SendFileConversationComponent</tt> by specifying the parent chat panel, where
     * this component is added, the destination contact of the transfer and file to transfer.
     *
     * @param cPanel the parent chat panel, where this view component is added
     * @param sendTo the name of the destination contact
     * @param fileName the file to transfer
     */

    public static FileSendConversation newInstance(ChatFragment cPanel, String msgUuid, String sendTo,
            final String fileName, boolean stickerMode)
    {
        FileSendConversation fragmentSFC = new FileSendConversation(cPanel, FileRecord.OUT);
        fragmentSFC.msgUuid = msgUuid;
        fragmentSFC.mSendTo = sendTo;
        fragmentSFC.mXferFile = new File(fileName);
        fragmentSFC.mDate = GuiUtils.formatDateTime(null);

        fragmentSFC.mStickerMode = stickerMode;
        fragmentSFC.mFHS = (FileHistoryServiceImpl) AndroidGUIActivator.getFileHistoryService();
        return fragmentSFC;
    }

    public View SendFileConversationForm(LayoutInflater inflater, ChatFragment.MessageViewHolder msgViewHolder,
            ViewGroup container, int id, boolean init)
    {
        msgViewId = id;
        View convertView = inflateViewForFileTransfer(inflater, msgViewHolder, container, init);

        updateFileViewInfo(mXferFile, false);
        messageViewHolder.retryButton.setOnClickListener(v -> {
            messageViewHolder.retryButton.setVisibility(View.GONE);
            mChatFragment.new SendFile(FileSendConversation.this, msgViewId).execute();
        });

		/* Must track file transfer status as Android will request view redraw on listView
		scrolling, new message send or received */
        int status = getXferStatus();
        if (status == -1) {
            updateXferFileViewState(FileTransferStatusChangeEvent.PREPARING,
                    aTalkApp.getResString(R.string.xFile_FILE_WAITING_TO_ACCEPT, mSendTo));
            mChatFragment.new SendFile(FileSendConversation.this, msgViewId).execute();
        }
        else {
            updateView(status);
        }
        return convertView;
    }

    /**
     * Handles file transfer status changes. Updates the interface to reflect the changes.
     */
    protected void updateView(final int status)
    {
        setXferStatus(status);
        setEncState(mEncryption);
        String statusText = null;

        switch (status) {
            case FileTransferStatusChangeEvent.PREPARING:
                statusText = aTalkApp.getResString(R.string.xFile_FILE_TRANSFER_PREPARING, mSendTo);
                break;

            case FileTransferStatusChangeEvent.IN_PROGRESS:
                statusText = aTalkApp.getResString(R.string.xFile_FILE_SENDING_TO, mSendTo);
                if (mUpdateDB) {
                    setEncState(mEncryption);
                    createHttpFileUploadRecord();
                    // Must get chatFragment to refresh once a new file transfer has started.
                    // Otherwise cache msg will re-trigger the transfer
                    mChatFragment.getChatPanel().setCacheRefresh(true);
                }
                break;

            case FileTransferStatusChangeEvent.COMPLETED:
                statusText = aTalkApp.getResString(R.string.xFile_FILE_SEND_COMPLETED, mSendTo);
                if (mUpdateDB) {
                    mFHS.updateFTStatusToDB(msgUuid, FileRecord.STATUS_COMPLETED, mXferFile.toString(), mEncryption);
                }
                break;

            // not offer to retry - smack replied as failed when recipient rejects on some devices
            case FileTransferStatusChangeEvent.FAILED:
                statusText = aTalkApp.getResString(R.string.xFile_FILE_UNABLE_TO_SEND, mSendTo);
                if (mUpdateDB)
                    mFHS.updateFTStatusToDB(msgUuid, FileRecord.STATUS_FAILED, mXferFile.toString(), mEncryption);
                break;

            case FileTransferStatusChangeEvent.CANCELED:
                statusText = aTalkApp.getResString(R.string.xFile_FILE_TRANSFER_CANCELED);
                if (mUpdateDB)
                    mFHS.updateFTStatusToDB(msgUuid, FileRecord.STATUS_CANCELED, mXferFile.toString(), mEncryption);

                // Inform remote user; sender cancel not in standard file xfer protocol
                mChatFragment.getChatPanel().sendMessage(statusText,
                        IMessage.FLAG_REMOTE_ONLY | IMessage.ENCODE_PLAIN);
                break;

            case FileTransferStatusChangeEvent.REFUSED:
                statusText = aTalkApp.getResString(R.string.xFile_FILE_SEND_REFUSED, mSendTo);
                break;
        }
        updateXferFileViewState(status, statusText);
    }

    /**
     * Handles file transfer status changes. Updates the interface to reflect the changes.
     */
    public void statusChanged(final FileTransferStatusChangeEvent event)
    {
        final FileTransfer fileTransfer = event.getFileTransfer();
        final int status = event.getNewStatus();

        // Presently statusChanged event is only trigger by non-encrypted file transfer protocol
        mEncryption = IMessage.ENCRYPTION_NONE;

        // Must execute in UiThread to Update UI information
        runOnUiThread(() -> {
            updateView(status);
            if (status == FileTransferStatusChangeEvent.COMPLETED
                    || status == FileTransferStatusChangeEvent.CANCELED
                    || status == FileTransferStatusChangeEvent.FAILED
                    || status == FileTransferStatusChangeEvent.REFUSED) {
                // must do this in UI, otherwise the status is not being updated to FileRecord
                if (fileTransfer != null)
                    fileTransfer.removeStatusListener(FileSendConversation.this);
                // removeProgressListener();
            }
        });
    }

    /**
     * Sets the <tt>FileTransfer</tt> object received from the protocol and corresponding to the
     * file transfer process associated with this panel.
     *
     * @param fileTransfer the <tt>FileTransfer</tt> object associated with this panel
     */
    public void setProtocolFileTransfer(FileTransfer fileTransfer)
    {
        // activate File History service to keep track of the progress - need more work if want to keep sending history.
        // fileTransfer.addStatusListener(new FileHistoryServiceImpl());

        this.fileTransfer = fileTransfer;
        fileTransfer.addStatusListener(this);
        this.setFileTransfer(fileTransfer, mXferFile.length());
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
        return aTalkApp.getResString(R.string.xFile_FILE_BYTE_SENT, bytesString);
    }

    private void createHttpFileUploadRecord()
    {
        HttpFileUploadJabberImpl fileTransfer = new HttpFileUploadJabberImpl(mEntityJid, msgUuid, mXferFile.getPath());
        HttpFileTransferEvent event = new HttpFileTransferEvent(fileTransfer, new Date());
        mFHS.fileTransferCreated(event);
    }

    /**
     * Check to see if the file transfe is sending sticker
     *
     * @return true if sending sticker
     */
    public boolean isStickerMode()
    {
        return mStickerMode;
    }
}
