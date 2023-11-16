/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.jmfext.media.protocol;

import org.atalk.impl.neomedia.control.AbstractControls;
import org.atalk.impl.neomedia.control.ControlsAdapter;

import java.io.IOException;

import javax.media.Format;
import javax.media.control.FormatControl;
import javax.media.protocol.ContentDescriptor;
import javax.media.protocol.DataSource;
import javax.media.protocol.SourceStream;

import timber.log.Timber;

/**
 * Provides a base implementation of <code>SourceStream</code> in order to facilitate implementers by
 * taking care of boilerplate in the most common cases.
 *
 * @author Lyubomir Marinov
 * @author Eng Chong Meng
 */
public abstract class AbstractBufferStream<T extends DataSource> extends AbstractControls
		implements SourceStream
{
	/**
	 * The (default) <code>ContentDescriptor</code> of the <code>AbstractBufferStream</code> instances.
	 */
	private static final ContentDescriptor CONTENT_DESCRIPTOR = new ContentDescriptor(ContentDescriptor.RAW);

	/**
	 * The <code>DataSource</code> which has created this instance and which contains it as one of its
	 * <code>streams</code>.
	 */
	protected final T dataSource;

	/**
	 * The <code>FormatControl</code> which gives access to the <code>Format</code> of the media data
	 * provided by this <code>SourceStream</code> and which, optionally, allows setting it.
	 */
	protected final FormatControl formatControl;

	/**
	 * Initializes a new <code>AbstractBufferStream</code> instance which is to have its <code>Format</code>
	 * -related information abstracted by a specific <code>FormatControl</code>.
	 *
	 * @param dataSource
	 * 		the <code>DataSource</code> which is creating the new instance so that it becomes one of
	 * 		its <code>streams</code>
	 * @param formatControl
	 * 		the <code>FormatControl</code> which is to abstract the <code>Format</code>-related
	 * 		information of the new instance
	 */
	protected AbstractBufferStream(T dataSource, FormatControl formatControl)
	{
		this.dataSource = dataSource;
		this.formatControl = formatControl;
	}

	/**
	 * Releases the resources used by this instance throughout its existence and makes it available
	 * for garbage collection. This instance is considered unusable after closing.
	 * <p>
	 * <b>Warning</b>: The method is not invoked by the framework, extenders may choose to invoke
	 * it.
	 * </p>
	 */
	public void close()
	{
		try {
			stop();
		}
		catch (IOException ioex) {
			Timber.e(ioex, "Failed to stop %s", getClass().getSimpleName());
		}
	}

	/**
	 * Gets the <code>Format</code> of this <code>AbstractBufferStream</code> as directly known by it.
	 * Allows extenders to override the <code>Format</code> known to the <code>DataSource</code> which
	 * created this instance and possibly provide more details on the currently set <code>Format</code>.
	 *
	 * @return the <code>Format</code> of this <code>AbstractBufferStream</code> as directly known by it or
	 * <code>null</code> if this <code>AbstractBufferStream</code> does not directly know its
	 * <code>Format</code> and it relies on the <code>DataSource</code> which created it to report
	 * its <code>Format</code>
	 */
	protected Format doGetFormat()
	{
		return null;
	}

	/**
	 * Attempts to set the <code>Format</code> of this <code>AbstractBufferStream</code>. Allows extenders
	 * to enable setting the <code>Format</code> of an existing <code>AbstractBufferStream</code> (in
	 * contract to setting it before the <code>AbstractBufferStream</code> is created by the
	 * <code>DataSource</code> which will provide it).
	 *
	 * @param format
	 * 		the <code>Format</code> to be set as the format of this <code>AbstractBufferStream</code>
	 * @return the <code>Format</code> of this <code>AbstractBufferStream</code> or <code>null</code> if the
	 * attempt to set the <code>Format</code> did not succeed and any last-known <code>Format</code>
	 * is to be left in effect
	 */
	protected Format doSetFormat(Format format)
	{
		return null;
	}

	/**
	 * Determines whether the end of this <code>SourceStream</code> has been reached. The
	 * <code>AbstractBufferStream</code> implementation always returns <code>false</code>.
	 *
	 * @return <code>true</code> if the end of this <code>SourceStream</code> has been reached; otherwise,
	 * <code>false</code>
	 */
	public boolean endOfStream()
	{
		return false;
	}

	/**
	 * Gets a <code>ContentDescriptor</code> which describes the type of the content made available by
	 * this <code>SourceStream</code>. The <code>AbstractBufferStream</code> implementation always
	 * returns a <code>ContentDescriptor</code> with content type equal to
	 * <code>ContentDescriptor#RAW</code>.
	 *
	 * @return a <code>ContentDescriptor</code> which describes the type of the content made available
	 * by this <code>SourceStream</code>
	 */
	public ContentDescriptor getContentDescriptor()
	{
		return CONTENT_DESCRIPTOR;
	}

	/**
	 * Gets the length in bytes of the content made available by this <code>SourceStream</code>. The
	 * <code>AbstractBufferStream</code> implementation always returns <code>LENGTH_UNKNOWN</code>.
	 *
	 * @return the length in bytes of the content made available by this <code>SourceStream</code>
	 * if it is known; otherwise, <code>LENGTH_UKNOWN</code>
	 */
	public long getContentLength()
	{
		return LENGTH_UNKNOWN;
	}

	/**
	 * Implements {@link javax.media.protocol.Controls#getControls()}. Gets the controls available
	 * for this instance.
	 *
	 * @return an array of <code>Object</code>s which represent the controls available for this instance
	 */
	public Object[] getControls()
	{
		if (formatControl != null)
			return new Object[]{formatControl};
		else
			return ControlsAdapter.EMPTY_CONTROLS;
	}

	/**
	 * Gets the <code>Format</code> of the media data made available by this
	 * <code>AbstractBufferStream</code>.
	 *
	 * @return the <code>Format</code> of the media data made available by this
	 * <code>AbstractBufferStream</code>
	 */
	public Format getFormat()
	{
		return (formatControl == null) ? null : formatControl.getFormat();
	}

	/**
	 * Gets the <code>Format</code> of this <code>AbstractBufferStream</code> as directly known by it.
	 *
	 * @return the <code>Format</code> of this <code>AbstractBufferStream</code> as directly known by it
	 */
	Format internalGetFormat()
	{
		return doGetFormat();
	}

	/**
	 * Attempts to set the <code>Format</code> of this <code>AbstractBufferStream</code>.
	 *
	 * @param format
	 * 		the <code>Format</code> to be set as the format of this <code>AbstractBufferStream</code>
	 * @return the <code>Format</code> of this <code>AbstractBufferStream</code> or <code>null</code> if the
	 * attempt to set the <code>Format</code> did not succeed and any last-known <code>Format</code>
	 * is to be left in effect
	 */
	Format internalSetFormat(Format format)
	{
		return doSetFormat(format);
	}

	/**
	 * Starts the transfer of media data from this <code>AbstractBufferStream</code>.
	 *
	 * @throws IOException
	 * 		if anything goes wrong while starting the transfer of media data from this
	 * 		<code>AbstractBufferStream</code>
	 */
	public void start()
			throws IOException
	{
	}

	/**
	 * Stops the transfer of media data from this <code>AbstractBufferStream</code>.
	 *
	 * @throws IOException
	 * 		if anything goes wrong while stopping the transfer of media data from this
	 * 		<code>AbstractBufferStream</code>
	 */
	public void stop()
			throws IOException
	{
	}
}
