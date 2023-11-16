package org.atalk.impl.neomedia.rtp.translator;

import javax.media.rtp.OutputDataStream;

/**
 * A <code>Payload</code> type that can be written to an <code>OutputDataStream</code>.
 *
 * @author George Politis
 */
public interface Payload
{
	public void writeTo(OutputDataStream stream);
}
