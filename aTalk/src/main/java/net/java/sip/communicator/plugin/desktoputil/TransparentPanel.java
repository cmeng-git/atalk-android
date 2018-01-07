/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package net.java.sip.communicator.plugin.desktoputil;

import org.atalk.android.util.java.awt.LayoutManager;

/**
 * Provides compatibility with source code written prior to the inception of
 * libjitsi.
 *
 * @author Lyubomir Marinov
 */
public class TransparentPanel extends org.atalk.util.swing.TransparentPanel {
	private static final long serialVersionUID = 0L;

	public TransparentPanel() {
	}

	public TransparentPanel(LayoutManager layout) {
		super(layout);
	}
}
