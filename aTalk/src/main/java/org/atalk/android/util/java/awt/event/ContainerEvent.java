package org.atalk.android.util.java.awt.event;

import org.atalk.android.util.java.awt.Component;
import org.atalk.android.util.java.awt.Container;

public class ContainerEvent extends ComponentEvent
{
  private static final long serialVersionUID = -4114942250539772041L;
  public static final int CONTAINER_FIRST = 300;
  public static final int CONTAINER_LAST = 301;
  public static final int COMPONENT_ADDED = 300;
  public static final int COMPONENT_REMOVED = 301;
  
  private final Component child;
  
  public ContainerEvent(Component source, int id, Component child)
  {
    super(source, id);
    this.child = child;
  }

  public Container getContainer()
  {
    return (Container) source;
  }

  public Component getChild()
  {
    return child;
  }
  
  public String paramString()
  {
    // Unlike Sun, we don't throw NullPointerException if child is illegally
    // null.
    return (id == COMPONENT_ADDED ? "COMPONENT_ADDED,child="
            : id == COMPONENT_REMOVED ? "COMPONENT_REMOVED,child="
            : "unknown type,child=") + (child == null ? "" : child.getName());
  }

}


/* Location:           D:\workspace\Android\soTalk\sotalk\libs\java-stubs.jar

 * Qualified Name:     ContainerEvent

 * JD-Core Version:    0.7.0.1

 */
