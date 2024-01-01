/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.jivesoftware.smackx.jingle_rtp;

import static org.atalk.impl.neomedia.format.MediaFormatImpl.FORMAT_PARAMETER_ATTR_IMAGEATTR;

import net.java.sip.communicator.impl.protocol.jabber.JabberActivator;
import net.java.sip.communicator.service.protocol.media.DynamicPayloadTypeRegistry;
import net.java.sip.communicator.service.protocol.media.DynamicRTPExtensionsRegistry;
import net.java.sip.communicator.util.NetworkUtils;

import org.apache.commons.lang3.StringUtils;
import org.atalk.impl.timberlog.TimberLog;
import org.atalk.service.neomedia.MediaDirection;
import org.atalk.service.neomedia.MediaService;
import org.atalk.service.neomedia.MediaStreamTarget;
import org.atalk.service.neomedia.RTPExtension;
import org.atalk.service.neomedia.format.AudioMediaFormat;
import org.atalk.service.neomedia.format.MediaFormat;
import org.atalk.service.neomedia.format.MediaFormatFactory;
import org.atalk.util.MediaType;
import org.jivesoftware.smack.packet.XmlEnvironment;
import org.jivesoftware.smackx.jingle.element.JingleContent;
import org.jivesoftware.smackx.jingle.element.JingleContent.Creator;
import org.jivesoftware.smackx.jingle.element.JingleContent.Senders;
import org.jivesoftware.smackx.jingle_rtp.element.IceUdpTransport;
import org.jivesoftware.smackx.jingle_rtp.element.IceUdpTransportCandidate;
import org.jivesoftware.smackx.jingle_rtp.element.IceUdpTransportRemoteCandidate;
import org.jivesoftware.smackx.jingle_rtp.element.ParameterElement;
import org.jivesoftware.smackx.jingle_rtp.element.PayloadType;
import org.jivesoftware.smackx.jingle_rtp.element.RtcpMux;
import org.jivesoftware.smackx.jingle_rtp.element.RtpDescription;
import org.jivesoftware.smackx.jingle_rtp.element.RtpHeader;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import timber.log.Timber;

/**
 * The class contains a number of utility methods that are meant to facilitate creating and parsing
 * jingle media rtp description descriptions and transports.
 *
 * @author Emil Ivov
 * @author Lyubomir Marinov
 * @author Eng Chong Meng
 */
public class JingleUtils
{
    /**
     * Extracts and returns the list of <code>MediaFormat</code>s advertised in <code>description</code>
     * preserving their oder and registering dynamic payload type numbers in the specified
     * <code>ptRegistry</code>. Note that this method would only include in the result list
     * <code>MediaFormat</code> instances that are currently supported by our <code>MediaService</code>
     * implementation and enabled in its configuration. This means that the method could return an
     * empty list even if there were actually some formats in the <code>mediaDesc</code> if we support none
     * of them or if all these we support are not enabled in the <code>MediaService</code> configuration form.
     *
     * @param description the <code>MediaDescription</code> that we'd like to probe for a list of <code>MediaFormat</code>s
     * @param ptRegistry a reference to the <code>DynamycPayloadTypeRegistry</code> where we should be registering
     * newly added payload type number to format mappings.
     * @return an ordered list of <code>MediaFormat</code>s that are both advertised in the
     * <code>description</code> and supported by our <code>MediaService</code> implementation.
     */
    public static List<MediaFormat> extractFormats(RtpDescription description, DynamicPayloadTypeRegistry ptRegistry)
    {
        List<MediaFormat> mediaFmts = new ArrayList<>();
        if (description == null) {
            return mediaFmts;
        }

        List<PayloadType> payloadTypes = description.getChildElements(PayloadType.class);
        for (PayloadType ptExt : payloadTypes) {
            MediaFormat format = payloadTypeToMediaFormat(ptExt, ptRegistry);

            // continue if our media service does not know this format
            if (format == null) {
                Timber.log(TimberLog.FINER, "Unsupported remote format: %S", ptExt.toXML(XmlEnvironment.EMPTY));
            }
            else
                mediaFmts.add(format);
        }
        return mediaFmts;
    }

    /**
     * Returns the {@link MediaFormat} described in the <code>payloadType</code> extension or
     * <code>null</code> if we don't recognize the format.
     *
     * @param payloadType the {@link PayloadType} which is to be parsed into a
     * {@link MediaFormat}.
     * @param ptRegistry the {@link DynamicPayloadTypeRegistry} that we would use for the registration of
     * possible dynamic payload types or <code>null</code> the returned <code>MediaFormat</code> is
     * to not be registered into a <code>DynamicPayloadTypeRegistry</code>.
     * @return the {@link MediaFormat} described in the <code>payloadType</code> extension or
     * <code>null</code> if we don't recognize the format.
     */
    public static MediaFormat payloadTypeToMediaFormat(PayloadType payloadType, DynamicPayloadTypeRegistry ptRegistry)
    {
        return payloadTypeToMediaFormat(payloadType, JabberActivator.getMediaService(), ptRegistry);
    }

    /**
     * Returns the {@link MediaFormat} described in the <code>payloadType</code> extension or
     * <code>null</code> if we don't recognize the format.
     *
     * @param payloadType the {@link PayloadType} which is to be parsed into a {@link MediaFormat}.
     * @param mediaService the <code>MediaService</code> implementation which is to be used for <code>MediaFormat</code>
     * -related factory methods
     * @param ptRegistry the {@link DynamicPayloadTypeRegistry} that we would use for the registration of
     * possible dynamic payload types or <code>null</code> the returned <code>MediaFormat</code> is
     * to not be registered into a <code>DynamicPayloadTypeRegistry</code>.
     * @return the {@link MediaFormat} described in the <code>payloadType</code> extension or
     * <code>null</code> if we don't recognize the format.
     */
    public static MediaFormat payloadTypeToMediaFormat(PayloadType payloadType,
            MediaService mediaService, DynamicPayloadTypeRegistry ptRegistry)
    {
        byte pt = (byte) payloadType.getID();
        boolean unknown = false;

        // convert params to a name:value map
        List<ParameterElement> params = payloadType.getChildElements(ParameterElement.class);
        Map<String, String> paramsMap = new HashMap<>();
        Map<String, String> advancedMap = new HashMap<>();

        for (ParameterElement param : params) {
            String paramName = param.getName();
            if ("imageattr".equals(paramName))
                advancedMap.put(paramName, param.getValue());
            else
                paramsMap.put(paramName, param.getValue());
        }

        // video-related attributes in payload-type element
        Set<String> keys = payloadType.getAttributes().keySet();
        for (String attr : keys) {
            if (attr.equals("width") || attr.equals("height"))
                paramsMap.put(attr, payloadType.getAttributeValue(attr));

            //update ptime with the actual value from the payload
            if (attr.equals(PayloadType.ATTR_PTIME))
                advancedMap.put(PayloadType.ATTR_PTIME, Integer.toString(payloadType.getPtime()));
        }

        // now create the format.
        MediaFormatFactory formatFactory = mediaService.getFormatFactory();
        MediaFormat format = formatFactory.createMediaFormat(
                pt, payloadType.getName(), payloadType.getClockrate(), payloadType.getChannels(),
                -1, paramsMap, advancedMap);

        // we don't seem to know anything about this format
        if (format == null) {
            unknown = true;
            format = formatFactory.createUnknownMediaFormat(MediaType.AUDIO);
        }

        /*
         * We've just created a MediaFormat for the specified payloadType so we have to remember the
         * mapping between the two so that we don't, for example, map the same payloadType to a
         * different MediaFormat at a later time when we do automatic generation of payloadType in
         * DynamicPayloadTypeRegistry. If the remote peer tries to remap a payloadType in its answer
         * to a different MediaFormat than the one we've specified in our offer, then the dynamic
         * payload type registry will keep the original value for receiving and also add an
         * overriding value for the new one. The overriding value will be streamed to our peer.
         */
        if ((ptRegistry != null) && (pt >= MediaFormat.MIN_DYNAMIC_PAYLOAD_TYPE)
                && (pt <= MediaFormat.MAX_DYNAMIC_PAYLOAD_TYPE))
        // some systems will violate 3264 by reusing previously defined
        // payload types for new formats. we try and salvage that
        // situation by creating an overriding mapping in such cases
        // we therefore don't do the following check.
        // && (ptRegistry.findFormat(pt) == null))
        {
            ptRegistry.addMapping(format, pt);
        }
        return unknown ? null : format;
    }

    /**
     * Extracts and returns the list of <code>RTPExtension</code>s advertised in <code>desc</code> and
     * registers newly encountered ones into the specified <code>extMap</code>. The method returns an
     * empty list in case there were no <code>extmap</code> advertisements in <code>desc</code>.
     *
     * @param desc the {@link RtpDescription} that we'd like to probe for a list of
     * {@link RTPExtension}s
     * @param extMap a reference to the <code>DynamicRTPExtensionsRegistry</code> where we should be
     * registering newly added extension mappings.
     * @return a <code>List</code> of {@link RTPExtension}s advertised in the <code>mediaDesc</code> description.
     */
    public static List<RTPExtension> extractRTPExtensions(RtpDescription desc, DynamicRTPExtensionsRegistry extMap)
    {
        List<RTPExtension> extensionsList = new ArrayList<>();
        if (desc == null) {
            return extensionsList;
        }

        List<RtpHeader> extmapList = desc.getChildElements(RtpHeader.class);
        for (RtpHeader extmap : extmapList) {
            RTPExtension rtpExtension = new RTPExtension(extmap.getURI(), getDirection(
                    extmap.getSenders(), false), extmap.getExtAttributes());
            extensionsList.add(rtpExtension);
        }
        return extensionsList;
    }

    /**
     * Converts the specified media <code>direction</code> into the corresponding {@link JingleContent.Senders}
     * value so that we could add it to a content element. The <code>initiatorPerspective</code> allows
     * callers to specify whether the direction is to be considered from the session initiator's
     * perspective or that of the responder.
     *
     * Example: A {@link MediaDirection#SENDONLY} value would be translated to
     * {@link Senders#initiator} from the initiator's perspective and to
     * {@link Senders#responder} otherwise.
     *
     * @param direction the {@link MediaDirection} that we'd like to translate.
     * @param initiatorPerspective <code>true</code> if the <code>direction</code> param is to be considered from the initiator's
     * perspective and <code>false</code> otherwise.
     * @return one of the <code>MediaDirection</code> values indicating the direction of the media steam
     * described by <code>content</code>.
     */
    public static JingleContent.Senders getSenders(MediaDirection direction, boolean initiatorPerspective)
    {
        if (direction == MediaDirection.SENDRECV)
            return Senders.both;
        if (direction == MediaDirection.INACTIVE)
            return Senders.none;

        if (initiatorPerspective) {
            if (direction == MediaDirection.SENDONLY)
                return Senders.initiator;
            else
                // recvonly
                return Senders.responder;
        }
        else {
            if (direction == MediaDirection.SENDONLY)
                return Senders.responder;
            else
                // recvonly
                return Senders.initiator;
        }
    }

    /**
     * Determines the direction of the media stream that <code>content</code> describes and returns the
     * corresponding <code>MediaDirection</code> enum entry. The method looks for a direction specifier
     * attribute (i.e. the content 'senders' attribute) or the absence thereof and returns the
     * corresponding <code>MediaDirection</code> entry. The <code>initiatorPerspective</code> allows callers
     * to specify whether the direction is to be considered from the session initiator's perspective
     * or that of the responder.
     *
     * Example: An <code>initiator</code> value would be translated to {@link MediaDirection#SENDONLY}
     * from the initiator's perspective and to {@link MediaDirection#RECVONLY} from the responder's.
     *
     * @param content the description of the media stream whose direction we are trying to determine.
     * @param initiatorPerspective <code>true</code> if the senders argument is to be translated into a direction from the
     * initiator's perspective and <code>false</code> for the sender's.
     * @return one of the <code>MediaDirection</code> values indicating the direction of the media steam
     * described by <code>content</code>.
     */
    public static MediaDirection getDirection(JingleContent content, boolean initiatorPerspective)
    {
        Senders senders = content.getSenders();
        return getDirection(senders, initiatorPerspective);
    }

    /**
     * Determines the direction of the media stream that <code>content</code> describes and returns the
     * corresponding <code>MediaDirection</code> enum entry. The method looks for a direction specifier
     * attribute (i.e. the content 'senders' attribute) or the absence thereof and returns the
     * corresponding <code>MediaDirection</code> entry. The <code>initiatorPerspectice</code> allows callers
     * to specify whether the direction is to be considered from the session initiator's perspective
     * or that of the responder.
     *
     * @param senders senders direction
     * @param initiatorPerspective <code>true</code> if the senders argument is to be translated into a direction from the
     * initiator's perspective and <code>false</code> for the sender's.
     * @return one of the <code>MediaDirection</code> values indicating the direction of the media steam
     * described by <code>content</code>.
     */
    public static MediaDirection getDirection(Senders senders, boolean initiatorPerspective)
    {
        if (senders == null)
            return MediaDirection.SENDRECV;

        if (senders == Senders.initiator) {
            if (initiatorPerspective)
                return MediaDirection.SENDONLY;
            else
                return MediaDirection.RECVONLY;
        }
        else if (senders == JingleContent.Senders.responder) {
            if (initiatorPerspective)
                return MediaDirection.RECVONLY;
            else
                return MediaDirection.SENDONLY;
        }
        else if (senders == JingleContent.Senders.both)
            return MediaDirection.SENDRECV;
        else
            // if (senders == Senders.none)
            return MediaDirection.INACTIVE;
    }

    /**
     * Returns the default candidate for the specified content <code>content</code>. The method is used
     * when establishing new calls and we need a default candidate to initiate our stream with
     * before we've discovered the one that ICE would pick.
     *
     * @param content the stream whose default candidate we are looking for.
     * @return a {@link MediaStreamTarget} containing the default <code>candidate</code>s for the stream described in
     * <code>content</code> or <code>null</code>, if for some reason, the packet does not contain any candidates.
     */
    public static MediaStreamTarget extractDefaultTarget(JingleContent content)
    {
        IceUdpTransport transport = content.getFirstChildElement(IceUdpTransport.class);
        return (transport == null) ? null : extractDefaultTarget(transport);
    }

    public static MediaStreamTarget extractDefaultTarget(IceUdpTransport transport)
    {
        // extract the default rtp candidate:
        IceUdpTransportCandidate rtpCand = getFirstCandidate(transport, IceUdpTransportCandidate.RTP_COMPONENT_ID);
        if (rtpCand == null)
            return null;

        InetAddress rtpAddress;
        try {
            rtpAddress = NetworkUtils.getInetAddress(rtpCand.getIP());
        } catch (UnknownHostException exc) {
            throw new IllegalArgumentException("Failed to parse address " + rtpCand.getIP(), exc);
        }

        // rtp port
        int rtpPort = rtpCand.getPort();
        InetSocketAddress rtpTarget = new InetSocketAddress(rtpAddress, rtpPort);

        // extract the RTCP candidate
        IceUdpTransportCandidate rtcpCand = getFirstCandidate(transport, IceUdpTransportCandidate.RTCP_COMPONENT_ID);
        InetSocketAddress rtcpTarget;

        if (rtcpCand == null) {
            rtcpTarget = new InetSocketAddress(rtpAddress, rtpPort + 1);
        }
        else {
            InetAddress rtcpAddress;
            try {
                rtcpAddress = NetworkUtils.getInetAddress(rtcpCand.getIP());
            } catch (UnknownHostException exc) {
                throw new IllegalArgumentException("Failed to parse address " + rtcpCand.getIP(), exc);
            }
            // rtcp port
            int rtcpPort = rtcpCand.getPort();
            rtcpTarget = new InetSocketAddress(rtcpAddress, rtcpPort);
        }
        return new MediaStreamTarget(rtpTarget, rtcpTarget);
    }

    /**
     * Returns the first candidate for the specified <code>componentID</code> or null if no such component exists.
     *
     * @param content the {@link JingleContent} that we'll be searching for a component.
     * @param componentID the id of the component that we are looking for (e.g. 1 for RTP, 2 for RTCP);
     * @return the first candidate for the specified <code>componentID</code> or null if no such component exists.
     */
    public static IceUdpTransportCandidate getFirstCandidate(JingleContent content, int componentID)
    {
        // passing IceUdp would also return RawUdp transports as one extends the other.
        IceUdpTransport transport = content.getFirstChildElement(IceUdpTransport.class);
        return (transport == null) ? null : getFirstCandidate(transport, componentID);
    }

    public static IceUdpTransportCandidate getFirstCandidate(IceUdpTransport transport, int componentID)
    {
        for (IceUdpTransportCandidate cand : transport.getCandidateList()) {
            // we don't care about remote candidates!
            if (!(cand instanceof IceUdpTransportRemoteCandidate) && (cand.getComponent() == componentID)) {
                return cand;
            }
        }
        return null;
    }

    /**
     * Creates a new {@link JingleContent} instance according to the specified
     * <code>formats</code>, <code>connector</code> and <code>direction</code>, and using the
     * <code>dynamicPayloadTypes</code> registry to handle dynamic payload type registrations. The type
     * (e.g. audio/video) of the media description is determined via from the type of the first
     * {@link MediaFormat} in the <code>formats</code> list.
     *
     * @param creator indicates whether the person who originally created
     * this content was the initiator or the responder of the jingle session
     * @param contentName the name of the content element as indicator by the creator or,
     * in case we are the creators: as we'd like it to be.
     * @param formats the list of formats that should be advertised in the newly created content extension.
     * @param senders indicates the direction of the media in this stream.
     * @param rtpExtensions a list of <code>RTPExtension</code>s supported by the <code>MediaDevice</code>
     * that we will be advertising.
     * @param dynamicPayloadTypes a reference to the <code>DynamicPayloadTypeRegistry</code>
     * that we should be using to lookup and register dynamic RTP mappings.
     * @param rtpExtensionsRegistry a reference to the <code>DynamicRTPExtensionRegistry</code>
     * that we should be using to lookup and register URN to ID mappings.
     * @return the newly create SDP <code>MediaDescription</code>.
     */
    public static JingleContent createDescription(Creator creator, String contentName, Senders senders,
            List<MediaFormat> formats, List<RTPExtension> rtpExtensions, DynamicPayloadTypeRegistry dynamicPayloadTypes,
            DynamicRTPExtensionsRegistry rtpExtensionsRegistry, boolean rtcpmux, boolean imgattr)
    {
        JingleContent.Builder cBuilder = JingleContent.getBuilder()
                .setCreator(creator)
                .setName(contentName);

        // senders - only if we have them and if they are different from default
        if (senders != null && senders != JingleContent.Senders.both) {
            cBuilder.setSenders(senders);
        }

        // RTP description
        RtpDescription.Builder rtpBuilder = RtpDescription.getBuilder()
                .setMedia(formats.get(0).getMediaType().toString());

        // now fill in the RTP description
        for (MediaFormat fmt : formats) {
            // remove FORMAT_PARAMETER_ATTR_IMAGEATTR if not found in the earlier format offered
            if (fmt.hasParameter(FORMAT_PARAMETER_ATTR_IMAGEATTR) && !imgattr) {
                fmt.removeParameter(FORMAT_PARAMETER_ATTR_IMAGEATTR);
            }
            rtpBuilder.addChildElement(formatToPayloadType(fmt, dynamicPayloadTypes));
        }

        // RTPExtension attributes
        if (rtpExtensions != null && rtpExtensions.size() > 0) {
            for (RTPExtension extension : rtpExtensions) {
                byte extID = rtpExtensionsRegistry.obtainExtensionMapping(extension);
                URI uri = extension.getURI();
                MediaDirection extDirection = extension.getDirection();
                String attributes = extension.getExtensionAttributes();
                Senders sendersEnum = getSenders(extDirection, false);

                RtpHeader.Builder builder = RtpHeader.getBuilder()
                        .setURI(uri)
                        .setSenders(sendersEnum)
                        .setID(Byte.toString(extID))
                        .setExtAttributes(attributes);

                rtpBuilder.addChildElement(builder.build());
            }
        }

        /*
         * @see RtcpMux per XEP-0167: Jingle RTP Sessions 1.2.1 (2020-09-29)
         * https://xmpp.org/extensions/xep-0167.html#format
         */
        if (rtcpmux) {
            rtpBuilder.addChildElement(RtcpMux.builder(RtpDescription.NAMESPACE).build());
        }

        // RTP description
        cBuilder.addChildElement(rtpBuilder.build());
        return cBuilder.build();
    }

    /**
     * Converts a specific {@link MediaFormat} into a new {@link PayloadType} instance.
     *
     * @param format the <code>MediaFormat</code> we'd like to convert.
     * @param ptRegistry the {@link DynamicPayloadTypeRegistry} to use for formats that don't have a static pt number.
     * @return the new <code>PayloadTypeExtensionElement</code> which contains <code>format</code>'s parameters.
     */
    public static PayloadType formatToPayloadType(MediaFormat format, DynamicPayloadTypeRegistry ptRegistry)
    {
        PayloadType.Builder ptBuilder = PayloadType.builder(RtpDescription.NAMESPACE);

        int payloadType = format.getRTPPayloadType();
        if (payloadType == MediaFormat.RTP_PAYLOAD_TYPE_UNKNOWN)
            payloadType = ptRegistry.obtainPayloadTypeNumber(format);

        ptBuilder.setId(payloadType);
        ptBuilder.setName(format.getEncoding());

        if (format instanceof AudioMediaFormat)
            ptBuilder.setChannels(((AudioMediaFormat) format).getChannels());

        ptBuilder.setClockrate((int) format.getClockRate());

        /*
         * Add the format parameters and the advanced attributes (as parameter packet extensions).
         */
        for (Map.Entry<String, String> entry : format.getFormatParameters().entrySet()) {
            ptBuilder.addParameter(ParameterElement.builder(RtpDescription.NAMESPACE)
                    .setNameValue(entry.getKey(), entry.getValue())
                    .build());
        }

        for (Map.Entry<String, String> entry : format.getAdvancedAttributes().entrySet()) {
            ptBuilder.addParameter(ParameterElement.builder(RtpDescription.NAMESPACE)
                    .setNameValue(entry.getKey(), entry.getValue())
                    .build());
        }
        return ptBuilder.build();
    }

    /**
     * Returns the <code>MediaType</code> for <code>content</code> by looking for it in the <code>content</code>'s
     * <code>description</code>, if any.
     *
     * @param content the content to return the <code>MediaType</code> of
     * @return the <code>MediaType</code> for <code>content</code> by looking for it in the <code>content</code>'s
     * <code>description</code>, if any. <code>contentName</code>
     */
    public static MediaType getMediaType(JingleContent content)
    {
        if (content == null)
            return null;

        // We will use content name for determining media type if no RTP description is present(SCTP connection case)
        String mediaTypeName = content.getName();

        RtpDescription desc = content.getFirstChildElement(RtpDescription.class);
        if (desc != null) {
            String rtpMedia = desc.getMedia().toLowerCase();
            if (StringUtils.isNotEmpty(rtpMedia)) {
                mediaTypeName = rtpMedia;
            }
        }
        if ("application".equals(mediaTypeName)) {
            return MediaType.DATA;
        }
        return MediaType.parseString(mediaTypeName);
    }
}
