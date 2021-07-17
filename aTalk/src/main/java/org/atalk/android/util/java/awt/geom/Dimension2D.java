package org.atalk.android.util.java.awt.geom;

public abstract class Dimension2D implements Cloneable
{
	public abstract double getWidth();

	public abstract double getHeight();

	public abstract void setSize(double paramDouble1, double paramDouble2);

	public void setSize(Dimension2D paramDimension2D)
	{
		setSize(paramDimension2D.getWidth(), paramDimension2D.getHeight());
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
