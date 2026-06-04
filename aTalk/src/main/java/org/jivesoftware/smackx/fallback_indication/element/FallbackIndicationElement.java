/*
 *
 * Copyright 2020 Paul Schaub
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
package org.jivesoftware.smackx.fallback_indication.element;

import javax.xml.namespace.QName;

import org.jivesoftware.smack.packet.ExtensionElement;
import org.jivesoftware.smack.packet.Message;
import org.jivesoftware.smack.packet.XmlEnvironment;
import org.jivesoftware.smack.util.XmlStringBuilder;

import org.jivesoftware.smackx.message_retraction.element.RetractElement;

public class FallbackIndicationElement implements ExtensionElement {

    public static final String NAMESPACE = "urn:xmpp:fallback:0";
    public static final String ELEMENT = "fallback";

    public static final QName QNAME = new QName(RetractElement.NAMESPACE, ELEMENT);
    public static final String ATTR_FOR = "for";

    private final String mNsFor;
    public String messageBody;

    // public static final FallbackIndicationElement INSTANCE = new FallbackIndicationElement();

    public FallbackIndicationElement(String nsFor) {
        mNsFor = nsFor;
        messageBody = "";
    }

    public String getNsFor() {
        return mNsFor;
    }

    public void setBody(String body) {
        messageBody = body;
    }

    public String getBody() {
        return messageBody;
    }

    public static boolean hasFallbackIndication(Message message) {
        return message.hasExtension(ELEMENT, NAMESPACE);
    }

    public static FallbackIndicationElement fromMessage(Message message) {
        return message.getExtension(FallbackIndicationElement.class);
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
     * XEP-0424: Message Retraction document example shows body is element of Message Retraction
     * XEP-0428: Fallback Indication document example shows body is a childElement of the 'fallback' xmlElement.
     *
     * @param xmlEnvironment XML environment
     *
     * @return instance of FallbackIndicationElement
     */
    @Override
    public XmlStringBuilder toXML(XmlEnvironment xmlEnvironment) {
        return new XmlStringBuilder(this)
                .attribute(ATTR_FOR, mNsFor)
                .closeEmptyElement()
                .append("<body>")
                .append(messageBody)
                .append("</body>");
    }
}
