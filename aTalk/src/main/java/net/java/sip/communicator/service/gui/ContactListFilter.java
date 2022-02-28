/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package net.java.sip.communicator.service.gui;

/**
 * The <code>ContactListFilter</code> is an interface meant to be implemented by
 * modules interested in filtering the contact list. An implementation of this
 * interface should be able to answer if an <code>UIContact</code> or an
 * <code>UIGroup</code> is matching the corresponding filter.
 *
 * @author Yana Stamcheva
 * @author Eng Chong Meng
 */
public interface ContactListFilter
{
    /**
     * Indicates if the given <code>uiGroup</code> is matching the current filter.
     * @param uiContact the <code>UIContact</code> to check
     * @return <code>true</code> to indicate that the given <code>uiContact</code>
     * matches this filter, <code>false</code> - otherwise
     */
    boolean isMatching(UIContact uiContact);

    /**
     * Indicates if the given <code>uiGroup</code> is matching the current filter.
     * @param uiGroup the <code>UIGroup</code> to check
     * @return <code>true</code> to indicate that the given <code>uiGroup</code>
     * matches this filter, <code>false</code> - otherwise
     */
    boolean isMatching(UIGroup uiGroup);

    /**
     * Applies this filter to any interested sources
     * @param filterQuery the <code>FilterQuery</code> that tracks the results of this filtering
     */
    void applyFilter(FilterQuery filterQuery);
}
