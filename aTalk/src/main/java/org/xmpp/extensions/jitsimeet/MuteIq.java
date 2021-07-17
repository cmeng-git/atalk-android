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

import org.jivesoftware.smack.packet.IQ;
import org.jxmpp.jid.Jid;

/**
 * IQ used for the signaling of audio muting functionality in Jitsi Meet
 * conferences.
 *
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
public class MuteIq extends IQ
{
    /**
     * Name space of mute packet extension.
     */
    public static final String NAMESPACE = "http://jitsi.org/jitmeet/audio";

    /**
     * XML element name of mute packet extension.
     */
    public static final String ELEMENT = "mute";

    /**
     * Attribute name of "jid".
     */
    public static final String JID_ATTR_NAME = "jid";

    /**
     * Attribute name of "actor".
     */
    public static final String ACTOR_ATTR_NAME = "actor";

    /**
     * Muted peer MUC jid.
     */
    private Jid jid;

    /**
     * The jid of the peer tha initiated the mute, optional.
     */
    private Jid actor;

    /**
     * To mute or unmute.
     */
    private Boolean mute;

    /**
     * Creates a new instance of this class.
     */
    public MuteIq()
    {
        super(ELEMENT, NAMESPACE);
    }

    @Override
    protected IQChildElementXmlStringBuilder getIQChildElementBuilder(IQChildElementXmlStringBuilder xml)
    {
        xml.optAttribute(JID_ATTR_NAME, jid);
        xml.optAttribute(ACTOR_ATTR_NAME, actor);

        xml.rightAngleBracket();
        xml.append(mute.toString());
        return xml;
    }

    /**
     * Sets the MUC jid of the user to be muted/unmuted.
     *
     * @param jid muc jid in the form of room_name@muc.server.net/nickname.
     */
    public void setJid(Jid jid)
    {
        this.jid = jid;
    }

    /**
     * Returns MUC jid of the participant in the form of
     * "room_name@muc.server.net/nickname".
     */
    public Jid getJid()
    {
        return jid;
    }

    /**
     * The action contained in the text part of 'mute' XML element body.
     *
     * @param mute <tt>true</tt> to mute the participant. <tt>null</tt> means no
     * action is included in result XML.
     */
    public void setMute(Boolean mute)
    {
        this.mute = mute;
    }

    /**
     * Returns <tt>true</tt> to mute the participant, <tt>false</tt> to unmute
     * or <tt>null</tt> if the action has not been specified(which is invalid).
     */
    public Boolean getMute()
    {
        return mute;
    }

    /**
     * Returns the peer jid that initiated the mute, if any.
     * @return the peer jid that initiated the mute.
     */
    public Jid getActor()
    {
        return actor;
    }

    /**
     * Sets jid for the peer that initiated the mute.
     * @param actor the jid of the peer doing the mute.
     */
    public void setActor(Jid actor)
    {
        this.actor = actor;
    }
}
