package org.jivesoftware.smackx.jinglenodes.element;

import org.jivesoftware.smack.packet.IQ;
import org.jxmpp.jid.Jid;

/**
 * Implementation of the XEP-0278: Jingle Relay Nodes #JIngle Channel
 *
 * @see <a href="https://xmpp.org/extensions/xep-0278.html">XEP-0278: Jingle Relay Nodes</a>
 * @see <a href="https://xmpp.org/extensions/xep-0278.html#def">XEP-0278: Jingle Relay Nodes# 6. Formal Definition</a>
 */
public class JingleChannelIQ extends IQ
{
    public static final String ELEMENT = "channel";
    public static final String NAMESPACE = "http://jabber.org/protocol/jinglenodes#channel";

    public static final String ATTR_EXPIRE = "expire";
    public static final String ATTR_HOST = "host";
    public static final String ATTR_ID = "id";
    public static final String ATTR_LOCALPORT = "localport";
    public static final String ATTR_MAXKBPS = "maxkbps";
    public static final String ATTR_PROTOCOL = "protocol";
    public static final String ATTR_REMOTEPORT = "remoteport";

    public static final String UDP = "udp";
    public static final String TCP = "tcp";

    private String protocol = UDP;
    private String host;
    private int localport = -1;
    private int remoteport = -1;
    private int maxkbps = -1;
    private int expire = -1;
    private String mChannelId;

    public JingleChannelIQ()
    {
        super(ELEMENT, NAMESPACE);
        setType(Type.get);
    }

    public boolean isRequest()
    {
        return Type.get.equals(this.getType());
    }

    public String getProtocol()
    {
        return protocol;
    }

    public void setProtocol(String protocol)
    {
        this.protocol = protocol;
    }

    public int getRemoteport()
    {
        return remoteport;
    }

    public void setRemoteport(int remoteport)
    {
        this.remoteport = remoteport;
    }

    public String getHost()
    {
        return host;
    }

    public void setHost(String host)
    {
        this.host = host;
    }

    public int getLocalport()
    {
        return localport;
    }

    public void setLocalport(int localport)
    {
        this.localport = localport;
    }

    public String getChannelId()
    {
        return mChannelId;
    }

    public void setChannelId(String channelId)
    {
        this.mChannelId = channelId;
    }

    public int getMaxKbps()
    {
        return maxkbps;
    }

    public void setMaxKbps(int kbps)
    {
        maxkbps = kbps;
    }

    public int getExpire()
    {
        return expire;
    }

    public void setExpire(int value)
    {
        expire = value;
    }

    public static IQ createEmptyResult(IQ iq)
    {
        return createIQ(iq.getStanzaId(), iq.getFrom(), iq.getTo(), IQ.Type.result);
    }

    public static IQ createEmptyError(IQ iq)
    {
        return createIQ(iq.getStanzaId(), iq.getFrom(), iq.getTo(), IQ.Type.error);
    }

    public static IQ createEmptyError()
    {
        return createIQ(null, null, null, IQ.Type.error);
    }

    public static IQ createIQ(String id, Jid to, Jid from, IQ.Type type)
    {
        IQ iqPacket = new JingleChannelIQ();
        iqPacket.setStanzaId(id);
        iqPacket.setTo(to);
        iqPacket.setFrom(from);
        iqPacket.setType(type);
        return iqPacket;
    }

    @Override
    protected IQChildElementXmlStringBuilder getIQChildElementBuilder(IQChildElementXmlStringBuilder xml)
    {
        xml.attribute(ATTR_PROTOCOL, protocol);
        if ((localport > 0) && (remoteport > 0) && (host != null)) {
            xml.attribute(ATTR_HOST, host);
            xml.attribute(ATTR_LOCALPORT, localport);
            xml.attribute(ATTR_REMOTEPORT, remoteport);
        }
        xml.append('>');
        return xml;
    }
}
