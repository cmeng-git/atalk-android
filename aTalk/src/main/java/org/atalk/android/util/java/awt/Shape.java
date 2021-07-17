package org.atalk.android.util.java.awt;

import org.atalk.android.util.java.awt.geom.AffineTransform;
import org.atalk.android.util.java.awt.geom.PathIterator;
import org.atalk.android.util.java.awt.geom.Point2D;
import org.atalk.android.util.java.awt.geom.Rectangle2D;

public abstract interface Shape
{
	public abstract Rectangle getBounds();

	public abstract Rectangle2D getBounds2D();

	public abstract boolean contains(double paramDouble1, double paramDouble2);

	public abstract boolean contains(Point2D paramPoint2D);

	public abstract boolean intersects(double paramDouble1, double paramDouble2, double paramDouble3, double paramDouble4);

	public abstract boolean intersects(Rectangle2D paramRectangle2D);

	public abstract boolean contains(double paramDouble1, double paramDouble2, double paramDouble3, double paramDouble4);

	public abstract boolean contains(Rectangle2D paramRectangle2D);

	public abstract PathIterator getPathIterator(AffineTransform paramAffineTransform);

	public abstract PathIterator getPathIterator(AffineTransform paramAffineTransform, double paramDouble);
}