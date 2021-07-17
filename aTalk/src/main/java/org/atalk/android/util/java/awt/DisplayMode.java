package org.atalk.android.util.java.awt;

public final class DisplayMode
{
	private Dimension size;
	private int bitDepth;
	private int refreshRate;
	public static final int BIT_DEPTH_MULTI = -1;
	public static final int REFRESH_RATE_UNKNOWN = 0;

	public DisplayMode(int paramInt1, int paramInt2, int paramInt3, int paramInt4) {
		this.size = new Dimension(paramInt1, paramInt2);
		this.bitDepth = paramInt3;
		this.refreshRate = paramInt4;
	}

	public int getHeight()
	{
		return this.size.height;
	}

	public int getWidth()
	{
		return this.size.width;
	}

	public int getBitDepth()
	{
		return this.bitDepth;
	}

	public int getRefreshRate()
	{
		return this.refreshRate;
	}

	public boolean equals(DisplayMode paramDisplayMode)
	{
		if (paramDisplayMode == null)
			return false;
		return ((getHeight() == paramDisplayMode.getHeight()) && (getWidth() == paramDisplayMode.getWidth())
			&& (getBitDepth() == paramDisplayMode.getBitDepth()) && (getRefreshRate() == paramDisplayMode.getRefreshRate()));
	}

	public boolean equals(Object paramObject)
	{
		if (paramObject instanceof DisplayMode)
			return equals((DisplayMode) paramObject);
		return false;
	}

	public int hashCode()
	{
		return (getWidth() + getHeight() + getBitDepth() * 7 + getRefreshRate() * 13);
	}
}