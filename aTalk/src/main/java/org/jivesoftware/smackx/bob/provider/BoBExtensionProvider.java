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
package org.jivesoftware.smackx.bob.provider;

import org.jivesoftware.smack.packet.XmlEnvironment;
import org.jivesoftware.smack.provider.ExtensionElementProvider;
import org.jivesoftware.smack.util.ParserUtils;
import org.jivesoftware.smack.xml.XmlPullParser;
import org.jivesoftware.smack.xml.XmlPullParserException;
import org.jivesoftware.smackx.bob.BoBData;
import org.jivesoftware.smackx.bob.BoBHash;
import org.jivesoftware.smackx.bob.element.BoBExt;

import java.io.IOException;

/**
 * The <tt>BoBExtensionProvider</tt> is an extension element provider that is meant to be used for
 * thumbnail & captcha requests and responses. Implementing XEP-0231: Bits of Binary
 *
 * @author Eng Chong Meng
 */
public class BoBExtensionProvider extends ExtensionElementProvider<BoBExt>
{
    /**
     * Parses the given <tt>XmlPullParser</tt> into a BoBExt packet and returns it.
     * Note: parse first XmlPullParser.OPEN_TAG is already consumed on first entry.
     * XEP-0231: Bits of Binary
     *
     * <data xmlns='urn:xmpp:tmp:bob'
     * cid='sha1+8f35fef110ffc5df08d579a50083ff9308fb6242@bob.xmpp.org'/>
     * max-age='86400'
     * type='image/png'>
     * iVBORw0KGgoAAAANSUhEUgAAAAoAAAAKCAYAAACNMs+9AAAABGdBTUEAALGP
     * ch9//q1uH4TLzw4d6+ErXMMcXuHWxId3KOETnnXXV6MJpcq2MLaI97CER3N0
     * vr4MkhoXe0rZigAAAABJRU5ErkJggg==
     * </data>
     *
     * @see ExtensionElementProvider#parse(XmlPullParser, int, XmlEnvironment)
     */
    @Override
    public BoBExt parse(XmlPullParser parser, int initialDepth, XmlEnvironment xmlEnvironment)
            throws IOException, XmlPullParserException
    {
        String cid = parser.getAttributeValue("", BoBExt.ATTR_CID);
        BoBHash bobHash = BoBHash.fromCid(cid);

        String dataType = parser.getAttributeValue("", BoBExt.ATTR_TYPE);
        int maxAge = ParserUtils.getIntegerAttribute(parser, BoBExt.ATTR_MAX_AGE, -1);

        String base64EncodedData = parser.nextText();

        BoBData bobData;
        if (dataType != null) {
            bobData = new BoBData(dataType, base64EncodedData, maxAge);
        }
        else {
            bobData = null;
        }

        return new BoBExt(bobHash, bobData);
    }
}
