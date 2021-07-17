/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.xmpp.extensions.jingle;

import org.xmpp.extensions.AbstractExtensionElement;
import org.xmpp.extensions.colibri.WebSocketExtension;

import org.jivesoftware.smack.packet.ExtensionElement;

import java.util.ArrayList;
import java.util.List;

import javax.xml.namespace.QName;

/**
 * An {@link AbstractExtensionElement} implementation for transport elements.
 * i.e. XEP-0176: Jingle ICE-UDP Transport Method; https://xmpp.org/extensions/xep-0176.html
 *
 * @author Emil Ivov
 * @author Lyubomir Marinov
 * @author Eng Chong Meng
 */
public class IceUdpTransportExtension extends AbstractExtensionElement
{
    /**
     * The name of the "transport" element.
     */
    public static final String NAMESPACE = "urn:xmpp:jingle:transports:ice-udp:1";

    /**
     * The name of the "transport" element.
     */
    public static final String ELEMENT = "transport";

    public static final QName QNAME = new QName(NAMESPACE, ELEMENT);

    /**
     * The name of the <tt>pwd</tt> ICE attribute.
     */
    public static final String PWD_ATTR_NAME = "pwd";

    /**
     * The name of the <tt>ufrag</tt> ICE attribute.
     */
    public static final String UFRAG_ATTR_NAME = "ufrag";

    /**
     * A list of one or more candidates representing each of the initiator's higher-priority
     * transport candidates as determined in accordance with the ICE methodology.
     */
    private final List<CandidateExtension> candidateList = new ArrayList<>();

    /**
     * Once the parties have connectivity and therefore the initiator has completed ICE as
     * explained in RFC 5245, the initiator MAY communicate the in-use candidate pair in the
     * signalling channel by sending a transport-info message that contains a "remote-candidate" element
     */
    private RemoteCandidateExtension remoteCandidate;

    /**
     * Creates a new {@link IceUdpTransportExtension} instance.
     */
    public IceUdpTransportExtension()
    {
        super(ELEMENT, NAMESPACE);
    }

    /**
     * Creates a new {@link IceUdpTransportExtension} instance with the specified
     * <tt>namespace</tt> and <tt>elementName</tt>. The purpose of this method is to allow
     * {@link RawUdpTransportExtension} to extend this class.
     *
     * @param namespace the XML namespace that the instance should belong to.
     * @param elementName the name of the element that we would be representing.
     */
    protected IceUdpTransportExtension(String namespace, String elementName)
    {
        super(elementName, namespace);
    }

    /**
     * Sets the ICE defined password attribute.
     *
     * @param pwd a password <tt>String</tt> as defined in RFC 5245
     */
    public void setPassword(String pwd)
    {
        super.setAttribute(PWD_ATTR_NAME, pwd);
    }

    /**
     * Returns the ICE defined password attribute.
     *
     * @return a password <tt>String</tt> as defined in RFC 5245
     */
    public String getPassword()
    {
        return super.getAttributeAsString(PWD_ATTR_NAME);
    }

    /**
     * Sets the ICE defined user fragment attribute.
     *
     * @param ufrag a user fragment <tt>String</tt> as defined in RFC 5245
     */
    public void setUfrag(String ufrag)
    {
        super.setAttribute(UFRAG_ATTR_NAME, ufrag);
    }

    /**
     * Returns the ICE defined user fragment attribute.
     *
     * @return a user fragment <tt>String</tt> as defined in RFC 5245
     */
    public String getUfrag()
    {
        return super.getAttributeAsString(UFRAG_ATTR_NAME);
    }

    /**
     * Returns this element's child (local or remote) candidate elements.
     *
     * @return this element's child (local or remote) candidate elements.
     */
    @Override
    public List<? extends ExtensionElement> getChildExtensions()
    {
        List<ExtensionElement> childExtensions = new ArrayList<>();
        List<? extends ExtensionElement> superChildExtensions = super.getChildExtensions();
        childExtensions.addAll(superChildExtensions);

        synchronized (candidateList) {
            if (candidateList.size() > 0)
                childExtensions.addAll(candidateList);
            else if (remoteCandidate != null)
                childExtensions.add(remoteCandidate);
        }
        return childExtensions;
    }

    /**
     * Adds <tt>candidate</tt> to the list of {@link CandidateExtension}s registered with this transport.
     *
     * @param candidate the new {@link CandidateExtension} to add to this transport element.
     */
    public void addCandidate(CandidateExtension candidate)
    {
        synchronized (candidateList) {
            candidateList.add(candidate);
        }
    }

    /**
     * Removes <tt>candidate</tt> from the list of {@link CandidateExtension}s registered with this transport.
     *
     * @param candidate the <tt>CandidateExtensionElement</tt> to remove from this transport element
     * @return <tt>true</tt> if the list of <tt>CandidateExtensionElement</tt>s registered with this
     * transport contained the specified <tt>candidate</tt>
     */
    public boolean removeCandidate(CandidateExtension candidate)
    {
        synchronized (candidateList) {
            return candidateList.remove(candidate);
        }
    }

    /**
     * Returns the list of {@link CandidateExtension}s currently registered with this transport.
     *
     * @return the list of {@link CandidateExtension}s currently registered with this transport.
     */
    public List<CandidateExtension> getCandidateList()
    {
        synchronized (candidateList) {
            return new ArrayList<>(candidateList);
        }
    }

    /**
     * Sets <tt>candidate</tt> as the in-use candidate after ICE has terminated.
     *
     * @param candidate the new {@link CandidateExtension} to set as an in-use candidate for this session.
     */
    public void setRemoteCandidate(RemoteCandidateExtension candidate)
    {
        this.remoteCandidate = candidate;
    }

    /**
     * Returns the in-use <tt>candidate</tt> for this session.
     *
     * @return Returns the in-use <tt>candidate</tt> for this session.
     */
    public RemoteCandidateExtension getRemoteCandidate()
    {
        return remoteCandidate;
    }

    /**
     * Tries to determine whether <tt>childExtension</tt> is a {@link CandidateExtension}, a
     * {@link RemoteCandidateExtension} or something else and then adds it as such.
     *
     * @param childExtension the extension we'd like to add here.
     */
    @Override
    public void addChildExtension(ExtensionElement childExtension)
    {
        // first check for RemoteCandidate because they extend Candidate.
        if (childExtension instanceof RemoteCandidateExtension)
            setRemoteCandidate((RemoteCandidateExtension) childExtension);

        else if (childExtension instanceof CandidateExtension)
            addCandidate((CandidateExtension) childExtension);

        else
            super.addChildExtension(childExtension);
    }

    /**
     * Checks whether an 'rtcp-mux' extension has been added to this <tt>IceUdpTransportExtensionElement</tt>.
     *
     * @return <tt>true</tt> if this <tt>IceUdpTransportExtensionElement</tt> has a child with the 'rtcp-mux' name.
     */
    public boolean isRtcpMux()
    {
        for (ExtensionElement packetExtension : getChildExtensions()) {
            if (RtcpmuxExtension.ELEMENT.equals(packetExtension.getElementName()))
                return true;
        }
        return false;
    }

    /**
     * Clones a specific <tt>IceUdpTransportExtensionElement</tt> and its candidates.
     *
     * @param src the <tt>IceUdpTransportExtensionElement</tt> to be cloned
     * @return a new <tt>IceUdpTransportExtensionElement</tt> instance which has the same run-time
     * type, attributes, namespace, text and candidates as the specified <tt>src</tt>
     */
    public static IceUdpTransportExtension cloneTransportAndCandidates(IceUdpTransportExtension src)
    {
        return cloneTransportAndCandidates(src, false);
    }

    /**
     * Clones a specific <tt>IceUdpTransportExtensionElement</tt> and its candidates.
     *
     * @param src the <tt>IceUdpTransportExtensionElement</tt> to be cloned
     * @param copyDtls if <tt>true</tt> will also copy {@link DtlsFingerprintExtension}.
     * @return a new <tt>IceUdpTransportExtensionElement</tt> instance which has the same run-time
     * type, attributes, namespace, text and candidates as the specified <tt>src</tt>
     */
    public static IceUdpTransportExtension cloneTransportAndCandidates(
            final IceUdpTransportExtension src, boolean copyDtls)
    {
        IceUdpTransportExtension dst = null;
        if (src != null) {
            dst = AbstractExtensionElement.clone(src);
            // Copy candidates
            for (CandidateExtension srcCand : src.getCandidateList()) {
                if (!(srcCand instanceof RemoteCandidateExtension))
                    dst.addCandidate(AbstractExtensionElement.clone(srcCand));
            }
            // Copy "web-socket" extensions.
            // cmeng - NPE for src during testing; force to use final hopefully it helps
            for (WebSocketExtension wspe : src.getChildExtensionsOfType(WebSocketExtension.class)) {
                dst.addChildExtension(new WebSocketExtension(wspe.getUrl()));
            }
            // Copy RTCP MUX
            if (src.isRtcpMux()) {
                dst.addChildExtension(new RtcpmuxExtension());
            }
            // Optionally copy DTLS
            if (copyDtls) {
                for (DtlsFingerprintExtension dtlsFingerprint
                        : src.getChildExtensionsOfType(DtlsFingerprintExtension.class)) {
                    DtlsFingerprintExtension copy = new DtlsFingerprintExtension();

                    copy.setFingerprint(dtlsFingerprint.getFingerprint());
                    copy.setHash(dtlsFingerprint.getHash());
                    // copy.setRequired(dtlsFingerprint.getRequired()); // obsoleted
                    copy.setSetup(dtlsFingerprint.getSetup());
                    dst.addChildExtension(copy);
                }
            }
        }
        return dst;
    }
}
