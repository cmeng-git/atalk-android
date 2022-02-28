/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.history.event;

/**
 * The <code>HistoryQueryListener</code> listens for changes in the result of a given <code>HistoryQuery</code>. When a query to
 * the history is started, this listener would be notified every time new results are available for this query.
 *
 * @author Yana Stamcheva
 */
public interface HistoryQueryListener
{
	/**
	 * Indicates that new <code>HistoryRecord</code> has been received as a result of the query.
	 * 
	 * @param event
	 *        the <code>HistoryRecordEvent</code> containing information about the query results.
	 */
	public void historyRecordReceived(HistoryRecordEvent event);

	/**
	 * Indicates that the status of the history has changed.
	 * 
	 * @param event
	 *        the <code>HistoryQueryStatusEvent</code> containing information about the status change
	 */
	public void queryStatusChanged(HistoryQueryStatusEvent event);
}
