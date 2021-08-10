package org.atalk.android.util.java.awt.geom;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;

import org.atalk.android.util.java.awt.Shape;

public class AffineTransform implements Cloneable, Serializable
{
	private static final int TYPE_UNKNOWN = -1;
	public static final int TYPE_IDENTITY = 0;
	public static final int TYPE_TRANSLATION = 1;
	public static final int TYPE_UNIFORM_SCALE = 2;
	public static final int TYPE_GENERAL_SCALE = 4;
	public static final int TYPE_MASK_SCALE = 6;
	public static final int TYPE_FLIP = 64;
	public static final int TYPE_QUADRANT_ROTATION = 8;
	public static final int TYPE_GENERAL_ROTATION = 16;
	public static final int TYPE_MASK_ROTATION = 24;
	public static final int TYPE_GENERAL_TRANSFORM = 32;
	static final int APPLY_IDENTITY = 0;
	static final int APPLY_TRANSLATE = 1;
	static final int APPLY_SCALE = 2;
	static final int APPLY_SHEAR = 4;
	private static final int HI_SHIFT = 3;
	private static final int HI_IDENTITY = 0;
	private static final int HI_TRANSLATE = 8;
	private static final int HI_SCALE = 16;
	private static final int HI_SHEAR = 32;
	double m00;
	double m10;
	double m01;
	double m11;
	double m02;
	double m12;
	transient int state;
	private transient int type;
	private static final int[] rot90conversion = { 4, 5, 4, 5, 2, 3, 6, 7 };
	private static final long serialVersionUID = 1330973210523860834L;

	private AffineTransform(double paramDouble1, double paramDouble2, double paramDouble3, double paramDouble4, double paramDouble5, double paramDouble6,
		int paramInt) {
		this.m00 = paramDouble1;
		this.m10 = paramDouble2;
		this.m01 = paramDouble3;
		this.m11 = paramDouble4;
		this.m02 = paramDouble5;
		this.m12 = paramDouble6;
		this.state = paramInt;
		this.type = -1;
	}

	public AffineTransform() {
		this.m00 = (this.m11 = 1.0D);
	}

	public AffineTransform(AffineTransform paramAffineTransform) {
		this.m00 = paramAffineTransform.m00;
		this.m10 = paramAffineTransform.m10;
		this.m01 = paramAffineTransform.m01;
		this.m11 = paramAffineTransform.m11;
		this.m02 = paramAffineTransform.m02;
		this.m12 = paramAffineTransform.m12;
		this.state = paramAffineTransform.state;
		this.type = paramAffineTransform.type;
	}

	// @ConstructorProperties({ "scaleX", "shearY", "shearX", "scaleY", "translateX", "translateY" })
	public AffineTransform(float paramFloat1, float paramFloat2, float paramFloat3, float paramFloat4, float paramFloat5, float paramFloat6) {
		this.m00 = paramFloat1;
		this.m10 = paramFloat2;
		this.m01 = paramFloat3;
		this.m11 = paramFloat4;
		this.m02 = paramFloat5;
		this.m12 = paramFloat6;
		updateState();
	}

	public AffineTransform(float[] paramArrayOfFloat) {
		this.m00 = paramArrayOfFloat[0];
		this.m10 = paramArrayOfFloat[1];
		this.m01 = paramArrayOfFloat[2];
		this.m11 = paramArrayOfFloat[3];
		if (paramArrayOfFloat.length > 5) {
			this.m02 = paramArrayOfFloat[4];
			this.m12 = paramArrayOfFloat[5];
		}
		updateState();
	}

	public AffineTransform(double paramDouble1, double paramDouble2, double paramDouble3, double paramDouble4, double paramDouble5, double paramDouble6) {
		this.m00 = paramDouble1;
		this.m10 = paramDouble2;
		this.m01 = paramDouble3;
		this.m11 = paramDouble4;
		this.m02 = paramDouble5;
		this.m12 = paramDouble6;
		updateState();
	}

	public AffineTransform(double[] paramArrayOfDouble) {
		this.m00 = paramArrayOfDouble[0];
		this.m10 = paramArrayOfDouble[1];
		this.m01 = paramArrayOfDouble[2];
		this.m11 = paramArrayOfDouble[3];
		if (paramArrayOfDouble.length > 5) {
			this.m02 = paramArrayOfDouble[4];
			this.m12 = paramArrayOfDouble[5];
		}
		updateState();
	}

	public static AffineTransform getTranslateInstance(double paramDouble1, double paramDouble2)
	{
		AffineTransform localAffineTransform = new AffineTransform();
		localAffineTransform.setToTranslation(paramDouble1, paramDouble2);
		return localAffineTransform;
	}

	public static AffineTransform getRotateInstance(double paramDouble)
	{
		AffineTransform localAffineTransform = new AffineTransform();
		localAffineTransform.setToRotation(paramDouble);
		return localAffineTransform;
	}

	public static AffineTransform getRotateInstance(double paramDouble1, double paramDouble2, double paramDouble3)
	{
		AffineTransform localAffineTransform = new AffineTransform();
		localAffineTransform.setToRotation(paramDouble1, paramDouble2, paramDouble3);
		return localAffineTransform;
	}

	public static AffineTransform getRotateInstance(double paramDouble1, double paramDouble2)
	{
		AffineTransform localAffineTransform = new AffineTransform();
		localAffineTransform.setToRotation(paramDouble1, paramDouble2);
		return localAffineTransform;
	}

	public static AffineTransform getRotateInstance(double paramDouble1, double paramDouble2, double paramDouble3, double paramDouble4)
	{
		AffineTransform localAffineTransform = new AffineTransform();
		localAffineTransform.setToRotation(paramDouble1, paramDouble2, paramDouble3, paramDouble4);
		return localAffineTransform;
	}

	public static AffineTransform getQuadrantRotateInstance(int paramInt)
	{
		AffineTransform localAffineTransform = new AffineTransform();
		localAffineTransform.setToQuadrantRotation(paramInt);
		return localAffineTransform;
	}

	public static AffineTransform getQuadrantRotateInstance(int paramInt, double paramDouble1, double paramDouble2)
	{
		AffineTransform localAffineTransform = new AffineTransform();
		localAffineTransform.setToQuadrantRotation(paramInt, paramDouble1, paramDouble2);
		return localAffineTransform;
	}

	public static AffineTransform getScaleInstance(double paramDouble1, double paramDouble2)
	{
		AffineTransform localAffineTransform = new AffineTransform();
		localAffineTransform.setToScale(paramDouble1, paramDouble2);
		return localAffineTransform;
	}

	public static AffineTransform getShearInstance(double paramDouble1, double paramDouble2)
	{
		AffineTransform localAffineTransform = new AffineTransform();
		localAffineTransform.setToShear(paramDouble1, paramDouble2);
		return localAffineTransform;
	}

	public int getType()
	{
		if (this.type == -1)
			calculateType();
		return this.type;
	}

	private void calculateType()
	{
		int i = 0;
		updateState();
		double d1;
		double d2;
		int j;
		int k;
		switch (this.state) {
			default:
				stateError();
			case 7:
				i = 1;
			case 6:
				d1 = this.m00;
				double d3 = this.m01;
				double d4 = this.m10;
				if (d1 * d3 + d4 * (d2 = this.m11) != 0.0D) {
					this.type = 32;
					return;
				}
				j = (d1 >= 0.0D) ? 1 : 0;
				k = (d2 >= 0.0D) ? 1 : 0;
				if (j == k)
					if ((d1 != d2) || (d3 != -d4))
						i |= 20;
					else if (d1 * d2 - (d3 * d4) != 1.0D)
						i |= 18;
					else
						i |= 16;
				else if ((d1 != -d2) || (d3 != d4))
					i |= 84;
				else if (d1 * d2 - (d3 * d4) != 1.0D)
					i |= 82;
				else
					i |= 80;
				break;
			case 5:
				i = 1;
			case 4:
				j = ((d1 = this.m01) >= 0.0D) ? 1 : 0;
				k = ((d2 = this.m10) >= 0.0D) ? 1 : 0;
				if (j != k)
					if (d1 != -d2)
						i |= 12;
					else if ((d1 != 1.0D) && (d1 != -1.0D))
						i |= 10;
					else
						i |= 8;
				else if (d1 == d2)
					i |= 74;
				else
					i |= 76;
				break;
			case 3:
				i = 1;
			case 2:
				j = ((d1 = this.m00) >= 0.0D) ? 1 : 0;
				k = ((d2 = this.m11) >= 0.0D) ? 1 : 0;
				if (j == k)
					if (j != 0)
						if (d1 == d2)
							i |= 2;
						else
							i |= 4;
					else if (d1 != d2)
						i |= 12;
					else if (d1 != -1.0D)
						i |= 10;
					else
						i |= 8;
				else if (d1 == -d2)
					if ((d1 == 1.0D) || (d1 == -1.0D))
						i |= 64;
					else
						i |= 66;
				else
					i |= 68;
				break;
			case 1:
				i = 1;
			case 0:
		}
		this.type = i;
	}

	public double getDeterminant()
	{
		switch (this.state) {
			default:
				stateError();
			case 6:
			case 7:
				return (this.m00 * this.m11 - (this.m01 * this.m10));
			case 4:
			case 5:
				return (-(this.m01 * this.m10));
			case 2:
			case 3:
				return (this.m00 * this.m11);
			case 0:
			case 1:
		}
		return 1.0D;
	}

	void updateState()
	{
		if ((this.m01 == 0.0D) && (this.m10 == 0.0D)) {
			if ((this.m00 == 1.0D) && (this.m11 == 1.0D)) {
				if ((this.m02 == 0.0D) && (this.m12 == 0.0D)) {
					this.state = 0;
					this.type = 0;
				}
				else {
					this.state = 1;
					this.type = 1;
				}
			}
			else if ((this.m02 == 0.0D) && (this.m12 == 0.0D)) {
				this.state = 2;
				this.type = -1;
			}
			else {
				this.state = 3;
				this.type = -1;
			}
		}
		else if ((this.m00 == 0.0D) && (this.m11 == 0.0D)) {
			if ((this.m02 == 0.0D) && (this.m12 == 0.0D)) {
				this.state = 4;
				this.type = -1;
			}
			else {
				this.state = 5;
				this.type = -1;
			}
		}
		else if ((this.m02 == 0.0D) && (this.m12 == 0.0D)) {
			this.state = 6;
			this.type = -1;
		}
		else {
			this.state = 7;
			this.type = -1;
		}
	}

	private void stateError()
	{
		throw new InternalError("missing case in transform state switch");
	}

	public void getMatrix(double[] paramArrayOfDouble)
	{
		paramArrayOfDouble[0] = this.m00;
		paramArrayOfDouble[1] = this.m10;
		paramArrayOfDouble[2] = this.m01;
		paramArrayOfDouble[3] = this.m11;
		if (paramArrayOfDouble.length <= 5)
			return;
		paramArrayOfDouble[4] = this.m02;
		paramArrayOfDouble[5] = this.m12;
	}

	public double getScaleX()
	{
		return this.m00;
	}

	public double getScaleY()
	{
		return this.m11;
	}

	public double getShearX()
	{
		return this.m01;
	}

	public double getShearY()
	{
		return this.m10;
	}

	public double getTranslateX()
	{
		return this.m02;
	}

	public double getTranslateY()
	{
		return this.m12;
	}

	public void translate(double paramDouble1, double paramDouble2)
	{
		switch (this.state) {
			default:
				stateError();
			case 7:
				this.m02 = (paramDouble1 * this.m00 + paramDouble2 * this.m01 + this.m02);
				this.m12 = (paramDouble1 * this.m10 + paramDouble2 * this.m11 + this.m12);
				if ((this.m02 == 0.0D) && (this.m12 == 0.0D)) {
					this.state = 6;
					if (this.type != -1)
						this.type -= 1;
				}
				return;
			case 6:
				this.m02 = (paramDouble1 * this.m00 + paramDouble2 * this.m01);
				this.m12 = (paramDouble1 * this.m10 + paramDouble2 * this.m11);
				if ((this.m02 != 0.0D) || (this.m12 != 0.0D)) {
					this.state = 7;
					this.type |= 1;
				}
				return;
			case 5:
				this.m02 = (paramDouble2 * this.m01 + this.m02);
				this.m12 = (paramDouble1 * this.m10 + this.m12);
				if ((this.m02 == 0.0D) && (this.m12 == 0.0D)) {
					this.state = 4;
					if (this.type != -1)
						this.type -= 1;
				}
				return;
			case 4:
				this.m02 = (paramDouble2 * this.m01);
				this.m12 = (paramDouble1 * this.m10);
				if ((this.m02 != 0.0D) || (this.m12 != 0.0D)) {
					this.state = 5;
					this.type |= 1;
				}
				return;
			case 3:
				this.m02 = (paramDouble1 * this.m00 + this.m02);
				this.m12 = (paramDouble2 * this.m11 + this.m12);
				if ((this.m02 == 0.0D) && (this.m12 == 0.0D)) {
					this.state = 2;
					if (this.type != -1)
						this.type -= 1;
				}
				return;
			case 2:
				this.m02 = (paramDouble1 * this.m00);
				this.m12 = (paramDouble2 * this.m11);
				if ((this.m02 != 0.0D) || (this.m12 != 0.0D)) {
					this.state = 3;
					this.type |= 1;
				}
				return;
			case 1:
				this.m02 = (paramDouble1 + this.m02);
				this.m12 = (paramDouble2 + this.m12);
				if ((this.m02 == 0.0D) && (this.m12 == 0.0D)) {
					this.state = 0;
					this.type = 0;
				}
				return;
			case 0:
		}
		this.m02 = paramDouble1;
		this.m12 = paramDouble2;
		if ((paramDouble1 == 0.0D) && (paramDouble2 == 0.0D))
			return;
		this.state = 1;
		this.type = 1;
	}

	private final void rotate90()
	{
		double d = this.m00;
		this.m00 = this.m01;
		this.m01 = (-d);
		d = this.m10;
		this.m10 = this.m11;
		this.m11 = (-d);
		int i = rot90conversion[this.state];
		if (((i & 0x6) == 2) && (this.m00 == 1.0D) && (this.m11 == 1.0D))
			;
		this.state = (i -= 2);
		this.type = -1;
	}

	private final void rotate180()
	{
		this.m00 = (-this.m00);
		this.m11 = (-this.m11);
		int i = this.state;
		if ((i & 0x4) != 0) {
			this.m01 = (-this.m01);
			this.m10 = (-this.m10);
		}
		else if ((this.m00 == 1.0D) && (this.m11 == 1.0D)) {
			this.state = (i & 0xFFFFFFFD);
		}
		else {
			this.state = (i | 0x2);
		}
		this.type = -1;
	}

	private final void rotate270()
	{
		double d = this.m00;
		this.m00 = (-this.m01);
		this.m01 = d;
		d = this.m10;
		this.m10 = (-this.m11);
		this.m11 = d;
		int i = rot90conversion[this.state];
		if (((i & 0x6) == 2) && (this.m00 == 1.0D) && (this.m11 == 1.0D))
			;
		this.state = (i -= 2);
		this.type = -1;
	}

	public void rotate(double paramDouble)
	{
		double d1 = Math.sin(paramDouble);
		if (d1 == 1.0D) {
			rotate90();
		}
		else if (d1 == -1.0D) {
			rotate270();
		}
		else {
			double d2 = Math.cos(paramDouble);
			if (d2 == -1.0D) {
				rotate180();
			}
			else {
				if (d2 == 1.0D)
					return;
				double d3 = this.m00;
				double d4 = this.m01;
				this.m00 = (d2 * d3 + d1 * d4);
				this.m01 = (-d1 * d3 + d2 * d4);
				d3 = this.m10;
				d4 = this.m11;
				this.m10 = (d2 * d3 + d1 * d4);
				this.m11 = (-d1 * d3 + d2 * d4);
				updateState();
			}
		}
	}

	public void rotate(double paramDouble1, double paramDouble2, double paramDouble3)
	{
		translate(paramDouble2, paramDouble3);
		rotate(paramDouble1);
		translate(-paramDouble2, -paramDouble3);
	}

	public void rotate(double paramDouble1, double paramDouble2)
	{
		if (paramDouble2 == 0.0D) {
			if (paramDouble1 >= 0.0D)
				return;
			rotate180();
		}
		else if (paramDouble1 == 0.0D) {
			if (paramDouble2 > 0.0D)
				rotate90();
			else
				rotate270();
		}
		else {
			double d1 = Math.sqrt(paramDouble1 * paramDouble1 + paramDouble2 * paramDouble2);
			double d2 = paramDouble2 / d1;
			double d3 = paramDouble1 / d1;
			double d4 = this.m00;
			double d5 = this.m01;
			this.m00 = (d3 * d4 + d2 * d5);
			this.m01 = (-d2 * d4 + d3 * d5);
			d4 = this.m10;
			d5 = this.m11;
			this.m10 = (d3 * d4 + d2 * d5);
			this.m11 = (-d2 * d4 + d3 * d5);
			updateState();
		}
	}

	public void rotate(double paramDouble1, double paramDouble2, double paramDouble3, double paramDouble4)
	{
		translate(paramDouble3, paramDouble4);
		rotate(paramDouble1, paramDouble2);
		translate(-paramDouble3, -paramDouble4);
	}

	public void quadrantRotate(int paramInt)
	{
		switch (paramInt & 0x3) {
			case 0:
				break;
			case 1:
				rotate90();
				break;
			case 2:
				rotate180();
				break;
			case 3:
				rotate270();
		}
	}

	public void quadrantRotate(int paramInt, double paramDouble1, double paramDouble2)
	{
		switch (paramInt & 0x3) {
			case 0:
				return;
			case 1:
				this.m02 += paramDouble1 * (this.m00 - this.m01) + paramDouble2 * (this.m01 + this.m00);
				this.m12 += paramDouble1 * (this.m10 - this.m11) + paramDouble2 * (this.m11 + this.m10);
				rotate90();
				break;
			case 2:
				this.m02 += paramDouble1 * (this.m00 + this.m00) + paramDouble2 * (this.m01 + this.m01);
				this.m12 += paramDouble1 * (this.m10 + this.m10) + paramDouble2 * (this.m11 + this.m11);
				rotate180();
				break;
			case 3:
				this.m02 += paramDouble1 * (this.m00 + this.m01) + paramDouble2 * (this.m01 - this.m00);
				this.m12 += paramDouble1 * (this.m10 + this.m11) + paramDouble2 * (this.m11 - this.m10);
				rotate270();
		}
		if ((this.m02 == 0.0D) && (this.m12 == 0.0D))
			this.state &= -2;
		else
			this.state |= 1;
	}

	public void scale(double paramDouble1, double paramDouble2)
	{
		int i = this.state;
		switch (i) {
			default:
				stateError();
			case 6:
			case 7:
				this.m00 *= paramDouble1;
				this.m11 *= paramDouble2;
			case 4:
			case 5:
				this.m01 *= paramDouble2;
				this.m10 *= paramDouble1;
				if ((this.m01 == 0.0D) && (this.m10 == 0.0D)) {
					i &= 1;
					if ((this.m00 == 1.0D) && (this.m11 == 1.0D)) {
						this.type = ((i == 0) ? 0 : 1);
					}
					else {
						i |= 2;
						this.type = -1;
					}
					this.state = i;
				}
				return;
			case 2:
			case 3:
				this.m00 *= paramDouble1;
				this.m11 *= paramDouble2;
				if ((this.m00 == 1.0D) && (this.m11 == 1.0D)) {
					this.state = (i &= 1);
					this.type = ((i == 0) ? 0 : 1);
				}
				else {
					this.type = -1;
				}
				return;
			case 0:
			case 1:
		}
		this.m00 = paramDouble1;
		this.m11 = paramDouble2;
		if ((paramDouble1 == 1.0D) && (paramDouble2 == 1.0D))
			return;
		this.state = (i | 0x2);
		this.type = -1;
	}

	public void shear(double paramDouble1, double paramDouble2)
	{
		int i = this.state;
		switch (i) {
			default:
				stateError();
			case 6:
			case 7:
				double d1 = this.m00;
				double d2 = this.m01;
				this.m00 = (d1 + d2 * paramDouble2);
				this.m01 = (d1 * paramDouble1 + d2);
				d1 = this.m10;
				d2 = this.m11;
				this.m10 = (d1 + d2 * paramDouble2);
				this.m11 = (d1 * paramDouble1 + d2);
				updateState();
				return;
			case 4:
			case 5:
				this.m00 = (this.m01 * paramDouble2);
				this.m11 = (this.m10 * paramDouble1);
				if ((this.m00 != 0.0D) || (this.m11 != 0.0D))
					this.state = (i | 0x2);
				this.type = -1;
				return;
			case 2:
			case 3:
				this.m01 = (this.m00 * paramDouble1);
				this.m10 = (this.m11 * paramDouble2);
				if ((this.m01 != 0.0D) || (this.m10 != 0.0D))
					this.state = (i | 0x4);
				this.type = -1;
				return;
			case 0:
			case 1:
		}
		this.m01 = paramDouble1;
		this.m10 = paramDouble2;
		if ((this.m01 == 0.0D) && (this.m10 == 0.0D))
			return;
		this.state = (i | 0x2 | 0x4);
		this.type = -1;
	}

	public void setToIdentity()
	{
		this.m00 = (this.m11 = 1.0D);
		this.m10 = (this.m01 = this.m02 = this.m12 = 0.0D);
		this.state = 0;
		this.type = 0;
	}

	public void setToTranslation(double paramDouble1, double paramDouble2)
	{
		this.m00 = 1.0D;
		this.m10 = 0.0D;
		this.m01 = 0.0D;
		this.m11 = 1.0D;
		this.m02 = paramDouble1;
		this.m12 = paramDouble2;
		if ((paramDouble1 != 0.0D) || (paramDouble2 != 0.0D)) {
			this.state = 1;
			this.type = 1;
		}
		else {
			this.state = 0;
			this.type = 0;
		}
	}

	public void setToRotation(double paramDouble)
	{
		double d1 = Math.sin(paramDouble);
		double d2;
		if ((d1 == 1.0D) || (d1 == -1.0D)) {
			d2 = 0.0D;
			this.state = 4;
			this.type = 8;
		}
		else {
			d2 = Math.cos(paramDouble);
			if (d2 == -1.0D) {
				d1 = 0.0D;
				this.state = 2;
				this.type = 8;
			}
			else if (d2 == 1.0D) {
				d1 = 0.0D;
				this.state = 0;
				this.type = 0;
			}
			else {
				this.state = 6;
				this.type = 16;
			}
		}
		this.m00 = d2;
		this.m10 = d1;
		this.m01 = (-d1);
		this.m11 = d2;
		this.m02 = 0.0D;
		this.m12 = 0.0D;
	}

	public void setToRotation(double paramDouble1, double paramDouble2, double paramDouble3)
	{
		setToRotation(paramDouble1);
		double d1 = this.m10;
		double d2 = 1.0D - this.m00;
		this.m02 = (paramDouble2 * d2 + paramDouble3 * d1);
		this.m12 = (paramDouble3 * d2 - (paramDouble2 * d1));
		if ((this.m02 == 0.0D) && (this.m12 == 0.0D))
			return;
		this.state |= 1;
		this.type |= 1;
	}

	public void setToRotation(double paramDouble1, double paramDouble2)
	{
		double d1;
		double d2;
		if (paramDouble2 == 0.0D) {
			d1 = 0.0D;
			if (paramDouble1 < 0.0D) {
				d2 = -1.0D;
				this.state = 2;
				this.type = 8;
			}
			else {
				d2 = 1.0D;
				this.state = 0;
				this.type = 0;
			}
		}
		else if (paramDouble1 == 0.0D) {
			d2 = 0.0D;
			d1 = (paramDouble2 > 0.0D) ? 1.0D : -1.0D;
			this.state = 4;
			this.type = 8;
		}
		else {
			double d3 = Math.sqrt(paramDouble1 * paramDouble1 + paramDouble2 * paramDouble2);
			d2 = paramDouble1 / d3;
			d1 = paramDouble2 / d3;
			this.state = 6;
			this.type = 16;
		}
		this.m00 = d2;
		this.m10 = d1;
		this.m01 = (-d1);
		this.m11 = d2;
		this.m02 = 0.0D;
		this.m12 = 0.0D;
	}

	public void setToRotation(double paramDouble1, double paramDouble2, double paramDouble3, double paramDouble4)
	{
		setToRotation(paramDouble1, paramDouble2);
		double d1 = this.m10;
		double d2 = 1.0D - this.m00;
		this.m02 = (paramDouble3 * d2 + paramDouble4 * d1);
		this.m12 = (paramDouble4 * d2 - (paramDouble3 * d1));
		if ((this.m02 == 0.0D) && (this.m12 == 0.0D))
			return;
		this.state |= 1;
		this.type |= 1;
	}

	public void setToQuadrantRotation(int paramInt)
	{
		switch (paramInt & 0x3) {
			case 0:
				this.m00 = 1.0D;
				this.m10 = 0.0D;
				this.m01 = 0.0D;
				this.m11 = 1.0D;
				this.m02 = 0.0D;
				this.m12 = 0.0D;
				this.state = 0;
				this.type = 0;
				break;
			case 1:
				this.m00 = 0.0D;
				this.m10 = 1.0D;
				this.m01 = -1.0D;
				this.m11 = 0.0D;
				this.m02 = 0.0D;
				this.m12 = 0.0D;
				this.state = 4;
				this.type = 8;
				break;
			case 2:
				this.m00 = -1.0D;
				this.m10 = 0.0D;
				this.m01 = 0.0D;
				this.m11 = -1.0D;
				this.m02 = 0.0D;
				this.m12 = 0.0D;
				this.state = 2;
				this.type = 8;
				break;
			case 3:
				this.m00 = 0.0D;
				this.m10 = -1.0D;
				this.m01 = 1.0D;
				this.m11 = 0.0D;
				this.m02 = 0.0D;
				this.m12 = 0.0D;
				this.state = 4;
				this.type = 8;
		}
	}

	public void setToQuadrantRotation(int paramInt, double paramDouble1, double paramDouble2)
	{
		switch (paramInt & 0x3) {
			case 0:
				this.m00 = 1.0D;
				this.m10 = 0.0D;
				this.m01 = 0.0D;
				this.m11 = 1.0D;
				this.m02 = 0.0D;
				this.m12 = 0.0D;
				this.state = 0;
				this.type = 0;
				break;
			case 1:
				this.m00 = 0.0D;
				this.m10 = 1.0D;
				this.m01 = -1.0D;
				this.m11 = 0.0D;
				this.m02 = (paramDouble1 + paramDouble2);
				this.m12 = (paramDouble2 - paramDouble1);
				if ((this.m02 == 0.0D) && (this.m12 == 0.0D)) {
					this.state = 4;
					this.type = 8;
					return;
				}
				this.state = 5;
				this.type = 9;
				break;
			case 2:
				this.m00 = -1.0D;
				this.m10 = 0.0D;
				this.m01 = 0.0D;
				this.m11 = -1.0D;
				this.m02 = (paramDouble1 + paramDouble1);
				this.m12 = (paramDouble2 + paramDouble2);
				if ((this.m02 == 0.0D) && (this.m12 == 0.0D)) {
					this.state = 2;
					this.type = 8;
					return;
				}
				this.state = 3;
				this.type = 9;
				break;
			case 3:
				this.m00 = 0.0D;
				this.m10 = -1.0D;
				this.m01 = 1.0D;
				this.m11 = 0.0D;
				this.m02 = (paramDouble1 - paramDouble2);
				this.m12 = (paramDouble2 + paramDouble1);
				if ((this.m02 == 0.0D) && (this.m12 == 0.0D)) {
					this.state = 4;
					this.type = 8;
					return;
				}
				this.state = 5;
				this.type = 9;
		}
	}

	public void setToScale(double paramDouble1, double paramDouble2)
	{
		this.m00 = paramDouble1;
		this.m10 = 0.0D;
		this.m01 = 0.0D;
		this.m11 = paramDouble2;
		this.m02 = 0.0D;
		this.m12 = 0.0D;
		if ((paramDouble1 != 1.0D) || (paramDouble2 != 1.0D)) {
			this.state = 2;
			this.type = -1;
		}
		else {
			this.state = 0;
			this.type = 0;
		}
	}

	public void setToShear(double paramDouble1, double paramDouble2)
	{
		this.m00 = 1.0D;
		this.m01 = paramDouble1;
		this.m10 = paramDouble2;
		this.m11 = 1.0D;
		this.m02 = 0.0D;
		this.m12 = 0.0D;
		if ((paramDouble1 != 0.0D) || (paramDouble2 != 0.0D)) {
			this.state = 6;
			this.type = -1;
		}
		else {
			this.state = 0;
			this.type = 0;
		}
	}

	public void setTransform(AffineTransform paramAffineTransform)
	{
		this.m00 = paramAffineTransform.m00;
		this.m10 = paramAffineTransform.m10;
		this.m01 = paramAffineTransform.m01;
		this.m11 = paramAffineTransform.m11;
		this.m02 = paramAffineTransform.m02;
		this.m12 = paramAffineTransform.m12;
		this.state = paramAffineTransform.state;
		this.type = paramAffineTransform.type;
	}

	public void setTransform(double paramDouble1, double paramDouble2, double paramDouble3, double paramDouble4, double paramDouble5, double paramDouble6)
	{
		this.m00 = paramDouble1;
		this.m10 = paramDouble2;
		this.m01 = paramDouble3;
		this.m11 = paramDouble4;
		this.m02 = paramDouble5;
		this.m12 = paramDouble6;
		updateState();
	}

	public void concatenate(AffineTransform paramAffineTransform)
	{
		int i = this.state;
		int j = paramAffineTransform.state;
		double d1, d2, d3, d4, d5, d6, d7, d8;
		switch (j << 3 | i) {
			case 0:
			case 1:
			case 2:
			case 3:
			case 4:
			case 5:
			case 6:
			case 7:
				return;
			case 56:
				this.m01 = paramAffineTransform.m01;
				this.m10 = paramAffineTransform.m10;
			case 24:
				this.m00 = paramAffineTransform.m00;
				this.m11 = paramAffineTransform.m11;
			case 8:
				this.m02 = paramAffineTransform.m02;
				this.m12 = paramAffineTransform.m12;
				this.state = j;
				this.type = paramAffineTransform.type;
				return;
			case 48:
				this.m01 = paramAffineTransform.m01;
				this.m10 = paramAffineTransform.m10;
			case 16:
				this.m00 = paramAffineTransform.m00;
				this.m11 = paramAffineTransform.m11;
				this.state = j;
				this.type = paramAffineTransform.type;
				return;
			case 40:
				this.m02 = paramAffineTransform.m02;
				this.m12 = paramAffineTransform.m12;
			case 32:
				this.m01 = paramAffineTransform.m01;
				this.m10 = paramAffineTransform.m10;
				this.m00 = (this.m11 = 0.0D);
				this.state = j;
				this.type = paramAffineTransform.type;
				return;
			case 9:
			case 10:
			case 11:
			case 12:
			case 13:
			case 14:
			case 15:
				translate(paramAffineTransform.m02, paramAffineTransform.m12);
				return;
			case 17:
			case 18:
			case 19:
			case 20:
			case 21:
			case 22:
			case 23:
				scale(paramAffineTransform.m00, paramAffineTransform.m11);
				return;
			case 38:
			case 39:
				d4  = paramAffineTransform.m01;
				d5 = paramAffineTransform.m10;
				d1 = this.m00;
				this.m00 = (this.m01 * d5);
				this.m01 = (d1 * d4);
				d1 = this.m10;
				this.m10 = (this.m11 * d5);
				this.m11 = (d1 * d4);
				this.type = -1;
				return;
			case 36:
			case 37:
				this.m00 = (this.m01 * paramAffineTransform.m10);
				this.m01 = 0.0D;
				this.m11 = (this.m10 * paramAffineTransform.m01);
				this.m10 = 0.0D;
				this.state = (i ^ 0x6);
				this.type = -1;
				return;
			case 34:
			case 35:
				this.m01 = (this.m00 * paramAffineTransform.m01);
				this.m00 = 0.0D;
				this.m10 = (this.m11 * paramAffineTransform.m10);
				this.m11 = 0.0D;
				this.state = (i ^ 0x6);
				this.type = -1;
				return;
			case 33:
				this.m00 = 0.0D;
				this.m01 = paramAffineTransform.m01;
				this.m10 = paramAffineTransform.m10;
				this.m11 = 0.0D;
				this.state = 5;
				this.type = -1;
				return;
			case 25:
			case 26:
			case 27:
			case 28:
			case 29:
			case 30:
			case 31:
			case 41:
			case 42:
			case 43:
			case 44:
			case 45:
			case 46:
			case 47:
			case 49:
			case 50:
			case 51:
			case 52:
			case 53:
			case 54:
			case 55:
		}
		d3 = paramAffineTransform.m00;
		d4 = paramAffineTransform.m01;
		d7 = paramAffineTransform.m02;
		d5 = paramAffineTransform.m10;
		d6 = paramAffineTransform.m11;
		d8 = paramAffineTransform.m12;
		switch (i) {
			default:
				stateError();
			case 6:
				this.state = (i | j);
			case 7:
				d1 = this.m00;
				d2 = this.m01;
				this.m00 = (d3 * d1 + d5 * d2);
				this.m01 = (d4 * d1 + d6 * d2);
				this.m02 += d7 * d1 + d8 * d2;
				d1 = this.m10;
				d2 = this.m11;
				this.m10 = (d3 * d1 + d5 * d2);
				this.m11 = (d4 * d1 + d6 * d2);
				this.m12 += d7 * d1 + d8 * d2;
				this.type = -1;
				return;
			case 4:
			case 5:
				d1 = this.m01;
				this.m00 = (d5 * d1);
				this.m01 = (d6 * d1);
				this.m02 += d8 * d1;
				d1 = this.m10;
				this.m10 = (d3 * d1);
				this.m11 = (d4 * d1);
				this.m12 += d7 * d1;
				break;
			case 2:
			case 3:
				d1 = this.m00;
				this.m00 = (d3 * d1);
				this.m01 = (d4 * d1);
				this.m02 += d7 * d1;
				d1 = this.m11;
				this.m10 = (d5 * d1);
				this.m11 = (d6 * d1);
				this.m12 += d8 * d1;
				break;
			case 1:
				this.m00 = d3;
				this.m01 = d4;
				this.m02 += d7;
				this.m10 = d5;
				this.m11 = d6;
				this.m12 += d8;
				this.state = (j | 0x1);
				this.type = -1;
				return;
		}
		updateState();
	}

	public void preConcatenate(AffineTransform paramAffineTransform)
	{
		int i = this.state;
		int j = paramAffineTransform.state;
		double d1, d2, d3, d4, d5, d6, d7, d8;
		switch (j << 3 | i) {
			case 0:
			case 1:
			case 2:
			case 3:
			case 4:
			case 5:
			case 6:
			case 7:
				return;
			case 8:
			case 10:
			case 12:
			case 14:
				this.m02 = paramAffineTransform.m02;
				this.m12 = paramAffineTransform.m12;
				this.state = (i | 0x1);
				this.type |= 1;
				return;
			case 9:
			case 11:
			case 13:
			case 15:
				this.m02 += paramAffineTransform.m02;
				this.m12 += paramAffineTransform.m12;
				return;
			case 16:
			case 17:
				this.state = (i | 0x2);
			case 18:
			case 19:
			case 20:
			case 21:
			case 22:
			case 23:
				d3 = paramAffineTransform.m00;
				d6 = paramAffineTransform.m11;
				if ((i & 0x4) != 0) {
					this.m01 *= d3;
					this.m10 *= d6;
					if ((i & 0x2) != 0) {
						this.m00 *= d3;
						this.m11 *= d6;
					}
				}
				else {
					this.m00 *= d3;
					this.m11 *= d6;
				}
				if ((i & 0x1) != 0) {
					this.m02 *= d3;
					this.m12 *= d6;
				}
				this.type = -1;
				return;
			case 36:
			case 37:
				i |= 2;
			case 32:
			case 33:
			case 34:
			case 35:
				this.state = (i ^ 0x4);
			case 38:
			case 39:
				d4 = paramAffineTransform.m01;
				d5 = paramAffineTransform.m10;
				d1 = this.m00;
				this.m00 = (this.m10 * d4);
				this.m10 = (d1 * d5);
				d1 = this.m01;
				this.m01 = (this.m11 * d4);
				this.m11 = (d1 * d5);
				d1 = this.m02;
				this.m02 = (this.m12 * d4);
				this.m12 = (d1 * d5);
				this.type = -1;
				return;
			case 24:
			case 25:
			case 26:
			case 27:
			case 28:
			case 29:
			case 30:
			case 31:
		}
		d3 = paramAffineTransform.m00;
		d4 = paramAffineTransform.m01;
		d7 = paramAffineTransform.m02;
		d5 = paramAffineTransform.m10;
		d6 = paramAffineTransform.m11;
		d8 = paramAffineTransform.m12;
		switch (i) {
			default:
				stateError();
			case 7:
				d1 = this.m02;
				d2 = this.m12;
				d7 += d1 * d3 + d2 * d4;
				d8 += d1 * d5 + d2 * d6;
			case 6:
				this.m02 = d7;
				this.m12 = d8;
				d1 = this.m00;
				d2 = this.m10;
				this.m00 = (d1 * d3 + d2 * d4);
				this.m10 = (d1 * d5 + d2 * d6);
				d1 = this.m01;
				d2 = this.m11;
				this.m01 = (d1 * d3 + d2 * d4);
				this.m11 = (d1 * d5 + d2 * d6);
				break;
			case 5:
				d1 = this.m02;
				d2 = this.m12;
				d7 += d1 * d3 + d2 * d4;
				d8 += d1 * d5 + d2 * d6;
			case 4:
				this.m02 = d7;
				this.m12 = d8;
				d1 = this.m10;
				this.m00 = (d1 * d4);
				this.m10 = (d1 * d6);
				d1 = this.m01;
				this.m01 = (d1 * d3);
				this.m11 = (d1 * d5);
				break;
			case 3:
				d1 = this.m02;
				d2 = this.m12;
				d7 += d1 * d3 + d2 * d4;
				d8 += d1 * d5 + d2 * d6;
			case 2:
				this.m02 = d7;
				this.m12 = d8;
				d1 = this.m00;
				this.m00 = (d1 * d3);
				this.m10 = (d1 * d5);
				d1 = this.m11;
				this.m01 = (d1 * d4);
				this.m11 = (d1 * d6);
				break;
			case 1:
				d1 = this.m02;
				d2 = this.m12;
				d7 += d1 * d3 + d2 * d4;
				d8 += d1 * d5 + d2 * d6;
			case 0:
				this.m02 = d7;
				this.m12 = d8;
				this.m00 = d3;
				this.m10 = d5;
				this.m01 = d4;
				this.m11 = d6;
				this.state = (i | j);
				this.type = -1;
				return;
		}
		updateState();
	}

	public AffineTransform createInverse()
		throws NoninvertibleTransformException
	{
		double d;
		switch (this.state) {
			default:
				stateError();
			case 7:
				d = this.m00 * this.m11 - (this.m01 * this.m10);
				if (Math.abs(d) <= 4.9E-324D)
					throw new NoninvertibleTransformException("Determinant is " + d);
				return new AffineTransform(this.m11 / d, -this.m10 / d, -this.m01 / d, this.m00 / d, (this.m01 * this.m12 - (this.m11 * this.m02)) / d,
					(this.m10 * this.m02 - (this.m00 * this.m12)) / d, 7);
			case 6:
				d = this.m00 * this.m11 - (this.m01 * this.m10);
				if (Math.abs(d) <= 4.9E-324D)
					throw new NoninvertibleTransformException("Determinant is " + d);
				return new AffineTransform(this.m11 / d, -this.m10 / d, -this.m01 / d, this.m00 / d, 0.0D, 0.0D, 6);
			case 5:
				if ((this.m01 == 0.0D) || (this.m10 == 0.0D))
					throw new NoninvertibleTransformException("Determinant is 0");
				return new AffineTransform(0.0D, 1.0D / this.m01, 1.0D / this.m10, 0.0D, -this.m12 / this.m10, -this.m02 / this.m01, 5);
			case 4:
				if ((this.m01 == 0.0D) || (this.m10 == 0.0D))
					throw new NoninvertibleTransformException("Determinant is 0");
				return new AffineTransform(0.0D, 1.0D / this.m01, 1.0D / this.m10, 0.0D, 0.0D, 0.0D, 4);
			case 3:
				if ((this.m00 == 0.0D) || (this.m11 == 0.0D))
					throw new NoninvertibleTransformException("Determinant is 0");
				return new AffineTransform(1.0D / this.m00, 0.0D, 0.0D, 1.0D / this.m11, -this.m02 / this.m00, -this.m12 / this.m11, 3);
			case 2:
				if ((this.m00 == 0.0D) || (this.m11 == 0.0D))
					throw new NoninvertibleTransformException("Determinant is 0");
				return new AffineTransform(1.0D / this.m00, 0.0D, 0.0D, 1.0D / this.m11, 0.0D, 0.0D, 2);
			case 1:
				return new AffineTransform(1.0D, 0.0D, 0.0D, 1.0D, -this.m02, -this.m12, 1);
			case 0:
		}
		return new AffineTransform();
	}

	public void invert()
		throws NoninvertibleTransformException
	{
		double d1;
		double d2;
		double d3;
		double d4;
		double d5;
		double d6;
		double d7;
		switch (this.state) {
			default:
				stateError();
			case 7:
				d1 = this.m00;
				d2 = this.m01;
				d3 = this.m02;
				d4 = this.m10;
				d5 = this.m11;
				d6 = this.m12;
				d7 = d1 * d5 - (d2 * d4);
				if (Math.abs(d7) <= 4.9E-324D)
					throw new NoninvertibleTransformException("Determinant is " + d7);
				this.m00 = (d5 / d7);
				this.m10 = (-d4 / d7);
				this.m01 = (-d2 / d7);
				this.m11 = (d1 / d7);
				this.m02 = ((d2 * d6 - (d5 * d3)) / d7);
				this.m12 = ((d4 * d3 - (d1 * d6)) / d7);
				break;
			case 6:
				d1 = this.m00;
				d2 = this.m01;
				d4 = this.m10;
				d5 = this.m11;
				d7 = d1 * d5 - (d2 * d4);
				if (Math.abs(d7) <= 4.9E-324D)
					throw new NoninvertibleTransformException("Determinant is " + d7);
				this.m00 = (d5 / d7);
				this.m10 = (-d4 / d7);
				this.m01 = (-d2 / d7);
				this.m11 = (d1 / d7);
				break;
			case 5:
				d2 = this.m01;
				d3 = this.m02;
				d4 = this.m10;
				d6 = this.m12;
				if ((d2 == 0.0D) || (d4 == 0.0D))
					throw new NoninvertibleTransformException("Determinant is 0");
				this.m10 = (1.0D / d2);
				this.m01 = (1.0D / d4);
				this.m02 = (-d6 / d4);
				this.m12 = (-d3 / d2);
				break;
			case 4:
				d2 = this.m01;
				d4 = this.m10;
				if ((d2 == 0.0D) || (d4 == 0.0D))
					throw new NoninvertibleTransformException("Determinant is 0");
				this.m10 = (1.0D / d2);
				this.m01 = (1.0D / d4);
				break;
			case 3:
				d1 = this.m00;
				d3 = this.m02;
				d5 = this.m11;
				d6 = this.m12;
				if ((d1 == 0.0D) || (d5 == 0.0D))
					throw new NoninvertibleTransformException("Determinant is 0");
				this.m00 = (1.0D / d1);
				this.m11 = (1.0D / d5);
				this.m02 = (-d3 / d1);
				this.m12 = (-d6 / d5);
				break;
			case 2:
				d1 = this.m00;
				d5 = this.m11;
				if ((d1 == 0.0D) || (d5 == 0.0D))
					throw new NoninvertibleTransformException("Determinant is 0");
				this.m00 = (1.0D / d1);
				this.m11 = (1.0D / d5);
				break;
			case 1:
				this.m02 = (-this.m02);
				this.m12 = (-this.m12);
			case 0:
		}
	}

	public Point2D transform(Point2D paramPoint2D1, Point2D paramPoint2D2)
	{
		if (paramPoint2D2 == null)
			if (paramPoint2D1 instanceof Point2D.Double)
				paramPoint2D2 = new Point2D.Double();
			else
				paramPoint2D2 = new Point2D.Float();
		double d1 = paramPoint2D1.getX();
		double d2 = paramPoint2D1.getY();
		switch (this.state) {
			default:
				stateError();
			case 7:
				paramPoint2D2.setLocation(d1 * this.m00 + d2 * this.m01 + this.m02, d1 * this.m10 + d2 * this.m11 + this.m12);
				return paramPoint2D2;
			case 6:
				paramPoint2D2.setLocation(d1 * this.m00 + d2 * this.m01, d1 * this.m10 + d2 * this.m11);
				return paramPoint2D2;
			case 5:
				paramPoint2D2.setLocation(d2 * this.m01 + this.m02, d1 * this.m10 + this.m12);
				return paramPoint2D2;
			case 4:
				paramPoint2D2.setLocation(d2 * this.m01, d1 * this.m10);
				return paramPoint2D2;
			case 3:
				paramPoint2D2.setLocation(d1 * this.m00 + this.m02, d2 * this.m11 + this.m12);
				return paramPoint2D2;
			case 2:
				paramPoint2D2.setLocation(d1 * this.m00, d2 * this.m11);
				return paramPoint2D2;
			case 1:
				paramPoint2D2.setLocation(d1 + this.m02, d2 + this.m12);
				return paramPoint2D2;
			case 0:
		}
		paramPoint2D2.setLocation(d1, d2);
		return paramPoint2D2;
	}

	public void transform(Point2D[] paramArrayOfPoint2D1, int paramInt1, Point2D[] paramArrayOfPoint2D2, int paramInt2, int paramInt3)
	{
		int i = this.state;
		while (--paramInt3 >= 0) {
			Point2D localPoint2D = paramArrayOfPoint2D1[(paramInt1++)];
			double d1 = localPoint2D.getX();
			double d2 = localPoint2D.getY();
			Point2D localObject = paramArrayOfPoint2D2[(paramInt2++)];
			if (localObject == null) {
				if (localPoint2D instanceof Point2D.Double)
					localObject = new Point2D.Double();
				else
					localObject = new Point2D.Float();
				paramArrayOfPoint2D2[(paramInt2 - 1)] = localObject;
			}
			switch (i) {
				default:
					stateError();
				case 7:
					((Point2D) localObject).setLocation(d1 * this.m00 + d2 * this.m01 + this.m02, d1 * this.m10 + d2 * this.m11 + this.m12);
					break;
				case 6:
					((Point2D) localObject).setLocation(d1 * this.m00 + d2 * this.m01, d1 * this.m10 + d2 * this.m11);
					break;
				case 5:
					((Point2D) localObject).setLocation(d2 * this.m01 + this.m02, d1 * this.m10 + this.m12);
					break;
				case 4:
					((Point2D) localObject).setLocation(d2 * this.m01, d1 * this.m10);
					break;
				case 3:
					((Point2D) localObject).setLocation(d1 * this.m00 + this.m02, d2 * this.m11 + this.m12);
					break;
				case 2:
					((Point2D) localObject).setLocation(d1 * this.m00, d2 * this.m11);
					break;
				case 1:
					((Point2D) localObject).setLocation(d1 + this.m02, d2 + this.m12);
					break;
				case 0:
					((Point2D) localObject).setLocation(d1, d2);
			}
		}
	}

	public void transform(float[] paramArrayOfFloat1, int paramInt1, float[] paramArrayOfFloat2, int paramInt2, int paramInt3)
	{
		if ((paramArrayOfFloat2 == paramArrayOfFloat1) && (paramInt2 > paramInt1) && (paramInt2 < paramInt1 + paramInt3 * 2)) {
			System.arraycopy(paramArrayOfFloat1, paramInt1, paramArrayOfFloat2, paramInt2, paramInt3 * 2);
			paramInt1 = paramInt2;
		}
		double d1;
		double d2;
		double d3;
		double d4;
		double d5;
		double d6;
		double d7;
		double d8;
		switch (this.state) {
			default:
				stateError();
			case 7:
				d1 = this.m00;
				d2 = this.m01;
				d3 = this.m02;
				d4 = this.m10;
				d5 = this.m11;
				d6 = this.m12;
				while (--paramInt3 >= 0) {
					d7 = paramArrayOfFloat1[(paramInt1++)];
					d8 = paramArrayOfFloat1[(paramInt1++)];
					paramArrayOfFloat2[(paramInt2++)] = (float) (d1 * d7 + d2 * d8 + d3);
					paramArrayOfFloat2[(paramInt2++)] = (float) (d4 * d7 + d5 * d8 + d6);
				}
				return;
			case 6:
				d1 = this.m00;
				d2 = this.m01;
				d4 = this.m10;
				d5 = this.m11;
				while (--paramInt3 >= 0) {
					d7 = paramArrayOfFloat1[(paramInt1++)];
					d8 = paramArrayOfFloat1[(paramInt1++)];
					paramArrayOfFloat2[(paramInt2++)] = (float) (d1 * d7 + d2 * d8);
					paramArrayOfFloat2[(paramInt2++)] = (float) (d4 * d7 + d5 * d8);
				}
				return;
			case 5:
				d2 = this.m01;
				d3 = this.m02;
				d4 = this.m10;
				d6 = this.m12;
				while (--paramInt3 >= 0) {
					d7 = paramArrayOfFloat1[(paramInt1++)];
					paramArrayOfFloat2[(paramInt2++)] = (float) (d2 * paramArrayOfFloat1[(paramInt1++)] + d3);
					paramArrayOfFloat2[(paramInt2++)] = (float) (d4 * d7 + d6);
				}
				return;
			case 4:
				d2 = this.m01;
				d4 = this.m10;
				while (--paramInt3 >= 0) {
					d7 = paramArrayOfFloat1[(paramInt1++)];
					paramArrayOfFloat2[(paramInt2++)] = (float) (d2 * paramArrayOfFloat1[(paramInt1++)]);
					paramArrayOfFloat2[(paramInt2++)] = (float) (d4 * d7);
				}
				return;
			case 3:
				d1 = this.m00;
				d3 = this.m02;
				d5 = this.m11;
				d6 = this.m12;
				while (--paramInt3 >= 0) {
					paramArrayOfFloat2[(paramInt2++)] = (float) (d1 * paramArrayOfFloat1[(paramInt1++)] + d3);
					paramArrayOfFloat2[(paramInt2++)] = (float) (d5 * paramArrayOfFloat1[(paramInt1++)] + d6);
				}
				return;
			case 2:
				d1 = this.m00;
				d5 = this.m11;
				while (--paramInt3 >= 0) {
					paramArrayOfFloat2[(paramInt2++)] = (float) (d1 * paramArrayOfFloat1[(paramInt1++)]);
					paramArrayOfFloat2[(paramInt2++)] = (float) (d5 * paramArrayOfFloat1[(paramInt1++)]);
				}
				return;
			case 1:
				d3 = this.m02;
				d6 = this.m12;
				while (--paramInt3 >= 0) {
					paramArrayOfFloat2[(paramInt2++)] = (float) (paramArrayOfFloat1[(paramInt1++)] + d3);
					paramArrayOfFloat2[(paramInt2++)] = (float) (paramArrayOfFloat1[(paramInt1++)] + d6);
				}
				return;
			case 0:
		}
		if ((paramArrayOfFloat1 == paramArrayOfFloat2) && (paramInt1 == paramInt2))
			return;
		System.arraycopy(paramArrayOfFloat1, paramInt1, paramArrayOfFloat2, paramInt2, paramInt3 * 2);
	}

	public void transform(double[] paramArrayOfDouble1, int paramInt1, double[] paramArrayOfDouble2, int paramInt2, int paramInt3)
	{
		if ((paramArrayOfDouble2 == paramArrayOfDouble1) && (paramInt2 > paramInt1) && (paramInt2 < paramInt1 + paramInt3 * 2)) {
			System.arraycopy(paramArrayOfDouble1, paramInt1, paramArrayOfDouble2, paramInt2, paramInt3 * 2);
			paramInt1 = paramInt2;
		}
		double d1;
		double d2;
		double d3;
		double d4;
		double d5;
		double d6;
		double d7;
		double d8;
		switch (this.state) {
			default:
				stateError();
			case 7:
				d1 = this.m00;
				d2 = this.m01;
				d3 = this.m02;
				d4 = this.m10;
				d5 = this.m11;
				d6 = this.m12;
				while (--paramInt3 >= 0) {
					d7 = paramArrayOfDouble1[(paramInt1++)];
					d8 = paramArrayOfDouble1[(paramInt1++)];
					paramArrayOfDouble2[(paramInt2++)] = (d1 * d7 + d2 * d8 + d3);
					paramArrayOfDouble2[(paramInt2++)] = (d4 * d7 + d5 * d8 + d6);
				}
				return;
			case 6:
				d1 = this.m00;
				d2 = this.m01;
				d4 = this.m10;
				d5 = this.m11;
				while (--paramInt3 >= 0) {
					d7 = paramArrayOfDouble1[(paramInt1++)];
					d8 = paramArrayOfDouble1[(paramInt1++)];
					paramArrayOfDouble2[(paramInt2++)] = (d1 * d7 + d2 * d8);
					paramArrayOfDouble2[(paramInt2++)] = (d4 * d7 + d5 * d8);
				}
				return;
			case 5:
				d2 = this.m01;
				d3 = this.m02;
				d4 = this.m10;
				d6 = this.m12;
				while (--paramInt3 >= 0) {
					d7 = paramArrayOfDouble1[(paramInt1++)];
					paramArrayOfDouble2[(paramInt2++)] = (d2 * paramArrayOfDouble1[(paramInt1++)] + d3);
					paramArrayOfDouble2[(paramInt2++)] = (d4 * d7 + d6);
				}
				return;
			case 4:
				d2 = this.m01;
				d4 = this.m10;
				while (--paramInt3 >= 0) {
					d7 = paramArrayOfDouble1[(paramInt1++)];
					paramArrayOfDouble2[(paramInt2++)] = (d2 * paramArrayOfDouble1[(paramInt1++)]);
					paramArrayOfDouble2[(paramInt2++)] = (d4 * d7);
				}
				return;
			case 3:
				d1 = this.m00;
				d3 = this.m02;
				d5 = this.m11;
				d6 = this.m12;
				while (--paramInt3 >= 0) {
					paramArrayOfDouble2[(paramInt2++)] = (d1 * paramArrayOfDouble1[(paramInt1++)] + d3);
					paramArrayOfDouble2[(paramInt2++)] = (d5 * paramArrayOfDouble1[(paramInt1++)] + d6);
				}
				return;
			case 2:
				d1 = this.m00;
				d5 = this.m11;
				while (--paramInt3 >= 0) {
					paramArrayOfDouble2[(paramInt2++)] = (d1 * paramArrayOfDouble1[(paramInt1++)]);
					paramArrayOfDouble2[(paramInt2++)] = (d5 * paramArrayOfDouble1[(paramInt1++)]);
				}
				return;
			case 1:
				d3 = this.m02;
				d6 = this.m12;
				while (--paramInt3 >= 0) {
					paramArrayOfDouble2[(paramInt2++)] = (paramArrayOfDouble1[(paramInt1++)] + d3);
					paramArrayOfDouble2[(paramInt2++)] = (paramArrayOfDouble1[(paramInt1++)] + d6);
				}
				return;
			case 0:
		}
		if ((paramArrayOfDouble1 == paramArrayOfDouble2) && (paramInt1 == paramInt2))
			return;
		System.arraycopy(paramArrayOfDouble1, paramInt1, paramArrayOfDouble2, paramInt2, paramInt3 * 2);
	}

	public void transform(float[] paramArrayOfFloat, int paramInt1, double[] paramArrayOfDouble, int paramInt2, int paramInt3)
	{
		double d1;
		double d2;
		double d3;
		double d4;
		double d5;
		double d6;
		double d7;
		double d8;
		switch (this.state) {
			default:
				stateError();
			case 7:
				d1 = this.m00;
				d2 = this.m01;
				d3 = this.m02;
				d4 = this.m10;
				d5 = this.m11;
				d6 = this.m12;
				while (--paramInt3 >= 0) {
					d7 = paramArrayOfFloat[(paramInt1++)];
					d8 = paramArrayOfFloat[(paramInt1++)];
					paramArrayOfDouble[(paramInt2++)] = (d1 * d7 + d2 * d8 + d3);
					paramArrayOfDouble[(paramInt2++)] = (d4 * d7 + d5 * d8 + d6);
				}
				return;
			case 6:
				d1 = this.m00;
				d2 = this.m01;
				d4 = this.m10;
				d5 = this.m11;
				while (--paramInt3 >= 0) {
					d7 = paramArrayOfFloat[(paramInt1++)];
					d8 = paramArrayOfFloat[(paramInt1++)];
					paramArrayOfDouble[(paramInt2++)] = (d1 * d7 + d2 * d8);
					paramArrayOfDouble[(paramInt2++)] = (d4 * d7 + d5 * d8);
				}
				return;
			case 5:
				d2 = this.m01;
				d3 = this.m02;
				d4 = this.m10;
				d6 = this.m12;
				while (--paramInt3 >= 0) {
					d7 = paramArrayOfFloat[(paramInt1++)];
					paramArrayOfDouble[(paramInt2++)] = (d2 * paramArrayOfFloat[(paramInt1++)] + d3);
					paramArrayOfDouble[(paramInt2++)] = (d4 * d7 + d6);
				}
				return;
			case 4:
				d2 = this.m01;
				d4 = this.m10;
				while (--paramInt3 >= 0) {
					d7 = paramArrayOfFloat[(paramInt1++)];
					paramArrayOfDouble[(paramInt2++)] = (d2 * paramArrayOfFloat[(paramInt1++)]);
					paramArrayOfDouble[(paramInt2++)] = (d4 * d7);
				}
				return;
			case 3:
				d1 = this.m00;
				d3 = this.m02;
				d5 = this.m11;
				d6 = this.m12;
				while (--paramInt3 >= 0) {
					paramArrayOfDouble[(paramInt2++)] = (d1 * paramArrayOfFloat[(paramInt1++)] + d3);
					paramArrayOfDouble[(paramInt2++)] = (d5 * paramArrayOfFloat[(paramInt1++)] + d6);
				}
				return;
			case 2:
				d1 = this.m00;
				d5 = this.m11;
				while (--paramInt3 >= 0) {
					paramArrayOfDouble[(paramInt2++)] = (d1 * paramArrayOfFloat[(paramInt1++)]);
					paramArrayOfDouble[(paramInt2++)] = (d5 * paramArrayOfFloat[(paramInt1++)]);
				}
				return;
			case 1:
				d3 = this.m02;
				d6 = this.m12;
				while (--paramInt3 >= 0) {
					paramArrayOfDouble[(paramInt2++)] = (paramArrayOfFloat[(paramInt1++)] + d3);
					paramArrayOfDouble[(paramInt2++)] = (paramArrayOfFloat[(paramInt1++)] + d6);
				}
				return;
			case 0:
		}
		while (--paramInt3 >= 0) {
			paramArrayOfDouble[(paramInt2++)] = paramArrayOfFloat[(paramInt1++)];
			paramArrayOfDouble[(paramInt2++)] = paramArrayOfFloat[(paramInt1++)];
		}
	}

	public void transform(double[] paramArrayOfDouble, int paramInt1, float[] paramArrayOfFloat, int paramInt2, int paramInt3)
	{
		double d1;
		double d2;
		double d3;
		double d4;
		double d5;
		double d6;
		double d7;
		double d8;
		switch (this.state) {
			default:
				stateError();
			case 7:
				d1 = this.m00;
				d2 = this.m01;
				d3 = this.m02;
				d4 = this.m10;
				d5 = this.m11;
				d6 = this.m12;
				while (--paramInt3 >= 0) {
					d7 = paramArrayOfDouble[(paramInt1++)];
					d8 = paramArrayOfDouble[(paramInt1++)];
					paramArrayOfFloat[(paramInt2++)] = (float) (d1 * d7 + d2 * d8 + d3);
					paramArrayOfFloat[(paramInt2++)] = (float) (d4 * d7 + d5 * d8 + d6);
				}
				return;
			case 6:
				d1 = this.m00;
				d2 = this.m01;
				d4 = this.m10;
				d5 = this.m11;
				while (--paramInt3 >= 0) {
					d7 = paramArrayOfDouble[(paramInt1++)];
					d8 = paramArrayOfDouble[(paramInt1++)];
					paramArrayOfFloat[(paramInt2++)] = (float) (d1 * d7 + d2 * d8);
					paramArrayOfFloat[(paramInt2++)] = (float) (d4 * d7 + d5 * d8);
				}
				return;
			case 5:
				d2 = this.m01;
				d3 = this.m02;
				d4 = this.m10;
				d6 = this.m12;
				while (--paramInt3 >= 0) {
					d7 = paramArrayOfDouble[(paramInt1++)];
					paramArrayOfFloat[(paramInt2++)] = (float) (d2 * paramArrayOfDouble[(paramInt1++)] + d3);
					paramArrayOfFloat[(paramInt2++)] = (float) (d4 * d7 + d6);
				}
				return;
			case 4:
				d2 = this.m01;
				d4 = this.m10;
				while (--paramInt3 >= 0) {
					d7 = paramArrayOfDouble[(paramInt1++)];
					paramArrayOfFloat[(paramInt2++)] = (float) (d2 * paramArrayOfDouble[(paramInt1++)]);
					paramArrayOfFloat[(paramInt2++)] = (float) (d4 * d7);
				}
				return;
			case 3:
				d1 = this.m00;
				d3 = this.m02;
				d5 = this.m11;
				d6 = this.m12;
				while (--paramInt3 >= 0) {
					paramArrayOfFloat[(paramInt2++)] = (float) (d1 * paramArrayOfDouble[(paramInt1++)] + d3);
					paramArrayOfFloat[(paramInt2++)] = (float) (d5 * paramArrayOfDouble[(paramInt1++)] + d6);
				}
				return;
			case 2:
				d1 = this.m00;
				d5 = this.m11;
				while (--paramInt3 >= 0) {
					paramArrayOfFloat[(paramInt2++)] = (float) (d1 * paramArrayOfDouble[(paramInt1++)]);
					paramArrayOfFloat[(paramInt2++)] = (float) (d5 * paramArrayOfDouble[(paramInt1++)]);
				}
				return;
			case 1:
				d3 = this.m02;
				d6 = this.m12;
				while (--paramInt3 >= 0) {
					paramArrayOfFloat[(paramInt2++)] = (float) (paramArrayOfDouble[(paramInt1++)] + d3);
					paramArrayOfFloat[(paramInt2++)] = (float) (paramArrayOfDouble[(paramInt1++)] + d6);
				}
				return;
			case 0:
		}
		while (--paramInt3 >= 0) {
			paramArrayOfFloat[(paramInt2++)] = (float) paramArrayOfDouble[(paramInt1++)];
			paramArrayOfFloat[(paramInt2++)] = (float) paramArrayOfDouble[(paramInt1++)];
		}
	}

	public Point2D inverseTransform(Point2D paramPoint2D1, Point2D paramPoint2D2)
		throws NoninvertibleTransformException
	{
		if (paramPoint2D2 == null)
			if (paramPoint2D1 instanceof Point2D.Double)
				paramPoint2D2 = new Point2D.Double();
			else
				paramPoint2D2 = new Point2D.Float();
		double d1 = paramPoint2D1.getX();
		double d2 = paramPoint2D1.getY();
		switch (this.state) {
			default:
				stateError();
			case 7:
				d1 -= this.m02;
				d2 -= this.m12;
			case 6:
				double d3 = this.m00 * this.m11 - (this.m01 * this.m10);
				if (Math.abs(d3) <= 4.9E-324D)
					throw new NoninvertibleTransformException("Determinant is " + d3);
				paramPoint2D2.setLocation((d1 * this.m11 - (d2 * this.m01)) / d3, (d2 * this.m00 - (d1 * this.m10)) / d3);
				return paramPoint2D2;
			case 5:
				d1 -= this.m02;
				d2 -= this.m12;
			case 4:
				if ((this.m01 == 0.0D) || (this.m10 == 0.0D))
					throw new NoninvertibleTransformException("Determinant is 0");
				paramPoint2D2.setLocation(d2 / this.m10, d1 / this.m01);
				return paramPoint2D2;
			case 3:
				d1 -= this.m02;
				d2 -= this.m12;
			case 2:
				if ((this.m00 == 0.0D) || (this.m11 == 0.0D))
					throw new NoninvertibleTransformException("Determinant is 0");
				paramPoint2D2.setLocation(d1 / this.m00, d2 / this.m11);
				return paramPoint2D2;
			case 1:
				paramPoint2D2.setLocation(d1 - this.m02, d2 - this.m12);
				return paramPoint2D2;
			case 0:
		}
		paramPoint2D2.setLocation(d1, d2);
		return paramPoint2D2;
	}

	public void inverseTransform(double[] paramArrayOfDouble1, int paramInt1, double[] paramArrayOfDouble2, int paramInt2, int paramInt3)
		throws NoninvertibleTransformException
	{
		if ((paramArrayOfDouble2 == paramArrayOfDouble1) && (paramInt2 > paramInt1) && (paramInt2 < paramInt1 + paramInt3 * 2)) {
			System.arraycopy(paramArrayOfDouble1, paramInt1, paramArrayOfDouble2, paramInt2, paramInt3 * 2);
			paramInt1 = paramInt2;
		}
		double d1;
		double d2;
		double d3;
		double d4;
		double d5;
		double d6;
		double d7;
		double d8;
		double d9;
		switch (this.state) {
			default:
				stateError();
			case 7:
				d1 = this.m00;
				d2 = this.m01;
				d3 = this.m02;
				d4 = this.m10;
				d5 = this.m11;
				d6 = this.m12;
				d7 = d1 * d5 - (d2 * d4);
				if (Math.abs(d7) <= 4.9E-324D)
					throw new NoninvertibleTransformException("Determinant is " + d7);
				while (--paramInt3 >= 0) {
					d8 = paramArrayOfDouble1[(paramInt1++)] - d3;
					d9 = paramArrayOfDouble1[(paramInt1++)] - d6;
					paramArrayOfDouble2[(paramInt2++)] = ((d8 * d5 - (d9 * d2)) / d7);
					paramArrayOfDouble2[(paramInt2++)] = ((d9 * d1 - (d8 * d4)) / d7);
				}
				return;
			case 6:
				d1 = this.m00;
				d2 = this.m01;
				d4 = this.m10;
				d5 = this.m11;
				d7 = d1 * d5 - (d2 * d4);
				if (Math.abs(d7) <= 4.9E-324D)
					throw new NoninvertibleTransformException("Determinant is " + d7);
				while (--paramInt3 >= 0) {
					d8 = paramArrayOfDouble1[(paramInt1++)];
					d9 = paramArrayOfDouble1[(paramInt1++)];
					paramArrayOfDouble2[(paramInt2++)] = ((d8 * d5 - (d9 * d2)) / d7);
					paramArrayOfDouble2[(paramInt2++)] = ((d9 * d1 - (d8 * d4)) / d7);
				}
				return;
			case 5:
				d2 = this.m01;
				d3 = this.m02;
				d4 = this.m10;
				d6 = this.m12;
				if ((d2 == 0.0D) || (d4 == 0.0D))
					throw new NoninvertibleTransformException("Determinant is 0");
				while (--paramInt3 >= 0) {
					d8 = paramArrayOfDouble1[(paramInt1++)] - d3;
					paramArrayOfDouble2[(paramInt2++)] = ((paramArrayOfDouble1[(paramInt1++)] - d6) / d4);
					paramArrayOfDouble2[(paramInt2++)] = (d8 / d2);
				}
				return;
			case 4:
				d2 = this.m01;
				d4 = this.m10;
				if ((d2 == 0.0D) || (d4 == 0.0D))
					throw new NoninvertibleTransformException("Determinant is 0");
				while (--paramInt3 >= 0) {
					d8 = paramArrayOfDouble1[(paramInt1++)];
					paramArrayOfDouble2[(paramInt2++)] = (paramArrayOfDouble1[(paramInt1++)] / d4);
					paramArrayOfDouble2[(paramInt2++)] = (d8 / d2);
				}
				return;
			case 3:
				d1 = this.m00;
				d3 = this.m02;
				d5 = this.m11;
				d6 = this.m12;
				if ((d1 == 0.0D) || (d5 == 0.0D))
					throw new NoninvertibleTransformException("Determinant is 0");
				while (--paramInt3 >= 0) {
					paramArrayOfDouble2[(paramInt2++)] = ((paramArrayOfDouble1[(paramInt1++)] - d3) / d1);
					paramArrayOfDouble2[(paramInt2++)] = ((paramArrayOfDouble1[(paramInt1++)] - d6) / d5);
				}
				return;
			case 2:
				d1 = this.m00;
				d5 = this.m11;
				if ((d1 == 0.0D) || (d5 == 0.0D))
					throw new NoninvertibleTransformException("Determinant is 0");
				while (--paramInt3 >= 0) {
					paramArrayOfDouble2[(paramInt2++)] = (paramArrayOfDouble1[(paramInt1++)] / d1);
					paramArrayOfDouble2[(paramInt2++)] = (paramArrayOfDouble1[(paramInt1++)] / d5);
				}
				return;
			case 1:
				d3 = this.m02;
				d6 = this.m12;
				while (--paramInt3 >= 0) {
					paramArrayOfDouble2[(paramInt2++)] = (paramArrayOfDouble1[(paramInt1++)] - d3);
					paramArrayOfDouble2[(paramInt2++)] = (paramArrayOfDouble1[(paramInt1++)] - d6);
				}
				return;
			case 0:
		}
		if ((paramArrayOfDouble1 == paramArrayOfDouble2) && (paramInt1 == paramInt2))
			return;
		System.arraycopy(paramArrayOfDouble1, paramInt1, paramArrayOfDouble2, paramInt2, paramInt3 * 2);
	}

	public Point2D deltaTransform(Point2D paramPoint2D1, Point2D paramPoint2D2)
	{
		if (paramPoint2D2 == null)
			if (paramPoint2D1 instanceof Point2D.Double)
				paramPoint2D2 = new Point2D.Double();
			else
				paramPoint2D2 = new Point2D.Float();
		double d1 = paramPoint2D1.getX();
		double d2 = paramPoint2D1.getY();
		switch (this.state) {
			default:
				stateError();
			case 6:
			case 7:
				paramPoint2D2.setLocation(d1 * this.m00 + d2 * this.m01, d1 * this.m10 + d2 * this.m11);
				return paramPoint2D2;
			case 4:
			case 5:
				paramPoint2D2.setLocation(d2 * this.m01, d1 * this.m10);
				return paramPoint2D2;
			case 2:
			case 3:
				paramPoint2D2.setLocation(d1 * this.m00, d2 * this.m11);
				return paramPoint2D2;
			case 0:
			case 1:
		}
		paramPoint2D2.setLocation(d1, d2);
		return paramPoint2D2;
	}

	public void deltaTransform(double[] paramArrayOfDouble1, int paramInt1, double[] paramArrayOfDouble2, int paramInt2, int paramInt3)
	{
		if ((paramArrayOfDouble2 == paramArrayOfDouble1) && (paramInt2 > paramInt1) && (paramInt2 < paramInt1 + paramInt3 * 2)) {
			System.arraycopy(paramArrayOfDouble1, paramInt1, paramArrayOfDouble2, paramInt2, paramInt3 * 2);
			paramInt1 = paramInt2;
		}
		double d1;
		double d2;
		double d3;
		double d4;
		double d5;
		switch (this.state) {
			default:
				stateError();
			case 6:
			case 7:
				d1 = this.m00;
				d2 = this.m01;
				d3 = this.m10;
				d4 = this.m11;
				while (--paramInt3 >= 0) {
					d5 = paramArrayOfDouble1[(paramInt1++)];
					double d6 = paramArrayOfDouble1[(paramInt1++)];
					paramArrayOfDouble2[(paramInt2++)] = (d5 * d1 + d6 * d2);
					paramArrayOfDouble2[(paramInt2++)] = (d5 * d3 + d6 * d4);
				}
				return;
			case 4:
			case 5:
				d2 = this.m01;
				d3 = this.m10;
				while (--paramInt3 >= 0) {
					d5 = paramArrayOfDouble1[(paramInt1++)];
					paramArrayOfDouble2[(paramInt2++)] = (paramArrayOfDouble1[(paramInt1++)] * d2);
					paramArrayOfDouble2[(paramInt2++)] = (d5 * d3);
				}
				return;
			case 2:
			case 3:
				d1 = this.m00;
				d4 = this.m11;
				while (--paramInt3 >= 0) {
					paramArrayOfDouble2[(paramInt2++)] = (paramArrayOfDouble1[(paramInt1++)] * d1);
					paramArrayOfDouble2[(paramInt2++)] = (paramArrayOfDouble1[(paramInt1++)] * d4);
				}
				return;
			case 0:
			case 1:
		}
		if ((paramArrayOfDouble1 == paramArrayOfDouble2) && (paramInt1 == paramInt2))
			return;
		System.arraycopy(paramArrayOfDouble1, paramInt1, paramArrayOfDouble2, paramInt2, paramInt3 * 2);
	}

	public Shape createTransformedShape(Shape paramShape)
	{
		if (paramShape == null)
			return null;
		return new Path2D.Double(paramShape, this);
	}

	private static double _matround(double paramDouble)
	{
		return (Math.rint(paramDouble * 1000000000000000.0D) / 1000000000000000.0D);
	}

	public String toString()
	{
		return "AffineTransform[[" + _matround(this.m00) + ", " + _matround(this.m01) + ", " + _matround(this.m02) + "], [" + _matround(this.m10) + ", "
			+ _matround(this.m11) + ", " + _matround(this.m12) + "]]";
	}

	public boolean isIdentity()
	{
		return ((this.state == 0) || (getType() == 0));
	}

	public Object clone()
	{
		try {
			return super.clone();
		}
		catch (CloneNotSupportedException localCloneNotSupportedException) {
			throw new InternalError();
		}
	}

	public int hashCode()
	{
		long l = Double.doubleToLongBits(this.m00);
		l = l * 31L + Double.doubleToLongBits(this.m01);
		l = l * 31L + Double.doubleToLongBits(this.m02);
		l = l * 31L + Double.doubleToLongBits(this.m10);
		l = l * 31L + Double.doubleToLongBits(this.m11);
		l = l * 31L + Double.doubleToLongBits(this.m12);
		return ((int) l ^ (int) (l >> 32));
	}

	public boolean equals(Object paramObject)
	{
		if (!(paramObject instanceof AffineTransform))
			return false;
		AffineTransform localAffineTransform = (AffineTransform) paramObject;
		return ((this.m00 == localAffineTransform.m00) && (this.m01 == localAffineTransform.m01) && (this.m02 == localAffineTransform.m02)
			&& (this.m10 == localAffineTransform.m10) && (this.m11 == localAffineTransform.m11) && (this.m12 == localAffineTransform.m12));
	}

	private void writeObject(ObjectOutputStream paramObjectOutputStream)
		throws ClassNotFoundException, IOException
	{
		paramObjectOutputStream.defaultWriteObject();
	}

	private void readObject(ObjectInputStream paramObjectInputStream)
		throws ClassNotFoundException, IOException
	{
		paramObjectInputStream.defaultReadObject();
		updateState();
	}
}
