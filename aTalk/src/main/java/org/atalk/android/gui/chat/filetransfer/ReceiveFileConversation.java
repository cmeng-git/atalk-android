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

import android.os.*;
import android.view.*;

import net.java.sip.communicator.service.protocol.*;
import net.java.sip.communicator.service.protocol.event.*;
import net.java.sip.communicator.util.Logger;

import org.atalk.android.R;
import org.atalk.android.gui.AndroidGUIActivator;
import org.atalk.android.gui.chat.ChatFragment;

import java.io.File;
import java.util.Date;

/**
 * The <tt>ReceiveFileConversationComponent</tt> is the component shown in the conversation area
 * of the chat window to display a incoming file transfer.
 *
 * @author Eng Chong Meng
 */
public class ReceiveFileConversation extends FileTransferConversation
		implements FileTransferListener, FileTransferStatusListener
{
	private final Logger logger = Logger.getLogger(ReceiveFileConversation.class);

	private IncomingFileTransferRequest fileTransferRequest;
	private OperationSetFileTransfer fileTransferOpSet;
	private ChatFragment mChatFragment;
	private Date date;
	private String dateString;
	private String senderName;

	private int msgId;
	private File downloadFile;

	public ReceiveFileConversation()
	{
	}

	/**
	 * Creates a <tt>ReceiveFileConversationComponent</tt>.
	 *
	 * @param cPanel
	 * 		the chat panel
	 * @param opSet
	 * 		the <tt>OperationSetFileTransfer</tt>
	 * @param request
	 * 		the <tt>IncomingFileTransferRequest</tt> associated with this component
	 * @param mDate
	 * 		the date
	 */
	// Constructor used by ChatFragment to start handle ReceiveFileTransferRequest
	public static ReceiveFileConversation newInstance(ChatFragment cPanel, String sendTo,
			OperationSetFileTransfer opSet, IncomingFileTransferRequest request, final Date mDate)
	{
		ReceiveFileConversation fragmentRFC = new ReceiveFileConversation();
		fragmentRFC.mChatFragment = cPanel;
		fragmentRFC.senderName = sendTo;
		fragmentRFC.fileTransferOpSet = opSet;
		fragmentRFC.fileTransferRequest = request;
		fragmentRFC.date = mDate;
		fragmentRFC.dateString = mDate.toString() + ":\n";

		// need to enable FileTransferListener for ReceiveFileConversion reject/cancellation.
		fragmentRFC.fileTransferOpSet.addFileTransferListener(fragmentRFC);
		return fragmentRFC;
	}

	public View ReceiveFileConversionForm(LayoutInflater inflater,
			ChatFragment.MessageViewHolder msgViewHolder, ViewGroup container, int id,
			boolean init)
	{
		msgId = id;
		View convertView = inflateViewForFileTransfer(inflater, msgViewHolder, container, init);
		messageViewHolder.arrowDir.setImageResource(R.drawable.filexferarrowin);

		messageViewHolder.titleLabel.setText(AndroidGUIActivator.getResources()
				.getI18NString("service.gui.FILE_TRANSFER_REQUEST_RECEIVED",
				new String[]{dateString, senderName}));

		String fileName = getFileLabel(fileTransferRequest.getFileName(),
				fileTransferRequest.getFileSize());
		messageViewHolder.fileLabel.setText(fileName);

		/* Must keep track of file transfer status as Android always request view redraw on
		listView scrolling, new message send or received */
		int status = mChatFragment.getChatListAdapter().getXferStatus(msgId);
		if (status == -1) {
			if (FileTransferConversation.FT_THUMBNAIL_ENABLE) {
				byte[] thumbnail = fileTransferRequest.getThumbnail();
				showThumbnail(thumbnail);
			}

			messageViewHolder.acceptButton.setVisibility(View.VISIBLE);
			messageViewHolder.acceptButton.setOnClickListener(new View.OnClickListener()
			{
				public void onClick(View v)
				{
					messageViewHolder.titleLabel.setText(AndroidGUIActivator.getResources()
							.getI18NString("service.gui.FILE_TRANSFER_PREPARING",
							new String[]{dateString, senderName}));
					messageViewHolder.acceptButton.setVisibility(View.GONE);
					messageViewHolder.rejectButton.setVisibility(View.GONE);

					// set the download for global display parameter
					downloadFile = createFile(fileTransferRequest);
					mChatFragment.getChatListAdapter().setFileName(msgId, downloadFile);
					(new acceptFile(downloadFile)).execute();
				}
			});

			messageViewHolder.rejectButton.setVisibility(View.VISIBLE);
			messageViewHolder.rejectButton.setOnClickListener(new View.OnClickListener()
			{
				@Override
				public void onClick(View v)
				{
					hideProgressRelatedComponents();
					messageViewHolder.titleLabel.setText(dateString + AndroidGUIActivator
							.getResources().getI18NString("service.gui.FILE_TRANSFER_REFUSED"));
					messageViewHolder.acceptButton.setVisibility(View.GONE);
					messageViewHolder.rejectButton.setVisibility(View.GONE);
					fileTransferRequest.rejectFile();
					// need to update status here as chatFragment statusListener is enabled for
					// fileTranfer and only after accept
					mChatFragment.getChatListAdapter().setXferStatus(msgId,
							FileTransferStatusChangeEvent.CANCELED);
				}
			});
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
				// hideProgressRelatedComponents();
				messageViewHolder.titleLabel.setText(AndroidGUIActivator.getResources()
						.getI18NString("service.gui.FILE_TRANSFER_PREPARING",
								new String[]{dateString, senderName}));
				break;

			case FileTransferStatusChangeEvent.IN_PROGRESS:
				if (!messageViewHolder.mProgressBar.isShown()) {
					messageViewHolder.mProgressBar.setVisibility(View.VISIBLE);
					messageViewHolder.mProgressBar.setMax((int) fileTransferRequest.getFileSize());
					// setFileTransfer(fileTransfer, fileTransferRequest.getFileSize());

					messageViewHolder.cancelButton.setVisibility(View.VISIBLE);
				}
				messageViewHolder.titleLabel.setText(dateString + AndroidGUIActivator
						.getResources().getI18NString("service.gui.FILE_RECEIVING_FROM",
								new String[]{senderName}));
				break;

			case FileTransferStatusChangeEvent.COMPLETED:
				if (downloadFile == null) { // Android view redraw happen
					downloadFile = mChatFragment.getChatListAdapter().getFileName(msgId);
				}
				String fileName = getFileLabel(downloadFile.getName(), downloadFile.length());
				messageViewHolder.fileLabel.setText(fileName);

				setCompletedDownloadFile(mChatFragment, downloadFile);
				messageViewHolder.titleLabel.setText(dateString + AndroidGUIActivator
						.getResources().getI18NString("service.gui.FILE_RECEIVE_COMPLETED",
								new String[]{senderName}));
				messageViewHolder.cancelButton.setVisibility(View.GONE);

				messageViewHolder.openFileButton.setVisibility(View.VISIBLE);
				messageViewHolder.openFolderButton.setVisibility(View.VISIBLE);
				break;

			case FileTransferStatusChangeEvent.FAILED:
				// hideProgressRelatedComponents(); keep the status info for user view
				messageViewHolder.titleLabel.setText(dateString + AndroidGUIActivator
						.getResources().getI18NString("service.gui.FILE_RECEIVE_FAILED",
								new String[]{senderName}));
				messageViewHolder.cancelButton.setVisibility(View.GONE);
				bgAlert = true;
				break;

			case FileTransferStatusChangeEvent.CANCELED:
				messageViewHolder.titleLabel.setText(dateString + AndroidGUIActivator
						.getResources().getI18NString("service.gui.FILE_TRANSFER_CANCELED"));
				messageViewHolder.cancelButton.setVisibility(View.GONE);
				bgAlert = true;
				break;

			case FileTransferStatusChangeEvent.REFUSED:
				// hideProgressRelatedComponents();
				messageViewHolder.titleLabel.setText(dateString
						+ AndroidGUIActivator.getResources().getI18NString(
						"service.gui.FILE_TRANSFER_REFUSED", new String[]{senderName}));
				messageViewHolder.cancelButton.setVisibility(View.GONE);
				messageViewHolder.openFileButton.setVisibility(View.GONE);
				messageViewHolder.openFolderButton.setVisibility(View.GONE);
				bgAlert = true;
				break;
		}
		if (bgAlert) {
			messageViewHolder.titleLabel.setTextColor(
					AndroidGUIActivator.getResources().getColor("red"));
		}
	}

	/**
	 * Handles status changes in file transfer.
	 */
	public void statusChanged(FileTransferStatusChangeEvent event)
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
					fileTransfer.removeStatusListener(ReceiveFileConversation.this);
					// removeProgressListener();
				}
			}
		});
	}

	/**
	 * Creates the local file to download.
	 *
	 * @return the local created file to download.
	 */
	private File createFile(IncomingFileTransferRequest fileTransferRequest)
	{
		File downloadFile = null;

		String incomingFileName = fileTransferRequest.getFileName();
		downloadDir
				= Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
		if (!downloadDir.exists()) {
			if (!downloadDir.mkdirs()) {
				logger.error("Could not create the download directory : "
						+ downloadDir.getAbsolutePath());
			}
			if (logger.isDebugEnabled())
				logger.debug("Download directory created : " + downloadDir.getAbsolutePath());
		}

		downloadFile = new File(downloadDir, incomingFileName);
		// If a file with the given name already exists, add an index to the file name.
		int index = 0;
		int filenameLength = incomingFileName.lastIndexOf(".");
		if (filenameLength == -1) {
			filenameLength = incomingFileName.length();
		}
		while (downloadFile.exists()) {
			String newFileName = incomingFileName.substring(0, filenameLength) + "-"
					+ ++index + incomingFileName.substring(filenameLength);
			downloadFile = new File(downloadDir, newFileName);
		}

		// Change the file name to the name we would use on the local file system.
		if (!downloadFile.getName().equals(incomingFileName)) {
			String fileName = getFileLabel(downloadFile.getName(),
					fileTransferRequest.getFileSize());
			messageViewHolder.fileLabel.setText(fileName);
		}
		return downloadFile;
	}

	/**
	 * Accepts the file in a new thread.
	 */
	private class acceptFile extends AsyncTask<Void, Void, String>
	{
		private final File dFile;
		private FileTransfer fileTransfer;

		private acceptFile(File mFile)
		{
			this.dFile = mFile;
		}

		@Override
		public void onPreExecute()
		{
		}

		@Override
		protected String doInBackground(Void... params)
		{
			fileTransfer = fileTransferRequest.acceptFile(dFile);
			mChatFragment.addActiveFileTransfer(fileTransfer.getID(), fileTransfer, msgId);

			// Remove previously added listener (not further required), that notify for request
			// cancellations if any.
			fileTransferOpSet.removeFileTransferListener(ReceiveFileConversation.this);

			fileTransfer.addStatusListener(ReceiveFileConversation.this);
			return "";
		}

		@Override
		protected void onPostExecute(String result)
		{
			if (fileTransfer != null) {
				setFileTransfer(fileTransfer, fileTransferRequest.getFileSize());
			}
		}
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
		return AndroidGUIActivator.getResources().getI18NString("service.gui.RECEIVED",
				new String[]{bytesString});
	}

	/**
	 * Called when a <tt>FileTransferCreatedEvent</tt> has been received.
	 *
	 * @param event
	 * 		the <tt>FileTransferCreatedEvent</tt> containing the newly received file transfer and
	 * 		other details.
	 */
	public void fileTransferCreated(FileTransferCreatedEvent event)
	{
	}

	/**
	 * Called when an <tt>IncomingFileTransferRequest</tt> has been canceled from the contact who
	 * sent it.
	 *
	 * @param event
	 * 		the <tt>FileTransferRequestEvent</tt> containing the request which was canceled.
	 */
	public void fileTransferRequestCanceled(FileTransferRequestEvent event)
	{
		final IncomingFileTransferRequest request = event.getRequest();
		// Different thread - Must execute in UiThread to Update UI information
		runOnUiThread(new Runnable()
		{
			@Override
			public void run()
			{
				if (request.equals(fileTransferRequest)) {
					messageViewHolder.acceptButton.setVisibility(View.GONE);
					messageViewHolder.rejectButton.setVisibility(View.GONE);
					fileTransferOpSet.removeFileTransferListener(ReceiveFileConversation.this);

					messageViewHolder.titleLabel.setText(dateString + AndroidGUIActivator
							.getResources().getI18NString("service.gui.FILE_TRANSFER_CANCELED"));
				}
			}
		});
	}

	/**
	 * Called when a new <tt>IncomingFileTransferRequest</tt> has been received.
	 * @see FileTransferActivator#fileTransferRequestReceived(FileTransferRequestEvent)
	 *
	 * @param event
	 * 		the <tt>FileTransferRequestEvent</tt> containing the newly received request and other
	 * 		details.
	 */
	public void fileTransferRequestReceived(FileTransferRequestEvent event)
	{
		// Event handled by FileTransferActivator - nothing to do here
	}

	/**
	 * Called when an <tt>IncomingFileTransferRequest</tt> has been rejected.
	 *
	 * @param event
	 * 		the <tt>FileTransferRequestEvent</tt> containing the received request which was
	 * 		rejected.
	 */
	public void fileTransferRequestRejected(FileTransferRequestEvent event)
	{
		final IncomingFileTransferRequest request = event.getRequest();
		// Different thread - Must execute in UiThread to Update UI information
		runOnUiThread(new Runnable()
		{
			@Override
			public void run()
			{
				if (request.equals(fileTransferRequest)) {
					messageViewHolder.acceptButton.setVisibility(View.GONE);
					messageViewHolder.rejectButton.setVisibility(View.GONE);
					fileTransferOpSet.removeFileTransferListener(ReceiveFileConversation.this);

					hideProgressRelatedComponents();
					// delete created downloadFile???
					messageViewHolder.titleLabel.setText(dateString + AndroidGUIActivator
							.getResources().getI18NString("service.gui.FILE_TRANSFER_REFUSED"));
				}
			}
		});
	}
}
