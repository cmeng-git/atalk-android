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
package org.atalk.crypto.omemo;

import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.widget.Toast;

import net.java.sip.communicator.service.protocol.AccountID;
import net.java.sip.communicator.service.protocol.ProtocolProviderService;
import net.java.sip.communicator.util.Logger;

import org.atalk.android.R;
import org.atalk.android.aTalkApp;
import org.atalk.android.gui.AndroidGUIActivator;
import org.atalk.android.gui.account.Account;
import org.atalk.persistance.DatabaseBackend;
import org.atalk.service.configuration.ConfigurationService;
import org.atalk.util.StringUtils;
import org.jivesoftware.smack.SmackException;
import org.jivesoftware.smack.XMPPException;
import org.jivesoftware.smack.tcp.XMPPTCPConnection;
import org.jivesoftware.smackx.omemo.OmemoManager;
import org.jivesoftware.smackx.omemo.exceptions.CannotEstablishOmemoSessionException;
import org.jivesoftware.smackx.omemo.exceptions.CorruptedOmemoKeyException;
import org.jivesoftware.smackx.omemo.internal.OmemoCachedDeviceList;
import org.jivesoftware.smackx.omemo.internal.OmemoDevice;
import org.jivesoftware.smackx.omemo.signal.SignalOmemoStore;
import org.jivesoftware.smackx.omemo.trust.OmemoFingerprint;
import org.jivesoftware.smackx.omemo.trust.OmemoTrustCallback;
import org.jivesoftware.smackx.omemo.trust.TrustState;
import org.jivesoftware.smackx.omemo.util.OmemoKeyUtil;
import org.jxmpp.jid.BareJid;
import org.jxmpp.jid.impl.JidCreate;
import org.jxmpp.stringprep.XmppStringprepException;
import org.whispersystems.libsignal.IdentityKey;
import org.whispersystems.libsignal.IdentityKeyPair;
import org.whispersystems.libsignal.state.PreKeyRecord;
import org.whispersystems.libsignal.state.SessionRecord;
import org.whispersystems.libsignal.state.SignedPreKeyRecord;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeMap;

/**
 * The extension of the OMEMO signal Store that uses SQLite database to support the storage of
 * OMEMO information for:
 * - Omemo devices
 * - PreKeys
 * - Signed preKeys
 * - Identities & fingerprints and its trust status
 * - Omemo sessions
 *
 * @author Eng Chong Meng
 */

public class SQLiteOmemoStore extends SignalOmemoStore
{
    /**
     * Logger used by the SQLiteOmemoStore class and its instances for logging output.
     */
    private static final Logger logger = Logger.getLogger(SQLiteOmemoStore.class);

    // omemoDevices Table
    public static final String OMEMO_DEVICES_TABLE_NAME = "omemo_devices";
    public static final String OMEMO_JID = "omemoJid"; // account user
    public static final String OMEMO_REG_ID = "omemoRegId"; // defaultDeviceId
    public static final String CURRENT_SIGNED_PREKEY_ID = "currentSignedPreKeyId";
    public static final String LAST_PREKEY_ID = "lastPreKeyId";

    // PreKeys Table
    public static final String PREKEY_TABLE_NAME = "preKeys";
    // public static final String BARE_JID = "bareJid";
    // public static final String DEVICE_ID = "deviceId";
    public static final String PRE_KEY_ID = "preKeyId";
    public static final String PRE_KEYS = "preKeys";

    // Signed PreKeys Table
    public static final String SIGNED_PREKEY_TABLE_NAME = "signed_preKeys";
    // public static final String BARE_JID = "bareJid";
    // public static final String DEVICE_ID = "deviceId";
    public static final String SIGNED_PRE_KEY_ID = "signedPreKeyId";
    public static final String SIGNED_PRE_KEYS = "signedPreKeys"; // signedPreKeyPublic?
    public static final String LAST_RENEWAL_DATE = "lastRenewalDate"; // lastSignedPreKeyRenewal

    // Identity Table
    public static final String IDENTITIES_TABLE_NAME = "identities";
    public static final String BARE_JID = "bareJid";
    public static final String DEVICE_ID = "deviceId";
    public static final String FINGERPRINT = "fingerPrint";
    public static final String CERTIFICATE = "certificate";
    public static final String TRUST = "trust";
    public static final String ACTIVE = "active";
    public static final String LAST_ACTIVATION = "last_activation"; // lastMessageReceivedDate
    public static final String LAST_DEVICE_ID_PUBLISH = "last_deviceid_publish"; // DateOfLastDeviceIdPublication
    public static final String LAST_MESSAGE_RX = "last_message_received"; // DateOfLastReceivedMessage
    public static final String IDENTITY_KEY = "identityKey"; // or identityKeyPair

    // Sessions Table
    public static final String SESSION_TABLE_NAME = "sessions";
    // public static final String BARE_JID = "bareJid";
    // public static final String DEVICE_ID = "deviceId";
    public static final String KEY = "key";

    private static final int NUM_TRUSTS_TO_CACHE = 100;
    /*
     * mDevice is used by overridden method create(String fingerprint) for trustCache self update
     * @see LruCache#create(Object)
     */
    private final DatabaseBackend mDB;
    private final OmemoKeyUtil mKeyUtil;
    private OmemoDevice mDevice;

    public SQLiteOmemoStore()
    {
        super();
        mDB = DatabaseBackend.getInstance(aTalkApp.getGlobalContext());
        mKeyUtil = this.keyUtil();
    }

    /**
     * Cache of a map fingerPrint string to FingerprintStatus
     */
    private final LruCache<String, FingerprintStatus> trustCache =
            new LruCache<String, FingerprintStatus>(NUM_TRUSTS_TO_CACHE)
            {
                @Override
                protected FingerprintStatus create(String fingerprint)
                {
                    return mDB.getFingerprintStatus(mDevice, fingerprint);
                }
            };

    // --------------------------------------
    // FingerprintStatus utilities
    // --------------------------------------

    /**
     * Get the fingerprint status for the specified device
     *
     * Need to pass device to create(String fingerprint) for trustCache
     *
     * @param device omemoDevice for which its fingerprint status is to be retrieved
     * @param fingerprint fingerprint to check
     * @return the fingerprint status for the specified device
     * @see LruCache#create(Object)
     */
    public FingerprintStatus getFingerprintStatus(OmemoDevice device, String fingerprint)
    {
        /* Must setup mDevice for FingerprintStatus#create(String fingerprint) */
        mDevice = device;
        return (fingerprint == null) ? null : trustCache.get(fingerprint);
    }

    private void setFingerprintStatus(OmemoDevice device, String fingerprint, FingerprintStatus status)
    {
        mDB.setIdentityKeyTrust(device, fingerprint, status);
        trustCache.remove(fingerprint); // clear old status in trustCache
    }

    //======================= OMEMO Store =========================================

    // --------------------------------------
    // OMEMO Devices Store
    // --------------------------------------

    /**
     * Returns a sorted set of all the deviceIds, the localUser has had data stored under in the store.
     * Basically this returns the deviceIds of all "accounts" of localUser, which are known to the store.
     *
     * @param localUser BareJid of the user.
     * @return set of deviceIds with available data.
     */
    @Override
    public SortedSet<Integer> localDeviceIdsOf(BareJid localUser)
    {
        return mDB.loadDevideIdsOf(localUser);
    }

    /**
     * Set the default deviceId of a user if it does not exist.
     *
     * @param user user
     * @param defaultDeviceId defaultDeviceId
     */
    public void setDefaultDeviceId(BareJid user, int defaultDeviceId)
    {
        mDB.storeOmemoRegId(user, defaultDeviceId);
    }

    // --------------------------------------
    // PreKey Store
    // --------------------------------------

    /**
     * Return all our current OmemoPreKeys.
     *
     * @param userDevice our OmemoDevice.
     * @return Map containing our preKeys
     */
    @Override
    public TreeMap<Integer, PreKeyRecord> loadOmemoPreKeys(OmemoDevice userDevice)
    {
        return mDB.loadPreKeys(userDevice);
    }

    /**
     * Load the preKey with id 'preKeyId' from storage.
     *
     * @param userDevice our OmemoDevice.
     * @param preKeyId id of the key to be loaded
     * @return loaded preKey
     */
    @Override
    public PreKeyRecord loadOmemoPreKey(OmemoDevice userDevice, int preKeyId)
    {
        PreKeyRecord record = mDB.loadPreKey(userDevice, preKeyId);
        if (record == null) {
            logger.warn("There is no PreKeyRecord for: " + preKeyId);
        }
        return record;
    }

    /**
     * Store a PreKey in storage.
     *
     * @param userDevice our OmemoDevice.
     * @param preKeyId id of the key
     * @param preKeyRecord ths PreKeyRecord
     */
    @Override
    public void storeOmemoPreKey(OmemoDevice userDevice, int preKeyId, PreKeyRecord preKeyRecord)
    {
        mDB.storePreKey(userDevice, preKeyRecord);
    }

    /**
     * remove a preKey from storage. This is called, when a contact used one of our preKeys to establish a session
     * with us.
     *
     * @param userDevice our OmemoDevice.
     * @param preKeyId id of the used key that will be deleted
     */
    @Override
    public void removeOmemoPreKey(OmemoDevice userDevice, int preKeyId)
    {
        mDB.deletePreKey(userDevice, preKeyId);
    }

    // --------------------------------------
    // SignedPreKeyStore
    // --------------------------------------

    /**
     * Return the signedPreKey with the id 'singedPreKeyId'.
     *
     * @param userDevice our OmemoDevice.
     * @param signedPreKeyId id of the key
     * @return key
     */
    @Override
    public SignedPreKeyRecord loadOmemoSignedPreKey(OmemoDevice userDevice, int signedPreKeyId)
    {
        SignedPreKeyRecord record = mDB.loadSignedPreKey(userDevice, signedPreKeyId);
        if (record == null) {
            logger.warn("There is no SignedPreKeyRecord for: " + signedPreKeyId);
        }
        return record;
    }

    /**
     * Load all our signed PreKeys.
     *
     * @param userDevice our OmemoDevice.
     * @return HashMap of our singedPreKeys
     */
    @Override
    public TreeMap<Integer, SignedPreKeyRecord> loadOmemoSignedPreKeys(OmemoDevice userDevice)
    {
        return mDB.loadSignedPreKeys(userDevice);
    }

    /**
     * Store a signedPreKey in storage.
     *
     * @param userDevice our OmemoDevice.
     * @param signedPreKeyId id of the signedPreKey
     * @param signedPreKey the key itself
     */
    @Override
    public void storeOmemoSignedPreKey(OmemoDevice userDevice, int signedPreKeyId, SignedPreKeyRecord signedPreKey)
    {
        mDB.storeSignedPreKey(userDevice, signedPreKeyId, signedPreKey);
    }

    /**
     * Remove a signedPreKey from storage.
     *
     * @param userDevice our OmemoDevice.
     * @param signedPreKeyId id of the key that will be removed
     */
    @Override
    public void removeOmemoSignedPreKey(OmemoDevice userDevice, int signedPreKeyId)
    {
        mDB.deleteSignedPreKey(userDevice, signedPreKeyId);
    }

    /**
     * Set the date in millis of the last time the signed preKey was renewed.
     *
     * @param userDevice our OmemoDevice.
     * @param date date
     */
    @Override
    public void setDateOfLastSignedPreKeyRenewal(OmemoDevice userDevice, Date date)
    {
        mDB.setLastSignedPreKeyRenewal(userDevice, date);
    }

    /**
     * Get the date in millis of the last time the signed preKey was renewed.
     *
     * @param userDevice our OmemoDevice.
     * @return date if existent, otherwise null
     */
    @Override
    public Date getDateOfLastSignedPreKeyRenewal(OmemoDevice userDevice)
    {
        return mDB.getLastSignedPreKeyRenewal(userDevice);
    }

    // --------------------------------------
    // IdentityKeyStore
    // --------------------------------------

    /**
     * Load our identityKeyPair from storage.
     *
     * @param userDevice our OmemoDevice.
     * @return identityKeyPair
     * @throws CorruptedOmemoKeyException Thrown, if the stored key is damaged (*hands up* not my fault!)
     */
    @Override
    public IdentityKeyPair loadOmemoIdentityKeyPair(OmemoDevice userDevice)
            throws CorruptedOmemoKeyException
    {
        String msg = null;
        boolean isCorrupted = false;
        IdentityKeyPair identityKeyPair = null;
        try {
            identityKeyPair = mDB.loadIdentityKeyPair(userDevice);
        } catch (CorruptedOmemoKeyException e) {
            msg = e.getMessage();
            isCorrupted = true;
        }
        if (identityKeyPair == null) {
            msg = aTalkApp.getResString(R.string.omemo_identity_keypairs_missing, userDevice);
        }
        if (!StringUtils.isNullOrEmpty(msg)) {
            logger.warn(msg);
            // throw only if key is corrupted else return null
            if (isCorrupted)
                throw new CorruptedOmemoKeyException(msg);
        }
        return identityKeyPair;
    }

    /**
     * Store our identityKeyPair in storage. It would be a cool feature, if the key could be stored in an encrypted
     * database or something similar.
     *
     * @param userDevice our OmemoDevice.
     * @param identityKeyPair identityKeyPair
     */
    @Override
    public void storeOmemoIdentityKeyPair(OmemoDevice userDevice, IdentityKeyPair identityKeyPair)
    {
        String fingerprint = mKeyUtil.getFingerprintOfIdentityKeyPair(identityKeyPair).toString();
        logger.info("Store omemo identityKeyPair for :" + userDevice);
        mDB.storeIdentityKeyPair(userDevice, identityKeyPair, fingerprint);
    }

    /**
     * Remove the identityKeyPair of a user.
     *
     * @param userDevice our device.
     */
    @Override
    public void removeOmemoIdentityKeyPair(OmemoDevice userDevice)
    {
        mDB.deleteIdentityKey(userDevice);
    }

    /**
     * Load the public identityKey of a device.
     *
     * @param userDevice our OmemoDevice.
     * @param contactDevice the device of which we want to load the identityKey.
     * @return identityKey
     * @throws CorruptedOmemoKeyException when the key in question is corrupted and cant be deserialized.
     */
    @Override
    public IdentityKey loadOmemoIdentityKey(OmemoDevice userDevice, OmemoDevice contactDevice)
            throws CorruptedOmemoKeyException
    {
        String msg = null;
        boolean isCorrupted = false;

        IdentityKey identityKey = null;
        try {
            identityKey = mDB.loadIdentityKey(contactDevice);
        } catch (CorruptedOmemoKeyException e) {
            msg = e.getMessage();
            isCorrupted = true;
        }
        if (identityKey == null) {
            msg = aTalkApp.getResString(R.string.omemo_identity_key_missing, contactDevice);
        }
        if (!StringUtils.isNullOrEmpty(msg)) {
            logger.warn(msg);
            aTalkApp.showToastMessage(msg);
            // throw only if key is corrupted else return null
            if (isCorrupted)
                throw new CorruptedOmemoKeyException(msg);
        }
        return identityKey;
    }

    /**
     * Store the public identityKey of the device. If new device, initialize its fingerprint trust
     * status basing on:
     * - found no previously manually verified fingerprints for the contact AND
     * - pending user option BlindTrustBeforeVerification.
     * Otherwise just set its status to active and update lastActivation to current.
     *
     * Daniel Gultsch wrote a nice article about BTBV. Basically BTBV works as follows:
     * When a new key k is received for a Jid J, then k is only considered automatically trusted,
     * when there is no other key n of J, which has been manually trusted (verified). As soon as
     * there is such a key, k will be considered undecided. So a new key does only get considered
     * blindly trusted, when no other key has been manually trusted.
     *
     * @param userDevice our OmemoDevice.
     * @param contactDevice device.
     * @param contactKey identityKey belonging to the contactsDevice.
     */
    @Override
    public void storeOmemoIdentityKey(OmemoDevice userDevice, OmemoDevice contactDevice, IdentityKey contactKey)
    {
        String bareJid = contactDevice.getJid().toString();
        String fingerprint = mKeyUtil.getFingerprintOfIdentityKey(contactKey).toString();

        if (!mDB.loadIdentityKeys(userDevice).contains(contactKey)) {
            logger.info("Update identityKey for: " + contactDevice);
            FingerprintStatus fpStatus = getFingerprintStatus(contactDevice, fingerprint);
            if (fpStatus == null) {
                ConfigurationService mConfig = AndroidGUIActivator.getConfigurationService();
                if (mConfig.isBlindTrustBeforeVerification()
                        && mDB.numTrustedKeys(bareJid) == 0) {
                    logger.info("Blind trusted fingerprint for: " + contactDevice);
                    fpStatus = FingerprintStatus.createActiveTrusted();
                }
                else {
                    fpStatus = FingerprintStatus.createActiveUndecided();
                }
            }
            else {
                fpStatus = fpStatus.toActive();
            }
            mDB.storeIdentityKey(contactDevice, contactKey, fingerprint, fpStatus);
            trustCache.remove(fingerprint);
        }
        else {
            logger.warn("Skip uUpdate duplicated identityKey for: " + contactDevice);
        }
    }

    /**
     * Removes the identityKey of a device.
     *
     * @param userDevice our omemoDevice.
     * @param contactDevice device of which we want to delete the identityKey.
     */
    @Override
    public void removeOmemoIdentityKey(OmemoDevice userDevice, OmemoDevice contactDevice)
    {
        mDB.deleteIdentityKey(contactDevice);
    }

    public OmemoTrustCallback getTrustCallBack()
    {
        return aTalkTrustCallback;
    }

    /**
     * Trust Callback used to make trust decisions on identities.
     */
    public OmemoTrustCallback aTalkTrustCallback = new OmemoTrustCallback()
    {
        /*
         * Determine the identityKey of a remote client's device is in which TrustState based on the stored
         * value in the database.
         *
         * If you want to use this module, you should memorize, whether the user has trusted this key
         * or not, since the owner of the identityKey will be able to read sent messages when this
         * method returned 'trusted' for their identityKey. Either you let the user decide whether you
         * trust a key every time you see a new key, or you implement something like 'blind trust'
         * (see https://gultsch.de/trust.html).
         *
         * By default aTalk trust state implementation is that (BTBV option enabled)
         * TextSecure protocol is 'trust on first use' an identity key is considered 'trusted' if
         * there is no entry for the recipient in the local store, or if it matches the saved key for
         * a recipient in the local store. Only if it mismatches an entry in the local store is it
         * considered 'untrusted.'
         */
        @Override
        public TrustState getTrust(OmemoDevice device, OmemoFingerprint fingerprint)
        {
            FingerprintStatus fpStatus = getFingerprintStatus(device, fingerprint.toString());
            if (fpStatus != null) {
                FingerprintStatus.Trust trustState = fpStatus.getTrust();
                if (fpStatus.isTrusted())  /* VERIFIED OR TRUSTED */
                    return TrustState.trusted;
                else if (FingerprintStatus.Trust.UNDECIDED.equals(trustState)) {
                    return TrustState.undecided;
                }
                else if (FingerprintStatus.Trust.UNTRUSTED.equals(trustState)) {
                    return TrustState.untrusted;
                }
                /* else default to trusted - should never has this condition on present implementation */
                else {
                    return TrustState.trusted;
                }
            }
            /* default null to undecided or trusted on first device pending BTBV option */
            else {
                ConfigurationService mConfig = AndroidGUIActivator.getConfigurationService();
                if (mConfig.isBlindTrustBeforeVerification()
                        && mDB.numTrustedKeys(device.getJid().toString()) == 0) {
                    return TrustState.trusted;
                }
                return TrustState.undecided;
            }
        }

        /**
         * setTrust an OmemoIdentity to the specified trust state.
         *
         * In aTalk, will only be set to Trust.VERIFIED on user manual verification.
         * Trust.TRUSTED state is used only for Blind trusted before verification
         *
         * Distrust an OmemoIdentity. This involved marking the key as distrusted or undecided if previously is null
         */
        @Override
        public void setTrust(OmemoDevice device, OmemoFingerprint identityKeyFingerprint, TrustState state)
        {
            String fingerprint = identityKeyFingerprint.toString();
            FingerprintStatus fpStatus = getFingerprintStatus(device, fingerprint);

            switch (state) {
                case undecided:
                    fpStatus = FingerprintStatus.createActiveUndecided();
                    break;
                case trusted:
                    ConfigurationService mConfig = AndroidGUIActivator.getConfigurationService();
                    if (mConfig.isBlindTrustBeforeVerification()
                            && mDB.numTrustedKeys(device.getJid().toString()) == 0) {
                        fpStatus = FingerprintStatus.createActiveTrusted();
                    }
                    else {
                        fpStatus = fpStatus.toVerified();
                    }
                    break;
                case untrusted:
                    fpStatus = (fpStatus != null) ? fpStatus.toUntrusted() : FingerprintStatus.createActiveUndecided();
                    break;
            }
            setFingerprintStatus(device, fingerprint, fpStatus);
            trustCache.put(fingerprint, fpStatus);
        }
    };

    /**
     * Load a list of deviceIds from contact 'contact' from the local cache.
     * static final String DEVICE_LIST_ACTIVE = "activeDevices"; // identities.active = 1
     * static final String DEVICE_LIST_INACTIVE = "inactiveDevices";  // identities.active = 0
     *
     * @param userDevice our OmemoDevice.
     * @param contact contact we want to get the deviceList of
     * @return CachedDeviceList of the contact
     */
    @Override
    public OmemoCachedDeviceList loadCachedDeviceList(OmemoDevice userDevice, BareJid contact)
    {
        return mDB.loadCachedDeviceList(contact);
    }

    /**
     * Store the DeviceList of the contact in local storage.
     * See this as a cache.
     *
     * @param userDevice our OmemoDevice.
     * @param contact Contact
     * @param contactDeviceList list of the contact devices' ids.
     */
    @Override
    public void storeCachedDeviceList(OmemoDevice userDevice, BareJid contact, OmemoCachedDeviceList contactDeviceList)
    {
        try {
            mDB.storeCachedDeviceList(userDevice, contact, contactDeviceList);
        } catch (CannotEstablishOmemoSessionException | CorruptedOmemoKeyException e) {
            final String errMsg = e.getMessage();
            new Handler(Looper.getMainLooper()).post(new Runnable()
            {
                @Override
                public void run()
                {
                    Toast.makeText(aTalkApp.getGlobalContext(), errMsg, Toast.LENGTH_SHORT).show();
                }
            });
        }
    }

    // --------------------------------------
    // SessionStore
    // --------------------------------------

    /**
     * Returns a copy of the {@link SessionRecord} corresponding to the recipientId + deviceId
     * tuple, or a new SessionRecord if one does not currently exist.
     *
     * It is important that implementations return a copy of the current durable information. The
     * returned SessionRecord may be modified, but those changes should not have an effect on the
     * durable session state (what is returned by subsequent calls to this method) without the
     * store method being called here first.
     *
     * Load the crypto-lib specific session object of the device from storage.
     *
     * @param userDevice our OmemoDevice.
     * @param contactDevice device whose session we want to load
     * @return crypto related session
     */
    @Override
    public SessionRecord loadRawSession(OmemoDevice userDevice, OmemoDevice contactDevice)
    {
        SessionRecord session = mDB.loadSession(contactDevice);
        return (session != null) ? session : new SessionRecord();
    }

    /**
     * Load all crypto-lib specific session objects of contact 'contact'.
     *
     * @param userDevice our OmemoDevice.
     * @param contact BareJid of the contact we want to get all sessions from
     * @return TreeMap of deviceId and sessions of the contact
     */
    @Override
    public HashMap<Integer, SessionRecord> loadAllRawSessionsOf(OmemoDevice userDevice, BareJid contact)
    {
        return mDB.getSubDeviceSessions(contact);
    }

    /**
     * Store a crypto-lib specific session to storage.
     *
     * @param userDevice our OmemoDevice.
     * @param contactDevice OmemoDevice whose session we want to store
     * @param session session
     */
    @Override
    public void storeRawSession(OmemoDevice userDevice, OmemoDevice contactDevice, SessionRecord session)
    {
        mDB.storeSession(contactDevice, session);
    }

    /**
     * Remove a crypto-lib specific session from storage.
     *
     * @param userDevice our OmemoDevice.
     * @param contactDevice device whose session we want to delete
     */
    @Override
    public void removeRawSession(OmemoDevice userDevice, OmemoDevice contactDevice)
    {
        mDB.deleteSession(contactDevice);
    }

    /**
     * Remove all crypto-lib specific session of a contact.
     *
     * @param userDevice our OmemoDevice.
     * @param contact BareJid of the contact
     */
    @Override
    public void removeAllRawSessionsOf(OmemoDevice userDevice, BareJid contact)
    {
        mDB.deleteAllSessions(contact);
    }

    /**
     * Return true, if we have a session with the device, otherwise false.
     * Hint for Signal: Do not try 'return getSession() != null' since this will create a new session.
     *
     * @param userDevice our OmemoDevice.
     * @param contactDevice device
     * @return true if we have session, otherwise false
     */
    @Override
    public boolean containsRawSession(OmemoDevice userDevice, OmemoDevice contactDevice)
    {
        return mDB.containsSession(contactDevice);
    }

    /**
     * Set the date of the last time the deviceId was published. This method only gets called, when the deviceId
     * was inactive/non-existent before it was published.
     *
     * @param userDevice our OmemoDevice
     * @param contactDevice OmemoDevice in question
     * @param date date of the last publication after not being published
     */
    @Override
    public void setDateOfLastDeviceIdPublication(OmemoDevice userDevice, OmemoDevice contactDevice, Date date)
    {
        mDB.setLastDeviceIdPublicationDate(contactDevice, date);
    }

    /**
     * Return the date of the last time the deviceId was published after previously being not published.
     * (Point in time, where the status of the deviceId changed from inactive/non-existent to active).
     *
     * @param userDevice our OmemoDevice
     * @param contactDevice OmemoDevice in question
     * @return date of the last publication after not being published
     */
    @Override
    public Date getDateOfLastDeviceIdPublication(OmemoDevice userDevice, OmemoDevice contactDevice)
    {
        return mDB.getLastDeviceIdPublicationDate(contactDevice);
    }

    /**
     * Set the date in millis of the last message that was received from a device.
     *
     * @param userDevice omemoManager of our device.
     * @param contactDevice device in question
     * @param date date of the last received message
     */
    @Override
    public void setDateOfLastReceivedMessage(OmemoDevice userDevice, OmemoDevice contactDevice, Date date)
    {
        mDB.setLastMessageReceiveDate(contactDevice, date);
    }

    /**
     * Return the date in millis of the last message that was received from device 'from'.
     *
     * @param userDevice our OmemoDevice.
     * @param contactDevice device in question
     * @return date if existent, otherwise null
     */
    @Override
    public Date getDateOfLastReceivedMessage(OmemoDevice userDevice, OmemoDevice contactDevice)
    {
        return mDB.getLastMessageReceiveDate(contactDevice);
    }

    /**
     * Generate fresh user identityKeyPairs and bundle and publish it to the server.
     */
    public void regenerate(AccountID accountId)
    {
        ProtocolProviderService pps = accountId.getProtocolProvider();
        if (pps != null) {
            XMPPTCPConnection connection = pps.getConnection();
            if ((connection != null) && connection.isAuthenticated()) {

                BareJid userJid = accountId.getBareJid();
                SortedSet<Integer> deviceIds = localDeviceIdsOf(userJid);
                for (int deviceId : deviceIds) {
                    OmemoDevice userDevice = new OmemoDevice(userJid, deviceId);
                    purgeOwnDeviceKeys(userDevice);

                    int defaultDeviceId = OmemoManager.randomDeviceId();
                    setDefaultDeviceId(userJid, defaultDeviceId);
                    OmemoManager.getInstanceFor(connection, defaultDeviceId);
                }
            }
        }
    }

    /**
     * Publish a new device list with just our own deviceId in it.
     */
//    public void cleanUpDeviceList(AccountID accountId)
//    {
//        //OmemoManager#purgeDeviceList(); keep only own omemoDevice
//
//    }

    /**
     * Delete this device's IdentityKey, PreKeys, SignedPreKeys and Sessions.
     *
     * @param userDevice our OmemoDevice.
     */
    @Override
    public void purgeOwnDeviceKeys(OmemoDevice userDevice)
    {
        mDB.purgeOmemoDb(userDevice);
        trustCache.evictAll();
    }

    /**
     * Purge owner old unused devices for pushlished server deviceList and local database
     * 1. perKeyPairs
     * 2. signed prekeys
     * 3. identities tables entries
     * 4. session table entries
     *
     * @param omemoManager OmemoManager instance for our OmemoDevice.
     */
    public void purgeInactiveUserDevices(OmemoManager omemoManager)
    {
        BareJid userJid = omemoManager.getOwnDevice().getJid();
        OmemoDevice userDevice = null;
        try {
            omemoManager.purgeDeviceList();
            OmemoCachedDeviceList deviceList = mDB.loadCachedDeviceList(userJid);
            for (int deviceId : deviceList.getInactiveDevices()) {
                userDevice = new OmemoDevice(userJid, deviceId);
                logger.info("Purge inactive device for: " +  userDevice);
                purgeOwnDeviceKeys(userDevice);
            }
        } catch (SmackException | InterruptedException | XMPPException.XMPPErrorException e) {
            aTalkApp.showToastMessage(R.string.omemo_purge_inactive_device_error, userDevice);
            e.printStackTrace();
        }
    }

    /**
     * Clean up omemo database for user account when deleted.
     *
     * @param account the omemo database of the account to be purged.
     */

    public void purgeUserOmemoData(Account account)
    {
        BareJid bareJid = account.getJid().asBareJid();
        SortedSet<Integer> deviceIds = localDeviceIdsOf(bareJid);
        for (int deviceId : deviceIds) {
            OmemoDevice userDevice = new OmemoDevice(bareJid, deviceId);
            purgeOwnDeviceKeys(userDevice);
        }
    }

    /**
     * Method help to clean up omemo database of accounts that have been removed
     */
    public void cleanUpOmemoDB()
    {
        List<String> userIds = mDB.getAllAccountIDs();
        HashMap<String, Integer> omemoIDs = mDB.loadAllOmemoRegIds();

        for (HashMap.Entry<String, Integer> entry : omemoIDs.entrySet()) {
            String userId = entry.getKey();
            if (userIds.contains(userId))
                continue;

            int deviceId = entry.getValue();
            try {
                BareJid bareJid = JidCreate.bareFrom(userId);
                OmemoDevice userDevice = new OmemoDevice(bareJid, deviceId);
                purgeOwnDeviceKeys(userDevice);
                logger.info("Clean up omemo database for: " + userDevice);
            } catch (XmppStringprepException e) {
                logger.error("Error in clean omemo database for: " + userId + ":" + deviceId);
            }
        }
    }
}
