package org.xmpp.extensions.condesc;

import org.jivesoftware.smack.packet.XmlEnvironment;
import org.jivesoftware.smack.provider.ExtensionElementProvider;
import org.jivesoftware.smack.xml.XmlPullParser;
import org.jivesoftware.smack.xml.XmlPullParserException;

import java.io.IOException;

/**
 * Parses elements with the {@value ConferenceDescriptionExtension#NAMESPACE} namespace.
 */
public class ConferenceDescriptionExtensionProvider
        extends ExtensionElementProvider<ConferenceDescriptionExtension>
{
    /**
     * Creates a <tt>ConferenceDescriptionPacketExtension</tt> by parsing an XML document.
     *
     * @param parser the parser to use.
     * @return the created <tt>ConferenceDescriptionPacketExtension</tt>.
     * @throws IOException, XmlPullParserException if error
     */
    @Override
    public ConferenceDescriptionExtension parse(XmlPullParser parser, int depth, XmlEnvironment xmlEnvironment)
            throws IOException, XmlPullParserException
    {
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