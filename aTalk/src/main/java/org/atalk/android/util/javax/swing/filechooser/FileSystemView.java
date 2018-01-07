/*  1:   */ package org.atalk.android.util.javax.swing.filechooser;
/*  2:   */ 
/*  3:   */ /*  4:   */ import java.io.File;

import android.os.Environment;
/*  5:   */ 
/*  6:   */ public class FileSystemView
/*  7:   */ {
/*  8:   */   public static FileSystemView getFileSystemView()
/*  9:   */   {
/* 10:15 */     return new FileSystemView();
/* 11:   */   }
/* 12:   */   
/* 13:   */   public File getHomeDirectory()
/* 14:   */   {
/* 15:20 */     return Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
/* 16:   */   }
/* 17:   */ }



/* Location:           D:\workspace\Android\soTalk\sotalk\libs\java-stubs.jar

 * Qualified Name:     FileSystemView

 * JD-Core Version:    0.7.0.1

 */