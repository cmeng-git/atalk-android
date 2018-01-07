/*
 * aTalk, android VoIP and Instant Messaging client
 * Copyright 2014 Eng Chong Meng
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.atalk.android.gui.chat.filetransfer;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import net.java.sip.communicator.service.protocol.FileTransfer;
import net.java.sip.communicator.service.protocol.event.FileTransferStatusChangeEvent;
import net.java.sip.communicator.service.protocol.event.FileTransferStatusListener;
import net.java.sip.communicator.util.Logger;

import org.atalk.android.R;
import org.atalk.android.gui.AndroidGUIActivator;
import org.atalk.android.gui.chat.ChatFragment;

import java.io.File;
import java.util.Calendar;
import java.util.Date;

/**
 * The <tt>SendFileConversationComponent</tt> is the component added in the chat conversation
 * when user sends a file.
 *
 * @author Eng Chong Meng
 */
public class SendFileConversation extends FileTransferConversation
		implements FileTransferStatusListener
{
	private static final Logger logger = Logger.getLogger(SendFileConversation.class);

	// private final FileTransfer fileTransfer;
	private String toContactName;
	private ChatFragment mChatFragment;
	private Date date;
	private String dateString;
	private File mSendFile;
	private int msgId;

	public SendFileConversation()
	{
	}

	/**
	 * Creates a <tt>SendFileConversationComponent</tt> by specifying the parent chat panel, where
	 * this component is added, the destination contact of the transfer and file to transfer.
	 *
	 * @param cPanel
	 * 		the parent chat panel, where this view component is added
	 * @param sendTo
	 * 		the name of the destination contact
	 * @param fileName
	 * 		the file to transfer
	 */

	public static SendFileConversation newInstance(ChatFragment cPanel, String sendTo,
			final String fileName)
	{
		SendFileConversation fragmentSFC = new SendFileConversation();
		fragmentSFC.mChatFragment = cPanel;
		fragmentSFC.toContactName = sendTo;
		fragmentSFC.mSendFile = new File(fileName);

		// Create the date that would be shown in ui
		fragmentSFC.date = Calendar.getInstance().getTime();
		fragmentSFC.dateString = fragmentSFC.date.toString() + ":\n";

		return fragmentSFC;
	}

	public View SendFileConversationForm(LayoutInflater inflater,
			ChatFragment.MessageViewHolder msgViewHolder, ViewGroup container, int id,
			boolean init)
	{
		msgId = id;
		View convertView = inflateViewForFileTransfer(inflater, msgViewHolder, container, init);
		messageViewHolder.arrowDir.setImageResource(R.drawable.filexferarrowout);

		this.setCompletedDownloadFile(mChatFragment, mSendFile);
		messageViewHolder.titleLabel.setText(AndroidGUIActivator.getResources().getI18NString(
				"service.gui.FILE_WAITING_TO_ACCEPT", new String[]{dateString, toContactName}));
		messageViewHolder.fileLabel.setText(getFileLabel(mSendFile));

		messageViewHolder.cancelButton.setVisibility(View.VISIBLE);
		messageViewHolder.retryButton.setVisibility(View.GONE);
		messageViewHolder.retryButton.setOnClickListener(new View.OnClickListener()
		{
			public void onClick(View v)
			{
				messageViewHolder.retryButton.setVisibility(View.GONE);
				mChatFragment.new SendFile(mSendFile, SendFileConversation.this, msgId).execute();
			}
		});

		/* Must track file transfer status as Android will request view redraw on listView
		scrolling, new message send or received */
		int status = mChatFragment.getChatListAdapter().getXferStatus(msgId);
		if (status == -1) {
			mChatFragment.new SendFile(mSendFile, SendFileConversation.this, msgId).execute();
		}
		else {
			updateView(status);
		}
		return convertView;
	}

	/**
	 * Handles file transfer status changes. Updates the interface to reflect the changes.
	 */
	private void updateView(final int status)
	{
		boolean bgAlert = false;
		switch (status) {
			case FileTransferStatusChangeEvent.PREPARING:
				messageViewHolder.titleLabel.setText(AndroidGUIActivator.getResources()
						.getI18NString("service.gui.FILE_TRANSFER_PREPARING",
								new String[]{dateString, toContactName}));
				break;

			case FileTransferStatusChangeEvent.IN_PROGRESS:
				if (!messageViewHolder.mProgressBar.isShown()) {
					messageViewHolder.mProgressBar.setVisibility(View.VISIBLE);
					;
					messageViewHolder.mProgressBar.setMax((int) mSendFile.length());
				}

				messageViewHolder.titleLabel.setText(AndroidGUIActivator.getResources()
						.getI18NString("service.gui.FILE_SENDING_TO",
								new String[]{dateString, toContactName}));
				break;

			case FileTransferStatusChangeEvent.COMPLETED:
				messageViewHolder.titleLabel.setText(dateString
						+ AndroidGUIActivator.getResources().getI18NString(
						"service.gui.FILE_SEND_COMPLETED", new String[]{toContactName}));
				messageViewHolder.cancelButton.setVisibility(View.GONE);
				// Do not want to offer file/folder opening on sending
				// messageViewHolder.openFileButton.setVisibility(View.VISIBLE);
				// messageViewHolder.openFolderButton.setVisibility(View.VISIBLE);
				break;

			case FileTransferStatusChangeEvent.FAILED:  // not offer to retry - smack replied as
				// failed when recipient rejects on some devices
				setFailed();
				// messageViewHolder.retryButton.setVisibility(View.VISIBLE);
				bgAlert = true;
				break;

			case FileTransferStatusChangeEvent.CANCELED:
				messageViewHolder.titleLabel.setText( dateString + AndroidGUIActivator
						.getResources().getI18NString("service.gui.FILE_TRANSFER_CANCELED"));
				messageViewHolder.cancelButton.setVisibility(View.GONE);
				bgAlert = true;
				break;

			case FileTransferStatusChangeEvent.REFUSED:
				messageViewHolder.titleLabel.setText(dateString
						+ AndroidGUIActivator.getResources().getI18NString(
						"service.gui.FILE_SEND_REFUSED", new String[]{toContactName}));
				messageViewHolder.retryButton.setVisibility(View.GONE);
				messageViewHolder.cancelButton.setVisibility(View.GONE);
				bgAlert = true;
				break;
		}
		if (bgAlert) {
			messageViewHolder.titleLabel.setTextColor(
					AndroidGUIActivator.getResources().getColor("red"));
		}
	}

	/**
	 * Handles file transfer status changes. Updates the interface to reflect the changes.
	 */
	public void statusChanged(final FileTransferStatusChangeEvent event)
	{
		final FileTransfer fileTransfer = event.getFileTransfer();
		final int status = event.getNewStatus();

		// Event thread - Must execute in UiThread to Update UI information
		runOnUiThread(new Runnable()
		{
			@Override
			public void run()
			{
				updateView(status);

				if (status == FileTransferStatusChangeEvent.COMPLETED
						|| status == FileTransferStatusChangeEvent.CANCELED
						|| status == FileTransferStatusChangeEvent.FAILED
						|| status == FileTransferStatusChangeEvent.REFUSED) {
					// must do this in UI, otherwise the status is not being updated to FileRecord
					fileTransfer.removeStatusListener(SendFileConversation.this);
					// removeProgressListener();
				}
			}
		});
	}

	/**
	 * Sets the <tt>FileTransfer</tt> object received from the protocol and corresponding to the
	 * file transfer process associated with this
	 * panel.
	 *
	 * @param fileTransfer
	 * 		the <tt>FileTransfer</tt> object associated with this panel
	 */
	public void setProtocolFileTransfer(FileTransfer fileTransfer)
	{
		// activate File History service to keep track of the progress - need more work if want to
		// keep sending history.
		// fileTransfer.addStatusListener(new FileHistoryServiceImpl());

		this.fileTransfer = fileTransfer;
		fileTransfer.addStatusListener(this);
		this.setFileTransfer(fileTransfer, mSendFile.length());
	}

	/**
	 * Change the style of the component to be failed. Caller must in UI thread to call this.
	 */
	public void setFailed()
	{
		// hideProgressRelatedComponents();
		messageViewHolder.titleLabel.setText(dateString
				+ AndroidGUIActivator.getResources().getI18NString(
				"service.gui.FILE_UNABLE_TO_SEND", new String[]{toContactName}));
		messageViewHolder.cancelButton.setVisibility(View.GONE);
	}

	/**
	 * Returns the label to show on the progress bar.
	 *
	 * @param bytesString
	 * 		the bytes that have been transferred
	 * @return the label to show on the progress bar
	 */
	@Override
	protected String getProgressLabel(String bytesString)
	{
		return bytesString + " " + AndroidGUIActivator.getResources().getI18NString(
				"service.gui.SENT");
	}
}
