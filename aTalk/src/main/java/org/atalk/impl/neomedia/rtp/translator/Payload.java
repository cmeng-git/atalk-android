package org.atalk.impl.neomedia.rtp.translator;

import javax.media.rtp.*;

/**
 * A <tt>Payload</tt> type that can be written to an <tt>OutputDataStream</tt>.
 *
 * @author George Politis
 */
public interface Payload
{
	public void writeTo(OutputDataStream stream);
}
