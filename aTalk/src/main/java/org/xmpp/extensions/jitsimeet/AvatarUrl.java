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
 * A implementation of a {@link ExtensionElement} for the jitsi-meet "avatar-url" element.
 *
 * @author Boris Grozev
 */
public class AvatarUrl implements ExtensionElement
{
    public static final String ELEMENT = "avatar-url";
    public static final String NAMESPACE = "jabber:client";
    public static final QName QNAME = new QName(NAMESPACE, ELEMENT);

    private String avatarUrl;

    /**
     * Initializes an {@link AvatarUrl} instance with a given string value.
     *
     * @param avatarUrl the string value.
     */
    public AvatarUrl(String avatarUrl)
    {
        this.avatarUrl = avatarUrl;
    }

    /**
     * @return the value of the avatar-url element as a string.
     */
    public String getAvatarUrl()
    {
        return avatarUrl;
    }

    /**
     * Sets the value of this avatar-url element.
     *
     * @param avatarUrl the value to set.
     */
    public void setAvatarUrl(String avatarUrl)
    {
        this.avatarUrl = avatarUrl;
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

    /*
     * Returns xml representation of this extension.
     * @return xml representation of this extension.
     */
    @Override
    public XmlStringBuilder toXML(XmlEnvironment xmlEnvironment)
    {
        XmlStringBuilder xml = new XmlStringBuilder();
        xml.element(ELEMENT, getAvatarUrl());
        return xml;
    }

    /**
     * The provider.
     */
    public static class Provider extends ExtensionElementProvider<AvatarUrl>
    {
        @Override
        public AvatarUrl parse(XmlPullParser parser, int initialDepth, XmlEnvironment xmlEnvironment)
                throws IOException, XmlPullParserException
        {
                parser.next();
                final String address = parser.getText();

                // Advance to end of extension.
                while (parser.getEventType() != XmlPullParser.Event.END_ELEMENT) {
                    parser.next();
                }
                return new AvatarUrl(address);
        }
    }
}
