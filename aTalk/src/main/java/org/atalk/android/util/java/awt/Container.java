package org.atalk.android.util.java.awt;

import java.io.ObjectStreamField;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

import net.java.sip.communicator.plugin.desktoputil.SIPCommCheckBox;

import org.atalk.android.util.java.awt.event.ContainerListener;
import org.atalk.impl.neomedia.jmfext.media.renderer.video.JAWTRendererVideoComponent;

public class Container extends Component {
	private static final Component[] EMPTY_ARRAY = null;
	private List<Component> component = new ArrayList();
	LayoutManager layoutMgr;
	private boolean focusCycleRoot = false;
	private boolean focusTraversalPolicyProvider;
	private transient Set printingThreads;
	private transient boolean printing = false;
	transient ContainerListener containerListener;
	transient int listeningChildren;
	transient int listeningBoundsChildren;
	transient int descendantsCount;
	transient Color preserveBackgroundColor = null;
	
	private static final long serialVersionUID = 4613797578919906343L;
	static final boolean INCLUDE_SELF = true;
	static final boolean SEARCH_HEAVYWEIGHTS = true;
	private transient int numOfHWComponents = 0;
	private transient int numOfLWComponents = 0;
	private static final Logger mixingLog = null;
	private static final ObjectStreamField[] serialPersistentFields = null;
	transient Component modalComp;
	private int containerSerializedDataVersion = 1;

	private static native void initIDs();	

	public LayoutManager getLayout()
	{
		return null;
	}

	public void removeAll() {}
	public void remove(Component comp) {}
	public synchronized void addContainerListener(ContainerListener l) {}
	
	public boolean isValid()
	{
	  return true;
	}

	final void invalidateIfValid()
	{
	  if (!(isValid()))
	    return;
	  invalidate();
	}	
	
	private void invalidate() {
		// TODO Auto-generated method stub
		
	}
	
	public Object getComponent() {
		// TODO Auto-generated method stub
		return null;
	}

	public Component add(Component comp, int index)
	{
	    addImpl(comp, null, index);
	    return comp;
	}

	public void add(Component comp, Object constraints) {}
	
	public Component add(Component paramComponent)
	{
		addImpl(paramComponent, null, -1);
		return paramComponent;
	}
	

	  public Component add(String paramString, Component paramComponent)
	  {
	    addImpl(paramComponent, paramString, -1);
	    return paramComponent;
	  }

	private void addImpl(
			Component paramComponent,
			Object object, int i) {
		// TODO Auto-generated method stub
		
	}

	public void add(Component comp, Object constraints, int index) {}

	public void validate() {}

	public void doLayout() {}
	
	public void setLayout(LayoutManager paramLayoutManager)
	{
		this.layoutMgr = paramLayoutManager;
	  invalidateIfValid();
	}

	public int getComponentCount()
	{
		return 0;
	}

	public Component getComponent(int n)
	{
		return null;
	}

	public Component[] getComponents()
	{
		return new Component[0];
	}

	public static void add(SIPCommCheckBox cbEnable) {
		// TODO Auto-generated method stub
		
	}

	public Insets getInsets() {
		// TODO Auto-generated method stub
		return null;
	}

	public int getComponentZOrder(JAWTRendererVideoComponent jawtRendererVideoComponent)
	{
		// TODO Auto-generated method stub
		return 0;
	}
}
