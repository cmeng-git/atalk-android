/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.device;

import android.content.Context;
import android.net.Uri;

import net.java.sip.communicator.util.ServiceUtils;

import org.atalk.impl.neomedia.NeomediaActivator;
import org.atalk.service.osgi.OSGiService;

import java.io.IOException;
import java.io.InputStream;

import javax.media.format.AudioFormat;

import timber.log.Timber;

/**
 * Utils that obtain audio resource input stream and its format.
 *
 * @author Damian Minkov
 * @author Eng Chong Meng
 */
public class AudioStreamUtils
{
	/**
	 * Obtains an audio input stream from the URL provided.
	 * 
	 * @param url
	 *        a valid url to a sound resource.
	 * @return the input stream to audio data.
	 * @throws java.io.IOException
	 *         if an I/O exception occurs
	 */
	public static InputStream getAudioInputStream(String url)
		throws IOException
	{
		InputStream audioStream = null;
		try {
			Context context = ServiceUtils.getService(NeomediaActivator.getBundleContext(),
				OSGiService.class);

			// As Android resources don't use file extensions, remove it if
			// there is one.
			int lastPathSeparator = url.lastIndexOf('/');
			int extensionStartIx;
			String resourceUri;

			if ((lastPathSeparator > -1)
				&& ((extensionStartIx = url.lastIndexOf('.')) > lastPathSeparator))
				resourceUri = url.substring(0, extensionStartIx);
			else
				resourceUri = url;
			resourceUri = "android.resource://" + context.getPackageName() + "/" + resourceUri;
			audioStream = context.getContentResolver().openInputStream(Uri.parse(resourceUri));
		}
		catch (Throwable t) {
			if (t instanceof ThreadDeath)
				throw (ThreadDeath) t;
			Timber.e(t, "Error opening file:%s", url);
		}
		return audioStream;
	}

	/**
	 * Returns the audio format for the <tt>InputStream</tt>. Or null if format cannot be obtained.
	 * 
	 * @param audioInputStream
	 *        the input stream.
	 * @return the format of the audio stream.
	 */
	public static AudioFormat getFormat(InputStream audioInputStream)
	{
		WaveHeader waveHeader = new WaveHeader(audioInputStream);

		return new javax.media.format.AudioFormat(javax.media.format.AudioFormat.LINEAR,
			waveHeader.getSampleRate(), waveHeader.getBitsPerSample(), waveHeader.getChannels());
	}
}
