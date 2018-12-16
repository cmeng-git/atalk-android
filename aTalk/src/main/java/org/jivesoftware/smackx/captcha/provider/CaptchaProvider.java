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

import org.jivesoftware.smack.provider.ExtensionElementProvider;
import org.jivesoftware.smackx.captcha.packet.Captcha;
import org.jivesoftware.smackx.xdata.packet.DataForm;
import org.jivesoftware.smackx.xdata.provider.DataFormProvider;
import org.xmlpull.v1.XmlPullParser;

/**
 * The <tt>CaptchaProvider</tt> is an extension element provider that is meant to be used for
 * thumbnail & captcha requests and responses. Implementing XEP-0158: CAPTCHA Forms
 *
 * @author Eng Chong Meng
 */
public class CaptchaProvider extends ExtensionElementProvider<Captcha>
{
    /**
     * Parses the given <tt>XmlPullParser</tt> into a DataForm packet and returns it.
     * Note: parse first XmlPullParser.OPEN_TAG is already consumed on first entry.
     * XEP-0158: CAPTCHA Forms
     *
     * @see ExtensionElementProvider#parse(XmlPullParser, int)
     */
    @Override
    public Captcha parse(XmlPullParser parser, int initialDepth)
            throws Exception
    {
        // feature
        DataForm form = null;
        DataFormProvider dataFormProvider = new DataFormProvider();

        String data = "";
        String elementName;
        String namespace;

        boolean done = false;
        while (!done) {
            int eventType = parser.next();
            elementName = parser.getName();
            namespace = parser.getNamespace();
            if (eventType == XmlPullParser.START_TAG) {
                if (elementName.equals("x") && namespace.equals("jabber:x:data")) {
                    form = dataFormProvider.parse(parser);
                }
                else if (eventType == XmlPullParser.TEXT) {
                    data = parser.getText();
                    // data = parser.nextText();
                }
            }
            else if (eventType == XmlPullParser.END_TAG) {
                if (Captcha.ELEMENT.equals(elementName)) {
                    done = true;
                }
            }
        }
        return new Captcha(form);
    }
}
