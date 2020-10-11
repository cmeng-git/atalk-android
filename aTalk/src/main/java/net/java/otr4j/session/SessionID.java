/*
 * otr4j, the open source java otr library.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.otr4j.session;

/**
 * @author George Politis
 */
public final class SessionID
{
	private final String accountID;
	private final String userID;
	private final String protocolName;

	public static final SessionID EMPTY = new SessionID(null, null, null);
	public SessionID(String accountID, String userID, String protocolName)
	{
		this.accountID = accountID;
		this.userID = userID;
		this.protocolName = protocolName;
	}

	public String getAccountID()
	{
		return accountID;
	}

	public String getUserID()
	{
		return userID;
	}

	public String getProtocolName()
	{
		return protocolName;
	}

	@Override
	public String toString()
	{
		return accountID + '_' + protocolName + '_' + userID;
	}

	@Override
	public int hashCode()
	{
		final int prime = 31;
		int result = 1;
		result = prime * result + ((accountID == null) ? 0 : accountID.hashCode());
		result = prime * result + ((protocolName == null) ? 0 : protocolName.hashCode());
		result = prime * result + ((userID == null) ? 0 : userID.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj)
	{
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		SessionID other = (SessionID) obj;
		if (accountID == null) {
			if (other.accountID != null)
				return false;
		}
		else if (!accountID.equals(other.accountID))
			return false;
		if (protocolName == null) {
			if (other.protocolName != null)
				return false;
		}
		else if (!protocolName.equals(other.protocolName))
			return false;
		if (userID == null) {
            return other.userID == null;
		}
		else
            return userID.equals(other.userID);
    }
}
