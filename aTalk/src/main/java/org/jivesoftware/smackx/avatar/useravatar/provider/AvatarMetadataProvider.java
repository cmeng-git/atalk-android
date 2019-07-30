/*
    BEEM is a videoconference application on the Android Platform.

    Copyright (C) 2009 by Frederic-Charles Barthelery,
                          Jean-Manuel Da Silva,
                          Nikita Kozlov,
                          Philippe Lago,
                          Jean Baptiste Vergely,
                          Vincent Veronis.

    This file is part of BEEM.

    BEEM is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    BEEM is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with BEEM.  If not, see <http://www.gnu.org/licenses/>.

    Please send bug reports with examples or suggestions to
    contact@beem-project.com or http://dev.beem-project.com/

    Epitech, hereby disclaims all copyright interest in the program "Beem"
    written by Frederic-Charles Barthelery,
               Jean-Manuel Da Silva,
               Nikita Kozlov,
               Philippe Lago,
               Jean Baptiste Vergely,
               Vincent Veronis.

    Nicolas Sadirac, November 26, 2009
    President of Epitech.

    Flavien Astraud, November 26, 2009
    Head of the EIP Laboratory.

*/
package org.jivesoftware.smackx.avatar.useravatar.provider;

import org.jivesoftware.smack.packet.XmlEnvironment;
import org.jivesoftware.smack.provider.ExtensionElementProvider;
import org.jivesoftware.smack.xml.XmlPullParser;
import org.jivesoftware.smack.xml.XmlPullParserException;
import org.jivesoftware.smackx.avatar.useravatar.packet.AvatarMetadata;
import org.jivesoftware.smackx.avatar.useravatar.packet.AvatarMetadata.Info;

import java.io.IOException;

/**
 * A PacketExtensionProvider to parse the Avatar metadata.
 * XML namespace urn:xmpp:avatar:metadata
 */
public class AvatarMetadataProvider extends ExtensionElementProvider
{
    @Override
    public AvatarMetadata parse(XmlPullParser parser, int initialDepth, XmlEnvironment xmlEnvironment)
            throws IOException, XmlPullParserException
    {
        AvatarMetadata metadata = new AvatarMetadata();
        outerloop:
        while (true) {
            XmlPullParser.Event eventType = parser.next();
            switch (eventType) {
                case START_ELEMENT:
                    String name = parser.getName();
                    if (AvatarMetadata.Info.ELEMENT_INFO.equals(name)) {
                        String id = parser.getAttributeValue(null, AvatarMetadata.Info.ATTR_ID);
                        String type = parser.getAttributeValue(null, AvatarMetadata.Info.ATTR_TYPE);
                        String sBytes = parser.getAttributeValue(null, AvatarMetadata.Info.ATTR_BYTES);
                        String sWidth = parser.getAttributeValue(null, AvatarMetadata.Info.ATTR_WIDTH);
                        String sHeight = parser.getAttributeValue(null, AvatarMetadata.Info.ATTR_HEIGHT);

                        int bytes = 0;
                        Info info;
                        try {
                            if (sBytes != null)
                                bytes = Integer.parseInt(sBytes);
                        } catch (NumberFormatException e) {
                            e.printStackTrace();
                        }

                        if ((id != null) && (type != null) && (bytes != 0))
                            info = new Info(id, type, bytes);
                        else // invalid info
                            continue;

                        String url = parser.getAttributeValue(null, Info.ATTR_URL);
                        info.setUrl(url);

                        try {
                            int width = 0;
                            int height = 0;
                            if (sWidth != null)
                                width = Integer.parseInt(parser.getAttributeValue(null, Info.ATTR_WIDTH));
                            if (sHeight != null)
                                height = Integer.parseInt(parser.getAttributeValue(null, Info.ATTR_HEIGHT));
                            info.setHeight(height);
                            info.setWidth(width);
                        } catch (NumberFormatException e) {
                            e.printStackTrace();
                        }
                        metadata.addInfo(info);
                    }
                    break;
                case END_ELEMENT:
                    if (parser.getDepth() == initialDepth) {
                        break outerloop;
                    }
                    break;
            }
        }
        return metadata;
    }
}
