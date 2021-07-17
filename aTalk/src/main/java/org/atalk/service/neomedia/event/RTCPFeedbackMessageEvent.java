/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.service.neomedia.event;

import java.util.*;

/**
 * Represents an event coming from RTCP that meant to tell codec to do something (i.e send a
 * keyframe, ...).
 *
 * @author Sebastien Vincent
 */
public class RTCPFeedbackMessageEvent extends EventObject
{
	/**
	 * Serial version UID.
	 */
	private static final long serialVersionUID = 0L;

	/**
	 * Full Intra Request (FIR) RTCP feedback message type.
	 */
	public static final int FMT_FIR = 4;

	/**
	 * Picture Loss Indication (PLI) feedback message type.
	 */
	public static final int FMT_PLI = 1;

	/**
	 * The payload type (PT) of payload-specific RTCP feedback messages.
	 */
    public static final int PT_PS = 206;

	/**
	 * The payload type (PT) of transport layer RTCP feedback messages.
	 */
    public static final int PT_TL = 205;

	/**
	 * Feedback message type (FMT).
	 */
	private final int feedbackMessageType;

	/**
	 * Payload type (PT).
	 */
    private final int payloadType;

	/**
	 * Constructor.
	 *
	 * @param source
	 *        source
	 * @param feedbackMessageType
	 *        feedback message type (FMT)
	 * @param payloadType
	 *        payload type (PT)
	 */
	public RTCPFeedbackMessageEvent(Object source, int feedbackMessageType, int payloadType)
	{
		super(source);

		this.feedbackMessageType = feedbackMessageType;
		this.payloadType = payloadType;
	}

	/**
	 * Get feedback message type (FMT).
	 *
	 * @return message type
	 */
	public int getFeedbackMessageType()
	{
		return feedbackMessageType;
	}

	/**
	 * Get payload type (PT) of RTCP packet.
	 *
	 * @return payload type
	 */
    public int getPayloadType()
	{
		return payloadType;
	}
}
