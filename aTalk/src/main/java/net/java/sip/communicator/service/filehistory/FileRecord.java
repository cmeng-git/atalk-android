/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.filehistory;

import java.io.File;
import java.util.Date;
import java.util.EventObject;
import java.util.HashMap;

import net.java.sip.communicator.service.protocol.ChatRoom;
import net.java.sip.communicator.service.protocol.Contact;
import net.java.sip.communicator.service.protocol.event.FileTransferStatusChangeEvent;

/**
 * Structure used for encapsulating data when writing or reading File History Data.
 *
 * @author Damian Minkov
 * @author Eng Chong Meng
 */
public class FileRecord extends EventObject {
    /**
     * Direction of the transfer: out
     */
    public final static String OUT = "out";
    /**
     * Direction of the transfer: in
     */
    public final static String IN = "in";

    /* ===============================================================
     File transfer status - save in message status for file transfer
     Definitions should be in sync with FileTransferStatusChangeEvent
      ===============================================================*/
    /**
     * Status indicating that the file transfer has been completed.
     */
    public static final int STATUS_COMPLETED = FileTransferStatusChangeEvent.COMPLETED;
    /**
     * Status indicating that the file transfer has failed.
     */
    public static final int STATUS_FAILED = FileTransferStatusChangeEvent.FAILED;
    /**
     * Status indicating that the file transfer has been canceled.
     */
    public static final int STATUS_CANCELED = FileTransferStatusChangeEvent.CANCELED;
    /**
     * Status indicating that the file transfer has been refused.
     */
    public static final int STATUS_DECLINED = FileTransferStatusChangeEvent.DECLINED;
    /**
     * Status indicating that the file transfer is preparing state.
     */
    public static final int STATUS_PREPARING = FileTransferStatusChangeEvent.PREPARING;
    /**
     * Status indicating that the file transfer was in active state.
     */
    public static final int STATUS_WAITING = FileTransferStatusChangeEvent.WAITING;
    /**
     * Indicates that the recipient has accepted the file transfer.
     */
    public static final int STATUS_ACCEPT = FileTransferStatusChangeEvent.ACCEPT;
    /**
     * Status indicating that the file transfer is in-progress state.
     */
    public static final int STATUS_IN_PROGRESS = FileTransferStatusChangeEvent.IN_PROGRESS;
    /**
     * Status indicating that the file transfer state is unknown.
     */
    public static final int STATUS_UNKNOWN = FileTransferStatusChangeEvent.UNKNOWN;

    // Special case where downloaded file cannot be found
    public static final int FILE_NOT_FOUND = -1;

    private final Object mEntityJid;  // Contact or ChatRoom
    private final int mXferStatus;  // File transfer status
    private final File file;
    private final Date date;
    private final String direction;
    private final String id;
    private final int mEncType;

    /**
     * A map between File transfer status to status descriptive text
     */
    public static final HashMap<Integer, String> statusMap = new HashMap<Integer, String>() {{
        put(STATUS_COMPLETED, "completed");
        put(STATUS_FAILED, "failed");
        put(STATUS_CANCELED, "canceled");
        put(STATUS_DECLINED, "declined");
        put(STATUS_PREPARING, "preparing");
        put(STATUS_WAITING, "waiting");
        put(STATUS_ACCEPT, "accepted");
        put(STATUS_IN_PROGRESS, "in_progress");
    }};

    /**
     * Constructs new FileRecord
     *
     * @param id File record Uuid
     * @param entityJid The entityJid
     * @param direction File received or send
     * @param date the timeStamp
     * @param file the file name
     * @param encType the file encryption (plain or OMEMO)
     * @param status Status of the fileTransfer
     */
    public FileRecord(String id, Object entityJid, String direction, Date date, File file, int encType, int status) {
        super(file);
        this.id = id;
        this.mEntityJid = entityJid;
        this.direction = direction;
        this.date = date;
        this.file = file;
        this.mEncType = encType;
        this.mXferStatus = status;
    }

    /**
     * The direction of the transfer.
     *
     * @return the direction
     */
    public String getDirection() {
        return direction;
    }

    /**
     * The date of the record.
     *
     * @return the date
     */
    public Date getDate() {
        return date;
    }

    /**
     * The file that was transferred.
     *
     * @return the file
     */
    public File getFile() {
        return file;
    }

    /**
     * The file encrypted type.
     *
     * @return the encType
     */
    public int getEncType() {
        return mEncType;
    }

    /**
     * The status of the file transfer.
     *
     * @return the status
     */
    public int getStatus() {
        return mXferStatus;
    }

    /**
     * The contact.
     *
     * @return the contact
     */
    public String getJidAddress() {
        if (mEntityJid instanceof Contact)
            return ((Contact) mEntityJid).getAddress();
        else
            return ((ChatRoom) mEntityJid).getName();
    }

    public Object getEntityJid() {
        return mEntityJid;
    }

    /**
     * The id.
     *
     * @return id.
     */
    public String getID() {
        return id;
    }
}
