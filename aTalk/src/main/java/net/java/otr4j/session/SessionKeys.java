package net.java.otr4j.session;

import net.java.otr4j.OtrException;

import java.math.BigInteger;
import java.security.KeyPair;

import javax.crypto.interfaces.DHPublicKey;

public interface SessionKeys
{
	int PREVIOUS = 0;
	int CURRENT = 1;
	int Previous = PREVIOUS;
	int Current = CURRENT;
	byte HIGH_SEND_BYTE = (byte) 0x01;
	byte HIGH_RECEIVE_BYTE = (byte) 0x02;
	byte LOW_SEND_BYTE = (byte) 0x02;
	byte LOW_RECEIVE_BYTE = (byte) 0x01;

	void setLocalPair(KeyPair keyPair, int localPairKeyID);

	void setRemoteDHPublicKey(DHPublicKey pubKey, int remoteKeyID);

	void incrementSendingCtr();

	byte[] getSendingCtr();

	byte[] getReceivingCtr();

	void setReceivingCtr(byte[] ctr);

	byte[] getSendingAESKey()
		throws OtrException;

	byte[] getReceivingAESKey()
		throws OtrException;

	byte[] getSendingMACKey()
		throws OtrException;

	byte[] getReceivingMACKey()
		throws OtrException;

	void setS(BigInteger s);

	void setIsUsedReceivingMACKey(Boolean isUsedReceivingMACKey);

	Boolean getIsUsedReceivingMACKey();

	int getLocalKeyID();

	int getRemoteKeyID();

	DHPublicKey getRemoteKey();

	KeyPair getLocalPair();

}