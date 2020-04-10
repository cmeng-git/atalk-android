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

import android.content.BroadcastReceiver;
import android.net.Uri;

import net.java.sip.communicator.service.protocol.*;

import org.atalk.android.aTalkApp;
import org.jivesoftware.smackx.omemo_media_sharing.AesgcmUrl;

import java.io.File;

import timber.log.Timber;

/**
 * The Jabber protocol HttpFileDownloadJabberImpl extension of the <tt>AbstractFileTransfer</tt>.
 *
 * @author Eng Chong Meng
 */
public class HttpFileDownloadJabberImpl extends AbstractFileTransfer
{
    private BroadcastReceiver downloadReceiver = null;
    private final String msgUuid;
    private final Contact mSender;

    private final File mFile;
    private final String mFileName;
    private String dnLink;
    private long fileSize = -1;
    /*
     * Transfer file encryption type
     */
    protected int mEncryption;

    /**
     * Creates an <tt>IncomingFileTransferJabberImpl</tt>.
     *
     * @param sender the sender of the file
     * @param id the message Uuid uniquely identify  record in DB
     * @param dnLinkDescription the download link may contains other options e.g. file.length()
     */
    public HttpFileDownloadJabberImpl(Contact sender, String id, String dnLinkDescription, int xferStatus)
    {
        mSender = sender;
        mStatus = xferStatus;

        // Create a new msg Uuid if none provided
        msgUuid = (id == null) ? String.valueOf(System.currentTimeMillis()) + hashCode() : id;

        String[] dnLinkInfos = dnLinkDescription.split("\\s+|,|\\t|\\n");
        dnLink = dnLinkInfos[0];
        String url;
        if (dnLink.matches("^aesgcm:.*")) {
            AesgcmUrl aesgcmUrl = new AesgcmUrl(dnLink);
            url = aesgcmUrl.getDownloadUrl().toString();
            mEncryption = IMessage.ENCRYPTION_OMEMO;
        }
        else {
            url = dnLink;
            mEncryption = IMessage.ENCRYPTION_NONE;
        }

        Uri uri = Uri.parse(url);
        mFileName = uri.getLastPathSegment();
        mFile = (mFileName != null) ? new File(mFileName) : null;

        if (dnLinkInfos.length > 1 && "fileSize".matches(dnLinkInfos[1])) {
            fileSize = Long.parseLong(dnLinkInfos[1].split("[:=]")[1]);
        }
        else
            fileSize = -1;
    }

    public void setDownloadReceiver (BroadcastReceiver receiver) {
        downloadReceiver = receiver;
    }

    /**
     * Unregister the HttpDownload transfer downloadReceiver.
     */
    @Override
    public void cancel()
    {
        if (downloadReceiver != null) {
            try {
                aTalkApp.getGlobalContext().unregisterReceiver(downloadReceiver);
            } catch (IllegalArgumentException e) {
                Timber.e("Error unRegister download receiver: %s", e.getMessage());
            }
            downloadReceiver = null;
        }
    }

    /**
     * Returns the number of bytes already received from the recipient.
     *
     * @return the number of bytes already received from the recipient
     */
    @Override
    public long getTransferredBytes()
    {
        // if (jabberTransfer != null)
        //    return jabberTransfer.getAmountWritten();
        // else
        return -1;
    }

    /**
     * The direction is incoming.
     *
     * @return IN
     */
    public int getDirection()
    {
        return IN;
    }

    /**
     * Returns the sender of the file.
     *
     * @return the sender of the file
     */
    public Contact getContact()
    {
        return mSender;
    }

//    public Contact getSender()
//    {
//        return mSender;
//    }

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

    /**
     * Returns the description of the file corresponding to this request.
     *
     * @return the description of the file corresponding to this request
     */
    public String getDnLink()
    {
        return dnLink;
    }

    /**
     * Returns the encryption of the file corresponding to this request.
     *
     * @return the encryption of the file corresponding to this request
     */
    public int getEncType()
    {
        return mEncryption;
    }
}
