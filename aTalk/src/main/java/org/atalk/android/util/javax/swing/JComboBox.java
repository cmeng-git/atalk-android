package org.atalk.android.util.javax.swing;

import java.beans.PropertyChangeListener;

import org.atalk.android.util.java.awt.event.ActionListener;
import org.atalk.android.util.java.awt.event.ItemListener;


public class JComboBox extends JComponent implements Accessible {
	
	private static final String uiClassID = "ComboBoxUI";
	protected ComboBoxModel dataModel;
	protected int maximumRowCount = 8;
	protected boolean isEditable = false;
	protected String actionCommand = "comboBoxChanged";
	protected Object selectedItemReminder = null;
	private Object prototypeDisplayValue;
	private boolean firingActionEvent = false;
	private boolean selectingItem = false;
	private Action action;
	private PropertyChangeListener actionPropertyChangeListener;
	
	public JComboBox(String[] am) {
		// TODO Auto-generated constructor stub
	}

	public JComboBox() {
		// TODO Auto-generated constructor stub
	}

	public void addItem(Object paramObject) {
	}

	public void setSelectedItem(Object actionComboBoxItem) {
		// TODO Auto-generated method stub
		
	}

	public void setSelectedIndex(int i) {
		// TODO Auto-generated method stub
		
	}

	public void addItemListener(ItemListener itemListener) {
		// TODO Auto-generated method stub
		
	}
	
	public void addActionListener(ActionListener paramActionListener) {
		this.listenerList.add(ActionListener.class, paramActionListener);
	}

	public Object getSelectedItem() {
		return this.dataModel.getSelectedItem();
	}
	
}
