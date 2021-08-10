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
package org.atalk.android.gui.util;

import org.jivesoftware.smack.packet.Message;
import org.jivesoftware.smackx.xhtmlim.XHTMLManager;
import org.jivesoftware.smackx.xhtmlim.packet.XHTMLExtension;

import java.util.List;

/**
 * Utility class that implements <tt>XHtml</tt> utility
 *
 * @author Eng Chong Meng
 */
public class XhtmlUtil
{
    /**
     * return the extracted content of the XHTMLExtension for the given message
     *
     * @param xhtmlExt XHTMLExtension extension
     * @return XHTML String of the given message
     */
    public static String getXhtmlExtension(Message message)
    {
        String xhtmlString = null;
        if (XHTMLManager.isXHTMLMessage(message)) {
            XHTMLExtension xhtmlExt = message.getExtension(XHTMLExtension.class);

            // parse all bodies
            List<CharSequence> bodies = xhtmlExt.getBodies();
            StringBuilder messageBuff = new StringBuilder();
            for (CharSequence body : bodies) {
                messageBuff.append(body);
            }

            // Convert to proper xml format before parse
            if (messageBuff.length() > 0) {
                xhtmlString = messageBuff.toString()
                        // removes <body> start tag
                        .replaceAll("<[bB][oO][dD][yY].*?>", "")
                        // removes </body> end tag
                        .replaceAll("</[bB][oO][dD][yY].*?>", "")
                        .replaceAll("&lt;", "<")
                        .replaceAll("&gt;", ">")
                        .replaceAll("&apos;", "\"");
            }
        }
        return xhtmlString;
    }
}
