package org.atalk.android.util.javax.swing;

import java.io.Serializable;

import org.atalk.android.util.javax.swing.plaf.LabelUI;

public class UIManager implements Serializable {
	private static final Object classLock = new Object();
	private static final String defaultLAFKey = "swing.defaultlaf";
	private static final String auxiliaryLAFsKey = "swing.auxiliarylaf";
	private static final String multiplexingLAFKey = "swing.plaf.multiplexinglaf";
	private static final String installedLAFsKey = "swing.installedlafs";
	private static final String disableMnemonicKey = "swing.disablenavaids";
	
	public static UIDefaults getDefaults() {
		// TODO Auto-generated method stub
		return null;
	}

	public static LabelUI getUI(JLabel jLabel)
	{
		// TODO Auto-generated method stub
		return null;
	}

	public static Object getLookAndFeel()
	{
		// TODO Auto-generated method stub
		return null;
	}
	
}
