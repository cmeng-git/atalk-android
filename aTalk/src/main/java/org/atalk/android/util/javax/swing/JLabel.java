package org.atalk.android.util.javax.swing;

import java.io.IOException;
import java.io.ObjectOutputStream;
import java.text.BreakIterator;
import java.util.Locale;

import org.atalk.android.util.java.awt.Insets;
import org.atalk.android.util.java.awt.Component;
import org.atalk.android.util.java.awt.Image;
import org.atalk.android.util.java.awt.Rectangle;
import org.atalk.android.util.java.awt.event.KeyEvent;
import org.atalk.android.util.javax.swing.plaf.LabelUI;

import android.util.AttributeSet;

public class JLabel extends JComponent implements SwingConstants, Accessible
{
	private static final long serialVersionUID = 1L;
	private static final String uiClassID = "LabelUI";
	private int mnemonic;
	private int mnemonicIndex;
	private String text;
	private Icon defaultIcon;
	private Icon disabledIcon;
	private boolean disabledIconSet;
	private int verticalAlignment;
	private int horizontalAlignment;
	private int verticalTextPosition;
	private int horizontalTextPosition;
	private int iconTextGap;
	protected Component labelFor;
	private LabelUI ui;
	private Object accessibleContext;
	static final String LABELED_BY_PROPERTY = "labeledBy";

	public JLabel(String paramString, Icon paramIcon, int paramInt)
	{
		this.mnemonic = 0;
		this.mnemonicIndex = -1;
		this.text = "";
		this.defaultIcon = null;
		this.disabledIcon = null;
		this.disabledIconSet = false;
		this.verticalAlignment = 0;
		this.horizontalAlignment = 10;
		this.verticalTextPosition = 0;
		this.horizontalTextPosition = 11;
		this.iconTextGap = 4;
		this.labelFor = null;
		setText(paramString);
		setIcon(paramIcon);
		setHorizontalAlignment(paramInt);
		updateUI();
		// setAlignmentX(0.0F);
	}

	public JLabel(String paramString, int paramInt)
	{
		this(paramString, null, paramInt);
	}

	public JLabel(String paramString)
	{
		this(paramString, null, 10);
	}

	public JLabel(Icon paramIcon, int paramInt)
	{
		this(null, paramIcon, paramInt);
	}

	public JLabel(Icon paramIcon)
	{
		this(null, paramIcon, 0);
	}

	public JLabel()
	{
		this("", null, 10);
	}

	public LabelUI getUI()
	{
		return ((LabelUI) this.ui);
	}

	public void setUI(LabelUI paramLabelUI)
	{
		// super.setUI(paramLabelUI);
		if ((this.disabledIconSet) || (this.disabledIcon == null))
			return;
		setDisabledIcon(null);
	}

	public void updateUI()
	{
		setUI((LabelUI) UIManager.getUI(this));
	}

	public String getUIClassID()
	{
		return "LabelUI";
	}

	public String getText()
	{
		return this.text;
	}

	public void setText(String paramString)
	{
		String str1 = null;
		if (this.accessibleContext != null)
			str1 = ((AccessibleJLabel) this.accessibleContext).getAccessibleName();
		String str2 = this.text;
		this.text = paramString;
		firePropertyChange("text", str2, paramString);
		setDisplayedMnemonicIndex(SwingUtilities.findDisplayedMnemonicIndex(paramString, getDisplayedMnemonic()));
		if ((this.accessibleContext != null) && (((AccessibleJLabel) this.accessibleContext).getAccessibleName() != str1))
			((JLabel) this.accessibleContext).firePropertyChange("AccessibleVisibleData", str1, ((AccessibleJLabel) this.accessibleContext).getAccessibleName());
		if ((paramString != null) && (str2 != null) && (paramString.equals(str2)))
			return;
		revalidate();
		repaint();
	}

	public Icon getIcon()
	{
		return this.defaultIcon;
	}

	public void setIcon(Icon paramIcon)
	{
		Icon localIcon = this.defaultIcon;
		this.defaultIcon = paramIcon;
		if ((this.defaultIcon != localIcon) && (!(this.disabledIconSet)))
			this.disabledIcon = null;
		firePropertyChange("icon", localIcon, this.defaultIcon);
		if ((this.accessibleContext != null) && (localIcon != this.defaultIcon))
			((JLabel) this.accessibleContext).firePropertyChange("AccessibleVisibleData", localIcon, this.defaultIcon);
		if (this.defaultIcon == localIcon)
			return;
		if ((this.defaultIcon == null) || (localIcon == null) || (this.defaultIcon.getIconWidth() != localIcon.getIconWidth())
			|| (this.defaultIcon.getIconHeight() != localIcon.getIconHeight()))
			revalidate();
		repaint();
	}

	private void firePropertyChange(String text2, Icon localIcon, Icon defaultIcon2)
	{
		// TODO Auto-generated method stub
		
	}

	public Icon getDisabledIcon()
	{
		if ((!(this.disabledIconSet)) && (this.disabledIcon == null) && (this.defaultIcon != null)) {
			this.disabledIcon = null; //UIManager.getLookAndFeel().getDisabledIcon(this, this.defaultIcon);
			if (this.disabledIcon != null)
				firePropertyChange("disabledIcon", null, this.disabledIcon);
		}
		return this.disabledIcon;
	}

	public void setDisabledIcon(Icon paramIcon)
	{
		Icon localIcon = this.disabledIcon;
		this.disabledIcon = paramIcon;
		this.disabledIconSet = (paramIcon != null);
		firePropertyChange("disabledIcon", localIcon, paramIcon);
		if (paramIcon == localIcon)
			return;
		if ((paramIcon == null) || (localIcon == null) || (paramIcon.getIconWidth() != localIcon.getIconWidth())
			|| (paramIcon.getIconHeight() != localIcon.getIconHeight()))
			revalidate();
		if (isEnabled())
			return;
		repaint();
	}

	public void setDisplayedMnemonic(int paramInt)
	{
		int i = this.mnemonic;
		this.mnemonic = paramInt;
		firePropertyChange("displayedMnemonic", i, this.mnemonic);
		setDisplayedMnemonicIndex(SwingUtilities.findDisplayedMnemonicIndex(getText(), this.mnemonic));
		if (paramInt == i)
			return;
		revalidate();
		repaint();
	}

	private void firePropertyChange(String text2, int i, int mnemonic2)
	{
		// TODO Auto-generated method stub
		
	}

	public void setDisplayedMnemonic(char paramChar)
	{
		int i = KeyEvent.getExtendedKeyCodeForChar(paramChar);
		if (i == 0)
			return;
		setDisplayedMnemonic(i);
	}

	public int getDisplayedMnemonic()
	{
		return this.mnemonic;
	}

	public void setDisplayedMnemonicIndex(int paramInt)
		throws IllegalArgumentException
	{
		int i = this.mnemonicIndex;
		if (paramInt == -1) {
			this.mnemonicIndex = -1;
		}
		else {
			String str = getText();
			int j = (str == null) ? 0 : str.length();
			if ((paramInt < -1) || (paramInt >= j))
				throw new IllegalArgumentException("index == " + paramInt);
		}
		this.mnemonicIndex = paramInt;
		firePropertyChange("displayedMnemonicIndex", i, paramInt);
		if (paramInt == i)
			return;
		revalidate();
		repaint();
	}

	public int getDisplayedMnemonicIndex()
	{
		return this.mnemonicIndex;
	}

	protected int checkHorizontalKey(int paramInt, String paramString)
	{
		if ((paramInt == 2) || (paramInt == 0) || (paramInt == 4) || (paramInt == 10) || (paramInt == 11))
			return paramInt;
		throw new IllegalArgumentException(paramString);
	}

	protected int checkVerticalKey(int paramInt, String paramString)
	{
		if ((paramInt == 1) || (paramInt == 0) || (paramInt == 3))
			return paramInt;
		throw new IllegalArgumentException(paramString);
	}

	public int getIconTextGap()
	{
		return this.iconTextGap;
	}

	public void setIconTextGap(int paramInt)
	{
		int i = this.iconTextGap;
		this.iconTextGap = paramInt;
		firePropertyChange("iconTextGap", i, paramInt);
		if (paramInt == i)
			return;
		revalidate();
		repaint();
	}

	public int getVerticalAlignment()
	{
		return this.verticalAlignment;
	}

	public void setVerticalAlignment(int paramInt)
	{
		if (paramInt == this.verticalAlignment)
			return;
		int i = this.verticalAlignment;
		this.verticalAlignment = checkVerticalKey(paramInt, "verticalAlignment");
		firePropertyChange("verticalAlignment", i, this.verticalAlignment);
		repaint();
	}

	public int getHorizontalAlignment()
	{
		return this.horizontalAlignment;
	}

	public void setHorizontalAlignment(int paramInt)
	{
		if (paramInt == this.horizontalAlignment)
			return;
		int i = this.horizontalAlignment;
		this.horizontalAlignment = checkHorizontalKey(paramInt, "horizontalAlignment");
		firePropertyChange("horizontalAlignment", i, this.horizontalAlignment);
		repaint();
	}

	public int getVerticalTextPosition()
	{
		return this.verticalTextPosition;
	}

	public void setVerticalTextPosition(int paramInt)
	{
		if (paramInt == this.verticalTextPosition)
			return;
		int i = this.verticalTextPosition;
		this.verticalTextPosition = checkVerticalKey(paramInt, "verticalTextPosition");
		firePropertyChange("verticalTextPosition", i, this.verticalTextPosition);
		revalidate();
		repaint();
	}

	public int getHorizontalTextPosition()
	{
		return this.horizontalTextPosition;
	}

	public void setHorizontalTextPosition(int paramInt)
	{
		int i = this.horizontalTextPosition;
		this.horizontalTextPosition = checkHorizontalKey(paramInt, "horizontalTextPosition");
		firePropertyChange("horizontalTextPosition", i, this.horizontalTextPosition);
		revalidate();
		repaint();
	}

	public boolean imageUpdate(Image paramImage, int paramInt1, int paramInt2, int paramInt3, int paramInt4, int paramInt5)
	{
		if ((!(isShowing()))
			|| ((!(SwingUtilities.doesIconReferenceImage(getIcon(), paramImage))) && (!(SwingUtilities.doesIconReferenceImage(this.disabledIcon, paramImage)))))
			return false;
		return super.imageUpdate(paramImage, paramInt1, paramInt2, paramInt3, paramInt4, paramInt5);
	}

	private boolean isShowing()
	{
		// TODO Auto-generated method stub
		return false;
	}

	private void writeObject(ObjectOutputStream paramObjectOutputStream)
		throws IOException
	{
		paramObjectOutputStream.defaultWriteObject();
		if (!(getUIClassID().equals("LabelUI")))
			return;
		byte b = JComponent.getWriteObjCounter(this);
		b = (byte) (b - 1);
		JComponent.setWriteObjCounter(this, b);
		if ((b != 0) || (this.ui == null))
			return;
		this.ui.installUI(this);
	}

	protected String paramString()
	{
		String str1 = (this.text != null) ? this.text : "";
		String str2 = ((this.defaultIcon != null) && (this.defaultIcon != this)) ? this.defaultIcon.toString() : "";
		String str3 = ((this.disabledIcon != null) && (this.disabledIcon != this)) ? this.disabledIcon.toString() : "";
		String str4 = (this.labelFor != null) ? this.labelFor.toString() : "";
		String str5;
		if (this.verticalAlignment == 1)
			str5 = "TOP";
		else if (this.verticalAlignment == 0)
			str5 = "CENTER";
		else if (this.verticalAlignment == 3)
			str5 = "BOTTOM";
		else
			str5 = "";
		String str6;
		if (this.horizontalAlignment == 2)
			str6 = "LEFT";
		else if (this.horizontalAlignment == 0)
			str6 = "CENTER";
		else if (this.horizontalAlignment == 4)
			str6 = "RIGHT";
		else if (this.horizontalAlignment == 10)
			str6 = "LEADING";
		else if (this.horizontalAlignment == 11)
			str6 = "TRAILING";
		else
			str6 = "";
		String str7;
		if (this.verticalTextPosition == 1)
			str7 = "TOP";
		else if (this.verticalTextPosition == 0)
			str7 = "CENTER";
		else if (this.verticalTextPosition == 3)
			str7 = "BOTTOM";
		else
			str7 = "";
		String str8;
		if (this.horizontalTextPosition == 2)
			str8 = "LEFT";
		else if (this.horizontalTextPosition == 0)
			str8 = "CENTER";
		else if (this.horizontalTextPosition == 4)
			str8 = "RIGHT";
		else if (this.horizontalTextPosition == 10)
			str8 = "LEADING";
		else if (this.horizontalTextPosition == 11)
			str8 = "TRAILING";
		else
			str8 = "";
		return /* super.paramString() +*/ ",defaultIcon=" + str2 + ",disabledIcon=" + str3 + ",horizontalAlignment=" + str6 + ",horizontalTextPosition=" + str8
			+ ",iconTextGap=" + this.iconTextGap + ",labelFor=" + str4 + ",text=" + str1 + ",verticalAlignment=" + str5 + ",verticalTextPosition=" + str7;
	}

	public Component getLabelFor()
	{
		return this.labelFor;
	}

	public void setLabelFor(Component paramComponent)
	{
		Component localComponent = this.labelFor;
		this.labelFor = paramComponent;
		firePropertyChange("labelFor", localComponent, paramComponent);
		if (localComponent instanceof JComponent)
			((JComponent) localComponent).putClientProperty("labeledBy", null);
		if (!(paramComponent instanceof JComponent))
			return;
		((JComponent) paramComponent).putClientProperty("labeledBy", this);
	}

	private void firePropertyChange(String text2, Component localComponent, Component paramComponent)
	{
		// TODO Auto-generated method stub
		
	}

//	public AccessibleContext getAccessibleContext()
//	{
//		if (this.accessibleContext == null)
//			this.accessibleContext = new AccessibleJLabel();
//		return this.accessibleContext;
//	}

	protected class AccessibleJLabel // extends JComponent.AccessibleJComponent implements AccessibleText, AccessibleExtendedComponent
	{
		protected AccessibleJLabel()
		{
			// super(JLabel.this);
		}

		public String getAccessibleName()
		{
			String str = null; //this.accessibleName;
//			if (str == null)
//				str = (String) JLabel.this.getClientProperty("AccessibleName");
			if (str == null)
				str = JLabel.this.getText();
//			if (str == null)
//				str = super.getAccessibleName();
			return str;
		}

//		public AccessibleRole getAccessibleRole()
//		{
//			return AccessibleRole.LABEL;
//		}
//
//		public AccessibleIcon[] getAccessibleIcon()
//		{
//			Icon localIcon = JLabel.this.getIcon();
//			if (localIcon instanceof Accessible) {
//				AccessibleContext localAccessibleContext = ((Accessible) localIcon).getAccessibleContext();
//				if ((localAccessibleContext != null) && (localAccessibleContext instanceof AccessibleIcon))
//					return new AccessibleIcon[] { (AccessibleIcon) localAccessibleContext };
//			}
//			return null;
//		}
//
//		public AccessibleRelationSet getAccessibleRelationSet()
//		{
//			AccessibleRelationSet localAccessibleRelationSet = super.getAccessibleRelationSet();
//			if (!(localAccessibleRelationSet.contains(AccessibleRelation.LABEL_FOR))) {
//				Component localComponent = JLabel.this.getLabelFor();
//				if (localComponent != null) {
//					AccessibleRelation localAccessibleRelation = new AccessibleRelation(AccessibleRelation.LABEL_FOR);
//					localAccessibleRelation.setTarget(localComponent);
//					localAccessibleRelationSet.add(localAccessibleRelation);
//				}
//			}
//			return localAccessibleRelationSet;
//		}
//
//		public AccessibleText getAccessibleText()
//		{
//			View localView = (View) JLabel.this.getClientProperty("html");
//			if (localView != null)
//				return this;
//			return null;
//		}
//
//		public int getIndexAtPoint(Point paramPoint)
//		{
//			View localView = (View) JLabel.this.getClientProperty("html");
//			if (localView != null) {
//				Rectangle localRectangle = getTextRectangle();
//				if (localRectangle == null)
//					return -1;
//				Rectangle2D.Float localFloat = new Rectangle2D.Float(localRectangle.x, localRectangle.y, localRectangle.width, localRectangle.height);
//				Position.Bias[] arrayOfBias = new Position.Bias[1];
//				return localView.viewToModel(paramPoint.x, paramPoint.y, localFloat, arrayOfBias);
//			}
//			return -1;
//		}

		public Rectangle getCharacterBounds(int paramInt)
		{
//			View localView = null; // (View) JLabel.this.getClientProperty("html");
//			if (localView != null) {
//				Rectangle localRectangle = getTextRectangle();
//				if (localRectangle == null)
//					return null;
//				Rectangle2D.Float localFloat = new Rectangle2D.Float(localRectangle.x, localRectangle.y, localRectangle.width, localRectangle.height);
//				try {
//					Shape localShape = localView.modelToView(paramInt, localFloat, Position.Bias.Forward);
//					return localShape.getBounds();
//				}
//				catch (Exception localBadLocationException) {
//					return null;
//				}
//			}
			return null;
		}

		public int getCharCount()
		{
//			View localView = (View) JLabel.this.getClientProperty("html");
//			if (localView != null) {
//				Document localDocument = localView.getDocument();
//				if (localDocument instanceof StyledDocument) {
//					StyledDocument localStyledDocument = (StyledDocument) localDocument;
//					return localStyledDocument.getLength();
//				}
//			}
//			return JLabel.this.accessibleContext.getAccessibleName().length();
			return -1;
		}

		public int getCaretPosition()
		{
			return -1;
		}

		public String getAtIndex(int paramInt1, int paramInt2)
		{
			if ((paramInt2 < 0) || (paramInt2 >= getCharCount()))
				return null;
			BreakIterator localBreakIterator;
			int i;
			switch (paramInt1) {
				case 1:
					try {
						return getText(paramInt2, 1);
					}
					catch (Exception localBadLocationException1) {
						return null;
					}
				case 2:
					try {
						String str1 = getText(0, getCharCount());
						localBreakIterator = BreakIterator.getWordInstance(getLocale());
						localBreakIterator.setText(str1);
						i = localBreakIterator.following(paramInt2);
						return str1.substring(localBreakIterator.previous(), i);
					}
					catch (Exception localBadLocationException2) {
						return null;
					}
				case 3:
					try {
						String str2 = getText(0, getCharCount());
						localBreakIterator = BreakIterator.getSentenceInstance(getLocale());
						localBreakIterator.setText(str2);
						i = localBreakIterator.following(paramInt2);
						return str2.substring(localBreakIterator.previous(), i);
					}
					catch (Exception localBadLocationException3) {
						return null;
					}
			}
			return null;
		}

		public String getAfterIndex(int paramInt1, int paramInt2)
		{
			if ((paramInt2 < 0) || (paramInt2 >= getCharCount()))
				return null;
			BreakIterator localBreakIterator;
			int i;
			int j;
			switch (paramInt1) {
				case 1:
					if (paramInt2 + 1 >= getCharCount())
						return null;
					try {
						return getText(paramInt2 + 1, 1);
					}
					catch (Exception localBadLocationException1) {
						return null;
					}
				case 2:
					try {
						String str1 = getText(0, getCharCount());
						localBreakIterator = BreakIterator.getWordInstance(getLocale());
						localBreakIterator.setText(str1);
						i = localBreakIterator.following(paramInt2);
						if ((i == -1) || (i >= str1.length()))
							return null;
						j = localBreakIterator.following(i);
						if ((j == -1) || (j >= str1.length()))
							return null;
						return str1.substring(i, j);
					}
					catch (Exception localBadLocationException2) {
						return null;
					}
				case 3:
					try {
						String str2 = getText(0, getCharCount());
						localBreakIterator = BreakIterator.getSentenceInstance(getLocale());
						localBreakIterator.setText(str2);
						i = localBreakIterator.following(paramInt2);
						if ((i == -1) || (i > str2.length()))
							return null;
						j = localBreakIterator.following(i);
						if ((j == -1) || (j > str2.length()))
							return null;
						return str2.substring(i, j);
					}
					catch (Exception localBadLocationException3) {
						return null;
					}
			}
			return null;
		}

		private Locale getLocale()
		{
			// TODO Auto-generated method stub
			return null;
		}

		public String getBeforeIndex(int paramInt1, int paramInt2)
		{
			if ((paramInt2 < 0) || (paramInt2 > getCharCount() - 1))
				return null;
			BreakIterator localBreakIterator;
			int i;
			int j;
			switch (paramInt1) {
				case 1:
					if (paramInt2 == 0)
						return null;
					try {
						return getText(paramInt2 - 1, 1);
					}
					catch (Exception localBadLocationException1) {
						return null;
					}
				case 2:
					try {
						String str1 = getText(0, getCharCount());
						localBreakIterator = BreakIterator.getWordInstance(getLocale());
						localBreakIterator.setText(str1);
						i = localBreakIterator.following(paramInt2);
						i = localBreakIterator.previous();
						j = localBreakIterator.previous();
						if (j == -1)
							return null;
						return str1.substring(j, i);
					}
					catch (Exception localBadLocationException2) {
						return null;
					}
				case 3:
					try {
						String str2 = getText(0, getCharCount());
						localBreakIterator = BreakIterator.getSentenceInstance(getLocale());
						localBreakIterator.setText(str2);
						i = localBreakIterator.following(paramInt2);
						i = localBreakIterator.previous();
						j = localBreakIterator.previous();
						if (j == -1)
							return null;
						return str2.substring(j, i);
					}
					catch (Exception localBadLocationException3) {
						return null;
					}
			}
			return null;
		}

		public AttributeSet getCharacterAttribute(int paramInt)
		{
//			View localView = (View) JLabel.this.getClientProperty("html");
//			if (localView != null) {
//				Document localDocument = localView.getDocument();
//				if (localDocument instanceof StyledDocument) {
//					StyledDocument localStyledDocument = (StyledDocument) localDocument;
//					Element localElement = localStyledDocument.getCharacterElement(paramInt);
//					if (localElement != null)
//						return localElement.getAttributes();
//				}
//			}
			return null;
		}

		public int getSelectionStart()
		{
			return -1;
		}

		public int getSelectionEnd()
		{
			return -1;
		}

		public String getSelectedText()
		{
			return null;
		}

		private String getText(int paramInt1, int paramInt2)
			// throws Exception
		{
//			View localView = null; //(View) JLabel.this.getClientProperty("html");
//			if (localView != null) {
//				Document localDocument = localView.getDocument();
//				if (localDocument instanceof StyledDocument) {
//					StyledDocument localStyledDocument = (StyledDocument) localDocument;
//					return localStyledDocument.getText(paramInt1, paramInt2);
//				}
//			}
			return null;
		}

		private Rectangle getTextRectangle()
		{
			String str1 = JLabel.this.getText();
			Icon localIcon = (JLabel.this.isEnabled()) ? JLabel.this.getIcon() : JLabel.this.getDisabledIcon();
			if ((localIcon == null) && (str1 == null))
				return null;
			Rectangle localRectangle1 = new Rectangle();
			Rectangle localRectangle2 = new Rectangle();
			Rectangle localRectangle3 = new Rectangle();
			Insets localInsets = new Insets(0, 0, 0, 0);
			// localInsets = JLabel.this.getInsets(localInsets);
			localRectangle3.x = localInsets.left;
			localRectangle3.y = localInsets.top;
			localRectangle3.width = (JLabel.this.getWidth() - (localInsets.left + localInsets.right));
			localRectangle3.height = (JLabel.this.getHeight() - (localInsets.top + localInsets.bottom));
			String str2 = "ok"; //
			// SwingUtilities.layoutCompoundLabel(JLabel.this, getFontMetrics(getFont()), str1, localIcon, JLabel.this.getVerticalAlignment(),
			//	JLabel.this.getHorizontalAlignment(), JLabel.this.getVerticalTextPosition(), JLabel.this.getHorizontalTextPosition(), localRectangle3,
			//	localRectangle1, localRectangle2, JLabel.this.getIconTextGap());
			return localRectangle2;
		}

//		AccessibleExtendedComponent getAccessibleExtendedComponent()
//		{
//			return this;
//		}

		public String getToolTipText()
		{
			return null; //JLabel.this.getToolTipText();
		}

		public String getTitledBorderText()
		{
			return null; //super.getTitledBorderText();
		}

//		public AccessibleKeyBinding getAccessibleKeyBinding()
//		{
//			int i = JLabel.this.getDisplayedMnemonic();
//			if (i == 0)
//				return null;
//			return new LabelKeyBinding(i);
//		}

		class LabelKeyBinding //implements AccessibleKeyBinding
		{
			int mnemonic;

			LabelKeyBinding(int paramInt)
			{
				this.mnemonic = paramInt;
			}

			public int getAccessibleKeyBindingCount()
			{
				return 1;
			}

			public Object getAccessibleKeyBinding(int paramInt)
			{
				if (paramInt != 0)
					throw new IllegalArgumentException();
				return null; //KeyStroke.getKeyStroke(this.mnemonic, 0);
			}
		}
	}
	
	private void firePropertyChange(String text, String str2, String paramString) {
		// cmeng stub
	}
}