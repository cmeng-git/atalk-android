/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.service.osgi;

import android.content.Context;
import android.os.Looper;

import androidx.fragment.app.Fragment;

import org.jetbrains.annotations.NotNull;
import org.osgi.framework.BundleContext;

/**
 * Class can be used to build {@link Fragment}s that require OSGI services access.
 *
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
public class OSGiFragment extends Fragment implements OSGiUiPart
{
    private OSGiActivity osGiActivity;

    /**
     * {@inheritDoc}
     */
    @Override
    public void onAttach(@NotNull Context context)
    {
        super.onAttach(context);
        this.osGiActivity = (OSGiActivity) getActivity();
        if (osGiActivity != null)
            osGiActivity.registerOSGiFragment(this);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onDetach()
    {
        osGiActivity.unregisterOSGiFragment(this);
        super.onDetach();
    }

    /**
     * {@inheritDoc}
     */
    public void start(BundleContext bundleContext)
            throws Exception
    {
    }

    /**
     * {@inheritDoc}
     */
    public void stop(BundleContext bundleContext)
            throws Exception
    {
    }

    /**
     * Convenience method for running code on UI thread looper(instead of getActivity().runOnUIThread()). It is never
     * guaranteed that <tt>getActivity()</tt> will return not <tt>null</tt> value, hence it must be checked in the
     * <tt>action</tt>.
     *
     * @param action <tt>Runnable</tt> action to execute on UI thread.
     */
    public void runOnUiThread(Runnable action)
    {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            action.run();
            return;
        }
        // Post action to the ui looper
        OSGiActivity.uiHandler.post(action);
    }
}
