/* 
 *  Licensed to the Apache Software Foundation (ASF) under one or more 
 *  contributor license agreements.  See the NOTICE file distributed with 
 *  this work for additional information regarding copyright ownership. 
 *  The ASF licenses this file to You under the Apache License, Version 2.0 
 *  (the "License"); you may not use this file except in compliance with 
 *  the License.  You may obtain a copy of the License at 
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0 
 * 
 *  Unless required by applicable law or agreed to in writing, software 
 *  distributed under the License is distributed on an "AS IS" BASIS, 
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. 
 *  See the License for the specific language governing permissions and 
 *  limitations under the License. 
 */

package android.org.apache.harmony.jndi.internal.parser;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import android.javax.naming.directory.BasicAttribute;
import android.javax.naming.ldap.Rdn;

import android.org.apache.harmony.jndi.internal.nls.Messages;

/**
 * This class maintains a list of relative distinguished names it supports
 * multiple values for any attribute
 */
public class LdapTypeAndValueList implements Map {

    private List list = null;

    private static final long serialVersionUID = -4540483835059087783L;

    private Map attributes = new TreeMap();

    /**
     * @return the list size
     */
    public int size() {
        return attributes.size();
    }

    /**
     * @return false when is not empty, true otherwise.
     */
    public boolean isEmpty() {
        return attributes.isEmpty();
    }

    /**
     * Used to check if a key exists in the list
     *
     * @return true if the key exists, false otherwise.
     */
    public boolean containsKey(Object key) {
        return attributes.containsKey(key);
    }

    /**
     * Used to check if a value exists in the list
     *
     * @return true if the value exists, false otherwise.
     */
    public boolean containsValue(Object value) {
        return attributes.containsValue(value);
    }

    /**
     * Used to get the values list from a key
     *
     * @return the list of values corresponding to the key
     */
    public Object get(Object key) {
        return attributes.get(key);
    }

    /**
     * Used to insert Key/Value pairs
     *
     * @param key
     *            the key
     * @param value
     *            a value
     * @return the list of values corresponding to this key
     */
    public Object put(Object key, Object value) {
        if (attributes.get(new ValueWrapper((String) key)) == null) {
            list = new ArrayList();
            list.add(new ValueWrapper(value));
            attributes.put(new ValueWrapper((String) key), list);
        } else {
            list = (List) attributes.get(new ValueWrapper((String) key));
            list.add(new ValueWrapper(value));
            Collections.sort(list);
            attributes.put(new ValueWrapper((String) key), list);
        }
        return list;
    }

    /**
     * Removes the list of values from the given key
     */
    public Object remove(Object key) {
        return attributes.remove(key);
    }

    public void putAll(Map map) {
        throw new UnsupportedOperationException(Messages.getString("ldap.1B"));
    }

    /**
     * Clears all contents of the list
     */
    public void clear() {
        attributes.clear();
    }

    public Set keySet() {
        return attributes.keySet();
    }

    /**
     * A list of attributes, each attribute can contain more than one value
     *
     * @return a list of Attribute
     */
    public List toAttributeList() {
        List list = new ArrayList();
        for (Iterator iter = keySet().iterator(); iter.hasNext();) {
            ValueWrapper element = (ValueWrapper) iter.next();
            BasicAttribute ba = new BasicAttribute(element.getStringValue(),
                    true);
            List list2 = (List) attributes.get(element);
            for (Iterator iterator = list2.iterator(); iterator.hasNext();) {
                ValueWrapper elementList = (ValueWrapper) iterator.next();
                ba.add(elementList.getValue());
            }
            list.add(ba);
        }
        return list;
    }

    public Collection values() {
        throw new UnsupportedOperationException(Messages.getString("ldap.1B"));
    }

    public Set entrySet() {
        return attributes.entrySet();
    }

    public String toString() {
        return attributes.toString();
    }

    /**
     * Its a wrapper for String/ByteArray that implements the Comparable
     * interface and uses an upper case string value to compare
     *
     * @author Juan Giannuzzo
     * @author Osvaldo C. Demo
     */
    private class ValueWrapper implements Comparable {

        private Object value;

        /**
         * Constructor
         *
         * @param value
         *            String value to make case insensitive
         */
        ValueWrapper(Object value) {
            this.value = value;
        }

        /**
         * Returns a String Value for the stored object value
         *
         * @return String value
         */
        public String getStringValue() {
            if (value instanceof String) {
                return (String) value;
            }
            return Rdn.escapeValue(value);
        }

        /**
         * Getter for value
         *
         * @return the value object (String or ByteArray)
         */
        public Object getValue() {
            return value;
        }

        /**
         * Used to compare CaseInsensitiveStrings
         *
         * @param obj
         *            object to compare
         * @return 0 when its equal, 1 when its greater, -1 otherwise
         * @throws ClassCastException
         *             when the object is not an instance of
         *             CaseInsensitiveString
         *
         */
        public int compareTo(Object obj) {
            return getStringValue().toUpperCase().compareTo(
                    ((ValueWrapper) obj).getStringValue().toUpperCase());
        }
    }

}