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

import java.util.Map;
import java.util.function.Supplier;
import java.util.logging.Handler;
import java.util.logging.Level;

public interface Logger {
    /**
     * Create a 'child' logger which derives from this one.  The child logger
     * will share the same log level setting as this one and its
     * {@link LogContext} (given here) will, in addition to the values
     * it contains, have the parent logger's context merged into it (the child's
     * context values take priority in case of a conflict)
     *
     * @return the created logger
     */
    Logger createChildLogger(String name, Map<String, String> context);

    /**
     * Create a 'child' logger which derives from this one.  The child logger
     * will share the same log level setting as this one and it will inherit
     * this logger's {@link LogContext}
     *
     * @return the created logger
     */
    Logger createChildLogger(String name);

    /**
     * See {@link java.util.logging.Logger#setUseParentHandlers(boolean)}
     */
    void setUseParentHandlers(boolean useParentHandlers);

    /**
     * See {@link java.util.logging.Logger#addHandler(Handler)}
     */
    void addHandler(Handler handler) throws SecurityException;

    /**
     * See {@link java.util.logging.Logger#removeHandler(Handler)}
     */
    void removeHandler(Handler handler) throws SecurityException;

    /**
     * Check if a message with a TRACE level would actually be logged by this
     * logger.
     * <p>
     *
     * @return true if the TRACE level is currently being logged
     */
    boolean isTraceEnabled();

    /**
     * Log a TRACE message.
     * <p>
     * If the logger is currently enabled for the TRACE message
     * level then the given message is forwarded to all the
     * registered output Handler objects.
     * <p>
     *
     * @param msg The message to log
     */
    void trace(Object msg);

    /**
     * Log a TRACE message.  Only invokes the given supplier
     * if the TRACE level is currently loggable.
     *
     * @param msgSupplier a {@link Supplier} which will return the
     * log mesage when invoked
     */
    void trace(Supplier<String> msgSupplier);

    /**
     * Check if a message with a DEBUG level would actually be logged by this
     * logger.
     * <p>
     *
     * @return true if the DEBUG level is currently being logged
     */
    boolean isDebugEnabled();

    /**
     * Log a DEBUG message.
     * <p>
     * If the logger is currently enabled for the DEBUG message
     * level then the given message is forwarded to all the
     * registered output Handler objects.
     * <p>
     *
     * @param msg The message to log
     */
    void debug(Object msg);

    /**
     * Log a DEBUG message.  Only invokes the given supplier
     * if the DEBUG level is currently loggable.
     *
     * @param msgSupplier a {@link Supplier} which will return the log mesage when invoked
     */
    void debug(Supplier<String> msgSupplier);

    /**
     * Check if a message with an INFO level would actually be logged by this logger.
     *
     * @return true if the INFO level is currently being logged
     */
    boolean isInfoEnabled();

    /**
     * Log an INFO message.
     * <p>
     * If the logger is currently enabled for the INFO message
     * level then the given message is forwarded to all the
     * registered output Handler objects.
     * <p>
     *
     * @param msg The message to log
     */
    void info(Object msg);

    /**
     * Log an INFO message.  Only invokes the given supplier
     * if the INFO level is currently loggable.
     *
     * @param msgSupplier a {@link Supplier} which will return the log mesage when invoked
     */
    void info(Supplier<String> msgSupplier);

    /**
     * Check if a message with a WARN level would actually be logged by this logger.
     *
     * @return true if the WARN level is currently being logged
     */
    boolean isWarnEnabled();

    /**
     * Log a WARN message.
     * <p>
     * If the logger is currently enabled for the WARN message
     * level then the given message is forwarded to all the
     * registered output Handler objects.
     * <p>
     *
     * @param msg The message to log
     */
    void warn(Object msg);

    /**
     * Log a WARN message.  Only invokes the given supplier
     * if the WARN level is currently loggable.
     *
     * @param msgSupplier a {@link Supplier} which will return the log mesage when invoked
     */
    void warn(Supplier<String> msgSupplier);

    /**
     * Log a message, with associated Throwable information.
     * <p>
     *
     * @param msg The message to log
     * @param t Throwable associated with log message.
     */
    void warn(Object msg, Throwable t);

    /**
     * Log a ERROR message.
     * <p>
     * If the logger is currently enabled for the ERROR message
     * level then the given message is forwarded to all the
     * registered output Handler objects.
     * <p>
     *
     * @param msg The message to log
     */
    void error(Object msg);

    /**
     * Log an ERROR message.  Only invokes the given supplier if the ERROR level is currently loggable.
     *
     * @param msgSupplier a {@link Supplier} which will return the log mesage when invoked
     */
    void error(Supplier<String> msgSupplier);

    /**
     * Log a message, with associated Throwable information.
     * <p>
     *
     * @param msg The message to log
     * @param t Throwable associated with log message.
     */
    void error(Object msg, Throwable t);

    /**
     * Set logging level for all handlers to ERROR
     */
    void setLevelError();

    /**
     * Set logging level for all handlers to WARNING
     */
    void setLevelWarn();

    /**
     * Set logging level for all handlers to INFO
     */
    void setLevelInfo();

    /**
     * Set logging level for all handlers to DEBUG
     */
    void setLevelDebug();

    /**
     * Set logging level for all handlers to TRACE
     */
    void setLevelTrace();

    /**
     * Set logging level for all handlers to ALL (allow all log messages)
     */
    void setLevelAll();

    /**
     * Set logging level for all handlers to OFF (allow no log messages)
     */
    void setLevelOff();

    /**
     * Set logging level for all handlers to <tt>level</tt>
     *
     * @param level the level to set for all logger handlers
     */
    void setLevel(Level level);

    /**
     * @return the {@link Level} configured for this {@link java.util.logging.Logger}.
     */
    Level getLevel();

    /**
     * Add additional log context to this logger
     *
     * @param addedContext a map of key, value pairs of the key names and values to add
     */
    void addContext(Map<String, String> addedContext);

    /**
     * Add additional log context to this logger
     *
     * @param key the context key
     * @param value the context value
     */
    void addContext(String key, String value);
}
