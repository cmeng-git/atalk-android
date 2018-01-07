package org.atalk.android.util.java.awt.geom;

import java.io.Serializable;

import org.atalk.android.util.java.awt.Rectangle;
import org.atalk.android.util.java.awt.Shape;

public abstract class Line2D implements Shape, Cloneable
{
	public abstract double getX1();

	public abstract double getY1();

	public abstract Point2D getP1();

	public abstract double getX2();

	public abstract double getY2();

	public abstract Point2D getP2();

	public abstract void setLine(double paramDouble1, double paramDouble2, double paramDouble3, double paramDouble4);

	public void setLine(Point2D paramPoint2D1, Point2D paramPoint2D2)
	{
		setLine(paramPoint2D1.getX(), paramPoint2D1.getY(), paramPoint2D2.getX(), paramPoint2D2.getY());
	}

	public void setLine(Line2D paramLine2D)
	{
		setLine(paramLine2D.getX1(), paramLine2D.getY1(), paramLine2D.getX2(), paramLine2D.getY2());
	}

	public static int relativeCCW(double paramDouble1, double paramDouble2, double paramDouble3, double paramDouble4, double paramDouble5, double paramDouble6)
	{
		paramDouble3 -= paramDouble1;
		paramDouble4 -= paramDouble2;
		paramDouble5 -= paramDouble1;
		paramDouble6 -= paramDouble2;
		double d = paramDouble5 * paramDouble4 - (paramDouble6 * paramDouble3);
		if (d == 0.0D) {
			d = paramDouble5 * paramDouble3 + paramDouble6 * paramDouble4;
			if (d > 0.0D) {
				paramDouble5 -= paramDouble3;
				paramDouble6 -= paramDouble4;
				d = paramDouble5 * paramDouble3 + paramDouble6 * paramDouble4;
				if (d < 0.0D)
					d = 0.0D;
			}
		}
		return ((d > 0.0D) ? 1 : (d < 0.0D) ? -1 : 0);
	}

	public int relativeCCW(double paramDouble1, double paramDouble2)
	{
		return relativeCCW(getX1(), getY1(), getX2(), getY2(), paramDouble1, paramDouble2);
	}

	public int relativeCCW(Point2D paramPoint2D)
	{
		return relativeCCW(getX1(), getY1(), getX2(), getY2(), paramPoint2D.getX(), paramPoint2D.getY());
	}

	public static boolean linesIntersect(double paramDouble1, double paramDouble2, double paramDouble3, double paramDouble4, double paramDouble5,
		double paramDouble6, double paramDouble7, double paramDouble8)
	{
		return ((relativeCCW(paramDouble1, paramDouble2, paramDouble3, paramDouble4, paramDouble5, paramDouble6)
			* relativeCCW(paramDouble1, paramDouble2, paramDouble3, paramDouble4, paramDouble7, paramDouble8) <= 0) && (relativeCCW(paramDouble5, paramDouble6,
			paramDouble7, paramDouble8, paramDouble1, paramDouble2)
			* relativeCCW(paramDouble5, paramDouble6, paramDouble7, paramDouble8, paramDouble3, paramDouble4) <= 0));
	}

	public boolean intersectsLine(double paramDouble1, double paramDouble2, double paramDouble3, double paramDouble4)
	{
		return linesIntersect(paramDouble1, paramDouble2, paramDouble3, paramDouble4, getX1(), getY1(), getX2(), getY2());
	}

	public boolean intersectsLine(Line2D paramLine2D)
	{
		return linesIntersect(paramLine2D.getX1(), paramLine2D.getY1(), paramLine2D.getX2(), paramLine2D.getY2(), getX1(), getY1(), getX2(), getY2());
	}

	public static double ptSegDistSq(double paramDouble1, double paramDouble2, double paramDouble3, double paramDouble4, double paramDouble5,
		double paramDouble6)
	{
		paramDouble3 -= paramDouble1;
		paramDouble4 -= paramDouble2;
		paramDouble5 -= paramDouble1;
		paramDouble6 -= paramDouble2;
		double d1 = paramDouble5 * paramDouble3 + paramDouble6 * paramDouble4;
		double d2;
		if (d1 <= 0.0D) {
			d2 = 0.0D;
		}
		else {
			paramDouble5 = paramDouble3 - paramDouble5;
			paramDouble6 = paramDouble4 - paramDouble6;
			d1 = paramDouble5 * paramDouble3 + paramDouble6 * paramDouble4;
			if (d1 <= 0.0D)
				d2 = 0.0D;
			else
				d2 = d1 * d1 / (paramDouble3 * paramDouble3 + paramDouble4 * paramDouble4);
		}
		double d3 = paramDouble5 * paramDouble5 + paramDouble6 * paramDouble6 - d2;
		if (d3 < 0.0D)
			d3 = 0.0D;
		return d3;
	}

	public static double ptSegDist(double paramDouble1, double paramDouble2, double paramDouble3, double paramDouble4, double paramDouble5, double paramDouble6)
	{
		return Math.sqrt(ptSegDistSq(paramDouble1, paramDouble2, paramDouble3, paramDouble4, paramDouble5, paramDouble6));
	}

	public double ptSegDistSq(double paramDouble1, double paramDouble2)
	{
		return ptSegDistSq(getX1(), getY1(), getX2(), getY2(), paramDouble1, paramDouble2);
	}

	public double ptSegDistSq(Point2D paramPoint2D)
	{
		return ptSegDistSq(getX1(), getY1(), getX2(), getY2(), paramPoint2D.getX(), paramPoint2D.getY());
	}

	public double ptSegDist(double paramDouble1, double paramDouble2)
	{
		return ptSegDist(getX1(), getY1(), getX2(), getY2(), paramDouble1, paramDouble2);
	}

	public double ptSegDist(Point2D paramPoint2D)
	{
		return ptSegDist(getX1(), getY1(), getX2(), getY2(), paramPoint2D.getX(), paramPoint2D.getY());
	}

	public static double ptLineDistSq(double paramDouble1, double paramDouble2, double paramDouble3, double paramDouble4, double paramDouble5,
		double paramDouble6)
	{
		paramDouble3 -= paramDouble1;
		paramDouble4 -= paramDouble2;
		paramDouble5 -= paramDouble1;
		paramDouble6 -= paramDouble2;
		double d1 = paramDouble5 * paramDouble3 + paramDouble6 * paramDouble4;
		double d2 = d1 * d1 / (paramDouble3 * paramDouble3 + paramDouble4 * paramDouble4);
		double d3 = paramDouble5 * paramDouble5 + paramDouble6 * paramDouble6 - d2;
		if (d3 < 0.0D)
			d3 = 0.0D;
		return d3;
	}

	public static double ptLineDist(double paramDouble1, double paramDouble2, double paramDouble3, double paramDouble4, double paramDouble5, double paramDouble6)
	{
		return Math.sqrt(ptLineDistSq(paramDouble1, paramDouble2, paramDouble3, paramDouble4, paramDouble5, paramDouble6));
	}

	public double ptLineDistSq(double paramDouble1, double paramDouble2)
	{
		return ptLineDistSq(getX1(), getY1(), getX2(), getY2(), paramDouble1, paramDouble2);
	}

	public double ptLineDistSq(Point2D paramPoint2D)
	{
		return ptLineDistSq(getX1(), getY1(), getX2(), getY2(), paramPoint2D.getX(), paramPoint2D.getY());
	}

	public double ptLineDist(double paramDouble1, double paramDouble2)
	{
		return ptLineDist(getX1(), getY1(), getX2(), getY2(), paramDouble1, paramDouble2);
	}

	public double ptLineDist(Point2D paramPoint2D)
	{
		return ptLineDist(getX1(), getY1(), getX2(), getY2(), paramPoint2D.getX(), paramPoint2D.getY());
	}

	public boolean contains(double paramDouble1, double paramDouble2)
	{
		return false;
	}

	public boolean contains(Point2D paramPoint2D)
	{
		return false;
	}

	public boolean intersects(double paramDouble1, double paramDouble2, double paramDouble3, double paramDouble4)
	{
		return intersects(new Rectangle2D.Double(paramDouble1, paramDouble2, paramDouble3, paramDouble4));
	}

	public boolean intersects(Rectangle2D paramRectangle2D)
	{
		return paramRectangle2D.intersectsLine(getX1(), getY1(), getX2(), getY2());
	}

	public boolean contains(double paramDouble1, double paramDouble2, double paramDouble3, double paramDouble4)
	{
		return false;
	}

	public boolean contains(Rectangle2D paramRectangle2D)
	{
		return false;
	}

	public Rectangle getBounds()
	{
		return getBounds2D().getBounds();
	}

	public PathIterator getPathIterator(AffineTransform paramAffineTransform)
	{
		return new LineIterator(this, paramAffineTransform);
	}

	public PathIterator getPathIterator(AffineTransform paramAffineTransform, double paramDouble)
	{
		return new LineIterator(this, paramAffineTransform);
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

	public static class Double extends Line2D implements Serializable
	{
		public double x1;
		public double y1;
		public double x2;
		public double y2;
		private static final long serialVersionUID = 7979627399746467499L;

		public Double() {
		}

		public Double(double paramDouble1, double paramDouble2, double paramDouble3, double paramDouble4) {
			setLine(paramDouble1, paramDouble2, paramDouble3, paramDouble4);
		}

		public Double(Point2D paramPoint2D1, Point2D paramPoint2D2) {
			setLine(paramPoint2D1, paramPoint2D2);
		}

		public double getX1()
		{
			return this.x1;
		}

		public double getY1()
		{
			return this.y1;
		}

		public Point2D getP1()
		{
			return new Point2D.Double(this.x1, this.y1);
		}

		public double getX2()
		{
			return this.x2;
		}

		public double getY2()
		{
			return this.y2;
		}

		public Point2D getP2()
		{
			return new Point2D.Double(this.x2, this.y2);
		}

		public void setLine(double paramDouble1, double paramDouble2, double paramDouble3, double paramDouble4)
		{
			this.x1 = paramDouble1;
			this.y1 = paramDouble2;
			this.x2 = paramDouble3;
			this.y2 = paramDouble4;
		}

		public Rectangle2D getBounds2D()
		{
			double d1;
			double d3;
			if (this.x1 < this.x2) {
				d1 = this.x1;
				d3 = this.x2 - this.x1;
			}
			else {
				d1 = this.x2;
				d3 = this.x1 - this.x2;
			}
			double d2;
			double d4;
			if (this.y1 < this.y2) {
				d2 = this.y1;
				d4 = this.y2 - this.y1;
			}
			else {
				d2 = this.y2;
				d4 = this.y1 - this.y2;
			}
			return new Rectangle2D.Double(d1, d2, d3, d4);
		}
	}

	public static class Float extends Line2D implements Serializable
	{
		public float x1;
		public float y1;
		public float x2;
		public float y2;
		private static final long serialVersionUID = 6161772511649436349L;

		public Float() {
		}

		public Float(float paramFloat1, float paramFloat2, float paramFloat3, float paramFloat4) {
			setLine(paramFloat1, paramFloat2, paramFloat3, paramFloat4);
		}

		public Float(Point2D paramPoint2D1, Point2D paramPoint2D2) {
			setLine(paramPoint2D1, paramPoint2D2);
		}

		public double getX1()
		{
			return this.x1;
		}

		public double getY1()
		{
			return this.y1;
		}

		public Point2D getP1()
		{
			return new Point2D.Float(this.x1, this.y1);
		}

		public double getX2()
		{
			return this.x2;
		}

		public double getY2()
		{
			return this.y2;
		}

		public Point2D getP2()
		{
			return new Point2D.Float(this.x2, this.y2);
		}

		public void setLine(double paramDouble1, double paramDouble2, double paramDouble3, double paramDouble4)
		{
			this.x1 = (float) paramDouble1;
			this.y1 = (float) paramDouble2;
			this.x2 = (float) paramDouble3;
			this.y2 = (float) paramDouble4;
		}

		public void setLine(float paramFloat1, float paramFloat2, float paramFloat3, float paramFloat4)
		{
			this.x1 = paramFloat1;
			this.y1 = paramFloat2;
			this.x2 = paramFloat3;
			this.y2 = paramFloat4;
		}

		public Rectangle2D getBounds2D()
		{
			float f1;
			float f3;
			if (this.x1 < this.x2) {
				f1 = this.x1;
				f3 = this.x2 - this.x1;
			}
			else {
				f1 = this.x2;
				f3 = this.x1 - this.x2;
			}
			float f2;
			float f4;
			if (this.y1 < this.y2) {
				f2 = this.y1;
				f4 = this.y2 - this.y1;
			}
			else {
				f2 = this.y2;
				f4 = this.y1 - this.y2;
			}
			return new Rectangle2D.Float(f1, f2, f3, f4);
		}

		@Override
		public boolean intersects(Rectangle2D paramRectangle2D)
		{
			// TODO Auto-generated method stub
			return false;
		}

		@Override
		public boolean contains(Rectangle2D paramRectangle2D)
		{
			// TODO Auto-generated method stub
			return false;
		}
	}
}
