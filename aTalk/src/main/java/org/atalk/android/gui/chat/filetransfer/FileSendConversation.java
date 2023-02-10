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

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.transition.Transition;

import net.java.sip.communicator.impl.filehistory.FileHistoryServiceImpl;
import net.java.sip.communicator.impl.protocol.jabber.HttpFileUploadJabberImpl;
import net.java.sip.communicator.impl.protocol.jabber.OutgoingFileTransferJabberImpl;
import net.java.sip.communicator.service.filehistory.FileRecord;
import net.java.sip.communicator.service.protocol.FileTransfer;
import net.java.sip.communicator.service.protocol.IMessage;
import net.java.sip.communicator.service.protocol.event.FileTransferCreatedEvent;
import net.java.sip.communicator.service.protocol.event.FileTransferStatusChangeEvent;
import net.java.sip.communicator.service.protocol.event.FileTransferStatusListener;
import net.java.sip.communicator.service.protocol.event.HttpFileTransferEvent;
import net.java.sip.communicator.util.ConfigurationUtils;
import net.java.sip.communicator.util.GuiUtils;

import org.atalk.android.R;
import org.atalk.android.aTalkApp;
import org.atalk.android.gui.AndroidGUIActivator;
import org.atalk.android.gui.chat.ChatFragment;
import org.atalk.android.gui.chat.ChatMessage;
import org.atalk.persistance.FileBackend;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.Date;

import timber.log.Timber;

/**
 * The <code>SendFileConversationComponent</code> is the component added in the chat conversation
 * when user sends a file either via legacy file transfer or httpFileUpload protocol.
 *
 * @author Eng Chong Meng
 */
public class FileSendConversation extends FileTransferConversation implements FileTransferStatusListener {
    /**
     * The thumbnail default width.
     */
    private static final int THUMBNAIL_WIDTH = 64;

    /**
     * The thumbnail default height.
     */
    private static final int THUMBNAIL_HEIGHT = 64;

    private String mSendTo;
    private boolean mStickerMode;
    private FileHistoryServiceImpl mFHS;

    private int mChatType;
    private byte[] mThumbnail = null;

    /**
     * For Http file Upload must set to true to update the message in the DB
     */
    protected boolean mUpdateDB = false;


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
        fragmentSFC.mFHS = (FileHistoryServiceImpl) AndroidGUIActivator.getFileHistoryService();
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

        /* Must track file transfer status as Android will redraw on listView scrolling, new message send or received */
        int status = getXferStatus();
        if (status != FileTransferStatusChangeEvent.CANCELED
                && status != FileTransferStatusChangeEvent.COMPLETED) {
            updateXferFileViewState(FileTransferStatusChangeEvent.PREPARING,
                    aTalkApp.getResString(R.string.xFile_FILE_TRANSFER_PREPARING, mSendTo));
            sendFileWithThumbnail();
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
        String statusText = null;
        updateFTStatus(status);

        switch (status) {
            case FileTransferStatusChangeEvent.PREPARING:
                statusText = aTalkApp.getResString(R.string.xFile_FILE_TRANSFER_PREPARING, mSendTo);
                break;

            case FileTransferStatusChangeEvent.WAITING:
                statusText = aTalkApp.getResString(R.string.xFile_FILE_WAITING_TO_ACCEPT, mSendTo);
                break;

            case FileTransferStatusChangeEvent.IN_PROGRESS:
                statusText = aTalkApp.getResString(R.string.xFile_FILE_SENDING_TO, mSendTo);
                // Transfer file record creation only after mEntityJid is known.
                if (mEntityJid != null && !mUpdateDB) {
                    createFileSendRecord();
                    mUpdateDB = true;
                    updateFTStatus(status);
                }
                break;

            case FileTransferStatusChangeEvent.COMPLETED:
                statusText = aTalkApp.getResString(R.string.xFile_FILE_SEND_COMPLETED, mSendTo);
                break;

            case FileTransferStatusChangeEvent.DECLINED:
                statusText = aTalkApp.getResString(R.string.xFile_FILE_SEND_DECLINED, mSendTo);
                break;

            // not offer to retry - smack replied as failed when recipient rejects on some devices
            case FileTransferStatusChangeEvent.FAILED:
                statusText = aTalkApp.getResString(R.string.xFile_FILE_UNABLE_TO_SEND, mSendTo);
                if (!TextUtils.isEmpty(reason)) {
                    statusText += "\n" + reason;
                }
                break;

            case FileTransferStatusChangeEvent.CANCELED:
                // Inform remote user if sender canceled; not in standard legacy file xfer protocol event
                statusText = aTalkApp.getResString(R.string.xFile_FILE_TRANSFER_CANCELED);
                if (!TextUtils.isEmpty(reason)) {
                    statusText += "\n" + reason;
                }

                if (mFileTransfer instanceof OutgoingFileTransferJabberImpl) {
                    mChatFragment.getChatPanel().sendMessage(statusText,
                            IMessage.FLAG_REMOTE_ONLY | IMessage.ENCODE_PLAIN);
                }
                break;
        }
        updateXferFileViewState(status, statusText);
        mChatFragment.scrollToBottom();
    }

    /**
     * Create a new File send message/record for file transfer status tracking;
     * Use HttpFileUploadJabberImpl class, as Object mEntityJid can either be contact or chatRoom
     */
    private void createFileSendRecord() {
        HttpFileUploadJabberImpl fileTransfer = new HttpFileUploadJabberImpl(mEntityJid, msgUuid, mXferFile.getPath());
        HttpFileTransferEvent event = new HttpFileTransferEvent(fileTransfer, new Date());
        mFHS.fileTransferCreated(event);
    }

    /**
     * Update the file transfer status into the DB if the file record has been created i.e. mUpdateDB
     * is true; update also the msgCache (and ChatSession UI) to ensure the file send request will not
     * get trigger again. The msgCache record will be used for view display on chat session resume.
     *
     * @param msgUuid The message UUID
     * @param status File transfer status
     */
    private void updateFTStatus(int status) {
        String fileName = mXferFile.getPath();
        if (mUpdateDB) {
            Timber.e("updateFTStatusToDB on status: %s; row count: %s", status,
                    mFHS.updateFTStatusToDB(msgUuid, status, fileName, mEncryption, ChatMessage.MESSAGE_FILE_TRANSFER_HISTORY));
        }
        mChatFragment.updateFTStatus(msgUuid, status, fileName, mEncryption, ChatMessage.MESSAGE_FILE_TRANSFER_HISTORY);
    }

    /**
     * Handles file transfer status changes. Updates the interface to reflect the changes.
     * Listens for changes in file transfers and update the DB record status if known.
     * Translate FileTransfer status to FileRecord status before updateFTStatus()
     *
     * @param event FileTransferStatusChangeEvent
     */
    public void statusChanged(final FileTransferStatusChangeEvent event) {
        final FileTransfer fileTransfer = event.getFileTransfer();
        if (fileTransfer == null)
            return;

        final int status = event.getNewStatus();
        final String reason = event.getReason();
        // Timber.e(new Exception(), "StatusChanged: %s => %s", status, reason);

        // Must execute in UiThread to Update UI information
        runOnUiThread(() -> {
            updateView(status, reason);
            if (status == FileTransferStatusChangeEvent.COMPLETED
                    || status == FileTransferStatusChangeEvent.CANCELED
                    || status == FileTransferStatusChangeEvent.FAILED
                    || status == FileTransferStatusChangeEvent.DECLINED) {
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
        runOnUiThread(() -> updateView(FileTransferStatusChangeEvent.WAITING, null));
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
        return aTalkApp.getResString(R.string.xFile_FILE_BYTE_SENT, bytesString);
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
     * Get the file thumbnail if applicable and start the file transfer process.
     * use sBitmap() to retrieve the thumbnail for smallest size;
     * The .as(byte[].class) returns a scale of the gif animation file (large size)
     */
    public void sendFileWithThumbnail() {
        if (ConfigurationUtils.isSendThumbnail()
                && (ChatFragment.MSGTYPE_OMEMO != mChatType)
                && !mStickerMode && FileBackend.isMediaFile(mXferFile)) {
            Glide.with(aTalkApp.getGlobalContext())
                    .asBitmap()
                    .load(Uri.fromFile(mXferFile))
                    .into(new CustomTarget<Bitmap>(THUMBNAIL_WIDTH, THUMBNAIL_HEIGHT) {
                              @Override
                              public void onResourceReady(@NonNull Bitmap bitmap,
                                      @Nullable Transition<? super Bitmap> transition) {
                                  ByteArrayOutputStream stream = new ByteArrayOutputStream();
                                  bitmap.compress(Bitmap.CompressFormat.JPEG, 100, stream);
                                  byte[] byteData = stream.toByteArray();

                                  Timber.d("ByteData Glide byteData: %s", byteData.length);
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
