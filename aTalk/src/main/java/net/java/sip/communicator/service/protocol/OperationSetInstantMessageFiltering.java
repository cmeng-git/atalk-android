/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.protocol;

import net.java.sip.communicator.service.protocol.event.EventFilter;

/**
 * An operation set that allows plugins to register filters which could intercept instant messages
 * and determine whether or not they should be dispatched to regular listeners. <code>EventFilter</code>
 * -s allow implementing features that use standard instant messaging channels to exchange
 *
 * @author Keio Kraaner
 */
public interface OperationSetInstantMessageFiltering extends OperationSet
{
	/**
	 * Registeres an <code>EventFilter</code> with this operation set so that events, that do not need
	 * processing, are filtered out.
	 *
	 * @param filter
	 *        the <code>EventFilter</code> to register.
	 */
	public void addEventFilter(EventFilter filter);

	/**
	 * Unregisteres an <code>EventFilter</code> so that it won't check any more if an event should be
	 * filtered out.
	 *
	 * @param filter
	 *        the <code>EventFilter</code> to unregister.
	 */
	public void removeEventFilter(EventFilter filter);
}
