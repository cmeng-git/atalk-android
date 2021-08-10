package org.xmpp.jnodes.smack;

import org.jxmpp.jid.Jid;

public class TrackerEntry
{
    public enum Type
    {
        relay, tracker
    }

    public enum Policy
    {
        _public, _roster;

        @Override
        public String toString()
        {
            return this.name().substring(1);
        }
    }

    private Type type;
    private Policy policy;
    private boolean verified = false;
    private String protocol;
    private Jid jid;

    public TrackerEntry(final Type type, final Policy policy, final Jid jid, final String protocol)
    {
        this.type = type;
        this.policy = policy;
        this.jid = jid;
        this.protocol = protocol;
    }

    public Type getType()
    {
        return type;
    }

    public void setType(Type type)
    {
        this.type = type;
    }

    public Jid getJid()
    {
        return jid;
    }

    public void setJid(Jid jid)
    {
        this.jid = jid;
    }

    public boolean isVerified()
    {
        return verified;
    }

    public void setVerified(boolean verified)
    {
        this.verified = verified;
    }

    public Policy getPolicy()
    {
        return policy;
    }

    public void setPolicy(Policy policy)
    {
        this.policy = policy;
    }

    public String getProtocol()
    {
        return protocol;
    }

    public void setProtocol(String protocol)
    {
        this.protocol = protocol;
    }
}
