/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.service.neomedia.recording;

import org.atalk.service.neomedia.MediaException;
import org.atalk.service.neomedia.MediaStream;

import java.io.IOException;
import java.util.List;

/**
 * The call recording interface. Provides the capability to start and stop call recording.
 *
 * @author Dmitri Melnikov
 * @author Lubomir Marinov
 * @author Boris Grozev
 */
public interface Recorder
{
	/**
	 * The name of the configuration property the value of which specifies the full path to the
	 * directory with media recorded by <code>Recorder</code> (e.g. the media being sent and received in
	 * a <code>Call</code>).
	 */
	public static final String SAVED_CALLS_PATH = "neomedia.SAVED_CALLS_PATH";

	/**
	 * The name of the configuration property the value of which specifies the format in which media
	 * is to be recorded by <code>Recorder</code> (e.g. the media being sent and received in a
	 * <code>Call</code>).
	 */
	public static final String FORMAT = "neomedia.Recorder.FORMAT";

	/**
	 * Adds a new <code>Listener</code> to the list of listeners interested in notifications from this
	 * <code>Recorder</code>.
	 *
	 * @param listener
	 *        the new <code>Listener</code> to be added to the list of listeners interested in
	 *        notifications from this <code>Recorder</code>
	 */
	public void addListener(Listener listener);

	/**
	 * Gets a list of the formats in which this <code>Recorder</code> supports recording media.
	 *
	 * @return a <code>List</code> of the formats in which this <code>Recorder</code> supports recording
	 *         media
	 */
	public List<String> getSupportedFormats();

	/**
	 * Removes an existing <code>Listener</code> from the list of listeners interested in notifications
	 * from this <code>Recorder</code>.
	 *
	 * @param listener
	 *        the existing <code>Listener</code> to be removed from the list of listeners interested in
	 *        notifications from this <code>Recorder</code>
	 */
	public void removeListener(Listener listener);

	/**
	 * Starts the recording of the media associated with this <code>Recorder</code> (e.g. the media
	 * being sent and received in a <code>Call</code>) into a file with a specific name.
	 *
	 * @param format
	 *        the format into which the media associated with this <code>Recorder</code> is to be
	 *        recorded into the specified file
	 * @param filename
	 *        the name of the file into which the media associated with this <code>Recorder</code> is to
	 *        be recorded
	 * @throws IOException
	 *         if anything goes wrong with the input and/or output performed by this
	 *         <code>Recorder</code>
	 * @throws MediaException
	 *         if anything else goes wrong while starting the recording of media performed by this
	 *         <code>Recorder</code>
	 */
	public void start(String format, String filename)
		throws IOException, MediaException;

	/**
	 * Stops the recording of the media associated with this <code>Recorder</code> (e.g. the media being
	 * sent and received in a <code>Call</code>) if it has been started and prepares this
	 * <code>Recorder</code> for garbage collection.
	 */
	public void stop();

	/**
	 * Represents a listener interested in notifications from a <code>Recorder</code>.
	 *
	 * @author Lubomir Marinov
	 */
	public interface Listener
	{
		/**
		 * Notifies this <code>Listener</code> that a specific <code>Recorder</code> has stopped recording
		 * the media associated with it.
		 *
		 * @param recorder
		 *        the <code>Recorder</code> which has stopped recording its associated media
		 */
		public void recorderStopped(Recorder recorder);
	}

	/**
	 * Put the recorder in mute state. It won't record the local input. This is used when the local
	 * call is muted and we don't won't to record the local input.
	 * 
	 * @param mute
	 *        the new value of the mute property
	 */
	public void setMute(boolean mute);

	/**
	 * Returns the filename we are last started or stopped recording to, null if not started.
	 * 
	 * @return the filename we are last started or stopped recording to, null if not started.
	 */
	public String getFilename();

	/**
	 * Sets the <code>RecorderEventHandler</code> which this <code>Recorder</code> should notify when events
	 * related to recording (such as start/end of a recording) occur.
	 * 
	 * @param eventHandler
	 *        the <code>RecorderEventHandler</code> to set.
	 */
	public void setEventHandler(RecorderEventHandler eventHandler);

	/**
	 * Gets the <code>Synchronizer</code> of this <code>Recorder</code>.
	 * 
	 * @return the <code>Synchronizer</code> of this <code>Recorder</code>.
	 */
	public Synchronizer getSynchronizer();

	/**
	 * Sets the <code>Synchronizer</code> that this instance should use.
	 * 
	 * @param synchronizer
	 *        the <code>Synchronizer</code> to set.
	 */
	public void setSynchronizer(Synchronizer synchronizer);

	/**
	 * Gets the <code>MediaStream</code> associated with this <code>Recorder</code>.
	 * 
	 * @return the <code>MediaStream</code> associated with this <code>Recorder</code>.
	 */
	public MediaStream getMediaStream();
}
