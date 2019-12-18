package org.atalk.android.util.javax.swing.event;

import java.util.EventListener;

public interface DocumentListener extends EventListener {
	void insertUpdate(DocumentEvent paramDocumentEvent);

	void removeUpdate(DocumentEvent paramDocumentEvent);

	void changedUpdate(DocumentEvent paramDocumentEvent);
}