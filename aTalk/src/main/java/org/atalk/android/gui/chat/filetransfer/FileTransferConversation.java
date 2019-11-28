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
import android.graphics.BitmapFactory;
import android.view.*;
import android.widget.ImageView.ScaleType;
import android.widget.TextView;
import android.widget.Toast;

import net.java.sip.communicator.service.protocol.FileTransfer;
import net.java.sip.communicator.service.protocol.IMessage;
import net.java.sip.communicator.service.protocol.event.*;
import net.java.sip.communicator.util.ByteFormat;
import net.java.sip.communicator.util.GuiUtils;

import org.atalk.android.R;
import org.atalk.android.aTalkApp;
import org.atalk.android.gui.AndroidGUIActivator;
import org.atalk.android.gui.chat.ChatActivity;
import org.atalk.android.gui.chat.ChatFragment;
import org.atalk.service.osgi.OSGiFragment;
import org.jivesoftware.smackx.httpfileupload.UploadProgressListener;

import java.io.File;
import java.util.*;

/**
 * The <tt>FileTransferConversationComponent</tt> is the parent of all file conversation
 * components - for incoming, outgoing and history file transfers.
 *
 * @author Eng Chong Meng
 */
public abstract class FileTransferConversation extends OSGiFragment
        implements FileTransferProgressListener, UploadProgressListener
{
    /**
     * XEP-0264: File Transfer Thumbnails option
     */
    public static boolean FT_THUMBNAIL_ENABLE = true;

    /**
     * Image default width / height.
     */
    private static final int IMAGE_WIDTH = 64;
    private static final int IMAGE_HEIGHT = 64;

    private ChatActivity chatActivity;

    /**
     * The xfer file and directory.
     */
    protected File mXferFile;

    /**
     * The file transfer.
     */
    protected FileTransfer fileTransfer = null;

    /**
     * The size of the file to be transferred.
     */
    private long mTransferFileSize = 0;

    /**
     * The time of the last fileTransfer update.
     */
    private long mLastTimestamp = 0;

    /**
     * The number of bytes last transferred.
     */
    private long mLastTransferredBytes = 0;

    /**
     * The last calculated progress speed.
     */
    private long mTransferSpeed = 0;

    /**
     * The last estimated time.
     */
    private long mEstimatedTimeLeft = 0;

    /**
     * The file transfer index/position of the message in chatListAdapter
     */
    protected int msgId = 0;

    /**
     * The message Uuid  uniquely identify the record in the message database
     */
    protected String msgUuid;

    /*
     * mEntityJid can be Contact or ChatRoom
     */
    protected Object mEntityJid;

    /*
     * Transfer file encryption type
     */
    protected int mEncryption = IMessage.ENCRYPTION_NONE;

    /**
     * For Http file Upload and Download must set to true to update the message in the DB
     */
    protected boolean mUpdateDB = false;


    protected ChatFragment mChatFragment;
    protected ChatFragment.MessageViewHolder messageViewHolder;

    private final Vector<UploadProgressListener> uploadProgressListeners = new Vector<>();

    protected View inflateViewForFileTransfer(LayoutInflater inflater, ChatFragment.MessageViewHolder msgViewHolder,
            ViewGroup container, boolean init)
    {
        this.messageViewHolder = msgViewHolder;
        View convertView = null;

        if (init) {
            convertView = inflater.inflate(R.layout.chat_file_transfer_row, container, false);

            messageViewHolder.imageLabel = convertView.findViewById(R.id.button_file);
            messageViewHolder.cancelButton = convertView.findViewById(R.id.buttonCancel);
            messageViewHolder.retryButton = convertView.findViewById(R.id.button_retry);
            messageViewHolder.acceptButton = convertView.findViewById(R.id.button_accept);
            messageViewHolder.rejectButton = convertView.findViewById(R.id.button_reject);
            messageViewHolder.arrowDir = convertView.findViewById(R.id.filexferArrowView);
            messageViewHolder.stickerView = convertView.findViewById(R.id.sticker);

            messageViewHolder.titleLabel = convertView.findViewById(R.id.filexferTitleView);
            messageViewHolder.encStateView = convertView.findViewById(R.id.encFileStateView);
            messageViewHolder.fileLabel = convertView.findViewById(R.id.filexferFileNameView);
            messageViewHolder.viewFileXferError = convertView.findViewById(R.id.errorView);

            messageViewHolder.timeView = convertView.findViewById(R.id.xferTimeView);
            messageViewHolder.progressSpeedLabel = convertView.findViewById(R.id.file_progressSpeed);
            messageViewHolder.estimatedTimeLabel = convertView.findViewById(R.id.file_estTime);
            messageViewHolder.mProgressBar = convertView.findViewById(R.id.file_progressbar);
        }
        else {
            messageViewHolder.cancelButton.setVisibility(View.GONE);
            messageViewHolder.retryButton.setVisibility(View.GONE);
            messageViewHolder.acceptButton.setVisibility(View.GONE);
            messageViewHolder.rejectButton.setVisibility(View.GONE);
            hideProgressRelatedComponents();
        }

        View.OnClickListener onAction = getOnSetListener();
        messageViewHolder.cancelButton.setOnClickListener(onAction);

        messageViewHolder.stickerView.setOnClickListener(onAction);
        messageViewHolder.titleLabel.setTextColor(AndroidGUIActivator.getResources().getColor("black"));
        return convertView;
    }

    /**
     * Shows the given error message in the error area of this component.
     *
     * @param resId the message to show
     */
    protected void showErrorMessage(int resId)
    {
        String message = getResources().getString(resId);
        messageViewHolder.viewFileXferError.setText(message);
        messageViewHolder.viewFileXferError.setVisibility(TextView.VISIBLE);
    }

    /**
     * Set the file encryption status icon.
     *
     * @param encType the encryption
     */
    protected void setEncState(int encType)
    {
        if (IMessage.ENCRYPTION_OMEMO == encType)
            messageViewHolder.encStateView.setImageResource(R.drawable.encryption_omemo);
        else
            messageViewHolder.encStateView.setImageResource(R.drawable.encryption_none);
    }

    /**
     * Shows file thumbnail.
     *
     * @param thumbnail the thumbnail to show
     */
    protected void showThumbnail(byte[] thumbnail)
    {
        if (thumbnail != null && thumbnail.length > 0) {
            Bitmap thumbnailIcon = BitmapFactory.decodeByteArray(thumbnail, 0, thumbnail.length);
            int mWidth = thumbnailIcon.getWidth();
            int mHeight = thumbnailIcon.getHeight();

            if (mWidth > IMAGE_WIDTH || mHeight > IMAGE_HEIGHT) {
                messageViewHolder.imageLabel.setScaleType(ScaleType.FIT_CENTER);
            }
            else {
                messageViewHolder.imageLabel.setScaleType(ScaleType.CENTER);
            }
            messageViewHolder.imageLabel.setImageBitmap(thumbnailIcon);
            // messageViewHolder.stickerView.setImageBitmap(thumbnailIcon);
        }
    }

    /**
     * Sets the download file.
     *
     * @param file the file that has been downloaded or sent
     */
    protected void setCompletedDownloadFile(ChatFragment chatFragment, File file)
    {
        this.chatActivity = (ChatActivity) chatFragment.getActivity();
        mXferFile = file;

        final String toolTip = aTalkApp.getResString(R.string.service_gui_OPEN_FILE_FROM_IMAGE);
        messageViewHolder.imageLabel.setContentDescription(toolTip);
        View.OnClickListener onAction = getOnSetListener();
        messageViewHolder.imageLabel.setOnClickListener(onAction);

        messageViewHolder.imageLabel.setOnLongClickListener(v -> {
            Toast.makeText(v.getContext(), toolTip, Toast.LENGTH_SHORT).show();
            return true;
        });
    }

    /**
     * Sets the file transfer.
     *
     * @param fileTransfer the file transfer
     * @param transferFileSize the size of the transferred file Running in thread, not UI here
     */
    protected void setFileTransfer(FileTransfer fileTransfer, long transferFileSize)
    {
        this.fileTransfer = fileTransfer;
        this.mTransferFileSize = transferFileSize;
        fileTransfer.addProgressListener(this);
    }

    /**
     * Hides all progress related components.
     */
    protected void hideProgressRelatedComponents()
    {
        messageViewHolder.mProgressBar.setVisibility(View.GONE);
        messageViewHolder.progressSpeedLabel.setVisibility(View.GONE);
        messageViewHolder.estimatedTimeLabel.setVisibility(View.GONE);
    }

    /**
     * Remove file transfer progress listener
     */
    protected void removeProgressListener()
    {
        fileTransfer.removeProgressListener(this);
    }

    /**
     * Updates progress bar progress line every time a progress event has been received file transport
     * Note: total size of event.getProgress() is always lag behind event.getFileTransfer().getTransferredBytes();
     *
     * @param event the <tt>FileTransferProgressEvent</tt> that notifies us
     */
    public void progressChanged(final FileTransferProgressEvent event)
    {
        long transferredBytes = event.getFileTransfer().getTransferredBytes();
        long progressTimestamp = event.getTimestamp();

        updateProgress(transferredBytes, progressTimestamp);
    }

    /**
     * Callback for displaying http file upload progress.
     *
     * @param uploadedBytes the number of bytes uploaded at the moment
     * @param totalBytes the total number of bytes to be uploaded
     */
    @Override
    public void onUploadProgress(long uploadedBytes, long totalBytes)
    {
        updateProgress(uploadedBytes, System.currentTimeMillis());
    }

    private void updateProgress(long transferredBytes, long progressTimestamp)
    {
        // before file transfer start is -1
        if (transferredBytes < 0)
            return;

        final String bytesString = ByteFormat.format(transferredBytes);
        long byteTransferDelta = transferredBytes - mLastTransferredBytes;
        long timeElapsed = progressTimestamp - mLastTimestamp;

        // Calculate running average transfer speed in bytes/sec
        if (timeElapsed > 0)
            mTransferSpeed = (((byteTransferDelta * 1000) / timeElapsed) + mTransferSpeed) / 2;
        // Calculate  running average time left in sec
        if (mTransferSpeed > 0)
            mEstimatedTimeLeft = (((mTransferFileSize - transferredBytes) / mTransferSpeed) + mEstimatedTimeLeft) / 2;

        mLastTimestamp = progressTimestamp;
        mLastTransferredBytes = transferredBytes;

        runOnUiThread(() -> {
            // Need to do it here as it was found that Http File Upload completed before the progress Bar is even visible
            if (!messageViewHolder.mProgressBar.isShown()) {
                messageViewHolder.mProgressBar.setVisibility(View.VISIBLE);
                if (mXferFile != null)
                    messageViewHolder.mProgressBar.setMax((int) mXferFile.length());
            }
            // Note: progress bar can only handle int size (4-bytes: 2,147,483, 647);
            messageViewHolder.mProgressBar.setProgress((int) transferredBytes);

            if (mTransferSpeed > 0) {
                messageViewHolder.progressSpeedLabel.setVisibility(View.VISIBLE);
                messageViewHolder.progressSpeedLabel.setText(
                        aTalkApp.getResString(R.string.service_gui_SPEED, ByteFormat.format(mTransferSpeed), bytesString));
            }

            if (transferredBytes >= mTransferFileSize) {
                messageViewHolder.estimatedTimeLabel.setVisibility(View.GONE);
            }
            else if (mEstimatedTimeLeft > 0) {
                messageViewHolder.estimatedTimeLabel.setVisibility(View.VISIBLE);
                messageViewHolder.estimatedTimeLabel.setText(aTalkApp.getResString(R.string.service_gui_ESTIMATED_TIME,
                        GuiUtils.formatSeconds(mEstimatedTimeLeft * 1000)));
            }
        });
    }

    /**
     * Returns the string, showing information for the given file.
     *
     * @param file the file
     * @return the name of the given file
     */
    protected String getFileLabel(File file)
    {
        String fileName = file.getName();
        long fileSize = file.length();
        return getFileLabel(fileName, fileSize);
    }

    /**
     * Returns the string, showing information for the given file.
     *
     * @param fileName the name of the file
     * @param fileSize the size of the file
     * @return the name of the given file
     */
    protected String getFileLabel(String fileName, long fileSize)
    {
        String text = ByteFormat.format(fileSize);
        return fileName + " (" + text + ")";
    }

    /**
     * Returns the label to show on the progress bar.
     *
     * @param bytesString the bytes that have been transferred
     * @return the label to show on the progress bar
     */
    protected abstract String getProgressLabel(long bytesString);

    // dummy updateView for implementation
    protected void updateView(final int status)
    {
    }

    /**
     * @param status File transfer send status
     * @param jid Conact or ChatRoom for Http file upload service
     */
    public void setStatus(final int status, Object jid, int encType)
    {
        mEntityJid = jid;
        mEncryption = encType;
        mUpdateDB = (jid != null);

        setXferStatus(status);
        // Must execute in UiThread to Update UI information
        runOnUiThread(() -> {
            updateView(status);
        });
    }

    /**
     * Must update chatListAdapter file transfer status to actual for refresh when user scroll.
     * Only if the chatListAdapter is not destroyed
     *
     * @param status the file transfer new status
     */
    public void setXferStatus(int status)
    {
        if (mChatFragment.getChatListAdapter() != null)
            mChatFragment.getChatListAdapter().setXferStatus(msgId, status);
    }

    /**
     * Get the current status fo the file transfer
     *
     * @return the current status of the file transfer
     */
    protected int getXferStatus()
    {
        return mChatFragment.getChatListAdapter().getXferStatus(msgId);
    }

    /**
     * Handles buttons action events.
     * <p>
     * // @param evt
     * the <tt>ActionEvent</tt> that notified us
     */
    protected View.OnClickListener getOnSetListener()
    {
        return v -> {
            switch (v.getId()) {
                case R.id.button_file:
                case R.id.sticker:
                    if (chatActivity != null)
                        chatActivity.openDownloadable(mXferFile);
                    break;

                case R.id.buttonCancel:
                    messageViewHolder.retryButton.setVisibility(View.GONE);
                    messageViewHolder.cancelButton.setVisibility(View.GONE);
                    setXferStatus(FileTransferStatusChangeEvent.CANCELED);
                    if (fileTransfer != null)
                        fileTransfer.cancel();
                    break;
            }
        };
    }

    /**
     * @return the fileTransfer file
     */
    public File getFileName()
    {
        return mXferFile;
    }

    /**
     * Adds the given <tt>ScFileTransferListener</tt> that would listen for file transfer requests
     * created file transfers.
     *
     * @param listener the <tt>ScFileTransferListener</tt> to add
     */
    public void addUploadListener(UploadProgressListener listener)
    {
        synchronized (uploadProgressListeners) {
            if (!uploadProgressListeners.contains(listener)) {
                this.uploadProgressListeners.add(listener);
            }
        }
    }

    /**
     * Removes the given <tt>ScFileTransferListener</tt> that listens for file transfer requests and
     * created file transfers.
     *
     * @param listener the <tt>ScFileTransferListener</tt> to remove
     */
    public void removeUploadListener(UploadProgressListener listener)
    {
        synchronized (uploadProgressListeners) {
            this.uploadProgressListeners.remove(listener);
        }
    }

    /**
     * Callback for displaying upload progress.
     *
     * @param uploadedBytes the number of bytes uploaded at the moment
     * @param totalBytes the total number of bytes to be uploaded
     */
    // @Override
    public void onUploadStatus(long uploadedBytes, long totalBytes)
    {
        Iterator<UploadProgressListener> listeners;
        synchronized (uploadProgressListeners) {
            listeners = new ArrayList<>(uploadProgressListeners).iterator();
        }

        while (listeners.hasNext()) {
            UploadProgressListener listener = listeners.next();
            listener.onUploadProgress(uploadedBytes, totalBytes);
        }
    }
}
