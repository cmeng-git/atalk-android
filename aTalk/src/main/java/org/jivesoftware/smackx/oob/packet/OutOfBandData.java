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

package org.jivesoftware.smackx.oob.packet;

import java.util.logging.Logger;

import javax.xml.namespace.QName;

import org.jivesoftware.smack.packet.XmlElement;
import org.jivesoftware.smack.packet.XmlEnvironment;
import org.jivesoftware.smack.util.XmlStringBuilder;

/**
 * Implements the Out of Band Data corresponding to element name "x" and namespace
 * "jabber:iq:oob" (cf. XEP-0066). This add extension in the HttpFileDownload send message.
 *
 * @author Eng Chong Meng
 */
public class OutOfBandData implements XmlElement {
    /**
     * The <code>Logger</code> used by the <code>OutOfBandData</code> class and its
     * instances for logging output.
     */
    private static final Logger LOGGER = Logger.getLogger(OutOfBandData.class.getName());

    /**
     * This oob extension namespace.
     */
    public static final String NAMESPACE = "jabber:iq:oob";

    /**
     * This oob extension element name.
     */
    public static final String ELEMENT = "x";

    public static final QName QNAME = new QName(NAMESPACE, ELEMENT);

    /**
     * This URL extension element name.
     */
    public static final String ELEMENT_URL = "url";

    /**
     * The URL of the transfer file for download
     */
    private final String mUrl;

    /**
     * Create an OutOfBandData.
     *
     * @param url the url for the transfer file
     */
    public OutOfBandData(String url) {
        mUrl = url;
    }

    /**
     * Returns the root element name.
     *
     * @return the element name.
     */
    public String getElementName() {
        return ELEMENT;
    }

    /**
     * Returns the root element XML namespace.
     *
     * @return the namespace.
     */
    public String getNamespace() {
        return NAMESPACE;
    }

    /**
     * Returns the XML representation of the XmlElement.
     *
     * @see #mUrl defination
     */
    @Override
    public CharSequence toXML(XmlEnvironment xmlEnvironment) {
        XmlStringBuilder xml = new XmlStringBuilder(this);
        xml.rightAngleBracket();
        xml.optElement(ELEMENT_URL, mUrl);
        xml.closeElement(ELEMENT);
        return xml;
    }
}
