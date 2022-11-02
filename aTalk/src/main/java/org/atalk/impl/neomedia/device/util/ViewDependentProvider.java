/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.device.util;

import android.app.Activity;
import android.view.View;
import android.view.ViewGroup;

import java.awt.Dimension;

import timber.log.Timber;

/**
 * <code>ViewDependentProvider</code> is used to implement classes that provide objects dependent on
 * <code>View</code> visibility state. It means that they can provide it only when <code>View</code> is
 * visible, and they have to release the object before <code>View</code> is hidden.
 *
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
public abstract class ViewDependentProvider<T>
{
    /**
     * Timeout for dispose surface operation
     */
    private static final long REMOVAL_TIMEOUT = 5000L;

    /**
     * Timeout for create surface operation
     */
    private static final long CREATE_TIMEOUT = 5000L;

    /**
     * <code>Activity</code> context.
     */
    protected final Activity mActivity;

    /**
     * The container that will hold maintained view.
     */
    private final ViewGroup mContainer;

    /**
     * The view (can either be SurfaceView or TextureView) maintained by this instance.
     */
    protected View view;

    /**
     * Use for surfaceCreation to set surface holder size for correct camera local preview aspect ratio
     * This size is the user selected video resolution independent of the device orientation
     */
    protected Dimension mVideoSize;

    /**
     * Provided object created when <code>View</code> is visible.
     */
    protected T providedObject;

    /**
     * Factory method that creates new <code>View</code> instance.
     *
     * @return new <code>View</code> instance.
     */
    protected abstract View createViewInstance();

    public abstract void setAspectRatio(int width, int height);

    /**
     * Create a new instance of <code>ViewDependentProvider</code>.
     *
     * @param activity parent <code>Activity</code> that manages the <code>container</code>.
     * @param container the container that will hold maintained <code>View</code>.
     */
    public ViewDependentProvider(Activity activity, ViewGroup container)
    {
        mActivity = activity;
        mContainer = container;
    }

    /**
     * Checks if the view is currently created. If not creates new <code>View</code> and adds it to the
     * <code>container</code>.
     */
    protected void ensureViewCreated()
    {
        if (view == null) {
            mActivity.runOnUiThread(() -> {
                view = createViewInstance();
                ViewGroup.LayoutParams params = new ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
                mContainer.addView(view, params);
                mContainer.setVisibility(View.VISIBLE);
            });
        }
    }

    /**
     * Returns maintained <code>View</code> object.
     *
     * @return maintained <code>View</code> object.
     */
    public View getView()
    {
        return view;
    }

    /**
     * Set the {@link #mVideoSize} with the video size selected by user for this instance
     *
     * @param size user selected video size independent of the device orientation
     */
    public void setVideoSize(Dimension size)
    {
        mVideoSize = size;
    }

    public Dimension getVideoSize()
    {
        return mVideoSize;
    }

    /**
     * Checks if maintained view exists and removes if from the <code>container</code>.
     */
    protected void ensureViewDestroyed()
    {
        if (view != null) {
            final View viewToRemove = view;
            view = null;

            mActivity.runOnUiThread(() -> {
                mContainer.removeView(viewToRemove);
                mContainer.setVisibility(View.GONE);
            });
        }
    }

    /**
     * Must be called by subclasses when provided object is created.
     *
     * @param obj provided object instance.
     */
    synchronized protected void onObjectCreated(T obj)
    {
        this.providedObject = obj;
        this.notifyAll();
    }

    /**
     * Should be called by consumer to obtain the object. It is causing hidden <code>View</code> to be
     * displayed and eventually {@link #onObjectCreated(Object)} method to be called which results
     * in object creation.
     *
     * @return provided object.
     */
    synchronized public T obtainObject()
    {
        ensureViewCreated();
        if (this.providedObject == null) {
            try {
                Timber.i("Waiting for object...%s", hashCode());
                this.wait(CREATE_TIMEOUT);
                if (providedObject == null) {
                    throw new RuntimeException("Timeout waiting for surface");
                }
                Timber.i("Returning object! %s", hashCode());
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
        return providedObject;
    }

    /**
     * Checks if provider has already the object and returns it immediately. If there is no object,
     * and we would have to wait for it, then the <code>null</code> is returned.
     *
     * @return the object if it is currently held by this provider or <code>null</code> otherwise.
     */
    synchronized public T tryObtainObject()
    {
        return providedObject;
    }

    /**
     * Should be called by subclasses when object is destroyed.
     */
    synchronized protected void onObjectDestroyed()
    {
        releaseObject();
    }

    /**
     * Should be called by the consumer to release the object.
     */
    public void onObjectReleased()
    {
        releaseObject();
        // Remove the view once it's released
        ensureViewDestroyed();
    }

    /**
     * Releases the subject object and notifies all threads waiting on the lock.
     */
    synchronized protected void releaseObject()
    {
        if (providedObject != null) {
            providedObject = null;
            this.notifyAll();
        }
    }

    /**
     * Blocks the current thread until subject object is released. It should be used to block UI thread
     * before the <code>View</code> is hidden.
     */
    synchronized public void waitForObjectRelease()
    {
        if (providedObject != null) {
            try {
                Timber.i("Waiting for object release... %s", hashCode());
                this.wait(REMOVAL_TIMEOUT);
                if (providedObject != null) {
                    // cmeng - do not throw, as this hangs the video call screen
                    // throw new RuntimeException("Timeout waiting for preview surface removal");
                    Timber.w("Timeout waiting for preview surface removal!");
                }
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
        ensureViewDestroyed();
    }
}
