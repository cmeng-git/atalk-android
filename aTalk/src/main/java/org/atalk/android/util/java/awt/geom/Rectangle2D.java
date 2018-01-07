package org.atalk.android.util.java.awt.geom;

/*** Eclipse Class Decompiler plugin, copyright (c) 2012 Chao Chen (cnfree2000@hotmail.com) ***/
import java.io.Serializable;

public abstract class Rectangle2D extends RectangularShape
{
	public static final int OUT_LEFT = 1;
	public static final int OUT_TOP = 2;
	public static final int OUT_RIGHT = 4;
	public static final int OUT_BOTTOM = 8;

	public abstract void setRect(double paramDouble1, double paramDouble2, double paramDouble3, double paramDouble4);

	public void setRect(Rectangle2D paramRectangle2D)
	{
		setRect(paramRectangle2D.getX(), paramRectangle2D.getY(), paramRectangle2D.getWidth(), paramRectangle2D.getHeight());
	}

	public boolean intersectsLine(double paramDouble1, double paramDouble2, double paramDouble3, double paramDouble4)
	{
		int j, i;
		double d;
		
		if ((j = outcode(paramDouble3, paramDouble4)) == 0)
			return true;
		while ((i = outcode(paramDouble1, paramDouble2)) != 0) {
			if ((i & j) != 0)
				return false;
			if ((i & 0x5) != 0) {
				d = getX();
				if ((i & 0x4) != 0)
					d += getWidth();
				paramDouble2 += (d - paramDouble1) * (paramDouble4 - paramDouble2) / (paramDouble3 - paramDouble1);
				paramDouble1 = d;
			}
			d = getY();
			if ((i & 0x8) != 0)
				d += getHeight();
			paramDouble1 += (d - paramDouble2) * (paramDouble3 - paramDouble1) / (paramDouble4 - paramDouble2);
			paramDouble2 = d;
		}
		return true;
	}

	public boolean intersectsLine(Line2D paramLine2D)
	{
		return intersectsLine(paramLine2D.getX1(), paramLine2D.getY1(), paramLine2D.getX2(), paramLine2D.getY2());
	}

	public abstract int outcode(double paramDouble1, double paramDouble2);

	public int outcode(Point2D paramPoint2D)
	{
		return outcode(paramPoint2D.getX(), paramPoint2D.getY());
	}

	public void setFrame(double paramDouble1, double paramDouble2, double paramDouble3, double paramDouble4)
	{
		setRect(paramDouble1, paramDouble2, paramDouble3, paramDouble4);
	}

	public Rectangle2D getBounds2D()
	{
		return ((Rectangle2D) clone());
	}

	public boolean contains(double paramDouble1, double paramDouble2)
	{
		double d1 = getX();
		double d2 = getY();
		return ((paramDouble1 >= d1) && (paramDouble2 >= d2) && (paramDouble1 < d1 + getWidth()) && (paramDouble2 < d2 + getHeight()));
	}

	public boolean intersects(double paramDouble1, double paramDouble2, double paramDouble3, double paramDouble4)
	{
		if ((isEmpty()) || (paramDouble3 <= 0.0D) || (paramDouble4 <= 0.0D))
			return false;
		double d1 = getX();
		double d2 = getY();
		return ((paramDouble1 + paramDouble3 > d1) && (paramDouble2 + paramDouble4 > d2) && (paramDouble1 < d1 + getWidth()) && (paramDouble2 < d2
			+ getHeight()));
	}

	public boolean contains(double paramDouble1, double paramDouble2, double paramDouble3, double paramDouble4)
	{
		if ((isEmpty()) || (paramDouble3 <= 0.0D) || (paramDouble4 <= 0.0D))
			return false;
		double d1 = getX();
		double d2 = getY();
		return ((paramDouble1 >= d1) && (paramDouble2 >= d2) && (paramDouble1 + paramDouble3 <= d1 + getWidth()) && (paramDouble2 + paramDouble4 <= d2
			+ getHeight()));
	}

	public abstract Rectangle2D createIntersection(Rectangle2D paramRectangle2D);

	public static void intersect(Rectangle2D paramRectangle2D1, Rectangle2D paramRectangle2D2, Rectangle2D paramRectangle2D3)
	{
		double d1 = Math.max(paramRectangle2D1.getMinX(), paramRectangle2D2.getMinX());
		double d2 = Math.max(paramRectangle2D1.getMinY(), paramRectangle2D2.getMinY());
		double d3 = Math.min(paramRectangle2D1.getMaxX(), paramRectangle2D2.getMaxX());
		double d4 = Math.min(paramRectangle2D1.getMaxY(), paramRectangle2D2.getMaxY());
		paramRectangle2D3.setFrame(d1, d2, d3 - d1, d4 - d2);
	}

	public abstract Rectangle2D createUnion(Rectangle2D paramRectangle2D);

	public static void union(Rectangle2D paramRectangle2D1, Rectangle2D paramRectangle2D2, Rectangle2D paramRectangle2D3)
	{
		double d1 = Math.min(paramRectangle2D1.getMinX(), paramRectangle2D2.getMinX());
		double d2 = Math.min(paramRectangle2D1.getMinY(), paramRectangle2D2.getMinY());
		double d3 = Math.max(paramRectangle2D1.getMaxX(), paramRectangle2D2.getMaxX());
		double d4 = Math.max(paramRectangle2D1.getMaxY(), paramRectangle2D2.getMaxY());
		paramRectangle2D3.setFrameFromDiagonal(d1, d2, d3, d4);
	}

	public void add(double paramDouble1, double paramDouble2)
	{
		double d1 = Math.min(getMinX(), paramDouble1);
		double d2 = Math.max(getMaxX(), paramDouble1);
		double d3 = Math.min(getMinY(), paramDouble2);
		double d4 = Math.max(getMaxY(), paramDouble2);
		setRect(d1, d3, d2 - d1, d4 - d3);
	}

	public void add(Point2D paramPoint2D)
	{
		add(paramPoint2D.getX(), paramPoint2D.getY());
	}

	public void add(Rectangle2D paramRectangle2D)
	{
		double d1 = Math.min(getMinX(), paramRectangle2D.getMinX());
		double d2 = Math.max(getMaxX(), paramRectangle2D.getMaxX());
		double d3 = Math.min(getMinY(), paramRectangle2D.getMinY());
		double d4 = Math.max(getMaxY(), paramRectangle2D.getMaxY());
		setRect(d1, d3, d2 - d1, d4 - d3);
	}

	public PathIterator getPathIterator(AffineTransform paramAffineTransform)
	{
		return new RectIterator(this, paramAffineTransform);
	}

	public PathIterator getPathIterator(AffineTransform paramAffineTransform, double paramDouble)
	{
		return new RectIterator(this, paramAffineTransform);
	}

	public int hashCode()
	{
		long l = java.lang.Double.doubleToLongBits(getX());
		l += java.lang.Double.doubleToLongBits(getY()) * 37L;
		l += java.lang.Double.doubleToLongBits(getWidth()) * 43L;
		l += java.lang.Double.doubleToLongBits(getHeight()) * 47L;
		return ((int) l ^ (int) (l >> 32));
	}

	public boolean equals(Object paramObject)
	{
		if (paramObject == this)
			return true;
		if (paramObject instanceof Rectangle2D) {
			Rectangle2D localRectangle2D = (Rectangle2D) paramObject;
			return ((getX() == localRectangle2D.getX()) && (getY() == localRectangle2D.getY()) && (getWidth() == localRectangle2D.getWidth()) && (getHeight() == localRectangle2D
				.getHeight()));
		}
		return false;
	}

	public static class Double extends Rectangle2D implements Serializable
	{
		public double x;
		public double y;
		public double width;
		public double height;
		private static final long serialVersionUID = 7771313791441850493L;

		public Double() {
		}

		public Double(double paramDouble1, double paramDouble2, double paramDouble3, double paramDouble4) {
			setRect(paramDouble1, paramDouble2, paramDouble3, paramDouble4);
		}

		public double getX()
		{
			return this.x;
		}

		public double getY()
		{
			return this.y;
		}

		public double getWidth()
		{
			return this.width;
		}

		public double getHeight()
		{
			return this.height;
		}

		public boolean isEmpty()
		{
			return ((this.width <= 0.0D) || (this.height <= 0.0D));
		}

		public void setRect(double paramDouble1, double paramDouble2, double paramDouble3, double paramDouble4)
		{
			this.x = paramDouble1;
			this.y = paramDouble2;
			this.width = paramDouble3;
			this.height = paramDouble4;
		}

		public void setRect(Rectangle2D paramRectangle2D)
		{
			this.x = paramRectangle2D.getX();
			this.y = paramRectangle2D.getY();
			this.width = paramRectangle2D.getWidth();
			this.height = paramRectangle2D.getHeight();
		}

		public int outcode(double paramDouble1, double paramDouble2)
		{
			int i = 0;
			if (this.width <= 0.0D)
				i |= 5;
			else if (paramDouble1 < this.x)
				i |= 1;
			else if (paramDouble1 > this.x + this.width)
				i |= 4;
			if (this.height <= 0.0D)
				i |= 10;
			else if (paramDouble2 < this.y)
				i |= 2;
			else if (paramDouble2 > this.y + this.height)
				i |= 8;
			return i;
		}

		public Rectangle2D getBounds2D()
		{
			return new Double(this.x, this.y, this.width, this.height);
		}

		public Rectangle2D createIntersection(Rectangle2D paramRectangle2D)
		{
			Double localDouble = new Double();
			Rectangle2D.intersect(this, paramRectangle2D, localDouble);
			return localDouble;
		}

		public Rectangle2D createUnion(Rectangle2D paramRectangle2D)
		{
			Double localDouble = new Double();
			Rectangle2D.union(this, paramRectangle2D, localDouble);
			return localDouble;
		}

		public String toString()
		{
			return super.getClass().getName() + "[x=" + this.x + ",y=" + this.y + ",w=" + this.width + ",h=" + this.height + "]";
		}
	}

	public static class Float extends Rectangle2D implements Serializable
	{
		public float x;
		public float y;
		public float width;
		public float height;
		private static final long serialVersionUID = 3798716824173675777L;

		public Float() {
		}

		public Float(float paramFloat1, float paramFloat2, float paramFloat3, float paramFloat4) {
			setRect(paramFloat1, paramFloat2, paramFloat3, paramFloat4);
		}

		public double getX()
		{
			return this.x;
		}

		public double getY()
		{
			return this.y;
		}

		public double getWidth()
		{
			return this.width;
		}

		public double getHeight()
		{
			return this.height;
		}

		public boolean isEmpty()
		{
			return ((this.width <= 0.0F) || (this.height <= 0.0F));
		}

		public void setRect(float paramFloat1, float paramFloat2, float paramFloat3, float paramFloat4)
		{
			this.x = paramFloat1;
			this.y = paramFloat2;
			this.width = paramFloat3;
			this.height = paramFloat4;
		}

		public void setRect(double paramDouble1, double paramDouble2, double paramDouble3, double paramDouble4)
		{
			this.x = (float) paramDouble1;
			this.y = (float) paramDouble2;
			this.width = (float) paramDouble3;
			this.height = (float) paramDouble4;
		}

		public void setRect(Rectangle2D paramRectangle2D)
		{
			this.x = (float) paramRectangle2D.getX();
			this.y = (float) paramRectangle2D.getY();
			this.width = (float) paramRectangle2D.getWidth();
			this.height = (float) paramRectangle2D.getHeight();
		}

		public int outcode(double paramDouble1, double paramDouble2)
		{
			int i = 0;
			if (this.width <= 0.0F)
				i |= 5;
			else if (paramDouble1 < this.x)
				i |= 1;
			else if (paramDouble1 > this.x + this.width)
				i |= 4;
			if (this.height <= 0.0F)
				i |= 10;
			else if (paramDouble2 < this.y)
				i |= 2;
			else if (paramDouble2 > this.y + this.height)
				i |= 8;
			return i;
		}

		public Rectangle2D getBounds2D()
		{
			return new Float(this.x, this.y, this.width, this.height);
		}

		public Rectangle2D createIntersection(Rectangle2D paramRectangle2D)
		{
			Object localObject;
			if (paramRectangle2D instanceof Float)
				localObject = new Float();
			else
				localObject = new Rectangle2D.Double();
			Rectangle2D.intersect(this, paramRectangle2D, (Rectangle2D) localObject);
			return ((Rectangle2D) localObject);
		}

		public Rectangle2D createUnion(Rectangle2D paramRectangle2D)
		{
			Object localObject;
			if (paramRectangle2D instanceof Float)
				localObject = new Float();
			else
				localObject = new Rectangle2D.Double();
			Rectangle2D.union(this, paramRectangle2D, (Rectangle2D) localObject);
			return ((Rectangle2D) localObject);
		}

		public String toString()
		{
			return super.getClass().getName() + "[x=" + this.x + ",y=" + this.y + ",w=" + this.width + ",h=" + this.height + "]";
		}
	}
}