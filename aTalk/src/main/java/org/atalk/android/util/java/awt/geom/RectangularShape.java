package org.atalk.android.util.java.awt.geom;

import org.atalk.android.util.java.awt.Rectangle;
import org.atalk.android.util.java.awt.Shape;

public abstract class RectangularShape implements Shape, Cloneable
{
	public abstract double getX();

	public abstract double getY();

	public abstract double getWidth();

	public abstract double getHeight();

	public double getMinX()
	{
		return getX();
	}

	public double getMinY()
	{
		return getY();
	}

	public double getMaxX()
	{
		return (getX() + getWidth());
	}

	public double getMaxY()
	{
		return (getY() + getHeight());
	}

	public double getCenterX()
	{
		return (getX() + getWidth() / 2.0D);
	}

	public double getCenterY()
	{
		return (getY() + getHeight() / 2.0D);
	}

	// @Transient
	public Rectangle2D getFrame()
	{
		return new Rectangle2D.Double(getX(), getY(), getWidth(), getHeight());
	}

	public abstract boolean isEmpty();

	public abstract void setFrame(double paramDouble1, double paramDouble2, double paramDouble3, double paramDouble4);

	public void setFrame(Point2D paramPoint2D, Dimension2D paramDimension2D)
	{
		setFrame(paramPoint2D.getX(), paramPoint2D.getY(), paramDimension2D.getWidth(), paramDimension2D.getHeight());
	}

	public void setFrame(Rectangle2D paramRectangle2D)
	{
		setFrame(paramRectangle2D.getX(), paramRectangle2D.getY(), paramRectangle2D.getWidth(), paramRectangle2D.getHeight());
	}

	public void setFrameFromDiagonal(double paramDouble1, double paramDouble2, double paramDouble3, double paramDouble4)
	{
		double d;
		if (paramDouble3 < paramDouble1) {
			d = paramDouble1;
			paramDouble1 = paramDouble3;
			paramDouble3 = d;
		}
		if (paramDouble4 < paramDouble2) {
			d = paramDouble2;
			paramDouble2 = paramDouble4;
			paramDouble4 = d;
		}
		setFrame(paramDouble1, paramDouble2, paramDouble3 - paramDouble1, paramDouble4 - paramDouble2);
	}

	public void setFrameFromDiagonal(Point2D paramPoint2D1, Point2D paramPoint2D2)
	{
		setFrameFromDiagonal(paramPoint2D1.getX(), paramPoint2D1.getY(), paramPoint2D2.getX(), paramPoint2D2.getY());
	}

	public void setFrameFromCenter(double paramDouble1, double paramDouble2, double paramDouble3, double paramDouble4)
	{
		double d1 = Math.abs(paramDouble3 - paramDouble1);
		double d2 = Math.abs(paramDouble4 - paramDouble2);
		setFrame(paramDouble1 - d1, paramDouble2 - d2, d1 * 2.0D, d2 * 2.0D);
	}

	public void setFrameFromCenter(Point2D paramPoint2D1, Point2D paramPoint2D2)
	{
		setFrameFromCenter(paramPoint2D1.getX(), paramPoint2D1.getY(), paramPoint2D2.getX(), paramPoint2D2.getY());
	}

	public boolean contains(Point2D paramPoint2D)
	{
		return contains(paramPoint2D.getX(), paramPoint2D.getY());
	}

	public boolean intersects(Rectangle2D paramRectangle2D)
	{
		return intersects(paramRectangle2D.getX(), paramRectangle2D.getY(), paramRectangle2D.getWidth(), paramRectangle2D.getHeight());
	}

	public boolean contains(Rectangle2D paramRectangle2D)
	{
		return contains(paramRectangle2D.getX(), paramRectangle2D.getY(), paramRectangle2D.getWidth(), paramRectangle2D.getHeight());
	}

	public Rectangle getBounds()
	{
		double d1 = getWidth();
		double d2 = getHeight();
		if ((d1 < 0.0D) || (d2 < 0.0D))
			return new Rectangle();
		double d3 = getX();
		double d4 = getY();
		double d5 = Math.floor(d3);
		double d6 = Math.floor(d4);
		double d7 = Math.ceil(d3 + d1);
		double d8 = Math.ceil(d4 + d2);
		return new Rectangle((int) d5, (int) d6, (int) (d7 - d5), (int) (d8 - d6));
	}

	public PathIterator getPathIterator(AffineTransform paramAffineTransform, double paramDouble)
	{
		return new FlatteningPathIterator(getPathIterator(paramAffineTransform), paramDouble);
	}

	public Object clone()
	{
		try {
			return super.clone();
		}
		catch (CloneNotSupportedException localCloneNotSupportedException) {
			throw new InternalError();
		}
	}
}
