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

import java.io.IOException;
import java.util.List;

import static org.jivesoftware.smack.xml.XmlPullParser.Event.END_ELEMENT;

/**
 * Jingle group packet extension(XEP-0338).
 *
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
public class GroupExtensionElement extends AbstractExtensionElement
{
    /**
     * The name of the "group" element.
     */
    public static final String ELEMENT_NAME = "group";

    /**
     * The namespace for the "group" element.
     */
    public static final String NAMESPACE = "urn:xmpp:jingle:apps:grouping:0";

    /**
     * The name of the payload <tt>id</tt> SDP argument.
     */
    public static final String SEMANTICS_ATTR_NAME = "semantics";

    /**
     * Name of the "bundle" semantics.
     */
    public static final String SEMANTICS_BUNDLE = "BUNDLE";

    /**
     * Creates a new {@link GroupExtensionElement} instance with the proper element name and namespace.
     */
    public GroupExtensionElement()
    {
        super(ELEMENT_NAME, NAMESPACE);
    }

    /**
     * Creates new <tt>GroupExtensionElement</tt> for BUNDLE semantics initialized with given <tt>contents</tt> list.
     *
     * @param contents the list that contains the contents to be bundled.
     * @return new <tt>GroupExtensionElement</tt> for BUNDLE semantics initialized with given <tt>contents</tt> list.
     */
    public static GroupExtensionElement createBundleGroup(List<ContentExtensionElement> contents)
    {
        GroupExtensionElement group = new GroupExtensionElement();
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
    public List<ContentExtensionElement> getContents()
    {
        return getChildExtensionsOfType(ContentExtensionElement.class);
    }

    /**
     * Sets the contents of this group. For each content from given <tt>contents</tt>list only its
     * name is being preserved.
     *
     * @param contents the contents of this group.
     */
    public void addContents(List<ContentExtensionElement> contents)
    {
        for (ContentExtensionElement content : contents) {
            ContentExtensionElement copy = new ContentExtensionElement();
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
    public static GroupExtensionElement parseExtension(XmlPullParser parser)
            throws IOException, XmlPullParserException, SmackParsingException
    {
        GroupExtensionElement group = new GroupExtensionElement();

        String semantics = parser.getAttributeValue("", SEMANTICS_ATTR_NAME);
        if (semantics != null)
            group.setSemantics(semantics);

        boolean done = false;
        XmlPullParser.Event eventType;
        String elementName;
        DefaultExtensionElementProvider<ContentExtensionElement> contentProvider
                = new DefaultExtensionElementProvider<>(ContentExtensionElement.class);
        while (!done) {
            eventType = parser.next();
            elementName = parser.getName();

            if (elementName.equals(ContentExtensionElement.ELEMENT_NAME)) {
                ContentExtensionElement content = contentProvider.parse(parser);
                group.addChildExtension(content);
            }

            if ((eventType == END_ELEMENT)
                    && parser.getName().equals(ELEMENT_NAME)) {
                done = true;
            }
        }
        return group;
    }
}
