/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.notify;

import org.atalk.service.audionotifier.AbstractSCAudioClip;
import org.atalk.service.audionotifier.AudioNotifierService;
import org.atalk.service.audionotifier.SCAudioClip;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.net.URL;
import java.security.AccessController;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;

/**
 * Implementation of SCAudioClip.
 *
 * @author Yana Stamcheva
 */
public class JavaSoundClipImpl extends AbstractSCAudioClip
{
	private static Constructor<SCAudioClip> acConstructor = null;
	private final SCAudioClip audioClip;

	@SuppressWarnings("unchecked")
	private static Constructor<SCAudioClip> createAcConstructor()
			throws ClassNotFoundException, NoSuchMethodException, SecurityException
	{
		Class<?> class1;
		try {
			class1 = Class.forName("com.sun.media.sound.JavaSoundAudioClip", true,
					ClassLoader.getSystemClassLoader());
		}
		catch (ClassNotFoundException cnfex) {
			class1 = Class.forName("sun.audio.SunAudioClip", true, null);
		}

		return (Constructor<SCAudioClip>) class1.getConstructor(InputStream.class);
	}

	/**
	 * Creates an AppletAudioClip.
	 *
	 * @param inputStream the audio input stream
	 * @throws IOException
	 */
	private static SCAudioClip createAppletAudioClip(InputStream inputStream)
			throws IOException
	{
		if (acConstructor == null) {
			try {
				acConstructor = AccessController.doPrivileged(
						new PrivilegedExceptionAction<Constructor<SCAudioClip>>()
						{
							public Constructor<SCAudioClip> run()
									throws ClassNotFoundException, NoSuchMethodException,
									SecurityException
							{
								return createAcConstructor();
							}
						});
			}
			catch (PrivilegedActionException paex) {
				throw new IOException("Failed to get AudioClip constructor: "
						+ paex.getException());
			}
		}

		try {
			return acConstructor.newInstance(inputStream);
		}
		catch (Exception ex) {
			throw new IOException("Failed to construct the AudioClip: " + ex);
		}
	}

	/**
	 * Initializes a new <code>JavaSoundClipImpl</code> instance which is to play audio stored at a
	 * specific <code>URL</code> using <code>java.applet.AudioClip</code>.
	 *
	 * @param uri the <code>URL</code> at which the audio is stored and which the new instance is to load
	 * @param audioNotifier the <code>AudioNotifierService</code> which is initializing the new instance and whose
	 * 		<code>mute</code> property/state is to be monitored by the new instance
	 * @throws IOException if a <code>java.applet.AudioClip</code> could not be initialized or the audio at the
	 * 		specified <code>url</code> could not be read
	 */
	public JavaSoundClipImpl(String uri, AudioNotifierService audioNotifier)
			throws IOException
	{
		super(uri, audioNotifier);
		audioClip = createAppletAudioClip(new URL(uri).openStream());
	}

	/**
	 * {@inheritDoc}
	 * <p>
	 * Stops the <code>java.applet.AudioClip</code> wrapped by this instance.
	 */
	@Override
	protected void internalStop()
	{
		try {
			if (audioClip != null)
				audioClip.stop();
		} finally {
			super.internalStop();
		}
	}

	/**
	 * {@inheritDoc}
	 * <p>
	 * Plays the <code>java.applet.AudioClip</code> wrapped by this instance.
	 */
	@Override
	protected boolean runOnceInPlayThread()
	{
		if (audioClip == null)
			return false;
		else {
			audioClip.play();
			return true;
		}
	}
}
