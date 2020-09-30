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
package org.jivesoftware.smackx.bob.element;

import org.jivesoftware.smack.packet.ExtensionElement;
import org.jivesoftware.smack.packet.XmlEnvironment;
import org.jivesoftware.smack.util.XmlStringBuilder;
import org.jivesoftware.smackx.bob.*;

import javax.xml.namespace.QName;

/**
 * The <tt>BoBExt</tt> implementing XEP-0231: Bits of Binary is an extension element to
 * include a small bit of binary data in an XMPP stanza. Its use include thumbnail sending prior
 * to large image file sending and inBand registration that has captcha protection etc.
 *
 * @author Eng Chong Meng
 */
public class BoBExt implements ExtensionElement
{
    /**
     * The name of the "data" element.
     */
    public static final String ELEMENT = "data";

    /**
     * The names XMPP space that the thumbnail elements belong to.
     */
    public static final String NAMESPACE = BoBManager.NAMESPACE;

    public static final QName QNAME = new QName(NAMESPACE, ELEMENT);

    /**
     * The name of the bob attribute "cid".
     */
    public static final String ATTR_CID = "cid";

    /**
     * The bob attribute for "max-age" - the meaning matches the
     * Max-Age attribute from RFC 2965 [12]
     */
    public static final String ATTR_MAX_AGE = "max-age";

    /**
     * The bob attribute for image type "mime-type".
     */
    public static final String ATTR_TYPE = "type";

    /**
     * A Content-ID bobHash that can be mapped to a cid: URL as specified in RFC 2111 [10]. The 'bobHash'
     * value SHOULD be of the form algo+hash@bob.xmpp.org, where the "algo" is the hashing
     * algorithm used (e.g., "sha1" for the SHA-1 algorithm as specified in RFC 3174 [11]) and the
     * "hash" is the hex output of the algorithm applied to the binary data itself.
     */
    private final BoBHash bobHash;

    private final BoBData bobData;

    // BoBData(String mimeType, String mData, int maxAge)
    /*
     // private String mimeType;
     * The value of the 'type' attribute MUST match the syntax specified in RFC 2045 [13]. That
     * is, the value MUST include a top-level media type, the "/" character, and a subtype; in
     * addition, it MAY include one or more optional parameters (e.g., the "audio/ogg" MIME type
     * in the example shown below includes a "codecs" parameter as specified in RFC 4281 [14]).
     * The "type/subtype" string SHOULD be registered in the IANA MIME Media Types Registry [15],
     * but MAY be an unregistered or yet-to-be-registered value.
     *
     // private String mData;
     * The final extension data sent MUST be encoded as Base64 in accordance with Section 4 of
     * RFC 4648 [9] (note: the Base64 output MUST NOT include whitespace and MUST set the number
     * of pad bits to zero).
     *
     // private long maxAge;
     * A suggestion regarding how long (in seconds) to cache the data.
     */

    /**
     * Creates a <tt>BoBExt</tt> packet, by specifying the content-ID, the maxAge, the type
     * of data and the data of the bob.
     *
     * @param bobHash the content-ID used to identify this packet in the destination
     * @param bobData the Base64 of the raw image data
     */
    public BoBExt(BoBHash bobHash, BoBData bobData)
    {
        this.bobHash = bobHash;
        this.bobData = bobData;
    }

    /**
     * Get the BoB hash.
     *
     * @return the BoB hash
     */
    public BoBHash getBoBHash()
    {
        return bobHash;
    }

    /**
     * Get the BoB data.
     *
     * @return the BoB data
     */
    public BoBData getBoBData()
    {
        return bobData;
    }

    /**
     * Returns the root element name.
     *
     * @return the element name.
     */
    public String getElementName()
    {
        return ELEMENT;
    }

    /**
     * Returns the root element XML namespace.
     *
     * @return the namespace.
     */
    public String getNamespace()
    {
        return NAMESPACE;
    }

    /**
     * Returns the XML representation of the <tt>BoBExt</tt>.
     * <p>
     * <data xmlns='urn:xmpp:bob'
     * cid='sha1+8f35fef110ffc5df08d579a50083ff9308fb6242@bob.xmpp.org'
     * max-age='86400'
     * type='image/png'>
     * iVBORw0KGgoAAAANSUhEUgAAAAoAAAAKCAYAAACNMs+9AAAABGdBTUEAALGP
     * AAAddEVYdENvbW1lbnQAQ3JlYXRlZCB3aXRoIFRoZSBHSU1Q72QlbgAAAF1J
     * vr4MkhoXe0rZigAAAABJRU5ErkJggg==
     * </data>
     */
    @Override
    public CharSequence toXML(XmlEnvironment xmlEnvironment)
    {
        XmlStringBuilder xml = new XmlStringBuilder(this);
        xml.attribute(ATTR_CID, bobHash.getCid());

        if (bobData != null) {
            xml.optIntAttribute(ATTR_MAX_AGE, bobData.getMaxAge());
            xml.attribute(ATTR_TYPE, bobData.getType());
            xml.rightAngleBracket();
            xml.escape(bobData.getContentBase64Encoded());
        }
        xml.closeElement(this);
        return xml;
    }
}
