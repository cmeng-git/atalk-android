/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.androidtray;

import android.app.*;
import android.content.*;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.support.v4.app.NotificationCompat;

import net.java.sip.communicator.impl.protocol.jabber.ChatRoomJabberImpl;
import net.java.sip.communicator.service.contactlist.MetaContact;
import net.java.sip.communicator.service.muc.ChatRoomWrapper;
import net.java.sip.communicator.service.protocol.*;
import net.java.sip.communicator.service.systray.PopupMessage;
import net.java.sip.communicator.util.Logger;

import org.atalk.android.*;
import org.atalk.android.gui.AndroidGUIActivator;
import org.atalk.android.gui.chat.*;
import org.atalk.android.gui.dialogs.DialogActivity;
import org.atalk.android.gui.util.AndroidImageUtil;
import org.atalk.android.plugin.notificationwiring.AndroidNotifications;

import java.util.*;

/**
 * Class manages displayed notification for given <tt>PopupMessage</tt>.
 *
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
public class AndroidPopup
{
	/**
	 * The logger.
	 */
	private final static Logger logger = Logger.getLogger(AndroidPopup.class);

	/**
	 * Parent notifications handler
	 */
	protected final NotificationPopupHandler handler;

	/**
	 * Displayed <tt>PopupMessage</tt>.
	 */
	protected PopupMessage popupMessage;

	/**
	 * Timeout handler.
	 */
	protected Timer timeoutHandler;

	/**
	 * Notification id.
	 */
	protected int id;

	/**
	 * Optional chatTransport descriptor if supplied by <tt>PopupMessage</tt>.
	 */
	protected Object mDescriptor;

	/**
	 * Small icon used for this notification.
	 */
	private int mSmallIcon;

	private String group = null;

	/**
	 * Creates new instance of <tt>AndroidPopup</tt>.
	 *
	 * @param handler
	 * 		parent notifications handler that manages displayed notifications.
	 * @param popupMessage
	 * 		the popup message that will be displayed by this instance.
	 */
	protected AndroidPopup(NotificationPopupHandler handler, PopupMessage popupMessage)
	{
		this.handler = handler;
		this.popupMessage = popupMessage;

		// Default Jitsi icon
		mSmallIcon = R.drawable.ic_notification;

		// Null group is sharing general notification icon
		if (popupMessage.getGroup() == null) {
			// By default all notifications share Jitsi icon
			id = SystrayServiceImpl.getGeneralNotificationId();
		}
		else {
			// Generate separate notification
			id = (int) (System.currentTimeMillis() % Integer.MAX_VALUE);

			group = popupMessage.getGroup();
			// Set message icon
			if (AndroidNotifications.MESSAGE_GROUP.equals(group)) {
				mSmallIcon = R.drawable.incoming_message;
			}
			else if (AndroidNotifications.FILE_GROUP.equals(group)) {
				mSmallIcon = R.drawable.incoming_file;
			}
			else if (AndroidNotifications.CALL_GROUP.equals(group)) {
				mSmallIcon = R.drawable.missed_call;
			}
		}
		// Extract contained chat descriptor if any
		mDescriptor = popupMessage.getTag();
	}

	/**
	 * Returns displayed <tt>PopupMessage</tt>.
	 *
	 * @return displayed <tt>PopupMessage</tt>.
	 */
	public PopupMessage getPopupMessage()
	{
		return popupMessage;
	}

	/**
	 * Removes this notification.
	 */
	public void removeNotification()
	{
		cancelTimeout();

		NotificationManager notifyManager = aTalkApp.getNotificationManager();
		notifyManager.cancel(id);
	}

	/**
	 * Returns <tt>true</tt> if this popup is related to given <tt>ChatPanel</tt>.
	 *
	 * @param chatPanel
	 * 		the <tt>ChatPanel</tt> to check.
	 * @return <tt>true</tt> if this popup is related to given <tt>ChatPanel</tt>.
	 */
	public boolean isChatRelated(ChatPanel chatPanel)
	{
		if (chatPanel != null) {
			Object descriptor
					= chatPanel.getChatSession().getCurrentChatTransport().getDescriptor();
			return (mDescriptor != null) && (descriptor != null) && descriptor.equals(mDescriptor)
					&& (AndroidNotifications.MESSAGE_GROUP.equals(group)
					|| AndroidNotifications.FILE_GROUP.equals(group));
		}
		else {
			return false;
		}
	}

	/**
	 * Returns notification id.
	 *
	 * @return notification id.
	 */
	public int getId()
	{
		return id;
	}

	/**
	 * Creates new <tt>AndroidPopup</tt> for given parameters.
	 *
	 * @param handler
	 * 		notifications manager.
	 * @param popupMessage
	 * 		the popup message that will be displayed by returned <tt>AndroidPopup</tt>
	 * @return new <tt>AndroidPopup</tt> for given parameters.
	 */
	static public AndroidPopup createNew(NotificationPopupHandler handler, PopupMessage
			popupMessage)
	{
		return new AndroidPopup(handler, popupMessage);
	}

	/**
	 * Tries to merge given <tt>PopupMessage</tt> with this instance. Will return merged
	 * <tt>AndroidPopup</tt> or <tt>null</tt> otherwise.
	 *
	 * @param popupMessage
	 * 		the <tt>PopupMessage</tt> to merge.
	 * @return merged <tt>AndroidPopup</tt> with given <tt>PopupMessage</tt> or <tt>null</tt>
	 * otherwise.
	 */
	public AndroidPopup tryMerge(PopupMessage popupMessage)
	{
		if (this.isGroupTheSame(popupMessage) && isSenderTheSame(popupMessage)) {
			return mergePopup(popupMessage);
		}
		else {
			return null;
		}
	}

	/**
	 * Merges this instance with given <tt>PopupMessage</tt>.
	 *
	 * @param popupMessage
	 * 		the <tt>PopupMessage</tt> to merge.
	 * @return merge result for this <tt>AndroidPopup</tt> and given <tt>PopupMessage</tt>.
	 */
	protected AndroidPopup mergePopup(PopupMessage popupMessage)
	{
		// Timeout notifications are replaced
		/*
		 * if(this.timeoutHandler != null) { cancelTimeout(); this.popupMessage = popupMessage;
		 * return this; } else {
		 */
		AndroidMergedPopup merge = new AndroidMergedPopup(this);
		merge.mergePopup(popupMessage);
		return merge;
		// }
	}

	/**
	 * Checks whether <tt>Contact</tt> of this instance matches with given <tt>PopupMessage</tt>.
	 *
	 * @param popupMessage
	 * 		the <tt>PopupMessage</tt> to check.
	 * @return <tt>true</tt> if <tt>Contact</tt>s for this instance and given
	 * <tt>PopupMessage</tt> are the same.
	 */
	private boolean isSenderTheSame(PopupMessage popupMessage)
	{
		return (mDescriptor != null) && mDescriptor.equals(popupMessage.getTag());
	}

	/**
	 * Checks whether group of this instance matches with given <tt>PopupMessage</tt>.
	 *
	 * @param popupMessage
	 * 		the <tt>PopupMessage</tt> to check.
	 * @return <tt>true</tt> if group of this instance and given <tt>PopupMessage</tt> are the
	 * same.
	 */
	private boolean isGroupTheSame(PopupMessage popupMessage)
	{
		if (this.popupMessage.getGroup() == null) {
			return popupMessage.getGroup() == null;
		}
		else {
			return this.popupMessage.getGroup().equals(popupMessage.getGroup());
		}
	}

	/**
	 * Returns message string that will displayed in single line notification.
	 *
	 * @return message string that will displayed in single line notification.
	 */
	protected String getMessage()
	{
		return popupMessage.getMessage();
	}

	/**
	 * Builds notification and returns the builder object which can be used to extend the
	 * notification.
	 *
	 * @return builder object describing current notification.
	 */
	NotificationCompat.Builder buildNotification()
	{
		Context ctx = aTalkApp.getGlobalContext();
		NotificationCompat.Builder builder = new NotificationCompat.Builder(ctx)
				.setSmallIcon(mSmallIcon)
				.setContentTitle(popupMessage.getMessageTitle())
				.setContentText(getMessage()).setAutoCancel(true) // will be cancelled once clicked
				.setVibrate(new long[]{}) // no vibration
				.setSound(null); // no sound

		Resources res = aTalkApp.getAppResources();

		// Preferred size
		int prefWidth = (int) res.getDimension(android.R.dimen.notification_large_icon_width);
		int prefHeight = (int) res.getDimension(android.R.dimen.notification_large_icon_height);

		// Use popup icon if any
		Bitmap iconBmp = null;
		byte[] icon = popupMessage.getIcon();
		if (icon != null) {
			iconBmp = AndroidImageUtil.scaledBitmapFromBytes(icon, prefWidth, prefHeight);
		}
		// Set default avatar
		if (iconBmp == null && mDescriptor != null) {
			if (mDescriptor instanceof ChatRoom)
				iconBmp = AndroidImageUtil.scaledBitmapFromResource(res, R.drawable.ic_chatroom,
						prefWidth, prefHeight);
			else
				iconBmp = AndroidImageUtil.scaledBitmapFromResource(res, R.drawable.avatar,
						prefWidth, prefHeight);
		}
		if (iconBmp != null) {
			if (iconBmp.getWidth() > prefWidth || iconBmp.getHeight() > prefHeight) {
				iconBmp = Bitmap.createScaledBitmap(iconBmp, prefWidth, prefHeight, true);
			}
			builder.setLargeIcon(iconBmp);
		}
		// Build inbox style
		NotificationCompat.InboxStyle inboxStyle = new NotificationCompat.InboxStyle();
		onBuildInboxStyle(inboxStyle);
		builder.setStyle(inboxStyle);

		return builder;
	}

	/**
	 * Returns the <tt>PendingIntent</tt> that should be trigger when user clicks the notification.
	 *
	 * @return the <tt>PendingIntent</tt> that should be trigger by notification
	 */
	public PendingIntent constructIntent()
	{
		Intent targetIntent = null;
		PopupMessage message = getPopupMessage();

		String group = (message != null) ? message.getGroup() : null;
		if (AndroidNotifications.MESSAGE_GROUP.equals(
				group) || AndroidNotifications.FILE_GROUP.equals(group)) {
			Object tag = message.getTag();
			if (tag instanceof Contact) {
				Contact contact = (Contact) tag;
				MetaContact metaContact = AndroidGUIActivator.getContactListService()
						.findMetaContactByContact(
								contact);
				if (metaContact == null) {
					logger.error("Meta contact not found for " + contact);
				}
				else {
					targetIntent = ChatSessionManager.getChatIntent(metaContact);
				}
			}
			else if (tag instanceof ChatRoomJabberImpl) {
				ChatRoomJabberImpl chatRoom = (ChatRoomJabberImpl) tag;
				ChatRoomWrapper chatRoomWrapper = AndroidGUIActivator.getMUCService()
						.getChatRoomWrapperByChatRoom(
								chatRoom, true);
				if (chatRoomWrapper == null) {
					logger.error("ChatRoomWrapper not found for " + chatRoom.getIdentifier());
				}
				else {
					targetIntent = ChatSessionManager.getChatIntent(chatRoomWrapper);
				}
			}
		}
		// Displays popup message details when the notification is clicked when targetIntent is
		// null
		if ((message != null) && (targetIntent == null)) {
			targetIntent = DialogActivity.getDialogIntent(aTalkApp.getGlobalContext(),
					message.getMessageTitle(), message.getMessage());
		}

		if (targetIntent == null) {
			return null;
		}
		else {
			// Must be unique for each, so use the notification id as the request code
			return PendingIntent.getActivity(aTalkApp.getGlobalContext(), getId(), targetIntent,
					PendingIntent.FLAG_UPDATE_CURRENT);
		}
	}

	/**
	 * Method fired when large notification view using <tt>InboxStyle</tt> is being built.
	 *
	 * @param inboxStyle
	 * 		the inbox style instance used for building large notification view.
	 */
	protected void onBuildInboxStyle(NotificationCompat.InboxStyle inboxStyle)
	{
		inboxStyle.addLine(popupMessage.getMessage());
		// Summary
		if ((mDescriptor != null) && mDescriptor instanceof Contact) {
			ProtocolProviderService pps = ((Contact) mDescriptor).getProtocolProvider();
			if (pps != null) {
				inboxStyle.setSummaryText(pps.getAccountID().getDisplayName());
			}
		}
	}

	/**
	 * Cancels the timeout if it exists.
	 */
	protected void cancelTimeout()
	{
		// Remove timeout handler
		if (timeoutHandler != null) {
			logger.debug("Removing timeout from notification: " + id);

			timeoutHandler.cancel();
			timeoutHandler = null;
		}
	}

	/**
	 * Method called by notification manger when the notification is posted to the tray.
	 */
	public void onPost()
	{
		cancelTimeout();
		long timeout = popupMessage.getTimeout();
		if (timeout > 0) {
			logger.debug("Setting timeout " + timeout + " on notification: " + id);

			timeoutHandler = new Timer();
			timeoutHandler.schedule(new TimerTask()
			{
				@Override
				public void run()
				{
					handler.onTimeout(AndroidPopup.this);
				}
			}, timeout);
		}
	}
}
