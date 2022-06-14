package org.jivesoftware.smackx.jinglenodes.element;

import org.jivesoftware.smack.packet.IQ;
import org.jxmpp.jid.Jid;
import org.jivesoftware.smackx.jinglenodes.TrackerEntry;

import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Implementation of the XEP-0278: Jingle Relay Nodes #JIngle Tracker
 *
 * @see <a href="https://xmpp.org/extensions/xep-0278.html">XEP-0278: Jingle Relay Nodes</a>
 */
public class JingleTrackerIQ extends IQ
{
    public static final String ELEMENT = "services";
    public static final String NAMESPACE = "http://jabber.org/protocol/jinglenodes";

    public static final String ATTR_ADDRESS = "address";
    public static final String ATTR_POLICY = "policy";
    public static final String ATTR_PROTOCOL = "protocol";
    public static final String ATTR_VERIFIED = "verified";

    private final ConcurrentHashMap<Jid, TrackerEntry> entries = new ConcurrentHashMap<>();

    public JingleTrackerIQ()
    {
        super(ELEMENT, NAMESPACE);
        this.setType(Type.get);
    }

    public boolean isRequest()
    {
        return Type.get.equals(this.getType());
    }

    public void addEntry(final TrackerEntry entry)
    {
        entries.put(entry.getJid(), entry);
    }

    public void removeEntry(final TrackerEntry entry)
    {
        entries.remove(entry.getJid());
    }

    public Collection<TrackerEntry> getEntries()
    {
        return entries.values();
    }

    //    /**
//     * <services xmlns='http://jabber.org/protocol/jinglenodes'>
//     * <relay policy='public' address='montague.lit' protocol='udp'/>
//     * <tracker policy='public' address='capulet.lit' protocol='udp'/>
//     * <turn policy='public' address='stun.capulet.lit' protocol='udp'/>
//     * <stun policy='public' address='200.111.111.111' port='3857' protocol='udp'/>
//     * </services>
//     */
    @Override
    protected IQChildElementXmlStringBuilder getIQChildElementBuilder(IQChildElementXmlStringBuilder xml)
    {
        xml.rightAngleBracket();
        for (final TrackerEntry entry : entries.values()) {
            xml.halfOpenElement(entry.getType().toString());

            xml.optAttribute(ATTR_POLICY, entry.getPolicy().toString());
            xml.optAttribute(ATTR_ADDRESS, entry.getJid());
            xml.optAttribute(ATTR_PROTOCOL, entry.getProtocol());
            xml.optBooleanAttribute(ATTR_VERIFIED, entry.isVerified());
            xml.closeEmptyElement();
        }
        return xml;
    }
}
