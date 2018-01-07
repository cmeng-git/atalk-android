package org.atalk.android.util.javax.swing;


import java.io.PrintStream;
import java.io.Serializable;

import org.atalk.android.util.java.awt.Component;
import org.atalk.android.util.java.awt.Dimension;
import org.atalk.android.util.java.awt.LayoutManager2;
import org.atalk.android.util.java.awt.AWTError;
import org.atalk.android.util.java.awt.Container;

public class BoxLayout implements LayoutManager2, Serializable {
		public static final int X_AXIS = 0;
		public static final int Y_AXIS = 1;
		public static final int LINE_AXIS = 2;
		public static final int PAGE_AXIS = 3;
		private int axis;
		private Container target;
		private transient PrintStream dbg;
		
		
		public BoxLayout(Container paramContainer, int paramInt) {
			if ((paramInt != 0) && (paramInt != 1) && (paramInt != 2)
					&& (paramInt != 3))
				throw new AWTError("Invalid axis");
			this.axis = paramInt;
			this.target = paramContainer;
		}

		BoxLayout(Container paramContainer, int paramInt,
				PrintStream paramPrintStream) {
			this(paramContainer, paramInt);
			this.dbg = paramPrintStream;
		}		

	public BoxLayout(JPanel mainPanel, int yAxis) {
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
