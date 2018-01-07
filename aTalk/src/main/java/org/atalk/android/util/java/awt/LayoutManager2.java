package org.atalk.android.util.java.awt;

public abstract interface LayoutManager2 extends LayoutManager {
	public abstract void addLayoutComponent(Component paramComponent,
			Object paramObject);

	public abstract Dimension maximumLayoutSize(Container paramContainer);

	public abstract float getLayoutAlignmentX(Container paramContainer);

	public abstract float getLayoutAlignmentY(Container paramContainer);

	public abstract void invalidateLayout(Container paramContainer);
}