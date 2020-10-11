/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.codec.video;

import android.media.MediaCodecInfo;
import android.media.MediaCodecList;

import org.atalk.service.neomedia.codec.Constants;

import java.util.*;

import timber.log.Timber;

import static android.media.MediaCodecList.REGULAR_CODECS;

/**
 * Class used to manage codecs information for <tt>MediaCodec</tt>.
 *
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
public abstract class CodecInfo
{
    /**
     * The mime type of H.264-encoded media data as defined by Android's <tt>MediaCodec</tt> class.
     */
    public static final String MEDIA_CODEC_TYPE_H264 = "video/avc";

    /**
     * The mime type of VP8-encoded media data as defined by Android's <tt>MediaCodec</tt> class.
     */
    public static final String MEDIA_CODEC_TYPE_VP8 = "video/x-vnd.on2.vp8";

    /**
     * The mime type of VP9-encoded media data as defined by Android's <tt>MediaCodec</tt> class.
     */
    public static final String MEDIA_CODEC_TYPE_VP9 = "video/x-vnd.on2.vp9";

    /**
     * List of crashing codecs
     */
    private static final List<String> bannedYuvCodecs;

    /**
     * List of all codecs discovered in the system.
     */
    private static final List<CodecInfo> codecs = new ArrayList<>();

    static {
        bannedYuvCodecs = new ArrayList<>();

        // Banned H264 encoders/decoders - Crashes
        bannedYuvCodecs.add("OMX.SEC.avc.enc");
        // Don't support 3.1 profile used by Jitsi
        bannedYuvCodecs.add("OMX.Nvidia.h264.decode");
        //bannedYuvCodecs.add("OMX.SEC.avc.dec");

        // Banned VP8 encoders/decoders
        bannedYuvCodecs.add("OMX.SEC.vp8.dec");
        // This one works only for res 176x144
        bannedYuvCodecs.add("OMX.google.vpx.encoder");

        MediaCodecInfo[] codecInfos = new MediaCodecList(REGULAR_CODECS).getCodecInfos();
        for (MediaCodecInfo codecInfo : codecInfos) {
            Timber.i("Codec discovered: %s/%s", codecInfo.getName(), Arrays.toString(codecInfo.getSupportedTypes()));
            CodecInfo ci = CodecInfo.getCodecInfo(codecInfo);
            if (ci != null) {
                codecs.add(ci);
                ci.setBanned(bannedYuvCodecs.contains(ci.getName()));
            }
        }

        Timber.i("H264 encoder info: %s", getCodecForType(MEDIA_CODEC_TYPE_H264, true));
        Timber.i("H264 decoder info: %s", getCodecForType(MEDIA_CODEC_TYPE_H264, false));
        Timber.i("VP8 encoder info: %s", getCodecForType(MEDIA_CODEC_TYPE_VP8, true));
        Timber.i("VP8 decoder info: %s", getCodecForType(MEDIA_CODEC_TYPE_VP8, false));
        Timber.i("VP9 encoder info: %s", getCodecForType(MEDIA_CODEC_TYPE_VP9, true));
        Timber.i("VP9 decoder info: %s", getCodecForType(MEDIA_CODEC_TYPE_VP9, false));
    }

    /**
     * <tt>MediaCodecInfo</tt> encapsulated by this instance.
     */
    protected final MediaCodecInfo codecInfo;

    /**
     * <tt>MediaCodecInfo.CodecCapabilities</tt> encapsulated by this instance.
     */
    protected final MediaCodecInfo.CodecCapabilities caps;

    /**
     * List of color formats supported by subject <tt>MediaCodec</tt>.
     */
    protected final ArrayList<CodecColorFormat> colors;

    /**
     * Media type of this <tt>CodecInfo</tt>.
     */
    private final String mediaType;

    /**
     * Profile levels supported by subject <tt>MediaCodec</tt>.
     */
    private ProfileLevel[] profileLevels;

    /**
     * Flag indicates that this codec is known to cause some troubles and is disabled
     * (will be ignored during codec select phase).
     */
    private boolean banned;

    /**
     * Creates a new instance of <tt>CodecInfo</tt> that will encapsulate given <tt>codecInfo</tt>.
     *
     * @param codecInfo the codec info object to encapsulate.
     * @param mediaType media type of the codec
     */
    public CodecInfo(MediaCodecInfo codecInfo, String mediaType)
    {
        this.codecInfo = codecInfo;
        this.mediaType = mediaType;
        this.caps = codecInfo.getCapabilitiesForType(mediaType);

        this.colors = new ArrayList<>();
        int[] colorFormats = caps.colorFormats;
        for (int colorFormat : colorFormats) {
            colors.add(CodecColorFormat.fromInt(colorFormat));
        }
    }

    /**
     * Returns codec name that can be used to obtain <tt>MediaCodec</tt>.
     *
     * @return codec name that can be used to obtain <tt>MediaCodec</tt>.
     */
    public String getName()
    {
        return codecInfo.getName();
    }

    /**
     * Find the codec for given <tt>mimeType</tt>.
     *
     * @param mimeType mime type of the codec.
     * @param isEncoder <tt>true</tt> if encoder should be returned or <tt>false</tt> for decoder.
     * @return the codec for given <tt>mimeType</tt>.
     */
    public static CodecInfo getCodecForType(String mimeType, boolean isEncoder)
    {
        for (CodecInfo codec : codecs) {
            if (!codec.isBanned()
                    && codec.mediaType.equals(mimeType)
                    && codec.codecInfo.isEncoder() == isEncoder) {
                return codec;
            }
        }
        return null;
    }

    /**
     * Returns the list of detected codecs.
     *
     * @return the list of detected codecs.
     */
    public static List<CodecInfo> getSupportedCodecs()
    {
        return Collections.unmodifiableList(codecs);
    }

    /**
     * Returns the list of profiles supported.
     *
     * @return the list of profiles supported.
     */
    protected abstract Profile[] getProfileSet();

    /**
     * Returns the list supported levels.
     *
     * @return the list supported levels.
     */
    protected abstract Level[] getLevelSet();

    private Profile getProfile(int profileInt)
    {
        for (Profile p : getProfileSet()) {
            if (p.value == profileInt)
                return p;
        }
        return new Profile("Unknown", profileInt);
    }

    private Level getLevel(int levelInt)
    {
        for (Level l : getLevelSet()) {
            if (l.value == levelInt)
                return l;
        }
        return new Level("Unknown", levelInt);
    }

    public ProfileLevel[] getProfileLevels()
    {
        if (profileLevels == null) {
            MediaCodecInfo.CodecProfileLevel[] plArray = caps.profileLevels;
            profileLevels = new ProfileLevel[plArray.length];
            for (int i = 0; i < profileLevels.length; i++) {
                Profile p = getProfile(plArray[i].profile);
                Level l = getLevel(plArray[i].level);
                profileLevels[i] = new ProfileLevel(p, l);
            }
        }
        return profileLevels;
    }

    @Override
    public String toString()
    {
        StringBuilder colorStr = new StringBuilder("\ncolors:\n");
        for (int i = 0; i < colors.size(); i++) {
            colorStr.append(colors.get(i));
            if (i != colors.size() - 1)
                colorStr.append(", \n");
        }

        StringBuilder plStr = new StringBuilder("\nprofiles:\n");
        ProfileLevel[] profiles = getProfileLevels();
        for (int i = 0; i < profiles.length; i++) {
            plStr.append(profiles[i].toString());
            if (i != profiles.length - 1)
                plStr.append(", \n");
        }

        return codecInfo.getName() + "(" + getLibjitsiEncoding() + ")" + colorStr + plStr;
    }

    public static CodecInfo getCodecInfo(MediaCodecInfo codecInfo)
    {
        String[] types = codecInfo.getSupportedTypes();
        for (String type : types) {
            try {
                switch (type) {
                    case MEDIA_CODEC_TYPE_H264:
                        return new H264CodecInfo(codecInfo);
                    case MEDIA_CODEC_TYPE_VP8:
                        return new VP8CodecInfo(codecInfo);
                    case MEDIA_CODEC_TYPE_VP9:
                        return new VP9CodecInfo(codecInfo);
                }
            } catch (IllegalArgumentException e) {
                Timber.e(e, "Error initializing codec info: %s, type: %s", codecInfo.getName(), type);
            }
        }
        return null;
    }

    public void setBanned(boolean banned)
    {
        this.banned = banned;
    }

    public boolean isBanned()
    {
        return banned;
    }

    public boolean isEncoder()
    {
        return codecInfo.isEncoder();
    }

    public boolean isNominated()
    {
        return getCodecForType(mediaType, isEncoder()) == this;
    }

    public String getLibjitsiEncoding()
    {
        switch (mediaType) {
            case MEDIA_CODEC_TYPE_H264:
                return Constants.H264;
            case MEDIA_CODEC_TYPE_VP8:
                return Constants.VP8;
            case MEDIA_CODEC_TYPE_VP9:
                return Constants.VP9;
            default:
                return mediaType;
        }
    }

    public static class ProfileLevel
    {
        private final Profile profile;
        private final Level level;

        public ProfileLevel(Profile p, Level l)
        {
            this.profile = p;
            this.level = l;
        }

        @Override
        public String toString()
        {
            return "P: " + profile.toString() + " L: " + level.toString();
        }
    }

    public static class Profile
    {
        private final int value;

        private final String name;

        public Profile(String name, int value)
        {
            this.value = value;
            this.name = name;
        }

        @Override
        public String toString()
        {
            return name + "(0x" + Integer.toString(value, 16) + ")";
        }
    }

    public static class Level
    {
        private final int value;

        private final String name;

        public Level(String name, int value)
        {
            this.value = value;
            this.name = name;
        }

        @Override
        public String toString()
        {
            return name + "(0x" + Integer.toString(value, 16) + ")";
        }
    }

    static class H264CodecInfo extends CodecInfo
    {
        // from OMX_VIDEO_AVCPROFILETYPE
        private final CodecInfo.Profile[] PROFILES = new CodecInfo.Profile[]{
                new Profile("AVCProfileBaseline", 0x01),
                new Profile("AVCProfileMain", 0x02),
                new Profile("AVCProfileExtended", 0x04),
                new Profile("AVCProfileHigh", 0x08),
                new Profile("AVCProfileHigh10", 0x10),
                new Profile("AVCProfileHigh422", 0x20),
                new Profile("AVCProfileHigh444", 0x40),
                new Profile("AVCProfileConstrainedBaseline", 0x10000),
                new Profile("AVCProfileConstrainedBaseline", 0x80000)
        };

        // from OMX_VIDEO_AVCLEVELTYPE
        private final CodecInfo.Level[] LEVELS = new CodecInfo.Level[]{
                new Level("Level1", 0x01),
                new Level("Level1b", 0x02),
                new Level("Level11", 0x04),
                new Level("Level12", 0x08),
                new Level("Level13", 0x10),
                new Level("Level2", 0x20),
                new Level("Level21", 0x40),
                new Level("Level22", 0x80),
                new Level("Level3", 0x100),
                new Level("Level31", 0x200),
                new Level("Level32", 0x400),
                new Level("Level4", 0x800),
                new Level("Level41", 0x1000),
                new Level("Level42", 0x2000),
                new Level("Level5", 0x4000),
                new Level("Level51", 0x8000)
        };

        public H264CodecInfo(MediaCodecInfo codecInfo)
        {
            super(codecInfo, MEDIA_CODEC_TYPE_H264);
        }

        @Override
        protected Profile[] getProfileSet()
        {
            return PROFILES;
        }

        @Override
        protected Level[] getLevelSet()
        {
            return LEVELS;
        }
    }

    static class VP8CodecInfo extends CodecInfo
    {
        private final Profile[] PROFILES = new Profile[]{
                // from OMX_VIDEO_VP8PROFILETYPE
                new Profile("ProfileMain", 0x01)
        };

        private final Level[] LEVELS = new Level[]{
                // from OMX_VIDEO_VP8LEVELTYPE
                new Level("VP8Level_Version0", 0x01),
                new Level("VP8Level_Version1", 0x02),
                new Level("VP8Level_Version2", 0x04),
                new Level("VP8Level_Version3", 0x08)
        };

        public VP8CodecInfo(MediaCodecInfo codecInfo)
        {
            super(codecInfo, MEDIA_CODEC_TYPE_VP8);
        }

        @Override
        protected Profile[] getProfileSet()
        {
            return PROFILES;
        }

        @Override
        protected Level[] getLevelSet()
        {
            return LEVELS;
        }
    }

    static class VP9CodecInfo extends CodecInfo
    {
        private final Profile[] PROFILES = new Profile[]{
                // from OMX_VIDEO_VP9PROFILETYPE
                new Profile("VP9Profile0", 0x01),
                new Profile("VP9Profile1", 0x02),
                new Profile("VP9Profile2", 0x04),
                new Profile("VP9Profile3", 0x08),

                // HDR profiles also support passing HDR metadata
                new Profile("VP9Profile2HDR", 0x1000),
                new Profile("VP9Profile3HDR", 0x2000),
        };

        private final Level[] LEVELS = new Level[]{
                // from OMX_VIDEO_VP9LEVELTYPE
                new Level("VP9Level1", 0x01),
                new Level("VP9Level11", 0x02),
                new Level("VP9Level2", 0x04),
                new Level("VP9Level2", 0x08),
                new Level("VP9Level3", 0x10),
                new Level("VP9Level31", 0x20),
                new Level("VP9Level4", 0x40),
                new Level("VP9Level41", 0x80),
                new Level("VP9Level5", 0x100),
                new Level("VP9Level51", 0x200),
                new Level("VP9Level52", 0x400),
                new Level("VP9Level52", 0x800),
                new Level("VP9Level61", 0x1000),
                new Level("VP9Level62", 0x2000)
        };

        public VP9CodecInfo(MediaCodecInfo codecInfo)
        {
            super(codecInfo, MEDIA_CODEC_TYPE_VP9);
        }

        @Override
        protected Profile[] getProfileSet()
        {
            return PROFILES;
        }

        @Override
        protected Level[] getLevelSet()
        {
            return LEVELS;
        }
    }
}
