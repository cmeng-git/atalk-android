package org.atalk.android.util.java.awt.event;

import org.atalk.android.util.java.awt.AWTEvent;
import org.atalk.android.util.java.awt.Component;
import org.atalk.android.util.java.awt.Container;


public class HierarchyEvent extends AWTEvent
{
	private static final long serialVersionUID = -5337576970038043990L;
	public static final int HIERARCHY_FIRST = 1400;
	public static final int HIERARCHY_CHANGED = 1400;
	public static final int ANCESTOR_MOVED = 1401;
	public static final int ANCESTOR_RESIZED = 1402;
	public static final int HIERARCHY_LAST = 1402;
	public static final int PARENT_CHANGED = 1;
	public static final int DISPLAYABILITY_CHANGED = 2;
	public static final int SHOWING_CHANGED = 4;
	
	Component changed;
	Container changedParent;
	long changeFlags;
	
	public HierarchyEvent(Component paramComponent1, int paramInt, Component paramComponent2, Container paramContainer) {
		super(paramComponent1, paramInt);
		this.changed = paramComponent2;
		this.changedParent = paramContainer;
	}

	public HierarchyEvent(Component paramComponent1, int paramInt, Component paramComponent2, Container paramContainer, long paramLong) {
		super(paramComponent1, paramInt);
		this.changed = paramComponent2;
		this.changedParent = paramContainer;
		this.changeFlags = paramLong;
	}

	public int getChangeFlags()
	{
		// TODO Auto-generated method stub
		return 0;
	}
}
