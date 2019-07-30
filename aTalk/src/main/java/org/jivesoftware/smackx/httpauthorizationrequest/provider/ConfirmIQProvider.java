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
package org.jivesoftware.smackx.httpauthorizationrequest.provider;

import org.jivesoftware.smack.xml.XmlPullParser;
import org.jivesoftware.smack.xml.XmlPullParserException;
import org.jivesoftware.smackx.httpauthorizationrequest.element.ConfirmExtension;
import org.jivesoftware.smackx.httpauthorizationrequest.packet.ConfirmIQ;
import org.jivesoftware.smack.packet.XmlEnvironment;
import org.jivesoftware.smack.parsing.SmackParsingException;
import org.jivesoftware.smack.provider.IQProvider;
import org.jivesoftware.smack.util.PacketParserUtils;

import java.io.IOException;

/**
 * The IQ Provider for ConfirmIQ <ConfirmExtension>.
 * XEP-0070: Verifying HTTP Requests via XMPP
 *
 * @author Eng Chong Meng
 */
public class ConfirmIQProvider extends IQProvider<ConfirmIQ>
{
    @Override
    public ConfirmIQ parse(XmlPullParser parser, int initialDepth, XmlEnvironment xmlEnvironment)
            throws XmlPullParserException, IOException, SmackParsingException
    {
        ConfirmExtension confirmExtension = (ConfirmExtension) PacketParserUtils
                .parseExtensionElement(ConfirmExtension.ELEMENT, ConfirmExtension.NAMESPACE, parser, xmlEnvironment);
        return new ConfirmIQ(confirmExtension);
    }
}
