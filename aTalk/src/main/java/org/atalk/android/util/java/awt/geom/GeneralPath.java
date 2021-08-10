package org.atalk.android.util.java.awt.geom;

import org.atalk.android.util.java.awt.Shape;

public final class GeneralPath extends Path2D.Float
{
	private static final long serialVersionUID = -8327096662768731142L;

	public GeneralPath() {
		super(1, 20);
	}

	public GeneralPath(int paramInt) {
		super(paramInt, 20);
	}

	public GeneralPath(int paramInt1, int paramInt2) {
		super(paramInt1, paramInt2);
	}

	public GeneralPath(Shape paramShape) {
		super(paramShape, null);
	}

	GeneralPath(int paramInt1, byte[] paramArrayOfByte, int paramInt2, float[] paramArrayOfFloat, int paramInt3) {
		this.windingRule = paramInt1;
		this.pointTypes = paramArrayOfByte;
		this.numTypes = paramInt2;
		this.floatCoords = paramArrayOfFloat;
		this.numCoords = paramInt3;
	}
}