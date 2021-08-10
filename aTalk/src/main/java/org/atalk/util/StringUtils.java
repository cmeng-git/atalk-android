/*
 * Copyright @ 2019 - Present 8x8, Inc
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.atalk.util;

import java.io.*;
import java.nio.charset.Charset;

/**
 * Implements utility functions to facilitate work with <tt>String</tt>s.
 *
 * @author Grigorii Balutsel
 * @author Emil Ivov
 * @author Eng Chong Meng
 *
 * Use StringUtils from apache commons except methods defined below.
 */
public final class StringUtils
{
    /**
     * Prevents the initialization of <tt>StringUtils</tt> instances because the
     * <tt>StringUtils</tt> class implements utility function only.
     */
    private StringUtils()
    {
    }

    /**
     * Creates <tt>InputStream</tt> from the string in UTF8 encoding.
     *
     * @param string the string to convert.
     * @return the <tt>InputStream</tt>.
     * @throws UnsupportedEncodingException if UTF8 is unsupported.
     */
    public static InputStream fromString(String string)
            throws UnsupportedEncodingException
    {
        return fromString(string, "UTF-8");
    }

    /**
     * Creates <tt>InputStream</tt> from the string in the specified encoding.
     *
     * @param string the string to convert.
     * @param encoding the encoding
     * @return the <tt>InputStream</tt>.
     * @throws UnsupportedEncodingException if the encoding is unsupported.
     */
    public static InputStream fromString(String string, String encoding)
            throws UnsupportedEncodingException
    {
        return new ByteArrayInputStream(string.getBytes(encoding));
    }

    /**
     * Initializes a new <tt>String</tt> instance by decoding a specified array of bytes (mostly used by JNI).
     *
     * @param bytes the bytes to be decoded into characters/a new <tt>String</tt> instance
     * @return a new <tt>String</tt> instance whose characters are decoded from the specified <tt>bytes</tt>
     */
    public static String newString(byte[] bytes)
    {
        if ((bytes == null) || (bytes.length == 0))
            return null;
        else {
            String charsetName = Charset.defaultCharset().name();
            try {
                return new String(bytes, charsetName);
            } catch (UnsupportedEncodingException ueex) {
                return new String(bytes);
            }
        }
    }
}
