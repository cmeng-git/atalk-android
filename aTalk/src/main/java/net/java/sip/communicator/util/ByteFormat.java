/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.util;

import java.text.*;

import androidx.annotation.NonNull;

/**
 * Acknowledgment: This file was originally provided by the Ignite Realtime community, and was part
 * of the Spark project (distributed under the terms of the LGPL).
 * <p/>
 * A formatter for formatting byte sizes. For example, formatting 12345 bytes results in "12.1 K"
 * and 1234567 results in "1.18 MB".
 *
 * @author Bill Lynch
 * @author Eng Chong Meng
 */
public class ByteFormat extends Format
{
    private static final long serialVersionUID = 0;

    public ByteFormat()
    {
    }

    /**
     * Formats a long which represent a number of bytes to human readable form.
     *
     * @param bytes the value to format
     * @return formatted string
     */
    public static String format(long bytes)
    {
        long check = 1;

        // sizes
        String[] sufixes = {"", " bytes", " KB", " MB", " GB"};

        for (int i = 1; i <= 4; i++) {
            long tempCheck = check * 1024;

            if (bytes < tempCheck || i == 4) {
                return new DecimalFormat(check == 1 ? "#,##0" :
                        "#,##0.0").format((double) bytes / check) + sufixes[i];
            }
            check = tempCheck;
        }
        // we are not suppose to come to here
        return null;
    }

    /**
     * Format the given object (must be a Long).
     *
     * @param obj assumed to be the number of bytes as a Long.
     * @param buf the StringBuffer to append to.
     * @param pos field position.
     * @return A formatted string representing the given bytes in more human-readable form.
     */
    @Override
    public StringBuffer format(Object obj, @NonNull StringBuffer buf, @NonNull FieldPosition pos)
    {
        if (obj instanceof Long) {
            long numBytes = (Long) obj;
            if (numBytes < 1024) {
                DecimalFormat formatter = new DecimalFormat("#,##0");
                buf.append(formatter.format((double) numBytes)).append(" bytes");
            }
            else if (numBytes < 1024 * 1024) {
                DecimalFormat formatter = new DecimalFormat("#,##0.0");
                buf.append(formatter.format(numBytes / 1024.0)).append(" KB");
            }
            else if (numBytes < 1024 * 1024 * 1024) {
                DecimalFormat formatter = new DecimalFormat("#,##0.0");
                buf.append(formatter.format(numBytes / (1024.0 * 1024.0))).append(" MB");
            }
            else {
                DecimalFormat formatter = new DecimalFormat("#,##0.0");
                buf.append(formatter.format(numBytes / (1024.0 * 1024.0 * 1024.0))).append(" GB");
            }
        }
        return buf;
    }

    /**
     * In this implementation, returns null always.
     *
     * @param source Source string to parse.
     * @param pos Position to parse from.
     * @return returns null in this implementation.
     */
    @Override
    public Object parseObject(String source, @NonNull ParsePosition pos)
    {
        return null;
    }
}
