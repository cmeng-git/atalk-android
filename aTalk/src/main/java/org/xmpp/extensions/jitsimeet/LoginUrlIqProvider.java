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

import org.apache.commons.lang3.StringUtils;
import org.jivesoftware.smack.packet.XmlEnvironment;
import org.jivesoftware.smack.parsing.SmackParsingException;
import org.jivesoftware.smack.provider.IQProvider;
import org.jivesoftware.smack.provider.ProviderManager;
import org.jivesoftware.smack.xml.XmlPullParser;
import org.jivesoftware.smack.xml.XmlPullParserException;
import org.jxmpp.jid.EntityBareJid;
import org.jxmpp.jid.impl.JidCreate;

import java.io.IOException;

/**
 * Provider handles parsing of {@link ConferenceIq} and {@link LoginUrlIq}
 * stanzas and converting objects back to their XML representation.
 *
 * @author Pawel Domas
 */
public class LoginUrlIqProvider extends IQProvider<LoginUrlIq>
{
    /**
     * Creates new instance of <tt>ConferenceIqProvider</tt>.
     */
    public LoginUrlIqProvider()
    {
        // <auth-url>
        ProviderManager.addIQProvider(
                LoginUrlIq.ELEMENT, LoginUrlIq.NAMESPACE, this);
    }

    @Override
    public LoginUrlIq parse(XmlPullParser parser, int initialDepth, XmlEnvironment xmlEnvironment)
            throws XmlPullParserException, IOException, SmackParsingException
    {
        String namespace = parser.getNamespace();

        // Check the namespace
        if (!ConferenceIq.NAMESPACE.equals(namespace)) {
            return null;
        }

        String rootElement = parser.getName();

        LoginUrlIq authUrlIQ;
        if (LoginUrlIq.ELEMENT.equals(rootElement)) {
            authUrlIQ = new LoginUrlIq();

            String url = parser.getAttributeValue("", LoginUrlIq.URL_ATTRIBUTE_NAME);
            if (StringUtils.isNotEmpty(url)) {
                authUrlIQ.setUrl(url);
            }
            String room = parser.getAttributeValue("", LoginUrlIq.ROOM_NAME_ATTR_NAME);
            if (StringUtils.isNotEmpty(room)) {
                EntityBareJid roomJid = JidCreate.entityBareFrom(room);
                authUrlIQ.setRoom(roomJid);
            }
            String popup = parser.getAttributeValue("", LoginUrlIq.POPUP_ATTR_NAME);
            if (StringUtils.isNotEmpty(popup)) {
                Boolean popupBool = Boolean.parseBoolean(popup);
                authUrlIQ.setPopup(popupBool);
            }
            String machineUID = parser.getAttributeValue("", LoginUrlIq.MACHINE_UID_ATTR_NAME);
            if (StringUtils.isNotEmpty(machineUID)) {
                authUrlIQ.setMachineUID(machineUID);
            }
        }
        else {
            return null;
        }

        return authUrlIQ;
    }
}
