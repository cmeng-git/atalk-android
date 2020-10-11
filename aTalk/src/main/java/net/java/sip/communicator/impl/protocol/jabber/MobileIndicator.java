/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.impl.protocol.jabber;

import android.text.TextUtils;

import net.java.sip.communicator.service.protocol.ContactResource;
import net.java.sip.communicator.service.protocol.RegistrationState;
import net.java.sip.communicator.service.protocol.event.*;

import org.jivesoftware.smackx.caps.EntityCapsManager;
import org.jxmpp.jid.FullJid;
import org.jxmpp.jid.Jid;
import net.java.sip.communicator.impl.protocol.jabber.caps.UserCapsNodeListener;

import java.util.*;

/**
 * Handles all the logic about mobile indicator for contacts. Has to modes, the first is searching
 * for particular string in the beginning of the contact resource and if found and this is the
 * highest priority then the contact in on mobile. The second one and the default one is searching
 * for strings in the node from the contact caps and if found and this is the most connected device
 * then the contact is a mobile one.
 *
 * @author Damian Minkov
 * @author Eng Chong Meng
 */
public class MobileIndicator implements RegistrationStateChangeListener, UserCapsNodeListener
{
    /**
     * The parent provider.
     */
    private final ProtocolProviderServiceJabberImpl parentProvider;

    /**
     * Whether we are using the default method for checking for mobile indicator.
     */
    private boolean isCapsMobileIndicator = true;

    /**
     * The strings that we will check.
     */
    private final String[] checkStrings;

    /**
     * A reference to the ServerStoredContactListImpl instance.
     */
    private final ServerStoredContactListJabberImpl ssclCallback;

    /**
     * The account property to activate the mode for checking the resource names, the strings to
     * check whether a resource starts with can be entered separated by comas.
     */
    private static final String MOBILE_INDICATOR_RESOURCE_ACC_PROP = "MOBILE_INDICATOR_RESOURCE";

    /**
     * The account property to activate the mode for checking the contact caps, the strings to check
     * whether a caps contains with can be entered separated by comas.
     */
    private static final String MOBILE_INDICATOR_CAPS_ACC_PROP = "MOBILE_INDICATOR_CAPS";

    /**
     * Construct Mobile indicator.
     *
     * @param parentProvider the parent provider.
     * @param ssclCallback the callback for the contact list to obtain contacts.
     */
    public MobileIndicator(ProtocolProviderServiceJabberImpl parentProvider,
            ServerStoredContactListJabberImpl ssclCallback)
    {
        this.parentProvider = parentProvider;
        this.ssclCallback = ssclCallback;

        String indicatorResource
                = parentProvider.getAccountID().getAccountProperties().get(MOBILE_INDICATOR_RESOURCE_ACC_PROP);
        if (!TextUtils.isEmpty(indicatorResource)) {
            isCapsMobileIndicator = false;
            checkStrings = indicatorResource.split(",");
        }
        else {
            String indicatorCaps
                    = parentProvider.getAccountID().getAccountProperties().get(MOBILE_INDICATOR_CAPS_ACC_PROP);
            if (TextUtils.isEmpty(indicatorCaps)) {
                indicatorCaps = "mobile, portable, android";
            }
            checkStrings = indicatorCaps.split(",");
            this.parentProvider.addRegistrationStateChangeListener(this);
        }
    }

    /**
     * Called when resources have been updated for a contact, on presence changed.
     *
     * @param contact the contact
     */
    public void resourcesUpdated(ContactJabberImpl contact)
    {
        if (isCapsMobileIndicator) {
            // we update it also here, cause sometimes caps update comes before presence changed and
            // contacts are still offline and we dispatch wrong initial mobile indicator
            updateMobileIndicatorUsingCaps(contact.getJid());
            return;
        }

        // checks resource starts with String and is current highest priority
        int highestPriority = Integer.MIN_VALUE;
        List<ContactResource> highestPriorityResources = new ArrayList<>();

        Collection<ContactResource> resources = contact.getResources();
        // sometimes volatile contacts do not have resources
        if (resources == null)
            return;

        for (ContactResource res : resources) {
            if (!res.getPresenceStatus().isOnline())
                continue;

            int priority = res.getPriority();
            if (priority >= highestPriority) {
                if (highestPriority != priority)
                    highestPriorityResources.clear();

                highestPriority = priority;
                highestPriorityResources.add(res);
            }
        }
        updateContactMobileStatus(contact, highestPriorityResources);
    }

    /**
     * Updates contact mobile status.
     *
     * @param contact the contact.
     * @param resources the list of contact resources.
     */
    private void updateContactMobileStatus(ContactJabberImpl contact, List<ContactResource> resources)
    {
        // check whether all are mobile
        boolean allMobile = false;
        for (ContactResource res : resources) {
            if (res.isMobile())
                allMobile = true;
            else {
                allMobile = false;
                break;
            }
        }
        if (resources.size() > 0)
            contact.setMobile(allMobile);
        else
            contact.setMobile(false);
    }

    /**
     * Checks a resource whether it is mobile or not, by checking the cache.
     *
     * @param fullJid the FullJid to check.
     * @return whether resource with that name is mobile or not.
     */
    boolean isMobileResource(Jid fullJid)
    {
        if (isCapsMobileIndicator) {
            EntityCapsManager.NodeVerHash caps = EntityCapsManager.getNodeVerHashByJid(fullJid);
            return (caps != null && containsStrings(caps.getNode(), checkStrings));

//            XMPPTCPConnection xmppConnection = ssclCallback.getParentProvider().getConnection();
//            if (xmppConnection != null) {
//                EntityCapsManager capsManager = EntityCapsManager.getInstanceFor(xmppConnection);
//                DiscoverInfo caps = EntityCapsManager.getDiscoveryInfoByNodeVer(capsManager.getLocalNodeVer());
//                return (caps != null && containsStrings(caps.getNode(), checkStrings));
//            }
        }
        return (startsWithStrings(fullJid.getResourceOrEmpty().toString(), checkStrings));
    }

    /**
     * The method is called by a ProtocolProvider implementation whenever a change in the
     * registration state of the corresponding provider had occurred.
     *
     * @param evt ProviderStatusChangeEvent the event describing the status change.
     */
    @Override
    public void registrationStateChanged(RegistrationStateChangeEvent evt)
    {
        if (evt.getNewState() == RegistrationState.REGISTERED) {
            this.parentProvider.getDiscoveryManager().addUserCapsNodeListener(this);
        }
        else if ((evt.getNewState() == RegistrationState.CONNECTION_FAILED
                || evt.getNewState() == RegistrationState.AUTHENTICATION_FAILED
                || evt.getNewState() == RegistrationState.UNREGISTERED)
                && this.parentProvider.getDiscoveryManager() != null) {
            this.parentProvider.getDiscoveryManager().removeUserCapsNodeListener(this);
        }
    }

    /**
     * Caps for user has been changed.
     *
     * @param user the user (full JID)
     * @param online indicates if the user for which we're notified is online
     */
    @Override
    public void userCapsNodeNotify(Jid user, boolean online)
    {
        updateMobileIndicatorUsingCaps(user);
    }

    /**
     * Update mobile indicator for contact, searching in contact caps.
     *
     * @param user the contact address with or without resource.
     */
    private void updateMobileIndicatorUsingCaps(Jid user)
    {
        ContactJabberImpl contact = ssclCallback.findContactById(user.asBareJid());
        if (contact == null)
            return;

        // 1. Find most connected resources and if all are mobile
        int currentMostConnectedStatus = 0;
        List<ContactResource> mostAvailableResources = new ArrayList<>();

        for (Map.Entry<FullJid, ContactResourceJabberImpl> resEntry : contact.getResourcesMap().entrySet()) {
            ContactResourceJabberImpl res = resEntry.getValue();
            if (!res.getPresenceStatus().isOnline())
                continue;

            // update the mobile indicator of connected resource, as caps have been updated
            boolean oldIndicator = res.isMobile();
            res.setMobile(isMobileResource(res.getFullJid()));

            if (oldIndicator != res.isMobile()) {
                contact.fireContactResourceEvent(new ContactResourceEvent(contact, res,
                        ContactResourceEvent.RESOURCE_MODIFIED));
            }

            int status = res.getPresenceStatus().getStatus();
            if (status > currentMostConnectedStatus) {
                mostAvailableResources.clear();
                currentMostConnectedStatus = status;
                mostAvailableResources.add(res);
            }
        }
        // check whether all are mobile
        updateContactMobileStatus(contact, mostAvailableResources);
    }

    /**
     * Checks whether <tt>value</tt> starts one of the <tt>checkStrs</> Strings.
     *
     * @param value the value to check
     * @param checkStrs an array of strings we are searching for.
     * @return <tt>true</tt> if <tt>value</tt> starts one of the Strings.
     */
    private static boolean startsWithStrings(String value, String[] checkStrs)
    {
        for (String str : checkStrs) {
            if (str.length() > 0 && value.startsWith(str))
                return true;
        }
        return false;
    }

    /**
     * Checks whether <tt>value</tt> contains one of the <tt>checkStrs</> Strings.
     *
     * @param value the value to check
     * @param checkStrs an array of strings we are searching for.
     * @return <tt>true</tt> if <tt>value</tt> contains one of the Strings.
     */
    private static boolean containsStrings(String value, String[] checkStrs)
    {
        for (String str : checkStrs) {
            if (str.length() > 0 && value.contains(str))
                return true;
        }
        return false;
    }
}
