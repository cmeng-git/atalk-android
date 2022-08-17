/**
 *
 * Copyright 2017-2022 Eng Chong Meng
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
package org.jivesoftware.smackx.jinglenodes.provider;

import static org.jivesoftware.smackx.jinglenodes.element.JingleEventIQ.ATTR_EVENT;
import static org.jivesoftware.smackx.jinglenodes.element.JingleEventIQ.ATTR_ID;
import static org.jivesoftware.smackx.jinglenodes.element.JingleEventIQ.ATTR_TIME;

import org.jivesoftware.smack.packet.XmlEnvironment;
import org.jivesoftware.smack.parsing.SmackParsingException;
import org.jivesoftware.smack.provider.IQProvider;
import org.jivesoftware.smack.xml.XmlPullParser;
import org.jivesoftware.smack.xml.XmlPullParserException;
import org.jivesoftware.smackx.jinglenodes.element.JingleEventIQ;

import java.io.IOException;
import java.util.IllegalFormatException;

public class JingleEventProvider extends IQProvider<JingleEventIQ>
{
    @Override
    public JingleEventIQ parse(XmlPullParser parser, int depth, XmlEnvironment xmlEnvironment)
            throws IOException, XmlPullParserException, SmackParsingException
    {
        boolean done = false;
        JingleEventIQ iq = null;
        XmlPullParser.Event eventType;
        String elementName;
        String namespace;

        while (!done) {
            eventType = parser.getEventType();
            elementName = parser.getName();
            namespace = parser.getNamespace();

            if (eventType == XmlPullParser.Event.START_ELEMENT) {
                if (elementName.equals(JingleEventIQ.ELEMENT)
                        && namespace.equals(JingleEventIQ.NAMESPACE)) {

                    final String id = parser.getAttributeValue(null, ATTR_ID);
                    final String event = parser.getAttributeValue(null, ATTR_EVENT);
                    final String time = parser.getAttributeValue(null, ATTR_TIME);

                    try {
                        iq = new JingleEventIQ();
                        if (id != null)
                            iq.setChannelId(id);
                        if (event != null)
                            iq.setEvent(event);
                        if (time != null)
                            iq.setTime(Integer.parseInt(time));
                    } catch (final IllegalFormatException | NumberFormatException e) {
                        e.printStackTrace();
                    }
                }
            }
            else if (eventType == XmlPullParser.Event.END_ELEMENT) {
                done = true;
            }
            if (!done)
                parser.next();
        }
        return iq;
    }
}
