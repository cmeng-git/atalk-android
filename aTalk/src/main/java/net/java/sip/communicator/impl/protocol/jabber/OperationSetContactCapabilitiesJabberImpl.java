/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.impl.protocol.jabber;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.java.sip.communicator.impl.protocol.jabber.caps.UserCapsNodeListener;
import net.java.sip.communicator.service.protocol.AbstractOperationSetContactCapabilities;
import net.java.sip.communicator.service.protocol.Contact;
import net.java.sip.communicator.service.protocol.OperationSet;
import net.java.sip.communicator.service.protocol.OperationSetBasicInstantMessaging;
import net.java.sip.communicator.service.protocol.OperationSetBasicTelephony;
import net.java.sip.communicator.service.protocol.OperationSetChatStateNotifications;
import net.java.sip.communicator.service.protocol.OperationSetDesktopSharingServer;
import net.java.sip.communicator.service.protocol.OperationSetMessageCorrection;
import net.java.sip.communicator.service.protocol.OperationSetPresence;
import net.java.sip.communicator.service.protocol.OperationSetServerStoredContactInfo;
import net.java.sip.communicator.service.protocol.OperationSetVideoTelephony;
import net.java.sip.communicator.service.protocol.PresenceStatus;
import net.java.sip.communicator.service.protocol.event.ContactPresenceStatusChangeEvent;
import net.java.sip.communicator.service.protocol.event.ContactPresenceStatusListener;
import net.java.sip.communicator.util.ConfigurationUtils;

import org.jivesoftware.smack.packet.Presence;
import org.jivesoftware.smack.roster.Roster;
import org.jivesoftware.smackx.chatstates.ChatStateManager;
import org.jivesoftware.smackx.message_correct.element.MessageCorrectExtension;
import org.jxmpp.jid.Jid;

import timber.log.Timber;

/**
 * Represents an <code>OperationSet</code> to query the <code>OperationSet</code>s supported for a specific
 * Jabber <code>Contact</code>. The <code>OperationSet</code>s reported as supported for a specific Jabber
 * <code>Contact</code> are considered by the associated protocol provider to be capabilities possessed
 * by the Jabber <code>Contact</code> in question.
 *
 * @author Lyubomir Marinov
 * @author Yana Stamcheva
 * @author Eng Chong Meng
 */
public class OperationSetContactCapabilitiesJabberImpl
        extends AbstractOperationSetContactCapabilities<ProtocolProviderServiceJabberImpl>
        implements UserCapsNodeListener, ContactPresenceStatusListener {
    /**
     * The list of <code>OperationSet</code> capabilities presumed to be supported by a
     * <code>Contact</code> when it is offline.
     */
    private static final Set<Class<? extends OperationSet>> OFFLINE_OPERATION_SETS = new HashSet<>();

    /**
     * The <code>Map</code> which associates specific <code>OperationSet</code> classes with the
     * features to be supported by a <code>Contact</code> in order to consider the <code>Contact</code>
     * to possess the respective <code>OperationSet</code> capability.
     */
    private static final Map<Class<? extends OperationSet>, String[]> OPERATION_SETS_TO_FEATURES = new HashMap<>();

    static {
        OFFLINE_OPERATION_SETS.add(OperationSetBasicInstantMessaging.class);
        OFFLINE_OPERATION_SETS.add(OperationSetMessageCorrection.class);
        OFFLINE_OPERATION_SETS.add(OperationSetServerStoredContactInfo.class);

        OPERATION_SETS_TO_FEATURES.put(OperationSetBasicTelephony.class, new String[]{
                ProtocolProviderServiceJabberImpl.URN_XMPP_JINGLE,
                ProtocolProviderServiceJabberImpl.URN_XMPP_JINGLE_RTP,
                ProtocolProviderServiceJabberImpl.URN_XMPP_JINGLE_RTP_AUDIO});

        OPERATION_SETS_TO_FEATURES.put(OperationSetVideoTelephony.class, new String[]{
                ProtocolProviderServiceJabberImpl.URN_XMPP_JINGLE,
                ProtocolProviderServiceJabberImpl.URN_XMPP_JINGLE_RTP,
                ProtocolProviderServiceJabberImpl.URN_XMPP_JINGLE_RTP_VIDEO});

        OPERATION_SETS_TO_FEATURES.put(OperationSetDesktopSharingServer.class, new String[]{
                ProtocolProviderServiceJabberImpl.URN_XMPP_JINGLE,
                ProtocolProviderServiceJabberImpl.URN_XMPP_JINGLE_RTP,
                ProtocolProviderServiceJabberImpl.URN_XMPP_JINGLE_RTP_VIDEO});

        OPERATION_SETS_TO_FEATURES.put(OperationSetMessageCorrection.class,
                new String[]{MessageCorrectExtension.NAMESPACE});
    }

    private final List<Jid> capCheckList = new ArrayList<>();

    /**
     * Initializes a new <code>OperationSetContactCapabilitiesJabberImpl</code> instance which is to be
     * provided by a specific <code>ProtocolProviderServiceJabberImpl</code>.
     *
     * @param pps the <code>ProtocolProviderServiceJabberImpl</code> which will provide the new instance
     */
    public OperationSetContactCapabilitiesJabberImpl(ProtocolProviderServiceJabberImpl pps) {
        super(pps);
        OperationSetPresence presenceOpSet = pps.getOperationSet(OperationSetPresence.class);
        if (presenceOpSet != null) {
            presenceOpSet.addContactPresenceStatusListener(this);
            ((OperationSetPersistentPresenceJabberImpl) presenceOpSet).addUserCapsNodeListener(this);
        }
        setOperationSetChatStateFeatures(ConfigurationUtils.isSendChatStateNotifications());
        capCheckList.clear();
    }

    public static void setOperationSetChatStateFeatures(boolean isEnable) {
        if (OPERATION_SETS_TO_FEATURES.containsKey(OperationSetChatStateNotifications.class)) {
            if (!isEnable)
                OPERATION_SETS_TO_FEATURES.remove(OperationSetChatStateNotifications.class);
        }
        else if (isEnable) {
            OPERATION_SETS_TO_FEATURES.put(OperationSetChatStateNotifications.class,
                    new String[]{ChatStateManager.NAMESPACE});
        }
    }

    /**
     * Gets the <code>OperationSet</code> corresponding to the specified <code>Class</code> and supported
     * by the specified <code>Contact</code>. If the returned value is non-<code>null</code>, it indicates
     * that the <code>Contact</code> is considered by the associated protocol provider to possess the
     * <code>opsetClass</code> capability. Otherwise, the associated protocol provider considers
     * <code>contact</code> to not have the <code>opsetClass</code> capability.
     *
     * @param <U> the type extending <code>OperationSet</code> for which the specified <code>contact</code> is
     * to be checked whether it possesses it as a capability
     * @param contact the <code>Contact</code> for which the <code>opsetClass</code> capability is to be queried
     * @param opsetClass the <code>OperationSet</code> <code>Class</code> for which the specified <code>contact</code> is
     * to be checked whether it possesses it as a capability
     * @param online <code>true</code> if <code>contact</code> is online; otherwise, <code>false</code>
     *
     * @return the <code>OperationSet</code> corresponding to the specified <code>opsetClass</code>
     * which is considered by the associated protocol provider to be possessed as a capability by
     * the specified <code>contact</code>; otherwise, <code>null</code>
     *
     * @see AbstractOperationSetContactCapabilities#getOperationSet(Contact, Class)
     */
    @Override
    protected <U extends OperationSet> U getOperationSet(Contact contact, Class<U> opsetClass, boolean online) {
        Jid jid = parentProvider.getFullJidIfPossible(contact);
        return getOperationSet(jid, opsetClass, online);
    }

    /**
     * Gets the <code>OperationSet</code>s supported by a specific <code>Contact</code>. The returned
     * <code>OperationSet</code>s are considered by the associated protocol provider to capabilities
     * possessed by the specified <code>contact</code>.
     *
     * @param contact the <code>Contact</code> for which the supported <code>OperationSet</code> capabilities are to
     * be retrieved
     * @param online <code>true</code> if <code>contact</code> is online; otherwise, <code>false</code>
     *
     * @return a <code>Map</code> listing the <code>OperationSet</code>s considered by the associated
     * protocol provider to be supported by the specified <code>contact</code> (i.e. to be
     * possessed as capabilities). Each supported <code>OperationSet</code> capability is
     * represented by a <code>Map.Entry</code> with key equal to the <code>OperationSet</code> class
     * name and value equal to the respective <code>OperationSet</code> instance
     *
     * @see AbstractOperationSetContactCapabilities#getSupportedOperationSets(Contact)
     */
    @Override
    protected Map<String, OperationSet> getSupportedOperationSets(Contact contact, boolean online) {
        Jid jid = parentProvider.getFullJidIfPossible(contact);
        return getSupportedOperationSets(jid, online);
    }

    /**
     * Gets the <code>OperationSet</code>s supported by a specific <code>Contact</code>. The returned
     * <code>OperationSet</code>s are considered by the associated protocol provider to capabilities
     * possessed by the specified <code>contact</code>.
     *
     * @param jid the <code>Contact</code> for which the supported <code>OperationSet</code> capabilities are to be retrieved
     * @param online <code>true</code> if <code>contact</code> is online; otherwise, <code>false</code>
     *
     * @return a <code>Map</code> listing the <code>OperationSet</code>s considered by the associated
     * protocol provider to be supported by the specified <code>contact</code> (i.e. to be
     * possessed as capabilities). Each supported <code>OperationSet</code> capability is
     * represented by a <code>Map.Entry</code> with key equal to the <code>OperationSet</code> class
     * name and value equal to the respective <code>OperationSet</code> instance
     *
     * @see AbstractOperationSetContactCapabilities#getSupportedOperationSets(Contact)
     */
    @SuppressWarnings("unchecked")
    private Map<String, OperationSet> getSupportedOperationSets(Jid jid, boolean online) {
        Map<String, OperationSet> supportedOperationSets = parentProvider.getSupportedOperationSets();
        int supportedOperationSetCount = supportedOperationSets.size();
        Map<String, OperationSet> contactSupportedOperationSets = new HashMap<>(supportedOperationSetCount);

        if (supportedOperationSetCount != 0) {
            for (Map.Entry<String, OperationSet> supportedOperationSetEntry : supportedOperationSets.entrySet()) {
                String opsetClassName = supportedOperationSetEntry.getKey();

                Class<? extends OperationSet> opsetClass;
                try {
                    opsetClass = (Class<? extends OperationSet>) Class.forName(opsetClassName);
                } catch (ClassNotFoundException cnfex) {
                    opsetClass = null;
                    Timber.e(cnfex, "Failed to get OperationSet class for name: %s", opsetClassName);
                }

                if (opsetClass != null) {
                    OperationSet opset = getOperationSet(jid, opsetClass, online);
                    if (opset != null) {
                        contactSupportedOperationSets.put(opsetClassName, opset);
                    }
                }
            }
        }
        return contactSupportedOperationSets;
    }

    /**
     * Gets the <code>OperationSet</code> corresponding to the specified <code>Class</code> and
     * supported by the specified <code>Contact</code>. If the returned value is non-<code>null</code>,
     * it indicates that the <code>Contact</code> is considered by the associated protocol provider
     * to possess the <code>opsetClass</code> capability. Otherwise, the associated protocol provider
     * considers <code>contact</code> to not have the <code>opsetClass</code> capability.
     *
     * @param <U> the type extending <code>OperationSet</code> for which the specified <code>contact</code> is
     * to be checked whether it possesses it as a capability
     * @param jid the Jabber id for which we're checking supported operation sets
     * @param opsetClass the <code>OperationSet</code> <code>Class</code> for which the specified <code>contact</code> is
     * to be checked whether it possesses it as a capability
     * @param online <code>true</code> if <code>contact</code> is online; otherwise, <code>false</code>
     *
     * @return the <code>OperationSet</code> corresponding to the specified <code>opsetClass</code>
     * which is considered by the associated protocol provider to be possessed as a capability by
     * the specified <code>contact</code>; otherwise, <code>null</code>
     *
     * @see AbstractOperationSetContactCapabilities#getOperationSet(Contact, Class)
     */
    private <U extends OperationSet> U getOperationSet(Jid jid, Class<U> opsetClass, boolean online) {
        U opset = parentProvider.getOperationSet(opsetClass);
        if (opset == null)
            return null;
        /*
         * If the specified contact is offline, don't query its features (they should fail anyway).
         */
        if (!online) {
            return OFFLINE_OPERATION_SETS.contains(opsetClass) ? opset : null;
        }

        /*
         * If we know the features required for the support of opsetClass, check whether the
         * contact supports them. Otherwise, presume the contact possesses the opsetClass
         * capability in light of the fact that we miss any knowledge of the opsetClass whatsoever.
         */
        if (OPERATION_SETS_TO_FEATURES.containsKey(opsetClass)) {
            String[] features = OPERATION_SETS_TO_FEATURES.get(opsetClass);

            /*
             * Either we've completely disabled the opsetClass capability by mapping it to the null
             * list of features or we've mapped it to an actual list of features which are to be
             * checked whether the contact supports them.
             */
            if ((features == null) || ((features.length != 0)
                    && !parentProvider.isFeatureListSupported(jid, features))) {
                opset = null;
            }
        }
        return opset;
    }

    /**
     * Notifies this listener that an <code>EntityCapsManager</code> has added a record for a specific
     * user about the caps node the user has.
     *
     * @param user the user (contact full Jid)
     * @param online indicates if the user is currently online
     *
     * @see UserCapsNodeListener#userCapsNodeNotify(Jid, boolean)
     */
    @Override
    public void userCapsNodeNotify(Jid user, boolean online) {
        /*
         * It doesn't matter to us whether a caps node has been added or removed for the specified
         * user because we report all changes.
         */
        OperationSetPresence opsetPresence = parentProvider.getOperationSet(OperationSetPresence.class);
        if (opsetPresence != null) {
            Contact contact = opsetPresence.findContactByJid(user);

            // If the contact isn't null and is online we try to discover the new set of
            // operation sets and to notify interested parties. Otherwise we ignore the event.
            if (contact != null) {
                if (online) {
                    // when going online we have received a presence and make sure we discover
                    // this particular jid for getSupportedOperationSets
                    fireContactCapabilitiesEvent(contact, user, getSupportedOperationSets(user, true));
                }
                else {
                    // Need to wait a while before getSupportedOperationSets(); otherwise non-updated values
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    // when offline, we use the contact to find the most connected jid for SupportedOperationSets
                    fireContactCapabilitiesEvent(contact, user, getSupportedOperationSets(contact));
                }
            }
        }
    }

    // ======================= Contacts userCap handler ==================================

    /**
     * Contact userCap handler when there is change in presence status. Skip handling of any duplication send from server.
     *
     * @param evt the <code>ContactPresenceStatusChangeEvent</code> that notified us
     */
    @Override
    public void contactPresenceStatusChanged(ContactPresenceStatusChangeEvent evt) {
        Jid jidFrom = evt.getJid();
        boolean capsUpdated = capCheckList.contains(jidFrom);

        if (evt.getNewStatus().getStatus() < PresenceStatus.ONLINE_THRESHOLD) {
            // Timber.d("userCapCheck notify for: %s (offline; capsUpdated: %s)", jidFrom, capsUpdated);
            if (capsUpdated) {
                capCheckList.remove(jidFrom);
                userCapsNodeNotify(jidFrom, false);
            }
        }
        else if (evt.hasCapsExtension()) {
            // Timber.d("userCapCheck notify for: %s (online; capsUpdated: %s)", jidFrom, capsUpdated);
            if (!capsUpdated) {
                capCheckList.add(jidFrom);
                userCapsNodeNotify(jidFrom, true);
            }
        }
    }

    /**
     * Fires event that contact capabilities has changed. This is called on received discovery info
     * for a particular jid, so we use its online and opSets for this particular jid
     *
     * @param user the user Jid to search for its contact.
     */
    public void fireContactCapabilitiesChanged(Jid user) {
        OperationSetPresence opsetPresence = parentProvider.getOperationSet(OperationSetPresence.class);
        if (opsetPresence != null) {
            Contact contact = opsetPresence.findContactByJid(user);
            if (contact != null) {
                Presence presence = Roster.getInstanceFor(parentProvider.getConnection()).getPresence(user.asBareJid());
                boolean online = (presence != null) && presence.isAvailable();
                fireContactCapabilitiesEvent(contact, user, getSupportedOperationSets(user, online));
            }
        }
    }
}
