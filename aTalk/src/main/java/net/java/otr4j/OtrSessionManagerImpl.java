/*
 * otr4j, the open source java otr librar
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */

package net.java.otr4j;

import net.java.otr4j.session.*;

import java.util.*;

/**
 * @author George Politis
 */
public class OtrSessionManagerImpl implements OtrSessionManager
{
    private OtrEngineHost host;
    private Map<SessionID, Session> sessions;
    private final List<OtrEngineListener> listeners = new Vector<>();

    public OtrSessionManagerImpl(OtrEngineHost host)
    {
        if (host == null)
            throw new IllegalArgumentException("OtrEgineHost is required.");
        this.setHost(host);
    }

    @Override
    public Session getSession(SessionID sessionID)
    {

        if (sessionID == null || sessionID.equals(SessionID.EMPTY))
            throw new IllegalArgumentException();

        if (sessions == null)
            sessions = new Hashtable<>();

        if (!sessions.containsKey(sessionID)) {
            Session session = new SessionImpl(sessionID, getHost());
            sessions.put(sessionID, session);

            session.addOtrEngineListener(new OtrEngineListener()
            {
                @Override
                public void sessionStatusChanged(SessionID sessionID)
                {
                    for (OtrEngineListener l : listeners)
                        l.sessionStatusChanged(sessionID);
                }

                @Override
                public void multipleInstancesDetected(SessionID sessionID)
                {
                    for (OtrEngineListener l : listeners)
                        l.multipleInstancesDetected(sessionID);
                }

                @Override
                public void outgoingSessionChanged(SessionID sessionID)
                {
                    for (OtrEngineListener l : listeners)
                        l.outgoingSessionChanged(sessionID);
                }
            });
            return session;
        }
        else
            return sessions.get(sessionID);
    }

    private void setHost(OtrEngineHost host)
    {
        this.host = host;
    }

    private OtrEngineHost getHost()
    {
        return host;
    }

    @Override
    public void addOtrEngineListener(OtrEngineListener l)
    {
        synchronized (listeners) {
            if (!listeners.contains(l))
                listeners.add(l);
        }
    }

    @Override
    public void removeOtrEngineListener(OtrEngineListener l)
    {
        synchronized (listeners) {
            listeners.remove(l);
        }
    }
}
