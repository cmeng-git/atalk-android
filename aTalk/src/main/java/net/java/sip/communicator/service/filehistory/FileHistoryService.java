/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.filehistory;

import net.java.sip.communicator.service.contactlist.MetaContact;

/**
 * File History Service stores info for file transfers from various protocols.
 *
 * @author Damian Minkov
 */
public interface FileHistoryService
{
    /**
     * Permanently removes all locally stored file history.
     */
    void eraseLocallyStoredHistory();

    /**
     * Permanently removes locally stored file history for the metaContact.
     */
    void eraseLocallyStoredHistory(MetaContact contact);

}
