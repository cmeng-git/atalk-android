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

import static org.atalk.persistance.FileBackend.getMimeType;

import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.AnimationDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnLongClickListener;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ImageView.ScaleType;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.gif.GifDrawable;
import com.bumptech.glide.request.target.CustomViewTarget;
import com.bumptech.glide.request.transition.Transition;

import net.java.sip.communicator.impl.protocol.jabber.ProtocolProviderServiceJabberImpl;
import net.java.sip.communicator.service.filehistory.FileRecord;
import net.java.sip.communicator.service.protocol.FileTransfer;
import net.java.sip.communicator.service.protocol.IMessage;
import net.java.sip.communicator.service.protocol.event.FileTransferProgressEvent;
import net.java.sip.communicator.service.protocol.event.FileTransferProgressListener;
import net.java.sip.communicator.service.protocol.event.FileTransferStatusChangeEvent;
import net.java.sip.communicator.util.ByteFormat;
import net.java.sip.communicator.util.GuiUtils;
import net.java.sip.communicator.util.UtilActivator;

import org.atalk.android.MyGlideApp;
import org.atalk.android.R;
import org.atalk.android.aTalkApp;
import org.atalk.android.gui.chat.ChatActivity;
import org.atalk.android.gui.chat.ChatFragment;
import org.atalk.android.plugin.audioservice.AudioBgService;
import org.atalk.persistance.FileBackend;
import org.atalk.service.osgi.OSGiFragment;
import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.util.StringUtils;
import org.jivesoftware.smackx.httpfileupload.UploadProgressListener;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Vector;

import timber.log.Timber;

/**
 * The <code>FileTransferConversationComponent</code> is the parent of all
 * file conversation components - for incoming, outgoing and history file transfers.
 *
 * The smack reply timer is extended to 10 sec in file sharing info exchanges e.g. IBB takes > 5sec
 *
 * @author Eng Chong Meng
 */
public abstract class FileTransferConversation extends OSGiFragment
        implements OnClickListener, OnLongClickListener, FileTransferProgressListener,
        UploadProgressListener, SeekBar.OnSeekBarChangeListener {
    /**
     * Image default width / height.
     */
    private static final int IMAGE_WIDTH = 64;
    private static final int IMAGE_HEIGHT = 64;

    /**
     * The xfer file full path for saving the received file.
     */
    protected File mXferFile;

    protected Uri mUri;

    protected String mDate;

    /**
     * The file transfer.
     */
    protected FileTransfer mFileTransfer = null;

    /**
     * The size of the file to be transferred.
     */
    protected long mTransferFileSize = 0;

    /**
     * The time of the last fileTransfer update.
     */
    private long mLastTimestamp = -1;

    /**
     * The number of bytes last transferred.
     */
    private long mLastTransferredBytes = 0;

    /**
     * The last calculated progress speed.
     */
    private long mTransferSpeedAverage = 0;

    /**
     * The last estimated time.
     */
    private long mEstimatedTimeLeft = -1;

    /**
     * The state of a player where playback is stopped
     */
    private static final int STATE_STOP = 0;
    /**
     * The state of a player when it's created
     */
    private static final int STATE_IDLE = 1;
    /**
     * The state of a player where playback is paused
     */
    private static final int STATE_PAUSE = 2;
    /**
     * The state of a player that is actively playing
     */
    private static final int STATE_PLAY = 3;

    private static final Map<Uri, BroadcastReceiver> bcRegisters = new HashMap<>();

    private int playerState = STATE_STOP;

    private AnimationDrawable mPlayerAnimate;

    private boolean isMediaAudio = false;
    private String mimeType = null;

    private final String mDir;

    private boolean isSeeking = false;
    private int positionSeek;

    /**
     * The file transfer index/position of the message in chatListAdapter
     */
    protected int msgViewId = 0;

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

    protected ChatFragment mChatFragment;
    protected ChatActivity mChatActivity;
    protected XMPPConnection mConnection;

    protected ChatFragment.MessageViewHolder messageViewHolder;

    private final Vector<UploadProgressListener> uploadProgressListeners = new Vector<>();

    protected FileTransferConversation(ChatFragment cPanel, String dir) {
        mChatFragment = cPanel;
        mChatActivity = (ChatActivity) cPanel.getActivity();
        mConnection = cPanel.getChatPanel().getProtocolProvider().getConnection();
        mDir = dir;
    }

    protected View inflateViewForFileTransfer(LayoutInflater inflater, ChatFragment.MessageViewHolder msgViewHolder,
            ViewGroup container, boolean init) {
        this.messageViewHolder = msgViewHolder;
        View convertView = null;

        if (init) {
            if (FileRecord.IN.equals(mDir))
                convertView = inflater.inflate(R.layout.chat_file_transfer_in_row, container, false);
            else
                convertView = inflater.inflate(R.layout.chat_file_transfer_out_row, container, false);

            messageViewHolder.fileIcon = convertView.findViewById(R.id.button_file);
            messageViewHolder.stickerView = convertView.findViewById(R.id.sticker);

            messageViewHolder.playerView = convertView.findViewById(R.id.playerView);
            messageViewHolder.fileAudio = convertView.findViewById(R.id.filename_audio);
            messageViewHolder.playbackPlay = convertView.findViewById(R.id.playback_play);
            messageViewHolder.playbackPosition = convertView.findViewById(R.id.playback_position);
            messageViewHolder.playbackDuration = convertView.findViewById(R.id.playback_duration);
            messageViewHolder.playbackSeekBar = convertView.findViewById(R.id.playback_seekbar);

            messageViewHolder.fileLabel = convertView.findViewById(R.id.filexferFileNameView);
            messageViewHolder.fileStatus = convertView.findViewById(R.id.filexferStatusView);
            messageViewHolder.fileXferError = convertView.findViewById(R.id.errorView);
            messageViewHolder.encStateView = convertView.findViewById(R.id.encFileStateView);

            messageViewHolder.timeView = convertView.findViewById(R.id.xferTimeView);
            messageViewHolder.fileXferSpeed = convertView.findViewById(R.id.file_progressSpeed);
            messageViewHolder.estTimeRemain = convertView.findViewById(R.id.file_estTime);
            messageViewHolder.progressBar = convertView.findViewById(R.id.file_progressbar);

            messageViewHolder.cancelButton = convertView.findViewById(R.id.buttonCancel);
            messageViewHolder.retryButton = convertView.findViewById(R.id.button_retry);
            messageViewHolder.acceptButton = convertView.findViewById(R.id.button_accept);
            messageViewHolder.declineButton = convertView.findViewById(R.id.button_decline);
        }

        hideProgressRelatedComponents();

        // Note-5: playbackSeekBar is not visible and thumb partially clipped with xml default settings.
        // So increase the playbackSeekBar height to 16dp
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            final float scale = mChatActivity.getResources().getDisplayMetrics().density;
            int dp_padding = (int) (16 * scale + 0.5f);

            messageViewHolder.playbackSeekBar.requestLayout();
            messageViewHolder.playbackSeekBar.getLayoutParams().height = dp_padding;
        }

        // set to viewHolder default state
        messageViewHolder.playerView.setVisibility(View.GONE);
        messageViewHolder.stickerView.setVisibility(View.GONE);
        messageViewHolder.fileLabel.setVisibility(View.VISIBLE);

        messageViewHolder.playbackSeekBar.setOnSeekBarChangeListener(this);
        messageViewHolder.cancelButton.setOnClickListener(this);
        messageViewHolder.stickerView.setOnClickListener(this);

        messageViewHolder.playbackPlay.setOnClickListener(this);
        messageViewHolder.playbackPlay.setOnLongClickListener(this);

        mPlayerAnimate = (AnimationDrawable) messageViewHolder.playbackPlay.getBackground();

        messageViewHolder.fileStatus.setTextColor(UtilActivator.getResources().getColor("black"));
        return convertView;
    }

    /**
     * A common routine to update the file transfer view component states
     *
     * @param status FileTransferStatusChangeEvent status
     * @param statusText the status text for update
     */
    protected void updateXferFileViewState(int status, final String statusText) {
        messageViewHolder.acceptButton.setVisibility(View.GONE);
        messageViewHolder.declineButton.setVisibility(View.GONE);
        messageViewHolder.cancelButton.setVisibility(View.GONE);
        messageViewHolder.retryButton.setVisibility(View.GONE);
        messageViewHolder.fileStatus.setTextColor(Color.BLACK);

        switch (status) {
            case FileTransferStatusChangeEvent.WAITING:
                if (this instanceof FileSendConversation) {
                    messageViewHolder.cancelButton.setVisibility(View.VISIBLE);
                }
                else {
                    messageViewHolder.acceptButton.setVisibility(View.VISIBLE);
                    messageViewHolder.declineButton.setVisibility(View.VISIBLE);
                }
                break;

            // Only allow user to cancel while in active data stream transferring; both legacy si and JFT cannot
            // support transfer cancel during protocol negotiation.
            case FileTransferStatusChangeEvent.PREPARING:
                // Preserve the cancel button view height, avoid being partially hidden by android when it is enabled
                messageViewHolder.cancelButton.setVisibility(View.GONE);
                break;

            case FileTransferStatusChangeEvent.IN_PROGRESS:
                messageViewHolder.cancelButton.setVisibility(View.VISIBLE);
                mConnection.setReplyTimeout(ProtocolProviderServiceJabberImpl.SMACK_REPLY_EXTENDED_TIMEOUT_10);
                break;

            case FileTransferStatusChangeEvent.COMPLETED:
                if (mXferFile == null)
                    break;

                // Update file label and image for incoming file
                if (FileRecord.IN.equals(mDir)) {
                    updateFileViewInfo(mXferFile, false);
                }
                // set to full for progressBar on file transfer completed
                long fileSize = mXferFile.length();
                // found http file download fileSize == 0; so fake to 100.
                if (fileSize == 0) {
                    fileSize = 100;
                }
                onUploadProgress(fileSize, fileSize);
                mConnection.setReplyTimeout(ProtocolProviderServiceJabberImpl.SMACK_REPLY_TIMEOUT_DEFAULT);
                break;

            case FileTransferStatusChangeEvent.FAILED:
            case FileTransferStatusChangeEvent.CANCELED:
                // Allow user retries only if sender cancels the file transfer
                if (FileRecord.OUT.equals(mDir)) {
                    messageViewHolder.retryButton.setVisibility(View.VISIBLE);
                } // fall through

            case FileTransferStatusChangeEvent.DECLINED: // user reject the incoming file xfer
                messageViewHolder.fileStatus.setTextColor(Color.RED);
                mConnection.setReplyTimeout(ProtocolProviderServiceJabberImpl.SMACK_REPLY_TIMEOUT_DEFAULT);
                break;
        }

        if (!TextUtils.isEmpty(statusText)) {
            messageViewHolder.fileStatus.setText(statusText);
        }
        messageViewHolder.timeView.setText(mDate);
   }

    /**
     * Shows the given error message in the error area of this component.
     *
     * @param resId the message to show
     */
    protected void showErrorMessage(int resId) {
        String message = getResources().getString(resId);
        messageViewHolder.fileXferError.setText(message);
        messageViewHolder.fileXferError.setVisibility(TextView.VISIBLE);
    }

    /**
     * Shows file thumbnail.
     *
     * @param thumbnail the thumbnail to show
     */
    protected void showThumbnail(byte[] thumbnail) {
        if (thumbnail != null && thumbnail.length > 0) {
            Bitmap thumbnailIcon = BitmapFactory.decodeByteArray(thumbnail, 0, thumbnail.length);
            int mWidth = thumbnailIcon.getWidth();
            int mHeight = thumbnailIcon.getHeight();

            if (mWidth > IMAGE_WIDTH || mHeight > IMAGE_HEIGHT) {
                messageViewHolder.fileIcon.setScaleType(ScaleType.FIT_CENTER);
            }
            else {
                messageViewHolder.fileIcon.setScaleType(ScaleType.CENTER);
            }
            messageViewHolder.fileIcon.setImageBitmap(thumbnailIcon);

            messageViewHolder.stickerView.setVisibility(View.VISIBLE);
            messageViewHolder.stickerView.setImageBitmap(thumbnailIcon);
        }
    }

    /**
     * Initialize all the local parameters i.e. mXferFile, mUri, mimeType and isMediaAudio
     * Update the file transfer view display info in thumbnail or audio player UI accordingly.
     *
     * @param file the file that has been downloaded/received or sent
     * @param isHistory true if the view file is history, so show small image size
     */
    protected void updateFileViewInfo(File file, boolean isHistory) {
        // File length = 0 will cause Glade to throw errors
        if ((file == null) || !file.exists() || file.length() == 0)
            return;

        mXferFile = file;
        mUri = FileBackend.getUriForFile(mChatActivity, file);
        mimeType = checkMimeType(file);
        isMediaAudio = ((mimeType != null) && (mimeType.contains("audio") || mimeType.contains("3gp")));

        messageViewHolder.fileLabel.setText(getFileLabel(file));
        if (isMediaAudio && playerInit()) {
            messageViewHolder.playerView.setVisibility(View.VISIBLE);
            messageViewHolder.stickerView.setVisibility(View.GONE);
            messageViewHolder.fileLabel.setVisibility(View.GONE);
            messageViewHolder.fileAudio.setText(getFileLabel(file));
        }
        else {
            messageViewHolder.playerView.setVisibility(View.GONE);
            messageViewHolder.stickerView.setVisibility(View.VISIBLE);
            messageViewHolder.fileLabel.setVisibility(View.VISIBLE);
            updateImageView(isHistory);
        }

        final String toolTip = aTalkApp.getResString(R.string.service_gui_OPEN_FILE_FROM_IMAGE);
        messageViewHolder.fileIcon.setContentDescription(toolTip);
        messageViewHolder.fileIcon.setOnClickListener(this);

        messageViewHolder.fileIcon.setOnLongClickListener(v -> {
            Toast.makeText(v.getContext(), toolTip, Toast.LENGTH_SHORT).show();
            return true;
        });
    }

    /**
     * Load the received media file image into the stickerView.
     * Ensure the loaded image view is fully visible after resource is ready.
     */
    protected void updateImageView(boolean isHistory) {
        if (isHistory || FileRecord.OUT.equals(mDir)) {
            MyGlideApp.loadImage(messageViewHolder.stickerView, mXferFile, isHistory);
            return;
        }

        Glide.with(aTalkApp.getGlobalContext())
                .asDrawable()
                .load(Uri.fromFile(mXferFile))
                .override(1280, 608)
                .into(new CustomViewTarget<ImageView, Drawable>(messageViewHolder.stickerView) {
                          @Override
                          public void onResourceReady(@NonNull Drawable resource, @Nullable Transition<? super Drawable> transition) {
                              messageViewHolder.stickerView.setImageDrawable(resource);
                              if (resource instanceof GifDrawable) {
                                  ((GifDrawable) resource).start();
                              }
                              mChatFragment.scrollToBottom();
                          }

                          @Override
                          protected void onResourceCleared(@Nullable Drawable placeholder) {
                              Timber.d("Glide onResourceCleared received!!!");
                          }

                          @Override
                          public void onLoadFailed(@Nullable Drawable errorDrawable) {
                              messageViewHolder.stickerView.setImageResource(R.drawable.ic_file_open);
                              mChatFragment.scrollToBottom();
                          }
                      }
                );
    }

    /**
     * Sets the file transfer and addProgressListener().
     * Note: HttpFileUpload adds ProgressListener in httpFileUploadManager.uploadFile()
     *
     * @param fileTransfer the file transfer
     * @param transferFileSize the size of the transferred file Running in thread, not UI here
     */
    protected void setFileTransfer(FileTransfer fileTransfer, long transferFileSize) {
        mFileTransfer = fileTransfer;
        mTransferFileSize = transferFileSize;
        fileTransfer.addProgressListener(this);
    }

    /**
     * Hides all progress related components.
     */
    protected void hideProgressRelatedComponents() {
        messageViewHolder.progressBar.setVisibility(View.GONE);
        messageViewHolder.fileXferSpeed.setVisibility(View.GONE);
        messageViewHolder.estTimeRemain.setVisibility(View.GONE);
    }

    /**
     * Remove file transfer progress listener
     */
    protected void removeProgressListener() {
        mFileTransfer.removeProgressListener(this);
    }

    /**
     * Updates progress bar progress line every time a progress event has been received file transport
     * Note: total size of event.getProgress() is always lag behind event.getFileTransfer().getTransferredBytes();
     *
     * @param event the <code>FileTransferProgressEvent</code> that notifies us
     */
    public void progressChanged(final FileTransferProgressEvent event) {
        long transferredBytes = event.getFileTransfer().getTransferredBytes();
        long progressTimestamp = event.getTimestamp();

        updateProgress(transferredBytes, progressTimestamp);
    }

    /**
     * Callback for displaying http file upload/download, and file transfer progress status.
     *
     * @param uploadedBytes the number of bytes uploaded at the moment
     * @param totalBytes the total number of bytes to be uploaded
     */
    @Override
    public void onUploadProgress(long uploadedBytes, long totalBytes) {
        updateProgress(uploadedBytes, System.currentTimeMillis());
    }

    private void updateProgress(long transferredBytes, long progressTimestamp) {
        long SMOOTHING_FACTOR = 100;

        // before file transfer start is -1
        if (transferredBytes < 0)
            return;

        final String bytesString = ByteFormat.format(transferredBytes);
        long byteTransferDelta = (transferredBytes == 0) ? 0 : (transferredBytes - mLastTransferredBytes);

        // Calculate running average transfer speed in bytes/sec and time left, with the given SMOOTHING_FACTOR
        if (mLastTimestamp > 0) {
            long timeElapsed = progressTimestamp - mLastTimestamp;
            long transferSpeedCurrent = (timeElapsed > 0) ? (byteTransferDelta * 1000) / timeElapsed : 0;
            if (mTransferSpeedAverage != 0) {
                mTransferSpeedAverage = (transferSpeedCurrent + (SMOOTHING_FACTOR - 1) * mTransferSpeedAverage) / SMOOTHING_FACTOR;
            }
            else {
                mTransferSpeedAverage = transferSpeedCurrent;
            }
        }
        else {
            mEstimatedTimeLeft = -1;
        }

        // Calculate  running average time left in sec
        if (mTransferSpeedAverage > 0)
            mEstimatedTimeLeft = (mTransferFileSize - transferredBytes) / mTransferSpeedAverage;

        mLastTimestamp = progressTimestamp;
        mLastTransferredBytes = transferredBytes;

        runOnUiThread(() -> {
            // Need to do it here as it was found that Http File Upload completed before the progress Bar is even visible
            if (!messageViewHolder.progressBar.isShown()) {
                messageViewHolder.progressBar.setVisibility(View.VISIBLE);
                messageViewHolder.progressBar.setMax((int) mTransferFileSize);
                mChatFragment.scrollToBottom();
            }
            // Note: progress bar can only handle int size (4-bytes: 2,147,483, 647);
            messageViewHolder.progressBar.setProgress((int) transferredBytes);

            if (mTransferSpeedAverage > 0) {
                messageViewHolder.fileXferSpeed.setVisibility(View.VISIBLE);
                messageViewHolder.fileXferSpeed.setText(
                        aTalkApp.getResString(R.string.service_gui_SPEED, ByteFormat.format(mTransferSpeedAverage), bytesString));
            }

            if (transferredBytes >= mTransferFileSize) {
                messageViewHolder.estTimeRemain.setVisibility(View.GONE);
            }
            else if (mEstimatedTimeLeft > 0) {
                messageViewHolder.estTimeRemain.setVisibility(View.VISIBLE);
                messageViewHolder.estTimeRemain.setText(aTalkApp.getResString(R.string.service_gui_ESTIMATED_TIME,
                        GuiUtils.formatSeconds(mEstimatedTimeLeft * 1000)));
            }
        });
    }

    /**
     * Returns a string showing information for the given file.
     *
     * @param file the file
     *
     * @return the name and size of the given file
     */
    protected String getFileLabel(File file) {
        if ((file != null) && file.exists()) {
            String fileName = file.getName();
            long fileSize = file.length();
            return getFileLabel(fileName, fileSize);
        }
        return (file == null) ? "" : file.getName();
    }

    /**
     * Returns the string, showing information for the given file.
     *
     * @param fileName the name of the file
     * @param fileSize the size of the file
     *
     * @return the name of the given file
     */
    protected String getFileLabel(String fileName, long fileSize) {
        String text = ByteFormat.format(fileSize);
        return fileName + " (" + text + ")";
    }

    /**
     * Returns the label to show on the progress bar.
     *
     * @param bytesString the bytes that have been transferred
     *
     * @return the label to show on the progress bar
     */
    protected abstract String getProgressLabel(long bytesString);

    // abstract updateView for class extension implementation
    protected abstract void updateView(final int status, final String reason);

    /**
     * Init some of the file transfer parameters. Mainly call by sendFile and File History.
     *
     * @param status File transfer send status
     * @param jid Contact or ChatRoom for Http file upload service
     * @param encType File encryption type
     * @param reason Contact or ChatRoom for Http file upload service
     */
    public void setStatus(final int status, Object jid, int encType, String reason) {
        mEntityJid = jid;
        mEncryption = encType;
        // Must execute in UiThread to Update UI information
        runOnUiThread(() -> {
            setEncState(mEncryption);
            updateView(status, reason);
        });
    }

    /**
     * Set the file encryption status icon.
     * Access directly by file receive constructor; sendFile via setStatus().
     *
     * @param encType the encryption
     */
    protected void setEncState(int encType) {
        if (IMessage.ENCRYPTION_OMEMO == encType)
            messageViewHolder.encStateView.setImageResource(R.drawable.encryption_omemo);
        else
            messageViewHolder.encStateView.setImageResource(R.drawable.encryption_none);
    }

    /**
     * Get the current status fo the file transfer
     *
     * @return the current status of the file transfer
     */
    protected int getXferStatus() {
        return mChatFragment.getChatListAdapter().getXferStatus(msgViewId);
    }

    /**
     * @return the fileTransfer file
     */
    public File getXferFile() {
        return mXferFile;
    }

    /**
     * The message Uuid uniquely identify the record in the message database
     *
     * @return the uid for the requested message to send file
     */
    public String getMessageUuid() {
        return msgUuid;
    }

    /**
     * Handles buttons click action events.
     */
    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.button_file:
            case R.id.sticker:
                if (mChatActivity != null)
                    mChatActivity.openDownloadable(mXferFile, view);
                break;

            case R.id.playback_play:
                playStart();
                break;

            case R.id.buttonCancel:
                messageViewHolder.retryButton.setVisibility(View.GONE);
                messageViewHolder.cancelButton.setVisibility(View.GONE);
                // Let transport event call back to handle
                // updateView(FileTransferStatusChangeEvent.CANCELED, null);
                if (mFileTransfer != null)
                    mFileTransfer.cancel();
                break;
        }
    }

    /**
     * Handles buttons long press action events
     * mainly use to stop and release player
     */
    @Override
    public boolean onLongClick(View v) {
        if (v.getId() == R.id.playback_play) {
            playerStop();
            return true;
        }
        return false;
    }

    /**
     * Initialize the broadcast receiver for the media player (uri).
     * Keep the active bc receiver instance in bcRegisters list to ensure only one bc is registered
     *
     * @param file the media file
     *
     * @return true if init is successful
     */
    private boolean bcReceiverInit(File file) {
        String mimeType = checkMimeType(file);
        if ((mimeType != null) && (mimeType.contains("audio") || mimeType.contains("3gp"))) {
            if (playerState == STATE_STOP) {
                BroadcastReceiver bcReceiver;
                if ((bcReceiver = bcRegisters.get(mUri)) != null) {
                    LocalBroadcastManager.getInstance(mChatActivity).unregisterReceiver(bcReceiver);
                }

                IntentFilter filter = new IntentFilter();
                filter.addAction(AudioBgService.PLAYBACK_STATE);
                filter.addAction(AudioBgService.PLAYBACK_STATUS);
                LocalBroadcastManager.getInstance(mChatActivity).registerReceiver(mReceiver, filter);
                bcRegisters.put(mUri, mReceiver);
            }
            return true;
        }
        return false;
    }

    /**
     * Get the active media player status or just media info for the view display;
     * update the view holder content via Broadcast receiver
     */
    private boolean playerInit() {
        if (isMediaAudio) {
            if (playerState == STATE_STOP) {

                if (!bcReceiverInit(mXferFile))
                    return false;

                Intent intent = new Intent(mChatActivity, AudioBgService.class);
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                intent.setDataAndType(mUri, mimeType);
                intent.setAction(AudioBgService.ACTION_PLAYER_INIT);
                mChatActivity.startService(intent);
            }
            return true;
        }
        return false;
    }

    /**
     * Stop the current active media player playback
     */
    private void playerStop() {
        if (isMediaAudio) {
            if ((playerState == STATE_PAUSE) || (playerState == STATE_PLAY)) {

                Intent intent = new Intent(mChatActivity, AudioBgService.class);
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                intent.setDataAndType(mUri, mimeType);
                intent.setAction(AudioBgService.ACTION_PLAYER_STOP);
                mChatActivity.startService(intent);
            }
        }
    }

    /**
     * Toggle audio file playback states:
     * STOP -> PLAY -> PAUSE -> PLAY;
     * long press play button to STOP
     *
     * Proceed to open the file for VIEW if this is not an audio file
     */
    private void playStart() {
        Intent intent = new Intent(mChatActivity, AudioBgService.class);
        if (isMediaAudio) {
            if (playerState == STATE_PLAY) {
                intent.setData(mUri);
                intent.setAction(AudioBgService.ACTION_PLAYER_PAUSE);
                mChatActivity.startService(intent);
                return;
            }
            else if (playerState == STATE_STOP) {
                if (!bcReceiverInit(mXferFile))
                    return;
            }

            intent.setAction(AudioBgService.ACTION_PLAYER_START);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            intent.setDataAndType(mUri, mimeType);
            mChatActivity.startService(intent);
            return;
        }

        intent = new Intent(Intent.ACTION_VIEW);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.setDataAndType(mUri, mimeType);

        PackageManager manager = mChatActivity.getPackageManager();
        List<ResolveInfo> info = manager.queryIntentActivities(intent, 0);
        if (info.size() == 0) {
            intent.setDataAndType(mUri, "*/*");
        }
        try {
            mChatActivity.startActivity(intent);
        } catch (ActivityNotFoundException e) {
            aTalkApp.showToastMessage(R.string.service_gui_FILE_OPEN_NO_APPLICATION);
        }
    }

    /**
     * SeekTo player new start play position
     *
     * @param position seek time position
     */
    private void playerSeek(int position) {
        if (isMediaAudio) {
            if (!bcReceiverInit(mXferFile))
                return;

            Intent intent = new Intent(mChatActivity, AudioBgService.class);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            intent.setDataAndType(mUri, mimeType);
            intent.putExtra(AudioBgService.PLAYBACK_POSITION, position);
            intent.setAction(AudioBgService.ACTION_PLAYER_SEEK);
            mChatActivity.startService(intent);
        }
    }

    /**
     * Media player BroadcastReceiver to animate and update player view holder info
     */
    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            // proceed only if it is the playback of the current mUri
            if (!mUri.equals(intent.getParcelableExtra(AudioBgService.PLAYBACK_URI)))
                return;

            int position = intent.getIntExtra(AudioBgService.PLAYBACK_POSITION, 0);
            int audioDuration = intent.getIntExtra(AudioBgService.PLAYBACK_DURATION, 0);

            if ((playerState == STATE_PLAY) && AudioBgService.PLAYBACK_STATUS.equals(intent.getAction())) {
                if (!isSeeking)
                    messageViewHolder.playbackPosition.setText(formatTime(position));
                messageViewHolder.playbackDuration.setText(formatTime(audioDuration - position));
                messageViewHolder.playbackSeekBar.setMax(audioDuration);
                messageViewHolder.playbackSeekBar.setProgress(position);

            }
            else if (AudioBgService.PLAYBACK_STATE.equals(intent.getAction())) {
                AudioBgService.PlaybackState playbackState
                        = (AudioBgService.PlaybackState) intent.getSerializableExtra(AudioBgService.PLAYBACK_STATE);

                Timber.d("Audio playback state: %s (%s/%s): %s", playbackState, position, audioDuration, mUri.getPath());
                switch (playbackState) {
                    case init:
                        playerState = STATE_IDLE;
                        messageViewHolder.playbackDuration.setText(formatTime(audioDuration));
                        messageViewHolder.playbackPosition.setText(formatTime(0));
                        messageViewHolder.playbackSeekBar.setMax(audioDuration);
                        messageViewHolder.playbackSeekBar.setProgress(0);

                        messageViewHolder.playbackPlay.setImageResource(R.drawable.ic_player_stop);
                        mPlayerAnimate.stop();
                        break;

                    case play:
                        playerState = STATE_PLAY;
                        messageViewHolder.playbackSeekBar.setMax(audioDuration);
                        messageViewHolder.playerView.clearAnimation();

                        messageViewHolder.playbackPlay.setImageDrawable(null);
                        mPlayerAnimate.start();
                        break;

                    case stop:
                        playerState = STATE_STOP;
                        bcRegisters.remove(mUri);
                        LocalBroadcastManager.getInstance(mChatActivity).unregisterReceiver(mReceiver);
                    case pause:
                        if (playerState != STATE_STOP) {
                            playerState = STATE_PAUSE;
                        }
                        messageViewHolder.playbackPosition.setText(formatTime(position));
                        messageViewHolder.playbackDuration.setText(formatTime(audioDuration - position));
                        messageViewHolder.playbackSeekBar.setMax(audioDuration);
                        messageViewHolder.playbackSeekBar.setProgress(position);

                        mPlayerAnimate.stop();
                        messageViewHolder.playbackPlay.setImageResource((playerState == STATE_PAUSE)
                                ? R.drawable.ic_player_pause : R.drawable.ic_player_stop);
                        break;
                }
            }
        }
    };

    /**
     * OnSeekBarChangeListener callback interface during multimedia playback
     *
     * A SeekBar callback that notifies clients when the progress level has been
     * changed. This includes changes that were initiated by the user through a
     * touch gesture or arrow key/trackball as well as changes that were initiated
     * programmatically.
     */
    @Override
    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
        if (fromUser && (messageViewHolder.playbackSeekBar == seekBar)) {
            positionSeek = progress;
            messageViewHolder.playbackPosition.setText(formatTime(progress));
        }
    }

    @Override
    public void onStartTrackingTouch(SeekBar seekBar) {
        if (messageViewHolder.playbackSeekBar == seekBar) {
            isSeeking = true;
        }

    }

    @Override
    public void onStopTrackingTouch(SeekBar seekBar) {
        if (messageViewHolder.playbackSeekBar == seekBar) {
            playerSeek(positionSeek);
            isSeeking = false;
        }
    }

    /**
     * Format the given time to mm:ss
     *
     * @param time time is ms
     *
     * @return the formatted time string in mm:ss
     */
    private String formatTime(int time) {
        // int ms = (time % 1000) / 10;
        int seconds = time / 1000;
        int minutes = seconds / 60;
        seconds = seconds % 60;
        return String.format(Locale.US, "%02d:%02d", minutes, seconds);
    }

    /**
     * Determine the mimeType of the given file
     *
     * @param file the media file to check
     *
     * @return mimeType or null if undetermined
     */
    private String checkMimeType(File file) {
        if (!file.exists()) {
            // aTalkApp.showToastMessage(R.string.service_gui_FILE_DOES_NOT_EXIST);
            return null;
        }

        try {
            Uri uri = FileBackend.getUriForFile(mChatActivity, file);
            String mimeType = getMimeType(mChatActivity, uri);
            if ((mimeType == null) || mimeType.contains("application")) {
                mimeType = "*/*";
            }
            return mimeType;

        } catch (SecurityException e) {
            Timber.i("No permission to access %s: %s", file.getAbsolutePath(), e.getMessage());
            aTalkApp.showToastMessage(R.string.service_gui_FILE_OPEN_NO_PERMISSION);
            return null;
        }
    }

    /**
     * Generate the mXferFile full filePath based on the given fileName and mimeType
     *
     * @param fileName the incoming xfer fileName
     * @param mimeType the incoming file mimeType
     */
    protected void setTransferFilePath(String fileName, String mimeType) {
        String downloadPath = FileBackend.MEDIA_DOCUMENT;
        if (fileName.contains("voice-"))
            downloadPath = FileBackend.MEDIA_VOICE_RECEIVE;
        else if (StringUtils.isNotEmpty(mimeType) && !mimeType.startsWith("*")) {
            downloadPath = FileBackend.MEDIA + File.separator + mimeType.split("/")[0];
        }

        File downloadDir = FileBackend.getaTalkStore(downloadPath, true);
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
    }

    /**
     * Adds the given <code>ScFileTransferListener</code> that would listen for file transfer requests
     * created file transfers.
     *
     * @param listener the <code>ScFileTransferListener</code> to add
     */
    public void addUploadListener(UploadProgressListener listener) {
        synchronized (uploadProgressListeners) {
            if (!uploadProgressListeners.contains(listener)) {
                this.uploadProgressListeners.add(listener);
            }
        }
    }

    /**
     * Removes the given <code>ScFileTransferListener</code> that listens for file transfer requests and
     * created file transfers.
     *
     * @param listener the <code>ScFileTransferListener</code> to remove
     */
    public void removeUploadListener(UploadProgressListener listener) {
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
    public void onUploadStatus(long uploadedBytes, long totalBytes) {
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
