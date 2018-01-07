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
import android.view.View;
import android.widget.Toast;

import net.java.sip.communicator.util.Logger;

import org.atalk.android.aTalkApp;
import org.atalk.android.gui.AndroidGUIActivator;
import org.atalk.android.gui.chat.filetransfer.ReceiveFileConversation;
import org.atalk.persistance.DatabaseBackend;
import org.atalk.service.configuration.ConfigurationService;
import org.jivesoftware.smackx.omemo.*;
import org.jivesoftware.smackx.omemo.exceptions.CannotEstablishOmemoSessionException;
import org.jivesoftware.smackx.omemo.exceptions.CorruptedOmemoKeyException;
import org.jivesoftware.smackx.omemo.internal.*;
import org.jivesoftware.smackx.omemo.signal.SignalOmemoStore;
import org.jivesoftware.smackx.omemo.util.OmemoKeyUtil;
import org.jxmpp.jid.BareJid;
import org.whispersystems.libsignal.*;
import org.whispersystems.libsignal.state.*;

import java.security.cert.X509Certificate;
import java.util.*;

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
	public static final String LAST_ACTIVATION = "last_activation"; //lastMessageReceivedDate
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
	private OmemoDevice mDevice;
	private DatabaseBackend mDB;
	private OmemoKeyUtil mKeyUtil;

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
	 * <p>
	 * Need to pass device to create(String fingerprint) for trustCache
	 *
	 * @param device
	 * 		omemoDevice for which its fingerprint status is to be retrieved
	 * @param fingerprint
	 * 		fingerprint to check
	 * @return the fingerprint status for the specified device
	 * @see LruCache#create(Object)
	 */
	public FingerprintStatus getFingerprintStatus(OmemoDevice device, String fingerprint)
	{
		/* need to setup mDevice for FingerprintStatus#create(String fingerprint) */
		mDevice = device;
		return (fingerprint == null) ? null : trustCache.get(fingerprint);
	}

	private void setFingerprintStatus(OmemoDevice device, String fingerprint,
			FingerprintStatus status)
	{
		mDB.setIdentityKeyTrust(device, fingerprint, status);
		trustCache.remove(fingerprint); // clear old status in trustCache
	}

	public void setFingerprintCertificate(String fingerprint, X509Certificate x509Certificate)
	{
		mDB.setIdentityKeyCertificate(mDevice, fingerprint, x509Certificate);
	}

	public X509Certificate getFingerprintCertificate(String fingerprint)
	{
		return mDB.getIdentityKeyCertificate(mDevice, fingerprint);
	}

	public Set<IdentityKey> getContactKeysWithTrust(String bareJid, FingerprintStatus status)
	{
		return mDB.loadIdentityKeys(mDevice, status);
	}

	public long getContactNumTrustedKeys(String bareJid)
	{
		return mDB.numTrustedKeys(bareJid);
	}

	public void preVerifyFingerprint(OmemoDevice device, String name, String fingerprint)
	{
		mDB.storePreVerification(device, fingerprint,
				FingerprintStatus.createInactiveVerified());
	}

	//======================= OMEMO Store =========================================

	// --------------------------------------
	// OMEMO Devices Store
	// --------------------------------------

	/**
	 * Return true if this is a fresh installation.
	 *
	 * @param omemoManager
	 * 		omemoManager of our device.
	 * @return always return false as OMEMO tables are created on start up.
	 */
	@Override
	public boolean isFreshInstallation(OmemoManager omemoManager)
	{
		return (mDB.getLastPreKeyId(omemoManager) == 0);
	}

	/**
	 * Return the default deviceId for a user. The defaultDeviceId will be used when the
	 * OmemoManager gets instantiated without passing a specific deviceId. If no default id is
	 * set, return -1;
	 *
	 * @param user
	 * 		user
	 * @return defaultDeviceId or -1
	 */
	@Override
	public int getDefaultDeviceId(BareJid user)
	{
		return mDB.loadOmemoRegId(user);
	}

	/**
	 * Set the default deviceId of a user.
	 *
	 * @param user
	 * 		user
	 * @param defaultDeviceId
	 * 		defaultDeviceId
	 */
	@Override
	public void setDefaultDeviceId(BareJid user, int defaultDeviceId)
	{
		mDB.storeOmemoRegId(user, defaultDeviceId);
	}

	/**
	 * Return the id of the currently used signed preKey.
	 * This is used to avoid collisions when generating a new signedPreKey.
	 *
	 * @param omemoManager
	 * 		omemoManager of our device.
	 * @return id
	 */
	@Override
	public int loadCurrentSignedPreKeyId(OmemoManager omemoManager)
	{
		return mDB.loadCurrentSignedPKeyId(omemoManager);
	}

	/**
	 * Store the id of the currently used signedPreKey.
	 *
	 * @param omemoManager
	 * 		omemoManager of our device.
	 * @param currentSignedPreKeyId
	 * 		if of the signedPreKey that is currently in use
	 */
	@Override
	public void storeCurrentSignedPreKeyId(OmemoManager omemoManager, int currentSignedPreKeyId)
	{
		mDB.storeCurrentSignedPKeyId(omemoManager, currentSignedPreKeyId);
	}

	// --------------------------------------
	// PreKey Store
	// --------------------------------------

	/**
	 * Return all our current OmemoPreKeys.
	 *
	 * @param omemoManager
	 * 		omemoManager of our device.
	 * @return Map containing our preKeys
	 */
	@Override
	public HashMap<Integer, PreKeyRecord> loadOmemoPreKeys(OmemoManager omemoManager)
	{
		return mDB.loadPreKeys(omemoManager);
	}

	/**
	 * Load the preKey with id 'preKeyId' from storage.
	 *
	 * @param omemoManager
	 * 		omemoManager of our device.
	 * @param preKeyId
	 * 		id of the PreKeyRecord to be loaded
	 * @return loaded corresponding PreKeyRecord
	 */
	@Override
	public PreKeyRecord loadOmemoPreKey(OmemoManager omemoManager, int preKeyId)
	{
		PreKeyRecord record = mDB.loadPreKey(omemoManager, preKeyId);
		if (record == null) {
			logger.warn("There is no PreKeyRecord for: " + preKeyId);
		}
		return record;
	}

	/**
	 * Store a PreKey in storage.
	 *
	 * @param omemoManager
	 * 		omemoManager of our device.
	 * @param preKeyId
	 * 		the ID of the PreKeyRecord to store
	 * @param preKeyRecord
	 * 		ths PreKeyRecord
	 */
	@Override
	public void storeOmemoPreKey(OmemoManager omemoManager, int preKeyId,
			PreKeyRecord preKeyRecord)
	{
		mDB.storePreKey(omemoManager, preKeyRecord);
	}

	/**
	 * remove a preKey from storage. This is called, when a contact used one of our preKeys to
	 * establish a session with us.
	 *
	 * @param omemoManager
	 * 		omemoManager of our device.
	 * @param preKeyId
	 * 		id of the used key that will be deleted
	 */
	@Override
	public void removeOmemoPreKey(OmemoManager omemoManager, int preKeyId)
	{
		mDB.deletePreKey(omemoManager, preKeyId);
	}

	/**
	 * Return the id of the last generated preKey.
	 * This is used to generate new preKeys without preKeyId collisions.
	 *
	 * @param omemoManager
	 * 		omemoManager of our device.
	 * @return id of the last preKey; 0 if none is found
	 */
	@Override
	public int loadLastPreKeyId(OmemoManager omemoManager)
	{
		return mDB.loadLastPreKeyId(omemoManager);
	}

	/**
	 * Store the id of the last preKey we generated.
	 *
	 * @param omemoManager
	 * 		omemoManager of our device.
	 * @param lastPreKeyId
	 * 		the id of the last generated PreKey
	 */
	@Override
	public void storeLastPreKeyId(OmemoManager omemoManager, int lastPreKeyId)
	{
		mDB.storeLastPreKeyId(omemoManager, lastPreKeyId);
	}

	// --------------------------------------
	// SignedPreKeyStore
	// --------------------------------------

	/**
	 * Return the signedPreKey with the id 'singedPreKeyId'.
	 *
	 * @param omemoManager
	 * 		omemoManager of our device.
	 * @param signedPreKeyId
	 * 		id of the SignedPreKeyRecord
	 * @return the corresponding SignedPreKeyRecord
	 */
	@Override
	public SignedPreKeyRecord loadOmemoSignedPreKey(OmemoManager omemoManager, int signedPreKeyId)
	{
		OmemoDevice device = omemoManager.getOwnDevice();
		SignedPreKeyRecord record = mDB.loadSignedPreKey(omemoManager, signedPreKeyId);
		if (record == null) {
			logger.warn("There is no SignedPreKeyRecord for: " + signedPreKeyId);
		}
		return record;
	}

	/**
	 * Load all our signed PreKeys.
	 *
	 * @param omemoManager
	 * 		omemoManager of our device.
	 * @return HashMap of our singedPreKeys
	 */
	@Override
	public HashMap<Integer, SignedPreKeyRecord> loadOmemoSignedPreKeys(OmemoManager omemoManager)
	{
		OmemoDevice device = omemoManager.getOwnDevice();
		return mDB.loadSignedPreKeys(device);
	}

	/**
	 * Store a signedPreKey in storage.
	 *
	 * @param omemoManager
	 * 		omemoManager of our device.
	 * @param signedPreKeyId
	 * 		id of the signedPreKey
	 * @param signedPreKey
	 * 		the key itself
	 */
	@Override
	public void storeOmemoSignedPreKey(OmemoManager omemoManager, int signedPreKeyId,
			SignedPreKeyRecord signedPreKey)
	{
		OmemoDevice device = omemoManager.getOwnDevice();
		mDB.storeSignedPreKey(device, signedPreKeyId, signedPreKey);
	}

	/**
	 * Remove a signedPreKey from storage.
	 *
	 * @param omemoManager
	 * 		omemoManager of our device.
	 * @param signedPreKeyId
	 * 		id of the signedPreKey that will be removed
	 */
	@Override
	public void removeOmemoSignedPreKey(OmemoManager omemoManager, int signedPreKeyId)
	{
		mDB.deleteSignedPreKey(omemoManager, signedPreKeyId);
	}

	/**
	 * Set the date in millis of the last time the signed preKey was renewed.
	 *
	 * @param omemoManager
	 * 		omemoManager of our device.
	 * @param date
	 * 		date
	 */
	@Override
	public void setDateOfLastSignedPreKeyRenewal(OmemoManager omemoManager, Date date)
	{
		OmemoDevice device = omemoManager.getOwnDevice();
		mDB.setLastSignedPreKeyRenewal(omemoManager, date);
	}

	/**
	 * Get the date in millis of the last time the signed preKey was renewed.
	 *
	 * @param omemoManager
	 * 		omemoManager of our device.
	 * @return date if existent, otherwise null
	 */
	@Override
	public Date getDateOfLastSignedPreKeyRenewal(OmemoManager omemoManager)
	{
		return mDB.getLastSignedPreKeyRenewal(omemoManager);
	}

	// --------------------------------------
	// IdentityKeyStore
	// --------------------------------------

	/**
	 * Load own identityKeyPair from storage.
	 *
	 * @param omemoManager
	 * 		omemoManager of our device.
	 * @return identityKeyPair
	 * @throws CorruptedOmemoKeyException
	 * 		Thrown, if the stored key is damaged (*hands up* not my fault!)
	 */
	@Override
	public IdentityKeyPair loadOmemoIdentityKeyPair(OmemoManager omemoManager)
			throws CorruptedOmemoKeyException
	{
		OmemoDevice device = omemoManager.getOwnDevice();
		IdentityKeyPair identityKeyPair = mDB.loadIdentityKeyPair(device);
		if (identityKeyPair == null) {
			String msg = "OMEMO Identity KeyPair is null for: " + device;
			throw new CorruptedOmemoKeyException(msg);
		}
		return identityKeyPair;
	}

	/**
	 * Store own identityKeyPair in storage
	 *
	 * @param omemoManager
	 * 		omemoManager of our device.
	 * @param identityKeyPair
	 * 		identityKeyPair
	 */
	@Override
	public void storeOmemoIdentityKeyPair(OmemoManager omemoManager,
			IdentityKeyPair identityKeyPair)
	{
		String fingerprint = mKeyUtil.getFingerprint(identityKeyPair.getPublicKey()).toString();
		logger.info("Store omemo identityKeyPair for :" + omemoManager.getOwnDevice());
		mDB.storeIdentityKeyPair(omemoManager, identityKeyPair, fingerprint);
	}

	/**
	 * Store the public identityKey of the device. If new device, initialize its fingerprint trust
	 * status basing on:
	 * - found no previously manually verified fingerprints for the contact AND
	 * - pending user option BlindTrustBeforeVerification.
	 * Otherwise just set its status to active and update lastActivation to current.
	 * <p>
	 * Daniel Gultsch wrote a nice article about BTBV. Basically BTBV works as follows:
	 * When a new key k is received for a Jid J, then k is only considered automatically trusted,
	 * when there is no other key n of J, which has been manually trusted (verified). As soon as
	 * there is such a key, k will be considered undecided. So a new key does only get considered
	 * blindly trusted, when no other key has been manually trusted.
	 *
	 * @param omemoManager
	 * 		omemoManager of our device.
	 * @param device
	 * 		The remote client's device
	 * @param identityKey
	 * 		The remote client's identity key.
	 */
	@Override
	public void storeOmemoIdentityKey(OmemoManager omemoManager, OmemoDevice device,
			IdentityKey identityKey)
	{
		String bareJid = device.getJid().toString();
		String fingerprint = null;
		fingerprint = mKeyUtil.getFingerprint(identityKey).toString();

		if (!mDB.loadIdentityKeys(device).contains(identityKey)) {
			FingerprintStatus fpStatus = getFingerprintStatus(device, fingerprint);
			if (fpStatus == null) {
				ConfigurationService mConfig = AndroidGUIActivator.getConfigurationService();
				if (mConfig.isBlindTrustBeforeVerification()
					&& mDB.numTrustedKeys(bareJid) == 0) {
					logger.info("Blind trusted fingerprint for: " + device.getJid());
					fpStatus = FingerprintStatus.createActiveTrusted();
				}
				else {
					fpStatus = FingerprintStatus.createActiveUndecided();
				}
			}
			else {
				fpStatus = fpStatus.toActive();
			}
			mDB.storeIdentityKey(device, identityKey, fingerprint, fpStatus);
			trustCache.remove(fingerprint);
		}
	}

	/**
	 * Load the public identityKey of the device.
	 *
	 * @param omemoManager
	 * 		omemoManager of our device.
	 * @param device
	 * 		device
	 * @return identityKey
	 * @throws CorruptedOmemoKeyException
	 * 		when the key in question is corrupted and cant be de-serialized.
	 */
	@Override
	public IdentityKey loadOmemoIdentityKey(OmemoManager omemoManager, OmemoDevice device)
			throws CorruptedOmemoKeyException
	{
		IdentityKey identityKey = mDB.loadIdentityKey(device);
		if (identityKey == null) {
			final String msg = "Loaded OMEMO IdentityKey is null for: " + device;
            new Handler(Looper.getMainLooper()).post(new Runnable()
            {
                @Override
                public void run()
                {
                    Toast.makeText(aTalkApp.getGlobalContext(), msg, Toast.LENGTH_SHORT);
                }
            });
			throw new CorruptedOmemoKeyException(msg);
		}
		return identityKey;
	}

	/**
	 * <p>
	 * Decide, whether a identityKey of a remote client's device is trusted or not.
	 * If you want to use this module, you should memorize, whether the user has trusted this key
	 * or not, since the owner of the identityKey will be able to read sent messages when this
	 * method returned 'true' for their identityKey. Either you let the user decide whether you
	 * trust a key every time you see a new key, or you implement something like 'blind trust'
	 * (see https://gultsch.de/trust.html).
	 * <p>
	 * Determine whether a remote client's identity is trusted. aTalk is that the
	 * TextSecure protocol is 'trust on first use.' an identity key is considered 'trusted' if
	 * there is no entry for the recipient in the local store, or if it matches the saved key for
	 * a recipient in the local store.  Only if it mismatches an entry in the local store is it
	 * considered 'untrusted.'
	 *
	 * @param omemoManager
	 * 		omemoManager of our device.
	 * @param device
	 * 		Owner of the key
	 * @param fingerprint
	 * 		fingerprint
	 * @return true, if the user trusts the key and wants to send messages to it, otherwise false
	 * @see #storeOmemoIdentityKey(OmemoManager, OmemoDevice, IdentityKey)
	 */
	@Override
	public boolean isTrustedOmemoIdentity(OmemoManager omemoManager, OmemoDevice device,
			OmemoFingerprint fingerprint)
	{
		FingerprintStatus fpStatus = getFingerprintStatus(device, fingerprint.toString());
		return (fpStatus == null) || fpStatus.isTrusted();
	}

	/**
	 * Did the user yet made a decision about whether to trust or distrust this device?
	 *
	 * @param omemoManager
	 * 		omemoManager of our device.
	 * @param device
	 * 		device
	 * @param fingerprint
	 * 		fingerprint
	 * @return true, if the user either trusted or distrusted the device. Return false, if the
	 * user did not yet decide.
	 */
	@Override
	public boolean isDecidedOmemoIdentity(OmemoManager omemoManager, OmemoDevice device,
			OmemoFingerprint fingerprint)
	{
		FingerprintStatus fpStatus = getFingerprintStatus(device, fingerprint.toString());
		if (fpStatus != null) {
			FingerprintStatus.Trust trust = fpStatus.getTrust();
			return !FingerprintStatus.Trust.UNDECIDED.equals(trust);
		}
		else {
			return true;
		}
	}

	/**
	 * Trust an OmemoIdentity. This involves marking the key as verified.
	 * In aTalk, will only be set to Trust.VERIFIED. Trust.TRUSTED state is used only for
	 * Blind trusted before verification
	 *
	 * @param omemoManager
	 * 		omemoManager of our device.
	 * @param device
	 * 		device
	 * @param identityKeyFingerprint
	 * 		fingerprint
	 */
	@Override
	public void trustOmemoIdentity(OmemoManager omemoManager, OmemoDevice device,
			OmemoFingerprint identityKeyFingerprint)
	{
		String fingerprint = identityKeyFingerprint.toString();
		FingerprintStatus fpStatus = getFingerprintStatus(device, fingerprint);
		if (fpStatus != null)
			fpStatus = fpStatus.toVerified();
		else {
			fpStatus = FingerprintStatus.createActiveTrusted();
		}
		setFingerprintStatus(device, fingerprint, fpStatus);
		trustCache.put(fingerprint, fpStatus);
	}

	/**
	 * Distrust an OmemoIdentity. This involved marking the key as distrusted.
	 *
	 * @param omemoManager
	 * 		omemoManager of our device.
	 * @param device
	 * 		device
	 * @param identityKeyFingerprint
	 * 		fingerprint
	 */
	@Override
	public void distrustOmemoIdentity(OmemoManager omemoManager, OmemoDevice device,
			OmemoFingerprint identityKeyFingerprint)
	{
		String fingerprint = identityKeyFingerprint.toString();
		FingerprintStatus fpStatus = getFingerprintStatus(device, fingerprint);
		if (fpStatus != null) {
            fpStatus = fpStatus.toUntrusted();
        }
		else {
			fpStatus = FingerprintStatus.createActiveUndecided();
		}
		setFingerprintStatus(device, fingerprint, fpStatus);
		trustCache.put(fingerprint, fpStatus);
	}

    /**
	 * Load a list of deviceIds from contact 'contact' from the local cache.
	 * static final String DEVICE_LIST_ACTIVE = "activeDevices"; // identities.active = 1
	 * static final String DEVICE_LIST_INACTIVE = "inactiveDevices";  // identities.active = 0
	 *
	 * @param omemoManager
	 * 		omemoManager of our device.
	 * @param contact
	 * 		contact we want to get the deviceList of
	 * @return CachedDeviceList of the contact
	 */
	@Override
	public CachedDeviceList loadCachedDeviceList(OmemoManager omemoManager, BareJid contact)
	{
		return mDB.loadCachedDeviceList(contact);
	}

	/**
	 * Store the DeviceList of the contact in local storage.
	 * See this as a cache.
	 *
	 * @param omemoManager
	 * 		omemoManager of our device.
	 * @param contact
	 * 		Contact
	 * @param deviceList
	 * 		list of the contacts devices' ids.
	 */
	@Override
	public void storeCachedDeviceList(OmemoManager omemoManager, BareJid contact,
			CachedDeviceList deviceList)
	{
        try {
            mDB.storeCachedDeviceList(omemoManager, contact, deviceList);
        } catch (CannotEstablishOmemoSessionException | CorruptedOmemoKeyException e) {
            final String errMsg = e.getMessage();
            new Handler(Looper.getMainLooper()).post(new Runnable()
            {
                @Override
                public void run()
                {
                    Toast.makeText(aTalkApp.getGlobalContext(), errMsg, Toast.LENGTH_SHORT);
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
	 * <p/>
	 * It is important that implementations return a copy of the current durable information. The
	 * returned SessionRecord may be modified, but those changes should not have an effect on the
	 * durable session state (what is returned by subsequent calls to this method) without the
	 * store method being called here first.
	 * <p>
	 * Load the crypto-lib specific session object of the device from storage.
	 *
	 * @param omemoManager
	 * 		omemoManager of our device.
	 * @param omemoDevice
	 * 		the remote client's device whose session we want to load
	 * @return a copy of the crypto related SessionRecord corresponding to the omemoDevice, or a
	 * new SessionRecord if one does not currently exist.
	 */
	@Override
	public SessionRecord loadRawSession(OmemoManager omemoManager, OmemoDevice omemoDevice)
	{
		SessionRecord session = mDB.loadSession(omemoDevice);
		return (session != null) ? session : new SessionRecord();
	}

	/**
	 * Returns all known devices with active sessions for a recipient
	 * Load all crypto-lib specific session objects of contact 'contact'.
	 *
	 * @param omemoManager
	 * 		omemoManager of our device.
	 * @param contact
	 * 		BareJid of the contact we want to get all sessions from
	 * @return HashMap of deviceId and sessions of the contact
	 */
	@Override
	public HashMap<Integer, SessionRecord> loadAllRawSessionsOf(OmemoManager omemoManager,
			BareJid contact)
	{
		return mDB.getSubDeviceSessions(contact);
	}

	/**
	 * Store a crypto-lib specific session for the specified device to storage.
	 *
	 * @param omemoManager
	 * 		omemoManager of our device.
	 * @param device
	 * 		The remote client OmemoDevice whose session we want to store
	 * @param session
	 * 		the current SessionRecord for the remote client.
	 */
	@Override
	public void storeRawSession(OmemoManager omemoManager, OmemoDevice device,
			SessionRecord session)
	{
		mDB.storeSession(device, session);
	}

	/**
	 * Remove a crypto-lib specific session from storage.
	 *
	 * @param omemoManager
	 * 		omemoManager of our device.
	 * @param device
	 * 		the remote client's device whose session we want to delete
	 */
	@Override
	public void removeRawSession(OmemoManager omemoManager, OmemoDevice device)
	{
		mDB.deleteSession(device);
	}

	/**
	 * Remove all crypto-lib specific session of a contact.
	 *
	 * @param omemoManager
	 * 		omemoManager of our device.
	 * @param contact
	 * 		BareJid of the contact (remote client)
	 */
	@Override
	public void removeAllRawSessionsOf(OmemoManager omemoManager, BareJid contact)
	{
		mDB.deleteAllSessions(contact);
	}

	/**
	 * Return true, if we have a session with the device, otherwise false.
	 * Hint for Signal: Do not try 'return getSession() != null' since this will create a new
	 * session.
	 *
	 * @param omemoManager
	 * 		omemoManager of our device.
	 * @param device
	 * 		the device of the remote client.
	 * @return true if we have session, otherwise false
	 */
	@Override
	public boolean containsRawSession(OmemoManager omemoManager, OmemoDevice device)
	{
		return mDB.containsSession(device);
	}

	/**
	 * Set the date in millis of the last message that was received from device 'from' to 'date'.
	 *
	 * @param omemoManager
	 * 		omemoManager of our device.
	 * @param from
	 * 		device in question
	 * @param date
	 * 		date of the last received message
	 */
	@Override
	public void setDateOfLastReceivedMessage(OmemoManager omemoManager, OmemoDevice from,
			Date date)
	{
		mDB.setLastMessageReceiveDate(from, date);
	}

	/**
	 * Return the date in millis of the last message that was received from device 'from'.
	 *
	 * @param omemoManager
	 * 		omemoManager of our device.
	 * @param from
	 * 		device in question
	 * @return date if existent as long, otherwise -1
	 */
	@Override
	public Date getDateOfLastReceivedMessage(OmemoManager omemoManager, OmemoDevice from)
	{
		return mDB.getLastMessageReceiveDate(from);
	}

	/**
	 * /**
	 * Delete this device's IdentityKey, PreKeys, SignedPreKeys and Sessions.
	 *
	 * @param omemoManager
	 * 		omemoManager of our device.
	 */
	@Override
	public void purgeOwnDeviceKeys(OmemoManager omemoManager)
	{
		OmemoDevice device = omemoManager.getOwnDevice();
		mDB.purgeOmemoDb(device);
		trustCache.evictAll();
	}
}
