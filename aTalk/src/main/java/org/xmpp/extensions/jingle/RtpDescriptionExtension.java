/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.xmpp.extensions.jingle;

import org.jivesoftware.smack.packet.ExtensionElement;
import org.xmpp.extensions.AbstractExtensionElement;

import java.util.ArrayList;
import java.util.List;

import javax.xml.namespace.QName;

import timber.log.Timber;

/**
 * Represents the content <tt>description</tt> elements described in
 * XEP-0167: Jingle RTP Sessions 1.2.0 (2020-04-22)
 *
 * Multiplexing RTP Data and Control Packets on a Single Port (April 2010),
 * https://tools.ietf.org/html/rfc5761 (5.1.3. Interactions with ICE) eem to propose
 * <rtpc-mux/> to be included in transport
 *
 * @author Emil Ivov
 * @author Eng Chong Meng
 */
public class RtpDescriptionExtension extends AbstractExtensionElement
{
    /**
     * The name space for RTP description elements.
     */
    public static final String NAMESPACE = "urn:xmpp:jingle:apps:rtp:1";

    /**
     * The name of the "description" element.
     */
    public static final String ELEMENT = "description";

    public static final QName QNAME = new QName(NAMESPACE, ELEMENT);

    /**
     * The name of the <tt>media</tt> description argument.
     */
    public static final String MEDIA_ATTR_NAME = "media";

    /**
     * The name of the <tt>ssrc</tt> description argument.
     */
    public static final String SSRC_ATTR_NAME = "ssrc";

    /**
     * The list of payload types that this description element contains.
     */
    private final List<PayloadTypeExtension> payloadTypes = new ArrayList<>();

    /**
     * An optional encryption element that contains encryption parameters for this session.
     */
    private EncryptionExtension encryption;

    /**
     * An optional encryption element that contains encryption parameters for this session.
     */
    private RtcpmuxExtension rtcpMux;

    /**
     * An optional bandwidth element that specifies the allowable or preferred bandwidth for use by
     * this application type.
     */
    private BandwidthExtension bandwidth;

    /**
     * A <tt>List</tt> of the optional <tt>extmap</tt> elements that allow negotiating RTP extension
     * headers as per RFC 5282.
     */
    private List<RTPHdrExtExtension> extmapList = new ArrayList<>();

    /**
     * The combined list of all child elements that this extension contains.
     */
    private List<ExtensionElement> children;

    /**
     * Creates a new <tt>RtpDescriptionExtensionElement</tt>.
     */
    public RtpDescriptionExtension()
    {
        super(ELEMENT, NAMESPACE);
    }

    /**
     * Specifies the media type for the stream that this description element represents, such as "audio" or "video".
     *
     * @param media the media type for the stream that this element represents such as "audio" or "video".
     */
    public void setMedia(String media)
    {
        super.setAttribute(MEDIA_ATTR_NAME, media);
    }

    /**
     * Returns the media type for the stream that this description element represents, such as "audio" or "video".
     *
     * @return the media type for the stream that this description element represents, such as "audio" or "video".
     */
    public String getMedia()
    {
        return getAttributeAsString(MEDIA_ATTR_NAME);
    }

    /**
     * Sets the synchronization source ID (SSRC as per RFC 3550) that the stream represented by this
     * description element will be using.
     *
     * @param ssrc the SSRC ID that the RTP stream represented here will be using.
     */
    public void setSsrc(String ssrc)
    {
        super.setAttribute(SSRC_ATTR_NAME, ssrc);
    }

    /**
     * Returns the synchronization source ID (SSRC as per RFC 3550) that the stream represented by
     * this description element will be using.
     *
     * @return the synchronization source ID (SSRC as per RFC 3550) that the stream represented by
     * this description element will be using.
     */
    public String getSsrc()
    {
        return getAttributeAsString(SSRC_ATTR_NAME);
    }

    /**
     * Adds a new payload type to this description element.
     *
     * @param payloadType the new payload to add.
     */
    public void addPayloadType(PayloadTypeExtension payloadType)
    {
        this.payloadTypes.add(payloadType);
    }

    /**
     * Returns a <b>reference</b> to the list of payload types that we have registered with this description so far.
     *
     * @return a <b>reference</b> to the list of payload types that we have registered with this description so far.
     */
    public List<PayloadTypeExtension> getPayloadTypes()
    {
        return payloadTypes;
    }

    /**
     * Returns all child elements that we currently have in this packet.
     *
     * @return the {@link List} of child elements currently registered with this packet.
     */
    @Override
    public List<? extends ExtensionElement> getChildExtensions()
    {
        if (children == null)
            children = new ArrayList<>();
        else
            children.clear();

        // payload types
        children.addAll(payloadTypes);

        // encryption element
        if (encryption != null)
            children.add(encryption);

        // bandwidth element
        if (bandwidth != null)
            children.add(bandwidth);

        // extmap elements
        if (extmapList != null)
            children.addAll(extmapList);

        children.addAll(super.getChildExtensions());
        return children;
    }

    /**
     * Casts <tt>childExtension</tt> to one of the extensions allowed here and sets the corresponding field.
     *
     * @param childExtension the extension we'd like to add here.
     */
    @Override
    public void addChildExtension(ExtensionElement childExtension)
    {
        if (childExtension instanceof PayloadTypeExtension)
            this.addPayloadType((PayloadTypeExtension) childExtension);

        else if (childExtension instanceof EncryptionExtension)
            this.setEncryption((EncryptionExtension) childExtension);

        else if (childExtension instanceof BandwidthExtension)
            this.setBandwidth((BandwidthExtension) childExtension);

        else if (childExtension instanceof RTPHdrExtExtension)
            this.addExtmap((RTPHdrExtExtension) childExtension);

        else {
            super.addChildExtension(childExtension);
            // Timber.w("Optional childExtension added: %s", childExtension.getClass().getSimpleName());
        }
    }

    /**
     * Sets the optional encryption element that contains encryption parameters for this session.
     *
     * @param encryption the encryption {@link ExtensionElement} we'd like to add to this packet.
     */
    public void setEncryption(EncryptionExtension encryption)
    {
        this.encryption = encryption;
    }

    /**
     * Returns the optional encryption element that contains encryption parameters for this session.
     *
     * @return the encryption {@link ExtensionElement} added to this packet or <tt>null</tt> if none has been set yet.
     */
    public EncryptionExtension getEncryption()
    {
        return encryption;
    }

    /**
     * Sets an optional bandwidth element that specifies the allowable or preferred bandwidth for
     * use by this application type.
     *
     * @param bandwidth the max/preferred bandwidth indication that we'd like to add to this packet.
     */
    public void setBandwidth(BandwidthExtension bandwidth)
    {
        this.bandwidth = bandwidth;
    }

    /**
     * Returns an optional bandwidth element that specifies the allowable or preferred bandwidth for
     * use by this application type.
     *
     * @return the max/preferred bandwidth set for this session or <tt>null</tt> if none has been set yet.
     */
    public BandwidthExtension getBandwidth()
    {
        return bandwidth;
    }

    /**
     * Adds an optional <tt>extmap</tt> element that allows negotiation RTP extension headers as per RFC 5282.
     *
     * @param extmap an optional <tt>extmap</tt> element that allows negotiation RTP extension headers as per RFC 5282.
     */
    public void addExtmap(RTPHdrExtExtension extmap)
    {
        this.extmapList.add(extmap);
    }

    /**
     * Returns a <tt>List</tt> of the optional <tt>extmap</tt> elements that allow negotiating RTP
     * extension headers as per RFC 5282.
     *
     * @return a <tt>List</tt> of the optional <tt>extmap</tt> elements that allow negotiating RTP
     * extension headers as per RFC 5282.
     */
    public List<RTPHdrExtExtension> getExtmapList()
    {
        return extmapList;
    }

    /**
     * Sets the optional rtcpmux element that contains rtcpmux parameters for this session.
     *
     * @param rtcpmux the rtcpmux {@link ExtensionElement} we'd like to add to this packet.
     */
    public void setRtcpMux(RtcpmuxExtension rtcpmux)
    {
        this.rtcpMux = rtcpmux;
    }

    /**
     * Returns the optional encryption element that contains encryption parameters for this session.
     *
     * @return the encryption {@link ExtensionElement} added to this packet or <tt>null</tt> if none has been set yet.
     */
    public RtcpmuxExtension getRtcpMux()
    {
        return rtcpMux;
    }


}
