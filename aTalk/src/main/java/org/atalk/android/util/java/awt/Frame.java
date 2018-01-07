/*  1:   */ package org.atalk.android.util.java.awt;
/*  2:   */ 
/*  3:   */ public class Frame
/*  4:   */   extends Window
/*  5:   */ {
/*  6:   */   public static final int NORMAL = 0;
/*  7:   */   public static final int ICONIFIED = 1;
/*  8:   */   public static final int MAXIMIZED_HORIZ = 2;
/*  9:   */   public static final int MAXIMIZED_VERT = 4;
/* 10:   */   public static final int MAXIMIZED_BOTH = 6;
/* 11:   */   
/* 12:   */   public Frame()
/* 13:   */   {
/* 14:28 */     this(null);
/* 15:   */   }
/* 16:   */   
/* 17:   */   public Frame(String title)
/* 18:   */   {
/* 19:34 */     super(null);
/* 20:   */   }
/* 21:   */   
/* 22:   */   public int getExtendedState()
/* 23:   */   {
/* 24:37 */     return -1;
/* 25:   */   }
/* 26:   */   
/* 27:   */   public void setExtendedState(int state) {}
/* 28:   */ }



/* Location:           D:\workspace\Android\soTalk\sotalk\libs\java-stubs.jar

 * Qualified Name:     Frame

 * JD-Core Version:    0.7.0.1

 */