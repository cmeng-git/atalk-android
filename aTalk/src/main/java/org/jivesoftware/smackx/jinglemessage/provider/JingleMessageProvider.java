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
package org.jivesoftware.smackx.jinglemessage.provider;

import org.jivesoftware.smack.packet.XmlEnvironment;
import org.jivesoftware.smack.parsing.SmackParsingException;
import org.jivesoftware.smack.provider.ExtensionElementProvider;
import org.jivesoftware.smack.provider.ProviderManager;
import org.jivesoftware.smack.xml.XmlPullParser;
import org.jivesoftware.smack.xml.XmlPullParserException;
import org.jivesoftware.smackx.jinglemessage.packet.JingleMessage;
import org.xmpp.extensions.jingle.RtpDescriptionExtension;

import java.io.IOException;

/**
 * The JingleMessageProvider parses Jingle Message extension.
 * XEP-0353: Jingle Message Initiation 0.3 (2017-09-11)
 *
 * @author Eng Chong Meng
 */
public class JingleMessageProvider extends ExtensionElementProvider<JingleMessage>
{
    @Override
    public JingleMessage parse(XmlPullParser parser, int initialDepth, XmlEnvironment xmlEnvironment)
            throws XmlPullParserException, IOException, SmackParsingException
    {
        RtpDescriptionExtension rtpDescriptionExtension = null;
        String elementName = null;
        String id = null;

        outerloop:
        while (true) {
            XmlPullParser.Event eventType = parser.next();
            if (eventType == XmlPullParser.Event.START_ELEMENT) {
                elementName = parser.getName();

                if (elementName.equals(JingleMessage.ACTION_PROPOSE)) {
                    id = parser.getAttributeValue(JingleMessage.ATTR_ID);
                }
                else if (elementName.equals(RtpDescriptionExtension.ELEMENT)) {
                    ExtensionElementProvider provider = ProviderManager.getExtensionProvider(
                            RtpDescriptionExtension.ELEMENT, RtpDescriptionExtension.NAMESPACE);
                    rtpDescriptionExtension = (RtpDescriptionExtension) provider.parse(parser);
                }
            }
            else if (eventType == XmlPullParser.Event.END_ELEMENT) {
                if (parser.getDepth() == initialDepth) {
                    break outerloop;
                }
            }
        }

        JingleMessage jingleMessage = new JingleMessage(elementName, id);
        jingleMessage.addDescriptionExtension(rtpDescriptionExtension);

        return jingleMessage;
    }
}
