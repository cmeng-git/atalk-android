/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.service.neomedia;

import android.content.Context;
import android.view.View;

/**
 * Declares the interface to be supported by providers of access to {@link View}s.
 *
 * @author Lyubomir Marinov
 */
public interface ViewAccessor
{
    /**
     * Gets the {@link View} provided by this instance which is to be used in a specific {@link Context}.
     *
     * @param context the <code>Context</code> in which the provided <code>View</code> will be used
     * @return the <code>View</code> provided by this instance which is to be used in a specific <code>Context</code>
     */
    public View getView(Context context);
}