/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.impl.protocol.jabber.extensions.thumbnail.packet;

import net.java.sip.communicator.util.Base64;

import org.jivesoftware.smack.packet.IQ;
import org.jxmpp.jid.Jid;

/**
 * The <tt>BoB</tt> is an IQ packet that is meant to be used for thumbnail requests and
 * responses. Implementing XEP-0231: Bits of Binary
 *
 * @author Yana Stamcheva
 * @author Eng Chong Meng
 */
public class ThumbnailIQ extends IQ
{
    /**
     * The name of the "data" element.
     */
    public static final String ELEMENT = "data";

    /**
     * The names XMPP space that the thumbnail elements belong to.
     */
    public static final String NAMESPACE = "urn:xmpp:bob";

    /**
     * The name of the thumbnail attribute "cid".
     */
    public final static String CID = "cid";

    /**
     * The name of the thumbnail attribute "mime-type".
     */
    public final static String TYPE = "type";

    private String cid;
    private String mimeType;
    private byte[] data;

    /**
     * An empty constructor used to initialize this class as an <tt>IQProvider</tt>.
     */
    public ThumbnailIQ(String cid, String mimeType, byte[] data)
    {
        super(ELEMENT, NAMESPACE);

        this.cid = cid;
        this.mimeType = mimeType;
        this.data = data;
    }

    /**
     * Creates a <tt>BoB</tt> packet, by specifying the source, the destination, the
     * content-ID and the type of this packet. The type could be one of the types defined in
     * <tt>IQ.Type</tt>.
     *
     * @param from the source of the packet
     * @param to the destination of the packet
     * @param cid the content-ID used to identify this packet in the destination
     * @param type the of the packet, which could be one of the types defined in <tt>IQ.Type</tt>
     */
    public ThumbnailIQ(Jid from, Jid to, String cid, Type type)
    {
        super(ELEMENT, NAMESPACE);

        this.cid = cid;
        this.setFrom(from);
        this.setTo(to);
        this.setType(type);
    }

    /**
     * Creates a <tt>BoB</tt> packet, by specifying the source, the destination, the
     * content-ID, the type of data and the data of the thumbnail. We also precise the type of the
     * packet to create.
     *
     * @param from the source of the packet
     * @param to the destination of the packet
     * @param cid the content-ID used to identify this packet in the destination
     * @param mimeType the type of the data passed
     * @param data the data of the thumbnail
     * @param type the of the packet, which could be one of the types defined in <tt>IQ.Type</tt>
     */
    public ThumbnailIQ(Jid from, Jid to, String cid, String mimeType, byte[] data, Type type)
    {
        this(from, to, cid, type);

        this.data = data;
        this.mimeType = mimeType;
    }

    /**
     * Returns the xml representing the data element in this <tt>IQ</tt> packet.
     */
    @Override
    protected IQChildElementXmlStringBuilder getIQChildElementBuilder(
            IQChildElementXmlStringBuilder xml)
    {
        xml.attribute(CID, cid);
        xml.optAttribute(TYPE, mimeType);
        xml.append('>');

        if (data != null) {
            byte[] encodedData = Base64.encode(data);
            xml.append(new String(encodedData));
        }
        return xml;
    }

    /**
     * Returns the content-ID of this thumbnail packet.
     *
     * @return the content-ID of this thumbnail packet
     */
    public String getCid()
    {
        return cid;
    }

    /**
     * Returns the data of the thumbnail.
     *
     * @return the data of the thumbnail
     */
    public byte[] getData()
    {
        return data;
    }
}
