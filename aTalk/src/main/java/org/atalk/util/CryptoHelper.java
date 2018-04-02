package org.atalk.util;

import java.util.Locale;
import java.util.regex.Pattern;

public final class CryptoHelper
{
    public static final String FILETRANSFER = "?FILETRANSFERv1:";
    private final static char[] hexArray = "0123456789abcdef".toCharArray();

    public static final Pattern UUID_PATTERN = Pattern.compile("[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}");
    final public static byte[] ONE = new byte[]{0, 0, 0, 1};

    public static String bytesToHex(byte[] bytes)
    {
        char[] hexChars = new char[bytes.length * 2];
        for (int j = 0; j < bytes.length; j++) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = hexArray[v >>> 4];
            hexChars[j * 2 + 1] = hexArray[v & 0x0F];
        }
        return new String(hexChars);
    }

    public static byte[] hexToBytes(String hexString)
    {
        int len = hexString.length();
        byte[] array = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            array[i / 2] = (byte) ((Character.digit(hexString.charAt(i), 16) << 4) + Character
                    .digit(hexString.charAt(i + 1), 16));
        }
        return array;
    }

    public static String hexToString(final String hexString)
    {
        return new String(hexToBytes(hexString));
    }

    public static byte[] concatenateByteArrays(byte[] a, byte[] b)
    {
        byte[] result = new byte[a.length + b.length];
        System.arraycopy(a, 0, result, 0, a.length);
        System.arraycopy(b, 0, result, a.length, b.length);
        return result;
    }

    public static String prettifyFingerprint(String fingerprint)
    {
        if (fingerprint == null) {
            return "";
        }
        else if (fingerprint.length() < 40) {
            return fingerprint;
        }
        StringBuilder builder = new StringBuilder(fingerprint.toLowerCase(Locale.US).replaceAll("\\s", ""));
        for (int i = 8; i < builder.length(); i += 9) {
            builder.insert(i, ' ');
        }
        return builder.toString();
    }

    public static String prettifyFingerprintCert(String fingerprint)
    {
        StringBuilder builder = new StringBuilder(fingerprint);
        for (int i = 2; i < builder.length(); i += 3) {
            builder.insert(i, ':');
        }
        return builder.toString();
    }
}
