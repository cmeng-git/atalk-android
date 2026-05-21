/*
 *
 * Copyright © 2014-2020 Eng Chong Meng
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
package org.jivesoftware.smackx.json.packet;

import javax.xml.namespace.QName;

import org.jivesoftware.smack.packet.XmlElement;
import org.jivesoftware.smack.packet.XmlEnvironment;
import org.jivesoftware.smack.util.XmlStringBuilder;

/**
 * XEP-0432: Simple JSON Messaging.
 *
 * @see <a href="https://xmpp.org/extensions/xep-0432.html">XEP-0432: Simple JSON Messaging</a>
 */
public class JsonMessageExtension implements XmlElement {
    public static final String ELEMENT = "payload";
    public static final String NAMESPACE = "urn:xmpp:json-msg:0";

    public static final QName QNAME = new QName(NAMESPACE, ELEMENT);

    private final JsonPacketExtension mJsonPacket;

    /**
     * Creates a {@link JsonMessageExtension} instance.
     */
    public JsonMessageExtension() {
        this(null);
    }

    /**
     * Initializes a {@link JsonMessageExtension} instance with a given
     * string value for its json content.
     *
     * @param json the value of the json content.
     */
    public JsonMessageExtension(String json) {
        mJsonPacket = new JsonPacketExtension(json);
    }

    @Override
    public String getNamespace() {
        return NAMESPACE;
    }

    @Override
    public String getElementName() {
        return ELEMENT;
    }

    /**
     * Returns the content of the json-message packet.
     *
     * @return the json string.
     */
    public String getJson() {
        return mJsonPacket.getJson();
    }

    public final XmlStringBuilder toXML(XmlEnvironment enclosingNamespace) {
        XmlStringBuilder xml = new XmlStringBuilder(this);
        xml.rightAngleBracket();
        xml.append(mJsonPacket);
        xml.closeElement(this);
        return xml;
    }
}
