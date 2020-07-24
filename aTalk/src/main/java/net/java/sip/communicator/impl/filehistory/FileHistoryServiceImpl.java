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

import net.java.sip.communicator.impl.protocol.jabber.HttpFileUploadJabberImpl;
import net.java.sip.communicator.service.contactlist.MetaContact;
import net.java.sip.communicator.service.filehistory.FileHistoryService;
import net.java.sip.communicator.service.filehistory.FileRecord;
import net.java.sip.communicator.service.history.HistoryService;
import net.java.sip.communicator.service.msghistory.MessageHistoryService;
import net.java.sip.communicator.service.protocol.*;
import net.java.sip.communicator.service.protocol.event.*;
import net.java.sip.communicator.util.ServiceUtils;

import org.apache.commons.lang3.StringUtils;
import org.atalk.android.gui.chat.ChatMessage;
import org.atalk.android.gui.chat.ChatSession;
import org.atalk.android.plugin.timberlog.TimberLog;
import org.atalk.persistance.DatabaseBackend;
import org.jxmpp.util.XmppStringUtils;
import org.osgi.framework.*;

import java.io.IOException;
import java.util.*;

import timber.log.Timber;

/**
 * File History Service stores info for file transfers from various protocols.
 * It handles both the outgoing and incoming file transfer events.
 * FileTransferStatusListener = in/out file transfer status
 * ScFileTransferListener = Incoming file transfer request
 *
 * @author Eng Chong Meng
 */
public class FileHistoryServiceImpl implements FileHistoryService, ServiceListener,
        FileTransferStatusListener, ScFileTransferListener
{
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
     * @param bc BundleContext
     */
    public void start(BundleContext bc)
    {
        Timber.d("Starting the file history implementation.");
        this.bundleContext = bc;

        // start listening for newly register or removed protocol providers
        bc.addServiceListener(this);
        ServiceReference[] ppsRefs = null;
        try {
            ppsRefs = bc.getServiceReferences(ProtocolProviderService.class.getName(), null);
        } catch (InvalidSyntaxException e) {
            e.printStackTrace();
        }

        // in case we found any
        if ((ppsRefs != null) && (ppsRefs.length != 0)) {
            Timber.d("Found %s installed providers.", ppsRefs.length);
            for (ServiceReference<ProtocolProviderService> ppsRef : ppsRefs) {
                ProtocolProviderService pps = bc.getService(ppsRef);
                handleProviderAdded(pps);
            }
        }
        mDB = DatabaseBackend.getWritableDB();
    }

    /**
     * Stops the service.
     *
     * @param bc BundleContext
     */
    public void stop(BundleContext bc)
    {
        bc.removeServiceListener(this);
        ServiceReference[] ppsRefs = null;
        try {
            ppsRefs = bc.getServiceReferences(ProtocolProviderService.class.getName(), null);
        } catch (InvalidSyntaxException e) {
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
     * When new protocol provider is registered we check does it supports FileTransfer and if so add a listener to it
     *
     * @param serviceEvent ServiceEvent
     */
    public void serviceChanged(ServiceEvent serviceEvent)
    {
        Object sService = bundleContext.getService(serviceEvent.getServiceReference());
        Timber.log(TimberLog.FINER, "Received a service event for: %s", sService.getClass().getName());

        // we don't care if the source service is not a protocol provider
        if (!(sService instanceof ProtocolProviderService)) {
            return;
        }

        Timber.d("Service is a protocol provider.");
        if (serviceEvent.getType() == ServiceEvent.REGISTERED) {
            Timber.d("Handling registration of a new Protocol Provider.");

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
     * @param provider ProtocolProviderService
     */
    private void handleProviderAdded(ProtocolProviderService provider)
    {
        Timber.d("Adding protocol provider %s", provider.getProtocolName());

        // check whether the provider has a file transfer operation set
        OperationSetFileTransfer opSetFileTransfer = provider.getOperationSet(OperationSetFileTransfer.class);
        if (opSetFileTransfer != null) {
            opSetFileTransfer.addFileTransferListener(this);
        }
        else {
            Timber.log(TimberLog.FINER, "Service did not have a file transfer op. set.");
        }
    }

    /**
     * Removes the specified provider from the list of currently known providers
     *
     * @param provider the ProtocolProviderService that has been unregistered.
     */
    private void handleProviderRemoved(ProtocolProviderService provider)
    {
        OperationSetFileTransfer opSetFileTransfer = provider.getOperationSet(OperationSetFileTransfer.class);
        if (opSetFileTransfer != null) {
            opSetFileTransfer.removeFileTransferListener(this);
        }
    }

    /**
     * Set the history service.
     *
     * @param historyService HistoryService
     */
    public void setHistoryService(HistoryService historyService)
    {
        this.historyService = historyService;
    }

    private MessageHistoryService getMHS()
    {
        if (mhs == null)
            mhs = ServiceUtils.getService(bundleContext, MessageHistoryService.class);
        return mhs;
    }

    /**
     * Receive fileTransfer requests.
     *
     * @param event FileTransferRequestEvent
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
     * @param event fileTransfer
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
        } catch (IOException e) {
            Timber.e(e, "Could not add file transfer log to history");
        }
    }

    /**
     * Create new fileTransfer record in dataBase when file transfer has started
     * Also use as conversion for http file upload link message to file transfer message
     *
     * @param evt FileTransferRequestEvent or FileTransferCreatedEvent
     * @param fileName Name of the file to received or send
     */
    private void insertRecordToDB(EventObject evt, String fileName)
    {
        long timeStamp = 0L;
        String uuid = null;
        String mJid, mEntityJid;
        String direction = FileRecord.OUT;
        Object entityJid = null;
        int msgType = ChatMessage.MESSAGE_FILE_TRANSFER_SEND;
        contentValues.clear();

        if (evt instanceof FileTransferRequestEvent) {
            FileTransferRequestEvent event = (FileTransferRequestEvent) evt;
            IncomingFileTransferRequest req = event.getRequest();
            uuid = req.getID();
            entityJid = req.getSender();
            timeStamp = event.getTimestamp().getTime();
            direction = FileRecord.IN;
            msgType = ChatMessage.MESSAGE_FILE_TRANSFER_RECEIVE;
            contentValues.put(ChatMessage.MSG_BODY, fileName);
        }
        else if (evt instanceof HttpFileTransferEvent) {
            HttpFileTransferEvent event = (HttpFileTransferEvent) evt;
            FileTransfer fileTransfer = event.getFileTransfer();
            uuid = fileTransfer.getID();
            entityJid = ((HttpFileUploadJabberImpl) fileTransfer).getEntityJid();
            timeStamp = event.getTimestamp().getTime();
        }
        else if (evt instanceof FileTransferCreatedEvent) {
            FileTransferCreatedEvent event = (FileTransferCreatedEvent) evt;
            FileTransfer fileTransfer = event.getFileTransfer();
            uuid = fileTransfer.getID();
            entityJid = fileTransfer.getContact();
            timeStamp = event.getTimestamp().getTime();
        }

        String sessionUuid;
        if (entityJid instanceof Contact) {
            sessionUuid = getMHS().getSessionUuidByJid((Contact) entityJid);
            mEntityJid = ((Contact) entityJid).getAddress();
            mJid = null;
        }
        else {
            ChatRoom chatroom = (ChatRoom) entityJid;
            sessionUuid = getMHS().getSessionUuidByJid(chatroom);
            mJid = chatroom.getParentProvider().getAccountID().getAccountJid();
            mEntityJid = XmppStringUtils.parseLocalpart(mJid);
        }

        contentValues.put(ChatMessage.UUID, uuid);
        contentValues.put(ChatMessage.SESSION_UUID, sessionUuid);
        contentValues.put(ChatMessage.TIME_STAMP, timeStamp);
        contentValues.put(ChatMessage.ENTITY_JID, mEntityJid);
        contentValues.put(ChatMessage.JID, mJid);
        contentValues.put(ChatMessage.ENC_TYPE, IMessage.ENCODE_PLAIN);
        contentValues.put(ChatMessage.MSG_TYPE, msgType);
        contentValues.put(ChatMessage.DIRECTION, direction);
        contentValues.put(ChatMessage.STATUS, FileRecord.STATUS_ACTIVE);
        contentValues.put(ChatMessage.FILE_PATH, fileName);
        mDB.insert(ChatMessage.TABLE_NAME, null, contentValues);
    }

    /**
     * Listens for changes in file transfers and update the DB record status if known.
     * Translate FileTransfer status to FileRecord status before saving
     *
     * @param event FileTransferStatusChangeEvent
     */
    public void statusChanged(FileTransferStatusChangeEvent event)
    {
        FileTransfer ft = event.getFileTransfer();
        int status = getStatus(ft.getStatus());

        // ignore events if status is unknown
        if (status != FileRecord.STATUS_UNKNOWN)
            updateFTStatusToDB(ft.getID(), status, null, IMessage.ENCRYPTION_NONE);
    }

    /**
     * Called when a new <tt>IncomingFileTransferRequest</tt> has been rejected.
     *
     * @param event the <tt>FileTransferRequestEvent</tt> containing the received request which was rejected.
     */
    public void fileTransferRequestRejected(FileTransferRequestEvent event)
    {
        IncomingFileTransferRequest req = event.getRequest();
        updateFTStatusToDB(req.getID(), FileRecord.STATUS_REFUSED, null, IMessage.ENCRYPTION_NONE);
    }

    /**
     * Called when a new <tt>IncomingFileTransferRequest</tt> has been cancel by the sender.
     *
     * @param event the <tt>FileTransferRequestEvent</tt> containing the received request which was rejected.
     */
    public void fileTransferRequestCanceled(FileTransferRequestEvent event)
    {
        IncomingFileTransferRequest req = event.getRequest();
        updateFTStatusToDB(req.getID(), FileRecord.STATUS_CANCELED, null, IMessage.ENCRYPTION_NONE);
    }

    /**
     * Update new status and fileName and convert to FileTransfer History Record in dataBase
     *
     * @param msgUuid message UUID
     * @param status New status for update
     * @param fileName local fileName path for http downloaded file; null => no change and keep the link in MSG_BODY
     * @param encType IMessage.ENCRYPTION_NONE, ENCRYPTION_OMEMO, ENCRYPTION_OTR
     */
    public void updateFTStatusToDB(String msgUuid, int status, String fileName, int encType)
    {
        updateFTStatusToDB(msgUuid, status, fileName, encType, ChatMessage.MESSAGE_FILE_TRANSFER_HISTORY);
    }

    /**
     * Update new status and fileName to the fileTransfer record in dataBase
     * Keep file uri; for retry if not converted to MESSAGE_FILE_TRANSFER_HISTORY
     *
     * @param msgUuid message UUID
     * @param status New status for update
     * @param fileName local fileName path for http downloaded file; null => no change and keep the link in MSG_BODY
     * @param encType IMessage.ENCRYPTION_NONE, ENCRYPTION_OMEMO, ENCRYPTION_OTR
     * @param recordType File Transfer record type
     */
    public void updateFTStatusToDB(String msgUuid, int status, String fileName, int encType, int recordType)
    {
        // Timber.w(new Exception("### File in/out transfer status changes to: " + status));
        String[] args = {msgUuid};

        contentValues.clear();
        contentValues.put(ChatMessage.STATUS, status);
        if (StringUtils.isNotEmpty(fileName)) {
            contentValues.put(ChatMessage.FILE_PATH, fileName);
        }
        contentValues.put(ChatMessage.ENC_TYPE, encType);
        contentValues.put(ChatMessage.MSG_TYPE, recordType);
        mDB.update(ChatMessage.TABLE_NAME, contentValues, ChatMessage.UUID + "=?", args);
    }

    /**
     * Maps only valid FileTransferStatusChangeEvent status, otherwise returns STATUS_UNKNOWN.
     *
     * @param status the status as receive from FileTransfer
     * @return the corresponding status of FileRecord.
     */
    private static int getStatus(int status)
    {
        switch (status) {
            case FileTransferStatusChangeEvent.COMPLETED:
                return FileRecord.STATUS_COMPLETED;
            case FileTransferStatusChangeEvent.REFUSED:
                return FileRecord.STATUS_REFUSED;
            case FileTransferStatusChangeEvent.CANCELED:
                return FileRecord.STATUS_CANCELED;
            case FileTransferStatusChangeEvent.FAILED:
                return FileRecord.STATUS_FAILED;
            case FileTransferStatusChangeEvent.PREPARING:
                return FileRecord.STATUS_PREPARING;
            case FileTransferStatusChangeEvent.IN_PROGRESS:
                return FileRecord.STATUS_IN_PROGRESS;
            default:
                return FileRecord.STATUS_UNKNOWN;
        }
    }

    /**
     * Permanently removes locally stored chatRoom message messages (need cleanup - not used)
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
