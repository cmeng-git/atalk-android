package org.atalk.android.util.javax.swing;

import java.util.Hashtable;

import org.atalk.android.util.java.awt.Font;

public class UIDefaults extends Hashtable<Object, Object> {
	private static final Object PENDING = new String("Pending");

	
	public Font getFont(Object paramObject) {
		Object localObject = get(paramObject);
		return ((localObject instanceof Font) ? (Font) localObject : null);
	}

}
