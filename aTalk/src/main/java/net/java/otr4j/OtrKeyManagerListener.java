package net.java.otr4j;

import net.java.otr4j.session.SessionID;

public interface OtrKeyManagerListener {
	void verificationStatusChanged(SessionID session);
}
