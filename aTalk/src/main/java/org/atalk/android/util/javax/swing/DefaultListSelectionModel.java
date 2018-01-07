package org.atalk.android.util.javax.swing;

import java.io.Serializable;
import java.util.BitSet;

import org.atalk.android.util.javax.swing.event.EventListenerList;
import org.atalk.android.util.javax.swing.event.ListSelectionListener;


public class DefaultListSelectionModel implements ListSelectionModel,
Cloneable, Serializable {
	private static final int MIN = -1;
	private static final int MAX = 2147483647;
	private int selectionMode = 2;
	private int minIndex = 2147483647;
	private int maxIndex = -1;
	private int anchorIndex = -1;
	private int leadIndex = -1;
	private int firstAdjustedIndex = 2147483647;
	private int lastAdjustedIndex = -1;
	private boolean isAdjusting = false;
	private int firstChangedIndex = 2147483647;
	private int lastChangedIndex = -1;
	private BitSet value = new BitSet(32);
	protected EventListenerList listenerList = new EventListenerList();
	protected boolean leadAnchorNotificationEnabled = true;

	@Override
	public void setSelectionInterval(int paramInt1, int paramInt2) {
		// TODO Auto-generated method stub
		
	}
	@Override
	public void addSelectionInterval(int paramInt1, int paramInt2) {
		// TODO Auto-generated method stub
		
	}
	@Override
	public void removeSelectionInterval(int paramInt1, int paramInt2) {
		// TODO Auto-generated method stub
		
	}
	@Override
	public int getMinSelectionIndex() {
		// TODO Auto-generated method stub
		return 0;
	}
	@Override
	public int getMaxSelectionIndex() {
		// TODO Auto-generated method stub
		return 0;
	}
	@Override
	public boolean isSelectedIndex(int paramInt) {
		// TODO Auto-generated method stub
		return false;
	}
	@Override
	public int getAnchorSelectionIndex() {
		// TODO Auto-generated method stub
		return 0;
	}
	@Override
	public void setAnchorSelectionIndex(int paramInt) {
		// TODO Auto-generated method stub
		
	}
	@Override
	public int getLeadSelectionIndex() {
		// TODO Auto-generated method stub
		return 0;
	}
	@Override
	public void setLeadSelectionIndex(int paramInt) {
		// TODO Auto-generated method stub
		
	}
	@Override
	public void clearSelection() {
		// TODO Auto-generated method stub
		
	}
	@Override
	public boolean isSelectionEmpty() {
		// TODO Auto-generated method stub
		return false;
	}
	@Override
	public void insertIndexInterval(int paramInt1, int paramInt2,
			boolean paramBoolean) {
		// TODO Auto-generated method stub
		
	}
	@Override
	public void removeIndexInterval(int paramInt1, int paramInt2) {
		// TODO Auto-generated method stub
		
	}
	@Override
	public void setValueIsAdjusting(boolean paramBoolean) {
		// TODO Auto-generated method stub
		
	}
	@Override
	public boolean getValueIsAdjusting() {
		// TODO Auto-generated method stub
		return false;
	}
	@Override
	public void setSelectionMode(int paramInt) {
		// TODO Auto-generated method stub
		
	}
	@Override
	public int getSelectionMode() {
		// TODO Auto-generated method stub
		return 0;
	}
	
	@Override
	public void addListSelectionListener(
			ListSelectionListener paramListSelectionListener) {
		this.listenerList.add(ListSelectionListener.class,
				paramListSelectionListener);
	}
	
	@Override
	public void removeListSelectionListener(
			ListSelectionListener paramListSelectionListener) {
		// TODO Auto-generated method stub
		
	}

}
