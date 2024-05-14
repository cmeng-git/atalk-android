/*
 * aTalk, android VoIP and Instant Messaging client
 * Copyright 2014~2024 Eng Chong Meng
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jivesoftware.smackx.thumbnail.element;

import java.io.File;

import javax.xml.namespace.QName;

import net.java.sip.communicator.impl.protocol.jabber.ThumbnailedFile;

import org.jivesoftware.smack.packet.ExtensionElement;
import org.jivesoftware.smack.packet.XmlEnvironment;
import org.jivesoftware.smack.util.SHA1;
import org.jivesoftware.smack.util.XmlStringBuilder;
import org.jivesoftware.smack.xml.XmlPullParser;
import org.jivesoftware.smackx.bob.ContentId;

public class Thumbnail implements ExtensionElement {
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
     * The name of the thumbnail attribute "media-type".
     */

    public static final String MEDIA_TYPE = "media-type";
    /**
     * The name of the thumbnail attribute "width".
     */
    public static final String WIDTH = "width";

    /**
     * The name of the thumbnail attribute "height".
     */
    public static final String HEIGHT = "height";

    private String uri;
    private ContentId cid;
    private String mediaType;
    private int width;
    private int height;


    @Override
    public String getNamespace() {
        return NAMESPACE;
    }

    @Override
    public String getElementName() {
        return ELEMENT;
    }

    /**
     * Creates a <code>ThumbnailExtensionElement</code> by specifying all extension attributes.
     *
     * @param thumbnailData the byte array containing the thumbnail data
     * @param mediaType the mime type attribute
     * @param width the width of the thumbnail
     * @param height the height of the thumbnail
     */
    public Thumbnail(byte[] thumbnailData, String mediaType, int width, int height) {
        this.cid = createCid(thumbnailData);
        this.mediaType = mediaType;
        this.width = width;
        this.height = height;
        setUri(cid.toSrc());
    }

    public Thumbnail(String uri, String mediaType, int width, int height) {
        this.uri = uri;
        if (uri.startsWith(CID_PREFIX))
            cid = ContentId.fromSrc(uri);
        this.mediaType = mediaType;
        this.width = width;
        this.height = height;
    }

    public static Thumbnail fromFile(File file) {
        if (file instanceof ThumbnailedFile) {
            ThumbnailedFile tnFile = (ThumbnailedFile) file;
            return new Thumbnail(tnFile.getThumbnailData(), tnFile.getThumbnailMimeType(), tnFile.getThumbnailWidth(), tnFile.getThumbnailHeight());
        } else
            return null;
    }

    /**
     * Creates a <code>Thumbnail</code> by parsing the given <code>xml</code>.
     * <thumbnail xmlns="urn:xmpp:thumbs:1" uri='cid:sha1+2845ad11024a99dc61fe2bad3c59c5fb0a23cd1c@atalk.org"
     * mime-type="image/png" width="64" height="64"/>
     *
     * @param parser the XML from which we obtain the needed information to create this <code>Thumbnail</code>
     */
    public Thumbnail(XmlPullParser parser) {
        parseUri(parser.getAttributeValue("", URI));
        mediaType = parser.getAttributeValue("", MEDIA_TYPE);
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
    private void parseUri(String uri) {
        this.uri = uri;
        if (uri.startsWith(CID_PREFIX)) {
            cid = ContentId.fromSrc(uri);
        }
    }

    /**
     * Returns the XML representation of this ExtensionElement.
     *
     * @return the packet extension as XML.
     */
    @Override
    public XmlStringBuilder toXML(XmlEnvironment enclosingNamespace) {
        XmlStringBuilder xml = new XmlStringBuilder(this);

        // adding thumbnail uri parameters
        xml.attribute(URI, uri);
        xml.attribute(MEDIA_TYPE, getMediaType());
        xml.attribute(WIDTH, getWidth());
        xml.attribute(HEIGHT, getHeight());
        xml.closeEmptyElement();
        return xml;
    }

    /**
     * Creates the cid attribute value for the given  <code>thumbnailData</code>.
     *
     * @param thumbnailData the byte array containing the data
     *
     * @return the cid attribute value for the thumbnail extension
     */
    private ContentId createCid(byte[] thumbnailData) {
        return new ContentId(SHA1.hex(thumbnailData), "sha1");
    }

    /**
     * Returns the uri, corresponding to this <code>Thumbnail</code>.
     *
     * @return the uri, corresponding to this <code>Thumbnail</code>
     */
    public String getUri() {
        return uri;
    }

    /**
     * Sets the uri of this <code>Thumbnail</code>.
     *
     * @param uri the uri to set
     */
    public void setUri(String uri) {
        this.uri = uri;
    }

    /**
     * Returns the Content-ID, corresponding to this <code>Thumbnail</code>.
     *
     * @return the Content-ID, corresponding to this <code>Thumbnail</code>
     */
    public ContentId getCid() {
        return cid;
    }

    /**
     * Sets the content-ID of this <code>Thumbnail</code>.
     *
     * @param cid the content-ID to set
     */
    public void setCid(ContentId cid) {
        this.cid = cid;
    }

    /**
     * Returns the mime type of this <code>Thumbnail</code>.
     *
     * @return the mime type of this <code>Thumbnail</code>
     */
    public String getMediaType() {
        return mediaType;
    }

    /**
     * Sets the mime type of the thumbnail.
     *
     * @param mediaType the mime type of the thumbnail
     */
    public void setMediaType(String mediaType) {
        this.mediaType = mediaType;
    }

    /**
     * Returns the width of this <code>Thumbnail</code>.
     *
     * @return the width of this <code>Thumbnail</code>
     */
    public int getWidth() {
        return width;
    }

    /**
     * Sets the width of the thumbnail
     *
     * @param width the width of the thumbnail
     */
    public void setWidth(int width) {
        this.width = width;
    }

    /**
     * Returns the height of this <code>Thumbnail</code>.
     *
     * @return the height of this <code>Thumbnail</code>
     */
    public int getHeight() {
        return height;
    }

    /**
     * Sets the height of the thumbnail
     *
     * @param height the height of the thumbnail
     */
    public void setHeight(int height) {
        this.height = height;
    }
}
