/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.protocol.event;

import java.util.EventObject;

import net.java.sip.communicator.service.protocol.FileTransfer;

/**
 * The <code>FileTransferProgressEvent</code> indicates the progress of a file transfer.
 *
 * @author Yana Stamcheva
 * @author Eng Chong Meng
 */
public class FileTransferProgressEvent extends EventObject {
    /**
     * Serial version UID.
     */
    private static final long serialVersionUID = 0L;

    /**
     * Indicates the progress of a file transfer in bytes.
     */
    private final long mProgress;

    /**
     * Indicates when this event occurred.
     */
    private final long mTimestamp;

    /**
     * Creates a <code>FileTransferProgressEvent</code> by specifying the source file transfer object,
     * that triggered the event and the new progress value.
     *
     * @param fileTransfer the source file transfer object, that triggered the event
     * @param timestamp when this event occurred
     * @param progress the new progress value
     */
    public FileTransferProgressEvent(FileTransfer fileTransfer, long timestamp, long progress) {
        super(fileTransfer);
        mTimestamp = timestamp;
        mProgress = progress;
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
     * Returns the progress of the file transfer in transferred bytes.
     *
     * @return the progress of the file transfer
     */
    public long getProgress() {
        return mProgress;
    }

    /**
     * Returns the timestamp when this event initially occurred.
     *
     * @return the timestamp when this event initially occurred
     */
    public long getTimestamp() {
        return mTimestamp;
    }
}
