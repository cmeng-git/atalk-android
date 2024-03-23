/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.impl.protocol.jabber;

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

import org.jivesoftware.smack.SmackException;
import org.jivesoftware.smack.SmackException.NotConnectedException;
import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.XMPPException;
import org.jivesoftware.smackx.bob.BoBManager;
import org.jivesoftware.smackx.bob.ContentId;
import org.jivesoftware.smackx.filetransfer.FileTransferRequest;
import org.jivesoftware.smackx.filetransfer.IncomingFileTransfer;
import org.jxmpp.jid.Jid;

import java.io.File;
import java.io.IOException;
import java.util.Date;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import timber.log.Timber;

/**
 * Jabber implementation of the incoming file transfer request
 *
 * @author Nicolas Riegel
 * @author Yana Stamcheva
 * @author Eng Chong Meng
 */

public class IncomingFileTransferRequestJabberImpl implements IncomingFileTransferRequest
{
    /**
     * Thread to fetch thumbnails in the background, one at a time
     */
    private static final ExecutorService thumbnailCollector = Executors.newSingleThreadExecutor();

    private final String id;

    /**
     * The Jabber file transfer request.
     */
    private final FileTransferRequest fileTransferRequest;
    private final OperationSetFileTransferJabberImpl fileTransferOpSet;
    private final ProtocolProviderServiceJabberImpl jabberProvider;
    private final Jid remoteJid;

    private IncomingFileTransfer mIncomingFileTransfer;
    private IncomingFileTransferJabberImpl mFileTransfer;
    private File mFile;
    private Contact sender;
    private byte[] thumbnail;

    /*
     * Transfer file encryption type.
     */
    protected int mEncryption;

    /**
     * Creates an <code>IncomingFileTransferRequestJabberImpl</code> based on the given
     * <code>fileTransferRequest</code>, coming from the Jabber protocol.
     *
     * @param pps the protocol provider
     * @param fileTransferOpSet file transfer operation set
     * @param fileTransferRequest the request coming from the Jabber protocol
     */
    public IncomingFileTransferRequestJabberImpl(ProtocolProviderServiceJabberImpl pps,
            OperationSetFileTransferJabberImpl fileTransferOpSet, FileTransferRequest fileTransferRequest)
    {
        this.jabberProvider = pps;
        this.fileTransferOpSet = fileTransferOpSet;
        this.fileTransferRequest = fileTransferRequest;

        // Legacy ByteStream transfer supports only ENCRYPTION_NONE
        mEncryption = IMessage.ENCRYPTION_NONE;

        remoteJid = fileTransferRequest.getRequestor();
        OperationSetPersistentPresenceJabberImpl opSetPersPresence
                = (OperationSetPersistentPresenceJabberImpl) pps.getOperationSet(OperationSetPersistentPresence.class);

        sender = opSetPersPresence.findContactByJid(remoteJid);
        if (sender == null) {
            ChatRoom privateContactRoom = null;
            OperationSetMultiUserChatJabberImpl mucOpSet
                    = (OperationSetMultiUserChatJabberImpl) pps.getOperationSet(OperationSetMultiUserChat.class);

            if (mucOpSet != null)
                privateContactRoom = mucOpSet.getChatRoom(remoteJid.asBareJid());

            if (privateContactRoom != null) {
                sender = opSetPersPresence.createVolatileContact(remoteJid, true);
                privateContactRoom.updatePrivateContactPresenceStatus(sender);
            }
            // just create a volatile contact for new sender
            else {
                sender = opSetPersPresence.createVolatileContact(remoteJid);
            }
        }
        this.id = String.valueOf(System.currentTimeMillis()) + hashCode();
    }

    @Override
    public FileTransfer onPrepare(File file)
    {
        mFile = file;
        mIncomingFileTransfer = fileTransferRequest.accept();
        mFileTransfer = new IncomingFileTransferJabberImpl(id, sender, file, mIncomingFileTransfer);
        return mFileTransfer;
    }

    /**
     * Returns the <code>Contact</code> making this request.
     *
     * @return the <code>Contact</code> making this request
     */
    @Override
    public Contact getSender()
    {
        return sender;
    }

    /**
     * Returns the description of the file corresponding to this request.
     *
     * @return the description of the file corresponding to this request
     */
    @Override
    public String getFileDescription()
    {
        return fileTransferRequest.getDescription();
    }

    public String getMimeType()
    {
        return fileTransferRequest.getMimeType();
    }

    /**
     * Returns the name of the file corresponding to this request.
     *
     * @return the name of the file corresponding to this request
     */
    @Override
    public String getFileName()
    {
        return fileTransferRequest.getFileName();
    }

    /**
     * Returns the size of the file corresponding to this request.
     *
     * @return the size of the file corresponding to this request
     */
    @Override
    public long getFileSize()
    {
        return fileTransferRequest.getFileSize();
    }

    /**
     * The unique id.
     *
     * @return the id.
     */
    @Override
    public String getID()
    {
        return id;
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
     *
     * @return the thumbnail contained in this request
     */
    @Override
    public byte[] getThumbnail()
    {
        return thumbnail;
    }

    /**
     * Accepts the file and starts the transfer.
     */
    @Override
    public void acceptFile()
    {
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
            throws OperationFailedException
    {
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
    public void fetchThumbnailAndNotify(final ContentId cid)
    {
        XMPPConnection connection = jabberProvider.getConnection();

        final BoBManager bobManager = BoBManager.getInstanceFor(connection);
        thumbnailCollector.submit(() -> {
            try {
                thumbnail = bobManager.requestBoB(remoteJid, cid).getContent();
            } catch (SmackException.NotLoggedInException
                    | SmackException.NoResponseException
                    | XMPPException.XMPPErrorException
                    | NotConnectedException
                    | InterruptedException e) {
                Timber.e("Error in requesting for thumbnail: %s", e.getMessage());
            } finally {
                // Notify the global listener that a request has arrived.
                fileTransferOpSet.fireFileTransferRequest(IncomingFileTransferRequestJabberImpl.this);
            }
        });
    }
}
