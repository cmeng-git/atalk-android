/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.service.neomedia;

/**
 * Represents a factory of <code>StreamConnector</code> instances.
 *
 * @author Lyubomir Marinov
 */
public interface StreamConnectorFactory
{
	/**
	 * Initializes a <code>StreamConnector</code> instance.
	 *
	 * @return a <code>StreamConnector</code> instance
	 */
	public StreamConnector createStreamConnector();
}
