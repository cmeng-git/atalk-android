/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.rtp.translator;

import org.atalk.impl.neomedia.rtp.StreamRTPManager;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;

import javax.media.protocol.DataSource;
import javax.media.rtp.SendStream;

/**
 * Describes a <code>SendStream</code> created by the <code>RTPManager</code> of an
 * <code>RTPTranslatorImpl</code>. Contains information about the <code>DataSource</code> and its stream
 * index from which the <code>SendStream</code> has been created so that various
 * <code>StreamRTPManager</code> receive different views of one and the same <code>SendStream</code>.
 *
 * @author Lyubomir Marinov
 */
class SendStreamDesc
{
	/**
	 * The <code>DataSource</code> from which {@link #sendStream} has been created.
	 */
	public final DataSource dataSource;

	/**
	 * The <code>SendStream</code> created from the stream of {@link #dataSource} at index
	 * {@link #streamIndex}.
	 */
	public final SendStream sendStream;

	/**
	 * The list of <code>StreamRTPManager</code>-specific views to {@link #sendStream}.
	 */
	private final List<SendStreamImpl> sendStreams = new LinkedList<>();

	/**
	 * The number of <code>StreamRTPManager</code>s which have started their views of
	 * {@link #sendStream}.
	 */
	private int started;

	/**
	 * The index of the stream of {@link #dataSource} from which {@link #sendStream} has been
	 * created.
	 */
	public final int streamIndex;

	private final RTPTranslatorImpl translator;

	public SendStreamDesc(RTPTranslatorImpl translator, DataSource dataSource, int streamIndex,
		SendStream sendStream)
	{
		this.translator = translator;
		this.dataSource = dataSource;
		this.sendStream = sendStream;
		this.streamIndex = streamIndex;
	}

	void close(SendStreamImpl sendStream)
	{
		boolean close = false;

		synchronized (this) {
			if (sendStreams.contains(sendStream)) {
				sendStreams.remove(sendStream);
				close = sendStreams.isEmpty();
			}
		}
		if (close)
			translator.closeSendStream(this);
	}

	public synchronized SendStreamImpl getSendStream(StreamRTPManager streamRTPManager,
		boolean create)
	{
		for (SendStreamImpl sendStream : sendStreams)
			if (sendStream.streamRTPManager == streamRTPManager)
				return sendStream;
		if (create) {
			SendStreamImpl sendStream = new SendStreamImpl(streamRTPManager, this);

			sendStreams.add(sendStream);
			return sendStream;
		}
		else
			return null;
	}

	public synchronized int getSendStreamCount()
	{
		return sendStreams.size();
	}

	synchronized void start(SendStreamImpl sendStream)
		throws IOException
	{
		if (sendStreams.contains(sendStream)) {
			if (started < 1) {
				this.sendStream.start();
				started = 1;
			}
			else
				started++;
		}
	}

	synchronized void stop(SendStreamImpl sendStream)
		throws IOException
	{
		if (sendStreams.contains(sendStream)) {
			if (started == 1) {
				this.sendStream.stop();
				started = 0;
			}
			else if (started > 1)
				started--;
		}
	}
}
