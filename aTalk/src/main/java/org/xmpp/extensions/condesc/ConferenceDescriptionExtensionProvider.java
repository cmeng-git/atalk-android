package org.xmpp.extensions.condesc;

import org.jivesoftware.smack.packet.XmlEnvironment;
import org.jivesoftware.smack.provider.ExtensionElementProvider;
import org.jivesoftware.smack.xml.XmlPullParser;
import org.jivesoftware.smack.xml.XmlPullParserException;

import java.io.IOException;

/**
 * Parses elements with the {@value ConferenceDescriptionExtensionElement#NAMESPACE} namespace.
 */
public class ConferenceDescriptionExtensionProvider
        extends ExtensionElementProvider<ConferenceDescriptionExtensionElement>
{
    /**
     * Creates a <tt>ConferenceDescriptionPacketExtension</tt> by parsing an XML document.
     *
     * @param parser the parser to use.
     * @return the created <tt>ConferenceDescriptionPacketExtension</tt>.
     * @throws IOException, XmlPullParserException if error
     */
    @Override
    public ConferenceDescriptionExtensionElement parse(XmlPullParser parser, int depth, XmlEnvironment xmlEnvironment)
            throws IOException, XmlPullParserException
    {
        ConferenceDescriptionExtensionElement packetExtension = new ConferenceDescriptionExtensionElement();

        //first, set all attributes
        int attrCount = parser.getAttributeCount();
        for (int i = 0; i < attrCount; i++) {
            packetExtension.setAttribute(parser.getAttributeName(i), parser.getAttributeValue(i));
        }

        //now parse the sub elements
        boolean done = false;
        String elementName;
        TransportExtensionElement transportExt = null;

        while (!done) {
            switch (parser.next()) {
                case START_ELEMENT:
                    elementName = parser.getName();
                    if (TransportExtensionElement.ELEMENT_NAME.equals(elementName)) {
                        String transportNs = parser.getNamespace();
                        if (transportNs != null) {
                            transportExt = new TransportExtensionElement(transportNs);
                        }
                    }
                    break;

                case END_ELEMENT:
                    switch (parser.getName()) {
                        case ConferenceDescriptionExtensionElement.ELEMENT_NAME:
                            done = true;
                            break;

                        case TransportExtensionElement.ELEMENT_NAME:
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