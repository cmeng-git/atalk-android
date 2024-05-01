/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.service.osgi;

import android.content.Context;
import android.os.Looper;

import androidx.fragment.app.DialogFragment;

import org.jetbrains.annotations.NotNull;
import org.osgi.framework.BundleContext;

/**
 * Class can be used to build {@link DialogFragment}s that require OSGI services access.
 *
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
public class OSGiDialogFragment extends DialogFragment implements OSGiUiPart
{
    private OSGiActivity osGiActivity;

    /**
     * {@inheritDoc}
     */
    @Override
    public void onAttach(@NotNull Context context)
    {
        super.onAttach(context);
        osGiActivity = (OSGiActivity) getActivity();
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
     * guaranteed that <code>getActivity()</code> will return not <code>null</code> value, hence it must be checked in the
     * <code>action</code>.
     *
     * @param action <code>Runnable</code> action to execute on UI thread.
     */
    protected void runOnUiThread(Runnable action)
    {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            action.run();
            return;
        }
        // Post action to the ui looper
        OSGiActivity.uiHandler.post(action);
    }
}
