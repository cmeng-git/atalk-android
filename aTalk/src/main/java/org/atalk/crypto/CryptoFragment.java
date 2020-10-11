/*
 * aTalk, android VoIP and Instant Messaging client
 * Copyright 2014 Eng Chong Meng
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.atalk.crypto;

import android.content.Context;
import android.content.Intent;
import android.text.TextUtils;
import android.view.*;

import net.java.otr4j.OtrPolicy;
import net.java.sip.communicator.plugin.otr.*;
import net.java.sip.communicator.plugin.otr.OtrContactManager.OtrContact;
import net.java.sip.communicator.service.contactlist.MetaContact;
import net.java.sip.communicator.service.gui.ChatLinkClickedListener;
import net.java.sip.communicator.service.msghistory.MessageHistoryService;
import net.java.sip.communicator.service.protocol.*;
import net.java.sip.communicator.service.protocol.event.ChatRoomMemberPresenceChangeEvent;
import net.java.sip.communicator.service.protocol.event.ChatRoomMemberPresenceListener;

import org.atalk.android.R;
import org.atalk.android.aTalkApp;
import org.atalk.android.gui.AndroidGUIActivator;
import org.atalk.android.gui.chat.*;
import org.atalk.android.gui.settings.SettingsActivity;
import org.atalk.crypto.listener.CryptoModeChangeListener;
import org.atalk.crypto.omemo.AndroidOmemoService;
import org.atalk.crypto.omemo.OmemoAuthenticateDialog;
import org.atalk.crypto.otr.OTRv3OutgoingSessionSwitcher;
import org.atalk.service.osgi.OSGiFragment;
import org.jivesoftware.smack.*;
import org.jivesoftware.smackx.muc.MultiUserChat;
import org.jivesoftware.smackx.muc.MultiUserChatManager;
import org.jivesoftware.smackx.omemo.*;
import org.jivesoftware.smackx.omemo.exceptions.*;
import org.jivesoftware.smackx.omemo.internal.OmemoCachedDeviceList;
import org.jivesoftware.smackx.omemo.internal.OmemoDevice;
import org.jivesoftware.smackx.omemo.trust.OmemoFingerprint;
import org.jivesoftware.smackx.pubsub.PubSubException;
import org.jxmpp.jid.*;

import java.io.IOException;
import java.net.URI;
import java.security.PublicKey;
import java.util.*;

import androidx.fragment.app.FragmentTransaction;
import timber.log.Timber;

import static net.java.sip.communicator.plugin.otr.OtrActivator.scOtrEngine;
import static net.java.sip.communicator.plugin.otr.OtrActivator.scOtrKeyManager;
import static org.atalk.android.gui.chat.ChatFragment.MSGTYPE_MUC_NORMAL;
import static org.atalk.android.gui.chat.ChatFragment.MSGTYPE_NORMAL;
import static org.atalk.android.gui.chat.ChatFragment.MSGTYPE_OMEMO;
import static org.atalk.android.gui.chat.ChatFragment.MSGTYPE_OTR;
import static org.atalk.android.gui.chat.ChatFragment.MSGTYPE_OTR_UA;
import static org.atalk.android.gui.chat.ChatFragment.MSGTYPE_UNKNOWN;

/**
 * Fragment when added to <tt>Activity</tt> will display the padlock allowing user to select
 * various type of encryption options. Only currently active chat is handled by this fragment.
 *
 * @author Eng Chong Meng
 */
public class CryptoFragment extends OSGiFragment
        implements ChatSessionManager.CurrentChatListener, ChatRoomMemberPresenceListener,
        OmemoAuthenticateDialog.AuthenticateListener
{
    /**
     * A map of the user selected chatType. The stored key is the chatSessionId. The information
     * is used to restore the last user selected encryption choice when a chat window is page slided in view.
     */
    private static final Map<String, Integer> encryptionChoice = new LinkedHashMap<>();

    /**
     * A cache map of the Descriptor and its OmemoSupport capability. The Descriptor can be ChatRoom or Contact
     */
    private static final Map<Object, Boolean> omemoCapable = new LinkedHashMap<>();

    /**
     * A cache map of the Descriptor and its CryptoModeChangeListener. Need this as listener is added only
     * when the chatFragment is launched. Slide pages does not get updated.
     * ChatType change event is sent to CryptoModeChangeListener to update chatFragment background colour:
     */
    private static final Map<Object, CryptoModeChangeListener> cryptoModeChangeListeners = new LinkedHashMap<>();

    /**
     * Menu instances used to select and control the crypto choices.
     */
    private MenuItem mCryptoChoice;
    private MenuItem mNone;
    private MenuItem mOmemo;
    private MenuItem mOtr;
    private MenuItem mOtr_Session;

    private XMPPConnection mConnection;

    /**
     * Can either be Contact or ChatRoom
     */
    private Object mDescriptor;

    private OmemoManager mOmemoManager;
    private OmemoStore mOmemoStore;

    private int mChatType = MSGTYPE_NORMAL;

    /**
     * Current active instance of chatSession & user.
     */
    private ChatPanel activeChat = null;
    private MultiUserChat mMultiUserChat;
    private String mEntity;
    private String mCurrentChatSessionId;

    /**
     * Otr Contact for currently active chatSession.
     */
    private OtrContact currentOtrContact = null;
    private MessageHistoryService mMHS;

    /**
     * isOmemoMode flag prevents otr from changing status when transition from otr to omemo when
     * <tt>true</tt>; otr listener is async event triggered.
     */
    private boolean isOmemoMode = false;

    /**
     * Creates a new instance of <tt>OtrFragment</tt>.
     */
    public CryptoFragment()
    {
        setHasOptionsMenu(true);
        mMHS = AndroidGUIActivator.getMessageHistoryService();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onStop()
    {
        ChatSessionManager.removeCurrentChatListener(this);
        scOtrEngine.removeListener(scOtrEngineListener);
        scOtrKeyManager.removeListener(scOtrKeyManagerListener);
        super.onStop();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater)
    {
        super.onCreateOptionsMenu(menu, inflater);

        // (OtrActivator.scOtrEngine == null)
        // This happens when Activity is recreated by the system after OSGi service has been
        // killed (and the whole process)
        if (AndroidGUIActivator.bundleContext == null) {
            Timber.e("OSGi service probably not initialized");
            return;
        }
        mOmemoStore = OmemoService.getInstance().getOmemoStoreBackend();

        /*
         * Menu instances used to select and control the crypto choices.
         * Add chat encryption choices if not found
         */
        if (menu.findItem(R.id.encryption_none) == null)
            inflater.inflate(R.menu.crypto_choices, menu);

        mCryptoChoice = menu.findItem(R.id.crypto_choice);
        mNone = menu.findItem(R.id.encryption_none);
        mOmemo = menu.findItem(R.id.encryption_omemo);
        mOtr = menu.findItem(R.id.encryption_otr);
        mOtr_Session = menu.findItem(R.id.otr_session);

        // Initialize the padlock icon only after the Crypto menu is created
        doInit();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item)
    {
        boolean hasChange = false;
        boolean showMultiOtrSession = false;
        item.setChecked(true);

        switch (item.getItemId()) {
            case R.id.crypto_choice:
                Boolean isOmemoSupported = omemoCapable.get(mDescriptor);
                if (isOmemoSupported == null)
                    isOmemoSupported = false;
                mOmemo.setEnabled(isOmemoSupported);
                mOmemo.getIcon().setAlpha(isOmemoSupported ? 255 : 80);

                // sync button check to current chatType
                if (activeChat != null) {
                    MenuItem mItem = checkCryptoButton(activeChat.getChatType());
                    mItem.setChecked(true);
                }
                return true;
            case R.id.encryption_none:
                // if ((mChatType != MSGTYPE_NORMAL) && (mChatType != MSGTYPE_MUC_NORMAL)) {
                if (mDescriptor instanceof Contact)
                    mChatType = ChatFragment.MSGTYPE_NORMAL;
                else
                    mChatType = ChatFragment.MSGTYPE_MUC_NORMAL;
                hasChange = true;
                doHandleOtrPressed(false);
                doHandleOmemoPressed(false);
                //}
                break;
            case R.id.encryption_omemo:
                if (!activeChat.isOmemoChat())
                    mChatType = MSGTYPE_OMEMO;
                hasChange = true;
                doHandleOtrPressed(false);
                doHandleOmemoPressed(true);
                break;
            case R.id.encryption_otr:
                if (!activeChat.isOTRChat())
                    mChatType = MSGTYPE_OTR;
                hasChange = true; //only if it is in plain text mode +++? currently
                // hasChange = (ScSessionStatus.PLAINTEXT == scOtrEngine.getSessionStatus(currentOtrContact));
                showMultiOtrSession = true;
                doHandleOtrPressed(true);
                doHandleOmemoPressed(false);
                break;

            case R.id.otr_session:
                OTRv3OutgoingSessionSwitcher otrSessionDialog = OTRv3OutgoingSessionSwitcher.newInstance(currentOtrContact);
                FragmentTransaction ft = getParentFragmentManager().beginTransaction();
                ft.addToBackStack(null);
                otrSessionDialog.show(ft, "otrDialog");
                break;
            default:
                break;
        }

        if (hasChange) {
            String chatId = ChatSessionManager.getCurrentChatId();
            encryptionChoice.put(chatId, mChatType);
            setStatusOmemo(mChatType);
            // Timber.w("update persistent ChatType to: %s", mChatType);

            mOtr_Session.setVisible(showMultiOtrSession);
            // cmeng (20200717): proceed to save but will forced to normal on retrieval
            // Do not store OTR as it is not valid on new session startup
            // if ((mChatType != MSGTYPE_OTR) && (mChatType != MSGTYPE_OTR_UA))
            mMHS.setSessionChatType(activeChat.getChatSession(), mChatType);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    /**
     * Synchronise the cryptoChoice button checkMark with the current chatType
     *
     * @param chatType Sync cryptoChoice button check to the given chatType
     * @return the button menuItem corresponding to the given chatType
     */
    private MenuItem checkCryptoButton(int chatType)
    {
        MenuItem mItem;
        switch (chatType) {
            case ChatFragment.MSGTYPE_NORMAL:
            case ChatFragment.MSGTYPE_MUC_NORMAL:
                // if offline or in plain mode
                if ((currentOtrContact == null)
                        || ScSessionStatus.PLAINTEXT == scOtrEngine.getSessionStatus(currentOtrContact))
                    mItem = mNone;
                else
                    mItem = mOtr;
                break;

            case ChatFragment.MSGTYPE_OMEMO:
            case ChatFragment.MSGTYPE_OMEMO_UA:
            case ChatFragment.MSGTYPE_OMEMO_UT:
                mItem = mOmemo;
                break;

            case ChatFragment.MSGTYPE_OTR:
            case ChatFragment.MSGTYPE_OTR_UA:
                mItem = mOtr;
                break;

            default:
                mItem = mNone;
        }
        return mItem;
    }

    /**
     * Handle OMEMO state when the option is selected/unSelected.
     */
    private void doHandleOmemoPressed(final boolean enable)
    {
        // return: nothing to do if not enable
        isOmemoMode = enable;
        if (!enable || (mDescriptor == null) || !activeChat.getProtocolProvider().isRegistered())
            return;

        // Linked map between OmemoDevice and its fingerprint.
        HashMap<OmemoDevice, OmemoFingerprint> fingerPrints = new HashMap<>();
        OmemoDevice omemoDevice;
        OmemoFingerprint fingerPrint;

        boolean allTrusted = true;

        if (mDescriptor instanceof Contact) {
            BareJid bareJid = ((Contact) mDescriptor).getJid().asBareJid();
            mEntity = bareJid.toString();
            try {
                fingerPrints = mOmemoManager.getActiveFingerprints(bareJid);
            } catch (CorruptedOmemoKeyException | CannotEstablishOmemoSessionException
                    | SmackException.NotConnectedException | SmackException.NotLoggedInException
                    | InterruptedException | SmackException.NoResponseException
                    | IllegalArgumentException | IOException e) {
                // IllegalArgumentException is throw when IdentityKeyPair is null
                Timber.w("Fetching active fingerPrints has failed: %s", e.getMessage());
            }

            try {
                mOmemoManager.encrypt(bareJid, "Hi buddy!");
            } catch (UndecidedOmemoIdentityException e) {
                Set<OmemoDevice> omemoDevices = e.getUndecidedDevices();
                Timber.w("There are undecided Omemo devices: %s", omemoDevices);
                startActivity(OmemoAuthenticateDialog.createIntent(getContext(), mOmemoManager, omemoDevices, this));
                allTrusted = false;
            } catch (InterruptedException | SmackException.NoResponseException | CryptoFailedException
                    | SmackException.NotConnectedException | SmackException.NotLoggedInException e) {
                mChatType = ChatFragment.MSGTYPE_MUC_NORMAL;
                activeChat.addMessage(mEntity, new Date(), ChatMessage.MESSAGE_ERROR, IMessage.ENCODE_PLAIN,
                        getString(R.string.crypto_msg_OMEMO_SESSION_SETUP_FAILED, e.getMessage()));
                Timber.i("OMEMO changes mChatType to: %s", mChatType);
                return;
            } catch (Exception e) { // catch any non-advertised exception
                Timber.w("UndecidedOmemoIdentity check failed: %s", e.getMessage());
                mChatType = ChatFragment.MSGTYPE_MUC_NORMAL;
                activeChat.addMessage(mEntity, new Date(), ChatMessage.MESSAGE_ERROR, IMessage.ENCODE_PLAIN,
                        getString(R.string.crypto_msg_OMEMO_SESSION_SETUP_FAILED, e.getMessage()));
                Timber.w("Revert OMEMO mChatType to: %s", mChatType);
                return;
            }

            int numUntrusted = 0;
            for (Map.Entry<OmemoDevice, OmemoFingerprint> entry : fingerPrints.entrySet()) {
                omemoDevice = entry.getKey();
                fingerPrint = entry.getValue();
                if (!mOmemoManager.isTrustedOmemoIdentity(omemoDevice, fingerPrint)) {
                    numUntrusted++;
                }
            }
            /*
             * Found no trusted device for OMEMO session, so set to MSGTYPE_OMEMO_UT
             * Encrypted message without the buddy <rid/> key
             */
            if ((numUntrusted > 0) && (numUntrusted == fingerPrints.size())) {
                mChatType = ChatFragment.MSGTYPE_OMEMO_UT;
                activeChat.addMessage(mEntity, new Date(), ChatMessage.MESSAGE_SYSTEM, IMessage.ENCODE_PLAIN,
                        getString(R.string.crypto_msg_OMEMO_SESSION_UNTRUSTED));
            }
            else if (allTrusted) {
                mChatType = ChatFragment.MSGTYPE_OMEMO;
            }
            else {
                mChatType = ChatFragment.MSGTYPE_OMEMO_UA;
                activeChat.addMessage(mEntity, new Date(), ChatMessage.MESSAGE_SYSTEM, IMessage.ENCODE_PLAIN,
                        getString(R.string.crypto_msg_OMEMO_SESSION_UNVERIFIED));
            }
        }
        else if (mDescriptor instanceof ChatRoom) {
            ((ChatRoom) mDescriptor).addMemberPresenceListener(this);
            EntityBareJid entityBareJid = ((ChatRoom) mDescriptor).getIdentifier();
            mEntity = entityBareJid.toString();
            MultiUserChatManager mucMgr = MultiUserChatManager.getInstanceFor(mConnection);
            mMultiUserChat = mucMgr.getMultiUserChat(entityBareJid);

            try {
                mOmemoManager.encrypt(mMultiUserChat, "Hi everybody!");
            } catch (UndecidedOmemoIdentityException e) {
                Set<OmemoDevice> omemoDevices = e.getUndecidedDevices();
                Timber.w("There are undecided Omemo devices: %s", omemoDevices);
                startActivity(OmemoAuthenticateDialog.createIntent(getContext(), mOmemoManager, omemoDevices, this));
                allTrusted = false;
            } catch (NoOmemoSupportException | InterruptedException | SmackException.NoResponseException
                    | XMPPException.XMPPErrorException | CryptoFailedException
                    | SmackException.NotConnectedException | SmackException.NotLoggedInException e) {
                mChatType = ChatFragment.MSGTYPE_MUC_NORMAL;
                activeChat.addMessage(mEntity, new Date(), ChatMessage.MESSAGE_ERROR, IMessage.ENCODE_PLAIN,
                        getString(R.string.crypto_msg_OMEMO_SESSION_SETUP_FAILED, e.getMessage()));
                return;
            } catch (Exception e) { // catch any non-advertised exception
                Timber.w("UndecidedOmemoIdentity check failed: %s", e.getMessage());
                mChatType = ChatFragment.MSGTYPE_MUC_NORMAL;
                activeChat.addMessage(mEntity, new Date(), ChatMessage.MESSAGE_ERROR, IMessage.ENCODE_PLAIN,
                        getString(R.string.crypto_msg_OMEMO_SESSION_SETUP_FAILED, e.getMessage()));
            }

            allTrusted = allTrusted && isAllTrusted(mMultiUserChat);
            if (allTrusted) {
                mChatType = ChatFragment.MSGTYPE_OMEMO;
            }
            else {
                mChatType = ChatFragment.MSGTYPE_OMEMO_UA;
                activeChat.addMessage(mEntity, new Date(), ChatMessage.MESSAGE_SYSTEM, IMessage.ENCODE_PLAIN,
                        getString(R.string.crypto_msg_OMEMO_SESSION_UNVERIFIED_UNTRUSTED));
            }
        }
        // Timber.d("OMEMO changes mChatType to: %s", mChatType);
        // else Let calling method or OTR Listener to decide what to set
    }

    /**
     * Check to see if all muc recipients are verified or trusted
     *
     * @param multiUserChat MultiUserChat
     * @return return <tt>true</tt> if all muc recipients are verified or trusted. Otherwise <tt>false</tt>
     */

    private boolean isAllTrusted(MultiUserChat multiUserChat)
    {
        boolean allTrusted = true;
        OmemoFingerprint fingerPrint;
        BareJid recipient;

        for (EntityFullJid e : multiUserChat.getOccupants()) {
            recipient = multiUserChat.getOccupant(e).getJid().asBareJid();
            OmemoCachedDeviceList theirDevices = null;
            try {
                theirDevices = mOmemoStore.loadCachedDeviceList(mOmemoManager.getOwnDevice(), recipient);
            } catch (IOException ex) {
                Timber.w("IOException: %s", ex.getMessage());
            }
            for (int id : theirDevices.getActiveDevices()) {
                OmemoDevice recipientDevice = new OmemoDevice(recipient, id);
                try {
                    fingerPrint = mOmemoManager.getFingerprint(recipientDevice);
                    allTrusted = mOmemoManager.isTrustedOmemoIdentity(recipientDevice, fingerPrint)
                            && allTrusted;
                } catch (CorruptedOmemoKeyException | CannotEstablishOmemoSessionException e1) {
                    Timber.w("AllTrusted check exception: %s", e1.getMessage());
                } catch (SmackException.NotLoggedInException | SmackException.NotConnectedException
                        | SmackException.NoResponseException | InterruptedException | IOException e1) {
                    e1.printStackTrace();
                }
            }
        }
        return allTrusted;
    }

    /**
     * Trigger when invited participant join the conference. This will fill the partial identities table for the
     * participant and request fingerPrint verification is undecided. Hence ensuring that the next sent message
     * is properly received by the new member.
     *
     * @param evt the <tt>ChatRoomMemberPresenceChangeEvent</tt> instance containing the source chat
     */
    @Override
    public void memberPresenceChanged(ChatRoomMemberPresenceChangeEvent evt)
    {
        if ((activeChat.isOmemoChat()) && ChatRoomMemberPresenceChangeEvent.MEMBER_JOINED.equals(evt.getEventType())) {
            try {
                mOmemoManager.encrypt(mMultiUserChat, "Hi everybody!");
            } catch (UndecidedOmemoIdentityException e) {
                Set<OmemoDevice> omemoDevices = e.getUndecidedDevices();
                Timber.w("There are undecided Omemo devices: %s", omemoDevices);
                startActivity(OmemoAuthenticateDialog.createIntent(getContext(), mOmemoManager, omemoDevices, this));
            } catch (NoOmemoSupportException | InterruptedException | SmackException.NoResponseException
                    | XMPPException.XMPPErrorException | CryptoFailedException | IOException
                    | SmackException.NotConnectedException | SmackException.NotLoggedInException e) {
                Timber.w("UndecidedOmemoIdentity check failed: %s", e.getMessage());
            }
        }
    }

    /**
     * Handle OTR state when the option is selected/unSelected.
     * mChatType is set by ScOtrEngineListener
     * <p>
     * When (enable == true):
     * - End session and go back to plain text for all non-encrypt states i.e.
     * TIMED_OUT, LOADING and FINISHED (remote client end otr session)
     * - Only start otr session if it is in PLAINTEXT mode
     * - Return if it is already in ENCRYPTED state
     * <p>
     * Default action when (enable == false):
     * - End session and go back to plain text for all states i.e.
     * TIMED_OUT, LOADING, FINISHED (remote client end otr session) and ENCRYPTED
     * - Do nothing if it is already in PLAINTEXT state
     * <p>
     * Run in new thread prevents network on main thread exception
     */
    private void doHandleOtrPressed(final boolean enable)
    {
        if ((currentOtrContact == null) || !activeChat.getProtocolProvider().isRegistered())
            return;

        new Thread()
        {
            @Override
            public void run()
            {
                Contact contact = currentOtrContact.contact;
                OtrPolicy policy = scOtrEngine.getContactPolicy(contact);
                ScSessionStatus status = scOtrEngine.getSessionStatus(currentOtrContact);
                int chatType = MSGTYPE_UNKNOWN;

                if (enable) {
                    switch (status) {
                        // Do nothing if it is already in encryption mode
                        case ENCRYPTED:
                            chatType = MSGTYPE_OTR;
                            break;
                        /*
                         * Default action for timeout, loading (handset) and finished
                         * is end otr private session.
                         */
                        case TIMED_OUT:
                            policy.setSendWhitespaceTag(false);
                            scOtrEngine.setContactPolicy(contact, policy);

                        case LOADING:
                        case FINISHED:
                            scOtrEngine.endSession(currentOtrContact);
                            break;

                        // Start otr private session if in plainText mode
                        case PLAINTEXT:
                            // End any unclean session if any before proceed
                            // scOtrEngine.endSession(currentOtrContact);

                            OtrPolicy globalPolicy = scOtrEngine.getGlobalPolicy();
                            policy.setSendWhitespaceTag(globalPolicy.getSendWhitespaceTag());
                            scOtrEngine.setContactPolicy(contact, policy);
                            scOtrEngine.startSession(currentOtrContact);
                            break;
                    }
                }
                else {
                    // let calling method or omemo decides what to set when OTR is disabled
                    switch (status) {
                        case PLAINTEXT:
                            chatType = MSGTYPE_NORMAL;
                            break;

                        /*
                         * Default action for timeout, encrypted, loading (handshake) and finished
                         * is end otr private session.
                         */
                        case TIMED_OUT:
                        case ENCRYPTED:
                            policy.setSendWhitespaceTag(false);
                            scOtrEngine.setContactPolicy(contact, policy);

                        case LOADING:
                        case FINISHED:
                            scOtrEngine.endSession(currentOtrContact);
                            break;
                    }
                }
                // cmeng - 20190721 - do not perform any UI update on user crypto option selection
                // Just update OtrEngine state and let the otr events take care the rest of UI update.
            }
        }.start();
    }

    /**
     * Register listeners, initializes the padlock icons and encryption options menu enable state.
     * Must only be performed after the completion of onCreateOptionsMenu().
     *
     * @see #onCreateOptionsMenu(Menu, MenuInflater)
     */
    private void doInit()
    {
        ChatSessionManager.addCurrentChatListener(this);
        scOtrEngine.addListener(scOtrEngineListener);
        scOtrKeyManager.addListener(scOtrKeyManagerListener);

        // Setup currentChatSession options for the Jid if login in
        String currentChatId = ChatSessionManager.getCurrentChatId();
        if (currentChatId != null)
            setCurrentChatSession(currentChatId);
    }

    /**
     * Event trigger when user slide the chatFragment
     *
     * @param chatId id of current chat session or <tt>null</tt> if there is no chat currently
     */
    @Override
    public void onCurrentChatChanged(String chatId)
    {
        setCurrentChatSession(chatId);
    }

    /**
     * Triggered from onCreateOption() or onCurrentChatChanged()
     * <p>
     * Sets current <tt>ChatPanel</tt> identified by given <tt>chatSessionKey</tt>.
     * Init Crypto choice to last selected or encryption_none if new
     * Set currentOtrContact as appropriate if OTR is supported
     *
     * @param chatSessionId chat session key managed by <tt>ChatSessionManager</tt>
     */
    private void setCurrentChatSession(String chatSessionId)
    {
        mCurrentChatSessionId = chatSessionId;
        MetaContact metaContact = null;
        activeChat = ChatSessionManager.getActiveChat(chatSessionId);
        if ((activeChat != null)
                && activeChat.getChatSession() instanceof MetaContactChatSession) {
            metaContact = activeChat.getMetaContact();
        }
        Contact contact = (metaContact == null) ? null : metaContact.getDefaultContact();

        // activeChat must not be null; proceed if it is conference call (contact == null).
        // Do not proceed if chat session is triggered from system server (domainBareJid) i.e. welcome message
        runOnUiThread(() -> {
            if ((activeChat != null)
                    && ((contact == null) || !(contact.getJid() instanceof DomainBareJid)))
                setCurrentContact(contact, chatSessionId);
            else {
                mOmemo.setEnabled(false);
                mOmemo.getIcon().setAlpha(80);

                mOtr.setVisible(false);
            }
        });
    }

    /**
     * Sets the current <tt>otrContact</tt> and updates status and OTR MenuItem.
     * // cmeng: Assume support only single remote resource login - current implementation
     *
     * @param contact new <tt>contact</tt> to be used.
     */
    private void setCurrentContact(Contact contact, String chatSessionId)
    {
        currentOtrContact = null;
        if (contact == null) {
            setOTRMenuItem(null);
            initOmemo(chatSessionId);
        }
        else {
            Collection<ContactResource> resources = contact.getResources();
            if (resources != null) {
                for (ContactResource resource : resources) {
                    OtrContact otrContact = OtrContactManager.getOtrContact(contact, resource);
                    if (otrContact != null) {
                        currentOtrContact = otrContact;
                        break;
                    }
                }
            }

            setOTRMenuItem(contact);
            initOmemo(chatSessionId);
            if (!isOmemoMode && (currentOtrContact != null)) {
                setStatusOtr(scOtrEngine.getSessionStatus(currentOtrContact));
            }
        }
        // Timber.w("set otr session visibility: %s %s", currentOtrContact,isOmemoMode);
        mOtr_Session.setVisible((currentOtrContact != null) && !isOmemoMode);
    }

    /**
     * This method needs to be called to update crypto status after:
     * - the crypto menu is first created (after launch of chatFragment).
     * - when user slides chatSession pages i.e. onCurrentChatChanged().
     *
     * @param chatSessionId Current ChatSession Id to update crypto Status and button check
     */
    private void initOmemo(String chatSessionId)
    {
        // Get from the persistent storage chatType for new instance
        int chatType;
        if (!encryptionChoice.containsKey(chatSessionId)) {
            chatType = mMHS.getSessionChatType(activeChat.getChatSession());
            // OTR state in DB may not be valid in the current chat session, so force it to normal chat mode
            if ((chatType == MSGTYPE_OTR) || (chatType == MSGTYPE_OTR_UA)) {
                chatType = MSGTYPE_NORMAL;
            }
            encryptionChoice.put(chatSessionId, chatType);
        }
        chatType = encryptionChoice.get(chatSessionId);
        activeChat.setChatType(chatType);

        // mItem may be null if it was accessed prior to Crypto menu init is completed.
        MenuItem mItem = checkCryptoButton(chatType);
        mItem.setChecked(true);
        updateOmemoSupport();

        // need to handle crypto state icon if it is not OTR - need to handle for all in Note 8???
        if (mItem != mOtr) {
            setStatusOmemo(chatType);
        }
        isOmemoMode = (mItem == mOmemo);

//		Timber.w("ChatSession ID: %s\nEncryption choice: %s\nmItem: %s\nChatType: %s", chatSessionId,
//				encryptionChoice, mItem, activeChat.getChatType());
    }

    /**
     * Reset the encryption choice for the specified chatSessionId.
     * Mainly use by ChatSessionFragement; to re-enable the chat Session for UI when it is selected again
     *
     * @param chatSessionId chat session Uuid
     */
    public static void resetEncryptionChoice(String chatSessionId){
        if (TextUtils.isEmpty(chatSessionId)) {
            encryptionChoice.clear();
        } else {
            encryptionChoice.remove(chatSessionId);
        }
    }

    /**
     * OTR engine listener.
     */
    private final ScOtrEngineListener scOtrEngineListener = new ScOtrEngineListener()
    {
        public void sessionStatusChanged(OtrContact otrContact)
        {
            // currentOtrContact can be null - equals order is important.
            if (otrContact.equals(currentOtrContact)) {
                setStatusOtr(scOtrEngine.getSessionStatus(otrContact));
            }
        }

        public void contactPolicyChanged(Contact contact)
        {
            // this.otContact can be null - equals order is important.
            if (contact.equals(currentOtrContact.contact)) {
                setOTRMenuItem(contact);
            }
        }

        public void globalPolicyChanged()
        {
            if (currentOtrContact != null)
                setOTRMenuItem(currentOtrContact.contact);
        }

        @Override
        public void multipleInstancesDetected(OtrContact otrContact)
        {
            runOnUiThread(() -> mOtr_Session.setVisible(true));
        }

        @Override
        public void outgoingSessionChanged(OtrContact otrContact)
        {
            // this.otrContact can be null - equals order is important.
            if (otrContact.equals(currentOtrContact)) {
                setStatusOtr(scOtrEngine.getSessionStatus(otrContact));
            }
        }
    };

    /**
     * OTR key manager listener.
     */
    private final ScOtrKeyManagerListener scOtrKeyManagerListener = otrContact -> {
        // this.otrContact can be null - equals order is important..
        if (otrContact.equals(currentOtrContact)) {
            setStatusOtr(scOtrEngine.getSessionStatus(otrContact));
        }
    };

    /**
     * Sets the button enabled status according to the passed in {@link OtrPolicy}.
     * Hides the padlock when OTR is not supported; Grey when supported but option disabled
     *
     * @param contact OTR .
     */
    private void setOTRMenuItem(final Contact contact)
    {
        runOnUiThread(() -> {
            if (mOtr == null)
                return;

            if ((contact != null) && contact.getPresenceStatus().isOnline()) {
                mOtr.setVisible(true);

                // aTalk does not implement contact OTR UI for user selection.
                OtrPolicy globalPolicy = scOtrEngine.getGlobalPolicy();
                if (globalPolicy.getEnableManual()) {
                    mOtr.setEnabled(true);
                    mOtr.getIcon().setAlpha(255);
                }
                else {
                    mOtr.getIcon().setAlpha(80);
                    mOtr.setEnabled(false);
                }
            }
            else {
                mOtr.setVisible(false);
            }
        });
    }

    /**
     * Sets the padlock icon according to the passed in {@link ScSessionStatus}.
     *
     * @param status the {@link ScSessionStatus}.
     * @see #isOmemoMode denition
     */
    private void setStatusOtr(ScSessionStatus status)
    {
        // Only allow otr changing status triggered from events when not in omemo session
        if (!isOmemoMode) {
            final int iconId;
            final int tipKey;
            int chatType = MSGTYPE_NORMAL;

            switch (status) {
                case ENCRYPTED:
                    PublicKey pubKey = scOtrEngine.getRemotePublicKey(currentOtrContact);
                    String fingerprint = scOtrKeyManager.getFingerprintFromPublicKey(pubKey);
                    boolean isVerified = scOtrKeyManager.isVerified(currentOtrContact.contact, fingerprint);
                    chatType = isVerified ? MSGTYPE_OTR : MSGTYPE_OTR_UA;

                    iconId = isVerified ? R.drawable.crypto_otr_verified : R.drawable.crypto_otr_unverified;
                    tipKey = isVerified ? R.string.plugin_otr_menu_OTR_AUTHETICATED
                            : R.string.plugin_otr_menu_OTR_NON_AUTHETICATED;
                    break;
                case FINISHED:
                    iconId = R.drawable.crypto_otr_finished;
                    tipKey = R.string.plugin_otr_menu_OTR_Finish;
                    break;
                case PLAINTEXT:
                    iconId = R.drawable.crypto_otr_unsecure;
                    tipKey = R.string.plugin_otr_menu_OTR_PLAINTTEXT;
                    chatType = MSGTYPE_NORMAL;
                    break;
                case LOADING:
                    iconId = R.drawable.crypto_otr_loading;
                    tipKey = R.string.plugin_otr_menu_OTR_HANDSHAKE;
                    break;
                case TIMED_OUT:
                    iconId = R.drawable.crypto_otr_pd_broken;
                    tipKey = R.string.plugin_otr_menu_OTR_TIMEOUT;
                    break;
                default:
                    return;
            }

            runOnUiThread(() -> {
                mCryptoChoice.setIcon(iconId);
                mCryptoChoice.setTitle(tipKey);
                mOtr_Session.setVisible(mChatType != MSGTYPE_NORMAL);
            });

            // setStatusOmemo() will always get executed. So skip if same chatType
            if ((chatType != mChatType) || (chatType != activeChat.getChatType())) {
                // Timber.d("OTR listener change mChatType to: %s (%s)", mChatType, chatType);
                mChatType = chatType;

                // cmeng (20200717): proceed to save but will forced to normal on retrieval
                // Do not store OTR as it is not valid on new session startup
                // if (mChatType == MSGTYPE_NORMAL)
                mMHS.setSessionChatType(activeChat.getChatSession(), mChatType);

                notifyCryptoModeChanged(mChatType);
            }
        }
    }

    /**
     * Sets the padlock icon according to the passed in OMEMO mChatType.
     *
     * @param chatType OMEMO ChatType.
     */
    private void setStatusOmemo(int chatType)
    {
        final int iconId;
        final int tipKey;
        mChatType = chatType;

        switch (chatType) {
            case ChatFragment.MSGTYPE_OMEMO:
                iconId = R.drawable.crypto_omemo_verified;
                tipKey = R.string.omemo_menu_authenticated;
                break;
            case ChatFragment.MSGTYPE_OMEMO_UA:
                iconId = R.drawable.crypto_omemo_unverified;
                tipKey = R.string.omemo_menu_unauthenticated;
                break;
            case ChatFragment.MSGTYPE_OMEMO_UT:
                iconId = R.drawable.crypto_omemo_untrusted;
                tipKey = R.string.omemo_menu_untrusted;
                break;
            case MSGTYPE_NORMAL:
            case MSGTYPE_MUC_NORMAL:
                iconId = R.drawable.crypto_otr_unsecure;
                tipKey = R.string.menu_crypto_plain_text;
                break;
            default:
                // return if it is in OTR mode (none of above)
                return;
        }
        runOnUiThread(() -> {
            mCryptoChoice.setIcon(iconId);
            mCryptoChoice.setTitle(tipKey);
        });
        // Timber.w("Omemo CryptMode change to: %s for %s", chatType, mDescriptor);
        notifyCryptoModeChanged(mChatType);
    }

    /**
     * Check and cache result of OMEMO is supported by current chatTransport;
     */
    public void updateOmemoSupport()
    {
        // Following few parameters must get initialized while in updateOmemoSupport()
        // Do not proceed if account is not log in, otherwise system crash
        ChatTransport mChatTransport = activeChat.getChatSession().getCurrentChatTransport();
        if (mChatTransport == null)
            return;

        mDescriptor = mChatTransport.getDescriptor();
        mConnection = mChatTransport.getProtocolProvider().getConnection();
        if ((mConnection == null) || !mConnection.isAuthenticated()) {
            omemoCapable.put(mDescriptor, false);
            return;
        }
        mOmemoManager = OmemoManager.getInstanceFor(mConnection);

        // Execute in a new thread to avoid ANR with black screen when chat window is opened.
        new Thread()
        {
            @Override
            public void run()
            {
                boolean serverCan = false;
                boolean entityCan = false;

                try {
                    DomainBareJid serverJid = mConnection.getXMPPServiceDomain();
                    serverCan = AndroidOmemoService.isOmemoInitSuccessful
                            || OmemoManager.serverSupportsOmemo(mConnection, serverJid);

                    if (mDescriptor instanceof ChatRoom) {
                        MultiUserChat muc = ((ChatRoom) mDescriptor).getMultiUserChat();
                        entityCan = mOmemoManager.multiUserChatSupportsOmemo(muc);
                    }
                    else {
                        // buddy online check may sometimes experience reply timeout; OMEMO obsoleted feature
                        // not a good idea to include PEP_NODE_DEVICE_LIST_NOTIFY as some siblings may
                        // support omemo encryption.
                        // boolean support = ServiceDiscoveryManager.getInstanceFor(connection)
                        //      .discoverInfo(contactJId).containsFeature(PEP_NODE_DEVICE_LIST_NOTIFY);

                        // Check based on present of keys on server - may have problem if buddy has old axolotf data
                        Jid contactJId = ((Contact) mDescriptor).getJid();
                        entityCan = mOmemoManager.contactSupportsOmemo(contactJId.asBareJid());

                        // cmeng - what about check from backend database entities table instead
                        // String usrID = ((Contact) mDescriptor).getAddress();
                        // entityCan = ((SQLiteOmemoStore) mOmemoStore).getContactNumTrustedKeys(usrID) > 0;
                    }
                } catch (XMPPException.XMPPErrorException | SmackException.NoResponseException
                        | InterruptedException | SmackException.NotConnectedException | IOException e) {
                    Timber.w("Exception in omemo support checking: %s", e.getMessage());
                } catch (PubSubException.NotALeafNodeException e) {
                    Timber.w("Exception in checking entity omemo support: %s", e.getMessage());
                }

                // update omemoSupported in cache; revert to MSGTYPE_NORMAL if Default OMEMO not supported by session
                boolean omemoSupported = serverCan && entityCan;
                omemoCapable.put(mDescriptor, omemoSupported);

                if (!omemoSupported && (MSGTYPE_OMEMO == mChatType))
                    setChatType(MSGTYPE_NORMAL);
            }
        }.start();
    }

    /**
     * Indicates a contact has changed its status.
     */
    public void onContactPresenceStatusChanged()
    {
        if (activeChat != null) {
            MetaContact metaContact = activeChat.getMetaContact();
            Contact contact = metaContact.getDefaultContact();
            // proceed if this is not a conference call (contact != null), and is not a domainBareJid
            if ((contact != null) && !(contact.getJid() instanceof DomainBareJid)) {
                setOTRMenuItem(contact);
            }
            else
                setOTRMenuItem(null);
        }
    }

    /**
     * Listens for show history popup link
     */
    public static class ShowHistoryLinkListener implements ChatLinkClickedListener
    {
        /**
         * {@inheritDoc}
         */
        @Override
        public void chatLinkClicked(URI uri)
        {
            if (uri.getPath().equals("/showHistoryPopupMenu")) {
                // Display settings
                Context ctx = aTalkApp.getGlobalContext();
                Intent settings = new Intent(ctx, SettingsActivity.class);
                settings.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                ctx.startActivity(settings);
            }
        }
    }

    /**
     * Callback when user clicks the omemo Authentication dialog's confirm/cancel button.
     *
     * @param allTrusted allTrusted state.
     * @param omemoDevices set of unTrusted devices
     */
    @Override
    public void onAuthenticate(boolean allTrusted, Set<OmemoDevice> omemoDevices)
    {
        if (allTrusted) {
            onOmemoAuthenticate(ChatFragment.MSGTYPE_OMEMO);
            activeChat.addMessage(mEntity, new Date(), ChatMessage.MESSAGE_SYSTEM, IMessage.ENCODE_PLAIN,
                    getString(R.string.crypto_msg_OMEMO_SESSION_VERIFIED));
        }
        else {
            onOmemoAuthenticate(ChatFragment.MSGTYPE_OMEMO_UA);
            // activeChat.addMessage(mEntity, new Date(), ChatMessage.MESSAGE_ERROR, IMessage.ENCODE_PLAIN,
            //         "Undecided Omemo Identity: " + omemoDevices.toString());
        }
    }

    /**
     * @param chatType New chatType pending user verification action from omemo Authentication dialog
     * @see OmemoAuthenticateDialog
     */
    private void onOmemoAuthenticate(int chatType)
    {
        // must update encryptionChoice and set ChatPanel to the new chatType
        encryptionChoice.put(mCurrentChatSessionId, chatType);
        activeChat.setChatType(chatType);

        // ChatActivity onResume (on OmemoAuthenticateDialog closed) will trigger this. but do it just in case
        setStatusOmemo(chatType);
        updateOmemoSupport();
    }

    /**
     * Chat Type change notification to all registered cryptoModeChangeListeners mainly to change
     * chatFragment background color for the new chatType; which is triggered from user cryptoMode selection.
     * When a listener for the mDescriptor key is not found, then the map is updated linking
     * current mDescriptor with the recent added listener with null key. null key is added when a
     * chatFragment is opened or became the primary selected.
     *
     * @param chatType The new chatType to broadcast to registered listener
     */
    private void notifyCryptoModeChanged(int chatType)
    {
        // Timber.w(new Exception(), "notifyCryptoModeChanged %s", chatType);
        CryptoModeChangeListener listener;
        if (!cryptoModeChangeListeners.containsKey(mDescriptor)) {
            listener = cryptoModeChangeListeners.get(null);
            addCryptoModeListener(mDescriptor, listener);
        }
        else {
            listener = cryptoModeChangeListeners.get(mDescriptor);
        }

        if (listener != null) {
            // Timber.w("CryptMode Listener changed: %s => %s", listener, chatType);
            listener.onCryptoModeChange(chatType);
        }
    }

    /**
     * Note: mDescriptor is always null when first triggers by chatFragment. It gets updated in notifyCryptoModeChanged()
     *
     * @param listener CryptoModeChangeListener added by chatFragment.
     * @see #notifyCryptoModeChanged(int)
     */
    public void addCryptoModeListener(Object descriptor, CryptoModeChangeListener listener)
    {
        // Timber.w("CryptMode Listener added: %s <= %s", listener, mDescriptor);
        cryptoModeChangeListeners.put(descriptor, listener);
    }

    /**
     * @param chatType chatType see case
     */
    public void setChatType(int chatType)
    {
        // Return if the crypto menu option items are not initialized yet.
        if (mCryptoChoice == null) {
            return;
        }
        runOnUiThread(() -> {
            switch (chatType) {
                case MSGTYPE_NORMAL:
                case MSGTYPE_MUC_NORMAL:
                    onOptionsItemSelected(mNone);
                    break;
                case MSGTYPE_OMEMO:
                    onOptionsItemSelected(mOmemo);
                    break;
                case MSGTYPE_OTR:
                    onOptionsItemSelected(mOtr);
            }
        });
    }
}
