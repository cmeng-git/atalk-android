/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.impl.protocol.jabber;

import net.java.sip.communicator.service.protocol.AbstractOperationSetBasicAutoAnswer;
import net.java.sip.communicator.service.protocol.AccountID;
import net.java.sip.communicator.service.protocol.Call;

import org.atalk.android.gui.call.JingleMessageSessionImpl;
import org.atalk.service.neomedia.MediaDirection;
import org.atalk.util.MediaType;
import org.jivesoftware.smackx.jingle.element.Jingle;

import java.util.Map;

import timber.log.Timber;

/**
 * An Operation Set defining option to unconditionally auto answer incoming calls.
 *
 * @author Damian Minkov
 * @author Vincent Lucas
 */
public class OperationSetAutoAnswerJabberImpl extends AbstractOperationSetBasicAutoAnswer
{
    /**
     * Creates this operation set, loads stored values, populating local variable settings.
     *
     * @param protocolProvider the parent Protocol Provider.
     */
    public OperationSetAutoAnswerJabberImpl(ProtocolProviderServiceJabberImpl protocolProvider)
    {
        super(protocolProvider);
        this.load();
    }

    /**
     * Save values to account properties.
     */
    @Override
    protected void save()
    {
        AccountID acc = mPPS.getAccountID();
        Map<String, String> accProps = acc.getAccountProperties();

        // let's clear anything before saving :)
        accProps.put(AUTO_ANSWER_UNCOND_PROP, null);

        if (mAnswerUnconditional)
            accProps.put(AUTO_ANSWER_UNCOND_PROP, Boolean.TRUE.toString());

        accProps.put(AUTO_ANSWER_WITH_VIDEO_PROP, Boolean.toString(this.mAnswerWithVideo));
        acc.setAccountProperties(accProps);
        JabberActivator.getProtocolProviderFactory().storeAccount(acc);
    }

    /**
     * Checks if the call satisfy the auto answer conditions.
     *
     * @param call The new incoming call to auto-answer if needed.
     * @return <code>true</code> if the call satisfy the auto answer conditions. <code>False</code> otherwise.
     */
    @Override
    protected boolean satisfyAutoAnswerConditions(Call call)
    {
        // The jabber implementation does not support advanced auto answer functionality.
        // We only need to check if the specific Call object knows it has to be auto-answered.
        return call.isAutoAnswer();
    }

    /**
     * Auto answer to a call with "audio only" or "audio/video" if the incoming call is a video call.
     *
     * @param call The new incoming call to auto-answer if needed.
     * @param directions The media type (audio / video) stream directions.
     * @param jingleSessionInit Jingle session-initiate is used to check if incoming call is via JingleMessage accept
     * @return <code>true</code> if we have processed and no further processing is needed, <code>false</code> otherwise.
     */
    public boolean autoAnswer(Call call, Map<MediaType, MediaDirection> directions, Jingle jingleSessionInit)
    {
        // 2022/11/29 (v3.0.5): Allow proceed to auto-answer the call if it is already accepted in JingleMessageSessionImpl
        // JingleMessage <propose/> will call via ReceivedCallActivity UI when android is in locked screen;
        // Check aTalkApp.isForeground for incoming alert to continue.
//        answerOnJingleMessageAccept = aTalkApp.isForeground && (jingleSessionInit != null)
//                && JingleMessageSessionImpl.isJingleMessageAccept(jingleSessionInit);
        answerOnJingleMessageAccept = (jingleSessionInit != null)
                && JingleMessageSessionImpl.isJingleMessageAccept(jingleSessionInit);
        Timber.d("OnJingleMessageAccept (auto answer): %s", answerOnJingleMessageAccept);

        boolean isVideoCall = false;
        MediaDirection direction = directions.get(MediaType.VIDEO);
        if (direction != null) {
            isVideoCall = (direction == MediaDirection.SENDRECV);
        }

        return super.autoAnswer(call, isVideoCall);
    }
}
