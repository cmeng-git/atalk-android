/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.filehistory;

import net.java.sip.communicator.service.protocol.ChatRoom;
import net.java.sip.communicator.service.protocol.Contact;

import java.io.File;
import java.util.Date;
import java.util.EventObject;

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

    /**
     * Status indicating that the file transfer has been completed.
     */
    public static final String COMPLETED = "completed";

    /**
     * Status indicating that the file transfer has been canceled.
     */
    public static final String CANCELED = "canceled";

    /**
     * Status indicating that the file transfer has failed.
     */
    public static final String FAILED = "failed";

    /**
     * Status indicating that the file transfer has been refused.
     */
    public static final String REFUSED = "refused";

    /**
     * Status indicating that the file transfer has been refused.
     */
    public static final String PREPARING = "preparing";
    /**
     * Status indicating that the file transfer has been refused.
     */
    public static final String IN_PROGRESS = "in_progress";
    /**
     * Status indicating that the file transfer was in active state.
     */
    public static final String ACTIVE = "active";

    /**
     * Special case where downloaded file cannot be found
     */
    public static final String NOT_FOUND = "no_found";

    private String direction = null;
    private Date date;
    private File file = null;
    private String mStatus;
    private Object mEntityJid;  // Contact or ChatRoom
    private String id = null;
    private int mEncType;
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
    public FileRecord(String id, Object entityJid, String direction, Date date, File file, int encType, String status)
    {
        super(file);
        this.id = id;
        this.mEntityJid = entityJid;
        this.direction = direction;
        this.date = date;
        this.file = file;
        this.mEncType = encType;
        this.mStatus = status;
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
     * The status of the transfer.
     *
     * @return the status
     */
    public String getStatus()
    {
        return mStatus;
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
