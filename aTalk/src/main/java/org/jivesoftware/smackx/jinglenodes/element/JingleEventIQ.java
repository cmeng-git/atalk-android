/**
 *
 * Copyright 2017-2022 Eng Chong Meng
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
package org.jivesoftware.smackx.jinglenodes.element;

import org.jivesoftware.smack.packet.IQ;
import org.jxmpp.jid.Jid;

/**
 * Implementation of the XEP-0278: Jingle Relay Nodes #JIngle Event:
 * Does not seems to be standard xmpp protocol; Event is sent from server???.
 *
 * @see <a href="https://xmpp.org/extensions/xep-0278.html">XEP-0278: Jingle Relay Nodes</a>
 */
public class JingleEventIQ extends IQ
{
    public static final String ELEMENT = "channel";
    public static final String NAMESPACE = "http://jabber.org/protocol/jinglenodes#event";

    public static final String ATTR_EVENT = "event";
    public static final String ATTR_ID = "id";
    public static final String ATTR_KILLED = "killed";
    public static final String ATTR_TIME = "time";
    public static final String KILLED = "killed";

    private String mEvent = KILLED;
    private String mChannelId;
    private int mTime;

    public JingleEventIQ()
    {
        super(ELEMENT, NAMESPACE);
        setType(Type.get);
    }

    public boolean isRequest()
    {
        return Type.get.equals(this.getType());
    }

    public String getChannelId()
    {
        return mChannelId;
    }

    public void setChannelId(String channelId)
    {
        mChannelId = channelId;
    }

    public String getEvent()
    {
        return mEvent;
    }

    public void setEvent(String event)
    {
        mEvent = event;
    }

    public int getTime()
    {
        return mTime;
    }

    public void setTime(int time)
    {
        mTime = time;
    }

    public static IQ createEmptyResult(IQ iq)
    {
        return createIQ(iq.getStanzaId(), iq.getFrom(), iq.getTo(), Type.result);
    }

    public static IQ createEmptyError(IQ iq)
    {
        return createIQ(iq.getStanzaId(), iq.getFrom(), iq.getTo(), Type.error);
    }

    public static IQ createEmptyError()
    {
        return createIQ(null, null, null, Type.error);
    }

    public static IQ createIQ(String id, Jid to, Jid from, Type type)
    {
        IQ iqPacket = new JingleEventIQ();
        iqPacket.setStanzaId(id);
        iqPacket.setTo(to);
        iqPacket.setFrom(from);
        iqPacket.setType(type);
        return iqPacket;
    }

    /**
     * <iq to='peacock@atalk.sytes.net/atalk' from='jn.atalk.sytes.net' type='set' id='stanza-1576044011'>
     * <channel xmlns='http://jabber.org/protocol/jinglenodes#event' event='killed' id='x0.10313.0X' time='208'/>
     * </iq>
     */
    @Override
    protected IQChildElementXmlStringBuilder getIQChildElementBuilder(IQChildElementXmlStringBuilder xml)
    {
        xml.attribute(ATTR_KILLED, mEvent);
        xml.attribute(ATTR_ID, mChannelId);
        if (mTime > 0) {
            xml.optAttribute(ATTR_TIME, mTime);
        }
        xml.append('>');
        return xml;
    }
}
