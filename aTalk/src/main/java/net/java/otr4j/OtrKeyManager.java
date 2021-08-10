package net.java.otr4j;

import net.java.otr4j.session.SessionID;

import java.security.*;
/**
 * @author George Politis
 */
public interface OtrKeyManager {

	void addListener(OtrKeyManagerListener l);

	void removeListener(OtrKeyManagerListener l);

	void verify(SessionID sessionID);

	void unverify(SessionID sessionID);

	boolean isVerified(SessionID sessionID);

	String getRemoteFingerprint(SessionID sessionID);

	String getLocalFingerprint(SessionID sessionID);

	byte[] getLocalFingerprintRaw(SessionID sessionID);

	void savePublicKey(SessionID sessionID, PublicKey pubKey);

	PublicKey loadRemotePublicKey(SessionID sessionID);

	KeyPair loadLocalKeyPair(SessionID sessionID);

	void generateLocalKeyPair(SessionID sessionID);
}
