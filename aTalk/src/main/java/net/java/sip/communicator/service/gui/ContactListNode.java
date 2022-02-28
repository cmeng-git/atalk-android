/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.gui;

/**
 * The <code>ContactListNode</code> represents a node in the contact list data
 * model. An implementation of this interface should be able to determine the
 * index of this node in its contact source.
 *
 * @author Yana Stamcheva
 */
public interface ContactListNode
{
    /**
     * Returns the index of this node in the <code>MetaContactListService</code>.
     * @return the index of this node in the <code>MetaContactListService</code>
     */
    public int getSourceIndex();
}
