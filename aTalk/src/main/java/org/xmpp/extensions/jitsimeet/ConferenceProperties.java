/*
 * Copyright @ 2018 - present 8x8, Inc.
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
package org.xmpp.extensions.jitsimeet;

import org.xmpp.extensions.AbstractExtensionElement;

import java.util.List;
import java.util.Objects;

import javax.xml.namespace.QName;

/**
 * A packet extension that represents a list of
 * {@link ConferenceProperty}s to be included in the focus MUC presence. The
 * idea is to use it for stuff like recording status, etherpad URL, and other
 * conference related information.
 *
 * @author George Politis
 * @author Eng Chong Meng
 */
public class ConferenceProperties extends AbstractExtensionElement
{
    /**
     * The XML namespace of this element.
     */
    public static final String NAMESPACE = ConferenceIq.NAMESPACE;

    /**
     * The XML name of the conference-properties element.
     */
    public static final String ELEMENT = "conference-properties";

    public static final QName QNAME = new QName(NAMESPACE, ELEMENT);

    /**
     * The property key used for the conference creation timestamp (in milliseconds since the Epoch).
     */
    public static final String KEY_CREATED_MS = "created-ms";

    /**
     * The property key used for an to signal whether octo is enabled or
     * disabled. Note that Octo can be enabled but not in use (e.g. when all
     * participants in the conference are in the same region).
     */
    public static final String KEY_OCTO_ENABLED = "octo-enabled";

    /**
     * The property key used to signal the number of jitsi-videobridge instances
     * currently used in the conference (a count larger than 1 indicates that Octo is being used).
     */
    public static final String KEY_BRIDGE_COUNT = "bridge-count";

    /**
     * Creates a deep copy of a {@link ConferenceProperties} instance.
     *
     * @param source the {@link ConferenceProperties} to copy.
     * @return the copy.
     */
    public static ConferenceProperties clone(ConferenceProperties source)
    {
        ConferenceProperties destination = AbstractExtensionElement.clone(source);

        for (ConferenceProperty property : source.getProperties()) {
            destination.addProperty(ConferenceProperty.clone(property));
        }
        return destination;
    }

    /**
     * Ctor.
     */
    public ConferenceProperties()
    {
        super(ELEMENT, NAMESPACE);
    }

    /**
     * @return the list of all {@link ConferenceProperty}.
     */
    public List<ConferenceProperty> getProperties()
    {
        return getChildExtensionsOfType(ConferenceProperty.class);
    }

    /**
     * Adds a specific {@link ConferenceProperty} object to this
     * {@link ConferenceProperties}. Existing properties with the same key are removed.
     *
     * @param property the property to add.
     */
    public void addProperty(ConferenceProperty property)
    {
        clear(property.getKey());
        addChildExtension(property);
    }

    /**
     * Associates the specified value with the specified key in this properties
     * list.
     *
     * @param key key with which the specified value is to be associated
     * @param value value to be associated with the specified key
     */
    public void put(String key, String value)
    {
        clear(key);
        addChildExtension(new ConferenceProperty(key, value));
    }

    /**
     * Removes all {@link ConferenceProperty}-s with a given key from this
     * instance.
     *
     * @param key the key to match.
     */
    private void clear(String key)
    {
        Objects.requireNonNull(key);

        for (ConferenceProperty property :
                getChildExtensionsOfType(ConferenceProperty.class)) {
            if (key.equals(property.getKey())) {
                removeChildExtension(property);
            }
        }
    }

    /**
     * A packet extension that represents a key-value
     * pair to be included in the focus MUC presence.
     *
     * @author George Politis
     */
    public static class ConferenceProperty extends AbstractExtensionElement
    {
        /**
         * The XML name of the conference property element.
         */
        public static final String ELEMENT = "property";

        /**
         * The name of the "key" attribute.
         */
        public static final String KEY_ATTR_NAME = "key";

        /**
         * The name of the "value" attribute.
         */
        public static final String VALUE_ATTR_NAME = "value";

        /**
         * This should not be used externally, because it leaves the instance
         * without a "key" and "vale" attribute. It is needed (with public access)
         * because of {@link AbstractExtensionElement#clone()}, though.
         */
        public ConferenceProperty()
        {
            super(ELEMENT, NAMESPACE);
        }

        /**
         * Ctor.
         *
         * @param key key with which the specified value is to be associated
         * @param value value to be associated with the specified key
         */
        public ConferenceProperty(String key, String value)
        {
            super(ELEMENT, NAMESPACE);
            setAttribute(KEY_ATTR_NAME, key);
            setAttribute(VALUE_ATTR_NAME, value);
        }

        /**
         * @return the key of this {@link ConferenceProperty}.
         */
        public String getKey()
        {
            return (String) getAttribute("key");
        }

        /**
         * @return the value of this {@link ConferenceProperty}.
         */
        public String getValue()
        {
            return (String) getAttribute("value");
        }
    }
}
