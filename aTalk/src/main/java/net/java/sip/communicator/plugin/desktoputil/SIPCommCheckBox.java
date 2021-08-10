/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package net.java.sip.communicator.plugin.desktoputil;

import org.atalk.android.util.java.awt.event.ActionListener;
import org.atalk.android.util.javax.swing.JCheckBox;
import org.atalk.util.OSUtils;

/**
 * @author Lubomir Marinov
 */
public class SIPCommCheckBox
		extends JCheckBox {
	private static final long serialVersionUID = 0L;

	private static final boolean setContentAreaFilled = (OSUtils.IS_WINDOWS || OSUtils.IS_LINUX);

	public SIPCommCheckBox(String i18nString) {
		// TODO Auto-generated constructor stub
	}

	public void setSelected(boolean otrEnabled) {
		// TODO Auto-generated method stub

	}

	public void setEnabled(boolean otrEnabled) {
		// TODO Auto-generated method stub

	}

	public void addActionListener(ActionListener actionListener) {
		// TODO Auto-generated method stub

	}
}
