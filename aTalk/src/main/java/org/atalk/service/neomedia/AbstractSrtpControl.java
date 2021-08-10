/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.service.neomedia;

import org.atalk.service.neomedia.event.SrtpListener;

import java.util.HashSet;
import java.util.Set;

/**
 * Provides an abstract, base implementation of {@link SrtpControl} to facilitate implementers.
 *
 * @author Lyubomir Marinov
 * @author MilanKral
 */
public abstract class AbstractSrtpControl<T extends SrtpControl.TransformEngine> implements SrtpControl
{
    private final SrtpControlType srtpControlType;

    /**
     * The <tt>SrtpListener</tt> listening to security events (to be) fired by this <tt>SrtpControl</tt> instance.
     */
    private SrtpListener srtpListener;

    protected T transformEngine;

    /**
     * The {@code Object}s currently registered as users of this
     * {@code SrtpControl} (through {@link #registerUser(Object)}).
     */
    private final Set<Object> users = new HashSet<>();

    /**
     * Initializes a new <tt>AbstractSrtpControl</tt> instance with a specific <tt>SrtpControlType</tt>.
     *
     * @param srtpControlType the <tt>SrtpControlType</tt> of the new instance
     */
    protected AbstractSrtpControl(SrtpControlType srtpControlType)
    {
        if (srtpControlType == null)
            throw new NullPointerException("srtpControlType");

        this.srtpControlType = srtpControlType;
    }

    /**
     * {@inheritDoc}
     *
     * The implementation of <tt>AbstractSrtpControl</tt> cleans up its associated <tt>TransformEngine</tt> (if any).
     */
    @Override
    public void cleanup(Object user)
    {
        synchronized (users) {
            if (users.remove(user) && users.isEmpty())
                doCleanup();
        }
    }

    /**
     * Initializes a new <tt>TransformEngine</tt> instance to be associated with
     * and used by this <tt>SrtpControl</tt> instance.
     *
     * @return a new <tt>TransformEngine</tt> instance to be associated with and
     * used by this <tt>SrtpControl</tt> instance
     */
    protected abstract T createTransformEngine();

    /**
     * Prepares this {@code SrtpControl} for garbage collection.
     */
    protected void doCleanup()
    {
        if (transformEngine != null) {
            transformEngine.cleanup();
            transformEngine = null;
        }
    }

    /**
     * {@inheritDoc}
     */
    public SrtpControlType getSrtpControlType()
    {
        return srtpControlType;
    }

    /**
     * {@inheritDoc}
     */
    public SrtpListener getSrtpListener()
    {
        return srtpListener;
    }

    /**
     * {@inheritDoc}
     */
    public T getTransformEngine()
    {
        synchronized (this) {
            if (transformEngine == null)
                transformEngine = createTransformEngine();
            return transformEngine;
        }
    }

    /**
     * {@inheritDoc}
     *
     * The implementation of <tt>AbstractSrtpControl</tt> does nothing because support for
     * multistream mode is the exception rather than the norm.
     */
    public void setMasterSession(boolean masterSession)
    {
    }

    /**
     * {@inheritDoc}
     *
     * The implementation of <tt>AbstractSrtpControl</tt> does nothing because support for
     * multistream mode is the exception rather than the norm.
     */
    public void setMultistream(SrtpControl master)
    {
    }

    /**
     * {@inheritDoc}
     */
    public void setSrtpListener(SrtpListener srtpListener)
    {
        this.srtpListener = srtpListener;
    }

    /**
     * {@inheritDoc}
     */
    public void registerUser(Object user)
    {
        synchronized (users) {
            users.add(user);
        }
    }
}
