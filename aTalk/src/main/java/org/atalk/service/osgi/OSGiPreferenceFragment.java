/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.service.osgi;

import android.content.Context;
import android.os.Bundle;
import android.preference.PreferenceFragment;

import org.osgi.framework.BundleContext;

/**
 * Class can be used to build {@link android.preference.PreferenceFragment}s that require OSGI services access.
 *
 * @author Pawel Domas
 */
public class OSGiPreferenceFragment extends PreferenceFragment implements OSGiUiPart
{

    protected BundleContext osgiContext;

    private boolean viewCreated = false;

    private boolean osgiNotified = false;

    /**
     * {@inheritDoc}
     */
    @Override
    public void onActivityCreated(Bundle savedInstanceState)
    {
        super.onActivityCreated(savedInstanceState);
        viewCreated = true;
        if (!osgiNotified && osgiContext != null) {
            onOSGiConnected();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onDestroyView()
    {
        viewCreated = false;
        osgiNotified = false;
        super.onDestroyView();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onAttach(Context context)
    {
        super.onAttach(context);
        OSGiActivity osGiActivity = (OSGiActivity) getActivity();
        osGiActivity.registerOSGiFragment(this);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onDetach()
    {
        ((OSGiActivity) getActivity()).unregisterOSGiFragment(this);
        super.onDetach();
    }

    /**
     * Fired when OSGI is started and the <tt>bundleContext</tt> is available.
     *
     * @param bundleContext the OSGI bundle context.
     */
    public void start(BundleContext bundleContext)
            throws Exception
    {
        this.osgiContext = bundleContext;
        if (viewCreated && !osgiNotified) {
            onOSGiConnected();
        }
    }

    /**
     * Method fired when OSGI context is attached, but after the <tt>View</tt> is created.
     */
    protected void onOSGiConnected()
    {
        osgiNotified = true;
    }

    /**
     * Fired when parent <tt>OSGiActivity</tt> is being stopped or this fragment is being detached.
     *
     * @param bundleContext the OSGI bundle context.
     */
    public void stop(BundleContext bundleContext)
            throws Exception
    {
        this.osgiContext = null;
    }
}
