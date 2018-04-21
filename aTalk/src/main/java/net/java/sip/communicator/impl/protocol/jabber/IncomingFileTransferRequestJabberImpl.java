/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.impl.protocol.jabber;

import net.java.sip.communicator.impl.protocol.jabber.extensions.thumbnail.packet.ThumbnailIQ;
import net.java.sip.communicator.service.protocol.AbstractFileTransfer;
import net.java.sip.communicator.service.protocol.ChatRoom;
import net.java.sip.communicator.service.protocol.Contact;
import net.java.sip.communicator.service.protocol.FileTransfer;
import net.java.sip.communicator.service.protocol.IncomingFileTransferRequest;
import net.java.sip.communicator.service.protocol.OperationSetMultiUserChat;
import net.java.sip.communicator.service.protocol.OperationSetPersistentPresence;
import net.java.sip.communicator.service.protocol.event.FileTransferCreatedEvent;
import net.java.sip.communicator.service.protocol.event.FileTransferRequestEvent;
import net.java.sip.communicator.util.Logger;

import org.jivesoftware.smack.SmackException;
import org.jivesoftware.smack.SmackException.NotConnectedException;
import org.jivesoftware.smack.StanzaListener;
import org.jivesoftware.smack.filter.AndFilter;
import org.jivesoftware.smack.filter.IQTypeFilter;
import org.jivesoftware.smack.filter.StanzaExtensionFilter;
import org.jivesoftware.smack.filter.StanzaFilter;
import org.jivesoftware.smack.filter.StanzaTypeFilter;
import org.jivesoftware.smack.packet.Stanza;
import org.jivesoftware.smackx.filetransfer.FileTransferRequest;
import org.jivesoftware.smackx.filetransfer.IncomingFileTransfer;
import org.jxmpp.jid.Jid;

import java.io.File;
import java.io.IOException;
import java.util.Date;

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
     * The logger for this class.
     */
    private static final Logger logger = Logger
            .getLogger(IncomingFileTransferRequestJabberImpl.class);

    private String id;

    /**
     * The Jabber file transfer request.
     */
    private final FileTransferRequest fileTransferRequest;
    private final OperationSetFileTransferJabberImpl fileTransferOpSet;
    private final ProtocolProviderServiceJabberImpl jabberProvider;
    private Contact sender;
    private String thumbnailCid;
    private byte[] thumbnail;

//    private static final StanzaFilter THUMBNAIL_RESULT = new AndFilter(StanzaTypeFilter.IQ, IQTypeFilter.RESULT,
//            new StanzaExtensionFilter(ThumbnailIQ.ELEMENT, ThumbnailIQ.NAMESPACE));
    private static final StanzaFilter THUMBNAIL_RESULT = new AndFilter(StanzaTypeFilter.IQ, IQTypeFilter.RESULT);

    /**
     * Creates an <tt>IncomingFileTransferRequestJabberImpl</tt> based on the given
     * <tt>fileTransferRequest</tt>, coming from the Jabber protocol.
     *
     * @param jabberProvider the protocol provider
     * @param fileTransferOpSet file transfer operation set
     * @param fileTransferRequest the request coming from the Jabber protocol
     */
    public IncomingFileTransferRequestJabberImpl(ProtocolProviderServiceJabberImpl jabberProvider,
            OperationSetFileTransferJabberImpl fileTransferOpSet,
            FileTransferRequest fileTransferRequest)
    {
        this.jabberProvider = jabberProvider;
        this.fileTransferOpSet = fileTransferOpSet;
        this.fileTransferRequest = fileTransferRequest;

        Jid fromUserID = fileTransferRequest.getRequestor();

        OperationSetPersistentPresenceJabberImpl opSetPersPresence = (OperationSetPersistentPresenceJabberImpl) jabberProvider
                .getOperationSet(OperationSetPersistentPresence.class);

        sender = opSetPersPresence.findContactByID(fromUserID);
        if (sender == null) {
            ChatRoom privateContactRoom = null;
            OperationSetMultiUserChatJabberImpl mucOpSet = (OperationSetMultiUserChatJabberImpl) jabberProvider
                    .getOperationSet(OperationSetMultiUserChat.class);

            if (mucOpSet != null)
                privateContactRoom = mucOpSet.getChatRoom(fromUserID.asBareJid());
            if (privateContactRoom != null) {
                sender = ((OperationSetPersistentPresenceJabberImpl) jabberProvider
                        .getOperationSet(OperationSetPersistentPresence.class)).createVolatileContact(
                        fromUserID, true);
                privateContactRoom.updatePrivateContactPresenceStatus(sender);
            }
        }
        this.id = String.valueOf(System.currentTimeMillis()) + String.valueOf(hashCode());
    }

    /**
     * Returns the <tt>Contact</tt> making this request.
     *
     * @return the <tt>Contact</tt> making this request
     */
    public Contact getSender()
    {
        return sender;
    }

    /**
     * Returns the description of the file corresponding to this request.
     *
     * @return the description of the file corresponding to this request
     */
    public String getFileDescription()
    {
        return fileTransferRequest.getDescription();
    }

    /**
     * Returns the name of the file corresponding to this request.
     *
     * @return the name of the file corresponding to this request
     */
    public String getFileName()
    {
        return fileTransferRequest.getFileName();
    }

    /**
     * Returns the size of the file corresponding to this request.
     *
     * @return the size of the file corresponding to this request
     */
    public long getFileSize()
    {
        return fileTransferRequest.getFileSize();
    }

    /**
     * Accepts the file and starts the transfer.
     *
     * @return a boolean : <code>false</code> if the transfer fails, <code>true</code> otherwise
     */
    public FileTransfer acceptFile(File file)
    {
        AbstractFileTransfer incomingTransfer = null;

        IncomingFileTransfer jabberTransfer = fileTransferRequest.accept();
        try {
            incomingTransfer = new IncomingFileTransferJabberImpl(id, sender, file, jabberTransfer);
            FileTransferCreatedEvent event = new FileTransferCreatedEvent(incomingTransfer,
                    new Date());
            fileTransferOpSet.fireFileTransferCreated(event);
            try {
                jabberTransfer.recieveFile(file);
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
            new OperationSetFileTransferJabberImpl.FileTransferProgressThread(jabberTransfer,
                    incomingTransfer, getFileSize()).start();
        } catch (SmackException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        return incomingTransfer;
    }

    /**
     * Refuses the file transfer request.
     */
    public void rejectFile()
    {
        try {
            fileTransferRequest.reject();
        } catch (NotConnectedException | InterruptedException e) {
            e.printStackTrace();
        }
        fileTransferOpSet.fireFileTransferRequestRejected(new FileTransferRequestEvent(
                fileTransferOpSet, this, new Date()));
    }

    /**
     * The unique id.
     *
     * @return the id.
     */
    public String getID()
    {
        return id;
    }

    /**
     * Returns the thumbnail contained in this request.
     *
     * @return the thumbnail contained in this request
     */
    public byte[] getThumbnail()
    {
        return thumbnail;
    }

    /**
     * Sets the thumbnail content-ID.
     *
     * @param cid the thumbnail content-ID
     */
    public void createThumbnailListeners(String cid)
    {
        this.thumbnailCid = cid;

        if (jabberProvider.getConnection() != null) {
            jabberProvider.getConnection().addAsyncStanzaListener(new ThumbnailResponseListener(), THUMBNAIL_RESULT);
        }
    }

    /**
     * The <tt>ThumbnailResponseListener</tt> listens for events triggered by the reception of a
     * <tt>BoB</tt> packet. The packet is examined and a file transfer request event is
     * fired when the thumbnail is extracted.
     */
    private class ThumbnailResponseListener implements StanzaListener
    {
        public void processStanza(Stanza stanza)
        {
            // If this is not an IQ packet, we're not interested.
            if (!(stanza instanceof ThumbnailIQ))
                return;

            if (logger.isDebugEnabled())
                logger.debug("Thumbnail response received.");

            ThumbnailIQ thumbnailResponse = (ThumbnailIQ) stanza;
            if ((thumbnailResponse.getCid() != null)
                    && thumbnailResponse.getCid().equals(thumbnailCid)) {
                thumbnail = thumbnailResponse.getData();

                // Create an event associated to this global request.
                FileTransferRequestEvent fileTransferRequestEvent = new FileTransferRequestEvent(
                        fileTransferOpSet, IncomingFileTransferRequestJabberImpl.this, new Date());

                // Notify the global listener that a request has arrived.
                fileTransferOpSet.fireFileTransferRequest(fileTransferRequestEvent);
            }
            else {
                // TODO: RETURN <item-not-found/>
            }
            if (jabberProvider.getConnection() != null) {
                jabberProvider.getConnection().removeAsyncStanzaListener(this);
            }
        }
    }
}
