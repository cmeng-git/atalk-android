/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */

package net.java.sip.communicator.impl.protocol.jabber;

import net.java.sip.communicator.service.protocol.FileTransfer;
import net.java.sip.communicator.service.protocol.*;
import net.java.sip.communicator.service.protocol.event.*;
import net.java.sip.communicator.service.protocol.jabberconstants.JabberStatusEnum;
import net.java.sip.communicator.util.ConfigurationUtils;

import org.atalk.android.R;
import org.atalk.android.aTalkApp;
import org.atalk.android.gui.chat.filetransfer.FileTransferConversation;
import org.jivesoftware.smack.*;
import org.jivesoftware.smack.XMPPException.XMPPErrorException;
import org.jivesoftware.smack.packet.Presence;
import org.jivesoftware.smack.packet.StanzaError;
import org.jivesoftware.smack.packet.StanzaError.Condition;
import org.jivesoftware.smack.roster.Roster;
import org.jivesoftware.smackx.filetransfer.FileTransfer.Status;
import org.jivesoftware.smackx.filetransfer.*;
import org.jivesoftware.smackx.si.packet.StreamInitiation;
import org.jxmpp.jid.EntityFullJid;
import org.jxmpp.jid.Jid;
import org.xmpp.extensions.thumbnail.Thumbnail;
import org.xmpp.extensions.thumbnail.ThumbnailFile;

import java.io.File;
import java.lang.reflect.Method;
import java.util.*;

import timber.log.Timber;

/**
 * The Jabber implementation of the <tt>OperationSetFileTransfer</tt> interface.
 *
 * @author Gregory Bande
 * @author Nicolas Riegel
 * @author Yana Stamcheva
 * @author Eng Chong Meng
 */
public class OperationSetFileTransferJabberImpl implements OperationSetFileTransfer
{
    // Change max to 20 MBytes. Original max 2GBytes i.e. 2147483648l = 2048*1024*1024;
    private final long MAX_FILE_LENGTH = 2147483648L;

    /**
     * The provider that created us.
     */
    private final ProtocolProviderServiceJabberImpl jabberProvider;

    /**
     * An active instance of the opSetPersPresence operation set.
     */
    private OperationSetPersistentPresenceJabberImpl opSetPersPresence = null;

    /**
     * The Smack file transfer request Listener.
     */
    private FileTransferRequestListener ftrListener = null;

    /**
     * A list of listeners registered for file transfer events.
     */
    private final Vector<ScFileTransferListener> fileTransferListeners = new Vector<>();

    // Register file transfer features on every established connection to make sure we register
    // them before creating our ServiceDiscoveryManager

    static {
        XMPPConnectionRegistry.addConnectionCreationListener(new ConnectionCreationListener()
        {
            @Override
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
            List<Presence> presences
                    = Roster.getInstanceFor(jabberProvider.getConnection()).getPresences(toContact.getJid().asBareJid());
            int bestPriority = -1;
            PresenceStatus jabberStatus = null;

            for (Presence presence : presences) {
                if (jabberProvider.isFeatureListSupported(presence.getFrom(),
                        StreamInitiation.NAMESPACE, StreamInitiation.NAMESPACE + "/profile/file-transfer")) {

                    int priority = (presence.getPriority() == Integer.MIN_VALUE) ? 0 : presence.getPriority();
                    if (priority > bestPriority) {
                        bestPriority = priority;
                        fullJid = presence.getFrom();
                        jabberStatus = OperationSetPersistentPresenceJabberImpl
                                .jabberStatusToPresenceStatus(presence, jabberProvider);
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

        // FullJid is null if file transfer is not supported for this contact; or if contact is offline.
        if (fullJid == null) {
            throw new OperationNotSupportedException(aTalkApp.getResString(R.string.service_gui_FILE_TRANSFER_NOT_SUPPORTED));
        }

        /* Must init to the correct ftManager at time of sending file with current jabberProvider; Otherwise
         * the ftManager is the last registered jabberProvide and may not be correct in multiple users env.
         */
        FileTransferManager ftManager = FileTransferManager.getInstanceFor(jabberProvider.getConnection());
        OutgoingFileTransfer transfer = ftManager.createOutgoingFileTransfer((EntityFullJid) fullJid);
        OutgoingFileTransferJabberImpl outgoingTransfer
                = new OutgoingFileTransferJabberImpl(toContact, file, transfer, jabberProvider);

        // Notify all interested listeners that a file transfer has been created.
        FileTransferCreatedEvent event = new FileTransferCreatedEvent(outgoingTransfer, new Date());
        fireFileTransferCreated(event);

        // cmeng: start file transferring with callback on status changes - required own handling of outputStream
        // transfer.sendFile(file.getName(), file.length(), "Sending file", this);
        try {
            // Start smack handle everything and start status and progress thread.
            transfer.sendFile(file, "Sending file");
            new FileTransferProgressThread(transfer, outgoingTransfer).start();
        } catch (SmackException e) {
            Timber.e(e, "Failed to send file.");
            throw new OperationNotSupportedException(
                    aTalkApp.getResString(R.string.service_gui_FILE_UNABLE_TO_SEND, fullJid));
        }
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
     * Adds the given <tt>ScFileTransferListener</tt> that would listen for file transfer requests
     * created file transfers.
     *
     * @param listener the <tt>ScFileTransferListener</tt> to add
     */
    public void addFileTransferListener(ScFileTransferListener listener)
    {
        synchronized (fileTransferListeners) {
            if (!fileTransferListeners.contains(listener)) {
                this.fileTransferListeners.add(listener);
            }
        }
    }

    /**
     * Removes the given <tt>ScFileTransferListener</tt> that listens for file transfer requests and
     * created file transfers.
     *
     * @param listener the <tt>ScFileTransferListener</tt> to remove
     */
    public void removeFileTransferListener(ScFileTransferListener listener)
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
        @Override
        public void registrationStateChanged(RegistrationStateChangeEvent evt)
        {
            FileTransferManager ftManager = null;
            XMPPConnection connection = jabberProvider.getConnection();
            if (connection != null)
                ftManager = FileTransferManager.getInstanceFor(connection);

            if (evt.getNewState() == RegistrationState.REGISTERED) {
                opSetPersPresence = (OperationSetPersistentPresenceJabberImpl)
                        jabberProvider.getOperationSet(OperationSetPersistentPresence.class);

                // cmeng: Registered only once - otherwise multiple triggers on single file request
                if ((ftrListener == null) && (ftManager != null)) {
                    ftrListener = new FileTransferRequestListener();
                    // Timber.w("Add FileTransferListener: %s", ftrListener);
                    ftManager.addFileTransferListener(ftrListener);
                }
            }
            // cmeng - resume does not trigger RegistrationState.UNREGISTERED
            // Must do it before UNREGISTERED state, otherwise ftManager == null
            else if (evt.getNewState() == RegistrationState.UNREGISTERING) {
                // Must unregistered ftrListener on protocolProvider UNREGISTERING to avoid any ghost listener
                // check ftManager to ensure it is still valid i..e not null
                if ((ftrListener != null) && (ftManager != null)) {
                    // Timber.w("Remove FileTransferListener: %s", ftrListener);
                    ftManager.removeFileTransferListener(ftrListener);
                    ftrListener = null;
                }
            }
        }
    }

    /**
     * Listener for Jabber incoming file transfer requests.
     */
    private class FileTransferRequestListener implements FileTransferListener
    {
        private StreamInitiation getStreamInitiation(FileTransferRequest request)
        {
            Method gsi;
            try {
                gsi = request.getClass().getDeclaredMethod("getStreamInitiation");
                gsi.setAccessible(true);
                return (StreamInitiation) gsi.invoke(request);
            } catch (Exception e) {
                Timber.e(e, "Cannot invoke getStreamInitiation");
                return null;
            }
        }

        /**
         * Listens for file transfer packets.
         *
         * @param request fileTransfer request from smack FileTransferListener
         */
        @Override
        public void fileTransferRequest(final FileTransferRequest request)
        {
            Timber.d("Received incoming Jabber file transfer request.");

            // Create a global incoming file transfer request.
            IncomingFileTransferRequestJabberImpl incomingFileTransferRequest = new IncomingFileTransferRequestJabberImpl(
                    jabberProvider, OperationSetFileTransferJabberImpl.this, request);
            StreamInitiation si = getStreamInitiation(request);

            // Send thumbnail request if advertised in streamInitiation packet, no autoAccept and the feature is enabled
            StreamInitiation.File file;
            boolean isThumbnailFile = false;
            if ((si != null) && (file = si.getFile()) instanceof ThumbnailFile) {
                // Proceed to request for the available thumbnail if auto accept file not permitted
                boolean isAutoAccept = (request.getFileSize() <= ConfigurationUtils.getAutoAcceptFileSize());
                Thumbnail thumbnail = ((ThumbnailFile) file).getThumbnail();
                if (!isAutoAccept && FileTransferConversation.FT_THUMBNAIL_ENABLE && (thumbnail != null)) {
                    isThumbnailFile = true;
                    incomingFileTransferRequest.fetchThumbnailAndNotify(thumbnail.getCid());
                }
            }
            // No thumbnail request, then proceed to notify the global listener that a request has arrived
            if (!isThumbnailFile) {
                fireFileTransferRequest(incomingFileTransferRequest);
            }
        }
    }

    /**
     * Delivers the specified event to all registered file transfer listeners.
     *
     * @param request the <tt>IncomingFileTransferRequestJabberImpl</tt> that we'd like delivered to all
     * registered file transfer listeners.
     */
    void fireFileTransferRequest(IncomingFileTransferRequestJabberImpl request)
    {
        // Create an event associated to this global request.
        FileTransferRequestEvent event = new FileTransferRequestEvent(
                OperationSetFileTransferJabberImpl.this, request, new Date());

        Iterator<ScFileTransferListener> listeners;
        synchronized (fileTransferListeners) {
            listeners = new ArrayList<>(fileTransferListeners).iterator();
        }

        while (listeners.hasNext()) {
            ScFileTransferListener listener = listeners.next();
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
        Iterator<ScFileTransferListener> listeners;
        synchronized (fileTransferListeners) {
            listeners = new ArrayList<>(fileTransferListeners).iterator();
        }

        while (listeners.hasNext()) {
            ScFileTransferListener listener = listeners.next();
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
        Iterator<ScFileTransferListener> listeners;
        synchronized (fileTransferListeners) {
            listeners = new ArrayList<>(fileTransferListeners).iterator();
        }

        while (listeners.hasNext()) {
            ScFileTransferListener listener = listeners.next();
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
            String statusReason = "";

            while (true) {
                try {
                    Thread.sleep(10);

                    // cmeng - do not use pre-fetched value, instead use actual at time of fireProgressChangeEvent
                    // progress = fileTransfer.getTransferredBytes();
                    status = parseJabberStatus(jabberTransfer.getStatus());
                    if (status == FileTransferStatusChangeEvent.COMPLETED
                            || status == FileTransferStatusChangeEvent.FAILED
                            || status == FileTransferStatusChangeEvent.CANCELED
                            || status == FileTransferStatusChangeEvent.REFUSED) {

                        if (fileTransfer instanceof OutgoingFileTransferJabberImpl) {
                            ((OutgoingFileTransferJabberImpl) fileTransfer).removeThumbnailHandler();
                        }

                        // sometimes a file transfer can be preparing and then completed :
                        // transferred in one iteration of current thread so it won't go through
                        // intermediate state - inProgress make sure this won't happen
                        if (status == FileTransferStatusChangeEvent.COMPLETED
                                && fileTransfer.getStatus() == FileTransferStatusChangeEvent.PREPARING) {
                            fileTransfer.fireStatusChangeEvent(
                                    FileTransferStatusChangeEvent.IN_PROGRESS, "Status changed");
                            fileTransfer.fireProgressChangeEvent(System.currentTimeMillis(), fileTransfer.getTransferredBytes());
                        }
                        break;
                    }

                    fileTransfer.fireStatusChangeEvent(status, "Status changed");
                    fileTransfer.fireProgressChangeEvent(System.currentTimeMillis(), fileTransfer.getTransferredBytes());
                } catch (InterruptedException e) {
                    Timber.d(e, "Unable to sleep thread.");
                }
            }

            if (jabberTransfer.getError() != null) {
                Timber.e("An error occurred while transferring file: %s", jabberTransfer.getError().getMessage());
            }

            Exception transferException = jabberTransfer.getException();
            if (transferException != null) {
                Timber.e(jabberTransfer.getException(), "An exception occurred while transferring file.");
                statusReason = transferException.getMessage();

                if (transferException instanceof XMPPErrorException) {
                    StanzaError error = ((XMPPErrorException) transferException).getStanzaError();
                    if (error != null) {
                        // get more specific reason for failure
                        statusReason = error.getDescriptiveText();

                        if ((error.getCondition() == Condition.not_acceptable)
                                || (error.getCondition() == Condition.forbidden))
                            status = FileTransferStatusChangeEvent.REFUSED;
                    }
                }
            }

            if (initialFileSize > 0 && status == FileTransferStatusChangeEvent.COMPLETED
                    && fileTransfer.getTransferredBytes() < initialFileSize) {
                status = FileTransferStatusChangeEvent.CANCELED;
            }
            fileTransfer.fireStatusChangeEvent(status, statusReason);
            fileTransfer.fireProgressChangeEvent(System.currentTimeMillis(), fileTransfer.getTransferredBytes());
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
}
