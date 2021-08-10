/*
 * otr4j, the open source java otr library.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package net.java.otr4j;

/**
 * @author George Politis
 * @author Eng Chong Meng
 */
public interface OtrPolicy
{
	int ALLOW_V1 = 0x01;
	int ALLOW_V2 = 0x02;
	int ALLOW_V3 = 0x40; // ALLOW_V3 is set to 0x40 for compatibility with older versions
	int REQUIRE_ENCRYPTION = 0x04;
	int SEND_WHITESPACE_TAG = 0x08;
	int WHITESPACE_START_AKE = 0x10;
	int ERROR_START_AKE = 0x20;
	int VERSION_MASK = (ALLOW_V1 | ALLOW_V2 | ALLOW_V3);

	// The four old version 1 policies correspond to the following combinations of flags (adding
	// an allowance for version 2 of the protocol):

	int NEVER = 0x00;
	int OPPORTUNISTIC = (ALLOW_V1 | ALLOW_V2 | ALLOW_V3
			| SEND_WHITESPACE_TAG | WHITESPACE_START_AKE | ERROR_START_AKE);
	int OTRL_POLICY_MANUAL = (ALLOW_V1 | ALLOW_V2 | ALLOW_V3);
	int OTRL_POLICY_ALWAYS = (ALLOW_V1 | ALLOW_V2 | ALLOW_V3
			| REQUIRE_ENCRYPTION | WHITESPACE_START_AKE | ERROR_START_AKE);
	int OTRL_POLICY_DEFAULT = OPPORTUNISTIC;

	boolean getAllowV1();

	boolean getAllowV2();

	boolean getAllowV3();

	boolean getRequireEncryption();

	boolean getSendWhitespaceTag();

	boolean getWhitespaceStartAKE();

	boolean getErrorStartAKE();

	int getPolicy();

	void setAllowV1(boolean value);

	void setAllowV2(boolean value);

	void setAllowV3(boolean value);

	void setRequireEncryption(boolean value);

	void setSendWhitespaceTag(boolean value);

	void setWhitespaceStartAKE(boolean value);

	void setErrorStartAKE(boolean value);

	void setEnableAlways(boolean value);

	boolean getEnableAlways();

	void setEnableManual(boolean value);

	boolean getEnableManual();
}
