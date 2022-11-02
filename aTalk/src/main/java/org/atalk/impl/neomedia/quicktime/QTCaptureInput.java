/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.quicktime;

/**
 * Represents a QTKit <code>QTCaptureInput</code> object.
 *
 * @author Lyubomir Marinov
 */
public class QTCaptureInput extends NSObject
{

	/**
	 * Initializes a new <code>QTCaptureInput</code> instance which is to represent a specific QTKit
	 * <code>QTCaptureInput</code> object.
	 *
	 * @param ptr
	 *        the pointer to the QTKit <code>QTCaptureInput</code> object to be represented by the new
	 *        instance
	 */
	public QTCaptureInput(long ptr)
	{
		super(ptr);
	}
}
