/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.xmpp.extensions.jitsimeet;

import org.xmpp.extensions.AbstractExtensionElement;
import org.xmpp.extensions.colibri.SourceExtension;

import org.jxmpp.jid.Jid;
import org.jxmpp.jid.impl.JidCreate;
import org.jxmpp.stringprep.XmppStringprepException;

/**
 * Packet extension is used to signal owner of media SSRC in jitsi-meet. Owner attribute stores MUC
 * JID of the user to whom it belongs. This extension is inserted as a child of {@link SourceExtension}
 * in 'session-initiate', 'source-add' and 'source-remove' Jingle IQs sent by the focus(Jicofo).
 *
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
public class SSRCInfoExtension extends AbstractExtensionElement
{
    /**
     * XML namespace of this packets extension.
     */
    public static final java.lang.String NAMESPACE = "http://jitsi.org/jitmeet";

    /**
     * XML element name of this packets extension.
     */
    public static final String ELEMENT = "ssrc-info";

    /**
     * Attribute stores owner JID of parent {@link SourceExtension}.
     */
    public static final String OWNER_ATTR_NAME = "owner";

    /**
     * Attribute stores the type of video SSRC. Can be {@link #CAMERA_VIDEO_TYPE} or
     * {@link #SCREEN_VIDEO_TYPE}.
     */
    public static final String VIDEO_TYPE_ATTR_NAME = "video-type";

    /**
     * Camera video type constant. Inidcates that the user is sending his camera video.
     */
    public static final String CAMERA_VIDEO_TYPE = "camera";

    /**
     * Screen video type constant. Indicates that the user is sharing his screen.
     */
    public static final String SCREEN_VIDEO_TYPE = "screen";

    /**
     * Creates new instance of <tt>SSRCInfoExtensionElement</tt>.
     */
    public SSRCInfoExtension()
    {
        super(ELEMENT, NAMESPACE);
    }

    /**
     * Returns the value of {@link #OWNER_ATTR_NAME}.
     *
     * @return MUC JID of SSRC owner stored by this instance or <tt>null</tt> if empty.
     */
    public Jid getOwner()
    {
        try {
            return JidCreate.from(getAttributeAsString(OWNER_ATTR_NAME));
        } catch (XmppStringprepException | IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid owner", e);
        }
    }

    /**
     * Sets the value of {@link #OWNER_ATTR_NAME}.
     *
     * @param owner MUC JID of SSRC owner to be stored in this packet extension.
     */
    public void setOwner(Jid owner)
    {
        setAttribute(OWNER_ATTR_NAME, owner);
    }

    /**
     * Returns the value of {@link #VIDEO_TYPE_ATTR_NAME}.
     *
     * @return {@link #CAMERA_VIDEO_TYPE}, {@link #SCREEN_VIDEO_TYPE} or <tt>null</tt> if not
     * specified or if media SSRC is not a video.
     */
    public String getVideoType()
    {
        return getAttributeAsString(VIDEO_TYPE_ATTR_NAME);
    }

    /**
     * Sets the type of video SSRC.
     *
     * @param videoType {@link #CAMERA_VIDEO_TYPE}, {@link #SCREEN_VIDEO_TYPE} or <tt>null</tt> if not
     * specified or if media SSRC is not a video.
     */
    public void setVideoType(String videoType)
    {
        setAttribute(VIDEO_TYPE_ATTR_NAME, videoType);
    }
}
