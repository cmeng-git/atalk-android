/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.plugin.jabberaccregwizz;

import net.java.sip.communicator.service.certificate.CertificateService;
import net.java.sip.communicator.service.gui.AccountRegistrationWizard;
import net.java.sip.communicator.service.gui.UIService;
import net.java.sip.communicator.service.protocol.ProtocolNames;
import net.java.sip.communicator.service.protocol.ProtocolProviderFactory;

import org.atalk.service.configuration.ConfigurationService;
import org.osgi.framework.*;

import java.util.Hashtable;

/**
 * Registers the <tt>SIPAccountRegistrationWizard</tt> in the UI Service.
 *
 * @author Yana Stamcheva
 * @author Eng Chong Meng
 */
public class JabberAccountRegistrationActivator implements BundleActivator
{
    public static BundleContext bundleContext;

    /**
     * A reference to the configuration service.
     */
    private static ConfigurationService configService;

    private static AccountRegistrationImpl jabberRegistration;

    private static CertificateService certService;

    /**
     * Starts this bundle.
     *
     * @param bc BundleContext
     * @throws Exception
     */
    public void start(BundleContext bc)
            throws Exception
    {
        bundleContext = bc;
        ServiceReference<?> uiServiceRef = bundleContext.getServiceReference(UIService.class.getName());
        jabberRegistration = new AccountRegistrationImpl();
        Hashtable<String, String> containerFilter = new Hashtable<String, String>();
        containerFilter.put(ProtocolProviderFactory.PROTOCOL, ProtocolNames.JABBER);
        bundleContext.registerService(AccountRegistrationWizard.class.getName(), jabberRegistration, containerFilter);
    }

    public void stop(BundleContext bundleContext)
            throws Exception
    {
    }

    /**
     * Returns the <tt>ProtocolProviderFactory</tt> for the Jabber protocol.
     *
     * @return the <tt>ProtocolProviderFactory</tt> for the Jabber protocol
     */
    public static ProtocolProviderFactory getJabberProtocolProviderFactory()
    {
        return ProtocolProviderFactory.getProtocolProviderFactory(bundleContext, ProtocolNames.JABBER);
    }

    /**
     * Returns the <tt>ConfigurationService</tt> obtained from the bundle context.
     *
     * @return the <tt>ConfigurationService</tt> obtained from the bundle context
     */
    public static ConfigurationService getConfigurationService()
    {
        if (configService == null) {
            ServiceReference<?> serviceReference = bundleContext.getServiceReference(ConfigurationService.class.getName());
            configService = (ConfigurationService) bundleContext.getService(serviceReference);
        }
        return configService;
    }

    /**
     * Returns the <tt>CertificateService</tt> obtained from the bundle context.
     *
     * @return the <tt>CertificateService</tt> obtained from the bundle context
     */
    public static CertificateService getCertificateService()
    {
        if (certService == null) {
            ServiceReference<?> serviceReference
                    = bundleContext.getServiceReference(CertificateService.class.getName());
            certService = (CertificateService) bundleContext.getService(serviceReference);
        }
        return certService;
    }
}
