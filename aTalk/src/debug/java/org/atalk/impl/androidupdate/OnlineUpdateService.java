package org.atalk.impl.androidupdate;

import android.app.*;
import android.content.Context;
import android.content.Intent;

import net.java.sip.communicator.service.update.UpdateService;
import net.java.sip.communicator.util.ServiceUtils;

import org.atalk.android.R;
import org.atalk.android.gui.AndroidGUIActivator;
import org.atalk.android.gui.settings.SettingsActivity;
import org.atalk.impl.androidnotification.AndroidNotifications;
import org.atalk.service.configuration.ConfigurationService;

import java.util.Calendar;

import androidx.core.app.NotificationCompat;

public class OnlineUpdateService extends IntentService
{
    public static final String ACTION_AUTO_UPDATE_APP = "org.atalk.android.ACTION_AUTO_UPDATE_APP";
    public static final String ACTION_AUTO_UPDATE_START = "org.atalk.android.ACTION_AUTO_UPDATE_START";
    public static final String ACTION_AUTO_UPDATE_STOP = "org.atalk.android.ACTION_AUTO_UPDATE_STOP";

    private static final String ACTION_UPDATE_AVAILABLE = "org.atalk.android.ACTION_UPDATE_AVAILABLE";
    private static final String ONLINE_UPDATE_SERVICE = "OnlineUpdateService";
    private static final String UPDATE_AVAIL_TAG = "aTalk Update Available";

    // in unit of seconds
    public static int CHECK_INTERVAL_ON_LAUNCH = 30;
    public static int CHECK_NEW_VERSION_INTERVAL = 24 * 60 * 60;
    private static final int UPDATE_AVAIL_NOTIFY_ID = 1;

    private NotificationManager mNotificationMgr;

    public OnlineUpdateService()
    {
        super(ONLINE_UPDATE_SERVICE);
    }

    @Override
    public void onCreate()
    {
        super.onCreate();
        mNotificationMgr = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
    }

    @Override
    protected void onHandleIntent(Intent intent)
    {
        if (intent != null) {
            String action = intent.getAction();
            if (action != null) {
                switch (action) {
                    case ACTION_AUTO_UPDATE_APP:
                        checkAppUpdate();
                        break;
                    case ACTION_UPDATE_AVAILABLE:
                        UpdateServiceImpl updateService = (UpdateServiceImpl) ServiceUtils
                                .getService(AndroidGUIActivator.bundleContext, UpdateService.class);
                        if (updateService != null) {
                            updateService.checkForUpdates(true);
                        }
                        break;
                    case ACTION_AUTO_UPDATE_START:
                        setNextAlarm(CHECK_INTERVAL_ON_LAUNCH);
                        break;
                    case ACTION_AUTO_UPDATE_STOP:
                        stopAlarm();
                        break;
                }
            }
        }
    }

    private void checkAppUpdate()
    {
        boolean isAutoUpdateCheckEnable = true;
        ConfigurationService cfg = AndroidGUIActivator.getConfigurationService();
        if (cfg != null)
            isAutoUpdateCheckEnable = cfg.getBoolean(SettingsActivity.AUTO_UPDATE_CHECK_ENABLE, true);

        UpdateService updateService = ServiceUtils.getService(AndroidGUIActivator.bundleContext, UpdateService.class);
        if (updateService != null) {
            boolean isLatest = updateService.isLatestVersion();

            if (!isLatest) {
                NotificationCompat.Builder nBuilder;
                nBuilder = new NotificationCompat.Builder(this, AndroidNotifications.DEFAULT_GROUP);

                String msgString = getString(R.string.plugin_newsoftware_DIALOG_MESSAGE,
                        updateService.getLatestVersion());
                nBuilder.setSmallIcon(R.drawable.ic_notification);
                nBuilder.setWhen(System.currentTimeMillis());
                nBuilder.setAutoCancel(true);
                nBuilder.setTicker(msgString);
                nBuilder.setContentTitle(getString(R.string.APPLICATION_NAME));
                nBuilder.setContentText(msgString);

                Intent intent = new Intent(this.getApplicationContext(), OnlineUpdateService.class);
                intent.setAction(ACTION_UPDATE_AVAILABLE);
                PendingIntent pending = PendingIntent.getService(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
                nBuilder.setContentIntent(pending);
                mNotificationMgr.notify(UPDATE_AVAIL_TAG, UPDATE_AVAIL_NOTIFY_ID, nBuilder.build());
            }
        }

        if (isAutoUpdateCheckEnable)
            setNextAlarm(CHECK_NEW_VERSION_INTERVAL);
    }

    private void setNextAlarm(int nextAlarmTime)
    {
        AlarmManager alarmManager = (AlarmManager) this.getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(this.getApplicationContext(), OnlineUpdateService.class);
        intent.setAction(ACTION_AUTO_UPDATE_APP);
        PendingIntent pendingIntent = PendingIntent.getService(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(System.currentTimeMillis());
        cal.add(Calendar.SECOND, nextAlarmTime);
        alarmManager.cancel(pendingIntent);
        alarmManager.set(AlarmManager.RTC_WAKEUP, cal.getTimeInMillis(), pendingIntent);
    }

    private void stopAlarm()
    {
        AlarmManager alarmManager = (AlarmManager) this.getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(this.getApplicationContext(), OnlineUpdateService.class);
        intent.setAction(ACTION_AUTO_UPDATE_APP);
        PendingIntent pendingIntent = PendingIntent.getService(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
        alarmManager.cancel(pendingIntent);
    }
}