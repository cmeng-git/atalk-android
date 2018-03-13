/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.android.gui.settings.notification;

import java.util.ArrayList;
import java.util.Iterator;

import net.java.sip.communicator.service.notification.NotificationChangeListener;
import net.java.sip.communicator.service.notification.NotificationService;
import net.java.sip.communicator.service.notification.event.NotificationActionTypeEvent;
import net.java.sip.communicator.service.notification.event.NotificationEventTypeEvent;
import net.java.sip.communicator.util.ServiceUtils;

import org.atalk.android.R;
import org.atalk.android.gui.AndroidGUIActivator;
import org.atalk.service.osgi.OSGiActivity;
import org.atalk.service.resources.ResourceManagementService;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.CompoundButton;
import android.widget.ListView;
import android.widget.TextView;

/**
 * The <tt>Activity</tt> lists all notification events. When user selects one of them the details screen is opened.
 *
 * @author Pawel Domas
 */
public class NotificationSettings extends OSGiActivity
{
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
		ListView listView =  findViewById(R.id.list);
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
		private final ArrayList<String> events;

		/**
		 * Resources service instance
		 */
		private final ResourceManagementService rms;

		/**
		 * Creates new instance of <tt>NotificationsAdapter</tt>.
		 */
		NotificationsAdapter() {
			Iterator<String> eventsIter = notificationService.getRegisteredEvents().iterator();
			this.events = new ArrayList<String>();
			while (eventsIter.hasNext()) {
				events.add(eventsIter.next());
			}
			this.rms = AndroidGUIActivator.getResourcesService();
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
			return rms.getI18NString("plugin.notificationconfig.event." + events.get(position));
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
		public View getView(final int position, View convertView, ViewGroup parent)
		{
			View row = getLayoutInflater().inflate(R.layout.notification_item, parent, false);
			row.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v)
				{
					Intent details = NotificationDetails.getIntent(NotificationSettings.this, events.get(position));
					startActivity(details);
				}
			});

			TextView textView = (TextView) row.findViewById(R.id.text1);
			textView.setText((String) getItem(position));
			CompoundButton enableBtn = (CompoundButton) row.findViewById(R.id.enable);
			enableBtn.setChecked(notificationService.isActive(events.get(position)));

			enableBtn.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
				@Override
				public void onCheckedChanged(CompoundButton buttonView, boolean isChecked)
				{
					notificationService.setActive(events.get(position), isChecked);
				}
			});
			return row;
		}

		/**
		 * {@inheritDoc}
		 */
		@Override
		public void eventTypeAdded(final NotificationEventTypeEvent event)
		{
			runOnUiThread(new Runnable() {
				@Override
				public void run()
				{
					events.add(event.getEventType());
					notifyDataSetChanged();
				}
			});
		}

		/**
		 * {@inheritDoc}
		 */
		@Override
		public void eventTypeRemoved(final NotificationEventTypeEvent event)
		{
			runOnUiThread(new Runnable() {
				@Override
				public void run()
				{
					events.remove(event.getEventType());
					notifyDataSetChanged();
				}
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
