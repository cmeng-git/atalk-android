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

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import java.io.File;

import net.java.sip.communicator.service.filehistory.FileRecord;
import net.java.sip.communicator.service.protocol.event.FileTransferStatusChangeEvent;
import net.java.sip.communicator.util.GuiUtils;

import org.atalk.android.R;
import org.atalk.android.aTalkApp;
import org.atalk.android.gui.chat.ChatFragment;
import org.atalk.android.gui.chat.ChatMessage;

/**
 * The component used to show a file transfer history record in the chat window.
 *
 * @author Eng Chong Meng
 */
public class FileHistoryConversation extends FileTransferConversation {
    private FileRecord fileRecord;
    private ChatMessage chatMessage;

    private FileHistoryConversation(ChatFragment cPanel, String dir) {
        super(cPanel, dir);
    }

    public static FileHistoryConversation newInstance(ChatFragment cPanel, FileRecord fileRecord, ChatMessage msg) {
        FileHistoryConversation fragmentFHC = new FileHistoryConversation(cPanel, fileRecord.getDirection());
        fragmentFHC.fileRecord = fileRecord;
        fragmentFHC.chatMessage = msg;
        return fragmentFHC;
    }

    public View FileHistoryConversationForm(LayoutInflater inflater, ChatFragment.MessageViewHolder msgViewHolder,
            ViewGroup container, boolean init) {
        View convertView = inflateViewForFileTransfer(inflater, msgViewHolder, container, init);
        // Assume history file transfer is completed with all button hidden
        updateXferFileViewState(FileTransferStatusChangeEvent.COMPLETED, null);

        if (fileRecord == null) {
            if (chatMessage != null) {
                String date = GuiUtils.formatDateTime(chatMessage.getDate());
                messageViewHolder.timeView.setText(date);
                messageViewHolder.fileStatus.setText(R.string.file_transfer_canceled);
            }
            return convertView;
        }

        String entityJid = fileRecord.getJidAddress();
        String dir = fileRecord.getDirection();

        File filePath = fileRecord.getFile();
        int status = fileRecord.getStatus();
        boolean bgAlert = (FileRecord.STATUS_COMPLETED != status);
        if (!bgAlert && !filePath.exists()) {
            bgAlert = true;
            status = FileRecord.FILE_NOT_FOUND;
        }

        updateFileViewInfo(filePath, true);
        mEncryption = fileRecord.getEncType();
        setEncState(mEncryption);

        String date = GuiUtils.formatDateTime(fileRecord.getDate());
        messageViewHolder.timeView.setText(date);
        String statusMessage = getStatusMessage(entityJid, dir, status);
        messageViewHolder.fileStatus.setText(statusMessage);

        if (bgAlert) {
            messageViewHolder.fileStatus.setTextColor(Color.RED);
        }
        return convertView;
    }

    /**
     * Generate the correct display message based on fileTransfer status and direction
     *
     * @param entityJid file transfer initiator
     * @param dir file send or received
     * @param status file transfer status
     *
     * @return the status message to display
     */
    private String getStatusMessage(String entityJid, String dir, int status) {
        String statusMsg = "";
        String statusText = FileRecord.statusMap.get(status);

        if (FileRecord.IN.equals(dir)) {
            switch (status) {
                case FileRecord.STATUS_COMPLETED:
                    statusMsg = aTalkApp.getResString(R.string.file_receive_completed, entityJid);
                    break;
                case FileRecord.STATUS_FAILED:
                    statusMsg = aTalkApp.getResString(R.string.file_receive_failed, entityJid);
                    break;
                case FileRecord.STATUS_CANCELED:
                    statusMsg = aTalkApp.getResString(R.string.file_transfer_canceled);
                    break;
                case FileRecord.STATUS_DECLINED:
                    statusMsg = aTalkApp.getResString(R.string.file_transfer_declined);
                    break;
                case FileRecord.STATUS_WAITING:
                case FileRecord.STATUS_PREPARING:
                case FileRecord.STATUS_IN_PROGRESS:
                    statusMsg = aTalkApp.getResString(R.string.file_transfer_active, statusText);
                    break;
                case FileRecord.FILE_NOT_FOUND:
                    statusMsg = aTalkApp.getResString(R.string.file_does_not_exist);
                    break;
                default: // http file transfer status containing http link
                    statusMsg = aTalkApp.getResString(R.string.file_transfer_active, statusText);
            }
        }
        else {
            switch (status) {
                case FileRecord.STATUS_COMPLETED:
                    statusMsg = aTalkApp.getResString(R.string.file_send_completed, entityJid);
                    break;
                case FileRecord.STATUS_FAILED:
                    statusMsg = aTalkApp.getResString(R.string.file_transfer_send_error, entityJid);
                    break;
                case FileRecord.STATUS_CANCELED:
                    statusMsg = aTalkApp.getResString(R.string.file_transfer_canceled);
                    break;
                case FileRecord.STATUS_DECLINED:
                    statusMsg = aTalkApp.getResString(R.string.file_send_declined, entityJid);
                    break;
                case FileRecord.STATUS_WAITING:
                case FileRecord.STATUS_PREPARING:
                case FileRecord.STATUS_IN_PROGRESS:
                    statusMsg = aTalkApp.getResString(R.string.file_transfer_active, statusText);
                    break;
                case FileRecord.FILE_NOT_FOUND:
                    statusMsg = aTalkApp.getResString(R.string.file_does_not_exist);
                    break;
            }
        }
        return statusMsg;
    }

    /**
     * We don't have progress label in history.
     *
     * @return empty string
     */
    @Override
    protected String getProgressLabel(long bytesString) {
        return "";
    }

    @Override
    protected void updateView(int status, String reason) {
        // No view update process is called for file history
    }
}
