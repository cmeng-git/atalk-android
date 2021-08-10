package org.atalk.android.util.javax.swing;


import org.atalk.android.util.java.awt.Component;
import org.atalk.android.util.java.awt.Container;


public class JRootPane extends JComponent implements Accessible {
	private static final long serialVersionUID = 1L;
	
	private static final String uiClassID = "RootPaneUI";
	public static final int NONE = 0;
	public static final int FRAME = 1;
	public static final int PLAIN_DIALOG = 2;
	public static final int INFORMATION_DIALOG = 3;
	public static final int ERROR_DIALOG = 4;
	public static final int COLOR_CHOOSER_DIALOG = 5;
	public static final int FILE_CHOOSER_DIALOG = 6;
	public static final int QUESTION_DIALOG = 7;
	public static final int WARNING_DIALOG = 8;
	private int windowDecorationStyle;
	protected JMenu menuBar;
	protected Container contentPane;
	protected Component glassPane;
	protected JButton defaultButton;


	boolean useTrueDoubleBuffering = true;
	
	public Container getContentPane() {
		return this.contentPane;
	}
	

}
