/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.codec.video;

import org.atalk.impl.neomedia.codec.FFmpeg;

/**
 * Represents a buffer of native memory with a specific size/capacity which may
 * contains a specific number of bytes of valid data. If the memory represented
 * by a <code>ByteBufferFmj</code> instance has been allocated by the
 * <code>ByteBufferFmj</code> instance itself, the native memory will automatically be
 * freed upon finalization.
 *
 * @author Lyubomir Marinov
 * @author Eng Chong Meng
 */
public class ByteBufferFmj {
    /**
     * The maximum number of bytes which may be written into the native memory
     * represented by this instance. If <code>0</code>, this instance has been
     * initialized to provide read-only access to the native memory it
     * represents and will not deallocate it upon finalization.
     */
    private int mCapacity;

    /**
     * The number of bytes of valid data that the native memory represented by this instance contains.
     */
    private int mLength;

    /**
     * The pointer to the native memory represented by this instance.
     */
    private long mPtr;

    /**
     * Initializes a new <code>ByteBufferFmj</code> instance with a specific
     * <code>capacity</code> of native memory. The new instance allocates the native
     * memory and automatically frees it upon finalization.
     *
     * @param capacity the maximum number of bytes which can be written into the
     * native memory represented by the new instance
     */
    public ByteBufferFmj(int capacity) {
        if (capacity < 1)
            throw new IllegalArgumentException("capacity");

        mPtr = FFmpeg.av_malloc(capacity);
        if (mPtr == 0)
            throw new OutOfMemoryError("av_malloc(" + capacity + ")");

        mCapacity = capacity;
        mLength = 0;
    }

    /**
     * Initializes a new <code>ByteBufferFmj</code> instance which is to represent a
     * specific block of native memory. Since the specified native memory has been allocated
     * outside the new instance, the new instance will not automatically free it.
     *
     * @param ptr a pointer to the block of native memory to be represented by the new instance
     */
    public ByteBufferFmj(long ptr) {
        mPtr = ptr;
        mCapacity = 0;
        mLength = 0;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Frees the native memory represented by this instance if the native memory
     * has been allocated by this instance and has not been freed yet i.e.
     * ensures that {@link #free()} is invoked on this instance.
     *
     * @see Object#finalize()
     */
    @Override
    protected void finalize()
            throws Throwable {
        try {
            free();
        } finally {
            super.finalize();
        }
    }

    /**
     * Frees the native memory represented by this instance if the native memory
     * has been allocated by this instance and has not been freed yet.
     */
    public synchronized void free() {
        if ((mCapacity != 0) && (mPtr != 0)) {
            FFmpeg.av_free(mPtr);
            mCapacity = 0;
            mPtr = 0;
        }
    }

    /**
     * Gets the maximum number of bytes which may be written into the native
     * memory represented by this instance. If <code>0</code>, this instance has
     * been initialized to provide read-only access to the native memory it
     * represents and will not deallocate it upon finalization.
     *
     * @return the maximum number of bytes which may be written into the native
     * memory represented by this instance
     */
    public synchronized int getCapacity() {
        return mCapacity;
    }

    /**
     * Gets the number of bytes of valid data that the native memory represented
     * by this instance contains.
     *
     * @return the number of bytes of valid data that the native memory
     * represented by this instance contains
     */
    public int getLength() {
        return mLength;
    }

    /**
     * Gets the pointer to the native memory represented by this instance.
     *
     * @return the pointer to the native memory represented by this instance
     */
    public synchronized long getPtr() {
        return mPtr;
    }

    /**
     * Sets the number of bytes of valid data that the native memory represented
     * by this instance contains.
     *
     * @param length the number of bytes of valid data that the native memory
     * represented by this instance contains
     *
     * @throws IllegalArgumentException if <code>length</code> is a negative value
     */
    public void setLength(int length) {
        if (length < 0)
            throw new IllegalArgumentException("length");

        mLength = length;
    }
}
