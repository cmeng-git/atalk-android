package org.atalk.android.util.java.awt;

import java.io.Serializable;

public class FlowLayout implements LayoutManager, Serializable {
	public static final int LEFT = 0;
	public static final int CENTER = 1;
	public static final int RIGHT = 2;
	public static final int LEADING = 3;
	public static final int TRAILING = 4;
	int align;
	int newAlign;
	int hgap;
	int vgap;
	private boolean alignOnBaseline;
	private static final long serialVersionUID = -7262534875583282631L;
	private static final int currentSerialVersion = 1;
	private int serialVersionOnStream;

	public FlowLayout() {
		this(1, 5, 5);
	}
	
	public FlowLayout(int paramInt1, int paramInt2, int paramInt3) {
		this.serialVersionOnStream = 1;
		this.hgap = paramInt2;
		this.vgap = paramInt3;
		setAlignment(paramInt1);
	}
	
	public void setAlignment(int paramInt) {
		this.newAlign = paramInt;
		switch (paramInt) {
		case 3:
			this.align = 0;
			break;
		case 4:
			this.align = 2;
			break;
		default:
			this.align = paramInt;
		}
	}
}