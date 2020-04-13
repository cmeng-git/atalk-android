/*
 * aTalk, android VoIP and Instant Messaging client
 * Copyright 2014 Eng Chong Meng
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jivesoftware.smackx.httpauthorizationrequest.element;

import org.jivesoftware.smack.packet.ExtensionElement;
import org.jivesoftware.smack.packet.Message;
import org.jivesoftware.smack.util.XmlStringBuilder;

/**
 * ExtensionElement <Conform/> for HTTP Request
 * XEP-0070: Verifying HTTP Requests via XMPP
 */
public class ConfirmExtension implements ExtensionElement
{
    public static final String NAMESPACE = "http://jabber.org/protocol/http-auth";
    public static final String ELEMENT = "confirm";

    public static final String ATTR_ID = "id";
    public static final String ATTR_METHOD = "method";
    public static final String ATTR_URL = "url";

    private final String id;
    private final String method;
    private final String url;

    /**
     * Create a new IdleElement with the current date as date of last user interaction.
     */
    public ConfirmExtension(String id, String method, String url)
    {
        this.id = id;
        this.method = method;
        this.url = url;
    }

    /**
     * Return the Id attr-value of confirm extension.
     *
     * @return id of attr-value confirm extension
     */
    public String getId()
    {
        return id;
    }

    /**
     * Return the method attr-value of confirm extension.
     *
     * @return the method attr-value of confirm extension
     */
    public String getMethod()
    {
        return method;
    }

    /**
     * Return the url attr-value of confirm extension.
     *
     * @return the url attr-value of confirm extension
     */
    public String getUrl()
    {
        return url;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getNamespace()
    {
        return NAMESPACE;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getElementName()
    {
        return ELEMENT;
    }

    /**
     * Extraction of Confirm Extension from message
     * @param message received message
     * @return Confirm extension
     */
    public static ConfirmExtension from(Message message) {
        return (ConfirmExtension) message.getExtension(ELEMENT, NAMESPACE);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public XmlStringBuilder toXML(org.jivesoftware.smack.packet.XmlEnvironment enclosingNamespace)
    {
        return new XmlStringBuilder(this)
                .attribute(ATTR_ID, id)
                .attribute(ATTR_METHOD, method)
                .attribute(ATTR_URL, url)
                .closeEmptyElement();
    }
}
