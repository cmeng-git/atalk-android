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
package org.jivesoftware.smackx.bob.packet;

import org.atalk.util.StringUtils;
import org.jivesoftware.smack.packet.ExtensionElement;
import org.jivesoftware.smack.provider.ProviderManager;
import org.jivesoftware.smack.util.XmlStringBuilder;
import org.jivesoftware.smack.util.stringencoder.Base64;

/**
 * The <tt>BoB</tt> implementing XEP-0231: Bits of Binary is an extension element to
 * include a small bit of binary data in an XMPP stanza. Its use include thumbnail sending prior
 * to large image file sending and inBand registration that has captcha protection etc.
 *
 * @author Eng Chong Meng
 */
public class BoB implements ExtensionElement
{
	/**
	 * The name of the "data" element.
	 */
	public static final String ELEMENT = "data";

	/**
	 * The names XMPP space that the thumbnail elements belong to.
	 */
	public static final String NAMESPACE = "urn:xmpp:bob";

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
	 * A Content-ID that can be mapped to a cid: URL as specified in RFC 2111 [10]. The 'cid'
	 * value SHOULD be of the form algo+hash@bob.xmpp.org, where the "algo" is the hashing
	 * algorithm used (e.g., "sha1" for the SHA-1 algorithm as specified in RFC 3174 [11]) and the
	 * "hash" is the hex output of the algorithm applied to the binary data itself.
	 */
	private String cid;

	/**
	 * The value of the 'type' attribute MUST match the syntax specified in RFC 2045 [13]. That
	 * is, the value MUST include a top-level media type, the "/" character, and a subtype; in
	 * addition, it MAY include one or more optional parameters (e.g., the "audio/ogg" MIME type
	 * in the example shown below includes a "codecs" parameter as specified in RFC 4281 [14]).
	 * The "type/subtype" string SHOULD be registered in the IANA MIME Media Types Registry [15],
	 * but MAY be an unregistered or yet-to-be-registered value.
	 */
	private String mimeType;

	/**
	 * A suggestion regarding how long (in seconds) to cache the data.
	 */
	private long maxAge;

	/**
	 * The final extension data sent MUST be encoded as Base64 in accordance with Section 4 of
	 * RFC 4648 [9] (note: the Base64 output MUST NOT include whitespace and MUST set the number
	 * of pad bits to zero).
	 */
	private String mData;

	/**
	 * An empty constructor for
	 * @see ProviderManager#addExtensionProvider(String, String, Object)
	 */
	public BoB()
	{
		this("", 0, null, "");
	}

	/**
	 * Creates a <tt>BoB</tt> packet, by specifying the content-ID, the maxAge, the type
	 * of data and the data of the bob.
	 *
	 * @param cid
	 * 		the content-ID used to identify this packet in the destination
	 * @param maxAge
	 * 		the content-ID used to identify this packet in the destination
	 * @param mimeType
	 * 		the type of the image data passed
	 * @param base64
	 * 		the Base64 of the raw image data
	 */
	public BoB(String cid, long maxAge, String mimeType, String base64)
	{
		this.cid = cid;
		this.maxAge = maxAge;
		this.mimeType = mimeType;
		this.mData = base64;
	}

	/**
	 * Returns the content-ID of this bob data content-cid.
	 *
	 * @return the content-ID of this bob packet
	 */
	public String getCid()
	{
		return cid;
	}

	/**
	 * Returns the mimeType of this bob data.
	 *
	 * @return the mimeType of this bob data
	 */
	public String getType()
	{
		return mimeType;
	}

	/**
	 * Returns the maxAge of this bob data.
	 *
	 * @return the maxAge of this bob data
	 */
	public long getMaxAge()
	{
		return maxAge;
	}

	/**
	 * Returns the data of the bob.
	 *
	 * @return the data of the bob
	 */
	public String getData()
	{
		return mData;
	}

	/**
	 * Get the content.
	 *
	 * @return the content
	 */
	public byte[] getContent() {
		return Base64.decode(mData);
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
	 * Returns the XML representation of the <tt>BoB</tt>.
	 * <p>
	 * <data xmlns='urn:xmpp:bob'
	 * 	cid='sha1+8f35fef110ffc5df08d579a50083ff9308fb6242@bob.xmpp.org'
	 * 	max-age='86400'
	 * 	type='image/png'>
	 * 		iVBORw0KGgoAAAANSUhEUgAAAAoAAAAKCAYAAACNMs+9AAAABGdBTUEAALGP
	 * 		AAAddEVYdENvbW1lbnQAQ3JlYXRlZCB3aXRoIFRoZSBHSU1Q72QlbgAAAF1J
	 * 		vr4MkhoXe0rZigAAAABJRU5ErkJggg==
	 * </data>
	 */
	@Override
	public XmlStringBuilder toXML(String enclosingNamespace)
	{
		XmlStringBuilder xml = new XmlStringBuilder(this);
		xml.attribute(ATTR_CID, cid);
		xml.optLongAttribute(ATTR_MAX_AGE, maxAge);
		xml.optAttribute(ATTR_TYPE, mimeType);
		xml.append('>');
		if (!StringUtils.isNullOrEmpty(mData)) {
			xml.append(mData);
		}
		xml.closeElement(this);
		return xml;
	}
}
