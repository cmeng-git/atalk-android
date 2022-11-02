/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.codec.audio.silk;

public class Tables
{
	static final int PITCH_EST_MAX_LAG_MS = 18; /* 18 ms -> 56 Hz */
	static final int PITCH_EST_MIN_LAG_MS = 2; /* 2 ms -> 500 Hz */

	/**
	 * Copies the specified range of the specified array into a new array. The initial index of the
	 * range (<code>from</code> ) must lie between zero and <code>original.length</code>, inclusive. The
	 * value at <code>original[from]</code> is placed into the initial element of the copy (unless
	 * <code>from == original.length</code> or <code>from == to</code>). Values from subsequent elements in
	 * the original array are placed into subsequent elements in the copy. The final index of the
	 * range (<code>to</code>), which must be greater than or equal to <code>from</code>, may be greater
	 * than <code>original.length</code>, in which case <code>0</code> is placed in all elements of the copy
	 * whose index is greater than or equal to <code>original.length - from</code>. The length of the
	 * returned array will be <code>to - from</code>.
	 *
	 * @param original
	 *        the array from which a range is to be copied
	 * @param from
	 *        the initial index of the range to be copied, inclusive
	 * @param to
	 *        the final index of the range to be copied, exclusive. (This index may lie outside the
	 *        array.)
	 * @return a new array containing the specified range from the original array, truncated or
	 *         padded with zeros to obtain the required length
	 * @throws ArrayIndexOutOfBoundsException
	 *         if <code>from &lt; 0</code> or <code>from &gt; original.length()</code>
	 * @throws IllegalArgumentException
	 *         if <code>from &gt; to</code>
	 * @throws NullPointerException
	 *         if <code>original</code> is <code>null</code>
	 */
	static int[] copyOfRange(int[] original, int from, int to)
	{
		if ((from < 0) || (from > original.length))
			throw new ArrayIndexOutOfBoundsException(from);
		if (from > to)
			throw new IllegalArgumentException("to");

		int length = to - from;
		int[] copy = new int[length];

		for (int c = 0, o = from; c < length; c++, o++)
			copy[c] = (o < original.length) ? original[o] : 0;

		return copy;
	}
}
