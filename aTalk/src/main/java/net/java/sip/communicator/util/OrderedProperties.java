/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package net.java.sip.communicator.util;

import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

/**
 * Implementation of Properties that keep order of couples [key, value] added.
 *
 * @author Sebastien Vincent
 * @author Eng Chong Meng
 */
public class OrderedProperties extends Properties {
    /**
     * Serial version UID.
     */
    private static final long serialVersionUID = 0L;

    /**
     * A linked hashmap to keep entry in order.
     */
    private final LinkedHashMap<Object, Object> linkedMap = new LinkedHashMap<>();

    /**
     * Get the object pointed by key.
     *
     * @param key key
     *
     * @return value pointed by key or null if not set
     */
    @Override
    public Object get(Object key) {
        return linkedMap.get(key);
    }

    /**
     * Put an couple key, value
     *
     * @param key key
     * @param value value
     *
     * @return previous value pointed by key if any, null otherwise
     */
    @Override
    public Object put(Object key, Object value) {
        return linkedMap.put(key, value);
    }

    /**
     * Remove a key entry
     *
     * @param key key
     *
     * @return previous value pointed by key if any, null otherwise
     */
    @Override
    public Object remove(Object key) {
        return linkedMap.remove(key);
    }

    /**
     * Clear the entries.
     */
    @Override
    public void clear() {
        linkedMap.clear();
    }

    /**
     * Get the keys enumeration.
     *
     * @return keys enumeration
     */
    @Override
    public Enumeration<Object> keys() {
        return Collections.enumeration(linkedMap.keySet());
    }

    /**
     * Get the elements of the <code>LinkedHashMap</code>.
     *
     * @return enumeration
     */
    @Override
    public Enumeration<Object> elements() {
        return Collections.enumeration(linkedMap.values());
    }

    /**
     * Return the entry Set.
     *
     * @return entry Set
     */
    @Override
    public Set<Map.Entry<Object, Object>> entrySet() {
        return linkedMap.entrySet();
    }

    /**
     * Get number of elements.
     *
     * @return number of elements
     */
    @Override
    public int size() {
        return linkedMap.size();
    }
}
