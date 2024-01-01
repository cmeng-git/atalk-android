/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.jivesoftware.smackx;

import org.atalk.impl.timberlog.TimberLog;
import org.jivesoftware.smack.packet.ExtensionElement;
import org.jivesoftware.smack.packet.XmlEnvironment;
import org.jivesoftware.smack.parsing.SmackParsingException;
import org.jivesoftware.smack.provider.ExtensionElementProvider;
import org.jivesoftware.smack.provider.ProviderManager;
import org.jivesoftware.smack.xml.XmlPullParser;
import org.jivesoftware.smack.xml.XmlPullParserException;
import org.jivesoftware.smackx.jingle_rtp.AbstractXmlElement;

import java.io.IOException;

import timber.log.Timber;

/**
 * A provider that parses incoming packet extensions into instances of the {@link Class} that it has
 * been instantiated for.
 *
 * @param <EE> Class that the packets we will be parsing belong to
 * @author Emil Ivov
 * @author Eng Chong Meng
 */
public class DefaultExtensionElementProvider<EE extends AbstractExtensionElement> extends ExtensionElementProvider<EE>
{
    /**
     * The {@link Class} that the packets we will be parsing here belong to.
     */
    private final Class<EE> stanzaClass;

    /**
     * Creates a new packet provider for the specified packet extensions.
     *
     * @param c the {@link Class} that the packets we will be parsing belong to.
     * l
     */
    public DefaultExtensionElementProvider(Class<EE> c)
    {
        this.stanzaClass = c;
    }

    /**
     * Parse an extension sub-packet and create a <code>EE</code> instance. At the beginning of the
     * method call, the xml parser will be positioned on the opening element of the packet extension
     * and at the end of the method call it will be on the closing element of the packet extension.
     *
     * @param parser an XML parser positioned at the packet's starting element.
     * @return a new packet extension instance.
     * @throws IOException, XmlPullParserException, ParseException if an error occurs parsing the XML.
     */
    @Override
    public EE parse(XmlPullParser parser, int depth, XmlEnvironment xmlEnvironment)
            throws IOException, XmlPullParserException, SmackParsingException
    {
        EE stanzaExtension;
        try {
            stanzaExtension = stanzaClass.newInstance();
        } catch (IllegalAccessException | InstantiationException ignore) {
            Timber.w("Unknown stanza class: %s", parser.getName());
            return null;
        }

        // first, set all attributes
        int attrCount = parser.getAttributeCount();
        for (int i = 0; i < attrCount; i++) {
            stanzaExtension.setAttribute(parser.getAttributeName(i), parser.getAttributeValue(i));
        }

        // now parse the sub elements
        boolean done = false;
        XmlPullParser.Event eventType;
        String elementName;
        String namespace;

        while (!done) {
            eventType = parser.next();
            elementName = parser.getName();
            namespace = parser.getNamespace();

            if (eventType == XmlPullParser.Event.START_ELEMENT) {
                // Timber.d("<%s %s/> class: %s", elementName, namespace, packetExtension.getClass().getSimpleName());

                ExtensionElementProvider<?> provider = ProviderManager.getExtensionProvider(elementName, namespace);
                if (provider == null) {
                    // Extension element provider may not have added properly if null
                    Timber.w("No provider for <%s %s/>", elementName, namespace);
                }
                else {
                    ExtensionElement childExtension = provider.parse(parser);
                    if (namespace != null) {
                        if (childExtension instanceof AbstractExtensionElement) {
                            ((AbstractExtensionElement) childExtension).setNamespace(namespace);
                        }
                        else if (childExtension instanceof AbstractXmlElement) {
                            Timber.d("AbstractXmlElement: <%s, %s>",
                                    childExtension.getElementName(), childExtension.getNamespace());
                        } else {
                            Timber.d("Invalid AbstractExtensionElement: <%s, %s>", elementName, namespace);
                        }
                    }
                    stanzaExtension.addChildExtension(childExtension);
                }
            }
            if (eventType == XmlPullParser.Event.END_ELEMENT) {
                if (parser.getName().equals(stanzaExtension.getElementName())) {
                    done = true;
                }
            }
            if (eventType == XmlPullParser.Event.TEXT_CHARACTERS) {
                String text = parser.getText();
                stanzaExtension.setText(text);
            }
            Timber.log(TimberLog.FINER, "Done parsing: %s", stanzaExtension.getClass().getSimpleName());
        }
        return stanzaExtension;
    }
}
