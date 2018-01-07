package org.atalk.xmpp.chatstate;


import org.atalk.xml.Element;
import org.jivesoftware.smackx.chatstates.ChatStateManager;

public enum ChatState {

	ACTIVE, INACTIVE, GONE, COMPOSING, PAUSED;

	public static ChatState parse(Element element) {
		final String NAMESPACE = ChatStateManager.NAMESPACE;

		if (element.hasChild("active",NAMESPACE)) {
			return ACTIVE;
		} else if (element.hasChild("inactive",NAMESPACE)) {
			return INACTIVE;
		} else if (element.hasChild("composing",NAMESPACE)) {
			return COMPOSING;
		} else if (element.hasChild("gone",NAMESPACE)) {
			return GONE;
		} else if (element.hasChild("paused",NAMESPACE)) {
			return PAUSED;
		} else {
			return null;
		}
	}

	public static Element toElement(ChatState state) {
		final String NAMESPACE = ChatStateManager.NAMESPACE;
		final Element element = new Element(state.toString().toLowerCase());
		element.setAttribute("xmlns",NAMESPACE);
		return element;
	}
}
