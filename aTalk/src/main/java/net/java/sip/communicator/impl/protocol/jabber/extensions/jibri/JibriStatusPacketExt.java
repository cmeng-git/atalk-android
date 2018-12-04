/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.impl.protocol.jabber.extensions.jibri;

import net.java.sip.communicator.impl.protocol.jabber.extensions.AbstractPacketExtension;
import net.java.sip.communicator.impl.protocol.jabber.extensions.DefaultPacketExtensionProvider;
import net.java.sip.communicator.impl.protocol.jabber.extensions.health.HealthStatusPacketExt;

import org.jivesoftware.smack.provider.ProviderManager;

/**
 * Status extension included in MUC presence by Jibri to indicate it's status. One of: <li>idle</li>
 * - the instance is idle and can be used for recording <li>busy</li> - the instance is currently
 * recording or doing something very important and should not be disturbed
 *
 * @author Eng Chong Meng
 */
public class JibriStatusPacketExt extends AbstractPacketExtension
{
    /**
     * The namespace of this packet extension.
     */
    public static final String NAMESPACE = JibriIq.NAMESPACE;

    /**
     * XML element name of this packet extension.
     */
    public static final String ELEMENT_NAME = "jibri-status";

    /**
     * Creates new instance of <tt>VideoMutedExtension</tt>.
     */
    public JibriStatusPacketExt()
    {
        super(NAMESPACE, ELEMENT_NAME);
    }

    static public void registerExtensionProvider()
    {
        ProviderManager.addExtensionProvider(ELEMENT_NAME, NAMESPACE,
                new DefaultPacketExtensionProvider<>(JibriStatusPacketExt.class));
    }

    public JibriBusyStatusPacketExt getBusyStatus()
    {
        return getChildExtension(JibriBusyStatusPacketExt.class);
    }

    public void setBusyStatus(JibriBusyStatusPacketExt busyStatus)
    {
        setChildExtension(busyStatus);
    }

    public HealthStatusPacketExt getHealthStatus()
    {
        return getChildExtension(HealthStatusPacketExt.class);
    }

    public void setHealthStatus(HealthStatusPacketExt healthStatus)
    {
        setChildExtension(healthStatus);
    }

    /**
     * Provides a convenient helper to determine if this Jibri is available or not by looking at
     * both the busy status and the health status.
     * @return true if this Jibri should be considered available for use according to this presence, false
     * otherwise
     */
    public boolean isAvailable()
    {
        return getHealthStatus().getStatus().equals(HealthStatusPacketExt.Health.HEALTHY) &&
                getBusyStatus().getStatus().equals(JibriBusyStatusPacketExt.BusyStatus.IDLE);
    }
}
