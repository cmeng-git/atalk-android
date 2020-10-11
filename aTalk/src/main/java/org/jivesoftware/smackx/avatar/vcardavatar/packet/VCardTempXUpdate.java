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

package org.jivesoftware.smackx.avatar.vcardavatar.packet;

import org.jivesoftware.smack.packet.ExtensionElement;
import org.jivesoftware.smack.packet.XmlEnvironment;
import org.jivesoftware.smack.util.XmlStringBuilder;

import java.util.logging.Level;
import java.util.logging.Logger;

import javax.xml.namespace.QName;

/**
 * Implements the presence extension corresponding to element name "x" and namespace
 * "vcard-temp:x:update" (cf. XEP-0153). This adds extension element <photo/> tag to
 * every <presence/> stanza to be sent
 *
 * @author Eng Chong Meng
 */
public class VCardTempXUpdate implements ExtensionElement
{
    /**
     * The <tt>Logger</tt> used by the <tt>VCardTempXUpdatePresenceExtension</tt> class and its
     * instances for logging output.
     */
    private static final Logger LOGGER = Logger.getLogger(VCardTempXUpdate.class.getName());

    /**
     * This presence extension namespace.
     */
    public static final String NAMESPACE = "vcard-temp:x:update";

    /**
     * This presence extension element name.
     */
    public static final String ELEMENT = "x";

    public static final QName QNAME = new QName(NAMESPACE, ELEMENT);

    /**
     * This photo extension element name.
     */
    public static final String ELEMENT_PHOTO = "photo";

    /**
     * The SHA-1 hash in hexadecimal representation of the avatar image.
     *
     * mAvatarHash value definition for <photo/> element generation
     * 1. mAvatarHash == null => client is not yet ready to advertise an image
     * 2. mAvatarHash.length() == 0 => vCard with an empty BINVAL i.e. No  photo
     * 3. mAvatarHash.length() > 0 => is the VCard <PHOTO/> image Hash value
     *
     * @see #toXML(XmlEnvironment) update <x xmlns='vcard-temp:x:update'/> element generation
     */
    private String mAvatarHash = null;

    /**
     * Create an AvatarData.
     *
     * @param avatarHash the data of the photo avatar
     */
    public VCardTempXUpdate(String avatarHash)
    {
        mAvatarHash = avatarHash;
    }

    /**
     * Updates the mAvatarHash used by extension.
     *
     * @param avatarHash The new avatarHash to be used. null => client is not ready, so just ignore.
     * @return "false" if the new avatarHash is the same as the current one. "true" if this
     * extension has been updated with the new avatarHash.
     */
    public boolean setAvatarHash(String avatarHash)
    {
        boolean isNewHash = false;
        if ((avatarHash != null) && !avatarHash.equals(mAvatarHash)) {
            LOGGER.log(Level.INFO, "Account avatar hash updated with (old => new): " + "\n"
                    + mAvatarHash + "\n" + avatarHash);
            mAvatarHash = avatarHash;
            isNewHash = true;
        }
        return isNewHash;
    }

    /**
     * Returns the current avatarHash.
     *
     * @return the current avatarHash.
     */
    public String getAvatarHash()
    {
        return mAvatarHash;
    }

    /**
     * Returns the root element name.
     *
     * @return the element name.
     */
    public String getElementName()
    {
        return ELEMENT;
    }

    /**
     * Returns the root element XML namespace.
     *
     * @return the namespace.
     */
    public String getNamespace()
    {
        return NAMESPACE;
    }

    /**
     * Returns the XML representation of the ExtensionElement.
     *
     * @return the packet extension as XML.
     * <p>
     * <pre>
     *  <x xmlns='vcard-temp:x:update'>
     *      <photo/> or
     *      <photo>910ec77e99e8fbf80778932ef26902f5ebe650df</photo>
     *   </x>
     * </pre>
     * XEP-0153: vCard-Based Avatars Definition for <photo/> element
     * 1. missing update <x xmlns='vcard-temp:x:update'/> element => XEP-0153 not support
     * 2. empty update <x xmlns='vcard-temp:x:update'/> element
     * => client is not yet ready to advertise an image
     * 3. empty <photo/> element => vCard with an empty BINVAL i.e. No photo
     * 4. <photo>{avatarHash}</photo> => contains the VCard <PHOTO/> image Hash value
     * @see #mAvatarHash defination
     */
    @Override
    public CharSequence toXML(XmlEnvironment xmlEnvironment)
    {
        XmlStringBuilder xml = new XmlStringBuilder(this);
        xml.rightAngleBracket();
        xml.optElement(ELEMENT_PHOTO, mAvatarHash);
        xml.closeElement(ELEMENT);
        return xml;
    }
}
