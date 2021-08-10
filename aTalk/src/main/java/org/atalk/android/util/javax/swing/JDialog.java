package org.atalk.android.util.javax.swing;

import org.atalk.android.util.java.awt.Container;
import org.atalk.android.util.java.awt.Frame;
import org.atalk.android.util.java.awt.Rectangle;

import android.app.Dialog;
import android.content.Context;

public class JDialog extends Dialog implements Accessible {
	private static final Object defaultLookAndFeelDecoratedKey = new Object();
	private int defaultCloseOperation;
	protected JRootPane rootPane;
	protected boolean rootPaneCheckingEnabled;

	public JDialog() {
		this((Frame) null, false);
	}

	public JDialog(Frame paramFrame) {
		this(paramFrame, false);
	}	
	
	public JDialog(Frame paramFrame, boolean paramBoolean) {
		this(paramFrame, null, paramBoolean);
	}
	
	public JDialog(Frame paramFrame, String paramString, boolean paramBoolean) {
		super(null);
	}
	
	public JDialog(Context context) {
		super(context);
		// TODO Auto-generated constructor stub
	}

	public Container getContentPane() {
		return getRootPane().getContentPane();
	}
	
	public JRootPane getRootPane() {
		return this.rootPane;
	}
	
	public void setVisible(boolean paramBoolean) {
	}
	
	public int getWidth() {
		// TODO Auto-generated method stub
		return 0;
	}

	public int getHeight() {
		// TODO Auto-generated method stub
		return 0;
	}

	public void setLocation(int i, int j) {
		// TODO Auto-generated method stub
		
	}
	
	public void pack()
	{
	}
	
	protected Rectangle getBox(Rectangle paramRectangle) {
		return paramRectangle;
	}
}
