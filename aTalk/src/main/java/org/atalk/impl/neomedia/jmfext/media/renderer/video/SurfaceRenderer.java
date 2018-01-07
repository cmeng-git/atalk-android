/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.jmfext.media.renderer.video;

import android.content.Context;
import android.view.*;

import org.atalk.android.util.java.awt.*;
import org.atalk.impl.neomedia.codec.video.AndroidDecoder;
import org.atalk.impl.neomedia.jmfext.media.renderer.AbstractRenderer;
import org.atalk.service.neomedia.ViewAccessor;
import org.atalk.service.neomedia.codec.Constants;

import javax.media.*;
import javax.media.format.VideoFormat;
import javax.media.renderer.VideoRenderer;

/**
 * Dummy renderer used only to construct valid codec graph when decoding into <tt>Surface</tt> is
 * enabled.
 *
 * @author Pawel Domas
 */
@SuppressWarnings("unused")
public class SurfaceRenderer extends AbstractRenderer<VideoFormat> implements VideoRenderer
{
	private final static Format[] INPUT_FORMATS = new Format[] {
			new VideoFormat(
					Constants.ANDROID_SURFACE,
					null, Format.NOT_SPECIFIED,
					Surface.class,
					Format.NOT_SPECIFIED) };

	private Component component;
	public SurfaceRenderer()
	{

	}

	@Override
	public Format[] getSupportedInputFormats()
	{
		return INPUT_FORMATS;
	}

	@Override
	public int process(Buffer buffer)
	{
		return 0;
	}

	@Override
	public void start()
	{

	}

	@Override
	public void stop()
	{

	}

	@Override
	public void close()
	{

	}

	@Override
	public String getName()
	{
		return "SurfaceRenderer";
	}

	@Override
	public void open()
		throws ResourceUnavailableException
	{
	}

	@Override
	public Format setInputFormat(Format format)
	{
		VideoFormat newFormat = (VideoFormat) super.setInputFormat(format);
		if (newFormat.getSize() != null) {
			getComponent().setPreferredSize(new Dimension(newFormat.getSize()));
		}
		return newFormat;
	}

	@Override
	public Rectangle getBounds()
	{
		return null;
	}

	@Override
	public void setBounds(Rectangle rectangle)
	{

	}

	@Override
	public Component getComponent()
	{
		if (component == null) {
			component = new SurfaceComponent();
		}
		return component;
	}

	@Override
	public boolean setComponent(Component component)
	{
		return false;
	}

	private class SurfaceComponent extends Component implements ViewAccessor
	{
		@Override
		public View getView(Context context)
		{
			return AndroidDecoder.renderSurfaceProvider.getView();
		}
	}
}
