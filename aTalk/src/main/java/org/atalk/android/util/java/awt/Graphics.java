package org.atalk.android.util.java.awt;

import org.atalk.android.util.java.awt.image.ImageObserver;

public abstract class Graphics
{
	public abstract void setColor(Color paramColor);
	 
	public abstract void fillRect(int paramInt1, int paramInt2, int paramInt3, int paramInt4);
	 
	public abstract boolean drawImage(Image paramImage, int paramInt1, int paramInt2, int paramInt3, int paramInt4, int paramInt5, int paramInt6, int paramInt7, int paramInt8, ImageObserver paramImageObserver);
	  
	public abstract boolean drawImage(Image paramImage, int paramInt1, int paramInt2, Color paramColor, ImageObserver paramImageObserver);
	  
	public abstract boolean drawImage(Image paramImage, int paramInt1, int paramInt2, ImageObserver paramImageObserver);
	  
	public abstract boolean drawImage(Image paramImage, int paramInt1, int paramInt2, int paramInt3, int paramInt4, ImageObserver paramImageObserver);
	
	public void fillOval(int x, int y, int width, int height) {
		// TODO Auto-generated method stub
	}

	public void drawImage(Image img, int dstX, int dstY, int paramInt3, int paramInt4, int paramInt5, int paramInt6, int imgWidth, int imgHeight, Canvas canvas)
	{
		// TODO Auto-generated method stub
		
	}
}
