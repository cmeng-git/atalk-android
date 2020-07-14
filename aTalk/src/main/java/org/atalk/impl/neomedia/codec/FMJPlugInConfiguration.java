/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.codec;

import org.atalk.android.plugin.timberlog.TimberLog;
import org.atalk.impl.neomedia.MediaServiceImpl;
import org.atalk.util.OSUtils;

import java.io.IOException;
import java.util.*;

import javax.media.*;

import timber.log.Timber;

/**
 * Utility class that handles registration of FMJ packages and plugins.
 *
 * @author Damian Minkov
 * @author Lyubomir Marinov
 * @author Boris Grozev
 * @author Eng Chong Meng
 */
public class FMJPlugInConfiguration
{
    /**
     * Whether the custom codecs have been registered with FMJ.
     */
    private static boolean codecsRegistered = false;

    /**
     * Whether the custom multiplexers have been registered with FMJ.
     */
    private static boolean multiplexersRegistered = false;

    /**
     * The additional custom JMF codecs.
     */
    private static final String[] CUSTOM_CODECS = {
            // "org.atalk.impl.neomedia.codec.AndroidMediaCodec",
            OSUtils.IS_ANDROID ? "org.atalk.impl.neomedia.codec.video.AndroidEncoder" : null,
            OSUtils.IS_ANDROID ? "org.atalk.impl.neomedia.codec.video.AndroidDecoder" : null,
            "org.atalk.impl.neomedia.codec.audio.alaw.DePacketizer",
            "org.atalk.impl.neomedia.codec.audio.alaw.JavaEncoder",
            "org.atalk.impl.neomedia.codec.audio.alaw.Packetizer",
            "org.atalk.impl.neomedia.codec.audio.ulaw.JavaDecoder",
            "org.atalk.impl.neomedia.codec.audio.ulaw.JavaEncoder",
            "org.atalk.impl.neomedia.codec.audio.ulaw.Packetizer",
            "org.atalk.impl.neomedia.codec.audio.opus.JNIDecoder",
            "org.atalk.impl.neomedia.codec.audio.opus.JNIEncoder",
            "org.atalk.impl.neomedia.codec.audio.speex.JNIDecoder",
            "org.atalk.impl.neomedia.codec.audio.speex.JNIEncoder",
            "org.atalk.impl.neomedia.codec.audio.speex.SpeexResampler",
            "org.atalk.impl.neomedia.codec.audio.ilbc.JavaDecoder",
            "org.atalk.impl.neomedia.codec.audio.ilbc.JavaEncoder",

             EncodingConfigurationImpl.G729 ? "org.atalk.impl.neomedia.codec.audio.g729.JavaDecoder" : null,
             EncodingConfigurationImpl.G729 ? "org.atalk.impl.neomedia.codec.audio.g729.JavaEncoder" : null,

            // cmeng - removed support g722; libjng722.so implementation incomplete???
            // "org.atalk.impl.neomedia.codec.audio.g722.JNIDecoderImpl",
            // "org.atalk.impl.neomedia.codec.audio.g722.JNIEncoderImpl",

            // gsm
            "org.atalk.impl.neomedia.codec.audio.gsm.Decoder",
            "org.atalk.impl.neomedia.codec.audio.gsm.Encoder",
            "org.atalk.impl.neomedia.codec.audio.gsm.DePacketizer",
            "org.atalk.impl.neomedia.codec.audio.gsm.Packetizer",

            // silk
            "org.atalk.impl.neomedia.codec.audio.silk.JavaDecoder",
            "org.atalk.impl.neomedia.codec.audio.silk.JavaEncoder",

            // VP8
            "org.atalk.impl.neomedia.codec.video.vp8.DePacketizer",
            "org.atalk.impl.neomedia.codec.video.vp8.Packetizer",
            "org.atalk.impl.neomedia.codec.video.vp8.VPXDecoder",
            "org.atalk.impl.neomedia.codec.video.vp8.VPXEncoder",

            // VP9 (FMJPlugInConfiguration.java:228)#registerCustomCodecs:
            // vp9.DePacketizer is NOT successfully registered: org.atalk.impl.neomedia.codec.video.vp9.DePacketizer
            // cannot be cast to javax.media.Codec
            "org.atalk.impl.neomedia.codec.video.vp9.DePacketizer",
            "org.atalk.impl.neomedia.codec.video.vp9.Packetizer",
            "org.atalk.impl.neomedia.codec.video.vp9.VPXDecoder",
            "org.atalk.impl.neomedia.codec.video.vp9.VPXEncoder",
    };

    /**
     * The additional custom JMF codecs, which depend on ffmpeg and should
     * therefore only be used when ffmpeg is enabled.
     */
    private static final String[] CUSTOM_CODECS_FFMPEG = {
            // MP3 - cmeng (not working)
            // "org.atalk.impl.neomedia.codec.audio.mp3.JNIEncoder",

            // h264
            "org.atalk.impl.neomedia.codec.video.h264.DePacketizer",
            "org.atalk.impl.neomedia.codec.video.h264.JNIDecoder",
            "org.atalk.impl.neomedia.codec.video.h264.JNIEncoder",
            "org.atalk.impl.neomedia.codec.video.h264.Packetizer",
            "org.atalk.impl.neomedia.codec.video.SwScale",

            // Adaptive Multi-Rate Wideband (AMR-WB)
            // "org.atalk.impl.neomedia.codec.audio.amrwb.DePacketizer",
            // "org.atalk.impl.neomedia.codec.audio.amrwb.JNIDecoder",
            // "org.atalk.impl.neomedia.codec.audio.amrwb.JNIEncoder",
            // "org.atalk.impl.neomedia.codec.audio.amrwb.Packetizer",
    };

    /**
     * The package prefixes of the additional JMF <tt>DataSource</tt>s (e.g. low latency PortAudio
     * and ALSA <tt>CaptureDevice</tt>s).
     */
    private static final String[] CUSTOM_PACKAGES = {
            "org.atalk.impl.neomedia.jmfext",
            "net.java.sip.communicator.impl.neomedia.jmfext",
            "net.sf.fmj"
    };

    /**
     * The list of class names to register as FMJ plugins with type <tt>PlugInManager.MULTIPLEXER</tt>.
     */
    private static final String[] CUSTOM_MULTIPLEXERS = {
            "org.atalk.impl.neomedia.recording.BasicWavMux"
    };

    /**
     * Whether custom packages have been registered with JFM
     */
    private static boolean packagesRegistered = false;

    /**
     * Register in JMF the custom codecs we provide
     *
     * @param enableFfmpeg whether codecs which depend of ffmpeg should be registered.
     */
    public static void registerCustomCodecs(boolean enableFfmpeg)
    {
        if (codecsRegistered)
            return;

        // Register the custom codec which haven't already been registered.
        @SuppressWarnings("unchecked")
        Collection<String> registeredPlugins
                = new HashSet<String>(PlugInManager.getPlugInList(null, null, PlugInManager.CODEC));
        boolean commit = false;

        // Remove JavaRGBToYUV.
        PlugInManager.removePlugIn("com.sun.media.codec.video.colorspace.JavaRGBToYUV", PlugInManager.CODEC);
        PlugInManager.removePlugIn("com.sun.media.codec.video.colorspace.JavaRGBConverter", PlugInManager.CODEC);
        PlugInManager.removePlugIn("com.sun.media.codec.video.colorspace.RGBScaler", PlugInManager.CODEC);

        // Remove JMF's GSM codec. As working only on some OS.
        String gsmCodecPackage = "com.ibm.media.codec.audio.gsm.";

        String[] gsmCodecClasses = new String[]{
                "JavaDecoder",
                "JavaDecoder_ms",
                "JavaEncoder",
                "JavaEncoder_ms",
                "NativeDecoder",
                "NativeDecoder_ms",
                "NativeEncoder",
                "NativeEncoder_ms",
                "Packetizer"
        };

        for (String gsmCodecClass : gsmCodecClasses) {
            PlugInManager.removePlugIn(gsmCodecPackage + gsmCodecClass, PlugInManager.CODEC);
        }

        /*
         * Remove FMJ's JavaSoundCodec because it seems to slow down the
         * building of the filter graph, and we do not currently seem to need it.
         */
        PlugInManager.removePlugIn("net.sf.fmj.media.codec.JavaSoundCodec", PlugInManager.CODEC);

        List<String> customCodecs = new LinkedList<>(Arrays.asList(CUSTOM_CODECS));
        if (enableFfmpeg) {
            customCodecs.addAll(Arrays.asList(CUSTOM_CODECS_FFMPEG));
        }

        for (String className : customCodecs) {
            /*
             * A codec with a className null configured at compile time to not be registered.
             */
            if (className == null)
                continue;

            if (registeredPlugins.contains(className)) {
                Timber.log(TimberLog.FINER, "Codec %s is already registered", className);
            }
            else {
                commit = true;
                try {
                    Codec codec = (Codec) Class.forName(className).newInstance();
                    PlugInManager.addPlugIn(
                            className,
                            codec.getSupportedInputFormats(),
                            codec.getSupportedOutputFormats(null),
                            PlugInManager.CODEC);

                    Timber.log(TimberLog.FINER, "Codec %s is successfully registered", className);
                    // Timber.d("Codec %s is successfully registered", className);
                } catch (Throwable ex) {
                    Timber.w("Codec %s is NOT successfully registered: %s", className, ex.getMessage());
                }
            }
        }

        /*
         * If Jitsi provides a codec which is also provided by FMJ and/or JMF, use Jitsi's version.
         */
        @SuppressWarnings("unchecked")
        Vector<String> codecs = PlugInManager.getPlugInList(null, null, PlugInManager.CODEC);

        if (codecs != null) {
            boolean setPlugInList = false;
            for (String className : customCodecs) {
                if (className != null) {
                    int classNameIndex = codecs.indexOf(className);

                    if (classNameIndex != -1) {
                        codecs.remove(classNameIndex);
                        codecs.add(0, className);
                        setPlugInList = true;
                    }
                }
            }
            if (setPlugInList)
                PlugInManager.setPlugInList(codecs, PlugInManager.CODEC);
        }

        if (commit && !MediaServiceImpl.isJmfRegistryDisableLoad()) {
            try {
                PlugInManager.commit();
            } catch (IOException ex) {
                Timber.e(ex, "Cannot commit to PlugInManager");
            }
        }
        codecsRegistered = true;
    }

    /**
     * Register in JMF the custom packages we provide
     */
    public static void registerCustomPackages()
    {
        if (packagesRegistered)
            return;

        @SuppressWarnings("unchecked")
        Vector<String> packages = PackageManager.getProtocolPrefixList();

        // We prefer our custom packages/protocol prefixes over FMJ's.
        for (int i = CUSTOM_PACKAGES.length - 1; i >= 0; i--) {
            String customPackage = CUSTOM_PACKAGES[i];

            /*
             * Linear search in a loop but it doesn't have to scale since the list is always short.
             */
            if (!packages.contains(customPackage)) {
                packages.add(0, customPackage);
                Timber.d("Adding package  : %s", customPackage);
            }
        }
        PackageManager.setProtocolPrefixList(packages);
        PackageManager.commitProtocolPrefixList();
        Timber.d("Registering new protocol prefix list: %s", packages);

        packagesRegistered = true;
    }

    /**
     * Registers custom libjitsi <tt>Multiplexer</tt> implementations.
     */
    @SuppressWarnings("unchecked")
    public static void registerCustomMultiplexers()
    {
        if (multiplexersRegistered)
            return;

        // Remove the FMJ WAV multiplexers, as they don't work.
        PlugInManager.removePlugIn("com.sun.media.multiplexer.audio.WAVMux", PlugInManager.MULTIPLEXER);
        PlugInManager.removePlugIn("net.sf.fmj.media.multiplexer.audio.WAVMux", PlugInManager.MULTIPLEXER);

        Collection<String> registeredMuxers
                = new HashSet<String>(PlugInManager.getPlugInList(null, null, PlugInManager.MULTIPLEXER));

        boolean commit = false;
        for (String className : CUSTOM_MULTIPLEXERS) {
            if (registeredMuxers.contains(className)) {
                Timber.d("Multiplexer %s is already registered", className);
                continue;
            }

            boolean registered;
            try {
                Multiplexer multiplexer = (Multiplexer) Class.forName(className).newInstance();
                registered = PlugInManager.addPlugIn(
                        className,
                        multiplexer.getSupportedInputFormats(),
                        multiplexer.getSupportedOutputContentDescriptors(null),
                        PlugInManager.MULTIPLEXER);
                Timber.log(TimberLog.FINER, "Codec %s is successfully registered", className);

            } catch (Throwable ex) {
                Timber.w("Codec %s is NOT successfully registered: %s", className, ex.getMessage());
                registered = false;
            }
            commit |= registered;
        }

        if (commit && !MediaServiceImpl.isJmfRegistryDisableLoad()) {
            try {
                PlugInManager.commit();
            } catch (IOException ex) {
                Timber.e(ex, "Cannot commit to PlugInManager");
            }
        }
        multiplexersRegistered = true;
    }
}
