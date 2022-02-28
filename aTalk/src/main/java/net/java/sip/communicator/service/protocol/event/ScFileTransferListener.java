/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.protocol.event;

import java.util.EventListener;

/**
 * A listener that would gather events notifying of incoming file transfer requests.
 *
 * @author Emil Ivov
 * @author Yana Stamcheva
 * @author Eng Chong Meng
 */
public interface ScFileTransferListener extends EventListener
{
    /**
     * Called when a new <code>IncomingFileTransferRequest</code> has been received.
     *
     * @param event the <code>FileTransferRequestEvent</code> containing the newly received request and other details.
     */
    void fileTransferRequestReceived(FileTransferRequestEvent event);

    /**
     * Called when a <code>FileTransferCreatedEvent</code> has been received.
     *
     * @param event the <code>FileTransferCreatedEvent</code> containing the newly received file transfer and other details.
     */
    void fileTransferCreated(FileTransferCreatedEvent event);

    /**
     * Called when an <code>IncomingFileTransferRequest</code> has been rejected.
     *
     * @param event the <code>FileTransferRequestEvent</code> containing the received request which was rejected.
     */
    void fileTransferRequestRejected(FileTransferRequestEvent event);

    /**
     * Called when an <code>IncomingFileTransferRequest</code> has been canceled from the contact who sent it.
     *
     * @param event the <code>FileTransferRequestEvent</code> containing the request which was canceled.
     */
    void fileTransferRequestCanceled(FileTransferRequestEvent event);
}
