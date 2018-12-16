package net.sf.fmj.media;

import java.util.*;

import javax.media.*;
import javax.media.format.*;
import javax.media.pim.PlugInManager;
import javax.media.protocol.*;

import net.sf.fmj.registry.*;
import net.sf.fmj.utility.*;
import net.sf.fmj.utility.PlugInInfo;

import com.lti.utils.*;

/**
 * Defaults for the FMJ registry. Broken out into fmj, jmf, and third-party. fmj ones are
 * fmj-specific. jmf ones are to duplicate what is in jmf (useful if jmf is in the classpath).
 * third-party ones are those that are not included with fmj but might be in the classpath (like
 * fobs4jmf). The flags give us the flexibility to make the registry the same as JMF's or JMF's +
 * FMJ's, or just FMJ's. Making it the same as JMF's is useful for unit testing.
 * 
 * @author Ken Larson
 * 
 */
public class RegistryDefaults
{
	// com.sun and com.ibm are added only for compatibility with the reference implementation.
	// FMJ does not provide any of the sun or ibm implementations.

	/** JMF registry entries. */
	public static final int JMF = 0x0001;
	/** FMJ registry entries. */
	public static final int FMJ = 0x0002;
	/** FMJ (native library required) registry entries. */
	public static final int FMJ_NATIVE = 0x0008;
	/** Third-party (not JMF or FMJ) registry entries. */
	public static final int THIRD_PARTY = 0x0004;
	/** No registry entries. */
	public static final int NONE = 0;
	/** All registry entries. */
	public static final int ALL = JMF | FMJ | FMJ_NATIVE | THIRD_PARTY;

	// GStreamer plugin is still a bit flaky, we'll disable by default.
	private static final boolean ENABLE_GSTREAMER = false;

	// if ogg is not needed, class loading is faster
	public static boolean DISABLE_OGG = false;

	private static int defaultFlags = -1;

	public static List<String> contentPrefixList(int flags)
	{
		final List<String> contentPrefixList = new ArrayList<>();

		if ((flags & JMF) != 0) {
			contentPrefixList.add("javax");
			contentPrefixList.add("com.sun");
			contentPrefixList.add("com.ibm");
		}
		if ((flags & FMJ_NATIVE) != 0) {
			if (OSUtils.isMacOSX() || OSUtils.isWindows()) {
				// Quicktime:
				contentPrefixList.add("net.sf.fmj.qt");
			}
			if (OSUtils.isWindows()) {
				// DirectShow:
				contentPrefixList.add("net.sf.fmj.ds");
			}
			if (ENABLE_GSTREAMER && OSUtils.isLinux()) {
				// TODO: we could add these for other OS's, as gstreamer is cross-platform.
				// DirectShow:
				contentPrefixList.add("net.sf.fmj.gst");
			}
		}
		if ((flags & FMJ) != 0) {
			contentPrefixList.add("net.sf.fmj");
		}
		if ((flags & THIRD_PARTY) != 0) {
			// none to add
		}
		return contentPrefixList;
	}

	public static final int getDefaultFlags()
	{
		if (defaultFlags == -1) {
			boolean jmfDefaults = false;
			try {
				jmfDefaults = System.getProperty("net.sf.fmj.utility.JmfRegistry.JMFDefaults",
					"false").equals("true");
			}
			catch (SecurityException e) { // we must be an applet.
			}
			defaultFlags = jmfDefaults ? RegistryDefaults.JMF : RegistryDefaults.ALL;
		}
		return defaultFlags;
	}

	/**
	 * List items are either classnames (String) or PlugInInfo.
	 */
	public static List<Object> plugInList(int flags)
	{
		final List<Object> result = new ArrayList<>();
		if (flags == NONE)
			return result;

		// TODO: if JMF is in the classpath ahead of FMJ, we get:
		// Problem adding net.sf.fmj.media.codec.video.jpeg.Packetizer to plugin table.
		// Already hash value of 8706141154469562557 in plugin table for class
		// name of com.sun.media.codec.video.jpeg.Packetizer
		// Problem adding net.sf.fmj.media.codec.video.jpeg.DePacketizer to plugin table.
		// Already hash value of 3049617401990556986 in plugin table for class
		// name of com.sun.media.codec.video.jpeg.DePacketizer
		// Problem adding net.sf.fmj.media.renderer.audio.JavaSoundRenderer to plugin table.
		// Already hash value of 1262232571547748861 in plugin table for class
		// name of com.sun.media.renderer.audio.JavaSoundRenderer
		// Problem adding net.sf.fmj.media.multiplexer.RTPSyncBufferMux to plugin table.
		// Already hash value of -2095741743343195187 in plugin table for class
		// name of com.sun.media.multiplexer.RTPSyncBufferMux

		// PlugInManager.DEMULTIPLEXER:
		if ((flags & JMF) != 0) {
			result.add(new PlugInInfo("com.ibm.media.parser.video.MpegParser", new Format[] {
				new ContentDescriptor("audio.mpeg"), new ContentDescriptor("video.mpeg"),
				new ContentDescriptor("audio.mpeg"), }, new Format[] {},
				javax.media.PlugInManager.DEMULTIPLEXER));
			result.add(new PlugInInfo("com.sun.media.parser.audio.WavParser",
				new Format[] { new ContentDescriptor("audio.x_wav"), }, new Format[] {},
				javax.media.PlugInManager.DEMULTIPLEXER));
			result.add(new PlugInInfo("com.sun.media.parser.audio.AuParser",
				new Format[] { new ContentDescriptor("audio.basic"), }, new Format[] {},
				javax.media.PlugInManager.DEMULTIPLEXER));
			result.add(new PlugInInfo("com.sun.media.parser.audio.AiffParser",
				new Format[] { new ContentDescriptor("audio.x_aiff"), }, new Format[] {},
				javax.media.PlugInManager.DEMULTIPLEXER));
			result.add(new PlugInInfo("com.sun.media.parser.audio.GsmParser",
				new Format[] { new ContentDescriptor("audio.x_gsm"), }, new Format[] {},
				javax.media.PlugInManager.DEMULTIPLEXER));
		}
		// FMJ overrides to come before SUN ones:
		// this one needs to be after the audio parsers, otherwise it will be used instead of them
		// in some cases.
		// TODO: why does the sun one get an NPE?
		// TODO: this causes audio not to play properly:
		if ((flags & FMJ) != 0) {
			result.add(new PlugInInfo("net.sf.fmj.media.parser.RawPushBufferParser",
				new Format[] { new ContentDescriptor("raw"), }, new Format[] {},
				javax.media.PlugInManager.DEMULTIPLEXER));
			// end FMJ override.
		}
		if ((flags & JMF) != 0) {
			result.add(new PlugInInfo("com.sun.media.parser.RawStreamParser",
				new Format[] { new ContentDescriptor("raw"), }, new Format[] {},
				javax.media.PlugInManager.DEMULTIPLEXER));
			result.add(new PlugInInfo("com.sun.media.parser.RawBufferParser",
				new Format[] { new ContentDescriptor("raw"), }, new Format[] {},
				javax.media.PlugInManager.DEMULTIPLEXER));
			result.add(new PlugInInfo("com.sun.media.parser.RawPullStreamParser",
				new Format[] { new ContentDescriptor("raw"), }, new Format[] {},
				javax.media.PlugInManager.DEMULTIPLEXER));
			result.add(new PlugInInfo("com.sun.media.parser.RawPullBufferParser",
				new Format[] { new ContentDescriptor("raw"), }, new Format[] {},
				javax.media.PlugInManager.DEMULTIPLEXER));
			result.add(new PlugInInfo("com.sun.media.parser.video.QuicktimeParser",
				new Format[] { new ContentDescriptor("video.quicktime"), }, new Format[] {},
				javax.media.PlugInManager.DEMULTIPLEXER));
			result.add(new PlugInInfo("com.sun.media.parser.video.AviParser",
				new Format[] { new ContentDescriptor("video.x_msvideo"), }, new Format[] {},
				javax.media.PlugInManager.DEMULTIPLEXER));

			// PlugInManager.CODEC:
			result
				.add(new PlugInInfo("com.sun.media.codec.audio.mpa.JavaDecoder",
					new Format[] {
						new AudioFormat("mpegaudio", 16000.0, -1, -1, -1, 1, -1, -1.0,
							Format.byteArray),
						new AudioFormat("mpegaudio", 22050.0, -1, -1, -1, 1, -1, -1.0,
							Format.byteArray),
						new AudioFormat("mpegaudio", 24000.0, -1, -1, -1, 1, -1, -1.0,
							Format.byteArray),
						new AudioFormat("mpegaudio", 32000.0, -1, -1, -1, 1, -1, -1.0,
							Format.byteArray),
						new AudioFormat("mpegaudio", 44100.0, -1, -1, -1, 1, -1, -1.0,
							Format.byteArray),
						new AudioFormat("mpegaudio", 48000.0, -1, -1, -1, 1, -1, -1.0,
							Format.byteArray), }, new Format[] { new AudioFormat("LINEAR", -1.0,
						-1, -1, -1, -1, -1, -1.0, Format.byteArray), },
					javax.media.PlugInManager.CODEC));
			result.add(new PlugInInfo("com.sun.media.codec.video.cinepak.JavaDecoder",
				new Format[] { new VideoFormat("cvid", null, -1, Format.byteArray, -1.0f), },
				new Format[] { new RGBFormat(null, -1, Format.intArray, -1.0f, 32, 0xff, 0xff00,
					0xff0000, 1, -1, 0, -1), }, javax.media.PlugInManager.CODEC));
			result.add(new PlugInInfo("com.ibm.media.codec.video.h263.JavaDecoder", new Format[] {
				new VideoFormat("h263", null, -1, Format.byteArray, -1.0f),
				new VideoFormat("h263/rtp", null, -1, Format.byteArray, -1.0f), },
				new Format[] { new RGBFormat(null, -1, null, -1.0f, -1, 0xffffffff, 0xffffffff,
					0xffffffff, -1, -1, -1, -1), }, javax.media.PlugInManager.CODEC));
			result.add(new PlugInInfo("com.sun.media.codec.video.colorspace.JavaRGBConverter",
				new Format[] { new RGBFormat(null, -1, null, -1.0f, -1, 0xffffffff, 0xffffffff,
					0xffffffff, -1, -1, -1, -1), }, new Format[] { new RGBFormat(null, -1, null,
					-1.0f, -1, 0xffffffff, 0xffffffff, 0xffffffff, -1, -1, -1, -1), },
				javax.media.PlugInManager.CODEC));
			result.add(new PlugInInfo("com.sun.media.codec.video.colorspace.JavaRGBToYUV",
				new Format[] {
					new RGBFormat(null, -1, Format.byteArray, -1.0f, 24, 0xffffffff, 0xffffffff,
						0xffffffff, -1, -1, -1, -1),
					new RGBFormat(null, -1, Format.intArray, -1.0f, 32, 0xff0000, 0xff00, 0xff, 1,
						-1, -1, -1),
					new RGBFormat(null, -1, Format.intArray, -1.0f, 32, 0xff, 0xff00, 0xff0000, 1,
						-1, -1, -1), }, new Format[] { new YUVFormat(null, -1, Format.byteArray,
					-1.0f, 2, -1, -1, -1, -1, -1), }, javax.media.PlugInManager.CODEC));
			result.add(new PlugInInfo("com.ibm.media.codec.audio.PCMToPCM", new Format[] {
				new AudioFormat("LINEAR", -1.0, 16, 1, -1, -1, -1, -1.0, Format.byteArray),
				new AudioFormat("LINEAR", -1.0, 16, 2, -1, -1, -1, -1.0, Format.byteArray),
				new AudioFormat("LINEAR", -1.0, 8, 1, -1, -1, -1, -1.0, Format.byteArray),
				new AudioFormat("LINEAR", -1.0, 8, 2, -1, -1, -1, -1.0, Format.byteArray), },
				new Format[] { new AudioFormat("LINEAR", -1.0, -1, -1, -1, -1, -1, -1.0,
					Format.byteArray), }, javax.media.PlugInManager.CODEC));
			// this one appears to be designed to convert to RTP-friendly audio formats?
			result.add(new PlugInInfo("com.ibm.media.codec.audio.rc.RCModule", new Format[] {
				new AudioFormat("LINEAR", -1.0, 16, 2, -1, -1, -1, -1.0, Format.byteArray),
				new AudioFormat("LINEAR", -1.0, 16, 1, -1, -1, -1, -1.0, Format.byteArray),
				new AudioFormat("LINEAR", -1.0, 8, 2, -1, -1, -1, -1.0, Format.byteArray),
				new AudioFormat("LINEAR", -1.0, 8, 1, -1, -1, -1, -1.0, Format.byteArray), },
				new Format[] {
					new AudioFormat("LINEAR", 8000.0, 16, 2, 0, 1, -1, -1.0, Format.byteArray),
					new AudioFormat("LINEAR", 8000.0, 16, 1, 0, 1, -1, -1.0, Format.byteArray), },
				javax.media.PlugInManager.CODEC));
			result.add(new PlugInInfo("com.sun.media.codec.audio.rc.RateCvrt",
				new Format[] { new AudioFormat("LINEAR", -1.0, -1, -1, -1, -1, -1, -1.0,
					Format.byteArray), }, new Format[] { new AudioFormat("LINEAR", -1.0, -1, -1,
					-1, -1, -1, -1.0, Format.byteArray), }, javax.media.PlugInManager.CODEC));
			result.add(new PlugInInfo("com.sun.media.codec.audio.msadpcm.JavaDecoder",
				new Format[] { new AudioFormat("msadpcm", -1.0, -1, -1, -1, -1, -1, -1.0,
					Format.byteArray), }, new Format[] { new AudioFormat("LINEAR", -1.0, -1, -1,
					-1, -1, -1, -1.0, Format.byteArray), }, javax.media.PlugInManager.CODEC));
			result.add(new PlugInInfo("com.ibm.media.codec.audio.ulaw.JavaDecoder",
				new Format[] { new AudioFormat("ULAW", -1.0, -1, -1, -1, -1, -1, -1.0,
					Format.byteArray), }, new Format[] { new AudioFormat("LINEAR", -1.0, -1, -1,
					-1, -1, -1, -1.0, Format.byteArray), }, javax.media.PlugInManager.CODEC));
			result.add(new PlugInInfo("com.ibm.media.codec.audio.alaw.JavaDecoder",
				new Format[] { new AudioFormat("alaw", -1.0, -1, -1, -1, -1, -1, -1.0,
					Format.byteArray), }, new Format[] { new AudioFormat("LINEAR", -1.0, -1, -1,
					-1, -1, -1, -1.0, Format.byteArray), }, javax.media.PlugInManager.CODEC));
			result.add(new PlugInInfo("com.ibm.media.codec.audio.dvi.JavaDecoder",
				new Format[] { new AudioFormat("dvi/rtp", -1.0, -1, -1, -1, -1, -1, -1.0,
					Format.byteArray), }, new Format[] { new AudioFormat("LINEAR", -1.0, -1, -1,
					-1, -1, -1, -1.0, Format.byteArray), }, javax.media.PlugInManager.CODEC));
			result.add(new PlugInInfo("com.ibm.media.codec.audio.g723.JavaDecoder", new Format[] {
				new AudioFormat("g723", -1.0, -1, -1, -1, -1, -1, -1.0, Format.byteArray),
				new AudioFormat("g723/rtp", -1.0, -1, -1, -1, -1, -1, -1.0, Format.byteArray), },
				new Format[] { new AudioFormat("LINEAR", -1.0, -1, -1, -1, -1, -1, -1.0,
					Format.byteArray), }, javax.media.PlugInManager.CODEC));
			result.add(new PlugInInfo("com.ibm.media.codec.audio.gsm.JavaDecoder", new Format[] {
				new AudioFormat("gsm", -1.0, -1, -1, -1, -1, -1, -1.0, Format.byteArray),
				new AudioFormat("gsm/rtp", -1.0, -1, -1, -1, -1, -1, -1.0, Format.byteArray), },
				new Format[] { new AudioFormat("LINEAR", -1.0, -1, -1, -1, -1, -1, -1.0,
					Format.byteArray), }, javax.media.PlugInManager.CODEC));
			result.add(new PlugInInfo("com.ibm.media.codec.audio.gsm.JavaDecoder_ms",
				new Format[] { new AudioFormat("gsm/ms", -1.0, -1, -1, -1, -1, -1, -1.0,
					Format.byteArray), }, new Format[] { new AudioFormat("LINEAR", -1.0, -1, -1,
					-1, -1, -1, -1.0, Format.byteArray), }, javax.media.PlugInManager.CODEC));
			result.add(new PlugInInfo("com.ibm.media.codec.audio.ima4.JavaDecoder",
				new Format[] { new AudioFormat("ima4", -1.0, -1, -1, -1, -1, -1, -1.0,
					Format.byteArray), }, new Format[] { new AudioFormat("LINEAR", -1.0, -1, -1,
					-1, -1, -1, -1.0, Format.byteArray), }, javax.media.PlugInManager.CODEC));
			result.add(new PlugInInfo("com.ibm.media.codec.audio.ima4.JavaDecoder_ms",
				new Format[] { new AudioFormat("ima4/ms", -1.0, -1, -1, -1, -1, -1, -1.0,
					Format.byteArray), }, new Format[] { new AudioFormat("LINEAR", -1.0, -1, -1,
					-1, -1, -1, -1.0, Format.byteArray), }, javax.media.PlugInManager.CODEC));
			result.add(new PlugInInfo("com.ibm.media.codec.audio.ulaw.JavaEncoder", new Format[] {
				new AudioFormat("LINEAR", -1.0, 16, 1, -1, -1, -1, -1.0, Format.byteArray),
				new AudioFormat("LINEAR", -1.0, 16, 2, -1, -1, -1, -1.0, Format.byteArray),
				new AudioFormat("LINEAR", -1.0, 8, 1, -1, -1, -1, -1.0, Format.byteArray),
				new AudioFormat("LINEAR", -1.0, 8, 2, -1, -1, -1, -1.0, Format.byteArray), },
				new Format[] { new AudioFormat("ULAW", 8000.0, 8, 1, -1, -1, -1, -1.0,
					Format.byteArray), }, javax.media.PlugInManager.CODEC));
			result.add(new PlugInInfo("com.ibm.media.codec.audio.dvi.JavaEncoder",
				new Format[] { new AudioFormat("LINEAR", -1.0, 16, 1, 0, 1, -1, -1.0,
					Format.byteArray), }, new Format[] { new AudioFormat("dvi/rtp", -1.0, 4, 1, -1,
					-1, -1, -1.0, Format.byteArray), }, javax.media.PlugInManager.CODEC));
			result.add(new PlugInInfo("com.ibm.media.codec.audio.gsm.JavaEncoder",
				new Format[] { new AudioFormat("LINEAR", -1.0, 16, 1, 0, 1, -1, -1.0,
					Format.byteArray), }, new Format[] { new AudioFormat("gsm", -1.0, -1, -1, -1,
					-1, -1, -1.0, Format.byteArray), }, javax.media.PlugInManager.CODEC));
			result.add(new PlugInInfo("com.ibm.media.codec.audio.gsm.JavaEncoder_ms",
				new Format[] { new AudioFormat("LINEAR", -1.0, 16, 1, 0, 1, -1, -1.0,
					Format.byteArray), }, new Format[] { new com.sun.media.format.WavAudioFormat(
					"gsm/ms", -1.0, -1, -1, -1, -1, -1, -1, -1.0f, Format.byteArray, null), },
				javax.media.PlugInManager.CODEC));
			result.add(new PlugInInfo("com.ibm.media.codec.audio.ima4.JavaEncoder",
				new Format[] { new AudioFormat("LINEAR", -1.0, 16, -1, 0, 1, -1, -1.0,
					Format.byteArray), }, new Format[] { new AudioFormat("ima4", -1.0, -1, -1, -1,
					-1, -1, -1.0, Format.byteArray), }, javax.media.PlugInManager.CODEC));
			result.add(new PlugInInfo("com.ibm.media.codec.audio.ima4.JavaEncoder_ms",
				new Format[] { new AudioFormat("LINEAR", -1.0, 16, -1, 0, 1, -1, -1.0,
					Format.byteArray), }, new Format[] { new com.sun.media.format.WavAudioFormat(
					"ima4/ms", -1.0, -1, -1, -1, -1, -1, -1, -1.0f, Format.byteArray, null), },
				javax.media.PlugInManager.CODEC));
			result.add(new PlugInInfo("com.sun.media.codec.audio.ulaw.Packetizer",
				new Format[] { new AudioFormat("ULAW", -1.0, 8, 1, -1, -1, 8, -1.0,
					Format.byteArray), }, new Format[] { new AudioFormat("ULAW/rtp", -1.0, 8, 1,
					-1, -1, 8, -1.0, Format.byteArray), }, javax.media.PlugInManager.CODEC));
			result.add(new PlugInInfo("com.sun.media.codec.audio.ulaw.DePacketizer",
				new Format[] { new AudioFormat("ULAW/rtp", -1.0, -1, -1, -1, -1, -1, -1.0,
					Format.byteArray), }, new Format[] { new AudioFormat("ULAW", -1.0, -1, -1, -1,
					-1, -1, -1.0, Format.byteArray), }, javax.media.PlugInManager.CODEC));
			result
				.add(new PlugInInfo("com.sun.media.codec.audio.mpa.Packetizer",
					new Format[] {
						new AudioFormat("mpeglayer3", 16000.0, -1, -1, -1, 1, -1, -1.0,
							Format.byteArray),
						new AudioFormat("mpeglayer3", 22050.0, -1, -1, -1, 1, -1, -1.0,
							Format.byteArray),
						new AudioFormat("mpeglayer3", 24000.0, -1, -1, -1, 1, -1, -1.0,
							Format.byteArray),
						new AudioFormat("mpeglayer3", 32000.0, -1, -1, -1, 1, -1, -1.0,
							Format.byteArray),
						new AudioFormat("mpeglayer3", 44100.0, -1, -1, -1, 1, -1, -1.0,
							Format.byteArray),
						new AudioFormat("mpeglayer3", 48000.0, -1, -1, -1, 1, -1, -1.0,
							Format.byteArray),
						new AudioFormat("mpegaudio", 16000.0, -1, -1, -1, 1, -1, -1.0,
							Format.byteArray),
						new AudioFormat("mpegaudio", 22050.0, -1, -1, -1, 1, -1, -1.0,
							Format.byteArray),
						new AudioFormat("mpegaudio", 24000.0, -1, -1, -1, 1, -1, -1.0,
							Format.byteArray),
						new AudioFormat("mpegaudio", 32000.0, -1, -1, -1, 1, -1, -1.0,
							Format.byteArray),
						new AudioFormat("mpegaudio", 44100.0, -1, -1, -1, 1, -1, -1.0,
							Format.byteArray),
						new AudioFormat("mpegaudio", 48000.0, -1, -1, -1, 1, -1, -1.0,
							Format.byteArray), }, new Format[] { new AudioFormat("mpegaudio/rtp",
						-1.0, -1, -1, -1, -1, -1, -1.0, Format.byteArray), },
					javax.media.PlugInManager.CODEC));
			result
				.add(new PlugInInfo("com.sun.media.codec.audio.mpa.DePacketizer",
					new Format[] { new AudioFormat("mpegaudio/rtp", -1.0, -1, -1, -1, -1, -1, -1.0,
						Format.byteArray), }, new Format[] {
						new AudioFormat("mpegaudio", 44100.0, 16, -1, 1, 1, -1, -1.0,
							Format.byteArray),
						new AudioFormat("mpegaudio", 48000.0, 16, -1, 1, 1, -1, -1.0,
							Format.byteArray),
						new AudioFormat("mpegaudio", 32000.0, 16, -1, 1, 1, -1, -1.0,
							Format.byteArray),
						new AudioFormat("mpegaudio", 22050.0, 16, -1, 1, 1, -1, -1.0,
							Format.byteArray),
						new AudioFormat("mpegaudio", 24000.0, 16, -1, 1, 1, -1, -1.0,
							Format.byteArray),
						new AudioFormat("mpegaudio", 16000.0, 16, -1, 1, 1, -1, -1.0,
							Format.byteArray),
						new AudioFormat("mpegaudio", 11025.0, 16, -1, 1, 1, -1, -1.0,
							Format.byteArray),
						new AudioFormat("mpegaudio", 12000.0, 16, -1, 1, 1, -1, -1.0,
							Format.byteArray),
						new AudioFormat("mpegaudio", 8000.0, 16, -1, 1, 1, -1, -1.0,
							Format.byteArray),
						new AudioFormat("mpeglayer3", 44100.0, 16, -1, 1, 1, -1, -1.0,
							Format.byteArray),
						new AudioFormat("mpeglayer3", 48000.0, 16, -1, 1, 1, -1, -1.0,
							Format.byteArray),
						new AudioFormat("mpeglayer3", 32000.0, 16, -1, 1, 1, -1, -1.0,
							Format.byteArray),
						new AudioFormat("mpeglayer3", 22050.0, 16, -1, 1, 1, -1, -1.0,
							Format.byteArray),
						new AudioFormat("mpeglayer3", 24000.0, 16, -1, 1, 1, -1, -1.0,
							Format.byteArray),
						new AudioFormat("mpeglayer3", 16000.0, 16, -1, 1, 1, -1, -1.0,
							Format.byteArray),
						new AudioFormat("mpeglayer3", 11025.0, 16, -1, 1, 1, -1, -1.0,
							Format.byteArray),
						new AudioFormat("mpeglayer3", 12000.0, 16, -1, 1, 1, -1, -1.0,
							Format.byteArray),
						new AudioFormat("mpeglayer3", 8000.0, 16, -1, 1, 1, -1, -1.0,
							Format.byteArray), }, javax.media.PlugInManager.CODEC));
			result.add(new PlugInInfo("com.ibm.media.codec.audio.gsm.Packetizer",
				new Format[] { new AudioFormat("gsm", 8000.0, -1, 1, -1, -1, 264, -1.0,
					Format.byteArray), }, new Format[] { new AudioFormat("gsm/rtp", 8000.0, -1, 1,
					-1, -1, 264, -1.0, Format.byteArray), }, javax.media.PlugInManager.CODEC));
			result.add(new PlugInInfo("com.ibm.media.codec.audio.g723.Packetizer",
				new Format[] { new AudioFormat("g723", 8000.0, -1, 1, -1, -1, 192, -1.0,
					Format.byteArray), }, new Format[] { new AudioFormat("g723/rtp", 8000.0, -1, 1,
					-1, -1, 192, -1.0, Format.byteArray), }, javax.media.PlugInManager.CODEC));
		}

		// mgodehardt: disabled this, is added later, we ask the class for input and output formats
		/*
		 * if ((flags & FMJ) != 0) { result.add(new
		 * PlugInInfo("net.sf.fmj.media.codec.video.jpeg.Packetizer", new Format[] { new
		 * JPEGFormat(), }, new Format[] { new VideoFormat("jpeg/rtp", null, -1, Format.byteArray,
		 * -1.0f), }, PlugInManager.CODEC)); }
		 */

		if ((flags & JMF) != 0) {
			result.add(new PlugInInfo("com.sun.media.codec.video.jpeg.Packetizer",
				new Format[] { new JPEGFormat(), // TODO: VideoFormat?
				},
				new Format[] { new VideoFormat("jpeg/rtp", null, -1, Format.byteArray, -1.0f), },
				javax.media.PlugInManager.CODEC));
		}
		if ((flags & FMJ) != 0) {
			result.add(new PlugInInfo("net.sf.fmj.media.codec.video.jpeg.DePacketizer",
				new Format[] { new VideoFormat("jpeg/rtp", null, -1, Format.byteArray, -1.0f), },
				new Format[] { new JPEGFormat(), }, javax.media.PlugInManager.CODEC));
		}
		if ((flags & JMF) != 0) {
			result.add(new PlugInInfo("com.sun.media.codec.video.jpeg.DePacketizer",
				new Format[] { new VideoFormat("jpeg/rtp", null, -1, Format.byteArray, -1.0f), },
				new Format[] { new JPEGFormat(), // TODO: VideoFormat?
				}, javax.media.PlugInManager.CODEC));
			result.add(new PlugInInfo("com.sun.media.codec.video.mpeg.Packetizer",
				new Format[] { new VideoFormat("mpeg", null, -1, Format.byteArray, -1.0f), },
				new Format[] { new VideoFormat("mpeg/rtp", null, -1, Format.byteArray, -1.0f), },
				javax.media.PlugInManager.CODEC));
			result.add(new PlugInInfo("com.sun.media.codec.video.mpeg.DePacketizer",
				new Format[] { new VideoFormat("mpeg/rtp", null, -1, Format.byteArray, -1.0f), },
				new Format[] { new VideoFormat("mpeg", null, -1, Format.byteArray, -1.0f), },
				javax.media.PlugInManager.CODEC));
		}
		// PlugInManager.EFFECT:

		// PlugInManager.RENDERER:
		if ((flags & JMF) != 0) {
			result.add(new PlugInInfo("com.sun.media.renderer.audio.JavaSoundRenderer",
				new Format[] {
					new AudioFormat("LINEAR", -1.0, -1, -1, -1, -1, -1, -1.0, Format.byteArray),
					new AudioFormat("ULAW", -1.0, -1, -1, -1, -1, -1, -1.0, Format.byteArray), },
				new Format[] {}, javax.media.PlugInManager.RENDERER));
			result.add(new PlugInInfo("com.sun.media.renderer.audio.SunAudioRenderer",
				new Format[] { new AudioFormat("ULAW", 8000.0, 8, 1, -1, -1, -1, -1.0,
					Format.byteArray), }, new Format[] {}, javax.media.PlugInManager.RENDERER));
			result.add(new PlugInInfo("com.sun.media.renderer.video.AWTRenderer", new Format[] {
				new RGBFormat(null, -1, Format.intArray, -1.0f, 32, 0xff0000, 0xff00, 0xff, 1, -1,
					0, -1),
				new RGBFormat(null, -1, Format.intArray, -1.0f, 32, 0xff, 0xff00, 0xff0000, 1, -1,
					0, -1), }, new Format[] {}, javax.media.PlugInManager.RENDERER));
			result.add(new PlugInInfo("com.sun.media.renderer.video.LightWeightRenderer",
				new Format[] {
					new RGBFormat(null, -1, Format.intArray, -1.0f, 32, 0xff0000, 0xff00, 0xff, 1,
						-1, 0, -1),
					new RGBFormat(null, -1, Format.intArray, -1.0f, 32, 0xff, 0xff00, 0xff0000, 1,
						-1, 0, -1), }, new Format[] {}, javax.media.PlugInManager.RENDERER));

			result.add(new PlugInInfo("com.sun.media.renderer.video.JPEGRenderer",
				new Format[] { new JPEGFormat(), }, new Format[] {},
				javax.media.PlugInManager.RENDERER));

			// PlugInManager.MULTIPLEXER:
			result.add(new PlugInInfo("com.sun.media.multiplexer.RawBufferMux", new Format[] {},
				new Format[] { new ContentDescriptor("raw"), },
				javax.media.PlugInManager.MULTIPLEXER));
			result.add(new PlugInInfo(
				// cmeng - not such multiplexer
				"com.sun.media.multiplexer.RawSyncBufferMux", new Format[] {},
				new Format[] { new ContentDescriptor("raw"), },
				javax.media.PlugInManager.MULTIPLEXER));
			result.add(new PlugInInfo("com.sun.media.multiplexer.RTPSyncBufferMux",
				new Format[] {}, new Format[] { new ContentDescriptor("raw.rtp"), },
				javax.media.PlugInManager.MULTIPLEXER));
			result.add(new PlugInInfo("com.sun.media.multiplexer.audio.GSMMux", new Format[] {},
				new Format[] { new FileTypeDescriptor("audio.x_gsm"), },
				javax.media.PlugInManager.MULTIPLEXER));
			result.add(new PlugInInfo("com.sun.media.multiplexer.audio.MPEGMux", new Format[] {},
				new Format[] { new FileTypeDescriptor("audio.mpeg"), },
				javax.media.PlugInManager.MULTIPLEXER));
			result.add(new PlugInInfo("com.sun.media.multiplexer.audio.WAVMux", new Format[] {},
				new Format[] { new FileTypeDescriptor("audio.x_wav"), },
				javax.media.PlugInManager.MULTIPLEXER));
			result.add(new PlugInInfo("com.sun.media.multiplexer.audio.AIFFMux", new Format[] {},
				new Format[] { new FileTypeDescriptor("audio.x_aiff"), },
				javax.media.PlugInManager.MULTIPLEXER));
			result.add(new PlugInInfo("com.sun.media.multiplexer.audio.AUMux", new Format[] {},
				new Format[] { new FileTypeDescriptor("audio.basic"), },
				javax.media.PlugInManager.MULTIPLEXER));
			result.add(new PlugInInfo("com.sun.media.multiplexer.video.AVIMux", new Format[] {},
				new Format[] { new FileTypeDescriptor("video.x_msvideo"), },
				javax.media.PlugInManager.MULTIPLEXER));
			result.add(new PlugInInfo("com.sun.media.multiplexer.video.QuicktimeMux",
				new Format[] {}, new Format[] { new FileTypeDescriptor("video.quicktime"), },
				javax.media.PlugInManager.MULTIPLEXER));
		}

		if ((flags & FMJ) != 0) {
			// ### cmeng - disable code / renderer not applicable to Android
			// ### result.add("net.sf.fmj.media.codec.video.jpeg.Packetizer");

			// ### result.add("net.sf.fmj.media.renderer.video.SimpleSwingRenderer");
			result.add("net.sf.fmj.media.renderer.video.SimpleAWTRenderer");
			result.add("net.sf.fmj.media.renderer.video.Java2dRenderer");
			// ### result.add("net.sf.fmj.media.renderer.video.JPEGRTPRenderer");
			// ### result.add("net.sf.fmj.media.renderer.video.JPEGRenderer");

			result.add("net.sf.fmj.media.parser.JavaSoundParser");
			result.add("net.sf.fmj.media.codec.JavaSoundCodec");
			result.add("net.sf.fmj.media.renderer.audio.JavaSoundRenderer");

			result.add("net.sf.fmj.media.codec.audio.gsm.Decoder");
			result.add("net.sf.fmj.media.codec.audio.gsm.Encoder");
			result.add("net.sf.fmj.media.codec.audio.gsm.DePacketizer");
			result.add("net.sf.fmj.media.codec.audio.gsm.Packetizer");
			result.add("net.sf.fmj.media.multiplexer.audio.GsmMux");
			result.add("net.sf.fmj.media.parser.GsmParser");

			result.add("net.sf.fmj.media.codec.audio.ulaw.Decoder");
			result.add("net.sf.fmj.media.codec.audio.ulaw.Encoder");
			result.add("net.sf.fmj.media.codec.audio.ulaw.DePacketizer");
			result.add("net.sf.fmj.media.codec.audio.ulaw.Packetizer");
			result.add("net.sf.fmj.media.codec.audio.RateConverter");

			result.add("net.sf.fmj.media.codec.audio.alaw.Decoder");
			result.add("net.sf.fmj.media.codec.audio.alaw.Encoder");
			result.add("net.sf.fmj.media.codec.audio.alaw.DePacketizer");
			result.add("net.sf.fmj.media.codec.audio.alaw.Packetizer");
			result.add("net.sf.fmj.media.codec.video.jpeg.JpegEncoder");
			// ### result.add("net.sf.fmj.media.codec.video.lossless.GIFEncoder");
			// ### result.add("net.sf.fmj.media.codec.video.lossless.GIFDecoder");
			// ### result.add("net.sf.fmj.media.codec.video.lossless.PNGEncoder");
			// ### result.add("net.sf.fmj.media.codec.video.lossless.PNGDecoder");

			result.add("net.sf.fmj.media.parser.RawPushBufferParser");
			// result.add("net.sf.fmj.media.parser.RawPullStreamParser");
			// TODO: test and add. Does it conflict with RawPushBufferParser?
			result.add("net.sf.fmj.media.multiplexer.RTPSyncBufferMux");
			result.add("net.sf.fmj.media.multiplexer.RawBufferMux");
			// ### result.add("net.sf.fmj.media.multiplexer.audio.AIFFMux");
			result.add("net.sf.fmj.media.multiplexer.audio.AUMux");
			// result.add("net.sf.fmj.media.multiplexer.audio.JavaSoundAUMux");
			// // not needed, AUMux works fine.
			// ### result.add("net.sf.fmj.media.multiplexer.audio.WAVMux");

			// TODO: the filter graph builder can't quite deal with this one yet:
			// result.add("net.sf.fmj.media.codec.video.ImageScaler");

			if (!DISABLE_OGG) {
				// ### result.add("net.sf.fmj.theora_java.JavaOggParser");
			}

			// if (OSUtils.isMacOSX() || OSUtils.isWindows())
			// {
			// result.add("net.sf.fmj.qt.QTParser");
			// }

			result.add("net.sf.fmj.media.parser.MultipartMixedReplaceParser");
			result.add("net.sf.fmj.media.multiplexer.MultipartMixedReplaceMux");

			result.add("net.sf.fmj.media.parser.XmlMovieParser");
			result.add("net.sf.fmj.media.multiplexer.XmlMovieMux");
			result.add("net.sf.fmj.media.multiplexer.audio.CsvAudioMux");

			// SIP communicator packetizers/depacketizers.
			// ### result.add("net.java.sip.communicator.impl.media.codec.audio.speex.JavaEncoder");
			// ### result.add("net.java.sip.communicator.impl.media.codec.audio.speex.JavaDecoder");
			// ### result.add("net.java.sip.communicator.impl.media.codec.audio.ilbc.JavaEncoder");
			// ### result.add("net.java.sip.communicator.impl.media.codec.audio.ilbc.JavaDecoder");

			// t4l jpeg encoder/decoder:
			result.add("com.t4l.jmf.JPEGDecoder");
			// result.add("com.t4l.jmf.JPEGEncoder"); // TODO: FMJ has a JPEG
			// encoder above, need to merge and get best of both worlds
		}

		if ((flags & FMJ_NATIVE) != 0) {
			// ffmpeg-java parser: may not be in classpath
			// #### result.add("net.sf.fmj.ffmpeg_java.FFMPEGParser");
			// #### result.add("net.sf.fmj.theora_java.NativeOggParser");
		}

		if ((flags & THIRD_PARTY) != 0) {
			// JFFMPEG: may not be in classpath
			// JFFMPEG is not needed for ogg playback, because JavaSound with an
			// spi can handle
			// ogg audio files. net.sourceforge.jffmpeg.demux.ogg.OggDemux does
			// not appear to split
			// out the video stream, so if this demux gets used instead of
			// net.sf.fmj.theora_java.OGGParser,
			// audio will play but no video.
			// result.add("net.sourceforge.jffmpeg.demux.ogg.OggDemux");
			// result.add("net.sourceforge.jffmpeg.AudioDecoder");

			// result.add("net.sourceforge.jffmpeg.demux.avi.AviDemux");
			// result.add("net.sourceforge.jffmpeg.VideoDecoder");
			// result.add("net.sourceforge.jffmpeg.AudioDecoder");
			// PlugInManager.removePlugIn("com.sun.media.parser.video.AviParser",
			// PlugInManager.DEMULTIPLEXER);

			// FOBS4JMF: may not be in classpath
			// #### result.add("com.omnividea.media.parser.video.Parser");
			// #### result.add("com.omnividea.media.codec.video.NativeDecoder");
			// #### result.add("com.omnividea.media.codec.audio.NativeDecoder");
			// #### result.add("com.omnividea.media.codec.video.JavaDecoder");
			// protocol: com.omnividea - also added in JmfRegistry
		}

		if ((flags & FMJ) != 0 && (flags & JMF) != 0) {
			// TODO: verify that mp3s play with JMF installed.
			// // remove the audio/mpeg from ibm's: - this can result in a demux
			// with no renderer.
			// if
			// (PlugInManager.removePlugIn("com.ibm.media.parser.video.MpegParser",
			// PlugInManager.DEMULTIPLEXER))
			// {
			result.add(new PlugInInfo("com.ibm.media.parser.video.MpegParser", new Format[] {
			// new ContentDescriptor("audio.mpeg"),
			new ContentDescriptor("video.mpeg"),
			// new ContentDescriptor("audio.mpeg"),
				}, new Format[] {}, javax.media.PlugInManager.DEMULTIPLEXER));
			// }
		}
		return result;
	}

	public static List<String> protocolPrefixList(int flags)
	{
		final List<String> protocolPrefixList = new ArrayList<>();

		if ((flags & JMF) != 0) {
			protocolPrefixList.add("javax");
			protocolPrefixList.add("com.sun");
			protocolPrefixList.add("com.ibm");
		}
		if ((flags & FMJ_NATIVE) != 0) {
			if (OSUtils.isMacOSX() || OSUtils.isWindows()) {
				// Quicktime:
				protocolPrefixList.add("net.sf.fmj.qt");
			}
			if (OSUtils.isWindows()) {
				// DirectShow:
				protocolPrefixList.add("net.sf.fmj.ds");
			}
			if (ENABLE_GSTREAMER && OSUtils.isLinux()) // TODO: we could add
														// these for other OS's,
														// as gstreamer is
														// cross-platform.
			{
				// GStreamer:
				protocolPrefixList.add("net.sf.fmj.gst");
			}
		}
		if ((flags & FMJ) != 0) {
			protocolPrefixList.add("net.sf.fmj");
		}
		if ((flags & THIRD_PARTY) != 0) {
			protocolPrefixList.add("com.omnividea"); // FOBS4JMF: may not be in
														// classpath
		}
		return protocolPrefixList;
	}

	public static void registerAll(int flags)
	{
		registerProtocolPrefixList(flags);
		registerContentPrefixList(flags);
		registerPlugins(flags);
	}

	public static void registerContentPrefixList(int flags)
	{
		if (flags == NONE)
			return;

		final Vector v = PackageManager.getContentPrefixList();
		final List<String> add = contentPrefixList(flags);
		for (String s : add) {
			if (!v.contains(s))
				v.add(s);
		}
		PackageManager.setContentPrefixList(v);
	}

	public static void registerPlugins(int flags)
	{
		if (flags == NONE)
			return;

		final List<Object> list = plugInList(flags);
		for (Object o : list) {
			if (o instanceof PlugInInfo) {
				final PlugInInfo i = (PlugInInfo) o;
				PlugInManager.addPlugIn(i.className, i.in, i.out, i.type);
			}
			else {
				PlugInUtility.registerPlugIn((String) o);
			}
		}
	}

	public static void registerProtocolPrefixList(int flags)
	{
		if (flags == NONE)
			return;

		final Vector v = PackageManager.getProtocolPrefixList();
		final List<String> add = protocolPrefixList(flags);
		for (String s : add) {
			if (!v.contains(s))
				v.add(s);
		}

		PackageManager.setProtocolPrefixList(v);

	}

	private static List<String> removePluginsFromList(int flags, List v)
	{
		final List<String> result = new ArrayList<>();
		for (Object o : v) {
			final String className = (String) o;

			boolean remove = false;
			if ((flags & JMF) != 0) {
				if (className.startsWith("com.ibm.") || className.startsWith("com.sun.")
					|| className.startsWith("javax.media."))
					remove = true;
			}
			if ((flags & FMJ) != 0) {
				if (className.startsWith("net.sf.fmj")
					|| className.startsWith("net.java.sip.communicator.impl.media.")
					|| className.startsWith("com.t4l.jmf")) {
					if (className.equals("net.sf.fmj.ffmpeg_java.FFMPEGParser")
						|| className.equals("net.sf.fmj.theora_java.NativeOggParser")) { // don't
																							// remove
					}
					else {
						remove = true;
					}
				}
			}
			if ((flags & FMJ_NATIVE) != 0) {
				if (className.equals("net.sf.fmj.ffmpeg_java.FFMPEGParser")
					|| className.equals("net.sf.fmj.theora_java.NativeOggParser"))
					remove = true;
			}
			if ((flags & THIRD_PARTY) != 0) {
				if (className.startsWith("com.omnividea.media."))
					remove = true;
			}
			if (remove) {
				result.add(className);
			}
		}
		return result;
	}

	public static final void setDefaultFlags(int flags)
	{
		defaultFlags = flags;
	}

	public static void unRegisterAll(int flags)
	{
		unRegisterProtocolPrefixList(flags);
		unRegisterContentPrefixList(flags);
		unRegisterPlugins(flags);
	}

	public static void unRegisterContentPrefixList(int flags)
	{
		if (flags == NONE)
			return;

		final Vector v = PackageManager.getContentPrefixList();
		final List<String> add = contentPrefixList(flags);
		for (String s : add) {
			if (v.contains(s))
				v.remove(s);
		}
		PackageManager.setContentPrefixList(v);
	}

	public static void unRegisterPlugins(int flags)
	{
		if (flags == NONE)
			return;

		final int[] types = new int[] { javax.media.PlugInManager.DEMULTIPLEXER,
			javax.media.PlugInManager.CODEC, javax.media.PlugInManager.EFFECT,
			javax.media.PlugInManager.RENDERER, javax.media.PlugInManager.MULTIPLEXER };

		// remove first from FMJ's registry instance. This is where
		// FMJ's PlugInManager gets its values. This will be more
		// efficient if PlugInManager is not yet initialized.
		final Registry registry = Registry.getInstance();

		for (int type : types) {
			final List<String> v = registry.getPluginList(type);
			final List<String> vRemove = removePluginsFromList(flags, v);
			v.removeAll(vRemove);
			registry.setPluginList(type, v);
		}

		// This handles the case where PlugInManager is already initialized,
		// or if we are using JMF's PlugInManager.
		for (int type : types) {
			final Vector v = PlugInManager.getPlugInList(null, null, type);
			final List<String> vRemove = removePluginsFromList(flags, v);

			for (String className : vRemove)
				PlugInManager.removePlugIn(className, type);
		}

	}

	public static void unRegisterProtocolPrefixList(int flags)
	{
		if (flags == NONE)
			return;

		final Vector v = PackageManager.getProtocolPrefixList();
		final List<String> add = protocolPrefixList(flags);
		for (String s : add) {
			if (v.contains(s))
				v.remove(s);
		}
		PackageManager.setProtocolPrefixList(v);
	}

}
