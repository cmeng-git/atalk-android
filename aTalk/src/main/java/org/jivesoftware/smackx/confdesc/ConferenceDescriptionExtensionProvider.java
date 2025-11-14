package org.jivesoftware.smackx.confdesc;

import org.jivesoftware.smack.packet.XmlEnvironment;
import org.jivesoftware.smack.parsing.SmackParsingException;
import org.jivesoftware.smack.provider.ExtensionElementProvider;
import org.jivesoftware.smack.xml.XmlPullParser;
import org.jivesoftware.smack.xml.XmlPullParserException;
import org.jxmpp.JxmppContext;

import java.io.IOException;
import java.text.ParseException;

/**
 * Parses elements with the {@value ConferenceDescriptionExtension#NAMESPACE} namespace.
 */
public class ConferenceDescriptionExtensionProvider extends ExtensionElementProvider<ConferenceDescriptionExtension>
{
    /**
     * Creates a <code>ConferenceDescriptionExtension</code> by parsing an XML document.
     *
     * @param parser the parser to use.
     * @return the created <code>ConferenceDescriptionExtension</code>.
     * @throws IOException, XmlPullParserException if error
     */

    @Override
    public ConferenceDescriptionExtension parse(XmlPullParser parser, int initialDepth, XmlEnvironment xmlEnvironment, JxmppContext jxmppContext)
            throws XmlPullParserException, IOException, SmackParsingException, ParseException {
        ConferenceDescriptionExtension packetExtension = new ConferenceDescriptionExtension();

        //first, set all attributes
        int attrCount = parser.getAttributeCount();
        for (int i = 0; i < attrCount; i++) {
            packetExtension.setAttribute(parser.getAttributeName(i), parser.getAttributeValue(i));
        }

        //now parse the sub elements
        boolean done = false;
        String elementName;
        TransportExtension transportExt = null;

        while (!done) {
            switch (parser.next()) {
                case START_ELEMENT:
                    elementName = parser.getName();
                    if (TransportExtension.ELEMENT.equals(elementName)) {
                        String transportNs = parser.getNamespace();
                        if (transportNs != null) {
                            transportExt = new TransportExtension(transportNs);
                        }
                    }
                    break;

                case END_ELEMENT:
                    switch (parser.getName()) {
                        case ConferenceDescriptionExtension.ELEMENT:
                            done = true;
                            break;

                        case TransportExtension.ELEMENT:
                            if (transportExt != null) {
                                packetExtension.addChildExtension(transportExt);
                            }
                            break;
                    }
            }
        }
        return packetExtension;
    }
}