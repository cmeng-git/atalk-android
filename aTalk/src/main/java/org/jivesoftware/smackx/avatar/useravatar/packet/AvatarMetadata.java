/*
 * aTalk, android VoIP and Instant Messaging client
 * Copyright 2014 Eng Chong Meng
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

package org.jivesoftware.smackx.avatar.useravatar.packet;

import org.jivesoftware.smack.packet.ExtensionElement;
import org.jivesoftware.smack.packet.XmlEnvironment;
import org.jivesoftware.smack.util.XmlStringBuilder;

import java.util.LinkedList;
import java.util.List;
import java.util.logging.Logger;

/**
 * Implementation for the XEP-0084: User Avatar Extension Element for <pubsub/> "metadata node" i.e.
 * Avatar "metadata" and XML namespace "urn:xmpp:avatar:metadata". The payload formats are
 * typically transported using the personal eventing profile of XMPP publish-subscribe as
 * specified in XEP-0163.
 *
 * @author Eng Chong Meng
 */
public class AvatarMetadata implements ExtensionElement
{
    /**
     * The logger.
     */
    private static final Logger LOGGER = Logger.getLogger(AvatarMetadata.class.getName());

    /**
     * This userAvatar metadata extension namespace.
     */
    public static final String NAMESPACE = "urn:xmpp:avatar:metadata";

    /*
     * Subscribe to Receive Metadata Notification
     * The user's virtual pubsub service would then send the metadata notification to entities that
     * have subscribed to the user's metadata node or contacts who have advertised an interest in
     * receiving avatar metadata by including a Entity Capabilities (XEP-0115) [8] feature of
     * "urn:xmpp:avatar:metadata+notify".
     */
    public static final String NAMESPACE_NOTIFY = "urn:xmpp:avatar:metadata+notify";

    /**
     * This userAvatar metadata extension element name.
     */
    public static final String ELEMENT = "metadata";

    private List<Info> mInfo = new LinkedList<Info>();

    /**
     * Get the metadata information.
     *
     * @return a list of information
     */
    public List<Info> getInfo()
    {
        return mInfo;
    }

    /**
     * Add a metadata information.
     *
     * @param info the metadata information to add
     */
    public void addInfo(Info info)
    {
        mInfo.add(info);
    }

    @Override
    public String getElementName()
    {
        return ELEMENT;
    }

    @Override
    public String getNamespace()
    {
        return NAMESPACE;
    }

    @Override
    public CharSequence toXML(XmlEnvironment xmlEnvironment)
    {
        XmlStringBuilder xml = new XmlStringBuilder(this);
        xml.rightAngleBracket();
        for (Info info : mInfo) {
            xml.append(info.toXML(XmlEnvironment.EMPTY));
        }
        xml.closeElement(this);
        return xml;
    }

    /**
     * A metadata information element.
     */
    public static class Info
    {
        /**
         * This info extension element name.
         */
        public static final String ELEMENT_INFO = "info";

        /* Attributes for the Info of the Avatar metadata */
        public static final String ATTR_ID = "id";
        public static final String ATTR_TYPE = "type";
        public static final String ATTR_BYTES = "bytes";
        public static final String ATTR_WIDTH = "width";
        public static final String ATTR_HEIGHT = "height";
        public static final String ATTR_URL = "url";

        private int mBytes;
        private int mWidth;
        private int mHeight;
        private String mId;
        private String mType;
        private String mUrl;

        /**
         * Create an Info.
         *
         * @param id the id of the info
         * @param type the MIME type of the avatar
         * @param bytes the size of the avatar in bytes
         */
        public Info(final String id, final String type, final int bytes)
        {
            mId = id;
            mType = type;
            mBytes = bytes;
        }

        /**
         * get the size of the avatar in bytes.
         *
         * @return the size
         */
        public int getBytes()
        {
            return mBytes;
        }

        /**
         * Set the size of the avatar in bytes.
         *
         * @param bytes the size
         */
        public void setBytes(int bytes)
        {
            this.mBytes = bytes;
        }

        /**
         * Get the width.
         *
         * @return the width
         */
        public int getWidth()
        {
            return mWidth;
        }

        /**
         * Set the width.
         *
         * @param width the width
         */
        public void setWidth(int width)
        {
            this.mWidth = width;
        }

        /**
         * Get the height.
         *
         * @return the height
         */
        public int getHeight()
        {
            return mHeight;
        }

        /**
         * Set the height.
         *
         * @param height the height
         */
        public void setHeight(int height)
        {
            this.mHeight = height;
        }

        /**
         * Get the id.
         *
         * @return the id
         */
        public String getId()
        {
            return mId;
        }

        /**
         * Set the id.
         *
         * @param id the id
         */
        public void setId(String id)
        {
            this.mId = id;
        }

        /**
         * Get the MIME type of the avatar.
         *
         * @return the type, null if no type is present
         */
        public String getType()
        {
            return mType;
        }

        /**
         * Set the MIME type of the avatar.
         *
         * @param type the type
         */
        public void setType(String type)
        {
            this.mType = type;
        }

        /**
         * Get the url.
         *
         * @return the url, null if no url is present
         */
        public String getUrl()
        {
            return mUrl;
        }

        /**
         * Set the url.
         *
         * @param url the url
         */
        public void setUrl(String url)
        {
            this.mUrl = url;
        }

        /**
         * Return this information as an xml element.
         * <p>
         * &lt;info
         * id='111f4b3c50d7b0df729d299bc6f8e9ef9066971f'
         * type='image/png'
         * bytes='6345'
         * width='64'
         * height='64'
         * url='http://avatars.example.org/happy.gif'
         * /&gt;
         *
         * @return an xml element representing this information
         */
        public CharSequence toXML(XmlEnvironment xmlEnvironment)
        {
            XmlStringBuilder xml = new XmlStringBuilder();
            xml.halfOpenElement(ELEMENT_INFO);
            xml.attribute(ATTR_ID, mId);
            xml.attribute(ATTR_TYPE, mType);
            xml.attribute(ATTR_BYTES, mBytes);

            xml.optIntAttribute(ATTR_WIDTH, mWidth);
            xml.optIntAttribute(ATTR_HEIGHT, mHeight);
            xml.optAttribute(ATTR_URL, mUrl);

            xml.closeEmptyElement();
            return xml;
        }
    }
}
