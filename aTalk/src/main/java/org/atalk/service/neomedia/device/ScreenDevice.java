/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.service.neomedia.device;

import java.awt.Dimension;
import java.awt.Point;

/**
 * Represents a physical screen display.
 *
 * @author Sebastien Vincent
 */
public interface ScreenDevice
{
	/**
	 * Determines whether this screen contains a specified point.
	 *
	 * @param p
	 * 		point coordinate
	 * @return <code>true</code> if <code>point</code> belongs to this screen; <code>false</code>, otherwise
	 */
	public boolean containsPoint(Point p);

	/**
	 * Gets this screen's index.
	 *
	 * @return this screen's index
	 */
	public int getIndex();

	/**
	 * Gets the current resolution of this screen.
	 *
	 * @return the current resolution of this screen
	 */
	public Dimension getSize();
}
