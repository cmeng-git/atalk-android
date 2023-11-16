/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.history;

import net.java.sip.communicator.service.history.records.HistoryRecordStructure;

/**
 * @author Alexander Pelov
 * @author Yana Stamcheva
 */
public interface History
{
	/**
	 * Returns an object which can be used to read and query this history.
	 * 
	 * @return an object which can be used to read and query this history
	 */
	HistoryReader getReader();

	/**
	 * Returns an object that can be used to read and query this history. The <code>InteractiveHistoryReader</code> differs
	 * from the <code>HistoryReader</code> in the way it manages query results. It allows to cancel a search at any time and
	 * to track history results through a <code>HistoryQueryListener</code>.
	 * 
	 * @return an object that can be used to read and query this history
	 */
	InteractiveHistoryReader getInteractiveReader();

	/**
	 * Returns an object which can be used to append records to this history.
	 * 
	 * @return an object which can be used to append records to this history
	 */
	HistoryWriter getWriter();

	/**
	 * @return Returns the ID of this history.
	 */
	HistoryID getID();

	/**
	 * @return Returns the structure of the history records in this history.
	 */
	HistoryRecordStructure getHistoryRecordsStructure();

	/**
	 * Sets the given <code>structure</code> to be the new history records structure used in this history implementation.
	 * 
	 * @param structure
	 *        the new <code>HistoryRecordStructure</code> to use
	 */
	void setHistoryRecordsStructure(HistoryRecordStructure structure);
}
