/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package net.java.sip.communicator.impl.sysactivity;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;

import androidx.core.content.ContextCompat;

import net.java.sip.communicator.service.sysactivity.event.SystemActivityEvent;

import org.atalk.android.aTalkApp;

/**
 * Listens for broadcasts from ConnectivityManager to get notified for network changes.
 *
 * @author Damian Minkov
 */
public class ConnectivityManagerListenerImpl extends BroadcastReceiver
        implements SystemActivityManager
{
    /**
     * The action name we will receive broadcasts for to get informed
     * for connectivity changes.
     */
    private static final String CONNECTIVITY_CHANGE_ACTION = "android.net.conn.CONNECTIVITY_CHANGE";

    /**
     * The only instance of this impl.
     */
    private static ConnectivityManagerListenerImpl connectivityManagerListenerImpl;

    /**
     * Whether we are working.
     */
    private boolean connected = false;

    /**
     * Gets the instance of <code>ConnectivityManagerListenerImpl</code>.
     *
     * @return the ConnectivityManagerListenerImpl.
     */
    public static ConnectivityManagerListenerImpl getInstance()
    {
        if (connectivityManagerListenerImpl == null)
            connectivityManagerListenerImpl = new ConnectivityManagerListenerImpl();

        return connectivityManagerListenerImpl;
    }

    /**
     * Starts
     */
    public void start()
    {
        ContextCompat.registerReceiver(aTalkApp.getInstance(), this,
                new IntentFilter(CONNECTIVITY_CHANGE_ACTION), ContextCompat.RECEIVER_EXPORTED);
        connected = true;
    }

    /**
     * Stops.
     */
    public void stop()
    {
        Context context = aTalkApp.getGlobalContext();
        context.unregisterReceiver(this);
        connected = false;
    }

    /**
     * Whether the underlying implementation is currently connected and working.
     *
     * @return whether we are connected and working.
     */
    public boolean isConnected()
    {
        return connected;
    }

    /**
     * Receiving broadcast for network change.
     *
     * @param context the context.
     * @param intent the intent for the broadcast.
     */
    public void onReceive(Context context, Intent intent)
    {
        if (intent.getAction().equals(CONNECTIVITY_CHANGE_ACTION)) {
            SystemActivityEvent evt = new SystemActivityEvent(
                    SysActivityActivator.getSystemActivityService(), SystemActivityEvent.EVENT_NETWORK_CHANGE);

            SysActivityActivator.getSystemActivityService().fireSystemActivityEvent(evt);
        }
    }
}
