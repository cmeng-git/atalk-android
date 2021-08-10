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
package org.jivesoftware.smackx.captcha.provider;

import org.jivesoftware.smack.packet.XmlEnvironment;
import org.jivesoftware.smack.parsing.SmackParsingException;
import org.jivesoftware.smack.provider.ExtensionElementProvider;
import org.jivesoftware.smack.xml.XmlPullParser;
import org.jivesoftware.smack.xml.XmlPullParserException;
import org.jivesoftware.smackx.captcha.packet.CaptchaExtension;
import org.jivesoftware.smackx.xdata.packet.DataForm;
import org.jivesoftware.smackx.xdata.provider.DataFormProvider;

import java.io.IOException;

/**
 * The <tt>CaptchaProvider</tt> is an extension element provider that is meant to be used for
 * thumbnail & captcha requests and responses. Implementing XEP-0158: CAPTCHA Forms
 *
 * @author Eng Chong Meng
 */
public class CaptchaProvider extends ExtensionElementProvider<CaptchaExtension>
{
    /**
     * Parses the given <tt>XmlPullParser</tt> into a DataForm packet and returns it.
     * Note: parse first XmlPullParser.OPEN_TAG is already consumed on first entry.
     * XEP-0158: CAPTCHA Forms
     *
     * @see ExtensionElementProvider#parse(XmlPullParser, int)
     */
    @Override
    public CaptchaExtension parse(XmlPullParser parser, int initialDepth, XmlEnvironment xmlEnvironment)
            throws IOException, XmlPullParserException, SmackParsingException
    {
        // feature
        DataForm form = null;
        DataFormProvider dataFormProvider = new DataFormProvider();

        String data = "";
        String elementName;
        String namespace;

        boolean done = false;
        while (!done) {
            XmlPullParser.Event eventType = parser.next();
            elementName = parser.getName();
            namespace = parser.getNamespace();
            if (eventType == XmlPullParser.Event.START_ELEMENT) {
                if (elementName.equals(DataForm.ELEMENT) && namespace.equals(DataForm.NAMESPACE)) {
                    form = dataFormProvider.parse(parser);
                }
                else if (eventType == XmlPullParser.Event.TEXT_CHARACTERS) {
                    data = parser.getText();
                    // data = parser.nextText();
                }
            }
            else if (eventType == XmlPullParser.Event.END_ELEMENT) {
                if (CaptchaExtension.ELEMENT.equals(elementName)) {
                    done = true;
                }
            }
        }
        return new CaptchaExtension(form);
    }
}
