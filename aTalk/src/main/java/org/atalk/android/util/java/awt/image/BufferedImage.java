/*  1:   */ package org.atalk.android.util.java.awt.image;
import java.util.Hashtable;

import org.atalk.android.util.java.awt.Image;

public class BufferedImage
extends Image
{
	int imageType = 0;
	WritableRaster raster;
	Hashtable properties;
	boolean isAlphaPremultiplied;
	public static final int TYPE_CUSTOM = 0;
	public static final int TYPE_INT_RGB = 1;
	public static final int TYPE_INT_ARGB = 2;
	public static final int TYPE_INT_ARGB_PRE = 3;
	public static final int TYPE_INT_BGR = 4;
	public static final int TYPE_3BYTE_BGR = 5;
	public static final int TYPE_4BYTE_ABGR = 6;
	public static final int TYPE_4BYTE_ABGR_PRE = 7;
	public static final int TYPE_USHORT_565_RGB = 8;
	public static final int TYPE_USHORT_555_RGB = 9;
	public static final int TYPE_BYTE_GRAY = 10;
	public static final int TYPE_USHORT_GRAY = 11;
	public static final int TYPE_BYTE_BINARY = 12;
	public static final int TYPE_BYTE_INDEXED = 13;
	private static final int DCM_RED_MASK = 16711680;
	private static final int DCM_GREEN_MASK = 65280;
	private static final int DCM_BLUE_MASK = 255;
	private static final int DCM_ALPHA_MASK = -16777216;
	private static final int DCM_565_RED_MASK = 63488;
	private static final int DCM_565_GRN_MASK = 2016;
	private static final int DCM_565_BLU_MASK = 31;
	private static final int DCM_555_RED_MASK = 31744;
	private static final int DCM_555_GRN_MASK = 992;
	private static final int DCM_555_BLU_MASK = 31;
	private static final int DCM_BGR_RED_MASK = 255;
	private static final int DCM_BGR_GRN_MASK = 65280;
	private static final int DCM_BGR_BLU_MASK = 16711680;	
	
	
/*  8:   */   public BufferedImage(int width, int height, int imageType) {}
/*  9:   */   
/* 10:   */   public WritableRaster getRaster()
/* 11:   */   {
/* 12:13 */     return null;
/* 13:   */   }
/* 14:   */   
/* 15:   */   public int getWidth(ImageObserver observer)
/* 16:   */   {
/* 17:18 */     return 0;
/* 18:   */   }
/* 19:   */   
/* 20:   */   public int getHeight(ImageObserver observer)
/* 21:   */   {
/* 22:24 */     return 0;
/* 23:   */   }
/* 24:   */   
/* 25:   */   public int getWidth()
/* 26:   */   {
/* 27:27 */     return 0;
/* 28:   */   }
/* 29:   */   
/* 30:   */   public int getHeight()
/* 31:   */   {
/* 32:29 */     return 0;
/* 33:   */   }
/* 34:   */
public int getType()
{
	// TODO Auto-generated method stub
	return 0;
} }
