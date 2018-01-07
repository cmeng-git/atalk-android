package org.atalk.android.util.javax.swing.event;

import java.util.EventListener;

public abstract interface DocumentListener extends EventListener {
	public abstract void insertUpdate(DocumentEvent paramDocumentEvent);

	public abstract void removeUpdate(DocumentEvent paramDocumentEvent);

	public abstract void changedUpdate(DocumentEvent paramDocumentEvent);
}