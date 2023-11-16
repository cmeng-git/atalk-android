package javax.imageio;

import java.awt.Image;
import java.io.IOException;
import java.lang.reflect.Method;
import java.net.URL;

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
