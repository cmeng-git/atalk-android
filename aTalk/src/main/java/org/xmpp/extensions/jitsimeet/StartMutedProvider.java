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

import org.jivesoftware.smack.provider.ExtensionElementProvider;
import org.jivesoftware.smack.provider.ProviderManager;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;

/**
 * The parser of {@link StartMutedExtensionElement}
 *
 * @author Hristo Terezov
 * @author Eng Chong Meng
 */
public class StartMutedProvider extends ExtensionElementProvider<StartMutedExtensionElement>
{
    /**
     * Registers this extension provider into the <tt>ProviderManager</tt>.
     */
    public static void registerStartMutedProvider()
    {
        ProviderManager.addExtensionProvider(
                StartMutedExtensionElement.ELEMENT_NAME,
                StartMutedExtensionElement.NAMESPACE,
                new StartMutedProvider());
    }

    @Override
    public StartMutedExtensionElement parse(XmlPullParser parser, int depth)
            throws XmlPullParserException, IOException
    {
        StartMutedExtensionElement packetExtension = new StartMutedExtensionElement();

        //now parse the sub elements
        boolean done = false;
        String elementName;
        while (!done) {
            switch (parser.getEventType()) {
                case XmlPullParser.START_TAG: {
                    elementName = parser.getName();
                    if (StartMutedExtensionElement.ELEMENT_NAME.equals(elementName)) {
                        boolean audioMute = Boolean.parseBoolean(
                                parser.getAttributeValue("", StartMutedExtensionElement.AUDIO_ATTRIBUTE_NAME));
                        boolean videoMute = Boolean.parseBoolean(
                                parser.getAttributeValue("", StartMutedExtensionElement.VIDEO_ATTRIBUTE_NAME));

                        packetExtension.setAudioMute(audioMute);
                        packetExtension.setVideoMute(videoMute);
                    }
                    parser.next();
                    break;
                }
                case XmlPullParser.END_TAG: {
                    elementName = parser.getName();
                    if (StartMutedExtensionElement.ELEMENT_NAME.equals(elementName)) {
                        done = true;
                    }
                    break;
                }
                default:
                    parser.next();
            }
        }
        return packetExtension;
    }
}
