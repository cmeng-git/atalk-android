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
package net.java.sip.communicator.impl.protocol.jabber;

import net.java.sip.communicator.service.protocol.AbstractFileTransfer;
import net.java.sip.communicator.service.protocol.Contact;

import java.io.File;

/**
 * The Jabber protocol OutgoingFileSendEntityImpl extension of the <code>AbstractFileTransfer</code>.
 * This class is used when sending file to a single recipient or multiple as in conference room
 *
 * @author Eng Chong Meng
 */
public class OutgoingFileSendEntityImpl extends AbstractFileTransfer {
    private final String msgUuid;

    /**
     * The file recipient i.e. Contact or ChatRoom
     */
    private final Object mRecipient;

    private final File mFile;
    private final String mFileName;
    private final long mFileSize;

    /**
     * Creates an <code>OutgoingFileSendEntityImpl</code>.
     *
     * @param sendTo the recipient of the file
     * @param id the message Uuid uniquely identify  record in DB
     * @param file the download link may contains other options e.g. file.length()
     */
    public OutgoingFileSendEntityImpl(Object sendTo, String id, String file) {
        // Create a new msg Uuid if none provided
        msgUuid = (id == null) ? String.valueOf(System.currentTimeMillis()) + hashCode() : id;

        mRecipient = sendTo;
        mFileName = file;
        mFile = new File(file);
        mFileSize = mFile.length();
    }

    /**
     * Cancels the file transfer.
     */
    @Override
    public void cancel() {
        // jabberTransfer.cancel();
    }

    /**
     * The direction is outgoing.
     *
     * @return OUT
     */
    public int getDirection() {
        return OUT;
    }

    /**
     * Returns the sender of the file.
     *
     * @return the sender of the file
     */
    public Contact getContact() {
        return (mRecipient instanceof Contact) ? (Contact) mRecipient : null;

    }

    /**
     * Get the recipient of the file
     *
     * @return Contact or ChatRoom
     */
    public Object getEntityJid() {
        return mRecipient;
    }

    /**
     * Returns the identifier of this file transfer.
     *
     * @return the identifier of this file transfer
     */
    public String getID() {
        return msgUuid;
    }

    /**
     * Returns the local file that is being transferred or to which we transfer.
     *
     * @return the file
     */
    public File getLocalFile() {
        return mFile;
    }

    /**
     * Returns the name of the file corresponding to this request.
     *
     * @return the name of the file corresponding to this request
     */
    public String getFileName() {
        return mFileName;
    }

    /**
     * Returns the size of the file corresponding to this request.
     *
     * @return the size of the file corresponding to this request
     */
    public long getFileSize() {
        return mFileSize;
    }
}
