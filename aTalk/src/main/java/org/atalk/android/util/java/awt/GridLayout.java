package org.atalk.android.util.java.awt;

import java.io.Serializable;

public class GridLayout implements LayoutManager, Serializable {
	private static final long serialVersionUID = -7411804673224730901L;
	int hgap;
	int vgap;
	int rows;
	int cols;

	public GridLayout() {
		this(1, 0, 0, 0);
	}

	public GridLayout(int paramInt1, int paramInt2) {
		this(paramInt1, paramInt2, 0, 0);
	}

	public GridLayout(int paramInt1, int paramInt2, int paramInt3, int paramInt4) {
		if ((paramInt1 == 0) && (paramInt2 == 0))
			throw new IllegalArgumentException(
					"rows and cols cannot both be zero");
		this.rows = paramInt1;
		this.cols = paramInt2;
		this.hgap = paramInt3;
		this.vgap = paramInt4;
	}
}
