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

import net.java.sip.communicator.service.filehistory.FileRecord;

import org.atalk.android.*;
import org.atalk.android.gui.AndroidGUIActivator;
import org.atalk.android.gui.chat.ChatFragment;
import org.atalk.android.gui.chat.ChatFragment.MessageViewHolder;
import org.atalk.android.gui.chat.ChatMessage;

import java.io.File;
import java.util.Date;

/**
 * The component used to show a file transfer history record in the chat window.
 *
 * @author Eng Chong Meng
 */
public class FileHistoryConversation extends FileTransferConversation
{
    private ChatFragment mChatFragment;
    private FileRecord fileRecord;
    private ChatMessage chatMessage;

    public FileHistoryConversation()
    {
    }

    public static FileHistoryConversation newInstance(ChatFragment cPanel, FileRecord fileRecord, ChatMessage msg)
    {
        FileHistoryConversation fragmentFHC = new FileHistoryConversation();
        fragmentFHC.mChatFragment = cPanel;
        fragmentFHC.fileRecord = fileRecord;
        fragmentFHC.chatMessage = msg;

        return fragmentFHC;
    }

    public View FileHistoryConversationForm(LayoutInflater inflater, MessageViewHolder msgViewHolder,
            ViewGroup container, boolean init)
    {
        View convertView = inflateViewForFileTransfer(inflater, msgViewHolder, container, init);

        boolean bgAlert = true;
        String titleString;

        if (fileRecord == null) {
            if (chatMessage != null) {
                // String contact = chatMessage.getContactName();
                Date date = chatMessage.getDate();
                titleString = aTalkApp.getResString(R.string.xFile_FILE_TRANSFER_CANCELED, date);
                messageViewHolder.titleLabel.setText(titleString);
            }
            return convertView;
        }

        String entityJid = fileRecord.getContact().getAddress();
        String dir = fileRecord.getDirection();

        if (FileRecord.IN.equals(dir))
            messageViewHolder.arrowDir.setImageResource(R.drawable.filexferarrowin);
        else
            messageViewHolder.arrowDir.setImageResource(R.drawable.filexferarrowout);

        File filePath = fileRecord.getFile();
        String status = fileRecord.getStatus();

        if (FileRecord.COMPLETED.equals(status)) {
            bgAlert = false;
            MyGlideApp.loadImage(messageViewHolder.stickerView, filePath, true);
        }

        titleString = getStatusMessage(entityJid, dir, status);
        mChatFragment.clearMsgCache = FileRecord.ACTIVE.equals(status);

        this.setCompletedDownloadFile(mChatFragment, filePath);
        Date date = fileRecord.getDate();
        StringBuilder tileLabel = new StringBuilder();
        tileLabel.append(date.toString())
                .append(":\n")
                .append(titleString);
        messageViewHolder.titleLabel.setText(tileLabel);
        messageViewHolder.fileLabel.setText(getFileLabel(filePath));

        if (bgAlert) {
            messageViewHolder.titleLabel.setTextColor(AndroidGUIActivator.getResources().getColor("red"));
        }
        return convertView;
    }

    /**
     * Generate the correct display message based on fileTransfer status and direction
     *
     * @param entityJid file transfer initiator
     * @param dir file send or received
     * @param status file transfer status
     * @return the status message to display
     */
    public String getStatusMessage(String entityJid, String dir, String status)
    {
        String statusMsg = "";
        if (dir.equals(FileRecord.IN)) {
            switch (status) {
                case FileRecord.COMPLETED:
                    statusMsg = aTalkApp.getResString(R.string.service_gui_FILE_RECEIVE_COMPLETED, entityJid);
                    break;
                case FileRecord.CANCELED:
                    statusMsg = aTalkApp.getResString(R.string.service_gui_FILE_TRANSFER_CANCELED);
                    break;
                case FileRecord.FAILED:
                    statusMsg = aTalkApp.getResString(R.string.service_gui_FILE_RECEIVE_FAILED, entityJid);
                    break;
                case FileRecord.REFUSED:
                    statusMsg = aTalkApp.getResString(R.string.service_gui_FILE_TRANSFER_REFUSED);
                    break;
                case FileRecord.ACTIVE:
                    statusMsg = aTalkApp.getResString(R.string.service_gui_FILE_TRANSFER_ACTIVE);
                    break;
            }
        }
        else {
            switch (status) {
                case FileRecord.COMPLETED:
                    statusMsg = aTalkApp.getResString(R.string.service_gui_FILE_SEND_COMPLETED, entityJid);
                    break;
                case FileRecord.CANCELED:
                    statusMsg = aTalkApp.getResString(R.string.service_gui_FILE_TRANSFER_CANCELED);
                    break;
                case FileRecord.FAILED:
                    statusMsg = aTalkApp.getResString(R.string.service_gui_FILE_UNABLE_TO_SEND, entityJid);
                    break;
                case FileRecord.REFUSED:
                    statusMsg = aTalkApp.getResString(R.string.service_gui_FILE_SEND_REFUSED, entityJid);
                    break;
                case FileRecord.ACTIVE:
                    statusMsg = aTalkApp.getResString(R.string.service_gui_FILE_TRANSFER_ACTIVE);
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
    protected String getProgressLabel(String bytesString)
    {
        return "";
    }
}
