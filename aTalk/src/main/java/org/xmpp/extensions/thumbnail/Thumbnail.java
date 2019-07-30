/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.xmpp.extensions.thumbnail;

import org.jivesoftware.smack.packet.XmlEnvironment;
import org.jivesoftware.smack.util.SHA1;
import org.jivesoftware.smack.util.XmlStringBuilder;
import org.jivesoftware.smack.xml.XmlPullParser;
import org.jivesoftware.smackx.bob.BoBHash;

/**
 * The <tt>Thumbnail</tt> represents a "thumbnail" XML element, that is contained in the file
 * element, we're sending to notify for a file transfer. The <tt>Thumbnail</tt>'s role is to
 * advertise a thumbnail. Implementing XEP-0264: Jingle Content Thumbnails
 *
 * @author Yana Stamcheva
 * @author Eng Chong Meng
 */
public class Thumbnail
{
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

    private BoBHash cid;
    private String mimeType;
    private int width;
    private int height;

    /**
     * Creates a <tt>ThumbnailExtensionElement</tt> by specifying all extension attributes.
     *
     * @param thumbnailData the byte array containing the thumbnail data
     * @param mimeType the mime type attribute
     * @param width the width of the thumbnail
     * @param height the height of the thumbnail
     */
    public Thumbnail(byte[] thumbnailData, String mimeType, int width, int height)
    {
        this.cid = createCid(thumbnailData);
        this.mimeType = mimeType;
        this.width = width;
        this.height = height;
    }

    /**
     * Creates a <tt>Thumbnail</tt> by parsing the given <tt>xml</tt>.
     * <thumbnail xmlns="urn:xmpp:thumbs:0" cid="sha1+2845ad11024a99dc61fe2bad3c59c5fb0a23cd1c@atalk.org"
     * mime-type="image/png" width="64" height="64"/>
     *
     * @param parser the XML from which we obtain the needed information to create this <tt>Thumbnail</tt>
     */
    public Thumbnail(XmlPullParser parser)
    {
        cid = parseCid(parser.getAttributeValue("", CID));
        mimeType = parser.getAttributeValue("", MIME_TYPE);
        String parserWidth = parser.getAttributeValue("", WIDTH);
        String parserHeight = parser.getAttributeValue("", HEIGHT);
        try {
            width = Integer.parseInt(parserWidth);
            height = Integer.parseInt(parserHeight);
        } catch (NumberFormatException nfe) {
            // ignore, width and height are optional
        }
    }

    private BoBHash parseCid(String cid)
    {
        if (!cid.endsWith("@bob.xmpp.org")) {
            cid = cid.substring(0, cid.indexOf('@')) + "@bob.xmpp.org";
        }
        return BoBHash.fromCid(cid);
    }

    /**
     * Returns the XML representation of this ExtensionElement.
     *
     * @return the packet extension as XML.
     */
    public String toXML(XmlEnvironment enclosingNamespace)
    {
        XmlStringBuilder xml = new XmlStringBuilder();
        xml.prelude(ELEMENT, NAMESPACE);

        // adding thumbnail parameters
        xml.attribute(CID, getCid().getCid());
        xml.attribute(MIME_TYPE, getMimeType());
        xml.attribute(WIDTH, getWidth());
        xml.attribute(HEIGHT, getHeight());
        xml.closeEmptyElement();
        return xml.toString();
    }

    /**
     * Returns the Content-ID, corresponding to this <tt>Thumbnail</tt>.
     *
     * @return the Content-ID, corresponding to this <tt>Thumbnail</tt>
     */
    public BoBHash getCid()
    {
        return cid;
    }

    /**
     * Returns the mime type of this <tt>Thumbnail</tt>.
     *
     * @return the mime type of this <tt>Thumbnail</tt>
     */
    public String getMimeType()
    {
        return mimeType;
    }

    /**
     * Returns the width of this <tt>Thumbnail</tt>.
     *
     * @return the width of this <tt>Thumbnail</tt>
     */
    public int getWidth()
    {
        return width;
    }

    /**
     * Returns the height of this <tt>Thumbnail</tt>.
     *
     * @return the height of this <tt>Thumbnail</tt>
     */
    public int getHeight()
    {
        return height;
    }

    /**
     * Sets the content-ID of this <tt>Thumbnail</tt>.
     *
     * @param cid the content-ID to set
     */
    public void setCid(BoBHash cid)
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
     * Creates the cid attribute value for the given  <tt>thumbnailData</tt>.
     *
     * @param thumbnailData the byte array containing the data
     * @return the cid attribute value for the thumbnail extension
     */
    private BoBHash createCid(byte[] thumbnailData)
    {
        return new BoBHash(SHA1.hex(thumbnailData), "sha1");
    }
}
