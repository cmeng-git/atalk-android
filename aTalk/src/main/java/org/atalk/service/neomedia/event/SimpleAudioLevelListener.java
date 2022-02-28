/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.service.neomedia.event;

/**
 * A very simple listener that delivers <code>int</code> values every time the audio level of an audio
 * source changes.
 *
 * @author Emil Ivov
 */
public interface SimpleAudioLevelListener
{
	/**
	 * The maximum level that can be reported for a participant in a conference. Level values should
	 * be distributed between <code>MAX_LEVEL</code> and {@link #MIN_LEVEL} in a way that would appear
	 * uniform to users.
	 * <p>
	 * <b>Note</b>: The value of <code>127</code> is specifically chosen as the value of
	 * <code>MAX_LEVEL</code> because (1) we transport the levels within RTP and it gives us a signed
	 * <code>byte</code> for it, and (2) the range of
	 * <code>[0, 127]</code> is pretty good to directly express the sound pressure
	 * level decibels as heard by humans in Earth's atmosphere.
	 * </p>
	 */
	public static final int MAX_LEVEL = 127;

	/**
	 * The maximum (zero) level that can be reported for a participant in a conference. Level values
	 * should be distributed among {@link #MAX_LEVEL} and <code>MIN_LEVEL</code> in a way that would
	 * appear uniform to users.
	 * <p>
	 * <b>Note</b>: The value of <code>0</code> is specifically chosen as the value of
	 * <code>MIN_LEVEL</code> because (1) we transport the levels within RTP and it gives us a signed
	 * <code>byte</code> for it, and (2) the range of
	 * <code>[0, 127]</code> is pretty good to directly express the sound pressure
	 * level decibels as heard by humans in Earth's atmosphere.
	 * </p>
	 */
	public static final int MIN_LEVEL = 0;

	/**
	 * Indicates a new audio level for the source that this listener was registered with.
	 * 
	 * @param level
	 *        the new/current level of the audio source that this listener is registered with.
	 */
	public void audioLevelChanged(int level);
}
