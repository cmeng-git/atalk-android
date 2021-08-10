package org.atalk.android.util.javax.swing.event;

import java.io.Serializable;
import java.util.EventListener;

import org.atalk.android.util.java.awt.event.ActionListener;

public class EventListenerList implements Serializable {
	private static final Object[] NULL_ARRAY = new Object[0];
	protected transient Object[] listenerList = NULL_ARRAY;
	
	
	public synchronized <T extends EventListener> void add(Class<T> paramClass,
			T paramT) {
		if (paramT == null)
			return;
		if (!(paramClass.isInstance(paramT)))
			throw new IllegalArgumentException("Listener " + paramT
					+ " is not of type " + paramClass);
		if (this.listenerList == NULL_ARRAY) {
			this.listenerList = new Object[] { paramClass, paramT };
		} else {
			int i = this.listenerList.length;
			Object[] arrayOfObject = new Object[i + 2];
			System.arraycopy(this.listenerList, 0, arrayOfObject, 0, i);
			arrayOfObject[i] = paramClass;
			arrayOfObject[(i + 1)] = paramT;
			this.listenerList = arrayOfObject;
		}
	}


	public void remove(Class<ActionListener> class1,
			ActionListener paramActionListener) {
		// TODO Auto-generated method stub
		
	}

	public Object[] getListenerList() {
		// TODO Auto-generated method stub
		return null;
	}
}
