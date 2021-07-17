package net.java.otr4j;

import net.java.otr4j.session.Session;
import net.java.otr4j.session.SessionID;

/**
 * 
 * @author George Politis
 * 
 */
public interface OtrSessionManager
{
	/**
     * Get an OTR session.
     * @param sessionID the session to retrieve
     * @return MVN_PASS_JAVADOC_INSPECTION
     */
	Session getSession(SessionID sessionID);

	void addOtrEngineListener(OtrEngineListener l);

	void removeOtrEngineListener(OtrEngineListener l);
}
