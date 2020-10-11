/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.xmpp.extensions.jingle;

import org.jivesoftware.smack.parsing.SmackParsingException;
import org.jivesoftware.smack.xml.XmlPullParser;
import org.jivesoftware.smack.xml.XmlPullParserException;
import org.xmpp.extensions.AbstractExtensionElement;
import org.xmpp.extensions.DefaultExtensionElementProvider;
import org.xmpp.extensions.jingle.element.JingleContent;

import java.io.IOException;
import java.util.List;

import javax.xml.namespace.QName;

import static org.jivesoftware.smack.xml.XmlPullParser.Event.END_ELEMENT;

/**
 * Jingle group packet extension(XEP-0338).
 *
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
public class GroupExtension extends AbstractExtensionElement
{
    /**
     * The name of the "group" element.
     */
    public static final String ELEMENT = "group";

    /**
     * The namespace for the "group" element.
     */
    public static final String NAMESPACE = "urn:xmpp:jingle:apps:grouping:0";

    public static final QName QNAME = new QName(NAMESPACE, ELEMENT);

    /**
     * The name of the payload <tt>id</tt> SDP argument.
     */
    public static final String SEMANTICS_ATTR_NAME = "semantics";

    /**
     * Name of the "bundle" semantics.
     */
    public static final String SEMANTICS_BUNDLE = "BUNDLE";

    /**
     * Creates a new {@link GroupExtension} instance with the proper element name and namespace.
     */
    public GroupExtension()
    {
        super(ELEMENT, NAMESPACE);
    }

    /**
     * Creates new <tt>GroupExtensionElement</tt> for BUNDLE semantics initialized with given <tt>contents</tt> list.
     *
     * @param contents the list that contains the contents to be bundled.
     * @return new <tt>GroupExtensionElement</tt> for BUNDLE semantics initialized with given <tt>contents</tt> list.
     */
    public static GroupExtension createBundleGroup(List<JingleContent> contents)
    {
        GroupExtension group = new GroupExtension();
        group.setSemantics(SEMANTICS_BUNDLE);
        group.addContents(contents);
        return group;
    }

    /**
     * Gets the semantics of this group.
     *
     * @return the semantics of this group.
     */
    public String getSemantics()
    {
        return getAttributeAsString(SEMANTICS_ATTR_NAME);
    }

    /**
     * Sets the semantics of this group.
     */
    public void setSemantics(String semantics)
    {
        this.setAttribute(SEMANTICS_ATTR_NAME, semantics);
    }

    /**
     * Gets the contents of this group.
     *
     * @return the contents of this group.
     */
    public List<JingleContent> getContents()
    {
        return getChildExtensionsOfType(JingleContent.class);
    }

    /**
     * Sets the contents of this group. For each content from given <tt>contents</tt>list only its
     * name is being preserved.
     *
     * @param contents the contents of this group.
     */
    public void addContents(List<JingleContent> contents)
    {
        for (JingleContent content : contents) {
            JingleContent copy = new JingleContent();
            copy.setName(content.getName());
            addChildExtension(copy);
        }
    }

    /**
     * Parses group extension content.
     *
     * @param parser an XML parser positioned at the packet's starting element.
     * @return new <tt>GroupExtensionElement</tt> initialized with parsed contents list.
     * @throws IOException, XmlPullParserException, ParseException if an error occurs parsing the XML.
     */
    public static GroupExtension parseExtension(XmlPullParser parser)
            throws IOException, XmlPullParserException, SmackParsingException
    {
        GroupExtension group = new GroupExtension();

        String semantics = parser.getAttributeValue("", SEMANTICS_ATTR_NAME);
        if (semantics != null)
            group.setSemantics(semantics);

        boolean done = false;
        XmlPullParser.Event eventType;
        String elementName;
        DefaultExtensionElementProvider<JingleContent> contentProvider
                = new DefaultExtensionElementProvider<>(JingleContent.class);
        while (!done) {
            eventType = parser.next();
            elementName = parser.getName();

            if (elementName.equals(JingleContent.ELEMENT)) {
                JingleContent content = contentProvider.parse(parser);
                group.addChildExtension(content);
            }

            if ((eventType == END_ELEMENT)
                    && parser.getName().equals(ELEMENT)) {
                done = true;
            }
        }
        return group;
    }
}
