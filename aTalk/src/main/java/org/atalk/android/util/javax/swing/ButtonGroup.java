package org.atalk.android.util.javax.swing;

/*** Eclipse Class Decompiler plugin, copyright (c) 2012 Chao Chen (cnfree2000@hotmail.com) ***/

import java.io.Serializable;
import java.util.Vector;

public class ButtonGroup implements Serializable {
	protected Vector<AbstractButton> buttons = new Vector();
	ButtonModel selection = null;


	public boolean isSelected(ButtonModel paramButtonModel) {
		return (paramButtonModel == this.selection);
	}

	public int getButtonCount() {
		if (this.buttons == null)
			return 0;
		return this.buttons.size();
	}

	public void add(JMenuItem menuItem) {
		// TODO Auto-generated method stub
		
	}
}
