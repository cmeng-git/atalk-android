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
import org.jivesoftware.smackx.avatar.useravatar.packet.AvatarData;

import java.io.IOException;

/**
 * A PacketExtensionProvider to parse the Avatar data.
 * XML namespace urn:xmpp:avatar:data
 *
 * <data xmlns='urn:xmpp:avatar:data'>qANQR1DBwU4DX7jmYZnncm...</data>
 */
public class AvatarDataProvider extends ExtensionElementProvider
{
    @Override
    public AvatarData parse(XmlPullParser parser, int initialDepth, XmlEnvironment xmlEnvironment)
            throws IOException, XmlPullParserException
    {
        String data = null;
        if (AvatarData.ELEMENT.equals(parser.getName())) {
            data = parser.nextText();
        }

        return new AvatarData(data);

          // cmeng - not working
//        outerloop: while (true) {
//            int eventType = parser.next();
//            switch (eventType) {
//                case XmlPullParser.TEXT:
//                    String data = parser.nextText();
//                    avatarData = new AvatarData(data);
//                    break;
//                case XmlPullParser.END_TAG:
//                    if (parser.getDepth() == initialDepth) {
//                        break outerloop;
//                    }
//                    break;
//            }
//        }
//        return avatarData;
    }
}
