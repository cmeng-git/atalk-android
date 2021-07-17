/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.protocol;

import net.java.sip.communicator.service.protocol.event.*;

import java.util.*;

import timber.log.Timber;

/**
 * An abstract implementation of the <tt>FileTransfer</tt> interface providing implementation of
 * status and progress events related methods and leaving all protocol specific methods abstract. A
 * protocol specific implementation could extend this class and implement only <tt>cancel()</tt> and
 * <tt>getTransferredBytes()</tt>.
 *
 * @author Yana Stamcheva
 * @author Eng Chong Meng
 */
public abstract class AbstractFileTransfer implements FileTransfer
{
    /**
     * A list of listeners registered for file transfer status events.
     */
    final private Vector<FileTransferStatusListener> statusListeners = new Vector<>();

    /**
     * A list of listeners registered for file transfer progress status events.
     */
    final private Vector<FileTransferProgressListener> progressListeners = new Vector<>();

    /*
     * current file transfer Status for keeping track if there is changes
     */
    protected int mStatus;

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
     *
     * @return the number of bytes already transferred through this file transfer
     */
    abstract public long getTransferredBytes();

    /**
     * Adds the given <tt>FileTransferProgressListener</tt> to listen for status changes on this file transfer.
     *
     * @param listener the listener to add
     */
    public void addProgressListener(FileTransferProgressListener listener)
    {
        synchronized (progressListeners) {
            if (!progressListeners.contains(listener)) {
                this.progressListeners.add(listener);
            }
        }
    }

    /**
     * Adds the given <tt>FileTransferStatusListener</tt> to listen for status changes on this file transfer.
     *
     * @param listener the listener to add
     */
    public void addStatusListener(FileTransferStatusListener listener)
    {
        synchronized (statusListeners) {
            if (!statusListeners.contains(listener)) {
                this.statusListeners.add(listener);
            }
        }
    }

    /**
     * Removes the given <tt>FileTransferProgressListener</tt>.
     *
     * @param listener the listener to remove
     */
    public void removeProgressListener(FileTransferProgressListener listener)
    {
        synchronized (progressListeners) {
            this.progressListeners.remove(listener);
        }
    }

    /**
     * Removes the given <tt>FileTransferStatusListener</tt>.
     *
     * @param listener the listener to remove
     */
    public void removeStatusListener(FileTransferStatusListener listener)
    {
        synchronized (statusListeners) {
            this.statusListeners.remove(listener);
        }
    }

    /**
     * Returns the current status of the transfer. This information could be used from the user
     * interface to show a progress bar indicating the file transfer status.
     *
     * @see FileTransferStatusChangeEvent (Active xfer status)
     * @see net.java.sip.communicator.service.filehistory.FileRecord (End of xfer status)
     *
     * @return the current status of the transfer
     */
    public int getStatus()
    {
        return mStatus;
    }

    /**
     * Notifies all status listeners that a new <tt>FileTransferStatusChangeEvent</tt> occured.
     *
     * @param newStatus the new status
     */
    public void fireStatusChangeEvent(int newStatus)
    {
        this.fireStatusChangeEvent(newStatus, null);
    }

    /**
     * Notifies all status listeners that a new <tt>FileTransferStatusChangeEvent</tt> occurred.
     *
     * @param newStatus the new status
     * @param reason the reason of the status change
     */
    public void fireStatusChangeEvent(int newStatus, String reason)
    {
        // ignore if status is the same
        if (mStatus == newStatus)
            return;

        // Updates the status.
        mStatus = newStatus;
        Collection<FileTransferStatusListener> listeners;
        synchronized (statusListeners) {
            listeners = new ArrayList<>(statusListeners);
        }
        Timber.d("Dispatching a FileTransfer Event to %d listeners. Status = %s", listeners.size(), newStatus);
        FileTransferStatusChangeEvent statusEvent = new FileTransferStatusChangeEvent(this, mStatus,
                newStatus, reason);

        for (FileTransferStatusListener statusListener : listeners) {
            statusListener.statusChanged(statusEvent);
        }
    }

    /**
     * Notifies all status listeners that a new <tt>FileTransferProgressEvent</tt> occurred.
     *
     * @param timestamp the date on which the event occurred
     * @param progress the bytes representing the progress of the transfer
     */
    public void fireProgressChangeEvent(long timestamp, long progress)
    {
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
