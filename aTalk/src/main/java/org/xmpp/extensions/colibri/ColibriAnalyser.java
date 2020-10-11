/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.xmpp.extensions.colibri;

import org.xmpp.extensions.jingle.element.JingleContent;
import net.java.sip.communicator.impl.protocol.jabber.jinglesdp.JingleUtils;

import org.atalk.util.MediaType;

import java.util.*;

import timber.log.Timber;

/**
 * Utility class for extracting info from responses received from the JVB and keeping track of
 * conference state.
 *
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
public class ColibriAnalyser
{
    /**
     * Colibri IQ instance used to store conference state.
     */
    private final ColibriConferenceIQ conferenceState;

    /**
     * Creates new instance of analyser that will used given Colibri IQ instance for storing conference state.
     *
     * @param conferenceStateHolder the Colibri IQ instance that will be used for storing conference state.
     */
    public ColibriAnalyser(ColibriConferenceIQ conferenceStateHolder)
    {
        this.conferenceState = conferenceStateHolder;
    }

    /**
     * Processes channels allocation response from the JVB and stores info about new channels in {@link #conferenceState}.
     *
     * @param allocateResponse the Colibri IQ that describes JVB response to allocate request.
     */
    public void processChannelAllocResp(ColibriConferenceIQ allocateResponse)
    {
        String conferenceResponseID = allocateResponse.getID();
        String colibriID = conferenceState.getID();

        if (colibriID == null) {
            conferenceState.setID(conferenceResponseID);
        }
        else if (!colibriID.equals(conferenceResponseID)) {
            throw new IllegalStateException("conference.id");
        }

        /*
         * XXX We must remember the JID of the Jitsi Videobridge because (1) we do not want to
         * re-discover it in every method invocation on this Call instance and (2) we want to use
         * one and the same for all CallPeers within this Call instance.
         */
        conferenceState.setFrom(allocateResponse.getFrom());

        Set<String> endpoints = new HashSet<>();

        for (ColibriConferenceIQ.Content contentResponse : allocateResponse.getContents()) {
            String contentName = contentResponse.getName();
            ColibriConferenceIQ.Content content = conferenceState.getOrCreateContent(contentName);

            // FIXME: we do not check if allocated channel does not clash with any existing one
            for (ColibriConferenceIQ.Channel channelResponse : contentResponse.getChannels()) {
                content.addChannel(channelResponse);
                endpoints.add(channelResponse.getEndpoint());
            }
            for (ColibriConferenceIQ.SctpConnection sctpConnResponse : contentResponse.getSctpConnections()) {
                content.addSctpConnection(sctpConnResponse);
                endpoints.add(sctpConnResponse.getEndpoint());
            }
        }

        for (ColibriConferenceIQ.ChannelBundle bundle : allocateResponse.getChannelBundles()) {
            // ChannelBundles are mapped by their ID, so here we update the
            // state of the conference with whatever the response contained.
            conferenceState.addChannelBundle(bundle);
        }

        for (ColibriConferenceIQ.Endpoint endpoint
                : allocateResponse.getEndpoints()) {
            // Endpoints are mapped by their ID, so here we update the
            // state of the conference with whatever the response contained.
            conferenceState.addEndpoint(endpoint);
        }
    }

    /**
     * Utility method for extracting info about channels allocated from JVB response. FIXME: this
     * might not work as expected when channels for multiple peers with single query were allocated.
     *
     * @param conferenceResponse JVB response to allocate channels request.
     * @param peerContents list of peer media contents that has to be matched with allocated channels.
     * @return the Colibri IQ that describes allocated channels.
     */
    public static ColibriConferenceIQ getResponseContents(ColibriConferenceIQ conferenceResponse,
            List<JingleContent> peerContents)
    {
        ColibriConferenceIQ conferenceResult = new ColibriConferenceIQ();

        conferenceResult.setFrom(conferenceResponse.getFrom());
        conferenceResult.setID(conferenceResponse.getID());
        conferenceResult.setGID(conferenceResponse.getGID());
        conferenceResult.setName(conferenceResponse.getName());

        // FIXME: we support single bundle for all channels
        String bundleId = null;
        Set<String> endpointIds = new HashSet<>();
        for (JingleContent content : peerContents) {
            MediaType mediaType = JingleUtils.getMediaType(content);

            ColibriConferenceIQ.Content contentResponse = conferenceResponse.getContent(mediaType.toString());
            if (contentResponse != null) {
                String contentName = contentResponse.getName();
                ColibriConferenceIQ.Content contentResult = new ColibriConferenceIQ.Content(contentName);

                conferenceResult.addContent(contentResult);
                for (ColibriConferenceIQ.Channel channelResponse : contentResponse.getChannels()) {
                    contentResult.addChannel(channelResponse);
                    bundleId = readChannelBundle(channelResponse, bundleId);
                    endpointIds.add(channelResponse.getEndpoint());
                }

                for (ColibriConferenceIQ.SctpConnection sctpConnResponse : contentResponse.getSctpConnections()) {
                    contentResult.addSctpConnection(sctpConnResponse);
                    bundleId = readChannelBundle(sctpConnResponse, bundleId);
                    endpointIds.add(sctpConnResponse.getEndpoint());
                }
            }
        }

        // Copy only peer's bundle(JVB returns all bundles)
        if (bundleId != null) {
            for (ColibriConferenceIQ.ChannelBundle bundle : conferenceResponse.getChannelBundles()) {
                if (bundleId.equals(bundle.getId())) {
                    conferenceResult.addChannelBundle(bundle);
                    break;
                }
            }
        }

        // copy only the endpoints we have seen
        for (ColibriConferenceIQ.Endpoint en : conferenceResponse.getEndpoints()) {
            if (endpointIds.contains(en.getId())) {
                conferenceResult.addEndpoint(en);
            }
        }
        return conferenceResult;
    }

    /**
     * Utility method for getting actual channel bundle. If <tt>currentBundle</tt> is <tt>null</tt>
     * then <tt>channels</tt> bundle is returned(and vice-versa). If both channel's and given
     * bundle IDs are not null then they are compared and error is logged, but channel's bundle is
     * returned in the last place anyway.
     */
    private static String readChannelBundle(ColibriConferenceIQ.ChannelCommon channel, String currentBundle)
    {
        String channelBundle = channel.getChannelBundleId();
        if (channelBundle == null) {
            return currentBundle;
        }

        if (currentBundle == null) {
            return channel.getChannelBundleId();
        }
        else {
            // Compare to detect problems
            if (!currentBundle.equals(channelBundle)) {
                Timber.e("Replaced bundle: %s with %s", currentBundle, channelBundle);
            }
            return channelBundle;
        }
    }
}
