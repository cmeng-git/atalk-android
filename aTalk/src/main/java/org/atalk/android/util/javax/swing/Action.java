package org.atalk.android.util.javax.swing;

import org.atalk.android.util.java.awt.event.ActionListener;

import java.beans.PropertyChangeListener;

public abstract interface Action extends ActionListener {
	public static final String DEFAULT = "Default";
	public static final String NAME = "Name";
	public static final String SHORT_DESCRIPTION = "ShortDescription";
	public static final String LONG_DESCRIPTION = "LongDescription";
	public static final String SMALL_ICON = "SmallIcon";
	public static final String ACTION_COMMAND_KEY = "ActionCommandKey";
	public static final String ACCELERATOR_KEY = "AcceleratorKey";
	public static final String MNEMONIC_KEY = "MnemonicKey";
	public static final String SELECTED_KEY = "SwingSelectedKey";
	public static final String DISPLAYED_MNEMONIC_INDEX_KEY = "SwingDisplayedMnemonicIndexKey";
	public static final String LARGE_ICON_KEY = "SwingLargeIconKey";

	public abstract Object getValue(String paramString);

	public abstract void putValue(String paramString, Object paramObject);

	public abstract void setEnabled(boolean paramBoolean);

	public abstract boolean isEnabled();

	public abstract void addPropertyChangeListener(
			PropertyChangeListener paramPropertyChangeListener);

	public abstract void removePropertyChangeListener(
			PropertyChangeListener paramPropertyChangeListener);
}