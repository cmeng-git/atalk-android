/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.protocol.event;

import net.java.sip.communicator.service.protocol.IncomingFileTransferRequest;
import net.java.sip.communicator.service.protocol.OperationSetFileTransfer;

import java.util.Date;
import java.util.EventObject;

/**
 * The <code>FileTransferRequestEvent</code> indicates the reception of a file transfer request.
 *
 * @author Nicolas Riegel
 * @author Yana Stamcheva
 */
public class FileTransferRequestEvent extends EventObject
{
    /**
     * Serial version UID.
     */
    private static final long serialVersionUID = 0L;

    /**
     * The request that triggered this event.
     */
    private final IncomingFileTransferRequest request;

    /**
     * The timestamp indicating the exact date when the event occurred.
     */
    private final Date timestamp;

    /**
     * Creates a <code>FileTransferRequestEvent</code> representing reception of an incoming file
     * transfer request.
     *
     * @param fileTransferOpSet the operation set, where this event initially occurred
     * @param request the <code>IncomingFileTransferRequest</code> whose reception this event represents.
     * @param timestamp the timestamp indicating the exact date when the event occurred
     */
    public FileTransferRequestEvent(OperationSetFileTransfer fileTransferOpSet,
            IncomingFileTransferRequest request, Date timestamp)
    {
        super(fileTransferOpSet);

        this.request = request;
        this.timestamp = timestamp;
    }

    /**
     * Returns the <code>OperationSetFileTransfer</code>, where this event initially occurred.
     *
     * @return the <code>OperationSetFileTransfer</code>, where this event initially occurred
     */
    public OperationSetFileTransfer getFileTransferOperationSet()
    {
        return (OperationSetFileTransfer) getSource();
    }

    /**
     * Returns the incoming file transfer request that triggered this event.
     *
     * @return the <code>IncomingFileTransferRequest</code> that triggered this event.
     */
    public IncomingFileTransferRequest getRequest()
    {
        return request;
    }

    /**
     * A timestamp indicating the exact date when the event occurred.
     *
     * @return a Date indicating when the event occurred.
     */
    public Date getTimestamp()
    {
        return timestamp;
    }
}
