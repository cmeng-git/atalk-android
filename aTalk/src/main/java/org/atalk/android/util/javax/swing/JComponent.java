package org.atalk.android.util.javax.swing;

import org.atalk.android.gui.util.event.EventListenerList;
import org.atalk.android.util.java.awt.Color;
import org.atalk.android.util.java.awt.Component;
import org.atalk.android.util.java.awt.Container;
import org.atalk.android.util.java.awt.Dimension;
import org.atalk.android.util.java.awt.Image;
import org.atalk.android.util.javax.swing.border.Border;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;

public abstract class JComponent extends Container implements Serializable {
	
	private static final String uiClassID = "ComponentUI";
	private static final Object ANCESTOR_NOTIFIER_KEY = new Object();
	private static final Object TRANSFER_HANDLER_KEY = new Object();
	private static final Object INPUT_VERIFIER_KEY = new Object();
	private static final Hashtable readObjectCallbacks = new Hashtable(1);
	private static final int NOT_OBSCURED = 0;
	private static final int PARTIALLY_OBSCURED = 1;
	private static final int COMPLETELY_OBSCURED = 2;
	static boolean DEBUG_GRAPHICS_LOADED;
	private static final Object INPUT_VERIFIER_SOURCE_KEY = new Object();
	private boolean isAlignmentXSet;
	private float alignmentX;
	private boolean isAlignmentYSet;
	private float alignmentY;
	protected EventListenerList listenerList = new EventListenerList();
	private Border border;
	private int flags;
	private boolean verifyInputWhenFocusTarget = true;
	transient Component paintingChild;
	public static final int WHEN_FOCUSED = 0;
	public static final int WHEN_ANCESTOR_OF_FOCUSED_COMPONENT = 1;
	public static final int WHEN_IN_FOCUSED_WINDOW = 2;
	public static final int UNDEFINED_CONDITION = -1;
	private static final String KEYBOARD_BINDINGS_KEY = "_KeyboardBindings";
	private static final String WHEN_IN_FOCUSED_WINDOW_BINDINGS = "_WhenInFocusedWindow";
	public static final String TOOL_TIP_TEXT_KEY = "ToolTipText";
	private static final String NEXT_FOCUS = "nextFocus";
	private JPopupMenu popupMenu;
	private static final int IS_DOUBLE_BUFFERED = 0;
	private static final int ANCESTOR_USING_BUFFER = 1;
	private static final int IS_PAINTING_TILE = 2;
	private static final int IS_OPAQUE = 3;
	private static final int KEY_EVENTS_ENABLED = 4;
	private static final int FOCUS_INPUTMAP_CREATED = 5;
	private static final int ANCESTOR_INPUTMAP_CREATED = 6;
	private static final int WIF_INPUTMAP_CREATED = 7;
	private static final int ACTIONMAP_CREATED = 8;
	private static final int CREATED_DOUBLE_BUFFER = 9;
	private static final int IS_PRINTING = 11;
	private static final int IS_PRINTING_ALL = 12;
	private static final int IS_REPAINTING = 13;
	private static final int WRITE_OBJ_COUNTER_FIRST = 14;
	private static final int RESERVED_1 = 15;
	private static final int RESERVED_2 = 16;
	private static final int RESERVED_3 = 17;
	private static final int RESERVED_4 = 18;
	private static final int RESERVED_5 = 19;
	private static final int RESERVED_6 = 20;
	private static final int WRITE_OBJ_COUNTER_LAST = 21;
	private static final int REQUEST_FOCUS_DISABLED = 22;
	private static final int INHERITS_POPUP_MENU = 23;
	private static final int OPAQUE_SET = 24;
	private static final int AUTOSCROLLS_SET = 25;
	private static final int FOCUS_TRAVERSAL_KEYS_FORWARD_SET = 26;
	private static final int FOCUS_TRAVERSAL_KEYS_BACKWARD_SET = 27;
	private static List tempRectangles = new ArrayList(11);
	private static final String defaultLocale = "JComponent.defaultLocale";
	private static Component componentObtainingGraphicsFrom;
	private static Object componentObtainingGraphicsFromLock = new Object();
	private transient Object aaTextInfo;

	public void revalidate() {}
  
	public void setForeground(Color paramColor) {
		Color localColor = Color.GRAY;
		if (localColor != null)
			if (localColor.equals(paramColor))
				return;
			else if ((paramColor == null) || (paramColor.equals(localColor)))
				return;
		repaint();
	}
	public void setBorder(Border paramBorder) {
		Border localBorder = this.border;
		this.border = paramBorder;
		if (paramBorder == localBorder)
			return;
		revalidate();
		repaint();
	}  
	
	public JPopupMenu getComponentPopupMenu() {
		if (this.popupMenu == null) {
			for (Container localContainer = getParent(); localContainer != null; localContainer = localContainer
					.getParent()) {
				if (localContainer instanceof JComponent)
					return ((JComponent) localContainer)
							.getComponentPopupMenu();
			}
			return null;
		}
		return this.popupMenu;
	}

	public JComponent() {
	}

	public void updateUI() {
	}
	
    public void setPreferredSize(Dimension dimension) {
		// TODO Auto-generated method stub - cmeng
		
	}
    
	public void setMinimumSize(Dimension dimension) {
		// TODO Auto-generated method stub - cmeng
		
	}

	public void setMaximumSize(Dimension dimension) {
		// TODO Auto-generated method stub - cmeng
		
	}

	public void setBorder(Object object) {
		// TODO Auto-generated method stub - cmeng
		
	}

	public void setOpaque(boolean b) {
		// TODO Auto-generated method stub - cmeng
		
	}

	public void setVisible(boolean b) {
		// TODO Auto-generated method stub - cmeng
		
	}

	public boolean isVisible() {
		// TODO Auto-generated method stub - cmeng
		return false;
	}

	public void setSelected(Object object) {
		// TODO Auto-generated method stub - cmeng
		
	}

	public Object getItem(int i) {
		// TODO Auto-generated method stub - cmeng
		return null;
	}

	public void repaint() {
		// TODO Auto-generated method stub - cmeng
		
	}

	public int getItemCount() {
		// TODO Auto-generated method stub - cmeng
		return 0;
	}

	public void setEnabled(boolean b) {
		// TODO Auto-generated method stub - cmeng
	}

	public int getWidth() {
		return super.getWidth();
	}

	public int getHeight() {
		return super.getHeight();
	}

	public boolean imageUpdate(Image paramImage, int paramInt1, int paramInt2, int paramInt3, int paramInt4, int paramInt5)
	{
		// TODO Auto-generated method stub
		return false;
	}

	public static byte getWriteObjCounter(JLabel jLabel)
	{
		// TODO Auto-generated method stub
		return 0;
	}

	public static void setWriteObjCounter(JLabel jLabel, byte b)
	{
		// TODO Auto-generated method stub
		
	}

	public void putClientProperty(String string, JLabel jLabel)
	{
		// TODO Auto-generated method stub
		
	}	
}
