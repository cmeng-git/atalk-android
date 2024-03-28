/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.plugin.otr;

import net.java.otr4j.crypto.OtrCryptoEngineImpl;
import net.java.otr4j.crypto.OtrCryptoException;
import net.java.sip.communicator.plugin.otr.OtrContactManager.OtrContact;
import net.java.sip.communicator.service.protocol.AccountID;
import net.java.sip.communicator.service.protocol.Contact;

import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.List;
import java.util.Vector;

/**
 * @author George Politis
 * @author Lyubomir Marinov
 * @author Eng Chong Meng
 */
public class ScOtrKeyManagerImpl implements ScOtrKeyManager
{
	private static final String PUBLIC_KEY = ".publicKey";
	private static final String PRIVATE_KEY = ".privateKey";
	private static final String FINGER_PRINT = ".fingerprints";
	private static final String FP_VERIFIED = ".fingerprint_verified";
	private static final String PUBLIC_KEY_VERIFIED = ".publicKey_verified";

	private final OtrConfigurator configurator = new OtrConfigurator();
	private final List<ScOtrKeyManagerListener> listeners = new Vector<>();

	public void addListener(ScOtrKeyManagerListener l)
	{
		synchronized (listeners) {
			if (!listeners.contains(l))
				listeners.add(l);
		}
	}

	/**
	 * Gets a copy of the list of <code>ScOtrKeyManagerListener</code>s registered with this instance
	 * which may safely be iterated without the risk of a <code>ConcurrentModificationException</code>.
	 *
	 * @return a copy of the list of <code>ScOtrKeyManagerListener<code>s registered with this
	 * instance which may safely be iterated without the risk of a
	 * <code>ConcurrentModificationException</code>
	 */
	private ScOtrKeyManagerListener[] getListeners()
	{
		synchronized (listeners) {
			return listeners.toArray(new ScOtrKeyManagerListener[listeners.size()]);
		}
	}

	public void removeListener(ScOtrKeyManagerListener l)
	{
		synchronized (listeners) {
			listeners.remove(l);
		}
	}

	public void verify(OtrContact otrContact, String fingerprint)
	{
		if ((fingerprint == null) || otrContact == null)
			return;

		this.configurator.setProperty(otrContact.contact.getAddress() + "." + fingerprint
				+ FP_VERIFIED, true);
		for (ScOtrKeyManagerListener l : getListeners())
			l.contactVerificationStatusChanged(otrContact);
	}

	public void unverify(OtrContact otrContact, String fingerprint)
	{
		if ((fingerprint == null) || otrContact == null)
			return;

		this.configurator.setProperty(otrContact.contact.getAddress() + "." + fingerprint
				+ FP_VERIFIED, false);
		for (ScOtrKeyManagerListener l : getListeners())
			l.contactVerificationStatusChanged(otrContact);
	}

	public boolean isVerified(Contact contact, String fingerprint)
	{
		if (fingerprint == null || contact == null)
			return false;

		return this.configurator.getPropertyBoolean(contact.getAddress() + "." + fingerprint
				+ FP_VERIFIED, false);
	}

	public List<String> getAllRemoteFingerprints(Contact contact)
	{
		if (contact == null)
			return null;

		/*
		 * The following lines are needed for backward compatibility with old versions of the otr
		 * plugin. Instead of lists of fingerprints the otr plugin used to store one public key
		 * for every contact in the form of "userID.publicKey=..." and one boolean property in
		 * the form of "userID.publicKey.verified=...". In order not to loose these old
		 * properties we have to convert them to match the new format.
		 */
		String userID = contact.getAddress();

		byte[] b64PubKey = this.configurator.getPropertyBytes(userID + PUBLIC_KEY);
		if (b64PubKey != null) {
			// We delete the old format property because we are going to convert it in the new
			// format
			this.configurator.removeProperty(userID + PUBLIC_KEY);
			X509EncodedKeySpec publicKeySpec = new X509EncodedKeySpec(b64PubKey);

			KeyFactory keyFactory;
			try {
				keyFactory = KeyFactory.getInstance("DSA");
				PublicKey pubKey = keyFactory.generatePublic(publicKeySpec);

				boolean isVerified = this.configurator.getPropertyBoolean(userID
						+ PUBLIC_KEY_VERIFIED, false);

				// We also make sure to delete this old format property if it exists.
				this.configurator.removeProperty(userID + PUBLIC_KEY_VERIFIED);
				String fingerprint = getFingerprintFromPublicKey(pubKey);

				// Now we can store the old properties in the new format.
				if (isVerified)
					verify(OtrContactManager.getOtrContact(contact, null), fingerprint);
				else
					unverify(OtrContactManager.getOtrContact(contact, null), fingerprint);

				// Finally we append the new fingerprint to out stored list of
				// fingerprints.
				this.configurator.appendProperty(userID + FINGER_PRINT, fingerprint);
			}
			catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
				e.printStackTrace();
			}
		}

		// Now we can safely return our list of fingerprints for this contact without worrying
		// that we missed an old format property.
		return this.configurator.getAppendedProperties(contact.getAddress() + FINGER_PRINT);
	}

	public String getFingerprintFromPublicKey(PublicKey pubKey)
	{
		try {
			return new OtrCryptoEngineImpl().getFingerprint(pubKey);
		}
		catch (OtrCryptoException e) {
			e.printStackTrace();
			return null;
		}
	}

	public String getLocalFingerprint(AccountID account)
	{
		KeyPair keyPair = loadKeyPair(account);
		if (keyPair == null)
			return null;

		PublicKey pubKey = keyPair.getPublic();
		try {
			return new OtrCryptoEngineImpl().getFingerprint(pubKey);
		}
		catch (OtrCryptoException e) {
			e.printStackTrace();
			return null;
		}
	}

	public byte[] getLocalFingerprintRaw(AccountID account)
	{
		KeyPair keyPair = loadKeyPair(account);
		if (keyPair == null)
			return null;

		PublicKey pubKey = keyPair.getPublic();
		try {
			return new OtrCryptoEngineImpl().getFingerprintRaw(pubKey);
		}
		catch (OtrCryptoException e) {
			e.printStackTrace();
			return null;
		}
	}

	public void saveFingerprint(Contact contact, String fingerprint)
	{
		if (contact == null)
			return;

		this.configurator.appendProperty(contact.getAddress() + FINGER_PRINT, fingerprint);
		this.configurator.setProperty(contact.getAddress() + "." + fingerprint + FP_VERIFIED,
				false);
	}

	public KeyPair loadKeyPair(AccountID account)
	{
		if (account == null)
			return null;

		String accountID = account.getAccountUid();
		// Load Private Key.
		byte[] b64PrivKey = this.configurator.getPropertyBytes(accountID + PRIVATE_KEY);
		if (b64PrivKey == null)
			return null;

		PKCS8EncodedKeySpec privateKeySpec = new PKCS8EncodedKeySpec(b64PrivKey);

		// Load Public Key.
		byte[] b64PubKey = this.configurator.getPropertyBytes(accountID + PUBLIC_KEY);
		if (b64PubKey == null)
			return null;

		X509EncodedKeySpec publicKeySpec = new X509EncodedKeySpec(b64PubKey);
		PublicKey publicKey;
		PrivateKey privateKey;

		// Generate KeyPair.
		KeyFactory keyFactory;
		try {
			keyFactory = KeyFactory.getInstance("DSA");
			publicKey = keyFactory.generatePublic(publicKeySpec);
			privateKey = keyFactory.generatePrivate(privateKeySpec);
		}
		catch (NoSuchAlgorithmException e) {
			e.printStackTrace();
			return null;
		}
		catch (InvalidKeySpecException e) {
			e.printStackTrace();
			return null;
		}
		return new KeyPair(publicKey, privateKey);
	}

	public void generateKeyPair(AccountID account)
	{
		if (account == null)
			return;

		String accountID = account.getAccountUid();
		KeyPair keyPair;
		try {
			keyPair = KeyPairGenerator.getInstance("DSA").genKeyPair();
		}
		catch (NoSuchAlgorithmException e) {
			e.printStackTrace();
			return;
		}

		// Store Public Key.
		PublicKey pubKey = keyPair.getPublic();
		X509EncodedKeySpec x509EncodedKeySpec = new X509EncodedKeySpec(pubKey.getEncoded());
		this.configurator.setProperty(accountID + PUBLIC_KEY, x509EncodedKeySpec.getEncoded());

		// Store Private Key.
		PrivateKey privKey = keyPair.getPrivate();
		PKCS8EncodedKeySpec pkcs8EncodedKeySpec = new PKCS8EncodedKeySpec(privKey.getEncoded());

		this.configurator.setProperty(accountID + PRIVATE_KEY, pkcs8EncodedKeySpec.getEncoded());
	}
}
