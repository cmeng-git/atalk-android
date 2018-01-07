package org.atalk.android.util.javax.swing;

import org.atalk.android.util.javax.swing.event.ListSelectionListener;

public abstract interface ListSelectionModel {
	public static final int SINGLE_SELECTION = 0;
	public static final int SINGLE_INTERVAL_SELECTION = 1;
	public static final int MULTIPLE_INTERVAL_SELECTION = 2;

	public abstract void setSelectionInterval(int paramInt1, int paramInt2);

	public abstract void addSelectionInterval(int paramInt1, int paramInt2);

	public abstract void removeSelectionInterval(int paramInt1, int paramInt2);

	public abstract int getMinSelectionIndex();

	public abstract int getMaxSelectionIndex();

	public abstract boolean isSelectedIndex(int paramInt);

	public abstract int getAnchorSelectionIndex();

	public abstract void setAnchorSelectionIndex(int paramInt);

	public abstract int getLeadSelectionIndex();

	public abstract void setLeadSelectionIndex(int paramInt);

	public abstract void clearSelection();

	public abstract boolean isSelectionEmpty();

	public abstract void insertIndexInterval(int paramInt1, int paramInt2,
			boolean paramBoolean);

	public abstract void removeIndexInterval(int paramInt1, int paramInt2);

	public abstract void setValueIsAdjusting(boolean paramBoolean);

	public abstract boolean getValueIsAdjusting();

	public abstract void setSelectionMode(int paramInt);

	public abstract int getSelectionMode();

	public abstract void addListSelectionListener(
			ListSelectionListener paramListSelectionListener);

	public abstract void removeListSelectionListener(
			ListSelectionListener paramListSelectionListener);	
	
}
