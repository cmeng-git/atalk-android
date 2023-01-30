/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */

package net.java.sip.communicator.service.protocol;

import java.io.File;

/**
 * Used for incoming file transfer request.
 *
 * @author Nicolas Riegel
 * @author Yana Stamcheva
 * @author Eng Chong Meng
 */
public interface IncomingFileTransferRequest
{
    /**
     * Recipient has prepared and ready to receive the incoming file offer;
     * listen in for any event for action e.g. remote cancel the file transfer before start
     *
     * @param file the file to accept
     * @return the <code>FileTransfer</code> object managing the transfer
     */
    FileTransfer onPrepare(File file);

    /**
     * Unique ID that is identifying the request and then the FileTransfer if the request has been accepted.
     *
     * @return the id.
     */
    String getID();

    /**
     * Returns a String that represents the name of the file that is being received. If there is no
     * name, returns null.
     *
     * @return a String that represents the name of the file
     */
    String getFileName();

    /**
     * Returns a String that represents the description of the file that is being received. If there
     * is no description available, returns null.
     *
     * @return a String that represents the description of the file
     */
    String getFileDescription();

    /**
     * Identifies the type of file that is desired to be transferred.
     *
     * @return The mime-type.
     */
    String getMimeType();

    /**
     * Returns a long that represents the size of the file that is being received. If there is no
     * file size available, returns null.
     *
     * @return a long that represents the size of the file
     */
    long getFileSize();

    /**
     * Returns a String that represents the name of the sender of the file being received. If there
     * is no sender name available, returns null.
     *
     * @return a String that represents the name of the sender
     */
    Contact getSender();

    /**
     * Returns the encryption of the incoming file corresponding to this FileTransfer.
     *
     * @return the encryption of the file corresponding to this request
     */
    int getEncryptionType();

    /**
     * Function called to accept and receive the file.
     *
     * @param file the file to accept
     * @return the <code>FileTransfer</code> object managing the transfer
     */
    void acceptFile();

    /**
     * Function called to decline the file offer.
     */
    void declineFile()
        throws OperationFailedException;

    /**
     * Returns the thumbnail contained in this request.
     *
     * @return the thumbnail contained in this request
     */
    byte[] getThumbnail();
}
