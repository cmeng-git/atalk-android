package net.java.sip.communicator.plugin.desktoputil;

import org.atalk.android.util.javax.swing.text.JTextComponent;
import org.atalk.android.util.java.awt.Color;
import org.atalk.android.util.javax.swing.JTextField;
import org.atalk.android.util.javax.swing.event.DocumentEvent;
import org.atalk.android.util.javax.swing.event.DocumentListener;
import org.atalk.android.util.javax.swing.text.Document;

public class SIPCommTextField extends JTextField implements DocumentListener {

	/**
	 * Serial version UID.
	 */
	private static final long serialVersionUID = 0L;

	/**
	 * The default text.
	 */
	private String defaultText;

	/**
	 * Indicates if the default text is currently visible.
	 */
	private boolean isDefaultTextVisible;
	private Color foregroundColor = Color.BLACK;
	private Color defaultTextColor = Color.GRAY;


	public SIPCommTextField(String text) {
		super(text);
		if (text != null && text.length() > 0) {
			this.defaultText = text;
			isDefaultTextVisible = true;
		}

		JTextComponent.getDocument().addDocumentListener(this);
	}

	public String getText() {
		// TODO Auto-generated method stub
		return null;
	}

	public void setBackground(Object green) {
		// TODO Auto-generated method stub

	}

	@Override
	public void insertUpdate(DocumentEvent paramDocumentEvent) {
		// TODO Auto-generated method stub

	}

	@Override
	public void removeUpdate(DocumentEvent paramDocumentEvent) {
		// TODO Auto-generated method stub

	}

	@Override
	public void changedUpdate(DocumentEvent paramDocumentEvent) {
		// TODO Auto-generated method stub

	}

	public Document getDocument() {
		// TODO Auto-generated method stub
		return null;
	}
}
