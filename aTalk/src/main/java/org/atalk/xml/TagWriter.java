package org.atalk.xml;

import net.java.sip.communicator.util.Logger;

import org.atalk.xmpp.stanzas.AbstractStanza;

import java.io.*;
import java.util.concurrent.LinkedBlockingQueue;

public class TagWriter {
	/**
	 * The logger
	 */
	private static final Logger logger = Logger.getLogger(TagWriter.class);

	private OutputStreamWriter outputStream;
	private boolean finshed = false;
	private LinkedBlockingQueue<AbstractStanza> writeQueue = new LinkedBlockingQueue<AbstractStanza>();
	private Thread asyncStanzaWriter = new Thread() {
	private boolean shouldStop = false;

		@Override
		public void run() {
			while (!shouldStop) {
				if ((finshed) && (writeQueue.size() == 0)) {
					return;
				}
				try {
					AbstractStanza output = writeQueue.take();
					outputStream.write(output.toString());
					outputStream.flush();
				} catch (Exception e) {
					shouldStop = true;
				}
			}
		}
	};

	public TagWriter() {
	}

	public synchronized void setOutputStream(OutputStream out) throws IOException {
		if (out == null) {
			throw new IOException();
		}
		this.outputStream = new OutputStreamWriter(out);
	}

	public TagWriter beginDocument() throws IOException {
		if (outputStream == null) {
			throw new IOException("output stream was null");
		}
		outputStream.write("<?xml version='1.0'?>");
		outputStream.flush();
		return this;
	}

	public synchronized  TagWriter writeTag(Tag tag) throws IOException {
		if (outputStream == null) {
			throw new IOException("output stream was null");
		}
		outputStream.write(tag.toString());
		outputStream.flush();
		return this;
	}

	public synchronized TagWriter writeElement(Element element) throws IOException {
		if (outputStream == null) {
			throw new IOException("output stream was null");
		}
		outputStream.write(element.toString());
		outputStream.flush();
		return this;
	}

	public TagWriter writeStanzaAsync(AbstractStanza stanza) {
		if (finshed) {
			logger.debug("attempting to write stanza to finished TagWriter");
			return this;
		} else {
			if (!asyncStanzaWriter.isAlive()) {
				try {
					asyncStanzaWriter.start();
				} catch (IllegalThreadStateException e) {
					// already started
				}
			}
			writeQueue.add(stanza);
			return this;
		}
	}

	public void finish() {
		this.finshed = true;
	}

	public boolean finished() {
		return (this.writeQueue.size() == 0);
	}

	public boolean isActive() {
		return outputStream != null;
	}

	public synchronized void forceClose() {
		finish();
		if (outputStream != null) {
			try {
				outputStream.close();
			} catch (IOException e) {
				//ignoring
			}
		}
		outputStream = null;
	}
}
