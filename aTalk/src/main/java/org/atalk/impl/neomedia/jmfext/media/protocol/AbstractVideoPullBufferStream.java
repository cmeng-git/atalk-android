/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.jmfext.media.protocol;

import java.io.IOException;

import javax.media.Buffer;
import javax.media.control.FormatControl;
import javax.media.control.FrameRateControl;
import javax.media.protocol.PullBufferDataSource;

/**
 * Provides a base implementation of <code>PullBufferStream</code> for video in order to facilitate
 * implementers by taking care of boilerplate in the most common cases.
 *
 * @author Lyubomir Marinov
 */
public abstract class AbstractVideoPullBufferStream<T extends PullBufferDataSource>
		extends AbstractPullBufferStream<T>
{

	/**
	 * The output frame rate of this <code>AbstractVideoPullBufferStream</code> which has been
	 * specified by {@link #frameRateControl} and depending on which
	 * {@link #minimumVideoFrameInterval} has been calculated.
	 */
	private float frameRate;

	/**
	 * The <code>FrameRateControl</code> which gets and sets the output frame rate of this
	 * <code>AbstractVideoPullBufferStream</code>.
	 */
	private FrameRateControl frameRateControl;

	/**
	 * The minimum interval in milliseconds between consecutive video frames i.e. the reverse of
	 * {@link #frameRate}.
	 */
	private long minimumVideoFrameInterval;

	/**
	 * Initializes a new <code>AbstractVideoPullBufferStream</code> instance which is to have its
	 * <code>Format</code>-related information abstracted by a specific <code>FormatControl</code>.
	 *
	 * @param dataSource
	 * 		the <code>PullBufferDataSource</code> which is creating the new instance so that it
	 * 		becomes one of its <code>streams</code>
	 * @param formatControl
	 * 		the <code>FormatControl</code> which is to abstract the <code>Format</code>-related
	 * 		information of the new instance
	 */
	protected AbstractVideoPullBufferStream(T dataSource, FormatControl formatControl)
	{
		super(dataSource, formatControl);
	}

	/**
	 * Blocks and reads into a <code>Buffer</code> from this <code>PullBufferStream</code>.
	 *
	 * @param buffer
	 * 		the <code>Buffer</code> this <code>PullBufferStream</code> is to read into
	 * @throws IOException
	 * 		if an I/O error occurs while this <code>PullBufferStream</code> reads into the specified
	 * 		<code>Buffer</code>
	 */
	protected abstract void doRead(Buffer buffer)
			throws IOException;

	/**
	 * Blocks and reads into a <code>Buffer</code> from this <code>PullBufferStream</code>.
	 *
	 * @param buffer
	 * 		the <code>Buffer</code> this <code>PullBufferStream</code> is to read into
	 * @throws IOException
	 * 		if an I/O error occurs while this <code>PullBufferStream</code> reads into the specified
	 * 		<code>Buffer</code>
	 */
	public void read(Buffer buffer)
			throws IOException
	{
		FrameRateControl frameRateControl = this.frameRateControl;
		if (frameRateControl != null) {
			float frameRate = frameRateControl.getFrameRate();
			if (frameRate > 0) {
				if (this.frameRate != frameRate) {
					minimumVideoFrameInterval = (long) (1000 / frameRate);
					this.frameRate = frameRate;
				}
				if (minimumVideoFrameInterval > 0) {
					long startTime = System.currentTimeMillis();
					doRead(buffer);
					if (!buffer.isDiscard()) {
						boolean interrupted = false;
						while (true) {
							// Sleep to respect the frame rate as much as possible.
							long sleep = minimumVideoFrameInterval
									- (System.currentTimeMillis() - startTime);
							if (sleep > 0) {
								try {
									Thread.sleep(sleep);
								}
								catch (InterruptedException ie) {
									interrupted = true;
								}
							}
							else {
								// Yield a little bit to not use all the whole CPU.
								Thread.yield();
								break;
							}
						}
						if (interrupted)
							Thread.currentThread().interrupt();
					}
					// We've executed #doRead(Buffer).
					return;
				}
			}
		}
		// If there is no frame rate to be respected, just #doRead(Buffer).
		doRead(buffer);
	}

	/**
	 * Starts the transfer of media data from this <code>AbstractBufferStream</code>.
	 *
	 * @throws IOException
	 * 		if anything goes wrong while starting the transfer of media data from this
	 * 		<code>AbstractBufferStream</code>
	 * @see AbstractBufferStream#start()
	 */
	@Override
	public void start()
			throws IOException
	{
		super.start();
		frameRateControl = (FrameRateControl) dataSource
				.getControl(FrameRateControl.class.getName());
	}

	/**
	 * Stops the transfer of media data from this <code>AbstractBufferStream</code>.
	 *
	 * @throws IOException
	 * 		if anything goes wrong while stopping the transfer of media data from this
	 * 		<code>AbstractBufferStream</code>
	 * @see AbstractBufferStream#stop()
	 */
	@Override
	public void stop()
			throws IOException
	{
		super.stop();
		frameRateControl = null;
	}
}
