/*
 * otr4j, the open source java otr library.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.otr4j.session;

/**
 * @author George Politis
 */
public enum SessionStatus
{

	/**
	 * This state indicates that outgoing messages are sent without encryption. This is the state
	 * that is used before an OTR conversation is initiated. This is the initial state, and the
	 * only way to subsequently enter this state is for the user to explicitly request to do so
	 * via some UI operation.
	 */
	PLAINTEXT,

	/**
	 * This state indicates that outgoing messages are sent encrypted. This is the state that is
	 * used during an OTR conversation. The only way to enter this state is for the authentication
	 * state machine to successfully complete.
	 */
	ENCRYPTED,

	/**
	 * This state indicates that outgoing messages are not delivered at all. This state is entered
	 * only when the other party indicates he has terminated his side of the OTR conversation.
	 */
	FINISHED
}
