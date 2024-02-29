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

import com.google.common.collect.ImmutableMap;

import org.atalk.util.collections.JMap;
import org.jetbrains.annotations.NotNull;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Maintains a map of key-value pairs (both Strings) which holds
 * arbitrary context to use as a prefix for log messages.  Sub-contexts
 * can be created and will inherit any context values from their ancestors'
 * context.
 */
// Supress warnings about access since this is a library and usages will
// occur outside this repo
@SuppressWarnings("WeakerAccess")
public class LogContext {
    public static final String CONTEXT_START_TOKEN = "[";
    public static final String CONTEXT_END_TOKEN = "]";

    /**
     * All context inherited from the 'ancestors' of this
     * LogContext
     */
    protected ImmutableMap<String, String> ancestorsContext;

    /**
     * The context held by this specific LogContext.
     */
    protected ImmutableMap<String, String> context;

    /**
     * The formatted String representing the total context
     * (the combination of the ancestors' context and this
     * context)
     */
    protected String formattedContext;

    /**
     * Child LogContext's of this LogContext (which will be notified
     * anytime this context changes)
     */
    private final List<WeakReference<LogContext>> childContexts = new ArrayList<>();

    public LogContext() {
        this(Collections.emptyMap());
    }

    public LogContext(Map<String, String> context) {
        this(context, ImmutableMap.of());
    }

    protected LogContext(Map<String, String> context, ImmutableMap<String, String> ancestorsContext) {
        this.context = ImmutableMap.copyOf(context);
        this.ancestorsContext = ancestorsContext;
        updateFormattedContext();
    }

    protected synchronized void updateFormattedContext() {
        ImmutableMap<String, String> combined = combineMaps(ancestorsContext, context);
        this.formattedContext = formatContext(combined);
        updateChildren(combined);
    }

    public synchronized LogContext createSubContext(Map<String, String> childContextData) {
        ImmutableMap<String, String> childAncestorContext = combineMaps(ancestorsContext, context);
        LogContext child = new LogContext(childContextData, childAncestorContext);
        childContexts.add(new WeakReference<>(child));
        return child;
    }

    public void addContext(String key, String value) {
        addContext(JMap.of(key, value));
    }

    public synchronized void addContext(Map<String, String> addedContext) {
        this.context = combineMaps(context, addedContext);
        updateFormattedContext();
    }

    /**
     * Notify children of changes in this context
     */
    protected synchronized void updateChildren(ImmutableMap<String, String> newAncestorContext) {
        Iterator<WeakReference<LogContext>> iter = childContexts.iterator();
        while (iter.hasNext()) {
            LogContext c = iter.next().get();
            if (c != null) {
                c.ancestorContextUpdated(newAncestorContext);
            }
            else {
                iter.remove();
            }
        }
    }

    /**
     * Handle a change in the ancestors' context
     *
     * @param newAncestorContext the ancestors' new context
     */
    protected synchronized void ancestorContextUpdated(ImmutableMap<String, String> newAncestorContext) {
        this.ancestorsContext = newAncestorContext;
        updateFormattedContext();
    }

    @Override
    public String toString() {
        return formattedContext;
    }

    /**
     * Combine all the given maps into a new map.  Note that the order in which the maps
     * are passed matters: keys in later maps will override duplicates in earlier maps.
     *
     * @param maps the maps to combine, in order of lowest to highest priority for keys
     *
     * @return an *unmodifiable* combined map containing all the data of the given maps
     */
    @SafeVarargs
    @NotNull
    protected static ImmutableMap<String, String> combineMaps(@NotNull Map<String, String>... maps) {
        Map<String, String> combinedMap = new HashMap<>();
        for (Map<String, String> map : maps) {
            combinedMap.putAll(map);
        }
        return ImmutableMap.copyOf(combinedMap);
    }

    protected static String formatContext(Map<String, String> context) {
        StringBuilder contextString = new StringBuilder();
        String data = context.entrySet()
                .stream()
                .map(e -> e.getKey() + "=" + e.getValue())
                .collect(Collectors.joining(" "));
        contextString.append(data);

        if (contextString.length() > 0) {
            return CONTEXT_START_TOKEN + contextString + CONTEXT_END_TOKEN;
        }
        else {
            return "";
        }
    }
}
