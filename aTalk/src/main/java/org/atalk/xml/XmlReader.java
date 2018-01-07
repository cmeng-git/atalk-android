package org.atalk.xml;

import android.os.PowerManager.WakeLock;
import android.util.*;

import org.atalk.Config;
import org.xmlpull.v1.*;

import java.io.*;

public class XmlReader {
	private XmlPullParser parser;
	private WakeLock wakeLock;
	private InputStream is;

	public XmlReader(WakeLock wakeLock) {
		this.parser = Xml.newPullParser();
		try {
			this.parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true);
		} catch (XmlPullParserException e) {
			Log.d(Config.LOGTAG, "error setting namespace feature on parser");
		}
		this.wakeLock = wakeLock;
	}

	public void setInputStream(InputStream inputStream) throws IOException {
		if (inputStream == null) {
			throw new IOException();
		}
		this.is = inputStream;
		try {
			parser.setInput(new InputStreamReader(this.is));
		} catch (XmlPullParserException e) {
			throw new IOException("error resetting parser");
		}
	}

	public void reset() throws IOException {
		if (this.is == null) {
			throw new IOException();
		}
		try {
			parser.setInput(new InputStreamReader(this.is));
		} catch (XmlPullParserException e) {
			throw new IOException("error resetting parser");
		}
	}

	public Tag readTag() throws XmlPullParserException, IOException {
		if (wakeLock.isHeld()) {
			try {
				wakeLock.release();
			} catch (RuntimeException re) {
				Log.d(Config.LOGTAG,"runtime exception releasing wakelock before reading tag "+re.getMessage());
			}
		}
		try {
			while (this.is != null && parser.next() != XmlPullParser.END_DOCUMENT) {
				wakeLock.acquire();
				if (parser.getEventType() == XmlPullParser.START_TAG) {
					Tag tag = Tag.start(parser.getName());
					for (int i = 0; i < parser.getAttributeCount(); ++i) {
						tag.setAttribute(parser.getAttributeName(i),
								parser.getAttributeValue(i));
					}
					String xmlns = parser.getNamespace();
					if (xmlns != null) {
						tag.setAttribute("xmlns", xmlns);
					}
					return tag;
				} else if (parser.getEventType() == XmlPullParser.END_TAG) {
					return Tag.end(parser.getName());
				} else if (parser.getEventType() == XmlPullParser.TEXT) {
					return Tag.no(parser.getText());
				}
			}

		} catch (Throwable throwable) {
			throw new IOException("xml parser mishandled "+throwable.getClass().getName(), throwable);
		} finally {
			if (wakeLock.isHeld()) {
				try {
					wakeLock.release();
				} catch (RuntimeException re) {
					Log.d(Config.LOGTAG,"runtime exception releasing wakelock after exception "+re.getMessage());
				}
			}
		}
		return null;
	}

	public Element readElement(Tag currentTag) throws XmlPullParserException,
			IOException {
		Element element = new Element(currentTag.getName());
		element.setAttributes(currentTag.getAttributes());
		Tag nextTag = this.readTag();
		if (nextTag == null) {
			throw new IOException("interrupted mid tag");
		}
		if (nextTag.isNo()) {
			element.setContent(nextTag.getName());
			nextTag = this.readTag();
			if (nextTag == null) {
				throw new IOException("interrupted mid tag");
			}
		}
		while (!nextTag.isEnd(element.getName())) {
			if (!nextTag.isNo()) {
				Element child = this.readElement(nextTag);
				element.addChild(child);
			}
			nextTag = this.readTag();
			if (nextTag == null) {
				throw new IOException("interrupted mid tag");
			}
		}
		return element;
	}
}
