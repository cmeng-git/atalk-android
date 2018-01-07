package org.atalk.android.util.java.awt.geom;

import java.util.NoSuchElementException;

class RectIterator implements PathIterator
{
	double x;
	double y;
	double w;
	double h;
	AffineTransform affine;
	int index;

	RectIterator(Rectangle2D paramRectangle2D, AffineTransform paramAffineTransform) {
		this.x = paramRectangle2D.getX();
		this.y = paramRectangle2D.getY();
		this.w = paramRectangle2D.getWidth();
		this.h = paramRectangle2D.getHeight();
		this.affine = paramAffineTransform;
		if ((this.w >= 0.0D) && (this.h >= 0.0D))
			return;
		this.index = 6;
	}

	public int getWindingRule()
	{
		return 1;
	}

	public boolean isDone()
	{
		return (this.index > 5);
	}

	public void next()
	{
		this.index += 1;
	}

	public int currentSegment(float[] paramArrayOfFloat)
	{
		if (isDone())
			throw new NoSuchElementException("rect iterator out of bounds");
		if (this.index == 5)
			return 4;
		paramArrayOfFloat[0] = (float) this.x;
		paramArrayOfFloat[1] = (float) this.y;
		if ((this.index == 1) || (this.index == 2))
			paramArrayOfFloat[0] += (float) this.w;
		if ((this.index == 2) || (this.index == 3))
			paramArrayOfFloat[1] += (float) this.h;
		if (this.affine != null)
			this.affine.transform(paramArrayOfFloat, 0, paramArrayOfFloat, 0, 1);
		return ((this.index == 0) ? 0 : 1);
	}

	public int currentSegment(double[] paramArrayOfDouble)
	{
		if (isDone())
			throw new NoSuchElementException("rect iterator out of bounds");
		if (this.index == 5)
			return 4;
		paramArrayOfDouble[0] = this.x;
		paramArrayOfDouble[1] = this.y;
		if ((this.index == 1) || (this.index == 2))
			paramArrayOfDouble[0] += this.w;
		if ((this.index == 2) || (this.index == 3))
			paramArrayOfDouble[1] += this.h;
		if (this.affine != null)
			this.affine.transform(paramArrayOfDouble, 0, paramArrayOfDouble, 0, 1);
		return ((this.index == 0) ? 0 : 1);
	}
}
