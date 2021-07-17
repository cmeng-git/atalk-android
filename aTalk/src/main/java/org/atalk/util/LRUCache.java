/*
 * Copyright @ 2015 Atlassian Pty Ltd
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.atalk.util;

import java.util.*;

/**
 * @author George Politis
 */
public class LRUCache<K, V> extends LinkedHashMap<K, V>
{
    /**
     * Creates a new LRU set. For a set with insertion order
     * ({@code accessOrder = false}), only inserting new elements in the set
     * is taken into account. With access order, any insertion (even for
     * elements already in the set) "touches" them.
     *
     * @param cacheSize the maximum number of entries.
     * @param accessOrder {@code true} to use access order, and {@code false} to use insertion order.
     */
    public static <T> Set<T> lruSet(int cacheSize, boolean accessOrder)
    {
        return Collections.newSetFromMap(new LRUCache<T, Boolean>(cacheSize, accessOrder));
    }

    /**
     * The maximum number of entries this cache will store.
     */
    private int cacheSize;

    /**
     * Initializes a {@link LRUCache} with a given size using insertion order.
     *
     * @param cacheSize the maximum number of entries.
     */
    public LRUCache(int cacheSize)
    {
        this(cacheSize, false);
    }

    /**
     * Initializes a {@link LRUCache} with a given size using either
     * insertion or access order depending on {@code accessOrder}.
     *
     * @param cacheSize the maximum number of entries.
     * @param accessOrder {@code true} to use access order, and {@code false} to use insertion order.
     */
    public LRUCache(int cacheSize, boolean accessOrder)
    {
        super(16 /* DEFAULT_INITIAL_CAPACITY */,
                0.75f /* DEFAULT_LOAD_FACTOR */,
                accessOrder);
        this.cacheSize = cacheSize;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected boolean removeEldestEntry(Map.Entry<K, V> eldest)
    {
        return size() > cacheSize;
    }
}