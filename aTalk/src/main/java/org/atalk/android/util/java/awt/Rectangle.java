
package org.atalk.android.util.java.awt;

public class Rectangle {

	public int height;
	public int width;
	public int x;
	public int y;

  public Rectangle()
  {
  }
  public Rectangle(Rectangle r)
  {
    x = r.x;
    y = r.y;
    width = r.width;
    height = r.height;
  }

  	public Rectangle(int x, int y, int width, int height)
  	{
  		this.x = x;
  		this.y = y;
  		this.width = width;
  		this.height = height;
	}
  	
  public Rectangle(int width, int height)
  {
    this.width = width;
    this.height = height;
  }
  
  public Rectangle(Dimension d)
  {
    width = d.width;
    height = d.height;
  }
  public Dimension getSize()
  {
    return new Dimension(width, height);
  }
  
/* 20:   */   public void setSize(Dimension d)
/* 21:   */   {
/* 22:37 */     setSize(d.width, d.height);
/* 23:   */   }
/* 24:   */   
/* 25:   */   public void setSize(int width, int height)
/* 26:   */   {
/* 27:42 */     this.width = width;
/* 28:43 */     this.height = height;
/* 29:   */   }
/* 30:   */   
/* 31:   */   public void setBounds(int x, int y, int width, int height)
/* 32:   */   {
/* 33:48 */     this.x = x;
/* 34:49 */     this.y = y;
/* 35:50 */     this.width = width;
/* 36:51 */     this.height = height;
/* 37:   */   }
/* 38:   */   
/* 39:   */   public void setBounds(Rectangle r)
/* 40:   */   {
/* 41:56 */     setBounds(r.x, r.y, r.width, r.height);
/* 42:   */   }
/* 43:   */
public boolean contains(Point p)
{
	// TODO Auto-generated method stub
	return false;
} }



/* Location:           D:\workspace\Android\soTalk\sotalk\libs\java-stubs.jar

 * Qualified Name:     Rectangle

 * JD-Core Version:    0.7.0.1

 */