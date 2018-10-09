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
import net.java.sip.communicator.service.protocol.event.*;
import net.java.sip.communicator.util.ByteFormat;
import net.java.sip.communicator.util.GuiUtils;

import org.atalk.android.R;
import org.atalk.android.aTalkApp;
import org.atalk.android.gui.AndroidGUIActivator;
import org.atalk.android.gui.chat.ChatActivity;
import org.atalk.android.gui.chat.ChatFragment;
import org.atalk.service.osgi.OSGiFragment;

import java.io.File;

/**
 * The <tt>FileTransferConversationComponent</tt> is the parent of all file conversation
 * components - for incoming, outgoing and history file transfers.
 *
 * @author Eng Chong Meng
 */
public abstract class FileTransferConversation extends OSGiFragment implements FileTransferProgressListener
{
    /**
     * XEP-0264: File Transfer Thumbnails option
     */
    public static boolean FT_THUMBNAIL_ENABLE = true;

    /**
     * Image default width.
     */
    public static final int IMAGE_WIDTH = 64;

    /**
     * Image default height.
     */
    public static final int IMAGE_HEIGHT = 64;

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
     * The speed calculated delay.
     */
    private final static int SPEED_CALCULATE_DELAY = 5000;

    /**
     * The transferred file size.
     */
    private long transferredFileSize = 0;

    /**
     * The time of the last calculated transfer speed.
     */
    private long lastSpeedTimestamp = 0;

    /**
     * The last estimated time for the transfer.
     */
    private long lastEstimatedTimeTimestamp = 0;

    /**
     * The number of bytes last transferred.
     */
    private long lastTransferredBytes = 0;

    /**
     * The last calculated progress speed.
     */
    private long lastProgressSpeed;

    /**
     * The last estimated time.
     */
    private long lastEstimatedTime;

    /**
     * The file transfer index of the chatListAdapter
     */
    protected int msgId = 0;

    protected ChatFragment mChatFragment;
    protected ChatFragment.MessageViewHolder messageViewHolder;

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
            messageViewHolder.mProgressBar.setVisibility(View.GONE);
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
    protected void setCompletedDownloadFile(ChatFragment cPanel, File file)
    {
        this.chatActivity = (ChatActivity) cPanel.getActivity();
        mXferFile = file;
        final String toolTip = aTalkApp.getResString(R.string.service_gui_OPEN_FILE_FROM_IMAGE);
        messageViewHolder.imageLabel.setContentDescription(toolTip);
        View.OnClickListener onAction = getOnSetListener();
        messageViewHolder.imageLabel.setOnClickListener(onAction);

        messageViewHolder.imageLabel.setOnLongClickListener(new View.OnLongClickListener()
        {
            public boolean onLongClick(View v)
            {
                Toast.makeText(v.getContext(), toolTip, Toast.LENGTH_SHORT).show();
                return true;
            }
        });
    }

    /**
     * Sets the file transfer.
     *
     * @param fileTransfer the file transfer
     * @param transferredFileSize the size of the transferred file Running in thread, not UI here
     */
    protected void setFileTransfer(FileTransfer fileTransfer, long transferredFileSize)
    {
        this.fileTransfer = fileTransfer;
        this.transferredFileSize = transferredFileSize;
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
     * Updates progress bar progress line every time a progress event has been received.
     *
     * @param event the <tt>FileTransferProgressEvent</tt> that notifies us
     */
    public void progressChanged(final FileTransferProgressEvent event)
    {
        final int progressStatus = (int) event.getProgress();
        final long transferredBytes = event.getFileTransfer().getTransferredBytes();
        final long progressTimestamp = event.getTimestamp();

        final String bytesString = ByteFormat.format(transferredBytes);

        runOnUiThread(new Runnable()
        {
            @Override
            public void run()
            {
                messageViewHolder.mProgressBar.setProgress(progressStatus);

                if ((progressTimestamp - lastSpeedTimestamp) >= SPEED_CALCULATE_DELAY) {
                    lastProgressSpeed = Math.round(calculateProgressSpeed(transferredBytes));

                    lastSpeedTimestamp = progressTimestamp;
                    lastTransferredBytes = transferredBytes;
                }

                if ((progressTimestamp - lastEstimatedTimeTimestamp) >= SPEED_CALCULATE_DELAY
                        && lastProgressSpeed > 0) {
                    lastEstimatedTime = Math.round(calculateEstimatedTransferTime(lastProgressSpeed,
                            transferredFileSize - transferredBytes));
                    lastEstimatedTimeTimestamp = progressTimestamp;
                }

                if (lastProgressSpeed > 0) {
                    messageViewHolder.progressSpeedLabel.setVisibility(View.VISIBLE);
                    messageViewHolder.progressSpeedLabel.setText(
                            aTalkApp.getResString(R.string.service_gui_SPEED, ByteFormat.format(lastProgressSpeed), bytesString));
                }

                if (transferredBytes >= transferredFileSize) {
                    messageViewHolder.estimatedTimeLabel.setVisibility(View.GONE);
                }
                else if (lastEstimatedTime > 0) {
                    messageViewHolder.estimatedTimeLabel.setVisibility(View.VISIBLE);
                    messageViewHolder.estimatedTimeLabel.setText(aTalkApp.getResString(R.string.service_gui_ESTIMATED_TIME,
                            GuiUtils.formatSeconds(lastEstimatedTime * 1000)));
                }
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
    protected abstract String getProgressLabel(String bytesString);

    /**
     * Must update chatListAdapter file transfer status to actual for refresh when user scroll.
     *
     * @param xferStatus the file transfer new status
     */
    protected abstract void setXferStatus(int xferStatus);

    /**
     * Get the current status fo the file transfer
     * @return the current status of the file transfer
     */
    protected int getXferStatus()
    {
        return mChatFragment.getChatListAdapter().getXferStatus(msgId);
    }

    /**
     * Returns the speed of the transfer.
     *
     * @param transferredBytes the number of bytes that have been transferred
     * @return the speed of the transfer
     */
    private double calculateProgressSpeed(long transferredBytes)
    {
        // Bytes per second = bytes / SPEED_CALCULATE_DELAY milli-seconds * 1000.
        return (transferredBytes - lastTransferredBytes) / SPEED_CALCULATE_DELAY * 1000;
    }

    /**
     * Returns the estimated transfer time left.
     *
     * @param speed the speed of the transfer
     * @param bytesLeft the size of the file
     * @return the estimated transfer time left
     */
    private double calculateEstimatedTransferTime(double speed, long bytesLeft)
    {
        return bytesLeft / speed;
    }

    /**
     * Handles buttons action events.
     * <p>
     * // @param evt
     * the <tt>ActionEvent</tt> that notified us
     */
    protected View.OnClickListener getOnSetListener()
    {
        return new View.OnClickListener()
        {
            public void onClick(View v)
            {
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
            }
        };
    }

    /**
     * @return the fileTransfer file
     */
    public File getFileName() {
        return mXferFile;
    }
}
