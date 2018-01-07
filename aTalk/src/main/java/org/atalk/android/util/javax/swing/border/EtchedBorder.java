package org.atalk.android.util.javax.swing.border;

import org.atalk.android.util.java.awt.Color;

public class EtchedBorder extends AbstractBorder {
	public static final int RAISED = 0;
	public static final int LOWERED = 1;
	protected int etchType;
	protected Color highlight;
	protected Color shadow;

	public EtchedBorder() {
		this(1);
	}

	public EtchedBorder(int paramInt) {
		this(paramInt, null, null);
	}

	public EtchedBorder(int paramInt, Object object, Object object2) {
		// TODO Auto-generated constructor stub
	}
}