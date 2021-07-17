/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package net.java.sip.communicator.plugin.otr;

/**
 * Extends otr4j's <tt>SessionStatus</tt> with two additional states.
 *
 * @author Marin Dzhigarov
 * @author Eng Chong Meng
 */
public enum ScSessionStatus {
    /*
     * A Session in PLAINTEXT state after OTR session has ended.
     */
	PLAINTEXT,
    /*
     * A Session in OTR Encrypted state after OTR session setup successfully.
     */
	ENCRYPTED,
    /*
     * A Session transitions in FINISHED state right after remote user terminate the OTR session.
     */
	FINISHED,
	/*
	 * A Session transitions in LOADING state right before Session.startSession() is invoked.
	 */
	LOADING,
	/*
	 * A Session transitions in TIMED_OUT state after being in LOADING state for a long period of time.
	 */
	TIMED_OUT
}
