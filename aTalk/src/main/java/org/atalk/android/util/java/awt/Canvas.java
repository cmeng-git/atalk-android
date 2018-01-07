package org.atalk.android.util.java.awt;

import org.atalk.android.util.javax.swing.Accessible;

public class Canvas extends Component implements Accessible
{
	private static final String base = "canvas";
	private static int nameCounter = 0;
	private static final long serialVersionUID = -2284879212465893870L;

	public Canvas() {
	}


	String constructComponentName()
	{
		synchronized (Canvas.class) {
			return "canvas" + (nameCounter++);
		}
	}

	public void addNotify()
	{
		synchronized (getTreeLock()) {
			super.addNotify();
		}
	}

	public void paint(Graphics paramGraphics)
	{
	}

	public void update(Graphics paramGraphics)
	{
		paint(paramGraphics);
	}

	boolean postsOldMouseEvents()
	{
		return true;
	}

	public void createBufferStrategy(int paramInt)
	{
	}


	public void removeNotify()
	{
		// TODO Auto-generated method stub
		
	}
}
