/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
pHideExtendedAwayStatus * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package org.xmpp.extensions.jitsimeet;

import org.xmpp.extensions.AbstractExtensionElement;

import javax.xml.namespace.QName;

/**
 * Video muted extension that is included in users presence in Jitsi-meet conferences. It does carry
 * the info about user's video muted status.
 *
 * @author Pawel Domas
 */
public class VideoMutedExtension extends AbstractExtensionElement
{
    /**
     * The namespace of this packet extension.
     */
    public static final String NAMESPACE = "http://jitsi.org/jitmeet/video";

    /**
     * XML element name of this packet extension.
     */
    public static final String ELEMENT = "videomuted";

    public static final QName QNAME = new QName(NAMESPACE, ELEMENT);

    /**
     * Creates new instance of <tt>VideoMutedExtensionElement</tt>.
     */
    public VideoMutedExtension()
    {
        super(ELEMENT, NAMESPACE);
    }

    /**
     * Check whether or not user's video is in muted status.
     *
     * @return <tt>true</tt> if muted, <tt>false</tt> if unmuted or <tt>null</tt> if no valid info
     * found in the extension body.
     */
    public Boolean isVideoMuted()
    {
        return Boolean.valueOf(getText());
    }

    /**
     * Sets user's video muted status.
     *
     * @param videoMuted <tt>true</tt> or <tt>false</tt> which indicates video muted status of the user.
     */
    public void setVideoMuted(Boolean videoMuted)
    {
        setText(videoMuted == null ? "false" : videoMuted.toString());
    }
}
