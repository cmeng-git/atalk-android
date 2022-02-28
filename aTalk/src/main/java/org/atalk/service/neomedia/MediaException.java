/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.service.neomedia;

/**
 * Implements an <code>Exception</code> thrown by the neomedia service interfaces and their
 * implementations. <code>MediaException</code> carries an error code in addition to the standard
 * <code>Exception</code> properties which gives more information about the specifics of the particular
 * <code>MediaException</code>.
 *
 * @author Lubomir Marinov
 */
public class MediaException extends Exception
{
	/**
	 * Serial version UID.
	 */
	private static final long serialVersionUID = 0L;

	/**
	 * The error code value which specifies that the <code>MediaException</code> carrying it does not
	 * give more information about its specifics.
	 */
	public static final int GENERAL_ERROR = 1;

	/**
	 * The error code carried by this <code>MediaException</code> which gives more information about the
	 * specifics of this <code>MediaException</code>.
	 */
	private final int errorCode;

	/**
	 * Initializes a new <code>MediaException</code> instance with a specific detailed message and
	 * {@link #GENERAL_ERROR} error code.
	 *
	 * @param message
	 *        the detailed message to initialize the new instance with
	 */
	public MediaException(String message)
	{
		this(message, GENERAL_ERROR);
	}

	/**
	 * Initializes a new <code>MediaException</code> instance with a specific detailed message and a
	 * specific error code.
	 *
	 * @param message
	 *        the detailed message to initialize the new instance with
	 * @param errorCode
	 *        the error code which is to give more information about the specifics of the new
	 *        instance
	 */
	public MediaException(String message, int errorCode)
	{
		super(message);

		this.errorCode = errorCode;
	}

	/**
	 * Initializes a new <code>MediaException</code> instance with a specific detailed message,
	 * {@link #GENERAL_ERROR} error code and a specific <code>Throwable</code> cause.
	 *
	 * @param message
	 *        the detailed message to initialize the new instance with
	 * @param cause
	 *        the <code>Throwable</code> which is to be carried by the new instance and which is to be
	 *        reported as the cause for throwing the new instance. If <code>cause</code> is
	 *        <code>null</code>, the cause for throwing the new instance is considered to be unknown.
	 */
	public MediaException(String message, Throwable cause)
	{
		this(message, GENERAL_ERROR, cause);
	}

	/**
	 * Initializes a new <code>MediaException</code> instance with a specific detailed message, a
	 * specific error code and a specific <code>Throwable</code> cause.
	 *
	 * @param message
	 *        the detailed message to initialize the new instance with
	 * @param errorCode
	 *        the error code which is to give more information about the specifics of the new
	 *        instance
	 * @param cause
	 *        the <code>Throwable</code> which is to be carried by the new instance and which is to be
	 *        reported as the cause for throwing the new instance. If <code>cause</code> is
	 *        <code>null</code>, the cause for throwing the new instance is considered to be unknown.
	 */
	public MediaException(String message, int errorCode, Throwable cause)
	{
		super(message, cause);

		this.errorCode = errorCode;
	}

	/**
	 * Gets the error code carried by this <code>MediaException</code> which gives more information
	 * about the specifics of this <code>MediaException</code>.
	 *
	 * @return the error code carried by this <code>MediaException</code> which gives more information
	 *         about the specifics of this <code>MediaException</code>
	 */
	public int getErrorCode()
	{
		return errorCode;
	}
}
