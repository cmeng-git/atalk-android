package org.xmpp.jnodes.smack;

import org.jivesoftware.smack.packet.IQ;
import org.jxmpp.jid.Jid;

import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;

public class JingleTrackerIQ extends IQ
{
	public static final String NAME = "services";
	public static final String NAMESPACE = "http://jabber.org/protocol/jinglenodes";
	private final ConcurrentHashMap<Jid, TrackerEntry> entries = new ConcurrentHashMap<>();

	public JingleTrackerIQ()
	{
		super(NAME, NAMESPACE);
		this.setType(Type.get);
		// this.setStanzaId(IQ.nextID());
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

//	 <services xmlns='http://jabber.org/protocol/jinglenodes'>
//	    <relay policy='public' address='montague.lit' protocol='udp'/>
//	    <tracker policy='public' address='capulet.lit' protocol='udp'/> 
//	    <stun policy='public' address='200.111.111.111' port='3857' protocol='udp'/>
//	 </services>

	@Override
	protected IQChildElementXmlStringBuilder getIQChildElementBuilder(
			IQChildElementXmlStringBuilder xml)
	{
		xml.append('>');
		for (final TrackerEntry entry : entries.values()) {
			xml.halfOpenElement(entry.getType().toString());

			xml.optAttribute("policy", entry.getPolicy());
			xml.optAttribute("address", entry.getJid());
			xml.optAttribute("protocol", entry.getProtocol());
			if (entry.isVerified())
				xml.optBooleanAttribute("verified", entry.isVerified());
			xml.closeEmptyElement();
		}
		return xml;
	}

	public Collection<TrackerEntry> getEntries()
	{
		return entries.values();
	}
}
