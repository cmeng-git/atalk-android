/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.impl.neomedia.jmfext.media.protocol.mediarecorder;

import android.hardware.Camera;
import android.media.MediaRecorder;
import android.net.LocalServerSocket;
import android.net.LocalSocket;
import android.os.Process;
import android.view.Surface;

import org.atalk.android.aTalkApp;
import org.atalk.android.util.java.awt.Dimension;
import org.atalk.impl.neomedia.codec.FFmpeg;
import org.atalk.impl.neomedia.codec.video.h264.H264;
import org.atalk.impl.neomedia.device.util.AndroidCamera;
import org.atalk.impl.neomedia.device.util.CameraUtils;
import org.atalk.impl.neomedia.jmfext.media.protocol.AbstractPushBufferCaptureDevice;
import org.atalk.impl.neomedia.jmfext.media.protocol.AbstractPushBufferStream;
import org.atalk.persistance.FileBackend;
import org.atalk.service.neomedia.codec.Constants;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

import javax.media.*;
import javax.media.control.FormatControl;
import javax.media.format.VideoFormat;
import javax.media.protocol.BufferTransferHandler;
import javax.media.protocol.PushBufferStream;

import timber.log.Timber;

/**
 * Implements <tt>PushBufferDataSource</tt> and <tt>CaptureDevice</tt> using Android's <tt>MediaRecorder</tt>.
 *
 * @author Lyubomir Marinov
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
public class DataSource extends AbstractPushBufferCaptureDevice
{
    /**
     * The path of the file into which the bytes read from {@link #mediaRecorder} are to be dumped.
     * If the value is not <tt>null</tt>, the bytes in question will only be dumped to the specified
     * file and will not be made available through the <tt>DataSource</tt>.
     */
    private static final String DUMP_FILE = null;

    private static final String EOS_IOE_MESSAGE = "END_OF_STREAM";

    private static final long FREE_SPACE_BOX_TYPE = stringToBoxType("free");

    private static final long FILE_TYPE_BOX_TYPE = stringToBoxType("ftyp");

    private static final String INTEGER_OVERFLOW_IOE_MESSAGE = "INTEGER_OVERFLOW";

    /**
     * The name of the <tt>LocalServerSocket</tt> created by the <tt>DataSource</tt> class to be
     * utilized by the <tt>MediaRecorder</tt>s which implement the actual capturing of the media
     * data for the purposes of the <tt>DataSource</tt> instances.
     */
    private static final String LOCAL_SERVER_SOCKET_NAME = DataSource.class.getName() + ".localServerSocket";

    private static final long PARAMETER_SET_INTERVAL = 750;

    /**
     * The maximum size of a NAL unit. RFC 6184 &quot;RTP Payload Format for H.264 Video&quot;
     * states: [t]he maximum size of a NAL unit encapsulated in any aggregation packet is 65535 bytes.
     */
    private static final long MAX_NAL_LENGTH = 65535;

    private static final long MEDIA_DATA_BOX_TYPE = stringToBoxType("mdat");

    private static final int MEDIA_RECORDER_STOPPING = 1;

    private static final int MEDIA_RECORDER_STOPPED = 2;

    private static final String STREAM_CLOSED_IOE_MESSAGE = "STREAM_CLOSED";

    /**
     * The priority to be set to the thread executing the {@link MediaRecorderStream#read(Buffer)}
     * method of a given <tt>MediaRecorderStream</tt>.
     */
    private static final int THREAD_PRIORITY = Process.THREAD_PRIORITY_URGENT_DISPLAY;

    private static final String UNEXPECTED_IOEXCEPTION_MESSAGE = "UNEXPECTED";

    private static final String UNSUPPORTED_BOXSIZE_IOE_MESSAGE = "UNSUPPORTED_BOX_SIZE";

    private static final Map<String, DataSource> dataSources = new HashMap<>();

    /**
     * The path of the file into which {@link #mediaRecorder} is to write. If the value is not <tt>null</tt>, no
     * bytes will be read from the <tt>mediaRecorder</tt> by the <tt>DataSource</tt> and made available through it.
     */
    private String mOutputFile = null;

    private static LocalServerSocket mLocalServerSocket;

    private static int maxDataSourceKeySize;

    private static long nextDataSourceKey = 0;

    /**
     * The system time stamp in nanoseconds of the access unit of {@link #nal}.
     */
    private long accessUnitTimeStamp;

    private final String mDataSourceKey;

    private long lastWrittenParameterSetTime;

    private LocalSocket mLocalSocket;

    private String mLocalSocketKey;

    private int maxLocalSocketKeySize;

    protected static int mCameraId;

    private Camera mCamera;
    private VideoFormat mVideoFormat = null;

    /**
     * The <tt>MediaRecorder</tt> which implements the actual capturing of media data for this <tt>DataSource</tt>.
     */
    private MediaRecorder mediaRecorder;

    private byte[] nal;

    /**
     * The <tt>Buffer</tt> flags to be applied when {@link #nal} is read out of the associated <tt>MediaRecorderStream</tt>.
     */
    private int nalFlags;

    /**
     * The number of {@link #nal} elements containing (valid) NAL unit data.
     */
    private int nalLength;

    /**
     * The <tt>Object</tt> to synchronize the access to {@link #nal}, {@link #nalLength}, etc.
     */
    private final Object nalSyncRoot = new Object();

    private long nextLocalSocketKey = 0;

    /**
     * The <tt>nal_unit_type</tt> of the NAL unit preceding {@link #nal}.
     */
    private int prevNALUnitType = 0;

    /**
     * The interval of time in nanoseconds between two consecutive video frames produced by this
     * <tt>DataSource</tt> with which the time stamps of <tt>Buffer</tt>s are to be increased.
     */
    private long videoFrameInterval;

    /**
     * The picture and sequence parameter set for video.
     */
    private H264Parameters h264Params;

    /**
     * Initializes a new <tt>DataSource</tt> instance.
     */
    public DataSource()
    {
        mDataSourceKey = getNextDataSourceKey();
    }

    /**
     * Initializes a new <tt>DataSource</tt> from a specific <tt>MediaLocator</tt>.
     *
     * @param locator the <tt>MediaLocator</tt> to create the new instance from
     */
    public DataSource(MediaLocator locator)
    {
        super(locator);
        mDataSourceKey = getNextDataSourceKey();
    }

    /**
     * Create a new <tt>PushBufferStream</tt> which is to be at a specific zero-based index in the
     * list of streams of this <tt>PushBufferDataSource</tt>. The <tt>Format</tt>-related
     * information of the new instance is to be abstracted by a specific <tt>FormatControl</tt>.
     *
     * @param streamIndex the zero-based index of the <tt>PushBufferStream</tt> in the list of streams of this
     * <tt>PushBufferDataSource</tt>
     * @param formatControl the <tt>FormatControl</tt> which is to abstract the <tt>Format</tt>-related
     * information of the new instance
     * @return a new <tt>PushBufferStream</tt> which is to be at the specified <tt>streamIndex</tt>
     * in the list of streams of this <tt>PushBufferDataSource</tt> and which has its
     * <tt>Format</tt>-related information abstracted by the specified <tt>formatControl</tt>
     * @see AbstractPushBufferCaptureDevice#createStream(int, FormatControl)
     */
    protected AbstractPushBufferStream createStream(int streamIndex, FormatControl formatControl)
    {
        return new MediaRecorderStream(this, formatControl);
    }

    private void discard(InputStream inputStream, long byteCount)
            throws IOException
    {
        while (byteCount-- > 0)
            if (-1 == inputStream.read())
                throw new IOException(EOS_IOE_MESSAGE);
    }

    /**
     * Starts the transfer of media data from this <tt>DataSource</tt>.
     *
     * @throws IOException if anything goes wrong while starting the transfer of media data from this <tt>DataSource</tt>
     * @see AbstractPushBufferCaptureDevice#doStart()
     */
    @Override
    protected synchronized void doStart()
            throws IOException
    {
        if (mediaRecorder == null) {
            mCameraId = AndroidCamera.getCameraId(getLocator());
            mediaRecorder = new MediaRecorder();
            mCamera = null;

            try {
                // Gets the Camera instance which corresponds to this MediaLocator of this <tt>DataSource</tt>.
                mCamera = CameraUtils.getCamera(getLocator());
                if (mCamera == null) {
                    throw new RuntimeException("Unable to select camera for locator: " + getLocator());
                }

                // Adjust preview display orientation
                int rotation = CameraUtils.getPreviewOrientation(AndroidCamera.getCameraId(getLocator()));
                mCamera.setDisplayOrientation(rotation);

                Format[] streamFormats = getStreamFormats();
                // Selects video format
                for (Format candidate : streamFormats) {
                    if (Constants.H264.equalsIgnoreCase(candidate.getEncoding())) {
                        mVideoFormat = (VideoFormat) candidate;
                        break;
                    }
                }
                if (mVideoFormat == null) {
                    throw new RuntimeException("H264 not supported");
                }

                // Tries to read previously stored parameters
                h264Params = H264Parameters.getStoredParameters(mVideoFormat);
                if (h264Params == null) {
                    obtainParameters();
                }
                else {
                    startVideoRecording();
                }

            } catch (IllegalStateException | IOException ioe) {
                Timber.e(ioe, "IllegalStateException (media recorder) in configuring data source: : %s", ioe.getMessage());
                closeMediaRecorder();
            }
        }
    }

    /**
     * Must use H264/RTP for video encoder
     *
     *  After API 23, android doesn't allow non seekable file descriptors i.e. mOutputFile = null
     * org.atalk.android E/(DataSource.java:294)#startVideoRecording: IllegalStateException (media recorder) in configuring data source: : null
     *     java.lang.IllegalStateException
     *         at android.media.MediaRecorder._start(Native Method)
     *         at android.media.MediaRecorder.start(MediaRecorder.java:1340)
     *         at org.atalk.impl.neomedia.jmfext.media.protocol.mediarecorder.DataSource.startVideoRecording(DataSource.java:295)
     */
    private void startVideoRecording()
    {
        try {
            mOutputFile = null;
            // mOutputFile = new File(FileBackend.getaTalkStore(FileBackend.TMP, true), System.currentTimeMillis() + ".mp4").getAbsolutePath();

            // Configure media recorder for video recording
            configureMediaRecorder();

            if (mOutputFile == null) {
                mediaRecorder.setOutputFile(createLocalSocket());
            }
            else
                mediaRecorder.setOutputFile(mOutputFile);

            mediaRecorder.prepare();
            mediaRecorder.start(); // always cause IllegalStateException
            super.doStart();

        } catch (IllegalStateException re) {
            Timber.e(re, "IllegalStateException (media recorder) in configuring data source: : %s", re.getMessage());
            closeMediaRecorder();
        } catch (ThreadDeath td) {
            throw (ThreadDeath) td;
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Configures the camera and media recorder to work with given <tt>videoFormat</tt>.
     */
    private void configureMediaRecorder()
    {
        mCamera.unlock();
        mediaRecorder.setCamera(mCamera);

        // The sources need to be specified before setting recording-parameters or encoders, e.g. before setOutputFormat().
        mediaRecorder.setVideoSource(MediaRecorder.VideoSource.DEFAULT);
        mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);

        /*
         * Reflect the size of the VideoFormat of this DataSource on the Camera. It should not be
         * necessary because it is the responsibility of MediaRecorder to configure the Camera it is
         * provided with. Anyway, MediaRecorder.setVideoSize(int,int) is not always supported so it may
         * (or may not) turn out that Camera.Parameters.setPictureSize(int,int) saves the day in some cases.
         */
        Dimension videoSize = mVideoFormat.getSize();
//        if ((videoSize != null) && (videoSize.height > 0) && (videoSize.width > 0)) {
//            Camera.Parameters params = camera.getParameters();
//            if (params != null) {
//                params.setPictureSize(videoSize.width, videoSize.height);
//                camera.setParameters(params);
//            }
//        }

        if ((videoSize != null) && (videoSize.height > 0) && (videoSize.width > 0)) {
            Timber.w("Set video size for '%s' with %sx%s.",
                    getLocator(), videoSize.width, videoSize.height);
            mediaRecorder.setVideoSize(videoSize.width, videoSize.height);
        }

        // Setup media recorder bitrate and frame rate
        mediaRecorder.setVideoEncodingBitRate(10000000);
        float frameRate = mVideoFormat.getFrameRate();
        if (frameRate <= 0)
            frameRate = 15;

        if (frameRate > 0) {
            mediaRecorder.setVideoFrameRate((int) frameRate);
            videoFrameInterval = Math.round((1000 / frameRate) * 1000 * 1000);
            videoFrameInterval /= 2 /* ticks_per_frame */;
        }

        // Stack Overflow says that setVideoSize should be called before setVideoEncoder.
        mediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
        Surface previewSurface = CameraUtils.obtainPreviewSurface().getSurface();
        if (previewSurface == null) {
            Timber.e(new NullPointerException(), "Preview surface must not be null");
        }
        mediaRecorder.setPreviewDisplay(previewSurface);

        // Adjust preview display orientation
        int previewOrientation = CameraUtils.getPreviewOrientation(AndroidCamera.getCameraId(getLocator()));
        // cmeng - added for correct video orientation streaming
        mediaRecorder.setOrientationHint(previewOrientation);

        // Reset to recording max duration/size, as it may have been manipulated during parameters retrieval
        mediaRecorder.setMaxDuration(-1);
        mediaRecorder.setMaxFileSize(-1);
    }

    /**
     * Tries to read sequence and picture video parameters by recording sample video and parsing
     * "avcC" part of "stsd" mp4 box.
     *
     * @throws IOException if we failed to retrieve the parameters.
     */
    private void obtainParameters()
            throws IOException
    {
        final String sampleFile = aTalkApp.getGlobalContext().getCacheDir().getPath() + "/atalk-test.mpeg4";
        File outFile = new File(sampleFile);
        Timber.d("Obtaining H264Parameters from short sample video file: %s", sampleFile);

        // Configure media recorder for obtainParameters
        configureMediaRecorder();
        mediaRecorder.setOutputFile(sampleFile);

        // Limit recording time to 1 sec and max file to 1MB
        mediaRecorder.setMaxDuration(1000);
        mediaRecorder.setMaxFileSize(1024 * 1024);

        // Wait until one of limits is reached; must not use limitMonitor = new Object() => limitMonitor.notifyAll();;
        mediaRecorder.setOnInfoListener((mr, what, extra) -> {
            if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED
                    || what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_FILESIZE_REACHED) {
                Timber.d("Limit monitor notified: %s", what);

                // Disable the callback
                mediaRecorder.setOnInfoListener(null);
                mediaRecorder.stop();
                mediaRecorder.reset();

                try {
                    mCamera.reconnect();
                    mCamera.stopPreview();

                    // Retrieve SPS and PPS parameters
                    H264Parameters h264Params = new H264Parameters(sampleFile);
                    H264Parameters.storeParameters(h264Params, mVideoFormat);
                    h264Params.logParameters();
                } catch (IOException e) {
                    Timber.e("H264Parameters extraction exception: %s", e.getMessage());
                }

                // Remove sample video
                if (!outFile.delete()) {
                    Timber.e("Sample file could not be removed");
                }

                // Start final mediaRecord after obtainParameters()
                startVideoRecording();
            }
        });

        mediaRecorder.prepare();
        mediaRecorder.start();
    }

    /**
     * Stops the transfer of media data from this <tt>DataSource</tt>.
     *
     * @throws java.io.IOException if anything goes wrong while stopping the transfer of media data from this <tt>DataSource</tt>
     * @see AbstractPushBufferCaptureDevice#doStop()
     */
    @Override
    protected synchronized void doStop()
            throws IOException
    {
        super.doStop();

        /*
         * We will schedule stop and release on the mediaRecorder, close the localSocket while stop
         * and release on the mediaRecorder is starting or executing, wait for stop and release on
         * the mediaRecorder to complete, and release the camera.
         */

        int[] mediaRecorderStopState = null;
        try {
            if (mediaRecorder != null) {
                try {
                    mediaRecorderStopState = stop(mediaRecorder);
                } finally {
                    mediaRecorder = null;
                }
            }
        } finally {
            if (mLocalSocket != null) {
                try {
                    mLocalSocket.close();
                } catch (IOException ioe) {
                    Timber.w(ioe, "Failed to close LocalSocket.");
                } finally {
                    mLocalSocket = null;
                    mLocalSocketKey = null;
                }
            }

            if (mediaRecorderStopState != null) {
                boolean stopped = false;
                /*
                 * Unfortunately, MediaRecorder may never stop and/or release. So we will not wait forever.
                 */
                int maxWaits = -1;
                boolean interrupted = false;

                while (!stopped) {
                    synchronized (mediaRecorderStopState) {
                        switch (mediaRecorderStopState[0]) {
                            case MEDIA_RECORDER_STOPPED:
                                stopped = true;
                                break;
                            case MEDIA_RECORDER_STOPPING:
                                if (maxWaits == -1)
                                    maxWaits = 10;
                                if (maxWaits == 0) {
                                    stopped = true;
                                    break;
                                }
                                else if (maxWaits > 0)
                                    maxWaits--;
                            default:
                                try {
                                    mediaRecorderStopState.wait(500);
                                } catch (InterruptedException ie) {
                                    interrupted = true;
                                }
                                break;
                        }
                    }
                }
                if (interrupted)
                    Thread.currentThread().interrupt();
                if (mediaRecorderStopState[0] != MEDIA_RECORDER_STOPPED) {
                    Timber.d("Stopping/releasing MediaRecorder seemed to take a long time - give up.");
                }
            }

            if (mCamera != null) {
                mCamera.reconnect();
                final Camera camera = this.mCamera;
                this.mCamera = null;
                CameraUtils.releaseCamera(camera);
            }
        }
    }

    /**
     * Closes the current {@link Camera}.
     */
    protected void closeMediaRecorder()
    {
        if (mCamera != null) {
            try {
                mCamera.reconnect();
                mCamera.release();
                mCamera = null;
                videoFrameInterval = 0;

                if (mediaRecorder != null) {
                    mediaRecorder.stop();
                    mediaRecorder.release();
                    mediaRecorder = null;
                }
            } catch (IOException e) {
                throw new RuntimeException("Interrupted while trying to close camera.", e);
            }
        }
    }

    private Format[] getStreamFormats()
    {
        FormatControl[] formatControls = getFormatControls();
        final int count = formatControls.length;
        Format[] streamFormats = new Format[count];

        for (int i = 0; i < count; i++) {
            FormatControl formatControl = formatControls[i];
            Format format = formatControl.getFormat();

            if (format == null) {
                Format[] supportedFormats = formatControl.getSupportedFormats();

                if ((supportedFormats != null) && (supportedFormats.length > 0)) {
                    format = supportedFormats[0];
                }
            }
            streamFormats[i] = format;
        }
        return streamFormats;
    }

    @SuppressWarnings("unused")
    private static String boxTypeToString(long type)
    {
        byte[] bytes = new byte[4];
        int end = bytes.length - 1;

        for (int i = end; i >= 0; i--)
            bytes[end - i] = (byte) ((type >> (8 * i)) & 0xFF);
        return new String(bytes, StandardCharsets.US_ASCII);
    }

    private static String getNextDataSourceKey()
    {
        synchronized (DataSource.class) {
            return Long.toString(nextDataSourceKey++);
        }
    }

    private FileDescriptor createLocalSocket()
            throws IOException
    {
        LocalServerSocket localServerSocket;

        synchronized (DataSource.class) {
            if (mLocalServerSocket == null) {
                mLocalServerSocket = new LocalServerSocket(LOCAL_SERVER_SOCKET_NAME);
                Thread localServerSocketThread = new Thread()
                {
                    @Override
                    public void run()
                    {
                        runInLocalServerSocketThread();
                    }
                };

                localServerSocketThread.setDaemon(true);
                localServerSocketThread.setName(mLocalServerSocket.getLocalSocketAddress().getName());
                localServerSocketThread.start();
            }
            localServerSocket = mLocalServerSocket;
        }

        if (mLocalSocket != null) {
            try {
                mLocalSocket.close();
            } catch (IOException ioe) {
                Timber.e("IO Exception: %s", ioe.getMessage());
            }
        }
        if (mLocalSocketKey != null)
            mLocalSocketKey = null;
        mLocalSocket = new LocalSocket();
        mLocalSocketKey = getNextLocalSocketKey();

        /*
         * Since one LocalServerSocket is being used by multiple DataSource instances, make sure
         * that the LocalServerSocket will be able to determine which DataSource is to receive the
         * media data delivered though a given LocalSocket.
         */
        String dataSourceKey = mDataSourceKey;
        try {
            byte[] dataSourceKeyBytes = (dataSourceKey + "\n").getBytes(StandardCharsets.UTF_8);
            int dataSourceKeySize = dataSourceKeyBytes.length;
            byte[] localSocketKeyBytes = (mLocalSocketKey + "\n").getBytes(StandardCharsets.UTF_8);
            int localSocketKeySize = localSocketKeyBytes.length;

            synchronized (DataSource.class) {
                dataSources.put(dataSourceKey, this);
                if (maxDataSourceKeySize < dataSourceKeySize)
                    maxDataSourceKeySize = dataSourceKeySize;
            }
            if (maxLocalSocketKeySize < localSocketKeySize)
                maxLocalSocketKeySize = localSocketKeySize;
            mLocalSocket.connect(localServerSocket.getLocalSocketAddress());

            OutputStream outputStream = mLocalSocket.getOutputStream();

            outputStream.write(dataSourceKeyBytes);
            outputStream.write(localSocketKeyBytes);
        } catch (IOException ioe) {
            synchronized (DataSource.class) {
                dataSources.remove(dataSourceKey);
            }
            throw ioe;
        }
        return mLocalSocket.getFileDescriptor();
    }

    private synchronized String getNextLocalSocketKey()
    {
        return Long.toString(nextLocalSocketKey++);
    }

    private void localSocketAccepted(LocalSocket localSocket, InputStream inputStream)
    {
        OutputStream dump = null;
        try {
            /*
             * After this DataSource closes its write LocalSocket, the read LocalSocket will
             * continue to read bytes which have already been written into the write LocalSocket
             * before the closing. In order to prevent the pushing of such invalid data out of the
             * PushBufferStream of this DataSource, the read LocalSocket should identify each of its
             * pushes with the key of the write LocalSocket. Thus this DataSource will recognize and
             * discard the invalid data.
             */
            int maxLocalSocketKeySize;

            synchronized (this) {
                maxLocalSocketKeySize = this.maxLocalSocketKeySize;
            }

            String localSocketKey;

            if (maxLocalSocketKeySize > 0) {
                localSocketKey = readLine(inputStream, maxLocalSocketKeySize);
                if (localSocketKey == null)
                    throw new IOException(UNEXPECTED_IOEXCEPTION_MESSAGE);
            }
            else
                throw new IOException(UNEXPECTED_IOEXCEPTION_MESSAGE);

            /*
             * The indicator which determines whether the sequence and picture parameter sets are
             * yet to be written. Technically, we could be writing them when we see a
             * FILE_TYPE_BOX_TYPE. Unfortunately, we have experienced a racing after a reINVITE
             * between the RTCP BYE packet and the RTP packets carrying the parameter sets. Since
             * the MEDIA_DATA_BOX_TYPE comes relatively late after the FILE_TYPE_BOX_TYPE, we will
             * just write the parameter sets when we see the first piece of actual media data from
             * the MEDIA_DATA_BOX_TYPE.
             */
            boolean writeParameterSets = true;

            if (DUMP_FILE != null)
                dump = new FileOutputStream(DUMP_FILE + "." + localSocketKey);

            while (true) {
                if (dump != null) {
                    dump.write(inputStream.read());
                    continue;
                }

                long size = readUnsignedInt32(inputStream);
                long type = readUnsignedInt32(inputStream);

                if (type == FILE_TYPE_BOX_TYPE) {
                    /*
                     * Android's MPEG4Writer writes the ftyp box by initially writing a size of zero,
                     * then writing the other fields and finally overwriting the size with the correct value.
                     */
                    size = 4 /* size */
                            + 4 /* type */
                            + 4 /* major_brand */
                            + 4 /* minor_version */
                            + 4 /* compatible_brands[0] == "isom" */
                            + 4 /* compatible_brands[1] == "3gp4" */;
                    discard(inputStream, size - (4 /* size */ + 4 /* type */));
                    if (size != readUnsignedInt32(inputStream)) {
                        throw new IOException(UNEXPECTED_IOEXCEPTION_MESSAGE);
                    }
                }
                else if (type == FREE_SPACE_BOX_TYPE) {
                    /*
                     * Android's MPEG4Writer writes a free box with size equal to the estimated
                     * number of bytes of the moov box. When the MPEG4Writer is stopped, it seeks
                     * back and splits the free box into a moov box and a free box both of which fit
                     * into the initial free box.
                     */
                }
                else if (type == MEDIA_DATA_BOX_TYPE) {
                    while (true) {
                        long nalLength = readUnsignedInt32(inputStream);

                        // Some devices write ASCII ???? ???? at this point we can retry here
                        if (nalLength == 1061109567) {
                            Timber.w("Detected ???? ???? NAL length, trying to discard...");
                            // Currently read only 4(????) need 4 more
                            discard(inputStream, 4);
                            // Try to read nal length again
                            nalLength = readUnsignedInt32(inputStream);
                        }

                        if ((nalLength > 0) && (nalLength <= MAX_NAL_LENGTH)) {
                            if (writeParameterSets) {
                                writeParameterSets = false;

                                byte[] sps = h264Params.getSps();
                                byte[] pps = h264Params.getPps();
                                /*
                                 * Android's MPEG4Writer will not write the sequence and picture
                                 * parameter set until the associated MediaRecorder is stopped.
                                 */
                                readNAL(localSocketKey, sps, sps.length);
                                readNAL(localSocketKey, pps, pps.length);
                            }
                            readNAL(localSocketKey, inputStream, (int) nalLength);
                        }
                        else {
                            throw new IOException(UNEXPECTED_IOEXCEPTION_MESSAGE);
                        }
                    }
                }
                else {
                    if (size == 1) {
                        /* largesize */
                        size = readUnsignedInt64(inputStream) - 8;
                    }
                    if (size == 0) {
                        throw new IOException(UNSUPPORTED_BOXSIZE_IOE_MESSAGE);
                    }
                    else
                        discard(inputStream, size - (4 /* size */ + 4 /* type */));
                }
            }
        } catch (IllegalArgumentException | IOException iae) {
            Timber.e(iae, "Failed to read from MediaRecorder.");
        } finally {
            try {
                localSocket.close();
            } catch (IOException ioe) {
            }
            if (dump != null) {
                try {
                    dump.close();
                } catch (IOException ioe) {
                }
            }
        }
    }

    /**
     * Notifies this <tt>DataSource</tt> that a NAL unit has just been read from the associated
     * <tt>MediaRecorder</tt> into {@link #nal}.
     */
    private void nalRead()
    {
        int nal_unit_type = nal[0] & 0x1F;

        /*
         * Determine whether the access unit time stamp associated with the (current) NAL unit is to be changed.
         */
        switch (prevNALUnitType) {
            case 6 /* Supplemental enhancement information (SEI) */:
            case 7 /* Sequence parameter set */:
            case 8 /* Picture parameter set */:
            case 9 /* Access unit delimiter */:
                break;

            case 0 /* Unspecified */:
            case 1 /* Coded slice of a non-IDR picture */:
            case 5 /* Coded slice of an IDR picture */:
            default:
                accessUnitTimeStamp += videoFrameInterval;
                break;
        }

        /*
         * Determine whether the Buffer flags associated with the (current) NAL unit are to be changed.
         */
        switch (nal_unit_type) {
            case 7 /* Sequence parameter set */:
            case 8 /* Picture parameter set */:
                lastWrittenParameterSetTime = System.currentTimeMillis();
                /* Do fall through. */

            case 6 /* Supplemental enhancement information (SEI) */:
            case 9 /* Access unit delimiter */:
                nalFlags = 0;
                break;

            case 0 /* Unspecified */:
            case 1 /* Coded slice of a non-IDR picture */:
            case 5 /* Coded slice of an IDR picture */:
            default:
                nalFlags = Buffer.FLAG_RTP_MARKER;
                break;
        }
        prevNALUnitType = nal_unit_type;
    }

    private static String readLine(InputStream inputStream, int maxSize)
            throws IOException
    {
        int size = 0;
        int b;
        byte[] bytes = new byte[maxSize];

        while ((size < maxSize) && ((b = inputStream.read()) != -1) && (b != '\n')) {
            bytes[size] = (byte) b;
            size++;
        }
        return new String(bytes, 0, size, StandardCharsets.UTF_8);
    }

    private void readNAL(String localSocketKey, byte[] bytes, int nalLength)
            throws IOException
    {
        synchronized (this) {
            if ((mLocalSocketKey == null) || !mLocalSocketKey.equals(localSocketKey))
                throw new IOException(STREAM_CLOSED_IOE_MESSAGE);

            synchronized (nalSyncRoot) {
                if ((nal == null) || (nal.length < nalLength))
                    nal = new byte[nalLength];
                this.nalLength = 0;

                if (bytes.length < nalLength)
                    throw new IOException(EOS_IOE_MESSAGE);
                else {
                    System.arraycopy(bytes, 0, nal, 0, nalLength);
                    this.nalLength = nalLength;

                    // Notify this DataSource that a NAL unit has just been read from the MediaRecorder into #nal.
                    nalRead();
                }
            }
        }
        writeNAL();
    }

    private void readNAL(String localSocketKey, InputStream inputStream, int nalLength)
            throws IOException
    {
        byte[] delayed = null;

        synchronized (this) {
            if ((mLocalSocketKey == null) || !mLocalSocketKey.equals(localSocketKey))
                throw new IOException(STREAM_CLOSED_IOE_MESSAGE);

            synchronized (nalSyncRoot) {
                if ((nal == null) || (nal.length < nalLength))
                    nal = new byte[nalLength];
                this.nalLength = 0;

                int remainingToRead = nalLength;
                int totalRead = 0;

                while (remainingToRead > 0) {
                    int read = inputStream.read(nal, totalRead, remainingToRead);

                    if (-1 == read)
                        throw new IOException(EOS_IOE_MESSAGE);
                    else {
                        remainingToRead -= read;
                        totalRead += read;
                    }
                }

                this.nalLength = nalLength;
                if (this.nalLength > 0) {
                    int nal_unit_type = nal[0] & 0x1F;

                    switch (nal_unit_type) {
                        case 5 /* Coded slice of an IDR picture */:
                        case 6 /* Supplemental enhancement information (SEI) */:
                            long now = System.currentTimeMillis();

                            if ((now - lastWrittenParameterSetTime) > PARAMETER_SET_INTERVAL) {
                                delayed = new byte[this.nalLength];
                                System.arraycopy(nal, 0, delayed, 0, this.nalLength);
                                this.nalLength = 0;
                            }
                            break;
                    }
                }

                if (delayed == null) {
                    // Notify this DataSource that a NAL unit has just been read from the MediaRecorder into #nal.
                    nalRead();
                }
            }
        }
        if (delayed == null) {
            writeNAL();
        }
        else {
            readNAL(localSocketKey, h264Params.getSps(), h264Params.getSps().length);
            readNAL(localSocketKey, h264Params.getPps(), h264Params.getPps().length);
            readNAL(localSocketKey, delayed, delayed.length);
        }
    }

    public static long readUnsignedInt(InputStream inputStream, int byteCount)
            throws IOException
    {
        long value = 0;

        for (int i = byteCount - 1; i >= 0; i--) {
            int b = inputStream.read();

            if (-1 == b)
                throw new IOException(EOS_IOE_MESSAGE);
            else {
                if ((i == 7) && ((b & 0x80) != 0))
                    throw new IOException(INTEGER_OVERFLOW_IOE_MESSAGE);
                value |= ((b & 0xFFL) << (8 * i));
            }
        }
        return value;
    }

    private static long readUnsignedInt32(InputStream inputStream)
            throws IOException
    {
        return readUnsignedInt(inputStream, 4);
    }

    private static long readUnsignedInt64(InputStream inputStream)
            throws IOException
    {
        return readUnsignedInt(inputStream, 8);
    }

    private static void runInLocalServerSocketThread()
    {
        while (true) {
            LocalServerSocket localServerSocket;

            synchronized (DataSource.class) {
                localServerSocket = mLocalServerSocket;
            }
            if (localServerSocket == null)
                break;

            LocalSocket localSocket = null;

            try {
                localSocket = localServerSocket.accept();
            } catch (IOException ioe) {
                /*
                 * At the time of this writing, an IOException during the execution of LocalServerSocket#accept()
                 * will leave localSocket to be equal to null which will in turn break the while loop.
                 */
            }
            if (localSocket == null)
                break;

            int maxDataSourceKeySize;

            synchronized (DataSource.class) {
                maxDataSourceKeySize = DataSource.maxDataSourceKeySize;
            }

            if (maxDataSourceKeySize < 1) {
                // We are not currently expecting such a connection so ignore whoever has connected.
                try {
                    localSocket.close();
                } catch (IOException ioe) {
                    Timber.e("IO Exception: %s", ioe.getMessage());
                }
            }
            else {
                final LocalSocket finalLocalSocket = localSocket;
                final int finalMaxDataSourceKeySize = maxDataSourceKeySize;
                Thread localSocketAcceptedThread = new Thread()
                {
                    @Override
                    public void run()
                    {
                        runInLocalSocketAcceptedThread(finalLocalSocket, finalMaxDataSourceKeySize);
                    }
                };

                localSocketAcceptedThread.setDaemon(true);
                localSocketAcceptedThread.setName(DataSource.class.getName() + ".LocalSocketAcceptedThread");
                localSocketAcceptedThread.start();
            }
        }
    }

    private static void runInLocalSocketAcceptedThread(LocalSocket localSocket, int maxDataSourceKeySize)
    {
        InputStream inputStream = null;
        String dataSourceKey = null;
        boolean closeLocalSocket = true;

        try {
            inputStream = localSocket.getInputStream();
            dataSourceKey = readLine(inputStream, maxDataSourceKeySize);
        } catch (IOException ioe) {
            /*
             * The connection does not seem to be able to identify its associated DataSource so
             * ignore whoever has made that connection.
             */
        }
        if (dataSourceKey != null) {
            DataSource dataSource;

            synchronized (DataSource.class) {
                dataSource = dataSources.get(dataSourceKey);
                if (dataSource != null) {
                    /*
                     * Once the DataSource instance to receive the media data received though the
                     * LocalSocket has been determined, the association by key is no longer
                     * necessary.
                     */
                    dataSources.remove(dataSourceKey);
                }
            }
            if (dataSource != null) {
                dataSource.localSocketAccepted(localSocket, inputStream);
                closeLocalSocket = false;
            }
        }
        if (closeLocalSocket) {
            try {
                localSocket.close();
            } catch (IOException ioe) {
                /*
                 * Apart from logging, there do not seem to exist a lot of reasonable alternatives to just ignoring it.
                 */
            }
        }
    }

    /**
     * Attempts to set the <tt>Format</tt> to be reported by the <tt>FormatControl</tt> of a
     * <tt>PushBufferStream</tt> at a specific zero-based index in the list of streams of this
     * <tt>PushBufferDataSource</tt>. The <tt>PushBufferStream</tt> does not exist at the time of
     * the attempt to set its <tt>Format</tt>.
     *
     * @param streamIndex the zero-based index of the <tt>PushBufferStream</tt> the <tt>Format</tt> of which is to be set
     * @param oldValue the last-known <tt>Format</tt> for the <tt>PushBufferStream</tt> at the specified <tt>streamIndex</tt>
     * @param newValue the <tt>Format</tt> which is to be set
     * @return the <tt>Format</tt> to be reported by the <tt>FormatControl</tt> of the
     * <tt>PushBufferStream</tt> at the specified <tt>streamIndex</tt> in the list of
     * streams of this <tt>PushBufferStream</tt> or <tt>null</tt> if the attempt to set the
     * <tt>Format</tt> did not success and any last-known <tt>Format</tt> is to be left in effect
     * @see AbstractPushBufferCaptureDevice#setFormat(int, Format, Format)
     */
    @Override
    protected Format setFormat(int streamIndex, Format oldValue, Format newValue)
    {
        // This DataSource supports setFormat.
        if (newValue instanceof VideoFormat) {
            return newValue;
        }
        else
            return super.setFormat(streamIndex, oldValue, newValue);
    }

    /**
     * Sets the priority of the calling thread to {@link #THREAD_PRIORITY}.
     */
    public static void setThreadPriority()
    {
        org.atalk.impl.neomedia.jmfext.media.protocol.audiorecord.DataSource.setThreadPriority(THREAD_PRIORITY);
    }

    /**
     * Asynchronously calls {@link MediaRecorder#stop()} and {@link MediaRecorder#release()} on a
     * specific <tt>MediaRecorder</tt>. Allows initiating <tt>stop</tt> and <tt>release</tt> on the
     * specified <tt>mediaRecorder</tt> which may be slow and performing additional cleanup in the meantime.
     *
     * @param mediaRecorder the <tt>MediaRecorder</tt> to stop and release
     * @return an array with a single <tt>int</tt> element which represents the state of the stop
     * and release performed by the method. The array is signaled upon changes to its
     * element's value via {@link Object#notify()}. The value is one of
     * {@link #MEDIA_RECORDER_STOPPING} and {@link #MEDIA_RECORDER_STOPPED}.
     */
    private int[] stop(final MediaRecorder mediaRecorder)
    {
        final int[] state = new int[1];
        Thread mediaRecorderStop = new Thread("MediaRecorder.stop")
        {
            @Override
            public void run()
            {
                try {
                    synchronized (state) {
                        state[0] = MEDIA_RECORDER_STOPPING;
                        state.notify();
                    }
                    Timber.d("Stopping MediaRecorder in %s", this);
                    mediaRecorder.stop();
                    Timber.d("Releasing MediaRecorder in %s", this);
                    mediaRecorder.release();
                } catch (Throwable t) {
                    if (t instanceof ThreadDeath)
                        throw (ThreadDeath) t;
                    else {
                        Timber.d("Failed to stop and release MediaRecorder: %s", t.getMessage());
                    }
                } finally {
                    synchronized (state) {
                        state[0] = MEDIA_RECORDER_STOPPED;
                        state.notify();
                    }
                }
            }
        };
        mediaRecorderStop.setDaemon(true);
        mediaRecorderStop.start();
        return state;
    }

    private static long stringToBoxType(String str)
    {
        byte[] bytes;
        bytes = str.getBytes(StandardCharsets.US_ASCII);

        final int end = bytes.length - 1;
        long value = 0;

        for (int i = end; i >= 0; i--)
            value |= ((bytes[end - i] & 0xFFL) << (8 * i));
        return value;
    }

    /**
     * Writes the (current) {@link #nal} into the <tt>MediaRecorderStream</tt> made available by this <tt>DataSource</tt>.
     */
    private void writeNAL()
    {
        MediaRecorderStream stream;

        synchronized (getStreamSyncRoot()) {
            PushBufferStream[] streams = getStreams();

            stream = ((streams != null) && (streams.length != 0))
                    ? (MediaRecorderStream) streams[0] : null;
        }
        if (stream != null)
            stream.writeNAL();
    }

    private static class MediaRecorderStream extends AbstractPushBufferStream
    {
        public MediaRecorderStream(DataSource dataSource, FormatControl formatControl)
        {
            super(dataSource, formatControl);
        }

        public void read(Buffer buffer)
                throws IOException
        {
            DataSource dataSource = (DataSource) this.dataSource;
            int byteLength = H264.NAL_PREFIX.length + FFmpeg.FF_INPUT_BUFFER_PADDING_SIZE;
            byte[] bytes = null;
            int flags = 0;
            long timeStamp = 0;

            synchronized (dataSource.nalSyncRoot) {
                int nalLength = dataSource.nalLength;

                if (nalLength > 0) {
                    byteLength += nalLength;

                    Object data = buffer.getData();
                    if (data instanceof byte[]) {
                        bytes = (byte[]) data;
                        if (bytes.length < byteLength)
                            bytes = null;
                    }
                    else
                        bytes = null;
                    if (bytes == null) {
                        bytes = new byte[byteLength];
                        buffer.setData(bytes);
                    }

                    System.arraycopy(dataSource.nal, 0, bytes, H264.NAL_PREFIX.length, nalLength);
                    flags = dataSource.nalFlags;
                    timeStamp = dataSource.accessUnitTimeStamp;
                    dataSource.nalLength = 0;
                }
            }

            buffer.setOffset(0);
            if (bytes == null)
                buffer.setLength(0);
            else {
                System.arraycopy(H264.NAL_PREFIX, 0, bytes, 0, H264.NAL_PREFIX.length);
                Arrays.fill(bytes, byteLength - FFmpeg.FF_INPUT_BUFFER_PADDING_SIZE, byteLength, (byte) 0);

                buffer.setFlags(Buffer.FLAG_RELATIVE_TIME);
                buffer.setLength(byteLength);
                buffer.setTimeStamp(timeStamp);
            }
        }

        /**
         * Writes the (current) {@link DataSource#nal} into this <tt>MediaRecorderStream</tt> i.e.
         * forces this <tt>MediaRecorderStream</tt> to notify its associated
         * <tt>BufferTransferHandler</tt> that data is available for transfer.
         */
        void writeNAL()
        {
            BufferTransferHandler transferHandler = this.transferHandler;
            if (transferHandler != null)
                transferHandler.transferData(this);
        }
    }
}
