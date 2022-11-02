/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.quicktime;

/**
 * Represents a QTKit <code>QTCaptureOutput</code> object.
 *
 * @author Lyubomir Marinov
 */
public class QTCaptureOutput extends NSObject
{

	/**
	 * Initializes a new <code>QTCaptureOutput</code> instance which is to represent a specific QTKit
	 * <code>QTCaptureOutput</code> object.
	 *
	 * @param ptr
	 *        the pointer to the QTKit <code>QTCaptureOutput</code> object to be represented by the new
	 *        instance
	 */
	public QTCaptureOutput(long ptr)
	{
		super(ptr);
	}
}
