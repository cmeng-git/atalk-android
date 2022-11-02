/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.quicktime;

/**
 * Represents an Objective-C <code>NSMutableDictionary</code> object.
 *
 * @author Lyubomir Marinov
 */
public class NSMutableDictionary extends NSDictionary
{

	/**
	 * Initializes a new <code>NSMutableDictionary</code> instance which is to represent a new
	 * Objective-C <code>NSMutableDictionary</code> object.
	 */
	public NSMutableDictionary()
	{
		this(allocAndInit());
	}

	/**
	 * Initializes a new <code>NSMutableDictionary</code> instance which is to represent a specific
	 * Objective-C <code>NSMutableDictionary</code> object.
	 *
	 * @param ptr
	 *        the pointer to the Objective-C <code>NSMutableDictionary</code> object to be represented
	 *        by the new instance
	 */
	public NSMutableDictionary(long ptr)
	{
		super(ptr);
	}

	private static native long allocAndInit();

	public void setIntForKey(int value, long key)
	{
		setIntForKey(getPtr(), value, key);
	}

	private static native void setIntForKey(long ptr, int value, long key);
}
