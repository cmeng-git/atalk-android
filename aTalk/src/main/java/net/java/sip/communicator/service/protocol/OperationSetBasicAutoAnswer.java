/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.protocol;

/**
 * An Operation Set defining option to unconditional auto answer incoming calls.
 *
 * @author Damian Minkov
 * @author Eng Chong Meng
 */
public interface OperationSetBasicAutoAnswer extends OperationSet
{
    /**
     * Auto-answer unconditional account property.
     */
    String AUTO_ANSWER_UNCOND_PROP = "AUTO_ANSWER_UNCONDITIONAL";

    /**
     * Auto answer video calls with video account property.
     */
    String AUTO_ANSWER_WITH_VIDEO_PROP = "AUTO_ANSWER_WITH_VIDEO";

    /**
     * Sets the auto answer option to unconditionally answer all incoming calls.
     */
    void setAutoAnswerUnconditional();

    /**
     * Is the auto answer option set to unconditionally answer all incoming calls.
     *
     * @return is auto answer set to unconditional.
     */
    boolean isAutoAnswerUnconditionalSet();

    /**
     * Clear any previous settings.
     */
    void clear();

    /**
     * Sets the auto answer with video to video calls.
     *
     * @param answerWithVideo A boolean set to true to activate the auto answer with video
     * when receiving a video call. False otherwise.
     */
    void setAutoAnswerWithVideo(boolean answerWithVideo);

    /**
     * Return if the auto answer with video to video calls is enabled.
     *
     * @return A boolean set to true if the auto answer with video when receiving
     * a video call is activated. False otherwise.
     */
    boolean isAutoAnswerWithVideoSet();

}
