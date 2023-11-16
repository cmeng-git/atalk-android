/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.systray;

import net.java.sip.communicator.service.systray.event.SystrayPopupMessageListener;
import net.java.sip.communicator.util.ServiceUtils;

import org.atalk.service.configuration.ConfigurationService;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceEvent;
import org.osgi.framework.ServiceListener;
import org.osgi.framework.ServiceReference;

import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.List;

import timber.log.Timber;

/**
 * Base implementation of {@link SystrayService}. Manages
 * <code>PopupMessageHandler</code>s and <code>SystrayPopupMessageListener</code>s.
 *
 * @author Nicolas Chamouard
 * @author Yana Stamcheva
 * @author Lyubomir Marinov
 * @author Symphorien Wanko
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
public abstract class AbstractSystrayService implements SystrayService
{
    /**
     * OSGI bundle context
     */
    private final BundleContext bundleContext;

    /**
     * The popup handler currently used to show popup messages
     */
    private PopupMessageHandler activePopupMessageHandler;

    /**
     * A set of usable <code>PopupMessageHandler</code>
     */
    private final Hashtable<String, PopupMessageHandler> popupHandlerSet = new Hashtable<>();

    /**
     * List of listeners from early received calls to addPopupMessageListener.
     * Calls to addPopupMessageListener before the UIService is registered.
     */
    private List<SystrayPopupMessageListener> earlyAddedListeners = null;

    /**
     * Creates new instance of <code>AbstractSystrayService</code>.
     *
     * @param bundleContext OSGI bundle context that will be used by this
     * instance
     */
    public AbstractSystrayService(BundleContext bundleContext)
    {
        this.bundleContext = bundleContext;
    }

    /**
     * Registers given <code>PopupMessageHandler</code>.
     *
     * @param handler the <code>PopupMessageHandler</code> to be registered.
     */
    protected void addPopupHandler(PopupMessageHandler handler)
    {
        popupHandlerSet.put(handler.getClass().getName(), handler);
    }

    /**
     * Removes given <code>PopupMessageHandler</code>.
     *
     * @param handler the <code>PopupMessageHandler</code> to be removed.
     */
    protected void removePopupHandler(PopupMessageHandler handler)
    {
        popupHandlerSet.remove(handler.getClass().getName());
    }

    /**
     * Checks if given <code>handlerClass</code> is registered as a handler.
     *
     * @param handlerClass the class name to be checked.
     * @return <code>true</code> if given <code>handlerClass</code> is already registered as a handler.
     */
    protected boolean containsHandler(String handlerClass)
    {
        return popupHandlerSet.contains(handlerClass);
    }

    /**
     * Get the handler currently used by this implementation to popup message
     *
     * @return the current handler
     */
    public PopupMessageHandler getActivePopupMessageHandler()
    {
        return activePopupMessageHandler;
    }

    /**
     * Implements <code>SystraService#showPopupMessage()</code>
     *
     * @param popupMessage the message we will show
     */
    public void showPopupMessage(PopupMessage popupMessage)
    {
        // since popup handler could be loaded and unloaded on the fly,
        // we have to check if we currently have a valid one.
        if (activePopupMessageHandler != null)
            activePopupMessageHandler.showPopupMessage(popupMessage);
    }

    /**
     * Implements the <code>SystrayService.addPopupMessageListener</code> method.
     * If <code>activePopupHandler</code> is still not available record the listener so we can add him later.
     *
     * @param listener the listener to add
     */
    public void addPopupMessageListener(SystrayPopupMessageListener listener)
    {
        if (activePopupMessageHandler != null)
            activePopupMessageHandler.addPopupMessageListener(listener);
        else {
            if (earlyAddedListeners == null)
                earlyAddedListeners = new ArrayList<>();

            earlyAddedListeners.add(listener);
        }
    }

    /**
     * Implements the <code>SystrayService.removePopupMessageListener</code> method.
     *
     * @param listener the listener to remove
     */
    public void removePopupMessageListener(SystrayPopupMessageListener listener)
    {
        if (activePopupMessageHandler != null)
            activePopupMessageHandler.removePopupMessageListener(listener);
    }

    /**
     * Set the handler which will be used for popup message
     *
     * @param newHandler the handler to set. providing a null handler is like disabling popup.
     * @return the previously used popup handler
     */
    public PopupMessageHandler setActivePopupMessageHandler(PopupMessageHandler newHandler)
    {
        PopupMessageHandler oldHandler = activePopupMessageHandler;
        Timber.i("setting the following popup handler as active: %s", newHandler);

        activePopupMessageHandler = newHandler;
        // if we have received calls to addPopupMessageListener before
        // the UIService is registered we should add those listeners
        if (earlyAddedListeners != null) {
            for (SystrayPopupMessageListener l : earlyAddedListeners) {
                activePopupMessageHandler.addPopupMessageListener(l);
            }
            earlyAddedListeners.clear();
            earlyAddedListeners = null;
        }
        return oldHandler;
    }

    /**
     * Sets activePopupHandler to be the one with the highest preference index.
     */
    public void selectBestPopupMessageHandler()
    {
        PopupMessageHandler preferredHandler = null;
        int highestPrefIndex = 0;

        if (!popupHandlerSet.isEmpty()) {
            Enumeration<String> keys = popupHandlerSet.keys();

            while (keys.hasMoreElements()) {
                String handlerName = keys.nextElement();
                PopupMessageHandler h = popupHandlerSet.get(handlerName);
                if (h.getPreferenceIndex() > highestPrefIndex) {
                    highestPrefIndex = h.getPreferenceIndex();
                    preferredHandler = h;
                }
            }
            setActivePopupMessageHandler(preferredHandler);
        }
    }

    /**
     * Initializes popup handler by searching registered services for class <code>PopupMessageHandler</code>.
     */
    protected void initHandlers()
    {
        // Listens for new popup handlers
        try {
            bundleContext.addServiceListener(new ServiceListenerImpl(),
                    "(objectclass=" + PopupMessageHandler.class.getName() + ")");
        } catch (Exception e) {
            Timber.w("%s", e.getMessage());
        }

        // now we look if some handler has been registered before we start to listen
        ServiceReference<PopupMessageHandler>[] handlerRefs
                = ServiceUtils.getServiceReferences(bundleContext, PopupMessageHandler.class);

        if (handlerRefs.length != 0) {
            ConfigurationService config = ServiceUtils.getService(bundleContext, ConfigurationService.class);
            String configuredHandler = config.getString("systray.POPUP_HANDLER");

            for (ServiceReference<PopupMessageHandler> handlerRef : handlerRefs) {
                PopupMessageHandler handler = bundleContext.getService(handlerRef);
                String handlerName = handler.getClass().getName();

                if (!containsHandler(handlerName)) {
                    addPopupHandler(handler);
                    Timber.i("added the following popup handler : %s", handler);
                    if ((configuredHandler != null)
                            && configuredHandler.equals(handler.getClass().getName())) {
                        setActivePopupMessageHandler(handler);
                    }
                }
            }
            if (configuredHandler == null)
                selectBestPopupMessageHandler();
        }
    }

    /**
     * An implementation of <code>ServiceListener</code> we will use
     */
    private class ServiceListenerImpl implements ServiceListener
    {
        /**
         * implements <code>ServiceListener.serviceChanged</code>
         *
         * @param serviceEvent
         */
        public void serviceChanged(ServiceEvent serviceEvent)
        {
            try {
                Object service = bundleContext.getService(serviceEvent.getServiceReference());
                // Event filters don't work on Android
                if (!(service instanceof PopupMessageHandler))
                    return;

                PopupMessageHandler handler
                        = (PopupMessageHandler) bundleContext.getService(serviceEvent.getServiceReference());

                if (serviceEvent.getType() == ServiceEvent.REGISTERED) {
                    if (!containsHandler(handler.getClass().getName())) {
                        Timber.i("adding the following popup handler : %s", handler);
                        addPopupHandler(handler);
                    }
                    else
                        Timber.w("the following popup handler has not been added since it is already known : %s", handler);

                    ConfigurationService cfg = ServiceUtils.getService(bundleContext, ConfigurationService.class);
                    String configuredHandler = cfg.getString("systray.POPUP_HANDLER");

                    if ((configuredHandler == null)
                            && ((activePopupMessageHandler == null)
                            || (handler.getPreferenceIndex() > activePopupMessageHandler.getPreferenceIndex()))) {
                        // The user doesn't have a preferred handler set and new
                        // handler with better preference index has arrived,
                        // thus setting it as active.
                        setActivePopupMessageHandler(handler);
                    }
                    if ((configuredHandler != null)
                            && configuredHandler.equals(handler.getClass().getName())) {
                        // The user has a preferred handler set and it just
                        // became available, thus setting it as active
                        setActivePopupMessageHandler(handler);
                    }
                }
                else if (serviceEvent.getType() == ServiceEvent.UNREGISTERING) {
                    Timber.i("removing the following popup handler : %s", handler);
                    removePopupHandler(handler);
                    PopupMessageHandler activeHandler = activePopupMessageHandler;
                    if (activeHandler == handler) {
                        setActivePopupMessageHandler(null);

                        // We just lost our default handler, so we replace it
                        // with the one that has the highest preference index.
                        selectBestPopupMessageHandler();
                    }
                }
            } catch (IllegalStateException e) {
                Timber.d(e);
            }
        }
    }
}
