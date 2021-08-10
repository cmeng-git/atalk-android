package org.atalk.android.util.java.awt;

import org.atalk.android.util.java.awt.image.ImageObserver;

public abstract class Graphics2D extends Graphics
{
	public abstract void setColor(Color paramColor);
  
	public abstract void fillRect(int paramInt1, int paramInt2, int paramInt3, int paramInt4);
  
	public abstract boolean drawImage(Image paramImage, int paramInt1, int paramInt2, int paramInt3, int paramInt4, int paramInt5, int paramInt6, int paramInt7, int paramInt8, ImageObserver paramImageObserver);
  
	public abstract boolean drawImage(Image paramImage, int paramInt1, int paramInt2, Color paramColor, ImageObserver paramImageObserver);
  
	public abstract boolean drawImage(Image paramImage, int paramInt1, int paramInt2, ImageObserver paramImageObserver);
  
	public abstract boolean drawImage(Image paramImage, int paramInt1, int paramInt2, int paramInt3, int paramInt4, ImageObserver paramImageObserver);

	public void setRenderingHint(String keyAntialiasing, String valueAntialiasOn) {
		// TODO Auto-generated method stub
		
	}

	public void setComposite(Object instance) {
		// TODO Auto-generated method stub
		
	}
}

