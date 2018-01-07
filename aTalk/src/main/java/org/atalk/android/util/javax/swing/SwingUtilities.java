package org.atalk.android.util.javax.swing;

import org.atalk.android.util.java.awt.Window;
import org.atalk.android.util.java.awt.Image;

public class SwingUtilities
{
	public static boolean isEventDispatchThread()
	{
		return true;
	}

	public static void invokeLater(Runnable doRun)
	{
		new Thread(doRun).start();
	}

	public static Window windowForComponent(JComponent container)
	{
		// TODO Auto-generated method stub
		return null;
	}

	public static int findDisplayedMnemonicIndex(String paramString, int displayedMnemonic)
	{
		// TODO Auto-generated method stub
		return 0;
	}

	public static boolean doesIconReferenceImage(Icon icon, Image paramImage)
	{
		// TODO Auto-generated method stub
		return false;
	}
}

/*
 * Location: D:\workspace\Android\soTalk\sotalk\libs\java-stubs.jar
 * 
 * Qualified Name: SwingUtilities
 * 
 * JD-Core Version: 0.7.0.1
 */