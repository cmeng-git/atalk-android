package org.atalk.service;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.text.TextUtils;

import net.java.sip.communicator.impl.configuration.SQLiteConfigurationStore;
import net.java.sip.communicator.util.ConfigurationUtils;

import org.atalk.android.aTalkApp;
import org.atalk.android.gui.LauncherActivity;
import org.atalk.persistance.DatabaseBackend;

import timber.log.Timber;

public class SystemEventReceiver extends BroadcastReceiver
{
    public static final String AUTO_START_ONBOOT = "org.atalk.start_boot";

    @Override
    public void onReceive(Context context, Intent intent)
    {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            if (isAutoStartEnable(context)) {
                if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
                    String msg = "aTalk cannot support autoStart onBoot for android API: " + Build.VERSION.SDK_INT;
                    Timber.w("%s", msg);
                    aTalkApp.showToastMessage(msg);
                }
                else {
                    Intent i = new Intent(context, LauncherActivity.class);
                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    i.putExtra(AUTO_START_ONBOOT, true);
                    context.startActivity(i);
                }
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

        SQLiteConfigurationStore store = new SQLiteConfigurationStore(context);
        String autoStart = (String) store.getProperty(ConfigurationUtils.pAutoStart);
        return (TextUtils.isEmpty(autoStart) || Boolean.parseBoolean(autoStart));
    }
}
