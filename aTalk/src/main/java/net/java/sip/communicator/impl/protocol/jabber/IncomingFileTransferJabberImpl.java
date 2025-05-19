/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.impl.protocol.jabber;

import java.io.File;

import net.java.sip.communicator.service.protocol.AbstractFileTransfer;
import net.java.sip.communicator.service.protocol.Contact;

import org.jivesoftware.smackx.filetransfer.IncomingFileTransfer;

/**
 * The Jabber protocol extension of the <code>AbstractFileTransfer</code>.
 *
 * @author Yana Stamcheva
 * @author Eng Chong Meng
 */
public class IncomingFileTransferJabberImpl extends AbstractFileTransfer {
    private final String mId;
    private final Contact mSender;
    private final File mFile;

    /**
     * The Jabber incoming file transfer.
     */
    private final IncomingFileTransfer mJabberTransfer;

    /**
     * Creates an <code>IncomingFileTransferJabberImpl</code>.
     *
     * @param id the identifier of this transfer
     * @param sender the sender of the file
     * @param file the file
     * @param jabberTransfer the Jabber file transfer object
     */
    public IncomingFileTransferJabberImpl(String id, Contact sender, File file, IncomingFileTransfer jabberTransfer) {
        mId = id;
        mSender = sender;
        mFile = file;
        mJabberTransfer = jabberTransfer;
    }

    /**
     * User declines the incoming file transfer/offer.
     */
    @Override
    public void cancel() {
        mJabberTransfer.cancel();
    }

    /**
     * Returns the number of bytes already received from the recipient.
     *
     * @return the number of bytes already received from the recipient
     */
    @Override
    public long getTransferredBytes() {
        return mJabberTransfer.getAmountWritten();
    }

    /**
     * The direction is incoming.
     *
     * @return IN
     */
    public int getDirection() {
        return IN;
    }

    /**
     * Returns the sender of the file.
     *
     * @return the sender of the file
     */
    public Contact getContact() {
        return mSender;
    }

    /**
     * Returns the identifier of this file transfer.
     *
     * @return the identifier of this file transfer
     */
    public String getID() {
        return mId;
    }

    /**
     * Returns the local file that is being transferred or to which we transfer.
     *
     * @return the file
     */
    public File getLocalFile() {
        return mFile;
    }
}
