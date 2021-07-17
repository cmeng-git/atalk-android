package org.atalk.android.util.java.awt;

import java.util.EventObject;

public abstract class AWTEvent extends EventObject
{
  protected int id;
  protected boolean consumed;
  byte[] bdata;
  
  public AWTEvent(Object source, int id)
  {
    super(source);
    this.id = id;
  }

  public int getID()
  {
    return id;
  }
}


/* Location:           D:\workspace\Android\soTalk\sotalk\libs\java-stubs.jar

 * Qualified Name:     org.atalk.android.util.java.awt.event.AWTEvent

 * JD-Core Version:    0.7.0.1

 */