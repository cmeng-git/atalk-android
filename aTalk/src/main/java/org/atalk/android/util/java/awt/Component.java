package org.atalk.android.util.java.awt;

import android.database.Cursor;
import android.graphics.Region;

import org.atalk.android.util.java.awt.event.*;

import java.beans.*;
import java.io.Serializable;
import java.security.*;
import java.util.*;

public class Component implements Serializable
{
	transient Container parent;
  private static final Dimension DEFAULT_MAX_SIZE
		  = new Dimension(Short.MAX_VALUE, Short.MAX_VALUE);
    /**
     * The x position of the component in the parent's coordinate system.
     */
    int x;

    /**
     * The y position of the component in the parent's coordinate system.
     */
    int y;

    /**
     * The width of the component.
     *
     * @serial
     * @see #getSize
     */
    int width;

    /**
     * The height of the component.
     *
     * @serial
     * @see #getSize
     */
    int height;
	
	int prefWidth;
	int prefHeight;
	
	
    /**
     * The foreground color for this component.
     * {@code foreground</code> can be <code>null}.
     *
     * @serial
     * @see #getForeground
     * @see #setForeground
     */
    Color       foreground;
	
    /**
     * The background color for this component.
     * {@code background</code> can be <code>null}.
     *
     * @serial
     * @see #getBackground
     * @see #setBackground
     */
    Color       background;

    /**
     * The font used by this component.
     * The {@code font</code> can be <code>null}.
     *
     * @serial
     * @see #getFont
     * @see #setFont
     */
    volatile Font font;

    /**
     * The font which the peer is currently using.
     * ({@code null} if no peer exists.)
     */
    Font        peerFont;

    /**
     * The cursor displayed when pointer is over this component.
     * This value can be {@code null}.
     *
     * @serial
     * @see #getCursor
     * @see #setCursor
     */
    Cursor cursor;

    /**
     * The locale for the component.
     *
     * @serial
     * @see #getLocale
     * @see #setLocale
     */
    Locale      locale;

	boolean ignoreRepaint = false;
	boolean visible = true;
	boolean enabled = true;
	boolean prefSizeIsSet = true;
	private volatile boolean valid = false;
	Vector popups;

    /**
     * A component's name.
     * This field can be {@code null}.
     *
     * @serial
     * @see #getName
     * @see #setName(String)
     */
    private String name;

    /**
     * A bool to determine whether the name has
     * been set explicitly. {@code nameExplicitlySet} will
     * be false if the name has not been set and
     * true if it has.
     *
     * @serial
     * @see #getName
     * @see #setName(String)
     */
    private boolean nameExplicitlySet = false;

    /**
     * Indicates whether this Component can be focused.
     */
    private boolean focusable = true;

    private static final int FOCUS_TRAVERSABLE_UNKNOWN = 0;
    private static final int FOCUS_TRAVERSABLE_DEFAULT = 1;
    private static final int FOCUS_TRAVERSABLE_SET = 2;

	private static final int DEFAULT_OPERATION = 3;

    /**
     * Tracks whether this Component is relying on default focus travesability.
     *
     * @serial
     * @since 1.4
     */
    private int isFocusTraversableOverridden = FOCUS_TRAVERSABLE_UNKNOWN;

    /**
     * The focus traversal keys. These keys will generate focus traversal
     * behavior for Components for which focus traversal keys are enabled. If a
     * value of null is specified for a traversal key, this Component inherits
     * that traversal key from its parent. If all ancestors of this Component
     * have null specified for that traversal key, then the current
     * KeyboardFocusManager's default traversal key is used.
     */
    Set[] focusTraversalKeys;

    private static final String[] focusTraversalKeyPropertyNames = {
        "forwardFocusTraversalKeys",
        "backwardFocusTraversalKeys",
        "upCycleFocusTraversalKeys",
        "downCycleFocusTraversalKeys"
    };

    /**
     * Indicates whether focus traversal keys are enabled for this Component.
     * Components for which focus traversal keys are disabled receive key
     * events for focus traversal keys. Components for which focus traversal
     * keys are enabled do not see these events; instead, the events are
     * automatically converted to traversal operations.
     */
    private boolean focusTraversalKeysEnabled = true;

    /**
     * The locking object for AWT component-tree and layout operations.
     *
     * @see #getTreeLock
     */
    static final Object LOCK = new AWTTreeLock();
    static class AWTTreeLock {}

    /*
     * The component's AccessControlContext.
     */
    private transient volatile AccessControlContext acc = AccessController.getContext();

    /**
     * Minimum size.
     * (This field perhaps should have been transient).
     *
     * @serial
     */
    Dimension minSize;

    /**
     * Whether or not setMinimumSize has been invoked with a non-null value.
     */
    boolean minSizeSet;

    /**
     * Preferred size.
     * (This field perhaps should have been transient).
     *
     * @serial
     */
    Dimension prefSize;

    /**
     * Whether or not setPreferredSize has been invoked with a non-null value.
     */
    boolean prefSizeSet;

    /**
     * Maximum size
     *
     * @serial
     */
    Dimension maxSize;

    /**
     * Whether or not setMaximumSize has been invoked with a non-null value.
     */
    boolean maxSizeSet;

    /**
     * {@code newEventsOnly} will be true if the event is
     * one of the event types enabled for the component.
     * It will then allow for normal processing to
     * continue.  If it is false the event is passed
     * to the component's parent and up the ancestor
     * tree until the event has been consumed.
     *
     * @serial
     * @see #dispatchEvent
     */
    boolean newEventsOnly = false;
    transient ComponentListener componentListener;
	transient HierarchyListener hierarchyListener;
	transient RuntimeException windowClosingException = null;

    /** Internal, constants for serialization */
    final static String actionListenerK = "actionL";
    final static String adjustmentListenerK = "adjustmentL";
    final static String componentListenerK = "componentL";
    final static String containerListenerK = "containerL";
    final static String focusListenerK = "focusL";
    final static String itemListenerK = "itemL";
    final static String keyListenerK = "keyL";
    final static String mouseListenerK = "mouseL";
    final static String mouseMotionListenerK = "mouseMotionL";
    final static String mouseWheelListenerK = "mouseWheelL";
    final static String textListenerK = "textL";
    final static String ownedWindowK = "ownedL";
    final static String windowListenerK = "windowL";
    final static String inputMethodListenerK = "inputMethodL";
    final static String hierarchyListenerK = "hierarchyL";
    final static String hierarchyBoundsListenerK = "hierarchyBoundsL";
    final static String windowStateListenerK = "windowStateL";
    final static String windowFocusListenerK = "windowFocusL";
	long eventMask = 4096L;

    /**
     * Static properties for incremental drawing.
     * @see #imageUpdate
     */
    static boolean isInc;
    static int incRate;

    /**
     * Ease-of-use constant for {@code getAlignmentY()}.
     * Specifies an alignment to the top of the component.
     * @see     #getAlignmentY
     */
    public static final float TOP_ALIGNMENT = 0.0f;

    /**
     * Ease-of-use constant for {@code getAlignmentY} and
     * {@code getAlignmentX}. Specifies an alignment to
     * the center of the component
     * @see     #getAlignmentX
     * @see     #getAlignmentY
     */
    public static final float CENTER_ALIGNMENT = 0.5f;

    /**
     * Ease-of-use constant for {@code getAlignmentY}.
     * Specifies an alignment to the bottom of the component.
     * @see     #getAlignmentY
     */
    public static final float BOTTOM_ALIGNMENT = 1.0f;

    /**
     * Ease-of-use constant for {@code getAlignmentX}.
     * Specifies an alignment to the left side of the component.
     * @see     #getAlignmentX
     */
    public static final float LEFT_ALIGNMENT = 0.0f;

    /**
     * Ease-of-use constant for {@code getAlignmentX}.
     * Specifies an alignment to the right side of the component.
     * @see     #getAlignmentX
     */
    public static final float RIGHT_ALIGNMENT = 1.0f;

    /*
     * JDK 1.1 serialVersionUID
     */
    private static final long serialVersionUID = -7644114512714619750L;

    /**
     * If any {@code PropertyChangeListeners} have been registered,
     * the {@code changeSupport} field describes them.
     *
     * @serial
     * @since 1.2
     * @see #addPropertyChangeListener
     * @see #removePropertyChangeListener
     */
    private PropertyChangeSupport changeSupport;

    /*
     * In some cases using "this" as an object to synchronize by
     * can lead to a deadlock if client code also uses synchronization
     * by a component object. For every such situation revealed we should
     * consider possibility of replacing "this" with the package private
     * objectLock object introduced below. So far there're 3 issues known:
     * - CR 6708322 (the getName/setName methods);
     * - CR 6608764 (the PropertyChangeListener machinery);
     * - CR 7108598 (the Container.paint/KeyboardFocusManager.clearMostRecentFocusOwner methods).
     *
     * Note: this field is considered final, though readObject() prohibits
     * initializing final fields.
     */
    private transient Object objectLock = new Object();
    Object getObjectLock() {
        return objectLock;
    }
	boolean isPacked = false;
	private transient Object privateKey = new Object();
    /**
     * Pseudoparameter for direct Geometry API (setLocation, setBounds setSize
     * to signal setBounds what's changing. Should be used under TreeLock.
     * This is only needed due to the inability to change the cross-calling
     * order of public and deprecated methods.
     */
    private int boundsOp = DEFAULT_OPERATION;

    /*
     * The shape set with the applyCompoundShape() method. It uncludes the result
     * of the HW/LW mixing related shape computation. It may also include
     * the user-specified shape of the component.
     * The 'null' value means the component has normal shape (or has no shape at all)
     * and applyCompoundShape() will skip the following shape identical to normal.
     */
    private transient Region compoundShape = null;

    /*
     * Represents the shape of this lightweight component to be cut out from
     * heavyweight components should they intersect. Possible values:
     *    1. null - consider the shape rectangular
     *    2. EMPTY_REGION - nothing gets cut out (children still get cut out)
     *    3. non-empty - this shape gets cut out.
     */
    private transient Region mixingCutoutRegion = null;

    /*
     * Indicates whether addNotify() is complete
     * (i.e. the peer is created).
     */
    private transient boolean isAddNotifyComplete = false;


    // Whether this Component has had the background erase flag
    // specified via SunToolkit.disableBackgroundErase(). This is
    // needed in order to make this function work on X11 platforms,
    // where currently there is no chance to interpose on the creation
    // of the peer and therefore the call to XSetBackground.
	transient boolean backgroundEraseDisabled;
	private int componentSerializedDataVersion = 4;
	
	public void addComponentListener(ComponentListener paramComponentListener) {}
	public void addHierarchyListener(HierarchyListener paramHierarchyListener) {}
	
	public static final int CENTER = 0;
	public static final int TOP = 1;
	public static final int LEFT = 2;
	public static final int BOTTOM = 3;
	public static final int RIGHT = 4;
	public static final int NORTH = 1;
	public static final int NORTH_EAST = 2;
	public static final int EAST = 3;
	public static final int SOUTH_EAST = 4;
	public static final int SOUTH = 5;
	public static final int SOUTH_WEST = 6;
	public static final int WEST = 7;
	public static final int NORTH_WEST = 8;
	public static final int HORIZONTAL = 0;
	public static final int VERTICAL = 1;
	public static final int LEADING = 10;
	public static final int TRAILING = 11;
	public static final int NEXT = 12;
	public static final int PREVIOUS = 13;
	
	public Color getBackground()
	{
		return null;
	}

	  public int getWidth()
	  {
	    return this.width;
	  }

	  public int getHeight()
	  {
	    return this.height;
	  }

	public String getName()
	{
		return null;
	}

	public Container getParent()
	{
		return null;
	}

	public Dimension getPreferredSize()
	{
		return new Dimension(this.prefWidth, this.prefHeight);
	}

	public boolean isDisplayable()
	{
		return true;
	}

	public boolean isEnabled()
	{
		return false;
	}

	public boolean isPreferredSizeSet()
	{
		return this.prefSizeIsSet;
	}
 
	public boolean isVisible()
	{
		return true;
	}

	public void paint(Graphics paramGraphics) {}

	public void removeHierarchyListener(HierarchyListener paramHierarchyListener) {}

	public void repaint()
	{
		update(null);
	}
  
	public void setBackground(Color paramColor) {}

	public void setEnabled(boolean paramBoolean) {}
  
	public void setLocation(int paramInt1, int paramInt2)
	{
		move(paramInt1, paramInt2);
	}

	void setBoundsOp(int paramInt)
	{
	  assert (Thread.holdsLock(getTreeLock()));
	  if (paramInt == 5)
	  {
	    this.boundsOp = 3;
	  }
	  else
	  {
	    if (this.boundsOp != 3)
	      return;
	    this.boundsOp = paramInt;
	  }
	}				
	
	@Deprecated
	public void move(int paramInt1, int paramInt2)
	{
		synchronized (getTreeLock())
		{
	    setBoundsOp(1);
	    setBounds(paramInt1, paramInt2, this.width, this.height);
	  }
	}

  public Dimension getSize()
  {
    return size ();
  }
  
  public Dimension size()
  {
    return new Dimension (width, height);
  }
  
	public void setMaximumSize(Dimension paramDimension) {}

	public void setName(String paramString) {}

 
	public void setPreferredSize(Dimension paramDimension)
	{
		if (paramDimension == null)
		{
			this.prefWidth = 0;
			this.prefHeight = 0;
			this.prefSizeIsSet = false;
		}
		else {
			this.prefWidth = paramDimension.width;
			this.prefHeight = paramDimension.height;
			this.prefSizeIsSet = true;
		}
	}

	public void setSize(Dimension paramDimension)
	{
		if (paramDimension == null) {
			setSize(0, 0);
		} else {
			setSize(paramDimension.width, paramDimension.height);
		}
	}

	public void setSize(int paramInt1, int paramInt2)
	{
		this.width = paramInt1;
		this.height = paramInt2;
	}

  /**
   * Tests if this component is opaque. All "heavyweight" (natively-drawn)
   * components are opaque. A component is opaque if it draws all pixels in
   * the bounds; a lightweight component is partially transparent if it lets
   * pixels underneath show through. Subclasses that guarantee that all pixels
   * will be drawn should override this.
   *
   * @return true if this is opaque
   * @see #isLightweight()
   * @since 1.2
   */
  public boolean isOpaque()
  {
    return ! isLightweight();
  }

  /**
   * Return whether the component is lightweight. That means the component has
   * no native peer, but is displayable. This applies to subclasses of
   * Component not in this package, such as javax.swing.
   *
   * @return true if the component has a lightweight peer
   * @see #isDisplayable()
   * @since 1.2
   */
  public boolean isLightweight()
  {
    return true;
  }
  
	public void setVisible(boolean paramBoolean) {}

	public void update(Graphics paramGraphics)
	{
		paint(paramGraphics);
	}

	public boolean isMinimumSizeSet()
	{
	return this.minSizeSet;
	}

	public Dimension getMinimumSize()
	{
	return minimumSize();
	}

	@Deprecated
	public Dimension minimumSize()
	{
	  Dimension localDimension = this.minSize;
	  if ((localDimension == null) || ((!(isMinimumSizeSet()))))
	    synchronized (getTreeLock())
	    {
	      localDimension = this.minSize;
	    }
	  return new Dimension(localDimension);
	}
	  
  public Dimension getMaximumSize()
  {
    return new Dimension(maximumSizeImpl());
  }

  Dimension maximumSizeImpl()
  {
    Dimension size;
    if (maxSizeSet)
      size = maxSize;
    else
      size = DEFAULT_MAX_SIZE;
    return size;
  }
	
  public boolean isMaximumSizeSet()
  {
    return maxSizeSet;
  }
  
	public final Object getTreeLock()
	{
	return LOCK;
	}
	public void setBounds(int i, int j, int k, int l) {
		// TODO Auto-generated method stub
	}
	public void addNotify()
	{
		// TODO Auto-generated method stub
		
	}
	
	public int getX()
	{
	  return this.x;
	}

	public int getY()
	{
	  return this.y;
	}
	
  public void addPropertyChangeListener(PropertyChangeListener listener)
  {
    if (changeSupport == null)
      changeSupport = new PropertyChangeSupport(this);
    changeSupport.addPropertyChangeListener(listener);
  }
  
  public void addPropertyChangeListener(String propertyName,
                                        PropertyChangeListener listener)
  {
    if (changeSupport == null)
      changeSupport = new PropertyChangeSupport(this);
    changeSupport.addPropertyChangeListener(propertyName, listener);
  }
  
  public void removePropertyChangeListener(String propertyName,
      PropertyChangeListener listener)
	{
		if (changeSupport != null)
		changeSupport.removePropertyChangeListener(propertyName, listener);
	}
}
