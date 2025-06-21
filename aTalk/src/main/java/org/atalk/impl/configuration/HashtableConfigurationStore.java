/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.configuration;

import java.util.Hashtable;
import java.util.Set;

/**
 * A simple in-memory {@link ConfigurationStore} implementation that only uses a hashTable.
 *
 * @param <T> the hashTable extension that descendants are going to use.
 *
 * @author Lyubomir Marinov
 * @author Eng Chong Meng
 */
@SuppressWarnings("rawtypes")
public abstract class HashtableConfigurationStore<T extends Hashtable>
        implements ConfigurationStore {
    /**
     * The <code>HashTable</code> instance which stores the property name-value associations of this
     * <code>ConfigurationStore</code> instance and which is effectively adapted by this instance to
     * <code>ConfigurationStore</code>.
     */
    protected final T properties;

    /**
     * Creates an instance of this class using <code>properties</code> as the set of properties where
     * it will be storing an retrieving properties.
     *
     * @param properties the map that this store will use for storing and retrieving properties.
     */
    protected HashtableConfigurationStore(T properties) {
        this.properties = properties;
    }

    /**
     * Implements {@link ConfigurationStore#getProperty(String)}. If this
     * <code>ConfigurationStore</code> contains a value associated with the specified property name,
     * returns it. Otherwise, searches for a system property with the specified name and returns
     * its value.
     *
     * @param name the name of the property to get the value of
     *
     * @return the value in this <code>ConfigurationStore</code> of the property with the specified
     * name; <code>null</code> if the property with the specified name does not have an association
     * with a value in this <code>ConfigurationStore</code>
     *
     * @see ConfigurationStore#getProperty(String)
     */
    public Object getProperty(String name) {
        Object value = properties.get(name);
        return (value != null) ? value : System.getProperty(name);
    }

    /**
     * Implements {@link ConfigurationStore#getPropertyNames(String)}. Gets the names of the
     * properties which have values associated in this <code>ConfigurationStore</code>.
     *
     * @return an array of <code>String</code>s which specify the names of the properties that have
     * values associated in this <code>ConfigurationStore</code>; an empty array if this instance
     * contains no property values
     *
     * @see ConfigurationStore#getPropertyNames(String)
     */
    public String[] getPropertyNames(String name) {
        synchronized (properties) {
            Set<?> propertyNames = properties.keySet();
            return propertyNames.toArray(new String[0]);
        }
    }

    /**
     * Implements {@link ConfigurationStore#isSystemProperty(String)}. Considers a property to be
     * system if the system properties contain a value associated with its name.
     *
     * @param name the name of a property which is to be determined whether it is a system property
     *
     * @return <code>true</code> if the specified name stands for a system property; <code>false</code>,
     * otherwise
     *
     * @see ConfigurationStore#isSystemProperty(String)
     */
    public boolean isSystemProperty(String name) {
        return (System.getProperty(name) != null);
    }

    /**
     * Implements {@link ConfigurationStore#removeProperty(String)}. Removes the value association
     * in this <code>ConfigurationStore</code> of the property with a specific name. If the property
     * with the specified name is not associated with a value in this
     * <code>ConfigurationStore</code>, does nothing.
     *
     * @param name the name of the property which is to have its value association in this
     * <code>ConfigurationStore</code> removed
     *
     * @see ConfigurationStore#removeProperty(String)
     */
    public void removeProperty(String name) {
        properties.remove(name);
    }

    /**
     * Implements {@link ConfigurationStore#setNonSystemProperty(String, Object)}.
     *
     * @param name the name of the non-system property to be set to the specified value in this
     * <code>ConfigurationStore</code>
     * @param value the value to be assigned to the non-system property with the specified name in this
     * <code>ConfigurationStore</code>
     *
     * @see ConfigurationStore#setNonSystemProperty(String, Object)
     */
    @SuppressWarnings("unchecked")
    public void setNonSystemProperty(String name, Object value) {
        properties.put(name, value);
    }

    /**
     * Implements {@link ConfigurationStore#setSystemProperty(String)}. Since system properties
     * are managed through the <code>System</code> class, setting a property as system in this
     * <code>ConfigurationStore</code> effectively removes any existing value associated with the
     * specified property name from this instance.
     *
     * @param name the name of the property to be set as a system property in this
     * <code>ConfigurationStore</code>
     *
     * @see ConfigurationStore#setSystemProperty(String)
     */
    public void setSystemProperty(String name) {
        removeProperty(name);
    }
}
