/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.conference;

import javax.media.protocol.DataSource;

/**
 * Represents a filter which determines whether a specific <code>DataSource</code> is to be selected or
 * deselected by the caller of the filter.
 *
 * @author Lyubomir Marinov
 */
public interface DataSourceFilter
{
	/**
	 * Determines whether a specific <code>DataSource</code> is accepted by this filter i.e. whether
	 * the caller of this filter should include it in its selection.
	 *
	 * @param dataSource
	 * 		the <code>DataSource</code> to be checked whether it is accepted by this filter
	 * @return <code>true</code> if this filter accepts the specified <code>DataSource</code> i.e. if the
	 * caller of this filter should include it in its selection; otherwise, <code>false</code>
	 */
	public boolean accept(DataSource dataSource);
}
