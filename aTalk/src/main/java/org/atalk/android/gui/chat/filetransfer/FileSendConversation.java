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

import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.Date;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.transition.Transition;

import net.java.sip.communicator.impl.filehistory.FileHistoryServiceImpl;
import net.java.sip.communicator.impl.protocol.jabber.OutgoingFileSendEntityImpl;
import net.java.sip.communicator.impl.protocol.jabber.OutgoingFileTransferJabberImpl;
import net.java.sip.communicator.service.filehistory.FileRecord;
import net.java.sip.communicator.service.protocol.FileTransfer;
import net.java.sip.communicator.service.protocol.IMessage;
import net.java.sip.communicator.service.protocol.event.FileTransferCreatedEvent;
import net.java.sip.communicator.service.protocol.event.FileTransferStatusChangeEvent;
import net.java.sip.communicator.service.protocol.event.FileTransferStatusListener;
import net.java.sip.communicator.util.ConfigurationUtils;
import net.java.sip.communicator.util.GuiUtils;

import org.atalk.android.R;
import org.atalk.android.aTalkApp;
import org.atalk.android.gui.AppGUIActivator;
import org.atalk.android.gui.chat.ChatFragment;
import org.atalk.android.gui.chat.ChatMessage;
import org.atalk.persistance.FileBackend;

import timber.log.Timber;

/**
 * The <code>SendFileConversationComponent</code> is the component added in the chat conversation
 * when user sends a file either via legacy file transfer, jingleFileTransfer or httpFileUpload protocol.
 *
 * @author Eng Chong Meng
 */
public class FileSendConversation extends FileTransferConversation implements FileTransferStatusListener {
    /**
     * The thumbnail default width and height; Ensure ejabberd.yml shaper#normal is set to high value e.g. 50000
     * When the normal = 1000 in ejabberd.yml:
     * BobData response time is ~16s (jpeg=14784) and 39s (png=31326) with thumbnail size = 128 x 96.
     * Thumbnail size 64x64 => jpeg 5303 and takes ~7s
     */
    public static final int THUMBNAIL_WIDTH = 128;
    public static final int THUMBNAIL_HEIGHT = 96;

    private String mSendTo;
    private boolean mStickerMode;
    private FileHistoryServiceImpl mFHS;

    private int mChatType;
    private byte[] mThumbnail = null;

    /**
     * For Http file Upload must set to true to update the message in the DB
     */
    protected boolean mRecordCreated = false;


    private FileSendConversation(ChatFragment cPanel, String dir) {
        super(cPanel, dir);
    }

    /**
     * Creates a <code>SendFileConversationComponent</code> by specifying the parent chat panel, where
     * this component is added, the destination contact of the transfer and file to transfer.
     *
     * @param cPanel the parent chat panel, where this view component is added
     * @param sendTo the name of the destination contact
     * @param fileName the file to transfer
     */

    public static FileSendConversation newInstance(ChatFragment cPanel, String msgUuid, String sendTo,
            final String fileName, int chatType, boolean stickerMode) {
        FileSendConversation fragmentSFC = new FileSendConversation(cPanel, FileRecord.OUT);
        fragmentSFC.msgUuid = msgUuid;
        fragmentSFC.mSendTo = sendTo;
        fragmentSFC.mXferFile = new File(fileName);
        fragmentSFC.mTransferFileSize = fragmentSFC.mXferFile.length();
        fragmentSFC.mDate = GuiUtils.formatDateTime(null);
        fragmentSFC.mChatType = chatType;

        fragmentSFC.mStickerMode = stickerMode;
        fragmentSFC.mFHS = (FileHistoryServiceImpl) AppGUIActivator.getFileHistoryService();
        return fragmentSFC;
    }

    public View SendFileConversationForm(LayoutInflater inflater, ChatFragment.MessageViewHolder msgViewHolder,
            ViewGroup container, int id, boolean init) {
        View convertView = inflateViewForFileTransfer(inflater, msgViewHolder, container, init);

        msgViewId = id;
        updateFileViewInfo(mXferFile, false);
        messageViewHolder.retryButton.setOnClickListener(v -> {
            messageViewHolder.retryButton.setVisibility(View.GONE);
            messageViewHolder.cancelButton.setVisibility(View.GONE);
            sendFileTransferRequest(mThumbnail);
        });

        /*
         * Must check current file transfer status before send FileTransfer request; Android will redraw UI
         * on listView scrolling (manual or auto) e.g. when send multiple files, new message sent or received.
         * UI refresh during multiple files transfer will cause it to resend some of the fileSend requests.
         * Note: getXferStatus() will be UNKNOWN on first init of the FileSendConversion UI.
         */
        int status = getXferStatus();
        if (status == FileTransferStatusChangeEvent.UNKNOWN) {
            updateStatus(FileTransferStatusChangeEvent.PREPARING, null);
            sendFileWithThumbnail();
        }
        else {
            updateStatus(status, null);
        }
        return convertView;
    }

    /**
     * Handles file transfer status changes. Updates the UI and DB to reflect the changes.
     */
    @Override
    protected void updateStatus(final int status, final String reason) {
        String statusText = null;
        if (!mRecordCreated)
            createFileSendRecord();

        switch (status) {
            case FileTransferStatusChangeEvent.PREPARING:
                statusText = aTalkApp.getResString(R.string.file_transfer_preparing, mSendTo);
                break;

            case FileTransferStatusChangeEvent.WAITING:
                statusText = aTalkApp.getResString(R.string.file_transfer_waiting_acceptance, mSendTo);
                break;

            case FileTransferStatusChangeEvent.ACCEPT:
                statusText = aTalkApp.getResString(R.string.file_transfer_accepted);
                break;

            case FileTransferStatusChangeEvent.IN_PROGRESS:
                statusText = aTalkApp.getResString(R.string.file_sending_to, mSendTo);
                break;

            case FileTransferStatusChangeEvent.COMPLETED:
                statusText = aTalkApp.getResString(R.string.file_send_completed, mSendTo);
                break;

            // not offer to retry - smack replied as failed when recipient rejects on some devices
            case FileTransferStatusChangeEvent.FAILED:
                statusText = aTalkApp.getResString(R.string.file_transfer_send_error, mSendTo);
                if (!TextUtils.isEmpty(reason)) {
                    statusText += "\n" + reason;
                }
                break;

            case FileTransferStatusChangeEvent.CANCELED:
                // Inform remote user if sender canceled; not in standard legacy file xfer protocol event
                statusText = aTalkApp.getResString(R.string.file_transfer_canceled);
                if (!TextUtils.isEmpty(reason)) {
                    statusText += ": " + reason;
                }

                if (mFileTransfer instanceof OutgoingFileTransferJabberImpl) {
                    mChatFragment.getChatPanel().sendMessage(statusText,
                            IMessage.FLAG_REMOTE_ONLY | IMessage.ENCODE_PLAIN);
                }
                // Must invalid view; else the progress and cancel buttons are still visible when user has canceled.
                mChatFragment.getChatListAdapter().notifyDataSetInvalidated();
                break;

            case FileTransferStatusChangeEvent.DECLINED:
                statusText = aTalkApp.getResString(R.string.file_send_declined, mSendTo);
                break;
        }
        // Do here so newly created DB record is properly updated.
        updateFTStatus(status);
        updateXferFileViewState(status, statusText);
        mChatFragment.scrollToBottom();
    }

    /**
     * Create a new File send message/record for file transfer status tracking; File transport used is undefined.
     * Use OutgoingFileSendEntityImpl class, as recipient entityJid can either be contact or chatRoom
     */
    private void createFileSendRecord() {
        if (mEntityJid != null) {
            OutgoingFileSendEntityImpl fileTransfer = new OutgoingFileSendEntityImpl(mEntityJid, msgUuid, mXferFile.getPath());
            FileTransferCreatedEvent event = new FileTransferCreatedEvent(fileTransfer, new Date());
            mFHS.fileTransferCreated(event);
            mRecordCreated = true;
        }
    }

    /**
     * Update the file transfer status into the DB if the file record has been created i.e. mUpdateDB
     * is true; update also the msgCache (and ChatSession UI) to ensure the file send request will not
     * get trigger again. The msgCache record will be used for view display on chat session resume.
     *
     * @param status File transfer status
     */
    private void updateFTStatus(int status) {
        String fileName = mXferFile.getPath();
        Timber.d("File status change (Send): %s: %s", status, fileName);

        // Keep file transfer active when it failed for retry.
        int ftState = (status != FileTransferStatusChangeEvent.FAILED) && isFileTransferEnd(status) ?
                ChatMessage.MESSAGE_FILE_TRANSFER_HISTORY : ChatMessage.MESSAGE_FILE_TRANSFER_SEND;

        mFHS.updateFTStatusToDB(msgUuid, status, fileName, mEncryption, ftState);
        mChatFragment.updateFTStatus(msgUuid, status, fileName, mEncryption, ftState);
    }

    /**
     * Handles file transfer status changes. Updates the interface to reflect the changes.
     * Listens for changes in file transfers and update the DB record status if known.
     * Translate FileTransfer status to FileRecord status before updateFTStatus()
     *
     * @param event FileTransferStatusChangeEvent
     */
    @Override
    public void statusChanged(final FileTransferStatusChangeEvent event) {
        final FileTransfer fileTransfer = event.getFileTransfer();
        if (fileTransfer == null)
            return;

        final int status = event.getNewStatus();
        final String reason = event.getReason();

        // Must execute in UiThread to Update UI information
        runOnUiThread(() -> {
            updateStatus(status, reason);
            if (isFileTransferEnd(status)) {
                // Timber.e( "removeStatusListener: %s => %s", fileTransfer, FileSendConversation.this);
                // must update this in UI, otherwise the status is not being updated to FileRecord
                fileTransfer.removeStatusListener(FileSendConversation.this);
            }
        });
    }

    /**
     * Sets the <code>FileTransfer</code> object received, associated with the file transfer
     * process in this panel. Registered callback to receive all file transfer events.
     * Note: HttpFileUpload adds ProgressListener in httpFileUploadManager.uploadFile()
     *
     * @param fileTransfer the <code>FileTransfer</code> object associated with this panel
     */
    public void setTransportFileTransfer(FileTransfer fileTransfer) {
        // activate File History service to keep track of the progress - need more work if want to keep sending history.
        // fileTransfer.addStatusListener(new FileHistoryServiceImpl());

        mFileTransfer = fileTransfer;
        fileTransfer.addStatusListener(this);
        setFileTransfer(fileTransfer, mXferFile.length());
        runOnUiThread(() -> updateStatus(FileTransferStatusChangeEvent.WAITING, null));
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
        return aTalkApp.getResString(R.string.file_byte_sent, bytesString);
    }

    /**
     * Check to see if the file transfer is sending sticker
     *
     * @return true if sending sticker
     */
    public boolean isStickerMode() {
        return mStickerMode;
    }

    public byte[] getFileThumbnail() {
        return mThumbnail;
    }

    /**
     * Get the file thumbnail if applicable (disabled for OMEMO) and start the file transfer process.
     * Use asBitmap() to retrieve the thumbnail for smallest size;
     * The .as(byte[].class) returns a scale of the gif animation file (large size)
     */
    public void sendFileWithThumbnail() {
        if (ConfigurationUtils.isSendThumbnail()
                && (ChatFragment.MSGTYPE_OMEMO != mChatType)
                && !mStickerMode && FileBackend.isMediaFile(mXferFile)) {
            Glide.with(aTalkApp.getInstance())
                    .asBitmap()
                    .load(Uri.fromFile(mXferFile))
                    .into(new CustomTarget<Bitmap>(THUMBNAIL_WIDTH, THUMBNAIL_HEIGHT) {
                              @Override
                              public void onResourceReady(@NonNull Bitmap bitmap,
                                      @Nullable Transition<? super Bitmap> transition) {
                                  ByteArrayOutputStream stream = new ByteArrayOutputStream();
                                  bitmap.compress(Bitmap.CompressFormat.JPEG, 80, stream);
                                  byte[] byteData = stream.toByteArray();
                                  Timber.d("Thumbnail byteData size: %s", byteData.length);
                                  sendFileTransferRequest(byteData);
                              }

                              @Override
                              public void onLoadCleared(@Nullable Drawable placeholder) {
                                  Timber.d("Glide onLoadCleared received!!!");
                              }

                              @Override
                              public void onLoadFailed(@Nullable Drawable errorDrawable) {
                                  // load failed due to some reason, notify callers here about the same
                                  sendFileTransferRequest(null);
                              }
                          }
                    );
        }
        else {
            sendFileTransferRequest(null);
        }
    }

    /**
     * Send the file transfer offer to remote. Need to update view to WAITING here after
     * sendFile() step; setTransportFileTransfer#fileTransfer.addStatusListener()
     * setup is only call after the file offer initiated event.
     *
     * @param thumbnail file thumbnail or null (not video media file)
     */
    public void sendFileTransferRequest(byte[] thumbnail) {
        mThumbnail = thumbnail;
        mChatFragment.new SendFile(this, msgViewId).execute();
    }
}
