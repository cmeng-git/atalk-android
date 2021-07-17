package org.atalk.android.util.java.awt;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamField;
import java.io.Serializable;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Vector;

import org.atalk.android.util.javax.swing.JPanel;

public class CardLayout implements LayoutManager2, Serializable {
	private static final long serialVersionUID = -4328196481005934313L;
	Vector vector;
	int currentCard;
	int hgap;
	int vgap;
	private static final ObjectStreamField[] serialPersistentFields = {
			new ObjectStreamField("tab", Hashtable.class),
			new ObjectStreamField("hgap", Integer.TYPE),
			new ObjectStreamField("vgap", Integer.TYPE),
			new ObjectStreamField("vector", Vector.class),
			new ObjectStreamField("currentCard", Integer.TYPE) };

	public CardLayout() {
		this(0, 0);
	}

	public CardLayout(int paramInt1, int paramInt2) {
		this.vector = new Vector();
		this.currentCard = 0;
		this.hgap = paramInt1;
		this.vgap = paramInt2;
	}

	public int getHgap() {
		return this.hgap;
	}

	public void setHgap(int paramInt) {
		this.hgap = paramInt;
	}

	public int getVgap() {
		return this.vgap;
	}

	public void setVgap(int paramInt) {
		this.vgap = paramInt;
	}

	public void addLayoutComponent(Component paramComponent, Object paramObject) {
		synchronized (paramComponent.getTreeLock()) {
			if (paramObject instanceof String)
				addLayoutComponent((String) paramObject, paramComponent);
			else
				throw new IllegalArgumentException(
						"cannot add to layout: constraint must be a string");
		}
	}

	@Deprecated
	public void addLayoutComponent(String paramString, Component paramComponent) {
		synchronized (paramComponent.getTreeLock()) {
			if (!(this.vector.isEmpty()))
				paramComponent.setVisible(false);
			for (int i = 0; i < this.vector.size(); ++i) {
				if (!(((Card) this.vector.get(i)).name.equals(paramString)))
					continue;
				((Card) this.vector.get(i)).comp = paramComponent;
				return;
			}
			this.vector.add(new Card(paramString, paramComponent));
		}
	}

	public void removeLayoutComponent(Component paramComponent) {
		synchronized (paramComponent.getTreeLock()) {
			for (int i = 0; i < this.vector.size(); ++i) {
				if (((Card) this.vector.get(i)).comp != paramComponent)
					continue;
				if ((paramComponent.isVisible())
						&& (paramComponent.getParent() != null))
					next(paramComponent.getParent());
				this.vector.remove(i);
				if (this.currentCard <= i)
					break;
				this.currentCard -= 1;
				break;
			}
		}
	}

	public Dimension preferredLayoutSize(Container paramContainer) {
		synchronized (paramContainer.getTreeLock()) {
			Insets localInsets = paramContainer.getInsets();
			int i = paramContainer.getComponentCount();
			int j = 0;
			int k = 0;
			for (int l = 0; l < i; ++l) {
				Component localComponent = paramContainer.getComponent(l);
				Dimension localDimension = localComponent.getPreferredSize();
				if (localDimension.width > j)
					j = localDimension.width;
				if (localDimension.height <= k)
					continue;
				k = localDimension.height;
			}
			return new Dimension(localInsets.left + localInsets.right + j
					+ this.hgap * 2, localInsets.top + localInsets.bottom + k
					+ this.vgap * 2);
		}
	}

	public Dimension minimumLayoutSize(Container paramContainer) {
		synchronized (paramContainer.getTreeLock()) {
			Insets localInsets = paramContainer.getInsets();
			int i = paramContainer.getComponentCount();
			int j = 0;
			int k = 0;
			for (int l = 0; l < i; ++l) {
				Component localComponent = paramContainer.getComponent(l);
				Dimension localDimension = localComponent.getMinimumSize();
				if (localDimension.width > j)
					j = localDimension.width;
				if (localDimension.height <= k)
					continue;
				k = localDimension.height;
			}
			return new Dimension(localInsets.left + localInsets.right + j
					+ this.hgap * 2, localInsets.top + localInsets.bottom + k
					+ this.vgap * 2);
		}
	}

	public Dimension maximumLayoutSize(Container paramContainer) {
		return new Dimension(2147483647, 2147483647);
	}

	public float getLayoutAlignmentX(Container paramContainer) {
		return 0.5F;
	}

	public float getLayoutAlignmentY(Container paramContainer) {
		return 0.5F;
	}

	public void invalidateLayout(Container paramContainer) {
	}

	public void layoutContainer(Container paramContainer) {
		synchronized (paramContainer.getTreeLock()) {
			Insets localInsets = paramContainer.getInsets();
			int i = paramContainer.getComponentCount();
			Component localComponent = null;
			int j = 0;
			for (int k = 0; k < i; ++k) {
				localComponent = paramContainer.getComponent(k);
				localComponent
						.setBounds(
								this.hgap + localInsets.left,
								this.vgap + localInsets.top,
								paramContainer.width
										- (this.hgap * 2 + localInsets.left + localInsets.right),
								paramContainer.height
										- (this.vgap * 2 + localInsets.top + localInsets.bottom));
				if (!(localComponent.isVisible()))
					continue;
				j = 1;
			}
			if ((j == 0) && (i > 0))
				paramContainer.getComponent(0).setVisible(true);
		}
	}

	void checkLayout(Container paramContainer) {
		if (paramContainer.getLayout() == this)
			return;
		throw new IllegalArgumentException("wrong parent for CardLayout");
	}

	public void first(Container paramContainer) {
		synchronized (paramContainer.getTreeLock()) {
			checkLayout(paramContainer);
			int i = paramContainer.getComponentCount();
			for (int j = 0; j < i; ++j) {
				Component localComponent = paramContainer.getComponent(j);
				if (!(localComponent.isVisible()))
					continue;
				localComponent.setVisible(false);
				break;
			}
			if (i > 0) {
				this.currentCard = 0;
				paramContainer.getComponent(0).setVisible(true);
				paramContainer.validate();
			}
		}
	}

	public void next(Container paramContainer) {
		synchronized (paramContainer.getTreeLock()) {
			checkLayout(paramContainer);
			int i = paramContainer.getComponentCount();
			for (int j = 0; j < i; ++j) {
				Component localComponent = paramContainer.getComponent(j);
				if (!(localComponent.isVisible()))
					continue;
				localComponent.setVisible(false);
				this.currentCard = ((j + 1) % i);
				localComponent = paramContainer.getComponent(this.currentCard);
				localComponent.setVisible(true);
				paramContainer.validate();
				return;
			}
			showDefaultComponent(paramContainer);
		}
	}

	public void previous(Container paramContainer) {
		synchronized (paramContainer.getTreeLock()) {
			checkLayout(paramContainer);
			int i = paramContainer.getComponentCount();
			for (int j = 0; j < i; ++j) {
				Component localComponent = paramContainer.getComponent(j);
				if (!(localComponent.isVisible()))
					continue;
				localComponent.setVisible(false);
				this.currentCard = ((j > 0) ? j - 1 : i - 1);
				localComponent = paramContainer.getComponent(this.currentCard);
				localComponent.setVisible(true);
				paramContainer.validate();
				return;
			}
			showDefaultComponent(paramContainer);
		}
	}

	void showDefaultComponent(Container paramContainer) {
		if (paramContainer.getComponentCount() <= 0)
			return;
		this.currentCard = 0;
		paramContainer.getComponent(0).setVisible(true);
		paramContainer.validate();
	}

	public void last(Container paramContainer) {
		synchronized (paramContainer.getTreeLock()) {
			checkLayout(paramContainer);
			int i = paramContainer.getComponentCount();
			for (int j = 0; j < i; ++j) {
				Component localComponent = paramContainer.getComponent(j);
				if (!(localComponent.isVisible()))
					continue;
				localComponent.setVisible(false);
				break;
			}
			if (i > 0) {
				this.currentCard = (i - 1);
				paramContainer.getComponent(this.currentCard).setVisible(true);
				paramContainer.validate();
			}
		}
	}

	public void show(Container paramContainer, String paramString) {
		synchronized (paramContainer.getTreeLock()) {
			checkLayout(paramContainer);
			Component localComponent = null;
			int i = this.vector.size();
			Object localObject1;
			for (int j = 0; j < i; ++j) {
				localObject1 = (Card) this.vector.get(j);
				if (!(((Card) localObject1).name.equals(paramString)))
					continue;
				localComponent = ((Card) localObject1).comp;
				this.currentCard = j;
				break;
			}
			if ((localComponent != null) && (!(localComponent.isVisible()))) {
				i = paramContainer.getComponentCount();
				for (int j = 0; j < i; ++j) {
					localObject1 = paramContainer.getComponent(j);
					if (!(((Component) localObject1).isVisible()))
						continue;
					((Component) localObject1).setVisible(false);
					break;
				}
				localComponent.setVisible(true);
				paramContainer.validate();
			}
		}
	}

	public String toString() {
		return super.getClass().getName() + "[hgap=" + this.hgap + ",vgap="
				+ this.vgap + "]";
	}

	private void readObject(ObjectInputStream paramObjectInputStream)
			throws ClassNotFoundException, IOException {
		ObjectInputStream.GetField localGetField = paramObjectInputStream
				.readFields();
		this.hgap = localGetField.get("hgap", 0);
		this.vgap = localGetField.get("vgap", 0);
		if (localGetField.defaulted("vector")) {
			Hashtable localHashtable = (Hashtable) localGetField.get("tab",
					null);
			this.vector = new Vector();
			if ((localHashtable != null) && (!(localHashtable.isEmpty()))) {
				Enumeration localEnumeration = localHashtable.keys();
				while (localEnumeration.hasMoreElements()) {
					String str = (String) localEnumeration.nextElement();
					Component localComponent = (Component) localHashtable
							.get(str);
					this.vector.add(new Card(str, localComponent));
					if (localComponent.isVisible())
						this.currentCard = (this.vector.size() - 1);
				}
			}
		} else {
			this.vector = ((Vector) localGetField.get("vector", null));
			this.currentCard = localGetField.get("currentCard", 0);
		}
	}

	private void writeObject(ObjectOutputStream paramObjectOutputStream)
			throws IOException {
		Hashtable localHashtable = new Hashtable();
		int i = this.vector.size();
		for (int j = 0; j < i; ++j) {
			Card localCard = (Card) this.vector.get(j);
			localHashtable.put(localCard.name, localCard.comp);
		}
		ObjectOutputStream.PutField localPutField = paramObjectOutputStream
				.putFields();
		localPutField.put("hgap", this.hgap);
		localPutField.put("vgap", this.vgap);
		localPutField.put("vector", this.vector);
		localPutField.put("currentCard", this.currentCard);
		localPutField.put("tab", localHashtable);
		paramObjectOutputStream.writeFields();
	}

	class Card implements Serializable {
		static final long serialVersionUID = 6640330810709497518L;
		public String name;
		public Component comp;

		public Card(String paramString, Component paramComponent) {
			this.name = paramString;
			this.comp = paramComponent;
		}
	}

	public void show(JPanel authenticationPanel, String item) {
		// TODO Auto-generated method stub
		
	}

}
