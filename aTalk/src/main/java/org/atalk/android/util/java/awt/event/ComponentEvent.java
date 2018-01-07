package org.atalk.android.util.java.awt.event;
import org.atalk.android.util.java.awt.AWTEvent;
import org.atalk.android.util.java.awt.Component;

public class ComponentEvent extends AWTEvent
{
  public ComponentEvent(Component source, int id)
  {
    super(source, id);
  }
  public Component getComponent()
  {
    return source instanceof Component ? (Component) source : null;
  }
/* 17:   */ }



/* Location:           D:\workspace\Android\soTalk\sotalk\libs\java-stubs.jar

 * Qualified Name:     ComponentEvent

 * JD-Core Version:    0.7.0.1

 */