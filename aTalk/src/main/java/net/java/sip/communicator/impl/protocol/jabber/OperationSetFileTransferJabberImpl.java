/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */

package net.java.sip.communicator.impl.protocol.jabber;

import java.io.File;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Vector;

import net.java.sip.communicator.plugin.notificationwiring.NotificationManager;
import net.java.sip.communicator.service.protocol.AbstractFileTransfer;
import net.java.sip.communicator.service.protocol.Contact;
import net.java.sip.communicator.service.protocol.FileTransfer;
import net.java.sip.communicator.service.protocol.IncomingFileTransferRequest;
import net.java.sip.communicator.service.protocol.OperationNotSupportedException;
import net.java.sip.communicator.service.protocol.OperationSetFileTransfer;
import net.java.sip.communicator.service.protocol.OperationSetMultiUserChat;
import net.java.sip.communicator.service.protocol.OperationSetPersistentPresence;
import net.java.sip.communicator.service.protocol.PresenceStatus;
import net.java.sip.communicator.service.protocol.RegistrationState;
import net.java.sip.communicator.service.protocol.event.FileTransferCreatedEvent;
import net.java.sip.communicator.service.protocol.event.FileTransferRequestEvent;
import net.java.sip.communicator.service.protocol.event.FileTransferStatusChangeEvent;
import net.java.sip.communicator.service.protocol.event.RegistrationStateChangeEvent;
import net.java.sip.communicator.service.protocol.event.RegistrationStateChangeListener;
import net.java.sip.communicator.service.protocol.event.ScFileTransferListener;
import net.java.sip.communicator.service.protocol.jabberconstants.JabberStatusEnum;

import org.atalk.android.R;
import org.atalk.android.aTalkApp;
import org.atalk.android.gui.chat.ChatFragment;
import org.jivesoftware.smack.SmackException;
import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.XMPPConnectionRegistry;
import org.jivesoftware.smack.XMPPException.XMPPErrorException;
import org.jivesoftware.smack.packet.Presence;
import org.jivesoftware.smack.packet.StanzaError;
import org.jivesoftware.smack.packet.StanzaError.Condition;
import org.jivesoftware.smack.roster.Roster;
import org.jivesoftware.smackx.filetransfer.FileTransfer.Status;
import org.jivesoftware.smackx.filetransfer.FileTransferListener;
import org.jivesoftware.smackx.filetransfer.FileTransferManager;
import org.jivesoftware.smackx.filetransfer.FileTransferNegotiator;
import org.jivesoftware.smackx.filetransfer.FileTransferRequest;
import org.jivesoftware.smackx.filetransfer.OutgoingFileTransfer;
import org.jivesoftware.smackx.filetransfer.OutgoingFileTransfer.NegotiationProgress;
import org.jivesoftware.smackx.jingle_filetransfer.JingleFileTransferManager;
import org.jivesoftware.smackx.jingle_filetransfer.controller.IncomingFileOfferController;
import org.jivesoftware.smackx.jingle_filetransfer.listener.IncomingFileOfferListener;
import org.jivesoftware.smackx.si.packet.StreamInitiation;
import org.jxmpp.jid.EntityFullJid;
import org.jxmpp.jid.Jid;

import timber.log.Timber;

/**
 * The Jabber implementation of the <code>OperationSetFileTransfer</code> interface.
 *
 * @author Gregory Bande
 * @author Nicolas Riegel
 * @author Yana Stamcheva
 * @author Eng Chong Meng
 */
public class OperationSetFileTransferJabberImpl implements OperationSetFileTransfer {
    // Change max to 20 MBytes. Original max 2GBytes i.e. 2147483648l = 2048*1024*1024;
    private final static long MAX_FILE_LENGTH = 2147483647L;

    /**
     * The provider that created us.
     */
    private final ProtocolProviderServiceJabberImpl mPPS;

    /**
     * An active instance of the opSetPersPresence operation set.
     */
    private OperationSetPersistentPresenceJabberImpl opSetPersPresence = null;

    /**
     * The Smack file transfer request Listener for legacy IBB and Sock5.
     */
    private FileTransferRequestListener ftrListener = null;

    /**
     * The Smack Jingle file transfer request Listener for legacy IBB and SOCK5.
     */
    private IncomingFileOfferListener ifoListener = null;

    /**
     * Flag indicates and ByteStream file transfer exception has occurred.
     */
    private boolean byteStreamError = false;

    /**
     * A list of listeners registered for file transfer events.
     */
    private final Vector<ScFileTransferListener> fileTransferListeners = new Vector<>();

    // Register file transfer features on every established connection to make sure we register
    // them before creating our ServiceDiscoveryManager

    static {
        XMPPConnectionRegistry.addConnectionCreationListener(FileTransferNegotiator::getInstanceFor);
    }

    /**
     * Constructor
     *
     * @param provider is the provider that created us
     */
    public OperationSetFileTransferJabberImpl(ProtocolProviderServiceJabberImpl provider) {
        mPPS = provider;
        provider.addRegistrationStateChangeListener(new RegistrationStateListener());
    }

    /**
     * Sends a file transfer request to the given <code>contact</code>.
     *
     * @param contact the contact that should receive the file
     * @param file file to send
     * @param chatType ChatFragment.MSGTYPE_OMEMO or MSGTYPE_NORMAL
     * @param msgUuid the id that uniquely identifies this file transfer and saved DB record
     *
     * @return the transfer object
     */
    public FileTransfer sendFile(Contact contact, File file, int chatType, String msgUuid)
            throws IllegalStateException, IllegalArgumentException, OperationNotSupportedException {

        // Legacy si File Transfer cannot support encrypted file sending.
        if (ChatFragment.MSGTYPE_OMEMO == chatType) {
            throw new OperationNotSupportedException(aTalkApp.getResString(R.string.file_transfer_not_secure));
        }

        assertConnected();
        if (file.length() > getMaximumFileLength())
            throw new IllegalArgumentException(aTalkApp.getResString(R.string.file_size_too_big, mPPS.getOurJid()));

        // null if the contact is offline, or file transfer is not supported by this contact;
        // Then throws OperationNotSupportedException for caller to try alternative method
        EntityFullJid mContactJid = getFullJid(contact,
                StreamInitiation.NAMESPACE, StreamInitiation.NAMESPACE + "/profile/file-transfer");
        if (mContactJid == null) {
            throw new OperationNotSupportedException(aTalkApp.getResString(R.string.file_transfer_not_supported,
                    opSetPersPresence.getPresenceStatus().getStatusName()));
        }

        /* Must init to the correct ftManager at time of sending file with current mPPS; Otherwise
         * the ftManager is the last registered PPS and may not be correct in multiple user accounts env.
         */
        FileTransferManager ftManager = FileTransferManager.getInstanceFor(mPPS.getConnection());
        // OutgoingFileTransfer.setResponseTimeout(2*60*1000); // use default 60s instead for user accept timeout.
        OutgoingFileTransfer transfer = ftManager.createOutgoingFileTransfer(mContactJid);
        OutgoingFileTransferJabberImpl oFileTransfer = new OutgoingFileTransferJabberImpl(contact, file, transfer, mPPS, msgUuid);

        // Notify all interested listeners that a file transfer has been created.
        FileTransferCreatedEvent event = new FileTransferCreatedEvent(oFileTransfer, new Date());
        fireFileTransferCreated(event);

        // cmeng: start file transferring with callback on status changes;
        // Start FileTransferNegotiator to support both sock5 and IBB; fallback to IBB if sock5 failed on retry
        FileTransferNegotiator.IBB_ONLY =  byteStreamError;
        try {
            // Start smack handle everything and start status and progress thread.
            transfer.setCallback(new OFTNegotiationProgress(oFileTransfer));
            transfer.sendFile(file, "Sending file with thumbnail element if enabled");
            new FileTransferProgressThread(transfer, oFileTransfer).start();
        } catch (SmackException e) {
            Timber.e("Failed to send file: %s", e.getMessage());
            throw new OperationNotSupportedException(
                    aTalkApp.getResString(R.string.file_transfer_send_error, mContactJid));
        }
        return oFileTransfer;
    }

    /**
     * A callback class to retrieve the status of an outgoing transfer negotiation process
     * for legacy IBB/SOCK5 Bytestream file transfer.
     * This callback is for each mOGFileTransfer for multiple files transfer;
     */
    private class OFTNegotiationProgress implements NegotiationProgress {
        private final OutgoingFileTransferJabberImpl mOGFileTransfer;

        public OFTNegotiationProgress(OutgoingFileTransferJabberImpl oFileTransfer) {
            mOGFileTransfer = oFileTransfer;
        }

        /**
         * Called when the status changes.
         *
         * @param oldStatus the previous status of the file transfer.
         * @param newStatus the new status of the file transfer.
         */
        @Override
        public void statusUpdated(Status oldStatus, Status newStatus) {
            Timber.d("NegotiationProgress status change: %s => %s", oldStatus, newStatus);

            switch (newStatus) {
                case complete:
                case cancelled:
                case refused:
                    byteStreamError = false;
                    mOGFileTransfer.removeThumbnailHandler();
                    mOGFileTransfer.fireStatusChangeEvent(parseJabberStatus(newStatus), newStatus.toString());
                    break;

                // Leave fireStatusChangeEvent to errorEstablishingStream to have proper error message to user.
                case error:
                    mOGFileTransfer.removeThumbnailHandler();
                    if (Status.negotiating_stream == oldStatus) {
                        byteStreamError = !FileTransferNegotiator.IBB_ONLY;
                    }
                    break;
            }
        }

        /**
         * Once the negotiation process is completed the output stream can be retrieved.
         * Valid for SOCK5ByteStream protocol only. Reset byteStreamError flag to false.
         *
         * @param stream the established stream which can be used to transfer the file to the remote entity
         */
        @Override
        public void outputStreamEstablished(OutputStream stream) {
            // Timber.d("NegotiationProgress outputStreamEstablished: %s", stream);
            byteStreamError = false;
        }

        /**
         * Called when an exception occurs during the negotiation progress.
         *
         * @param ex the exception that occurred.
         */
        @Override
        public void errorEstablishingStream(Exception ex) {
            String errMsg = ex.getMessage();
            if (errMsg != null && ex instanceof SmackException.NoResponseException) {
                errMsg = errMsg.substring(0, errMsg.indexOf(". StanzaCollector"));
            }
            mOGFileTransfer.fireStatusChangeEvent(FileTransferStatusChangeEvent.FAILED, errMsg);
        }
    }

    /**
     * Find the EntityFullJid of an ONLINE contact with the highest priority if more than one found,
     * and supports the given file transfer features; if we have equals priorities choose the one more available.
     *
     * @param contact The Contact
     * @param features The desired features' namespace
     *
     * @return the filtered contact EntityFullJid if found, or null otherwise
     */
    public EntityFullJid getFullJid(Contact contact, String... features) {
        Jid contactJid = contact.getJid();  // bareJid from the roster unless is volatile contact

        OperationSetMultiUserChat mucOpSet = mPPS.getOperationSet(OperationSetMultiUserChat.class);
        if ((mucOpSet == null) || !mucOpSet.isPrivateMessagingContact(contactJid)) {
            List<Presence> presences = Roster.getInstanceFor(mPPS.getConnection()).getPresences(contactJid.asBareJid());
            int bestPriority = -128;
            PresenceStatus pStatus = null;

            for (Presence presence : presences) {
                // Proceed only for presence with Type.available
                if (presence.isAvailable() && mPPS.isFeatureListSupported(presence.getFrom(), features)) {
                    int priority = presence.getPriority();  // return priority range: -128~127
                    if (priority > bestPriority) {
                        bestPriority = priority;
                        contactJid = presence.getFrom();
                        pStatus = OperationSetPersistentPresenceJabberImpl.jabberStatusToPresenceStatus(presence, mPPS);
                    }
                    else if ((priority == bestPriority) && (pStatus != null)) {
                        PresenceStatus tmpStatus
                                = OperationSetPersistentPresenceJabberImpl.jabberStatusToPresenceStatus(presence, mPPS);
                        if (tmpStatus.compareTo(pStatus) > 0) {
                            contactJid = presence.getFrom();
                            pStatus = tmpStatus;
                        }
                    }
                }
            }
        }
        // Force to null if contact is offline i.e does not resolved to an EntityFullJid.
        return (contactJid instanceof EntityFullJid) ? (EntityFullJid) contactJid : null;
    }

    /**
     * Adds the given <code>ScFileTransferListener</code> that would listen for file transfer requests
     * created file transfers.
     *
     * @param listener the <code>ScFileTransferListener</code> to add
     */
    public void addFileTransferListener(ScFileTransferListener listener) {
        synchronized (fileTransferListeners) {
            if (!fileTransferListeners.contains(listener)) {
                this.fileTransferListeners.add(listener);
            }
        }
    }

    /**
     * Removes the given <code>ScFileTransferListener</code> that listens for file transfer requests and
     * created file transfers.
     *
     * @param listener the <code>ScFileTransferListener</code> to remove
     */
    public void removeFileTransferListener(ScFileTransferListener listener) {
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
            throws IllegalStateException {
        if (mPPS == null)
            throw new IllegalStateException("The provider must be non-null and signed in before being able to send a file.");
        else if (!mPPS.isRegistered()) {
            // if we are not registered but the current status is online change the current status
            if (opSetPersPresence.getPresenceStatus().isOnline()) {
                opSetPersPresence.fireProviderStatusChangeEvent(opSetPersPresence.getPresenceStatus(),
                        mPPS.getJabberStatusEnum().getStatus(JabberStatusEnum.OFFLINE));
            }
            throw new IllegalStateException("The provider must be signed in before being able to send a file.");
        }
    }

    /**
     * Returns the maximum file length supported by the protocol in bytes. Supports up to 2GB.
     *
     * @return the file length that is supported.
     */
    public long getMaximumFileLength() {
        return MAX_FILE_LENGTH;
    }

    /**
     * Our listener that will tell us when we're registered to
     */
    private class RegistrationStateListener implements RegistrationStateChangeListener {
        /**
         * The method is called by a ProtocolProvider implementation whenever a change in the
         * registration state of the corresponding provider had occurred.
         *
         * @param evt ProviderStatusChangeEvent the event describing the status change.
         */
        @Override
        public void registrationStateChanged(RegistrationStateChangeEvent evt) {
            FileTransferManager ftManager = null;
            JingleFileTransferManager jftManager = null;

            XMPPConnection connection = mPPS.getConnection();
            if (connection != null) {
                ftManager = FileTransferManager.getInstanceFor(connection);
                jftManager = JingleFileTransferManager.getInstanceFor(connection);
            }

            if (evt.getNewState() == RegistrationState.REGISTERED) {
                opSetPersPresence = (OperationSetPersistentPresenceJabberImpl)
                        mPPS.getOperationSet(OperationSetPersistentPresence.class);

                // cmeng: Registered only once - otherwise multiple triggers on single file request
                if ((ftrListener == null) && (ftManager != null)) {
                    ftrListener = new FileTransferRequestListener();
                    ftManager.addFileTransferListener(ftrListener);
                }

                if ((ifoListener == null) && (jftManager != null)) {
                    ifoListener = new JingleIFOListener();
                    jftManager.addIncomingFileOfferListener(ifoListener);
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

                if ((ifoListener != null) && (jftManager != null)) {
                    jftManager.removeIncomingFileOfferListener(ifoListener);
                    ifoListener = null;
                }
            }
        }
    }

    /**
     * Listener for Jingle IBB/SOCK5 ByteStream incoming file offer.
     */
    private class JingleIFOListener implements IncomingFileOfferListener {
        /**
         * Listens for file transfer packets.
         *
         * @param offer IncomingFileOfferController offer from smack IncomingFileOfferListener
         */
        @Override
        public void onIncomingFileOffer(IncomingFileOfferController offer) {
            // Timber.d("Received jingle incoming file offer.");

            // Create a global incoming file transfer request.
            IncomingFileOfferJingleImpl ifoJingle
                    = new IncomingFileOfferJingleImpl(mPPS, OperationSetFileTransferJabberImpl.this, offer);
            fireFileTransferRequest(ifoJingle);
        }
    }

    /**
     * Listener for Jabber legacy IBB/SOCK5 incoming file transfer requests.
     */
    private class FileTransferRequestListener implements FileTransferListener {
        private StreamInitiation getStreamInitiation(FileTransferRequest request) {
            Method gsi;
            try {
                gsi = request.getClass().getDeclaredMethod("getStreamInitiation");
                gsi.setAccessible(true);
                return (StreamInitiation) gsi.invoke(request);
            } catch (Exception e) {
                Timber.e("Cannot invoke getStreamInitiation: %s", e.getMessage());
                return null;
            }
        }

        /**
         * Listens for incoming file transfer stanza.
         *
         * @param request fileTransfer request from smack FileTransferListener
         */
        @Override
        public void fileTransferRequest(final FileTransferRequest request) {
            // Timber.d("Received incoming Jabber file transfer request.");
            // Create and fire global incoming file transfer request received.
            IncomingFileTransferRequestJabberImpl incomingFileTransferRequest
                    = new IncomingFileTransferRequestJabberImpl(mPPS, OperationSetFileTransferJabberImpl.this, request);
            fireFileTransferRequest(incomingFileTransferRequest);
        }
    }

    /**
     * Delivers the specified event to all registered file transfer listeners.
     *
     * @param request the <code>IncomingFileTransferRequestJabberImpl</code> that we'd like delivered to all
     * registered file transfer listeners.
     */
    void fireFileTransferRequest(IncomingFileTransferRequest request) {
        Contact contact = request.getSender();
        NotificationManager.updateMessageCount(contact);

        // Create an event associated to this global request.
        FileTransferRequestEvent event = new FileTransferRequestEvent(this, request, new Date());
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
     * When the remote declines an incoming file offer;
     * delivers the specified event to all registered file transfer listeners.
     *
     * @param event the <code>EventObject</code> that we'd like delivered to all registered file transfer listeners.
     */
    void fireFileTransferRequestRejected(FileTransferRequestEvent event) {
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
     * When the remote user cancels the file transfer/offer;
     * delivers the specified event to all registered file transfer listeners.
     * Note: Legacy XMPP IBB/SOCK5 protocol does reverts this info to sender.
     *
     * @param event the <code>EventObject</code> that we'd like delivered to all
     * registered file transfer listeners.
     */
    void fireFileTransferRequestCanceled(FileTransferRequestEvent event) {
        Iterator<ScFileTransferListener> listeners;
        synchronized (fileTransferListeners) {
            listeners = new ArrayList<>(fileTransferListeners).iterator();
        }

        while (listeners.hasNext()) {
            ScFileTransferListener listener = listeners.next();
            listener.fileTransferRequestCanceled(event);
        }
    }

    /**
     * Delivers the file transfer to all registered listeners.
     *
     * @param event the <code>FileTransferEvent</code> that we'd like delivered to all registered file transfer listeners.
     */
    public void fireFileTransferCreated(FileTransferCreatedEvent event) {
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
    protected static class FileTransferProgressThread extends Thread {
        private final org.jivesoftware.smackx.filetransfer.FileTransfer jabberTransfer;
        private final AbstractFileTransfer fileTransfer;
        private long initialFileSize;

        public FileTransferProgressThread(org.jivesoftware.smackx.filetransfer.FileTransfer jabberTransfer,
                AbstractFileTransfer transfer, long initialFileSize) {
            this.jabberTransfer = jabberTransfer;
            this.fileTransfer = transfer;
            this.initialFileSize = initialFileSize;
        }

        public FileTransferProgressThread(org.jivesoftware.smackx.filetransfer.FileTransfer jabberTransfer,
                AbstractFileTransfer transfer) {
            this.jabberTransfer = jabberTransfer;
            this.fileTransfer = transfer;
        }

        /**
         * Thread entry point.
         */
        @Override
        public void run() {
            int status;
            String statusReason = "";
            int count = 0;

            while (true) {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    Timber.d("Unable to thread sleep.");
                }

                // OutgoingFileTransfer has its own callback to handle status change
                if (fileTransfer instanceof IncomingFileTransferJabberImpl) {
                    status = parseJabberStatus(jabberTransfer.getStatus());
                    // if (count++ % 250 == 0) {
                    //     Timber.d("FileTransferProgressThread: %s (%s) <= %s",
                    //             jabberTransfer.getStatus().toString(), status, jabberTransfer);
                    // }

                    if (jabberTransfer.getError() != null) {
                        Timber.e("An error occurred while transferring file: %s", jabberTransfer.getError().getMessage());
                    }

                    Exception transferException = jabberTransfer.getException();
                    if (transferException != null) {
                        statusReason = transferException.getMessage();
                        Timber.e("An exception occurred while transferring file: %s", statusReason);

                        if (transferException instanceof XMPPErrorException) {
                            StanzaError error = ((XMPPErrorException) transferException).getStanzaError();
                            // get more specific reason for failure if available
                            if (error != null) {
                                statusReason = error.getDescriptiveText();

                                if ((error.getCondition() == Condition.not_acceptable)
                                        || (error.getCondition() == Condition.forbidden))
                                    status = FileTransferStatusChangeEvent.DECLINED;
                            }
                        }
                    }

                    // Only partial file is received
                    if (initialFileSize > 0 && status == FileTransferStatusChangeEvent.COMPLETED
                            && fileTransfer.getTransferredBytes() < initialFileSize) {
                        status = FileTransferStatusChangeEvent.CANCELED;
                    }
                    fileTransfer.fireStatusChangeEvent(status, statusReason);
                }

                // cmeng - use actual transfer bytes at time of fireProgressChangeEvent
                // for both the outgoing and incoming legacy file transfer.
                fileTransfer.fireProgressChangeEvent(System.currentTimeMillis(), fileTransfer.getTransferredBytes());

                // stop the FileTransferProgressThread thread once everything isDone()
                if (jabberTransfer.isDone()) {
                    break;
                }
            }
        }
    }

    /**
     * Parses the given Jabber status to a <code>FileTransfer</code> interface status.
     *
     * @param smackFTStatus the smack file transfer status to parse
     *
     * @return the parsed status
     *
     * @see org.jivesoftware.smackx.filetransfer.FileTransfer
     */
    private static int parseJabberStatus(Status smackFTStatus) {
        switch (smackFTStatus) {
            case complete:
                return FileTransferStatusChangeEvent.COMPLETED;
            case cancelled:
                return FileTransferStatusChangeEvent.CANCELED;
            case refused:
                return FileTransferStatusChangeEvent.DECLINED;
            case error:
                return FileTransferStatusChangeEvent.FAILED;

            case initial:
                return FileTransferStatusChangeEvent.PREPARING;

            case negotiating_transfer:
                return FileTransferStatusChangeEvent.WAITING;

            case negotiating_stream:
                return FileTransferStatusChangeEvent.ACCEPT;

            case negotiated:
            case in_progress:
                return FileTransferStatusChangeEvent.IN_PROGRESS;
            default:
                return FileTransferStatusChangeEvent.UNKNOWN;
        }
    }
}
