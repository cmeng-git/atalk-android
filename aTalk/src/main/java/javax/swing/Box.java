package javax.swing;

import java.awt.Component;
import java.awt.Dimension;

public class Box extends JComponent implements Accessible {
	
	public Box(int paramInt) {
		super.setLayout(new BoxLayout(this, paramInt));
	}

	public static class Filler extends JComponent implements Accessible {
		public Filler(Dimension paramDimension1, Dimension paramDimension2,
				Dimension paramDimension3) {
			setMinimumSize(paramDimension1);
			setPreferredSize(paramDimension2);
			setMaximumSize(paramDimension3);
		}
	}

	public static Component createVerticalStrut(int paramInt) {
		return new Filler(new Dimension(0, paramInt),
				new Dimension(0, paramInt), new Dimension(32767, paramInt));
	}
	
	public static Component createRigidArea(Dimension dimension) {
		// TODO Auto-generated method stub
		return null;
	}

}
