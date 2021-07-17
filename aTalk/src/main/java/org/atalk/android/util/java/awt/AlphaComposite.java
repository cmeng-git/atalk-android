package org.atalk.android.util.java.awt;

public final class AlphaComposite {
	public static final int CLEAR = 1;
	public static final int SRC = 2;
	public static final int DST = 9;
	public static final int SRC_OVER = 3;
	public static final int DST_OVER = 4;
	public static final int SRC_IN = 5;
	public static final int DST_IN = 6;
	public static final int SRC_OUT = 7;
	public static final int DST_OUT = 8;
	public static final int SRC_ATOP = 10;
	public static final int DST_ATOP = 11;
	public static final int XOR = 12;
	public static final AlphaComposite Clear = new AlphaComposite(1);
	public static final AlphaComposite Src = new AlphaComposite(2);
	public static final AlphaComposite Dst = new AlphaComposite(9);
	public static final AlphaComposite SrcOver = new AlphaComposite(3);
	public static final AlphaComposite DstOver = new AlphaComposite(4);
	public static final AlphaComposite SrcIn = new AlphaComposite(5);
	public static final AlphaComposite DstIn = new AlphaComposite(6);
	public static final AlphaComposite SrcOut = new AlphaComposite(7);
	public static final AlphaComposite DstOut = new AlphaComposite(8);
	public static final AlphaComposite SrcAtop = new AlphaComposite(10);
	public static final AlphaComposite DstAtop = new AlphaComposite(11);
	public static final AlphaComposite Xor = new AlphaComposite(12);
	private static final int MIN_RULE = 1;
	private static final int MAX_RULE = 12;
	float extraAlpha;
	int rule;

	private AlphaComposite(int paramInt) {
	}

	public static Object getInstance(int srcOver2, float alpha) {
		// TODO Auto-generated method stub
		return null;
	}

}
