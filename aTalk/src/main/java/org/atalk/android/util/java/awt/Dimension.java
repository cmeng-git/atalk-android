package org.atalk.android.util.java.awt;

import java.io.Serializable;

import org.atalk.android.util.java.awt.geom.Dimension2D;

public class Dimension extends Dimension2D implements Serializable
{
	public int width;
	public int height;
	private static final long serialVersionUID = 4723952579491349524L;

	private static native void initIDs();

	public Dimension() {
		this(0, 0);
	}

	public Dimension(Dimension paramDimension) {
		this(paramDimension.width, paramDimension.height);
	}

	public Dimension(int paramInt1, int paramInt2) {
		this.width = paramInt1;
		this.height = paramInt2;
	}

	public double getWidth()
	{
		return this.width;
	}

	public double getHeight()
	{
		return this.height;
	}

	public void setSize(double paramDouble1, double paramDouble2)
	{
		this.width = (int) Math.ceil(paramDouble1);
		this.height = (int) Math.ceil(paramDouble2);
	}

	// @Transient
	public Dimension getSize()
	{
		return new Dimension(this.width, this.height);
	}

	public void setSize(Dimension paramDimension)
	{
		setSize(paramDimension.width, paramDimension.height);
	}

	public void setSize(int paramInt1, int paramInt2)
	{
		this.width = paramInt1;
		this.height = paramInt2;
	}

	public boolean equals(Object paramObject)
	{
		if (paramObject instanceof Dimension) {
			Dimension localDimension = (Dimension) paramObject;
			return ((this.width == localDimension.width) && (this.height == localDimension.height));
		}
		return false;
	}

	public int hashCode()
	{
		int i = this.width + this.height;
		return (i * (i + 1) / 2 + this.width);
	}

	public String toString()
	{
		return "[width=" + this.width + ", height=" + this.height + "]";
	}

	static {
		Toolkit.loadLibraries();
//		if (GraphicsEnvironment.isHeadless())
//			return;
		// initIDs();
	}
}


