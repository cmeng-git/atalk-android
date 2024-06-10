/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.impl.protocol.jabber;

import java.io.File;
import java.io.IOException;
import java.util.Date;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import net.java.sip.communicator.service.protocol.ChatRoom;
import net.java.sip.communicator.service.protocol.Contact;
import net.java.sip.communicator.service.protocol.FileTransfer;
import net.java.sip.communicator.service.protocol.IMessage;
import net.java.sip.communicator.service.protocol.IncomingFileTransferRequest;
import net.java.sip.communicator.service.protocol.OperationFailedException;
import net.java.sip.communicator.service.protocol.OperationSetMultiUserChat;
import net.java.sip.communicator.service.protocol.OperationSetPersistentPresence;
import net.java.sip.communicator.service.protocol.event.FileTransferCreatedEvent;
import net.java.sip.communicator.service.protocol.event.FileTransferRequestEvent;
import net.java.sip.communicator.util.ConfigurationUtils;

import org.atalk.android.gui.chat.filetransfer.FileReceiveConversation;
import org.jivesoftware.smack.SmackException;
import org.jivesoftware.smack.SmackException.NotConnectedException;
import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.XMPPException;
import org.jivesoftware.smackx.bob.BoBManager;
import org.jivesoftware.smackx.bob.ContentId;
import org.jivesoftware.smackx.filetransfer.FileTransferRequest;
import org.jivesoftware.smackx.filetransfer.IncomingFileTransfer;
import org.jivesoftware.smackx.thumbnail.element.Thumbnail;
import org.jxmpp.jid.Jid;

import timber.log.Timber;

/**
 * Jabber implementation of the incoming file transfer request
 *
 * @author Nicolas Riegel
 * @author Yana Stamcheva
 * @author Eng Chong Meng
 */
public class IncomingFileTransferRequestJabberImpl implements IncomingFileTransferRequest {
    /**
     * Thread to fetch thumbnails in the background, one at a time
     */
    private static final ExecutorService thumbnailCollector = Executors.newSingleThreadExecutor();

    /**
     * The Jabber file transfer request.
     */
    private final FileTransferRequest fileTransferRequest;
    private final OperationSetFileTransferJabberImpl fileTransferOpSet;
    private IncomingFileTransfer mIncomingFileTransfer;
    private IncomingFileTransferJabberImpl mFileTransfer;

    private final XMPPConnection mConnection;
    private final Jid remoteJid;
    private Contact mSender;
    private final String mId;

    private File mFile;
    private final Thumbnail thumbnailElement;
    private byte[] thumbnail = null;
    FileReceiveConversation mCallback = null;

    /*
     * Transfer file encryption type; Legacy ByteStream transfer supports only ENCRYPTION_NONE
     */
    private final int mEncryption = IMessage.ENCRYPTION_NONE;

    /**
     * Creates an <code>IncomingFileTransferRequestJabberImpl</code> based on the given
     * <code>fileTransferRequest</code>, coming from the Jabber protocol.
     *
     * @param pps the protocol provider
     * @param fileTransferOpSet file transfer operation set
     * @param fileTransferRequest the request coming from the Jabber protocol
     */
    public IncomingFileTransferRequestJabberImpl(ProtocolProviderServiceJabberImpl pps,
            OperationSetFileTransferJabberImpl fileTransferOpSet, FileTransferRequest fileTransferRequest) {
        mConnection = pps.getConnection();
        this.fileTransferOpSet = fileTransferOpSet;
        this.fileTransferRequest = fileTransferRequest;

        mId = String.valueOf(System.currentTimeMillis()) + hashCode();
        thumbnailElement = fileTransferRequest.getThumbnail();
        remoteJid = fileTransferRequest.getRequestor();
        OperationSetPersistentPresenceJabberImpl opSetPersPresence
                = (OperationSetPersistentPresenceJabberImpl) pps.getOperationSet(OperationSetPersistentPresence.class);

        mSender = opSetPersPresence.findContactByJid(remoteJid);
        if (mSender == null) {
            ChatRoom privateContactRoom = null;
            OperationSetMultiUserChatJabberImpl mucOpSet
                    = (OperationSetMultiUserChatJabberImpl) pps.getOperationSet(OperationSetMultiUserChat.class);

            if (mucOpSet != null)
                privateContactRoom = mucOpSet.getChatRoom(remoteJid.asBareJid());

            if (privateContactRoom != null) {
                mSender = opSetPersPresence.createVolatileContact(remoteJid, true);
                privateContactRoom.updatePrivateContactPresenceStatus(mSender);
            }
            else {
                // just create a volatile contact for new sender
                mSender = opSetPersPresence.createVolatileContact(remoteJid);
            }
        }
    }

    @Override
    public FileTransfer onPrepare(File file) {
        mFile = file;
        mIncomingFileTransfer = fileTransferRequest.accept();
        mFileTransfer = new IncomingFileTransferJabberImpl(mId, mSender, file, mIncomingFileTransfer);
        return mFileTransfer;
    }

    /**
     * Returns the <code>Contact</code> making this request.
     *
     * @return the <code>Contact</code> making this request
     */
    @Override
    public Contact getSender() {
        return mSender;
    }

    /**
     * Returns the description of the file corresponding to this request.
     *
     * @return the description of the file corresponding to this request
     */
    @Override
    public String getFileDescription() {
        return fileTransferRequest.getDescription();
    }

    public String getMimeType() {
        return fileTransferRequest.getMimeType();
    }

    /**
     * Returns the name of the file corresponding to this request.
     *
     * @return the name of the file corresponding to this request
     */
    @Override
    public String getFileName() {
        return fileTransferRequest.getFileName();
    }

    /**
     * Returns the size of the file corresponding to this request.
     *
     * @return the size of the file corresponding to this request
     */
    @Override
    public long getFileSize() {
        return fileTransferRequest.getFileSize();
    }

    /**
     * The file transfer unique id.
     *
     * @return the id.
     */
    @Override
    public String getID() {
        return mId;
    }

    /**
     * Return the encryption of the incoming file corresponding to this FileTransfer.
     *
     * @return the encryption of the file corresponding to this request
     */
    public int getEncryptionType() {
        return mEncryption;
    }

    /**
     * Returns the thumbnail contained in this request.
     * Proceed to request for the available thumbnail if auto accept file not permitted
     *
     * @param callback the caller requesting the thumbnail
     *
     * @return the thumbnail contained in this request
     */
    @Override
    public byte[] getThumbnail(FileReceiveConversation callback) {
        if (thumbnail == null && thumbnailElement != null) {
            mCallback = callback;
            boolean isAutoAccept = ConfigurationUtils.isAutoAcceptFile(mFile.length());
            if (!isAutoAccept && ConfigurationUtils.isSendThumbnail()) {
                fetchThumbnailAndNotify(thumbnailElement.getCid());
            }
        }
        return thumbnail;
    }

    /**
     * Accepts the file and starts the transfer.
     */
    @Override
    public void acceptFile() {
        try {
            FileTransferCreatedEvent event = new FileTransferCreatedEvent(mFileTransfer, new Date());
            fileTransferOpSet.fireFileTransferCreated(event);

            mIncomingFileTransfer.receiveFile(mFile);
            new OperationSetFileTransferJabberImpl.FileTransferProgressThread(mIncomingFileTransfer,
                    mFileTransfer, getFileSize()).start();
        } catch (IOException | SmackException e) {
            Timber.e(e, "Receiving file failed.");
        }
    }

    /**
     * Declines the incoming file transfer request.
     */
    @Override
    public void declineFile()
            throws OperationFailedException {
        try {
            fileTransferRequest.reject();
        } catch (NotConnectedException | InterruptedException e) {
            throw new OperationFailedException("Could not reject file transfer",
                    OperationFailedException.GENERAL_ERROR, e);
        }

        fileTransferOpSet.fireFileTransferRequestRejected(
                new FileTransferRequestEvent(fileTransferOpSet, this, new Date()));
    }

    /**
     * Request the thumbnail from the peer, allow extended smack timeout on thumbnail request.
     * Then fire the incoming transfer request event to start the actual incoming file transfer.
     *
     * @param cid the thumbnail content-Id
     */
    private void fetchThumbnailAndNotify(final ContentId cid) {
        final BoBManager bobManager = BoBManager.getInstanceFor(mConnection);
        thumbnailCollector.submit(() -> {
            try {
                // Not required if ejabberd server does not limit transfer rate in Shaper#normal
                // mConnection.setReplyTimeout(ProtocolProviderServiceJabberImpl.SMACK_REPLY_EXTENDED_TIMEOUT_20);
                thumbnail = bobManager.requestBoB(remoteJid, cid).getContent();
            } catch (SmackException.NotLoggedInException
                     | SmackException.NoResponseException
                     | XMPPException.XMPPErrorException
                     | NotConnectedException
                     | InterruptedException e) {
                Timber.e("Error in requesting for thumbnail: %s", e.getMessage());
            } finally {
                // mConnection.setReplyTimeout(ProtocolProviderServiceJabberImpl.SMACK_DEFAULT_REPLY_TIMEOUT);
                if (mCallback != null)
                    mCallback.showThumbnail(thumbnail);
            }
        });
    }
}
