package org.atalk.android.util.java.awt.event;

import org.atalk.android.util.java.awt.AWTEvent;


public class ItemEvent extends AWTEvent {
	public static final int ITEM_FIRST = 701;
	public static final int ITEM_LAST = 701;
	public static final int ITEM_STATE_CHANGED = 701;
	public static final int SELECTED = 1;
	public static final int DESELECTED = 2;
	Object item;
	int stateChange;
	private static final long serialVersionUID = -608708132447206933L;
	
	public ItemEvent(Object source, int id) {
		super(source, id);
		// TODO Auto-generated constructor stub
	}
	
	public Object getItem() {
		return this.item;
	}

	public int getStateChange() {
		return this.stateChange;
	}

}
