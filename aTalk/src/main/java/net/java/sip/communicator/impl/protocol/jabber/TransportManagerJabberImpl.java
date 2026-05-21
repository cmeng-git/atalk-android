/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.impl.protocol.jabber;

import java.net.InetAddress;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.java.sip.communicator.service.protocol.Call;
import net.java.sip.communicator.service.protocol.CallPeer;
import net.java.sip.communicator.service.protocol.OperationFailedException;
import net.java.sip.communicator.service.protocol.media.TransportManager;

import org.atalk.service.neomedia.MediaStreamTarget;
import org.atalk.service.neomedia.StreamConnector;
import org.atalk.util.MediaType;

import org.jivesoftware.smack.packet.XmlElement;

import org.jivesoftware.smackx.jingle.element.JingleContent;
import org.jivesoftware.smackx.jingle_rtp.JingleUtils;
import org.jivesoftware.smackx.jingle_rtp.element.IceUdpTransport;
import org.jivesoftware.smackx.jingle_rtp.element.RtpDescription;

/**
 * <code>TransportManager</code>s gather local candidates for incoming and outgoing calls. Their work
 * starts by calling a start method which, using the remote peer's session description, would start
 * the harvest. Calling a second wrap up method would deliver the candidate harvest, possibly after
 * blocking if it has not yet completed.
 *
 * @author Emil Ivov
 * @author Lyubomir Marinov
 * @author Eng Chong Meng
 */
public abstract class TransportManagerJabberImpl extends TransportManager<CallPeerJabberImpl> {
    /**
     * The ID that we will be assigning to our next candidate. We use <code>int</code>s for
     * interoperability reasons (Emil: I believe that GTalk uses <code>int</code>s. If that turns out
     * not to be the case we can stop using <code>int</code>s here if that's an issue).
     */
    private static int nextID = 1;

    /**
     * The generation of the candidates we are currently generating
     */
    private int currentGeneration = 0;

    /**
     * Creates a new instance of this transport manager, binding it to the specified peer.
     *
     * @param callPeer the {@link CallPeer} whose traffic we will be taking care of.
     */
    protected TransportManagerJabberImpl(CallPeerJabberImpl callPeer) {
        super(callPeer);
    }

    /**
     * Returns the <code>InetAddress</code> that is most likely to be to be used as a next hop when
     * contacting the specified <code>destination</code>. This is an utility method that is used
     * whenever we have to choose one of our local addresses to put in the Via, Contact or (in the
     * case of no registrar accounts) From headers.
     *
     * @param peer the CallPeer that we would contact.
     *
     * @return the <code>InetAddress</code> that is most likely to be to be used as a next hop when
     * contacting the specified <code>destination</code>.
     *
     * @throws IllegalArgumentException if <code>destination</code> is not a valid host/IP/FQDN
     */
    @Override
    protected InetAddress getIntendedDestination(CallPeerJabberImpl peer) {
        return peer.getProtocolProvider().getNextHop();
    }

    /**
     * Returns the ID that we will be assigning to the next candidate we create.
     *
     * @return the next ID to use with a candidate.
     */
    protected String getNextID() {
        int nextID;
        synchronized (TransportManagerJabberImpl.class) {
            nextID = TransportManagerJabberImpl.nextID++;
        }
        return Integer.toString(nextID);
    }

    /**
     * Gets the <code>MediaStreamTarget</code> to be used as the <code>target</code> of the
     * <code>MediaStream</code> with a specific <code>MediaType</code>.
     *
     * @param mediaType the <code>MediaType</code> of the <code>MediaStream</code> which is to have its
     * <code>target</code> set to the returned <code>MediaStreamTarget</code>
     *
     * @return the <code>MediaStreamTarget</code> to be used as the <code>target</code> of the
     * <code>MediaStream</code> with the specified <code>MediaType</code>
     */
    public abstract MediaStreamTarget getStreamTarget(MediaType mediaType);

    /**
     * Gets the XML namespace of the Jingle transport implemented by this <code>TransportManagerJabberImpl</code>.
     *
     * @return the XML namespace of the Jingle transport implemented by this <code>TransportManagerJabberImpl</code>
     */
    public abstract String getXmlNamespace();

    /**
     * Returns the generation that our current candidates belong to.
     *
     * @return the generation that we should assign to candidates that we are currently advertising.
     */
    protected int getCurrentGeneration() {
        return currentGeneration;
    }

    /**
     * Increments the generation that we are assigning candidates.
     */
    protected void incrementGeneration() {
        currentGeneration++;
    }

    /**
     * Starts transport candidate harvest for a specific <code>JingleContent</code> that we are
     * going to offer or answer with.
     *
     * @param theirContent the <code>JingleContent</code> offered by the remote peer to which we are going
     * to answer with <code>ourContent</code> or <code>null</code> if <code>ourContent</code> will be an offer to the remote peer
     * @param ourContent the <code>JingleContent</code> for which transport candidate harvest is to be started
     * @param transportInfoSender a <code>TransportInfoSender</code> if the harvested transport candidates are to be sent in
     * a <code>transport-info</code> rather than in <code>ourContent</code>; otherwise, <code>null</code>
     * @param media the media of the <code>RtpDescriptionExtensionElement</code> child of <code>ourContent</code>
     *
     * @return a <code>XmlElement</code> to be added as a child to <code>ourContent</code>; otherwise, <code>null</code>
     *
     * @throws OperationFailedException if anything goes wrong while starting transport candidate harvest for
     * the specified <code>ourContent</code>
     */
    protected abstract XmlElement startCandidateHarvest(JingleContent theirContent,
            JingleContent ourContent, TransportInfoSender transportInfoSender, String media)
            throws OperationFailedException;

    /**
     * Starts transport candidate harvest. This method should complete rapidly and, in case of
     * lengthy procedures like STUN/TURN/UPnP candidate harvests are necessary, they should be
     * executed in a separate thread. Candidate harvest would then need to be concluded in the
     * {@link #wrapupCandidateHarvest()} method which would be called once we absolutely need the candidates.
     *
     * @param theirOffer a media description offer that we've received from the remote party
     * and that we should use in case we need to know what transports our peer is using.
     * @param ourAnswer the content descriptions that we should be adding our transport lists to.
     * This is used i.e. when their offer is null, for sending the Jingle session-initiate offer.
     * @param transportInfoSender the <code>TransportInfoSender</code> to be used by this
     * <code>TransportManagerJabberImpl</code> to send <code>transport-info</code> <code>Jingle</code>s
     * from the local peer to the remote peer if this <code>TransportManagerJabberImpl</code>
     * wishes to utilize <code>transport-info</code>. Local candidate addresses sent by this
     * <code>TransportManagerJabberImpl</code> in <code>transport-info</code> are expected to not be
     * included in the result of {@link #wrapupCandidateHarvest()}.
     *
     * @throws OperationFailedException if we fail to allocate a port number.
     */
    public void startCandidateHarvest(List<JingleContent> theirOffer, List<JingleContent> ourAnswer,
            TransportInfoSender transportInfoSender)
            throws OperationFailedException {
        List<JingleContent> cpes = (theirOffer == null) ? ourAnswer : theirOffer;

        /*
         * If Jitsi Videobridge is to be used, determine which channels are to be allocated and
         * attempt to allocate them now.
         */
        CallPeerJabberImpl peer = getCallPeer();
        for (JingleContent cpe : cpes) {
            String contentName = cpe.getName();
            JingleContent ourContent = findContentByName(ourAnswer, contentName);

            // it might be that we decided not to reply to this content
            if (ourContent != null) {
                JingleContent theirContent = (theirOffer == null) ? null : findContentByName(theirOffer, contentName);
                RtpDescription rtpDesc = ourContent.getFirstChildElement(RtpDescription.class);
                String media = rtpDesc.getMedia();
                XmlElement pe = startCandidateHarvest(theirContent, ourContent, transportInfoSender, media);

                // This will add the transport-info into the jingleContent for session-initiate
                if (pe != null) {
                    ourContent.addChildElement(pe);
                }

                // cmeng (20220228): Not working: Correct JingleContent created but not the same instance/reference
                // i.e. OurContent1: JingleContent@67c0ff2 <> OurContent2: JingleContent@19b25f9 (new)
//                if (pe != null) {
//                    Timber.w("OurContent1: %s:\n %s\n%s", ourContent, ourContent.toXML(), pe.toXML());
//                    ourContent = (JingleContent) ourContent.getBuilder(null)
//                            .addChildElement(pe)
//                            .build();
//                    Timber.w("OurContent2: %s: %s",ourContent, ourContent.toXML());
//                }
            }
        }
    }

    /**
     * Notifies the transport manager that it should conclude candidate harvesting as soon as
     * possible and return the lists of candidates gathered so far.
     *
     * @return the content list that we received earlier (possibly cloned into a new instance) and
     * that we have updated with transport lists.
     */
    public abstract List<JingleContent> wrapupCandidateHarvest();

    /**
     * Looks through the <code>cpExtList</code> and returns the {@link JingleContent} with the specified name.
     *
     * @param cpExtList the list that we will be searching for a specific content.
     * @param name the name of the content element we are looking for.
     *
     * @return the {@link JingleContent} with the specified name or <code>null</code> if no
     * such content element exists.
     */
    public static JingleContent findContentByName(Iterable<JingleContent> cpExtList, String name) {
        for (JingleContent cpExt : cpExtList) {
            if (cpExt.getName().equals(name))
                return cpExt;
        }
        return null;
    }

    /**
     * Starts the connectivity establishment of this <code>TransportManagerJabberImpl</code> i.e. checks
     * the connectivity between the local and the remote peers given the remote counterpart of the
     * negotiation between them.
     *
     * @param remote the collection of <code>JingleContent</code>s which represents the remote
     * counterpart of the negotiation between the local and the remote peer
     *
     * @return <code>true</code> if connectivity establishment has been started in response to the call;
     * otherwise, <code>false</code>. <code>TransportManagerJabberImpl</code> implementations which
     * do not perform connectivity checks (e.g. raw UDP) should return <code>true</code>. The
     * default implementation does not perform connectivity checks and always returns <code>true</code>.
     */
    public boolean startConnectivityEstablishment(Iterable<JingleContent> remote)
            throws OperationFailedException {
        return true;
    }

    /**
     * Starts the connectivity establishment of this <code>TransportManagerJabberImpl</code> i.e. checks
     * the connectivity between the local and the remote peers given the remote counterpart of the
     * negotiation between them.
     *
     * @param remote a <code>Map</code> of media-<code>IceUdpTransport</code> pairs which represents
     * the remote counterpart of the negotiation between the local and the remote peers
     *
     * @return <code>true</code> if connectivity establishment has been started in response to the call;
     * otherwise, <code>false</code>. <code>TransportManagerJabberImpl</code> implementations which
     * do not perform connectivity checks (e.g. raw UDP) should return <code>true</code>. The
     * default implementation does not perform connectivity checks and always returns <code>true</code>.
     */
    protected boolean startConnectivityEstablishment(Map<String, IceUdpTransport> remote) {
        return true;
    }

    /**
     * Notifies this <code>TransportManagerJabberImpl</code> that it should conclude any started connectivity establishment.
     *
     * @throws OperationFailedException if anything goes wrong with connectivity establishment (i.e. ICE failed, ...)
     */
    public void wrapupConnectivityEstablishment()
            throws OperationFailedException {
    }

    /**
     * Removes a content with a specific name from the transport-related part of the session
     * represented by this <code>TransportManagerJabberImpl</code> which may have been reported through
     * previous calls to the <code>startCandidateHarvest</code> and <code>startConnectivityEstablishment</code> methods.
     * <p>
     * <b>Note</b>: Because <code>TransportManager</code> deals with <code>MediaType</code>s, not content
     * names and <code>TransportManagerJabberImpl</code> does not implement translating from content
     * name to <code>MediaType</code>, implementers are expected to call
     * {@link TransportManager#closeStreamConnector(MediaType)}.
     *
     * @param name the name of the content to be removed from the transport-related part of the session
     * represented by this <code>TransportManagerJabberImpl</code>
     */
    public abstract void removeContent(String name);

    /**
     * Removes a content with a specific name from a specific collection of contents and closes any
     * associated <code>StreamConnector</code>.
     *
     * @param contents the collection of contents to remove the content with the specified name from
     * @param name the name of the content to remove
     *
     * @return the removed <code>JingleContent</code> if any; otherwise, <code>null</code>
     */
    protected JingleContent removeContent(Iterable<JingleContent> contents, String name) {
        for (Iterator<JingleContent> contentIter = contents.iterator(); contentIter.hasNext(); ) {
            JingleContent content = contentIter.next();

            if (name.equals(content.getName())) {
                contentIter.remove();

                // closeStreamConnector
                MediaType mediaType = JingleUtils.getMediaType(content);
                if (mediaType != null) {
                    closeStreamConnector(mediaType);
                }
                return content;
            }
        }
        return null;
    }

    /**
     * Releases the resources acquired by this <code>TransportManager</code> and prepares it for garbage collection.
     */
    public void close() {
        for (MediaType mediaType : MediaType.values())
            closeStreamConnector(mediaType);
    }

    /**
     * {@inheritDoc}
     *
     * Adds support for telephony conferences utilizing the Jitsi Videobridge server-side technology.
     *
     * @see #doCreateStreamConnector(MediaType)
     */
    @Override
    protected StreamConnector createStreamConnector(final MediaType mediaType)
            throws OperationFailedException {
        return doCreateStreamConnector(mediaType);
    }

    protected abstract XmlElement createTransport(String media)
            throws OperationFailedException;

    protected XmlElement createTransportForStartCandidateHarvest(String media)
            throws OperationFailedException {
        return createTransport(media);
    }

    /**
     * Initializes a new <code>XmlElement</code> instance appropriate to the type of Jingle
     * transport represented by this <code>TransportManager</code>. The new instance is not initialized
     * with any attributes or child extensions.
     *
     * @return a new <code>XmlElement</code> instance appropriate to the type of Jingle transport
     * represented by this <code>TransportManager</code>
     */
    protected abstract XmlElement createTransportPacketExtension();

    /**
     * Creates a media <code>StreamConnector</code> for a stream of a specific <code>MediaType</code>. The
     * minimum and maximum of the media port boundaries are taken into account.
     *
     * @param mediaType the <code>MediaType</code> of the stream for which a <code>StreamConnector</code> is to be created
     *
     * @return a <code>StreamConnector</code> for the stream of the specified <code>mediaType</code>
     *
     * @throws OperationFailedException if the binding of the sockets fails
     */
    protected StreamConnector doCreateStreamConnector(MediaType mediaType)
            throws OperationFailedException {
        return super.createStreamConnector(mediaType);
    }

    /**
     * Finds a <code>TransportManagerJabberImpl</code> participating in a telephony conference utilizing
     * the Jitsi Videobridge server-side technology that this instance is participating in which is
     * establishing the connectivity with the Jitsi Videobridge server (as opposed to a <code>CallPeer</code>).
     *
     * @return a <code>TransportManagerJabberImpl</code> which is participating in a telephony
     * conference utilizing the Jitsi Videobridge server-side technology that this instance
     * is participating in which is establishing the connectivity with the Jitsi Videobridge
     * server (as opposed to a <code>CallPeer</code>).
     */
    TransportManagerJabberImpl findTransportManagerEstablishingConnectivityWithJitsiVideobridge() {
        TransportManagerJabberImpl transportManager = null;
        return transportManager;
    }

    /**
     * Sets the flag which indicates whether to use rtcpmux or not.
     */
    public abstract void setRtcpmux(boolean rtcpmux);

    public boolean isRtcpmux() {
        return false;
    }
}
