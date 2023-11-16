/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.protocol.media;

import net.java.sip.communicator.service.protocol.Call;
import net.java.sip.communicator.service.protocol.CallConference;

import org.atalk.service.neomedia.BasicVolumeControl;
import org.atalk.service.neomedia.MediaService;
import org.atalk.service.neomedia.MediaUseCase;
import org.atalk.service.neomedia.RTPTranslator;
import org.atalk.service.neomedia.VolumeControl;
import org.atalk.service.neomedia.device.MediaDevice;
import org.atalk.service.neomedia.device.MediaDeviceWrapper;
import org.atalk.util.MediaType;
import org.atalk.util.OSUtils;
import org.atalk.util.event.PropertyChangeNotifier;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.lang.ref.WeakReference;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

/**
 * Extends <code>CallConference</code> to represent the media-specific information associated with the
 * telephony conference-related state of a <code>MediaAwareCall</code>.
 *
 * @author Lyubomir Marinov
 * @author Eng Chong Meng
 */
public class MediaAwareCallConference extends CallConference
{
    /**
     * The <code>PropertyChangeListener</code> which will listen to the <code>MediaService</code> about
     * <code>PropertyChangeEvent</code>s.
     */
    private static WeakPropertyChangeListener mediaServicePropertyChangeListener;

    /**
     * The <code>MediaDevice</code>s indexed by <code>MediaType</code> ordinal which are to be used by this
     * telephony conference for media capture and/or playback. If the <code>MediaDevice</code> for a
     * specific <code>MediaType</code> is <code>null</code> ,
     * {@link MediaService#getDefaultDevice(MediaType, MediaUseCase)} is called.
     */
    private final MediaDevice[] devices;

    /**
     * The <code>MediaDevice</code>s which implement media mixing on the respective
     * <code>MediaDevice</code> in {@link #devices} for the purposes of this telephony conference.
     */
    private final MediaDevice[] mixers;

    /**
     * The <code>VolumeControl</code> implementation which is to control the volume (level) of the
     * audio played back the telephony conference represented by this instance.
     */
    private final VolumeControl outputVolumeControl
            = new BasicVolumeControl(VolumeControl.PLAYBACK_VOLUME_LEVEL_PROPERTY_NAME);

    /**
     * The <code>PropertyChangeListener</code> which listens to sources of
     * <code>PropertyChangeEvent</code>s on behalf of this instance.
     */
    private final PropertyChangeListener propertyChangeListener = new PropertyChangeListener()
    {
        @Override
        public void propertyChange(PropertyChangeEvent ev)
        {
            MediaAwareCallConference.this.propertyChange(ev);
        }
    };

    /**
     * Sync around creating/removing audio and video translator.
     */
    private final Object translatorSyncRoot = new Object();

    /**
     * The <code>RTPTranslator</code> which forwards video RTP and RTCP traffic between the
     * <code>CallPeer</code>s of the <code>Call</code>s participating in this telephony conference when the
     * local peer is acting as a conference focus.
     */
    private RTPTranslator videoRTPTranslator;

    /**
     * The <code>RTPTranslator</code> which forwards audio RTP and RTCP traffic
     * between the <code>CallPeer</code>s of the <code>Call</code>s participating in
     * this telephony conference when the local peer is acting as a conference focus.
     */
    private RTPTranslator audioRTPTranslator;

    /**
     * The indicator which determines whether the telephony conference
     * represented by this instance is mixing or relaying.
     * By default what can be mixed is mixed (audio) and rest is relayed.
     */
    private boolean translator = false;

    /**
     * Initializes a new <code>MediaAwareCallConference</code> instance.
     */
    public MediaAwareCallConference()
    {
        this(false);
    }

    /**
     * Initializes a new <code>MediaAwareCallConference</code> instance which is to optionally utilize
     * the Jitsi Videobridge server-side telephony conferencing technology.
     *
     * @param jitsiVideobridge <code>true</code> if the telephony conference represented by the new instance is to
     * utilize the Jitsi Videobridge server-side telephony conferencing technology; otherwise, <code>false</code>
     */
    public MediaAwareCallConference(boolean jitsiVideobridge)
    {
        this(jitsiVideobridge, false);
    }

    /**
     * Initializes a new <code>MediaAwareCallConference</code> instance which is to optionally
     * utilize the Jitsi Videobridge server-side telephony conferencing technology.
     *
     * @param jitsiVideobridge <code>true</code> if the telephony conference
     * represented by the new instance is to utilize the Jitsi Videobridge
     * server-side telephony conferencing technology; otherwise, <code>false</code>
     */
    public MediaAwareCallConference(boolean jitsiVideobridge, boolean translator)
    {
        super(jitsiVideobridge);

        this.translator = translator;
        int mediaTypeCount = MediaType.values().length;
        devices = new MediaDevice[mediaTypeCount];
        mixers = new MediaDevice[mediaTypeCount];

        /*
         * Listen to the MediaService in order to reflect changes in the user's selection with
         * respect to the default media device.
         */
        addMediaServicePropertyChangeListener(propertyChangeListener);
    }

    /**
     * Adds a specific <code>PropertyChangeListener</code> to be notified about
     * <code>PropertyChangeEvent</code>s fired by the current <code>MediaService</code> implementation. The
     * implementation adds a <code>WeakReference</code> to the specified <code>listener</code> because
     * <code>MediaAwareCallConference</code> is unable to determine when the
     * <code>PropertyChangeListener</code> is to be removed.
     *
     * @param listener the <code>PropertyChangeListener</code> to add
     */
    private static synchronized void addMediaServicePropertyChangeListener(
            PropertyChangeListener listener)
    {
        if (mediaServicePropertyChangeListener == null) {
            final MediaService mediaService = ProtocolMediaActivator.getMediaService();

            if (mediaService != null) {
                mediaServicePropertyChangeListener = new WeakPropertyChangeListener()
                {
                    @Override
                    protected void addThisToNotifier()
                    {
                        mediaService.addPropertyChangeListener(this);
                    }

                    @Override
                    protected void removeThisFromNotifier()
                    {
                        mediaService.removePropertyChangeListener(this);
                    }
                };
            }
        }
        if (mediaServicePropertyChangeListener != null) {
            mediaServicePropertyChangeListener.addPropertyChangeListener(listener);
        }
    }

    /**
     * {@inheritDoc}
     * <p>
     * If this telephony conference switches from being a conference focus to not being such,
     * disposes of the mixers used by this instance when it was a conference focus
     */
    @Override
    protected void conferenceFocusChanged(boolean oldValue, boolean newValue)
    {
        /*
         * If this telephony conference switches from being a conference focus to not being one,
         * dispose of the mixers used when it was a conference focus.
         */
        if (oldValue && !newValue) {
            Arrays.fill(mixers, null);

            /*
             * Disposing the video translator is not needed when the conference changes as we have
             * video and we will want to continue with the video Removed when chasing a bug where
             * video call becomes conference call and then back again video call and the video from
             * the conference focus side is not transmitted. if (videoRTPTranslator != null) {
             * videoRTPTranslator.dispose(); videoRTPTranslator = null; }
             */
        }
        super.conferenceFocusChanged(oldValue, newValue);
    }

    /**
     * {@inheritDoc}
     * <p>
     * Disposes of <code>this.videoRTPTranslator</code> if the removed <code>Call</code> was the last
     * <code>Call</code> in this <code>CallConference</code>.
     *
     * @param call the <code>Call</code> which has been removed from the list of <code>Call</code>s participating
     * in this telephony conference.
     */
    @Override
    protected void callRemoved(Call call)
    {
        super.callRemoved(call);
        if (getCallCount() == 0) {
            synchronized (translatorSyncRoot) {
                if (videoRTPTranslator != null) {
                    videoRTPTranslator.dispose();
                    videoRTPTranslator = null;
                }

                if (audioRTPTranslator != null) {
                    audioRTPTranslator.dispose();
                    audioRTPTranslator = null;
                }
            }
        }
    }

    /**
     * Gets a <code>MediaDevice</code> which is capable of capture and/or playback of media of the
     * specified <code>MediaType</code> and is the default choice of the user with respect to such a
     * <code>MediaDevice</code>.
     *
     * @param mediaType the <code>MediaType</code> in which the retrieved <code>MediaDevice</code> is to capture
     * and/or play back media
     * @param useCase the <code>MediaUseCase</code> associated with the intended utilization of the
     * <code>MediaDevice</code> to be retrieved
     * @return a <code>MediaDevice</code> which is capable of capture and/or playback of media of the
     * specified <code>mediaType</code> and is the default choice of the user with respect to
     * such a <code>MediaDevice</code>
     */
    public MediaDevice getDefaultDevice(MediaType mediaType, MediaUseCase useCase)
    {
        int mediaTypeIndex = mediaType.ordinal();
        MediaDevice device = devices[mediaTypeIndex];
        MediaService mediaService = ProtocolMediaActivator.getMediaService();

        if (device == null)
            device = mediaService.getDefaultDevice(mediaType, useCase);

        /*
         * Make sure that the device is capable of mixing in order to support conferencing and call
         * recording.
         */
        if (device != null) {
            MediaDevice mixer = mixers[mediaTypeIndex];

            if (mixer == null) {
                switch (mediaType) {
                    case AUDIO:
                        /*
                         * TODO AudioMixer leads to very poor audio quality on Android so do not
                         * use it unless it is really really necessary.
                         */
                        if ((!OSUtils.IS_ANDROID || isConferenceFocus())
                                && !this.translator
                                /*
                                 * We can use the AudioMixer only if the device is able to capture
                                 * (because the AudioMixer will push when the capture device pushes).
                                 */
                                && device.getDirection().allowsSending()) {
                            mixer = mediaService.createMixer(device);
                        }
                        break;

                    case VIDEO:
                        if (isConferenceFocus())
                            mixer = mediaService.createMixer(device);
                        break;
                }
                mixers[mediaTypeIndex] = mixer;
            }

            if (mixer != null)
                device = mixer;
        }
        return device;
    }

    /**
     * Gets the <code>VolumeControl</code> which controls the volume (level) of the audio played
     * back in the telephony conference represented by this instance.
     *
     * @return the <code>VolumeControl</code> which controls the volume (level) of the audio played
     * back in the telephony conference represented by this instance
     */
    public VolumeControl getOutputVolumeControl()
    {
        return outputVolumeControl;
    }

    /**
     * Gets the <code>RTPTranslator</code> which forwards RTP and RTCP traffic between the
     * <code>CallPeer</code>s of the <code>Call</code>s participating in this telephony conference when the
     * local peer is acting as a conference focus.
     *
     * @param mediaType the <code>MediaType</code> of the <code>MediaStream</code> which RTP and RTCP traffic is to be
     * forwarded between
     * @return the <code>RTPTranslator</code> which forwards RTP and RTCP traffic between the
     * <code>CallPeer</code>s of the <code>Call</code>s participating in this telephony conference
     * when the local peer is acting as a conference focus
     */
    public RTPTranslator getRTPTranslator(MediaType mediaType)
    {
        /*
         * XXX A mixer is created for audio even when the local peer is not a conference focus in
         * order to enable additional functionality. Similarly, the videoRTPTranslator is created
         * even when the local peer is not a conference focus in order to enable the local peer to
         * turn into a conference focus at a later time. More specifically, MediaStreamImpl is
         * unable to accommodate an RTPTranslator after it has created its RTPManager. Yet again
         * like the audio mixer, we'd better not try to use it on Android at this time because of
         * performance issues that might arise.
         */

        // cmeng - enable it even for Android - need it for jitsi-videBridge ???
        // if (MediaType.VIDEO.equals(mediaType) && (isConferenceFocus())) {
        if (MediaType.VIDEO.equals(mediaType) && (!OSUtils.IS_ANDROID || isConferenceFocus())) {
            synchronized (translatorSyncRoot) {
                if (videoRTPTranslator == null) {
                    videoRTPTranslator = ProtocolMediaActivator.getMediaService().createRTPTranslator();
                }
                return videoRTPTranslator;
            }
        }

        if (this.translator) {
            synchronized (translatorSyncRoot) {
                if (audioRTPTranslator == null) {
                    audioRTPTranslator = ProtocolMediaActivator.getMediaService().createRTPTranslator();
                }
                return audioRTPTranslator;
            }
        }
        return null;
    }

    /**
     * Notifies this <code>MediaAwareCallConference</code> about changes in the values of the
     * properties of sources of <code>PropertyChangeEvent</code>s. For example, this instance listens
     * to  changes of the value of {@link MediaService#DEFAULT_DEVICE} which represents the
     * user's choice with respect to the default audio device.
     *
     * @param ev a <code>PropertyChangeEvent</code> which specifies the name of the property which had its
     * value changed and the old and new values of that property
     */
    private void propertyChange(PropertyChangeEvent ev)
    {
        String propertyName = ev.getPropertyName();

        if (MediaService.DEFAULT_DEVICE.equals(propertyName)) {
            Object source = ev.getSource();

            if (source instanceof MediaService) {
                /*
                 * XXX We only support changing the default audio device at the time of this
                 * writing.
                 */
                int mediaTypeIndex = MediaType.AUDIO.ordinal();
                MediaDevice mixer = mixers[mediaTypeIndex];
                MediaDevice oldValue = (mixer instanceof MediaDeviceWrapper)
                        ? ((MediaDeviceWrapper) mixer).getWrappedDevice() : null;
                MediaDevice newValue = devices[mediaTypeIndex];

                if (newValue == null) {
                    newValue = ProtocolMediaActivator.getMediaService()
                            .getDefaultDevice(MediaType.AUDIO, MediaUseCase.ANY);
                }

                /*
                 * XXX If MediaService#getDefaultDevice(MediaType, MediaUseCase) above returns null
                 * and its earlier return value was not null, we will not notify of an actual
                 * change in the value of the user's choice with respect to the default audio
                 * device.
                 */
                if (oldValue != newValue) {
                    mixers[mediaTypeIndex] = null;
                    firePropertyChange(MediaAwareCall.DEFAULT_DEVICE, oldValue, newValue);
                }
            }
        }
    }

    /**
     * Sets the <code>MediaDevice</code> to be used by this telephony conference for capture and/or
     * playback of media of a specific <code>MediaType</code>.
     *
     * @param mediaType the <code>MediaType</code> of the media which is to be captured and/or played back by the
     * specified <code>device</code>
     * @param device the <code>MediaDevice</code> to be used by this telephony conference for capture and/or
     * playback of media of the specified <code>mediaType</code>
     */
    void setDevice(MediaType mediaType, MediaDevice device)
    {
        int mediaTypeIndex = mediaType.ordinal();
        MediaDevice oldValue = devices[mediaTypeIndex];

        /*
         * XXX While we know the old and the new master/wrapped devices, we are not sure whether
         * the mixer has been used. Anyway, we have to report different values in order to have
         * PropertyChangeSupport really fire an event.
         */
        MediaDevice mixer = mixers[mediaTypeIndex];

        if (mixer instanceof MediaDeviceWrapper)
            oldValue = ((MediaDeviceWrapper) mixer).getWrappedDevice();

        MediaDevice newValue = devices[mediaTypeIndex] = device;

        if (oldValue != newValue) {
            mixers[mediaTypeIndex] = null;
            firePropertyChange(MediaAwareCall.DEFAULT_DEVICE, oldValue, newValue);
        }
    }

    /**
     * Implements a <code>PropertyChangeListener</code> which weakly references and delegates to
     * specific <code>PropertyChangeListener</code>s and automatically adds itself to and removes
     * itself from a specific <code>PropertyChangeNotifier</code> depending on whether there are
     * <code>PropertyChangeListener</code>s to delegate to. Thus enables listening to a
     * <code>PropertyChangeNotifier</code> by invoking
     * {@link PropertyChangeNotifier#addPropertyChangeListener(PropertyChangeListener)} without
     * {@link PropertyChangeNotifier#removePropertyChangeListener(PropertyChangeListener)}.
     */
    private static class WeakPropertyChangeListener implements PropertyChangeListener
    {
        /**
         * The indicator which determines whether this <code>PropertyChangeListener</code> has been
         * added to {@link #notifier}.
         */
        private boolean added = false;

        /**
         * The list of <code>PropertyChangeListener</code>s which are to be notified about
         * <code>PropertyChangeEvent</code>s fired by {@link #notifier}.
         */
        private final List<WeakReference<PropertyChangeListener>> listeners = new LinkedList<>();

        /**
         * The <code>PropertyChangeNotifier</code> this instance is to listen to about
         * <code>PropertyChangeEvent</code>s which are to be forwarded to {@link #listeners}.
         */
        private final PropertyChangeNotifier notifier;

        /**
         * Initializes a new <code>WeakPropertyChangeListener</code> instance.
         */
        protected WeakPropertyChangeListener()
        {
            this(null);
        }

        /**
         * Initializes a new <code>WeakPropertyChangeListener</code> instance which is to listen to a
         * specific <code>PropertyChangeNotifier</code>.
         *
         * @param notifier the <code>PropertyChangeNotifier</code> the new instance is to listen to
         */
        public WeakPropertyChangeListener(PropertyChangeNotifier notifier)
        {
            this.notifier = notifier;
        }

        /**
         * Adds a specific <code>PropertyChangeListener</code> to the list of
         * <code>PropertyChangeListener</code>s to be notified about <code>PropertyChangeEvent</code>s
         * fired by the <code>PropertyChangeNotifier</code> associated with this instance.
         *
         * @param listener the <code>PropertyChangeListener</code> to add
         */
        public synchronized void addPropertyChangeListener(PropertyChangeListener listener)
        {
            Iterator<WeakReference<PropertyChangeListener>> i = listeners.iterator();
            boolean add = true;

            while (i.hasNext()) {
                PropertyChangeListener l = i.next().get();

                if (l == null)
                    i.remove();
                else if (l.equals(listener))
                    add = false;
            }
            if (add && listeners.add(new WeakReference<>(listener))
                    && !this.added) {
                addThisToNotifier();
                this.added = true;
            }
        }

        /**
         * Adds this as a <code>PropertyChangeListener</code> to {@link #notifier}.
         */
        protected void addThisToNotifier()
        {
            if (notifier != null)
                notifier.addPropertyChangeListener(this);
        }

        /**
         * {@inheritDoc}
         * <p>
         * Notifies this instance about a <code>PropertyChangeEvent</code> fired by {@link #notifier}.
         */
        @Override
        public void propertyChange(PropertyChangeEvent ev)
        {
            PropertyChangeListener[] ls;
            int n;

            synchronized (this) {
                Iterator<WeakReference<PropertyChangeListener>> i = listeners.iterator();

                ls = new PropertyChangeListener[listeners.size()];
                n = 0;
                while (i.hasNext()) {
                    PropertyChangeListener l = i.next().get();

                    if (l == null)
                        i.remove();
                    else
                        ls[n++] = l;
                }
                if ((n == 0) && this.added) {
                    removeThisFromNotifier();
                    this.added = false;
                }
            }

            if (n != 0) {
                for (PropertyChangeListener l : ls) {
                    if (l == null)
                        break;
                    else
                        l.propertyChange(ev);
                }
            }
        }

        /**
         * Removes a specific <code>PropertyChangeListener</code> from the list of
         * <code>PropertyChangeListener</code>s to be notified about <code>PropertyChangeEvent</code>s
         * fired by the <code>PropertyChangeNotifier</code> associated with this instance.
         *
         * @param listener the <code>PropertyChangeListener</code> to remove
         */
        @SuppressWarnings("unused")
        public synchronized void removePropertyChangeListener(PropertyChangeListener listener)
        {
            Iterator<WeakReference<PropertyChangeListener>> i = listeners.iterator();
            while (i.hasNext()) {
                PropertyChangeListener l = i.next().get();

                if ((l == null) || l.equals(listener))
                    i.remove();
            }
            if (this.added && (listeners.size() == 0)) {
                removeThisFromNotifier();
                this.added = false;
            }
        }

        /**
         * Removes this as a <code>PropertyChangeListener</code> from {@link #notifier}.
         */
        protected void removeThisFromNotifier()
        {
            if (notifier != null)
                notifier.removePropertyChangeListener(this);
        }
    }
}
