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

import net.java.sip.communicator.service.protocol.*;

import java.io.File;

/**
 * The Jabber protocol HttpFileDownloadJabberImpl extension of the <tt>AbstractFileTransfer</tt>.
 *
 * @author Eng Chong Meng
 */
public class HttpFileUploadJabberImpl extends AbstractFileTransfer
{
    private final String msgUuid;
    private Object mSendTo;

    private final File mFile;
    private final String mFileName;
    private long fileSize = -1;

    /**
     * Creates an <tt>IncomingFileTransferJabberImpl</tt>.
     *
     * @param sendTo the recipient of the file
     * @param id the message Uuid uniquely identify  record in DB
     * @param file the download link may contains other options e.g. file.length()
     */
    public HttpFileUploadJabberImpl(Object sendTo, String id, String file)
    {
        // Create a new msg Uuid if none provided
        msgUuid = (id == null) ? String.valueOf(System.currentTimeMillis()) + hashCode() : id;

        mSendTo = sendTo;
        mFileName = file;
        mFile = new File(file);
        fileSize = mFile.length();
    }

    /**
     * Cancels the file transfer.
     */
    @Override
    public void cancel()
    {
//            jabberTransfer.cancel();
    }

    /**
     * Returns the number of bytes already received from the recipient.
     *
     * @return the number of bytes already received from the recipient
     */
    @Override
    public long getTransferredBytes()
    {
//        if (jabberTransfer != null)
//            return jabberTransfer.getAmountWritten();
//        else
        return -1;
    }

    /**
     * The direction is incoming.
     *
     * @return IN
     */
    public int getDirection()
    {
        return OUT;
    }

    /**
     * Returns the sender of the file.
     *
     * @return the sender of the file
     */
    public Contact getContact()
    {
        return (mSendTo instanceof Contact) ? (Contact) mSendTo : null;

    }

    public Object getEntityJid()
    {
        return mSendTo;
    }

    /**
     * Returns the identifier of this file transfer.
     *
     * @return the identifier of this file transfer
     */
    public String getID()
    {
        return msgUuid;
    }

    /**
     * Returns the local file that is being transferred or to which we transfer.
     *
     * @return the file
     */
    public File getLocalFile()
    {
        return mFile;
    }

    /**
     * Returns the name of the file corresponding to this request.
     *
     * @return the name of the file corresponding to this request
     */
    public String getFileName()
    {
        return mFileName;
    }

    /**
     * Returns the size of the file corresponding to this request.
     *
     * @return the size of the file corresponding to this request
     */
    public long getFileSize()
    {
        return fileSize;
    }
}
