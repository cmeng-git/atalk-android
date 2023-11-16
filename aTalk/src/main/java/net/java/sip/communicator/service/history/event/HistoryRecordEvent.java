/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.history.event;

import net.java.sip.communicator.service.history.HistoryQuery;
import net.java.sip.communicator.service.history.records.HistoryRecord;

import java.util.EventObject;

/**
 * The <code>HistoryRecordEvent</code> indicates that a <code>HistoryRecord</code>s has been received as a result of a
 * <code>HistoryQuery</code>.
 *
 * @author Yana Stamcheva
 */
public class HistoryRecordEvent extends EventObject
{
	/**
	 * Serial version UID.
	 */
	private static final long serialVersionUID = 0L;

	/**
	 * The <code>HistoryRecord</code> this event is about.
	 */
	private final HistoryRecord historyRecord;

	/**
	 * Creates a <code>HistoryRecordEvent</code> by specifying the initial query and the record this event is about.
	 * 
	 * @param query
	 *        the source that triggered this event
	 * @param historyRecord
	 *        the <code>HistoryRecord</code> this event is about
	 */
	public HistoryRecordEvent(HistoryQuery query, HistoryRecord historyRecord) {
		super(query);

		this.historyRecord = historyRecord;
	}

	/**
	 * Returns the <code>HistoryQuery</code> that triggered this event.
	 * 
	 * @return the <code>HistoryQuery</code> that triggered this event
	 */
	public HistoryQuery getQuerySource()
	{
		return (HistoryQuery) source;
	}

	/**
	 * Returns the <code>HistoryRecord</code>s this event is about.
	 * 
	 * @return the <code>HistoryRecord</code>s this event is about
	 */
	public HistoryRecord getHistoryRecord()
	{
		return historyRecord;
	}
}
