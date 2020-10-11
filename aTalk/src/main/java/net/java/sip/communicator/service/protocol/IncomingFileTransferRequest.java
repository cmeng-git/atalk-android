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
     * Function called to accept and receive the file.
     *
     * @param file the file to accept
     * @return the <tt>FileTransfer</tt> object managing the transfer
     */
    FileTransfer acceptFile(File file);

    /**
     * Function called to refuse the file.
     */
    void rejectFile()
        throws OperationFailedException;

    /**
     * Returns the thumbnail contained in this request.
     *
     * @return the thumbnail contained in this request
     */
    byte[] getThumbnail();
}
