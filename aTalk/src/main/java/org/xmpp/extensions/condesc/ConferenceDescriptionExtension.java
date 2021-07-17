/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Copyright @ 2015 Atlassian Pty Ltd
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
package org.xmpp.extensions.condesc;

import org.xmpp.extensions.AbstractExtensionElement;

import java.util.HashSet;
import java.util.Set;

import javax.xml.namespace.QName;

/**
 * A <tt>ExtensionElement</tt> that represents a <tt>ConferenceDescription</tt> object in XML.
 *
 * @author Boris Grozev
 * @author Eng Chong Meng
 */
public class ConferenceDescriptionExtension extends AbstractExtensionElement
{
    /**
     * The namespace for the XML element.
     */
    public static final String NAMESPACE = "http://jitsi.org/protocol/condesc";

    /**
     * The name of the "conference" XML element.
     */
    public static final String ELEMENT = "conference";

    public static final QName QNAME = new QName(NAMESPACE, ELEMENT);

    /**
     * The name of the "uri" attribute.
     */
    public static final String URI_ATTR_NAME = "uri";

    /**
     * The name of the "password" attribute.
     */
    public static final String PASSWORD_ATTR_NAME = "auth";

    /**
     * The name of the "callid" attribute.
     */
    public static final String CALLID_ATTR_NAME = "callid";

    /**
     * The name of the "available" attribute.
     */
    public static final String AVAILABLE_ATTR_NAME = "available";

    /**
     * The name of the conference name attribute.
     */
    public static final String CONFERENCE_NAME_ATTR_NAME = "conference_name";

    /**
     * Creates a new instance without any attributes or children.
     */
    public ConferenceDescriptionExtension()
    {
        this(null, null, null);
    }

    /**
     * Creates a new instance and sets the "uri" attribute.
     *
     * @param uri the value to use for the "uri" attribute.
     */
    public ConferenceDescriptionExtension(String uri)
    {
        this(uri, null, null);
    }

    /**
     * Creates a new instance and sets the "uri" and "callid" attributes.
     *
     * @param uri the value to use for the "uri" attribute.
     * @param callId the value to use for the "callid" attribute.
     */
    public ConferenceDescriptionExtension(String uri, String callId)
    {
        this(uri, callId, null);
    }

    /**
     * Creates a new instance and sets the "uri", "callid" and "password" attributes.
     *
     * @param uri the value to use for the "uri" attribute.
     * @param callId the value to use for the "callid" attribute.
     * @param password the value to use for the "auth" attribute.
     */
    public ConferenceDescriptionExtension(String uri, String callId, String password)
    {
        super(ELEMENT, NAMESPACE);

        if (uri != null)
            setUri(uri);
        if (callId != null)
            setCallId(callId);
        if (password != null)
            setAuth(password);
    }

    /**
     * Creates a new instance which represents <tt>ca</tt>.
     *
     * @param uri - The URI of the conference.
     * @param callId - The call ID to use to call into the conference.
     * @param password - The password to use to call into the conference.
     * @param available - Whether the conference is available or not.
     * @param displayName - The name of the conference.
     * @param transports - The transport methods supported for calling into the conference.
     */
    public ConferenceDescriptionExtension(String uri, String callId, String password,
            boolean available, String displayName, Set<String> transports)
    {
        this(uri, callId, password);
        setAvailable(available);
        if (displayName != null)
            setName(displayName);

        for (String transport : transports) {
            addChildExtension(new TransportExtension(transport));
        }
    }

    /**
     * Gets the value of the "uri" attribute.
     *
     * @return the value of the "uri" attribute.
     */
    public String getUri()
    {
        return getAttributeAsString(URI_ATTR_NAME);
    }

    /**
     * Gets the value of the "callid" attribute.
     *
     * @return the value of the "callid" attribute.
     */
    public String getCallId()
    {
        return getAttributeAsString(CALLID_ATTR_NAME);
    }

    /**
     * Gets the value of the "password" attribute.
     *
     * @return the value of the "password" attribute.
     */
    public String getPassword()
    {
        return getAttributeAsString(PASSWORD_ATTR_NAME);
    }

    /**
     * Sets the value of the "uri" attribute.
     *
     * @param uri the value to set
     */
    public void setUri(String uri)
    {
        setAttribute(URI_ATTR_NAME, uri);
    }

    /**
     * Sets the value of the "callid" attribute.
     *
     * @param callId the value to set
     */
    public void setCallId(String callId)
    {
        setAttribute(CALLID_ATTR_NAME, callId);
    }

    /**
     * Sets the value of the "password" attribute.
     *
     * @param password the value to set
     */
    public void setAuth(String password)
    {
        setAttribute(PASSWORD_ATTR_NAME, password);
    }

    /**
     * Sets the value of the "available" attribute.
     *
     * @param available the value to set
     */
    public void setAvailable(boolean available)
    {
        setAttribute(AVAILABLE_ATTR_NAME, available);
    }

    /**
     * Sets the value of the "available" attribute.
     *
     * @param name the value to set
     */
    public void setName(String name)
    {
        setAttribute(CONFERENCE_NAME_ATTR_NAME, name);
    }

    /**
     * Gets the value of the "available" attribute.
     */
    public boolean isAvailable()
    {
        return Boolean.parseBoolean(getAttributeAsString(AVAILABLE_ATTR_NAME));
    }

    /**
     * Adds a "transport" child element with the given value.
     *
     * @param transport the transport to add.
     */
    public void addTransport(String transport)
    {
        addChildExtension(new TransportExtension(transport));
    }

    /**
     * Returns the transports from
     * this <tt>ConferenceDescriptionPacketExtension</tt>
     *
     * @return the transports.
     */
    public Set<String> getTransports()
    {
        Set<String> transports = new HashSet<>();
        for (TransportExtension t : getChildExtensionsOfType(TransportExtension.class)) {
            transports.add(t.getNamespace());
        }
        return transports;
    }

    /**
     * Returns the value of the <tt>CONFERENCE_NAME_ATTR_NAME</tt> attribute.
     *
     * @return the name of the conference.
     */
    private String getName()
    {
        return getAttributeAsString(CONFERENCE_NAME_ATTR_NAME);
    }
}
