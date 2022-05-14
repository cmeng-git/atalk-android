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
     * The <code>SrtpListener</code> listening to security events (to be) fired by this <code>SrtpControl</code> instance.
     */
    private SrtpListener srtpListener;

    protected T transformEngine;

    /**
     * The {@code Object}s currently registered as users of this
     * {@code SrtpControl} (through {@link #registerUser(Object)}).
     */
    private final Set<Object> users = new HashSet<>();

    /**
     * Initializes a new <code>AbstractSrtpControl</code> instance with a specific <code>SrtpControlType</code>.
     *
     * @param srtpControlType the <code>SrtpControlType</code> of the new instance
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
     * The implementation of <code>AbstractSrtpControl</code> cleans up its associated <code>TransformEngine</code> (if any).
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
     * Initializes a new <code>TransformEngine</code> instance to be associated with
     * and used by this <code>SrtpControl</code> instance.
     *
     * @return a new <code>TransformEngine</code> instance to be associated with and
     * used by this <code>SrtpControl</code> instance
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
        if (transformEngine == null)
            transformEngine = createTransformEngine();
        return transformEngine;
    }

    /**
     * {@inheritDoc}
     *
     * The implementation of <code>AbstractSrtpControl</code> does nothing because support for
     * multistream mode is the exception rather than the norm.
     */
    public void setMasterSession(boolean masterSession)
    {
    }

    /**
     * {@inheritDoc}
     *
     * The implementation of <code>AbstractSrtpControl</code> does nothing because support for
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
