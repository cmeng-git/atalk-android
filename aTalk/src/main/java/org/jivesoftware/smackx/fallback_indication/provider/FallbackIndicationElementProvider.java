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
package org.jivesoftware.smackx.fallback_indication.provider;

import java.io.IOException;

import org.jivesoftware.smack.packet.XmlEnvironment;
import org.jivesoftware.smack.parsing.SmackParsingException;
import org.jivesoftware.smack.provider.BodyElementProvider;
import org.jivesoftware.smack.provider.ExtensionElementProvider;
import org.jivesoftware.smack.util.PacketParserUtils;
import org.jivesoftware.smack.xml.XmlPullParser;
import org.jivesoftware.smack.xml.XmlPullParserException;

import org.jivesoftware.smackx.avatar.vcardavatar.packet.VCardTempXUpdate;
import org.jivesoftware.smackx.fallback_indication.element.FallbackIndicationElement;

import org.jxmpp.JxmppContext;

public class FallbackIndicationElementProvider extends ExtensionElementProvider<FallbackIndicationElement> {
    @Override
    public FallbackIndicationElement parse(XmlPullParser parser, int initialDepth, XmlEnvironment xmlEnvironment, JxmppContext jxmppContext)
            throws XmlPullParserException, IOException, SmackParsingException {
        String nsFor = parser.getAttributeValue("", FallbackIndicationElement.ATTR_FOR);

        String messageBody = PacketParserUtils.parseElementText(parser);

        outerloop:
        while (true) {
            XmlPullParser.Event eventType = parser.next();
            switch (eventType) {
            case START_ELEMENT:
                String name = parser.getName();
                if ("body".equals(name)) {
                    messageBody = parser.nextText();
                }
                break;
            case END_ELEMENT:
                if (parser.getDepth() == initialDepth) {
                    break outerloop;
                }
                break;
            }
        }

        FallbackIndicationElement fBIndication = new FallbackIndicationElement(nsFor);
        fBIndication.setBody(messageBody);
        return fBIndication;
    }
}
