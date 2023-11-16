/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.protocol;

import javax.media.protocol.ContentDescriptor;
import javax.media.protocol.Controls;
import javax.media.protocol.SourceStream;

/**
 * Implements a <code>SourceStream</code> which wraps a specific <code>SourceStream</code>.
 *
 * @param <T>
 * 		the very type of the <code>SourceStream</code> wrapped by <code>SourceStreamDelegate</code>
 * @author Lyubomir Marinov
 */
public class SourceStreamDelegate<T extends SourceStream> implements SourceStream
{

	/**
	 * The <code>SourceStreamDelegate</code> wrapped by this instance.
	 */
	protected final T stream;

	/**
	 * Initializes a new <code>SourceStreamDelegate</code> instance which is to wrap a specific
	 * <code>SourceStream</code>.
	 *
	 * @param stream
	 * 		the <code>SourceStream</code> the new instance is to wrap
	 */
	public SourceStreamDelegate(T stream)
	{
		this.stream = stream;
	}

	/**
	 * Implements {@link SourceStream#endOfStream()}. Delegates to the wrapped
	 * <code>SourceStream</code>
	 * .
	 *
	 * @return <code>true</code> if the wrapped <code>SourceStream</code> has reached the end the
	 * content it makes available
	 */
	public boolean endOfStream()
	{
		return stream.endOfStream();
	}

	/**
	 * Implements {@link SourceStream#getContentDescriptor()}. Delegates to the wrapped
	 * <code>SourceStream</code>.
	 *
	 * @return a <code>ContentDescriptor</code> which describes the content made available by the
	 * wrapped <code>SourceStream</code>
	 */
	public ContentDescriptor getContentDescriptor()
	{
		return stream.getContentDescriptor();
	}

	/**
	 * Implements {@link SourceStream#getContentLength()}. Delegates to the wrapped
	 * <code>SourceStream</code>.
	 *
	 * @return the length of the content made available by the wrapped <code>SourceStream</code>
	 */
	public long getContentLength()
	{
		return stream.getContentLength();
	}

	/**
	 * Implements {@link Controls#getControl(String)}. Delegates to the wrapped
	 * <code>SourceStream</code>.
	 *
	 * @param controlType
	 * 		a <code>String</code> value which specifies the type of the control to be retrieved
	 * @return an <code>Object</code> which represents the control of the wrapped <code>SourceStream</code>
	 * of the specified type if such a control is available; otherwise, <code>null</code>
	 */
	public Object getControl(String controlType)
	{
		return stream.getControl(controlType);
	}

	/**
	 * Implements {@link Controls#getControls()}. Delegates to the wrapped <code>SourceStream</code>.
	 *
	 * @return an array of <code>Object</code>s which represent the controls available for the wrapped
	 * <code>SourceStream</code>
	 */
	public Object[] getControls()
	{
		return stream.getControls();
	}
}
