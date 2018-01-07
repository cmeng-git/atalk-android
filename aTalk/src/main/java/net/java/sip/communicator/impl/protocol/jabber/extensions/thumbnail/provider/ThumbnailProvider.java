/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.impl.protocol.jabber.extensions.thumbnail.provider;

import net.java.sip.communicator.impl.protocol.jabber.extensions.thumbnail.packet.ThumbnailIQ;
import net.java.sip.communicator.util.Base64;

import org.jivesoftware.smack.SmackException;
import org.jivesoftware.smack.provider.IQProvider;
import org.xmlpull.v1.*;

import java.io.IOException;

/**
 * The <tt>BoB</tt> is an IQ packet that is meant to be used for thumbnail requests and
 * responses. Implementing XEP-0231: Bits of Binary
 * 
 * @author Yana Stamcheva
 */
public class ThumbnailProvider extends IQProvider<ThumbnailIQ>
{
	/**
	 * Parses the given <tt>XmlPullParser</tt> into a BoB packet and returns it.
	 * 
	 * @see IQProvider#parse(XmlPullParser)
	 */
	public ThumbnailIQ parse(XmlPullParser parser, int initialDepth)
		throws XmlPullParserException, IOException, SmackException
	{
		String cid = null;
		String mimeType = null;
		byte[] data = null;

		String elementName = parser.getName();
		String namespace = parser.getNamespace();

		if (elementName.equals(ThumbnailIQ.ELEMENT) && namespace.equals(ThumbnailIQ.NAMESPACE)) {
			cid = parser.getAttributeValue("", "cid");
			mimeType = parser.getAttributeValue("", "type");
		}

		int eventType = parser.next();
		if (eventType == XmlPullParser.TEXT) {
			data = Base64.decode(parser.getText());
		}
		ThumbnailIQ thumnailIQ = new ThumbnailIQ(cid, mimeType, data);
		return thumnailIQ;
	}
}
