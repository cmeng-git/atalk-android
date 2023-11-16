/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.configuration;

import java.util.Arrays;
import java.util.Enumeration;
import java.util.Properties;

/**
 * This class is a sorted version of classical <code>java.util.Properties</code>. It is strongly
 * inspired by http://forums.sun.com/thread.jspa?threadID=141144.
 *
 * @author Sebastien Vincent
 * @author Damian Minkov
 */
public class SortedProperties extends Properties
{
    /**
     * Serial version UID.
     */
    private static final long serialVersionUID = 0L;

    /**
     * Gets an <code>Enumeration</code> of the keys in this <code>Properties</code> object. Contrary to
     * the original <code>Properties</code> implementation, it forces the keys to be alphabetically
     * sorted.
     *
     * @return an <code>Enumeration</code> of the keys in this <code>Properties</code> object
     */
    @Override
    public synchronized Enumeration<Object> keys()
    {
        final Object[] keys = keySet().toArray();

        Arrays.sort(keys);
        return new Enumeration<Object>()
        {
            private int i = 0;

            public boolean hasMoreElements()
            {
                return i < keys.length;
            }

            public Object nextElement()
            {
                return keys[i++];
            }
        };
    }

    /**
     * Does not allow putting empty <code>String</code> keys in this <code>Properties</code> object.
     *
     * @param key the key
     * @param value the value
     * @return the previous value of the specified <code>key</code> in this <code>Hashtable</code>, or
     * <code>null</code> if it did not have one
     */
    @Override
    public synchronized Object put(Object key, Object value)
    {
        /*
         * We discovered a special case related to the Properties ConfigurationService
         * implementation during testing in which the key was a String composed of null characters
         * only (which would be trimmed) consumed megabytes of heap. Do now allow such keys.
         */
        if (key.toString().trim().length() == 0)
            return null;

        return super.put(key, value);
    }
}
