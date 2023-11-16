/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.protocol;

import java.util.HashSet;
import java.util.Set;

/**
 * A description of a conference call that can be dialed into. Contains an URI and additional
 * parameters to use.
 *
 * @author Boris Grozev
 */
public class ConferenceDescription
{
	/**
	 * The URI of the conference.
	 */
	private String uri;

	/**
	 * The subject of the conference.
	 */
	private String subject;

	/**
	 * The call ID to use to call into the conference.
	 */
	private String callId;

	/**
	 * The password to use to call into the conference.
	 */
	private String password;

	/**
	 * The name of the conference.
	 */
	private String displayName;
	/**
	 * Whether the conference is available or not.
	 */
	private boolean available = true;

	/**
	 * The transport methods supported for calling into the conference.
	 *
	 * If the set is empty, the intended interpretation is that it is up to the caller to chose an
	 * appropriate transport.
	 */
	private Set<String> transports = new HashSet<String>();

	/**
	 * Creates a new instance with the specified <code>uri</code>, <code>callId</code> and <code>password</code>
	 * .
	 * 
	 * @param uri
	 *        the <code>uri</code> to set.
	 * @param callId
	 *        the <code>callId</code> to set.
	 * @param password
	 *        the <code>auth</code> to set.
	 */
	public ConferenceDescription(String uri, String callId, String password)
	{
		this.uri = uri;
		this.callId = callId;
		this.password = password;
	}

	/**
	 * Creates a new instance with the specified <code>uri</code> and <code>callId</code>
	 * 
	 * @param uri
	 *        the <code>uri</code> to set.
	 * @param callId
	 *        the <code>callId</code> to set.
	 */
	public ConferenceDescription(String uri, String callId)
	{
		this(uri, callId, null);
	}

	/**
	 * Creates a new instance with the specified <code>uri</code>.
	 * 
	 * @param uri
	 *        the <code>uri</code> to set.
	 */
	public ConferenceDescription(String uri)
	{
		this(uri, null, null);
	}

	/**
	 * Creates a new instance.
	 */
	public ConferenceDescription()
	{
		this(null, null, null);
	}

	/**
	 * Returns the display name of the conference.
	 * 
	 * @return the display name
	 */
	public String getDisplayName()
	{
		return displayName;
	}

	/**
	 * Sets the display name of the conference.
	 * 
	 * @param displayName
	 *        the display name to set
	 */
	public void setDisplayName(String displayName)
	{
		this.displayName = displayName;
	}

	/**
	 * Gets the uri of this <code>ConferenceDescription</code>.
	 * 
	 * @return the uri of this <code>ConferenceDescription</code>.
	 */
	public String getUri()
	{
		return uri;
	}

	/**
	 * Sets the uri of this <code>ConferenceDescription</code>.
	 * 
	 * @param uri
	 *        the value to set
	 */
	public void setUri(String uri)
	{
		this.uri = uri;
	}

	/**
	 * Gets the subject of this <code>ConferenceDescription</code>.
	 * 
	 * @return the subject of this <code>ConferenceDescription</code>.
	 */
	public String getSubject()
	{
		return subject;
	}

	/**
	 * Sets the subject of this <code>ConferenceDescription</code>.
	 * 
	 * @param subject
	 *        the value to set
	 */
	public void setSubject(String subject)
	{
		this.subject = subject;
	}

	/**
	 * Gets the call ID of this <code>ConferenceDescription</code>
	 * 
	 * @return the call ID of this <code>ConferenceDescription</code>
	 */
	public String getCallId()
	{
		return callId;
	}

	/**
	 * Sets the call ID of this <code>ConferenceDescription</code>.
	 * 
	 * @param callId
	 *        the value to set
	 */
	public void setCallId(String callId)
	{
		this.callId = callId;
	}

	/**
	 * Gets the password of this <code>ConferenceDescription</code>
	 * 
	 * @return the password of this <code>ConferenceDescription</code>
	 */
	public String getPassword()
	{
		return password;
	}

	/**
	 * Sets the auth of this <code>ConferenceDescription</code>.
	 * 
	 * @param password
	 *        the value to set
	 */
	public void setPassword(String password)
	{
		this.password = password;
	}

	/**
	 * Checks if the conference is available.
	 * 
	 * @return <code>true</code> iff the conference is available.
	 */
	public boolean isAvailable()
	{
		return available;
	}

	/**
	 * Sets the availability of this <code>ConferenceDescription</code>.
	 * 
	 * @param available
	 *        the value to set
	 */
	public void setAvailable(boolean available)
	{
		this.available = available;
	}

	/**
	 * Adds a <code>Transport</code> to the set of <code>Transport</code>s supported by the conference.
	 * 
	 * @param transport
	 *        the <code>Transport</code> to add.
	 */
	public void addTransport(String transport)
	{
		transports.add(transport);
	}

	/**
	 * Checks whether <code>transport</code> is supported by this <code>ConferenceDescription</code>. If the
	 * set of transports for this <code>ConferenceDescription</code> is empty, always returns true.
	 * 
	 * @param transport
	 *        the <code>Transport</code> to check.
	 * @return <code>true</code> if <code>transport</code> is supported by this
	 *         <code>ConferenceDescription</code>
	 */
	public boolean supportsTransport(String transport)
	{
		/*
		 * An empty list means that all transports are supported.
		 */
		if (transports.isEmpty())
			return true;
		return transports.contains(transport);
	}

	/**
	 * Returns the transports supported by this <code>ConferenceDescription</code>
	 * 
	 * @return the supported by this <code>ConferenceDescription</code>
	 */
	public Set<String> getSupportedTransports()
	{
		return new HashSet<String>(transports);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public String toString()
	{
		return "ConferenceDescription(uri=" + uri + "; callid=" + callId + ")";
	}

	/**
	 * Checks if two <code>ConferenceDescription</code> instances have the same call id, URI and
	 * supported transports.
	 * 
	 * @param cd1
	 *        the first <code>ConferenceDescription</code> instance.
	 * @param cd2
	 *        the second <code>ConferenceDescription</code> instance.
	 * @return <code>true</code> if the <code>ConferenceDescription</code> instances have the same call id,
	 *         URI and supported transports. Otherwise <code>false</code> is returned.
	 */
	public boolean compareConferenceDescription(ConferenceDescription cd)
	{
		return (getCallId().equals(cd.getCallId()) && getUri().equals(cd.getUri()) && getSupportedTransports()
			.equals(cd.getSupportedTransports()));
	}
}
