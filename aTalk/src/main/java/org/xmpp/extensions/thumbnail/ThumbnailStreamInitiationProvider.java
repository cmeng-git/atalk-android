/**
 * Copyright 2003-2006 Jive Software.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package org.xmpp.extensions.thumbnail;

import org.jivesoftware.smack.packet.XmlEnvironment;
import org.jivesoftware.smack.parsing.SmackParsingException;
import org.jivesoftware.smack.provider.IQProvider;
import org.jivesoftware.smack.util.StringUtils;
import org.jivesoftware.smack.xml.XmlPullParser;
import org.jivesoftware.smack.xml.XmlPullParserException;
import org.jivesoftware.smackx.si.packet.StreamInitiation;
import org.jivesoftware.smackx.xdata.packet.DataForm;
import org.jivesoftware.smackx.xdata.provider.DataFormProvider;
import org.jxmpp.util.XmppDateTime;

import java.io.IOException;
import java.text.ParseException;
import java.util.Date;

import timber.log.Timber;

/**
 * The ThumbnailStreamInitiationProvider parses StreamInitiation packets.
 *
 * @author Alexander Wenckus
 * @author Eng Chong Meng
 */
public class ThumbnailStreamInitiationProvider extends IQProvider<StreamInitiation>
{
    /**
     * Parses the given <tt>parser</tt> in order to create a <tt>FileElement</tt> from it.
     *
     * @param parser the parser to parse
     */
    @Override
    public StreamInitiation parse(XmlPullParser parser, int initialDepth, XmlEnvironment xmlEnvironment)
            throws IOException, XmlPullParserException, SmackParsingException
    {
        boolean done = false;

        // si
        String id = parser.getAttributeValue("", "id");
        String mimeType = parser.getAttributeValue("", "mime-type");
        StreamInitiation initiation = new StreamInitiation();

        // file
        String name = null;
        String size = null;
        String hash = null;
        String date = null;
        String desc = null;
        Thumbnail thumbnail = null;
        boolean isRanged = false;

        // feature
        DataForm form = null;
        DataFormProvider dataFormProvider = new DataFormProvider();

        XmlPullParser.Event eventType;
        String elementName;
        String namespace;
        while (!done) {
            eventType = parser.next();
            elementName = parser.getName();
            namespace = parser.getNamespace();
            if (eventType == XmlPullParser.Event.START_ELEMENT) {
                if (elementName.equals("file")) {
                    name = parser.getAttributeValue("", "name");
                    size = parser.getAttributeValue("", "size");
                    hash = parser.getAttributeValue("", "hash");
                    date = parser.getAttributeValue("", "date");
                }
                else if (elementName.equals("desc")) {
                    desc = parser.nextText();
                }
                else if (elementName.equals("range")) {
                    isRanged = true;
                }
                else if (elementName.equals("x") && namespace.equals("jabber:x:data")) {
                    form = dataFormProvider.parse(parser);
                }
                else if (elementName.equals("thumbnail")) {
                    thumbnail = new Thumbnail(parser);
                }
            }
            else if (eventType == XmlPullParser.Event.END_ELEMENT) {
                if (elementName.equals("si")) {
                    done = true;
                }
                // The name-attribute is required per XEP-0096, so ignore the IQ if the name is not
                // set to avoid exceptions. Particularly,
                // the SI response of Empathy contains an invalid, empty file-tag.
                else if (elementName.equals("file") && (name != null)) {
                    long fileSize = 0;
                    size = StringUtils.returnIfNotEmptyTrimmed(size);
                    if (size != null) {
                        try {
                            fileSize = Long.parseLong(size);
                        } catch (NumberFormatException e) {
                            Timber.w(e, "Received an invalid file size, continuing with fileSize set to 0");
                        }
                    }
                    ThumbnailFile file = new ThumbnailFile(name, fileSize);
                    file.setHash(hash);

                    if (date != null) {
                        try {
                            file.setDate(XmppDateTime.parseDate(date));
                        } catch (ParseException e) {
                            Timber.w("Unknown dateformat on incoming file transfer: %s", date);
                        }
                    }
                    else {
                        file.setDate(new Date());
                    }

                    if (thumbnail != null)
                        file.setThumbnail(thumbnail);

                    file.setDesc(desc);
                    file.setRanged(isRanged);
                    initiation.setFile(file);
                }
            }
        }
        initiation.setSessionID(id);
        initiation.setMimeType(mimeType);
        initiation.setFeatureNegotiationForm(form);
        return initiation;
    }
}
