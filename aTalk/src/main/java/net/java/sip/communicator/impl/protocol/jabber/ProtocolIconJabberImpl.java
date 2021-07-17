/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.impl.protocol.jabber;

import net.java.sip.communicator.service.protocol.ProtocolIcon;

import org.atalk.service.resources.ResourceManagementService;
import org.osgi.framework.ServiceReference;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Hashtable;
import java.util.Iterator;

import timber.log.Timber;

/**
 * Represents the Jabber protocol icon. Implements the <tt>ProtocolIcon</tt> interface in order to
 * provide a Jabber icon image in two different sizes.
 *
 * @author Yana Stamcheva
 * @author Lubomir Marinov
 * @author Eng Chong Meng
 */
public class ProtocolIconJabberImpl implements ProtocolIcon
{
    /**
     * The path where all protocol icons are placed.
     */
    private final String iconPath;

    private static ResourceManagementService resourcesService;

    /**
     * A hash table containing the protocol icon in different sizes.
     */
    private final Hashtable<String, byte[]> iconsTable = new Hashtable<>();

    /**
     * A hash table containing the path to the protocol icon in different sizes.
     */
    private final Hashtable<String, String> iconPathsTable = new Hashtable<>();

    /**
     * Creates an instance of this class by passing to it the path, where all protocol icons are placed.
     *
     * @param iconPath the protocol icon path
     */
    public ProtocolIconJabberImpl(String iconPath)
    {
        this.iconPath = iconPath;

        iconsTable.put(ProtocolIcon.ICON_SIZE_16x16, loadIcon(iconPath + "/status16x16-online.png"));
        iconsTable.put(ProtocolIcon.ICON_SIZE_32x32, loadIcon(iconPath + "/logo32x32.png"));
        iconsTable.put(ProtocolIcon.ICON_SIZE_48x48, loadIcon(iconPath + "/logo48x48.png"));
        iconPathsTable.put(ProtocolIcon.ICON_SIZE_16x16, iconPath + "/status16x16-online.png");
        iconPathsTable.put(ProtocolIcon.ICON_SIZE_32x32, iconPath + "/logo32x32.png");
        iconPathsTable.put(ProtocolIcon.ICON_SIZE_48x48, iconPath + "/logo48x48.png");
    }

    /**
     * Implements the <tt>ProtocolIcon.getSupportedSizes()</tt> method. Returns an iterator to a set
     * containing the supported icon sizes.
     *
     * @return an iterator to a set containing the supported icon sizes
     */
    public Iterator<String> getSupportedSizes()
    {
        return iconsTable.keySet().iterator();
    }

    /**
     * Returns TRUE if a icon with the given size is supported, FALSE-otherwise.
     *
     * @return TRUE if a icon with the given size is supported, FALSE-otherwise.
     */
    public boolean isSizeSupported(String iconSize)
    {
        return iconsTable.containsKey(iconSize);
    }

    /**
     * Returns the icon image in the given size.
     *
     * @param iconSize the icon size; one of ICON_SIZE_XXX constants
     */
    public byte[] getIcon(String iconSize)
    {
        return iconsTable.get(iconSize);
    }

    /**
     * Returns a path to the icon with the given size.
     *
     * @param iconSize the size of the icon we're looking for
     * @return the path to the icon with the given size
     */
    public String getIconPath(String iconSize)
    {
        return iconPathsTable.get(iconSize);
    }

    /**
     * Returns the icon image used to represent the protocol connecting state.
     *
     * @return the icon image used to represent the protocol connecting state
     */
    public byte[] getConnectingIcon()
    {
        return loadIcon(iconPath + "/status16x16-connecting.gif");
    }

    /**
     * Loads an image from a given image path.
     *
     * @param imagePath The identifier of the image.
     * @return The image for the given identifier.
     */
    public static byte[] loadIcon(String imagePath)
    {
        InputStream is = null;
        try {
            // try to load path it maybe valid url
            is = new URL(imagePath).openStream();
        } catch (Exception e) {
        }

        if (is == null)
            is = getResources().getImageInputStreamForPath(imagePath);

        if (is == null)
            return new byte[0];

        byte[] icon = null;
        try {
            icon = new byte[is.available()];
            is.read(icon);
        } catch (IOException e) {
            Timber.e(e, "Failed to load icon: %s", imagePath);
        }
        return icon;
    }

    /**
     * Get the <tt>ResourceMaangementService</tt> registered.
     *
     * @return <tt>ResourceManagementService</tt> registered
     */
    public static ResourceManagementService getResources()
    {
        if (resourcesService == null) {
            ServiceReference serviceReference
                    = JabberActivator.bundleContext.getServiceReference(ResourceManagementService.class.getName());

            if (serviceReference == null)
                return null;
            resourcesService = (ResourceManagementService) JabberActivator.bundleContext.getService(serviceReference);
        }
        return resourcesService;
    }
}
