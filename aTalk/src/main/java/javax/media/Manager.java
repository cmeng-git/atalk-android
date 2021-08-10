package javax.media;

import net.sf.fmj.media.*;
import net.sf.fmj.media.protocol.*;
import net.sf.fmj.utility.LoggerSingleton;

import org.atalk.android.plugin.timberlog.TimberLog;

import java.io.IOException;
import java.net.URL;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.media.control.TrackControl;
import javax.media.protocol.URLDataSource;
import javax.media.protocol.*;

import timber.log.Timber;

/**
 * Standard JMF class -- see <a href= "http://java.sun.com/products/java-media/jmf/2.1.1/apidocs/javax/media/Manager.html"
 * target="_blank">this class in the JMF Javadoc</a>.
 *
 * @author Ken Larson
 * @author Eng Chong Meng
 */
public final class Manager
{
    private static class BlockingRealizer implements ControllerListener
    {
        private final Controller controller;

        private volatile boolean realized = false;
        private volatile boolean busy = true;
        private volatile String cannotRealizeExceptionMessage;

        public BlockingRealizer(Controller controller)
        {
            this.controller = controller;
        }

        @Override
        public synchronized void controllerUpdate(ControllerEvent event)
        {
            if (event instanceof RealizeCompleteEvent) {
                realized = true;
                busy = false;
                notify();
            }
            else if ((event instanceof StopEvent) || (event instanceof ControllerClosedEvent)) {
                if (event instanceof StopEvent) {
                    cannotRealizeExceptionMessage = "Cannot realize: received StopEvent: " + event;
                    Timber.i("%s", cannotRealizeExceptionMessage);
                }
                else { // if (event instanceof ControllerClosedEvent)
                    cannotRealizeExceptionMessage = "Cannot realize: received ControllerClosedEvent: " + event + "; message: "
                            + ((ControllerClosedEvent) event).getMessage();
                    Timber.i("%s", cannotRealizeExceptionMessage);
                }
                realized = false;
                busy = false;
                notify();
            }
        }

        public void realize()
                throws CannotRealizeException, InterruptedException
        {
            controller.addControllerListener(this);
            controller.realize();
            while (busy) {
                try {
                    synchronized (this) {
                        wait();
                    }
                } catch (InterruptedException e) {
                    controller.removeControllerListener(this);
                    throw e;
                }
            }
            controller.removeControllerListener(this);
            if (!realized)
                throw new CannotRealizeException(cannotRealizeExceptionMessage);
        }
    }

    // Do not remove - this is used by ClasspathChecker to distinguish this version from JMF's.
    public static final String FMJ_TAG = "FMJ";

    // for strict JMF-compatibility, we are supposed to look for handlers etc starting with "media."
    // in reality, neither JMF or FMJ has any classes that match this, and this has never really been seen anywhere
    // as far as I can tell. I think the idea was that people could write their own handlers and not have to add
    // a prefix, simply by using "media". This just causes more failed reflection instantations and hits to
    // the web server if it is an applet. so for strict JMF-compatibility, this should be set to true. see also Registry.READD_JAVAX
    // TODO: we could use hints for these.
    private static final boolean USE_MEDIA_PREFIX = false;
    private static final Logger logger = LoggerSingleton.logger;

    public static final int MAX_SECURITY = 1;
    public static final int CACHING = 2;
    public static final int LIGHTWEIGHT_RENDERER = 3;
    public static final int PLUGIN_PLAYER = 4;

    public static final String UNKNOWN_CONTENT_NAME = "unknown";
    private static TimeBase systemTimeBase = new SystemTimeBase();
    private static final Map<Integer, Object> hints = new HashMap<>();

    // one thing that is (was) silly about this connect and retry loop, is that in the case of something like RTP, if
    // there is a timeout connecting to the source, it will then go on and try others. This would be in
    // the case of an IOException thrown by dataSource.connect, for example. What makes this strange is that the end result is a
    // "No player exception", when it should really rethrow the IOException. Let's try this.
    public static final boolean RETHROW_IO_EXCEPTIONS = true;

    static {
        hints.put(MAX_SECURITY, Boolean.FALSE);
        hints.put(CACHING, Boolean.TRUE);
        hints.put(LIGHTWEIGHT_RENDERER, Boolean.FALSE);
        hints.put(PLUGIN_PLAYER, Boolean.FALSE);
    }

    private static void blockingRealize(Controller controller)
            throws CannotRealizeException
    {
        try {
            new BlockingRealizer(controller).realize();
        } catch (InterruptedException e) {
            throw new CannotRealizeException("Interrupted");
        }
    }

    public static DataSource createCloneableDataSource(DataSource source)
    {
        if (source instanceof SourceCloneable)
            return source;

        if (source instanceof PushBufferDataSource) {
            if (source instanceof CaptureDevice)
                return new CloneableCaptureDevicePushBufferDataSource((PushBufferDataSource) source);
            else
                return new CloneablePushBufferDataSource((PushBufferDataSource) source);
        }
        else if (source instanceof PullBufferDataSource) {
            if (source instanceof CaptureDevice)
                return new CloneableCaptureDevicePullBufferDataSource((PullBufferDataSource) source);
            else
                return new CloneablePullBufferDataSource((PullBufferDataSource) source);
        }
        else if (source instanceof PushDataSource) {
            if (source instanceof CaptureDevice)
                return new CloneableCaptureDevicePushDataSource((PushDataSource) source);
            else
                return new CloneablePushDataSource((PushDataSource) source);
        }
        else if (source instanceof PullDataSource) {
            if (source instanceof CaptureDevice)
                return new CloneableCaptureDevicePullDataSource((PullDataSource) source);
            else
                return new CloneablePullDataSource((PullDataSource) source);
        }
        else
            throw new IllegalArgumentException("Unknown or unsupported DataSource type: " + source);
    }

    public static DataSink createDataSink(DataSource datasource, MediaLocator destLocator)
            throws NoDataSinkException
    {
        final String protocol = destLocator.getProtocol();

        for (String handlerClassName : getDataSinkClassList(protocol)) {
            try {
                final Class<?> handlerClass = Class.forName(handlerClassName);
                if (!DataSink.class.isAssignableFrom(handlerClass) && !DataSinkProxy.class.isAssignableFrom(handlerClass))
                    continue; // skip any classes that will not be matched below.

                final MediaHandler handler = (MediaHandler) handlerClass.newInstance();
                handler.setSource(datasource);
                if (handler instanceof DataSink) {
                    DataSink dataSink = (DataSink) handler;
                    dataSink.setOutputLocator(destLocator);
                    return dataSink;
                }
                else if (handler instanceof DataSinkProxy) {
                    // If the MediaHandler is a DataSinkProxy, obtain the content type of the proxy using the getContentType() method.
                    // Now obtain a list of MediaHandlers that support the protocol of the Medialocator and the content type
                    // returned by the proxy i.e. look for content-prefix.media.datasink.protocol.content-type.Handler
                    final DataSinkProxy mediaProxy = (DataSinkProxy) handler;

                    Vector<String> handlerClassList2 = getDataSinkClassList(protocol + "." + toPackageFriendly(mediaProxy.getContentType(destLocator)));
                    for (String handlerClassName2 : handlerClassList2) {
                        try {
                            final Class<?> handlerClass2 = Class.forName(handlerClassName2);
                            if (!DataSink.class.isAssignableFrom(handlerClass2))
                                continue; // skip any classes that will not be matched below.
                            final MediaHandler handler2 = (MediaHandler) handlerClass2.newInstance();
                            handler2.setSource(mediaProxy.getDataSource());
                            if (handler2 instanceof DataSink) {
                                DataSink dataSink = (DataSink) handler2;
                                dataSink.setOutputLocator(destLocator);
                                return (DataSink) handler2;
                            }

                        } catch (ClassNotFoundException e) {
                            Timber.log(TimberLog.FINER, "createDataSink %s", e.getMessage()); // no need for call stack
                        } catch (IncompatibleSourceException e) {
                            Timber.log(TimberLog.FINER, "createDataSink(%s, %s), proxy = %s: %s", datasource, destLocator,
                                    mediaProxy.getDataSource(), e.getMessage());
                            // no need for call stack
                        } catch (NoClassDefFoundError | Exception e) {
                            logger.log(Level.FINE, "" + e, e);
                        }
                    }
                }
            } catch (ClassNotFoundException e) {
                Timber.log(TimberLog.FINER, "createDataSink: %s", e.getMessage()); // no need for call stack
            } catch (IncompatibleSourceException e) {
                Timber.log(TimberLog.FINER, "createDataSink(%s, %s): %s", datasource, destLocator, e.getMessage()); // no need for call stack
            } catch (NoClassDefFoundError | Exception e) {
                logger.log(Level.FINE, "" + e, e);
            }
        }
        throw new NoDataSinkException();

    }

    // does not handle proxies.
    private static DataSink createDataSink(DataSource datasource, String protocol)
            throws NoDataSinkException
    {
        for (String handlerClassName : getDataSinkClassList(protocol)) {
            try {
                final Class<?> handlerClass = Class.forName(handlerClassName);
                if (!DataSink.class.isAssignableFrom(handlerClass))
                    continue; // skip any classes that will not be matched below.

                final MediaHandler handler = (MediaHandler) handlerClass.newInstance();
                handler.setSource(datasource);
                if (handler instanceof DataSink) {
                    return (DataSink) handler;
                }

            } catch (ClassNotFoundException e) {
                Timber.log(TimberLog.FINER, "createDataSink: %s", e.getMessage()); // no need for call stack
            } catch (IncompatibleSourceException e) {
                Timber.log(TimberLog.FINER, "createDataSink(%s, %s): %s", datasource, protocol, e.getMessage()); // no need for call stack
            } catch (NoClassDefFoundError | Exception e) {
                logger.log(Level.FINE, "" + e, e);
            }
        }
        throw new NoDataSinkException();
    }

    public static DataSource createDataSource(java.net.URL sourceURL)
            throws java.io.IOException, NoDataSourceException
    {
        return createDataSource(new MediaLocator(sourceURL));
    }

    // this method has a fundamental flaw (carried over from JMF): the DataSource may not be
    // accepted by the Handler. So createPlayer(createDataSource(MediaLocator)) is not equivalent to
    // createPlayer(MediaLocator)
    public static DataSource createDataSource(MediaLocator sourceLocator)
            throws java.io.IOException, NoDataSourceException
    {
        final String protocol = sourceLocator.getProtocol();
        for (String dataSourceClassName : getDataSourceList(protocol)) {
            try {
                final Class<?> dataSourceClass = Class.forName(dataSourceClassName);
                final DataSource dataSource = (DataSource) dataSourceClass.newInstance();
                dataSource.setLocator(sourceLocator);
                dataSource.connect();
                return dataSource;

            } catch (ClassNotFoundException e) {
                Timber.log(TimberLog.FINER, "createDataSource: %s", e.getMessage()); // no need for call stack
            } catch (IOException e) {
                logger.log(Level.FINE, "" + e, e);
                if (RETHROW_IO_EXCEPTIONS)
                    throw e;
            } catch (NoClassDefFoundError | Exception e) {
                logger.log(Level.FINE, "" + e, e);
            }
        }

        // if none found, try URLDataSource:
        final URL url;
        try {
            url = sourceLocator.getURL();
        } catch (Exception e) {
            logger.log(Level.WARNING, "" + e, e);
            throw new NoDataSourceException();
        }
        final URLDataSource dataSource = new URLDataSource(url);
        dataSource.connect();
        return dataSource;
    }

    public static DataSource createMergingDataSource(DataSource[] sources)
            throws IncompatibleSourceException
    {
        // Note: JMF does not return source[0] if sources.length == 1.
        boolean allPushBufferDataSource = true;
        boolean allPullBufferDataSource = true;
        boolean allPushDataSource = true;
        boolean allPullDataSource = true;
        boolean allCaptureDevice = true;

        for (DataSource source : sources) {
            if (!(source instanceof PushBufferDataSource))
                allPushBufferDataSource = false;
            if (!(source instanceof PullBufferDataSource))
                allPullBufferDataSource = false;
            if (!(source instanceof PushDataSource))
                allPushDataSource = false;
            if (!(source instanceof PullDataSource))
                allPullDataSource = false;
            if (!(source instanceof CaptureDevice))
                allCaptureDevice = false;
        }

        if (allPushBufferDataSource) {
            final List<PushBufferDataSource> sourcesCast = new ArrayList<>();
            for (DataSource source : sources)
                sourcesCast.add((PushBufferDataSource) source);
            if (allCaptureDevice)
                return new MergingCaptureDevicePushBufferDataSource(sourcesCast);
            else
                return new MergingPushBufferDataSource(sourcesCast);
        }
        else if (allPullBufferDataSource) {
            final List<PullBufferDataSource> sourcesCast = new ArrayList<>();
            for (DataSource source : sources)
                sourcesCast.add((PullBufferDataSource) source);
            if (allCaptureDevice)
                return new MergingCaptureDevicePullBufferDataSource(sourcesCast);
            else
                return new MergingPullBufferDataSource(sourcesCast);
        }
        else if (allPushDataSource) {
            final List<PushDataSource> sourcesCast = new ArrayList<>();
            for (DataSource source : sources)
                sourcesCast.add((PushDataSource) source);
            if (allCaptureDevice)
                return new MergingCaptureDevicePushDataSource(sourcesCast);
            else
                return new MergingPushDataSource(sourcesCast);
        }
        else if (allPullDataSource) {
            final List<PullDataSource> sourcesCast = new ArrayList<>();
            for (DataSource source : sources)
                sourcesCast.add((PullDataSource) source);
            if (allCaptureDevice)
                return new MergingCaptureDevicePullDataSource(sourcesCast);
            else
                return new MergingPullDataSource(sourcesCast);
        }
        else {
            throw new IncompatibleSourceException();
        }
    }

    public static Player createPlayer(DataSource source)
            throws java.io.IOException, NoPlayerException
    {
        try {
            return createPlayer(source, source.getContentType());
        } catch (NoPlayerException e) { // no need to log, will be logged by call to createProcessor.
        } catch (IOException e) {
            logger.log(Level.FINE, "" + e, e);
            if (RETHROW_IO_EXCEPTIONS)
                throw e;
        } catch (Exception e) {
            logger.log(Level.FINER, "" + e, e);

        }
        // TODO: this is dangerous to re-use the same source for another player.
        // this may actually cause it to re-use this source multiple times.
        return createPlayer(source, "unknown");
    }

    private static Player createPlayer(DataSource source, String contentType)
            throws java.io.IOException, NoPlayerException
    {
        final List<String> classFoundHandlersTried = new ArrayList<>();
        // only include ones where classes were found
        for (String handlerClassName : getHandlerClassList(contentType)) {
            try {
                final Class<?> handlerClass = Class.forName(handlerClassName);
                if (!Player.class.isAssignableFrom(handlerClass) && !MediaProxy.class.isAssignableFrom(handlerClass))
                    continue; // skip any classes that will not be matched below.
                final MediaHandler handler = (MediaHandler) handlerClass.newInstance();
                handler.setSource(source);
                if (handler instanceof Player) {
                    Timber.i("Using player: %s", handler.getClass().getName());
                    return (Player) handler;
                }
                else if (handler instanceof MediaProxy) {
                    final MediaProxy mediaProxy = (MediaProxy) handler;
                    return createPlayer(mediaProxy.getDataSource());
                }
                else {
                    Timber.log(TimberLog.FINER, "Not Player, and not MediaProxy: %s", handler.getClass().getName());
                    classFoundHandlersTried.add(handlerClassName);
                }
            } catch (ClassNotFoundException e) {
                // don't add to classFoundHandlersTried
                Timber.log(TimberLog.FINER, "createPlayer: %s", e.getMessage()); // no need for call stack
            } catch (IncompatibleSourceException e) {
                classFoundHandlersTried.add(handlerClassName);
                Timber.log(TimberLog.FINER, "createPlayer(%s, %s): %s", source, contentType, e.getMessage()); // no need for call stack
            } catch (IOException e) {
                classFoundHandlersTried.add(handlerClassName);
                logger.log(Level.FINE, "" + e, e);
                if (RETHROW_IO_EXCEPTIONS)
                    throw e;
            } catch (NoPlayerException e) {
                classFoundHandlersTried.add(handlerClassName);
            } catch (NoClassDefFoundError | Exception e) {
                classFoundHandlersTried.add(handlerClassName);
                logger.log(Level.FINE, "" + e, e);
            }
        }

        final StringBuilder b = new StringBuilder();
        b.append("Tried handlers:");
        for (int i = 0; i < classFoundHandlersTried.size(); ++i) {
            b.append('\n');
            b.append(classFoundHandlersTried.get(i));
        }

        // because this exception basically masks exceptions caught above, we'll
        // add some info about what we did
        throw new NoPlayerException("No player found for " + source.getLocator() + " - " + b.toString());
    }

    public static Player createPlayer(java.net.URL sourceURL)
            throws java.io.IOException, NoPlayerException
    {
        return createPlayer(new MediaLocator(sourceURL));
    }

    public static Player createPlayer(MediaLocator sourceLocator)
            throws java.io.IOException, NoPlayerException
    {
        final String protocol = sourceLocator.getProtocol();
        for (String dataSourceClassName : getDataSourceList(protocol)) {
            try {
                final Class<?> dataSourceClass = Class.forName(dataSourceClassName);
                final DataSource dataSource = (DataSource) dataSourceClass.newInstance();
                dataSource.setLocator(sourceLocator);
                dataSource.connect();
                return createPlayer(dataSource);

                // TODO: JMF seems to disconnect data sources in this method,
                // based on this stack trace:
                // java.lang.NullPointerException at
                // com.sun.media.protocol.rtp.DataSource.disconnect(DataSource.java:207)
                // at javax.media.Manager.createPlayer(Manager.java:425) at
                // net.sf.fmj.ui.application.ContainerPlayer.createNewPlayer(ContainerPlayer.java:357)

                // TODO: JMF appears to get the streams from the data source
                // (somewhere near) here. If the stream is null, we get (from JMF:)
                // javax.media.NoPlayerException: Error instantiating class:
                // com.sun.media.content.unknown.Handler : java.io.IOException:
                // Got a null stream from the DataSource at
                // javax.media.Manager.createPlayerForSource(Manager.java:1502)
                // at javax.media.Manager.createPlayer(Manager.java:500)
                // more specifically, it calls getStreams, and if there are no
                // streams or only a null streams, it throws an exception.
                // it then calls isRandomAccess on the stream (or streams?).

                // perhaps calling random access is necessary to determine
                // whether some kind of buffered clone of the source is needed
                // OR whether the datasource needs to be closed and re-opened,
                // for the case when one handler is tried, and rejects
                // the source. If the source is not random access, then you'd
                // have to throw it a way if a handler opened it, read
                // from it, and rejected it.
                // see comments below with URLDataSource connection, below.
                // this is one of the weaknesses of the JMF architecture, that
                // it potentially needs to open a media data source multiple times
                // in order to figure out what to do with it.
            } catch (NoPlayerException e) { // no need to log, will be logged by call to createPlayer.
            } catch (ClassNotFoundException e) {
                Timber.log(TimberLog.FINER, "createPlayer: %s", e.getMessage()); // no need for call stack
            } catch (IOException e) {
                logger.log(Level.FINE, "" + e, e);
                if (RETHROW_IO_EXCEPTIONS)
                    throw e;
            } catch (NoClassDefFoundError | Exception e) {
                logger.log(Level.FINE, "" + e, e);
            }
        }

        // if none found, try URLDataSource:
        final URL url;
        try {
            url = sourceLocator.getURL();
        } catch (Exception e) {
            logger.log(Level.WARNING, "" + e, e);
            throw new NoPlayerException();
        }
        final URLDataSource dataSource = new URLDataSource(url);
        dataSource.connect(); // TODO: there is a problem because we connect to the datasource here, but
        // the following call may try twice or more to use the datasource with a player, once
        // for the right content type, and multiple times for unknown. The first attempt (for example)
        // may actually read data, in which case the second one will be
        // missing data when it reads. really, the datasource needs to be recreated.
        // The workaround for now is that URLDataSource (and others) allows repeated connect() calls.
        return createPlayer(dataSource);
    }

    public static Processor createProcessor(DataSource source)
            throws java.io.IOException, NoProcessorException
    {
        // cmeng - Android has no handler for raw type - so go straight to unknown
        String contentType = source.getContentType();
        if (contentType.equals("raw")) {
            contentType = "unknown";
        }

        try {
            Timber.i("### Creating Processor for DataSource ###: %s for type: %s", source, contentType);
            Processor processor = createProcessor(source, contentType);
            return processor;
        } catch (IOException e) {
            logger.log(Level.FINE, "" + e, e);
            if (RETHROW_IO_EXCEPTIONS)
                throw e;
        } catch (NoProcessorException e) {
            Timber.i("Failed to create Processor for DataSource#1: %s and type: %s", source, source.getContentType());
            // no need to log, will be logged by call to createProcessor.
        } catch (Exception e) {
            logger.log(Level.FINE, "" + e, e);
        }
        Timber.i("### Re-attempt to create Processor for DataSource ###: %s with type: unknown", source);
        return createProcessor(source, "unknown");
    }

    private static Processor createProcessor(DataSource source, String contentType)
            throws java.io.IOException, NoProcessorException
    {
        for (String handlerClassName : getProcessorClassList(contentType)) {
            Timber.i("### Creating Processor with className: %s for type: %s", handlerClassName, contentType);
            try {
                final Class<?> handlerClass = Class.forName(handlerClassName);
                if (!Processor.class.isAssignableFrom(handlerClass) && !MediaProxy.class.isAssignableFrom(handlerClass))
                    continue; // skip any classes that will not be matched below.
                final MediaHandler handler = (MediaHandler) handlerClass.newInstance();
                handler.setSource(source);
                if (handler instanceof Processor) {
                    return (Processor) handler;
                }
                else if (handler instanceof MediaProxy) {
                    final MediaProxy mediaProxy = (MediaProxy) handler;
                    return createProcessor(mediaProxy.getDataSource());
                }
            } catch (ClassNotFoundException e) {
                Timber.log(TimberLog.FINER, "createProcessor: Class not found: %s", e.getMessage()); // no need for call stack
            } catch (IncompatibleSourceException e) {
                Timber.log(TimberLog.FINER, "createProcessor(%s, %s): %s", source, contentType, e.getMessage()); // no need for call stack
            } catch (NoProcessorException e) {
                // no need to log, will be logged by call to createProcessor.
            } catch (IOException e) {
                logger.log(Level.FINE, "" + e, e);
                if (RETHROW_IO_EXCEPTIONS)
                    throw e;
            } catch (NoClassDefFoundError | Exception e) {
                logger.log(Level.FINE, "" + e, e);
            }
        }
        throw new NoProcessorException();
    }

    public static Processor createProcessor(java.net.URL sourceURL)
            throws java.io.IOException, NoProcessorException
    {
        return createProcessor(new MediaLocator(sourceURL));
    }

    public static Processor createProcessor(MediaLocator sourceLocator)
            throws java.io.IOException, NoProcessorException
    {
        final String protocol = sourceLocator.getProtocol();
        for (String dataSourceClassName : getDataSourceList(protocol)) {
            try {
                final Class<?> dataSourceClass = Class.forName(dataSourceClassName);
                final DataSource dataSource = (DataSource) dataSourceClass.newInstance();
                dataSource.setLocator(sourceLocator);
                dataSource.connect();
                return createProcessor(dataSource);

            } catch (ClassNotFoundException e) {
                Timber.log(TimberLog.FINER, "createProcessor: %s", e.getMessage()); // no need for call stack
            } catch (IOException e) {
                logger.log(Level.FINE, "" + e, e);
                if (RETHROW_IO_EXCEPTIONS)
                    throw e;
            } catch (NoProcessorException e) {
                // no need to log, will be logged by call to createProcessor.
            } catch (NoClassDefFoundError | Exception e) {
                logger.log(Level.FINE, "" + e, e);
            }
        }

        // if none found, try URLDataSource:
        final URL url;
        try {
            url = sourceLocator.getURL();
        } catch (Exception e) {
            logger.log(Level.WARNING, "" + e, e);
            throw new NoProcessorException();
        }
        final URLDataSource dataSource = new URLDataSource(url);
        dataSource.connect();
        return createProcessor(dataSource);
    }

    public static Player createRealizedPlayer(DataSource source)
            throws java.io.IOException, NoPlayerException, CannotRealizeException
    {
        final Player player = createPlayer(source);
        blockingRealize(player);
        return player;
    }

    public static Player createRealizedPlayer(java.net.URL sourceURL)
            throws java.io.IOException, NoPlayerException, CannotRealizeException
    {
        final Player player = createPlayer(sourceURL);
        blockingRealize(player);
        return player;
    }

    public static Player createRealizedPlayer(MediaLocator ml)
            throws java.io.IOException, NoPlayerException, CannotRealizeException
    {
        final Player player = createPlayer(ml);
        blockingRealize(player);
        return player;
    }

    public static Processor createRealizedProcessor(ProcessorModel model)
            throws java.io.IOException, NoProcessorException, CannotRealizeException
    {
        // TODO: verify that this implementation is JMF-compatible
        // TODO: according to
        // http://www.ee.iitm.ac.in/~tgvenky/JMFBook/SampleSections/8.3.pdf,
        // if the model's content descriptor is null, it is to behave like a player.

        final Processor processor;
        if (model.getInputDataSource() != null)
            processor = createProcessor(model.getInputDataSource());
        else
            processor = createProcessor(model.getInputLocator());
        // TODO: if model.getInputLocator() is null and ds is null, we are to
        // get a capture device, according to JMF.

        final net.sf.fmj.ejmf.toolkit.util.StateWaiter stateWaiter = new net.sf.fmj.ejmf.toolkit.util.StateWaiter(processor);

        if (!stateWaiter.blockingConfigure())
            throw new CannotRealizeException("Failed to configure");

        if (model.getContentDescriptor() != null)
            processor.setContentDescriptor(model.getContentDescriptor());

        final Format[] outputFormats;
        int numTracks = model.getTrackCount(Integer.MAX_VALUE);
        if (numTracks > 0) {
            outputFormats = new Format[numTracks];
            for (int i = 0; i < outputFormats.length; ++i)
                outputFormats[i] = model.getOutputTrackFormat(i);
        }
        else
            outputFormats = null;

        if (outputFormats != null && outputFormats.length > 0) {
            final TrackControl trackControl[] = processor.getTrackControls();

            // which tracks have been either configured or disabled
            final boolean[] trackConfigured = new boolean[trackControl.length];

            // which output formats we  have found a track for.
            final boolean[] outputFormatUsed = new boolean[outputFormats.length];

            // assign specified formats to compatible tracks:
            for (int j = 0; j < outputFormats.length; ++j) {
                final Format outputFormat = outputFormats[j];
                if (outputFormat == null) { // this format does not need to be set.
                    continue;
                }

                // Go through the tracks and try to program one of them to output the desired format.

                for (int i = 0; i < trackControl.length; i++) {
                    if (trackConfigured[i])
                        continue;

                    if (trackControl[i] == null) {
                        Timber.w("Disabling track %d; trackControl is not a FormatControl: %s", i, trackControl[i]);
                        trackControl[i].setEnabled(false);
                        trackConfigured[i] = true;
                    }
                    else {
                        if (trackControl[i].setFormat(outputFormat) == null) {
                            Timber.log(TimberLog.FINER, "Track %d; does not accept %S", i, outputFormat);
                        }
                        else {
                            Timber.log(TimberLog.FINER, "Using track %d; accepted %s", i, outputFormat);
                            trackConfigured[i] = true;
                            outputFormatUsed[j] = true;
                        }
                    }
                }
            }

            // any remaining free tracks can be used for any "unspecified" formats.
            for (int j = 0; j < outputFormats.length; ++j) {
                final Format outputFormat = outputFormats[j];
                if (outputFormat == null) {
                    for (int i = 0; i < trackControl.length; i++) {
                        if (!trackConfigured[i]) {
                            Timber.log(TimberLog.FINER, "Using track %d; for unspecified format", i);
                            trackConfigured[i] = true;
                            outputFormatUsed[j] = true;
                        }
                    }
                }
            }
            // disable any unused tracks
            for (int i = 0; i < trackControl.length; i++) {
                if (!trackConfigured[i]) {
                    Timber.i("Disabling track %d; no format set.", i);
                    trackControl[i].setEnabled(false);
                }
            }

            // make sure all formats have been assigned to a track:
            for (int j = 0; j < outputFormats.length; ++j) {
                if (!outputFormatUsed[j])
                    throw new CannotRealizeException("No tracks found that are compatible with format " + outputFormats[j]);
                // TODO: others that weren't used.
            }
        }
        if (!stateWaiter.blockingRealize()) {
            throw new CannotRealizeException("Failed to realize");
        }
        return processor;
    }

    public static String getCacheDirectory()
    {
        return System.getProperty("java.io.tmpdir");
    }

    public static Vector<String> getClassList(String contentName, Vector<?> packages, String component2, String className)
    {
        final Vector<String> result = new Vector<>();
        if (USE_MEDIA_PREFIX) {
            result.add("media." + component2 + "." + contentName + "." + className);
        }

        for (Object aPackage : packages) {
            result.add(aPackage + ".media." + component2 + "." + contentName + "." + className);
        }
        return result;
    }

    public static Vector<String> getDataSinkClassList(String contentName)
    {
        return getClassList(toPackageFriendly(contentName), PackageManager.getContentPrefixList(), "datasink", "Handler");
    }

    public static Vector<String> getDataSourceList(String protocolName)
    {
        return getClassList(protocolName, PackageManager.getProtocolPrefixList(), "protocol", "DataSource");
    }

    public static Vector<String> getHandlerClassList(String contentName)
    {
        return getClassList(toPackageFriendly(contentName), PackageManager.getContentPrefixList(), "content", "Handler");
    }

    public static Object getHint(int hint)
    {
        return hints.get(hint);
    }

    public static Vector<String> getProcessorClassList(String contentName)
    {
        return getClassList(toPackageFriendly(contentName), PackageManager.getContentPrefixList(), "processor", "Handler");
    }

    public static TimeBase getSystemTimeBase()
    {
        return systemTimeBase;
    }

    public static String getVersion()
    {
        // try to load fmj.build.properties as a resource, look for build=.
        // this will succeed only for release builds, not when running from
        // CVS. This allows us to see what build a user is running.
        try {
            final Properties p = new Properties();
            p.load(Manager.class.getResourceAsStream("/" + "fmj.build.properties"));
            final String s = p.getProperty("build");
            if (s != null && !s.equals(""))
                return "FMJ " + s.trim();
        } catch (Exception e) { // ignore, fall through...
        }

        return "FMJ non-release x.x"; // this is just a generic reponse when
        // running from CVS or any other source.

        // for compatibility reasons. Eventually, we should perhaps have our own version numbering.
        // return "2.1.1e";
    }

    public static void setHint(int hint, Object value)
    {
        hints.put(hint, value);
    }

    private static char toPackageFriendly(char c)
    {
        if (c >= 'a' && c <= 'z')
            return c;
        else if (c >= 'A' && c <= 'Z')
            return c;
        else if (c >= '0' && c <= '9')
            return c;
        else if (c == '.')
            return c;
        else if (c == '/')
            return '.';
        else
            return '_';
    }

    private static String toPackageFriendly(String contentName)
    {
        final StringBuilder b = new StringBuilder();
        for (int i = 0; i < contentName.length(); ++i) {
            final char c = contentName.charAt(i);
            b.append(toPackageFriendly(c));
        }
        return b.toString();
    }
}
