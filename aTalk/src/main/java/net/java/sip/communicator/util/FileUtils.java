/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package net.java.sip.communicator.util;

/**
 * Utility class that allows to check if a given file is an image or to obtain the file thumbnail icon.
 *
 * @author Yana Stamcheva
 * @author Eng Chong Meng
 */
public class FileUtils
{
    /**
     * Logger.
     */
    private static final Logger logger = Logger.getLogger(FileUtils.class);

    /**
     * Returns <code>true</code> if the file given by <tt>fileName</tt> is an
     * image, <tt>false</tt> - otherwise.
     *
     * @param fileName the name of the file to check
     * @return <code>true</code> if the file is an image, <tt>false</tt> - otherwise.
     */
    public static boolean isImage(String fileName)
    {
        fileName = fileName.toLowerCase();

        String[] imageTypes = {"jpeg", "jpg", "png", "gif"};

        for (String imageType : imageTypes) {
            if (fileName.endsWith(imageType)) {
                return true;
            }
        }
        return false;
    }
}
