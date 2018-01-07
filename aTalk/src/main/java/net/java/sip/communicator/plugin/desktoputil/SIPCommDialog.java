package net.java.sip.communicator.plugin.desktoputil;

import net.java.sip.communicator.util.Logger;

import org.atalk.android.util.javax.swing.JDialog;

public class SIPCommDialog extends JDialog {
	/* 
	* Serial version UID.
    */
	private static final long serialVersionUID = 0L;

	/**
	 * The <tt>Logger</tt> used by the <tt>SIPCommDialog</tt> class and its
	 * instances for logging output.
	 */
	private static final Logger logger = Logger.getLogger(SIPCommDialog.class);

	/**
	 * Indicates if the size and location of this dialog are stored after
	 * closing.
	 */
	private boolean isSaveSizeAndLocation = true;

	public SIPCommDialog() {
		super();
		this.init();
	}

	public SIPCommDialog(boolean isSaveSizeAndLocation) {
		this();
		this.isSaveSizeAndLocation = isSaveSizeAndLocation;
	}


	public void setTitle(String paramString) {
	}


	public void dispose() {

	}

	private void init() {
	}

	public void setVisible(boolean isVisible) {
		super.setVisible(isVisible);
	}

}
