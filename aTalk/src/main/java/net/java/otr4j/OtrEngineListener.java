package net.java.otr4j;

import net.java.otr4j.session.SessionID;

/**
 * This interface should be implemented by the host application. It notifies
 * about session status changes.
 * 
 * @author George Politis
 */
public interface OtrEngineListener {
	void sessionStatusChanged(SessionID sessionID);

	void multipleInstancesDetected(SessionID sessionID);

	void outgoingSessionChanged(SessionID sessionID);
}
