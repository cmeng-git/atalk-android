/**
 * Copyright 2003-2006 Jive Software.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package net.java.sip.communicator.impl.protocol.jabber.extensions.si.packet;

import net.java.sip.communicator.impl.protocol.jabber.extensions.thumbnail.ThumbnailElement;

import org.jivesoftware.smack.util.StringUtils;
import org.jivesoftware.smackx.si.packet.StreamInitiation;
import org.jxmpp.util.XmppDateTime;

import java.util.Date;

/**
 * The process by which two entities initiate a stream.
 *
 * @author Alexander Wenckus
 */
public class SiThumb extends StreamInitiation
{
    /**
     * <ul>
     * <li>size: The size, in bytes, of the data to be sent.</li>
     * <li>name: The name of the file that the Sender wishes to send.</li>
     * <li>date: The last modification time of the file. This is specified using the DateTime
     * profile as described in Jabber Date and Time Profiles.</li>
     * <li>hash: The MD5 sum of the file contents.</li>
     * </ul>
     *
     * &lt;desc&gt; is used to provide a sender-generated description of the file so the receiver
     * can better understand what is being sent. It MUST NOT be sent in the result.
     *
     * When &lt;range&gt; is sent in the offer, it should have no attributes. This signifies that
     * the sender can do ranged transfers. When a Stream Initiation result is sent with the <range>
     * element, it uses these attributes:
     *
     * <ul>
     * <li>offset: Specifies the position, in bytes, to start transferring the file data from. This
     * defaults to zero (0) if not specified.</li>
     * <li>length - Specifies the number of bytes to retrieve starting at offset. This defaults to
     * the length of the file from offset to the end.</li>
     * </ul>
     *
     * Both attributes are OPTIONAL on the &lt;range&gt; element. Sending no attributes is
     * synonymous with not sending the &lt;range&gt; element. When no &lt;range&gt; element is sent
     * in the Stream Initiation result, the Sender MUST send the complete file starting at offset 0.
     * More generally, data is sent over the stream byte for byte starting at the offset position
     * for the length specified.
     *
     * @author Alexander Wenckus
     */
    public static class FileElement extends File
    {
        private ThumbnailElement thumbnail;

        /**
         * Creates a <tt>FileElement</tt> by specifying the name and the size of the file.
         *
         * @param name the name of the file
         * @param size the size of the file
         */
        public FileElement(String name, long size)
        {
            super(name, size);
        }

        /**
         * Creates a <tt>FileElement</tt> by specifying a base file and a thumbnail to extend it with.
         *
         * @param baseFile the file used as a base
         * @param thumbnail the thumbnail to add
         */
        public FileElement(File baseFile, ThumbnailElement thumbnail)
        {
            super(baseFile.getName(), baseFile.getSize());
            this.thumbnail = thumbnail;
        }

        /**
         * Returns the <tt>ThumbnailElement</tt> contained in this <tt>FileElement</tt>.
         *
         * @return the <tt>ThumbnailElement</tt> contained in this <tt>FileElement</tt>
         */
        public ThumbnailElement getThumbnailElement()
        {
            return thumbnail;
        }

        /**
         * Sets the given <tt>thumbnail</tt> to this <tt>FileElement</tt>.
         *
         * @param thumbnail the <tt>ThumbnailElement</tt> to set
         */
        public void setThumbnailElement(ThumbnailElement thumbnail)
        {
            this.thumbnail = thumbnail;
        }

        /**
         * Represents this <tt>FileElement</tt> in an XML.
         *
         * @see File#toXML()
         */
        @Override
        public String toXML()
        {
            StringBuilder buffer = new StringBuilder();
            Date date = getDate();
            String desc = getDesc();

            buffer.append("<").append(getElementName()).append(" xmlns=\"").append(getNamespace())
                    .append("\" ");

            if (getName() != null) {
                buffer.append("name=\"").append(StringUtils.escapeForXML(getName())).append("\" ");
            }

            if (getSize() > 0) {
                buffer.append("size=\"").append(getSize()).append("\" ");
            }

            if (getDate() != null) {
                buffer.append("date=\"").append(XmppDateTime.formatXEP0082Date(date)).append("\" ");
            }

            if (getHash() != null) {
                buffer.append("hash=\"").append(getHash()).append("\" ");
            }

            if (((desc != null) && (desc.length() > 0)) || isRanged() || (thumbnail != null)) {
                buffer.append(">");
                if (getDesc() != null && desc.length() > 0) {
                    buffer.append("<desc>").append(StringUtils.escapeForXML(getDesc()))
                            .append("</desc>");
                }
                if (isRanged()) {
                    buffer.append("<range/>");
                }

                if (thumbnail != null) {
                    buffer.append(thumbnail.toXML());
                }
                buffer.append("</").append(getElementName()).append(">");
            }
            else {
                buffer.append("/>");
            }
            return buffer.toString();
        }
    }
}
