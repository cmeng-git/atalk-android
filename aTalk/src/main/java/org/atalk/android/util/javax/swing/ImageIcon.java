package org.atalk.android.util.javax.swing;

import java.net.URL;

import org.atalk.android.util.java.awt.Component;
import org.atalk.android.util.java.awt.Graphics;
import org.atalk.android.util.java.awt.Image;

public class ImageIcon implements Icon
{
	int width;
	int height;

	public ImageIcon(URL location) {}

	public ImageIcon(Image read) {
		// TODO Auto-generated constructor stub
	}
	
	public Image getImage()
	{
		return null;
	}

	public void paintIcon(Component c, Graphics g, int x, int y) {}

	public int getIconWidth()
	{
		return this.width;
	}

	public int getIconHeight()
	{
		return this.height;
	}
}

/* Location:           D:\workspace\Android\soTalk\sotalk\libs\java-stubs.jar
 * Qualified Name:     ImageIcon
 * JD-Core Version:    0.7.0.1
 */