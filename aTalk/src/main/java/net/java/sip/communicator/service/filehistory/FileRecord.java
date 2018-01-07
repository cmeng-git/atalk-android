/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.filehistory;

import net.java.sip.communicator.service.protocol.Contact;

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
	 * Status indicating that the file transfer was in active state.
	 */
	public static final String ACTIVE = "active";

	private String direction = null;
	private Date date;
	private File file = null;
	private String status;
	private Contact contact;
	private String id = null;

	/**
	 * Constructs new FileRecord
	 *
	 * @param id
	 * 		File record Uuid
	 * @param contact
	 * 		The entityJid
	 * @param direction
	 * 		File received or send
	 * @param date
	 * 		the timeStamp
	 * @param file
	 * 		the file name
	 * @param status
	 * 		Status of the fileTransfer
	 */
	public FileRecord(String id, Contact contact, String direction, Date date, File file,
			String status)
	{
		super(file);
		this.id = id;
		this.contact = contact;
		this.direction = direction;
		this.date = date;
		this.file = file;
		this.status = status;
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
	 * The status of the transfer.
	 *
	 * @return the status
	 */
	public String getStatus()
	{
		return status;
	}

	/**
	 * The contact.
	 *
	 * @return the contact
	 */
	public Contact getContact()
	{
		return contact;
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
