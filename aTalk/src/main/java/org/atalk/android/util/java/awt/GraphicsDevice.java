package org.atalk.android.util.java.awt;

import org.atalk.impl.neomedia.device.ScreenDeviceImpl;

public abstract class GraphicsDevice
{
	private Window fullScreenWindow;
	private final Object fsAppContextLock = new Object();
	private Rectangle windowedModeBounds;
	public static final int TYPE_RASTER_SCREEN = 0;
	public static final int TYPE_PRINTER = 1;
	public static final int TYPE_IMAGE_BUFFER = 2;

	public abstract int getType();
	public abstract String getIDstring();

	public boolean isFullScreenSupported()
	{
		return false;
	}
	public ScreenDeviceImpl getDefaultConfiguration()
	{
		// TODO Auto-generated method stub
		return null;
	}
	public DisplayMode getDisplayMode()
	{
		// TODO Auto-generated method stub
		return null;
	}

}
