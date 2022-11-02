/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.rtp.translator;

import javax.media.rtp.OutputDataStream;

/**
 * Describes an <code>OutputDataStream</code> associated with an endpoint to which an
 * <code>RTPTranslatorImpl</code> is translating.
 *
 * @author Lyubomir Marinov
 */
class OutputDataStreamDesc
{
    /**
     * The endpoint <code>RTPConnector</code> which owns {@link #stream}.
     */
    public final RTPConnectorDesc connectorDesc;

    /**
     * The <code>OutputDataStream</code> associated with an endpoint to which an
     * <code>RTPTranslatorImpl</code> is translating.
     */
    public final OutputDataStream stream;

    /**
     * Initializes a new <code>OutputDataStreamDesc</code> instance which is to describe an endpoint
     * <code>OutputDataStream</code> for an <code>RTPTranslatorImpl</code>.
     *
     * @param connectorDesc the endpoint <code>RTPConnector</code> which own the specified <code>stream</code>
     * @param stream the endpoint <code>OutputDataStream</code> to be described by the new instance for an
     * <code>RTPTranslatorImpl</code>
     */
    public OutputDataStreamDesc(RTPConnectorDesc connectorDesc, OutputDataStream stream)
    {
        this.connectorDesc = connectorDesc;
        this.stream = stream;
    }
}
