/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.jmfext.media.protocol;

import javax.media.Buffer;
import javax.media.control.FormatControl;
import javax.media.protocol.PullBufferDataSource;
import javax.media.protocol.PullBufferStream;

/**
 * Provides a base implementation of <code>PullBufferStream</code> in order to facilitate implementers
 * by taking care of boilerplate in the most common cases.
 *
 * @author Lyubomir Marinov
 */
public abstract class AbstractPullBufferStream<T extends PullBufferDataSource>
        extends AbstractBufferStream<T> implements PullBufferStream {

    /**
     * Initializes a new <code>AbstractPullBufferStream</code> instance which is to have its
     * <code>Format</code>-related information abstracted by a specific <code>FormatControl</code>.
     *
     * @param dataSource the <code>PullBufferDataSource</code> which is creating the new instance so that it
     * becomes one of its <code>streams</code>
     * @param formatControl the <code>FormatControl</code> which is to abstract the <code>Format</code>-related
     * information of the new instance
     */
    protected AbstractPullBufferStream(T dataSource, FormatControl formatControl) {
        super(dataSource, formatControl);
    }

    /**
     * Determines if {@link #read(Buffer)} will block.
     *
     * @return <code>true</code> if read block, <code>false</code> otherwise
     */
    public boolean willReadBlock() {
        return true;
    }
}
