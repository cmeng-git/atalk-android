/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia;

/**
 * Implements a circular <code>byte</code> array.
 *
 * @author Lyubomir Marinov
 */
public class CircularByteArray
{
	/**
	 * The elements of this <code>CircularByteArray</code>.
	 */
	private final byte[] elements;

	/**
	 * The index at which the next invocation of {@link #push(byte)} is to insert an element.
	 */
	private int tail;

	/**
	 * Initializes a new <code>CircularBufferArray</code> instance with a specific length.
	 *
	 * @param length
	 *        the length i.e. the number of elements of the new instance
	 */
	public CircularByteArray(int length)
	{
		elements = new byte[length];
		tail = 0;
	}

	/**
	 * Adds a specific element at the end of this <code>CircularByteArray</code>.
	 *
	 * @param element
	 *        the element to add at the end of this <code>CircularByteArray</code>
	 */
	public synchronized void push(byte element)
	{
		int tail = this.tail;

		elements[tail] = element;
		tail++;
		if (tail >= elements.length)
			tail = 0;
		this.tail = tail;
	}

	/**
	 * Copies the elements of this <code>CircularByteArray</code> into a new <code>byte</code> array.
	 *
	 * @return a new <code>byte</code> array which contains the same elements and in the same order as
	 *         this <code>CircularByteArray</code>
	 */
	public synchronized byte[] toArray()
	{
		byte[] elements = this.elements;
		byte[] array;

		if (elements == null) {
			array = null;
		}
		else {
			array = new byte[elements.length];
			for (int i = 0, index = tail; i < elements.length; i++) {
				array[i] = elements[index];
				index++;
				if (index >= elements.length)
					index = 0;
			}
		}
		return array;
	}
}
