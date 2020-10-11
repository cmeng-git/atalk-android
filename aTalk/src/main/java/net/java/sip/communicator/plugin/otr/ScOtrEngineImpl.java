/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package net.java.sip.communicator.plugin.otr;

import net.java.otr4j.*;
import net.java.otr4j.crypto.OtrCryptoEngineImpl;
import net.java.otr4j.crypto.OtrCryptoException;
import net.java.otr4j.session.*;
import net.java.sip.communicator.plugin.otr.OtrContactManager.OtrContact;
import net.java.sip.communicator.plugin.otr.authdialog.SmpAuthenticateBuddyDialog;
import net.java.sip.communicator.plugin.otr.authdialog.SmpProgressDialog;
import net.java.sip.communicator.service.browserlauncher.BrowserLauncherService;
import net.java.sip.communicator.service.contactlist.MetaContact;
import net.java.sip.communicator.service.gui.ChatLinkClickedListener;
import net.java.sip.communicator.service.protocol.*;

import org.atalk.android.R;
import org.atalk.android.aTalkApp;
import org.atalk.android.gui.chat.ChatMessage;
import org.osgi.framework.ServiceEvent;
import org.osgi.framework.ServiceListener;

import java.net.URI;
import java.security.KeyPair;
import java.security.PublicKey;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import timber.log.Timber;

/**
 * @author George Politis
 * @author Lyubomir Marinov
 * @author Pawel Domas
 * @author Marin Dzhigarov
 * @author Danny van Heumen
 * @author Eng Chong Meng
 */
public class ScOtrEngineImpl implements ScOtrEngine, ChatLinkClickedListener, ServiceListener
{
    private static String CONTACT_POLICY = ".contact_policy";
    private static String GLOBAL_POLICY = "GLOBAL_POLICY";

    /**
     * The max timeout period elapsed prior to establishing a TIMED_OUT session.
     */
    private static final int SESSION_TIMEOUT
            = OtrActivator.configService.getInt("otr.SESSION_STATUS_TIMEOUT", 30000);

    private static final Map<ScSessionID, OtrContact> contactsMap = new Hashtable<>();
    private static final Map<OtrContact, SmpProgressDialog> progressDialogMap = new ConcurrentHashMap<>();
    private final OtrConfigurator configurator = new OtrConfigurator();
    private final List<String> injectedMessageUIDs = new Vector<>();
    private final List<ScOtrEngineListener> listeners = new Vector<>();
    private final OtrEngineHost otrEngineHost = new ScOtrEngineHost();
    private final OtrSessionManager otrEngine;

    /**
     * Manages the scheduling of TimerTasks that are used to set Contact's ScSessionStatus
     * (to TIMED_OUT) after a period of time.
     */
    private ScSessionStatusScheduler scheduler = new ScSessionStatusScheduler();
    /**
     * This mapping is used for taking care of keeping SessionStatus and ScSessionStatus in sync
     * for every Session object.
     */
    private Map<SessionID, ScSessionStatus> scSessionStatusMap = new ConcurrentHashMap<>();

    public ScOtrEngineImpl()
    {
        otrEngine = new OtrSessionManagerImpl(otrEngineHost);

        // Clears the map after previous instance
        // This is required because of OSGi restarts in the same VM on Android
        contactsMap.clear();
        scSessionStatusMap.clear();

        this.otrEngine.addOtrEngineListener(new OtrEngineListener()
        {
            @Override
            public void sessionStatusChanged(SessionID sessionID)
            {
                OtrContact otrContact = getOtrContact(sessionID);
                if (otrContact == null)
                    return;

                String resourceName = (otrContact.resource != null) ? "/" + otrContact.resource.getResourceName() : "";
                Contact contact = otrContact.contact;
                String sender = contact.getAddress();

                // Cancel any scheduled tasks that will change the ScSessionStatus for this Contact
                scheduler.cancel(otrContact);

                String message = "";
                ScSessionStatus scSessionStatus;
                final Session session = otrEngine.getSession(sessionID);
                switch (session.getSessionStatus()) {
                    case ENCRYPTED:
                        scSessionStatus = ScSessionStatus.ENCRYPTED;
                        scSessionStatusMap.put(sessionID, scSessionStatus);
                        PublicKey remotePubKey = session.getRemotePublicKey();
                        String remoteFingerprint = null;
                        try {
                            remoteFingerprint = new OtrCryptoEngineImpl().getFingerprint(remotePubKey);
                        } catch (OtrCryptoException e) {
                            Timber.d("Could not get the fingerprint from the public key for: %s", contact);
                        }

                        List<String> allFingerprintsOfContact
                                = OtrActivator.scOtrKeyManager.getAllRemoteFingerprints(contact);
                        if (allFingerprintsOfContact != null) {
                            if (!allFingerprintsOfContact.contains(remoteFingerprint)) {
                                OtrActivator.scOtrKeyManager.saveFingerprint(contact, remoteFingerprint);
                            }
                        }

                        if (!OtrActivator.scOtrKeyManager.isVerified(contact, remoteFingerprint)) {
                            OtrActivator.scOtrKeyManager.unverify(otrContact, remoteFingerprint);
                            UUID sessionGuid = null;
                            for (ScSessionID scSessionID : contactsMap.keySet()) {
                                if (scSessionID.getSessionID().equals(sessionID)) {
                                    sessionGuid = scSessionID.getGUID();
                                    break;
                                }
                            }

                            OtrActivator.uiService.getChat(contact).addChatLinkClickedListener(ScOtrEngineImpl.this);
                            String unverifiedSessionWarning = aTalkApp.getResString(
                                    R.string.plugin_otr_activator_unverifiedsessionwarning, sender + resourceName,
                                    this.getClass().getName(), "AUTHENTIFICATION", sessionGuid.toString());
                            OtrActivator.uiService.getChat(contact).addMessage(sender, new Date(),
                                    ChatMessage.MESSAGE_SYSTEM, IMessage.ENCODE_HTML, unverifiedSessionWarning);
                        }

                        // show info whether history is on or off
                        String otrAndHistoryMessage;
                        if (!OtrActivator.getMessageHistoryService().isHistoryLoggingEnabled()
                                || !isHistoryLoggingEnabled(contact)) {
                            otrAndHistoryMessage = aTalkApp.getResString(R.string.plugin_otr_activator_historyoff,
                                    aTalkApp.getResString(R.string.APPLICATION_NAME),
                                    this.getClass().getName(), "showHistoryPopupMenu");
                        }
                        else {
                            otrAndHistoryMessage = aTalkApp.getResString(R.string.plugin_otr_activator_historyon,
                                    aTalkApp.getResString(R.string.APPLICATION_NAME),
                                    this.getClass().getName(), "showHistoryPopupMenu");
                        }
                        OtrActivator.uiService.getChat(contact).addMessage(sender, new Date(),
                                ChatMessage.MESSAGE_SYSTEM, IMessage.ENCODE_HTML, otrAndHistoryMessage);

                        // show info on OTR session status
                        message = aTalkApp.getResString(R.string.plugin_otr_activator_multipleinstancesdetected, sender);

                        if (contact.supportResources() && contact.getResources() != null
                                && contact.getResources().size() > 1) {
                            OtrActivator.uiService.getChat(contact).addMessage(sender, new Date(),
                                    ChatMessage.MESSAGE_SYSTEM, IMessage.ENCODE_HTML, message);
                        }

                        message = aTalkApp.getResString(OtrActivator.scOtrKeyManager.isVerified(contact, remoteFingerprint)
                                ? R.string.plugin_otr_activator_sessionstared
                                : R.string.plugin_otr_activator_unverifiedsessionstared, sender + resourceName);
                        break;
                    case FINISHED:
                        scSessionStatus = ScSessionStatus.FINISHED;
                        scSessionStatusMap.put(sessionID, scSessionStatus);
                        message = aTalkApp.getResString(R.string.plugin_otr_activator_sessionfinished,
                                sender + resourceName);
                        break;
                    case PLAINTEXT:
                        scSessionStatus = ScSessionStatus.PLAINTEXT;
                        scSessionStatusMap.put(sessionID, scSessionStatus);
                        message = aTalkApp.getResString(R.string.plugin_otr_activator_sessionlost, sender + resourceName);
                        break;
                }
                OtrActivator.uiService.getChat(contact).
                        addMessage(sender, new Date(), ChatMessage.MESSAGE_SYSTEM, IMessage.ENCODE_HTML, message);
                for (ScOtrEngineListener l : getListeners())
                    l.sessionStatusChanged(otrContact);
            }

            @Override
            public void multipleInstancesDetected(SessionID sessionID)
            {
                OtrContact otrContact = getOtrContact(sessionID);
                if (otrContact == null)
                    return;

                for (ScOtrEngineListener l : getListeners())
                    l.multipleInstancesDetected(otrContact);
            }

            @Override
            public void outgoingSessionChanged(SessionID sessionID)
            {
                OtrContact otrContact = getOtrContact(sessionID);
                if (otrContact == null)
                    return;

                for (ScOtrEngineListener l : getListeners())
                    l.outgoingSessionChanged(otrContact);
            }
        });
    }

    public static OtrContact getOtrContact(SessionID sessionID)
    {
        return contactsMap.get(new ScSessionID(sessionID));
    }

    /**
     * Returns the <tt>ScSessionID</tt> for given <tt>UUID</tt>.
     *
     * @param guid the <tt>UUID</tt> identifying <tt>ScSessionID</tt>.
     * @return the <tt>ScSessionID</tt> for given <tt>UUID</tt> or <tt>null</tt> if no matching session found.
     */
    public static ScSessionID getScSessionForGuid(UUID guid)
    {
        for (ScSessionID scSessionID : contactsMap.keySet()) {
            if (scSessionID.getGUID().equals(guid)) {
                return scSessionID;
            }
        }
        return null;
    }

    public static SessionID getSessionID(OtrContact otrContact)
    {
        ProtocolProviderService pps = otrContact.contact.getProtocolProvider();
        String resourceName = otrContact.resource != null
                ? "/" + otrContact.resource.getResourceName() : "";
        SessionID sessionID = new SessionID(pps.getAccountID().getAccountUniqueID(),
                otrContact.contact.getAddress() + resourceName, pps.getProtocolName());

        synchronized (contactsMap) {
            if (contactsMap.containsKey(new ScSessionID(sessionID)))
                return sessionID;

            ScSessionID scSessionID = new ScSessionID(sessionID);
            contactsMap.put(scSessionID, otrContact);
        }
        return sessionID;
    }

    /**
     * Checks whether history is enabled for the metaContact containing the <tt>contact</tt>.
     *
     * @param contact the contact to check.
     * @return whether chat logging is enabled while chatting with <tt>contact</tt>.
     */
    private boolean isHistoryLoggingEnabled(Contact contact)
    {
        MetaContact metaContact = OtrActivator.getContactListService().findMetaContactByContact(contact);
        if (metaContact != null)
            return OtrActivator.getMessageHistoryService().isHistoryLoggingEnabled(metaContact.getMetaUID());
        else
            return true;
    }

    @Override
    public void addListener(ScOtrEngineListener l)
    {
        synchronized (listeners) {
            if (!listeners.contains(l))
                listeners.add(l);
        }
    }

    @Override
    public void chatLinkClicked(URI url)
    {
        String action = url.getPath();
        if (action.equals("/AUTHENTIFICATION")) {
            UUID guid = UUID.fromString(url.getQuery());
            if (guid == null)
                throw new RuntimeException("No UUID found in OTR authenticate URL");

            // Looks for registered action handler
            OtrActionHandler actionHandler = net.java.sip.communicator.util
                    .ServiceUtils.getService(OtrActivator.bundleContext, OtrActionHandler.class);

            if (actionHandler != null) {
                actionHandler.onAuthenticateLinkClicked(guid);
            }
            else {
                Timber.e("No OtrActionHandler registered");
            }
        }
    }

    @Override
    public void endSession(OtrContact otrContact)
    {
        SessionID sessionID = getSessionID(otrContact);
        try {
            setSessionStatus(otrContact, ScSessionStatus.PLAINTEXT);
            otrEngine.getSession(sessionID).endSession();
        } catch (OtrException e) {
            showError(sessionID, e.getMessage());
        }
    }

    @Override
    public OtrPolicy getContactPolicy(Contact contact)
    {
        ProtocolProviderService pps = contact.getProtocolProvider();
        SessionID sessionID = new SessionID(pps.getAccountID().getAccountUniqueID(),
                contact.getAddress(), pps.getProtocolName());
        int policy = this.configurator.getPropertyInt(sessionID + CONTACT_POLICY, -1);
        if (policy < 0)
            return getGlobalPolicy();
        else
            return new OtrPolicyImpl(policy);
    }

    @Override
    public OtrPolicy getGlobalPolicy()
    {
        /*
         * SEND_WHITESPACE_TAG bit will be lowered until we stabilize the OTR.
         */
        int defaultScOtrPolicy = OtrPolicy.OTRL_POLICY_DEFAULT & ~OtrPolicy.SEND_WHITESPACE_TAG;
        return new OtrPolicyImpl(this.configurator.getPropertyInt(GLOBAL_POLICY, defaultScOtrPolicy));
    }

    @Override
    public void setGlobalPolicy(OtrPolicy policy)
    {
        if (policy == null)
            this.configurator.removeProperty(GLOBAL_POLICY);
        else
            this.configurator.setProperty(GLOBAL_POLICY, policy.getPolicy());

        for (ScOtrEngineListener l : getListeners())
            l.globalPolicyChanged();
    }

    /**
     * Gets a copy of the list of <tt>ScOtrEngineListener</tt>s registered with this instance
     * which may safely be iterated without the risk of a <tt>ConcurrentModificationException</tt>.
     *
     * @return a copy of the list of <tt>ScOtrEngineListener<tt>s registered with this instance
     * which may safely be iterated without the risk of a <tt>ConcurrentModificationException</tt>
     */
    private ScOtrEngineListener[] getListeners()
    {
        synchronized (listeners) {
            return listeners.toArray(new ScOtrEngineListener[0]);
        }
    }

    private void setSessionStatus(OtrContact otrContact, ScSessionStatus status)
    {
        scSessionStatusMap.put(getSessionID(otrContact), status);
        scheduler.cancel(otrContact);
        for (ScOtrEngineListener l : getListeners())
            l.sessionStatusChanged(otrContact);
    }

    @Override
    public ScSessionStatus getSessionStatus(OtrContact otrContact)
    {
        SessionID sessionID = getSessionID(otrContact);
        SessionStatus sessionStatus = otrEngine.getSession(sessionID).getSessionStatus();
        ScSessionStatus scSessionStatus = null;
        if (!scSessionStatusMap.containsKey(sessionID)) {
            switch (sessionStatus) {
                case PLAINTEXT:
                    scSessionStatus = ScSessionStatus.PLAINTEXT;
                    break;
                case ENCRYPTED:
                    scSessionStatus = ScSessionStatus.ENCRYPTED;
                    break;
                case FINISHED:
                    scSessionStatus = ScSessionStatus.FINISHED;
                    break;
            }
            scSessionStatusMap.put(sessionID, scSessionStatus);
        }
        return scSessionStatusMap.get(sessionID);
    }

    @Override
    public boolean isMessageUIDInjected(String mUID)
    {
        return injectedMessageUIDs.contains(mUID);
    }

    @Override
    public void launchHelp()
    {
        org.osgi.framework.ServiceReference ref
                = OtrActivator.bundleContext.getServiceReference(BrowserLauncherService.class.getName());
        if (ref == null)
            return;
        BrowserLauncherService service = (BrowserLauncherService) OtrActivator.bundleContext.getService(ref);
        service.openURL(aTalkApp.getResString(R.string.plugin_otr_authbuddydialog_HELP_URI));
    }

    @Override
    public void refreshSession(OtrContact otrContact)
    {
        SessionID sessionID = getSessionID(otrContact);
        try {
            otrEngine.getSession(sessionID).refreshSession();
        } catch (OtrException e) {
            Timber.e(e, "Error refreshing session");
            showError(sessionID, e.getMessage());
        }
    }

    @Override
    public void removeListener(ScOtrEngineListener l)
    {
        synchronized (listeners) {
            listeners.remove(l);
        }
    }

    /**
     * Cleans the contactsMap when <tt>ProtocolProviderService</tt> gets unregistered.
     */
    @Override
    public void serviceChanged(ServiceEvent ev)
    {
        Object service = OtrActivator.bundleContext.getService(ev.getServiceReference());

        if (!(service instanceof ProtocolProviderService))
            return;

        if (ev.getType() == ServiceEvent.UNREGISTERING) {
            Timber.d("Unregister a PPS, cleaning OTR's ScSessionID (Contact map); and Contact (SpmProgressDialog map).");
            ProtocolProviderService provider = (ProtocolProviderService) service;

            synchronized (contactsMap) {
                Iterator<OtrContact> i = contactsMap.values().iterator();
                while (i.hasNext()) {
                    OtrContact otrContact = i.next();
                    if (provider.equals(otrContact.contact.getProtocolProvider())) {
                        scSessionStatusMap.remove(getSessionID(otrContact));
                        i.remove();
                    }
                }
            }

            Iterator<OtrContact> i = progressDialogMap.keySet().iterator();
            while (i.hasNext()) {
                if (provider.equals(i.next().contact.getProtocolProvider()))
                    i.remove();
            }
            scheduler.serviceChanged(ev);
        }
    }

    @Override
    public void setContactPolicy(Contact contact, OtrPolicy policy)
    {
        ProtocolProviderService pps = contact.getProtocolProvider();
        SessionID sessionID = new SessionID(pps.getAccountID().getAccountUniqueID(),
                contact.getAddress(), pps.getProtocolName());
        String propertyID = sessionID + CONTACT_POLICY;
        if (policy == null)
            this.configurator.removeProperty(propertyID);
        else
            this.configurator.setProperty(propertyID, policy.getPolicy());

        for (ScOtrEngineListener l : getListeners())
            l.contactPolicyChanged(contact);
    }

    public void showError(SessionID sessionID, String err)
    {
        OtrContact otrContact = getOtrContact(sessionID);
        if (otrContact == null)
            return;

        Contact contact = otrContact.contact;
        OtrActivator.uiService.getChat(contact).addMessage(contact.getAddress(), new Date(),
                ChatMessage.MESSAGE_ERROR, IMessage.ENCODE_PLAIN, err);
    }

    @Override
    public void startSession(OtrContact otrContact)
    {
        SessionID sessionID = getSessionID(otrContact);
        ScSessionStatus scSessionStatus = ScSessionStatus.LOADING;
        scSessionStatusMap.put(sessionID, scSessionStatus);

        for (ScOtrEngineListener l : getListeners()) {
            l.sessionStatusChanged(otrContact);
        }
        scheduler.scheduleScSessionStatusChange(otrContact, ScSessionStatus.TIMED_OUT);
        try {
            otrEngine.getSession(sessionID).startSession();
        } catch (OtrException e) {
            Timber.e(e, "Error starting session");
            showError(sessionID, e.getMessage());
        }
    }

    @Override
    public String transformReceiving(OtrContact otrContact, String msgText)
    {
        SessionID sessionID = getSessionID(otrContact);
        try {
            return otrEngine.getSession(sessionID).transformReceiving(msgText);
        } catch (OtrException e) {
            Timber.e(e, "Error receiving the message");
            showError(sessionID, e.getMessage());
            return null;
        }
    }

    @Override
    public String[] transformSending(OtrContact otrContact, String msgText)
    {
        SessionID sessionID = getSessionID(otrContact);
        try {
            return otrEngine.getSession(sessionID).transformSending(msgText);
        } catch (OtrException e) {
            Timber.e(e, "Error transforming the message");
            showError(sessionID, e.getMessage());
            return null;
        }
    }

    public Session getSession(OtrContact otrContact)
    {
        SessionID sessionID = getSessionID(otrContact);
        return otrEngine.getSession(sessionID);
    }

    @Override
    public void initSmp(OtrContact otrContact, String question, String secret)
    {
        Session session = getSession(otrContact);
        try {
            session.initSmp(question, secret);

            SmpProgressDialog progressDialog = progressDialogMap.get(otrContact);
            if (progressDialog == null) {
                progressDialog = new SmpProgressDialog(otrContact.contact);
                progressDialogMap.put(otrContact, progressDialog);
            }
            progressDialog.init();
            progressDialog.setVisible(true);
        } catch (OtrException e) {
            Timber.e(e, "Error initializing SMP session with contact %s", otrContact.contact.getDisplayName());
            showError(session.getSessionID(), e.getMessage());
        }
    }

    @Override
    public void respondSmp(OtrContact otrContact, InstanceTag receiverTag, String question, String secret)
    {
        Session session = getSession(otrContact);
        try {
            session.respondSmp(receiverTag, question, secret);

            SmpProgressDialog progressDialog = progressDialogMap.get(otrContact);
            if (progressDialog == null) {
                progressDialog = new SmpProgressDialog(otrContact.contact);
                progressDialogMap.put(otrContact, progressDialog);
            }
            progressDialog.incrementProgress();
            progressDialog.setVisible(true);
        } catch (OtrException e) {
            Timber.e(e, "Error occurred when sending SMP response to contact %s", otrContact.contact.getDisplayName());
            showError(session.getSessionID(), e.getMessage());
        }
    }

    @Override
    public void abortSmp(OtrContact otrContact)
    {
        Session session = getSession(otrContact);
        try {
            session.abortSmp();

            SmpProgressDialog progressDialog = progressDialogMap.get(otrContact);
            if (progressDialog == null) {
                progressDialog = new SmpProgressDialog(otrContact.contact);
                progressDialogMap.put(otrContact, progressDialog);
            }
            progressDialog.dispose();
        } catch (OtrException e) {
            Timber.e(e, "Error aborting SMP session with contact %s", otrContact.contact.getDisplayName());
            showError(session.getSessionID(), e.getMessage());
        }
    }

    @Override
    public PublicKey getRemotePublicKey(OtrContact otrContact)
    {
        if (otrContact == null)
            return null;

        Session session = getSession(otrContact);
        return session.getRemotePublicKey();
    }

    @Override
    public List<Session> getSessionInstances(OtrContact otrContact)
    {
        if (otrContact == null)
            return Collections.emptyList();
        return getSession(otrContact).getInstances();
    }

    @Override
    public boolean setOutgoingSession(OtrContact otrContact, InstanceTag tag)
    {
        if (otrContact == null)
            return false;

        Session session = getSession(otrContact);
        scSessionStatusMap.remove(session.getSessionID());
        return session.setOutgoingInstance(tag);
    }

    @Override
    public Session getOutgoingSession(OtrContact otrContact)
    {
        if (otrContact == null)
            return null;

        SessionID sessionID = getSessionID(otrContact);
        return otrEngine.getSession(sessionID).getOutgoingInstance();
    }

    private class ScOtrEngineHost implements OtrEngineHost
    {
        @Override
        public KeyPair getLocalKeyPair(SessionID sessionID)
        {
            AccountID accountID = OtrActivator.getAccountIDByUID(sessionID.getAccountID());
            KeyPair keyPair = OtrActivator.scOtrKeyManager.loadKeyPair(accountID);
            if (keyPair == null)
                OtrActivator.scOtrKeyManager.generateKeyPair(accountID);
            return OtrActivator.scOtrKeyManager.loadKeyPair(accountID);
        }

        @Override
        public OtrPolicy getSessionPolicy(SessionID sessionID)
        {
            return getContactPolicy(getOtrContact(sessionID).contact);
        }

        @Override
        public void injectMessage(SessionID sessionID, String messageText)
        {
            OtrContact otrContact = getOtrContact(sessionID);
            Contact contact = otrContact.contact;
            ContactResource resource = otrContact.resource;

            // Following may return null even resource name is the same
//			ContactResource resource = null;
//			if (contact.supportResources()) {
//				Collection<ContactResource> resources = contact.getResources();
//				if (resources != null) {
//					for (ContactResource r : resources) {
//						if (r.equals(otrContact.resource)) {
//							resource = r;
//							break;
//						}
//					}
//				}
//			}

            OperationSetBasicInstantMessaging imOpSet
                    = contact.getProtocolProvider().getOperationSet(OperationSetBasicInstantMessaging.class);

            // This is a dirty way of detecting whether the injected message contains HTML markup.
            // If this is the case then we should create the message with the appropriate content
            // type so that the remote party can properly display the HTML. When otr4j injects
            // QueryMessages it calls OtrEngineHost.getFallbackMessage() which is currently the
            // only host method that uses HTML so we can simply check if the injected message
            // contains the string that getFallbackMessage() returns.
            String otrHtmlFallbackMessage = "<a href=\"https://en.wikipedia.org/wiki/Off-the-Record_Messaging\">";
            int mimeType = messageText.contains(otrHtmlFallbackMessage)
                    ? IMessage.ENCODE_HTML : IMessage.ENCODE_PLAIN;
            IMessage message = imOpSet.createMessage(messageText, mimeType, null);

            injectedMessageUIDs.add(message.getMessageUID());
            imOpSet.sendInstantMessage(contact, resource, message);
        }

        @Override
        public void showError(SessionID sessionID, String err)
        {
            ScOtrEngineImpl.this.showError(sessionID, err);
        }

        @Override
        public void showAlert(SessionID sessionID, String warn)
        {
            OtrContact otrContact = getOtrContact(sessionID);
            if (otrContact == null)
                return;

            Contact contact = otrContact.contact;
            OtrActivator.uiService.getChat(contact).addMessage(contact.getAddress(),
                    new Date(), ChatMessage.MESSAGE_SYSTEM, IMessage.ENCODE_PLAIN, warn);
        }

        @Override
        public void unreadableMessageReceived(SessionID sessionID)
                throws OtrException
        {
            OtrContact otrContact = getOtrContact(sessionID);
            String resourceName = otrContact.resource != null
                    ? "/" + otrContact.resource.getResourceName() : "";

            Contact contact = otrContact.contact;
            String error = aTalkApp.getResString(R.string.plugin_otr_activator_unreadablemsgreceived,
                    contact.getDisplayName() + resourceName);
            OtrActivator.uiService.getChat(contact).addMessage(contact.getAddress(), new Date(),
                    ChatMessage.MESSAGE_ERROR, IMessage.ENCODE_PLAIN, error);
        }

        @Override
        public void unencryptedMessageReceived(SessionID sessionID, String msg)
                throws OtrException
        {
            OtrContact otrContact = getOtrContact(sessionID);
            if (otrContact == null)
                return;

            Contact contact = otrContact.contact;
            String warn = aTalkApp.getResString(R.string.plugin_otr_activator_unencryptedmsgreceived);
            OtrActivator.uiService.getChat(contact).addMessage(contact.getAddress(), new Date(),
                    ChatMessage.MESSAGE_SYSTEM, IMessage.ENCODE_PLAIN, warn);
        }

        @Override
        public void smpError(SessionID sessionID, int tlvType, boolean cheated)
                throws OtrException
        {
            OtrContact otrContact = getOtrContact(sessionID);
            if (otrContact == null)
                return;

            Contact contact = otrContact.contact;
            Timber.d("SMP error occurred. Contact: %s. TLV type: %s. Cheated: %s",
                    contact.getDisplayName(), tlvType, cheated);

            String error = aTalkApp.getResString(R.string.plugin_otr_activator_smperror);
            OtrActivator.uiService.getChat(contact).addMessage(contact.getAddress(),
                    new Date(), ChatMessage.MESSAGE_ERROR, IMessage.ENCODE_PLAIN, error);

            SmpProgressDialog progressDialog = progressDialogMap.get(otrContact);
            if (progressDialog == null) {
                progressDialog = new SmpProgressDialog(contact);
                progressDialogMap.put(otrContact, progressDialog);
            }
            progressDialog.setProgressFail();
            progressDialog.setVisible(true);
        }

        @Override
        public void smpAborted(SessionID sessionID)
                throws OtrException
        {
            OtrContact otrContact = getOtrContact(sessionID);
            if (otrContact == null)
                return;

            Contact contact = otrContact.contact;
            Session session = otrEngine.getSession(sessionID);
            if (session.isSmpInProgress()) {
                String warn = aTalkApp.getResString(R.string.plugin_otr_activator_smpaborted, contact.getDisplayName());
                OtrActivator.uiService.getChat(contact).addMessage(contact.getAddress(),
                        new Date(), ChatMessage.MESSAGE_SYSTEM, IMessage.ENCODE_PLAIN, warn);

                SmpProgressDialog progressDialog = progressDialogMap.get(otrContact);
                if (progressDialog == null) {
                    progressDialog = new SmpProgressDialog(contact);
                    progressDialogMap.put(otrContact, progressDialog);
                }
                progressDialog.setProgressFail();
                progressDialog.setVisible(true);
            }
        }

        @Override
        public void finishedSessionMessage(SessionID sessionID, String msgText)
                throws OtrException
        {
            OtrContact otrContact = getOtrContact(sessionID);
            if (otrContact == null)
                return;

//			String resourceName = otrContact.resource != null ? "/" + otrContact.resource.getResourceName() : "";
            Contact contact = otrContact.contact;
            String error = aTalkApp.getResString(R.string.plugin_otr_activator_sessionfinishederror,
                    contact.getDisplayName());
            OtrActivator.uiService.getChat(contact).addMessage(contact.getAddress(),
                    new Date(), ChatMessage.MESSAGE_ERROR, IMessage.ENCODE_PLAIN, error);
        }

        @Override
        public void requireEncryptedMessage(SessionID sessionID, String msgText)
                throws OtrException
        {
            OtrContact otrContact = getOtrContact(sessionID);
            if (otrContact == null)
                return;

            Contact contact = otrContact.contact;
            String error = aTalkApp.getResString(R.string.plugin_otr_activator_requireencryption, msgText);
            OtrActivator.uiService.getChat(contact).addMessage(contact.getAddress(),
                    new Date(), ChatMessage.MESSAGE_ERROR, IMessage.ENCODE_PLAIN, error);
        }

        @Override
        public byte[] getLocalFingerprintRaw(SessionID sessionID)
        {
            AccountID accountID = OtrActivator.getAccountIDByUID(sessionID.getAccountID());
            return OtrActivator.scOtrKeyManager.getLocalFingerprintRaw(accountID);
        }

        @Override
        public void askForSecret(SessionID sessionID, InstanceTag receiverTag, String question)
        {
            OtrContact otrContact = getOtrContact(sessionID);
            if (otrContact == null)
                return;

            Contact contact = otrContact.contact;
            SmpAuthenticateBuddyDialog dialog = new SmpAuthenticateBuddyDialog(otrContact, receiverTag, question);
            dialog.setVisible(true);

            SmpProgressDialog progressDialog = progressDialogMap.get(otrContact);
            if (progressDialog == null) {
                progressDialog = new SmpProgressDialog(contact);
                progressDialogMap.put(otrContact, progressDialog);
            }
            progressDialog.init();
            progressDialog.setVisible(true);
        }

        @Override
        public void verify(SessionID sessionID, String fingerprint, boolean approved)
        {
            OtrContact otrContact = getOtrContact(sessionID);
            if (otrContact == null)
                return;

            Contact contact = otrContact.contact;
            OtrActivator.scOtrKeyManager.verify(otrContact, fingerprint);

            SmpProgressDialog progressDialog = progressDialogMap.get(otrContact);
            if (progressDialog == null) {
                progressDialog = new SmpProgressDialog(contact);
                progressDialogMap.put(otrContact, progressDialog);
            }
            progressDialog.setProgressSuccess();
            progressDialog.setVisible(true);
        }

        @Override
        public void unverify(SessionID sessionID, String fingerprint)
        {
            OtrContact otrContact = getOtrContact(sessionID);
            if (otrContact == null)
                return;

            Contact contact = otrContact.contact;
            OtrActivator.scOtrKeyManager.unverify(otrContact, fingerprint);

            SmpProgressDialog progressDialog = progressDialogMap.get(otrContact);
            if (progressDialog == null) {
                progressDialog = new SmpProgressDialog(contact);
                progressDialogMap.put(otrContact, progressDialog);
            }
            progressDialog.setProgressFail();
            progressDialog.setVisible(true);
        }

        @Override
        public String getReplyForUnreadableMessage(SessionID sessionID)
        {
            AccountID accountID = OtrActivator.getAccountIDByUID(sessionID.getAccountID());
            return aTalkApp.getResString(R.string.plugin_otr_activator_unreadablemsgreply,
                    accountID.getDisplayName(), accountID.getDisplayName());
        }

        @Override
        public String getFallbackMessage(SessionID sessionID)
        {
            AccountID accountID = OtrActivator.getAccountIDByUID(sessionID.getAccountID());
            return aTalkApp.getResString(R.string.plugin_otr_activator_fallbackmessage,
                    accountID.getDisplayName());
        }

        @Override
        public void multipleInstancesDetected(SessionID sessionID)
        {
            OtrContact otrContact = getOtrContact(sessionID);
            if (otrContact == null)
                return;

            String resourceName = otrContact.resource != null ? "/" + otrContact.resource.getResourceName() : "";
            Contact contact = otrContact.contact;
            String message = aTalkApp.getResString(R.string.plugin_otr_activator_multipleinstancesdetected,
                    contact.getDisplayName() + resourceName);
            OtrActivator.uiService.getChat(contact).addMessage(contact.getAddress(),
                    new Date(), ChatMessage.MESSAGE_SYSTEM, IMessage.ENCODE_HTML, message);
        }

        @Override
        public void messageFromAnotherInstanceReceived(SessionID sessionID)
        {
            OtrContact otrContact = getOtrContact(sessionID);
            if (otrContact == null)
                return;

            String resourceName = otrContact.resource != null ? "/" + otrContact.resource.getResourceName() : "";
            Contact contact = otrContact.contact;
            String message = aTalkApp.getResString(R.string.plugin_otr_activator_msgfromanotherinstance,
                    contact.getDisplayName() + resourceName);
            OtrActivator.uiService.getChat(contact).addMessage(contact.getAddress(),
                    new Date(), ChatMessage.MESSAGE_SYSTEM, IMessage.ENCODE_HTML, message);
        }

        /**
         * Provide fragmenter instructions according to the Instant Messaging transport channel of
         * the contact's protocol.
         */
        @Override
        public FragmenterInstructions getFragmenterInstructions(final SessionID sessionID)
        {
            final OtrContact otrContact = getOtrContact(sessionID);
            final OperationSetBasicInstantMessagingTransport transport
                    = otrContact.contact.getProtocolProvider().getOperationSet(
                    OperationSetBasicInstantMessagingTransport.class);
            if (transport == null) {
                // There is no operation set for querying transport parameters.
                // Assuming transport capabilities are unlimited.
                Timber.d("No implementation of BasicInstantMessagingTransport available. Assuming OTR defaults for OTR fragmentation instructions.");
                return null;
            }

            int messageSize = transport.getMaxMessageSize(otrContact.contact);
            if (messageSize == OperationSetBasicInstantMessagingTransport.UNLIMITED) {
                messageSize = FragmenterInstructions.UNLIMITED;
            }

            int numberOfMessages = transport.getMaxNumberOfMessages(otrContact.contact);
            if (numberOfMessages == OperationSetBasicInstantMessagingTransport.UNLIMITED) {
                numberOfMessages = FragmenterInstructions.UNLIMITED;
            }
            Timber.d("OTR fragmentation instructions for sending a message to %s (%s). Max messages no: %s, Max message size: %s",
                    otrContact.contact.getDisplayName(), otrContact.contact.getAddress(), numberOfMessages, messageSize);
            return new FragmenterInstructions(numberOfMessages, messageSize);
        }
    }

    /**
     * Manages the scheduling of TimerTasks that are used to set Contact's ScSessionStatus after a
     * period of time.
     *
     * @author Marin Dzhigarov
     */
    private class ScSessionStatusScheduler
    {
        private final Timer timer = new Timer();
        private final Map<OtrContact, TimerTask> tasks = new ConcurrentHashMap<>();

        public void scheduleScSessionStatusChange(final OtrContact otrContact, final ScSessionStatus status)
        {
            cancel(otrContact);
            TimerTask task = new TimerTask()
            {
                @Override
                public void run()
                {
                    setSessionStatus(otrContact, status);
                }
            };
            timer.schedule(task, SESSION_TIMEOUT);
            tasks.put(otrContact, task);
        }

        public void cancel(final OtrContact otrContact)
        {
            TimerTask task = tasks.get(otrContact);
            if (task != null)
                task.cancel();
            tasks.remove(otrContact);
        }

        public void serviceChanged(ServiceEvent ev)
        {
            Object service = OtrActivator.bundleContext.getService(ev.getServiceReference());
            if (!(service instanceof ProtocolProviderService))
                return;

            if (ev.getType() == ServiceEvent.UNREGISTERING) {
                ProtocolProviderService provider = (ProtocolProviderService) service;
                Iterator<OtrContact> i = tasks.keySet().iterator();

                while (i.hasNext()) {
                    OtrContact otrContact = i.next();
                    if (provider.equals(otrContact.contact.getProtocolProvider())) {
                        cancel(otrContact);
                        i.remove();
                    }
                }
            }
        }
    }
}
