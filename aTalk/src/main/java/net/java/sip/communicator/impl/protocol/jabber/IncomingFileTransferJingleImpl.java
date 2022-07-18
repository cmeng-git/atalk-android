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
import net.java.sip.communicator.service.protocol.OperationFailedException;
import net.java.sip.communicator.service.protocol.event.FileTransferStatusChangeEvent;

import org.atalk.android.R;
import org.atalk.android.aTalkApp;
import org.jivesoftware.smackx.jingle.component.JingleSessionImpl;
import org.jivesoftware.smackx.jingle.element.JingleReason;
import org.jivesoftware.smackx.jingle_filetransfer.listener.ProgressListener;

import java.io.File;

import timber.log.Timber;

/**
 * The Jabber protocol extension of the <code>AbstractFileTransfer</code> to handle Jingle File Offer.
 *
 * @author Eng Chong Meng
 */
public class IncomingFileTransferJingleImpl extends AbstractFileTransfer
        implements JingleSessionImpl.JingleSessionListener, ProgressListener
{
    private final String id;
    private final Contact sender;
    private final File mFile;
    private int byteRead;

    /**
     * The Jingle incoming file offer.
     */
    private final IncomingFileOfferJingleImpl mIfoJingle;

    /**
     * Creates an <code>IncomingFileTransferJingleImpl</code>.
     *
     * @param inFileOffer the Jingle incoming file offer
     * @param file the file
     */
    public IncomingFileTransferJingleImpl(IncomingFileOfferJingleImpl inFileOffer, File file)
    {
        this.id = inFileOffer.getID();
        this.sender = inFileOffer.getSender();
        this.mFile = file;
        this.mIfoJingle = inFileOffer;
        this.byteRead = 0;
        inFileOffer.mOffer.addProgressListener(this);
        JingleSessionImpl.addJingleSessionListener(this);
    }

    /**
     * User declines or cancels an incoming file transfer request during initial or during the active state.
     */
    @Override
    public void cancel()
    {
        try {
            onCanceled();
            mIfoJingle.declineFile();
            mIfoJingle.mOffer.removeProgressListener(this);
            JingleSessionImpl.removeJingleSessionListener(this);
        } catch (OperationFailedException e) {
            Timber.e("Exception: %s", e.getMessage());
        }
    }

    public void onCanceled()
    {
        String reason = aTalkApp.getResString(R.string.xFile_FILE_TRANSFER_CANCELED);
        fireStatusChangeEvent(FileTransferStatusChangeEvent.CANCELED, reason);
    }

    /**
     * Returns the number of bytes already received from the recipient.
     *
     * @return the number of bytes already received from the recipient
     */
    @Override
    public long getTransferredBytes()
    {
        // Timber.d("getTransferredBytes received: %s", byteRead);
        return byteRead;
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
        return sender;
    }

    /**
     * Returns the identifier of this file transfer.
     *
     * @return the identifier of this file transfer
     */
    public String getID()
    {
        return id;
    }

    /**
     * Returns the local file that is being transferred.
     *
     * @return the file
     */
    public File getLocalFile()
    {
        return mFile;
    }

    @Override
    public void onStarted()
    {
        fireStatusChangeEvent(FileTransferStatusChangeEvent.IN_PROGRESS, "InProgress");
    }

    @Override
    public void progress(int rwBytes)
    {
        byteRead = rwBytes;
    }

    @Override
    public void onFinished()
    {
        fireStatusChangeEvent(FileTransferStatusChangeEvent.COMPLETED, "Completed");
    }

    @Override
    public void onError(JingleReason reason)
    {
        JingleSessionImpl.removeJingleSessionListener(this);
        fireStatusChangeEvent(reason);
    }

    @Override
    public void onSessionTerminated(JingleReason reason)
    {
        JingleSessionImpl.removeJingleSessionListener(this);
        fireStatusChangeEvent(reason);
    }
}
