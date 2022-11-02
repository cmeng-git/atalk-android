/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.quicktime;

/**
 * Represents an Objective-C <code>NSDictionary</code> object.
 *
 * @author Lyubomir Marinov
 */
public class NSDictionary extends NSObject
{

	/**
	 * Initializes a new <code>NSDictionary</code> instance which is to represent a specific Objective-C
	 * <code>NSDictionary</code> object.
	 *
	 * @param ptr
	 *        the pointer to the Objective-C <code>NSDictionary</code> object to be represented by the
	 *        new instance
	 */
	public NSDictionary(long ptr)
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

	public int intForKey(long key)
	{
		return intForKey(getPtr(), key);
	}

	private static native int intForKey(long ptr, long key);
}
