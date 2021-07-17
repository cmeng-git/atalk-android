package org.atalk.android.util.java.awt;

import java.io.Serializable;
import java.util.Hashtable;
import java.util.Locale;

public class Font implements Serializable {
	private Hashtable fRequestedAttributes;
	public static final String DIALOG = "Dialog";
	public static final String DIALOG_INPUT = "DialogInput";
	public static final String SANS_SERIF = "SansSerif";
	public static final String SERIF = "Serif";
	public static final String MONOSPACED = "Monospaced";
	public static final int PLAIN = 0;
	public static final int BOLD = 1;
	public static final int ITALIC = 2;
	public static final int ROMAN_BASELINE = 0;
	public static final int CENTER_BASELINE = 1;
	public static final int HANGING_BASELINE = 2;
	public static final int TRUETYPE_FONT = 0;
	public static final int TYPE1_FONT = 1;
	protected String name;
	protected int style;
	protected int size;
	protected float pointSize;
	private transient long pData;
	private transient boolean hasLayoutAttributes;
	private transient boolean createdFont;
	private transient boolean nonIdentityTx;
	private static final long serialVersionUID = -4206021311591459213L;
	transient int hash;
	public static final int LAYOUT_LEFT_TO_RIGHT = 0;
	public static final int LAYOUT_RIGHT_TO_LEFT = 1;
	public static final int LAYOUT_NO_START_CONTEXT = 2;
	public static final int LAYOUT_NO_LIMIT_CONTEXT = 4;
		

	public Font(String fontName, int bold2, int i) {
		// TODO Auto-generated constructor stub
	}

	public String getFontName() {
		return getFontName(Locale.getDefault());
	}
	
	public String getFontName(Locale paramLocale) {
		if (paramLocale == null)
			throw new NullPointerException("null locale doesn't mean default");
		return "Font2D";
	}

	public int getSize() {
		// TODO Auto-generated method stub
		return 0;
	}
	
}