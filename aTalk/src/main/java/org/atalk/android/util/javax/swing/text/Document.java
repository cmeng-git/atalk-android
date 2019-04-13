package org.atalk.android.util.javax.swing.text;

import java.util.Dictionary;

import org.xmpp.extensions.colibri.ColibriConferenceIQ.Content;

import org.atalk.android.gui.util.event.EventListenerList;
import org.atalk.android.util.javax.swing.event.DocumentListener;


public class Document {
	private transient int numReaders;
	private transient Thread currWriter;
	private transient int numWriters;
	private transient boolean notifyingListeners;
	private static Boolean defaultI18NProperty;
	private Dictionary<Object, Object> documentProperties;
	protected EventListenerList listenerList;
	private Content data;
	private static final String BAD_LOCK_STATE = "document lock failure";
	protected static final String BAD_LOCATION = "document location failure";
	public static final String ParagraphElementName = "paragraph";
	public static final String ContentElementName = "content";
	public static final String SectionElementName = "section";
	public static final String BidiElementName = "bidi level";
	public static final String ElementNameAttribute = "$ename";
	static final String I18NProperty = "i18n";
	static final Object MultiByteProperty = "multiByte";
	static final String AsyncLoadPriority = "load priority";	
	
	public void addDocumentListener(DocumentListener paramDocumentListener) {
		this.listenerList.add(DocumentListener.class, paramDocumentListener);
	}

}
