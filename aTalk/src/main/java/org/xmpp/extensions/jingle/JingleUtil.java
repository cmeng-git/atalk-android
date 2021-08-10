/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.xmpp.extensions.jingle;

import org.jivesoftware.smack.packet.ExtensionElement;
import org.jivesoftware.smack.packet.IQ;
import org.jxmpp.jid.FullJid;
import org.xmpp.extensions.jingle.element.*;

import java.util.List;

import static org.xmpp.extensions.jingle.element.JingleAction.CONTENT_ACCEPT;
import static org.xmpp.extensions.jingle.element.JingleAction.CONTENT_ADD;
import static org.xmpp.extensions.jingle.element.JingleAction.CONTENT_MODIFY;
import static org.xmpp.extensions.jingle.element.JingleAction.CONTENT_REJECT;
import static org.xmpp.extensions.jingle.element.JingleAction.CONTENT_REMOVE;
import static org.xmpp.extensions.jingle.element.JingleAction.DESCRIPTION_INFO;
import static org.xmpp.extensions.jingle.element.JingleAction.SESSION_ACCEPT;
import static org.xmpp.extensions.jingle.element.JingleAction.SESSION_INITIATE;
import static org.xmpp.extensions.jingle.element.JingleAction.SESSION_TERMINATE;
import static org.xmpp.extensions.jingle.element.JingleAction.TRANSPORT_INFO;

/**
 * A utility class containing methods for creating {@link Jingle} instances for various situations.
 *
 * @author Emil Ivov
 * @author Eng Chong Meng
 */
public class JingleUtil
{
    /**
     * Creates a {@link Jingle} <tt>session-info</tt> packet carrying a <tt>ringing</tt> payload.
     *
     * @param sessionInitiate the {@link Jingle} that established the session which the response is going to belong to.
     * @return a {@link Jingle} <tt>session-info</tt> packet carrying a <tt>ringing</tt> payload.
     */
    public static Jingle createRinging(Jingle sessionInitiate)
    {
        return createSessionInfo(sessionInitiate.getTo().asFullJidIfPossible(), sessionInitiate.getInitiator(),
                sessionInitiate.getSid(), SessionInfoType.ringing);
    }

    /**
     * Creates a {@link Jingle} <tt>session-info</tt> packet carrying a the specified payload type.
     *
     * @param from our full jid
     * @param recipient their full jid
     * @param sessionId the ID of the Jingle session this IQ will belong to.
     * @return a {@link Jingle} <tt>session-info</tt> packet carrying a the specified payload type.
     */
    public static Jingle createSessionInfo(FullJid from, FullJid recipient, String sessionId)
    {
        Jingle sessionInfo = new Jingle(JingleAction.SESSION_INFO, sessionId);
        sessionInfo.setFrom(from);
        sessionInfo.setTo(recipient);
        sessionInfo.setType(IQ.Type.set);

        return sessionInfo;
    }

    /**
     * Creates a {@link Jingle} <tt>session-info</tt> packet carrying the specified payload type.
     *
     * @param from our full jid
     * @param recipient their full jid
     * @param sessionId the ID of the Jingle session this IQ will belong to.
     * @param type the exact type (e.g. ringing, hold, mute) of the session info IQ.
     * @return a {@link Jingle} <tt>session-info</tt> packet carrying a the specified payload type.
     */
    public static Jingle createSessionInfo(FullJid from, FullJid recipient, String sessionId, SessionInfoType type)
    {
        Jingle ringing = createSessionInfo(from, recipient, sessionId);
        SessionInfoExtension sessionInfoType = new SessionInfoExtension(type);

        ringing.setSessionInfo(sessionInfoType);
        return ringing;
    }

    /**
     * Creates a {@link Jingle} <tt>session-terminate</tt> packet carrying a {@link Reason#BUSY} payload.
     *
     * @param from our Jid
     * @param recipient the destination Jid
     * @param sessionId the ID of the Jingle session that this message will be terminating.
     * @return a {@link Jingle} <tt>session-terminate</tt> packet.
     */
    public static Jingle createSessionTerminateBusy(FullJid from, FullJid recipient, String sessionId)
    {
        return createSessionTerminate(from, recipient, sessionId, Reason.BUSY, null);
    }

    /**
     * Creates a {@link Jingle} <tt>session-terminate</tt> packet that is meant to terminate an
     * ongoing, established session (similar to a SIP BYE request).
     *
     * @param from our Jid
     * @param recipient the destination Jid
     * @param sessionId the ID of the Jingle session that this message will be terminating.
     * @return a {@link Jingle} <tt>session-terminate</tt> packet .
     */
    public static Jingle createSessionTerminateSuccess(FullJid from, FullJid recipient, String sessionId)
    {
        return createSessionTerminate(from, recipient, sessionId, Reason.SUCCESS, "Nice talking to you!");
    }

    /**
     * Creates a {@link Jingle} <tt>session-terminate</tt> packet that is meant to terminate a not
     * yet established session.
     *
     * @param from our Jid
     * @param recipient the destination Jid
     * @param sessionId the ID of the Jingle session that this message will be terminating.
     * @return a {@link Jingle} <tt>session-terminate</tt> packet .
     */
    public static Jingle createSessionTerminateCancel(FullJid from, FullJid recipient, String sessionId)
    {
        return createSessionTerminate(from, recipient, sessionId, Reason.CANCEL, "Oops!");
    }

    /**
     * Creates a {@link Jingle} <tt>session-terminate</tt> packet with the specified src, dst, sessionId, and reason.
     *
     * @param from our Jid
     * @param recipient the destination Jid
     * @param sessionId the ID of the Jingle session that this message will be terminating.
     * @param reason the reason for the termination
     * @param reasonText a human readable reason for the termination or <tt>null</tt> for none.
     * @return the newly constructed {@link Jingle} <tt>session-terminate</tt> packet. .
     */
    public static Jingle createSessionTerminate(FullJid from, FullJid recipient, String sessionId,
            Reason reason, String reasonText)
    {
        Jingle terminate = new Jingle(SESSION_TERMINATE, sessionId);
        terminate.setTo(recipient);
        terminate.setFrom(from);
        terminate.setType(IQ.Type.set);

        JingleReason reasonPacketExt = new JingleReason(reason, reasonText, null);
        terminate.setReason(reasonPacketExt);
        return terminate;
    }

    /**
     * Creates a {@link Jingle} <tt>session-accept</tt> packet with the specified <tt>from</tt>,
     * <tt>to</tt>, <tt>sessionId</tt>, and <tt>content</tt>. Given our role in a conversation, we would
     * assume that the <tt>from</tt> value should also be used for the value of the Jingle <tt>responder</tt>.
     *
     * @param from our Jid
     * @param sessionInitIQ the received session-initiate Jingle
     * @param contentList the content elements containing media and transport descriptions.
     * @return the newly constructed {@link Jingle} <tt>session-accept</tt> packet.
     */
    public static Jingle createSessionAccept(FullJid from, Jingle sessionInitIQ, Iterable<JingleContent> contentList)
    {
        Jingle sessionAccept = new Jingle(SESSION_ACCEPT, sessionInitIQ.getSid());

        sessionAccept.setTo(sessionInitIQ.getInitiator());
        sessionAccept.setFrom(from);
        sessionAccept.setResponder(from);
        sessionAccept.setType(IQ.Type.set);

        // Just copy to sessionInitIQ GroupExtension element to session-accept
        ExtensionElement groupExtension = sessionInitIQ.getExtension(GroupExtension.QNAME);
        if (groupExtension != null) {
            sessionAccept.addExtension(groupExtension);
        }

        for (JingleContent content : contentList)
            sessionAccept.addContent(content);

        return sessionAccept;
    }

    /**
     * Creates a {@link Jingle} <tt>description-info</tt> packet with the specified <tt>from</tt>,
     * <tt>to</tt>, <tt>sessionId</tt>, and <tt>content</tt>. Given our role in a conversation, we would
     * assume that the <tt>from</tt> value should also be used for the value of the Jingle <tt>responder</tt>.
     *
     * @param from our Jid
     * @param sessionInitIQ the received session-initiate Jingle
     * @param contentList the content elements containing media and transport descriptions.
     * @return the newly constructed {@link Jingle} <tt>description-info</tt> packet.
     */
    public static Jingle createDescriptionInfo(FullJid from, Jingle sessionInitIQ, Iterable<JingleContent> contentList)
    {
        Jingle descriptionInfo = new Jingle(DESCRIPTION_INFO, sessionInitIQ.getSid());

        descriptionInfo.setTo(sessionInitIQ.getInitiator());
        descriptionInfo.setFrom(from);
        descriptionInfo.setResponder(from);
        descriptionInfo.setType(IQ.Type.set);

        for (JingleContent content : contentList)
            descriptionInfo.addContent(content);

        return descriptionInfo;
    }

    /**
     * Creates a {@link Jingle} <tt>transport-info</tt> packet with the specified <tt>from</tt>,
     * <tt>to</tt>, <tt>sessionId</tt>, and <tt>contentList</tt>. Given our role in a conversation, we
     * would assume that the <tt>from</tt> value should also be used for the value of the Jingle <tt>responder</tt>.
     *
     * @param from our Jid
     * @param recipient the destination Jid
     * @param sessionId the ID of the Jingle session that this message will be terminating.
     * @param contentList the content elements containing media transport descriptions.
     * @return the newly constructed {@link Jingle} <tt>transport-info</tt> packet.
     */
    public static Jingle createTransportInfo(FullJid from, FullJid recipient, String sessionId,
            Iterable<JingleContent> contentList)
    {
        Jingle transportInfo = new Jingle(TRANSPORT_INFO, sessionId);

        transportInfo.setTo(recipient);
        transportInfo.setFrom(from);
        transportInfo.setInitiator(from);
        transportInfo.setType(IQ.Type.set);
        for (JingleContent content : contentList)
            transportInfo.addContent(content);
        return transportInfo;
    }

    /**
     * Creates a new {@link Jingle} with the <tt>session-initiate</tt> action.
     *
     * @param from our Jid
     * @param recipient the destination Jid
     * @param sessionId the ID of the Jingle session that this message will be terminating.
     * @param contentList the content elements containing media and transport descriptions.
     * @return the newly constructed {@link Jingle} <tt>session-initiate</tt> packet.
     */
    public static Jingle createSessionInitiate(FullJid from, FullJid recipient, String sessionId,
            List<JingleContent> contentList)
    {
        Jingle sessionInitiate = new Jingle(SESSION_INITIATE, sessionId);

        sessionInitiate.setTo(recipient);
        sessionInitiate.setFrom(from);
        sessionInitiate.setInitiator(from);
        sessionInitiate.setType(IQ.Type.set);

        for (JingleContent content : contentList) {
            sessionInitiate.addContent(content);
        }
        return sessionInitiate;
    }

    /**
     * Creates a new {@link Jingle} with the <tt>content-add</tt> action.
     *
     * @param from our Jid
     * @param recipient the destination Jid
     * @param sessionId the ID of the Jingle session that this message will be terminating.
     * @param contentList the content elements containing media and transport descriptions.
     * @return the newly constructed {@link Jingle} <tt>content-add</tt> packet.
     */
    public static Jingle createContentAdd(FullJid from, FullJid recipient, String sessionId,
            List<JingleContent> contentList)
    {
        Jingle contentAdd = new Jingle(CONTENT_ADD, sessionId);

        contentAdd.setTo(recipient);
        contentAdd.setFrom(from);
        contentAdd.setType(IQ.Type.set);

        for (JingleContent content : contentList)
            contentAdd.addContent(content);
        return contentAdd;
    }

    /**
     * Creates a new {@link Jingle} with the <tt>content-accept</tt> action.
     *
     * @param from our Jid
     * @param recipient the destination Jid
     * @param sessionId the ID of the Jingle session that this message will be terminating.
     * @param contentList the content elements containing media and transport descriptions.
     * @return the newly constructed {@link Jingle} <tt>content-accept</tt> packet.
     */
    public static Jingle createContentAccept(FullJid from, FullJid recipient, String sessionId,
            Iterable<JingleContent> contentList)
    {
        Jingle contentAccept = new Jingle(CONTENT_ACCEPT, sessionId);

        contentAccept.setTo(recipient);
        contentAccept.setFrom(from);
        contentAccept.setType(IQ.Type.set);

        for (JingleContent content : contentList)
            contentAccept.addContent(content);
        return contentAccept;
    }

    /**
     * Creates a new {@link Jingle} with the <tt>content-reject</tt> action.
     *
     * @param from our Jid
     * @param recipient the destination Jid
     * @param sessionId the ID of the Jingle session that this message will be terminating.
     * @param contentList the content elements containing media and transport descriptions.
     * @return the newly constructed {@link Jingle} <tt>content-reject</tt> packet.
     */
    public static Jingle createContentReject(FullJid from, FullJid recipient, String sessionId,
            Iterable<JingleContent> contentList)
    {
        Jingle contentReject = new Jingle(CONTENT_REJECT, sessionId);

        contentReject.setTo(recipient);
        contentReject.setFrom(from);
        contentReject.setType(IQ.Type.set);

        if (contentList != null) {
            for (JingleContent content : contentList)
                contentReject.addContent(content);
        }
        return contentReject;
    }

    /**
     * Creates a new {@link Jingle} with the <tt>content-modify</tt> action.
     *
     * @param from our Jid
     * @param recipient the destination Jid
     * @param sessionId the ID of the Jingle session that this message will be terminating.
     * @param content the content element containing media and transport description.
     * @return the newly constructed {@link Jingle} <tt>content-modify</tt> packet.
     */
    public static Jingle createContentModify(FullJid from, FullJid recipient, String sessionId, JingleContent content)
    {
        Jingle contentModify = new Jingle(CONTENT_MODIFY, sessionId);

        contentModify.setTo(recipient);
        contentModify.setFrom(from);
        contentModify.setType(IQ.Type.set);

        contentModify.addContent(content);
        return contentModify;
    }

    /**
     * Creates a new {@link Jingle} with the <tt>content-remove</tt> action.
     *
     * @param from our Jid
     * @param recipient the destination Jid
     * @param sessionId the ID of the Jingle session that this message will be terminating.
     * @param contentList the content elements containing media and transport descriptions.
     * @return the newly constructed {@link Jingle} <tt>content-remove</tt> packet.
     */
    public static Jingle createContentRemove(FullJid from, FullJid recipient, String sessionId,
            Iterable<JingleContent> contentList)
    {
        Jingle contentRemove = new Jingle(CONTENT_REMOVE, sessionId);

        contentRemove.setTo(recipient);
        contentRemove.setFrom(from);
        contentRemove.setType(IQ.Type.set);

        for (JingleContent content : contentList)
            contentRemove.addContent(content);
        return contentRemove;
    }
}
