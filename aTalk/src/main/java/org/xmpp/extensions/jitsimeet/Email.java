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
package org.xmpp.extensions.jitsimeet;

import org.jivesoftware.smack.packet.ExtensionElement;
import org.jivesoftware.smack.packet.XmlEnvironment;
import org.jivesoftware.smack.provider.ExtensionElementProvider;
import org.jivesoftware.smack.util.XmlStringBuilder;
import org.jivesoftware.smack.xml.XmlPullParser;
import org.jivesoftware.smack.xml.XmlPullParserException;

import java.io.IOException;

import javax.xml.namespace.QName;

/**
 * A implementation of a {@link ExtensionElement} for emails.
 *
 * @author Damian Minkov
 * @author Eng Chong Meng
 */
public class Email implements ExtensionElement
{
    public static final String ELEMENT = "email";
    public static final String NAMESPACE = "jabber:client";

    public static final QName QNAME = new QName(NAMESPACE, ELEMENT);

    private String address = null;

    public Email(String address)
    {
        this.address = address;
    }

    /**
     * The value of this email
     *
     * @return the email address
     */
    public String getAddress()
    {
        return address;
    }

    /**
     * Sets the value of this email
     *
     * @param address the address to set
     */
    public void setAddress(String address)
    {
        this.address = address;
    }

    /**
     * Element name.
     *
     * @return element name for this extension.
     */
    public String getElementName()
    {
        return ELEMENT;
    }

    /**
     * Returns the namespace for this extension.
     *
     * @return the namespace for this extension.
     */
    public String getNamespace()
    {
        return NAMESPACE;
    }

    /**
     * Returns xml representation of this extension.
     *
     * @return xml representation of this extension.
     */
    @Override
    public XmlStringBuilder toXML(XmlEnvironment xmlEnvironment)
    {
        XmlStringBuilder xml = new XmlStringBuilder();
        xml.element(ELEMENT, getAddress());
        return xml;
    }

    /**
     * The provider.
     */
    public static class EmailProvider extends ExtensionElementProvider<Email>
    {
        public Email parse(XmlPullParser parser, int initialDepth, XmlEnvironment xmlEnvironment)
                throws IOException, XmlPullParserException
        {
            parser.next();
            final String address = parser.getText();

            // Advance to end of extension.
            while (parser.getEventType() != XmlPullParser.Event.END_ELEMENT) {
                parser.next();
            }
            return new Email(address);
        }
    }
}
