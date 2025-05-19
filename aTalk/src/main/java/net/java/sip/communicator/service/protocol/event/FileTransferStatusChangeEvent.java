/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.protocol.event;

import java.util.EventObject;

import net.java.sip.communicator.service.protocol.FileTransfer;

/**
 * The <code>FileTransferStatusChangeEvent</code> is the event indicating of a change in the state of a file transfer.
 *
 * @author Yana Stamcheva
 * @author Eng Chong Meng
 */
public class FileTransferStatusChangeEvent extends EventObject {
    /* ===============================================================
     File transfer status - track during actual file transfer process, and save in DB.
     Definitions should be in sync with FileRecord
      ===============================================================*/
    /**
     * Serial version UID.
     */
    private static final long serialVersionUID = 0L;

    /**
     * Indicates that the file transfer has been completed, or has finishing sending to stream.
     */
    public static final int COMPLETED = 10;

    /**
     * Indicates that the file transfer has failed.
     */
    public static final int FAILED = 11;

    /**
     * Indicates that the file transfer has been canceled.
     */
    public static final int CANCELED = 12;

    /**
     * Indicates that the file transfer has been declined.
     */
    public static final int DECLINED = 13;

    /**
     * Indicates that the file transfer is at the start of protocol initial state.
     */
    public static final int PREPARING = 14;

    /**
     * Indicates that the file transfer waits for the user/recipient decision e.g. accept/decline.
     */
    public static final int WAITING = 15;

    /**
     * Indicates that the recipient has accepted the file transfer.
     */
    public static final int ACCEPT = 16;
    /**
     * Indicates that the file transfer is in progress.
     */
    public static final int IN_PROGRESS = 17;


    public static final int UNKNOWN = -1;

    /**
     * The state of the file transfer before this event occurred.
     */
    private final int oldStatus;

    /**
     * The new state of the file transfer.
     */
    private final int newStatus;

    /**
     * The reason of this status change.
     */
    private final String reason;

    /**
     * Creates a <code>FileTransferStatusChangeEvent</code> by specifying the source
     * <code>fileTransfer</code>, the old transfer status and the new status.
     *
     * @param fileTransfer the source file transfer, for which this status change occured
     * @param oldStatus the old status
     * @param newStatus the new status
     * @param reason the reason of this status change
     */
    public FileTransferStatusChangeEvent(FileTransfer fileTransfer, int oldStatus, int newStatus, String reason) {
        super(fileTransfer);

        this.oldStatus = oldStatus;
        this.newStatus = newStatus;
        this.reason = reason;
    }

    /**
     * Returns the source <code>FileTransfer</code> that triggered this event.
     *
     * @return the source <code>FileTransfer</code> that triggered this event
     */
    public FileTransfer getFileTransfer() {
        return (FileTransfer) source;
    }

    /**
     * Returns the state of the file transfer before this event occured.
     *
     * @return the old state
     */
    public int getOldStatus() {
        return oldStatus;
    }

    /**
     * The new state of the file transfer.
     *
     * @return the new state
     */
    public int getNewStatus() {
        return newStatus;
    }

    /**
     * Returns the reason of the status change.
     *
     * @return the reason of the status change
     */
    public String getReason() {
        return reason;
    }
}
