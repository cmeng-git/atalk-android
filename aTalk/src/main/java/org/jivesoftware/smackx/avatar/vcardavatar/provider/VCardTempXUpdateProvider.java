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
    along with BEEM.  If not, see <https://www.gnu.org/licenses/>.

    Please send bug reports with examples or suggestions to
    contact@beem-project.com or https://dev.beem-project.com/

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
package org.jivesoftware.smackx.avatar.vcardavatar.provider;

import org.jivesoftware.smack.packet.XmlEnvironment;
import org.jivesoftware.smack.provider.ExtensionElementProvider;
import org.jivesoftware.smack.xml.XmlPullParser;
import org.jivesoftware.smack.xml.XmlPullParserException;
import org.jivesoftware.smackx.avatar.vcardavatar.packet.VCardTempXUpdate;

import java.io.IOException;

/**
 * An ExtensionElementProvider to parse the VcardTempXUpdate photo data.
 * XML namespace "vcard-temp:x:update". Possible format:
 * 1. <x xmlns='vcard-temp:x:update'><photo>186f39da130310dbc59002608c56d1bd26abd72d</photo></x>
 * 2. <x xmlns='vcard-temp:x:update'/>
 * Smack 4.4.0-alpha3 (20200404) cannot accept null return. must return even with data == null.
 */
public class VCardTempXUpdateProvider extends ExtensionElementProvider
{
    @Override
    public VCardTempXUpdate parse(XmlPullParser parser, int initialDepth, XmlEnvironment xmlEnvironment)
            throws IOException, XmlPullParserException
    {
        String data = null;

        outerloop:
        while (true) {
            XmlPullParser.Event eventType = parser.next();
            switch (eventType) {
                case START_ELEMENT:
                    String name = parser.getName();
                    if (VCardTempXUpdate.ELEMENT_PHOTO.equals(name)) {
                        data = parser.nextText();
                    }
                    break;
                case END_ELEMENT:
                    if (parser.getDepth() == initialDepth) {
                        break outerloop;
                    }
                    break;
            }
        }
        return new VCardTempXUpdate(data);
    }
}
