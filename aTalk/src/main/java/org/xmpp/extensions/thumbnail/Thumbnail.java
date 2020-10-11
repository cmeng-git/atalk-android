/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.xmpp.extensions.thumbnail;

import org.jivesoftware.smack.packet.ExtensionElement;
import org.jivesoftware.smack.packet.XmlEnvironment;
import org.jivesoftware.smack.util.SHA1;
import org.jivesoftware.smack.util.XmlStringBuilder;
import org.jivesoftware.smack.xml.XmlPullParser;
import org.jivesoftware.smackx.bob.BoBHash;

import javax.xml.namespace.QName;

/**
 * The <tt>Thumbnail</tt> represents a "thumbnail" XML element, that is contained in the file
 * element, we're sending to notify for a file transfer. The <tt>Thumbnail</tt>'s role is to
 * advertise a thumbnail. Implementing XEP-0264: Jingle Content Thumbnails v0.4
 *
 * The class is designed mainly to handle only cid
 *
 * @author Yana Stamcheva
 * @author Eng Chong Meng
 */
public class Thumbnail implements ExtensionElement
{
    /**
     * The name of the XML element used for transport of thumbnail parameters.
     */
    public static final String ELEMENT = "thumbnail";

    /**
     * The names XMPP space that the thumbnail elements belong to.
     */
    public static final String NAMESPACE = "urn:xmpp:thumbs:1";

    public static final QName QNAME = new QName(NAMESPACE, ELEMENT);

    /**
     * The name of the thumbnail attribute "cid".
     */
    public static final String URI = "uri";

    public static final String CID_PREFIX = "cid:";

    /**
     * The name of the thumbnail attribute "mime-type".
     */
    public static final String MIME_TYPE = "mime-type";

    /**
     * The name of the thumbnail attribute "width".
     */
    public static final String WIDTH = "width";

    /**
     * The name of the thumbnail attribute "height".
     */
    public static final String HEIGHT = "height";

    private String uri;
    private BoBHash cid;
    private String mimeType;
    private int width;
    private int height;


    @Override
    public String getNamespace()
    {
        return NAMESPACE;
    }

    @Override
    public String getElementName()
    {
        return ELEMENT;
    }

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
        setUri(cid.toSrc());
    }

    public Thumbnail(String uri, String mimeType, int width, int height)
    {
        this.uri = uri;
        if (uri.startsWith(CID_PREFIX))
            cid = BoBHash.fromSrc(uri);
        this.mimeType = mimeType;
        this.width = width;
        this.height = height;
    }

    /**
     * Creates a <tt>Thumbnail</tt> by parsing the given <tt>xml</tt>.
     * <thumbnail xmlns="urn:xmpp:thumbs:1" uri='cid:sha1+2845ad11024a99dc61fe2bad3c59c5fb0a23cd1c@atalk.org"
     * mime-type="image/png" width="64" height="64"/>
     *
     * @param parser the XML from which we obtain the needed information to create this <tt>Thumbnail</tt>
     */
    public Thumbnail(XmlPullParser parser)
    {
        parseUri(parser.getAttributeValue("", URI));
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

    /**
     * Generic 'uri' attribute may contain 'https:' and 'http:' URIs in addition to BoB 'cid:' URIs.
     *
     * @param uri A URI where the thumbnail data can be accessed
     * (typically by using a URI scheme of 'cid:', 'https:', or 'http:').
     */
    private void parseUri(String uri)
    {
        this.uri = uri;
        if (uri.startsWith(CID_PREFIX)) {
            cid = BoBHash.fromSrc(uri);
        }
    }

    /**
     * Returns the XML representation of this ExtensionElement.
     *
     * @return the packet extension as XML.
     */
    @Override
    public XmlStringBuilder toXML(XmlEnvironment enclosingNamespace)
    {
        XmlStringBuilder xml = new XmlStringBuilder(this);

        // adding thumbnail uri parameters
        xml.attribute(URI, uri);
        xml.attribute(MIME_TYPE, getMimeType());
        xml.attribute(WIDTH, getWidth());
        xml.attribute(HEIGHT, getHeight());
        xml.closeEmptyElement();
        return xml;
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

    /**
     * Returns the uri, corresponding to this <tt>Thumbnail</tt>.
     *
     * @return the uri, corresponding to this <tt>Thumbnail</tt>
     */
    public String getUri()
    {
        return uri;
    }

    /**
     * Sets the uri of this <tt>Thumbnail</tt>.
     *
     * @param uri the uri to set
     */
    public void setUri(String uri)
    {
        this.uri = uri;
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
     * Sets the content-ID of this <tt>Thumbnail</tt>.
     *
     * @param cid the content-ID to set
     */
    public void setCid(BoBHash cid)
    {
        this.cid = cid;
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
     * Sets the mime type of the thumbnail.
     *
     * @param mimeType the mime type of the thumbnail
     */
    public void setMimeType(String mimeType)
    {
        this.mimeType = mimeType;
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
     * Sets the width of the thumbnail
     *
     * @param width the width of the thumbnail
     */
    public void setWidth(int width)
    {
        this.width = width;
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
     * Sets the height of the thumbnail
     *
     * @param height the height of the thumbnail
     */
    public void setHeight(int height)
    {
        this.height = height;
    }
}
