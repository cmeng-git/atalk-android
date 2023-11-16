package javax.swing;

import java.awt.Font;
import java.util.Hashtable;

public class UIDefaults extends Hashtable<Object, Object> {
	private static final Object PENDING = new String("Pending");

	
	public Font getFont(Object paramObject) {
		Object localObject = get(paramObject);
		return ((localObject instanceof Font) ? (Font) localObject : null);
	}

}
