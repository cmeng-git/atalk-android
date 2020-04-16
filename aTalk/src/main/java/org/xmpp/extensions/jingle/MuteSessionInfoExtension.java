/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.xmpp.extensions.jingle;

/**
 * Represents the <tt>mute</tt> and <tt>unmute</tt> session info types.
 *
 * @author Emil Ivov
 */
public class MuteSessionInfoExtension extends SessionInfoExtension
{
    /**
     * The name of the <tt>name</tt> mute attribute.
     */
    public static final String NAME_ATTR_VALUE = "name";

    /**
     * Creates a <tt>SessionInfoExtensionElement</tt> instance corresponding to either the
     * {@link SessionInfoType#mute} or {@link SessionInfoType#unmute} types according to the value
     * of the <tt>mute</tt> parameter.
     *
     * @param mute <tt>true</tt> if the new instance is to be of the {@link SessionInfoType#mute} type
     * and <tt>false</tt> for a {@link SessionInfoType#unmute}
     * @param name the name of the session to be muted or <tt>null</tt> if the element pertains to all active sessions
     */
    public MuteSessionInfoExtension(boolean mute, String name)
    {
        super(mute ? SessionInfoType.mute : SessionInfoType.unmute);
        setAttribute(NAME_ATTR_VALUE, name);

    }

    /**
     * Determines if this session info packet represents a mute.
     *
     * @return <tt>true</tt> if this packet represents a {@link SessionInfoType#mute} and <tt>false</tt> otherwise.
     */
    public boolean isMute()
    {
        return getType() == SessionInfoType.mute;
    }

    /**
     * Returns the name of the session that this extension is pertaining to or <tt>null</tt> if it
     * is referring to all active sessions.
     *
     * @return the name of the session that this extension is pertaining to or <tt>null</tt> if it
     * is referring to all active sessions.
     */
    public String getName()
    {
        return getAttributeAsString(NAME_ATTR_VALUE);
    }
}
