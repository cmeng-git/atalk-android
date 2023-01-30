/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.impl.protocol.jabber;

import net.java.sip.communicator.service.protocol.OperationSetThumbnailedFileFactory;

import java.io.File;

/**
 * The <code>OperationSetThumbnailedFileFactory</code> is meant to be used by bundles interested in
 * making files with thumbnails. For example the user interface can be interested in sending files
 * with thumbnails through the <code>OperationSetFileTransfer</code>.
 *
 * @author Yana Stamcheva
 * @author Eng Chong Meng
 */
public class OperationSetThumbnailedFileFactoryImpl implements OperationSetThumbnailedFileFactory
{
    /**
     * Creates a file, by attaching the thumbnail, given by the details, to it.
     *
     * @param file the base file
     * @param thumbnailWidth the width of the thumbnail
     * @param thumbnailHeight the height of the thumbnail
     * @param thumbnailMimeType the mime type of the thumbnail
     * @param thumbnail the thumbnail data, must not be null
     * @return a file with a thumbnail
     */
    public File createFileWithThumbnail(File file, int thumbnailWidth, int thumbnailHeight,
            String thumbnailMimeType, byte[] thumbnail)
    {
        return new ThumbnailedFile(file, thumbnailWidth, thumbnailHeight, thumbnailMimeType, thumbnail);
    }
}
