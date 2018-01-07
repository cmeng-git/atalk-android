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
package net.java.sip.communicator.impl.filehistory;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import net.java.sip.communicator.service.contactlist.MetaContact;
import net.java.sip.communicator.service.filehistory.*;
import net.java.sip.communicator.service.history.HistoryService;
import net.java.sip.communicator.service.msghistory.MessageHistoryService;
import net.java.sip.communicator.service.protocol.*;
import net.java.sip.communicator.service.protocol.event.*;
import net.java.sip.communicator.util.*;

import org.atalk.android.gui.chat.*;
import org.atalk.persistance.DatabaseBackend;
import org.osgi.framework.*;

import java.io.IOException;
import java.util.*;

/**
 * File History Service stores info for file transfers from various protocols. Uses History Service.
 *
 * @author Eng Chong Meng
 */
public class FileHistoryServiceImpl implements FileHistoryService, ServiceListener,
		FileTransferStatusListener, FileTransferListener
{
	/**
	 * The logger for this class.
	 */
	private static final Logger logger = Logger.getLogger(FileHistoryServiceImpl.class);

	/**
	 * The BundleContext that we got from the OSGI bus.
	 */
	private BundleContext bundleContext = null;

	/**
	 * The <tt>HistoryService</tt> reference.
	 */
	private HistoryService historyService = null;

	private ContentValues contentValues = new ContentValues();
	private SQLiteDatabase mDB;
	private MessageHistoryService mhs;

	/**
	 * Starts the service. Check the current registered protocol providers which supports
	 * FileTransfer and adds a listener to them.
	 *
	 * @param bc
	 * 		BundleContext
	 */
	public void start(BundleContext bc)
	{
		if (logger.isDebugEnabled())
			logger.debug("Starting the file history implementation.");
		this.bundleContext = bc;

		// start listening for newly register or removed protocol providers
		bc.addServiceListener(this);
		ServiceReference[] ppsRefs = null;
		try {
			ppsRefs = bc.getServiceReferences(ProtocolProviderService.class.getName(), null);
		}
		catch (InvalidSyntaxException e) {
			e.printStackTrace();
		}

		// in case we found any
		if ((ppsRefs != null) && (ppsRefs.length != 0)) {
			if (logger.isDebugEnabled()) {
				logger.debug("Found " + ppsRefs.length + " installed providers.");
			}
			for (ServiceReference<ProtocolProviderService> ppsRef : ppsRefs) {
				ProtocolProviderService pps = bc.getService(ppsRef);
				handleProviderAdded(pps);
			}
		}
		mDB = DatabaseBackend.getWritableDB();
	}

	private MessageHistoryService getMHS()
	{
		if (mhs == null)
			mhs = ServiceUtils.getService(bundleContext, MessageHistoryService.class);
		return mhs;
	}

	/**
	 * Stops the service.
	 *
	 * @param bc
	 * 		BundleContext
	 */
	public void stop(BundleContext bc)
	{
		bc.removeServiceListener(this);
		ServiceReference[] ppsRefs = null;
		try {
			ppsRefs = bc.getServiceReferences(ProtocolProviderService.class.getName(), null);
		}
		catch (InvalidSyntaxException e) {
			e.printStackTrace();
		}

		// in case we found any
		if ((ppsRefs != null) && (ppsRefs.length != 0)) {
			for (ServiceReference<ProtocolProviderService> ppsRef : ppsRefs) {
				ProtocolProviderService pps = bc.getService(ppsRef);
				handleProviderRemoved(pps);
			}
		}
	}

	/**
	 * When new protocol provider is registered we check does it supports FileTransfer and if so
	 * add a listener to it
	 *
	 * @param serviceEvent
	 * 		ServiceEvent
	 */
	public void serviceChanged(ServiceEvent serviceEvent)
	{
		Object sService = bundleContext.getService(serviceEvent.getServiceReference());

		if (logger.isTraceEnabled())
			logger.trace("Received a service event for: " + sService.getClass().getName());

		// we don't care if the source service is not a protocol provider
		if (!(sService instanceof ProtocolProviderService)) {
			return;
		}

		if (logger.isDebugEnabled())
			logger.debug("Service is a protocol provider.");
		if (serviceEvent.getType() == ServiceEvent.REGISTERED) {
			if (logger.isDebugEnabled())
				logger.debug("Handling registration of a new Protocol Provider.");

			this.handleProviderAdded((ProtocolProviderService) sService);
		}
		else if (serviceEvent.getType() == ServiceEvent.UNREGISTERING) {
			this.handleProviderRemoved((ProtocolProviderService) sService);
		}
	}

	/**
	 * Used to attach the File History Service to existing or just registered protocol provider.
	 * Checks if the provider has implementation of OperationSetFileTransfer
	 *
	 * @param provider
	 * 		ProtocolProviderService
	 */
	private void handleProviderAdded(ProtocolProviderService provider)
	{
		if (logger.isDebugEnabled())
			logger.debug("Adding protocol provider " + provider.getProtocolName());

		// check whether the provider has a file transfer operation set
		OperationSetFileTransfer opSetFileTransfer
				= provider.getOperationSet(OperationSetFileTransfer.class);

		if (opSetFileTransfer != null) {
			opSetFileTransfer.addFileTransferListener(this);
		}
		else {
			if (logger.isTraceEnabled())
				logger.trace("Service did not have a file transfer op. set.");
		}
	}

	/**
	 * Removes the specified provider from the list of currently known providers
	 *
	 * @param provider
	 * 		the ProtocolProviderService that has been unregistered.
	 */
	private void handleProviderRemoved(ProtocolProviderService provider)
	{
		OperationSetFileTransfer opSetFileTransfer
				= provider.getOperationSet(OperationSetFileTransfer.class);

		if (opSetFileTransfer != null) {
			opSetFileTransfer.removeFileTransferListener(this);
		}
	}

	/**
	 * Set the history service.
	 *
	 * @param historyService
	 * 		HistoryService
	 */
	public void setHistoryService(HistoryService historyService)
	{
		this.historyService = historyService;
	}

	/**
	 * We ignore fileTransfer requests.
	 *
	 * @param event
	 * 		FileTransferRequestEvent
	 */
	public void fileTransferRequestReceived(FileTransferRequestEvent event)
	{
		IncomingFileTransferRequest req = event.getRequest();
		String fileName = req.getFileName();

		insertRecordToDB(event, fileName);
	}

	/**
	 * New file transfer was created.
	 *
	 * @param event
	 * 		fileTransfer
	 */
	public void fileTransferCreated(FileTransferCreatedEvent event)
	{
		FileTransfer fileTransfer = event.getFileTransfer();
		fileTransfer.addStatusListener(this);

		try {
			String fileName = fileTransfer.getLocalFile().getCanonicalPath();

			if (fileTransfer.getDirection() == FileTransfer.IN) {
				String[] args = {fileTransfer.getID()};
				contentValues.clear();
				contentValues.put(ChatMessage.FILE_PATH, fileName);

				mDB.update(ChatMessage.TABLE_NAME, contentValues, ChatMessage.UUID + "=?", args);
			}
			else if (fileTransfer.getDirection() == FileTransfer.OUT) {
				insertRecordToDB(event, fileName);
			}
		}
		catch (IOException e) {
			logger.error("Could not add file transfer log to history", e);
		}
	}

	/**
	 * Create new fileTransfer record in dataBase when file transfer is ACTIVE
	 *
	 * @param evt
	 * 		FileTransferRequestEvent or FileTransferCreatedEvent
	 * @param fileName
	 * 		Name of the file to received or send
	 */
	private void insertRecordToDB(EventObject evt, String fileName)
	{
		Contact entityJid = null;
		long timeStamp = 0L;
		String uuid = null;
		String direction = FileRecord.IN;
		int msgTye = ChatMessage.MESSAGE_FILE_TRANSFER_RECEIVE;

		if (evt instanceof FileTransferRequestEvent) {
			FileTransferRequestEvent event = (FileTransferRequestEvent) evt;
			IncomingFileTransferRequest req = event.getRequest();
			uuid = req.getID();
			entityJid = req.getSender();
			timeStamp = event.getTimestamp().getTime();
		}
		else if (evt instanceof FileTransferCreatedEvent) {
			FileTransferCreatedEvent event = (FileTransferCreatedEvent) evt;
			FileTransfer fileTransfer = event.getFileTransfer();
			uuid = fileTransfer.getID();
			entityJid = fileTransfer.getContact();
			timeStamp = event.getTimestamp().getTime();

			direction = FileRecord.OUT;
			msgTye = ChatMessage.MESSAGE_FILE_TRANSFER_SEND;
		}
		String sessionUuid = getMHS().getSessionUuidByJid(entityJid);

		contentValues.clear();
		contentValues.put(ChatMessage.UUID, uuid);
		contentValues.put(ChatMessage.SESSION_UUID, sessionUuid);
		contentValues.put(ChatMessage.TIME_STAMP, timeStamp);
		if (entityJid != null)
			contentValues.put(ChatMessage.ENTITY_JID, entityJid.getAddress());
		contentValues.put(ChatMessage.MSG_BODY, FileRecord.ACTIVE);
		contentValues.put(ChatMessage.ENC_TYPE, ChatMessage.ENCODE_PLAIN);
		contentValues.put(ChatMessage.MSG_TYPE, msgTye);
		contentValues.put(ChatMessage.DIRECTION, direction);
		contentValues.put(ChatMessage.STATUS, ChatMessage.STATUS_ACTIVE);
		contentValues.put(ChatMessage.FILE_PATH, fileName);
		mDB.insert(ChatMessage.TABLE_NAME, null, contentValues);
	}

	/**
	 * Listens for changes in file transfers.
	 *
	 * @param event
	 * 		FileTransferStatusChangeEvent
	 */
	public void statusChanged(FileTransferStatusChangeEvent event)
	{
		FileTransfer ft = event.getFileTransfer();
		String status = getStatus(ft.getStatus());

		// ignore events if status is null
		if (status != null)
			updateFTStatusToDB(ft.getID(), status);
	}

	/**
	 * Called when a new <tt>IncomingFileTransferRequest</tt> has been rejected.
	 *
	 * @param event
	 * 		the <tt>FileTransferRequestEvent</tt> containing the received request which was
	 * 		rejected.
	 */
	public void fileTransferRequestRejected(FileTransferRequestEvent event)
	{
		IncomingFileTransferRequest req = event.getRequest();
		updateFTStatusToDB(req.getID(), FileRecord.REFUSED);
	}

	public void fileTransferRequestCanceled(FileTransferRequestEvent event)
	{
		IncomingFileTransferRequest req = event.getRequest();
		updateFTStatusToDB(req.getID(), FileRecord.CANCELED);
	}

	/**
	 * Update new status to the fileTransfer record in dataBase
	 *
	 * @param msgUuid
	 * 		File record UUID
	 * @param status
	 * 		New status for update
	 */
	private void updateFTStatusToDB(String msgUuid, String status)
	{
		String[] args = {msgUuid};

		contentValues.clear();
		contentValues.put(ChatMessage.MSG_BODY, status);
		contentValues.put(ChatMessage.STATUS, ChatMessageImpl.statusMap.get(status));
		contentValues.put(ChatMessage.MSG_TYPE, ChatMessage.MESSAGE_FILE_TRANSFER_HISTORY);

		mDB.update(ChatMessage.TABLE_NAME, contentValues, ChatMessage.UUID + "=?", args);
	}

	/**
	 * Maps only the statuses we are interested in, otherwise returns null.
	 *
	 * @param status
	 * 		the status as receive from FileTransfer
	 * @return the corresponding status of FileRecord.
	 */
	private static String getStatus(int status)
	{
		switch (status) {
			case FileTransferStatusChangeEvent.CANCELED:
				return FileRecord.CANCELED;
			case FileTransferStatusChangeEvent.COMPLETED:
				return FileRecord.COMPLETED;
			case FileTransferStatusChangeEvent.FAILED:
				return FileRecord.FAILED;
			case FileTransferStatusChangeEvent.REFUSED:
				return FileRecord.REFUSED;
			default:
				return null;
		}
	}

	/**
	 * Permanently removes locally stored message history for the file transfer.
	 */
	public void eraseLocallyStoredHistory()
	{
		String[] args = {String.valueOf(ChatSession.MODE_MULTI)};
		String[] columns = {ChatSession.SESSION_UUID};

		Cursor cursor = mDB.query(ChatSession.TABLE_NAME, columns,
				ChatSession.MODE + "=?", args, null, null, null);
		while (cursor.moveToNext()) {
			purgeLocallyStoredHistory(null, cursor.getString(0));
		}
		cursor.close();
		mDB.delete(ChatMessage.TABLE_NAME, null, null);

	}

	/**
	 * Permanently removes locally stored message history for the sessionUuid.
	 * - Remove only chatMessages for metaContacts
	 * - Remove both chatSessions and chatMessages for muc
	 */
	public void eraseLocallyStoredHistory(MetaContact metaContact)
	{
		getMHS();
		Iterator<Contact> contacts = metaContact.getContacts();
		while (contacts.hasNext()) {
			Contact contact = contacts.next();
			String sessionUuid = mhs.getSessionUuidByJid(contact);

			purgeLocallyStoredHistory(contact, sessionUuid);
		}
	}

	/**
	 * Permanently removes locally stored message history for the sessionUuid.
	 * - Remove only chatMessages for metaContacts
	 * - Remove both chatSessions and chatMessages for muc
	 */
	private void purgeLocallyStoredHistory(Contact contact, String sessionUuid)
	{
		String[] args = {sessionUuid};
		if (contact != null) {
			mDB.delete(ChatMessage.TABLE_NAME, ChatMessage.SESSION_UUID + "=?", args);
		}
		else {
			mDB.delete(ChatSession.TABLE_NAME, ChatSession.SESSION_UUID + "=?", args);
		}
	}

	/**
	 * Used to compare FileRecords and to be ordered in TreeSet according their timestamp
	 */
	private static class FileRecordComparator implements Comparator<FileRecord>
	{
		public int compare(FileRecord o1, FileRecord o2)
		{
			Date date1 = o1.getDate();
			Date date2 = o2.getDate();
			return date1.compareTo(date2);
		}
	}
}
