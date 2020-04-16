/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.impl.protocol.jabber;

import net.java.sip.communicator.service.protocol.*;
import net.java.sip.communicator.service.protocol.event.FileTransferCreatedEvent;
import net.java.sip.communicator.service.protocol.event.FileTransferRequestEvent;

import org.jivesoftware.smack.SmackException;
import org.jivesoftware.smack.SmackException.NotConnectedException;
import org.jivesoftware.smack.XMPPException;
import org.jivesoftware.smackx.bob.BoBHash;
import org.jivesoftware.smackx.bob.BoBManager;
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
    private static ExecutorService thumbnailCollector = Executors.newSingleThreadExecutor();

    private String id;

    /**
     * The Jabber file transfer request.
     */
    private final FileTransferRequest fileTransferRequest;
    private final OperationSetFileTransferJabberImpl fileTransferOpSet;
    private final ProtocolProviderServiceJabberImpl jabberProvider;
    private Jid fromJid;
    private Contact sender;
    private byte[] thumbnail;

    /**
     * Creates an <tt>IncomingFileTransferRequestJabberImpl</tt> based on the given
     * <tt>fileTransferRequest</tt>, coming from the Jabber protocol.
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

        fromJid = fileTransferRequest.getRequestor();
        OperationSetPersistentPresenceJabberImpl opSetPersPresence
                = (OperationSetPersistentPresenceJabberImpl) pps.getOperationSet(OperationSetPersistentPresence.class);

        sender = opSetPersPresence.findContactByJid(fromJid);
        if (sender == null) {
            ChatRoom privateContactRoom = null;
            OperationSetMultiUserChatJabberImpl mucOpSet
                    = (OperationSetMultiUserChatJabberImpl) pps.getOperationSet(OperationSetMultiUserChat.class);

            if (mucOpSet != null)
                privateContactRoom = mucOpSet.getChatRoom(fromJid.asBareJid());

            if (privateContactRoom != null) {
                sender = opSetPersPresence.createVolatileContact(fromJid, true);
                privateContactRoom.updatePrivateContactPresenceStatus(sender);
            }
            // just create a volatile contact for new sender
            else {
                sender = opSetPersPresence.createVolatileContact(fromJid);
            }
        }
        this.id = String.valueOf(System.currentTimeMillis()) + hashCode();
    }

    /**
     * Returns the <tt>Contact</tt> making this request.
     *
     * @return the <tt>Contact</tt> making this request
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
     * Accepts the file and starts the transfer.
     *
     * @return a boolean : <code>false</code> if the transfer fails, <code>true</code> otherwise
     */
    @Override
    public FileTransfer acceptFile(File file)
    {
        AbstractFileTransfer incomingTransfer = null;

        IncomingFileTransfer jabberTransfer = fileTransferRequest.accept();
        try {
            incomingTransfer = new IncomingFileTransferJabberImpl(id, sender, file, jabberTransfer);
            FileTransferCreatedEvent event = new FileTransferCreatedEvent(incomingTransfer, new Date());
            fileTransferOpSet.fireFileTransferCreated(event);

            jabberTransfer.receiveFile(file);
            new OperationSetFileTransferJabberImpl.FileTransferProgressThread(jabberTransfer,
                    incomingTransfer, getFileSize()).start();
        } catch (IOException | SmackException e) {
            Timber.e(e, "Receiving file failed.");
        }
        return incomingTransfer;
    }

    /**
     * Refuses the file transfer request.
     */
    @Override
    public void rejectFile()
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
     * Requests the thumbnail from the peer and fire the incoming transfer request event.
     *
     * @param cid the thumbnail content-ID
     */
    public void fetchThumbnailAndNotify(final BoBHash cid)
    {
        final BoBManager bobManager = BoBManager.getInstanceFor(jabberProvider.getConnection());
        thumbnailCollector.submit(new Runnable()
        {
            @Override
            public void run()
            {
                Timber.d("Sending thumbnail request");
                try {
                    thumbnail = bobManager.requestBoB(fromJid, cid).getContent();
                } catch (SmackException.NotLoggedInException
                        | SmackException.NoResponseException
                        | XMPPException.XMPPErrorException
                        | NotConnectedException
                        | InterruptedException e) {
                    Timber.e(e, "Could not get thumbnail");
                } finally {
                    // Notify the global listener that a request has arrived.
                    fileTransferOpSet.fireFileTransferRequest(IncomingFileTransferRequestJabberImpl.this);
                }
            }
        });
    }
}
