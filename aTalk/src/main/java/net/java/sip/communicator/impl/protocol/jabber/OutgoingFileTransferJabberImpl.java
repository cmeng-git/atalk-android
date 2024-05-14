/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.impl.protocol.jabber;

import java.io.File;

import net.java.sip.communicator.service.protocol.AbstractFileTransfer;
import net.java.sip.communicator.service.protocol.Contact;

import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smackx.bob.BoBData;
import org.jivesoftware.smackx.bob.BoBInfo;
import org.jivesoftware.smackx.bob.BoBManager;
import org.jivesoftware.smackx.bob.ContentId;
import org.jivesoftware.smackx.filetransfer.FileTransfer;
import org.jivesoftware.smackx.filetransfer.OutgoingFileTransfer;

/**
 * The Jabber protocol extension of the <code>AbstractFileTransfer</code>.
 *
 * @author Yana Stamcheva
 * @author Eng Chong Meng
 */
public class OutgoingFileTransferJabberImpl extends AbstractFileTransfer {
    // must include this attribute in bobData; else smack 4.4.0 throws NPE
    private static final int maxAge = 86400;

    // Message Uuid also used as file transfer id.
    private final String msgUuid;
    private final Contact mContact;
    private final File mFile;

    /**
     * The jabber outgoing file transfer.
     */
    private final OutgoingFileTransfer mJabberFileTransfer;
    private final XMPPConnection mConnection;

    private BoBInfo bobInfo;

    /**
     * Creates an <code>OutgoingFileTransferJabberImpl</code> by specifying the <code>receiver</code>
     * contact, the <code>file</code> , the <code>jabberTransfer</code>, that would be used to send the file
     * through Jabber and the <code>protocolProvider</code>.
     *
     * @param recipient the destination contact
     * @param file the file to send
     * @param jabberTransfer the Jabber transfer object, containing all transfer information
     * @param pps the parent protocol provider
     * @param id the id that uniquely identifies this file transfer and saved DB record
     */
    public OutgoingFileTransferJabberImpl(Contact recipient, File file, OutgoingFileTransfer jabberTransfer,
            ProtocolProviderServiceJabberImpl pps, String id) {
        mContact = recipient;
        mFile = file;
        mJabberFileTransfer = jabberTransfer;
        mConnection = pps.getConnection();

        // Create the identifier of this file transfer that is used from the history and the user
        // interface to track this transfer. Use pass in value if available (cmeng 20220206: true always)
        // this.id = (TextUtils.isEmpty(msgUuid)) ? String.valueOf(System.currentTimeMillis()) + hashCode() : msgUuid;
        // Timber.e("OutgoingFileTransferJabberImpl msgUid: %s", id);
        msgUuid = id;

        // jabberTransfer is null for http file upload
        if (jabberTransfer != null) {
            bobInfoInit(file);
        }
    }

    /**
     * Cancels the file transfer.
     */
    @Override
    public void cancel() {
        mJabberFileTransfer.cancel();
    }

    /**
     * Get the transfer error
     *
     * @return FileTransfer.Error
     */
    public FileTransfer.Error getTransferError() {
        return mJabberFileTransfer.getError();
    }

    /**
     * Returns the number of bytes already sent to the recipient.
     *
     * @return the number of bytes already sent to the recipient.
     */
    @Override
    public long getTransferredBytes() {
        return mJabberFileTransfer.getBytesSent();
    }

    /**
     * The direction is outgoing.
     *
     * @return OUT.
     */
    public int getDirection() {
        return OUT;
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
     * The contact we are sending the file.
     *
     * @return the receiver.
     */
    public Contact getContact() {
        return mContact;
    }

    /**
     * The unique id that uniquely identity the record and in DB.
     *
     * @return the id.
     */
    public String getID() {
        return msgUuid;
    }

    private void bobInfoInit(File file) {
        bobInfo = null;
        if (file instanceof ThumbnailedFile) {
            ThumbnailedFile tnFile = (ThumbnailedFile) file;
            byte[] thumbnail = tnFile.getThumbnailData();

            if (thumbnail != null && thumbnail.length > 0) {
                BoBData bobData = new BoBData(tnFile.getThumbnailMimeType(), thumbnail, maxAge);

                BoBManager bobManager = BoBManager.getInstanceFor(mConnection);
                bobInfo = bobManager.addBoB(bobData);
            }
        }
    }

    /**
     * Removes previously added thumbnail request listener.
     */
    public void removeThumbnailHandler() {
        if (bobInfo != null) {
            BoBManager bobManager = BoBManager.getInstanceFor(mConnection);
            for (ContentId hash : bobInfo.getHashes()) {
                bobManager.removeBoB(hash);
            }
            bobInfo = null;
        }
    }
}
