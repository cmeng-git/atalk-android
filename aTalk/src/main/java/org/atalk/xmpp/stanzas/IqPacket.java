package org.atalk.xmpp.stanzas;

import org.atalk.xml.Element;

public class IqPacket extends AbstractAcknowledgeableStanza {

	public enum TYPE {
		ERROR,
		SET,
		RESULT,
		GET,
		INVALID,
		TIMEOUT
	}

	public IqPacket(final TYPE type) {
		super("iq");
		if (type != TYPE.INVALID) {
			this.setAttribute("type", type.toString().toLowerCase());
		}
	}

	public IqPacket() {
		super("iq");
	}

	public Element query() {
		Element query = findChild("query");
		if (query == null) {
			query = addChild("query");
		}
		return query;
	}

	public Element query(final String xmlns) {
		final Element query = query();
		query.setAttribute("xmlns", xmlns);
		return query();
	}

	public TYPE getType() {
		final String type = getAttribute("type");
		if (type == null) {
			return TYPE.INVALID;
		}
		switch (type) {
			case "error":
				return TYPE.ERROR;
			case "result":
				return TYPE.RESULT;
			case "set":
				return TYPE.SET;
			case "get":
				return TYPE.GET;
			case "timeout":
				return TYPE.TIMEOUT;
			default:
				return TYPE.INVALID;
		}
	}

	public IqPacket generateResponse(final TYPE type) {
		final IqPacket packet = new IqPacket(type);
		packet.setTo(this.getFrom());
		packet.setId(this.getId());
		return packet;
	}

}
