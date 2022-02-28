/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.service.neomedia;

/**
 * The <code>MediaUseCase</code> enumeration contains a list of use-cases for media related. Typically
 * it can be used to differentiate a video call (video comes from webcam) and desktop session (video
 * comes from desktop).
 *
 * @author Sebastien Vincent
 */
public enum MediaUseCase {
	/**
	 * Represents any usecase.
	 */
	ANY("any"),

	/**
	 * Represents a standard call (voice/video).
	 */
	CALL("call"),

	/**
	 * Represents a desktop streaming/sharing session.
	 */
	DESKTOP("desktop");

	/**
	 * Name of this <code>MediaUseCase</code>.
	 */
	private final String mediaUseCase;

	/**
	 * Constructor.
	 *
	 * @param mediaUseCase
	 *        type of <code>MediaUseCase</code> we'd like to create
	 */
	private MediaUseCase(String mediaUseCase)
	{
		this.mediaUseCase = mediaUseCase;
	}

	/**
	 * Returns the name of this <code>MediaUseCase</code>.
	 *
	 * @return the name of this <code>MediaUseCase</code>.
	 */
	@Override
	public String toString()
	{
		return mediaUseCase;
	}

	/**
	 * Returns a <code>MediaUseCase</code> value corresponding to the specified <code>mediaUseCase</code>.
	 *
	 * @param mediaUseCase
	 *        the name that we'd like to parse.
	 * @return a <code>MediaUseCase</code> value corresponding to the specified <code>mediaUseCase</code>.
	 *
	 * @throws IllegalArgumentException
	 *         in case <code>mediaUseCase</code> is not a valid or currently supported media usecase.
	 */
	public static MediaUseCase parseString(String mediaUseCase)
		throws IllegalArgumentException
	{
		if (CALL.toString().equals(mediaUseCase))
			return CALL;
		if (ANY.toString().equals(mediaUseCase))
			return ANY;
		if (DESKTOP.toString().equals(mediaUseCase))
			return DESKTOP;

		throw new IllegalArgumentException(mediaUseCase
			+ " is not a currently supported MediaUseCase");
	}
}
