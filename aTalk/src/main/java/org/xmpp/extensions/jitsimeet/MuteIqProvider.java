/*
 * Copyright @ 2018 - present 8x8, Inc.
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
package org.xmpp.extensions.jitsimeet;

import org.jivesoftware.smack.packet.XmlEnvironment;
import org.jivesoftware.smack.parsing.SmackParsingException;
import org.jivesoftware.smack.provider.IQProvider;
import org.jivesoftware.smack.provider.ProviderManager;
import org.jivesoftware.smack.xml.XmlPullParser;
import org.jivesoftware.smack.xml.XmlPullParserException;
import org.jxmpp.jid.Jid;
import org.jxmpp.jid.impl.JidCreate;

import java.io.IOException;

/**
 * The parser of {@link MuteIq}.
 *
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
public class MuteIqProvider extends IQProvider<MuteIq>
{
    /**
     * Registers this IQ provider into given <tt>ProviderManager</tt>.
     */
    public static void registerMuteIqProvider()
    {
        ProviderManager.addIQProvider(MuteIq.ELEMENT, MuteIq.NAMESPACE, new MuteIqProvider());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public MuteIq parse(XmlPullParser parser, int initialDepth, XmlEnvironment xmlEnvironment)
            throws XmlPullParserException, IOException, SmackParsingException
    {
        String namespace = parser.getNamespace();

        // Check the namespace
        if (!MuteIq.NAMESPACE.equals(namespace)) {
            return null;
        }

        String rootElement = parser.getName();
        MuteIq iq;
        if (MuteIq.ELEMENT.equals(rootElement)) {
            iq = new MuteIq();
            String jidStr = parser.getAttributeValue("", MuteIq.JID_ATTR_NAME);
            if (jidStr != null) {
                Jid jid = JidCreate.from(jidStr);
                iq.setJid(jid);
            }

            String actorStr = parser.getAttributeValue("", MuteIq.ACTOR_ATTR_NAME);
            if (actorStr != null) {
                Jid actor = JidCreate.from(actorStr);
                iq.setActor(actor);
            }
        }
        else {
            return null;
        }

        boolean done = false;

        while (!done) {
            switch (parser.next()) {
                case END_ELEMENT: {
                    String name = parser.getName();

                    if (rootElement.equals(name)) {
                        done = true;
                    }
                    break;
                }

                case TEXT_CHARACTERS: {
                    Boolean mute = Boolean.parseBoolean(parser.getText());
                    iq.setMute(mute);
                    break;
                }
            }
        }
        return iq;
    }
}
