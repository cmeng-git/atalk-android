/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.protocol;

import org.atalk.impl.neomedia.ProcessorUtility;
import org.atalk.impl.neomedia.control.ControlsAdapter;

import java.io.IOException;
import java.lang.reflect.UndeclaredThrowableException;

import javax.media.Format;
import javax.media.Manager;
import javax.media.NoProcessorException;
import javax.media.Processor;
import javax.media.Time;
import javax.media.control.TrackControl;
import javax.media.format.AudioFormat;
import javax.media.protocol.DataSource;
import javax.media.protocol.PullBufferDataSource;
import javax.media.protocol.PullDataSource;
import javax.media.protocol.PushBufferDataSource;
import javax.media.protocol.PushDataSource;
import javax.media.protocol.SourceStream;

/**
 * Represents a <code>DataSource</code> which transcodes the tracks of a specific input
 * <code>DataSource</code> into a specific output <code>Format</code>. The transcoding is attempted only for
 * tracks which actually support it for the specified output <code>Format</code>.
 *
 * @author Lubomir Marinov
 * @author Eng Chong Meng
 */
public class TranscodingDataSource extends DataSource
{

	/**
	 * The <code>DataSource</code> which has its tracks transcoded by this instance.
	 */
	private final DataSource inputDataSource;

	/**
	 * The <code>DataSource</code> which contains the transcoded tracks of <code>inputDataSource</code> and
	 * which is wrapped by this instance. It is the output of <code>transcodingProcessor</code>.
	 */
	private DataSource outputDataSource;

	/**
	 * The <code>Format</code> in which the tracks of <code>inputDataSource</code> are transcoded.
	 */
	private final Format outputFormat;

	/**
	 * The <code>Processor</code> which carries out the actual transcoding of the tracks of
	 * <code>inputDataSource</code>.
	 */
	private Processor transcodingProcessor;

	/**
	 * Initializes a new <code>TranscodingDataSource</code> instance to transcode the tracks of a
	 * specific <code>DataSource</code> into a specific output <code>Format</code>.
	 *
	 * @param inputDataSource
	 * 		the <code>DataSource</code> which is to have its tracks transcoded in a specific output
	 * 		<code>Format</code>
	 * @param outputFormat
	 * 		the <code>Format</code> in which the new instance is to transcode the tracks of
	 * 		<code>inputDataSource</code>
	 */
	public TranscodingDataSource(DataSource inputDataSource, Format outputFormat)
	{
		super(inputDataSource.getLocator());

		this.inputDataSource = inputDataSource;
		this.outputFormat = outputFormat;
	}

	/**
	 * Implements {@link DataSource#connect()}. Sets up the very transcoding process and just does
	 * not start it i.e. creates a <code>Processor</code> on the <code>inputDataSource</code>, sets
	 * <code>outputFormat</code> on its tracks (which support a <code>Format</code> compatible with
	 * <code>outputFormat</code>) and connects to its <code>output DataSource</code>.
	 *
	 * @throws IOException
	 * 		if creating the transcoding <code>Processor</code>, setting its <code>Format</code> or
	 * 		connecting to it fails
	 */
	@Override
	public synchronized void connect()
			throws IOException
	{
		if (outputDataSource != null)
			return;

		/*
		 * Manager#createProcessor(DataSource) requires the specified DataSource to be connected.
		 */
		inputDataSource.connect();
		Processor processor;
		try {
			processor = Manager.createProcessor(inputDataSource);
		}
		catch (NoProcessorException npex) {
			IOException ioex = new IOException();
			ioex.initCause(npex);
			throw ioex;
		}

		ProcessorUtility processorUtility = new ProcessorUtility();
		if (!processorUtility.waitForState(processor, Processor.Configured))
			throw new IOException("Couldn't configure transcoding processor.");

		TrackControl[] trackControls = processor.getTrackControls();
		if (trackControls != null)
			for (TrackControl trackControl : trackControls) {
				Format trackFormat = trackControl.getFormat();

				/*
				 * XXX We only care about AudioFormat here and we assume outputFormat is of such
				 * type because it is in our current and only use case of TranscodingDataSource
				 */
				if ((trackFormat instanceof AudioFormat) && !trackFormat.matches(outputFormat)) {
					Format[] supportedTrackFormats = trackControl.getSupportedFormats();

					if (supportedTrackFormats != null) {
						for (Format supportedTrackFormat : supportedTrackFormats) {
							if (supportedTrackFormat.matches(outputFormat)) {
								Format intersectionFormat = supportedTrackFormat
										.intersects(outputFormat);

								if (intersectionFormat != null) {
									trackControl.setFormat(intersectionFormat);
									break;
								}
							}
						}
					}
				}
			}

		if (!processorUtility.waitForState(processor, Processor.Realized))
			throw new IOException("Couldn't realize transcoding processor.");

		DataSource outputDataSource = processor.getDataOutput();
		outputDataSource.connect();

		transcodingProcessor = processor;
		this.outputDataSource = outputDataSource;
	}

	/**
	 * Implements {@link DataSource#disconnect()}. Stops and undoes the whole setup of the very
	 * transcoding process i.e. disconnects from the output <code>DataSource</code> of the
	 * transcodingProcessor and disposes of the <code>transcodingProcessor</code>.
	 */
	@Override
	public synchronized void disconnect()
	{
		if (outputDataSource == null)
			return;

		try {
			stop();
		}
		catch (IOException ioex) {
			throw new UndeclaredThrowableException(ioex);
		}

		outputDataSource.disconnect();

		transcodingProcessor.deallocate();
		transcodingProcessor.close();
		transcodingProcessor = null;

		outputDataSource = null;
	}

	/**
	 * Implements {@link DataSource#getContentType()}. Delegates to the actual output of the
	 * transcoding.
	 *
	 * @return a <code>String</code> value which describes the type of the content made available by
	 * this <code>DataSource</code>
	 */
	@Override
	public synchronized String getContentType()
	{
		return (outputDataSource == null) ? null : outputDataSource.getContentType();
	}

	/**
	 * Implements {@link DataSource#getControl(String)}. Delegates to the actual output of the
	 * transcoding.
	 *
	 * @param controlType
	 * 		a <code>String</code> value which names the type of the control to be retrieved
	 * @return an <code>Object</code> which represents the control of this instance with the specified
	 * type if such a control is available; otherwise, <code>null</code>
	 */
	@Override
	public synchronized Object getControl(String controlType)
	{
		/*
		 * The Javadoc of DataSource#getControl(String) says it's an error to call the method
		 * without being connected and by that time we should have the outputDataSource.
		 */
		return outputDataSource.getControl(controlType);
	}

	/**
	 * Implements {@link DataSource#getControls()}. Delegates to the actual output of the
	 * transcoding.
	 *
	 * @return an array of <code>Object</code>s which represent the controls available for this
	 * instance
	 */
	@Override
	public synchronized Object[] getControls()
	{
		return (outputDataSource == null)
				? ControlsAdapter.EMPTY_CONTROLS : outputDataSource.getControls();
	}

	/**
	 * Implements {@link DataSource#getDuration()}. Delegates to the actual output of the
	 * transcoding.
	 *
	 * @return a <code>Time</code> value which describes the duration of the content made available by
	 * this instance
	 */
	@Override
	public synchronized Time getDuration()
	{
		return (outputDataSource == null) ? DURATION_UNKNOWN : outputDataSource.getDuration();
	}

	/**
	 * Gets the output streams that this instance provides. Some of them may be the result of
	 * transcoding the tracks of the input <code>DataSource</code> of this instance in the output
	 * <code>Format</code> of this instance.
	 *
	 * @return an array of <code>SourceStream</code>s which represents the collection of output streams
	 * that this instance provides
	 */
	public synchronized SourceStream[] getStreams()
	{
		if (outputDataSource instanceof PushBufferDataSource)
			return ((PushBufferDataSource) outputDataSource).getStreams();
		if (outputDataSource instanceof PullBufferDataSource)
			return ((PullBufferDataSource) outputDataSource).getStreams();
		if (outputDataSource instanceof PushDataSource)
			return ((PushDataSource) outputDataSource).getStreams();
		if (outputDataSource instanceof PullDataSource)
			return ((PullDataSource) outputDataSource).getStreams();
		return new SourceStream[0];
	}

	/**
	 * Implements {@link DataSource#start()}. Starts the actual transcoding process already set up
	 * with {@link #connect()}.
	 *
	 * @throws IOException
	 * 		if starting the transcoding fails
	 */
	@Override
	public synchronized void start()
			throws IOException
	{
		/*
		 * The Javadoc of DataSource#start() says it's an error to call the method without being
		 * connected and by that time we should have the outputDataSource.
		 */
		outputDataSource.start();
		transcodingProcessor.start();
	}

	/**
	 * Implements {@link DataSource#stop()}. Stops the actual transcoding process if it has already
	 * been set up with {@link #connect()}.
	 *
	 * @throws IOException
	 * 		if stopping the transcoding fails
	 */
	@Override
	public synchronized void stop()
			throws IOException
	{
		if (outputDataSource != null) {
			transcodingProcessor.stop();
			outputDataSource.stop();
		}
	}

	/**
	 * Returns this instance's <code>Processor</code> object
	 *
	 * @return this instance's <code>Processor</code> object
	 */
	public Processor getTranscodingProcessor()
	{
		return transcodingProcessor;
	}
}
