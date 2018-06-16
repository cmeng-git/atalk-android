/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */

package net.java.sip.communicator.impl.protocol.jabber;

import net.java.sip.communicator.impl.protocol.jabber.extensions.si.packet.SiThumb.FileElement;
import net.java.sip.communicator.impl.protocol.jabber.extensions.si.provider.SiThumbProvider;
import net.java.sip.communicator.impl.protocol.jabber.extensions.thumbnail.ThumbnailElement;
import net.java.sip.communicator.impl.protocol.jabber.extensions.thumbnail.packet.ThumbnailIQ;
import net.java.sip.communicator.impl.protocol.jabber.extensions.thumbnail.provider.ThumbnailProvider;
import net.java.sip.communicator.service.protocol.*;
import net.java.sip.communicator.service.protocol.FileTransfer;
import net.java.sip.communicator.service.protocol.event.*;
import net.java.sip.communicator.service.protocol.event.FileTransferListener;
import net.java.sip.communicator.service.protocol.jabberconstants.JabberStatusEnum;
import net.java.sip.communicator.util.Logger;

import org.atalk.android.gui.chat.filetransfer.FileTransferConversation;
import org.jivesoftware.smack.*;
import org.jivesoftware.smack.SmackException.NotConnectedException;
import org.jivesoftware.smack.XMPPException.XMPPErrorException;
import org.jivesoftware.smack.iqrequest.AbstractIqRequestHandler;
import org.jivesoftware.smack.packet.*;
import org.jivesoftware.smack.packet.XMPPError.Condition;
import org.jivesoftware.smack.provider.ProviderManager;
import org.jivesoftware.smack.roster.Roster;
import org.jivesoftware.smack.tcp.XMPPTCPConnection;
import org.jivesoftware.smackx.filetransfer.FileTransfer.Status;
import org.jivesoftware.smackx.filetransfer.*;
import org.jivesoftware.smackx.filetransfer.OutgoingFileTransfer.NegotiationProgress;
import org.jivesoftware.smackx.si.packet.StreamInitiation;
import org.jxmpp.jid.EntityFullJid;
import org.jxmpp.jid.Jid;
import org.jxmpp.jid.impl.JidCreate;
import org.jxmpp.jid.parts.Domainpart;
import org.jxmpp.stringprep.XmppStringprepException;

import java.io.File;
import java.io.OutputStream;
import java.util.*;

/**
 * The Jabber implementation of the <tt>OperationSetFileTransfer</tt> interface.
 *
 * @author Gregory Bande
 * @author Nicolas Riegel
 * @author Yana Stamcheva
 * @author Eng Chong Meng
 */
public class OperationSetFileTransferJabberImpl implements OperationSetFileTransfer, NegotiationProgress
{
    /**
     * The logger for this class.
     */
    private static final Logger logger = Logger.getLogger(OperationSetFileTransferJabberImpl.class);

    // Change max to 20 MBytes. Original max 2GBytes i.e. 2147483648l = 2048*1024*1024;
    private final long MAX_FILE_LENGTH = 21474836;

    /**
     * The provider that created us.
     */
    private final ProtocolProviderServiceJabberImpl jabberProvider;

    /**
     * An active instance of the opSetPersPresence operation set.
     */
    private OperationSetPersistentPresenceJabberImpl opSetPersPresence = null;

    /**
     * The Jabber file transfer manager.
     */
    private FileTransferManager manager = null;
    private OutgoingFileTransferJabberImpl outgoingTransfer = null;

    /**
     * The Jabber file transfer IQRequest Handler.
     */
    private IQRequestHandler iqRequestHandler = null;

    /**
     * A list of listeners registered for file transfer events.
     */
    final private Vector<FileTransferListener> fileTransferListeners = new Vector<>();

    // Register file transfer features on every established connection to make sure we register
    // them before creating our ServiceDiscoveryManager

    static {
        XMPPConnectionRegistry.addConnectionCreationListener(new ConnectionCreationListener()
        {
            public void connectionCreated(XMPPConnection connection)
            {
                FileTransferNegotiator.getInstanceFor(connection);
            }
        });
    }

    /**
     * Constructor
     *
     * @param provider is the provider that created us
     */
    public OperationSetFileTransferJabberImpl(ProtocolProviderServiceJabberImpl provider)
    {
        this.jabberProvider = provider;
        provider.addRegistrationStateChangeListener(new RegistrationStateListener());

        // use ibb & socket5 for file transfer. Jingle not implemented yet
        FileTransferNegotiator.IBB_ONLY = false;
    }

    /**
     * Sends a file transfer request to the given <tt>toContact</tt>.
     *
     * @param toContact the contact that should receive the file
     * @param file file to send
     * @return the transfer object
     */
    public FileTransfer sendFile(Contact toContact, File file)
            throws IllegalStateException, IllegalArgumentException, OperationNotSupportedException
    {
        return sendFile(toContact, file, null);
    }

    /**
     * Sends a file transfer request to the given <tt>toContact</tt>.
     *
     * @param toContact the contact that should receive the file
     * @param file file to send
     * @param gw special gateway to be used for receiver if its jid misses the domain part
     * @return the transfer object
     */
    FileTransfer sendFile(Contact toContact, File file, String gw)
            throws IllegalStateException, IllegalArgumentException, OperationNotSupportedException
    {
        assertConnected();
        if (file.length() > getMaximumFileLength())
            throw new IllegalArgumentException("File length exceeds the allowed one for this protocol");

        Jid fullJid = null;
        // Find the jid of the contact which support file transfer and is with highest priority if
        // more than one found if we have equals priorities choose the one that is more available
        OperationSetMultiUserChat mucOpSet = jabberProvider.getOperationSet(OperationSetMultiUserChat.class);
        if (mucOpSet != null && mucOpSet.isPrivateMessagingContact(toContact.getAddress())) {
            fullJid = toContact.getJid();
        }
        else {
            List<Presence> iter
                    = Roster.getInstanceFor(jabberProvider.getConnection()).getPresences(toContact.getJid().asBareJid());
            int bestPriority = -1;
            PresenceStatus jabberStatus = null;

            for (Presence presence : iter) {
                if (jabberProvider.isFeatureListSupported(presence.getFrom(),
                        StreamInitiation.NAMESPACE, StreamInitiation.NAMESPACE + "/profile/file-transfer")) {

                    int priority = (presence.getPriority() == Integer.MIN_VALUE) ? 0 : presence.getPriority();
                    if (priority > bestPriority) {
                        bestPriority = priority;
                        fullJid = presence.getFrom();
                        jabberStatus = OperationSetPersistentPresenceJabberImpl.jabberStatusToPresenceStatus(presence,
                                jabberProvider);
                    }
                    else if (priority == bestPriority && jabberStatus != null) {
                        PresenceStatus tempStatus = OperationSetPersistentPresenceJabberImpl
                                .jabberStatusToPresenceStatus(presence, jabberProvider);
                        if (tempStatus.compareTo(jabberStatus) > 0) {
                            fullJid = presence.getFrom();
                            jabberStatus = tempStatus;
                        }
                    }
                }
            }
        }

        // First we check if file transfer is at all supported for this contact.
        if (fullJid == null) {
            throw new OperationNotSupportedException("Contact client or server does not support file transfers.");
        }
        if (!(fullJid instanceof Jid)) {
            try {
                fullJid = JidCreate.bareFrom(fullJid.getLocalpartOrNull(), Domainpart.from(gw));
            } catch (XmppStringprepException e) {
                e.printStackTrace();
            }
        }

        OutgoingFileTransfer transfer = manager.createOutgoingFileTransfer((EntityFullJid) fullJid);
        outgoingTransfer = new OutgoingFileTransferJabberImpl(toContact, file, transfer, jabberProvider);

        // Notify all interested listeners that a file transfer has been created.
        FileTransferCreatedEvent event = new FileTransferCreatedEvent(outgoingTransfer, new Date());
        fireFileTransferCreated(event);

        // start file transferring with callback on status changes
        // transfer.sendFile(file.getName(), file.length(), "Sending file", this);
        try {
            transfer.sendFile(file, "Sending file");
        } catch (SmackException e) {
            e.printStackTrace();
        }
        // Start the status and progress thread.
        new FileTransferProgressThread(transfer, outgoingTransfer).start();
        return outgoingTransfer;
    }

    /**
     * Sends a file transfer request to the given <tt>toContact</tt> by specifying the local and
     * remote file path and the <tt>fromContact</tt>, sending the file.
     *
     * @param toContact the contact that should receive the file
     * @param fromContact the contact sending the file
     * @param remotePath the remote file path
     * @param localPath the local file path
     * @return the transfer object
     */
    public FileTransfer sendFile(Contact toContact, Contact fromContact, String remotePath, String localPath)
            throws IllegalStateException, IllegalArgumentException, OperationNotSupportedException
    {
        return this.sendFile(toContact, new File(localPath));
    }

    /**
     * Adds the given <tt>FileTransferListener</tt> that would listen for file transfer requests
     * created file transfers.
     *
     * @param listener the <tt>FileTransferListener</tt> to add
     */
    public void addFileTransferListener(FileTransferListener listener)
    {
        synchronized (fileTransferListeners) {
            if (!fileTransferListeners.contains(listener)) {
                this.fileTransferListeners.add(listener);
            }
        }
    }

    /**
     * Removes the given <tt>FileTransferListener</tt> that listens for file transfer requests and
     * created file transfers.
     *
     * @param listener the <tt>FileTransferListener</tt> to remove
     */
    public void removeFileTransferListener(FileTransferListener listener)
    {
        synchronized (fileTransferListeners) {
            this.fileTransferListeners.remove(listener);
        }
    }

    /**
     * Utility method throwing an exception if the stack is not properly initialized.
     *
     * @throws java.lang.IllegalStateException if the underlying stack is not registered and initialized.
     */
    private void assertConnected()
            throws IllegalStateException
    {
        if (jabberProvider == null)
            throw new IllegalStateException("The provider must be non-null and signed in before being able to send a file.");
        else if (!jabberProvider.isRegistered()) {
            // if we are not registered but the current status is online change the current status
            if (opSetPersPresence.getPresenceStatus().isOnline()) {
                opSetPersPresence.fireProviderStatusChangeEvent(opSetPersPresence.getPresenceStatus(),
                        jabberProvider.getJabberStatusEnum().getStatus(JabberStatusEnum.OFFLINE));
            }
            throw new IllegalStateException("The provider must be signed in before being able to send a file.");
        }
    }

    /**
     * Returns the maximum file length supported by the protocol in bytes. Supports up to 2GB.
     *
     * @return the file length that is supported.
     */
    public long getMaximumFileLength()
    {
        return MAX_FILE_LENGTH;
    }

    /**
     * Our listener that will tell us when we're registered to
     */
    private class RegistrationStateListener implements RegistrationStateChangeListener
    {
        /**
         * The method is called by a ProtocolProvider implementation whenever a change in the
         * registration state of the corresponding provider had occurred.
         *
         * @param evt ProviderStatusChangeEvent the event describing the status change.
         */
        public void registrationStateChanged(RegistrationStateChangeEvent evt)
        {
            XMPPTCPConnection connection = jabberProvider.getConnection();
            if (evt.getNewState() == RegistrationState.REGISTERED) {
                opSetPersPresence = (OperationSetPersistentPresenceJabberImpl)
                        jabberProvider.getOperationSet(OperationSetPersistentPresence.class);

                // Create the Jabber IQRequestHandler; initialized the manager for others to use
                manager = FileTransferManager.getInstanceFor(connection);
                ProviderManager.addIQProvider(StreamInitiation.ELEMENT, StreamInitiation.NAMESPACE, new SiThumbProvider());
                ProviderManager.addIQProvider(ThumbnailIQ.ELEMENT, ThumbnailIQ.NAMESPACE, new ThumbnailProvider());

                iqRequestHandler = new IQRequestHandler();
                connection.registerIQRequestHandler(new IQRequestHandler());
            }
            else if (evt.getNewState() == RegistrationState.UNREGISTERED) {
                if ((connection != null) && (iqRequestHandler != null)) {
                    connection.unregisterIQRequestHandler(iqRequestHandler);
                }
                ProviderManager.removeIQProvider(StreamInitiation.ELEMENT, StreamInitiation.NAMESPACE);
                ProviderManager.removeIQProvider(ThumbnailIQ.ELEMENT, ThumbnailIQ.NAMESPACE);

                iqRequestHandler = null;
                manager = null;
            }
        }
    }

    /**
     * Handler for Jabber incoming file transfer request.
     */
    private class IQRequestHandler extends AbstractIqRequestHandler
    {
        // setup for Si FileTransferRequest event
        protected IQRequestHandler()
        {
            super(StreamInitiation.ELEMENT, StreamInitiation.NAMESPACE, IQ.Type.set, IQRequestHandler.Mode.async);
        }

        @Override
        public IQ handleIQRequest(IQ packet)
        {
            if (!(packet instanceof StreamInitiation))
                return null;

            if (logger.isDebugEnabled())
                logger.debug("Received incoming Jabber file transfer request.");

            StreamInitiation streamInitiation = (StreamInitiation) packet;
            FileTransferRequest jabberRequest = new FileTransferRequest(manager, streamInitiation);

            // Create a global incoming file transfer request.
            IncomingFileTransferRequestJabberImpl incomingFileTransferRequest = new IncomingFileTransferRequestJabberImpl(
                    jabberProvider, OperationSetFileTransferJabberImpl.this, jabberRequest);

            // cmeng - Send thumbnail request if advertised in streamInitiation packet and the feature is enabled
            org.jivesoftware.smackx.si.packet.StreamInitiation.File file = streamInitiation.getFile();

            boolean isThumbnailFile = false;

            if ((file instanceof FileElement) && FileTransferConversation.FT_THUMBNAIL_ENABLE) {
                ThumbnailElement thumbnailElement = ((FileElement) file).getThumbnailElement();

                if ((thumbnailElement != null) && (thumbnailElement.getCid() != null)) {
                    isThumbnailFile = true;
                    incomingFileTransferRequest.createThumbnailListeners(thumbnailElement.getCid());
                    ThumbnailIQ thumbnailRequest = new ThumbnailIQ(streamInitiation.getTo(),
                            streamInitiation.getFrom(), thumbnailElement.getCid(), IQ.Type.get);

                    if (logger.isDebugEnabled())
                        logger.debug("Sending thumbnail request:" + thumbnailRequest.toXML());
                    try {
                        jabberProvider.getConnection().sendStanza(thumbnailRequest);
                    } catch (NotConnectedException | InterruptedException e) {
                        isThumbnailFile = false;
                        e.printStackTrace();
                    }
                }
            }
            // wait for thumb nail received before firing to receive actual image file
            if (!isThumbnailFile) {
                // Create an event associated to this global request.
                FileTransferRequestEvent fileTransferRequestEvent = new FileTransferRequestEvent(
                        OperationSetFileTransferJabberImpl.this, incomingFileTransferRequest, new Date());

                // Notify the global listener that a request has arrived.
                fireFileTransferRequest(fileTransferRequestEvent);
            }
            return null;
        }
    }

    /**
     * Delivers the specified event to all registered file transfer listeners.
     *
     * @param event the <tt>EventObject</tt> that we'd like delivered to all registered file transfer
     * listeners.
     */
    void fireFileTransferRequest(FileTransferRequestEvent event)
    {
        Iterator<FileTransferListener> listeners;
        synchronized (fileTransferListeners) {
            listeners = new ArrayList<>(fileTransferListeners).iterator();
        }

        while (listeners.hasNext()) {
            FileTransferListener listener = listeners.next();
            listener.fileTransferRequestReceived(event);
        }
    }

    /**
     * Delivers the specified event to all registered file transfer listeners.
     *
     * @param event the <tt>EventObject</tt> that we'd like delivered to all registered file transfer
     * listeners.
     */
    void fireFileTransferRequestRejected(FileTransferRequestEvent event)
    {
        Iterator<FileTransferListener> listeners;
        synchronized (fileTransferListeners) {
            listeners = new ArrayList<>(fileTransferListeners).iterator();
        }

        while (listeners.hasNext()) {
            FileTransferListener listener = listeners.next();
            listener.fileTransferRequestRejected(event);
        }
    }

    /**
     * Delivers the file transfer to all registered listeners.
     *
     * @param event the <tt>FileTransferEvent</tt> that we'd like delivered to all registered file
     * transfer listeners.
     */
    void fireFileTransferCreated(FileTransferCreatedEvent event)
    {
        Iterator<FileTransferListener> listeners;
        synchronized (fileTransferListeners) {
            listeners = new ArrayList<>(fileTransferListeners).iterator();
        }

        while (listeners.hasNext()) {
            FileTransferListener listener = listeners.next();
            listener.fileTransferCreated(event);
        }
    }

    /**
     * Updates file transfer progress and status while sending or receiving a file.
     */
    protected static class FileTransferProgressThread extends Thread
    {
        private final org.jivesoftware.smackx.filetransfer.FileTransfer jabberTransfer;
        private final AbstractFileTransfer fileTransfer;
        private long initialFileSize;

        public FileTransferProgressThread(org.jivesoftware.smackx.filetransfer.FileTransfer jabberTransfer,
                AbstractFileTransfer transfer, long initialFileSize)
        {
            this.jabberTransfer = jabberTransfer;
            this.fileTransfer = transfer;
            this.initialFileSize = initialFileSize;
        }

        public FileTransferProgressThread(org.jivesoftware.smackx.filetransfer.FileTransfer jabberTransfer,
                AbstractFileTransfer transfer)
        {
            this.jabberTransfer = jabberTransfer;
            this.fileTransfer = transfer;
        }

        /**
         * Thread entry point.
         */
        @Override
        public void run()
        {
            int status;
            long progress;
            String statusReason = "";

            while (true) {
                try {
                    Thread.sleep(10);

                    status = parseJabberStatus(jabberTransfer.getStatus());
                    progress = fileTransfer.getTransferredBytes();

                    if (status == FileTransferStatusChangeEvent.FAILED
                            || status == FileTransferStatusChangeEvent.COMPLETED
                            || status == FileTransferStatusChangeEvent.CANCELED
                            || status == FileTransferStatusChangeEvent.REFUSED) {
                        if (fileTransfer instanceof OutgoingFileTransferJabberImpl) {
                            ((OutgoingFileTransferJabberImpl) fileTransfer).removeThumbnailRequestListener();
                        }

                        // sometimes a file transfer can be preparing and then completed :
                        // transferred in one iteration of current thread so it won't go through
                        // intermediate state - inProgress make sure this won't happen
                        if (status == FileTransferStatusChangeEvent.COMPLETED
                                && fileTransfer.getStatus() == FileTransferStatusChangeEvent.PREPARING) {
                            fileTransfer.fireStatusChangeEvent(
                                    FileTransferStatusChangeEvent.IN_PROGRESS, "Status changed");
                            fileTransfer.fireProgressChangeEvent(System.currentTimeMillis(), progress);
                        }
                        break;
                    }
                    fileTransfer.fireStatusChangeEvent(status, "Status changed");
                    fileTransfer.fireProgressChangeEvent(System.currentTimeMillis(), progress);
                } catch (InterruptedException e) {
                    if (logger.isDebugEnabled())
                        logger.debug("Unable to sleep thread.", e);
                }
            }

            if (jabberTransfer.getError() != null) {
                logger.error("An error occurred while transferring file: " + jabberTransfer.getError().getMessage());
            }

            Exception transferException = jabberTransfer.getException();
            if (transferException != null) {
                logger.error("An exception occurred while transferring file: ", jabberTransfer.getException());

                if (transferException instanceof XMPPErrorException) {
                    XMPPError error = ((XMPPErrorException) transferException).getXMPPError();
                    if ((error != null)
                            && ((error.getCondition() == Condition.not_acceptable)
                            || (error.getCondition() == Condition.forbidden)))
                        status = FileTransferStatusChangeEvent.REFUSED;
                }
                statusReason = transferException.getMessage();
            }

            if (initialFileSize > 0 && status == FileTransferStatusChangeEvent.COMPLETED
                    && fileTransfer.getTransferredBytes() < initialFileSize) {
                status = FileTransferStatusChangeEvent.CANCELED;
            }
            fileTransfer.fireStatusChangeEvent(status, statusReason);
            fileTransfer.fireProgressChangeEvent(System.currentTimeMillis(), progress);
        }
    }

    /**
     * Parses the given Jabber status to a <tt>FileTransfer</tt> interface status.
     *
     * @param jabberStatus the Jabber status to parse
     * @return the parsed status
     */
    private static int parseJabberStatus(Status jabberStatus)
    {
        if (jabberStatus.equals(Status.complete))
            return FileTransferStatusChangeEvent.COMPLETED;
        else if (jabberStatus.equals(Status.cancelled))
            return FileTransferStatusChangeEvent.CANCELED;
        else if (jabberStatus.equals(Status.in_progress) || jabberStatus.equals(Status.negotiated))
            return FileTransferStatusChangeEvent.IN_PROGRESS;
        else if (jabberStatus.equals(Status.error))
            return FileTransferStatusChangeEvent.FAILED;
        else if (jabberStatus.equals(Status.refused))
            return FileTransferStatusChangeEvent.REFUSED;
        else if (jabberStatus.equals(Status.negotiating_transfer)
                || jabberStatus.equals(Status.negotiating_stream))
            return FileTransferStatusChangeEvent.PREPARING;
        else
            // FileTransfer.Status.initial
            return FileTransferStatusChangeEvent.WAITING;
    }

    @Override
    public void statusUpdated(Status oldStatus, Status newStatus)
    {
        if (!newStatus.equals(oldStatus)) {
            int status = parseJabberStatus(newStatus);
            outgoingTransfer.fireStatusChangeEvent(status, "Status changed");
        }
    }

    @Override
    public void outputStreamEstablished(OutputStream paramOutputStream)
    {
        // TODO Auto-generated method stub
    }

    @Override
    public void errorEstablishingStream(Exception paramException)
    {
        // TODO Auto-generated method stub
    }
}
