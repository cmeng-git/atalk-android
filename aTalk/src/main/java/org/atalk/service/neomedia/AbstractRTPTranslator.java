/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.service.neomedia;

/**
 * An abstract, base implementation of {@link RTPTranslator} which aid the implementation of the
 * interface.
 *
 * @author Lyubomir Marinov
 */
public abstract class AbstractRTPTranslator implements RTPTranslator
{
	/**
	 * An empty array with element type <code>WriteFilter</code>. Explicitly defined in order to reduce
	 * unnecessary allocations and the consequent effects of the garbage collector.
	 */
	private static final WriteFilter[] NO_WRITE_FILTERS = new WriteFilter[0];

	/**
	 * The <code>WriteFilter</code>s added to this <code>RTPTranslator</code>.
	 */
	private WriteFilter[] writeFilters = NO_WRITE_FILTERS;

	/**
	 * The <code>Object</code> which synchronizes the access to {@link #writeFilters}.
	 */
	private final Object writeFiltersSyncRoot = new Object();

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void addWriteFilter(WriteFilter writeFilter)
	{
		if (writeFilter == null)
			throw new NullPointerException("writeFilter");

		synchronized (writeFiltersSyncRoot) {
			for (WriteFilter wf : writeFilters) {
				if (wf.equals(writeFilter))
					return;
			}

			WriteFilter[] newWriteFilters = new WriteFilter[writeFilters.length + 1];

			if (writeFilters.length != 0) {
				System.arraycopy(writeFilters, 0, newWriteFilters, 0, writeFilters.length);
			}
			newWriteFilters[writeFilters.length] = writeFilter;
			writeFilters = newWriteFilters;
		}
	}

	/**
	 * Gets the <code>WriteFilter</code>s added to this <code>RTPTranslator</code>.
	 *
	 * @return the <code>WriteFilter</code>s added to this <code>RTPTranslator</code>
	 */
	protected WriteFilter[] getWriteFilters()
	{
		synchronized (writeFiltersSyncRoot) {
			return (writeFilters.length == 0) ? NO_WRITE_FILTERS : writeFilters.clone();
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void removeWriteFilter(WriteFilter writeFilter)
	{
		if (writeFilter != null) {
			synchronized (writeFiltersSyncRoot) {
				for (int i = 0; i < writeFilters.length; ++i) {
					if (writeFilters[i].equals(writeFilter)) {
						WriteFilter[] newWriteFilters;

						if (writeFilters.length == 1) {
							newWriteFilters = NO_WRITE_FILTERS;
						}
						else {
							int newWriteFiltersLength = writeFilters.length - 1;

							newWriteFilters = new WriteFilter[newWriteFiltersLength];
							if (i != 0) {
								System.arraycopy(writeFilters, 0, newWriteFilters, 0, i);
							}
							if (i != newWriteFiltersLength) {
								System.arraycopy(writeFilters, i + 1, newWriteFilters, i,
									newWriteFiltersLength - i);
							}
						}
						writeFilters = newWriteFilters;
						break;
					}
				}
			}
		}
	}

	/**
	 * Notifies this <code>RTPTranslator</code> that a <code>buffer</code> from a <code>source</code> will be
	 * written into a <code>destination</code>.
	 *
	 * @param source
	 *        the source of <code>buffer</code>
	 * @param pkt
	 *        the packet from <code>source</code> which is to be written into <code>destination</code>
	 * @param destination
	 *        the destination into which <code>buffer</code> is to be written
	 * @param data
	 *        <code>true</code> for data/RTP or <code>false</code> for control/RTCP
	 * @return <code>true</code> if the writing is to continue or <code>false</code> if the writing is to
	 *         abort
	 */
    protected boolean willWrite(MediaStream source, RawPacket pkt,
		MediaStream destination, boolean data)
	{
		WriteFilter writeFilter = null;
		WriteFilter[] writeFilters = null;
		boolean accept = true;

		synchronized (writeFiltersSyncRoot) {
			if (this.writeFilters.length != 0) {
				if (this.writeFilters.length == 1)
					writeFilter = this.writeFilters[0];
				else
					writeFilters = this.writeFilters.clone();
			}
		}
		if (writeFilter != null) {
			accept = willWrite(writeFilter, source, pkt, destination, data);
		}
		else if (writeFilters != null) {
			for (WriteFilter wf : writeFilters) {
				accept = willWrite(wf, source, pkt, destination, data);
				if (!accept)
					break;
			}
		}
		return accept;
	}

	/**
	 * Invokes a specific <code>WriteFilter</code>.
	 *
	 * @param source
	 *        the source of <code>buffer</code>
	 * @param pkt
	 *        the packet from <code>source</code> which is to be written into <code>destination</code>
	 * @param destination
	 *        the destination into which <code>buffer</code> is to be written
	 * @param data
	 *        <code>true</code> for data/RTP or <code>false</code> for control/RTCP
	 * @return <code>true</code> if the writing is to continue or <code>false</code> if the writing is to
	 *         abort
	 */
	protected boolean willWrite(WriteFilter writeFilter, MediaStream source,
	        RawPacket pkt, MediaStream destination, boolean data)
	{
		boolean accept;

		try {
			accept = writeFilter.accept(source, pkt, destination, data);
		}
		catch (Throwable t) {
			accept = true;
			if (t instanceof InterruptedException)
				Thread.currentThread().interrupt();
			else if (t instanceof ThreadDeath)
				throw (ThreadDeath) t;
		}
		return accept;
	}
}
