/*
 *
 * Copyright 2014~2024 Paul Schaub
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jivesoftware.smackx.thumbnails.element;

import java.io.File;

import javax.xml.namespace.QName;

import org.jivesoftware.smack.packet.XmlElement;
import org.jivesoftware.smack.packet.XmlEnvironment;
import org.jivesoftware.smack.util.Objects;
import org.jivesoftware.smack.util.SHA1;
import org.jivesoftware.smack.util.XmlStringBuilder;

import org.jivesoftware.smackx.bob.ContentId;
import org.jivesoftware.smackx.thumbnails.component.ThumbnailedFile;

/**
 * Implement XEP-0264: Jingle Content Thumbnails sent in a file transfer.
 *
 * @author Paul Schaub
 * @author Eng Chong Meng
 */
public class ThumbnailElement implements XmlElement {
    /**
     * The name of the XML element used for transport of thumbnail parameters.
     */
    public static final String ELEMENT = "thumbnail";
    /**
     * The names XMPP space that the thumbnail elements belong to.
     */
    public static final String NAMESPACE = "urn:xmpp:thumbs:1";

    public static final QName QNAME = new QName(NAMESPACE, ELEMENT);

    public static final String ATTR_URI = "uri";
    /**
     * The name of the thumbnail attribute "media-type".
     */
    public static final String ATTR_MEDIA_TYPE = "media-type";
    /**
     * The name of the thumbnail attribute "width".
     */
    public static final String ATTR_WIDTH = "width";
    /**
     * The name of the thumbnail attribute "height".
     */
    public static final String ATTR_HEIGHT = "height";

    private final String uri;
    private final String mediaType;
    private final Integer width;
    private final Integer height;

    public ThumbnailElement(String uri) {
        this(uri, null, null, null);
    }

    /**
     * Creates a <code>ThumbnailElement XmlElement</code> by specifying all extension attributes.
     *
     * @param uri the thumbnail uri
     * @param mediaType the mime type attribute
     * @param width the width of the thumbnail
     * @param height the height of the thumbnail
     */
    public ThumbnailElement(String uri, String mediaType, Integer width, Integer height) {
        this.uri = Objects.requireNonNull(uri);
        this.mediaType = mediaType;

        if (width != null && width < 0) {
            throw new IllegalArgumentException("Width cannot be negative.");
        }
        this.width = width;

        if (height != null && height < 0) {
            throw new IllegalArgumentException("Height cannot be negative.");
        }
        this.height = height;
    }

    /**
     * Creates a <code>ThumbnailElement XmlElement</code> by specifying all extension attributes.
     *
     * @param thumbnailData the byte array containing the thumbnail data
     * @param mediaType the mime type attribute
     * @param width the width of the thumbnail
     * @param height the height of the thumbnail
     */
    public ThumbnailElement(byte[] thumbnailData, String mediaType, int width, int height) {
        ContentId cid = new ContentId(SHA1.hex(thumbnailData), "sha1");
        this.uri = cid.toSrc();
        this.mediaType = mediaType;
        this.width = width;
        this.height = height;

    }

    public static ThumbnailElement fromFile(File file) {
        if (file instanceof ThumbnailedFile) {
            ThumbnailedFile tnFile = (ThumbnailedFile) file;
            return new ThumbnailElement(tnFile.getThumbnailData(), tnFile.getThumbnailMimeType(), tnFile.getThumbnailWidth(), tnFile.getThumbnailHeight());
        }
        else
            return null;
    }

    /**
     * Returns the uri, corresponding to this <code>ThumbnailElement</code>.
     *
     * @return the uri, corresponding to this <code>ThumbnailElement</code>
     */
    public String getUri() {
        return uri;
    }

    /**
     * Returns the mime type of this <code>ThumbnailElement</code>.
     *
     * @return the mime type of this <code>ThumbnailElement</code>
     */
    public String getMediaType() {
        return mediaType;
    }

    /**
     * Returns the width of this <code>ThumbnailElement</code>.
     *
     * @return the width of this <code>ThumbnailElement</code>
     */
    public Integer getWidth() {
        return width;
    }

    /**
     * Returns the height of this <code>ThumbnailElement</code>.
     *
     * @return the height of this <code>ThumbnailElement</code>
     */
    public Integer getHeight() {
        return height;
    }

    @Override
    public String getNamespace() {
        return NAMESPACE;
    }

    @Override
    public String getElementName() {
        return ELEMENT;
    }

    /**
     * Returns the XML representation of this XmlElement.
     *
     * @return the extension element as XML.
     */
    @Override
    public CharSequence toXML(XmlEnvironment xmlEnvironment) {
        XmlStringBuilder xml = new XmlStringBuilder(this, xmlEnvironment);

        xml.attribute(ATTR_URI, uri);
        xml.optAttribute(ATTR_MEDIA_TYPE, mediaType);
        xml.optAttribute(ATTR_WIDTH, width);
        xml.optAttribute(ATTR_HEIGHT, height);
        xml.closeEmptyElement();
        return xml;
    }
}
