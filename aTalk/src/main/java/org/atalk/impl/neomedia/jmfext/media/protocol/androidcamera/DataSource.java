/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.jmfext.media.protocol.androidcamera;

import javax.media.Format;
import javax.media.MediaLocator;
import javax.media.control.FormatControl;
import javax.media.format.VideoFormat;

import org.atalk.impl.neomedia.jmfext.media.protocol.AbstractPushBufferCaptureDevice;
import org.atalk.impl.neomedia.jmfext.media.protocol.AbstractPushBufferStream;
import org.atalk.service.neomedia.codec.Constants;

/**
 * Camera data source. Creates <tt>PreviewStream</tt> or <tt>SurfaceStream</tt> based on used format encoding.
 *
 * @author Pawel Domas
 */
public class DataSource extends AbstractPushBufferCaptureDevice
{
	public DataSource()
	{
	}

	public DataSource(MediaLocator locator)
	{
		super(locator);
	}

	@Override
	protected AbstractPushBufferStream createStream(int i, FormatControl formatControl)
	{
		String encoding = formatControl.getFormat().getEncoding();
		if (encoding.equals(Constants.ANDROID_SURFACE)) {
			return new SurfaceStream(this, formatControl);
		}
		else {
			return new PreviewStream(this, formatControl);
		}
	}

	@Override
	protected Format setFormat(int streamIndex, Format oldValue, Format newValue)
	{
		if (newValue instanceof VideoFormat) {
			// This DataSource supports setFormat.
			return newValue;
		}
		else
			return super.setFormat(streamIndex, oldValue, newValue);
	}
}
