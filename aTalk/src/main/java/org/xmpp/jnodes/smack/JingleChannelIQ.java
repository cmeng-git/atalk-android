package org.xmpp.jnodes.smack;

import org.jivesoftware.smack.packet.IQ;
import org.jxmpp.jid.Jid;

public class JingleChannelIQ extends IQ
{
    public static final String ELEMENT = "channel";
    public static final String NAMESPACE = "http://jabber.org/protocol/jinglenodes#channel";

    public static final String UDP = "udp";
    public static final String TCP = "tcp";

    private String protocol = UDP;
    private String host;
    private int localport = -1;
    private int remoteport = -1;
    private String id;

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

    public String getId()
    {
        return id;
    }

    public void setId(String id)
    {
        this.id = id;
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
        xml.attribute("protocol", protocol);
        if ((localport > 0) && (remoteport > 0) && (host != null)) {
            xml.attribute("host", host);
            xml.attribute("localport", localport);
            xml.attribute("remoteport", remoteport);
        }
        xml.append('>');
        return xml;
    }
}
