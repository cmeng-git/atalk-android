/*
 * Copyright @ 2018 - present 8x8, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.atalk.util.logging2;

import java.util.Collections;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;

/**
 * Implements {@link Logger} by delegating to a {@link java.util.logging.Logger}.
 */
public class LoggerImpl implements Logger
{
    static Function<String, java.util.logging.Logger> loggerFactory = java.util.logging.Logger::getLogger;

    private final java.util.logging.Logger loggerDelegate;

    /**
     * The 'minimum' level a log statement must be to be logged by this Logger. For example, if this
     * is set to {@link Level#WARNING}, then only log statements at the warning level or above
     * will actually be logged.
     */
    private final Level minLogLevel;

    private final LogContext logContext;

    public LoggerImpl(String name)
    {
        this(name, Level.ALL);
    }

    public LoggerImpl(String name, Level minLogLevel)
    {
        this(name, minLogLevel, new LogContext());
    }

    public LoggerImpl(String name, LogContext logContext)
    {
        this(name, Level.ALL, logContext);
    }

    public LoggerImpl(String name, Level minLogLevel, LogContext logContext)
    {
        this.loggerDelegate = LoggerImpl.loggerFactory.apply(name);
        this.minLogLevel = minLogLevel;
        this.logContext = logContext;
    }

    /**
     * Create a new logger with the given name.  The resulting logger's {@link LogContext}
     * will be the result of merging the given {@link LogContext} with this logger's
     * {@link LogContext}.
     *
     * @param name
     * @param context
     * @return
     */
    @Override
    public Logger createChildLogger(String name, Map<String, String> context)
    {
        return new LoggerImpl(name, minLogLevel, this.logContext.createSubContext(context));
    }

    @Override
    public Logger createChildLogger(String name)
    {
        // Note that we still need to create a subcontext here for the log
        // context, otherwise if other values are added later they'll affect
        // the parent's log context as well.
        return new LoggerImpl(name, minLogLevel, this.logContext.createSubContext(Collections.emptyMap()));
    }

    @Override
    public void setUseParentHandlers(boolean useParentHandlers)
    {
        loggerDelegate.setUseParentHandlers(false);
    }

    @Override
    public void addHandler(Handler handler) throws SecurityException
    {
        loggerDelegate.addHandler(handler);
    }

    @Override
    public void removeHandler(Handler handler) throws SecurityException
    {
        loggerDelegate.removeHandler(handler);
    }

    private boolean isLoggable(Level level)
    {
        return level.intValue() >= minLogLevel.intValue() && loggerDelegate.isLoggable(level);
    }

    private void log(Level level, Object msg, Throwable thrown)
    {
        if (!isLoggable(level)) {
            return;
        }
        LogRecord lr = new ContextLogRecord(level, msg.toString(), logContext.formattedContext);
        lr.setThrown(thrown);
        lr.setLoggerName(this.loggerDelegate.getName());
        loggerDelegate.log(lr);
    }

    private void log(Level level, Object msg)
    {
        if (!isLoggable(level)) {
            return;
        }
        LogRecord lr = new ContextLogRecord(level, msg.toString(), logContext.formattedContext);
        lr.setLoggerName(this.loggerDelegate.getName());
        loggerDelegate.log(lr);
    }

    private void log(Level level, Supplier<String> msgSupplier)
    {
        if (!isLoggable(level)) {
            return;
        }
        LogRecord lr = new ContextLogRecord(level, msgSupplier.get(), logContext.formattedContext);
        lr.setLoggerName(this.loggerDelegate.getName());
        loggerDelegate.log(lr);
    }

    @Override
    public void setLevel(Level level)
    {
        Handler[] handlers = loggerDelegate.getHandlers();
        for (Handler handler : handlers)
            handler.setLevel(level);

        loggerDelegate.setLevel(level);
    }

    @Override
    public Level getLevel()
    {
        // OpenJDK's Logger implementation initializes its effective level value
        // with Level.INFO.intValue(), but DOESN'T initialize the Level object.
        // So, if it hasn't been explicitly set, assume INFO.
        Level level = loggerDelegate.getLevel();
        return level != null ? level : Level.INFO;
    }

    @Override
    public void setLevelAll()
    {
        setLevel(Level.ALL);
    }

    @Override
    public void setLevelDebug()
    {
        setLevel(Level.FINE);
    }

    @Override
    public void setLevelError()
    {
        setLevel(Level.SEVERE);
    }

    @Override
    public void setLevelInfo()
    {
        setLevel(Level.INFO);
    }

    @Override
    public void setLevelOff()
    {
        setLevel(Level.OFF);
    }

    @Override
    public void setLevelTrace()
    {
        setLevel(Level.FINER);
    }

    @Override
    public void setLevelWarn()
    {
        setLevel(Level.WARNING);
    }

    @Override
    public boolean isTraceEnabled()
    {
        return isLoggable(Level.FINER);
    }

    @Override
    public void trace(Object msg)
    {
        log(Level.FINER, msg);
    }

    @Override
    public void trace(Supplier<String> msgSupplier)
    {
        log(Level.FINER, msgSupplier);
    }

    @Override
    public boolean isDebugEnabled()
    {
        return isLoggable(Level.FINE);
    }

    @Override
    public void debug(Object msg)
    {
        log(Level.FINE, msg);
    }

    @Override
    public void debug(Supplier<String> msgSupplier)
    {
        log(Level.FINE, msgSupplier);
    }

    @Override
    public boolean isInfoEnabled()
    {
        return isLoggable(Level.INFO);
    }

    @Override
    public void info(Object msg)
    {
        log(Level.INFO, msg);
    }

    @Override
    public void info(Supplier<String> msgSupplier)
    {
        log(Level.INFO, msgSupplier);
    }

    @Override
    public boolean isWarnEnabled()
    {
        return isLoggable(Level.WARNING);
    }

    @Override
    public void warn(Object msg)
    {
        log(Level.WARNING, msg);
    }

    @Override
    public void warn(Supplier<String> msgSupplier)
    {
        log(Level.WARNING, msgSupplier);
    }

    @Override
    public void warn(Object msg, Throwable t)
    {
        log(Level.WARNING, msg, t);
    }

    @Override
    public void error(Object msg)
    {
        log(Level.SEVERE, msg);
    }

    @Override
    public void error(Supplier<String> msgSupplier)
    {
        log(Level.SEVERE, msgSupplier);
    }

    @Override
    public void error(Object msg, Throwable t)
    {
        log(Level.SEVERE, msg, t);
    }

    @Override
    public void addContext(Map<String, String> addedContext)
    {
        logContext.addContext(addedContext);
    }

    @Override
    public void addContext(String key, String value)
    {
        logContext.addContext(key, value);
    }
}
