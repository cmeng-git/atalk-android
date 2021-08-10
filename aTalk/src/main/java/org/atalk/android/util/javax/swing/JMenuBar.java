package org.atalk.android.util.javax.swing;

import org.atalk.android.util.java.awt.Insets;


public class JMenuBar extends JComponent implements Accessible {
	private static final String uiClassID = "MenuBarUI";
	private boolean paintBorder = true;
	private Insets margin = null;
	private static final boolean TRACE = false;
	private static final boolean VERBOSE = false;
	private static final boolean DEBUG = false;
	
	public JMenu add(JMenu paramJMenu) {
		return paramJMenu;
	}

}
