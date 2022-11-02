/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.quicktime;

/**
 * Represents an Objective-C <code>NSError</code> object.
 *
 * @author Lyubomir Marinov
 */
public class NSError extends NSObject
{
	/**
	 * Initializes a new <code>NSError</code> instance which is to represent a specific Objective-C
	 * <code>NSError</code> object.
	 *
	 * @param ptr
	 *        the pointer to the Objective-C <code>NSError</code> object to be represented by the new
	 *        instance
	 */
	public NSError(long ptr)
	{
		super(ptr);
	}

	/**
	 * Called by the garbage collector to release system resources and perform other cleanup.
	 *
	 * @see Object#finalize()
	 */
	@Override
	protected void finalize()
	{
		release();
	}
}
