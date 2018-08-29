/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.impl.protocol.jabber.extensions.thumbnail;

import net.java.sip.communicator.util.Logger;
import net.java.sip.communicator.util.Sha1Crypto;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.security.NoSuchAlgorithmException;

/**
 * The <tt>ThumbnailElement</tt> represents a "thumbnail" XML element, that is contained in the file
 * element, we're sending to notify for a file transfer. The <tt>ThumbnailElement</tt>'s role is to
 * advertise a thumbnail. Implementing XEP-0264: Jingle Content Thumbnails
 *
 * @author Yana Stamcheva
 */
public class ThumbnailElement
{
    private static final Logger logger = Logger.getLogger(ThumbnailElement.class);

    /**
     * The name of the XML element used for transport of thumbnail parameters.
     */
    public static final String ELEMENT = "thumbnail";

    /**
     * The names XMPP space that the thumbnail elements belong to.
     */
    public static final String NAMESPACE = "urn:xmpp:thumbs:0";
    // public static final String NAMESPACE = "urn:xmpp:thumbs:1";

    /**
     * The name of the thumbnail attribute "cid".
     */
    public final static String CID = "cid";

    /**
     * The name of the thumbnail attribute "mime-type".
     */
    public final static String MIME_TYPE = "mime-type";

    /**
     * The name of the thumbnail attribute "width".
     */
    public final static String WIDTH = "width";

    /**
     * The name of the thumbnail attribute "height".
     */
    public final static String HEIGHT = "height";

    private String cid;
    private String mimeType;
    private int width;
    private int height;

    /**
     * Creates a <tt>ThumbnailPacketExtension</tt> by specifying all extension attributes.
     *
     * @param serverAddress the Jabber address of the destination contact
     * @param thumbnailData the byte array containing the thumbnail data
     * @param mimeType the mime type attribute
     * @param width the width of the thumbnail
     * @param height the height of the thumbnail
     */
    public ThumbnailElement(String serverAddress, byte[] thumbnailData, String mimeType, int width, int height)
    {
        this.cid = createCid(serverAddress, thumbnailData);
        this.mimeType = mimeType;
        this.width = width;
        this.height = height;
    }

    /**
     * Creates a <tt>ThumbnailElement</tt> by parsing the given <tt>xml</tt>.
     * <thumbnail xmlns="urn:xmpp:thumbs:0" cid="sha1+2845ad11024a99dc61fe2bad3c59c5fb0a23cd1c@atalk.org"
     * mime-type="image/png" width="64" height="64"/>
     *
     * @param parser the XML from which we obtain the needed information to create this <tt>ThumbnailElement</tt>
     */
    public ThumbnailElement(XmlPullParser parser)
    {
        boolean done = false;
        int eventType;
        String elementName;

        while (!done) {
            try {
                eventType = parser.getEventType();
                elementName = parser.getName();

                if (eventType == XmlPullParser.START_TAG) {
                    if (elementName.equals(ELEMENT)) {
                        this.setCid(parser.getAttributeValue("", CID));
                        this.setMimeType(parser.getAttributeValue("", MIME_TYPE));
                        this.setWidth(Integer.parseInt(parser.getAttributeValue("", WIDTH)));
                        this.setHeight(Integer.parseInt(parser.getAttributeValue("", HEIGHT)));
                    }
                }
                else if (eventType == XmlPullParser.END_TAG) {
                    if (elementName.equals(ELEMENT)) {
                        done = true;
                    }
                }
                if (!done)
                    eventType = parser.next();
            } catch (XmlPullParserException e) {
                if (logger.isDebugEnabled())
                    logger.debug("Element name unknown!");
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Returns the XML representation of this ExtensionElement.
     *
     * @return the packet extension as XML.
     */
    public String toXML(String enclosingNamespace)
    {
        StringBuffer buf = new StringBuffer();

        // open element
        buf.append("<").append(ELEMENT).append(" xmlns=\"").append(NAMESPACE).append("\"");

        // adding thumbnail parameters
        buf = addXmlAttribute(buf, CID, this.getCid());
        buf = addXmlAttribute(buf, MIME_TYPE, this.getMimeType());
        buf = addXmlIntAttribute(buf, WIDTH, this.getWidth());
        buf = addXmlIntAttribute(buf, HEIGHT, this.getWidth());

        // close element
        buf.append("/>");
        return buf.toString();
    }

    /**
     * Returns the Content-ID, corresponding to this <tt>ThumbnailElement</tt>.
     *
     * @return the Content-ID, corresponding to this <tt>ThumbnailElement</tt>
     */
    public String getCid()
    {
        return cid;
    }

    /**
     * Returns the mime type of this <tt>ThumbnailElement</tt>.
     *
     * @return the mime type of this <tt>ThumbnailElement</tt>
     */
    public String getMimeType()
    {
        return mimeType;
    }

    /**
     * Returns the width of this <tt>ThumbnailElement</tt>.
     *
     * @return the width of this <tt>ThumbnailElement</tt>
     */
    public int getWidth()
    {
        return width;
    }

    /**
     * Returns the height of this <tt>ThumbnailElement</tt>.
     *
     * @return the height of this <tt>ThumbnailElement</tt>
     */
    public int getHeight()
    {
        return height;
    }

    /**
     * Sets the content-ID of this <tt>ThumbnailElement</tt>.
     *
     * @param cid the content-ID to set
     */
    public void setCid(String cid)
    {
        this.cid = cid;
    }

    /**
     * Sets the mime type of the thumbnail.
     *
     * @param mimeType the mime type of the thumbnail
     */
    public void setMimeType(String mimeType)
    {
        this.mimeType = mimeType;
    }

    /**
     * Sets the width of the thumbnail
     *
     * @param width the width of the thumbnail
     */
    public void setWidth(int width)
    {
        this.width = width;
    }

    /**
     * Sets the height of the thumbnail
     *
     * @param height the height of the thumbnail
     */
    public void setHeight(int height)
    {
        this.height = height;
    }

    /**
     * Creates the XML <tt>String</tt> corresponding to the specified attribute and value and adds
     * them to the <tt>buff</tt> StringBuffer.
     *
     * @param buff the <tt>StringBuffer</tt> to add the attribute and value to.
     * @param attrName the name of the thumbnail attribute that we're adding.
     * @param attrValue the value of the attribute we're adding to the XML buffer.
     * @return the <tt>StringBuffer</tt> that we've added the attribute and its value to.
     */
    private StringBuffer addXmlAttribute(StringBuffer buff, String attrName, String attrValue)
    {
        buff.append(" " + attrName + "=\"").append(attrValue).append("\"");
        return buff;
    }

    /**
     * Creates the XML <tt>String</tt> corresponding to the specified attribute and value and adds
     * them to the <tt>buff</tt> StringBuffer.
     *
     * @param buff the <tt>StringBuffer</tt> to add the attribute and value to.
     * @param attrName the name of the thumbnail attribute that we're adding.
     * @param attrValue the value of the attribute we're adding to the XML buffer.
     * @return the <tt>StringBuffer</tt> that we've added the attribute and its value to.
     */
    private StringBuffer addXmlIntAttribute(StringBuffer buff, String attrName, int attrValue)
    {
        return addXmlAttribute(buff, attrName, String.valueOf(attrValue));
    }

    /**
     * Creates the cid attrubte value for the given <tt>contactJabberAddress</tt> and
     * <tt>thumbnailData</tt>.
     *
     * @param serverAddress the Jabber server address
     * @param thumbnailData the byte array containing the data
     * @return the cid attrubte value for the thumbnail extension
     */
    private String createCid(String serverAddress, byte[] thumbnailData)
    {
        try {
            return "sha1+" + Sha1Crypto.encode(thumbnailData) + "@" + serverAddress;
        } catch (NoSuchAlgorithmException e) {
            if (logger.isDebugEnabled())
                logger.debug("Failed to encode the thumbnail in SHA-1.", e);
        } catch (UnsupportedEncodingException e) {
            if (logger.isDebugEnabled())
                logger.debug("Failed to encode the thumbnail in SHA-1.", e);
        }
        return null;
    }
}
