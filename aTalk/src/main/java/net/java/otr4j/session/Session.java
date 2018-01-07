package net.java.otr4j.session;

import net.java.otr4j.*;
import net.java.otr4j.io.messages.AbstractMessage;

import java.math.BigInteger;
import java.security.*;
import java.util.List;

public interface Session
{
	interface OTRv
	{
		int ONE = 1;
		int TWO = 2;
		int THREE = 3;
	}

	SessionStatus getSessionStatus();

	SessionID getSessionID();

	void injectMessage(AbstractMessage m)
			throws OtrException;

	KeyPair getLocalKeyPair()
			throws OtrException;

	OtrPolicy getSessionPolicy();

	String transformReceiving(String content)
			throws OtrException;

	String[] transformSending(String content, List<TLV> tlvs)
			throws OtrException;

	String[] transformSending(String content)
			throws OtrException;

	void startSession()
			throws OtrException;

	void endSession()
			throws OtrException;

	void refreshSession()
			throws OtrException;

	PublicKey getRemotePublicKey();

	void addOtrEngineListener(OtrEngineListener l);

	void removeOtrEngineListener(OtrEngineListener l);

	void initSmp(String question, String secret)
			throws OtrException;

	void respondSmp(String question, String secret)
			throws OtrException;

	void abortSmp()
			throws OtrException;

	boolean isSmpInProgress();

	BigInteger getS();

	// OTRv3 methods
	List<Session> getInstances();

	Session getOutgoingInstance();

	boolean setOutgoingInstance(InstanceTag tag);

	InstanceTag getSenderInstanceTag();

	InstanceTag getReceiverInstanceTag();

	void setReceiverInstanceTag(InstanceTag tag);

	void setProtocolVersion(int protocolVersion);

	int getProtocolVersion();

	void respondSmp(InstanceTag receiverTag, String question, String secret)
			throws OtrException;

	SessionStatus getSessionStatus(InstanceTag tag);

	PublicKey getRemotePublicKey(InstanceTag tag);
}