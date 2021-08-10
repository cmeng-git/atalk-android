package org.atalk.android.util.java.awt;

import java.io.Serializable;

public class BorderLayout implements LayoutManager2, Serializable {
	
	public static final String NORTH = "North";
	public static final String SOUTH = "South";
	public static final String EAST = "East";
	public static final String WEST = "West";
	public static final String CENTER = "Center";
	public static final String BEFORE_FIRST_LINE = "First";
	public static final String AFTER_LAST_LINE = "Last";
	public static final String BEFORE_LINE_BEGINS = "Before";
	public static final String AFTER_LINE_ENDS = "After";
	public static final String PAGE_START = "First";
	public static final String PAGE_END = "Last";
	public static final String LINE_START = "Before";
	public static final String LINE_END = "After";
	private static final long serialVersionUID = -8658291919501921765L;

	public BorderLayout() {
		this(0, 0);
	}

	public BorderLayout(int i, int j) {
		// TODO Auto-generated constructor stub
	}

	@Override
	public void addLayoutComponent(Component paramComponent, Object paramObject) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public Dimension maximumLayoutSize(Container paramContainer) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public float getLayoutAlignmentX(Container paramContainer) {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public float getLayoutAlignmentY(Container paramContainer) {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public void invalidateLayout(Container paramContainer) {
		// TODO Auto-generated method stub
		
	}	

}
