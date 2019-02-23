/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.impl.protocol.jabber.extensions.jibri;

import net.java.sip.communicator.impl.protocol.jabber.extensions.AbstractExtensionElement;
import net.java.sip.communicator.impl.protocol.jabber.extensions.DefaultExtensionElementProvider;
import net.java.sip.communicator.impl.protocol.jabber.extensions.health.HealthStatusExtElement;

import org.jivesoftware.smack.provider.ProviderManager;

/**
 * Status extension included in MUC presence by Jibri to indicate it's status. One of: <li>idle</li>
 * - the instance is idle and can be used for recording <li>busy</li> - the instance is currently
 * recording or doing something very important and should not be disturbed
 *
 * @author Eng Chong Meng
 */
public class JibriStatusExtElement extends AbstractExtensionElement
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
     * Creates new instance of <tt>VideoMutedExtensionElement</tt>.
     */
    public JibriStatusExtElement()
    {
        super(NAMESPACE, ELEMENT_NAME);
    }

    static public void registerExtensionProvider()
    {
        ProviderManager.addExtensionProvider(ELEMENT_NAME, NAMESPACE,
                new DefaultExtensionElementProvider<>(JibriStatusExtElement.class));
    }

    public JibriBusyStatusExtElement getBusyStatus()
    {
        return getChildExtension(JibriBusyStatusExtElement.class);
    }

    public void setBusyStatus(JibriBusyStatusExtElement busyStatus)
    {
        setChildExtension(busyStatus);
    }

    public HealthStatusExtElement getHealthStatus()
    {
        return getChildExtension(HealthStatusExtElement.class);
    }

    public void setHealthStatus(HealthStatusExtElement healthStatus)
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
        return getHealthStatus().getStatus().equals(HealthStatusExtElement.Health.HEALTHY) &&
                getBusyStatus().getStatus().equals(JibriBusyStatusExtElement.BusyStatus.IDLE);
    }
}
