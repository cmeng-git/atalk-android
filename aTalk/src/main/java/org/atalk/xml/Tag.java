package org.atalk.xml;

import org.apache.commons.lang3.StringEscapeUtils;

import java.util.*;
import java.util.Map.Entry;

//import eu.siacs.conversations.utils.XmlHelper;

public class Tag {
	public static final int NO = -1;
	public static final int START = 0;
	public static final int END = 1;
	public static final int EMPTY = 2;

	protected int type;
	protected String name;
	protected Hashtable<String, String> attributes = new Hashtable<String, String>();

	protected Tag(int type, String name) {
		this.type = type;
		this.name = name;
	}

	public static Tag no(String text) {
		return new Tag(NO, text);
	}

	public static Tag start(String name) {
		return new Tag(START, name);
	}

	public static Tag end(String name) {
		return new Tag(END, name);
	}

	public static Tag empty(String name) {
		return new Tag(EMPTY, name);
	}

	public String getName() {
		return name;
	}

	public String getAttribute(String attrName) {
		return this.attributes.get(attrName);
	}

	public Tag setAttribute(String attrName, String attrValue) {
		this.attributes.put(attrName, attrValue);
		return this;
	}

	public Tag setAtttributes(Hashtable<String, String> attributes) {
		this.attributes = attributes;
		return this;
	}

	public boolean isStart(String needle) {
		if (needle == null)
			return false;
		return (this.type == START) && (needle.equals(this.name));
	}

	public boolean isEnd(String needle) {
		if (needle == null)
			return false;
		return (this.type == END) && (needle.equals(this.name));
	}

	public boolean isNo() {
		return (this.type == NO);
	}

	public String toString() {
		StringBuilder tagOutput = new StringBuilder();
		tagOutput.append('<');
		if (type == END) {
			tagOutput.append('/');
		}
		tagOutput.append(name);
		if (type != END) {
			Set<Entry<String, String>> attributeSet = attributes.entrySet();
			Iterator<Entry<String, String>> it = attributeSet.iterator();
			while (it.hasNext()) {
				Entry<String, String> entry = it.next();
				tagOutput.append(' ');
				tagOutput.append(entry.getKey());
				tagOutput.append("=\"");
				tagOutput.append(StringEscapeUtils.escapeHtml4(entry.getValue()));
				tagOutput.append('"');
			}
		}
		if (type == EMPTY) {
			tagOutput.append('/');
		}
		tagOutput.append('>');
		return tagOutput.toString();
	}

	public Hashtable<String, String> getAttributes() {
		return this.attributes;
	}
}
