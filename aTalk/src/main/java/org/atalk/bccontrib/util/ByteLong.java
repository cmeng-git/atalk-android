/*
Copyright (c) 2010 Werner Dittmann

Permission is hereby granted, free of charge, to any person
obtaining a copy of this software and associated documentation
files (the "Software"), to deal in the Software without
restriction, including without limitation the rights to use,
copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the
Software is furnished to do so, subject to the following
conditions:

The above copyright notice and this permission notice shall be
included in all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES
OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT
HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR
OTHER DEALINGS IN THE SOFTWARE.
*/

package org.atalk.bccontrib.util;

/**
 * Conversion from byte to long and vice versa, long in little endian order.
 * 
 * @author Werner Dittmann (Werner.Dittmann@t-online.de)
 *
 */
public class ByteLong {

    /**
     * Creates a long from 8 bytes.
     * 
     * Because Java does not provide unsigned data types this is a bit tricky.
     * The function takes care to process the sign bit correctly. The created
     * {@code long} is in little endian order.
     * 
     * @param b
     *     The array that contains the bytes to combine.
     * @param i
     *     The offset into the byte array where the data starts.
     */
    public static long GetUInt64(byte[] b, int i) {
        if (i >= b.length + 8) {
            throw new ArrayIndexOutOfBoundsException();
        }
        return (((b[i++] & 255) | ((b[i++] & 255) << 8)
                | ((b[i++] & 255) << 16) | ((b[i++] & 255) << 24)) & 0xffffffffL)
                | (((b[i++] & 255) | ((b[i++] & 255) << 8)
                        | ((b[i++] & 255) << 16) | ((b[i] & 255L) << 24)) << 32);
    }

    /**
     * Disassmble an array of Long into a byte array.
     * 
     * @param input
     *     The long input array.
     * @param output
     *     The byte output array.
     * @param offset
     *     The offset into the output array.
     * @param byteCount
     *     The number of bytes to disassemble.
     */
    public static void PutBytes(long[] input, byte[] output, int offset, int byteCount) {
        int j = 0;
        for (int i = 0; i < byteCount; i++) {
            output[offset++] = (byte) ((input[i >> 3] >> j) & 255);
            j = (j + 8) & 63;
        }
    }
}
