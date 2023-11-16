/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.recording;

import org.atalk.impl.neomedia.device.AudioMixerMediaDevice;
import org.atalk.impl.neomedia.device.MediaDeviceSession;
import org.atalk.service.neomedia.MediaDirection;
import org.atalk.service.neomedia.MediaException;
import org.atalk.service.neomedia.MediaStream;
import org.atalk.service.neomedia.recording.Recorder;
import org.atalk.service.neomedia.recording.RecorderEvent;
import org.atalk.service.neomedia.recording.RecorderEventHandler;
import org.atalk.service.neomedia.recording.Synchronizer;
import org.atalk.util.MediaType;
import org.atalk.util.SoundFileUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.media.DataSink;
import javax.media.Manager;
import javax.media.MediaLocator;
import javax.media.NoDataSinkException;
import javax.media.protocol.ContentDescriptor;
import javax.media.protocol.DataSource;
import javax.media.protocol.FileTypeDescriptor;

/**
 * The call recording implementation. Provides the capability to start and stop call recording.
 *
 * @author Dmitri Melnikov
 * @author Lubomir Marinov
 * @author Boris Grozev
 */
public class RecorderImpl implements Recorder
{
    /**
     * The list of formats in which <code>RecorderImpl</code> instances support recording media.
     */
    public static final String[] SUPPORTED_FORMATS = new String[]{
            /* Disables formats currently not working
            SoundFileUtils.aif,
            SoundFileUtils.au,
            SoundFileUtils.gsm, */
            SoundFileUtils.wav,
            SoundFileUtils.mp3
    };

    /**
     * The <code>AudioMixerMediaDevice</code> which is to be or which is already being recorded by this <code>Recorder</code>.
     */
    private final AudioMixerMediaDevice device;

    /**
     * The <code>RecorderEventHandler</code> which this <code>Recorder</code> should notify when events
     * related to recording (such as start/end of a recording) occur.
     */
    private RecorderEventHandler eventHandler = null;

    /**
     * The <code>MediaDeviceSession</code> is used to create an output data source.
     */
    private MediaDeviceSession deviceSession;

    /**
     * The <code>List</code> of <code>Recorder.Listener</code>s interested in notifications from this <code>Recorder</code>.
     */
    private final List<Recorder.Listener> listeners = new ArrayList<>();

    /**
     * <code>DataSink</code> used to save the output data.
     */
    private DataSink sink;

    /**
     * The indicator which determines whether this <code>Recorder</code> is set to skip media from mic.
     */
    private boolean mute = false;

    /**
     * The filename we will use to record data, supplied when Recorder is started.
     */
    private String filename = null;

    /**
     * Constructs the <code>RecorderImpl</code> with the provided session.
     *
     * @param device device that can create a session that provides the output data source
     */
    public RecorderImpl(AudioMixerMediaDevice device)
    {
        if (device == null)
            throw new NullPointerException("device");

        this.device = device;
    }

    /**
     * Adds a new <code>Recorder.Listener</code> to the list of listeners interested in notifications
     * from this <code>Recorder</code>.
     *
     * @param listener the new <code>Recorder.Listener</code> to be added to the list of listeners interested in
     * notifications from this <code>Recorder</code>
     * @see Recorder#addListener(Recorder.Listener)
     */
    public void addListener(Recorder.Listener listener)
    {
        if (listener == null)
            throw new NullPointerException("listener");

        synchronized (listeners) {
            if (!listeners.contains(listener))
                listeners.add(listener);
        }
    }

    /**
     * Returns a content descriptor to create a recording session with.
     *
     * @param format the format that corresponding to the content descriptor
     * @return content descriptor
     * @throws IllegalArgumentException if the specified <code>format</code> is not a supported recording format
     */
    private ContentDescriptor getContentDescriptor(String format)
            throws IllegalArgumentException
    {
        String type;

        if (SoundFileUtils.wav.equalsIgnoreCase(format))
            type = FileTypeDescriptor.WAVE;
        else if (SoundFileUtils.mp3.equalsIgnoreCase(format))
            type = FileTypeDescriptor.MPEG_AUDIO;
        else if (SoundFileUtils.gsm.equalsIgnoreCase(format))
            type = FileTypeDescriptor.GSM;
        else if (SoundFileUtils.au.equalsIgnoreCase(format))
            type = FileTypeDescriptor.BASIC_AUDIO;
        else if (SoundFileUtils.aif.equalsIgnoreCase(format))
            type = FileTypeDescriptor.AIFF;
        else {
            throw new IllegalArgumentException(format + " is not a supported recording format.");
        }

        return new ContentDescriptor(type);
    }

    /**
     * Gets a list of the formats in which this <code>Recorder</code> supports recording media.
     *
     * @return a <code>List</code> of the formats in which this <code>Recorder</code> supports recording
     * media
     * @see Recorder#getSupportedFormats()
     */
    public List<String> getSupportedFormats()
    {
        return Arrays.asList(SUPPORTED_FORMATS);
    }

    /**
     * Removes a existing <code>Recorder.Listener</code> from the list of listeners interested in
     * notifications from this <code>Recorder</code>.
     *
     * @param listener the existing <code>Recorder.Listener</code> to be removed from the list of listeners
     * interested in notifications from this <code>Recorder</code>
     * @see Recorder#removeListener(Recorder.Listener)
     */
    public void removeListener(Recorder.Listener listener)
    {
        if (listener != null) {
            synchronized (listeners) {
                listeners.remove(listener);
            }
        }
    }

    /**
     * Starts the recording of the media associated with this <code>Recorder</code> (e.g. the media
     * being sent and received in a <code>Call</code>) into a file with a specific name.
     *
     * @param format the format into which the media associated with this <code>Recorder</code> is to be
     * recorded into the specified file
     * @param filename the name of the file into which the media associated with this <code>Recorder</code> is to
     * be recorded
     * @throws IOException if anything goes wrong with the input and/or output performed by this
     * <code>Recorder</code>
     * @throws MediaException if anything else goes wrong while starting the recording of media performed by this
     * <code>Recorder</code>
     * @see Recorder#start(String, String)
     */
    public void start(String format, String filename)
            throws IOException, MediaException
    {
        if (this.sink == null) {
            if (format == null)
                throw new NullPointerException("format");
            if (filename == null)
                throw new NullPointerException("filename");

            this.filename = filename;

            /*
             * A file without an extension may not only turn out to be a touch more difficult to
             * play but is suspected to also cause an exception inside of JMF.
             */
            int extensionBeginIndex = filename.lastIndexOf('.');

            if (extensionBeginIndex < 0)
                filename += '.' + format;
            else if (extensionBeginIndex == filename.length() - 1)
                filename += format;

            MediaDeviceSession deviceSession = device.createSession();

            try {
                deviceSession.setContentDescriptor(getContentDescriptor(format));

                // set initial mute state, if mute was set before starting the
                // recorder
                deviceSession.setMute(mute);

                /*
                 * This RecorderImpl will use deviceSession to get a hold of the media being set to
                 * the remote peers associated with the same AudioMixerMediaDevice i.e. this
                 * RecorderImpl needs deviceSession to only capture and not play back.
                 */
                deviceSession.start(MediaDirection.SENDONLY);

                this.deviceSession = deviceSession;
            } finally {
                if (this.deviceSession == null) {
                    throw new MediaException("Failed to create MediaDeviceSession from"
                            + " AudioMixerMediaDevice for the purposes of recording");
                }
            }

            Throwable exception = null;
            try {
                DataSource outputDataSource = deviceSession.getOutputDataSource();
                DataSink sink = Manager.createDataSink(outputDataSource,
                        new MediaLocator("file: " + filename));

                sink.open();
                sink.start();
                this.sink = sink;
            } catch (NoDataSinkException ndsex) {
                exception = ndsex;
            } finally {
                if ((this.sink == null) || (exception != null)) {
                    stop();

                    throw new MediaException("Failed to start recording into file "
                            + filename, exception);
                }
            }

            if (eventHandler != null) {
                RecorderEvent event = new RecorderEvent();
                event.setType(RecorderEvent.Type.RECORDING_STARTED);
                event.setInstant(System.currentTimeMillis());
                event.setMediaType(MediaType.AUDIO);
                event.setFilename(filename);
                eventHandler.handleEvent(event);
            }
        }
    }

    /**
     * Stops the recording of the media associated with this <code>Recorder</code> (e.g. the media
     * being
     * sent and received in a <code>Call</code>) if it has been started and prepares this
     * <code>Recorder</code> for garbage collection.
     *
     * @see Recorder#stop()
     */
    public void stop()
    {
        if (deviceSession != null) {
            deviceSession.close(MediaDirection.SENDRECV);
            deviceSession = null;
        }

        if (sink != null) {
            sink.close();
            sink = null;

            /*
             * RecorderImpl creates the sink upon start() and it does it only if it is null so this
             * RecorderImpl has really stopped only if it has managed to close() the (existing)
             * sink. Notify the registered listeners.
             */
            Recorder.Listener[] listeners;

            synchronized (this.listeners) {
                listeners = this.listeners.toArray(new Recorder.Listener[this.listeners.size()]);
            }
            for (Recorder.Listener listener : listeners)
                listener.recorderStopped(this);

            if (eventHandler != null) {
                RecorderEvent event = new RecorderEvent();
                event.setType(RecorderEvent.Type.RECORDING_ENDED);
                event.setInstant(System.currentTimeMillis());
                event.setMediaType(MediaType.AUDIO);
                event.setFilename(filename);
                eventHandler.handleEvent(event);
            }
        }
    }

    /**
     * Put the recorder in mute state. It won't record the local input. This is used when the local
     * call is muted and we don't won't to record the local input.
     *
     * @param mute the new value of the mute property
     */
    public void setMute(boolean mute)
    {
        this.mute = mute;
        if (deviceSession != null)
            deviceSession.setMute(mute);
    }

    /**
     * Returns the filename we are last started or stopped recording to, null if not started.
     *
     * @return the filename we are last started or stopped recording to, null if not started.
     */
    public String getFilename()
    {
        return filename;
    }

    /**
     * {@inheritDoc}
     */
    public void setEventHandler(RecorderEventHandler eventHandler)
    {
        this.eventHandler = eventHandler;
    }

    /**
     * {@inheritDoc}
     * <p>
     * This <code>Recorder</code> implementation does not use a <code>Synchronizer</code>.
     */
    public Synchronizer getSynchronizer()
    {
        return null;
    }

    /**
     * {@inheritDoc}
     * <p>
     * This <code>Recorder</code> implementation does not use a <code>Synchronizer</code>.
     */
    public void setSynchronizer(Synchronizer synchronizer)
    {
    }

    /**
     * {@inheritDoc}
     */
    public MediaStream getMediaStream()
    {
        return null;
    }
}
