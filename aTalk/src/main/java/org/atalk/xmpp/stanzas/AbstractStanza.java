package org.atalk.xmpp.stanzas;

import net.java.sip.communicator.service.protocol.AccountID;

import org.atalk.xml.Element;
import org.jxmpp.jid.Jid;

public class AbstractStanza extends Element
{

	protected AbstractStanza(final String name) {
		super(name);
	}

	public Jid getTo() {
		return getAttributeAsJid("to");
	}

	public Jid getFrom() {
		return getAttributeAsJid("from");
	}

	public void setTo(final Jid to) {
		if (to != null) {
			setAttribute("to", to.toString());
		}
	}

	public void setFrom(final Jid from) {
		if (from != null) {
			setAttribute("from", from.toString());
		}
	}

	public boolean fromServer(final AccountID accountId) {
		return getFrom() == null
			|| getFrom().equals(accountId.getServerAddress())
			|| getFrom().equals(accountId.getFullJid().asBareJid())
			|| getFrom().equals(accountId.getFullJid());
	}

	public boolean toServer(final AccountID accountId) {
		return getTo() == null
			|| getTo().equals(accountId.getServerAddress())
			|| getTo().equals(accountId.getFullJid().asBareJid())
			|| getTo().equals(accountId.getFullJid());
	}

	public boolean fromAccount(final AccountID accountId) {
		return (getFrom() != null)
				&& getFrom().asBareJid().equals(accountId.getFullJid().asBareJid());
	}
}
