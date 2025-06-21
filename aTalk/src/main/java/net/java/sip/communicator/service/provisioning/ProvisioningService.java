/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.provisioning;

/**
 * Provisioning service.
 *
 * @author Sebastien Vincent
 * @author Eng Chong Meng
 */
public interface ProvisioningService {
    /**
     * Indicates if the provisioning has been enabled.
     *
     * @return <code>true</code> if the provisioning is enabled, <code>false</code> - otherwise
     */
    String getProvisioningMethod();

    /**
     * Enables the provisioning with the given method. If the provisioningMethod is null disables the provisioning.
     *
     * @param provisioningMethod the provisioning method
     */
    void setProvisioningMethod(String provisioningMethod);

    /**
     * Returns the provisioning URI.
     *
     * @return the provisioning URI
     */
    String getProvisioningUri();
}
