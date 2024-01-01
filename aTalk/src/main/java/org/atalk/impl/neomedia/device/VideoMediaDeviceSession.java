/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.device;

import org.atalk.impl.timberlog.TimberLog;
import org.atalk.impl.neomedia.AbstractRTPConnector;
import org.atalk.impl.neomedia.MediaStreamImpl;
import org.atalk.impl.neomedia.NeomediaServiceUtils;
import org.atalk.impl.neomedia.RTCPFeedbackMessagePacket;
import org.atalk.impl.neomedia.VideoMediaStreamImpl;
import org.atalk.impl.neomedia.codec.video.HFlip;
import org.atalk.impl.neomedia.codec.video.SwScale;
import org.atalk.impl.neomedia.codec.video.h264.DePacketizer;
import org.atalk.impl.neomedia.codec.video.h264.JNIDecoder;
import org.atalk.impl.neomedia.codec.video.h264.JNIEncoder;
import org.atalk.impl.neomedia.control.ImgStreamingControl;
import org.atalk.impl.neomedia.format.MediaFormatImpl;
import org.atalk.impl.neomedia.format.VideoMediaFormatImpl;
import org.atalk.impl.neomedia.transform.ControlTransformInputStream;
import org.atalk.service.libjitsi.LibJitsi;
import org.atalk.service.neomedia.MediaDirection;
import org.atalk.service.neomedia.control.KeyFrameControl;
import org.atalk.service.neomedia.control.KeyFrameControlAdapter;
import org.atalk.service.neomedia.event.RTCPFeedbackMessageCreateListener;
import org.atalk.service.neomedia.event.RTCPFeedbackMessageEvent;
import org.atalk.service.neomedia.event.RTCPFeedbackMessageListener;
import org.atalk.service.neomedia.format.MediaFormat;
import org.atalk.service.neomedia.format.VideoMediaFormat;
import org.atalk.service.resources.ResourceManagementService;
import org.atalk.util.MediaType;
import org.atalk.util.OSUtils;
import org.atalk.util.event.SizeChangeVideoEvent;
import org.atalk.util.event.VideoEvent;
import org.atalk.util.event.VideoListener;
import org.atalk.util.event.VideoNotifierSupport;
import org.atalk.util.swing.VideoLayout;

import java.awt.Canvas;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Image;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import javax.media.Buffer;
import javax.media.Codec;
import javax.media.ConfigureCompleteEvent;
import javax.media.Controller;
import javax.media.ControllerEvent;
import javax.media.Format;
import javax.media.Manager;
import javax.media.MediaLocator;
import javax.media.NotConfiguredError;
import javax.media.NotRealizedError;
import javax.media.Player;
import javax.media.Processor;
import javax.media.RealizeCompleteEvent;
import javax.media.SizeChangeEvent;
import javax.media.UnsupportedPlugInException;
import javax.media.control.FormatControl;
import javax.media.control.FrameRateControl;
import javax.media.control.TrackControl;
import javax.media.format.VideoFormat;
import javax.media.protocol.CaptureDevice;
import javax.media.protocol.DataSource;
import javax.media.protocol.PullBufferDataSource;
import javax.media.protocol.PullBufferStream;
import javax.media.protocol.SourceCloneable;
import javax.media.rtp.OutputDataStream;
import javax.swing.ImageIcon;
import javax.swing.SwingUtilities;

import timber.log.Timber;

/**
 * Extends <code>MediaDeviceSession</code> to add video-specific functionality.
 *
 * @author Lyubomir Marinov
 * @author Sebastien Vincent
 * @author Hristo Terezov
 * @author Boris Grozev
 * @author Eng Chong Meng
 */
public class VideoMediaDeviceSession extends MediaDeviceSession
        implements RTCPFeedbackMessageCreateListener
{
    /**
     * The image ID of the icon which is to be displayed as the local visual <code>Component</code>
     * depicting the streaming of the desktop of the local peer to the remote peer.
     */
    private static final String DESKTOP_STREAMING_ICON = "impl.media.DESKTOP_STREAMING_ICON";

    /**
     * Gets the visual <code>Component</code> of a specific <code>Player</code> if it has one and
     * ignores the failure to access it if the specified <code>Player</code> is unrealized.
     *
     * @param player the <code>Player</code> to get the visual <code>Component</code> of if it has one
     * @return the visual <code>Component</code> of the specified <code>Player</code> if it has one;
     * <code>null</code> if the specified <code>Player</code> does not have a visual
     * <code>Component</code> or the <code>Player</code> is unrealized
     */
    private static Component getVisualComponent(Player player)
    {
        Component visualComponent = null;

        if (player.getState() >= Player.Realized) {
            try {
                visualComponent = player.getVisualComponent();
            } catch (NotRealizedError nre) {
                Timber.w("Called Player#getVisualComponent on unrealized player %s: %s", player, nre.getMessage());
            }
        }
        return visualComponent;
    }

    /**
     * <code>RTCPFeedbackMessageListener</code> instance that will be passed to
     * {@link #rtpConnector} to handle RTCP PLI requests.
     */
    private RTCPFeedbackMessageListener encoder = null;

    /**
     * The <code>KeyFrameControl</code> used by this<code>VideoMediaDeviceSession</code> as a means to
     * control its key frame-related logic.
     */
    private KeyFrameControl keyFrameControl;

    /**
     * The <code>KeyFrameRequester</code> implemented by this <code>VideoMediaDeviceSession</code> and
     * provided to {@link #keyFrameControl} .
     */
    private KeyFrameControl.KeyFrameRequester keyFrameRequester;

    /**
     * The <code>Player</code> which provides the local visual/video <code>Component</code>.
     */
    private Player localPlayer;

    /**
     * The <code>Object</code> which synchronizes the access to {@link #localPlayer} .
     */
    private final Object localPlayerSyncRoot = new Object();

    /**
     * Local SSRC.
     */
    private long localSSRC = -1;

    /**
     * Output size of the stream.
     * <p>
     * It is used to specify a different size (generally lesser ones) than the capture device
     * provides. Typically one usage can be in desktop streaming/sharing session when sender
     * desktop is bigger than remote ones.
     */
    private Dimension outputSize;

    /**
     * The <code>SwScale</code> inserted into the codec chain of the <code>Player</code> rendering the
     * media received from the remote peer and enabling the explicit setting of the video size.
     */
    private SwScale playerScaler;

    /**
     * Remote SSRC.
     */
    private long remoteSSRC = -1;

    /**
     * The list of <code>RTCPFeedbackMessageCreateListener</code> which will be notified when a
     * <code>RTCPFeedbackMessageListener</code> is created.
     */
    final private List<RTCPFeedbackMessageCreateListener> rtcpFeedbackMessageCreateListeners = new LinkedList<>();

    /**
     * The <code>RTPConnector</code> with which the <code>RTPManager</code> of this instance is to be or is
     * already initialized.
     */
    private AbstractRTPConnector rtpConnector;

    /**
     * Use or not RTCP feedback Picture Loss Indication to request keyframes. Does not affect
     * handling of received RTCP feedback events.
     */
    private boolean useRTCPFeedbackPLI = false;

    /**
     * The facility which aids this instance in managing a list of <code>VideoListener</code>s and
     * firing <code>VideoEvent</code>s to them.
     */
    private final VideoNotifierSupport videoNotifierSupport = new VideoNotifierSupport(this, false);

    /**
     * Initializes a new <code>VideoMediaDeviceSession</code> instance which is to represent the work
     * of a <code>MediaStream</code> with a specific video <code>MediaDevice</code>.
     *
     * @param device the video <code>MediaDevice</code> the use of which by a <code>MediaStream</code> is to be
     * represented by the new instance
     */
    public VideoMediaDeviceSession(AbstractMediaDevice device)
    {
        super(device);
    }

    /**
     * Adds <code>RTCPFeedbackMessageCreateListener</code>.
     *
     * @param listener the listener to add
     */
    public void addRTCPFeedbackMessageCreateListener(RTCPFeedbackMessageCreateListener listener)
    {
        synchronized (rtcpFeedbackMessageCreateListeners) {
            rtcpFeedbackMessageCreateListeners.add(listener);
        }

        if (encoder != null)
            listener.onRTCPFeedbackMessageCreate(encoder);
    }

    /**
     * Adds a specific <code>VideoListener</code> to this instance in order to receive notifications
     * when visual/video <code>Component</code>s are being added and removed.
     * <p>
     * Adding a listener which has already been added does nothing i.e. it is not added more than
     * once and thus does not receive one and the same <code>VideoEvent</code> multiple times.
     * </p>
     *
     * @param listener the <code>VideoListener</code> to be notified when visual/video <code>Component</code>s are
     * being added or removed in this instance
     */
    public void addVideoListener(VideoListener listener)
    {
        videoNotifierSupport.addVideoListener(listener);
    }

    /**
     * Asserts that a specific <code>MediaDevice</code> is acceptable to be set as the
     * <code>MediaDevice</code> of this instance. Makes sure that its <code>MediaType</code> is
     * {@link MediaType#VIDEO}.
     *
     * @param device the <code>MediaDevice</code> to be checked for suitability to become the
     * <code>MediaDevice</code> of this instance
     * @see MediaDeviceSession#checkDevice(AbstractMediaDevice)
     */
    @Override
    protected void checkDevice(AbstractMediaDevice device)
    {
        if (!MediaType.VIDEO.equals(device.getMediaType()))
            throw new IllegalArgumentException("device");
    }

    /**
     * Gets notified about <code>ControllerEvent</code>s generated by {@link #localPlayer}.
     *
     * @param ev the <code>ControllerEvent</code> specifying the <code>Controller</tt which is the source of
     * the event and the very type of the event
     * @param hflip <code>true</code> if the image displayed in the local visual <code>Component</code> is to be
     * horizontally flipped; otherwise, <code>false</code>
     */
    private void controllerUpdateForCreateLocalVisualComponent(ControllerEvent ev, boolean hflip)
    {
        if (ev instanceof ConfigureCompleteEvent) {
            Processor player = (Processor) ev.getSourceController();

            /*
             * Use SwScale for the scaling since it produces an image with better quality and add
             * the "flip" effect to the video.
             */
            TrackControl[] trackControls = player.getTrackControls();

            if ((trackControls != null) && (trackControls.length != 0)) {
                try {
                    for (TrackControl trackControl : trackControls) {
                        trackControl.setCodecChain(hflip
                                ? new Codec[]{new HFlip(), new SwScale()}
                                : new Codec[]{new SwScale()});
                        break;
                    }
                } catch (UnsupportedPlugInException upiex) {
                    Timber.w(upiex, "Failed to add HFlip/SwScale Effect");
                }
            }

            // Turn the Processor into a Player.
            try {
                player.setContentDescriptor(null);
            } catch (NotConfiguredError nce) {
                Timber.e(nce, "Failed to set ContentDescriptor of Processor");
            }
            player.realize();
        }
        else if (ev instanceof RealizeCompleteEvent) {
            Player player = (Player) ev.getSourceController();
            Component visualComponent = player.getVisualComponent();
            boolean start;

            if (visualComponent == null)
                start = false;
            else {
                fireVideoEvent(VideoEvent.VIDEO_ADDED, visualComponent, VideoEvent.LOCAL, false);
                start = true;
            }
            if (start)
                player.start();
            else {
                // No listener is interested in our event so free the resources.
                synchronized (localPlayerSyncRoot) {
                    if (localPlayer == player)
                        localPlayer = null;
                }
                player.stop();
                player.deallocate();
                player.close();
            }
        }
        else if (ev instanceof SizeChangeEvent) {
            /*
             * Mostly for the sake of completeness, notify that the size of the local video has
             * changed like we do for the remote videos.
             */
            SizeChangeEvent scev = (SizeChangeEvent) ev;
            playerSizeChange(scev.getSourceController(), VideoEvent.LOCAL, scev.getWidth(), scev.getHeight());
        }
    }

    /**
     * Creates the <code>DataSource</code> that this instance is to read captured media from.
     *
     * @return the <code>DataSource</code> that this instance is to read captured media from
     */
    @Override
    protected DataSource createCaptureDevice()
    {
        /*
         * Create our DataSource as SourceCloneable so we can use it to both display local video
         * and stream to remote peer.
         */
        DataSource captureDevice = super.createCaptureDevice();
        if (captureDevice != null) {
            MediaLocator locator = captureDevice.getLocator();
            String protocol = (locator == null) ? null : locator.getProtocol();
            float frameRate;
            DeviceConfiguration deviceConfig = NeomediaServiceUtils.getMediaServiceImpl().getDeviceConfiguration();

            // Apply the video size and frame rate configured by the user.
            if (DeviceSystem.LOCATOR_PROTOCOL_IMGSTREAMING.equals(protocol)) {
                /*
                 * It is not clear at this time what the default frame rate for desktop streaming should be.
                 */
                frameRate = 10;
            }
            else {
                Dimension videoSize = deviceConfig.getVideoSize();
                // if we have an output size smaller than our current settings, respect that size
                if (outputSize != null && videoSize.height > outputSize.height
                        && videoSize.width > outputSize.width)
                    videoSize = outputSize;

                Dimension dim = VideoMediaStreamImpl.selectVideoSize(captureDevice, videoSize.width, videoSize.height);
                frameRate = deviceConfig.getFrameRate();
                if (dim != null)
                    Timber.i("Video set initial resolution: [%dx%d]", dim.width, dim.height);
            }

            FrameRateControl frameRateControl = (FrameRateControl) captureDevice.getControl(FrameRateControl.class.getName());
            if (frameRateControl != null) {
                float maxSupportedFrameRate = frameRateControl.getMaxSupportedFrameRate();

                if ((maxSupportedFrameRate > 0) && (frameRate > maxSupportedFrameRate))
                    frameRate = maxSupportedFrameRate;
                if (frameRate > 0)
                    frameRateControl.setFrameRate(frameRate);

                // print initial video frame rate, when starting video
                Timber.i("video send FPS: %s", (frameRate == -1 ? "default(no restriction)" : frameRate));
            }

            if (!(captureDevice instanceof SourceCloneable)) {
                DataSource cloneableDataSource = Manager.createCloneableDataSource(captureDevice);
                if (cloneableDataSource != null)
                    captureDevice = cloneableDataSource;
            }
        }
        return captureDevice;
    }

    /**
     * Initializes a new <code>Player</code> instance which is to provide the local visual/video
     * <code>Component</code>. The new instance is initialized to render the media of the
     * <code>captureDevice</code> of this <code>MediaDeviceSession</code>.
     *
     * @return a new <code>Player</code> instance which is to provide the local visual/video <code>Component</code>
     */
    private Player createLocalPlayer()
    {
        return createLocalPlayer(getCaptureDevice());
    }

    /**
     * Initializes a new <code>Player</code> instance which is to provide the local visual/video
     * <code>Component</code>. The new instance is initialized to render the media of a specific <code>DataSource</code>.
     *
     * @param captureDevice the <code>DataSource</code> which is to have its media rendered by the new instance as the
     * local visual/video <code>Component</code>
     * @return a new <code>Player</code> instance which is to provide the local visual/video <code>Component</code>
     */
    protected Player createLocalPlayer(DataSource captureDevice)
    {
        DataSource dataSource = (captureDevice instanceof SourceCloneable)
                ? ((SourceCloneable) captureDevice).createClone() : null;
        Processor localPlayer = null;

        if (dataSource != null) {
            Exception exception = null;

            try {
                localPlayer = Manager.createProcessor(dataSource);
            } catch (Exception ex) {
                exception = ex;
            }

            if (exception == null) {
                if (localPlayer != null) {
                    /*
                     * If a local visual Component is to be displayed for desktop
                     * sharing/streaming, do not flip it because it does not seem natural.
                     */
                    final boolean hflip = (captureDevice.getControl(ImgStreamingControl.class.getName()) == null);

                    localPlayer.addControllerListener(ev -> controllerUpdateForCreateLocalVisualComponent(ev, hflip));
                    localPlayer.configure();
                }
            }
            else {
                Timber.e(exception, "Failed to connect to %s", MediaStreamImpl.toString(dataSource));
            }
        }
        return localPlayer;
    }

    /**
     * Creates the visual <code>Component</code> depicting the video being streamed from the local peer
     * to the remote peer.
     *
     * @return the visual <code>Component</code> depicting the video being streamed from the local peer
     * to the remote peer if it was immediately created or <code>null</code> if it was not
     * immediately created and it is to be delivered to the currently registered
     * <code>VideoListener</code>s in a <code>VideoEvent</code> with type
     * {@link VideoEvent#VIDEO_ADDED} and origin {@link VideoEvent#LOCAL}
     */
    protected Component createLocalVisualComponent()
    {
        // On Android local preview is displayed directly using Surface provided
        // to the recorder. We don't want to build unused codec chain.
        if (OSUtils.IS_ANDROID) {
            return null;
        }

        /*
         * Displaying the currently streamed desktop is perceived as unnecessary because the user
         * sees the whole desktop anyway. Instead, a static image will be presented.
         */
        DataSource captureDevice = getCaptureDevice();

        if ((captureDevice != null)
                && (captureDevice.getControl(ImgStreamingControl.class.getName()) != null)) {
            return createLocalVisualComponentForDesktopStreaming();
        }

        /*
         * The visual Component to depict the video being streamed from the local peer to the
         * remote peer is created by JMF and its Player so it is likely to take noticeably
         * long time. Consequently, we will deliver it to the currently registered
         * VideoListeners in a VideoEvent after returning from the call.
         */
        Component localVisualComponent;

        synchronized (localPlayerSyncRoot) {
            if (localPlayer == null)
                localPlayer = createLocalPlayer();
            localVisualComponent = (localPlayer == null) ? null : getVisualComponent(localPlayer);
        }
        /*
         * If the local visual/video Component exists at this time, it has likely been created by a
         * previous call to this method. However, the caller may still depend on a VIDEO_ADDED
         * event being fired for it.
         */
        if (localVisualComponent != null) {
            fireVideoEvent(VideoEvent.VIDEO_ADDED, localVisualComponent, VideoEvent.LOCAL, false);
        }
        return localVisualComponent;
    }

    /**
     * Creates the visual <code>Component</code> to depict the streaming of the desktop of the local
     * peer to the remote peer.
     *
     * @return the visual <code>Component</code> to depict the streaming of the desktop of the local
     * peer to the remote peer
     */
    private Component createLocalVisualComponentForDesktopStreaming()
    {
        ResourceManagementService resources = LibJitsi.getResourceManagementService();
        ImageIcon icon = (resources == null) ? null : resources.getImage(DESKTOP_STREAMING_ICON);
        Canvas canvas;

        if (icon == null)
            canvas = null;
        else {
            final Image img = icon.getImage();

            canvas = new Canvas()
            {
                public static final long serialVersionUID = 0L;

                @Override
                public void paint(Graphics g)
                {
                    int width = getWidth();
                    int height = getHeight();

                    g.setColor(Color.BLACK);
                    g.fillRect(0, 0, width, height);

                    int imgWidth = img.getWidth(this);
                    int imgHeight = img.getHeight(this);

                    if ((imgWidth < 1) || (imgHeight < 1))
                        return;

                    boolean scale = false;
                    float scaleFactor = 1;

                    if (imgWidth > width) {
                        scale = true;
                        scaleFactor = width / (float) imgWidth;
                    }
                    if (imgHeight > height) {
                        scale = true;
                        scaleFactor = Math.min(scaleFactor, height / (float) imgHeight);
                    }

                    int dstWidth;
                    int dstHeight;

                    if (scale) {
                        dstWidth = Math.round(imgWidth * scaleFactor);
                        dstHeight = Math.round(imgHeight * scaleFactor);
                    }
                    else {
                        dstWidth = imgWidth;
                        dstHeight = imgHeight;
                    }

                    int dstX = (width - dstWidth) / 2;
                    int dstY = (height - dstWidth) / 2;
                    g.drawImage(img, dstX, dstY, dstX + dstWidth, dstY + dstHeight, 0, 0, imgWidth,
                            imgHeight, this);
                }
            };

            Dimension iconSize = new Dimension(icon.getIconWidth(), icon.getIconHeight());
            canvas.setMaximumSize(iconSize);
            canvas.setPreferredSize(iconSize);

            /*
             * Set a clue so that we can recognize it if it gets received as an argument to
             * #disposeLocalVisualComponent().
             */
            canvas.setName(DESKTOP_STREAMING_ICON);
            fireVideoEvent(VideoEvent.VIDEO_ADDED, canvas, VideoEvent.LOCAL, false);
        }
        return canvas;
    }

    /**
     * Releases the resources allocated by a specific local <code>Player</code> in the course of its
     * execution and prepares it to be garbage collected. If the specified <code>Player</code> is
     * rendering video, notifies the <code>VideoListener</code>s of this instance that its visual
     * <code>Component</code> is to no longer be used by firing a {@link VideoEvent#VIDEO_REMOVED}
     * <code>VideoEvent</code>.
     *
     * @param player the <code>Player</code> to dispose of
     * @see MediaDeviceSession#disposePlayer(Player)
     */
    protected void disposeLocalPlayer(Player player)
    {
        /*
         * The player is being disposed so let the (interested) listeners know its
         * Player#getVisualComponent() (if any) should be released.
         */
        Component visualComponent = null;
        try {
            visualComponent = getVisualComponent(player);
            player.stop();
            player.deallocate();
            player.close();
        } finally {
            synchronized (localPlayerSyncRoot) {
                if (localPlayer == player)
                    localPlayer = null;
            }
            if (visualComponent != null) {
                fireVideoEvent(VideoEvent.VIDEO_REMOVED, visualComponent, VideoEvent.LOCAL, false);
            }
        }
    }

    /**
     * Disposes of the local visual <code>Component</code> of the local peer.
     *
     * @param component the local visual <code>Component</code> of the local peer to dispose of
     */
    protected void disposeLocalVisualComponent(Component component)
    {
        if (component != null) {
            /*
             * Desktop streaming does not use a Player but a Canvas with its name equal to the
             * value of DESKTOP_STREAMING_ICON.
             */
            if (DESKTOP_STREAMING_ICON.equals(component.getName())) {
                fireVideoEvent(VideoEvent.VIDEO_REMOVED, component, VideoEvent.LOCAL, false);
            }
            else {
                Player localPlayer;

                synchronized (localPlayerSyncRoot) {
                    localPlayer = this.localPlayer;
                }
                if (localPlayer != null) {
                    Component localPlayerVisualComponent = getVisualComponent(localPlayer);

                    if ((localPlayerVisualComponent == null)
                            || (localPlayerVisualComponent == component))
                        disposeLocalPlayer(localPlayer);
                }
            }
        }
    }

    /**
     * Releases the resources allocated by a specific <code>Player</code> in the course of its
     * execution and prepares it to be garbage collected. If the specified <code>Player</code>
     * is rendering video, notifies the <code>VideoListener</code>s of this instance that its
     * visual <code>Component</code> is to no longer be used by firing a
     * {@link VideoEvent#VIDEO_REMOVED} <code>VideoEvent</code>.
     *
     * @param player the <code>Player</code> to dispose of
     * @see MediaDeviceSession#disposePlayer(Player)
     */
    @Override
    protected void disposePlayer(Player player)
    {
        /*
         * The player is being disposed so let the (interested) listeners know its
         * Player#getVisualComponent() (if any) should be released.
         */
        Component visualComponent = getVisualComponent(player);
        super.disposePlayer(player);

        if (visualComponent != null) {
            fireVideoEvent(VideoEvent.VIDEO_REMOVED, visualComponent, VideoEvent.REMOTE, false);
        }
    }

    /**
     * Notify the <code>VideoListener</code>s registered with this instance about a specific type of
     * change in the availability of a specific visual <code>Component</code> depicting video.
     *
     * @param type the type of change as defined by <code>VideoEvent</code> in the availability of the
     * specified visual <code>Component</code> depicting video
     * @param visualComponent the visual <code>Component</code> depicting video which has been added
     * or removed in this instance
     * @param origin {@link VideoEvent#LOCAL} if the origin of the video is local (e.g. it is being locally
     * captured); {@link VideoEvent#REMOTE} if the origin of the video is remote (e.g. a
     * remote peer is streaming it)
     * @param wait <code>true</code> if the call is to wait till the specified <code>VideoEvent</code> has been
     * delivered to the <code>VideoListener</code>s; otherwise, <code>false</code>
     * @return <code>true</code> if this event and, more specifically, the visual <code>Component</code> it
     * describes have been consumed and should be considered owned, referenced (which is
     * important because <code>Component</code>s belong to a single <code>Container</code> at a
     * time); otherwise, <code>false</code>
     */
    protected boolean fireVideoEvent(int type, Component visualComponent, int origin, boolean wait)
    {
        Timber.log(TimberLog.FINER, "Firing VideoEvent with type %s, originated from %s and Wait is %s",
                VideoEvent.typeToString(type), VideoEvent.originToString(origin), wait);
        return videoNotifierSupport.fireVideoEvent(type, visualComponent, origin, wait);
    }

    /**
     * Notifies the <code>VideoListener</code>s registered with this instance about a specific <code>VideoEvent</code>.
     *
     * @param videoEvent the <code>VideoEvent</code> to be fired to the <code>VideoListener</code>s registered with
     * this instance
     * @param wait <code>true</code> if the call is to wait till the specified <code>VideoEvent</code> has been
     * delivered to the <code>VideoListener</code>s; otherwise, <code>false</code>
     */
    protected void fireVideoEvent(VideoEvent videoEvent, boolean wait)
    {
        videoNotifierSupport.fireVideoEvent(videoEvent, wait);
    }

    /**
     * Gets the JMF <code>Format</code> of the <code>captureDevice</code> of this <code>MediaDeviceSession</code>.
     *
     * @return the JMF <code>Format</code> of the <code>captureDevice</code> of this <code>MediaDeviceSession</code>
     */
    private Format getCaptureDeviceFormat()
    {
        DataSource captureDevice = getCaptureDevice();
        if (captureDevice != null) {
            FormatControl[] formatControls = null;

            if (captureDevice instanceof CaptureDevice) {
                formatControls = ((CaptureDevice) captureDevice).getFormatControls();
            }
            if ((formatControls == null) || (formatControls.length == 0)) {
                FormatControl formatControl = (FormatControl) captureDevice.getControl(FormatControl.class.getName());

                if (formatControl != null)
                    formatControls = new FormatControl[]{formatControl};
            }
            if (formatControls != null) {
                for (FormatControl formatControl : formatControls) {
                    Format format = formatControl.getFormat();

                    if (format != null)
                        return format;
                }
            }
        }
        return null;
    }

    /**
     * Gets the visual <code>Component</code>, if any, depicting the video streamed from the local peer
     * to the remote peer.
     *
     * @return the visual <code>Component</code> depicting the local video if local video is actually
     * being streamed from the local peer to the remote peer; otherwise, <code>null</code>
     */
    public Component getLocalVisualComponent()
    {
        synchronized (localPlayerSyncRoot) {
            return (localPlayer == null) ? null : getVisualComponent(localPlayer);
        }
    }

    /**
     * Returns the FMJ <code>Format</code> of the video we are receiving from the remote peer.
     *
     * @return the FMJ <code>Format</code> of the video we are receiving from the remote peer or
     * <code>null</code> if we are not receiving any video or the FMJ <code>Format</code> of the
     * video we are receiving from the remote peer cannot be determined
     */
    public VideoFormat getReceivedVideoFormat()
    {
        if (playerScaler != null) {
            Format format = playerScaler.getInputFormat();

            if (format instanceof VideoFormat) {
                return (VideoFormat) format;
            }
        }
        return null;
    }

    /**
     * Returns the format of the video we are streaming to the remote peer.
     *
     * @return The video format of the sent video. Null, if no video is sent.
     */
    public VideoFormat getSentVideoFormat()
    {
        DataSource capture = getCaptureDevice();
        if (capture instanceof PullBufferDataSource) {
            PullBufferStream[] streams = ((PullBufferDataSource) capture).getStreams();

            for (PullBufferStream stream : streams) {
                VideoFormat format = (VideoFormat) stream.getFormat();

                if (format != null)
                    return format;
            }
        }
        return null;
    }

    /**
     * Gets the visual <code>Component</code>s rendering the <code>ReceiveStream</code> corresponding to
     * the given ssrc.
     *
     * @param ssrc the src-id of the receive stream, which visual <code>Component</code> we're looking for
     * @return the visual <code>Component</code> rendering the <code>ReceiveStream</code> corresponding to
     * the given ssrc
     */
    public Component getVisualComponent(long ssrc)
    {
        Player player = getPlayer(ssrc);
        return (player == null) ? null : getVisualComponent(player);
    }

    /**
     * Gets the visual <code>Component</code>s where video from the remote peer is being rendered.
     *
     * @return the visual <code>Component</code>s where video from the remote peer is being rendered
     */
    public List<Component> getVisualComponents()
    {
        List<Component> visualComponents = new LinkedList<>();

        /*
         * When we know (through means such as SDP) that we don't want to receive, it doesn't make
         * sense to wait for the remote peer to acknowledge our desire. So we'll just stop
         * depicting the video of the remote peer regardless of whether it stops or continues its sending.
         */
        if (getStartedDirection().allowsReceiving()) {
            for (Player player : getPlayers()) {
                Component visualComponent = getVisualComponent(player);

                if (visualComponent != null)
                    visualComponents.add(visualComponent);
            }
        }
        return visualComponents;
    }

    /**
     * Implements {@link KeyFrameControl.KeyFrameRequester#requestKeyFrame()} of
     * {@link #keyFrameRequester}.
     *
     * @param keyFrameRequester the <code>KeyFrameControl.KeyFrameRequester</code> on which the method is invoked
     * @return <code>true</code> if this <code>KeyFrameRequester</code> has indeed requested a key frame
     * from the remote peer of the associated <code>VideoMediaStream</code> in response to the
     * call; otherwise, <code>false</code>
     */
    private boolean keyFrameRequesterRequestKeyFrame(KeyFrameControl.KeyFrameRequester keyFrameRequester)
    {
        boolean requested = false;

        if (VideoMediaDeviceSession.this.useRTCPFeedbackPLI) {
            try {
                OutputDataStream controlOutputStream = rtpConnector.getControlOutputStream();
                if (controlOutputStream != null) {
                    new RTCPFeedbackMessagePacket(RTCPFeedbackMessageEvent.FMT_PLI,
                            RTCPFeedbackMessageEvent.PT_PS, localSSRC,
                            remoteSSRC).writeTo(controlOutputStream);
                    requested = true;
                }
            } catch (IOException ioe) {
                /*
                 * Apart from logging the IOException, there are not a lot of ways to handle it.
                 */
            }
        }
        return requested;
    }

    /**
     * Notifies this <code>VideoMediaDeviceSession</code> of a new <code>RTCPFeedbackListener</code>
     *
     * @param rtcpFeedbackMessageListener the listener to be added.
     */
    @Override
    public void onRTCPFeedbackMessageCreate(
            RTCPFeedbackMessageListener rtcpFeedbackMessageListener)
    {
        if (rtpConnector != null) {
            try {
                ((ControlTransformInputStream) rtpConnector.getControlInputStream())
                        .addRTCPFeedbackMessageListener(rtcpFeedbackMessageListener);
            } catch (IOException ioe) {
                Timber.e(ioe, "Error cannot get RTCP input stream");
            }
        }
    }

    /**
     * Notifies this instance that a specific <code>Player</code> of remote content has generated a
     * <code>ConfigureCompleteEvent</code>.
     *
     * @param player the <code>Player</code> which is the source of a <code>ConfigureCompleteEvent</code>
     * @see MediaDeviceSession#playerConfigureComplete(Processor)
     */
    @Override
    protected void playerConfigureComplete(Processor player)
    {
        super.playerConfigureComplete(player);

        TrackControl[] trackControls = player.getTrackControls();
        SwScale playerScaler = null;

        /* We don't add SwScale, KeyFrameControl on Android. */
        if ((trackControls != null) && (trackControls.length != 0) && !OSUtils.IS_ANDROID) {
            String fmjEncoding = getFormat().getJMFEncoding();

            try {
                for (TrackControl trackControl : trackControls) {
                    /*
                     * Since SwScale will scale any input size into the configured output size, we
                     * may never get SizeChangeEvent from the player. We'll generate it ourselves
                     * then.
                     */
                    playerScaler = new PlayerScaler(player);

                    /*
                     * For H.264, we will use RTCP feedback. For example, to tell the sender that
                     * we've missed a frame.
                     */
                    if ("h264/rtp".equalsIgnoreCase(fmjEncoding)) {
                        final DePacketizer depacketizer = new DePacketizer();
                        JNIDecoder decoder = new JNIDecoder();

                        if (keyFrameControl != null) {
                            depacketizer.setKeyFrameControl(keyFrameControl);
                            decoder.setKeyFrameControl(new KeyFrameControlAdapter()
                            {
                                @Override
                                public boolean requestKeyFrame(boolean urgent)
                                {
                                    return depacketizer.requestKeyFrame(urgent);
                                }
                            });
                        }

                        trackControl.setCodecChain(new Codec[]{depacketizer, decoder, playerScaler});
                    }
                    else {
                        trackControl.setCodecChain(new Codec[]{playerScaler});
                    }
                    break;
                }
            } catch (UnsupportedPlugInException upiex) {
                Timber.e(upiex, "Failed to add SwScale or H.264 DePacketizer to codec chain");
                playerScaler = null;
            }
        }
        this.playerScaler = playerScaler;
    }

    /**
     * Gets notified about <code>ControllerEvent</code>s generated by a specific <code>Player</code> of remote content.
     *
     * @param ev the <code>ControllerEvent</code> specifying the <code>Controller</code> which is the source of
     * the event and the very type of the event
     * @see MediaDeviceSession#playerControllerUpdate(ControllerEvent)
     */
    @Override
    protected void playerControllerUpdate(ControllerEvent ev)
    {
        super.playerControllerUpdate(ev);

        /*
         * If SwScale is in the chain and it forces a specific size of the output, the
         * SizeChangeEvents of the Player do not really notify about changes in the size of the
         * input. Besides, playerScaler will take care of the events in such a case.
         */
        if ((ev instanceof SizeChangeEvent)
                && ((playerScaler == null) || (playerScaler.getOutputSize() == null))) {
            SizeChangeEvent scev = (SizeChangeEvent) ev;
            playerSizeChange(scev.getSourceController(), VideoEvent.REMOTE, scev.getWidth(), scev.getHeight());
        }
    }

    /**
     * Notifies this instance that a specific <code>Player</code> of remote content has generated a
     * <code>RealizeCompleteEvent</code>.
     *
     * @param player the <code>Player</code> which is the source of a <code>RealizeCompleteEvent</code>.
     * @see MediaDeviceSession#playerRealizeComplete(Processor)
     */
    @Override
    protected void playerRealizeComplete(final Processor player)
    {
        super.playerRealizeComplete(player);

        Component visualComponent = getVisualComponent(player);
        if (visualComponent != null) {
            /*
             * SwScale seems to be very good at scaling with respect to image quality so use it for
             * the scaling in the player replacing the scaling it does upon rendering.
             */
            visualComponent.addComponentListener(new ComponentAdapter()
            {
                @Override
                public void componentResized(ComponentEvent ev)
                {
                    playerVisualComponentResized(player, ev);
                }
            });
            // cmeng - unnecessary here, let actual video streaming fire.
            // Double fireVideoEvent affects video call reliability
            // fireVideoEvent(VideoEvent.VIDEO_ADDED, visualComponent, VideoEvent.REMOTE, false);
        }
    }

    /**
     * Notify this instance that a specific <code>Player</code> of local or remote content/video has
     * generated a <code>SizeChangeEvent</code>.
     * cmeng: trigger by user?
     *
     * @param sourceController the <code>Player</code> which is the source of the event
     * @param origin {@link VideoEvent#LOCAL} or {@link VideoEvent#REMOTE} which specifies the origin of
     * the visual <code>Component</code> displaying video which is concerned
     * @param width the width reported in the event
     * @param height the height reported in the event
     * @see SizeChangeEvent
     */
    protected void playerSizeChange(final Controller sourceController, final int origin,
            final int width, final int height)
    {
        /*
         * Invoking anything that is likely to change the UI in the Player thread seems like a
         * performance hit so bring it into the event thread.
         */
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(() -> playerSizeChange(sourceController, origin, width, height));
            return;
        }

        Player player = (Player) sourceController;
        Component visualComponent = getVisualComponent(player);

        if (visualComponent != null) {
            /*
             * The Player will notify the new size, and before it reaches the Renderer.
             * The notification/event may as well arrive before the Renderer reflects the
             * new size onto the preferredSize of the Component. In order to make sure the new
             * size is reflected on the preferredSize of the Component before the notification/event
             * arrives to its destination/listener, reflect it as soon as possible i.e. now.
             */
            try {
                Dimension prefSize = visualComponent.getPreferredSize();

                if ((prefSize == null) || (prefSize.width < 1) || (prefSize.height < 1)
                        || !VideoLayout.areAspectRatiosEqual(prefSize, width, height)
                        || (prefSize.width < width) || (prefSize.height < height)) {
                    visualComponent.setPreferredSize(new Dimension(width, height));
                }
            } finally {
                fireVideoEvent(new SizeChangeVideoEvent(this, visualComponent, origin, width, height), false);
                Timber.d("Remote video size change event: %dx%d", width, height);
            }
        }
    }

    /**
     * Notify this instance that the visual <code>Component</code> of a <code>Player</code> rendering
     * remote content has been resized.
     *
     * @param player the <code>Player</code> rendering remote content the visual <code>Component</code> of which has been resized
     * @param ev a <code>ComponentEvent</code> which specifies the resized <code>Component</code>
     */
    private void playerVisualComponentResized(Processor player, ComponentEvent ev)
    {
        if (playerScaler == null)
            return;

        Component visualComponent = ev.getComponent();

        /*
         * When the visualComponent is not in a UI hierarchy, its size is not expected to be
         * representative of what the user is seeing.
         */
        if (visualComponent.isDisplayable())
            return;

        Dimension outputSize = visualComponent.getSize();
        float outputWidth = outputSize.width;
        float outputHeight = outputSize.height;

        if ((outputWidth < SwScale.MIN_SWS_SCALE_HEIGHT_OR_WIDTH)
                || (outputHeight < SwScale.MIN_SWS_SCALE_HEIGHT_OR_WIDTH))
            return;

        /*
         * The size of the output video will be calculated so that it fits into the visualComponent
         * and the video aspect ratio is preserved. The presumption here is that the inputFormat
         * holds the video size with the correct aspect ratio.
         */
        Format inputFormat = playerScaler.getInputFormat();

        if (inputFormat == null)
            return;

        Dimension inputSize = ((VideoFormat) inputFormat).getSize();
        if (inputSize == null)
            return;

        int inputWidth = inputSize.width;
        int inputHeight = inputSize.height;

        if ((inputWidth < 1) || (inputHeight < 1))
            return;

        // Preserve the aspect ratio.
        outputHeight = outputWidth * inputHeight / inputWidth;

        // Fit the output video into the visualComponent.
        boolean scale = false;
        float widthRatio;
        float heightRatio;

        if (Math.abs(outputWidth - inputWidth) < 1) {
            scale = true;
            widthRatio = outputWidth / inputWidth;
        }
        else
            widthRatio = 1;
        if (Math.abs(outputHeight - inputHeight) < 1) {
            scale = true;
            heightRatio = outputHeight / inputHeight;
        }
        else
            heightRatio = 1;
        if (scale) {
            float scaleFactor = Math.min(widthRatio, heightRatio);

            outputWidth = inputWidth * scaleFactor;
            outputHeight = inputHeight * scaleFactor;
        }

        outputSize.width = (int) outputWidth;
        outputSize.height = (int) outputHeight;

        Dimension playerScalerOutputSize = playerScaler.getOutputSize();
        if (playerScalerOutputSize == null)
            playerScaler.setOutputSize(outputSize);
        else {
            /*
             * If we are not going to make much of a change, do not even bother because any scaling
             * in the Renderer will not be noticeable anyway.
             */
            int outputWidthDelta = outputSize.width - playerScalerOutputSize.width;
            int outputHeightDelta = outputSize.height - playerScalerOutputSize.height;

            if ((outputWidthDelta < -1) || (outputWidthDelta > 1) || (outputHeightDelta < -1)
                    || (outputHeightDelta > 1)) {
                playerScaler.setOutputSize(outputSize);
            }
        }
    }

    /**
     * Removes <code>RTCPFeedbackMessageCreateListener</code>.
     *
     * @param listener the listener to remove
     */
    public void removeRTCPFeedbackMessageCreateListner(RTCPFeedbackMessageCreateListener listener)
    {
        synchronized (rtcpFeedbackMessageCreateListeners) {
            rtcpFeedbackMessageCreateListeners.remove(listener);
        }
    }

    /**
     * Removes a specific <code>VideoListener</code> from this instance in order to have to no longer
     * receive notifications when visual/video <code>Component</code>s are being added and removed.
     *
     * @param listener the <code>VideoListener</code> to no longer be notified when visual/video
     * <code>Component</code>s are being added or removed in this instance
     */
    public void removeVideoListener(VideoListener listener)
    {
        videoNotifierSupport.removeVideoListener(listener);
    }

    /**
     * Sets the <code>RTPConnector</code> that will be used to initialize some codec for RTCP feedback.
     *
     * @param rtpConnector the RTP connector
     */
    public void setConnector(AbstractRTPConnector rtpConnector)
    {
        this.rtpConnector = rtpConnector;
    }

    /**
     * Sets the <code>MediaFormat</code> in which this <code>MediaDeviceSession</code> outputs the media
     * captured by its <code>MediaDevice</code>.
     *
     * @param format the <code>MediaFormat</code> in which this <code>MediaDeviceSession</code> is to output the
     * media captured by its <code>MediaDevice</code>
     */
    @Override
    public void setFormat(MediaFormat format)
    {
        if (format instanceof VideoMediaFormat
                && ((VideoMediaFormat) format).getFrameRate() != -1) {
            FrameRateControl frameRateControl
                    = (FrameRateControl) getCaptureDevice().getControl(FrameRateControl.class.getName());

            if (frameRateControl != null) {
                float frameRate = ((VideoMediaFormat) format).getFrameRate();
                float maxSupportedFrameRate = frameRateControl.getMaxSupportedFrameRate();

                if ((maxSupportedFrameRate > 0) && (frameRate > maxSupportedFrameRate))
                    frameRate = maxSupportedFrameRate;
                if (frameRate > 0) {
                    frameRateControl.setFrameRate(frameRate);
                    Timber.i("video send FPS: %s", frameRate);
                }
            }
        }
        super.setFormat(format);
    }

    /**
     * Sets the <code>KeyFrameControl</code> to be used by this <code>VideoMediaDeviceSession</code> as a
     * means of control over its key frame-related logic.
     *
     * @param keyFrameControl the <code>KeyFrameControl</code> to be used by this <code>VideoMediaDeviceSession</code> as a
     * means of control over its key frame-related logic
     */
    public void setKeyFrameControl(KeyFrameControl keyFrameControl)
    {
        if (this.keyFrameControl != keyFrameControl) {
            if ((this.keyFrameControl != null) && (keyFrameRequester != null))
                this.keyFrameControl.removeKeyFrameRequester(keyFrameRequester);

            this.keyFrameControl = keyFrameControl;

            if ((this.keyFrameControl != null) && (keyFrameRequester != null))
                this.keyFrameControl.addKeyFrameRequester(-1, keyFrameRequester);
        }
    }

    /**
     * Set the local SSRC.
     *
     * @param localSSRC local SSRC
     */
    public void setLocalSSRC(long localSSRC)
    {
        this.localSSRC = localSSRC;
    }

    /**
     * Sets the size of the output video.
     *
     * @param size the size of the output video
     */
    public void setOutputSize(Dimension size)
    {
        boolean equal = (size == null) ? (outputSize == null) : size.equals(outputSize);

        if (!equal) {
            outputSize = size;
            outputSizeChanged = true;
        }
    }

    /**
     * Sets the <code>MediaFormatImpl</code> in which a specific <code>Processor</code> producing media to
     * be streamed to the remote peer is to output.
     *
     * @param processor the <code>Processor</code> to set the output <code>MediaFormatImpl</code> of
     * @param mediaFormat the <code>MediaFormatImpl</code> to set on <code>processor</code>
     * @see MediaDeviceSession#setProcessorFormat(Processor, MediaFormatImpl)
     */
    @Override
    protected void setProcessorFormat(Processor processor, MediaFormatImpl<? extends Format> mediaFormat)
    {
        Format format = mediaFormat.getFormat();
        /*
         * Add a size in the output format. As VideoFormat has no setter, we recreate the object.
         * Also check whether capture device can output such a size.
         */
        if ((outputSize != null) && (outputSize.width > 0) && (outputSize.height > 0)) {
            Dimension deviceSize = ((VideoFormat) getCaptureDeviceFormat()).getSize();
            Dimension videoFormatSize;

            if ((deviceSize != null)
                    && ((deviceSize.width > outputSize.width)
                    || (deviceSize.height > outputSize.height))) {
                videoFormatSize = outputSize;
            }
            else {
                videoFormatSize = deviceSize;
                outputSize = null;
            }

            VideoFormat videoFormat = (VideoFormat) format;
            /*
             * FIXME The assignment to the local variable format makes no difference because it is
             * no longer user afterwards.
             */
            format = new VideoFormat(videoFormat.getEncoding(), videoFormatSize,
                    videoFormat.getMaxDataLength(), videoFormat.getDataType(),
                    videoFormat.getFrameRate());
        }
        else
            outputSize = null;

        super.setProcessorFormat(processor, mediaFormat);
    }

    /**
     * Sets the <code>MediaFormatImpl</code> of a specific <code>TrackControl</code> of the
     * <code>Processor</code> which produces the media to be streamed by this <code>MediaDeviceSession</code>
     * to the remote peer. Allows extenders to override the set procedure and to detect when the
     * JMF <code>Format</code> of the specified <code>TrackControl</code> changes.
     *
     * @param trackControl the <code>TrackControl</code> to set the JMF <code>Format</code> of
     * @param mediaFormat the <code>MediaFormatImpl</code> to be set on the specified <code>TrackControl</code>. Though
     * <code>mediaFormat</code> encapsulates a JMF <code>Format</code>, <code>format</code> is to be set
     * on the specified <code>trackControl</code> because it may be more specific. In any case,
     * the two JMF <code>Format</code>s match. The <code>MediaFormatImpl</code> is provided anyway
     * because it carries additional information such as format parameters.
     * @param format the JMF <code>Format</code> to be set on the specified <code>TrackControl</code>. Though
     * <code>mediaFormat</code> encapsulates a JMF <code>Format</code>, the specified <code>format</code>
     * is to be set on the specified <code>trackControl</code> because it may be more specific
     * than the JMF <code>Format</code> of the <code>mediaFormat</code>
     * @return the JMF <code>Format</code> set on <code>TrackControl</code> after the attempt to set the
     * specified <code>mediaFormat</code> or <code>null</code> if the specified <code>format</code> was
     * found to be incompatible with <code>trackControl</code>
     * @see MediaDeviceSession#setProcessorFormat(TrackControl, MediaFormatImpl, Format)
     */
    @Override
    protected Format setProcessorFormat(TrackControl trackControl,
            MediaFormatImpl<? extends Format> mediaFormat, Format format)
    {
        JNIEncoder encoder = null;
        SwScale scaler = null;
        int codecCount = 0;

        /*
         * For H.264 we will monitor RTCP feedback. For example, if we receive a PLI/FIR
         * message, we will send a keyframe.
         */
        /*
         * The current Android video capture device system provided H.264 so it is not possible to
         * insert an H.264 encoder in the chain. Ideally, we will want to base the decision on the
         * format of the capture device and not on the operating system. In a perfect worlds, we
         * will re-implement the functionality bellow using a Control interface and we will not
         * bother with inserting customized codecs.
         */
        // aTalk uses external h264, so accept OSUtils.IS_ANDROID ???
        if (!OSUtils.IS_ANDROID && "h264/rtp".equalsIgnoreCase(format.getEncoding())) {
            encoder = new JNIEncoder();

            // packetization-mode
            Map<String, String> formatParameters = mediaFormat.getFormatParameters();
            String packetizationMode = (formatParameters == null)
                    ? null : formatParameters.get(VideoMediaFormatImpl.H264_PACKETIZATION_MODE_FMTP);
            encoder.setPacketizationMode(packetizationMode);

            // additionalCodecSettings
            encoder.setAdditionalCodecSettings(mediaFormat.getAdditionalCodecSettings());

            this.encoder = encoder;
            onRTCPFeedbackMessageCreate(encoder);
            synchronized (rtcpFeedbackMessageCreateListeners) {
                for (RTCPFeedbackMessageCreateListener l : rtcpFeedbackMessageCreateListeners)
                    l.onRTCPFeedbackMessageCreate(encoder);
            }

            if (keyFrameControl != null)
                encoder.setKeyFrameControl(keyFrameControl);
            codecCount++;
        }
        if (outputSize != null) {
            /*
             * We have been explicitly told to use a specific output size so insert a SwScale into
             * the codec chain which is to take care of the specified output size. However, since
             * the video frames which it will output will be streamed to a remote peer, preserve
             * the aspect ratio of the input.
             */
            scaler = new SwScale(
                    /* fixOddYuv420Size */false,
                    /* preserveAspectRatio */true);
            scaler.setOutputSize(outputSize);
            codecCount++;
        }
        Codec[] codecs = new Codec[codecCount];
        codecCount = 0;
        if (scaler != null)
            codecs[codecCount++] = scaler;
        if (encoder != null)
            codecs[codecCount++] = encoder;

        if (codecCount != 0) {
            /*
             * Add our custom SwScale and possibly RTCP aware codec to the codec chain so that it
             * will be used instead of default.
             */
            try {
                trackControl.setCodecChain(codecs);
            } catch (UnsupportedPlugInException upiex) {
                Timber.e(upiex, "Failed to add SwScale/JNIEncoder to codec chain");
            }
        }
        return super.setProcessorFormat(trackControl, mediaFormat, format);
    }

    /**
     * Set the remote SSRC.
     *
     * @param remoteSSRC remote SSRC
     */
    public void setRemoteSSRC(long remoteSSRC)
    {
        this.remoteSSRC = remoteSSRC;
    }

    /**
     * Sets the indicator which determines whether RTCP feedback Picture Loss Indication (PLI) is
     * to be used to request keyframes.
     *
     * @param useRTCPFeedbackPLI <code>true</code> to use PLI; otherwise, <code>false</code>
     */
    public void setRTCPFeedbackPLI(boolean useRTCPFeedbackPLI)
    {
        if (this.useRTCPFeedbackPLI != useRTCPFeedbackPLI) {
            this.useRTCPFeedbackPLI = useRTCPFeedbackPLI;

            if (this.useRTCPFeedbackPLI) {
                if (keyFrameRequester == null) {
                    keyFrameRequester = new KeyFrameControl.KeyFrameRequester()
                    {
                        @Override
                        public boolean requestKeyFrame()
                        {
                            return keyFrameRequesterRequestKeyFrame(this);
                        }
                    };
                }
                if (keyFrameControl != null)
                    keyFrameControl.addKeyFrameRequester(-1, keyFrameRequester);
            }
            else if (keyFrameRequester != null) {
                if (keyFrameControl != null)
                    keyFrameControl.removeKeyFrameRequester(keyFrameRequester);
                keyFrameRequester = null;
            }
        }
    }

    /**
     * Notify this instance that the value of its <code>startedDirection</code> property has changed
     * from a specific <code>oldValue</code> to a specific <code>newValue</code>.
     *
     * @param oldValue the <code>MediaDirection</code> which used to be the value of the
     * <code>startedDirection</code> property of this instance
     * @param newValue the <code>MediaDirection</code> which is the value of the <code>startedDirection</code>
     * property of this instance
     */
    @Override
    protected void startedDirectionChanged(MediaDirection oldValue, MediaDirection newValue)
    {
        super.startedDirectionChanged(oldValue, newValue);

        try {
            Player localPlayer;
            synchronized (localPlayerSyncRoot) {
                localPlayer = getLocalPlayer();
            }
            if (newValue.allowsSending()) {
                if (localPlayer == null)
                    createLocalVisualComponent();
            }
            else if (localPlayer != null) {
                disposeLocalPlayer(localPlayer);
            }
        } catch (Throwable t) {
            if (t instanceof ThreadDeath)
                throw (ThreadDeath) t;
            else {
                Timber.e(t, "Failed to start/stop the preview of the local video");
            }
        }

        /*
         * Translate the starting and stopping of the playback into respective VideoEvents for the REMOTE origin.
         */
        for (Player player : getPlayers()) {
            int state = player.getState();

            /*
             * The visual Component of a Player is safe to access and, respectively, report through
             * a VideoEvent only when the Player is Realized.
             */
            if (state < Player.Realized) {
                continue;
            }

            if (newValue.allowsReceiving()) {
                if (state != Player.Started) {
                    player.start();

                    Component visualComponent = getVisualComponent(player);
                    if (visualComponent != null) {
                        fireVideoEvent(VideoEvent.VIDEO_ADDED, visualComponent, VideoEvent.REMOTE, false);
                    }
                }
            }
            else {
                /*
                 * cmeng: Video size change is triggered by media decoder and may change due to quality change
                 * Therefore must not dispose of the player when a remote video dimension change;
                 * otherwise there is no player when new video streaming/format is received (with no jingle action).
                 */
                Component visualComponent = getVisualComponent(player);
                player.stop();

                if (visualComponent != null) {
                    fireVideoEvent(VideoEvent.VIDEO_REMOVED, visualComponent, VideoEvent.REMOTE, false);
                }
            }
        }
    }

    /**
     * Return the <code>Player</code> instance which provides the local visual/video <code>Component</code>.
     *
     * @return the <code>Player</code> instance which provides the local visual/video <code>Component</code>
     * .
     */
    protected Player getLocalPlayer()
    {
        synchronized (localPlayerSyncRoot) {
            return localPlayer;
        }
    }

    /**
     * Extends <code>SwScale</code> in order to provide scaling with high quality to a specific
     * <code>Player</code> of remote video.
     */
    private class PlayerScaler extends SwScale
    {
        /**
         * The last size reported in the form of a <code>SizeChangeEvent</code>.
         */
        private Dimension lastSize;

        /**
         * The <code>Player</code> into the codec chain of which this <code>SwScale</code> is set.
         */
        private final Player player;

        /**
         * Initializes a new <code>PlayerScaler</code> instance which is to provide scaling with high
         * quality to a specific <code>Player</code> of remote video.
         *
         * @param player the <code>Player</code> of remote video into the codec chain of which the new instance
         * is to be set
         */
        public PlayerScaler(Player player)
        {
            super(true);
            this.player = player;
        }

        /**
         * Determines when the input video sizes changes and reports it as a
         * <code>SizeChangeVideoEvent</code> because <code>Player</code> is unable to do it when this
         * <code>SwScale</code> is scaling to a specific <code>outputSize</code>.
         *
         * @param inBuf input buffer
         * @param outBuf output buffer
         * @return the native <code>PaSampleFormat</code>
         * @see SwScale#process(Buffer, Buffer)
         */
        @Override
        public int process(Buffer inBuf, Buffer outBuf)
        {
            int result = super.process(inBuf, outBuf);
            if (result == BUFFER_PROCESSED_OK) {
                Format inputFormat = getInputFormat();

                if (inputFormat != null) {
                    Dimension size = ((VideoFormat) inputFormat).getSize();
                    if ((size != null) && (size.height >= MIN_SWS_SCALE_HEIGHT_OR_WIDTH)
                            && (size.width >= MIN_SWS_SCALE_HEIGHT_OR_WIDTH)
                            && !size.equals(lastSize)) {
                        lastSize = size;
                        playerSizeChange(player, VideoEvent.REMOTE, lastSize.width, lastSize.height);
                    }
                }
            }
            return result;
        }

        /**
         * Ensures that this <code>SwScale</code> preserves the aspect ratio of its input video when scaling.
         *
         * @param inputFormat format to set
         * @return format
         * @see SwScale#setInputFormat(Format)
         */
        @Override
        public Format setInputFormat(Format inputFormat)
        {
            inputFormat = super.setInputFormat(inputFormat);
            if (inputFormat instanceof VideoFormat) {
                Dimension inputSize = ((VideoFormat) inputFormat).getSize();

                if ((inputSize != null) && (inputSize.width > 0)) {
                    Dimension outputSize = getOutputSize();
                    int outputWidth;

                    if ((outputSize != null) && ((outputWidth = outputSize.width) > 0)) {
                        int outputHeight = (int) (outputWidth * inputSize.height / (float) inputSize.width);
                        int outputHeightDelta = outputHeight - outputSize.height;

                        if ((outputHeightDelta < -1) || (outputHeightDelta > 1)) {
                            super.setOutputSize(new Dimension(outputWidth, outputHeight));
                        }
                    }
                }
            }
            return inputFormat;
        }
    }
}
