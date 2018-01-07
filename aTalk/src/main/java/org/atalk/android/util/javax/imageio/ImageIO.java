package org.atalk.android.util.javax.imageio;

import java.io.IOException;
import java.lang.reflect.Method;
import java.net.URL;

import org.atalk.android.util.java.awt.Image;

public final class ImageIO {
	private static Method readerFormatNamesMethod;
	private static Method readerFileSuffixesMethod;
	private static Method readerMIMETypesMethod;
	private static Method writerFormatNamesMethod;
	private static Method writerFileSuffixesMethod;
	private static Method writerMIMETypesMethod;

	public static Image read(URL imageURL) throws IOException  {
		// TODO Auto-generated method stub
		return null;
	}

}
