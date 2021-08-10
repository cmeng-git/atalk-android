/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.android.gui.settings.notification;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;

import net.java.sip.communicator.service.notification.NotificationChangeListener;
import net.java.sip.communicator.service.notification.NotificationService;
import net.java.sip.communicator.service.notification.event.NotificationActionTypeEvent;
import net.java.sip.communicator.service.notification.event.NotificationEventTypeEvent;
import net.java.sip.communicator.util.ServiceUtils;
import net.java.sip.communicator.util.UtilActivator;

import org.atalk.android.R;
import org.atalk.android.gui.AndroidGUIActivator;
import org.atalk.service.osgi.OSGiActivity;
import org.atalk.service.resources.ResourceManagementService;

import java.util.*;

/**
 * The <tt>Activity</tt> lists all notification events. When user selects one of them the details screen is opened.
 *
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
public class NotificationSettings extends OSGiActivity
{
    public static final String NOTICE_PREFIX = "plugin.notificationconfig.event.";

    /**
     * Notifications adapter.
     */
    private NotificationsAdapter adapter;

    /**
     * Notification service instance.
     */
    private NotificationService notificationService;

    /**
     * {@inheritDoc}
     */
    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        this.notificationService = ServiceUtils.getService(AndroidGUIActivator.bundleContext, NotificationService.class);
        setContentView(R.layout.list_layout);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void onResume()
    {
        super.onResume();
        // Refresh the list each time is displayed
        adapter = new NotificationsAdapter();
        ListView listView = findViewById(R.id.list);
        listView.setAdapter(adapter);
        // And start listening for updates
        notificationService.addNotificationChangeListener(adapter);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void onPause()
    {
        super.onPause();
        // Do not listen for changes when paused
        notificationService.removeNotificationChangeListener(adapter);
        adapter = null;
    }

    /**
     * Adapter lists all notification events.
     */
    class NotificationsAdapter extends BaseAdapter implements NotificationChangeListener
    {
        /**
         * List of event types
         */
        private final ArrayList<String> events = new ArrayList<>();

        /**
         * Map of events => eventType : eventName in ascending order by eventName
         */
        private final Map<String, String> sortedEvents = new TreeMap<>();

        /**
         * Creates new instance of <tt>NotificationsAdapter</tt>;
         * Values are sorted in ascending order by eventNames for user easy reference.
         */
        NotificationsAdapter()
        {
            ResourceManagementService rms = UtilActivator.getResources();
            Map<String, String> unSortedMap = new HashMap<>();
            for (String event : notificationService.getRegisteredEvents()) {
                unSortedMap.put(rms.getI18NString(NOTICE_PREFIX + event), event);
            }

            // sort and save copies in sortedEvents and events
            Map<String, String> sortedMap = new TreeMap<>(unSortedMap);
            for (Map.Entry<String, String> entry : sortedMap.entrySet()) {
                sortedEvents.put(entry.getValue(), entry.getKey());
                events.add(entry.getValue());
            }
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public int getCount()
        {
            return events.size();
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public Object getItem(int position)
        {
            return sortedEvents.get(events.get(position));
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public long getItemId(int position)
        {
            return position;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public View getView(final int position, View rowView, ViewGroup parent)
        {
            //if (rowView == null) cmeng would not update properly the status on enter/return
            rowView = getLayoutInflater().inflate(R.layout.notification_item, parent, false);

            String eventType = events.get(position);
            rowView.setOnClickListener(v -> {
                Intent details = NotificationDetails.getIntent(NotificationSettings.this, eventType);
                startActivity(details);
            });

            TextView textView = rowView.findViewById(R.id.descriptor);
            textView.setText((String) getItem(position));

            CompoundButton enableBtn = rowView.findViewById(R.id.enable);
            enableBtn.setChecked(notificationService.isActive(eventType));

            enableBtn.setOnCheckedChangeListener((buttonView, isChecked)
                    -> notificationService.setActive(eventType, isChecked));
            return rowView;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void eventTypeAdded(final NotificationEventTypeEvent event)
        {
            runOnUiThread(() -> {
                events.add(event.getEventType());
                notifyDataSetChanged();
            });
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void eventTypeRemoved(final NotificationEventTypeEvent event)
        {
            runOnUiThread(() -> {
                events.remove(event.getEventType());
                notifyDataSetChanged();
            });
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void actionAdded(NotificationActionTypeEvent event)
        {
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void actionRemoved(NotificationActionTypeEvent event)
        {
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void actionChanged(NotificationActionTypeEvent event)
        {
        }
    }
}
