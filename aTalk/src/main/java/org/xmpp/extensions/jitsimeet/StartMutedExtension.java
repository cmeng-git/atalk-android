/*
 * Copyright @ 2018 - present 8x8, Inc.
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
package org.xmpp.extensions.jitsimeet;

import org.xmpp.extensions.*;

import javax.xml.namespace.QName;

/**
 * Packet extension used to indicate whether the peer should start muted or not.
 *
 * @author Hristo Terezov
 * @author Eng Chong Meng
 */
public class StartMutedExtension extends AbstractExtensionElement
{
    /**
     * Name space of start muted packet extension.
     */
    public static final String NAMESPACE = "http://jitsi.org/jitmeet/start-muted";

    /**
     * XML element name of this packet extension.
     */
    public static final String ELEMENT = "startmuted";

    public static final QName QNAME = new QName(NAMESPACE, ELEMENT);

    /**
     * Attribute name for audio muted.
     */
    public static final String AUDIO_ATTRIBUTE_NAME = "audio";

    /**
     * Attribute name for video muted.
     */
    public static final String VIDEO_ATTRIBUTE_NAME = "video";

    /**
     * Constructs new instance of <tt>StartMutedExtensionElement</tt>
     */
    public StartMutedExtension()
    {
        super(ELEMENT, NAMESPACE);
    }

    /**
     * Sets audio muted attribute.
     * @param audioMute the value to be set
     */
    public void setAudioMute(boolean audioMute)
    {
        setAttribute(AUDIO_ATTRIBUTE_NAME, audioMute);
    }

    /**
     * Sets video muted attribute.
     * @param videoMute the value to be set.
     */
    public void setVideoMute(boolean videoMute)
    {
        setAttribute(VIDEO_ATTRIBUTE_NAME, videoMute);
    }

    /**
     * Returns the audio muted attribute.
     * @return the audio muted attribute.
     */
    public boolean getAudioMuted()
    {
        return Boolean.parseBoolean(getAttributeAsString(AUDIO_ATTRIBUTE_NAME));
    }

    /**
     * Returns the video muted attribute.
     * @return the video muted attribute.
     */
    public boolean getVideoMuted()
    {
        return Boolean.parseBoolean(getAttributeAsString(VIDEO_ATTRIBUTE_NAME));
    }
}
