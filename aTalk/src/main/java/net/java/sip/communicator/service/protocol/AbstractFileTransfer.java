/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.protocol;

import net.java.sip.communicator.service.protocol.event.FileTransferProgressEvent;
import net.java.sip.communicator.service.protocol.event.FileTransferProgressListener;
import net.java.sip.communicator.service.protocol.event.FileTransferStatusChangeEvent;
import net.java.sip.communicator.service.protocol.event.FileTransferStatusListener;

import org.atalk.android.R;
import org.atalk.android.aTalkApp;
import org.jivesoftware.smackx.jingle.element.JingleReason;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Vector;

import timber.log.Timber;

/**
 * An abstract implementation of the <code>FileTransfer</code> interface providing implementation of
 * status and progress events related methods and leaving all protocol specific methods abstract. A
 * protocol specific implementation could extend this class and implement only <code>cancel()</code> and
 * <code>getTransferredBytes()</code>.
 *
 * @author Yana Stamcheva
 * @author Eng Chong Meng
 */
public abstract class AbstractFileTransfer implements FileTransfer {
    /**
     * A list of listeners registered for file transfer status events.
     */
    final private Vector<FileTransferStatusListener> statusListeners = new Vector<>();

    /**
     * A list of listeners registered for file transfer progress status events.
     */
    final private Vector<FileTransferProgressListener> progressListeners = new Vector<>();

    /*
     * current file transfer Status for keeping track if there is changes;
     * Default to WAITING for contact to accept on start up.
     */
    protected int mStatus = FileTransferStatusChangeEvent.WAITING;

    /*
     * current progress of byte transferred for keeping track if there is changes
     */
    private long mProgress;

    /**
     * Cancels this file transfer. When this method is called transfer should be interrupted.
     */
    abstract public void cancel();

    /**
     * Returns the number of bytes already transferred through this file transfer.
     * Note Some file transfer progress is handled via event trigger.
     *
     * @return the number of bytes already transferred through this file transfer
     */
    @Override
    public long getTransferredBytes() {
        return -1;
    }

    /**
     * Adds the given <code>FileTransferProgressListener</code> to listen for status changes on this file transfer.
     *
     * @param listener the listener to add
     */
    public void addProgressListener(FileTransferProgressListener listener) {
        synchronized (progressListeners) {
            if (!progressListeners.contains(listener)) {
                progressListeners.add(listener);
            }
        }
    }

    /**
     * Adds the given <code>FileTransferStatusListener</code> to listen for status changes on this file transfer.
     *
     * @param listener the listener to add
     */
    public void addStatusListener(FileTransferStatusListener listener) {
        synchronized (statusListeners) {
            if (!statusListeners.contains(listener)) {
                statusListeners.add(listener);
            }
        }
    }

    /**
     * Removes the given <code>FileTransferProgressListener</code>.
     *
     * @param listener the listener to remove
     */
    public void removeProgressListener(FileTransferProgressListener listener) {
        synchronized (progressListeners) {
            progressListeners.remove(listener);
        }
    }

    /**
     * Removes the given <code>FileTransferStatusListener</code>.
     *
     * @param listener the listener to remove
     */
    public void removeStatusListener(FileTransferStatusListener listener) {
        synchronized (statusListeners) {
            statusListeners.remove(listener);
        }
    }

    /**
     * Returns the current status of the transfer. This information could be used from the user
     * interface to show a progress bar indicating the file transfer status.
     *
     * @return the current status of the transfer
     * @see FileTransferStatusChangeEvent (Active xfer status)
     * @see net.java.sip.communicator.service.filehistory.FileRecord (End of xfer status)
     */
    public int getStatus() {
        return mStatus;
    }

    /**
     * Notifies all status listeners that a new <code>FileTransferStatusChangeEvent</code> has occurred.
     *
     * @param reason the jingle terminate reason
     */
    public void fireStatusChangeEvent(JingleReason reason) {
        String reasonText = (reason.getText() != null) ? reason.getText() : reason.asEnum().toString();
        // Timber.d("SetStatus# jingle reason: %s", reason.asEnum());
        switch (reason.asEnum()) {
            case decline:
                fireStatusChangeEvent(FileTransferStatusChangeEvent.DECLINED, reasonText);
                break;
            case cancel:
                fireStatusChangeEvent(FileTransferStatusChangeEvent.CANCELED, reasonText);
                break;
            case success:
                fireStatusChangeEvent(FileTransferStatusChangeEvent.COMPLETED, reasonText);
                break;
            default: {
                reasonText = aTalkApp.getResString(R.string.service_gui_FILE_SEND_CLIENT_ERROR, reasonText);
                fireStatusChangeEvent(FileTransferStatusChangeEvent.FAILED, reasonText);
            }
        }
    }

    /**
     * Notifies all status listeners that a new <code>FileTransferStatusChangeEvent</code> has occurred.
     *
     * @param newStatus the new status
     * @param reason the reason of the status change
     */
    public void fireStatusChangeEvent(int newStatus, String reason) {
        // Just ignore if status is the same
        if (mStatus == newStatus)
            return;

        Collection<FileTransferStatusListener> listeners;
        synchronized (statusListeners) {
            listeners = new ArrayList<>(statusListeners);
        }
        Timber.d("Dispatching FileTransfer status change: %s => %s to %d listeners.",
                mStatus, newStatus, listeners.size());

        // Updates the mStatus only after statusEvent is created.
        FileTransferStatusChangeEvent statusEvent
                = new FileTransferStatusChangeEvent(this, mStatus, newStatus, reason);
        mStatus = newStatus;

        for (FileTransferStatusListener statusListener : listeners) {
            statusListener.statusChanged(statusEvent);
        }
    }

    /**
     * Notifies all status listeners that a new <code>FileTransferProgressEvent</code> occurred.
     *
     * @param timestamp the date on which the event occurred
     * @param progress the bytes representing the progress of the transfer
     */
    public void fireProgressChangeEvent(long timestamp, long progress) {
        // ignore if there is no change since the last progress check
        if (mProgress == progress)
            return;

        mProgress = progress;
        Collection<FileTransferProgressListener> listeners;
        synchronized (progressListeners) {
            listeners = new ArrayList<>(progressListeners);
        }

        FileTransferProgressEvent progressEvent = new FileTransferProgressEvent(this, timestamp, progress);
        for (FileTransferProgressListener statusListener : listeners) {
            statusListener.progressChanged(progressEvent);
        }
    }
}
