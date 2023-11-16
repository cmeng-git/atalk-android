/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.plugin.sipaccregwizz;

import static timber.log.Timber.e;

import net.java.sip.communicator.service.certificate.CertificateService;
import net.java.sip.communicator.service.gui.AccountRegistrationWizard;
import net.java.sip.communicator.service.gui.UIService;
import net.java.sip.communicator.service.protocol.ProtocolNames;
import net.java.sip.communicator.service.protocol.ProtocolProviderFactory;

import org.atalk.service.configuration.ConfigurationService;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceReference;

import java.util.Hashtable;

/**
 * Registers the <code>SIPAccountRegistrationWizard</code> in the UI Service.
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
     * Returns the <code>ProtocolProviderFactory</code> for the SIP protocol.
     *
     * @return the <code>ProtocolProviderFactory</code> for the SIP protocol
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
     * Returns the <code>ConfigurationService</code> obtained from the bundle
     * context.
     *
     * @return the <code>ConfigurationService</code> obtained from the bundle
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
     * Returns the <code>CertificateService</code> obtained from the bundle
     * context.
     *
     * @return the <code>CertificateService</code> obtained from the bundle
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
