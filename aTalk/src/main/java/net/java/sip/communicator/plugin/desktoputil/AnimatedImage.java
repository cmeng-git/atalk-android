/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package net.java.sip.communicator.plugin.desktoputil;

import org.atalk.android.util.java.awt.Image;
import org.atalk.android.util.java.awt.event.ActionEvent;
import org.atalk.android.util.java.awt.event.ActionListener;
import org.atalk.android.util.java.awt.image.BufferedImage;

/**
 * AnimatedImage will display a series of Images in a predetermined sequence.
 * This sequence can be configured to keep repeating or stop after a specified
 * number of cycles.
 * <p/>
 * An AnimatedImage cannot be shared by different components. However,
 * the Images added to the AnimatedImage can be shared.
 * <p/>
 * The animation sequence is a simple sequential display of each Image. When the
 * end is reached the animation restarts at the first Image. Images are
 * displayed in the order in which they were added. To create custom animation
 * sequences you will need to override the getNextIconIndex() and
 * isCycleCompleted() methods.
 *
 * @author Marin Dzhigarov
 */
public class AnimatedImage extends BufferedImage implements ActionListener {

	public AnimatedImage(int width, int height, int imageType) {
		super(width, height, imageType);
		// TODO Auto-generated constructor stub
	}

	public AnimatedImage(SIPCommButton button, Image i1, Image i2, Image i3) {
		super(0, 0, 0);
		// TODO Auto-generated constructor stub
	}

	@Override
	public void actionPerformed(ActionEvent paramActionEvent) {
		// TODO Auto-generated method stub

	}

	public void pause() {
		// TODO Auto-generated method stub

	}

	public void start() {
		// TODO Auto-generated method stub

	}

}