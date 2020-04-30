/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.xmpp.extensions.jibri;

import org.xmpp.extensions.AbstractExtensionElement;
import org.xmpp.extensions.DefaultExtensionElementProvider;
import org.xmpp.extensions.health.HealthStatusExtension;

import org.jivesoftware.smack.provider.ProviderManager;

import javax.xml.namespace.QName;

/**
 * Status extension included in MUC presence by Jibri to indicate it's status. One of: <li>idle</li>
 * - the instance is idle and can be used for recording <li>busy</li> - the instance is currently
 * recording or doing something very important and should not be disturbed
 *
 * @author Eng Chong Meng
 */
public class JibriStatusExtension extends AbstractExtensionElement
{
    /**
     * The namespace of this packet extension.
     */
    public static final String NAMESPACE = JibriIq.NAMESPACE;

    /**
     * XML element name of this packet extension.
     */
    public static final String ELEMENT = "jibri-status";

    public static final QName QNAME = new QName(NAMESPACE, ELEMENT);

    /**
     * Creates new instance of <tt>VideoMutedExtensionElement</tt>.
     */
    public JibriStatusExtension()
    {
        super(ELEMENT, NAMESPACE);
    }

    static public void registerExtensionProvider()
    {
        ProviderManager.addExtensionProvider(ELEMENT, NAMESPACE,
                new DefaultExtensionElementProvider<>(JibriStatusExtension.class));
    }

    public JibriBusyStatusExtension getBusyStatus()
    {
        return getChildExtension(JibriBusyStatusExtension.class);
    }

    public void setBusyStatus(JibriBusyStatusExtension busyStatus)
    {
        setChildExtension(busyStatus);
    }

    public HealthStatusExtension getHealthStatus()
    {
        return getChildExtension(HealthStatusExtension.class);
    }

    public void setHealthStatus(HealthStatusExtension healthStatus)
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
        return getHealthStatus().getStatus().equals(HealthStatusExtension.Health.HEALTHY) &&
                getBusyStatus().getStatus().equals(JibriBusyStatusExtension.BusyStatus.IDLE);
    }
}
