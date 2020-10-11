/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.plugin.sipaccregwizz;

import net.java.sip.communicator.service.certificate.CertificateService;
import net.java.sip.communicator.service.gui.AccountRegistrationWizard;
import net.java.sip.communicator.service.gui.UIService;
import net.java.sip.communicator.service.protocol.ProtocolNames;
import net.java.sip.communicator.service.protocol.ProtocolProviderFactory;

import org.atalk.service.configuration.ConfigurationService;
import org.osgi.framework.*;

import java.util.Hashtable;

import static timber.log.Timber.e;

/**
 * Registers the <tt>SIPAccountRegistrationWizard</tt> in the UI Service.
 *
 * @author Yana Stamcheva
 */
public class SIPAccountRegistrationActivator implements BundleActivator
{
    public static BundleContext bundleContext;

    /**
     * A reference to the configuration service.
     */
    private static ConfigurationService configService;

    private static AccountRegistrationImpl sipWizard;

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

        ServiceReference<?> uiServiceRef =
                bundleContext.getServiceReference(UIService.class.getName());

        sipWizard = new AccountRegistrationImpl();

        Hashtable<String, String> containerFilter
                = new Hashtable<String, String>();

        containerFilter.put(
                ProtocolProviderFactory.PROTOCOL,
                ProtocolNames.SIP);

        bundleContext.registerService(
                AccountRegistrationWizard.class.getName(),
                sipWizard,
                containerFilter);
    }

    public void stop(BundleContext bundleContext)
            throws Exception
    {
    }

    /**
     * Returns the <tt>ProtocolProviderFactory</tt> for the SIP protocol.
     *
     * @return the <tt>ProtocolProviderFactory</tt> for the SIP protocol
     */
    public static ProtocolProviderFactory getSIPProtocolProviderFactory()
    {

        ServiceReference<?>[] serRefs = null;

        String osgiFilter =
                "(" + ProtocolProviderFactory.PROTOCOL + "=" + ProtocolNames.SIP
                        + ")";

        try {
            serRefs =
                    bundleContext.getServiceReferences(
                            ProtocolProviderFactory.class.getName(), osgiFilter);
        } catch (InvalidSyntaxException ex) {
            e("SIPAccRegWizzActivator: %s", ex.getMessage());
            return null;
        }

        return (ProtocolProviderFactory) bundleContext.getService(serRefs[0]);
    }

    /**
     * Returns the <tt>ConfigurationService</tt> obtained from the bundle
     * context.
     *
     * @return the <tt>ConfigurationService</tt> obtained from the bundle
     * context
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
     * Returns the <tt>CertificateService</tt> obtained from the bundle
     * context.
     *
     * @return the <tt>CertificateService</tt> obtained from the bundle
     * context
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
