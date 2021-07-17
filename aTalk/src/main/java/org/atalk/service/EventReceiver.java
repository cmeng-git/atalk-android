package org.atalk.service;

import android.content.*;
import android.text.TextUtils;

import net.java.sip.communicator.impl.configuration.SQLiteConfigurationStore;
import net.java.sip.communicator.util.ConfigurationUtils;

import org.atalk.android.gui.LauncherActivity;
import org.atalk.persistance.DatabaseBackend;


public class EventReceiver extends BroadcastReceiver
{
    static public final String AUTO_START_ONBOOT = "org.atalk.start_boot";

    @Override
    public void onReceive(Context context, Intent intent)
    {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            if (isAutoStartEnable(context)) {
                Intent i = new Intent(context, LauncherActivity.class);
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                i.putExtra(AUTO_START_ONBOOT, true);
                context.startActivity(i);
            }
            else {
                System.exit(0);
            }
        }
    }

    /**
     * Check if the aTalk auto start on reboot is enabled
     *
     * @param context Application context
     * @return true if aTalk Auto Start Option is enabled by user. false otherwise
     */

    private boolean isAutoStartEnable(Context context)
    {
        DatabaseBackend.getInstance(context);

        SQLiteConfigurationStore store = new SQLiteConfigurationStore();
        String autoStart = (String) store.getProperty(ConfigurationUtils.pAutoStart);
        return (TextUtils.isEmpty(autoStart) || Boolean.parseBoolean(autoStart));
    }
}
