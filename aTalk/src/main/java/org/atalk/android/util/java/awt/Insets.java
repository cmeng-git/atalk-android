package org.atalk.android.util.java.awt;

import java.io.Serializable;

public class Insets implements Cloneable, Serializable {
	public int top;
	public int left;
	public int bottom;
	public int right;
	private static final long serialVersionUID = -2272572637695466749L;

	public Insets(int paramInt1, int paramInt2, int paramInt3, int paramInt4) {
		this.top = paramInt1;
		this.left = paramInt2;
		this.bottom = paramInt3;
		this.right = paramInt4;
	}

	public void set(int paramInt1, int paramInt2, int paramInt3, int paramInt4) {
		this.top = paramInt1;
		this.left = paramInt2;
		this.bottom = paramInt3;
		this.right = paramInt4;
	}

	public boolean equals(Object paramObject) {
		if (paramObject instanceof Insets) {
			Insets localInsets = (Insets) paramObject;
			return ((this.top == localInsets.top)
					&& (this.left == localInsets.left)
					&& (this.bottom == localInsets.bottom) && (this.right == localInsets.right));
		}
		return false;
	}

	public int hashCode() {
		int i = this.left + this.bottom;
		int j = this.right + this.top;
		int k = i * (i + 1) / 2 + this.left;
		int l = j * (j + 1) / 2 + this.top;
		int i1 = k + l;
		return (i1 * (i1 + 1) / 2 + l);
	}

	public String toString() {
		return super.getClass().getName() + "[top=" + this.top + ",left="
				+ this.left + ",bottom=" + this.bottom + ",right=" + this.right
				+ "]";
	}

	public Object clone() {
		try {
			return super.clone();
		} catch (CloneNotSupportedException localCloneNotSupportedException) {
			throw new InternalError();
		}
	}

	private static native void initIDs();

	static {
		Toolkit.loadLibraries();
		initIDs();
	}
}