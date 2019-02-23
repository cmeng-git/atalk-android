/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.impl.protocol.jabber.extensions.jingle;

import org.jivesoftware.smack.packet.IQ;
import org.jxmpp.jid.Jid;

import java.util.List;

import static net.java.sip.communicator.impl.protocol.jabber.extensions.jingle.JingleAction.CONTENT_ACCEPT;
import static net.java.sip.communicator.impl.protocol.jabber.extensions.jingle.JingleAction.CONTENT_ADD;
import static net.java.sip.communicator.impl.protocol.jabber.extensions.jingle.JingleAction.CONTENT_MODIFY;
import static net.java.sip.communicator.impl.protocol.jabber.extensions.jingle.JingleAction.CONTENT_REJECT;
import static net.java.sip.communicator.impl.protocol.jabber.extensions.jingle.JingleAction.CONTENT_REMOVE;
import static net.java.sip.communicator.impl.protocol.jabber.extensions.jingle.JingleAction.DESCRIPTION_INFO;
import static net.java.sip.communicator.impl.protocol.jabber.extensions.jingle.JingleAction.SESSION_ACCEPT;
import static net.java.sip.communicator.impl.protocol.jabber.extensions.jingle.JingleAction.SESSION_INITIATE;
import static net.java.sip.communicator.impl.protocol.jabber.extensions.jingle.JingleAction.SESSION_TERMINATE;
import static net.java.sip.communicator.impl.protocol.jabber.extensions.jingle.JingleAction.TRANSPORT_INFO;

/**
 * A utility class containing methods for creating {@link JingleIQ} instances for various situations.
 *
 * @author Emil Ivov
 * @author Eng Chong Meng
 * @TODO a number of methods in this class have almost identical bodies. please refactor and reuse code (Emil).
 */
public class JinglePacketFactory
{
    /**
     * Creates a {@link JingleIQ} <tt>session-info</tt> packet carrying a <tt>ringing</tt> payload.
     *
     * @param sessionInitiate the {@link JingleIQ} that established the session which the response is going to
     * belong to.
     * @return a {@link JingleIQ} <tt>session-info</tt> packet carrying a <tt>ringing</tt> payload.
     */
    public static JingleIQ createRinging(JingleIQ sessionInitiate)
    {
        return createSessionInfo(sessionInitiate.getTo(), sessionInitiate.getFrom(),
                sessionInitiate.getSID(), SessionInfoType.ringing);
    }

    /**
     * Creates a {@link JingleIQ} <tt>session-info</tt> packet carrying a the specified payload type.
     *
     * @param from our full jid
     * @param to their full jid
     * @param sid the ID of the Jingle session this IQ will belong to.
     * @return a {@link JingleIQ} <tt>session-info</tt> packet carrying a the specified payload type.
     */
    public static JingleIQ createSessionInfo(Jid from, Jid to, String sid)
    {
        JingleIQ sessionInfo = new JingleIQ(JingleAction.SESSION_INFO, sid);
        sessionInfo.setFrom(from);
        sessionInfo.setTo(to);
        sessionInfo.setType(IQ.Type.set);

        return sessionInfo;
    }

    /**
     * Creates a {@link JingleIQ} <tt>session-info</tt> packet carrying a the specified payload type.
     *
     * @param from our full jid
     * @param to their full jid
     * @param sid the ID of the Jingle session this IQ will belong to.
     * @param type the exact type (e.g. ringing, hold, mute) of the session info IQ.
     * @return a {@link JingleIQ} <tt>session-info</tt> packet carrying a the specified payload type.
     */
    public static JingleIQ createSessionInfo(Jid from, Jid to, String sid, SessionInfoType type)
    {
        JingleIQ ringing = createSessionInfo(from, to, sid);
        SessionInfoExtensionElement sessionInfoType = new SessionInfoExtensionElement(type);

        ringing.setSessionInfo(sessionInfoType);
        return ringing;
    }

    /**
     * Creates a {@link JingleIQ} <tt>session-terminate</tt> packet carrying a {@link Reason#BUSY} payload.
     *
     * @param from our Jid
     * @param to the destination Jid
     * @param sid the ID of the Jingle session that this message will be terminating.
     * @return a {@link JingleIQ} <tt>session-terminate</tt> packet.
     */
    public static JingleIQ createBusy(Jid from, Jid to, String sid)
    {
        return createSessionTerminate(from, to, sid, Reason.BUSY, null);
    }

    /**
     * Creates a {@link JingleIQ} <tt>session-terminate</tt> packet that is meant to terminate an
     * on-going, well established session (similar to a SIP BYE request).
     *
     * @param from our Jid
     * @param to the destination Jid
     * @param sid the ID of the Jingle session that this message will be terminating.
     * @return a {@link JingleIQ} <tt>session-terminate</tt> packet .
     */
    public static JingleIQ createBye(Jid from, Jid to, String sid)
    {
        return createSessionTerminate(from, to, sid, Reason.SUCCESS, "Nice talking to you!");
    }

    /**
     * Creates a {@link JingleIQ} <tt>session-terminate</tt> packet that is meant to terminate a not
     * yet established session.
     *
     * @param from our Jid
     * @param to the destination Jid
     * @param sid the ID of the Jingle session that this message will be terminating.
     * @return a {@link JingleIQ} <tt>session-terminate</tt> packet .
     */
    public static JingleIQ createCancel(Jid from, Jid to, String sid)
    {
        return createSessionTerminate(from, to, sid, Reason.CANCEL, "Oops!");
    }

    /**
     * Creates a {@link JingleIQ} <tt>session-terminate</tt> packet with the specified src, dst, sid, and reason.
     *
     * @param from our Jid
     * @param to the destination Jid
     * @param sid the ID of the Jingle session that this message will be terminating.
     * @param reason the reason for the termination
     * @param reasonText a human readable reason for the termination or <tt>null</tt> for none.
     * @return the newly constructed {@link JingleIQ} <tt>session-terminate</tt> packet. .
     */
    public static JingleIQ createSessionTerminate(Jid from, Jid to, String sid,
            Reason reason, String reasonText)
    {
        JingleIQ terminate = new JingleIQ(SESSION_TERMINATE, sid);
        terminate.setTo(to);
        terminate.setFrom(from);
        terminate.setType(IQ.Type.set);

        ReasonPacketExtension reasonPacketExt = new ReasonPacketExtension(reason, reasonText, null);
        terminate.setReason(reasonPacketExt);
        return terminate;
    }

    /**
     * Creates a {@link JingleIQ} <tt>session-accept</tt> packet with the specified <tt>from</tt>,
     * <tt>to</tt>, <tt>sid</tt>, and <tt>content</tt>. Given our role in a conversation, we would
     * assume that the <tt>from</tt> value should also be used for the value of the Jingle
     * <tt>responder</tt>.
     *
     * @param from our Jid
     * @param to the destination Jid
     * @param sid the ID of the Jingle session that this message will be terminating.
     * @param contentList the content elements containing media and transport descriptions.
     * @return the newly constructed {@link JingleIQ} <tt>session-accept</tt> packet.
     */
    public static JingleIQ createSessionAccept(Jid from, Jid to, String sid, Iterable<ContentExtensionElement> contentList)
    {
        JingleIQ sessionAccept = new JingleIQ(SESSION_ACCEPT, sid);

        sessionAccept.setTo(to);
        sessionAccept.setFrom(from);
        sessionAccept.setResponder(from);
        sessionAccept.setType(IQ.Type.set);

        for (ContentExtensionElement content : contentList)
            sessionAccept.addContent(content);
        return sessionAccept;
    }

    /**
     * Creates a {@link JingleIQ} <tt>description-info</tt> packet with the specified <tt>from</tt>,
     * <tt>to</tt>, <tt>sid</tt>, and <tt>content</tt>. Given our role in a conversation, we would
     * assume that the <tt>from</tt> value should also be used for the value of the Jingle <tt>responder</tt>.
     *
     * @param from our Jid
     * @param to the destination Jid
     * @param sid the ID of the Jingle session that this message will be terminating.
     * @param contentList the content elements containing media and transport descriptions.
     * @return the newly constructed {@link JingleIQ} <tt>description-info</tt> packet.
     */
    public static JingleIQ createDescriptionInfo(Jid from, Jid to, String sid,
            Iterable<ContentExtensionElement> contentList)
    {
        JingleIQ descriptionInfo = new JingleIQ(DESCRIPTION_INFO, sid);

        descriptionInfo.setTo(to);
        descriptionInfo.setFrom(from);
        descriptionInfo.setResponder(from);
        descriptionInfo.setType(IQ.Type.set);

        for (ContentExtensionElement content : contentList)
            descriptionInfo.addContent(content);
        return descriptionInfo;
    }

    /**
     * Creates a {@link JingleIQ} <tt>transport-info</tt> packet with the specified <tt>from</tt>,
     * <tt>to</tt>, <tt>sid</tt>, and <tt>contentList</tt>. Given our role in a conversation, we
     * would assume that the <tt>from</tt> value should also be used for the value of the Jingle
     * <tt>responder</tt>.
     *
     * @param from our Jid
     * @param to the destination Jid
     * @param sid the ID of the Jingle session that this message will be terminating.
     * @param contentList the content elements containing media transport descriptions.
     * @return the newly constructed {@link JingleIQ} <tt>transport-info</tt> packet.
     */
    public static JingleIQ createTransportInfo(Jid from, Jid to, String sid, Iterable<ContentExtensionElement> contentList)
    {
        JingleIQ transportInfo = new JingleIQ(TRANSPORT_INFO, sid);

        transportInfo.setTo(to);
        transportInfo.setFrom(from);
        transportInfo.setInitiator(from);
        transportInfo.setType(IQ.Type.set);
        for (ContentExtensionElement content : contentList)
            transportInfo.addContent(content);
        return transportInfo;
    }

    /**
     * Creates a new {@link JingleIQ} with the <tt>session-initiate</tt> action.
     *
     * @param from our Jid
     * @param to the destination Jid
     * @param sid the ID of the Jingle session that this message will be terminating.
     * @param contentList the content elements containing media and transport descriptions.
     * @return the newly constructed {@link JingleIQ} <tt>session-initiate</tt> packet.
     */
    public static JingleIQ createSessionInitiate(Jid from, Jid to, String sid, List<ContentExtensionElement> contentList)
    {
        JingleIQ sessionInitiate = new JingleIQ(SESSION_INITIATE, sid);

        sessionInitiate.setTo(to);
        sessionInitiate.setFrom(from);
        sessionInitiate.setInitiator(from);
        sessionInitiate.setType(IQ.Type.set);

        for (ContentExtensionElement content : contentList) {
            sessionInitiate.addContent(content);
        }
        return sessionInitiate;
    }

    /**
     * Creates a new {@link JingleIQ} with the <tt>content-add</tt> action.
     *
     * @param from our Jid
     * @param to the destination Jid
     * @param sid the ID of the Jingle session that this message will be terminating.
     * @param contentList the content elements containing media and transport descriptions.
     * @return the newly constructed {@link JingleIQ} <tt>content-add</tt> packet.
     */
    public static JingleIQ createContentAdd(Jid from, Jid to, String sid, List<ContentExtensionElement> contentList)
    {
        JingleIQ contentAdd = new JingleIQ(CONTENT_ADD, sid);

        contentAdd.setTo(to);
        contentAdd.setFrom(from);
        contentAdd.setType(IQ.Type.set);

        for (ContentExtensionElement content : contentList)
            contentAdd.addContent(content);
        return contentAdd;
    }

    /**
     * Creates a new {@link JingleIQ} with the <tt>content-accept</tt> action.
     *
     * @param from our Jid
     * @param to the destination Jid
     * @param sid the ID of the Jingle session that this message will be terminating.
     * @param contentList the content elements containing media and transport descriptions.
     * @return the newly constructed {@link JingleIQ} <tt>content-accept</tt> packet.
     */
    public static JingleIQ createContentAccept(Jid from, Jid to, String sid, Iterable<ContentExtensionElement> contentList)
    {
        JingleIQ contentAccept = new JingleIQ(CONTENT_ACCEPT, sid);

        contentAccept.setTo(to);
        contentAccept.setFrom(from);
        contentAccept.setType(IQ.Type.set);

        for (ContentExtensionElement content : contentList)
            contentAccept.addContent(content);
        return contentAccept;
    }

    /**
     * Creates a new {@link JingleIQ} with the <tt>content-reject</tt> action.
     *
     * @param from our Jid
     * @param to the destination Jid
     * @param sid the ID of the Jingle session that this message will be terminating.
     * @param contentList the content elements containing media and transport descriptions.
     * @return the newly constructed {@link JingleIQ} <tt>content-reject</tt> packet.
     */
    public static JingleIQ createContentReject(Jid from, Jid to, String sid, Iterable<ContentExtensionElement> contentList)
    {
        JingleIQ contentReject = new JingleIQ(CONTENT_REJECT, sid);

        contentReject.setTo(to);
        contentReject.setFrom(from);
        contentReject.setType(IQ.Type.set);

        if (contentList != null) {
            for (ContentExtensionElement content : contentList)
                contentReject.addContent(content);
        }
        return contentReject;
    }

    /**
     * Creates a new {@link JingleIQ} with the <tt>content-modify</tt> action.
     *
     * @param from our Jid
     * @param to the destination Jid
     * @param sid the ID of the Jingle session that this message will be terminating.
     * @param content the content element containing media and transport description.
     * @return the newly constructed {@link JingleIQ} <tt>content-modify</tt> packet.
     */
    public static JingleIQ createContentModify(Jid from, Jid to, String sid, ContentExtensionElement content)
    {
        JingleIQ contentModify = new JingleIQ(CONTENT_MODIFY, sid);

        contentModify.setTo(to);
        contentModify.setFrom(from);
        contentModify.setType(IQ.Type.set);

        contentModify.addContent(content);
        return contentModify;
    }

    /**
     * Creates a new {@link JingleIQ} with the <tt>content-remove</tt> action.
     *
     * @param from our Jid
     * @param to the destination Jid
     * @param sid the ID of the Jingle session that this message will be terminating.
     * @param contentList the content elements containing media and transport descriptions.
     * @return the newly constructed {@link JingleIQ} <tt>content-remove</tt> packet.
     */
    public static JingleIQ createContentRemove(Jid from, Jid to, String sid, Iterable<ContentExtensionElement> contentList)
    {
        JingleIQ contentRemove = new JingleIQ(CONTENT_REMOVE, sid);

        contentRemove.setTo(to);
        contentRemove.setFrom(from);
        contentRemove.setType(IQ.Type.set);

        for (ContentExtensionElement content : contentList)
            contentRemove.addContent(content);
        return contentRemove;
    }
}
