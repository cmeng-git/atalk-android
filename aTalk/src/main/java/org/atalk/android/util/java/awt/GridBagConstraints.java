package org.atalk.android.util.java.awt;

import java.io.Serializable;

public class GridBagConstraints implements Cloneable, Serializable {
	/**
	 * 
	 */
	private static final long serialVersionUID = -1000070633030801713L;	

	public static final int RELATIVE = -1;
	public static final int REMAINDER = 0;
	public static final int NONE = 0;
	public static final int BOTH = 1;
	public static final int HORIZONTAL = 2;
	public static final int VERTICAL = 3;
	public static final int CENTER = 10;
	public static final int NORTH = 11;
	public static final int NORTHEAST = 12;
	public static final int EAST = 13;
	public static final int SOUTHEAST = 14;
	public static final int SOUTH = 15;
	public static final int SOUTHWEST = 16;
	public static final int WEST = 17;
	public static final int NORTHWEST = 18;
	public static final int PAGE_START = 19;
	public static final int PAGE_END = 20;
	public static final int LINE_START = 21;
	public static final int LINE_END = 22;
	public static final int FIRST_LINE_START = 23;
	public static final int FIRST_LINE_END = 24;
	public static final int LAST_LINE_START = 25;
	public static final int LAST_LINE_END = 26;
	public static final int BASELINE = 256;
	public static final int BASELINE_LEADING = 512;
	public static final int BASELINE_TRAILING = 768;
	public static final int ABOVE_BASELINE = 1024;
	public static final int ABOVE_BASELINE_LEADING = 1280;
	public static final int ABOVE_BASELINE_TRAILING = 1536;
	public static final int BELOW_BASELINE = 1792;
	public static final int BELOW_BASELINE_LEADING = 2048;
	public static final int BELOW_BASELINE_TRAILING = 2304;
	
	public int gridx;
	public int gridy;
	public int gridwidth;
	public int gridheight;
	public double weightx;
	public double weighty;
	public int anchor;
	public int fill;
	public Insets insets;
	public int ipadx;
	public int ipady;
	int tempX;
	int tempY;
	int tempWidth;
	int tempHeight;
	int minWidth;
	int minHeight;
	transient int ascent;
	transient int descent;
	// transient Component.BaselineResizeBehavior baselineResizeBehavior;
	transient int centerPadding;
	transient int centerOffset;

	public GridBagConstraints() {
		this.gridx = -1;
		this.gridy = -1;
		this.gridwidth = 1;
		this.gridheight = 1;
		this.weightx = 0.0D;
		this.weighty = 0.0D;
		this.anchor = 10;
		this.fill = 0;
		this.insets = new Insets(0, 0, 0, 0);
		this.ipadx = 0;
		this.ipady = 0;
	}

	public GridBagConstraints(int paramInt1, int paramInt2, int paramInt3,
			int paramInt4, double paramDouble1, double paramDouble2,
			int paramInt5, int paramInt6, Insets paramInsets, int paramInt7,
			int paramInt8) {
		this.gridx = paramInt1;
		this.gridy = paramInt2;
		this.gridwidth = paramInt3;
		this.gridheight = paramInt4;
		this.fill = paramInt6;
		this.ipadx = paramInt7;
		this.ipady = paramInt8;
		this.insets = paramInsets;
		this.anchor = paramInt5;
		this.weightx = paramDouble1;
		this.weighty = paramDouble2;
	}

	public Object clone() {
		try {
			GridBagConstraints localGridBagConstraints = (GridBagConstraints) super
					.clone();
			localGridBagConstraints.insets = ((Insets) this.insets.clone());
			return localGridBagConstraints;
		} catch (CloneNotSupportedException localCloneNotSupportedException) {
			throw new InternalError();
		}
	}

	boolean isVerticallyResizable() {
		return ((this.fill == 1) || (this.fill == 3));
	}	
	
}