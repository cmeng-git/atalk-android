/*  1:   */ package org.atalk.android.util.java.beans;
/*  2:   */ 
/*  3:   */ import java.lang.reflect.Method;
/*  4:   */ 
/*  5:   */ public class PropertyDescriptor
/*  6:   */ {
/*  7:   */   private Class<?> objectClass;
/*  8:   */   private String name;
/*  9:   */   
/* 10:   */   public PropertyDescriptor(String name, Class<?> objectClass)
/* 11:   */   {
/* 12:10 */     this.name = name;
/* 13:11 */     this.objectClass = objectClass;
/* 14:   */   }
/* 15:   */   
/* 16:   */   public Class<?> getPropertyType()
/* 17:   */   {
/* 18:   */     try
/* 19:   */     {
/* 20:16 */       return this.objectClass.getMethod("get" + Character.toUpperCase(this.name.charAt(0)) + this.name.substring(1), new Class[0]).getReturnType();
/* 21:   */     }
/* 22:   */     catch (SecurityException e)
/* 23:   */     {
/* 24:22 */       return null;
/* 25:   */     }
/* 26:   */     catch (NoSuchMethodException e) {}
/* 27:24 */     return null;
/* 28:   */   }
/* 29:   */   
/* 30:   */   public Method getWriteMethod()
/* 31:   */   {
/* 32:   */     try
/* 33:   */     {
/* 34:30 */       return this.objectClass.getMethod("set" + Character.toUpperCase(this.name.charAt(0)) + this.name.substring(1), new Class[] { getPropertyType() });
/* 35:   */     }
/* 36:   */     catch (SecurityException e)
/* 37:   */     {
/* 38:33 */       return null;
/* 39:   */     }
/* 40:   */     catch (NoSuchMethodException e) {}
/* 41:35 */     return null;
/* 42:   */   }
/* 43:   */ }



/* Location:           D:\workspace\Android\soTalk\sotalk\libs\java-stubs.jar

 * Qualified Name:     PropertyDescriptor

 * JD-Core Version:    0.7.0.1

 */