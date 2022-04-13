/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.jivesoftware.smackx.jitsimeet;

import org.jivesoftware.smackx.AbstractExtensionElement;

import org.jxmpp.jid.Jid;
import org.jxmpp.jid.impl.JidCreate;
import org.jxmpp.stringprep.XmppStringprepException;

/**
 * Packet extension is used to signal owner of media SSRC in jitsi-meet. Owner attribute stores MUC
 * JID of the user to whom it belongs. This extension is inserted as a child of {@link SdpSource}
 * in 'session-initiate', 'source-add' and 'source-remove' Jingle IQs sent by the focus(Jicofo).
 *
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
public class SSRCInfoExtension extends AbstractExtensionElement
{
    /**
     * XML element name of this packets extension.
     */
    public static final String ELEMENT = "ssrc-info";

    /**
     * XML namespace of this packets extension.
     */
    public static final java.lang.String NAMESPACE = "http://jitsi.org/jitmeet";

    /**
     * Attribute stores owner JID of parent {@link SdpSource}.
     */
    public static final String ATTR_OWNER = "owner";

    /**
     * Attribute stores the type of video SSRC. Can be {@link #ATTR_CAMERA} or
     * {@link #ATTR_SCREEN}.
     */
    public static final String ATTR_VIDEO_TYPE = "video-type";

    /**
     * Camera video type constant. Inidcates that the user is sending his camera video.
     */
    public static final String ATTR_CAMERA = "camera";

    /**
     * Screen video type constant. Indicates that the user is sharing his screen.
     */
    public static final String ATTR_SCREEN = "screen";

    /**
     * Creates new instance of <code>SSRCInfoExtensionElement</code>.
     */
    public SSRCInfoExtension()
    {
        super(ELEMENT, NAMESPACE);
    }

    /**
     * Returns the value of {@link #ATTR_OWNER}.
     *
     * @return MUC JID of SSRC owner stored by this instance or <code>null</code> if empty.
     */
    public Jid getOwner()
    {
        try {
            return JidCreate.from(getAttributeAsString(ATTR_OWNER));
        } catch (XmppStringprepException | IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid owner", e);
        }
    }

    /**
     * Sets the value of {@link #ATTR_OWNER}.
     *
     * @param owner MUC JID of SSRC owner to be stored in this packet extension.
     */
    public void setOwner(Jid owner)
    {
        setAttribute(ATTR_OWNER, owner);
    }

    /**
     * Returns the value of {@link #ATTR_VIDEO_TYPE}.
     *
     * @return {@link #ATTR_CAMERA}, {@link #ATTR_SCREEN} or <code>null</code> if not
     * specified or if media SSRC is not a video.
     */
    public String getVideoType()
    {
        return getAttributeAsString(ATTR_VIDEO_TYPE);
    }

    /**
     * Sets the type of video SSRC.
     *
     * @param videoType {@link #ATTR_CAMERA}, {@link #ATTR_SCREEN} or <code>null</code> if not
     * specified or if media SSRC is not a video.
     */
    public void setVideoType(String videoType)
    {
        setAttribute(ATTR_VIDEO_TYPE, videoType);
    }
}
