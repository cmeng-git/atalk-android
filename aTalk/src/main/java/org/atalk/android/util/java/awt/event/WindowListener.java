package org.atalk.android.util.java.awt.event;

public abstract interface WindowListener
{
  public abstract void windowOpened(WindowEvent paramWindowEvent);
  
  public abstract void windowClosing(WindowEvent paramWindowEvent);
  
  public abstract void windowClosed(WindowEvent paramWindowEvent);
  
  public abstract void windowIconified(WindowEvent paramWindowEvent);
  
  public abstract void windowDeiconified(WindowEvent paramWindowEvent);
  
  public abstract void windowActivated(WindowEvent paramWindowEvent);
  
  public abstract void windowDeactivated(WindowEvent paramWindowEvent);
}



/* Location:           D:\workspace\Android\soTalk\sotalk\libs\java-stubs.jar

 * Qualified Name:     WindowListener

 * JD-Core Version:    0.7.0.1

 */