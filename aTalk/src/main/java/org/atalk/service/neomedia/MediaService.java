/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.service.neomedia;

import java.awt.Point;
import java.beans.PropertyChangeListener;
import java.io.IOException;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.atalk.service.neomedia.codec.EncodingConfiguration;
import org.atalk.service.neomedia.device.MediaDevice;
import org.atalk.service.neomedia.device.ScreenDevice;
import org.atalk.service.neomedia.format.MediaFormat;
import org.atalk.service.neomedia.format.MediaFormatFactory;
import org.atalk.service.neomedia.recording.Recorder;
import org.atalk.service.neomedia.recording.RecorderEventHandler;
import org.atalk.util.MediaType;

/**
 * The <code>MediaService</code> service is meant to be a wrapper of media libraries such as JMF, FMJ,
 * FFMPEG, and/or others. It takes care of all media play and capture as well as media transport
 * (e.g. over RTP).
 *
 * @author Emil Ivov
 * @author Lyubomir Marinov
 * @author MilanKral
 * @author Eng Chong Meng
 */
public interface MediaService {
    /**
     * The name of the property of <code>MediaService</code> the value of which corresponds to the value
     * returned by {@link #getDefaultDevice(MediaType, MediaUseCase)}. The <code>oldValue</code> and the
     * <code>newValue</code> of the fired <code>PropertyChangeEvent</code> are not to be relied on and
     * instead a call to <code>getDefaultDevice</code> is to be performed to retrieve the new value.
     */
    String DEFAULT_DEVICE = "defaultDevice";

    /**
     * The name of the property which controls whether the libjitsi codecs
     * which depend on ffmpeg (currently mp3, h264 and amrwb) will be enabled.
     */
    String ENABLE_FFMPEG_CODECS_PNAME = "neomedia.MediaService.ENABLE_FFMPEG_CODECS";

    /**
     * The name of the property which controls whether the h264 formats
     * will be registered in libjitsi even if the ffmpeg codec is missing.
     */
    String ENABLE_H264_FORMAT_PNAME = "neomedia.MediaService.ENABLE_H264_FORMAT";

    /**
     * Adds a <code>PropertyChangeListener</code> to be notified about changes in the values of the
     * properties of this instance.
     *
     * @param listener the <code>PropertyChangeListener</code> to be notified about changes in the values of the
     * properties of this instance
     */
    void addPropertyChangeListener(PropertyChangeListener listener);

    /**
     * Those interested in Recorder events add listener through MediaService. This way they don't
     * need to have access to the Recorder instance. Adds a new <code>Recorder.Listener</code> to the
     * list of listeners interested in notifications from a <code>Recorder</code>.
     *
     * @param listener the new <code>Recorder.Listener</code> to be added to the list of listeners interested in
     * notifications from <code>Recorder</code>s.
     */
    void addRecorderListener(Recorder.Listener listener);

    /**
     * Returns a new <code>EncodingConfiguration</code> instance.
     *
     * @return a new <code>EncodingConfiguration</code> instance.
     */
    EncodingConfiguration createEmptyEncodingConfiguration();

    /**
     * Create a <code>MediaStream</code> which will use a specific <code>MediaDevice</code> for capture and
     * playback of media. The new instance will not have a <code>StreamConnector</code> at the time of
     * its construction and a <code>StreamConnector</code> will be specified later on in order to enable
     * the new instance to send and receive media.
     *
     * @param device the <code>MediaDevice</code> to be used by the new instance for capture and playback of media
     *
     * @return a newly-created <code>MediaStream</code> which will use the specified <code>device</code> for
     * capture and playback of media
     */
    MediaStream createMediaStream(MediaDevice device);

    /**
     * Initializes a new <code>MediaStream</code> of a specific <code>MediaType</code>. The new instance
     * will not have a <code>MediaDevice</code> at the time of its initialization and a
     * <code>MediaDevice</code> may be specified later on with the constraint that
     * {@link MediaDevice#getMediaType()} equals <code>mediaType</code>.
     *
     * @param mediaType the <code>MediaType</code> of the new instance to be initialized
     *
     * @return a new <code>MediaStream</code> instance of the specified <code>mediaType</code>
     */
    MediaStream createMediaStream(MediaType mediaType);

    /**
     * Creates a <code>MediaStream</code> that will be using the specified <code>MediaDevice</code> for both
     * capture and playback of media exchanged via the specified <code>StreamConnector</code>.
     *
     * @param connector the <code>StreamConnector</code> the stream should use for sending and receiving media or
     * <code>null</code> if the stream is to not have a <code>StreamConnector</code> configured at
     * initialization time and a <code>StreamConnector</code> is to be specified later on
     * @param device the device to be used for both capture and playback of media exchanged via the
     * specified <code>StreamConnector</code>
     *
     * @return the newly created <code>MediaStream</code>.
     */
    MediaStream createMediaStream(StreamConnector connector, MediaDevice device);

    /**
     * Initializes a new <code>MediaStream</code> instance which is to exchange media of a specific
     * <code>MediaType</code> via a specific <code>StreamConnector</code>.
     *
     * @param connector the <code>StreamConnector</code> the stream should use for sending and receiving media or
     * <code>null</code> if the stream is to not have a <code>StreamConnector</code> configured at
     * initialization time and a <code>StreamConnector</code> is to be specified later on
     * @param mediaType the <code>MediaType</code> of the media to be exchanged by the new instance via the
     * specified <code>connector</code>
     *
     * @return a new <code>MediaStream</code> instance which is to exchange media of the specified
     * <code>mediaType</code> via the specified <code>connector</code>
     */
    MediaStream createMediaStream(StreamConnector connector, MediaType mediaType);

    /**
     * Creates a <code>MediaStream</code> that will be using the specified <code>MediaDevice</code> for both
     * capture and playback of media exchanged via the specified <code>StreamConnector</code>.
     *
     * @param connector the <code>StreamConnector</code> the stream should use for sending and receiving media or
     * <code>null</code> if the stream is to not have a <code>StreamConnector</code> configured at
     * initialization time and a <code>StreamConnector</code> is to be specified later on
     * @param device the device to be used for both capture and playback of media exchanged via the
     * specified <code>StreamConnector</code>
     * @param srtpControl a control which is already created, used to control the ZRTP operations.
     *
     * @return the newly created <code>MediaStream</code>.
     */
    MediaStream createMediaStream(StreamConnector connector, MediaDevice device,
            SrtpControl srtpControl);

    /**
     * Initializes a new <code>MediaStream</code> instance which is to exchange media of a specific
     * <code>MediaType</code> via a specific <code>StreamConnector</code>. The security of the media
     * exchange is to be controlled by a specific <code>SrtpControl</code>.
     *
     * @param connector the <code>StreamConnector</code> the stream should use for sending and receiving media or
     * <code>null</code> if the stream is to not have a <code>StreamConnector</code> configured at
     * initialization time and a <code>StreamConnector</code> is to be specified later on
     * @param mediaType the <code>MediaType</code> of the media to be exchanged by the new instance via the
     * specified <code>connector</code>
     * @param srtpControl the <code>SrtpControl</code> to control the security of the media exchange
     *
     * @return a new <code>MediaStream</code> instance which is to exchange media of the specified
     * <code>mediaType</code> via the specified <code>connector</code>
     */
    MediaStream createMediaStream(StreamConnector connector, MediaType mediaType,
            SrtpControl srtpControl);

    /**
     * Creates a new <code>MediaDevice</code> which uses a specific <code>MediaDevice</code> to capture and
     * play back media and performs mixing of the captured media and the media played back by any
     * other users of the returned <code>MediaDevice</code>. For the <code>AUDIO</code> <code>MediaType</code>,
     * the returned device is commonly referred to as an audio mixer. The <code>MediaType</code> of the
     * returned <code>MediaDevice</code> is the same as the <code>MediaType</code> of the specified <code>device</code>.
     *
     * @param device the <code>MediaDevice</code> which is to be used by the returned <code>MediaDevice</code> to
     * actually capture and play back media
     *
     * @return a new <code>MediaDevice</code> instance which uses <code>device</code> to capture and play
     * back media and performs mixing of the captured media and the media played back by any
     * other users of the returned <code>MediaDevice</code> instance
     */
    MediaDevice createMixer(MediaDevice device);

    /**
     * Creates a new <code>Recorder</code> instance that can be used to record a call which captures and
     * plays back media using a specific <code>MediaDevice</code>.
     *
     * @param device the <code>MediaDevice</code> which is used for media capture and playback by the call to
     * be recorded
     *
     * @return a new <code>Recorder</code> instance that can be used to record a call which captures and
     * plays back media using the specified <code>MediaDevice</code>
     */
    Recorder createRecorder(MediaDevice device);

    /**
     * Creates a new <code>Recorder</code> instance that can be used to record media from a specific
     * <code>RTPTranslator</code>.
     *
     * @param translator the <code>RTPTranslator</code> for which to create a <code>Recorder</code>
     *
     * @return a new <code>Recorder</code> instance that can be used to record media from a specific
     * <code>RTPTranslator</code>.
     */
    Recorder createRecorder(RTPTranslator translator);

    /**
     * Initializes a new <code>RTPTranslator</code> which is to forward RTP and RTCP traffic between
     * multiple <code>MediaStream</code>s.
     *
     * @return a new <code>RTPTranslator</code> which is to forward RTP and RTCP traffic between
     * multiple <code>MediaStream</code>s
     */
    RTPTranslator createRTPTranslator();

    /**
     * Initializes a new <code>SrtpControl</code> instance with a specific <code>SrtpControlType</code>.
     *
     * @param srtpControlType the <code>SrtpControlType</code> of the new instance
     * @param myZid ZRTP seed value
     *
     * @return a new <code>SrtpControl</code> instance with the specified <code>srtpControlType</code>
     */
    SrtpControl createSrtpControl(SrtpControlType srtpControlType, final byte[] myZid);

    /**
     * Get available <code>ScreenDevice</code>s.
     *
     * @return screens
     */
    List<ScreenDevice> getAvailableScreenDevices();

    /**
     * Returns the current <code>EncodingConfiguration</code> instance.
     *
     * @return the current <code>EncodingConfiguration</code> instance.
     */
    EncodingConfiguration getCurrentEncodingConfiguration();

    /**
     * Returns the default <code>MediaDevice</code> for the specified media <code>type</code>.
     *
     * @param mediaType a <code>MediaType</code> value indicating the kind of device that we are trying to obtain.
     * @param useCase <code>MediaUseCase</code> value indicating for the use-case of device that we are trying
     * to obtain.
     *
     * @return the currently default <code>MediaDevice</code> for the specified <code>MediaType</code>, or
     * <code>null</code> if no such device exists.
     */
    MediaDevice getDefaultDevice(MediaType mediaType, MediaUseCase useCase);

    /**
     * Get default <code>ScreenDevice</code> device.
     *
     * @return default screen device
     */
    ScreenDevice getDefaultScreenDevice();

    /**
     * Returns a list containing all devices known to this service implementation and handling the
     * specified <code>MediaType</code>.
     *
     * @param mediaType the media type (i.e. AUDIO or VIDEO) that we'd like to obtain the device list for.
     * @param useCase <code>MediaUseCase</code> value indicating for the use-case of device that we are trying
     * to obtain.
     *
     * @return the list of <code>MediaDevice</code>s currently known to handle the specified
     * <code>mediaType</code>.
     */
    List<MediaDevice> getDevices(MediaType mediaType, MediaUseCase useCase);

    /**
     * Returns a {@link Map} that binds indicates whatever preferences the media service
     * implementation may have for the RTP payload type numbers that get dynamically assigned to
     * {@link MediaFormat}s with no static payload type. The method is useful for formats such as
     * "telephone-event" for example that is statically assigned the 101 payload type by some legacy
     * systems. Signalling protocol implementations such as SIP and XMPP should make sure that,
     * whenever this is possible, they assign to formats the dynamic payload type returned in this
     * {@link Map}.
     *
     * @return a {@link Map} binding some formats to a preferred dynamic RTP payload type number.
     */
    Map<MediaFormat, Byte> getDynamicPayloadTypePreferences();

    /**
     * Gets the <code>MediaFormatFactory</code> through which <code>MediaFormat</code> instances may be
     * created for the purposes of working with the <code>MediaStream</code>s created by this
     * <code>MediaService</code>.
     *
     * @return the <code>MediaFormatFactory</code> through which <code>MediaFormat</code> instances may be
     * created for the purposes of working with the <code>MediaStream</code>s created by this
     * <code>MediaService</code>
     */
    MediaFormatFactory getFormatFactory();

    /**
     * Gets the <code>VolumeControl</code> which controls the volume level of audio input/capture.
     *
     * @return the <code>VolumeControl</code> which controls the volume level of audio input/capture
     */
    VolumeControl getInputVolumeControl();

    /**
     * Get a <code>MediaDevice</code> for a part of desktop streaming/sharing.
     *
     * @param width width of the part
     * @param height height of the part
     * @param x origin of the x coordinate (relative to the full desktop)
     * @param y origin of the y coordinate (relative to the full desktop)
     *
     * @return <code>MediaDevice</code> representing the part of desktop or null if problem
     */
    MediaDevice getMediaDeviceForPartialDesktopStreaming(int width, int height, int x, int y);

    /**
     * Get origin for desktop streaming device.
     *
     * @param mediaDevice media device
     *
     * @return origin
     */
    Point getOriginForDesktopStreamingDevice(MediaDevice mediaDevice);

    /**
     * Gets the <code>VolumeControl</code> which controls the volume level of audio output/playback.
     *
     * @return the <code>VolumeControl</code> which controls the volume level of audio output/playback
     */
    VolumeControl getOutputVolumeControl();

    /**
     * Gives access to currently registered <code>Recorder.Listener</code>s.
     *
     * @return currently registered <code>Recorder.Listener</code>s.
     */
    Iterator<Recorder.Listener> getRecorderListeners();

    /**
     * Creates a preview component for the specified device(video device) used to show video preview
     * from it.
     *
     * @param device the video device
     * @param preferredWidth the width we prefer for the component
     * @param preferredHeight the height we prefer for the component
     *
     * @return the preview component.
     */
    Object getVideoPreviewComponent(MediaDevice device, int preferredWidth,
            int preferredHeight);

    /**
     * If the <code>MediaDevice</code> corresponds to partial desktop streaming device.
     *
     * @param mediaDevice <code>MediaDevice</code>
     *
     * @return true if <code>MediaDevice</code> is a partial desktop streaming device, false otherwise
     */
    boolean isPartialStreaming(MediaDevice mediaDevice);

    /**
     * Removes a <code>PropertyChangeListener</code> to no longer be notified about changes in the
     * values of the properties of this instance.
     *
     * @param listener the <code>PropertyChangeListener</code> to no longer be notified about changes in the
     * values of the properties of this instance
     */
    void removePropertyChangeListener(PropertyChangeListener listener);

    /**
     * Removes an existing <code>Recorder.Listener</code> from the list of listeners interested in
     * notifications from <code>Recorder</code>s.
     *
     * @param listener the existing <code>Listener</code> to be removed from the list of listeners interested in
     * notifications from <code>Recorder</code>s
     */
    void removeRecorderListener(Recorder.Listener listener);

    /**
     * Returns the value which will be used for the canonical end-point identifier (CNAME) in RTCP
     * packets sent by this running instance of libjitsi.
     *
     * @return the value which will be used for the canonical end-point identifier (CNAME) in RTCP
     * packets sent by this running instance of libjitsi.
     */
    String getRtpCname();

    /**
     * Creates a <code>RecorderEventHandler</code> instance that saves received events in JSON format.
     *
     * @param filename the filename into which the created <code>RecorderEventHandler</code> will save received
     * events.
     *
     * @return a <code>RecorderEventHandler</code> instance that saves received events in JSON format.
     *
     * @throws IOException if a <code>RecorderEventHandler</code> could not be created for <code>filename</code>.
     */
    RecorderEventHandler createRecorderEventHandlerJson(String filename)
            throws IOException;
}
