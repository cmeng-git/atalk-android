/*  1:   */ package org.atalk.android.util.javax.sound.sampled;
/*  2:   */ 
/*  3:   */ public abstract class Control
/*  4:   */ {
/*  5:   */   private Type type;
/*  6:   */   
/*  7:   */   public Type getType()
/*  8:   */   {
/*  9:10 */     return this.type;
/* 10:   */   }
/* 11:   */   
/* 12:   */   public static class Type
/* 13:   */   {
/* 14:   */     private String name;
/* 15:   */     
/* 16:   */     protected Type(String name)
/* 17:   */     {
/* 18:18 */       this.name = name;
/* 19:   */     }
/* 20:   */   }
/* 21:   */ }



/* Location:           D:\workspace\Android\soTalk\sotalk\libs\java-stubs.jar

 * Qualified Name:     Control

 * JD-Core Version:    0.7.0.1

 */