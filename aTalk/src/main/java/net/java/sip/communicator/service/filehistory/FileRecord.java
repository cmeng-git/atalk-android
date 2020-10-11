/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.filehistory;

import net.java.sip.communicator.service.protocol.ChatRoom;
import net.java.sip.communicator.service.protocol.Contact;

import org.atalk.android.gui.chat.ChatMessage;

import java.io.File;
import java.util.*;

/**
 * Structure used for encapsulating data when writing or reading File History Data.
 *
 * @author Damian Minkov
 * @author Eng Chong Meng
 */
public class FileRecord extends EventObject
{
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
      ===============================================================*/
    /**
     * Status indicating that the file transfer has been completed.
     */
    public static final int STATUS_COMPLETED = 10;
    /**
     * Status indicating that the file transfer has failed.
     */
    public static final int STATUS_FAILED = 11;
    /**
     * Status indicating that the file transfer has been canceled.
     */
    public static final int STATUS_CANCELED = 12;
    /**
     * Status indicating that the file transfer has been refused.
     */
    public static final int STATUS_REFUSED = 13;
    /**
     * Status indicating that the file transfer was in active state.
     */
    public static final int STATUS_ACTIVE = 14;
    /**
     * Status indicating that the file transfer is preparing state.
     */
    public static final int STATUS_PREPARING = 15;
    /**
     * Status indicating that the file transfer is in-progress state.
     */
    public static final int STATUS_IN_PROGRESS = 16;
    /**
     * Status indicating that the file transfer state is unknown.
     */
    public static final int STATUS_UNKNOWN = -1;

    // Special case where downloaded file cannot be found
    public static final int FILE_NOT_FOUND = -1;

    private String direction = null;
    private Date date;
    private File file = null;
    private int mXferStatus;  // File transfer status
    private Object mEntityJid;  // Contact or ChatRoom
    private String id = null;
    private int mEncType;

    /**
     * A map between File transfer status to status descriptive text
     */
    public static final HashMap<Integer, String> statusMap = new HashMap<Integer, String>()
    {{
        put(STATUS_COMPLETED, "completed");
        put(STATUS_FAILED, "failed");
        put(STATUS_CANCELED, "canceled");
        put(STATUS_REFUSED, "refused");
        put(STATUS_ACTIVE, "active");
        put(STATUS_PREPARING, "preparing");
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
     * @param status Status of the fileTransfer
     */
    public FileRecord(String id, Object entityJid, String direction, Date date, File file, int encType, int status)
    {
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
    public String getDirection()
    {
        return direction;
    }

    /**
     * The date of the record.
     *
     * @return the date
     */
    public Date getDate()
    {
        return date;
    }

    /**
     * The file that was transferred.
     *
     * @return the file
     */
    public File getFile()
    {
        return file;
    }

    /**
     * The file encrypted type.
     *
     * @return the encType
     */
    public int getEncType()
    {
        return mEncType;
    }

    /**
     * The status of the file transfer.
     *
     * @return the status
     */
    public int getStatus()
    {
        return mXferStatus;
    }

    /**
     * The contact.
     *
     * @return the contact
     */
    public String getJidAddress()
    {
        if (mEntityJid instanceof Contact)
            return ((Contact) mEntityJid).getAddress();
        else
            return ((ChatRoom) mEntityJid).getName();
    }

    public Object getEntityJid()
    {
        return mEntityJid;
    }

    /**
     * The id.
     *
     * @return id.
     */
    public String getID()
    {
        return id;
    }
}
