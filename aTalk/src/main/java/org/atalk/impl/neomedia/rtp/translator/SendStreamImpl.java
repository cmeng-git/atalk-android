/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.rtp.translator;

import org.atalk.impl.neomedia.rtp.StreamRTPManager;

import java.io.IOException;
import java.lang.reflect.UndeclaredThrowableException;

import javax.media.protocol.DataSource;
import javax.media.rtp.Participant;
import javax.media.rtp.SendStream;
import javax.media.rtp.TransmissionStats;
import javax.media.rtp.rtcp.SenderReport;
import javax.media.rtp.rtcp.SourceDescription;

/**
 * Implements a <code>SendStream</code> which is an endpoint-specific view of an actual
 * <code>SendStream</code> of the <code>RTPManager</code> of an <code>RTPTranslatorImpl</code>. When the last
 * endpoint-specific view of an actual <code>SendStream</code> is closed, the actual <code>SendStream</code>
 * is closed.
 *
 * @author Lyubomir Marinov
 */
class SendStreamImpl implements SendStream
{
	private boolean closed;

	public final SendStreamDesc sendStreamDesc;

	private boolean started;

	public final StreamRTPManager streamRTPManager;

	public SendStreamImpl(StreamRTPManager streamRTPManager, SendStreamDesc sendStreamDesc)
	{
		this.sendStreamDesc = sendStreamDesc;
		this.streamRTPManager = streamRTPManager;
	}

	@Override
	public void close()
	{
		if (!closed) {
			try {
				if (started)
					stop();
			}
			catch (IOException ioe) {
				throw new UndeclaredThrowableException(ioe);
			}
			finally {
				sendStreamDesc.close(this);
				closed = true;
			}
		}
	}

	@Override
	public DataSource getDataSource()
	{
		return sendStreamDesc.sendStream.getDataSource();
	}

	@Override
	public Participant getParticipant()
	{
		return sendStreamDesc.sendStream.getParticipant();
	}

	@Override
	public SenderReport getSenderReport()
	{
		return sendStreamDesc.sendStream.getSenderReport();
	}

	@Override
	public TransmissionStats getSourceTransmissionStats()
	{
		return sendStreamDesc.sendStream.getSourceTransmissionStats();
	}

	@Override
	public long getSSRC()
	{
		return sendStreamDesc.sendStream.getSSRC();
	}

	/**
	 * Not implemented because there are currently no uses of the underlying functionality.
	 */
	@Override
	public int setBitRate(int bitRate)
	{
		// TODO Auto-generated method stub
		return 0;
	}

	/**
	 * Not implemented because there are currently no uses of the underlying functionality.
	 */
	@Override
	public void setSourceDescription(SourceDescription[] sourceDescription)
	{
		// TODO Auto-generated method stub
	}

	@Override
	public void start()
		throws IOException
	{
		if (closed) {
			throw new IOException("Cannot start SendStream after it has been closed.");
		}
		if (!started) {
			sendStreamDesc.start(this);
			started = true;
		}
	}

	@Override
	public void stop()
		throws IOException
	{
		if (!closed && started) {
			sendStreamDesc.stop(this);
			started = false;
		}
	}
}
