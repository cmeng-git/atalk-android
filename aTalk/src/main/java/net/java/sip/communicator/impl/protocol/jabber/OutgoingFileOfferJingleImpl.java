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

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import net.java.sip.communicator.service.protocol.AbstractFileTransfer;
import net.java.sip.communicator.service.protocol.Contact;
import net.java.sip.communicator.service.protocol.event.FileTransferStatusChangeEvent;

import org.jivesoftware.smack.SmackException;
import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.XMPPException;
import org.jivesoftware.smackx.bob.BoBData;
import org.jivesoftware.smackx.bob.BoBInfo;
import org.jivesoftware.smackx.bob.BoBManager;
import org.jivesoftware.smackx.bob.ContentId;
import org.jivesoftware.smackx.jingle.component.JingleSessionImpl;
import org.jivesoftware.smackx.jingle.component.JingleSessionImpl.JingleSessionListener;
import org.jivesoftware.smackx.jingle.component.JingleSessionImpl.SessionState;
import org.jivesoftware.smackx.jingle.element.JingleReason;
import org.jivesoftware.smackx.jingle_filetransfer.controller.OutgoingFileOfferController;
import org.jivesoftware.smackx.jingle_filetransfer.listener.ProgressListener;
import org.jivesoftware.smackx.thumbnail.component.ThumbnailedFile;

import timber.log.Timber;

/**
 * Jabber implementation of the jingle incoming file offer
 *
 * @author Eng Chong Meng
 */

public class OutgoingFileOfferJingleImpl extends AbstractFileTransfer {
    /**
     * Default number of fallback to use HttpFileUpload if previously has securityError
     */
    private static final int defaultErrorTimer = 10;

    // must include this attribute in bobData; else smack 4.4.0 throws NPE
    private static final int maxAge = 86400;

    /**
     * Fallback to use HttpFileUpload file transfer if previously has securityError i.e. not zero
     */
    private static final Map<Contact, Integer> mSecurityErrorTimer = new HashMap<>();

    private final JingleSessionImpl mJingleSession;
    private final String msgUuid;
    private final Contact mContact;
    private final File mFile;
    private int byteWrite;

    /**
     * The Jingle outgoing file offer.
     */
    private final OutgoingFileOfferController mOfoJingle;
    private final XMPPConnection mConnection;
    private BoBInfo bobInfo = null;

    /**
     * Creates an <code>OutgoingFileTransferJabberImpl</code> by specifying the <code>receiver</code>
     * contact, the <code>file</code> , the <code>jabberTransfer</code>, that would be used to send the file
     * through Jabber and the <code>protocolProvider</code>.
     *
     * @param recipient the destination contact
     * @param file the file to send
     * @param mUuid the id that uniquely identifies this file transfer and saved DB record
     * @param offer the Jabber OutgoingFileOfferController object
     * @param connection the XMPP Connection
     */
    public OutgoingFileOfferJingleImpl(Contact recipient, File file, String mUuid,
            OutgoingFileOfferController offer, XMPPConnection connection) {
        mContact = recipient;
        mFile = file;
        msgUuid = mUuid;
        mOfoJingle = offer;
        mConnection = connection;

        bobInfoInit(file);
        offer.addProgressListener(outFileProgressListener);
        mJingleSession = offer.getJingleSession();
        mJingleSession.addJingleSessionListener(jingleSessionListener);
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
     * Cleanup bobInfo from BoBManager, and remove OFO Listener:
     * a. When sender cancel file transfer (FileTransferConversation); nothing returns from remote.
     * b. onSessionTerminated() received from remote (uer declines or cancels during active transfer)
     */
    private void jingleFileOfferCleanup() {
        // Timber.d("Jingle File Offer Cleanup: %s", bobInfo);
        if (bobInfo != null) {
            BoBManager bobManager = BoBManager.getInstanceFor(mConnection);
            for (ContentId hash : bobInfo.getHashes()) {
                bobManager.removeBoB(hash);
            }
            bobInfo = null;
        }

        mOfoJingle.removeProgressListener(outFileProgressListener);
        mJingleSession.removeJingleSessionListener(jingleSessionListener);
    }

    /**
     * Cancel the file transfer.
     */
    @Override
    public void cancel() {
        try {
            mOfoJingle.cancel(mConnection);
        } catch (SmackException.NotConnectedException | InterruptedException | XMPPException.XMPPErrorException
                 | SmackException.NoResponseException e) {
            Timber.e("File send cancel exception: %s", e.getMessage());
        }

        // Must perform the following even if cancel failed due to remote: XMPPError: item-not-found - cancel
        jingleFileOfferCleanup();
        SessionState oldState = mOfoJingle.getJingleSession().getSessionState();
        fireStatusChangeEvent(FileTransferStatusChangeEvent.CANCELED, oldState.toString());
    }

    /**
     * Returns the number of bytes already sent to the recipient.
     *
     * @return the number of bytes already sent to the recipient.
     */
    @Override
    public long getTransferredBytes() {
        return byteWrite;
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

    // User interface for jingle file transfer progress status.
    ProgressListener outFileProgressListener = new ProgressListener() {
        @Override
        public void onStarted() {
            fireStatusChangeEvent(FileTransferStatusChangeEvent.IN_PROGRESS, "Byte sending started");
        }

        @Override
        public void progress(int rwBytes) {
            byteWrite = rwBytes;
            fireProgressChangeEvent(System.currentTimeMillis(), rwBytes);
        }

        @Override
        public void onFinished() {
            jingleFileOfferCleanup();
            fireStatusChangeEvent(FileTransferStatusChangeEvent.COMPLETED, "Byte sent completed");
        }

        @Override
        public void onError(JingleReason reason) {
            jingleSessionListener.onSessionTerminated(reason);
        }
    };

    JingleSessionListener jingleSessionListener = new JingleSessionListener() {
        @Override
        public void sessionStateUpdated(SessionState oldState, SessionState newState) {
            String sessionState = newState.toString();
            Timber.d("Jingle session state: %s => %s", oldState, newState);
            switch (newState) {
                case fresh:
                    fireStatusChangeEvent(FileTransferStatusChangeEvent.PREPARING, sessionState);
                    break;

                case pending:
                    fireStatusChangeEvent(FileTransferStatusChangeEvent.WAITING, sessionState);
                    break;

                case active:
                    fireStatusChangeEvent(FileTransferStatusChangeEvent.ACCEPT, sessionState);
                    break;

                case cancelled:
                    fireStatusChangeEvent(FileTransferStatusChangeEvent.DECLINED, sessionState);
                    break;

                case ended:
                    // This is triggered only on session terminate; while onFinished() is triggered
                    // upon end of stream sending. hence superseded the formal event.
                    // So "ended" event is not triggered, rely onFinished() instead.
                    fireStatusChangeEvent(FileTransferStatusChangeEvent.COMPLETED, sessionState);
                    break;
            }
        }

        public void onSessionInit() {
            // Waiting for remote to accept
            fireStatusChangeEvent(FileTransferStatusChangeEvent.WAITING, "In waiting");
        }

        @Override
        public void onSessionAccepted() {
            fireStatusChangeEvent(FileTransferStatusChangeEvent.IN_PROGRESS, "Session accepted");
        }

        @Override
        public void onSessionTerminated(JingleReason reason) {
            if (JingleReason.Reason.security_error.equals(reason.asEnum())) {
                mSecurityErrorTimer.put(mContact, defaultErrorTimer);
            }

            jingleFileOfferCleanup();
            fireStatusChangeEvent(reason);
        }
    };

    /**
     * Avoid use of Jet for file transfer if it is still within the securityErrorTimber count.
     *
     * @return true if the timer is not zero.
     */
    public static boolean hasSecurityError(Contact contact) {
        Integer errorTimer = mSecurityErrorTimer.get(contact);
        if ((errorTimer != null) && --errorTimer > 0) {
            mSecurityErrorTimer.put(contact, errorTimer);
            return true;
        }
        return false;
    }
}
