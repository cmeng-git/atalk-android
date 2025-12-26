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

import static org.atalk.android.gui.chat.ChatFragment.MSGTYPE_MUC_NORMAL;
import static org.atalk.android.gui.chat.ChatFragment.MSGTYPE_NORMAL;
import static org.atalk.android.gui.chat.ChatFragment.MSGTYPE_OMEMO;

import android.content.Context;
import android.content.Intent;
import android.text.TextUtils;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;

import androidx.annotation.NonNull;
import androidx.core.view.MenuProvider;

import java.io.IOException;
import java.net.URI;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import net.java.sip.communicator.impl.msghistory.MessageHistoryActivator;
import net.java.sip.communicator.service.contactlist.MetaContact;
import net.java.sip.communicator.service.gui.ChatLinkClickedListener;
import net.java.sip.communicator.service.msghistory.MessageHistoryService;
import net.java.sip.communicator.service.protocol.ChatRoom;
import net.java.sip.communicator.service.protocol.Contact;
import net.java.sip.communicator.service.protocol.IMessage;
import net.java.sip.communicator.service.protocol.ProtocolProviderService;
import net.java.sip.communicator.service.protocol.event.ChatRoomMemberPresenceChangeEvent;
import net.java.sip.communicator.service.protocol.event.ChatRoomMemberPresenceListener;

import org.atalk.android.BaseFragment;
import org.atalk.android.R;
import org.atalk.android.aTalkApp;
import org.atalk.android.gui.AppGUIActivator;
import org.atalk.android.gui.chat.ChatFragment;
import org.atalk.android.gui.chat.ChatMessage;
import org.atalk.android.gui.chat.ChatPanel;
import org.atalk.android.gui.chat.ChatSessionManager;
import org.atalk.android.gui.chat.ChatTransport;
import org.atalk.android.gui.chat.MetaContactChatSession;
import org.atalk.android.gui.settings.SettingsActivity;
import org.atalk.crypto.listener.CryptoModeChangeListener;
import org.atalk.crypto.omemo.AndroidOmemoService;
import org.atalk.crypto.omemo.OmemoAuthenticateDialog;
import org.jivesoftware.smack.SmackException;
import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.XMPPException;
import org.jivesoftware.smackx.muc.MultiUserChat;
import org.jivesoftware.smackx.muc.MultiUserChatManager;
import org.jivesoftware.smackx.omemo.OmemoManager;
import org.jivesoftware.smackx.omemo.OmemoService;
import org.jivesoftware.smackx.omemo.OmemoStore;
import org.jivesoftware.smackx.omemo.exceptions.CannotEstablishOmemoSessionException;
import org.jivesoftware.smackx.omemo.exceptions.CorruptedOmemoKeyException;
import org.jivesoftware.smackx.omemo.exceptions.CryptoFailedException;
import org.jivesoftware.smackx.omemo.exceptions.NoOmemoSupportException;
import org.jivesoftware.smackx.omemo.exceptions.UndecidedOmemoIdentityException;
import org.jivesoftware.smackx.omemo.internal.OmemoCachedDeviceList;
import org.jivesoftware.smackx.omemo.internal.OmemoDevice;
import org.jivesoftware.smackx.omemo.trust.OmemoFingerprint;
import org.jivesoftware.smackx.pubsub.PubSubException;
import org.jxmpp.jid.BareJid;
import org.jxmpp.jid.DomainBareJid;
import org.jxmpp.jid.EntityBareJid;
import org.jxmpp.jid.EntityFullJid;
import org.jxmpp.jid.Jid;

import timber.log.Timber;

/**
 * Fragment when added to <code>Activity</code> will display the padlock allowing user to select
 * various type of encryption options. Only currently active chat is handled by this fragment.
 *
 * @author Eng Chong Meng
 */
public class CryptoFragment extends BaseFragment
        implements MenuProvider, ChatSessionManager.CurrentChatListener, ChatRoomMemberPresenceListener,
        OmemoAuthenticateDialog.AuthenticateListener {
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

    private final MessageHistoryService mMHS;

    /**
     * Creates a new instance of <code>CryptoFragment</code>.
     */
    public CryptoFragment() {
        mMHS = MessageHistoryActivator.getMessageHistoryService();
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        requireActivity().addMenuProvider(this);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onStop() {
        ChatSessionManager.removeCurrentChatListener(this);
        super.onStop();
    }

    @Override
    public void onCreateMenu(@NonNull Menu menu, @NonNull MenuInflater menuInflater) {
        // This happens when Activity is recreated by the system after OSGi service has been killed (and the whole process)
        if (AppGUIActivator.bundleContext == null) {
            Timber.e("OSGi service probably not initialized");
            return;
        }
        mOmemoStore = OmemoService.getInstance().getOmemoStoreBackend();

        /*
         * Menu instances used to select and control the crypto choices.
         * Add chat encryption choices if not found
         */
        if (menu.findItem(R.id.encryption_none) == null)
            menuInflater.inflate(R.menu.crypto_choices, menu);

        mCryptoChoice = menu.findItem(R.id.crypto_choice);
        mNone = menu.findItem(R.id.encryption_none);
        mOmemo = menu.findItem(R.id.encryption_omemo);

        // Initialize the padlock icon only after the Crypto menu is created
        doInit();
    }

    @Override
    public boolean onMenuItemSelected(@NonNull MenuItem menuItem) {
        boolean hasChange = false;
        menuItem.setChecked(true);

        switch (menuItem.getItemId()) {
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
                if (mDescriptor instanceof Contact)
                    mChatType = ChatFragment.MSGTYPE_NORMAL;
                else
                    mChatType = ChatFragment.MSGTYPE_MUC_NORMAL;
                hasChange = true;
                doHandleOmemoPressed(false);
                break;

            case R.id.encryption_omemo:
                if (!activeChat.isOmemoChat())
                    mChatType = MSGTYPE_OMEMO;
                hasChange = true;
                doHandleOmemoPressed(true);
                break;

            default:
                break;
        }

        if (hasChange) {
            String chatId = ChatSessionManager.getCurrentChatId();
            encryptionChoice.put(chatId, mChatType);
            setStatusOmemo(mChatType);
            // Timber.w("update persistent ChatType to: %s", mChatType);

            mMHS.setSessionChatType(activeChat.getChatSession(), mChatType);
            return true;
        }
        return super.onOptionsItemSelected(menuItem);
    }

    /**
     * Synchronise the cryptoChoice button checkMark with the current chatType
     *
     * @param chatType Sync cryptoChoice button check to the given chatType
     *
     * @return the button menuItem corresponding to the given chatType
     */
    private MenuItem checkCryptoButton(int chatType) {
        MenuItem mItem;
        switch (chatType) {
            case ChatFragment.MSGTYPE_OMEMO:
            case ChatFragment.MSGTYPE_OMEMO_UA:
            case ChatFragment.MSGTYPE_OMEMO_UT:
                mItem = mOmemo;
                break;

            case ChatFragment.MSGTYPE_NORMAL:
            case ChatFragment.MSGTYPE_MUC_NORMAL:
            default:
                mItem = mNone;
        }
        return mItem;
    }

    /**
     * Handle OMEMO state when the option is selected/unSelected.
     */
    private void doHandleOmemoPressed(final boolean enable) {
        // return: nothing to do if not enable
        ProtocolProviderService pps = activeChat.getProtocolProvider();

        if (!enable || mOmemoManager == null || (mDescriptor == null) || !pps.isRegistered())
            return;

        // Linked map between OmemoDevice and its fingerprint.
        Map<OmemoDevice, OmemoFingerprint> fingerPrints = new HashMap<>();
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
                startActivity(OmemoAuthenticateDialog.createIntent(mContext, mOmemoManager, omemoDevices, this));
                allTrusted = false;
            } catch (InterruptedException | SmackException.NoResponseException | CryptoFailedException
                     | SmackException.NotConnectedException | SmackException.NotLoggedInException e) {
                mChatType = ChatFragment.MSGTYPE_MUC_NORMAL;
                activeChat.addMessage(mEntity, new Date(), ChatMessage.MESSAGE_ERROR, IMessage.ENCODE_PLAIN,
                        getString(R.string.crypto_msg_omemo_session_setup_failed, e.getMessage()));
                Timber.i("OMEMO changes mChatType to: %s", mChatType);
                return;
            } catch (Exception e) { // catch any non-advertised exception
                Timber.w("UndecidedOmemoIdentity check failed: %s", e.getMessage());
                mChatType = ChatFragment.MSGTYPE_MUC_NORMAL;
                activeChat.addMessage(mEntity, new Date(), ChatMessage.MESSAGE_ERROR, IMessage.ENCODE_PLAIN,
                        getString(R.string.crypto_msg_omemo_session_setup_failed, e.getMessage()));
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
                        getString(R.string.crypto_msg_omemo_session_untrusted));
            }
            else if (allTrusted) {
                mChatType = ChatFragment.MSGTYPE_OMEMO;
            }
            else {
                mChatType = ChatFragment.MSGTYPE_OMEMO_UA;
                activeChat.addMessage(mEntity, new Date(), ChatMessage.MESSAGE_SYSTEM, IMessage.ENCODE_PLAIN,
                        getString(R.string.crypto_msg_omemo_session_unverified));
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
                startActivity(OmemoAuthenticateDialog.createIntent(mContext, mOmemoManager, omemoDevices, this));
                allTrusted = false;
            } catch (NoOmemoSupportException | InterruptedException | SmackException.NoResponseException
                     | XMPPException.XMPPErrorException | CryptoFailedException
                     | SmackException.NotConnectedException | SmackException.NotLoggedInException e) {
                mChatType = ChatFragment.MSGTYPE_MUC_NORMAL;
                activeChat.addMessage(mEntity, new Date(), ChatMessage.MESSAGE_ERROR, IMessage.ENCODE_PLAIN,
                        getString(R.string.crypto_msg_omemo_session_setup_failed, e.getMessage()));
                return;
            } catch (Exception e) { // catch any non-advertised exception
                Timber.w("UndecidedOmemoIdentity check failed: %s", e.getMessage());
                mChatType = ChatFragment.MSGTYPE_MUC_NORMAL;
                activeChat.addMessage(mEntity, new Date(), ChatMessage.MESSAGE_ERROR, IMessage.ENCODE_PLAIN,
                        getString(R.string.crypto_msg_omemo_session_setup_failed, e.getMessage()));
            }

            allTrusted = allTrusted && isAllTrusted(mMultiUserChat);
            if (allTrusted) {
                mChatType = ChatFragment.MSGTYPE_OMEMO;
            }
            else {
                mChatType = ChatFragment.MSGTYPE_OMEMO_UA;
                activeChat.addMessage(mEntity, new Date(), ChatMessage.MESSAGE_SYSTEM, IMessage.ENCODE_PLAIN,
                        getString(R.string.crypto_msg_omemo_session_unverified_untrusted));
            }
        }
        // Timber.d("OMEMO changes mChatType to: %s", mChatType);
    }

    /**
     * Check to see if all muc recipients are verified or trusted
     *
     * @param multiUserChat MultiUserChat
     *
     * @return return <code>true</code> if all muc recipients are verified or trusted. Otherwise <code>false</code>
     */
    private boolean isAllTrusted(MultiUserChat multiUserChat) {
        boolean allTrusted = true;
        OmemoFingerprint fingerPrint;
        BareJid recipient;

        for (EntityFullJid e : multiUserChat.getOccupants()) {
            recipient = multiUserChat.getOccupant(e).getJid().asBareJid();
            try {
                OmemoCachedDeviceList theirDevices = mOmemoStore.loadCachedDeviceList(mOmemoManager.getOwnDevice(), recipient);
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
            } catch (IOException ex) {
                Timber.w("IOException: %s", ex.getMessage());
            }
        }
        return allTrusted;
    }

    /**
     * Trigger when invited participant join the conference. This will fill the partial identities table for the
     * participant and request fingerPrint verification is undecided. Hence ensuring that the next sent message
     * is properly received by the new member.
     *
     * @param evt the <code>ChatRoomMemberPresenceChangeEvent</code> instance containing the source chat
     */
    @Override
    public void memberPresenceChanged(ChatRoomMemberPresenceChangeEvent evt) {
        if (mOmemoManager != null && (activeChat.isOmemoChat())
                && ChatRoomMemberPresenceChangeEvent.MEMBER_JOINED.equals(evt.getEventType())) {
            try {
                mOmemoManager.encrypt(mMultiUserChat, "Hi everybody!");
            } catch (UndecidedOmemoIdentityException e) {
                Set<OmemoDevice> omemoDevices = e.getUndecidedDevices();
                Timber.w("There are undecided Omemo devices: %s", omemoDevices);
                startActivity(OmemoAuthenticateDialog.createIntent(mContext, mOmemoManager, omemoDevices, this));
            } catch (NoOmemoSupportException | InterruptedException | SmackException.NoResponseException
                     | XMPPException.XMPPErrorException | CryptoFailedException | IOException
                     | SmackException.NotConnectedException | SmackException.NotLoggedInException e) {
                Timber.w("UndecidedOmemoIdentity check failed: %s", e.getMessage());
            }
        }
    }

    /**
     * Register listeners, initializes the padlock icons and encryption options menu enable state.
     * Must only be performed after the completion of onCreateOptionsMenu().
     *
     * @see #onCreateOptionsMenu(Menu, MenuInflater)
     */
    private void doInit() {
        ChatSessionManager.addCurrentChatListener(this);

        // Setup currentChatSession options for the Jid if login in
        String currentChatId = ChatSessionManager.getCurrentChatId();
        if (currentChatId != null)
            setCurrentChatSession(currentChatId);
    }

    /**
     * Event trigger when user slide the chatFragment
     *
     * @param chatId id of current chat session or <code>null</code> if there is no chat currently
     */
    @Override
    public void onCurrentChatChanged(String chatId) {
        setCurrentChatSession(chatId);
    }

    /**
     * Triggered from onCreateOption() or onCurrentChatChanged()
     * <p>
     * Sets current <code>ChatPanel</code> identified by given <code>chatSessionKey</code>.
     * Init Crypto choice to last selected or encryption_none
     *
     * @param chatSessionId chat session key managed by <code>ChatSessionManager</code>
     */
    private void setCurrentChatSession(String chatSessionId) {
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
                initOmemo(chatSessionId);
            else {
                mOmemo.setEnabled(false);
                mOmemo.getIcon().setAlpha(80);
            }
        });
    }

    /**
     * This method needs to be called to update crypto status after:
     * - the crypto menu is first created (after launch of chatFragment).
     * - when user slides chatSession pages i.e. onCurrentChatChanged().
     *
     * @param chatSessionId Current ChatSession Id to update crypto Status and button check
     */
    private void initOmemo(String chatSessionId) {
        // Get from the persistent storage chatType for new instance
        int chatType;
        if (!encryptionChoice.containsKey(chatSessionId)) {
            chatType = mMHS.getSessionChatType(activeChat.getChatSession());
            encryptionChoice.put(chatSessionId, chatType);
        }
        chatType = encryptionChoice.get(chatSessionId);
        activeChat.setChatType(chatType);

        // mItem may be null if it was accessed prior to Crypto menu init is completed.
        MenuItem mItem = checkCryptoButton(chatType);
        mItem.setChecked(true);
        updateOmemoSupport();
        setStatusOmemo(chatType);

//		Timber.w("ChatSession ID: %s\nEncryption choice: %s\nmItem: %s\nChatType: %s", chatSessionId,
//				encryptionChoice, mItem, activeChat.getChatType());
    }

    /**
     * Reset the encryption choice for the specified chatSessionId.
     * Mainly use by ChatSessionFragement; to re-enable the chat Session for UI when it is selected again
     *
     * @param chatSessionId chat session Uuid
     */
    public static void resetEncryptionChoice(String chatSessionId) {
        if (TextUtils.isEmpty(chatSessionId)) {
            encryptionChoice.clear();
        }
        else {
            encryptionChoice.remove(chatSessionId);
        }
    }

    /**
     * Sets the padlock icon according to the passed in OMEMO mChatType.
     *
     * @param chatType OMEMO ChatType.
     */
    private void setStatusOmemo(int chatType) {
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
                iconId = R.drawable.crypto_unsecure;
                tipKey = R.string.menu_crypto_plain_text;
                break;

            // return if it is in none of above
            default:
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
    public void updateOmemoSupport() {
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

        // Seems like from FFR; OmemoManager can still be null after user is authenticated
        mOmemoManager = OmemoManager.getInstanceFor(mConnection);
        if (mOmemoManager == null) {
            omemoCapable.put(mDescriptor, false);
            return;
        }
        // Skip if previous check has omemo supported. The contactSupportsOmemo can cause ANR in OmemoManager.encrypt
        else if (Boolean.TRUE.equals(omemoCapable.get(mDescriptor))) {
            return;
        }

        // Execute in a new thread to avoid ANR with black screen when chat window is opened.
        new Thread() {
            @Override
            public void run() {
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
     * Listens for show history popup link
     */
    public static class ShowHistoryLinkListener implements ChatLinkClickedListener {
        /**
         * {@inheritDoc}
         */
        @Override
        public void chatLinkClicked(URI uri) {
            if (uri.getPath().equals("/showHistoryPopupMenu")) {
                // Display settings
                Context ctx = aTalkApp.getInstance();
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
    public void onAuthenticate(boolean allTrusted, Set<OmemoDevice> omemoDevices) {
        if (allTrusted) {
            onOmemoAuthenticate(ChatFragment.MSGTYPE_OMEMO);
            activeChat.addMessage(mEntity, new Date(), ChatMessage.MESSAGE_SYSTEM, IMessage.ENCODE_PLAIN,
                    getString(R.string.crypto_msg_omemo_session_verified));
        }
        else {
            onOmemoAuthenticate(ChatFragment.MSGTYPE_OMEMO_UA);
            // activeChat.addMessage(mEntity, new Date(), ChatMessage.MESSAGE_ERROR, IMessage.ENCODE_PLAIN,
            //         "Undecided Omemo Identity: " + omemoDevices.toString());
        }
    }

    /**
     * @param chatType New chatType pending user verification action from omemo Authentication dialog
     *
     * @see OmemoAuthenticateDialog
     */
    private void onOmemoAuthenticate(int chatType) {
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
    private void notifyCryptoModeChanged(int chatType) {
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
     *
     * @see #notifyCryptoModeChanged(int)
     */
    public void addCryptoModeListener(Object descriptor, CryptoModeChangeListener listener) {
        // Timber.w("CryptMode Listener added: %s <= %s", listener, mDescriptor);
        cryptoModeChangeListeners.put(descriptor, listener);
    }

    /**
     * @param chatType chatType see case
     */
    public void setChatType(int chatType) {
        // Return if the crypto menu option items are not initialized yet.
        if (mCryptoChoice == null) {
            return;
        }
        runOnUiThread(() -> {
            switch (chatType) {
                case MSGTYPE_NORMAL:
                case MSGTYPE_MUC_NORMAL:
                    onMenuItemSelected(mNone);
                    break;

                case MSGTYPE_OMEMO:
                    // Do not emulate Omemo button press if mOmemoManager is null
                    if (mOmemoManager != null) {
                        onMenuItemSelected(mOmemo);
                    }
                    break;
            }
        });
    }
}
