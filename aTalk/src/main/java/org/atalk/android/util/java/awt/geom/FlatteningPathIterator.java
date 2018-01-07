package org.atalk.android.util.java.awt.geom;

import java.util.NoSuchElementException;

public class FlatteningPathIterator implements PathIterator
{
	static final int GROW_SIZE = 24;
	PathIterator src;
	double squareflat;
	int limit;
	double[] hold;
	double curx;
	double cury;
	double movx;
	double movy;
	int holdType;
	int holdEnd;
	int holdIndex;
	int[] levels;
	int levelIndex;
	boolean done;

	public FlatteningPathIterator(PathIterator paramPathIterator, double paramDouble) {
		this(paramPathIterator, paramDouble, 10);
	}

	public FlatteningPathIterator(PathIterator paramPathIterator, double paramDouble, int paramInt) {
		this.hold = new double[14];
		if (paramDouble < 0.0D)
			throw new IllegalArgumentException("flatness must be >= 0");
		if (paramInt < 0)
			throw new IllegalArgumentException("limit must be >= 0");
		this.src = paramPathIterator;
		this.squareflat = (paramDouble * paramDouble);
		this.limit = paramInt;
		this.levels = new int[paramInt + 1];
		next(false);
	}

	public double getFlatness()
	{
		return Math.sqrt(this.squareflat);
	}

	public int getRecursionLimit()
	{
		return this.limit;
	}

	public int getWindingRule()
	{
		return this.src.getWindingRule();
	}

	public boolean isDone()
	{
		return this.done;
	}

	void ensureHoldCapacity(int paramInt)
	{
		if (this.holdIndex - paramInt >= 0)
			return;
		int i = this.hold.length - this.holdIndex;
		int j = this.hold.length + 24;
		double[] arrayOfDouble = new double[j];
		System.arraycopy(this.hold, this.holdIndex, arrayOfDouble, this.holdIndex + 24, i);
		this.hold = arrayOfDouble;
		this.holdIndex += 24;
		this.holdEnd += 24;
	}

	public void next()
	{
		next(true);
	}

	private void next(boolean paramBoolean)
	{
		if (this.holdIndex >= this.holdEnd) {
			if (paramBoolean)
				this.src.next();
			if (this.src.isDone()) {
				this.done = true;
				return;
			}
			this.holdType = this.src.currentSegment(this.hold);
			this.levelIndex = 0;
			this.levels[0] = 0;
		}
		int i;
		switch (this.holdType) {
			case 0:
			case 1:
				this.curx = this.hold[0];
				this.cury = this.hold[1];
				if (this.holdType == 0) {
					this.movx = this.curx;
					this.movy = this.cury;
				}
				this.holdIndex = 0;
				this.holdEnd = 0;
				break;
			case 4:
				this.curx = this.movx;
				this.cury = this.movy;
				this.holdIndex = 0;
				this.holdEnd = 0;
				break;
			case 2:
				if (this.holdIndex >= this.holdEnd) {
					this.holdIndex = (this.hold.length - 6);
					this.holdEnd = (this.hold.length - 2);
					this.hold[(this.holdIndex + 0)] = this.curx;
					this.hold[(this.holdIndex + 1)] = this.cury;
					this.hold[(this.holdIndex + 2)] = this.hold[0];
					this.hold[(this.holdIndex + 3)] = this.hold[1];
					this.hold[(this.holdIndex + 4)] = (this.curx = this.hold[2]);
					this.hold[(this.holdIndex + 5)] = (this.cury = this.hold[3]);
				}
				i = this.levels[this.levelIndex];
				while (i < this.limit) {
//					if (QuadCurve2D.getFlatnessSq(this.hold, this.holdIndex) < this.squareflat)
//						break;
//					ensureHoldCapacity(4);
//					QuadCurve2D.subdivide(this.hold, this.holdIndex, this.hold, this.holdIndex - 4, this.hold, this.holdIndex);
					this.holdIndex -= 4;
					this.levels[this.levelIndex] = (++i);
					this.levelIndex += 1;
					this.levels[this.levelIndex] = i;
				}
				this.holdIndex += 4;
				this.levelIndex -= 1;
				break;
			case 3:
				if (this.holdIndex >= this.holdEnd) {
					this.holdIndex = (this.hold.length - 8);
					this.holdEnd = (this.hold.length - 2);
					this.hold[(this.holdIndex + 0)] = this.curx;
					this.hold[(this.holdIndex + 1)] = this.cury;
					this.hold[(this.holdIndex + 2)] = this.hold[0];
					this.hold[(this.holdIndex + 3)] = this.hold[1];
					this.hold[(this.holdIndex + 4)] = this.hold[2];
					this.hold[(this.holdIndex + 5)] = this.hold[3];
					this.hold[(this.holdIndex + 6)] = (this.curx = this.hold[4]);
					this.hold[(this.holdIndex + 7)] = (this.cury = this.hold[5]);
				}
				i = this.levels[this.levelIndex];
				while (i < this.limit) {
//					if (CubicCurve2D.getFlatnessSq(this.hold, this.holdIndex) < this.squareflat)
//						break;
//					ensureHoldCapacity(6);
//					CubicCurve2D.subdivide(this.hold, this.holdIndex, this.hold, this.holdIndex - 6, this.hold, this.holdIndex);
					this.holdIndex -= 6;
					this.levels[this.levelIndex] = (++i);
					this.levelIndex += 1;
					this.levels[this.levelIndex] = i;
				}
				this.holdIndex += 6;
				this.levelIndex -= 1;
		}
	}

	public int currentSegment(float[] paramArrayOfFloat)
	{
		if (isDone())
			throw new NoSuchElementException("flattening iterator out of bounds");
		int i = this.holdType;
		if (i != 4) {
			paramArrayOfFloat[0] = (float) this.hold[(this.holdIndex + 0)];
			paramArrayOfFloat[1] = (float) this.hold[(this.holdIndex + 1)];
			if (i != 0)
				i = 1;
		}
		return i;
	}

	public int currentSegment(double[] paramArrayOfDouble)
	{
		if (isDone())
			throw new NoSuchElementException("flattening iterator out of bounds");
		int i = this.holdType;
		if (i != 4) {
			paramArrayOfDouble[0] = this.hold[(this.holdIndex + 0)];
			paramArrayOfDouble[1] = this.hold[(this.holdIndex + 1)];
			if (i != 0)
				i = 1;
		}
		return i;
	}
}
