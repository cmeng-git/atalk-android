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

package org.jivesoftware.smackx.avatar.useravatar.packet;

import net.iharder.Base64;

import org.jivesoftware.smack.packet.ExtensionElement;
import org.jivesoftware.smack.packet.XmlEnvironment;
import org.jivesoftware.smack.util.XmlStringBuilder;

import java.util.logging.Logger;

/**
 * Implementation for the XEP-0084: User Avatar Extension Element for <pubsub/> "data node" i.e.
 * Avatar "data" and XML namespace "urn:xmpp:avatar:data". The payload formats are
 * typically transported using the personal eventing profile of XMPP publish-subscribe as
 * specified in XEP-0163.
 *
 * @author Eng Chong Meng
 */
public class AvatarData implements ExtensionElement
{
    /**
     * The logger.
     */
    private static final Logger LOGGER = Logger.getLogger(AvatarData.class.getName());

    /**
     * This userAvatar data extension namespace.
     */
    public static final String NAMESPACE = "urn:xmpp:avatar:data";

    /**
     * This userAvatar data extension element name.
     */
    public static final String ELEMENT = "data";

    private String mData;

    /**
     * Create an AvatarData.
     *
     * @param base64 the data of the avatar as a base64 string
     */
    public AvatarData(final String base64)
    {
        mData = base64;
    }

    /**
     * Create an AvatarData.
     *
     * @param data the data of the avatar
     */
    public AvatarData(final byte[] data)
    {
        mData = Base64.encodeBytes(data);
    }

    /**
     * Get the avatar data as a Base64 string.
     *
     * @return a base64 string.
     */
    public String getBase64()
    {
        return mData;
    }

    /**
     * Get the avatar data.
     *
     * @return the decoded data
     */
    public byte[] getData()
    {
        return Base64.decode(mData);
    }

    @Override
    public String getElementName()
    {
        return ELEMENT;
    }

    @Override
    public String getNamespace()
    {
        return NAMESPACE;
    }

    @Override
    public CharSequence toXML(XmlEnvironment xmlEnvironment)
    {
        XmlStringBuilder xml = new XmlStringBuilder(this);
        xml.rightAngleBracket();
        xml.append(mData);
        xml.closeElement(this);
        return xml;
    }
}
