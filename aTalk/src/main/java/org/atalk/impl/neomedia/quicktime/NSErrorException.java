/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.quicktime;

/**
 * Defines an <code>Exception</code> which reports an <code>NSError</code>.
 *
 * @author Lyubomir Marinov
 */
public class NSErrorException extends Exception
{

	/**
	 * The <code>NSError</code> reported by this instance.
	 */
	private final NSError error;

	/**
	 * Initializes a new <code>NSErrorException</code> instance which is to report a specific
	 * Objective-C <code>NSError</code>.
	 *
	 * @param errorPtr
	 *        the pointer to the Objective-C <code>NSError</code> object to be reported by the new
	 *        instance
	 */
	public NSErrorException(long errorPtr)
	{
		this(new NSError(errorPtr));
	}

	/**
	 * Initializes a new <code>NSErrorException</code> instance which is to report a specific
	 * <code>NSError</code>.
	 *
	 * @param error
	 *        the <code>NSError</code> to be reported by the new instance
	 */
	public NSErrorException(NSError error)
	{
		this.error = error;
	}

	/**
	 * Gets the <code>NSError</code> reported by this instance.
	 *
	 * @return the <code>NSError</code> reported by this instance
	 */
	public NSError getError()
	{
		return error;
	}
}
