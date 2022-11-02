/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Copyright @ 2015 Atlassian Pty Ltd
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
package org.jivesoftware.smackx.thumbnail;

import org.jivesoftware.smack.packet.XmlEnvironment;
import org.jivesoftware.smack.util.StringUtils;
import org.jivesoftware.smack.util.XmlStringBuilder;
import org.jivesoftware.smackx.si.packet.StreamInitiation;
import org.jxmpp.util.XmppDateTime;

/**
 * The <code>FileElement</code> extends the smackx <code>StreamInitiation.File</code>
 * in order to provide a file that supports thumbnails.
 *
 * @author Yana Stamcheva
 * @author Eng Chong Meng
 */
public class ThumbnailFile extends StreamInitiation.File
{
    private Thumbnail thumbnail;

    /**
     * Creates a <code>FileElement</code> by specifying a base file and a thumbnail to extend it with.
     *
     * @param baseFile the file used as a base
     * @param thumbnail the thumbnail to add
     */
    public ThumbnailFile(StreamInitiation.File baseFile, Thumbnail thumbnail)
    {
        this(baseFile.getName(), baseFile.getSize());
        this.thumbnail = thumbnail;
    }

    /**
     * Creates a <code>FileElement</code> by specifying the name and the size of the file.
     *
     * @param name the name of the file
     * @param size the size of the file
     */
    public ThumbnailFile(String name, long size)
    {
        super(name, size);
    }

    /**
     * Represents this <code>FileElement</code> in an XML.
     */
    @Override
    public String toXML(XmlEnvironment enclosingNamespace)
    {
        XmlStringBuilder xml = new XmlStringBuilder(this);

        xml.optAttribute("name", getName());
        xml.optAttribute("size", getSize());

        if (getDate() != null)
            xml.optAttribute("date", XmppDateTime.formatXEP0082Date(this.getDate()));

        xml.optAttribute("hash", getHash());
        xml.rightAngleBracket();

        String desc = this.getDesc();
        if (StringUtils.isNotEmpty(desc)
                || isRanged()
                || (thumbnail != null)) {
            if (StringUtils.isNotEmpty(desc)) {
                xml.element("desc", desc);
            }
            if (isRanged()) {
                xml.emptyElement("range");
            }
            if (thumbnail != null) {
                xml.append(thumbnail.toXML(enclosingNamespace));
            }
            xml.closeElement(getElementName());
        }
        else {
            xml.closeEmptyElement();
        }
        return xml.toString();
    }

    /**
     * Returns the <code>Thumbnail</code> contained in this <code>FileElement</code>.
     *
     * @return the <code>Thumbnail</code> contained in this <code>FileElement</code>
     */
    public Thumbnail getThumbnail()
    {
        return thumbnail;
    }

    /**
     * Sets the given <code>thumbnail</code> to this <code>File</code>.
     *
     * @param thumbnail the <code>Thumbnail</code> to set
     */
    public void setThumbnail(Thumbnail thumbnail)
    {
        this.thumbnail = thumbnail;
    }
}
